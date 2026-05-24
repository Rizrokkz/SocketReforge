package irai.mod.reforge.Lore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;

/**
 * Buffers lore metadata updates for held items so we do not replace the live
 * in-hand stack while the player is actively using it. Replacing the held
 * stack can reset ranged weapon state such as auto-fire chains or loaded ammo.
 */
public final class LoreHeldItemUpdateManager {
    private static final Map<UUID, PendingHeldItemUpdate> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RECENT_BLOCKING_UNTIL = new ConcurrentHashMap<>();
    private static final long INTERACTION_BUFFER_GRACE_MS = 900L;

    private LoreHeldItemUpdateManager() {}

    public static ItemStack resolveHeldItem(Player player, PlayerInventoryUtils.HeldItemContext ctx) {
        if (ctx == null || !ctx.isValid()) {
            return null;
        }
        PendingHeldItemUpdate pending = getPending(player, ctx);
        if (pending == null || pending.data == null) {
            return ctx.getItemStack();
        }
        ItemStack current = ctx.getItemStack();
        if (current == null || current.isEmpty()) {
            return current;
        }
        return LoreSocketManager.withLoreSocketData(current, copyData(pending.data));
    }

    public static void applyOrQueue(Store<EntityStore> store,
                                    Ref<EntityStore> playerRef,
                                    Player player,
                                    PlayerInventoryUtils.HeldItemContext ctx,
                                    ItemStack updated) {
        if (player == null || ctx == null || updated == null || updated.isEmpty()) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            applyImmediately(player, ctx, updated);
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(updated);
        if (data == null) {
            applyImmediately(player, ctx, updated);
            PENDING.remove(playerId);
            return;
        }

        PENDING.put(playerId, new PendingHeldItemUpdate(
                ctx.getSectionId(),
                ctx.getSlot(),
                safeItemId(ctx.getItemStack()),
                copyData(data)));
    }

    public static void flushPending(Store<EntityStore> store,
                                    Ref<EntityStore> playerRef,
                                    Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }
        PendingHeldItemUpdate pending = PENDING.get(playerId);
        if (pending == null || pending.data == null) {
            return;
        }
        if (hasBlockingActiveInteraction(store, playerRef)) {
            return;
        }

        PlayerInventoryUtils.HeldItemContext currentCtx = PlayerInventoryUtils.getHeldItemContext(player);
        if (matches(pending, currentCtx)) {
            // Keep the metadata buffered while this exact item is still in hand.
            // Writing the stack back into the active slot can reset internal
            // ranged-weapon state even when the lore change is only XP.
            return;
        }

        ItemContainer container = resolveContainer(player, pending.sectionId);
        if (container != null && pending.slot >= 0 && pending.slot < container.getCapacity()) {
            ItemStack slotItem = safeGetItem(container, pending.slot);
            if (itemIdsMatch(pending.baseItemId, safeItemId(slotItem))) {
                ItemStack merged = mergeLoreMetadata(slotItem, pending.data);
                if (merged != null && !merged.isEmpty()) {
                    container.setItemStackForSlot(pending.slot, merged);
                }
                PENDING.remove(playerId);
                return;
            }
        }

