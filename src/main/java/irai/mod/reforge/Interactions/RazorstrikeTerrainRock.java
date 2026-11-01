package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Vector3d;

import com.hypixel.hytale.builtin.fallingblocks.FallingBlock;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.WorldBlockUtils;

/** Drops a short-lived copy of the terrain block below a Razorstrike rock impact. */
public final class RazorstrikeTerrainRock extends SimpleInteraction {
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<RazorstrikeTerrainRock> CODEC =
            com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                    RazorstrikeTerrainRock.class, RazorstrikeTerrainRock::new,
                    SimpleInteraction.CODEC).build();

    private static final int MAX_GROUND_SEARCH = 20;
    private static final double DROP_HEIGHT = 2.4d;
    private static final double INITIAL_DOWNWARD_SPEED = 5.0d;
    private static final double MIN_PLAYER_CLEARANCE_SQUARED = 2.25d;
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

        EntityStore entityStore = commandBuffer.getExternalData();
        World world = entityStore == null ? null : entityStore.getWorld();
        TimeResource timeResource = commandBuffer.getResource(TimeResource.getResourceType());
        if (world == null || timeResource == null) {
            return;
        }

        Vector3d impact = projectileTransform.getPosition();
        int blockX = floor(impact.x);
        int blockZ = floor(impact.z);
        int firstY = floor(impact.y);
        Ref<EntityStore> ownerRef = context.getOwningEntity();
        TransformComponent ownerTransform = ownerRef == null || !ownerRef.isValid()
                ? null : commandBuffer.getComponent(ownerRef, TransformComponent.getComponentType());
        if (ownerTransform != null && ownerTransform.getPosition() != null) {
            Vector3d ownerPosition = ownerTransform.getPosition();
            double dx = ownerPosition.x - (blockX + 0.5d);
            double dz = ownerPosition.z - (blockZ + 0.5d);
            // A full terrain block dropped over the caster can occlude their model and camera.
            if (dx * dx + dz * dz < MIN_PLAYER_CLEARANCE_SQUARED) {
                return;
            }
        }
        int terrainY = findSolidBlockY(world, blockX, firstY, blockZ);
        if (terrainY == Integer.MIN_VALUE) {
            return;
        }
        BlockType terrain = WorldBlockUtils.getBlockType(world, blockX, terrainY, blockZ);
        if (terrain == null) {
            return;
        }

        Vector3d spawnPosition = new Vector3d(blockX + 0.5d, terrainY + DROP_HEIGHT, blockZ + 0.5d);
        Holder<EntityStore> rock = FallingBlock.generateFallingBlock(
                terrain, spawnPosition, RotationTuple.NONE, null);
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

    private static int findSolidBlockY(World world, int x, int startY, int z) {
        for (int offset = 0; offset <= MAX_GROUND_SEARCH; offset++) {
            int y = startY - offset;
            BlockType block = WorldBlockUtils.getBlockType(world, x, y, z);
            if (block != null && !block.isUnknown() && block.getMaterial() == BlockMaterial.Solid) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
