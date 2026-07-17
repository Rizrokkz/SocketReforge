package irai.mod.reforge.Common.UI;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Shared safe edit helpers for HyUI page contexts.
 */
public final class HyUIEditUtils {

    private HyUIEditUtils() {}

    public static void editStyle(Object ctxObj, String id, String style) {
        editById(ctxObj, id, builder -> invoke(builder, "withStyle", String.class, style));
    }

    public static void editText(Object ctxObj, String id, String text) {
        editById(ctxObj, id, builder -> invoke(builder, "withText", String.class, text == null ? "" : text));
    }

    public static void editImage(Object ctxObj, String id, String image) {
        editById(ctxObj, id, builder -> invoke(builder, "withImage", String.class, image == null ? "" : image));
    }

    public static void editVisible(Object ctxObj, String id, boolean visible) {
        editById(ctxObj, id, builder -> invoke(builder, "withVisible", boolean.class, visible));
    }

    public static void editProgress(Object ctxObj, String id, int value) {
        float clamped = Math.max(0, Math.min(100, value));
        editById(ctxObj, id, builder -> invoke(builder, "withValue", float.class, clamped));
    }

    public static void editDisabled(Object ctxObj, String id, boolean disabled) {
        editById(ctxObj, id, builder -> invoke(builder, "withDisabled", boolean.class, disabled));
    }

    public static void updatePage(Object ctxObj, boolean rebuild) {
        if (ctxObj == null) {
            return;
        }
        try {
            ctxObj.getClass().getMethod("updatePage", boolean.class).invoke(ctxObj, rebuild);
        } catch (Exception ignored) {
        }
    }

    private static void editById(Object ctxObj, String id, Consumer<Object> editor) {
        if (ctxObj == null || id == null || id.isBlank() || editor == null) {
            return;
        }
        try {
            Method editById = ctxObj.getClass().getMethod("editById", String.class, Consumer.class);
            editById.invoke(ctxObj, id, editor);
        } catch (Exception ignored) {
        }
    }

    private static void invoke(Object target, String methodName, Class<?> valueType, Object value) {
        if (target == null) {
            return;
        }
        try {
            target.getClass().getMethod(methodName, valueType).invoke(target, value);
        } catch (Exception ignored) {
        }
    }
}
