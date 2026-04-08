package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.UI.LoreSocketBenchUI;
import irai.mod.reforge.Util.LangLoader;

/**
 * Opens the Lore Socket Bench UI when the bench is used.
 */
@SuppressWarnings("removal")
public class LoreSocketBench extends SimpleInteraction {

    public static final BuilderCodec<LoreSocketBench> CODEC =
            BuilderCodec.builder(LoreSocketBench.class, LoreSocketBench::new, SimpleInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context, @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);

        if (!firstRun || type != InteractionType.Use) {
            return;
        }

        Player player = getPlayerFromContext(context);
        if (player == null) {
            return;
        }

        if (LoreSocketBenchUI.isAvailable()) {
            LoreSocketBenchUI.open(player);
            return;
        }

        player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.lore_socket.hyui_missing")));
    }

    /**
     * Gets the player from the interaction context.
     */
    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return null;
        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) return null;
        return store.getComponent(owningEntity, Player.getComponentType());
    }
}