        PENDING.remove(playerId);
    }

    private static PendingHeldItemUpdate getPending(Player player, PlayerInventoryUtils.HeldItemContext ctx) {
        if (player == null || ctx == null) {
            return null;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return null;
        }
        PendingHeldItemUpdate pending = PENDING.get(playerId);
        return matches(pending, ctx) ? pending : null;
    }

    private static boolean matches(PendingHeldItemUpdate pending, PlayerInventoryUtils.HeldItemContext ctx) {
        if (pending == null || ctx == null) {
            return false;
        }
        if (pending.sectionId != ctx.getSectionId() || pending.slot != ctx.getSlot()) {
            return false;
        }
        return itemIdsMatch(pending.baseItemId, safeItemId(ctx.getItemStack()));
    }

    private static boolean hasBlockingActiveInteraction(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        UUID playerId = resolvePlayerId(store, playerRef);
        long now = System.currentTimeMillis();
        if (isWithinGrace(playerId, now)) {
            return true;
        }
        if (store == null || playerRef == null) {
            return false;
        }
        InteractionModule module;
        try {
            module = InteractionModule.get();
        } catch (Throwable ignored) {
            return false;
        }
        if (module == null) {
            return false;
        }
        InteractionManager manager;
        try {
            manager = store.getComponent(playerRef, module.getInteractionManagerComponent());
        } catch (Throwable ignored) {
            return false;
        }
        if (manager == null) {
            return false;
        }
        var chains = manager.getChains();
        if (chains == null || chains.isEmpty()) {
            return false;
        }
        for (var chain : chains.values()) {
            if (chain == null) {
                continue;
            }
            if (isBlockingType(chain.getType()) || isBlockingType(chain.getBaseType())) {
                rememberRecentBlocking(playerId, now);
                return true;
            }
        }
        return false;
    }

    private static UUID resolvePlayerId(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null) {
            return null;
        }
        try {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            return player == null ? null : player.getUuid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isWithinGrace(UUID playerId, long now) {
        if (playerId == null) {
            return false;
        }
        Long until = RECENT_BLOCKING_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        if (until > now) {
            return true;
        }
        RECENT_BLOCKING_UNTIL.remove(playerId, until);
        return false;
    }

    private static void rememberRecentBlocking(UUID playerId, long now) {
        if (playerId == null) {
            return;
        }
        RECENT_BLOCKING_UNTIL.put(playerId, now + INTERACTION_BUFFER_GRACE_MS);
    }

    private static boolean isBlockingType(InteractionType type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return "Primary".equals(name)
                || "Secondary".equals(name)
                || "Ability1".equals(name)
                || "Ability2".equals(name)
                || "Ability3".equals(name)
                || "Use".equals(name);
    }

    private static void applyImmediately(Player player,
                                         PlayerInventoryUtils.HeldItemContext ctx,
                                         ItemStack updated) {
        if (player == null || ctx == null || updated == null || updated.isEmpty()) {
            return;
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(updated);
        ItemContainer container = ctx.getContainer();
        short slot = ctx.getSlot();
        if (container != null && slot >= 0 && slot < container.getCapacity()) {
            ItemStack current = safeGetItem(container, slot);
            ItemStack merged = data == null ? updated : mergeLoreMetadata(current, data);
            container.setItemStackForSlot(slot, merged == null ? updated : merged);
            return;
        }
        ItemStack current = PlayerInventoryUtils.getSelectedHotbarItem(player);
        ItemStack merged = data == null ? updated : mergeLoreMetadata(current, data);
        PlayerInventoryUtils.setSelectedHotbarItem(player, merged == null ? updated : merged);
    }

    private static ItemStack mergeLoreMetadata(ItemStack base, LoreSocketData data) {
        if (base == null || base.isEmpty() || data == null) {
            return base;
        }
        return LoreSocketManager.withLoreSocketData(base, copyData(data));
    }

    private static LoreSocketData copyData(LoreSocketData source) {
        if (source == null) {
            return null;
        }
        LoreSocketData copy = new LoreSocketData(source.getMaxSockets());
        copy.ensureSocketCount(source.getSocketCount());
        for (int i = 0; i < source.getSocketCount(); i++) {
            LoreSocketData.LoreSocket src = source.getSocket(i);
            LoreSocketData.LoreSocket dst = copy.getSocket(i);
            if (src == null || dst == null) {
                continue;
            }
            dst.setGemItemId(src.getGemItemId());
            dst.setColor(src.getColor());
            dst.setSpiritId(src.getSpiritId());
            dst.setEffectOverride(src.getEffectOverride());
            dst.setLevel(src.getLevel());
            dst.setXp(src.getXp());
            dst.setFeedTier(src.getFeedTier());
            dst.setLocked(src.isLocked());
        }
        return copy;
    }

    private static ItemContainer resolveContainer(Player player, int sectionId) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        Inventory inventory = player.getInventory();
        try {
            if (sectionId == Inventory.HOTBAR_SECTION_ID) {
                return inventory.getHotbar();
            }
            if (sectionId == Inventory.TOOLS_SECTION_ID) {
                return inventory.getTools();
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static ItemStack safeGetItem(ItemContainer container, short slot) {
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            return null;
        }
        try {
            return container.getItemStack(slot);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
            return "";
        }
        return stack.getItemId();
    }

    private static boolean itemIdsMatch(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private static final class PendingHeldItemUpdate {
        private final int sectionId;
        private final short slot;
        private final String baseItemId;
        private final LoreSocketData data;

        private PendingHeldItemUpdate(int sectionId,
                                      short slot,
                                      String baseItemId,
                                      LoreSocketData data) {
            this.sectionId = sectionId;
            this.slot = slot;
            this.baseItemId = baseItemId == null ? "" : baseItemId;
            this.data = data;
        }
    }
}
