package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreProcHandler;

/** Routes projectile contacts from core runes into their damage and bounce handlers. */
public final class CoreRuneProjectileInteraction {
    private CoreRuneProjectileInteraction() {}

    public static final class Hit extends SimpleInteraction {
        private String ability = "Big_Arrow";

        public static final BuilderCodec<Hit> CODEC =
                BuilderCodec.<Hit>builder(Hit.class, Hit::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("Ability", Codec.STRING),
                                (interaction, value) -> interaction.ability = value,
                                interaction -> interaction.ability)
                        .add()
                        .build();

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
            if (ownerRef == null || !ownerRef.isValid()) {
                return;
            }
            Store<EntityStore> store = ownerRef.getStore();
            LoreProcHandler.handleCoreRuneProjectileHit(
                    store, ownerRef, context.getEntity(), targetRef, ability);
        }
    }

    public static final class Miss extends SimpleInteraction {
        private String ability = "Volley";

        public static final BuilderCodec<Miss> CODEC =
                BuilderCodec.<Miss>builder(Miss.class, Miss::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("Ability", Codec.STRING),
                                (interaction, value) -> interaction.ability = value,
                                interaction -> interaction.ability)
                        .add()
                        .build();

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
            LoreProcHandler.handleCoreRuneProjectileMiss(
                    store, ownerRef, context.getEntity(), ability);
        }
    }
}
