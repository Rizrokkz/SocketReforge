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

/** Cast entry points for the three feed tiers of the Soulstone Wolf Pack rune. */
public abstract class WolfPackRune extends SimpleInteraction {
    private final int tier;

    protected WolfPackRune(int tier) {
        this.tier = tier;
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
        LoreProcHandler.startWolfPackRune(store, ownerRef, targetRef, tier);
    }

    public static final class One extends WolfPackRune {
        public static final BuilderCodec<One> CODEC =
                BuilderCodec.builder(One.class, One::new, SimpleInteraction.CODEC).build();

        public One() { super(1); }
    }

    public static final class Two extends WolfPackRune {
        public static final BuilderCodec<Two> CODEC =
                BuilderCodec.builder(Two.class, Two::new, SimpleInteraction.CODEC).build();

        public Two() { super(2); }
    }

    public static final class Three extends WolfPackRune {
        public static final BuilderCodec<Three> CODEC =
                BuilderCodec.builder(Three.class, Three::new, SimpleInteraction.CODEC).build();

        public Three() { super(3); }
    }
}
