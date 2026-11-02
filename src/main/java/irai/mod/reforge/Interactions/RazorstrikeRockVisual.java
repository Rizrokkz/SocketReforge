package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import com.hypixel.hytale.builtin.fallingblocks.FallingBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Drops a short-lived Wood_Stripped_Deco block as the Razorstrike rock impact visual. */
public final class RazorstrikeRockVisual extends SimpleInteraction {
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<RazorstrikeRockVisual> CODEC =
            com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                    RazorstrikeRockVisual.class, RazorstrikeRockVisual::new,
                    SimpleInteraction.CODEC).build();

    private static final String ROCK_BLOCK_ID = "Wood_Stripped_Deco";
    private static final double DROP_HEIGHT = 2.4d;
    private static final double INITIAL_DOWNWARD_SPEED = 5.0d;
    private static final double MIN_PLAYER_CLEARANCE_SQUARED = 2.25d;
    private static final double PLAYER_CLEARANCE = 1.75d;
    private static final float VISUAL_LIFETIME_SECONDS = 1.6f;

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        TransformComponent projectileTransform = commandBuffer.getComponent(
                context.getEntity(), TransformComponent.getComponentType());
        if (projectileTransform == null || projectileTransform.getPosition() == null) {
            return;
        }

        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
        if (timeResource == null) {
            return;
        }

        Vector3d impact = projectileTransform.getPosition();
        Ref<EntityStore> ownerRef = context.getOwningEntity();
        TransformComponent ownerTransform = ownerRef == null || !ownerRef.isValid()
                ? null : commandBuffer.getComponent(ownerRef, TransformComponent.getComponentType());
        BlockType rockBlock = BlockType.getAssetMap().getAsset(ROCK_BLOCK_ID);
        if (rockBlock == null || rockBlock.isUnknown()) {
            return;
        }

        double spawnX = impact.x;
        double spawnZ = impact.z;
        if (ownerTransform != null && ownerTransform.getPosition() != null) {
            Vector3d ownerPosition = ownerTransform.getPosition();
            double dx = spawnX - ownerPosition.x;
            double dz = spawnZ - ownerPosition.z;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < MIN_PLAYER_CLEARANCE_SQUARED) {
                // Keep the rock when it lands near the dash start; shifting the visual is
                // safer than dropping the entire follow-up or clipping it into the caster.
                if (distanceSquared < 0.0001d && ownerTransform.getRotation() != null) {
                    Vector3d forward = ownerTransform.getRotation()
                            .transform(new Vector3d(0.0d, 0.0d, -1.0d));
                    dx = forward.x;
                    dz = forward.z;
                    distanceSquared = dx * dx + dz * dz;
                }
                if (distanceSquared < 0.0001d) {
                    dx = 0.0d;
                    dz = 1.0d;
                    distanceSquared = 1.0d;
                }
                double scale = PLAYER_CLEARANCE / Math.sqrt(distanceSquared);
                spawnX = ownerPosition.x + dx * scale;
                spawnZ = ownerPosition.z + dz * scale;
            }
        }
        Vector3d spawnPosition = new Vector3d(spawnX, impact.y + DROP_HEIGHT, spawnZ);
        Holder<EntityStore> rock = FallingBlock.generateFallingBlock(
                rockBlock, spawnPosition, RotationTuple.NONE, null);
        if (rock == null) {
            return;
        }

        // Keep the native block appearance and physics, but prevent the falling-block system
        // from placing or breaking terrain when this visual reaches the ground.
        rock.removeComponent(FallingBlock.getComponentType());
        Velocity velocity = rock.getComponent(Velocity.getComponentType());
        if (velocity != null) {
            velocity.set(0.0d, -INITIAL_DOWNWARD_SPEED, 0.0d);
        }
        rock.addComponent(DespawnComponent.getComponentType(),
                DespawnComponent.despawnInSeconds(timeResource, VISUAL_LIFETIME_SECONDS));
        commandBuffer.addEntity(rock, AddReason.SPAWN);
    }
}
