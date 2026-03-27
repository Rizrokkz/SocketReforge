package irai.mod.reforge.Entity.Events;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthChunk;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ItemTypeUtils;
import irai.mod.reforge.Common.LeafSaplingDropUtils;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.ToolAbilityUtils;

/**
 * Simulates a returning hatchet flight for tools that unlocked the effect through parts.
 * The flight is intentionally server-driven so it can reuse the normal block-harvest path.
 */
public final class HatchetThrowEST extends EntityTickingSystem<EntityStore> {
    private static final double ORIGIN_HEIGHT_OFFSET = 1.15d;
    private static final double ORIGIN_FORWARD_OFFSET = 0.85d;
    private static final double SEGMENT_SAMPLE_STEP = 0.45d;
    private static final double CATCH_DISTANCE = 1.4d;
    private static final double MIN_DIRECTION_LENGTH = 0.001d;
    private static final float VISUAL_PICKUP_DELAY_SECONDS = 3600.0f;
    private static final float VISUAL_MODEL_TILT_DEGREES = 15.0f;
    private static final float VISUAL_OUTBOUND_SPIN_DEGREES_PER_SECOND = 1080.0f;
    private static final float VISUAL_RECALL_SPIN_DEGREES_PER_SECOND = 1440.0f;
    private static final boolean DEBUG_LOGGING = false;
    private static volatile HatchetThrowEST activeInstance;

    private final Map<UUID, FlightState> flights = new ConcurrentHashMap<>();

    public HatchetThrowEST() {
        activeInstance = this;
    }

    public static HatchetThrowEST getInstance() {
        return activeInstance;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    public void onPlayerMouseButton(PlayerMouseButtonEvent event) {
        if (event == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || player.getWorld() == null || player.getUuid() == null) {
            return;
        }

        if (!isThrowButton(event.getMouseButton())) {
            return;
        }

        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        ItemStack held = heldContext == null ? null : heldContext.getItemStack();
        debug("mouse", "player=" + player.getUuid()
                + ", button=" + describeMouseButton(event.getMouseButton())
                + ", section=" + describeSection(heldContext)
                + ", item=" + describeItem(held));
        if (tryTrigger(player, heldContext, held, "mouse")) {
            debug("mouse", "flight started successfully");
            event.setCancelled(true);
        }
    }

    public boolean handleInteraction(Player player, InteractionContext context, InteractionType type) {
        if (player == null || context == null || type == null) {
            return false;
        }

        PlayerInventoryUtils.HeldItemContext heldContext = new PlayerInventoryUtils.HeldItemContext(
                context.getHeldItemSectionId(),
                (short) context.getHeldItemSlot(),
                context.getHeldItemContainer(),
                context.getHeldItem());
        if (!heldContext.isValid() || heldContext.getSectionId() < 0) {
            PlayerInventoryUtils.HeldItemContext fallbackContext = PlayerInventoryUtils.getHeldItemContext(player);
            if (fallbackContext != null && fallbackContext.isValid()) {
                heldContext = fallbackContext;
            }
        }
        ItemStack held = heldContext.getItemStack();
        debug("usage", "type=" + type
                + ", section=" + describeSection(heldContext)
                + ", item=" + describeItem(held));
        return tryTrigger(player, heldContext, held, "usage");
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || player.getWorld() == null || player.getUuid() == null) {
            return;
        }

        InteractionType action = event.getActionType();
        if (action == null) {
            return;
        }

        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        ItemStack eventHeld = event.getItemInHand();
        ItemStack held = heldContext == null ? null : heldContext.getItemStack();
        FlightState active = flights.get(player.getUuid());
        if (active != null || shouldLogInteractEvent(held, eventHeld)) {
            debug("interact", "action=" + action
                    + ", held=" + describeItem(held)
                    + ", eventHeld=" + describeItem(eventHeld)
                    + ", section=" + describeSection(heldContext)
                    + ", targetBlock=" + String.valueOf(event.getTargetBlock())
                    + ", hasTargetEntity=" + (event.getTargetEntity() != null));
        }
        if (active != null && isFallbackRecallAction(action, event)) {
            debug("interact", "fallback recall from action=" + action);
            event.setCancelled(true);
            requestRecall(player, active, "interact");
            return;
        }
        if (active != null && isBlockedWhileFlying(action)) {
            debug("interact", "blocked interaction while flight active: action=" + action);
            event.setCancelled(true);
        }
    }

    public void onDrainPlayerFromWorld(DrainPlayerFromWorldEvent event) {
        if (event == null || event.getHolder() == null) {
            return;
        }

        Holder<EntityStore> holder = event.getHolder();
        Player player = holder.getComponent(Player.getComponentType());
        if (player == null || player.getUuid() == null) {
            return;
        }

        FlightState state = flights.remove(player.getUuid());
        if (state == null) {
            return;
        }

        restoreThrownItemForDrain(player, state);
        removeVisualEntityDirect(state);
        debug("disconnect", "restored in-flight hatchet for disconnecting player " + player.getUuid());
    }

    @Override
    public void tick(float time,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        if (time <= 0f) {
            return;
        }

        Ref<EntityStore> playerRef = chunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || player.getUuid() == null || player.getWorld() == null) {
            return;
        }

