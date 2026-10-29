package irai.mod.reforge.Lore;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import irai.mod.reforge.Config.LoreConfig;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreEffectType;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSoulstoneUtils;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.MetadataKeys;

/**
 * Helpers for reading/writing lore socket metadata and leveling.
 */
public final class LoreSocketManager {
    public static final String RUNE_DAMAGE_FEED_ITEM_ID = "Ingredient_Gobliterator_Core";
    public static final String RUNE_MANA_FEED_ITEM_ID = "Ingredient_Enkindled_Resource";
    public static final String OCTASLASH_RUNE_ITEM_ID = "Rune_Octaslash";
    public static final String BLOOD_RUSH_RUNE_ITEM_ID = "Rune_BloodRush";
    public static final String PUMMEL_RUNE_ITEM_ID = "Rune_Pummel";
    public static final String WOLF_PACK_RUNE_1_ITEM_ID = "Rune_WolfPack_1";
    public static final String WOLF_PACK_RUNE_2_ITEM_ID = "Rune_WolfPack_2";
    public static final String WOLF_PACK_RUNE_3_ITEM_ID = "Rune_WolfPack_3";
    public static final String OMNISLASH_RUNE_3_ITEM_ID = "Rune_Omnislash_3";
    public static final String OMNISLASH_RUNE_6_ITEM_ID = "Rune_Omnislash_6";
    public static final String OMNISLASH_RUNE_8_ITEM_ID = "Rune_Omnislash_8";
    public static final String OMNISLASH_RUNE_12_ITEM_ID = "Rune_Omnislash_12";
    public static final String GHASTLY_ESSENCE_ITEM_ID = "Ingredient_Ghastly_Essence";
    private static final float RUNE_DAMAGE_BONUS_PER_FEED = 0.10f;
    private static final float RUNE_MANA_REDUCTION_PER_FEED = 0.05f;
    private static final float MIN_RUNE_MANA_COST_MULTIPLIER = 0.40f;
    private static LoreConfig config = new LoreConfig();

