package irai.mod.reforge.Common;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.MetadataKeys;
import irai.mod.reforge.Util.NameResolver;

@SuppressWarnings("removal")
public final class SmithyLegacyUtils {
    public static final String SMITHY_CHEST_ID = "Armor_Smithy_Chest";

    private SmithyLegacyUtils() {}

    public enum Legacy {
        IRAI("irai", 0.45d, 0.65d, 0.45d, 0.65d, 1.25d, 1.50d, 1.35d, 1.60d, 1.50d, 2.00d),
        SNOW("snow", 0.95d, 1.00d, 0.55d, 0.80d, 1.20d, 1.45d, 1.00d, 1.10d, 0.95d, 1.05d),
        ATHRAIL("athrail", 0.65d, 0.85d, 0.95d, 1.05d, 0.95d, 1.05d, 1.00d, 1.15d, 1.30d, 1.65d),
        FRITH("frith", 0.60d, 0.80d, 0.60d, 0.85d, 1.00d, 1.12d, 0.95d, 1.05d, 0.90d, 1.00d);

        private final String id;
        private final double breakMin;
        private final double breakMax;
        private final double degradeMin;
        private final double degradeMax;
        private final double sameMin;
        private final double sameMax;
        private final double upgradeMin;
        private final double upgradeMax;
        private final double jackpotMin;
        private final double jackpotMax;

        Legacy(String id, double breakMin, double breakMax, double degradeMin, double degradeMax,
               double sameMin, double sameMax, double upgradeMin, double upgradeMax,
               double jackpotMin, double jackpotMax) {
            this.id = id;
            this.breakMin = breakMin;
            this.breakMax = breakMax;
            this.degradeMin = degradeMin;
            this.degradeMax = degradeMax;
            this.sameMin = sameMin;
            this.sameMax = sameMax;
            this.upgradeMin = upgradeMin;
            this.upgradeMax = upgradeMax;
            this.jackpotMin = jackpotMin;
            this.jackpotMax = jackpotMax;
        }

        public String id() {
            return id;
        }

        public double breakMultiplier() {
            return midpoint(breakMin, breakMax);
        }

        public double degradeMultiplier() {
            return midpoint(degradeMin, degradeMax);
        }

        public double sameMultiplier() {
            return midpoint(sameMin, sameMax);
        }

        public double upgradeMultiplier() {
            return midpoint(upgradeMin, upgradeMax);
        }

        public double jackpotMultiplier() {
            return midpoint(jackpotMin, jackpotMax);
        }

        public double breakCapMultiplier() {
            return breakMin;
        }

        public double degradeCapMultiplier() {
            return degradeMin;
        }

        public double sameCapMultiplier() {
            return sameMax;
        }

        public double upgradeCapMultiplier() {
            return upgradeMax;
        }

        public double jackpotCapMultiplier() {
            return jackpotMax;
        }

        public String nameKey() {
            return "ui.smithy_legacy." + id + ".name";
        }

        public String itemNameKey() {
            return "irai.items.Armor_Smithy_Chest.legacy." + id + ".name";
        }

        public String tooltipKey() {
            return "ui.smithy_legacy." + id + ".tooltip";
        }
    }

    public static final class Bonuses {
        private final Legacy legacy;
        private final double breakMultiplier;
        private final double degradeMultiplier;
        private final double sameMultiplier;
        private final double upgradeMultiplier;
        private final double jackpotMultiplier;

        private Bonuses(Legacy legacy, double breakMultiplier, double degradeMultiplier, double sameMultiplier,
                        double upgradeMultiplier, double jackpotMultiplier) {
            this.legacy = legacy;
            this.breakMultiplier = breakMultiplier;
            this.degradeMultiplier = degradeMultiplier;
            this.sameMultiplier = sameMultiplier;
            this.upgradeMultiplier = upgradeMultiplier;
            this.jackpotMultiplier = jackpotMultiplier;
        }

        public Legacy legacy() {
            return legacy;
        }

        public double breakMultiplier() {
            return breakMultiplier;
        }

        public double degradeMultiplier() {
            return degradeMultiplier;
        }

