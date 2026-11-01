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

/** Applies the existing Socket Reforge stun ailment to a native core-rune hit target. */
public final class CoreAbilityRuneStun extends SimpleInteraction {
    private long durationMs = 1200L;

    public static final BuilderCodec<CoreAbilityRuneStun> CODEC =
            BuilderCodec.<CoreAbilityRuneStun>builder(CoreAbilityRuneStun.class, CoreAbilityRuneStun::new,
                            SimpleInteraction.CODEC)
                    .append(new KeyedCodec<>("DurationMs", Codec.LONG),
                            (interaction, value) -> interaction.durationMs = value,
                            interaction -> interaction.durationMs)
                    .add()
                    .build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || durationMs <= 0L) {
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
        LoreProcHandler.applyCoreAbilityRuneStun(store, ownerRef, targetRef, durationMs);
    }
}
