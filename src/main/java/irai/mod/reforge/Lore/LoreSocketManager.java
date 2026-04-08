package irai.mod.reforge.Lore;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Config.LoreConfig;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreEffectType;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Util.MetadataKeys;

/**
 * Helpers for reading/writing lore socket metadata and leveling.
 */
public final class LoreSocketManager {
    private static LoreConfig config = new LoreConfig();
    private static final String[] LORE_WEAPON_EXCLUDE_TOKENS = {
            "shield",
            "buckler",
            "arrow",
            "bolt",
            "ammo",
            "ammunition",
            "projectile",
            "bomb",
            "tnt",
            "dynamite",
            "explosive",
            "mine",
            "grenade",
            "rocket"
    };
    private static final LoreEffectType[] EFFECT_TIER_1 = new LoreEffectType[] {
            LoreEffectType.HEAL_SELF,
            LoreEffectType.HEAL_DEFENDER,
            LoreEffectType.HEAL_SELF_OVER_TIME,
            LoreEffectType.HEAL_AREA,
            LoreEffectType.HEAL_AREA_OVER_TIME,
            LoreEffectType.APPLY_SLOW,
            LoreEffectType.APPLY_WEAKNESS,
            LoreEffectType.APPLY_BLIND
    };
    private static final LoreEffectType[] EFFECT_TIER_2 = new LoreEffectType[] {
            LoreEffectType.APPLY_HASTE,
            LoreEffectType.APPLY_SHIELD,
            LoreEffectType.APPLY_INVISIBLE,
            LoreEffectType.LIFESTEAL
    };
    private static final LoreEffectType[] EFFECT_TIER_3 = new LoreEffectType[] {
            LoreEffectType.DAMAGE_TARGET,
            LoreEffectType.DAMAGE_ATTACKER,
            LoreEffectType.APPLY_BLEED,
            LoreEffectType.APPLY_POISON
    };
    private static final LoreEffectType[] EFFECT_TIER_4 = new LoreEffectType[] {
            LoreEffectType.APPLY_BURN,
            LoreEffectType.APPLY_SHOCK,
            LoreEffectType.APPLY_FREEZE,
            LoreEffectType.APPLY_ROOT
    };
    private static final LoreEffectType[] EFFECT_TIER_5 = new LoreEffectType[] {
            LoreEffectType.APPLY_STUN,
            LoreEffectType.APPLY_FEAR,
            LoreEffectType.DOUBLE_CAST,
            LoreEffectType.MULTI_HIT,
            LoreEffectType.CRIT_CHARGE,
            LoreEffectType.BERSERK
    };

    private LoreSocketManager() {}

    public static void initialize(LoreConfig cfg) {
        if (cfg != null) {
            config = cfg;
        }
    }

    public static LoreConfig getConfig() {
        return config;
    }

    public static boolean isEquipment(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (hasLoreSockets(item)) {
            return true;
        }
        if (!ReforgeEquip.isWeapon(item)) {
            return false;
        }
        String itemId = item.getItemId();
        if (isExcludedLoreWeaponId(itemId)) {
            return false;
        }
        Item cfg = item.getItem();
        if (cfg != null && cfg != Item.UNKNOWN && cfg.isConsumable()) {
            return false;
        }
        return true;
    }

