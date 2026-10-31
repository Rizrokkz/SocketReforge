package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Starts the Omnislash combo with the hit count selected by the rune's feed stage. */
public abstract class OmnislashRune extends SimpleInteraction {
    private static final double[] DEFAULT_PASS_THROUGH_DISTANCES = {0.75d, 1.25d, 1.75d};

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
        if (!CoreAbilityRune.canUseWithHeldWeapon(store, ownerRef, context.getHeldItem())) {
            return;
        }

        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        if (targetRef != null && !targetRef.isValid()) {
            targetRef = null;
        }
        ImpactModifiers nativeModifiers = ImpactModifiers.collect(context, ownerRef, store);
        Map<String, Float> nativeAttributes = nativeModifiers == null
                ? Map.of() : nativeModifiers.getAttributes();
        if (nativeAttributes == null || nativeAttributes.isEmpty()) {
            nativeAttributes = CoreAbilityRune.readEquippedSupportAttributes(
                    context, ownerRef, store, context.getHeldItem());
        }
        LoreProcHandler.captureCoreAbilityNativeModifiers(ownerRef, nativeAttributes,
                nativeModifiers == null ? null : nativeModifiers.getOnHitEffectIds(),
                nativeModifiers == null ? null : nativeModifiers.getElementalCauseId());

        List<LoreSocketManager.SocketedModifierRunestone> supportRunestones =
                CoreAbilityRune.readEquippedSupportRunestones(context, ownerRef, store, context.getHeldItem());
        String[] nativeOnHitEffects = nativeModifiers == null
                ? null : nativeModifiers.getOnHitEffectIds();
        if ((nativeAttributes != null && !nativeAttributes.isEmpty())
                || (nativeOnHitEffects != null && nativeOnHitEffects.length > 0)
                || !supportRunestones.isEmpty()) {
            System.out.println("[SocketReforge] Omnislash support snapshot: rune="
                    + (context.getHeldItem() == null ? "null" : context.getHeldItem().getItemId())
                    + ", nativeAttributes=" + nativeAttributes
                    + ", nativeOnHitEffects=" + Arrays.toString(nativeOnHitEffects)
                    + ", supportRunestones=" + supportRunestones);
        }
        LoreProcHandler.startOmnislashRune(store, ownerRef, targetRef, hitCount, supportRunestones);
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

    /** Faces the next selected target before a JSON-configured dash is chosen. */
    public static final class Ready extends SimpleInteraction {
        private String sideStepDirection = "Left";

        public static final BuilderCodec<Ready> CODEC =
                BuilderCodec.<Ready>builder(Ready.class, Ready::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("SideStepDirection", Codec.STRING),
                                (interaction, value) -> interaction.sideStepDirection =
                                        "Right".equalsIgnoreCase(value) ? "Right" : "Left",
                                interaction -> interaction.sideStepDirection)
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
            LoreProcHandler.prepareOmnislashRuneStep(
                    ownerRef.getStore(), ownerRef, sideStepDirection);
        }
    }

    /** Chooses one authored pass-through distance and starts that slash's dash. */
    public static final class DashChoice extends SimpleInteraction {
        private double[] passThroughDistances = DEFAULT_PASS_THROUGH_DISTANCES.clone();

        public static final BuilderCodec<DashChoice> CODEC =
                BuilderCodec.<DashChoice>builder(DashChoice.class, DashChoice::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("PassThroughDistances", Codec.DOUBLE_ARRAY),
                                (interaction, value) -> interaction.passThroughDistances = sanitize(value),
                                interaction -> interaction.passThroughDistances)
                        .add()
                        .build();

        private static double[] sanitize(double[] values) {
            if (values == null || values.length == 0) {
                return DEFAULT_PASS_THROUGH_DISTANCES.clone();
            }
            return Arrays.stream(values)
                    .filter(value -> Double.isFinite(value) && value >= 0.1d && value <= 8.0d)
                    .limit(8)
                    .toArray();
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
            LoreProcHandler.applyOmnislashRuneDashChoice(
                    ownerRef.getStore(), ownerRef, passThroughDistances);
        }
    }

    /** Launches the hidden rechain-finale rune's vertical jump. */
    public static final class RechainSlamJump extends SimpleInteraction {
        private double jumpVerticalSpeed = 22.0d;

        public static final BuilderCodec<RechainSlamJump> CODEC =
                BuilderCodec.<RechainSlamJump>builder(
                                RechainSlamJump.class, RechainSlamJump::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("JumpVerticalSpeed", Codec.DOUBLE),
                                (interaction, value) -> interaction.jumpVerticalSpeed = value,
                                interaction -> interaction.jumpVerticalSpeed)
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
            LoreProcHandler.startOmnislashRechainSlamJump(
                    ownerRef.getStore(), ownerRef, jumpVerticalSpeed);
        }
    }

    /** Dives toward the ground and applies the rechain-finale slam on landing. */
    public static final class RechainSlamDive extends SimpleInteraction {
        private double diveVerticalSpeed = 30.0d;
        private double impactRadius = 5.0d;
        private double damageMultiplier = 1.5d;

        public static final BuilderCodec<RechainSlamDive> CODEC =
                BuilderCodec.<RechainSlamDive>builder(
                                RechainSlamDive.class, RechainSlamDive::new, SimpleInteraction.CODEC)
                        .append(new KeyedCodec<>("DiveVerticalSpeed", Codec.DOUBLE),
                                (interaction, value) -> interaction.diveVerticalSpeed = value,
                                interaction -> interaction.diveVerticalSpeed)
                        .add()
                        .append(new KeyedCodec<>("ImpactRadius", Codec.DOUBLE),
                                (interaction, value) -> interaction.impactRadius = value,
                                interaction -> interaction.impactRadius)
                        .add()
                        .append(new KeyedCodec<>("DamageMultiplier", Codec.DOUBLE),
                                (interaction, value) -> interaction.damageMultiplier = value,
                                interaction -> interaction.damageMultiplier)
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
            LoreProcHandler.startOmnislashRechainSlamDive(
                    ownerRef.getStore(), ownerRef, diveVerticalSpeed, impactRadius, damageMultiplier);
        }
    }
}
