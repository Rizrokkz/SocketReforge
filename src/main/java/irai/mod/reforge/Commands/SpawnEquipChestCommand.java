package irai.mod.reforge.Commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import irai.mod.reforge.Entity.Events.TreasureChestSocketLootListener;
import irai.mod.reforge.Interactions.ReforgeEquip;

/**
 * Spawns a nearby test chest with a droplist that tends to contain equipment.
 *
 * Usage:
 * /spawnequipchest [mixed|temple|dungeon]
 */
public class SpawnEquipChestCommand extends CommandBase {
    private static final int FRONT_DISTANCE_BLOCKS = 2;
    private static final int SAMPLE_ROLLS = 24;
    private static final int TOP_DROPLIST_POOL = 8;
    private static final int MAX_CACHED_DROPLISTS = 24;
    private static final int MAX_DROPLISTS_TO_TRY = 12;
    private static final int MAX_ROLL_ATTEMPTS_PER_DROPLIST = 36;

    private static final String[] TEMPLE_CHEST_BLOCK_IDS = {
            "Furniture_Temple_Dark_Chest_Small",
            "Furniture_Temple_Dark_Chest_Large",
            "Furniture_Temple_Emerald_Chest_Small",
            "Furniture_Temple_Emerald_Chest_Large",
            "Furniture_Temple_Light_Chest_Small",
            "Furniture_Temple_Light_Chest_Large",
            "Furniture_Temple_Scarak_Chest_Small",
            "Furniture_Temple_Scarak_Chest_Large",
            "Furniture_Temple_Wind_Chest_Small",
            "Furniture_Temple_Wind_Chest_Large"
    };

    private static final String[] DUNGEON_CHEST_BLOCK_IDS = {
            "Furniture_Dungeon_Chest_Epic",
            "Furniture_Dungeon_Chest_Epic_Large",
            "Furniture_Dungeon_Chest_Legendary_Large"
    };

    private static volatile List<DroplistCandidate> cachedEquipmentDroplists = List.of();
    private static final Object DROPLIST_LOCK = new Object();

    private final OptionalArg<String> themeArg;

