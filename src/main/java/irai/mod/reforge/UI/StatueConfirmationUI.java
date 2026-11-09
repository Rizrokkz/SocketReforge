package irai.mod.reforge.UI;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;

import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Interactions.CorruptionInteraction;
import irai.mod.reforge.Interactions.StatueInteraction;
import irai.mod.reforge.Util.LangLoader;

public final class StatueConfirmationUI {
    private static final String PAGE_UI = "Pages/StatueConfirmation.ui";

    private StatueConfirmationUI() {}

    public static void openNotice(Player player, String title, String message, String buttonLabel) {
        if (player == null || player.getPlayerRef() == null || player.getWorld() == null) return;
        PlayerRef owner = player.getPlayerRef();
        player.getWorld().execute(() -> openPage(player, new ConfirmPage(owner,
                title == null || title.isBlank() ? "Notice" : title,
                "", message == null ? "" : message,
                "", buttonLabel == null || buttonLabel.isBlank() ? "OK" : buttonLabel,
                null, true)));
    }

    public enum ActionType { ASCENSION, CORRUPTION }

    private record PendingAction(ActionType type, ItemStack item, short slot) {}

    public static void open(Player player, ActionType type, ItemStack item, short slot) {
        if (player == null || player.getPlayerRef() == null || player.getWorld() == null || type == null || item == null) return;
        String itemName = UIItemUtils.displayNameOrItemId(item, player);
        String title = type == ActionType.ASCENSION
                ? tr(player, "statue.confirm_title", "Confirm Ascension")
                : tr(player, "statue.confirm_corruption_title", "Confirm Corruption");
        String voice = type == ActionType.ASCENSION
                ? tr(player, "statue.ascension_voice", "Mortal child, place your steel before me. I shall raise it beyond its old shape, but its tempered memory will be reborn.")
                : tr(player, "statue.corruption_voice", "Little vessel of hunger, offer your armament to the abyss. Power will answer, but every gift from the dark leaves a scar.");
        String warning = type == ActionType.ASCENSION
                ? tr(player, "statue.ascension_warning", "This will ascend the item and reset its refinement level.")
                : tr(player, "statue.corruption_warning", "This will permanently lock the item from refinement, socketing, and ascension!");
        String itemLabel = tr(player, "statue.item_label", "{0}", itemName);
        String confirm = type == ActionType.ASCENSION
                ? tr(player, "statue.confirm_ascend", "Ascend")
                : tr(player, "statue.confirm_corrupt", "Corrupt");
        PlayerRef owner = player.getPlayerRef();
        PendingAction action = new PendingAction(type, item, slot);
        player.getWorld().execute(() -> openPage(player, new ConfirmPage(owner, title, voice, warning,
                itemLabel, confirm, action, false)));
    }

    private static void openPage(Player player, ConfirmPage page) {
        if (player == null || player.getWorld() == null) return;
        Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef = page.owner.getReference();
        if (entityRef == null || !entityRef.isValid()) return;
        Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store = entityRef.getStore();
        if (store == null) return;
        player.getPageManager().openCustomPage(entityRef, store, page);
    }

    private static void executeAction(Player player, PendingAction action) {
        if (action.type == ActionType.ASCENSION) {
            StatueInteraction.executeAscension(player, action.item, action.slot);
        } else {
            CorruptionInteraction.executeCorruption(player, action.item, action.slot);
        }
    }

    private static String tr(Player player, String key, String fallback, Object... params) {
        String value = LangLoader.getUITranslation(player, key);
        if (value == null || value.isBlank() || value.equals(key)) value = fallback;
        for (int i = 0; i < params.length; i++) value = value.replace("{" + i + "}", String.valueOf(params[i]));
        return value;
    }

    private static final class ConfirmPage extends InteractiveCustomUIPage<ConfirmPage.PageEvent> {
        private final PlayerRef owner;
        private final String title;
        private final String voice;
        private final String message;
        private final String itemLabel;
        private final String confirmLabel;
        private final PendingAction action;
        private final boolean notice;

        private ConfirmPage(PlayerRef owner, String title, String voice, String message,
                            String itemLabel, String confirmLabel, PendingAction action, boolean notice) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
            this.title = title;
            this.voice = voice;
            this.message = message;
            this.itemLabel = itemLabel;
            this.confirmLabel = confirmLabel;
            this.action = action;
            this.notice = notice;
        }

        @Override
        public void build(Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerRef,
                          UICommandBuilder commands, UIEventBuilder events,
                          Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            commands.append(PAGE_UI);
            commands.set("#ConfirmTitle.Text", title);
            commands.set("#ConfirmVoice.Text", voice);
            commands.set("#ConfirmItem.Text", itemLabel);
            commands.set("#ConfirmWarning.Text", message);
            commands.set("#ConfirmButton.Text", confirmLabel);
            commands.set("#CancelButton.Text", tr(player, "statue.cancel", "Cancel"));
            commands.set("#ConfirmVoice.Visible", !notice && voice != null && !voice.isBlank());
            commands.set("#ConfirmItem.Visible", !notice && itemLabel != null && !itemLabel.isBlank());
            commands.set("#CancelButton.Visible", !notice);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", EventData.of("Action", "confirm"));
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", EventData.of("Action", "cancel"));
        }

        @Override
        public void handleDataEvent(Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> playerRef,
                                    Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                                    PageEvent event) {
            super.handleDataEvent(playerRef, store, event);
            if (event == null || event.action == null) return;
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            if (!notice && "confirm".equals(event.action) && action != null) executeAction(player, action);
            if ("confirm".equals(event.action) || "cancel".equals(event.action)) {
                player.getPageManager().closePage(playerRef, store);
            }
        }

        public static final class PageEvent {
            public String action;
            public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value,
                            event -> event.action).add()
                    .build();
        }
    }
}
