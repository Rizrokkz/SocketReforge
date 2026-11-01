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

import irai.mod.reforge.Lore.CoreRuneHandler;

/** Briefly counters vertical gravity during Vortexstrike IV's airborne Pummel sequence. */
public final class VortexstrikeGravityHold extends SimpleInteraction {
    private long durationMs = 1200L;
    private boolean release;

    public static final BuilderCodec<VortexstrikeGravityHold> CODEC =
            BuilderCodec.<VortexstrikeGravityHold>builder(
                            VortexstrikeGravityHold.class, VortexstrikeGravityHold::new, SimpleInteraction.CODEC)
                    .append(new KeyedCodec<>("DurationMs", Codec.LONG),
                            (interaction, value) -> interaction.durationMs = value,
                            interaction -> interaction.durationMs)
                    .add()
                    .append(new KeyedCodec<>("Release", Codec.BOOLEAN),
                            (interaction, value) -> interaction.release = value,
                            interaction -> interaction.release)
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

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        Store<EntityStore> store = targetRef.getStore();
        if (release) {
            CoreRuneHandler.releaseVortexstrikeTargetGravity(targetRef);
        } else {
            CoreRuneHandler.holdVortexstrikeTargetGravity(store, targetRef, durationMs);
        }
    }
}
