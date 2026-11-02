package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Logs the configured damage stage and hit context for Razorstrike projectiles. */
public final class RazorstrikeDamageDebug extends SimpleInteraction {
    private String stage = "unknown";
    private float basePhysical = -1.0f;
    private String damageAttribute = "Damage";

    public static final BuilderCodec<RazorstrikeDamageDebug> CODEC =
            BuilderCodec.<RazorstrikeDamageDebug>builder(
                            RazorstrikeDamageDebug.class,
                            RazorstrikeDamageDebug::new,
                            SimpleInteraction.CODEC)
                    .append(new KeyedCodec<>("Stage", Codec.STRING),
                            (interaction, value) -> interaction.stage = value,
                            interaction -> interaction.stage)
                    .add()
                    .append(new KeyedCodec<>("BasePhysical", Codec.FLOAT),
                            (interaction, value) -> interaction.basePhysical = value,
                            interaction -> interaction.basePhysical)
                    .add()
                    .append(new KeyedCodec<>("DamageAttribute", Codec.STRING),
                            (interaction, value) -> interaction.damageAttribute = value,
                            interaction -> interaction.damageAttribute)
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

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> ownerRef = context.getOwningEntity();
        Ref<EntityStore> projectileRef = context.getEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }

        ImpactModifiers modifiers = commandBuffer == null || !isValid(projectileRef)
                ? null : commandBuffer.getComponent(projectileRef, ImpactModifiers.getComponentType());
        System.out.println("[SocketReforge][RazorstrikeDamageDebug] stage=" + stage
                + ", interaction=" + type
                + ", basePhysical=" + basePhysical
                + ", damageAttribute=" + damageAttribute
                + ", owner=" + describe(commandBuffer, ownerRef)
                + ", projectile=" + describe(commandBuffer, projectileRef)
                + ", target=" + describe(commandBuffer, targetRef)
                + ", impactModifiers=" + (modifiers == null ? "none" : modifiers));
    }

    private static String describe(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref) {
        if (!isValid(ref)) {
            return "none";
        }
        String id = ref.toString();
        if (commandBuffer == null) {
            return id;
        }
        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (uuidComponent != null) {
            id = uuidComponent.getUuid().toString();
        }
        return id + (transform == null || transform.getPosition() == null
                ? "" : "@" + transform.getPosition());
    }

    private static boolean isValid(Ref<EntityStore> ref) {
        return ref != null && ref.isValid();
    }
}
