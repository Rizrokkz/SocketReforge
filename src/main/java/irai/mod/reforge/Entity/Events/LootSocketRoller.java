package irai.mod.reforge.Entity.Events;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.entities.Player;

import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.NameResolver;

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
    private static volatile double chestResonanceChance = 0.01d;
    private static volatile double dropResonanceChance = 0.01d;
    private static volatile double chestSocketedEssenceChance = 0.05d;
    private static volatile double dropSocketedEssenceChance = 0.05d;
    private static volatile double greaterEssenceChance = 0.15d;

    private static final int RECIPE_PITY_THRESHOLD = 150;
    private static final Map<UUID, Integer> RECIPE_PITY = new ConcurrentHashMap<>();
    private static final Essence.Type[] ESSENCE_TYPES = Essence.Type.values();

    private static final String META_WORLD_LOOT_ROLL_DONE = "SocketReforge.WorldLoot.RollDone";
    private static final String META_WORLD_LOOT_SOCKETED = "SocketReforge.WorldLoot.Socketed";

    // Legacy chest metadata keys kept for backward compatibility.
    private static final String META_WORLD_CHEST_ROLL_DONE = "SocketReforge.WorldLoot.ChestRollDone";
    private static final String META_WORLD_CHEST_SOCKETED = "SocketReforge.WorldLoot.ChestSocketed";

    private static final String META_SOCKET_MAX = "SocketReforge.Socket.Max";
    private static final String META_SOCKET_VALUES = "SocketReforge.Socket.Values";
    private static final String META_RECIPE_NAME = "SocketReforge.Recipe.ResonanceName";
    private static final String META_RECIPE_PATTERN = "SocketReforge.Recipe.Pattern";
    private static final String META_RECIPE_TYPE = "SocketReforge.Recipe.Type";
    private static final String RECIPE_ITEM_ID = "Resonant_Recipe";
    private static final int RECIPE_MIN_REVEAL = 1;
    private static final int RECIPE_MAX_REVEAL = 3;

    private LootSocketRoller() {}

    public static ItemStack maybeSocketizeLootStack(ItemStack stack) {
        return maybeSocketizeLootStack(stack, LootSource.CHEST);
    }

    public static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source) {
        return maybeSocketizeLootStack(stack, source, null, null);
    }

    public static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source, long seed) {
        return maybeSocketizeLootStack(stack, source, Long.valueOf(seed), null);
    }

    public static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source, Player player) {
        return maybeSocketizeLootStack(stack, source, null, player);
    }

    public static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source, long seed, Player player) {
        return maybeSocketizeLootStack(stack, source, Long.valueOf(seed), player);
    }

    private static ItemStack maybeSocketizeLootStack(ItemStack stack, LootSource source, Long recipeSeed, Player player) {
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

        ItemStack resonant = recipeSeed != null
                ? maybeRollResonance(stack, source, recipeSeed.longValue(), player)
                : maybeRollResonance(stack, source, player);
        if (resonant != null) {
            return markRollDone(resonant, 1);
        }

        RollProfile profile = getProfile(source);
        int socketCount = rollBrokenSocketCount(profile);
        if (socketCount <= 0) {
            return markRollDone(stack, 0);
        }

        ItemStack socketed = shouldSocketEssences(stack, source)
                ? createSocketedEssenceLoot(stack, socketCount, profile)
                : createBrokenSocketLoot(stack, socketCount, profile);
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
        chestResonanceChance = clamp01(config.getChestResonanceChance());
        dropResonanceChance = clamp01(config.getDropResonanceChance());
        chestSocketedEssenceChance = clamp01(config.getChestSocketedEssenceChance());
        dropSocketedEssenceChance = clamp01(config.getDropSocketedEssenceChance());
        greaterEssenceChance = clamp01(config.getGreaterEssenceChance());

        int min = Math.max(1, config.getMinBrokenSockets());
        int max = Math.max(min, config.getMaxBrokenSockets());
        minBrokenSockets = min;
        maxBrokenSockets = max;
    }

    private static ItemStack maybeRollResonance(ItemStack stack, LootSource source, Player player) {
        double chance = getResonanceChance(source);
        if (chance <= 0.0d) {
            return null;
        }
        if (!rollResonantRecipeWithPity(player, chance)) {
            return null;
        }

        return createResonantRecipeShard(stack, 1);
    }

    private static ItemStack maybeRollResonance(ItemStack stack, LootSource source, long seed, Player player) {
        double chance = getResonanceChance(source);
        if (chance <= 0.0d) {
            return null;
        }
        if (!rollResonantRecipeWithPity(player, chance)) {
            return null;
        }

        return createResonantRecipeShard(stack, 1, seed);
    }

    public static boolean rollResonantRecipeWithPity(Player player, double chance) {
        if (chance <= 0.0d) {
            return false;
        }
        UUID playerId = player != null ? player.getUuid() : null;
        if (playerId == null) {
            return ThreadLocalRandom.current().nextDouble() < chance;
        }

        int current = RECIPE_PITY.getOrDefault(playerId, 0);
        if (current >= RECIPE_PITY_THRESHOLD) {
            RECIPE_PITY.remove(playerId);
            return true;
        }

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            RECIPE_PITY.remove(playerId);
            return true;
        }

        int next = Math.min(RECIPE_PITY_THRESHOLD, current + 1);
        RECIPE_PITY.put(playerId, next);
        return false;
    }

    public static ItemStack createResonantRecipeShard(ItemStack context, int quantity) {
        ResonanceSystem.ResonanceRecipe recipe = context != null
                ? ResonanceSystem.rollRandomResonanceRecipe(context)
                : ResonanceSystem.rollRandomResonanceRecipe();
        if (recipe == null || recipe.name() == null || recipe.name().isBlank()) {
            return null;
        }
        int safeQty = Math.max(1, quantity);
        String displayName = recipe.name().trim() + " Recipe";
        String pattern = buildPartialRecipe(recipe.pattern(), null);
        String appliesTo = recipe.appliesTo();
        return new ItemStack(RECIPE_ITEM_ID, safeQty)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING, displayName)
                .withMetadata(META_RECIPE_NAME, Codec.STRING, recipe.name().trim())
                .withMetadata(META_RECIPE_PATTERN, Codec.STRING, pattern)
                .withMetadata(META_RECIPE_TYPE, Codec.STRING, appliesTo == null ? "" : appliesTo);
    }

    public static ItemStack createResonantRecipeShard(ItemStack context, int quantity, long seed) {
        Random rng = new Random(seed);
        ResonanceSystem.ResonanceRecipe recipe = context != null
                ? ResonanceSystem.rollRandomResonanceRecipe(context, rng)
                : ResonanceSystem.rollRandomResonanceRecipe(rng);
        if (recipe == null || recipe.name() == null || recipe.name().isBlank()) {
            return null;
        }
        int safeQty = Math.max(1, quantity);
        String displayName = recipe.name().trim() + " Recipe";
        String pattern = buildPartialRecipe(recipe.pattern(), rng);
        String appliesTo = recipe.appliesTo();
        return new ItemStack(RECIPE_ITEM_ID, safeQty)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING, displayName)
                .withMetadata(META_RECIPE_NAME, Codec.STRING, recipe.name().trim())
                .withMetadata(META_RECIPE_PATTERN, Codec.STRING, pattern)
                .withMetadata(META_RECIPE_TYPE, Codec.STRING, appliesTo == null ? "" : appliesTo);
    }

    public static boolean hasResonantRecipeMetadata(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String pattern = stack.getFromMetadataOrNull(META_RECIPE_PATTERN, Codec.STRING);
        return pattern != null && !pattern.isBlank();
    }

    private static String buildPartialRecipe(Essence.Type[] pattern, Random rng) {
        if (pattern == null || pattern.length == 0) {
            return "";
        }
        Random random = rng != null ? rng : ThreadLocalRandom.current();
        int length = pattern.length;
        int reveals = rollRevealCount(length, random);
        boolean[] reveal = new boolean[length];
        int picked = 0;
        while (picked < reveals) {
            int idx = random.nextInt(length);
            if (!reveal[idx]) {
                reveal[idx] = true;
                picked++;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String token = reveal[i] && pattern[i] != null ? formatEssenceToken(pattern[i]) : "x";
            sb.append('[').append(token).append(']');
        }
        return sb.toString();
    }

    private static int rollRevealCount(int length, Random random) {
        if (length <= 1) {
            return length;
        }
        if (length >= 4) {
            return 1;
        }
        int max = Math.min(RECIPE_MAX_REVEAL, Math.max(1, length - 1));
        int min = Math.min(RECIPE_MIN_REVEAL, max);
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private static String formatEssenceToken(Essence.Type type) {
        String raw = type == null ? "" : type.name().toLowerCase(java.util.Locale.ROOT);
        if (raw.isEmpty()) {
            return "x";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
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

    private static ItemStack createSocketedEssenceLoot(ItemStack stack, int socketCount, RollProfile profile) {
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
            socket.setBroken(false);
        }

        int available = socketData.getCurrentSocketCount();
        if (available <= 0) {
            return SocketManager.withSocketData(stack, socketData);
        }
        int fillCount = ThreadLocalRandom.current().nextInt(1, available + 1);
        int[] indices = new int[available];
        for (int i = 0; i < available; i++) {
            indices[i] = i;
        }
        shuffle(indices);
        for (int i = 0; i < fillCount; i++) {
            String essenceId = rollRandomEssenceId();
            if (essenceId == null) {
                continue;
            }
            socketData.setEssenceAt(indices[i], essenceId);
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

    private static double getResonanceChance(LootSource source) {
        if (source == LootSource.NPC_DROP) {
            return dropResonanceChance;
        }
        return chestResonanceChance;
    }

    private static double getSocketedEssenceChance(LootSource source) {
        if (source == LootSource.NPC_DROP) {
            return dropSocketedEssenceChance;
        }
        return chestSocketedEssenceChance;
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

    private static boolean shouldSocketEssences(ItemStack stack, LootSource source) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!isEquipment(stack)) {
            return false;
        }
        double chance = getSocketedEssenceChance(source);
        return chance > 0.0d && ThreadLocalRandom.current().nextDouble() < chance;
    }

    private static String rollRandomEssenceId() {
        if (ESSENCE_TYPES.length == 0) {
            return null;
        }
        Essence.Type type = ESSENCE_TYPES[ThreadLocalRandom.current().nextInt(ESSENCE_TYPES.length)];
        boolean greater = ThreadLocalRandom.current().nextDouble() < greaterEssenceChance;
        String essenceId = SocketManager.buildEssenceId(type.name(), greater);
        if (essenceId == null) {
            return null;
        }
        if (EssenceRegistry.get().exists(essenceId)) {
            return essenceId;
        }
        String fallback = SocketManager.buildEssenceId(type.name(), false);
        return fallback != null && EssenceRegistry.get().exists(fallback) ? fallback : null;
    }

    private static void shuffle(int[] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = ThreadLocalRandom.current().nextInt(i + 1);
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }
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
