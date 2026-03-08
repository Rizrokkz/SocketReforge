package irai.mod.reforge.Common;

import java.util.Locale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

/**
 * Shared metadata rules for tool-part-driven abilities.
 * Keeps the unlock logic in one place so the UI and runtime behavior stay aligned.
 */
public final class ToolAbilityUtils {
    public static final String META_PROFILE_TYPE = "SocketReforge.Parts.ProfileType";
    public static final String META_PART1_TIER = "SocketReforge.Parts.Part1Tier";
    public static final String META_PART2_TIER = "SocketReforge.Parts.Part2Tier";
    public static final String META_PART3_TIER = "SocketReforge.Parts.Part3Tier";

    public static final String META_HATCHET_THROW_ENABLED = "SocketReforge.Tool.HatchetThrow.Enabled";
    public static final String META_HATCHET_THROW_SPEED = "SocketReforge.Tool.HatchetThrow.Speed";
    public static final String META_HATCHET_THROW_RECALL_SPEED = "SocketReforge.Tool.HatchetThrow.RecallSpeed";
    public static final String META_HATCHET_THROW_RANGE = "SocketReforge.Tool.HatchetThrow.Range";
    public static final String META_HATCHET_THROW_MAX_WOOD_HITS = "SocketReforge.Tool.HatchetThrow.MaxWoodHits";
    public static final String META_TOOL_SWING_SPEED_MULTIPLIER = "SocketReforge.Tool.SwingSpeedMultiplier";
    public static final String META_TOOL_DURABILITY_SAVE_CHANCE = "SocketReforge.Tool.DurabilitySaveChance";
    public static final String META_TOOL_BREAK_POWER_MULTIPLIER = "SocketReforge.Tool.BreakPowerMultiplier";

    public static final int HATCHET_THROW_MIN_HAFT_TIER = 2;
    public static final int HATCHET_THROW_MIN_WEDGE_TIER = 2;
    public static final int HATCHET_THROW_MIN_HEAD_TIER = 4;

    private ToolAbilityUtils() {}

