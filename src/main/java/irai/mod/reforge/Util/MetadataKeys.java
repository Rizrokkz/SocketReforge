package irai.mod.reforge.Util;

/**
 * Centralized registry for all NBT metadata key constants used by SocketReforge.
 * Prevents scattered string literals and makes key changes easier to manage.
 */
public final class MetadataKeys {
    private MetadataKeys() {} // Prevent instantiation

    // Socket system
    public static final String SOCKET_MAX = "SocketReforge.Socket.Max";
    public static final String SOCKET_VALUES = "SocketReforge.Socket.Values";
    public static final String SOCKET_EFFECTS = "SocketReforge.Socket.Effects";

    // Essence system
    public static final String ESSENCE_EFFECTS = "SocketReforge.Essence.Effects";
    public static final String ESSENCE_EFFECTS_TIERS = "SocketReforge.Essence.Effects.Tiers";
    public static final String ESSENCE_TIER_MAP = "SocketReforge.Essence.TierMap";
    public static final String ESSENCE_EFFECT_LINES = "SocketReforge.Essence.EffectLines";
    public static final String ESSENCE_BONUS_STATS = "SocketReforge.Essence.Bonus.Stats";
    public static final String ESSENCE_BONUS_FLAT = "SocketReforge.Essence.Bonus.Flat";
    public static final String ESSENCE_BONUS_PERCENT = "SocketReforge.Essence.Bonus.Percent";

    // Resonance system
    public static final String RESONANCE = "SocketReforge.Resonance";
    public static final String RESONANCE_RECIPE_NAME = "SocketReforge.Resonance.RecipeName";

    // Lore socket system (separate from essence sockets)
    public static final String LORE_SOCKET_MAX = "SocketReforge.Lore.Socket.Max";
    public static final String LORE_SOCKET_VALUES = "SocketReforge.Lore.Socket.Values";
    public static final String LORE_SOCKET_SPIRITS = "SocketReforge.Lore.Socket.Spirits";
    public static final String LORE_SOCKET_LEVELS = "SocketReforge.Lore.Socket.Levels";
    public static final String LORE_SOCKET_XP = "SocketReforge.Lore.Socket.XP";
    public static final String LORE_SOCKET_COLORS = "SocketReforge.Lore.Socket.Colors";
    public static final String LORE_SOCKET_FEED_TIERS = "SocketReforge.Lore.Socket.FeedTiers";
    public static final String LORE_SOCKET_LOCKED = "SocketReforge.Lore.Socket.Locked";
    public static final String LORE_SOCKET_EFFECTS = "SocketReforge.Lore.Socket.Effects";

    // Loot tracking
    public static final String LOOT_ROLLED = "SocketReforge.Loot.Rolled";
}