        FlightState state = flights.get(player.getUuid());
        if (state == null) {
            return;
        }

        try {
            tickFlight(playerRef, player, store, commandBuffer, time, state);
        } catch (Throwable t) {
            restoreThrownItem(playerRef, player, state);
            clearFlight(player.getUuid(), state, commandBuffer);
            System.err.println("[SocketReforge] HatchetThrowEST tick error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private boolean startFlight(Player player, PlayerInventoryUtils.HeldItemContext heldContext, ItemStack held) {
        TransformComponent transform = player.getTransformComponent();
        if (transform == null || transform.getPosition() == null) {
            return false;
        }

        ToolAbilityUtils.HatchetThrowStats stats = ToolAbilityUtils.getHatchetThrowStats(held);
        if (!stats.enabled) {
            return false;
        }

        Vector3d direction = getLookDirection(transform);
        if (direction == null || direction.length() <= MIN_DIRECTION_LENGTH) {
            direction = new Vector3d(0.0d, 0.0d, 1.0d);
        } else {
            direction.normalize();
        }

        Vector3d start = transform.getPosition().clone()
                .add(0.0d, ORIGIN_HEIGHT_OFFSET, 0.0d)
                .addScaled(direction, ORIGIN_FORWARD_OFFSET);

        int sectionId = heldContext == null ? -1 : heldContext.getSectionId();
        short slot = heldContext == null ? (short) -1 : heldContext.getSlot();
        ItemContainer container = heldContext == null ? null : heldContext.getContainer();
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            debug("flight", "start blocked because held container/slot is unavailable");
            return false;
        }

        ItemStack thrownItem = held.withQuantity(1);
        var removeTransaction = container.removeItemStackFromSlot(slot, 1);
        if (removeTransaction == null || !removeTransaction.succeeded()) {
            debug("flight", "start blocked because held item could not be removed from inventory");
            return false;
        }

        flights.put(player.getUuid(), new FlightState(
                player.getUuid(),
                sectionId,
                slot,
                held.getItemId(),
                thrownItem,
                start,
                direction,
                stats.speed,
                stats.recallSpeed,
                stats.range,
                stats.maxWoodHits,
                stats.durabilitySaveChance,
                stats.breakPowerMultiplier));
        debug("flight", "started item=" + held.getItemId()
                + ", section=" + sectionId
                + ", slot=" + slot
                + ", speed=" + stats.speed
                + ", recallSpeed=" + stats.recallSpeed
                + ", range=" + stats.range
                + ", maxWoodHits=" + stats.maxWoodHits
                + ", swingSpeedMultiplier=" + String.format(Locale.ROOT, "%.2f", stats.swingSpeedMultiplier)
                + ", durabilitySaveChance=" + String.format(Locale.ROOT, "%.2f", stats.durabilitySaveChance)
                + ", breakPowerMultiplier=" + String.format(Locale.ROOT, "%.2f", stats.breakPowerMultiplier)
                + ", direction=" + formatVector(direction));
        return true;
    }

    private boolean tryTrigger(Player player,
                               PlayerInventoryUtils.HeldItemContext heldContext,
                               ItemStack held,
                               String source) {
        if (player == null || player.getWorld() == null || player.getUuid() == null) {
            debug(source, "ignored because player/world/uuid is unavailable");
            return false;
        }

        FlightState active = flights.get(player.getUuid());
        if (active != null) {
            debug(source, "recall requested for active flight");
            requestRecall(player, active, source);
            return true;
        }

        if (held == null || held.isEmpty() || !ItemTypeUtils.isTool(held) || !ToolAbilityUtils.isHatchet(held)) {
            debug(source, "ignored because held item is not a throwable hatchet");
            return false;
        }

        if (!ToolAbilityUtils.isHatchetThrowUnlocked(held)) {
            debug(source, "blocked because throw metadata is not unlocked");
            return true;
        }

        boolean started = startFlight(player, heldContext, held);
        if (!started) {
            debug(source, "startFlight returned false");
        }
        return started;
    }

    private void tickFlight(Ref<EntityStore> playerRef,
                            Player player,
                            Store<EntityStore> store,
                            CommandBuffer<EntityStore> commandBuffer,
                            float time,
                            FlightState state) {
        ItemStack thrownItem = state.thrownItem;
        if (thrownItem == null || thrownItem.isEmpty()) {
            cancelFlight(playerRef, player, state, commandBuffer, "tick",
                    "flight cancelled because thrown item state is empty");
            return;
        }
        ensureVisualEntity(commandBuffer, thrownItem, state);

        TransformComponent transform = player.getTransformComponent();
        if (transform == null || transform.getPosition() == null) {
            cancelFlight(playerRef, player, state, commandBuffer, "tick",
                    "flight cancelled because player transform is unavailable");
            return;
        }

        Vector3d previous = state.position.clone();
        Vector3d next = state.position.clone();
        if (advanceReturningFlight(playerRef, player, state, commandBuffer, transform, next, time)) {
            return;
        }
        if (!state.returning) {
            advanceOutboundFlight(state, next, time);
        }

        state.position.assign(next);
        processSegment(playerRef, player, store, commandBuffer, state, previous, next);
        syncVisualEntity(commandBuffer, state, previous, time);
    }

