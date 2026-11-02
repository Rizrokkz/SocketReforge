package irai.mod.reforge.Interactions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreDamageUtils;
import irai.mod.reforge.Lore.LoreWorldTasks;

/** Fires an aimed Razorstrike rock from each spike position after a short delay. */
public final class RazorstrikeSpikeRockLauncher extends SimpleInteraction {
    private static final double PROJECTILE_LAUNCH_HEIGHT = 3.0d;
    private static final double SPIKE_FORWARD_OFFSET = 1.6d;
    private static final long PROJECTILE_DELAY_MS = 400L;

    private String projectile = "Projectile_Config_Razorstrike_Aimed_Rock_Center";

    public static final BuilderCodec<RazorstrikeSpikeRockLauncher> CODEC =
            BuilderCodec.<RazorstrikeSpikeRockLauncher>builder(
                            RazorstrikeSpikeRockLauncher.class,
                            RazorstrikeSpikeRockLauncher::new,
                            SimpleInteraction.CODEC)
                    .append(new KeyedCodec<>("Projectile", Codec.STRING),
                            (interaction, value) -> interaction.projectile = value,
                            interaction -> interaction.projectile)
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
        var commandBuffer = context.getCommandBuffer();
        if (!isValid(ownerRef) || commandBuffer == null
                || commandBuffer.getComponent(ownerRef, UUIDComponent.getComponentType()) == null) {
            return;
        }

        TransformComponent ownerTransform = commandBuffer.getComponent(
                ownerRef, TransformComponent.getComponentType());
        if (ownerTransform == null || ownerTransform.getPosition() == null) {
            return;
        }

        Vector3d spikeForward = ownerTransform.getRotation() == null
                ? new Vector3d(0.0d, 0.0d, -1.0d)
                : ownerTransform.getRotation().transform(new Vector3d(0.0d, 0.0d, -1.0d));
        spikeForward.y = 0.0d;
        if (spikeForward.lengthSquared() < 1.0e-6d) {
            spikeForward.set(0.0d, 0.0d, -1.0d);
        } else {
            spikeForward.normalize();
        }
        // The raised-rock visual is disabled while isolating the tier-3 disappearance issue.
        // Keep the aimed projectile at the same spike-line position and launch height.
        Vector3d spikePosition = new Vector3d(ownerTransform.getPosition())
                .fma(SPIKE_FORWARD_OFFSET, spikeForward)
                .add(0.0d, 0.25d, 0.0d);
        Store<EntityStore> store = ownerRef.getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        ImpactModifiers resolvedModifiers = ImpactModifiers.resolve(context, ownerRef, commandBuffer);
        ImpactModifiers impactModifiers = resolvedModifiers == null
                ? null : (ImpactModifiers) resolvedModifiers.clone();
        float weaponBaseDamage = LoreDamageUtils.resolveWeaponBaseDamage(store, ownerRef);
        Vector3d launchPosition = new Vector3d(spikePosition).add(0.0d, PROJECTILE_LAUNCH_HEIGHT, 0.0d);
        String projectileId = projectile;
        CompletableFuture.delayedExecutor(PROJECTILE_DELAY_MS, TimeUnit.MILLISECONDS).execute(() ->
                LoreWorldTasks.queue(store, () -> fireRockProjectile(
                        store, ownerRef, launchPosition, projectileId, impactModifiers,
                        weaponBaseDamage)));
    }

    private static void fireRockProjectile(Store<EntityStore> store,
                                           Ref<EntityStore> ownerRef,
                                           Vector3d launchPosition,
                                           String projectileId,
                                           ImpactModifiers impactModifiers,
                                           float weaponBaseDamage) {
        if (store == null || store.isShutdown() || !isValid(ownerRef)
                || store.getComponent(ownerRef, UUIDComponent.getComponentType()) == null) {
            return;
        }

        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(projectileId);
        ProjectileModule projectileModule = ProjectileModule.get();
        if (config == null || projectileModule == null) {
            return;
        }

        Vector3d direction = resolveAimDirection(store, ownerRef);
        if (direction == null || direction.lengthSquared() < 1.0e-6d) {
            return;
        }
        direction.normalize();

        boolean[] launched = {false};
        store.forEachChunk(Query.any(), (chunk, commandBuffer) -> {
            if (launched[0] || commandBuffer == null) {
                return;
            }
            Ref<EntityStore> projectileRef = projectileModule.spawnProjectile(
                    ownerRef, commandBuffer, config,
                    new Vector3d(launchPosition), new Vector3d(direction), 1.0f, 1.0f);
            launched[0] = projectileRef != null;
            if (projectileRef != null) {
                if (impactModifiers != null) {
                    commandBuffer.putComponent(projectileRef, ImpactModifiers.getComponentType(),
                            (ImpactModifiers) impactModifiers.clone());
                }
                RazorstrikeProjectileDamage.registerRockProjectile(projectileRef, weaponBaseDamage);
            }
        });
    }

    private static Vector3d resolveAimDirection(Store<EntityStore> store, Ref<EntityStore> ownerRef) {
        HeadRotation headRotation = store.getComponent(ownerRef, HeadRotation.getComponentType());
        if (headRotation != null && headRotation.getDirection() != null
                && headRotation.getDirection().lengthSquared() > 1.0e-6d) {
            return new Vector3d(headRotation.getDirection()).normalize();
        }

        TransformComponent transform = store.getComponent(ownerRef, TransformComponent.getComponentType());
        if (transform == null || transform.getRotation() == null) {
            return null;
        }
        Vector3d direction = transform.getRotation().transform(new Vector3d(0.0d, 0.0d, -1.0d));
        return direction.lengthSquared() > 1.0e-6d ? direction.normalize() : null;
    }

    private static boolean isValid(Ref<EntityStore> ref) {
        return ref != null && ref.isValid();
    }
}