        public double sameMultiplier() {
            return sameMultiplier;
        }

        public double upgradeMultiplier() {
            return upgradeMultiplier;
        }

        public double jackpotMultiplier() {
            return jackpotMultiplier;
        }
    }

    public static boolean isSmithyChest(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String itemId = stack.getItemId();
        if (isSmithyChestId(itemId)) {
            return true;
        }
        String baseItemId = stack.getFromMetadataOrNull(MetadataKeys.REFINEMENT_BASE_ITEM_ID, Codec.STRING);
        return isSmithyChestId(baseItemId);
    }

    public static boolean isSmithyChestId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String baseId = ReforgeEquip.getBaseItemId(itemId);
        return SMITHY_CHEST_ID.equalsIgnoreCase(itemId)
                || (baseId != null && SMITHY_CHEST_ID.equalsIgnoreCase(baseId));
    }

    public static ItemStack ensureRolledLegacy(ItemStack stack) {
        if (!isSmithyChest(stack)) {
            return stack;
        }
        Legacy legacy = getLegacy(stack);
        if (legacy != null && hasRolledBonuses(stack)) {
            return stack;
        }
        return withLegacy(stack, legacy != null ? legacy : rollLegacy());
    }

    public static ItemStack withLegacy(ItemStack stack, Legacy legacy) {
        if (stack == null || stack.isEmpty() || legacy == null) {
            return stack;
        }
        Bonuses bonuses = rollBonuses(legacy);
        return stack
                .withMetadata(MetadataKeys.SMITHY_LEGACY, Codec.STRING, legacy.id())
                .withMetadata(MetadataKeys.SMITHY_LEGACY_BREAK_MULTIPLIER, Codec.DOUBLE, bonuses.breakMultiplier())
                .withMetadata(MetadataKeys.SMITHY_LEGACY_DEGRADE_MULTIPLIER, Codec.DOUBLE, bonuses.degradeMultiplier())
                .withMetadata(MetadataKeys.SMITHY_LEGACY_SAME_MULTIPLIER, Codec.DOUBLE, bonuses.sameMultiplier())
                .withMetadata(MetadataKeys.SMITHY_LEGACY_UPGRADE_MULTIPLIER, Codec.DOUBLE, bonuses.upgradeMultiplier())
                .withMetadata(MetadataKeys.SMITHY_LEGACY_JACKPOT_MULTIPLIER, Codec.DOUBLE, bonuses.jackpotMultiplier())
                .withMetadata(NameResolver.KEY_DISPLAY_NAME_KEY, Codec.STRING, legacy.itemNameKey());
    }

    public static Legacy getLegacy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return fromId(stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY, Codec.STRING));
    }

    public static Legacy equippedLegacy(Player player) {
        Bonuses bonuses = equippedBonuses(player);
        return bonuses == null ? null : bonuses.legacy();
    }

    public static Bonuses equippedBonuses(Player player) {
        if (player == null) {
            return null;
        }
        for (ItemStack armor : PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor)) {
            if (!isSmithyChest(armor)) {
                continue;
            }
            Bonuses bonuses = getBonuses(armor);
            return bonuses != null ? bonuses : defaultBonuses(Legacy.IRAI);
        }
        return null;
    }

    public static Legacy rollLegacy() {
        Legacy[] values = Legacy.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    public static Legacy fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (Legacy legacy : Legacy.values()) {
            if (legacy.id.equals(normalized) || legacy.name().equalsIgnoreCase(normalized)) {
                return legacy;
            }
        }
        return null;
    }

    public static double[] applyToOutcomeWeights(double[] weights, Legacy legacy) {
        return applyToOutcomeWeights(weights, legacy == null ? null : defaultBonuses(legacy));
    }

    public static double[] applyToOutcomeWeights(double[] weights, Bonuses bonuses) {
        if (weights == null || weights.length < 4 || bonuses == null) {
            return weights;
        }
        double[] adjusted = weights.clone();
        adjusted[0] = Math.max(0.0d, adjusted[0]) * bonuses.degradeMultiplier();
        adjusted[1] = Math.max(0.0d, adjusted[1]) * bonuses.sameMultiplier();
        adjusted[2] = Math.max(0.0d, adjusted[2]) * bonuses.upgradeMultiplier();
        adjusted[3] = Math.max(0.0d, adjusted[3]) * bonuses.jackpotMultiplier();
        normalize(adjusted);
        return adjusted;
    }

    public static Bonuses getBonuses(ItemStack stack) {
        Legacy legacy = getLegacy(stack);
        if (legacy == null) {
            return null;
        }
        return new Bonuses(
                legacy,
                metadataMultiplier(stack, MetadataKeys.SMITHY_LEGACY_BREAK_MULTIPLIER, legacy.breakMultiplier()),
                metadataMultiplier(stack, MetadataKeys.SMITHY_LEGACY_DEGRADE_MULTIPLIER, legacy.degradeMultiplier()),
                metadataMultiplier(stack, MetadataKeys.SMITHY_LEGACY_SAME_MULTIPLIER, legacy.sameMultiplier()),
                metadataMultiplier(stack, MetadataKeys.SMITHY_LEGACY_UPGRADE_MULTIPLIER, legacy.upgradeMultiplier()),
                metadataMultiplier(stack, MetadataKeys.SMITHY_LEGACY_JACKPOT_MULTIPLIER, legacy.jackpotMultiplier()));
    }

    public static Bonuses fromMetadata(Legacy legacy, double breakMultiplier, double degradeMultiplier,
                                       double sameMultiplier, double upgradeMultiplier, double jackpotMultiplier) {
        if (legacy == null) {
            return null;
        }
        return new Bonuses(
                legacy,
                sanitizeMultiplier(breakMultiplier, legacy.breakMultiplier()),
                sanitizeMultiplier(degradeMultiplier, legacy.degradeMultiplier()),
                sanitizeMultiplier(sameMultiplier, legacy.sameMultiplier()),
                sanitizeMultiplier(upgradeMultiplier, legacy.upgradeMultiplier()),
                sanitizeMultiplier(jackpotMultiplier, legacy.jackpotMultiplier()));
    }

    public static String localizedName(Legacy legacy, String langCode) {
        if (legacy == null) {
            return "";
        }
        String translated = LangLoader.formatTranslation(legacy.nameKey(), langCode);
        return translated == null || translated.isBlank() || translated.equals(legacy.nameKey())
                ? fallbackName(legacy)
                : translated;
    }

    public static String localizedItemName(Legacy legacy, String langCode) {
        if (legacy == null) {
            return "";
        }
        String translated = LangLoader.formatTranslation(legacy.itemNameKey(), langCode);
        return translated == null || translated.isBlank() || translated.equals(legacy.itemNameKey())
                ? fallbackName(legacy) + " Smithy Chest"
                : translated;
    }

    public static String localizedTooltip(Legacy legacy, String langCode) {
        if (legacy == null) {
            return "";
        }
        String translated = LangLoader.formatTranslation(legacy.tooltipKey(), langCode);
        return translated == null || translated.isBlank() || translated.equals(legacy.tooltipKey())
                ? fallbackTooltip(legacy)
                : translated;
    }

    public static String compactBonusText(Legacy legacy) {
        return compactBonusText(legacy == null ? null : defaultBonuses(legacy));
    }

    public static String compactBonusText(Bonuses bonuses) {
        if (bonuses == null) {
            return "";
        }
        return "Break " + percentDelta(bonuses.breakMultiplier())
                + " | Degrade " + percentDelta(bonuses.degradeMultiplier())
                + " | Same " + percentDelta(bonuses.sameMultiplier())
                + " | Upgrade " + percentDelta(bonuses.upgradeMultiplier())
                + " | Jackpot " + percentDelta(bonuses.jackpotMultiplier());
    }

    public static String[] bonusLines(Bonuses bonuses) {
        if (bonuses == null) {
            return new String[0];
        }
        return new String[] {
                bonusLine("Break", bonuses.breakMultiplier(), bonuses.legacy().breakCapMultiplier()),
                bonusLine("Degrade", bonuses.degradeMultiplier(), bonuses.legacy().degradeCapMultiplier()),
                bonusLine("Same", bonuses.sameMultiplier(), bonuses.legacy().sameCapMultiplier()),
                bonusLine("Upgrade", bonuses.upgradeMultiplier(), bonuses.legacy().upgradeCapMultiplier()),
                bonusLine("Jackpot", bonuses.jackpotMultiplier(), bonuses.legacy().jackpotCapMultiplier())
        };
    }

    private static Bonuses rollBonuses(Legacy legacy) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Bonuses(
                legacy,
                between(random, legacy.breakMin, legacy.breakMax),
                between(random, legacy.degradeMin, legacy.degradeMax),
                between(random, legacy.sameMin, legacy.sameMax),
                between(random, legacy.upgradeMin, legacy.upgradeMax),
                between(random, legacy.jackpotMin, legacy.jackpotMax));
    }

    private static Bonuses defaultBonuses(Legacy legacy) {
        return new Bonuses(
                legacy,
                legacy.breakMultiplier(),
                legacy.degradeMultiplier(),
                legacy.sameMultiplier(),
                legacy.upgradeMultiplier(),
                legacy.jackpotMultiplier());
    }

    private static void normalize(double[] weights) {
        double total = 0.0d;
        for (int i = 0; i < Math.min(4, weights.length); i++) {
            total += Math.max(0.0d, weights[i]);
        }
        if (total <= 0.0d) {
            return;
        }
        for (int i = 0; i < Math.min(4, weights.length); i++) {
            weights[i] = Math.max(0.0d, weights[i]) / total;
        }
    }

    private static String fallbackName(Legacy legacy) {
        return switch (legacy) {
            case IRAI -> "Legacy of Irai";
            case SNOW -> "Legacy of Snow";
            case ATHRAIL -> "Legacy of Daevis";
            case FRITH -> "Legacy of Frith";
        };
    }

    private static String fallbackTooltip(Legacy legacy) {
        return switch (legacy) {
            case IRAI -> "Balanced legacy: better upgrade and jackpot odds with less degrade/break risk.";
            case SNOW -> "Stability legacy: lowers degrade odds and turns more rolls into safe same results.";
            case ATHRAIL -> "Gambler legacy: improves jackpot odds and lowers break risk.";
            case FRITH -> "Safeguard legacy: heavily lowers degrade and break risk.";
        };
    }

    private static double midpoint(double min, double max) {
        return (min + max) / 2.0d;
    }

    private static double between(ThreadLocalRandom random, double min, double max) {
        if (max <= min) {
            return min;
        }
        return random.nextDouble(min, max);
    }

    private static double metadataMultiplier(ItemStack stack, String key, double fallback) {
        if (stack == null || stack.isEmpty()) {
            return fallback;
        }
        Double value = stack.getFromMetadataOrNull(key, Codec.DOUBLE);
        return sanitizeMultiplier(value, fallback);
    }

    private static boolean hasRolledBonuses(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY_BREAK_MULTIPLIER, Codec.DOUBLE) != null
                && stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY_DEGRADE_MULTIPLIER, Codec.DOUBLE) != null
                && stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY_SAME_MULTIPLIER, Codec.DOUBLE) != null
                && stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY_UPGRADE_MULTIPLIER, Codec.DOUBLE) != null
                && stack.getFromMetadataOrNull(MetadataKeys.SMITHY_LEGACY_JACKPOT_MULTIPLIER, Codec.DOUBLE) != null;
    }

    private static double sanitizeMultiplier(Double value, double fallback) {
        if (value == null || !Double.isFinite(value) || value < 0.0d) {
            return fallback;
        }
        return value;
    }

    private static String percentDelta(double multiplier) {
        double delta = (multiplier - 1.0d) * 100.0d;
        if (Math.abs(delta) < 0.05d) {
            return "+0%";
        }
        return String.format(Locale.ROOT, "%+.0f%%", delta);
    }

    private static String bonusLine(String label, double rolledMultiplier, double capMultiplier) {
        return String.format(
                Locale.ROOT,
                "%-10s %s (%s)",
                label,
                percentDelta(rolledMultiplier),
                percentDelta(capMultiplier));
    }
}
