package irai.mod.reforge.Entity.Events;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.entityui.EntityUIModule;
import com.hypixel.hytale.server.core.modules.entityui.UIComponentList;
import com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.EntityUIType;
import com.hypixel.hytale.protocol.UIComponentsUpdate;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumberMeta;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumbers;

public class DamageNumberEST extends DamageEventSystem {
    private static final float NON_DOT_RANDOM_JITTER_DEGREES = 240f;
    private static final boolean DEBUG_COMBAT_TEXT = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.combatText", "false"));
    private static final HytaleLogger LOGGER = HytaleLogger.get("SocketReforge.DamageNumber");
    private static final HytaleLogger ROOT_LOGGER = HytaleLogger.getLogger();
    private static final Level DEBUG_LOG_LEVEL = Level.WARNING;

    static {
        try {
            LOGGER.setLevel(Level.ALL);
        } catch (Throwable ignored) {
            // logger may not allow level changes
        }
    }

    private volatile ComponentType<EntityStore, Visible> visibleComponentType;
    private volatile ComponentType<EntityStore, UIComponentList> uiComponentListComponentType;
    private final Query<EntityStore> query;

    public DamageNumberEST() {
        ComponentType<EntityStore, Visible> visibleType = null;
        ComponentType<EntityStore, UIComponentList> uiType = null;
        try {
            EntityModule entityModule = EntityModule.get();
            if (entityModule != null) {
                visibleType = entityModule.getVisibleComponentType();
            }
        } catch (Throwable ignored) {
            // Module may not be ready during plugin init.
        }
        try {
            EntityUIModule uiModule = EntityUIModule.get();
            if (uiModule != null) {
                uiType = uiModule.getUIComponentListType();
            }
        } catch (Throwable ignored) {
            // Module may not be ready during plugin init.
        }
        this.visibleComponentType = visibleType;
        this.uiComponentListComponentType = uiType;
        if (visibleType != null && uiType != null) {
            this.query = Query.and(visibleType, uiType);
        } else {
            this.query = Query.any();
        }
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (DEBUG_COMBAT_TEXT) {
            String causeId = "null";
            try {
                DamageCause cause = damage.getCause();
                if (cause != null && cause.getId() != null) {
                    causeId = cause.getId();
                }
            } catch (Throwable ignored) {
                // ignore cause lookup errors
            }
            String message = String.format(
                "[DamageNumberEST] handle amount=%.3f cause=%s target=%s source=%s",
                damage.getAmount(),
                causeId,
                targetRef,
                damage.getSource()
            );
            LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
            ROOT_LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
        }
        ensureComponentTypes();
        if (DamageNumberMeta.shouldSkipCombatText(damage)) {
            debug("[DamageNumberEST] skip via meta");
            return;
        }
        if (damage.getAmount() <= 0f) {
            debug("[DamageNumberEST] skip amount <= 0");
            return;
        }

        Damage.Source source = damage.getSource();
        Ref<EntityStore> attackerRef = null;
        if (source instanceof Damage.EntitySource entitySource) {
            attackerRef = entitySource.getRef();
        }

        Float hitAngle = (Float) damage.getIfPresentMetaObject(Damage.HIT_ANGLE);
        UIComponentList uiList = null;
        if (uiComponentListComponentType != null) {
            uiList = (UIComponentList) commandBuffer.getComponent(targetRef, uiComponentListComponentType);
            if (uiList == null) {
                uiList = store.getComponent(targetRef, uiComponentListComponentType);
            }
            if (uiList == null) {
                debug("[DamageNumberEST] skip missing UIComponentList on target");
                return;
            }
        } else {
            debug("[DamageNumberEST] uiComponentListComponentType is null");
        }
        Visible visible = null;
        if (visibleComponentType != null) {
            visible = (Visible) commandBuffer.getComponent(targetRef, visibleComponentType);
            if (visible == null) {
                visible = store.getComponent(targetRef, visibleComponentType);
            }
        } else {
            debug("[DamageNumberEST] visibleComponentType is null");
        }
        EntityViewer[] viewers = resolveViewers(commandBuffer, visible, attackerRef);
        if (viewers.length == 0) {
            debug("[DamageNumberEST] skip no viewers (source=" + source + ")");
            return;
        }
        String kindId = resolveKind(damage);
        float resolvedAngle = hitAngle == null ? 0f : hitAngle.floatValue();
        if (isDotKind(kindId) || hitAngle == null) {
            resolvedAngle = (ThreadLocalRandom.current().nextFloat() * 360f) - 180f;
        } else {
            // Wider jitter so normal hits can drift upward/downward as well.
            float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * NON_DOT_RANDOM_JITTER_DEGREES;
            resolvedAngle += jitter;
        }
        resolvedAngle = normalizeAngle(resolvedAngle);
        String text = DamageNumbers.format(damage.getAmount(), kindId);
        CombatTextUpdate update = new CombatTextUpdate(resolvedAngle, text);
        for (EntityViewer viewer : viewers) {
            if (viewer == null) {
                continue;
            }
            queueCombatTextComponentSwap(viewer, targetRef, uiList, kindId);
            viewer.queueUpdate(targetRef, update);
        }
    }

