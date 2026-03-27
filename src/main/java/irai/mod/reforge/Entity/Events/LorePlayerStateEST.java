package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Lore.LoreTrigger;
import irai.mod.reforge.Util.LangLoader;

/**
 * Ticking system that emits lore triggers from player state changes:
 * movement, healing, status effects, and potion-like item use.
 */
@SuppressWarnings("removal")
public final class LorePlayerStateEST extends EntityTickingSystem<EntityStore> {
    private static final float HEAL_EPSILON = 0.05f;
    private static final String[] POTION_ITEM_TOKENS = {
            "potion", "elixir", "flask", "tonic", "brew", "draught"
    };
    private static final String[] POTION_EFFECT_TOKENS = {
            "potion", "elixir", "regen", "regeneration", "healing", "heal", "restoration", "strength", "speed", "haste"
    };
    private static final long PROXIMITY_SCAN_INTERVAL_MS = 2500L;
    private static final double PROXIMITY_RADIUS = 8.0d;
    private static final double PROXIMITY_RADIUS_SQ = PROXIMITY_RADIUS * PROXIMITY_RADIUS;
    private static final float PROXIMITY_EFFECT_DURATION = 1.25f;
    private static final double PROXIMITY_MIN_MOVE_SQ = 1.0d;
    private static final int PROXIMITY_MAX_HIGHLIGHTS = 6;
    private static final int PROXIMITY_MAX_NPC_CHECKS = 140;
    private static final long PROXIMITY_MESSAGE_INTERVAL_MS = 8000L;
    private static final String PROXIMITY_MESSAGE_KEY = "irai.lore.proximity_hint";
    private static final String[] HIGHLIGHT_AVOID_TOKENS = {
            "burn", "freeze", "shock", "bleed", "poison", "slow", "stun", "fear", "damage",
            "heal", "regen", "regeneration", "shield", "invulnerable", "invisible", "root"
    };
    private static final String HIGHLIGHT_EFFECT_ID = "Lore_Spirit_Highlight";
    private static final String[] HIGHLIGHT_FALLBACK_IDS = {
            HIGHLIGHT_EFFECT_ID,
            "Lore/Lore_Spirit_Highlight",
            "Drop_Rare",
            "Drop_Uncommon",
            "Drop_Epic",
            "Red_Flash"
    };
    private static final long HIGHLIGHT_RESOLVE_INTERVAL_MS = 5000L;
    private static final Object HIGHLIGHT_LOCK = new Object();
    private static volatile boolean highlightResolved = false;
    private static volatile int highlightEffectIndex = -1;
    private static volatile long lastHighlightResolveMs = 0L;
    private static final Object NPC_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> npcQuery;