    public static boolean hasLoreSockets(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Integer max = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_MAX, Codec.INTEGER);
        String[] values = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_VALUES, Codec.STRING_ARRAY);
        return (max != null && max > 0) || (values != null && values.length > 0);
    }

    public static LoreSocketData getLoreSocketData(ItemStack item) {
        if (!isEquipment(item)) {
            return null;
        }

        Integer maxFromMeta = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_MAX, Codec.INTEGER);
        String[] values = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_VALUES, Codec.STRING_ARRAY);
        String[] colors = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY);
        String[] spirits = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY);
        String[] effects = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY);
        int[] levels = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY);
        int[] xp = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY);
        int[] feedTiers = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY);
        int[] locked = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_LOCKED, Codec.INT_ARRAY);

        int count = maxArrayLength(values, colors, spirits, effects, levels, xp, feedTiers, locked);
        int maxSockets = maxFromMeta != null ? Math.max(0, maxFromMeta) : Math.max(0, count);
        int socketCount = Math.max(count, maxSockets);
        if (socketCount == 0) {
            return null;
        }

        LoreSocketData data = new LoreSocketData(maxSockets);
        data.ensureSocketCount(socketCount);

        for (int i = 0; i < socketCount; i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            socket.setGemItemId(getString(values, i));
            socket.setColor(getString(colors, i));
            socket.setSpiritId(getString(spirits, i));
            socket.setEffectOverride(getString(effects, i));
            socket.setLevel(getInt(levels, i));
            socket.setXp(getInt(xp, i));
            socket.setFeedTier(getInt(feedTiers, i));
            socket.setLocked(getBool(locked, i));
        }
        return data;
    }

    public static ItemStack withLoreSocketData(ItemStack item, LoreSocketData data) {
        if (item == null || item.isEmpty() || data == null) {
            return item;
        }

        int maxSockets = Math.max(0, data.getMaxSockets());
        int socketCount = Math.max(data.getSocketCount(), maxSockets);
        if (socketCount == 0) {
            return item.withMetadata(MetadataKeys.LORE_SOCKET_MAX, Codec.INTEGER, 0)
                    .withMetadata(MetadataKeys.LORE_SOCKET_VALUES, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_LOCKED, Codec.INT_ARRAY, new int[0]);
        }

        if (data.getSocketCount() != socketCount) {
            data.ensureSocketCount(socketCount);
        }
        if (maxSockets < socketCount) {
            maxSockets = socketCount;
            data.setMaxSockets(maxSockets);
        }

        String[] values = new String[socketCount];
        String[] colors = new String[socketCount];
        String[] spirits = new String[socketCount];
        String[] effects = new String[socketCount];
        int[] levels = new int[socketCount];
        int[] xp = new int[socketCount];
        int[] feedTiers = new int[socketCount];
        int[] locked = new int[socketCount];

        for (int i = 0; i < socketCount; i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            values[i] = safeString(socket.getGemItemId());
            colors[i] = safeString(socket.getColor());
            spirits[i] = safeString(socket.getSpiritId());
            effects[i] = safeString(socket.getEffectOverride());
            levels[i] = Math.max(0, socket.getLevel());
            xp[i] = Math.max(0, socket.getXp());
            feedTiers[i] = Math.max(0, socket.getFeedTier());
            locked[i] = socket.isLocked() ? 1 : 0;
        }

        return item
                .withMetadata(MetadataKeys.LORE_SOCKET_MAX, Codec.INTEGER, maxSockets)
                .withMetadata(MetadataKeys.LORE_SOCKET_VALUES, Codec.STRING_ARRAY, values)
                .withMetadata(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY, spirits)
                .withMetadata(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY, colors)
                .withMetadata(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY, effects)
                .withMetadata(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY, levels)
                .withMetadata(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY, xp)
                .withMetadata(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY, feedTiers)
                .withMetadata(MetadataKeys.LORE_SOCKET_LOCKED, Codec.INT_ARRAY, locked);
    }

    public static ItemStack maybeRollLoreSockets(ItemStack item, boolean isChest, Random rng) {
        if (item == null || item.isEmpty() || !isEquipment(item)) {
            return item;
        }
        // Lore sockets should only roll on weapons (not armor).
        if (ReforgeEquip.isArmor(item)) {
            return item;
        }
        if (hasLoreSockets(item)) {
            return item;
        }

        double chance = isChest ? config.getChestLoreSocketChance() : config.getDropLoreSocketChance();
        if (chance <= 0.0d) {
            return item;
        }
        Random random = rng == null ? ThreadLocalRandom.current() : rng;
        if (random.nextDouble() >= chance) {
            return item;
        }

        int minSockets = Math.max(0, config.getMinLoreSockets());
        int maxSockets = Math.max(minSockets, config.getMaxLoreSockets());
        int count = minSockets == maxSockets
                ? minSockets
                : minSockets + random.nextInt((maxSockets - minSockets) + 1);

        if (count <= 0) {
            return item;
        }

        LoreSocketData data = new LoreSocketData(count);
        data.ensureSocketCount(count);
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null) {
                continue;
            }
            if (socket.getColor() == null || socket.getColor().isBlank()) {
                socket.setColor(LoreGemRegistry.pickRandomColor(random));
            }
        }
        return withLoreSocketData(item, data);
    }

    /**
     * Adds one lore socket (random color) to the item if possible.
     * Returns the original item if no changes were applied.
     */
    public static ItemStack punchRandomLoreSocket(ItemStack item, Random rng) {
        if (item == null || item.isEmpty() || !isEquipment(item)) {
            return item;
        }

        int maxAllowed = Math.max(0, config.getMaxLoreSockets());
        if (maxAllowed <= 0) {
            return item;
        }

        LoreSocketData data = getLoreSocketData(item);
        int currentMax = 0;
        if (data == null) {
            data = new LoreSocketData(1);
            data.ensureSocketCount(1);
        } else {
            currentMax = Math.max(data.getMaxSockets(), data.getSocketCount());
        }

        if (currentMax >= maxAllowed) {
            return item;
        }

        int newMax = Math.min(currentMax + 1, maxAllowed);
        data.setMaxSockets(newMax);
        data.ensureSocketCount(newMax);

        Random random = rng == null ? ThreadLocalRandom.current() : rng;
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null) {
                continue;
            }
            if (socket.getColor() == null || socket.getColor().isBlank()) {
                socket.setColor(LoreGemRegistry.pickRandomColor(random));
            }
        }

        return withLoreSocketData(item, data);
    }

    public static ItemStack syncLoreSocketColors(ItemStack item) {
        LoreSocketData data = getLoreSocketData(item);
        if (data == null) {
            return item;
        }
        if (!syncSocketColors(item, data)) {
            return item;
        }
        return withLoreSocketData(item, data);
    }

    public static boolean syncSocketColors(ItemStack item, LoreSocketData data) {
        if (item == null || item.isEmpty() || data == null) {
            return false;
        }
        var knownColors = LoreGemRegistry.getKnownColors();
        boolean hasKnown = knownColors != null && !knownColors.isEmpty();
        boolean changed = false;
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null) {
                continue;
            }
            String gemColor = LoreGemRegistry.resolveColor(socket.getGemItemId());
            String spiritId = socket.getSpiritId();
            String spiritColor = (spiritId == null || spiritId.isBlank())
                    ? null
                    : LoreGemRegistry.resolveSpiritColor(spiritId);

            if (gemColor != null && !gemColor.isBlank()
                    && spiritColor != null && !spiritColor.isBlank()
                    && !gemColor.equalsIgnoreCase(spiritColor)) {
                // Incompatible spirit assignment from earlier bug; clear it.
                socket.setSpiritId("");
                socket.setEffectOverride("");
                socket.setLevel(0);
                socket.setXp(0);
                socket.setFeedTier(0);
                socket.setLocked(false);
                changed = true;
            }
            if (gemColor != null && !gemColor.isBlank()) {
                if (!gemColor.equalsIgnoreCase(socket.getColor())) {
                    socket.setColor(gemColor.toLowerCase(Locale.ROOT));
                    changed = true;
                }
                continue;
            }

            String color = socket.getColor();
            if (color == null || color.isBlank()) {
                if (hasKnown) {
                    socket.setColor(pickStableColor(item, i, knownColors));
                    changed = true;
                }
                continue;
            }

            if (hasKnown && !isKnownColor(color, knownColors)) {
                socket.setColor(pickStableColor(item, i, knownColors));
                changed = true;
            }
        }
        boolean effectsChanged = syncSocketEffects(data);
        return changed || effectsChanged;
    }

    private static boolean syncSocketEffects(LoreSocketData data) {
        if (data == null) {
            return false;
        }
        boolean changed = false;
        Set<LoreEffectType> used = EnumSet.noneOf(LoreEffectType.class);
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
                continue;
            }
            LoreAbility ability = LoreAbilityRegistry.getAbility(socket.getSpiritId());
            if (ability == null || ability.getEffectType() == null) {
                continue;
            }

            LoreEffectType base = ability.getEffectType();
            LoreEffectType effective = resolveSocketEffectType(socket, ability);
            if (effective == null) {
                effective = base;
            }

            if (used.contains(effective)) {
                if (ability.getAbilityNameKey() != null && !ability.getAbilityNameKey().isBlank()) {
                    // Preserve signature abilities even if they repeat.
                    used.add(effective);
                    continue;
                }
                LoreEffectType override = pickAlternateEffect(base, used, socket.getSpiritId(), i);
                if (override != null && override != base) {
                    socket.setEffectOverride(override.name());
                    effective = override;
                    changed = true;
                }
            }

            if (effective == base) {
                if (socket.getEffectOverride() != null && !socket.getEffectOverride().isBlank()) {
                    socket.setEffectOverride("");
                    changed = true;
                }
            }
            used.add(effective);
        }
        return changed;
    }

    private static LoreEffectType resolveSocketEffectType(LoreSocketData.LoreSocket socket, LoreAbility ability) {
        if (socket == null) {
            return ability == null ? null : ability.getEffectType();
        }
        String overrideRaw = socket.getEffectOverride();
        if (overrideRaw != null && !overrideRaw.isBlank()) {
            LoreEffectType override = LoreEffectType.fromString(overrideRaw, null);
            if (override != null) {
                return override;
            }
        }
        return ability == null ? null : ability.getEffectType();
    }

    private static LoreEffectType pickAlternateEffect(LoreEffectType base,
                                                      Set<LoreEffectType> used,
                                                      String spiritId,
                                                      int slotIndex) {
        if (base == null || used == null) {
            return null;
        }
        LoreEffectType[] pool = getEffectTierPool(base);
        if (pool == null || pool.length == 0) {
            return null;
        }
        int hash = Math.abs(((spiritId == null ? "" : spiritId) + ":" + slotIndex).hashCode());
        int start = Math.floorMod(hash, pool.length);
        for (int i = 0; i < pool.length; i++) {
            LoreEffectType candidate = pool[(start + i) % pool.length];
            if (candidate == null || candidate == base) {
                continue;
            }
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static LoreEffectType[] getEffectTierPool(LoreEffectType effectType) {
        if (effectType == null) {
            return EFFECT_TIER_3;
        }
        return switch (effectType) {
            case HEAL_SELF, HEAL_DEFENDER, HEAL_SELF_OVER_TIME, HEAL_AREA, HEAL_AREA_OVER_TIME,
                    APPLY_SLOW, APPLY_WEAKNESS, APPLY_BLIND -> EFFECT_TIER_1;
            case APPLY_HASTE, APPLY_SHIELD, APPLY_INVISIBLE, LIFESTEAL -> EFFECT_TIER_2;
            case DAMAGE_TARGET, DAMAGE_ATTACKER, APPLY_BLEED, APPLY_POISON, DRAIN_LIFE -> EFFECT_TIER_3;
            case APPLY_BURN, APPLY_SHOCK, APPLY_FREEZE, APPLY_ROOT -> EFFECT_TIER_4;
            case APPLY_STUN, APPLY_FEAR, DOUBLE_CAST, MULTI_HIT, CRIT_CHARGE, BERSERK,
                    SUMMON_WOLF_PACK, CHARGE_ATTACK, OMNISLASH, OCTASLASH, VORTEXSTRIKE, PUMMEL, BLOOD_RUSH,
                    CAUSTIC_FINALE, SHRAPNEL_FINALE, BURN_FINALE -> EFFECT_TIER_5;
        };
    }

    private static boolean isKnownColor(String color, java.util.List<String> known) {
        if (color == null || color.isBlank() || known == null || known.isEmpty()) {
            return false;
        }
        for (String entry : known) {
            if (entry != null && entry.equalsIgnoreCase(color)) {
                return true;
            }
        }
        return false;
    }

    private static String pickStableColor(ItemStack item, int index, java.util.List<String> colors) {
        if (colors == null || colors.isEmpty()) {
            return "unknown";
        }
        String seed = safeString(item.getItemId()) + ":" + index;
        int idx = Math.floorMod(seed.hashCode(), colors.size());
        return safeString(colors.get(idx));
    }

    public static boolean isLoreGem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        String itemId = item.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return LoreGemRegistry.resolveColor(itemId) != null;
    }

    public static String resolveGemColor(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return LoreGemRegistry.resolveColor(item.getItemId());
    }

    public static boolean addProcXp(LoreSocketData data, int slotIndex) {
        if (data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
            return false;
        }

        int maxLevel = Math.max(1, config.getMaxLevel());
        if (socket.getLevel() >= maxLevel) {
            return false;
        }
        if (needsFeed(socket)) {
            return false;
        }

        int beforeXp = socket.getXp();
        int beforeLevel = socket.getLevel();
        socket.setXp(beforeXp + Math.max(1, config.getXpPerProc()));

        boolean leveled = applyStoredXp(socket);
        return leveled || socket.getXp() != beforeXp || socket.getLevel() != beforeLevel;
    }

    public static boolean applyStoredXp(LoreSocketData.LoreSocket socket) {
        if (socket == null || !socket.hasSpirit()) {
            return false;
        }
        int maxLevel = Math.max(1, config.getMaxLevel());
        if (socket.getLevel() >= maxLevel) {
            return false;
        }
        boolean leveled = false;
        while (socket.getLevel() < maxLevel) {
            if (needsFeed(socket)) {
                break;
            }
            int xpNeeded = getXpRequired(socket.getLevel());
            if (socket.getXp() < xpNeeded) {
                break;
            }
            socket.setXp(socket.getXp() - xpNeeded);
            socket.setLevel(socket.getLevel() + 1);
            leveled = true;
        }
        return leveled;
    }

    public static boolean needsFeed(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return false;
        }
        int interval = Math.max(1, config.getFeedInterval());
        int maxLevel = Math.max(1, config.getMaxLevel());
        if (socket.getLevel() >= maxLevel) {
            return false;
        }
        int nextGateLevel = interval * (socket.getFeedTier() + 1);
        return socket.getLevel() >= nextGateLevel;
    }

    public static int getFeedCost(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return 0;
        }
        int base = Math.max(0, config.getFeedBase());
        int multiplier = Math.max(1, config.getFeedMultiplier());
        int tier = Math.max(0, socket.getFeedTier());
        int cost = base;
        for (int i = 0; i < tier; i++) {
            cost *= multiplier;
        }
        return Math.max(0, cost);
    }

    public static boolean tryFeed(Player player, LoreSocketData data, int slotIndex) {
        if (player == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || !needsFeed(socket)) {
            return false;
        }
        int cost = getFeedCost(socket);
        if (cost <= 0) {
            socket.setFeedTier(socket.getFeedTier() + 1);
            applyStoredXp(socket);
            return true;
        }

        String[] feedIds = config.getFeedItemIds();
        if (feedIds == null || feedIds.length == 0) {
            return false;
        }

        if (!consumeFromInventory(player, feedIds, cost)) {
            return false;
        }
        socket.setFeedTier(socket.getFeedTier() + 1);
        applyStoredXp(socket);
        return true;
    }

    public static boolean tryClearSpirit(Player player, LoreSocketData data, int slotIndex) {
        return tryClearSpirit(player, data, slotIndex, false);
    }

    public static boolean tryClearSpirit(Player player, LoreSocketData data, int slotIndex, boolean allowInventory) {
        if (player == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || !socket.hasSpirit()) {
            return false;
        }
        String[] clearIds = config.getClearItemIds();
        if (clearIds == null || clearIds.length == 0) {
            return false;
        }
        boolean consumed = allowInventory
                ? consumeFromInventory(player, clearIds, 1)
                : consumeFromSelectedHotbar(player, clearIds, 1);
        if (!consumed) {
            return false;
        }
        clearSocketSpirit(socket);
        return true;
    }

    public static boolean isHoldingClearItem(Player player) {
        if (player == null) {
            return false;
        }
        String[] clearIds = config.getClearItemIds();
        if (clearIds == null || clearIds.length == 0) {
            return false;
        }
        ItemStack held = PlayerInventoryUtils.getSelectedHotbarItem(player);
        if (held == null || held.isEmpty()) {
            return false;
        }
        return matchesAnyId(held.getItemId(), clearIds);
    }

    private static void clearSocketSpirit(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return;
        }
        socket.setSpiritId("");
        socket.setEffectOverride("");
        socket.setLevel(0);
        socket.setXp(0);
        socket.setFeedTier(0);
        socket.setLocked(false);
    }

    private static boolean consumeFromInventory(Player player, String[] itemIds, int amount) {
        if (player == null || player.getInventory() == null) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();
        for (String itemId : itemIds) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            if (consumeFromContainer(hotbar, itemId, amount)) {
                return true;
            }
            if (consumeFromContainer(storage, itemId, amount)) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromSelectedHotbar(Player player, String[] itemIds, int amount) {
        if (player == null || player.getInventory() == null || itemIds == null || itemIds.length == 0) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null) {
            return false;
        }
        short slot = PlayerInventoryUtils.getSelectedHotbarSlot(player);
        if (slot < 0 || slot >= hotbar.getCapacity()) {
            return false;
        }
        ItemStack stack = hotbar.getItemStack(slot);
        if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
            return false;
        }
        if (!matchesAnyId(stack.getItemId(), itemIds)) {
            return false;
        }
        if (stack.getQuantity() < amount) {
            return false;
        }
        hotbar.removeItemStackFromSlot(slot, amount, false, false);
        return true;
    }

    private static boolean matchesAnyId(String itemId, String[] itemIds) {
        if (itemId == null || itemIds == null || itemIds.length == 0) {
            return false;
        }
        String target = itemId.trim().toLowerCase(Locale.ROOT);
        for (String id : itemIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (target.equals(id.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromContainer(ItemContainer container, String itemId, int amount) {
        if (container == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return false;
        }
        String target = itemId.trim().toLowerCase(Locale.ROOT);
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty() || stack.getItemId() == null) {
                continue;
            }
            if (!stack.getItemId().trim().toLowerCase(Locale.ROOT).equals(target)) {
                continue;
            }
            if (stack.getQuantity() < amount) {
                continue;
            }
            container.removeItemStackFromSlot(slot, amount, false, false);
            return true;
        }
        return false;
    }

    private static int getXpRequired(int currentLevel) {
        int level = Math.max(1, currentLevel);
        int base = Math.max(1, config.getBaseXpPerLevel());
        double growth = Math.max(0.0d, config.getXpGrowthPerLevel());
        double raw = base * Math.pow(level, growth);
        return (int) Math.max(1, Math.round(raw));
    }

    public static int getXpRequiredForLevel(int currentLevel) {
        return getXpRequired(currentLevel);
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static boolean isExcludedLoreWeaponId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return containsAny(lower, LORE_WEAPON_EXCLUDE_TOKENS);
    }

    private static boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String getString(String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return "";
        }
        String value = values[index];
        return value == null ? "" : value;
    }

    private static int getInt(int[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return 0;
        }
        return values[index];
    }

    private static boolean getBool(int[] values, int index) {
        return getInt(values, index) > 0;
    }

    private static int maxArrayLength(String[] a, String[] b, String[] c, String[] d,
                                      int[] e, int[] f, int[] g, int[] h) {
        int max = 0;
        if (a != null) max = Math.max(max, a.length);
        if (b != null) max = Math.max(max, b.length);
        if (c != null) max = Math.max(max, c.length);
        if (d != null) max = Math.max(max, d.length);
        if (e != null) max = Math.max(max, e.length);
        if (f != null) max = Math.max(max, f.length);
        if (g != null) max = Math.max(max, g.length);
        if (h != null) max = Math.max(max, h.length);
        return max;
    }
}
