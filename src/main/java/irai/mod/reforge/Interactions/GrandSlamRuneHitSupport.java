package irai.mod.reforge.Interactions;

import java.util.List;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.CoreRuneHandler;
import irai.mod.reforge.Lore.LoreSocketManager;

/** Preserves Socket Reforge on-hit support callbacks for Grand Slam I. */
public final class GrandSlamRuneHitSupport extends SimpleInteraction {
    public static final BuilderCodec<GrandSlamRuneHitSupport> CODEC =
            BuilderCodec.builder(GrandSlamRuneHitSupport.class, GrandSlamRuneHitSupport::new,
                    SimpleInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        Ref<EntityStore> ownerRef = context.getOwningEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        if (ownerRef == null || !ownerRef.isValid() || targetRef == null || !targetRef.isValid()) {
            return;
        }

        Store<EntityStore> store = ownerRef.getStore();
        if (!CoreAbilityRune.canUseWithHeldWeapon(store, ownerRef, context.getHeldItem())) {
            return;
        }
        List<LoreSocketManager.SocketedModifierRunestone> supportRunestones =
                CoreAbilityRune.readEquippedSupportRunestones(
                        context, ownerRef, store, context.getHeldItem());
        CoreRuneHandler.applyGrandSlamRuneHitSupport(
                store, ownerRef, targetRef, supportRunestones);
    }
}