    private boolean advanceReturningFlight(Ref<EntityStore> playerRef,
                                           Player player,
                                           FlightState state,
                                           CommandBuffer<EntityStore> commandBuffer,
                                           TransformComponent transform,
                                           Vector3d next,
                                           float time) {
        if (!state.returning) {
            return false;
        }
        Vector3d target = transform.getPosition().clone().add(0.0d, ORIGIN_HEIGHT_OFFSET, 0.0d);
        Vector3d toTarget = target.clone().subtract(state.position);
        double distance = toTarget.length();
        if (distance <= CATCH_DISTANCE) {
            completeReturn(playerRef, player, state, commandBuffer);
            return true;
        }
        if (distance > MIN_DIRECTION_LENGTH) {
            toTarget.normalize().scale(state.recallSpeed * time);
            if (toTarget.length() > distance) {
                toTarget.setLength(distance);
            }
            next.add(toTarget);
        }
        return false;
    }

    private void advanceOutboundFlight(FlightState state, Vector3d next, float time) {
        double movement = state.outboundSpeed * time;
        next.addScaled(state.direction, movement);
        state.distanceTravelled += movement;
        if (state.distanceTravelled >= state.maxRange) {
            debug("tick", "flight reached max range, switching to recall");
            state.returning = true;
        }
    }

    private void completeReturn(Ref<EntityStore> playerRef,
                                Player player,
                                FlightState state,
                                CommandBuffer<EntityStore> commandBuffer) {
        debug("tick", "flight returned to player");
        restoreThrownItem(playerRef, player, state);
        playCatchAnimation(playerRef, commandBuffer);
        clearFlight(state.playerId, state, commandBuffer);
    }

    private void cancelFlight(Ref<EntityStore> playerRef,
                              Player player,
                              FlightState state,
                              CommandBuffer<EntityStore> commandBuffer,
                              String stage,
                              String reason) {
        debug(stage, reason);
        restoreThrownItem(playerRef, player, state);
        clearFlight(state.playerId, state, commandBuffer);
    }

    private void processSegment(Ref<EntityStore> playerRef,
                                Player player,
                                Store<EntityStore> store,
                                CommandBuffer<EntityStore> commandBuffer,
                                FlightState state,
                                Vector3d start,
                                Vector3d end) {
        if (state.forceRecall) {
            return;
        }

        Vector3d delta = end.clone().subtract(start);
        double distance = delta.length();
        if (distance <= MIN_DIRECTION_LENGTH) {
            return;
        }

        int steps = Math.max(1, (int) Math.ceil(distance / SEGMENT_SAMPLE_STEP));
        Set<Long> visitedBlocks = new HashSet<>();
        int nullBlocks = 0;
        int emptyBlocks = 0;
        int softBlocks = 0;
        String firstSolidId = null;
        String firstSoftId = null;
        boolean encounteredWood = false;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            Vector3d sample = Vector3d.lerp(start, end, t);
            int blockX = (int) Math.floor(sample.getX());
            int blockY = (int) Math.floor(sample.getY());
            int blockZ = (int) Math.floor(sample.getZ());
            long blockKey = packBlockKey(blockX, blockY, blockZ);
            if (!visitedBlocks.add(blockKey)) {
                continue;
            }

            BlockType blockType = getBlockType(player.getWorld(), blockX, blockY, blockZ);
            if (blockType == null) {
                nullBlocks++;
                continue;
            }
            if (blockType == BlockType.EMPTY || blockType.isUnknown() || "Empty".equalsIgnoreCase(blockType.getId())) {
                emptyBlocks++;
                continue;
            }
            if (blockType.getGathering() != null && blockType.getGathering().isSoft()) {
                softBlocks++;
                if (firstSoftId == null) {
                    firstSoftId = blockType.getId();
                }
            }
            if (firstSolidId == null) {
                firstSolidId = blockType.getId();
            }
            ItemStack thrownItem = state.thrownItem;
            if (thrownItem == null || thrownItem.isEmpty()) {
                cancelFlight(playerRef, player, state, commandBuffer, "segment",
                        "flight cancelled because thrown item state became empty");
                return;
            }

            if (handleWoodCollision(playerRef, player, store, commandBuffer, state, thrownItem, blockType, blockX, blockY, blockZ)) {
                encounteredWood = true;
                continue;
            }
            if (isPassThroughBlock(blockType)) {
                continue;
            }
            if (encounteredWood) {
                continue;
            }

            handleNonWoodImpact(state, start, end, steps, i, blockType, blockX, blockY, blockZ);
            return;
        }

