package irai.mod.reforge.Lore;

import java.util.Locale;

/**
 * Unified debug logger for lore systems.
 */
public final class LoreDebug {
    public static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.loreprocs", "false"));
    private static final String PREFIX = "[LoreProc]";

    private LoreDebug() {}

    public static void log(String event, String message) {
        if (!ENABLED) {
            return;
        }
        StringBuilder sb = new StringBuilder(PREFIX);
        if (event != null && !event.isBlank()) {
            sb.append(' ').append(event);
        }
        if (message != null && !message.isBlank()) {
            sb.append(' ').append(message);
        }
        System.out.println(sb);
    }

    public static void logKv(String event, Object... kv) {
        if (!ENABLED) {
            return;
        }
        if (kv == null || kv.length == 0) {
            log(event, "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.valueOf(kv[i])).append('=').append(formatValue(kv[i + 1]));
        }
        log(event, sb.toString());
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double d) {
            return String.format(Locale.ROOT, "%.3f", d);
        }
        if (value instanceof Float f) {
            return String.format(Locale.ROOT, "%.3f", f);
        }
        return String.valueOf(value);
    }
}
