package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Spawns the two Razorstrike dagger follow-ups from the rock impact toward its hit target. */
public final class RazorstrikeRockDaggerBurst extends SimpleInteraction {
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<RazorstrikeRockDaggerBurst> CODEC =
            com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                    RazorstrikeRockDaggerBurst.class, RazorstrikeRockDaggerBurst::new,
                    SimpleInteraction.CODEC).build();

    private static final String LEFT_DAGGER_CONFIG = "Projectile_Config_Razorstrike_Dagger_Left";
    private static final String RIGHT_DAGGER_CONFIG = "Projectile_Config_Razorstrike_Dagger_Right";
    private static final double SPAWN_BACKOFF = 1.15d;
    private static final double SPREAD_RADIANS = Math.toRadians(8.0d);

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        Ref<EntityStore> ownerRef = context.getOwningEntity();
        Ref<EntityStore> rockRef = context.getEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (!isValid(ownerRef) || !isValid(rockRef) || commandBuffer == null
                || commandBuffer.getComponent(ownerRef, UUIDComponent.getComponentType()) == null) {
            return;
        }

        TransformComponent rockTransform = commandBuffer.getComponent(
                rockRef, TransformComponent.getComponentType());
        if (rockTransform == null || rockTransform.getPosition() == null) {
            return;
        }

        Vector3d impactPosition = new Vector3d(rockTransform.getPosition());
        Vector3d towardTarget = null;
        if (isValid(targetRef)) {
            TransformComponent targetTransform = commandBuffer.getComponent(
                    targetRef, TransformComponent.getComponentType());
            if (targetTransform != null && targetTransform.getPosition() != null) {
                towardTarget = new Vector3d(targetTransform.getPosition())
                        .add(0.0d, 0.8d, 0.0d).sub(impactPosition);
            }
        }
        if (towardTarget == null || towardTarget.lengthSquared() < 0.04d) {
            Velocity rockVelocity = commandBuffer.getComponent(rockRef, Velocity.getComponentType());
            if (rockVelocity != null) {
                towardTarget = new Vector3d(rockVelocity.getVelocity());
            }
        }
        if (towardTarget == null || towardTarget.lengthSquared() < 0.04d) {
            TransformComponent ownerTransform = commandBuffer.getComponent(
                    ownerRef, TransformComponent.getComponentType());
            if (ownerTransform == null || ownerTransform.getRotation() == null) {
                return;
            }
            towardTarget = ownerTransform.getRotation().transform(new Vector3d(0.0d, 0.0d, -1.0d));
            towardTarget.y = 0.0d;
            if (towardTarget.lengthSquared() < 0.04d) {
                return;
            }
        }
        towardTarget.normalize();
        Vector3d startPosition = new Vector3d(impactPosition).fma(-SPAWN_BACKOFF, towardTarget);
        ProjectileModule projectileModule = ProjectileModule.get();
        if (projectileModule == null) {
            return;
        }

        spawnDagger(projectileModule, commandBuffer, ownerRef, LEFT_DAGGER_CONFIG,
                startPosition, rotateYaw(towardTarget, -SPREAD_RADIANS));
        spawnDagger(projectileModule, commandBuffer, ownerRef, RIGHT_DAGGER_CONFIG,
                startPosition, rotateYaw(towardTarget, SPREAD_RADIANS));
    }

    private static void spawnDagger(ProjectileModule projectileModule,
                                   CommandBuffer<EntityStore> commandBuffer,
                                   Ref<EntityStore> ownerRef,
                                   String configId,
                                   Vector3d startPosition,
                                   Vector3d direction) {
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(configId);
        if (config == null) {
            return;
        }
        projectileModule.spawnProjectile(ownerRef, commandBuffer, config,
                new Vector3d(startPosition), direction, 1.0f, 1.0f);
    }

    private static Vector3d rotateYaw(Vector3d direction, double radians) {
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vector3d(
                direction.x * cos + direction.z * sin,
                direction.y,
                -direction.x * sin + direction.z * cos).normalize();
    }

    private static boolean isValid(Ref<EntityStore> ref) {
        return ref != null && ref.isValid();
    }
}