        debug("segment", "no collision over "
                + visitedBlocks.size()
                + " samples; null="
                + nullBlocks
                + ", empty="
                + emptyBlocks
                + ", soft="
                + softBlocks
                + ", firstSoft="
                + String.valueOf(firstSoftId)
                + ", firstSolid="
                + String.valueOf(firstSolidId)
                + ", start="
                + formatVector(start)
                + ", end="
                + formatVector(end));
    }

    private boolean handleWoodCollision(Ref<EntityStore> playerRef,
                                        Player player,
                                        Store<EntityStore> store,
                                        CommandBuffer<EntityStore> commandBuffer,
                                        FlightState state,
                                        ItemStack thrownItem,
                                        BlockType blockType,
                                        int blockX,
                                        int blockY,
                                        int blockZ) {
        if (!isWoodBlock(blockType, thrownItem)) {
            return false;
        }
        if (state.woodBlocksHit >= state.maxWoodHits) {
            return true;
        }
        if (breakWoodBlock(playerRef, player, store, commandBuffer, thrownItem, state,
                new Vector3i(blockX, blockY, blockZ), blockType)) {
            state.woodBlocksHit++;
            debug("segment", "broke wood block " + blockType.getId() + " at " + blockX + "," + blockY + "," + blockZ);
        }
        return true;
    }

    private void handleNonWoodImpact(FlightState state,
                                     Vector3d start,
                                     Vector3d end,
                                     int steps,
                                     int hitStep,
                                     BlockType blockType,
                                     int blockX,
                                     int blockY,
                                     int blockZ) {
        Vector3d impactPosition = resolveImpactPosition(start, end, steps, hitStep);
        debug("segment", "non-wood impact on " + (blockType == null ? "null" : blockType.getId())
                + " at " + blockX + "," + blockY + "," + blockZ + ", switching to return");
        returnHatchetFromImpact(state, impactPosition);
    }

    private boolean breakWoodBlock(Ref<EntityStore> playerRef,
                                   Player player,
                                   Store<EntityStore> store,
                                   CommandBuffer<EntityStore> commandBuffer,
                                   ItemStack held,
                                   FlightState state,
                                   Vector3i blockPos,
                                   BlockType blockType) {
        World world = player.getWorld();
        if (world == null || world.getChunkStore() == null) {
            return false;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        if (chunkStore == null) {
            return false;
        }

        int chunkX = ChunkUtil.chunkCoordinate(blockPos.x);
        int chunkZ = ChunkUtil.chunkCoordinate(blockPos.z);
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            debug("segment", "failed to resolve chunk section for break at "
                    + blockPos.x + "," + blockPos.y + "," + blockPos.z
                    + " (chunkX=" + chunkX + ", chunkZ=" + chunkZ + ", chunkIndex=" + chunkIndex + ")");
            return false;
        }

        Item item = held.getItem();
        if (item == null || item == Item.UNKNOWN || item.getTool() == null) {
            debug("segment", "failed to resolve tool item for break: " + describeItem(held));
            return false;
        }

        LeafSaplingDropUtils.onExternalTreeTrunkTouched(blockType, "hatchet_throw");

        BlockHealthChunk healthChunk = chunkStore.getComponent(
                chunkRef,
                BlockHealthModule.get().getBlockHealthChunkComponentType());
        float beforeHealth = healthChunk == null ? 1.0f : healthChunk.getBlockHealth(blockPos);
        float breakStrength = getEffectiveBreakStrength(playerRef, player, store, world, held);
        if (!held.isBroken()) {
            breakStrength *= (float) Math.max(0.0d, state.breakPowerMultiplier);
        }

        boolean destroyed = BlockHarvestUtils.performBlockDamage(
                blockPos,
                held,
                item.getTool(),
                breakStrength,
                0,
                chunkRef,
                commandBuffer,
                chunkStore);

        float afterHealth = healthChunk == null ? beforeHealth : healthChunk.getBlockHealth(blockPos);
        BlockType afterBlockType = getBlockType(world, blockPos.x, blockPos.y, blockPos.z);
        boolean blockChanged = afterBlockType == null
                || afterBlockType == BlockType.EMPTY
                || afterBlockType.isUnknown()
                || !blockType.getId().equalsIgnoreCase(afterBlockType.getId());
        boolean damaged = destroyed || blockChanged || afterHealth < beforeHealth;
        if (!damaged) {
            return false;
        }

        applyDurabilityLoss(playerRef, player, held, state, item, blockType, store);
        return destroyed || blockChanged;
    }

    private Vector3d resolveImpactPosition(Vector3d start, Vector3d end, int steps, int hitStep) {
        if (start == null || end == null) {
            return new Vector3d();
        }
        if (steps <= 1 || hitStep <= 1) {
            return start.clone();
        }

        double previousT = (double) (hitStep - 1) / (double) steps;
        Vector3d surfacePoint = Vector3d.lerp(start, end, previousT);
        Vector3d direction = end.clone().subtract(start);
        if (direction.length() > MIN_DIRECTION_LENGTH) {
            direction.normalize().scale(0.08d);
            surfacePoint.subtract(direction);
        }
        return surfacePoint;
    }

    private float getEffectiveBreakStrength(Ref<EntityStore> playerRef,
                                            Player player,
                                            Store<EntityStore> store,
                                            World world,
                                            ItemStack held) {
        if (held == null || held.isEmpty() || !held.isBroken()) {
            return 1.0f;
        }
        if (player == null || playerRef == null || store == null) {
            return 1.0f;
        }
        if (player.getGameMode() == com.hypixel.hytale.protocol.GameMode.Creative) {
            return 1.0f;
        }
        if (world == null || world.getGameplayConfig() == null || world.getGameplayConfig().getItemDurabilityConfig() == null) {
            return 1.0f;
        }

        double brokenPenalty = world.getGameplayConfig()
                .getItemDurabilityConfig()
                .getBrokenPenalties()
                .getTool(0.0d);
        float multiplier = (float) Math.max(0.0d, 1.0d - brokenPenalty);
        return multiplier <= 0.0f ? 0.0f : multiplier;
    }

    private void applyDurabilityLoss(Ref<EntityStore> playerRef,
                                     Player player,
                                     ItemStack held,
                                     FlightState state,
                                     Item item,
                                     BlockType blockType,
                                     Store<EntityStore> store) {
        if (item == null || blockType == null || state == null || held == null || held.isEmpty()) {
            return;
        }

        double durabilityLoss = BlockHarvestUtils.calculateDurabilityUse(item, blockType);
        if (durabilityLoss <= 0.0d) {
            return;
        }
        if (state.durabilitySaveChance > 0.0d) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll <= state.durabilitySaveChance) {
                debug("durability", "saved durability on " + blockType.getId()
                        + " with roll " + String.format(Locale.ROOT, "%.2f", roll)
                        + " vs saveChance " + String.format(Locale.ROOT, "%.2f", state.durabilitySaveChance));
                return;
            }
        }

        double nextDurability = Math.max(0.0d, held.getDurability() - durabilityLoss);
        state.thrownItem = held.withDurability(nextDurability);
    }

    private void ensureVisualEntity(CommandBuffer<EntityStore> commandBuffer,
                                    ItemStack held,
                                    FlightState state) {
        if (commandBuffer == null || state == null) {
            return;
        }
        if (state.visualRef != null) {
            return;
        }
        if (held == null || held.isEmpty()) {
            return;
        }

        ItemStack visualStack = held.withQuantity(1);
        visualStack.setOverrideDroppedItemAnimation(true);
        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                commandBuffer,
                visualStack,
                state.position.clone(),
                createVisualRotation(state.visualDirection, state.visualSpinDegrees, state.returning),
                0.0f,
                0.0f,
                0.0f);
        if (holder == null) {
            debug("visual", "failed to generate visual drop for " + describeItem(held));
            return;
        }

        ItemComponent itemComponent = holder.getComponent(ItemComponent.getComponentType());
        if (itemComponent != null) {
            itemComponent.setPickupDelay(VISUAL_PICKUP_DELAY_SECONDS);
        }
        holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        Velocity velocity = holder.getComponent(Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
        }

        Ref<EntityStore> visualRef = commandBuffer.addEntity(holder, AddReason.SPAWN);
        if (visualRef == null) {
            debug("visual", "failed to spawn visual drop for " + describeItem(held));
            return;
        }

        state.visualRef = visualRef;
        state.visualSpawnPending = true;
        debug("visual", "spawned visual entity for " + state.itemId);
    }

    private void syncVisualEntity(CommandBuffer<EntityStore> commandBuffer,
                                  FlightState state,
                                  Vector3d previousPosition,
                                  float time) {
        if (commandBuffer == null || state == null || state.visualRef == null) {
            return;
        }
        if (state.visualSpawnPending) {
            state.visualSpawnPending = false;
            return;
        }

        if (previousPosition != null) {
            Vector3d movement = state.position.clone().subtract(previousPosition);
            if (movement.length() > MIN_DIRECTION_LENGTH) {
                state.visualDirection.assign(movement).normalize();
            }
            float spinSpeed = state.returning
                    ? VISUAL_RECALL_SPIN_DEGREES_PER_SECOND
                    : VISUAL_OUTBOUND_SPIN_DEGREES_PER_SECOND;
            state.visualSpinDegrees = wrapDegrees(state.visualSpinDegrees + (spinSpeed * time));
        }

        TransformComponent visualTransform;
        try {
            visualTransform = commandBuffer.getComponent(state.visualRef, TransformComponent.getComponentType());
        } catch (IllegalStateException ex) {
            debug("visual", "visual ref became invalid during sync for " + state.itemId);
            state.visualRef = null;
            return;
        }
        if (visualTransform != null) {
            visualTransform.teleportPosition(state.position.clone());
            Vector3f rotation = createVisualRotation(state.visualDirection, state.visualSpinDegrees, state.returning);
            if (rotation != null) {
                visualTransform.teleportRotation(rotation);
            }
        }

        ItemComponent visualItemComponent;
        try {
            visualItemComponent = commandBuffer.getComponent(state.visualRef, ItemComponent.getComponentType());
        } catch (IllegalStateException ex) {
            debug("visual", "visual item ref became invalid during sync for " + state.itemId);
            state.visualRef = null;
            return;
        }
        if (visualItemComponent != null) {
            visualItemComponent.setPickupDelay(VISUAL_PICKUP_DELAY_SECONDS);
        }

        Velocity velocity;
        try {
            velocity = commandBuffer.getComponent(state.visualRef, Velocity.getComponentType());
        } catch (IllegalStateException ex) {
            debug("visual", "visual velocity ref became invalid during sync for " + state.itemId);
            state.visualRef = null;
            return;
        }
        if (velocity != null) {
            velocity.setZero();
        }
    }

    private void clearFlight(UUID playerId,
                             FlightState state,
                             CommandBuffer<EntityStore> commandBuffer) {
        removeVisualEntity(commandBuffer, state);
        if (playerId == null) {
            return;
        }
        if (state != null) {
            if (!flights.remove(playerId, state)) {
                flights.remove(playerId);
            }
            return;
        }
        flights.remove(playerId);
    }

    private void removeVisualEntity(CommandBuffer<EntityStore> commandBuffer, FlightState state) {
        if (commandBuffer == null || state == null || state.visualRef == null) {
            return;
        }
        Ref<EntityStore> visualRef = state.visualRef;
        state.visualRef = null;
        commandBuffer.tryRemoveEntity(visualRef, RemoveReason.REMOVE);
        debug("visual", "removed visual entity for " + state.itemId);
    }

    private void removeVisualEntityDirect(FlightState state) {
        if (state == null || state.visualRef == null) {
            return;
        }
        Ref<EntityStore> visualRef = state.visualRef;
        state.visualRef = null;
        try {
            if (visualRef.isValid() && visualRef.getStore() != null) {
                visualRef.getStore().removeEntity(visualRef, RemoveReason.REMOVE);
                debug("visual", "removed visual entity directly for " + state.itemId);
            }
        } catch (Throwable t) {
            debug("visual", "failed to remove visual entity directly for " + state.itemId + ": " + t.getMessage());
        }
    }

    private void returnHatchetFromImpact(FlightState state, Vector3d position) {
        state.position.assign(position);
        state.returning = true;
        state.forceRecall = true;
    }

    private void requestRecall(Player player, FlightState state, String source) {
        if (state == null || state.returning) {
            return;
        }
        state.requestRecall();
        debug("recall", "manual recall engaged from " + source);
    }

    private void restoreThrownItem(Ref<EntityStore> playerRef, Player player, FlightState state) {
        if (player == null || state == null || state.thrownItem == null || state.thrownItem.isEmpty()) {
            return;
        }

        ItemStack returningItem = state.thrownItem;
        state.thrownItem = null;

        Inventory inventory = player.getInventory();
        ItemContainer container = getHeldContainer(inventory, state.sectionId);
        if (container != null && state.slot >= 0 && state.slot < container.getCapacity()) {
            ItemStack slotItem = readContainerItem(container, state.slot);
            if (slotItem == null || slotItem.isEmpty()) {
                var setTransaction = container.setItemStackForSlot(state.slot, returningItem);
                if (setTransaction != null && setTransaction.succeeded()) {
                    debug("inventory", "restored " + state.itemId + " to original slot "
                            + state.sectionId + ":" + state.slot);
                    return;
                }
            }
        }

        var giveTransaction = player.giveItem(returningItem, playerRef, playerRef.getStore());
        if (giveTransaction != null && (giveTransaction.getRemainder() == null || giveTransaction.getRemainder().isEmpty())) {
            debug("inventory", "restored " + state.itemId + " through giveItem fallback");
            return;
        }

        debug("inventory", "failed to fully restore " + state.itemId + " to inventory");
    }

    private void restoreThrownItemForDrain(Player player, FlightState state) {
        if (player == null || state == null || state.thrownItem == null || state.thrownItem.isEmpty()) {
            return;
        }

        ItemStack returningItem = state.thrownItem;
        state.thrownItem = null;

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        ItemContainer container = getHeldContainer(inventory, state.sectionId);
        if (container != null && state.slot >= 0 && state.slot < container.getCapacity()) {
            ItemStack slotItem = readContainerItem(container, state.slot);
            if (slotItem == null || slotItem.isEmpty()) {
                var setTransaction = container.setItemStackForSlot(state.slot, returningItem);
                if (setTransaction != null && setTransaction.succeeded()) {
                    player.markNeedsSave();
                    debug("inventory", "restored " + state.itemId + " to original slot during drain");
                    return;
                }
            }
        }

        boolean added = tryAddToInventory(inventory, returningItem);
        player.markNeedsSave();
        if (added) {
            debug("inventory", "restored " + state.itemId + " through combined inventory during drain");
            return;
        }

        debug("inventory", "failed to fully restore " + state.itemId + " during drain");
    }

    private void playCatchAnimation(Ref<EntityStore> playerRef, CommandBuffer<EntityStore> commandBuffer) {
        if (playerRef == null || commandBuffer == null) {
            return;
        }
        try {
            AnimationUtils.playAnimation(playerRef, AnimationSlot.Action, "Item", "Throw", true, commandBuffer);
            debug("animation", "played catch animation");
        } catch (Throwable t) {
            debug("animation", "failed to play catch animation: " + t.getMessage());
        }
    }

    private static Vector3d getLookDirection(TransformComponent transform) {
        if (transform == null || transform.getPosition() == null) {
            return null;
        }

        Vector3f rotation = resolveLookRotation(transform);
        if (rotation == null) {
            return null;
        }

        Transform look = new Transform(transform.getPosition().clone(), rotation);
        Vector3d direction = look.getDirection();
        return direction == null ? null : direction.clone();
    }

    private static Vector3f resolveLookRotation(TransformComponent transform) {
        if (transform == null) {
            return null;
        }

        ModelTransform sentTransform = transform.getSentTransform();
        if (sentTransform != null && sentTransform.lookOrientation != null) {
            return toRotationVector(sentTransform.lookOrientation);
        }

        Vector3f rotation = transform.getRotation();
        return rotation == null ? null : rotation.clone();
    }

    private static Vector3f toRotationVector(Direction direction) {
        if (direction == null) {
            return null;
        }

        Vector3f rotation = new Vector3f();
        rotation.setPitch(direction.pitch);
        rotation.setYaw(direction.yaw);
        rotation.setRoll(direction.roll);
        return rotation;
    }

    private static Vector3f createVisualRotation(Vector3d direction, float spinDegrees, boolean returning) {
        if (direction == null || direction.length() <= MIN_DIRECTION_LENGTH) {
            return null;
        }
        Vector3d horizontalDirection = new Vector3d(direction.getX(), 0.0d, direction.getZ());
        if (horizontalDirection.length() <= MIN_DIRECTION_LENGTH) {
            horizontalDirection.assign(0.0d, 0.0d, 1.0d);
        } else {
            horizontalDirection.normalize();
        }
        if (!returning) {
            horizontalDirection.negate();
        }

        Vector3f horizontalLook = Vector3f.lookAt(horizontalDirection);
        if (horizontalLook == null) {
            return null;
        }
        Vector3f rotation = new Vector3f();
        rotation.setYaw(horizontalLook.getYaw());
        rotation.setPitch(VISUAL_MODEL_TILT_DEGREES);
        rotation.setRoll(returning ? spinDegrees : -spinDegrees);
        return rotation;
    }

    private static BlockType getBlockType(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        int localX = ChunkUtil.localCoordinate(x);
        int localZ = ChunkUtil.localCoordinate(z);
        return chunk.getBlockType(localX, y, localZ);
    }

    private static boolean isPassThroughBlock(BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY || blockType.isUnknown()) {
            return true;
        }
        String id = blockType.getId();
        if (id == null || id.equalsIgnoreCase("Empty")) {
            return true;
        }
        String lowerId = id.toLowerCase(Locale.ROOT);
        if (isGuaranteedSolidBlock(lowerId)) {
            return false;
        }
        if (containsAny(lowerId, "bush", "grass", "flower", "fern", "vine", "moss", "sapling", "reeds", "bramble")) {
            return true;
        }
        String hitboxType = blockType.getHitboxType();
        if (hitboxType != null && hitboxType.toLowerCase(Locale.ROOT).startsWith("plant_")) {
            return true;
        }
        Item blockItem = blockType.getItem();
        if (blockItem != null && blockItem != Item.UNKNOWN) {
            String[] categories = blockItem.getCategories();
            if (categories != null) {
                for (String category : categories) {
                    if (category != null && category.toLowerCase(Locale.ROOT).contains("plants")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isGuaranteedSolidBlock(String lowerId) {
        if (lowerId == null || lowerId.isEmpty()) {
            return false;
        }
        return lowerId.startsWith("soil_")
                || lowerId.startsWith("rock_");
    }

    private static boolean isWoodBlock(BlockType blockType, ItemStack held) {
        if (blockType == null || held == null || held.isEmpty()) {
            return false;
        }

        String lowerId = blockType.getId() == null ? "" : blockType.getId().toLowerCase(Locale.ROOT);
        if (isGuaranteedSolidBlock(lowerId)) {
            return false;
        }
        String group = blockType.getGroup();
        if (group != null && group.toLowerCase(Locale.ROOT).contains("wood")) {
            return true;
        }
        if (containsAny(lowerId, "log", "trunk", "wood", "bark", "branch", "stump")) {
            return true;
        }

        BlockGathering gathering = blockType.getGathering();
        if (gathering != null && gathering.getBreaking() != null) {
            String gatherType = gathering.getBreaking().getGatherType();
            String lowerGatherType = gatherType == null ? "" : gatherType.toLowerCase(Locale.ROOT);
            if (containsAny(lowerGatherType, "hatchet", "axe", "wood")) {
                return true;
            }
        }
        Item blockItem = blockType.getItem();
        if (blockItem != null && blockItem != Item.UNKNOWN) {
            ItemResourceType[] resourceTypes = blockItem.getResourceTypes();
            if (resourceTypes != null) {
                for (ItemResourceType resourceType : resourceTypes) {
                    String resourceId = resourceType == null ? null : resourceType.id;
                    if (resourceId != null && resourceId.toLowerCase(Locale.ROOT).contains("wood")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... fragments) {
        if (value == null || fragments == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (fragment != null && !fragment.isEmpty() && value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static long packBlockKey(int x, int y, int z) {
        return (((long) x) << 42) ^ (((long) y) << 21) ^ (long) z;
    }

    private static boolean isFallbackRecallAction(InteractionType action, PlayerInteractEvent event) {
        if (action == InteractionType.Secondary || action == InteractionType.Ability1) {
            return true;
        }
        return action == InteractionType.Use;
    }

    private static boolean isBlockedWhileFlying(InteractionType action) {
        return action == InteractionType.Primary
                || action == InteractionType.Secondary
                || action == InteractionType.Ability1
                || action == InteractionType.Use;
    }

    private static boolean isThrowButton(MouseButtonEvent mouseButton) {
        return mouseButton != null
                && mouseButton.state == MouseButtonState.Pressed
                && mouseButton.mouseButtonType == MouseButtonType.Right;
    }

    private static boolean shouldLogInteractEvent(ItemStack held, ItemStack eventHeld) {
        return isHatchetCandidate(held) || isHatchetCandidate(eventHeld);
    }

    private static boolean isHatchetCandidate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String itemId = stack.getItemId();
        if (itemId != null && itemId.toLowerCase(Locale.ROOT).contains("hatchet")) {
            return true;
        }
        return ToolAbilityUtils.isHatchet(stack);
    }

    private static void debug(String stage, String message) {
        if (!DEBUG_LOGGING) {
            return;
        }
        System.out.println("[SocketReforge][HATCHET][" + stage + "] " + message);
    }

    private static String describeMouseButton(MouseButtonEvent mouseButton) {
        if (mouseButton == null) {
            return "null";
        }
        return String.valueOf(mouseButton.mouseButtonType) + "/" + mouseButton.state + "/clicks=" + mouseButton.clicks;
    }

    private static String describeSection(PlayerInventoryUtils.HeldItemContext heldContext) {
        if (heldContext == null) {
            return "null";
        }
        return heldContext.getSectionId() + ":" + heldContext.getSlot();
    }

    private static String describeItem(ItemStack held) {
        if (held == null || held.isEmpty()) {
            return "empty";
        }
        return held.getItemId();
    }

    private static String formatVector(Vector3d vector) {
        if (vector == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", vector.getX(), vector.getY(), vector.getZ());
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    private static ItemContainer getHeldContainer(Inventory inventory, int sectionId) {
        if (inventory == null) {
            return null;
        }
        if (sectionId == Inventory.TOOLS_SECTION_ID) {
            return inventory.getTools();
        }
        if (sectionId == Inventory.HOTBAR_SECTION_ID) {
            return inventory.getHotbar();
        }
        return null;
    }

    private static ItemStack readContainerItem(ItemContainer container, short slot) {
        if (container == null || slot < 0 || slot >= container.getCapacity()) {
            return null;
        }
        try {
            return container.getItemStack(slot);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean tryAddToInventory(Inventory inventory, ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) {
            return false;
        }
        ItemStack remaining = stack;
        ItemContainer[] containers = new ItemContainer[] {
                inventory.getTools(),
                inventory.getHotbar(),
                inventory.getStorage(),
                inventory.getBackpack()
        };
        for (ItemContainer container : containers) {
            if (container == null || remaining == null || remaining.isEmpty()) {
                continue;
            }
            var transaction = container.addItemStack(remaining);
            if (transaction != null) {
                remaining = transaction.getRemainder();
            }
            if (remaining == null || remaining.isEmpty()) {
                return true;
            }
        }
        return remaining == null || remaining.isEmpty();
    }

    private static final class FlightState {
        private final UUID playerId;
        private final int sectionId;
        private final short slot;
        private final String itemId;
        private ItemStack thrownItem;
        private final Vector3d position;
        private final Vector3d direction;
        private final double outboundSpeed;
        private final double recallSpeed;
        private final double maxRange;
        private final int maxWoodHits;
        private final double durabilitySaveChance;
        private final double breakPowerMultiplier;

        private double distanceTravelled;
        private int woodBlocksHit;
        private boolean returning;
        private boolean forceRecall;
        private final Vector3d visualDirection;
        private float visualSpinDegrees;
        private Ref<EntityStore> visualRef;
        private boolean visualSpawnPending;

        private FlightState(UUID playerId,
                            int sectionId,
                            short slot,
                            String itemId,
                            ItemStack thrownItem,
                            Vector3d position,
                            Vector3d direction,
                            double outboundSpeed,
                            double recallSpeed,
                            double maxRange,
                            int maxWoodHits,
                            double durabilitySaveChance,
                            double breakPowerMultiplier) {
            this.playerId = playerId;
            this.sectionId = sectionId;
            this.slot = slot;
            this.itemId = itemId == null ? "" : itemId;
            this.thrownItem = thrownItem;
            this.position = position;
            this.direction = direction;
            this.outboundSpeed = outboundSpeed;
            this.recallSpeed = recallSpeed;
            this.maxRange = maxRange;
            this.maxWoodHits = maxWoodHits;
            this.durabilitySaveChance = Math.max(0.0d, Math.min(1.0d, durabilitySaveChance));
            this.breakPowerMultiplier = Math.max(0.0d, breakPowerMultiplier);
            this.visualDirection = direction.clone();
        }

        private boolean matchesSelection(PlayerInventoryUtils.HeldItemContext context) {
            if (context == null) {
                return sectionId < 0 || slot < 0;
            }
            return context.getSectionId() == sectionId && context.getSlot() == slot;
        }

        private void requestRecall() {
            this.returning = true;
            this.forceRecall = true;
        }
    }
}
