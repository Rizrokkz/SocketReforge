package irai.mod.reforge.Entity.Events;

import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Shared socket-roll logic for world loot (chests, NPC drops, etc.).
 */
@SuppressWarnings("removal")
public final class LootSocketRoller {
    public enum LootSource {
        CHEST,
        NPC_DROP
    }

    private static volatile RollProfile chestProfile = RollProfile.of(0.30d, 0.10d, 0.01d, 0.50d);
    private static volatile RollProfile dropProfile = RollProfile.of(0.30d, 0.10d, 0.01d, 0.50d);
    private static volatile int minBrokenSockets = 3;
    private static volatile int maxBrokenSockets = 5;

    private static final String META_WORLD_LOOT_ROLL_DONE = "SocketReforge.WorldLoot.RollDone";
    private static final String META_WORLD_LOOT_SOCKETED = "SocketReforge.WorldLoot.Socketed";

    // Legacy chest metadata keys kept for backward compatibility.
    private static final String META_WORLD_CHEST_ROLL_DONE = "SocketReforge.WorldLoot.ChestRollDone";
    private static final String META_WORLD_CHEST_SOCKETED = "SocketReforge.WorldLoot.ChestSocketed";

    private static final String META_SOCKET_MAX = "SocketReforge.Socket.Max";
    private static final String META_SOCKET_VALUES = "SocketReforge.Socket.Values";

    private LootSocketRoller() {}

    public static ItemStack maybeSocketizeLootStack(ItemStack stack) {
        return maybeSocketizeLootStack(stack, LootSource.CHEST);
    }

    public static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        if (!isEquipment(stack)) {
            return stack;
        }

        if (isRollDone(stack)) {
            return stack;
        }

        if (hasSocketMetadata(stack)) {
            return markRollDone(stack, 0);
        }

        RollProfile profile = getProfile(source);
        int socketCount = rollBrokenSocketCount(profile);
        if (socketCount <= 0) {
            return markRollDone(stack, 0);
        }

        ItemStack socketed = createBrokenSocketLoot(stack, socketCount, profile);
        return markRollDone(socketed, 1);
    }

    public static void setConfig(LootSocketRollConfig config) {
        if (config == null) {
            return;
        }

        chestProfile = RollProfile.of(
                config.getChestThreeSocketChance(),
                config.getChestFourSocketChance(),
                config.getChestFiveSocketChance(),
                config.getChestThreeToFourChance()
        );
        dropProfile = RollProfile.of(
                config.getDropThreeSocketChance(),
                config.getDropFourSocketChance(),
                config.getDropFiveSocketChance(),
                config.getDropThreeToFourChance()
        );

        int min = Math.max(1, config.getMinBrokenSockets());
        int max = Math.max(min, config.getMaxBrokenSockets());
        minBrokenSockets = min;
        maxBrokenSockets = max;
    }

    private static boolean isRollDone(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Integer generic = stack.getFromMetadataOrNull(META_WORLD_LOOT_ROLL_DONE, Codec.INTEGER);
        if (generic != null && generic > 0) {
            return true;
        }
        Integer legacy = stack.getFromMetadataOrNull(META_WORLD_CHEST_ROLL_DONE, Codec.INTEGER);
        return legacy != null && legacy > 0;
    }

    private static int rollBrokenSocketCount(RollProfile profile) {
        if (profile == null || profile.totalChance <= 0.0d) {
            return 0;
        }
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll >= profile.totalChance) {
            return 0;
        }

        if (roll < profile.fiveSocketChance) {
            return 5;
        }
        roll -= profile.fiveSocketChance;

        if (roll < profile.fourSocketChance) {
            return 4;
        }
        roll -= profile.fourSocketChance;

        if (roll < profile.threeSocketChance) {
            return 3;
        }
        return 0;
    }

    private static ItemStack createBrokenSocketLoot(ItemStack stack, int socketCount, RollProfile profile) {
        SocketData socketData = SocketManager.getSocketData(stack);
        if (socketData == null) {
            return stack;
        }

        int clampedSockets = Math.max(minBrokenSockets, Math.min(maxBrokenSockets, socketCount));
        int targetMaxSockets = resolveTargetMaxSockets(clampedSockets, profile);
        if (socketData.getMaxSockets() != targetMaxSockets) {
            socketData.setMaxSockets(targetMaxSockets);
        }

        while (socketData.getCurrentSocketCount() < clampedSockets) {
            if (!socketData.addSocket()) {
                break;
            }
        }

        for (Socket socket : socketData.getSockets()) {
            socket.setEssenceId(null);
            socket.setLocked(false);
            socket.setBroken(true);
        }

        return SocketManager.withSocketData(stack, socketData);
    }

    private static int resolveTargetMaxSockets(int rolledSockets, RollProfile profile) {
        if (rolledSockets == 3) {
            double chanceForFour = profile == null ? 0.50d : profile.threeToFourChance;
            return ThreadLocalRandom.current().nextDouble() < chanceForFour ? 4 : 3;
        }
        return rolledSockets;
    }

    private static RollProfile getProfile(LootSource source) {
        if (source == LootSource.NPC_DROP) {
            return dropProfile;
        }
        return chestProfile;
    }

    private static boolean hasSocketMetadata(ItemStack stack) {
        Integer max = stack.getFromMetadataOrNull(META_SOCKET_MAX, Codec.INTEGER);
        String[] values = stack.getFromMetadataOrNull(META_SOCKET_VALUES, Codec.STRING_ARRAY);
        return max != null || values != null;
    }

    private static ItemStack markRollDone(ItemStack stack, int socketedFlag) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        int flag = Math.max(0, socketedFlag);
        return stack
                .withMetadata(META_WORLD_LOOT_ROLL_DONE, Codec.INTEGER, 1)
                .withMetadata(META_WORLD_LOOT_SOCKETED, Codec.INTEGER, flag)
                .withMetadata(META_WORLD_CHEST_ROLL_DONE, Codec.INTEGER, 1)
                .withMetadata(META_WORLD_CHEST_SOCKETED, Codec.INTEGER, flag);
    }

    private static boolean isEquipment(ItemStack stack) {
        return ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack);
    }

    private static final class RollProfile {
        private final double threeSocketChance;
        private final double fourSocketChance;
        private final double fiveSocketChance;
        private final double threeToFourChance;
        private final double totalChance;

        private RollProfile(double threeSocketChance,
                            double fourSocketChance,
                            double fiveSocketChance,
                            double threeToFourChance,
                            double totalChance) {
            this.threeSocketChance = threeSocketChance;
            this.fourSocketChance = fourSocketChance;
            this.fiveSocketChance = fiveSocketChance;
            this.threeToFourChance = threeToFourChance;
            this.totalChance = totalChance;
        }

        private static RollProfile of(double three, double four, double five, double threeToFour) {
            double safeThree = clamp01(three);
            double safeFour = clamp01(four);
            double safeFive = clamp01(five);
            double total = safeThree + safeFour + safeFive;

            if (total > 1.0d) {
                double scale = 1.0d / total;
                safeThree *= scale;
                safeFour *= scale;
                safeFive *= scale;
                total = 1.0d;
            }

            return new RollProfile(safeThree, safeFour, safeFive, clamp01(threeToFour), total);
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
