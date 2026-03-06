package irai.mod.reforge.Common.UI;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Shared reflection helpers for optional HyUI integration.
 */
public final class HyUIReflectionUtils {

    private HyUIReflectionUtils() {}

    public static boolean detectHyUi(String pageBuilderClassName, String pluginClassName, String uiName) {
        try {
            Class.forName(pageBuilderClassName);
            Class.forName(pluginClassName);
            System.out.println("[SocketReforge] " + uiName + ": HyUI loaded.");
            return true;
        } catch (ClassNotFoundException e) {
            System.out.println("[SocketReforge] " + uiName + ": HyUI unavailable.");
            return false;
        }
    }

    public static String extractEventValue(Object eventObj) {
        if (eventObj == null) {
            return null;
        }
        try {
            Method getValue = eventObj.getClass().getMethod("getValue");
            Object value = getValue.invoke(eventObj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return eventObj.toString();
        }
    }

    public static String getContextValue(Object ctxObj, String... keys) {
        if (ctxObj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            try {
                Method getValue = ctxObj.getClass().getMethod("getValue", String.class);
                Object optObj = getValue.invoke(ctxObj, key);
                if (!(optObj instanceof Optional<?> optional) || optional.isEmpty()) {
                    continue;
                }
                Object value = optional.get();
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Object getStore(PlayerRef playerRef) throws Exception {
        Method getReference = playerRef.getClass().getMethod("getReference");
        Object ref = getReference.invoke(playerRef);
        Method getStore = ref.getClass().getMethod("getStore");
        return getStore.invoke(ref);
    }

    public static <T> T resolveIndexSelection(List<T> entries, String value) {
        if (entries == null || value == null || value.isEmpty()) {
            return null;
        }
        try {
            int idx = Integer.parseInt(value.trim());
            if (idx < 0 || idx >= entries.size()) {
                return null;
            }
            return entries.get(idx);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static void closePageIfOpen(Map<PlayerRef, Object> openPages, PlayerRef playerRef) {
        Object page = openPages.remove(playerRef);
        if (page == null) {
            return;
        }
        try {
            page.getClass().getMethod("close").invoke(page);
        } catch (Exception ignored) {
        }
    }
}
