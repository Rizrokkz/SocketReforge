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

/** Starts the Omnislash combo with the hit count selected by the rune's feed stage. */
public abstract class OmnislashRune extends SimpleInteraction {
    private final int hitCount;

    protected OmnislashRune(int hitCount) {
        this.hitCount = hitCount;
    }

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
        LoreProcHandler.startOmnislashRune(store, ownerRef, targetRef, hitCount);
    }

    public static final class Three extends OmnislashRune {
        public static final BuilderCodec<Three> CODEC =
                BuilderCodec.builder(Three.class, Three::new, SimpleInteraction.CODEC).build();

        public Three() { super(3); }
    }

    public static final class Six extends OmnislashRune {
        public static final BuilderCodec<Six> CODEC =
                BuilderCodec.builder(Six.class, Six::new, SimpleInteraction.CODEC).build();

        public Six() { super(6); }
    }

    public static final class Eight extends OmnislashRune {
        public static final BuilderCodec<Eight> CODEC =
                BuilderCodec.builder(Eight.class, Eight::new, SimpleInteraction.CODEC).build();

        public Eight() { super(8); }
    }

    public static final class Twelve extends OmnislashRune {
        public static final BuilderCodec<Twelve> CODEC =
                BuilderCodec.builder(Twelve.class, Twelve::new, SimpleInteraction.CODEC).build();

        public Twelve() { super(12); }
    }

    /** One strike step in the editable JSON combo sequence. */
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
            Store<EntityStore> store = ownerRef.getStore();
            LoreProcHandler.applyOmnislashRuneStep(store, ownerRef);
        }
    }
}
