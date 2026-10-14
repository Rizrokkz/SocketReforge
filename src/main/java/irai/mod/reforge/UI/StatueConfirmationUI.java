package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIHtmlUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Util.LangLoader;

public final class StatueConfirmationUI {

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, PendingAction> pendingActions = new ConcurrentHashMap<>();
    private static boolean hyuiAvailable = false;

    private StatueConfirmationUI() {}

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "StatueConfirmationUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void openNotice(Player player, String title, String message, String buttonLabel) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.getPlayerRef().sendMessage(Message.raw(message == null ? "" : message));
            return;
        }

        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        player.getWorld().execute(() -> openNoticeWithSync(player, title, message, buttonLabel));
    }

    public enum ActionType {
        ASCENSION,
        CORRUPTION
    }

    private static class PendingAction {
        final ActionType type;
        final ItemStack item;
        final short slot;
        final String itemName;

        PendingAction(ActionType type, ItemStack item, short slot, String itemName) {
            this.type = type;
            this.item = item;
            this.slot = slot;
            this.itemName = itemName;
        }
    }

    public static void open(Player player, ActionType type, ItemStack item, short slot) {
        if (player == null || item == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.getPlayerRef().sendMessage(Message.raw("Confirmation UI is unavailable (HyUI missing)."));
            return;
        }

        String itemName = UIItemUtils.displayNameOrItemId(item, player);
        final String finalItemName = (itemName == null || itemName.isBlank()) ? item.getItemId() : itemName;

        pendingActions.put(player.getPlayerRef(), new PendingAction(type, item, slot, finalItemName));
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        player.getWorld().execute(() -> openWithSync(player, type, finalItemName));
    }

    private static void openWithSync(Player player, ActionType type, String itemName) {
        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);
            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class, eventBindingClass, java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object activating = eventBindingClass.getField("Activating").get(null);

            PlayerRef ref = player.getPlayerRef();
            Object pageBuilder = pageForPlayer.invoke(null, ref);
            pageBuilder = fromHtml.invoke(pageBuilder, buildHtml(player, type, itemName));

            addListener.invoke(pageBuilder, "confirmButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PendingAction action = pendingActions.remove(ref);
                        closePageIfOpen(ref);
                        if (action != null) {
                            player.getWorld().execute(() -> executeAction(player, action));
                        }
                    });

            addListener.invoke(pageBuilder, "cancelButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        pendingActions.remove(ref);
                        closePageIfOpen(ref);
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object page = openMethod.invoke(pageBuilder, HyUIReflectionUtils.getStore(ref));
            openPages.put(ref, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] StatueConfirmationUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void openNoticeWithSync(Player player, String title, String message, String buttonLabel) {
        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eventBindingClass = Class.forName(HYUI_EVENT_BINDING);
            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method addListener = pageBuilderClass.getMethod("addEventListener", String.class, eventBindingClass, java.util.function.BiConsumer.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object activating = eventBindingClass.getField("Activating").get(null);
            PlayerRef ref = player.getPlayerRef();
            Object pageBuilder = pageForPlayer.invoke(null, ref);
            pageBuilder = fromHtml.invoke(pageBuilder, buildNoticeHtml(title, message, buttonLabel));

            addListener.invoke(pageBuilder, "noticeButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> closePageIfOpen(ref));

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object page = openMethod.invoke(pageBuilder, HyUIReflectionUtils.getStore(ref));
            openPages.put(ref, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] StatueConfirmationUI notice error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void executeAction(Player player, PendingAction action) {
        if (action.type == ActionType.ASCENSION) {
            irai.mod.reforge.Interactions.StatueInteraction.executeAscension(player, action.item, action.slot);
        } else {
            irai.mod.reforge.Interactions.CorruptionInteraction.executeCorruption(player, action.item, action.slot);
        }
    }

    private static String buildHtml(Player player, ActionType type, String itemName) {
        String title = type == ActionType.ASCENSION
                ? tr(player, "statue.confirm_title", "Confirm Ascension")
                : tr(player, "statue.confirm_corruption_title", "Confirm Corruption");
        String warning = type == ActionType.ASCENSION
                ? tr(player, "statue.ascension_warning", "This will ascend the item and reset its refinement level.")
                : tr(player, "statue.corruption_warning", "This will permanently lock the item from refinement, socketing, and ascension!");
        String voice = type == ActionType.ASCENSION
                ? tr(player, "statue.ascension_voice", "Mortal child, place your steel before me. I shall raise it beyond its old shape, but its tempered memory will be reborn.")
                : tr(player, "statue.corruption_voice", "Little vessel of hunger, offer your armament to the abyss. Power will answer, but every gift from the dark leaves a scar.");
        String confirmLabel = type == ActionType.ASCENSION
                ? tr(player, "statue.confirm_ascend", "Ascend")
                : tr(player, "statue.confirm_corrupt", "Corrupt");
        String titleColor = type == ActionType.ASCENSION ? "#FFAA00" : "#FF5555";
        String cancelLabel = tr(player, "statue.cancel", "Cancel");
        String itemLabel = tr(player, "statue.item_label", "{0}", itemName);

        return "<div style=\"anchor-width:1840; anchor-height:900; layout-mode:center; background-color:#00000000;\">"
                + "<div class=\"decorated-container\" data-hyui-title=\""
                + esc(title)
                + "\" style=\"anchor-width:560; anchor-height:340;\">"
                + "<div class=\"container-contents\" style=\"anchor-full:16; layout-mode:top; spacing:9;\">"
                + "<p style=\"font-weight:bold; text-align:center; color:" + titleColor + ";\">"
                + esc(title)
                + "</p>"
                + "<p style=\"white-space:wrap; text-align:center; color:#D9C6FF; font-style:italic;\">"
                + esc(voice)
                + "</p>"
                + "<p style=\"text-align:center;\">"
                + esc(itemLabel)
                + "</p>"
                + "<p style=\"white-space:wrap; text-align:center; color:#FFAA55;\">"
                + esc(warning)
                + "</p>"
                + "<div style=\"flex-weight:1;\"></div>"
                + "<div style=\"layout-mode:left; spacing:14;\">"
                + "<div style=\"flex-weight:1;\"></div>"
                + UIHtmlUtils.button("confirmButton", confirmLabel, 150, 40, false)
                + "<div style=\"flex-weight:1;\"></div>"
                + UIHtmlUtils.button("cancelButton", cancelLabel, 150, 40, false)
                + "<div style=\"flex-weight:1;\"></div>"
                + "</div></div></div></div>";
    }

    private static String buildNoticeHtml(String title, String message, String buttonLabel) {
        String safeTitle = title == null || title.isBlank() ? "Notice" : title;
        String safeButton = buttonLabel == null || buttonLabel.isBlank() ? "OK" : buttonLabel;
        return "<div style=\"anchor-width:1840; anchor-height:900; layout-mode:center; background-color:#00000000;\">"
                + "<div class=\"decorated-container\" data-hyui-title=\""
                + esc(safeTitle)
                + "\" style=\"anchor-width:520; anchor-height:240;\">"
                + "<div class=\"container-contents\" style=\"anchor-full:14; layout-mode:top; spacing:10;\">"
                + "<p style=\"font-weight:bold; text-align:center; color:#AA55FF;\">"
                + esc(safeTitle)
                + "</p>"
                + "<p style=\"white-space:wrap; text-align:center; color:#FFD36A;\">"
                + esc(message)
                + "</p>"
                + "<div style=\"flex-weight:1;\"></div>"
                + "<div style=\"layout-mode:left; spacing:14;\">"
                + "<div style=\"flex-weight:1;\"></div>"
                + UIHtmlUtils.button("noticeButton", safeButton, 150, 40, false)
                + "<div style=\"flex-weight:1;\"></div>"
                + "</div></div></div></div>";
    }

    private static String tr(Player player, String key, String fallback, Object... params) {
        String template = LangLoader.getUITranslation(player, key);
        if (template == null || template.isBlank() || template.equals(key)) {
            template = fallback;
        }
        for (int i = 0; i < params.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return template;
    }

    private static void closePageIfOpen(PlayerRef playerRef) {
        Object page = openPages.remove(playerRef);
        if (page == null) {
            return;
        }
        try {
            page.getClass().getMethod("close").invoke(page);
        } catch (Exception ignored) {
        }
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
