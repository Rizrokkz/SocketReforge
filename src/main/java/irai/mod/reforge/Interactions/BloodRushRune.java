package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreProcHandler;

/** Starts and advances the six configurable strikes in the Blood Rush rune. */
public final class BloodRushRune {
    private BloodRushRune() {}

    public static final class Cast extends SimpleInteraction {
        public static final BuilderCodec<Cast> CODEC =
                BuilderCodec.builder(Cast.class, Cast::new, SimpleInteraction.CODEC).build();

        @Override
        protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                             @NonNullDecl InteractionContext context,
                             @NonNullDecl CooldownHandler cooldownHandler) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
            if (!firstRun) {
                return;
            }
            Ref<EntityStore> ownerRef = context.getOwningEntity();
            if (ownerRef == null || !ownerRef.isValid()) {
                return;
            }
            Store<EntityStore> store = ownerRef.getStore();
            if (store == null || store.getComponent(ownerRef, Player.getComponentType()) == null) {
                return;
            }
            Ref<EntityStore> targetRef = context.getTargetEntity();
            if (targetRef == null) {
                targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
            }
            if (targetRef != null && !targetRef.isValid()) {
                targetRef = null;
            }
            LoreProcHandler.startBloodRushRune(store, ownerRef, targetRef);
        }
    }

    /** One JSON-configured strike. */
    public static final class Step extends SimpleInteraction {
        public static final BuilderCodec<Step> CODEC =
                BuilderCodec.builder(Step.class, Step::new, SimpleInteraction.CODEC).build();

        @Override
        protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                             @NonNullDecl InteractionContext context,
                             @NonNullDecl CooldownHandler cooldownHandler) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
            if (!firstRun) {
                return;
            }
            Ref<EntityStore> ownerRef = context.getOwningEntity();
            if (ownerRef == null || !ownerRef.isValid()) {
                return;
            }
            LoreProcHandler.applyBloodRushRuneStep(ownerRef.getStore(), ownerRef);
        }
    }
}