    private static String resolveKind(Damage damage) {
        return DamageNumbers.resolveKindId(damage);
    }

    private static void queueCombatTextComponentSwap(EntityViewer viewer,
                                                     Ref<EntityStore> targetRef,
                                                     UIComponentList uiList,
                                                     String kindId) {
        if (viewer == null || uiList == null) {
            return;
        }
        int[] baseComponentIds = uiList.getComponentIds();
        if (baseComponentIds == null || baseComponentIds.length == 0) {
            return;
        }
        IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap = EntityUIComponent.getAssetMap();
        if (assetMap == null) {
            return;
        }
        int baseCombatCount = countCombatTextComponents(baseComponentIds, assetMap);
        int desiredIndex = resolveDesiredCombatTextIndex(assetMap, kindId);
        int[] componentIds = buildSingleCombatTextList(baseComponentIds, assetMap, desiredIndex);
        if (componentIds == null || componentIds.length == 0) {
            debug("[DamageNumberEST] filtered component list empty");
            return;
        }
        int filteredCombatCount = countCombatTextComponents(componentIds, assetMap);
        if (DEBUG_COMBAT_TEXT) {
            String message = String.format(
                "[DamageNumberEST] target=%s kind=%s baseCombatText=%d filteredCombatText=%d desiredIndex=%d baseComponents=%d filteredComponents=%d",
                targetRef,
                kindId,
                baseCombatCount,
                filteredCombatCount,
                desiredIndex,
                baseComponentIds.length,
                componentIds.length
            );
            LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
            ROOT_LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
        }
        viewer.queueUpdate(targetRef, new UIComponentsUpdate(componentIds));
    }

    public static void queueCombatTextDirect(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             float amount,
                                             String kindId) {
        if (store == null || targetRef == null || amount <= 0f) {
            return;
        }
        ComponentType<EntityStore, Visible> visibleType;
        ComponentType<EntityStore, UIComponentList> uiType;
        try {
            EntityModule entityModule = EntityModule.get();
            visibleType = entityModule == null ? null : entityModule.getVisibleComponentType();
        } catch (Throwable ignored) {
            visibleType = null;
        }
        try {
            EntityUIModule uiModule = EntityUIModule.get();
            uiType = uiModule == null ? null : uiModule.getUIComponentListType();
        } catch (Throwable ignored) {
            uiType = null;
        }
        if (visibleType == null || uiType == null) {
            return;
        }
        Visible visible = store.getComponent(targetRef, visibleType);
        UIComponentList uiList = store.getComponent(targetRef, uiType);
        if (visible == null || uiList == null) {
            return;
        }
        EntityViewer[] viewers = collectViewers(visible.visibleTo);
        if (viewers.length == 0) {
            viewers = collectViewers(visible.newlyVisibleTo);
        }
        if (viewers.length == 0) {
            viewers = collectViewers(visible.previousVisibleTo);
        }
        if (viewers.length == 0) {
            return;
        }
        float angle;
        if (isDotKind(kindId)) {
            angle = (ThreadLocalRandom.current().nextFloat() * 360f) - 180f;
        } else {
            angle = (ThreadLocalRandom.current().nextFloat() - 0.5f) * NON_DOT_RANDOM_JITTER_DEGREES;
        }
        angle = normalizeAngle(angle);
        String text = DamageNumbers.format(amount, kindId);
        CombatTextUpdate update = new CombatTextUpdate(angle, text);
        for (EntityViewer viewer : viewers) {
            if (viewer == null) {
                continue;
            }
            queueCombatTextComponentSwap(viewer, targetRef, uiList, kindId);
            viewer.queueUpdate(targetRef, update);
        }
    }

    public static void queueCombatTextDirect(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             float amount,
                                             DamageNumbers.KindStyle kindStyle) {
        String kindId = kindStyle == null ? null : kindStyle.id();
        queueCombatTextDirect(store, targetRef, amount, kindId);
    }

    public static void queueCombatTextDirect(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             float amount,
                                             irai.mod.reforge.Util.DamageNumberFormatter.DamageKind kind) {
        String kindId = kind == null ? null : kind.name();
        queueCombatTextDirect(store, targetRef, amount, kindId);
    }

    private static int resolveDesiredCombatTextIndex(IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap,
                                                     String kindId) {
        if (kindId == null || kindId.isBlank()) {
            return -1;
        }
        DamageNumbers.KindStyle style = DamageNumbers.getKindStyle(kindId);
        String desiredId = style == null ? null : style.uiComponentId();
        String altId = style == null ? null : style.uiComponentAltId();
        if (altId != null && !altId.isBlank()) {
            String chosen = DamageNumbers.resolveUiComponentId(kindId, true);
            int index = chosen == null || chosen.isBlank()
                    ? -1
                    : assetMap.getIndexOrDefault(chosen, -1);
            if (index >= 0) {
                return index;
            }
            // Fallback to base if alt missing.
            return desiredId == null || desiredId.isBlank()
                    ? -1
                    : assetMap.getIndexOrDefault(desiredId, -1);
        }
        if (desiredId == null || desiredId.isBlank()) {
            return -1;
        }
        return assetMap.getIndexOrDefault(desiredId, -1);
    }

