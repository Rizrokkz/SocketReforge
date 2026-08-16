package irai.mod.reforge.Common;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

public final class WeaponAffinityAppearanceState {
    private static final Field ENTITY_STAT_VALUES_FIELD = findEntityStatValuesField();
    private static volatile int primaryVisualStatIndex = Integer.MIN_VALUE;
    private static volatile int secondaryVisualStatIndex = Integer.MIN_VALUE;

    private WeaponAffinityAppearanceState() {}

    public static void refresh(Player player) {
        if (player == null || player.getUuid() == null) {
            return;
        }
        try {
            World world = player.getWorld();
            if (world == null) {
                return;
            }
            Ref<EntityStore> playerRef = world.getEntityRef(player.getUuid());
            if (playerRef != null) {
                refresh(player, playerRef);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void refreshDeferred(Player player) {
        if (player == null || player.getUuid() == null) {
            return;
        }
        try {
            World world = player.getWorld();
            if (world != null) {
                world.execute(() -> refresh(player));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void refresh(Player player, Ref<EntityStore> playerRef) {
        if (player == null || playerRef == null || playerRef.getStore() == null) {
            return;
        }
        refresh(player, playerRef, PlayerInventoryUtils.getHeldItem(player), getUtilityItem(player));
    }

    /**
     * Refreshes the appearance state using an explicitly resolved primary weapon stack.
     * <p>
     * Use this from {@code InventorySetActiveSlotEvent} handlers: the event carries the new slot
     * index, and reading the item at that slot directly avoids the appearance stat lagging one
     * weapon behind (e.g. an Ice weapon still showing the previously held Fire weapon's visuals).
     */
    public static void refresh(Player player, Ref<EntityStore> playerRef, ItemStack primaryWeapon) {
        refresh(player, playerRef, primaryWeapon, getUtilityItem(player));
    }

    /**
     * Refreshes the appearance state using explicitly resolved primary and secondary (utility /
     * off-hand) weapon stacks.
     */
    public static void refresh(Player player,
                               Ref<EntityStore> playerRef,
                               ItemStack primaryWeapon,
                               ItemStack secondaryWeapon) {
        if (player == null || playerRef == null || playerRef.getStore() == null) {
            return;
        }
        Essence.Type primaryAffinity = isVisualWeapon(primaryWeapon) ? resolveDominantAffinity(primaryWeapon) : null;
        Essence.Type secondaryAffinity = resolveSecondaryAffinity(primaryWeapon, secondaryWeapon, primaryAffinity);
        updateAppearanceStats(playerRef.getStore(), playerRef, primaryAffinity, secondaryAffinity);
    }

    public static void clear(Player player, Ref<EntityStore> playerRef) {
        if (playerRef == null || playerRef.getStore() == null) {
            return;
        }
        updateAppearanceStats(playerRef.getStore(), playerRef, null, null);
    }

    private static void updateAppearanceStats(Store<EntityStore> store,
                                              Ref<EntityStore> playerRef,
                                              Essence.Type primaryAffinity,
                                              Essence.Type secondaryAffinity) {
        if (store == null || playerRef == null) {
            return;
        }
        try {
            EntityStatMap statMap = store.ensureAndGetComponent(playerRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                return;
            }
            setVisualStat(statMap, resolvePrimaryVisualStatIndex(),
                    WeaponAffinityAppearanceInjector.statValueForAffinity(primaryAffinity));
            setVisualStat(statMap, resolveSecondaryVisualStatIndex(),
                    WeaponAffinityAppearanceInjector.statValueForAffinity(secondaryAffinity));
            statMap.update();
        } catch (Throwable ignored) {
        }
    }

    private static void setVisualStat(EntityStatMap statMap, int statIndex, int value) {
        if (statMap == null || statIndex < 0) {
            return;
        }
        ensureVisualStatValue(statMap, statIndex);
        if (statMap.get(statIndex) != null) {
            statMap.setStatValue(Predictable.ALL, statIndex, value);
        }
    }

    private static void ensureVisualStatValue(EntityStatMap statMap, int statIndex) {
        if (statMap == null || statIndex < 0 || statMap.get(statIndex) != null || ENTITY_STAT_VALUES_FIELD == null) {
            return;
        }
        try {
            EntityStatType statType = EntityStatType.getAssetMap().getAsset(statIndex);
            if (statType == null) {
                return;
            }
            EntityStatValue[] values = (EntityStatValue[]) ENTITY_STAT_VALUES_FIELD.get(statMap);
            int requiredSize = statIndex + 1;
            if (values == null) {
                values = new EntityStatValue[requiredSize];
            } else if (values.length < requiredSize) {
                values = Arrays.copyOf(values, requiredSize);
            }
            if (values[statIndex] == null) {
                values[statIndex] = new EntityStatValue(statIndex, statType);
                ENTITY_STAT_VALUES_FIELD.set(statMap, values);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findEntityStatValuesField() {
        try {
            Field field = EntityStatMap.class.getDeclaredField("values");
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int resolvePrimaryVisualStatIndex() {
        int cached = primaryVisualStatIndex;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        cached = resolveStatIndex(WeaponAffinityAppearanceInjector.PRIMARY_STAT_ID);
        primaryVisualStatIndex = cached;
        return cached;
    }

    private static int resolveSecondaryVisualStatIndex() {
        int cached = secondaryVisualStatIndex;
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        cached = resolveStatIndex(WeaponAffinityAppearanceInjector.SECONDARY_STAT_ID);
        secondaryVisualStatIndex = cached;
        return cached;
    }

    private static int resolveStatIndex(String statId) {
        try {
            var assetMap = EntityStatType.getAssetMap();
            return assetMap == null || statId == null ? -1 : assetMap.getIndex(statId);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static Essence.Type resolveSecondaryAffinity(ItemStack primaryWeapon,
                                                         ItemStack utility,
                                                         Essence.Type primaryAffinity) {
        if (utility != null && !utility.isEmpty()) {
            if (!isVisualWeapon(utility)) {
                return null;
            }
            Essence.Type utilityAffinity = resolveDominantAffinity(utility);
            if (utilityAffinity != null) {
                return utilityAffinity;
            }
            return primaryAffinity != null && (isDualDaggerItem(primaryWeapon) || isDualDaggerItem(utility))
                    ? primaryAffinity
                    : null;
        }
        return primaryAffinity != null && isDualDaggerItem(primaryWeapon) ? primaryAffinity : null;
    }

    private static boolean isVisualWeapon(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && !isShieldLikeItem(stack)
                && !ItemTypeUtils.isArmor(stack)
                && ItemTypeUtils.isWeapon(stack);
    }

    private static ItemStack getUtilityItem(Player player) {
        if (player == null || player.getInventory() == null) {
            return null;
        }
        try {
            Inventory inventory = player.getInventory();
            return inventory.getUtilityItem();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isDualDaggerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
            return false;
        }
        String itemId = stack.getItemId().toLowerCase(java.util.Locale.ROOT);
        return itemId.contains("dagger") || itemId.contains("daggers");
    }

    private static boolean isShieldLikeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
            return false;
        }
        String itemId = stack.getItemId().toLowerCase(java.util.Locale.ROOT);
        return itemId.contains("shield") || itemId.contains("buckler");
    }

    private static Essence.Type resolveDominantAffinity(ItemStack weapon) {
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return null;
        }

        EnumMap<Essence.Type, Double> weights = new EnumMap<>(Essence.Type.class);
        for (Socket socket : socketData.getSockets()) {
            Essence.Type type = SocketManager.getSocketAffinityType(socket);
            if (type == null) {
                continue;
            }
            double weight = SocketManager.isGreaterEssenceId(socket.getEssenceId()) ? 1.5d : 1.0d;
            weights.put(type, weights.getOrDefault(type, 0.0d) + weight);
        }

        Essence.Type bestType = null;
        double bestWeight = 0.0d;
        for (Map.Entry<Essence.Type, Double> entry : weights.entrySet()) {
            if (entry.getValue() > bestWeight) {
                bestType = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return bestType;
    }
}
