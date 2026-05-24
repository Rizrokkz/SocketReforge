package irai.mod.reforge.Config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small helpers for extending config arrays without overriding
 * user-provided entries that are already present.
 */
public final class ConfigMergeUtils {
    private ConfigMergeUtils() {}

    public static String[] mergeUniqueValues(String[] current, String[] defaults) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String entry : safeStrings(current)) {
            String key = normalizeRawKey(entry);
            if (key == null) {
                continue;
            }
            merged.put(key, entry);
        }
        for (String entry : safeStrings(defaults)) {
            String key = normalizeRawKey(entry);
            if (key == null || merged.containsKey(key)) {
                continue;
            }
            merged.put(key, entry);
        }
        return merged.values().toArray(String[]::new);
    }

    public static String[] mergeMissingByKey(String[] current, String[] defaults, char separator) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String entry : safeStrings(current)) {
            String key = normalizeKey(entry, separator);
            if (key == null) {
                continue;
            }
            merged.put(key, entry);
        }
        for (String entry : safeStrings(defaults)) {
            String key = normalizeKey(entry, separator);
            if (key == null || merged.containsKey(key)) {
                continue;
            }
            merged.put(key, entry);
        }
        return merged.values().toArray(String[]::new);
    }

    public static double[] extendDoubleArray(double[] current, double[] defaults) {
        double[] safeDefaults = defaults == null ? new double[0] : defaults;
        if (safeDefaults.length == 0) {
            return current == null ? new double[0] : current;
        }
        if (current != null && current.length >= safeDefaults.length) {
            return current;
        }
        double[] merged = new double[safeDefaults.length];
        System.arraycopy(safeDefaults, 0, merged, 0, safeDefaults.length);
        if (current != null) {
            System.arraycopy(current, 0, merged, 0, Math.min(current.length, merged.length));
        }
        return merged;
    }

    public static String[] extendStringArray(String[] current, String[] defaults) {
        String[] safeDefaults = defaults == null ? new String[0] : defaults;
        if (safeDefaults.length == 0) {
            return current == null ? new String[0] : current;
        }
        if (current != null && current.length >= safeDefaults.length) {
            return current;
        }
        String[] merged = new String[safeDefaults.length];
        System.arraycopy(safeDefaults, 0, merged, 0, safeDefaults.length);
        if (current != null) {
            System.arraycopy(current, 0, merged, 0, Math.min(current.length, merged.length));
        }
        return merged;
    }

    private static String normalizeKey(String entry, char separator) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String trimmed = entry.trim();
        int index = trimmed.indexOf(separator);
        String rawKey = index < 0 ? trimmed : trimmed.substring(0, index);
        return normalizeRawKey(rawKey);
    }

    private static String normalizeRawKey(String rawKey) {
        if (rawKey == null) {
            return null;
        }
        String trimmed = rawKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String[] safeStrings(String[] values) {
        return values == null ? new String[0] : values;
    }
}