    public SpawnEquipChestCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("speqchest");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));
        this.themeArg = this.withOptionalArg("theme", "mixed|temple|dungeon", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }
        if (!CommandUtils.isOperator(player)) {
            context.sendMessage(Message.raw("OP only command."));
            return;
        }
        if (player.getWorld() == null) {
            context.sendMessage(Message.raw("World is not available."));
            return;
        }

        TransformComponent transform = player.getTransformComponent();
        if (transform == null || transform.getPosition() == null) {
            context.sendMessage(Message.raw("Unable to read your position."));
            return;
        }

        String requestedTheme = themeArg.provided(context) ? themeArg.get(context) : "mixed";
        String chestBlockId = chooseChestBlockId(requestedTheme);
        if (chestBlockId == null || chestBlockId.isBlank()) {
            context.sendMessage(Message.raw("Invalid theme. Use mixed, temple, or dungeon."));
            return;
        }

        List<DroplistCandidate> droplistCandidates = getEquipmentDroplistCandidates();
        if (droplistCandidates.isEmpty()) {
            context.sendMessage(Message.raw("No equipment-capable droplists were discovered."));
            return;
        }

        PreparedLoot preparedLoot = prepareEquipmentLoot(droplistCandidates);
        if (preparedLoot == null || preparedLoot.droplist == null) {
            context.sendMessage(Message.raw("Failed to prepare equipment-capable loot for test chest."));
            return;
        }

        Vector3i target = getPlacementPositionInFront(transform, FRONT_DISTANCE_BLOCKS);
        long chunkIndex = ChunkUtil.indexChunkFromBlock(target.x, target.z);

        player.getWorld().getChunkAsync(chunkIndex).thenAcceptAsync(chunk -> {
            if (chunk == null) {
                context.sendMessage(Message.raw("Target chunk is not loaded."));
                return;
            }

            boolean placed = chunk.setBlock(target.x, target.y, target.z, chestBlockId);
            if (!placed) {
                context.sendMessage(Message.raw("Failed to place chest at " + target + "."));
                return;
            }

            // Allow re-testing when repeatedly spawning into the same coordinates.
            TreasureChestSocketLootListener.resetChestRollState(player, target.x, target.y, target.z);

            Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(player.getWorld(), target.x, target.y, target.z);
            if (blockRef == null || blockRef.getStore() == null) {
                context.sendMessage(Message.raw("Placed chest has no container state."));
                return;
            }

            BlockState blockState = BlockState.getBlockState(blockRef, blockRef.getStore());
            if (!(blockState instanceof ItemContainerState containerState)) {
                context.sendMessage(Message.raw("Placed block is not an item container."));
                return;
            }

            containerState.setDroplist(preparedLoot.droplist.id);
            containerState.setCustom(false);
            int injectedSlots = injectLoot(containerState.getItemContainer(), preparedLoot.drops);

            context.sendMessage(Message.raw(
                    "Spawned test chest at " + target
                            + " | block=" + chestBlockId
                            + " | droplist=" + preparedLoot.droplist.id
                            + " (" + preparedLoot.droplist.equipmentHitRolls + "/" + SAMPLE_ROLLS + " equip rolls)"
                            + " | rollAttempts=" + preparedLoot.rollAttempts
                            + " | equipmentInChest=" + preparedLoot.equipmentCount
                            + " | lootSlots=" + injectedSlots));
        }, player.getWorld()).exceptionally(error -> {
            String reason = error == null ? "unknown error" : error.getMessage();
            context.sendMessage(Message.raw("Failed to spawn test chest: " + reason));
            return null;
        });
    }

    private static String chooseChestBlockId(String requestedTheme) {
        String theme = requestedTheme == null ? "mixed" : requestedTheme.toLowerCase(Locale.ROOT);
        return switch (theme) {
            case "temple" -> randomFrom(TEMPLE_CHEST_BLOCK_IDS);
            case "dungeon" -> randomFrom(DUNGEON_CHEST_BLOCK_IDS);
            case "mixed" -> ThreadLocalRandom.current().nextBoolean()
                    ? randomFrom(TEMPLE_CHEST_BLOCK_IDS)
                    : randomFrom(DUNGEON_CHEST_BLOCK_IDS);
            default -> null;
        };
    }

    private static String randomFrom(String[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    private static Vector3i getPlacementPositionInFront(TransformComponent transform, int distance) {
        Vector3d position = transform.getPosition();
        float yaw = transform.getRotation() != null ? transform.getRotation().getYaw() : 0f;
        double yawRadians = Math.toRadians(yaw);

        int stepX = (int) Math.round(-Math.sin(yawRadians));
        int stepZ = (int) Math.round(Math.cos(yawRadians));
        if (stepX == 0 && stepZ == 0) {
            stepZ = 1;
        }

        int x = (int) Math.floor(position.getX()) + (stepX * distance);
        int y = (int) Math.floor(position.getY());
        int z = (int) Math.floor(position.getZ()) + (stepZ * distance);
        return new Vector3i(x, y, z);
    }

    private static int injectLoot(ItemContainer container, List<ItemStack> drops) {
        if (container == null) {
            return 0;
        }

        clearContainer(container);
        if (drops == null || drops.isEmpty()) {
            return 0;
        }

        int injected = 0;
        short slot = 0;
        short capacity = container.getCapacity();
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (slot >= capacity) {
                break;
            }
            container.setItemStackForSlot(slot, stack);
            slot++;
            injected++;
        }
        return injected;
    }

    private static void clearContainer(ItemContainer container) {
        if (container == null) {
            return;
        }

        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (existing == null || existing.isEmpty()) {
                continue;
            }
            int remove = Math.max(1, existing.getQuantity());
            container.removeItemStackFromSlot(slot, remove, false, false);
        }
    }

    private static PreparedLoot prepareEquipmentLoot(List<DroplistCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        int droplistsToTry = Math.min(MAX_DROPLISTS_TO_TRY, candidates.size());
        List<DroplistCandidate> trialPool = new ArrayList<>(candidates.subList(0, droplistsToTry));
        Collections.shuffle(trialPool, ThreadLocalRandom.current());

        PreparedLoot fallback = null;
        for (DroplistCandidate candidate : trialPool) {
            for (int attempt = 1; attempt <= MAX_ROLL_ATTEMPTS_PER_DROPLIST; attempt++) {
                List<ItemStack> drops = ItemModule.get().getRandomItemDrops(candidate.id);
                int equipmentCount = countEquipment(drops);
                if (equipmentCount > 0) {
                    return new PreparedLoot(candidate, drops, equipmentCount, attempt);
                }
                if (fallback == null && drops != null && !drops.isEmpty()) {
                    fallback = new PreparedLoot(candidate, drops, 0, attempt);
                }
            }
        }

        if (fallback != null) {
            return fallback;
        }

        DroplistCandidate any = chooseDroplistCandidate(candidates);
        List<ItemStack> drops = ItemModule.get().getRandomItemDrops(any.id);
        return new PreparedLoot(any, drops, countEquipment(drops), 1);
    }

    private static List<DroplistCandidate> getEquipmentDroplistCandidates() {
        List<DroplistCandidate> snapshot = cachedEquipmentDroplists;
        if (!snapshot.isEmpty()) {
            return snapshot;
        }

        synchronized (DROPLIST_LOCK) {
            if (!cachedEquipmentDroplists.isEmpty()) {
                return cachedEquipmentDroplists;
            }

            Map<String, ItemDropList> allDroplists = ItemDropList.getAssetMap().getAssetMap();
            if (allDroplists == null || allDroplists.isEmpty()) {
                cachedEquipmentDroplists = List.of();
                return cachedEquipmentDroplists;
            }

            List<String> ids = new ArrayList<>(allDroplists.keySet());
            List<DroplistCandidate> candidates = scanDroplists(ids, true);
            if (candidates.isEmpty()) {
                candidates = scanDroplists(ids, false);
            }

            candidates.sort((a, b) -> Integer.compare(b.equipmentHitRolls, a.equipmentHitRolls));
            if (candidates.size() > MAX_CACHED_DROPLISTS) {
                candidates = new ArrayList<>(candidates.subList(0, MAX_CACHED_DROPLISTS));
            }

            cachedEquipmentDroplists = List.copyOf(candidates);
            return cachedEquipmentDroplists;
        }
    }

    private static List<DroplistCandidate> scanDroplists(Collection<String> droplistIds, boolean filterByKeyword) {
        List<DroplistCandidate> result = new ArrayList<>();
        for (String droplistId : droplistIds) {
            if (droplistId == null || droplistId.isBlank()) {
                continue;
            }
            if (filterByKeyword && !looksLikeLootChestDroplist(droplistId)) {
                continue;
            }

            int equipmentHitRolls = 0;
            for (int i = 0; i < SAMPLE_ROLLS; i++) {
                List<ItemStack> rolledDrops = ItemModule.get().getRandomItemDrops(droplistId);
                if (containsEquipment(rolledDrops)) {
                    equipmentHitRolls++;
                }
            }

            if (equipmentHitRolls > 0) {
                result.add(new DroplistCandidate(droplistId, equipmentHitRolls));
            }
        }
        return result;
    }

    private static DroplistCandidate chooseDroplistCandidate(List<DroplistCandidate> candidates) {
        int poolSize = Math.min(TOP_DROPLIST_POOL, candidates.size());
        int index = ThreadLocalRandom.current().nextInt(poolSize);
        return candidates.get(index);
    }

    private static boolean looksLikeLootChestDroplist(String droplistId) {
        String normalized = droplistId.toLowerCase(Locale.ROOT);
        return normalized.contains("chest")
                || normalized.contains("treasure")
                || normalized.contains("loot")
                || normalized.contains("dungeon")
                || normalized.contains("temple")
                || normalized.contains("ruins")
                || normalized.contains("castle")
                || normalized.contains("legendary")
                || normalized.contains("epic");
    }

    private static boolean containsEquipment(List<ItemStack> drops) {
        return countEquipment(drops) > 0;
    }

    private static int countEquipment(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                count++;
            }
        }
        return count;
    }

    private static final class DroplistCandidate {
        private final String id;
        private final int equipmentHitRolls;

        private DroplistCandidate(String id, int equipmentHitRolls) {
            this.id = id;
            this.equipmentHitRolls = equipmentHitRolls;
        }
    }

    private static final class PreparedLoot {
        private final DroplistCandidate droplist;
        private final List<ItemStack> drops;
        private final int equipmentCount;
        private final int rollAttempts;

        private PreparedLoot(DroplistCandidate droplist, List<ItemStack> drops, int equipmentCount, int rollAttempts) {
            this.droplist = droplist;
            this.drops = drops == null ? List.of() : drops;
            this.equipmentCount = Math.max(0, equipmentCount);
            this.rollAttempts = Math.max(1, rollAttempts);
        }
    }
}
