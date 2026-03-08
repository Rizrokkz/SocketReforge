package irai.mod.reforge.Entity.Events;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.hypixel.hytale.builtin.adventure.objectives.events.TreasureChestOpeningEvent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import irai.mod.reforge.Common.LootInjectionUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;

/**
 * Adds a one-time roll for treasure chest equipment to spawn with broken sockets.
 */
@SuppressWarnings("removal")
public final class TreasureChestSocketLootListener {
    private static final Logger LOGGER = Logger.getLogger("SocketReforge.WorldLoot");
    private static final Set<String> ROLLED_CHESTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_SKIPPED_CHESTS = ConcurrentHashMap.newKeySet();
    private static final List<LootInjectionUtils.LootInjectionRule> CHEST_LOOT_INJECTION_RULES = List.of(
            LootInjectionUtils.rule("Refinement_Glob", 0.15d, 1, 30),
            LootInjectionUtils.rule("Socket_Puncher", 0.15d, 1, 30),
            LootInjectionUtils.rule("Socket_Stabilizer", 0.15d, 1, 5),
            LootInjectionUtils.rule("Ingredient_Voidheart", 0.05d, 1, 2)
    );
    private static final String[] WORLD_LOOT_BLOCK_ID_HINTS = {
            "furniture_temple_",
            "treasure_chest",
            "loot_chest",
            "dungeon_chest"
    };
    private static Field itemContainerCustomField;

    private TreasureChestSocketLootListener() {}

    public static void onTreasureChestOpening(TreasureChestOpeningEvent event) {
        if (event == null || event.getStore() == null || event.getPlayerRef() == null) {
            return;
        }

        Player player = event.getStore().getComponent(event.getPlayerRef(), Player.getComponentType());
        if (player == null) {
            return;
        }

        int changed = applyLootSocketsToOpenTreasureChest(player, "treasure_event");
        if (changed > 0) return;

        if (player.getWorld() != null) {
            player.getWorld().execute(() -> applyLootSocketsToOpenTreasureChest(player, "treasure_event_deferred"));
        }
    }