    public static ItemStack applyAbilityMetadata(ItemStack stack, String profileType, int part1Tier, int part2Tier, int part3Tier) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }

        HatchetThrowStats stats = buildHatchetThrowStats(profileType, part1Tier, part2Tier, part3Tier);
        return stack
                .withMetadata(META_HATCHET_THROW_ENABLED, Codec.BOOLEAN, stats.enabled)
                .withMetadata(META_HATCHET_THROW_SPEED, Codec.DOUBLE, stats.speed)
                .withMetadata(META_HATCHET_THROW_RECALL_SPEED, Codec.DOUBLE, stats.recallSpeed)
                .withMetadata(META_HATCHET_THROW_RANGE, Codec.DOUBLE, stats.range)
                .withMetadata(META_HATCHET_THROW_MAX_WOOD_HITS, Codec.INTEGER, stats.maxWoodHits)
                .withMetadata(META_TOOL_SWING_SPEED_MULTIPLIER, Codec.DOUBLE, stats.swingSpeedMultiplier)
                .withMetadata(META_TOOL_DURABILITY_SAVE_CHANCE, Codec.DOUBLE, stats.durabilitySaveChance)
                .withMetadata(META_TOOL_BREAK_POWER_MULTIPLIER, Codec.DOUBLE, stats.breakPowerMultiplier);
    }

    public static HatchetThrowStats getHatchetThrowStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return HatchetThrowStats.disabled();
        }

        String profileType = stack.getFromMetadataOrNull(META_PROFILE_TYPE, Codec.STRING);
        if ((profileType == null || profileType.isBlank()) && stack.getItemId() != null) {
            String lowerId = stack.getItemId().toLowerCase(Locale.ROOT);
            if (lowerId.contains("hatchet")) {
                profileType = "HATCHET";
            }
        }
        int part1Tier = safeTier(stack.getFromMetadataOrNull(META_PART1_TIER, Codec.INTEGER));
        int part2Tier = safeTier(stack.getFromMetadataOrNull(META_PART2_TIER, Codec.INTEGER));
        int part3Tier = safeTier(stack.getFromMetadataOrNull(META_PART3_TIER, Codec.INTEGER));

        HatchetThrowStats derived = buildHatchetThrowStats(profileType, part1Tier, part2Tier, part3Tier);
        Boolean enabled = stack.getFromMetadataOrNull(META_HATCHET_THROW_ENABLED, Codec.BOOLEAN);
        Double speed = stack.getFromMetadataOrNull(META_HATCHET_THROW_SPEED, Codec.DOUBLE);
        Double recallSpeed = stack.getFromMetadataOrNull(META_HATCHET_THROW_RECALL_SPEED, Codec.DOUBLE);
        Double range = stack.getFromMetadataOrNull(META_HATCHET_THROW_RANGE, Codec.DOUBLE);
        Integer maxWoodHits = stack.getFromMetadataOrNull(META_HATCHET_THROW_MAX_WOOD_HITS, Codec.INTEGER);
        Double swingSpeedMultiplier = stack.getFromMetadataOrNull(META_TOOL_SWING_SPEED_MULTIPLIER, Codec.DOUBLE);
        Double durabilitySaveChance = stack.getFromMetadataOrNull(META_TOOL_DURABILITY_SAVE_CHANCE, Codec.DOUBLE);
        Double breakPowerMultiplier = stack.getFromMetadataOrNull(META_TOOL_BREAK_POWER_MULTIPLIER, Codec.DOUBLE);
        boolean hasPartProfileData = normalizeProfile(profileType).equals("HATCHET")
                || part1Tier > 0
                || part2Tier > 0
                || part3Tier > 0;

        if (hasPartProfileData) {
            return derived;
        }

        return new HatchetThrowStats(
                enabled != null ? enabled : derived.enabled,
                clampPositive(speed, derived.speed),
                clampPositive(recallSpeed, derived.recallSpeed),
                clampPositive(range, derived.range),
                clampInt(maxWoodHits, derived.maxWoodHits),
                clampPositive(swingSpeedMultiplier, derived.swingSpeedMultiplier),
                clampProbability(durabilitySaveChance, derived.durabilitySaveChance),
                clampPositive(breakPowerMultiplier, derived.breakPowerMultiplier));
    }

    public static boolean isHatchet(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String profileType = stack.getFromMetadataOrNull(META_PROFILE_TYPE, Codec.STRING);
        if ("HATCHET".equals(normalizeProfile(profileType))) {
            return true;
        }
        String itemId = stack.getItemId();
        return itemId != null && itemId.toLowerCase(Locale.ROOT).contains("hatchet");
    }

    public static boolean isHatchetThrowUnlocked(ItemStack stack) {
        return getHatchetThrowStats(stack).enabled;
    }

    public static String describeHatchetThrowStatus(String profileType, int part1Tier, int part2Tier, int part3Tier) {
        if (!"HATCHET".equals(normalizeProfile(profileType))) {
            return "";
        }
        if (meetsHatchetThrowRequirements(part1Tier, part2Tier, part3Tier)) {
            return " Recall throw unlocked (right mouse to throw and recall).";
        }
        return " Recall throw locked (needs T"
                + HATCHET_THROW_MIN_HAFT_TIER
                + " haft, T"
                + HATCHET_THROW_MIN_WEDGE_TIER
                + " wedge, T"
                + HATCHET_THROW_MIN_HEAD_TIER
                + " head).";
    }

    public static String getHatchetRequirementText() {
        return "T" + HATCHET_THROW_MIN_HAFT_TIER
                + " haft, T" + HATCHET_THROW_MIN_WEDGE_TIER
                + " wedge, T" + HATCHET_THROW_MIN_HEAD_TIER
                + " hatchet head";
    }

    private static HatchetThrowStats buildHatchetThrowStats(String profileType, int part1Tier, int part2Tier, int part3Tier) {
        if (!"HATCHET".equals(normalizeProfile(profileType))) {
            return HatchetThrowStats.disabled();
        }
        if (!meetsHatchetThrowRequirements(part1Tier, part2Tier, part3Tier)) {
            return HatchetThrowStats.disabled();
        }

        double swingSpeedMultiplier = 1.0d + (part1Tier * 0.06d);
        double speed = 17.5d * swingSpeedMultiplier;
        double recallSpeed = speed * 1.15d;
        double range = 13.5d;
        int maxWoodHits = 7;
        double durabilitySaveChance = Math.min(0.45d, part2Tier * 0.09d);
        double breakPowerMultiplier = 1.0d + (part3Tier * 0.08d);
        return new HatchetThrowStats(true, speed, recallSpeed, range, maxWoodHits, swingSpeedMultiplier, durabilitySaveChance, breakPowerMultiplier);
    }

    private static boolean meetsHatchetThrowRequirements(int part1Tier, int part2Tier, int part3Tier) {
        return safeTier(part1Tier) >= HATCHET_THROW_MIN_HAFT_TIER
                && safeTier(part2Tier) >= HATCHET_THROW_MIN_WEDGE_TIER
                && safeTier(part3Tier) >= HATCHET_THROW_MIN_HEAD_TIER;
    }

    private static int safeTier(Integer tier) {
        if (tier == null) {
            return 0;
        }
        return Math.max(0, Math.min(5, tier));
    }

    private static String normalizeProfile(String profileType) {
        if (profileType == null || profileType.isBlank()) {
            return "";
        }
        return profileType.trim().toUpperCase(Locale.ROOT);
    }

    private static double clampPositive(Double value, double fallback) {
        double resolved = value == null ? fallback : value;
        return Math.max(0.0d, resolved);
    }

    private static int clampInt(Integer value, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(0, resolved);
    }

    private static double clampProbability(Double value, double fallback) {
        double resolved = value == null ? fallback : value;
        return Math.max(0.0d, Math.min(1.0d, resolved));
    }

    public static final class HatchetThrowStats {
        public final boolean enabled;
        public final double speed;
        public final double recallSpeed;
        public final double range;
        public final int maxWoodHits;
        public final double swingSpeedMultiplier;
        public final double durabilitySaveChance;
        public final double breakPowerMultiplier;

        public HatchetThrowStats(boolean enabled,
                                 double speed,
                                 double recallSpeed,
                                 double range,
                                 int maxWoodHits,
                                 double swingSpeedMultiplier,
                                 double durabilitySaveChance,
                                 double breakPowerMultiplier) {
            this.enabled = enabled;
            this.speed = speed;
            this.recallSpeed = recallSpeed;
            this.range = range;
            this.maxWoodHits = maxWoodHits;
            this.swingSpeedMultiplier = swingSpeedMultiplier;
            this.durabilitySaveChance = durabilitySaveChance;
            this.breakPowerMultiplier = breakPowerMultiplier;
        }

        public static HatchetThrowStats disabled() {
            return new HatchetThrowStats(false, 0.0d, 0.0d, 0.0d, 0, 0.0d, 0.0d, 0.0d);
        }
    }
}
