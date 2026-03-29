package irai.mod.reforge.Util;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumbers;

/**
 * Utility for formatting floating damage numbers with consistent labels + colors.
 */
public final class DamageNumberFormatter {

    public enum FormatStyle {
        MESSAGE,   // <color=#RRGGBB>text</color>
        TOOLTIP,   // <color is="#RRGGBB">text</color>
        PLAIN      // no color markup
    }

    public enum DamageKind {
        FLAT,
        CRITICAL,
        ICE,
        BURN,
        BLEED,
        POISON,
        SHOCK,
        WATER,
        VOID,
        HEAL
    }

    public record Style(String label, String colorHex, boolean labelByDefault) {}

    private static final Map<DamageKind, Style> STYLES = new EnumMap<>(DamageKind.class);

    static {
        STYLES.put(DamageKind.FLAT, new Style("", null, false));
        STYLES.put(DamageKind.CRITICAL, new Style("Critical", "#FF5555", true));
        STYLES.put(DamageKind.ICE, new Style("Ice", "#55FFFF", true));
        STYLES.put(DamageKind.BURN, new Style("Burn", "#FFAA00", true));
        STYLES.put(DamageKind.BLEED, new Style("Bleed", "#AA55FF", true));
        STYLES.put(DamageKind.POISON, new Style("Poison", "#008700", true));
        STYLES.put(DamageKind.SHOCK, new Style("Shock", "#FFFF55", true));
        STYLES.put(DamageKind.WATER, new Style("Water", "#5555FF", true));
        STYLES.put(DamageKind.VOID, new Style("Void", "#8000ff", true));
        STYLES.put(DamageKind.HEAL, new Style("Heal", "#55FF55", true));
    }

    private DamageNumberFormatter() {}

    public static Style getStyle(DamageKind kind) {
        if (kind == null) {
            return STYLES.get(DamageKind.FLAT);
        }
        DamageNumbers.KindStyle libStyle = DamageNumbers.getKindStyle(kind.name());
        Style fallback = STYLES.getOrDefault(kind, STYLES.get(DamageKind.FLAT));
        if (libStyle == null) {
            return fallback;
        }
        String label = libStyle.label() != null ? libStyle.label() : fallback.label();
        String color = libStyle.colorHex() != null ? libStyle.colorHex() : fallback.colorHex();
        boolean labelByDefault = libStyle.labelByDefault() != null ? libStyle.labelByDefault() : fallback.labelByDefault();
        return new Style(label, color, labelByDefault);
    }

    public static void registerStyle(DamageKind kind, Style style) {
        if (kind == null || style == null) {
            return;
        }
        STYLES.put(kind, style);
        DamageNumbers.registerKind(new DamageNumbers.KindStyle(
                kind.name(),
                style.label(),
                null,
                null,
                null,
                null,
                style.colorHex(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                style.labelByDefault(),
                null,
                null,
                null,
                null
        ));
    }

    public static String format(double amount, DamageKind kind) {
        return format(amount, kind, FormatStyle.MESSAGE);
    }

    public static String format(double amount, DamageKind kind, FormatStyle style) {
        if (kind == null) {
            return DamageNumbers.format(amount, "FLAT", toLibraryStyle(style), false);
        }
        return DamageNumbers.format(amount, kind.name(), toLibraryStyle(style), getStyle(kind).labelByDefault());
    }

    public static String format(double amount, DamageKind kind, FormatStyle style, boolean includeLabel) {
        if (kind == null) {
            return DamageNumbers.format(amount, "FLAT", toLibraryStyle(style), includeLabel);
        }
        return DamageNumbers.format(amount, kind.name(), toLibraryStyle(style), includeLabel);
    }

    public static String format(double amount, Style style, FormatStyle formatStyle, boolean includeLabel) {
        Style resolved = style == null ? getStyle(DamageKind.FLAT) : style;
        DamageNumbers.KindStyle libStyle = new DamageNumbers.KindStyle(
                "CUSTOM",
                resolved.label(),
                null,
                null,
                null,
                null,
                resolved.colorHex(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resolved.labelByDefault(),
                null,
                null,
                null,
                null
        );
        return DamageNumbers.format(amount, libStyle, toLibraryStyle(formatStyle), includeLabel);
    }

    public static DamageKind inferKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return DamageKind.FLAT;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("crit")) return DamageKind.CRITICAL;
        if (lower.contains("ice") || lower.contains("frost") || lower.contains("freeze")) return DamageKind.ICE;
        if (lower.contains("burn") || lower.contains("fire")) return DamageKind.BURN;
        if (lower.contains("bleed")) return DamageKind.BLEED;
        if (lower.contains("poison") || lower.contains("toxic")) return DamageKind.POISON;
        if (lower.contains("shock") || lower.contains("lightning") || lower.contains("electric")) return DamageKind.SHOCK;
        if (lower.contains("water")) return DamageKind.WATER;
        if (lower.contains("void")) return DamageKind.VOID;
        if (lower.contains("heal") || lower.contains("regen")) return DamageKind.HEAL;
        return DamageKind.FLAT;
    }

    private static String formatAmount(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return "0";
        }
        long rounded = Math.round(amount);
        if (rounded == 0L && amount > 0.0d) {
            rounded = 1L;
        }
        return Long.toString(rounded);
    }

    private static String wrap(String text, String colorHex, FormatStyle style) {
        if (style == null || style == FormatStyle.PLAIN || colorHex == null || colorHex.isBlank()) {
            return text;
        }
        String hex = normalizeHex(colorHex);
        if (style == FormatStyle.TOOLTIP) {
            return "<color is=\"" + hex + "\">" + text + "</color>";
        }
        return "<color=" + hex + ">" + text + "</color>";
    }

    private static String normalizeHex(String colorHex) {
        String trimmed = colorHex.trim();
        String prefixed = trimmed.startsWith("#") ? trimmed : ("#" + trimmed);
        return prefixed.toUpperCase(Locale.ROOT);
    }

    private static DamageNumbers.FormatStyle toLibraryStyle(FormatStyle style) {
        if (style == null) {
            return DamageNumbers.FormatStyle.PLAIN;
        }
        return switch (style) {
            case MESSAGE -> DamageNumbers.FormatStyle.MESSAGE;
            case TOOLTIP -> DamageNumbers.FormatStyle.TOOLTIP;
            case PLAIN -> DamageNumbers.FormatStyle.PLAIN;
        };
    }
}