    private static final ConcurrentMap<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private static final class PlayerState {
        boolean sprinting;
        boolean jumping;
        boolean sneaking;
        float lastHealth;
        boolean hasHealth;
        int[] effectIndexes = new int[0];
        String heldItemId = "";
        int heldQuantity = 0;
        long lastProximityCheckMs = 0L;
        boolean hasLastProximityPos = false;
        double lastProximityX = 0d;
        double lastProximityY = 0d;
        double lastProximityZ = 0d;
        long lastProximityMessageMs = 0L;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    @Override
    public void tick(float time,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> commandBuffer) {
        try {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }
            UUID uuid = player.getUuid();
            if (uuid == null) {
                return;
            }
            PlayerState state = STATES.computeIfAbsent(uuid, key -> new PlayerState());

            MovementStatesComponent movementComponent =
                    store.getComponent(ref, MovementStatesComponent.getComponentType());
            MovementStates movement = movementComponent == null ? null : movementComponent.getMovementStates();

            boolean sprinting = movement != null && movement.sprinting;
            boolean jumping = movement != null && movement.jumping;
            boolean sneaking = movement != null && (movement.crouching || movement.forcedCrouching);

            boolean sprintStart = sprinting && !state.sprinting;
            boolean jumpStart = jumping && !state.jumping;
            boolean sneakStart = sneaking && !state.sneaking;

            state.sprinting = sprinting;
            state.jumping = jumping;
            state.sneaking = sneaking;

            float currentHealth = readHealth(store, ref);
            boolean healed = false;
            if (currentHealth >= 0f) {
                if (state.hasHealth && currentHealth > state.lastHealth + HEAL_EPSILON) {
                    healed = true;
                }
                state.lastHealth = currentHealth;
                state.hasHealth = true;
            }

            EffectControllerComponent effectController =
                    store.getComponent(ref, EffectControllerComponent.getComponentType());
            int[] currentEffects = effectController == null ? new int[0] : safeEffectIndexes(effectController);
            boolean statusApplied = hasNewEffects(state.effectIndexes, currentEffects);
            boolean potionEffectApplied = statusApplied && hasPotionEffect(currentEffects, state.effectIndexes);
            state.effectIndexes = currentEffects;

            PlayerInventoryUtils.HeldItemContext heldCtx = PlayerInventoryUtils.getHeldItemContext(player);
            String heldItemId = "";
            int heldQty = 0;
            if (heldCtx != null && heldCtx.isValid()) {
                ItemStack held = heldCtx.getItemStack();
                heldItemId = held == null ? "" : safeString(held.getItemId());
                heldQty = held == null ? 0 : Math.max(0, held.getQuantity());
            }

            boolean potionCandidateUsed = wasPotionCandidateUsed(state, heldItemId, heldQty);
            boolean potionUse = potionCandidateUsed && (healed || statusApplied || potionEffectApplied);

            state.heldItemId = heldItemId;
            state.heldQuantity = heldQty;

            if (sprintStart) {
                applyTrigger(store, player, ref, LoreTrigger.ON_SPRINT);
            }
            if (jumpStart) {
                applyTrigger(store, player, ref, LoreTrigger.ON_JUMP);
            }
            if (sneakStart) {
                applyTrigger(store, player, ref, LoreTrigger.ON_SNEAK);
            }
            if (healed) {
                applyTrigger(store, player, ref, LoreTrigger.ON_HEAL);
            }
            if (statusApplied) {
                applyTrigger(store, player, ref, LoreTrigger.ON_STATUS_APPLY);
            }
            if (potionUse || potionEffectApplied) {
                applyTrigger(store, player, ref, LoreTrigger.ON_POTION_USE);
            }
            maybeHighlightCompatibleSpirits(store, player, ref, state);
        } catch (Throwable t) {
            System.err.println("[SocketReforge] LorePlayerStateEST tick error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void applyTrigger(Store<EntityStore> store, Player player, Ref<EntityStore> playerRef, LoreTrigger trigger) {
        if (store == null || player == null || trigger == null) {
            return;
        }

        Set<String> used = new HashSet<>();
        LoreProcHandler.ProcState procState = new LoreProcHandler.ProcState();
        List<ItemEntry> entries = new ArrayList<>();

        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(player);
        if (ctx != null && ctx.isValid()) {
            ItemStack weapon = ctx.getItemStack();
            if (weapon != null && !weapon.isEmpty() && LoreSocketManager.isEquipment(weapon)) {
                LoreSocketData data = LoreSocketManager.getLoreSocketData(weapon);
                if (data != null) {
                    entries.add(ItemEntry.held(ctx, data));
                    boolean changed = LoreSocketManager.syncSocketColors(weapon, data);
                    changed |= LoreProcHandler.applyLoreSockets(store, player, playerRef, null,
                            null, data, trigger, true, used, procState);
                    if (changed) {
                        updateHeldItem(player, ctx, LoreSocketManager.withLoreSocketData(weapon, data));
                    }
                }
            }
        }

        LoreProcHandler.applyAbsorbed(store, player, playerRef, null,
                null, trigger, true, used, procState);

        if (procState.hasTriggered()) {
            if (entries.isEmpty()) {
                LoreProcHandler.applyLoreProcChain(store, player, playerRef, null, null,
                        null, true, used);
            } else {
                for (ItemEntry entry : entries) {
                    if (entry == null || entry.data == null) {
                        continue;
                    }
                    boolean changed = LoreProcHandler.applyLoreProcChain(store, player, playerRef, null, null,
                            entry.data, true, used);
                    if (changed) {
                        entry.update(player);
                    }
                }
            }
        }
    }

    private void maybeHighlightCompatibleSpirits(Store<EntityStore> store,
                                                 Player player,
                                                 Ref<EntityStore> playerRef,
                                                 PlayerState state) {
        if (store == null || player == null || playerRef == null || state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - state.lastProximityCheckMs < PROXIMITY_SCAN_INTERVAL_MS) {
            return;
        }

        Set<String> colors = collectPendingGemColors(player);
        if (colors.isEmpty()) {
            state.lastProximityCheckMs = now;
            return;
        }

        TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        Vector3d playerPos = playerTransform == null ? null : playerTransform.getPosition();
        if (playerPos == null) {
            state.lastProximityCheckMs = now;
            return;
        }

        double px = playerPos.getX();
        double py = playerPos.getY();
        double pz = playerPos.getZ();
        if (state.hasLastProximityPos) {
            double dx = px - state.lastProximityX;
            double dy = py - state.lastProximityY;
            double dz = pz - state.lastProximityZ;
            if ((dx * dx) + (dy * dy) + (dz * dz) < PROXIMITY_MIN_MOVE_SQ) {
                state.lastProximityCheckMs = now;
                return;
            }
        }
        var npcType = NPCEntity.getComponentType();
        var transformType = TransformComponent.getComponentType();

        Query<EntityStore> query = resolveNpcQuery();
        if (query == null) {
            state.lastProximityCheckMs = now;
            return;
        }

        final int[] inspected = new int[]{0};
        final boolean[] found = new boolean[]{false};
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (inspected[0]++ >= PROXIMITY_MAX_NPC_CHECKS) {
                    return false;
                }
                NPCEntity npc = chunk.getComponent(i, npcType);
                if (npc == null) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d npcPos = npcTransform.getPosition();
                if (npcPos == null) {
                    continue;
                }
                if (distanceSquared(px, py, pz, npcPos) > PROXIMITY_RADIUS_SQ) {
                    continue;
                }
                Role role = npc.getRole();
                if (role == null) {
                    continue;
                }
                String roleId = safeString(role.getRoleName());
                if (roleId.isBlank()) {
                    continue;
                }
                String assignedColor = LoreGemRegistry.getSpiritAssignedColor(roleId);
                if (assignedColor == null || assignedColor.isBlank()) {
                    assignedColor = LoreGemRegistry.resolveSpiritColor(roleId);
                }
                if (assignedColor == null || assignedColor.isBlank()) {
                    continue;
                }
                if (!colors.contains(assignedColor.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                found[0] = true;
                return false;
            }
            return true;
        });

        if (found[0] && now - state.lastProximityMessageMs >= PROXIMITY_MESSAGE_INTERVAL_MS) {
            sendProximityMessage(player);
            state.lastProximityMessageMs = now;
        }

        state.lastProximityCheckMs = now;
        state.hasLastProximityPos = true;
        state.lastProximityX = px;
        state.lastProximityY = py;
        state.lastProximityZ = pz;
    }

    private static Query<EntityStore> resolveNpcQuery() {
        Query<EntityStore> cached = npcQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (NPC_QUERY_LOCK) {
            cached = npcQuery;
            if (cached != null) {
                return cached;
            }
            var npcType = NPCEntity.getComponentType();
            var transformType = TransformComponent.getComponentType();
            if (npcType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(npcType, transformType);
                npcQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Set<String> collectPendingGemColors(Player player) {
        Set<String> colors = new HashSet<>();
        if (player == null) {
            return colors;
        }

        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(player);
        if (ctx != null && ctx.isValid()) {
            ItemStack held = ctx.getItemStack();
            addPendingGemColorsFromItem(held, colors);
        }

        return colors;
    }

    private static void addPendingGemColorsFromItem(ItemStack item, Set<String> colors) {
        if (item == null || item.isEmpty() || !LoreSocketManager.isEquipment(item)) {
            return;
        }
        LoreSocketData data = LoreSocketManager.getLoreSocketData(item);
        if (data == null) {
            return;
        }
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || socket.hasSpirit() || socket.isLocked()) {
                continue;
            }
            String gemItemId = safeString(socket.getGemItemId());
            if (gemItemId.isBlank()) {
                continue;
            }
            String socketColor = safeString(socket.getColor());
            String gemColor = LoreGemRegistry.resolveColor(gemItemId);
            String effective = null;
            if (gemColor != null && !gemColor.isBlank()) {
                if (!socketColor.isBlank() && !socketColor.equalsIgnoreCase(gemColor)) {
                    continue;
                }
                effective = gemColor;
            } else if (!socketColor.isBlank()) {
                effective = socketColor;
            }
            if (effective != null && !effective.isBlank()) {
                colors.add(effective.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static double distanceSquared(double px, double py, double pz, Vector3d pos) {
        double dx = pos.getX() - px;
        double dy = pos.getY() - py;
        double dz = pos.getZ() - pz;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private static void applyHighlightEffect(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             EntityEffect effect) {
        if (store == null || targetRef == null || effect == null) {
            return;
        }
        try {
            EffectControllerComponent controller =
                    store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            controller.addEffect(targetRef, effect, PROXIMITY_EFFECT_DURATION, OverlapBehavior.OVERWRITE, store);
        } catch (Throwable ignored) {
            // Best effort visual cue.
        }
    }

    private static void sendProximityMessage(Player player) {
        if (player == null) {
            return;
        }
        String langCode = LangLoader.getPlayerLanguage(player);
        String message = LangLoader.resolveTranslation(PROXIMITY_MESSAGE_KEY, langCode);
        if (message == null || message.isBlank() || message.equals(PROXIMITY_MESSAGE_KEY)) {
            message = "You sense a compatible spirit nearby.";
        }
        player.sendMessage(Message.raw(message));
    }

    private static EntityEffect resolveHighlightEffect() {
        if (!highlightResolved || highlightEffectIndex < 0) {
            long now = System.currentTimeMillis();
            if (now - lastHighlightResolveMs < HIGHLIGHT_RESOLVE_INTERVAL_MS) {
                return null;
            }
            synchronized (HIGHLIGHT_LOCK) {
                if (!highlightResolved || highlightEffectIndex < 0) {
                    now = System.currentTimeMillis();
                    if (now - lastHighlightResolveMs < HIGHLIGHT_RESOLVE_INTERVAL_MS) {
                        return null;
                    }
                    lastHighlightResolveMs = now;
                    highlightEffectIndex = findHighlightEffectIndex();
                    highlightResolved = highlightEffectIndex >= 0;
                }
            }
        }
        if (highlightEffectIndex < 0) {
            return null;
        }
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            return assetMap.getAsset(highlightEffectIndex);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int findHighlightEffectIndex() {
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return -1;
            }
            var map = assetMap.getAssetMap();
            if (map == null || map.isEmpty()) {
                return -1;
            }

            for (String effectId : HIGHLIGHT_FALLBACK_IDS) {
                if (effectId == null || effectId.isBlank()) {
                    continue;
                }
                int preferred = assetMap.getIndex(effectId);
                if (preferred >= 0) {
                    return preferred;
                }
            }

            String bestId = null;
            int bestScore = 0;
            for (EntityEffect effect : map.values()) {
                if (effect == null) {
                    continue;
                }
                String id = safeString(effect.getId());
                String name = safeString(effect.getName());
                int score = scoreHighlightEffect(id, name);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = id;
                }
            }

            if (bestId == null || bestId.isBlank()) {
                return -1;
            }
            int index = assetMap.getIndex(bestId);
            return index < 0 ? -1 : index;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int scoreHighlightEffect(String id, String name) {
        if ((id == null || id.isBlank()) && (name == null || name.isBlank())) {
            return 0;
        }
        String idLower = safeString(id).toLowerCase(Locale.ROOT);
        String nameLower = safeString(name).toLowerCase(Locale.ROOT);
        int score = 0;
        score += tokenScore(idLower, nameLower, "glow", 60);
        score += tokenScore(idLower, nameLower, "outline", 55);
        score += tokenScore(idLower, nameLower, "highlight", 50);
        score += tokenScore(idLower, nameLower, "target", 40);
        score += tokenScore(idLower, nameLower, "mark", 35);
        score += tokenScore(idLower, nameLower, "track", 30);
        score += tokenScore(idLower, nameLower, "focus", 25);
        score += tokenScore(idLower, nameLower, "ping", 20);

        if (score <= 0) {
            return 0;
        }
        for (String token : HIGHLIGHT_AVOID_TOKENS) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (idLower.contains(token) || nameLower.contains(token)) {
                score -= 20;
            }
        }
        return score;
    }

    private static int tokenScore(String idLower, String nameLower, String token, int base) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        int score = 0;
        if (idLower.contains(token)) {
            score += base + 5;
        }
        if (nameLower.contains(token)) {
            score += base;
        }
        return score;
    }

    private float readHealth(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return -1f;
        }
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            return -1f;
        }
        int idx = getHealthStatIndex(statMap);
        if (idx < 0) {
            return -1f;
        }
        EntityStatValue health = statMap.get(idx);
        if (health == null) {
            return -1f;
        }
        return health.get();
    }

    private int getHealthStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getHealth();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"health", "Health", "HP", "hp"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    private boolean wasPotionCandidateUsed(PlayerState state, String currentId, int currentQty) {
        if (state == null || state.heldItemId == null) {
            return false;
        }
        String previousId = state.heldItemId;
        if (!looksLikePotionItem(previousId)) {
            return false;
        }
        if (currentId != null && previousId.equalsIgnoreCase(currentId)) {
            return currentQty < state.heldQuantity;
        }
        return true;
    }

    private boolean looksLikePotionItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        for (String token : POTION_ITEM_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNewEffects(int[] previous, int[] current) {
        if (current == null || current.length == 0) {
            return false;
        }
        if (previous == null || previous.length == 0) {
            return current.length > 0;
        }
        for (int idx : current) {
            if (!contains(previous, idx)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPotionEffect(int[] current, int[] previous) {
        if (current == null || current.length == 0) {
            return false;
        }
        for (int idx : current) {
            if (previous != null && contains(previous, idx)) {
                continue;
            }
            if (isPotionEffect(idx)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPotionEffect(int effectIndex) {
        EntityEffect effect = resolveEntityEffect(effectIndex);
        if (effect == null) {
            return false;
        }
        String id = safeString(effect.getId());
        String name = safeString(effect.getName());
        return containsAny(id, POTION_EFFECT_TOKENS) || containsAny(name, POTION_EFFECT_TOKENS);
    }

    private EntityEffect resolveEntityEffect(int effectIndex) {
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            return assetMap.getAsset(effectIndex);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int[] safeEffectIndexes(EffectControllerComponent controller) {
        try {
            int[] indexes = controller.getActiveEffectIndexes();
            return indexes == null ? new int[0] : indexes;
        } catch (Throwable ignored) {
            return new int[0];
        }
    }

    private static boolean containsAny(String value, String[] tokens) {
        if (value == null || value.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token != null && !token.isBlank() && lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(int[] arr, int value) {
        if (arr == null) {
            return false;
        }
        for (int v : arr) {
            if (v == value) return true;
        }
        return false;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static void updateHeldItem(Player player, PlayerInventoryUtils.HeldItemContext ctx, ItemStack updated) {
        if (player == null || ctx == null || updated == null) {
            return;
        }
        ItemContainer container = ctx.getContainer();
        short slot = ctx.getSlot();
        if (container != null && slot >= 0 && slot < container.getCapacity()) {
            container.setItemStackForSlot(slot, updated);
            return;
        }
        PlayerInventoryUtils.setSelectedHotbarItem(player, updated);
    }

    private static final class ItemEntry {
        private final PlayerInventoryUtils.HeldItemContext heldContext;
        private final ItemContainer armorContainer;
        private final short armorSlot;
        private final LoreSocketData data;

        private ItemEntry(PlayerInventoryUtils.HeldItemContext heldContext,
                          ItemContainer armorContainer,
                          short armorSlot,
                          LoreSocketData data) {
            this.heldContext = heldContext;
            this.armorContainer = armorContainer;
            this.armorSlot = armorSlot;
            this.data = data;
        }

        static ItemEntry held(PlayerInventoryUtils.HeldItemContext ctx, LoreSocketData data) {
            return new ItemEntry(ctx, null, (short) -1, data);
        }

        static ItemEntry armor(ItemContainer container, short slot, LoreSocketData data) {
            return new ItemEntry(null, container, slot, data);
        }

        void update(Player player) {
            if (data == null) {
                return;
            }
            if (heldContext != null && heldContext.isValid()) {
                ItemStack current = heldContext.getItemStack();
                if (current != null && !current.isEmpty()) {
                    updateHeldItem(player, heldContext, LoreSocketManager.withLoreSocketData(current, data));
                }
                return;
            }
            if (armorContainer != null && armorSlot >= 0) {
                ItemStack current = armorContainer.getItemStack(armorSlot);
                if (current != null && !current.isEmpty()) {
                    armorContainer.setItemStackForSlot(armorSlot, LoreSocketManager.withLoreSocketData(current, data));
                }
            }
        }
    }
}