    public record SocketedRunestone(String itemId, int slotIndex, int damageFeeds, int manaFeeds) {}
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
        String[] runes = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_RUNES, Codec.STRING_ARRAY);
        String[] colors = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY);
        String[] spirits = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY);
        String[] effects = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY);
        int[] levels = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY);
        int[] xp = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY);
        int[] feedTiers = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY);
        int[] runeDamageFeeds = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_RUNE_DAMAGE_FEEDS, Codec.INT_ARRAY);
        int[] runeManaFeeds = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_RUNE_MANA_FEEDS, Codec.INT_ARRAY);
        int[] locked = item.getFromMetadataOrNull(MetadataKeys.LORE_SOCKET_LOCKED, Codec.INT_ARRAY);

        int count = maxArrayLength(values, runes, colors, spirits, effects, levels, xp, feedTiers,
                runeDamageFeeds, runeManaFeeds, locked);
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
            socket.setRuneItemId(getString(runes, i));
            socket.setColor(getString(colors, i));
            socket.setSpiritId(getString(spirits, i));
            socket.setEffectOverride(getString(effects, i));
            socket.setLevel(getInt(levels, i));
            socket.setXp(getInt(xp, i));
            socket.setFeedTier(getInt(feedTiers, i));
            socket.setRuneDamageFeeds(getInt(runeDamageFeeds, i));
            socket.setRuneManaFeeds(getInt(runeManaFeeds, i));
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
                    .withMetadata(MetadataKeys.LORE_SOCKET_RUNES, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY, new String[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_RUNE_DAMAGE_FEEDS, Codec.INT_ARRAY, new int[0])
                    .withMetadata(MetadataKeys.LORE_SOCKET_RUNE_MANA_FEEDS, Codec.INT_ARRAY, new int[0])
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
        String[] runes = new String[socketCount];
        String[] colors = new String[socketCount];
        String[] spirits = new String[socketCount];
        String[] effects = new String[socketCount];
        int[] levels = new int[socketCount];
        int[] xp = new int[socketCount];
        int[] feedTiers = new int[socketCount];
        int[] runeDamageFeeds = new int[socketCount];
        int[] runeManaFeeds = new int[socketCount];
        int[] locked = new int[socketCount];

        for (int i = 0; i < socketCount; i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            values[i] = safeString(socket.getGemItemId());
            runes[i] = safeString(socket.getRuneItemId());
            colors[i] = safeString(socket.getColor());
            spirits[i] = safeString(socket.getSpiritId());
            effects[i] = safeString(socket.getEffectOverride());
            levels[i] = Math.max(0, socket.getLevel());
            xp[i] = Math.max(0, socket.getXp());
            feedTiers[i] = Math.max(0, socket.getFeedTier());
            runeDamageFeeds[i] = Math.max(0, socket.getRuneDamageFeeds());
            runeManaFeeds[i] = Math.max(0, socket.getRuneManaFeeds());
            locked[i] = socket.isLocked() ? 1 : 0;
        }

        return item
                .withMetadata(MetadataKeys.LORE_SOCKET_MAX, Codec.INTEGER, maxSockets)
                .withMetadata(MetadataKeys.LORE_SOCKET_VALUES, Codec.STRING_ARRAY, values)
                .withMetadata(MetadataKeys.LORE_SOCKET_RUNES, Codec.STRING_ARRAY, runes)
                .withMetadata(MetadataKeys.LORE_SOCKET_SPIRITS, Codec.STRING_ARRAY, spirits)
                .withMetadata(MetadataKeys.LORE_SOCKET_COLORS, Codec.STRING_ARRAY, colors)
                .withMetadata(MetadataKeys.LORE_SOCKET_EFFECTS, Codec.STRING_ARRAY, effects)
                .withMetadata(MetadataKeys.LORE_SOCKET_LEVELS, Codec.INT_ARRAY, levels)
                .withMetadata(MetadataKeys.LORE_SOCKET_XP, Codec.INT_ARRAY, xp)
                .withMetadata(MetadataKeys.LORE_SOCKET_FEED_TIERS, Codec.INT_ARRAY, feedTiers)
                .withMetadata(MetadataKeys.LORE_SOCKET_RUNE_DAMAGE_FEEDS, Codec.INT_ARRAY, runeDamageFeeds)
                .withMetadata(MetadataKeys.LORE_SOCKET_RUNE_MANA_FEEDS, Codec.INT_ARRAY, runeManaFeeds)
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
        if (isRuneDeltaActive(item)) {
            boolean changed = false;
            for (int i = 0; i < data.getSocketCount(); i++) {
                LoreSocketData.LoreSocket socket = data.getSocket(i);
                if (socket != null && socket.getColor() != null && !socket.getColor().isBlank()) {
                    socket.setColor("");
                    changed = true;
                }
            }
            return changed;
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
            case APPLY_HASTE, APPLY_SHIELD, APPLY_INVISIBLE, LIFESTEAL, RESTORE_MANA -> EFFECT_TIER_2;
            case DAMAGE_TARGET, DAMAGE_ATTACKER, APPLY_BLEED, APPLY_POISON, DRAIN_LIFE -> EFFECT_TIER_3;
            case APPLY_BURN, APPLY_SHOCK, APPLY_FREEZE, APPLY_ROOT -> EFFECT_TIER_4;
            case APPLY_STUN, APPLY_FEAR, DOUBLE_CAST, MULTI_HIT, CRIT_CHARGE, BERSERK,
                    SUMMON_WOLF_PACK, CHARGE_ATTACK, OMNISLASH, OCTASLASH, RUNE_OCTASLASH, VORTEXSTRIKE, PUMMEL, BLOOD_RUSH,
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

    public static boolean isOctaslashSpirit(String spiritId) {
        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        return ability != null && ability.getEffectType() == LoreEffectType.RUNE_OCTASLASH;
    }

    public static boolean isMaxLevelOctaslashLore(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasSpirit()
                && socket.getLevel() >= Math.max(1, config.getMaxLevel())
                && isOctaslashSpirit(socket.getSpiritId());
    }

    public static boolean isOmnislashSpirit(String spiritId) {
        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        return ability != null && ability.getEffectType() == LoreEffectType.OMNISLASH;
    }

    public static boolean isMaxLevelOmnislashLore(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasSpirit()
                && socket.getLevel() >= Math.max(1, config.getMaxLevel())
                && isOmnislashSpirit(socket.getSpiritId());
    }

    public static boolean isBloodRushSpirit(String spiritId) {
        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        return ability != null && ability.getEffectType() == LoreEffectType.BLOOD_RUSH;
    }

    public static boolean isMaxLevelBloodRushLore(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasSpirit()
                && socket.getLevel() >= Math.max(1, config.getMaxLevel())
                && isBloodRushSpirit(socket.getSpiritId());
    }

    public static boolean isPummelSpirit(String spiritId) {
        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        return ability != null && ability.getEffectType() == LoreEffectType.PUMMEL;
    }

    public static boolean isMaxLevelPummelLore(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasSpirit()
                && socket.getLevel() >= Math.max(1, config.getMaxLevel())
                && isPummelSpirit(socket.getSpiritId());
    }

    public static boolean isWolfPackSpirit(String spiritId) {
        LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
        return ability != null && ability.getEffectType() == LoreEffectType.SUMMON_WOLF_PACK;
    }

    public static boolean isMaxLevelWolfPackLore(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasSpirit()
                && socket.getLevel() >= Math.max(1, config.getMaxLevel())
                && socket.getFeedTier() >= 1
                && isWolfPackSpirit(socket.getSpiritId());
    }

    public static String resolveWolfPackRuneItemId(int feedTier) {
        return switch (LoreAbility.resolveWolfPackTier(feedTier)) {
            case 2 -> WOLF_PACK_RUNE_2_ITEM_ID;
            case 3 -> WOLF_PACK_RUNE_3_ITEM_ID;
            default -> WOLF_PACK_RUNE_1_ITEM_ID;
        };
    }

    /** Returns the current feed-stage rune; max-level Omnislash upgrades to twelve slashes. */
    public static String resolveOmnislashRuneItemId(int level, int feedTier) {
        if (level >= Math.max(1, config.getMaxLevel())) {
            return OMNISLASH_RUNE_12_ITEM_ID;
        }
        int maxThresholds = getMaxRuneFeedThresholds();
        if (maxThresholds <= 0) {
            return OMNISLASH_RUNE_3_ITEM_ID;
        }
        int firstUpgrade = Math.max(1, (maxThresholds + 2) / 3);
        int secondUpgrade = Math.max(firstUpgrade + 1, (2 * maxThresholds + 2) / 3);
        int tier = Math.max(0, feedTier);
        if (tier < firstUpgrade) {
            return OMNISLASH_RUNE_3_ITEM_ID;
        }
        if (tier < secondUpgrade) {
            return OMNISLASH_RUNE_6_ITEM_ID;
        }
        return OMNISLASH_RUNE_8_ITEM_ID;
    }

    public static int resolveOmnislashRuneHitCount(int level, int feedTier) {
        return switch (resolveOmnislashRuneItemId(level, feedTier)) {
            case OMNISLASH_RUNE_6_ITEM_ID -> 6;
            case OMNISLASH_RUNE_8_ITEM_ID -> 8;
            case OMNISLASH_RUNE_12_ITEM_ID -> 12;
            default -> 3;
        };
    }

    /** Consumes Ghastly Essence, clears a max-level Octaslash Lore, and grants its ability rune. */
    public static boolean tryExtractOctaslashRune(Player player,
                                                  LoreSocketData data,
                                                  int slotIndex) {
        if (player == null || player.getInventory() == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (!isMaxLevelOctaslashLore(socket)) {
            return false;
        }

        String spiritId = socket.getSpiritId();
        String[] essenceId = {GHASTLY_ESSENCE_ITEM_ID};
        if (!consumeFromInventory(player, essenceId, 1)) {
            return false;
        }

        ItemStack rune = new ItemStack(OCTASLASH_RUNE_ITEM_ID, 1);
        if (!UIInventoryUtils.addItemToInventory(player, rune)) {
            // Consuming the essence freed stack capacity or a slot, so this restores the cost.
            UIInventoryUtils.addItemToInventory(player, new ItemStack(GHASTLY_ESSENCE_ITEM_ID, 1));
            return false;
        }

        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        clearSocketGem(socket);
        return true;
    }

    /** Consumes Ghastly Essence, clears a max-level Omnislash Lore, and grants its final rune. */
    public static boolean tryExtractOmnislashRune(Player player,
                                                  LoreSocketData data,
                                                  int slotIndex) {
        if (player == null || player.getInventory() == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (!isMaxLevelOmnislashLore(socket)) {
            return false;
        }

        String spiritId = socket.getSpiritId();
        if (!consumeFromInventory(player, new String[]{GHASTLY_ESSENCE_ITEM_ID}, 1)) {
            return false;
        }

        ItemStack rune = new ItemStack(OMNISLASH_RUNE_12_ITEM_ID, 1);
        if (!UIInventoryUtils.addItemToInventory(player, rune)) {
            UIInventoryUtils.addItemToInventory(player, new ItemStack(GHASTLY_ESSENCE_ITEM_ID, 1));
            return false;
        }

        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        clearSocketGem(socket);
        return true;
    }

    /** Consumes Ghastly Essence, clears a max-level Blood Rush Lore, and grants its ability rune. */
    public static boolean tryExtractBloodRushRune(Player player,
                                                  LoreSocketData data,
                                                  int slotIndex) {
        if (player == null || player.getInventory() == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (!isMaxLevelBloodRushLore(socket)) {
            return false;
        }

        String spiritId = socket.getSpiritId();
        if (!consumeFromInventory(player, new String[]{GHASTLY_ESSENCE_ITEM_ID}, 1)) {
            return false;
        }

        ItemStack rune = new ItemStack(BLOOD_RUSH_RUNE_ITEM_ID, 1);
        if (!UIInventoryUtils.addItemToInventory(player, rune)) {
            UIInventoryUtils.addItemToInventory(player, new ItemStack(GHASTLY_ESSENCE_ITEM_ID, 1));
            return false;
        }

        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        clearSocketGem(socket);
        return true;
    }

    /** Consumes Ghastly Essence, clears a max-level Pummel Lore, and grants its ability rune. */
    public static boolean tryExtractPummelRune(Player player,
                                               LoreSocketData data,
                                               int slotIndex) {
        if (player == null || player.getInventory() == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (!isMaxLevelPummelLore(socket)) {
            return false;
        }

        String spiritId = socket.getSpiritId();
        if (!consumeFromInventory(player, new String[]{GHASTLY_ESSENCE_ITEM_ID}, 1)) {
            return false;
        }

        ItemStack rune = new ItemStack(PUMMEL_RUNE_ITEM_ID, 1);
        if (!UIInventoryUtils.addItemToInventory(player, rune)) {
            UIInventoryUtils.addItemToInventory(player, new ItemStack(GHASTLY_ESSENCE_ITEM_ID, 1));
            return false;
        }

        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        clearSocketGem(socket);
        return true;
    }

    /** Consumes Ghastly Essence and extracts the wolf rune matching the Lore's feed tier. */
    public static boolean tryExtractWolfPackRune(Player player,
                                                 LoreSocketData data,
                                                 int slotIndex) {
        if (player == null || player.getInventory() == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (!isMaxLevelWolfPackLore(socket)) {
            return false;
        }

        String spiritId = socket.getSpiritId();
        String runeItemId = resolveWolfPackRuneItemId(socket.getFeedTier());
        if (!consumeFromInventory(player, new String[]{GHASTLY_ESSENCE_ITEM_ID}, 1)) {
            return false;
        }

        if (!UIInventoryUtils.addItemToInventory(player, new ItemStack(runeItemId, 1))) {
            UIInventoryUtils.addItemToInventory(player, new ItemStack(GHASTLY_ESSENCE_ITEM_ID, 1));
            return false;
        }

        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        clearSocketGem(socket);
        return true;
    }

    public static boolean applyStoredXp(LoreSocketData.LoreSocket socket) {
        if (socket == null || socket.isEmpty()) {
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

        String[] feedIds = feedItemIdsExcludingClearItems();
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

    public static int getMaxRuneFeedThresholds() {
        int maxLevel = Math.max(1, config.getMaxLevel());
        int interval = Math.max(1, config.getFeedInterval());
        return Math.max(0, (maxLevel - 1) / interval);
    }

    public static boolean needsRuneFeed(LoreSocketData.LoreSocket socket) {
        return socket != null && socket.hasRune()
                && socket.getRuneFeedsUsed() < getMaxRuneFeedThresholds();
    }

    public static boolean tryFeedRune(Player player,
                                      LoreSocketData.LoreSocket socket,
                                      String feedItemId) {
        if (player == null || socket == null || !needsRuneFeed(socket)) {
            return false;
        }
        boolean damageFeed = matchesAnyId(feedItemId, new String[]{RUNE_DAMAGE_FEED_ITEM_ID});
        boolean manaFeed = matchesAnyId(feedItemId, new String[]{RUNE_MANA_FEED_ITEM_ID});
        if ((!damageFeed && !manaFeed)
                || !consumeFromInventory(player, new String[]{feedItemId}, 1)) {
            return false;
        }
        if (damageFeed) {
            socket.setRuneDamageFeeds(socket.getRuneDamageFeeds() + 1);
        } else {
            socket.setRuneManaFeeds(socket.getRuneManaFeeds() + 1);
        }
        return true;
    }

    public static int getRuneDamageBonusPercent(int feeds) {
        return Math.max(0, Math.min(getMaxRuneFeedThresholds(), feeds))
                * Math.round(RUNE_DAMAGE_BONUS_PER_FEED * 100f);
    }

    public static int getRuneManaCostReductionPercent(int feeds) {
        return Math.max(0, Math.min(getMaxRuneFeedThresholds(), feeds))
                * Math.round(RUNE_MANA_REDUCTION_PER_FEED * 100f);
    }

    public static int getRuneUpgradeLevel(LoreSocketData.LoreSocket socket) {
        return socket == null ? 0 : getRuneUpgradeLevel(socket.getRuneDamageFeeds(), socket.getRuneManaFeeds());
    }

    public static int getRuneUpgradeLevel(int damageFeeds, int manaFeeds) {
        long totalFeeds = (long) Math.max(0, damageFeeds) + Math.max(0, manaFeeds);
        return (int) Math.min(getMaxRuneFeedThresholds(), totalFeeds);
    }

    public static float getRuneDamageMultiplier(int feeds) {
        int tier = Math.max(0, Math.min(getMaxRuneFeedThresholds(), feeds));
        return 1.0f + (RUNE_DAMAGE_BONUS_PER_FEED * tier);
    }

    public static float getRuneManaCostMultiplier(int feeds) {
        int tier = Math.max(0, Math.min(getMaxRuneFeedThresholds(), feeds));
        return Math.max(MIN_RUNE_MANA_COST_MULTIPLIER, 1.0f - (RUNE_MANA_REDUCTION_PER_FEED * tier));
    }

    private static String[] feedItemIdsExcludingClearItems() {
        String[] feedIds = config.getFeedItemIds();
        if (feedIds == null || feedIds.length == 0) {
            return new String[0];
        }
        String[] clearIds = config.getClearItemIds();
        int count = 0;
        for (String feedId : feedIds) {
            if (feedId != null && !feedId.isBlank() && !matchesAnyId(feedId, clearIds)) {
                count++;
            }
        }
        String[] filtered = new String[count];
        int index = 0;
        for (String feedId : feedIds) {
            if (feedId != null && !feedId.isBlank() && !matchesAnyId(feedId, clearIds)) {
                filtered[index++] = feedId;
            }
        }
        return filtered;
    }

    public static boolean tryClearSpirit(Player player, LoreSocketData data, int slotIndex) {
        return tryClearSpirit(player, data, slotIndex, false);
    }

    public static boolean tryClearSpirit(Player player, LoreSocketData data, int slotIndex, boolean allowInventory) {
        if (player == null || data == null) {
            return false;
        }
        LoreSocketData.LoreSocket socket = data.getSocket(slotIndex);
        if (socket == null || socket.isEmpty()) {
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
        clearSocketGem(socket);
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

    private static void clearSocketGem(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return;
        }
        socket.setGemItemId("");
        socket.setRuneItemId("");
        socket.setSpiritId("");
        socket.setEffectOverride("");
        socket.setLevel(0);
        socket.setXp(0);
        socket.setFeedTier(0);
        socket.setRuneDamageFeeds(0);
        socket.setRuneManaFeeds(0);
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

    public static boolean isRuneDeltaActive(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        SocketData socketData = SocketManager.getSocketData(item);
        ResonanceSystem.ResonanceResult resonance = SocketManager.evaluateAllowedResonance(item, socketData);
        return resonance != null
                && resonance.active()
                && resonance.type() == ResonanceSystem.ResonanceType.RUNE_DELTA;
    }

    public static boolean isAbilityRunestone(ItemStack item) {
        if (item == null || item.isEmpty() || LoreSoulstoneUtils.isInfusedSoulstone(item)) {
            return false;
        }
        Item config = item.getItem();
        if (config == null || config == Item.UNKNOWN || !(config.getAbility() instanceof CoreItemAbility ability)) {
            return false;
        }
        String rootId = ability.getCastRootId();
        return rootId != null && !rootId.isBlank();
    }

    public static List<SocketedRunestone> getSocketedRunestones(ItemStack item) {
        if (!isRuneDeltaActive(item)) {
            return List.of();
        }
        LoreSocketData data = getLoreSocketData(item);
        if (data == null) {
            return List.of();
        }
        List<SocketedRunestone> runes = new ArrayList<>();
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || !socket.hasRune()) {
                continue;
            }
            String runeId = socket.getRuneItemId();
            if (isAbilityRunestone(new ItemStack(runeId, 1))) {
                runes.add(new SocketedRunestone(
                        runeId,
                        i,
                        socket.getRuneDamageFeeds(),
                        socket.getRuneManaFeeds()));
            }
        }
        return runes;
    }

    public static List<String> getSocketedRunestoneIds(ItemStack item) {
        return getSocketedRunestones(item).stream().map(SocketedRunestone::itemId).toList();
    }

    private static int maxArrayLength(String[] a, String[] b, String[] c, String[] d, String[] e,
                                      int[] f, int[] g, int[] h, int[] i, int[] j, int[] k) {
        int max = 0;
        if (a != null) max = Math.max(max, a.length);
        if (b != null) max = Math.max(max, b.length);
        if (c != null) max = Math.max(max, c.length);
        if (d != null) max = Math.max(max, d.length);
        if (e != null) max = Math.max(max, e.length);
        if (f != null) max = Math.max(max, f.length);
        if (g != null) max = Math.max(max, g.length);
        if (h != null) max = Math.max(max, h.length);
        if (i != null) max = Math.max(max, i.length);
        if (j != null) max = Math.max(max, j.length);
        if (k != null) max = Math.max(max, k.length);
        return max;
    }
}