    /**
     * Fallback path for normal world chests where TreasureChestOpeningEvent may not fire.
     */
    public static void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || player.getWorld() == null) {
            return;
        }

        InteractionType action = event.getActionType();
        if (action != InteractionType.Use && action != InteractionType.Secondary && action != InteractionType.Primary) {
            return;
        }
        if (event.getTargetBlock() == null) {
            return;
        }

        player.getWorld().execute(() -> applyLootSocketsToOpenTreasureChest(player, "interact_event"));
    }

    public static int scanOpenChestWindows(Player player, String source) {
        return applyLootSocketsToOpenTreasureChest(player, source);
    }

    /**
     * Clears runtime roll-tracking for a chest location.
     * Useful for test-spawned chests placed repeatedly at the same coordinates.
     */
    public static void resetChestRollState(Player player, int x, int y, int z) {
        if (player == null || player.getWorld() == null) {
            return;
        }
        String chestKey = player.getWorld().getName() + "|" + x + "|" + y + "|" + z;
        ROLLED_CHESTS.remove(chestKey);
        LOGGED_SKIPPED_CHESTS.remove(chestKey);
    }

    private static int applyLootSocketsToOpenTreasureChest(Player player, String source) {
        if (player == null || player.getWindowManager() == null) {
            return 0;
        }

        List<Window> windows = player.getWindowManager().getWindows();
        if (windows == null || windows.isEmpty()) {
            return 0;
        }

        int changed = 0;
        for (Window window : windows) {
            if (!(window instanceof ContainerBlockWindow containerBlockWindow)) {
                continue;
            }
            String blockId = containerBlockWindow.getBlockType() != null
                    ? containerBlockWindow.getBlockType().getId()
                    : "unknown";
            if (!isChestWindow(containerBlockWindow)) {
                continue;
            }
            if (!isWorldLootContainer(player, containerBlockWindow)) {
                continue;
            }

            String chestKey = chestKey(player, containerBlockWindow);
            if (ROLLED_CHESTS.contains(chestKey)) {
                if (LOGGED_SKIPPED_CHESTS.add(chestKey)) {
                    log("Chest already rolled: chest=" + chestKey + ", blockId=" + blockId + ", source=" + source);
                }
                continue;
            }

            RollResult result = applyLootSocketsToContainer(containerBlockWindow.getItemContainer());
            if (result.changedCount > 0 || result.injectedCount > 0) {
                // Force this window to rebuild so tooltip packet adapters see updated chest items.
                refreshChestWindow(player, containerBlockWindow);
            }

            // Mark chest as rolled once loot has actually materialized in container.
            // This avoids repeatedly scanning empty (not-yet-generated) containers while
            // preventing later player-deposited equipment from being treated as world loot.
            if (result.nonEmptyLootCount > 0) {
                if (ROLLED_CHESTS.add(chestKey)) {
                    log("Chest rolled: chest=" + chestKey
                            + ", blockId=" + blockId
                            + ", source=" + source
                            + ", lootSlots=" + result.nonEmptyLootCount
                            + ", eligibleLoot=" + result.eligibleCount
                            + ", socketedLoot=" + result.changedCount
                            + ", injectedLoot=" + result.injectedCount
                            + ", foundLoot=[" + result.foundLoot + "]");
                }
                LOGGED_SKIPPED_CHESTS.remove(chestKey);
            }
            changed += result.changedCount;
        }
        return changed;
    }

    private static void refreshChestWindow(Player player, ContainerBlockWindow window) {
        if (player == null || window == null || player.getWindowManager() == null) {
            return;
        }
        try {
            player.getWindowManager().markWindowChanged(window.getId());
            player.getWindowManager().updateWindows();
            OpenGuiListener.scanAndRegisterTooltipsForContainer(window.getItemContainer());
        } catch (Throwable ignored) {
            // Best effort: roll should still succeed even if client window refresh fails.
        }
        try {
            if (player.getWorld() != null) {
                player.getWorld().execute(() -> {
                    try {
                        if (player.getWindowManager() != null) {
                            player.getWindowManager().markWindowChanged(window.getId());
                            player.getWindowManager().updateWindows();
                            OpenGuiListener.scanAndRegisterTooltipsForContainer(window.getItemContainer());
                        }
                    } catch (Throwable ignored) {
                        // Best effort.
                    }
                });
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static RollResult applyLootSocketsToContainer(ItemContainer container) {
        if (container == null) {
            return RollResult.EMPTY;
        }

        int changedCount = 0;
        int eligibleCount = 0;
        int nonEmptyLootCount = 0;
        int injectedCount = 0;
        Set<String> foundLootIds = new LinkedHashSet<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            nonEmptyLootCount++;
            if (!isEquipment(stack)) {
                continue;
            }
            eligibleCount++;
            String itemId = stack.getItemId();
            if (itemId != null && !itemId.isBlank()) {
                foundLootIds.add(itemId);
            }
            ItemStack updated = LootSocketRoller.maybeSocketizeLootStack(stack, LootSocketRoller.LootSource.CHEST);
            if (updated == null || updated == stack) {
                continue;
            }
            container.setItemStackForSlot(slot, updated);
            changedCount++;
        }

        // Inject additional configured loot only after chest loot has materialized.
        if (nonEmptyLootCount > 0) {
            Map<String, Integer> injected = LootInjectionUtils.injectByRules(container, CHEST_LOOT_INJECTION_RULES);
            for (Map.Entry<String, Integer> entry : injected.entrySet()) {
                String itemId = entry.getKey();
                Integer qty = entry.getValue();
                if (itemId == null || itemId.isBlank() || qty == null || qty <= 0) {
                    continue;
                }
                injectedCount += qty;
                foundLootIds.add(itemId + " x" + qty);
            }
        }

        return new RollResult(eligibleCount, changedCount, nonEmptyLootCount, injectedCount, String.join(", ", foundLootIds));
    }

    private static boolean isEquipment(ItemStack stack) {
        return ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack);
    }

    private static boolean isChestWindow(ContainerBlockWindow window) {
        if (window == null) {
            return false;
        }
        BlockType blockType = window.getBlockType();
        if (blockType == null || blockType.getId() == null) {
            return false;
        }

        String lowerId = blockType.getId().toLowerCase(Locale.ROOT);
        return lowerId.contains("treasure") || lowerId.contains("chest");
    }

    private static boolean isWorldLootContainer(Player player, ContainerBlockWindow window) {
        if (player == null || player.getWorld() == null || window == null) {
            return false;
        }
        try {
            String blockId = window.getBlockType() != null ? window.getBlockType().getId() : null;
            String normalizedBlockId = normalizeBlockId(blockId);
            if (matchesGeneratedStructureChestId(blockId, normalizedBlockId)) {
                return true;
            }

            Ref<ChunkStore> ref = BlockModule.getBlockEntity(player.getWorld(), window.getX(), window.getY(), window.getZ());
            if (ref == null || ref.getStore() == null) {
                return false;
            }
            BlockState state = BlockState.getBlockState(ref, ref.getStore());
            if (state == null) {
                return false;
            }

            String stateClassName = state.getClass().getName();
            if (stateClassName != null && stateClassName.endsWith(".TreasureChestState")) {
                return true;
            }

            if (!(state instanceof ItemContainerState itemContainerState)) {
                return false;
            }

            String droplist = itemContainerState.getDroplist();
            boolean hasDroplist = droplist != null && !droplist.isBlank();
            boolean isCustom = isCustomContainer(itemContainerState);

            // Heuristic:
            // world-generated loot containers normally have a droplist and are not custom.
            // player-placed containers are usually custom and/or have no droplist.
            return hasDroplist && !isCustom;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean matchesGeneratedStructureChestId(String rawBlockId, String normalizedBlockId) {
        if (rawBlockId == null || rawBlockId.isBlank()) {
            return false;
        }
        String rawLower = rawBlockId.toLowerCase(Locale.ROOT);
        if (!rawLower.startsWith("*")) {
            return false;
        }
        if (!rawLower.contains("state_definitions")) {
            return false;
        }
        if (!rawLower.contains("chest")) {
            return false;
        }
        return matchesWorldLootBlockIdHint(normalizedBlockId);
    }

    private static String normalizeBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "";
        }
        String normalized = blockId.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace("_state_definitions_closewindow", "");
        normalized = normalized.replace("_state_definitions_openwindow", "");
        normalized = normalized.replace("_closewindow", "");
        normalized = normalized.replace("_openwindow", "");
        return normalized;
    }

    private static boolean matchesWorldLootBlockIdHint(String normalizedBlockId) {
        if (normalizedBlockId == null || normalizedBlockId.isBlank()) {
            return false;
        }
        for (String hint : WORLD_LOOT_BLOCK_ID_HINTS) {
            if (hint != null && !hint.isBlank() && normalizedBlockId.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCustomContainer(ItemContainerState state) {
        if (state == null) {
            return false;
        }
        try {
            if (itemContainerCustomField == null) {
                itemContainerCustomField = ItemContainerState.class.getDeclaredField("custom");
                itemContainerCustomField.setAccessible(true);
            }
            return itemContainerCustomField.getBoolean(state);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String chestKey(Player player, ContainerBlockWindow window) {
        String worldName = player != null && player.getWorld() != null ? player.getWorld().getName() : "unknown";
        return worldName + "|" + window.getX() + "|" + window.getY() + "|" + window.getZ();
    }

    private static void log(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        LOGGER.info("[WORLD_CHEST_SOCKET] " + message);
    }

    private static final class RollResult {
        private static final RollResult EMPTY = new RollResult(0, 0, 0, 0, "");
        private final int eligibleCount;
        private final int changedCount;
        private final int nonEmptyLootCount;
        private final int injectedCount;
        private final String foundLoot;

        private RollResult(int eligibleCount, int changedCount, int nonEmptyLootCount, int injectedCount, String foundLoot) {
            this.eligibleCount = eligibleCount;
            this.changedCount = changedCount;
            this.nonEmptyLootCount = nonEmptyLootCount;
            this.injectedCount = injectedCount;
            this.foundLoot = foundLoot == null ? "" : foundLoot;
        }
    }
}
