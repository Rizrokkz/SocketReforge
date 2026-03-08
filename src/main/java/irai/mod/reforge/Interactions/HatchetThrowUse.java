package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Entity.Events.HatchetThrowEST;

public class HatchetThrowUse extends SimpleInteraction {
    public static final BuilderCodec<HatchetThrowUse> CODEC =
            BuilderCodec.builder(HatchetThrowUse.class, HatchetThrowUse::new, SimpleInteraction.CODEC).build();

    public HatchetThrowUse() {}

    @Override
    protected void tick0(boolean firstRun,
                         float time,
                         @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || type != InteractionType.Secondary) {
            return;
        }

        Player player = getPlayerFromContext(context);
        if (player == null) {
            return;
        }

        HatchetThrowEST hatchetThrowEST = HatchetThrowEST.getInstance();
        if (hatchetThrowEST == null) {
            System.out.println("[SocketReforge] HatchetThrowUse fired before HatchetThrowEST was available");
            return;
        }

        hatchetThrowEST.handleInteraction(player, context, type);
    }

    private Player getPlayerFromContext(InteractionContext context) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) {
            return null;
        }

        Store<EntityStore> store = owningEntity.getStore();
        if (store == null) {
            return null;
        }

        return store.getComponent(owningEntity, Player.getComponentType());
    }
}