    private static int[] buildSingleCombatTextList(int[] baseComponentIds,
                                                   IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap,
                                                   int desiredIndex) {
        int[] filtered = new int[baseComponentIds.length + (desiredIndex >= 0 ? 1 : 0)];
        int count = 0;
        boolean insertedCombatText = false;
        boolean sawCombatText = false;
        for (int id : baseComponentIds) {
            if (id < 0) {
                continue;
            }
            EntityUIComponent component = assetMap.getAsset(id);
            if (component == null) {
                filtered[count++] = id;
                continue;
            }
            EntityUIType type;
            try {
                type = component.toPacket().type;
            } catch (Throwable ignored) {
                filtered[count++] = id;
                continue;
            }
            if (type == EntityUIType.CombatText) {
                sawCombatText = true;
                if (!insertedCombatText) {
                    if (desiredIndex >= 0) {
                        filtered[count++] = desiredIndex;
                    } else {
                        filtered[count++] = id;
                    }
                    insertedCombatText = true;
                }
            } else {
                filtered[count++] = id;
            }
        }
        if (!sawCombatText && desiredIndex >= 0) {
            filtered[count++] = desiredIndex;
            insertedCombatText = true;
        }
        if (count == 0) {
            return Arrays.copyOf(baseComponentIds, baseComponentIds.length);
        }
        return count == filtered.length ? filtered : Arrays.copyOf(filtered, count);
    }

    private static int countCombatTextComponents(int[] componentIds,
                                                 IndexedLookupTableAssetMap<String, EntityUIComponent> assetMap) {
        if (componentIds == null) {
            return 0;
        }
        int count = 0;
        for (int id : componentIds) {
            if (id < 0) {
                continue;
            }
            EntityUIComponent component = assetMap.getAsset(id);
            if (component == null) {
                continue;
            }
            try {
                if (component.toPacket().type == EntityUIType.CombatText) {
                    count++;
                }
            } catch (Throwable ignored) {
                // ignore malformed component entries
            }
        }
        return count;
    }

    private static float normalizeAngle(float angle) {
        if (angle > 180f) {
            angle -= 360f;
        } else if (angle < -180f) {
            angle += 360f;
        }
        return angle;
    }

    private void ensureComponentTypes() {
        if (visibleComponentType == null) {
            try {
                EntityModule entityModule = EntityModule.get();
                if (entityModule != null) {
                    visibleComponentType = entityModule.getVisibleComponentType();
                }
            } catch (Throwable ignored) {
                // ignore
            }
        }
        if (uiComponentListComponentType == null) {
            try {
                EntityUIModule uiModule = EntityUIModule.get();
                if (uiModule != null) {
                    uiComponentListComponentType = uiModule.getUIComponentListType();
                }
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }

    private static boolean isDotKind(String kindId) {
        return DamageNumbers.isDotKind(kindId);
    }

    private static EntityViewer[] resolveViewers(CommandBuffer<EntityStore> commandBuffer,
                                                 Visible visible,
                                                 Ref<EntityStore> attackerRef) {
        if (commandBuffer == null) {
            return new EntityViewer[0];
        }
        if (visible != null) {
            EntityViewer[] viewers = collectViewers(visible.visibleTo);
            if (viewers.length == 0) {
                viewers = collectViewers(visible.newlyVisibleTo);
            }
            if (viewers.length == 0) {
                viewers = collectViewers(visible.previousVisibleTo);
            }
            if (viewers.length > 0) {
                return viewers;
            }
        }
        if (attackerRef != null && attackerRef.isValid()) {
            try {
                PlayerRef playerRef = (PlayerRef) commandBuffer.getComponent(attackerRef, PlayerRef.getComponentType());
                if (playerRef != null && playerRef.isValid()) {
                    EntityViewer viewer = (EntityViewer) commandBuffer.getComponent(attackerRef, EntityViewer.getComponentType());
                    if (viewer != null) {
                        return new EntityViewer[] { viewer };
                    }
                }
            } catch (Throwable ignored) {
                // Fall through to empty.
            }
        }
        return new EntityViewer[0];
    }

    private static EntityViewer[] collectViewers(
            java.util.Map<Ref<EntityStore>, EntityViewer> viewersMap) {
        if (viewersMap == null || viewersMap.isEmpty()) {
            return new EntityViewer[0];
        }
        EntityViewer[] viewers = new EntityViewer[viewersMap.size()];
        int count = 0;
        for (EntityViewer viewer : viewersMap.values()) {
            if (viewer == null) {
                continue;
            }
            viewers[count++] = viewer;
        }
        return count == viewers.length ? viewers : Arrays.copyOf(viewers, count);
    }

    private static void debug(String message) {
        if (!DEBUG_COMBAT_TEXT) {
            return;
        }
        LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
        ROOT_LOGGER.at(DEBUG_LOG_LEVEL).log("%s", message);
    }
}
