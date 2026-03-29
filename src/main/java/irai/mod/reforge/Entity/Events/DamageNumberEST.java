package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.CombatTextUpdate;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.EntityUIType;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.UIComponentsUpdate;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumberMeta;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumbers;
import irai.mod.reforge.Lore.LoreTargetingUtils;

public class DamageNumberEST extends DamageEventSystem {
    private static final float NON_DOT_RANDOM_JITTER_DEGREES = 240f;
    private static final double FLOATING_DAMAGE_DEFAULT_HEIGHT = 1.4d;
    private static final double FLOATING_DAMAGE_Y_OFFSET = 0.15d;
    private static final double FLOATING_DAMAGE_SPREAD = 0.3d;
    private static final double FLOATING_DAMAGE_DIGIT_SPACING = 0.08d;
    private static final double FLOATING_DAMAGE_ICON_SPACING = 0.22d;
    private static final double FLOATING_DAMAGE_FRONT_OFFSET = 0.35d;
    private static final double FLOATING_DAMAGE_FAR_DISTANCE = 12.0d;
    private static final double FLOATING_DAMAGE_FAR_FRONT_OFFSET = 2.0d;
    private static final double FLOATING_DAMAGE_FAR_Y_OFFSET = 1.2d;
    private static final Color WHITE_COLOR = new Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
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
        Visible visible = null;
        if (visibleComponentType != null) {
            visible = (Visible) commandBuffer.getComponent(targetRef, visibleComponentType);
            if (visible == null) {
                visible = store.getComponent(targetRef, visibleComponentType);
            }
        } else {
            debug("[DamageNumberEST] visibleComponentType is null");
        }
        String kindId = resolveKind(damage);
        applyCombatTextVfx(store, targetRef, attackerRef, visible, kindId);
        if (trySpawnParticleFloatingDamage(store, targetRef, attackerRef, visible, damage.getAmount(), kindId)) {
            return;
        }
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
        EntityViewer[] viewers = resolveViewers(commandBuffer, visible, attackerRef);
        if (viewers.length == 0) {
            debug("[DamageNumberEST] skip no viewers (source=" + source + ")");
            return;
        }
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

    private static void applyCombatTextVfx(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           Ref<EntityStore> attackerRef,
                                           Visible visible,
                                           String kindId) {
        if (store == null || targetRef == null || kindId == null || kindId.isBlank()) {
            return;
        }
        DamageNumbers.KindStyle style = DamageNumbers.getKindStyle(kindId);
        if (style == null || style.vfxId() == null || style.vfxId().isBlank()) {
            return;
        }
        try {
            String vfxId = style.vfxId();
            ParticleSystem particleSystem = resolveParticleSystem(vfxId);
            if (particleSystem != null) {
                spawnCombatTextParticles(store, targetRef, attackerRef, visible, particleSystem.getId());
                return;
            }
            EntityEffect effect = resolveEntityEffect(vfxId);
            if (effect == null) {
                return;
            }
            EffectControllerComponent controller =
                    store.ensureAndGetComponent(targetRef, EffectControllerComponent.getComponentType());
            if (controller == null) {
                return;
            }
            controller.addEffect(targetRef, effect, store);
        } catch (Throwable ignored) {
            // VFX is optional; fail silently.
        }
    }

    private static ParticleSystem resolveParticleSystem(String systemId) {
        if (systemId == null || systemId.isBlank()) {
            return null;
        }
        try {
            var assetMap = ParticleSystem.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            return assetMap.getAsset(systemId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static EntityEffect resolveEntityEffect(String effectId) {
        if (effectId == null || effectId.isBlank()) {
            return null;
        }
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            int index = assetMap.getIndex(effectId);
            if (index < 0) {
                return null;
            }
            return assetMap.getAsset(index);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void spawnCombatTextParticles(Store<EntityStore> store,
                                                 Ref<EntityStore> targetRef,
                                                 Ref<EntityStore> attackerRef,
                                                 Visible visible,
                                                 String particleSystemId) {
        if (store == null || particleSystemId == null || particleSystemId.isBlank()) {
            return;
        }
        Vector3d pos = LoreTargetingUtils.resolveCenterPosition(store, targetRef, attackerRef);
        if (pos == null) {
            return;
        }
        List<Ref<EntityStore>> viewers = collectViewerRefsForParticles(store, visible, attackerRef);
        try {
            if (viewers == null || viewers.isEmpty()) {
                ParticleUtil.spawnParticleEffect(particleSystemId, pos, store);
            } else {
                ParticleUtil.spawnParticleEffect(particleSystemId, pos, viewers, store);
            }
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static boolean trySpawnParticleFloatingDamage(Store<EntityStore> store,
                                                          Ref<EntityStore> targetRef,
                                                          Ref<EntityStore> attackerRef,
                                                          Visible visible,
                                                          float amount,
                                                          String kindId) {
        if (store == null || targetRef == null) {
            return false;
        }
        DamageNumbers.KindStyle style = DamageNumbers.getKindStyle(kindId);
        if (style == null || style.particleFontId() == null || style.particleFontId().isBlank()) {
            return false;
        }
        String rawText = DamageNumbers.formatAmountOnly(amount, kindId);
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        String digits = rawText.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return false;
        }
        Vector3d base = resolveFloatingDamagePosition(store, targetRef, attackerRef);
        if (base == null) {
            return false;
        }
        double jitterX = (ThreadLocalRandom.current().nextDouble() * 2.0d - 1.0d) * FLOATING_DAMAGE_SPREAD;
        double jitterZ = (ThreadLocalRandom.current().nextDouble() * 2.0d - 1.0d) * FLOATING_DAMAGE_SPREAD;
        Vector3d origin = new Vector3d(base.x + jitterX, base.y, base.z + jitterZ);
        List<Ref<EntityStore>> viewers = collectViewerRefsForParticles(store, visible, attackerRef);

        boolean hasIcon = style.particleIconId() != null && !style.particleIconId().isBlank();
        double digitSpan = digits.length() > 1 ? (digits.length() - 1) * FLOATING_DAMAGE_DIGIT_SPACING : 0.0d;
        double iconGap = hasIcon ? FLOATING_DAMAGE_ICON_SPACING : 0.0d;
        double groupWidth = digitSpan + iconGap;
        Color digitColor = resolveParticleColor(style.colorHex());

        if (viewers == null || viewers.isEmpty()) {
            String backgroundId = style.particleBackgroundId();
            if (backgroundId != null && !backgroundId.isBlank()) {
                spawnParticleSystem(store, backgroundId, origin, viewers, null);
            }
            double startX = origin.x - (groupWidth / 2.0d) + iconGap;
            for (int i = 0; i < digits.length(); i++) {
                char digit = digits.charAt(i);
                if (digit < '0' || digit > '9') {
                    continue;
                }
                String systemId = style.particleFontId() + "_Digit_" + digit;
                Vector3d pos = new Vector3d(startX + (i * FLOATING_DAMAGE_DIGIT_SPACING), origin.y, origin.z);
                spawnParticleSystem(store, systemId, pos, viewers, digitColor);
            }
            if (hasIcon) {
                String iconId = style.particleIconId();
                Vector3d iconPos = new Vector3d(origin.x - (groupWidth / 2.0d), origin.y, origin.z);
                spawnParticleSystem(store, iconId, iconPos, viewers, null);
            }
            return true;
        }

        for (Ref<EntityStore> viewerRef : viewers) {
            if (viewerRef == null || !viewerRef.isValid()) {
                continue;
            }
            Vector3d viewerPos = resolveViewerPosition(store, viewerRef);
            Vector3d viewerForward = resolveViewerForward(store, viewerRef);
            Vector3d viewerOrigin = viewerPos == null
                    ? origin
                    : resolveViewerOrigin(origin, viewerPos, viewerForward);
            float scale = 1.0f;
            List<Ref<EntityStore>> singleViewer = java.util.Collections.singletonList(viewerRef);

            String backgroundId = style.particleBackgroundId();
            if (backgroundId != null && !backgroundId.isBlank()) {
                spawnParticleSystem(store, backgroundId, viewerOrigin, singleViewer, null);
            }

            Vector3d right = computeRightVector(viewerOrigin, viewerPos);
            Vector3d iconBase = offsetBy(viewerOrigin, right, -groupWidth / 2.0d);
            Vector3d digitBase = offsetBy(viewerOrigin, right, (-groupWidth / 2.0d) + iconGap);
            for (int i = 0; i < digits.length(); i++) {
                char digit = digits.charAt(i);
                if (digit < '0' || digit > '9') {
                    continue;
                }
                String systemId = style.particleFontId() + "_Digit_" + digit;
                Vector3d pos = offsetBy(digitBase, right, i * FLOATING_DAMAGE_DIGIT_SPACING);
                spawnParticleSystemScaled(store, systemId, pos, singleViewer, digitColor, scale);
            }

            if (hasIcon) {
                String iconId = style.particleIconId();
                Vector3d iconPos = iconBase;
                spawnParticleSystem(store, iconId, iconPos, singleViewer, null);
            }
        }

        return true;
    }

    private static Vector3d resolveFloatingDamagePosition(Store<EntityStore> store,
                                                          Ref<EntityStore> targetRef,
                                                          Ref<EntityStore> attackerRef) {
        Vector3d pos = LoreTargetingUtils.getPosition(store, targetRef);
        if (pos == null) {
            pos = LoreTargetingUtils.resolveCenterPosition(store, targetRef, attackerRef);
        }
        if (pos == null) {
            return null;
        }
        double height = FLOATING_DAMAGE_DEFAULT_HEIGHT;
        try {
            BoundingBox bbox = store.getComponent(targetRef, BoundingBox.getComponentType());
            if (bbox != null && bbox.getBoundingBox() != null) {
                var box = bbox.getBoundingBox();
                if (box != null && box.max != null) {
                    double candidate = box.max.y;
                    if (candidate > 0.01d) {
                        height = candidate;
                    }
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return new Vector3d(pos.x, pos.y + height + FLOATING_DAMAGE_Y_OFFSET, pos.z);
    }

    private static void spawnParticleSystem(Store<EntityStore> store,
                                            String systemId,
                                            Vector3d pos,
                                            List<Ref<EntityStore>> viewers,
                                            Color color) {
        if (store == null || systemId == null || systemId.isBlank() || pos == null) {
            return;
        }
        try {
            if (color != null && viewers != null && !viewers.isEmpty()) {
                ParticleUtil.spawnParticleEffect(systemId, pos, 0f, 0f, 0f, 1f, color, viewers, store);
                return;
            }
            if (viewers == null || viewers.isEmpty()) {
                ParticleUtil.spawnParticleEffect(systemId, pos, store);
                return;
            }
            ParticleUtil.spawnParticleEffect(systemId, pos, viewers, store);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static void spawnParticleSystemScaled(Store<EntityStore> store,
                                                  String systemId,
                                                  Vector3d pos,
                                                  List<Ref<EntityStore>> viewers,
                                                  Color color,
                                                  float scale) {
        if (store == null || systemId == null || systemId.isBlank() || pos == null) {
            return;
        }
        if (viewers == null || viewers.isEmpty()) {
            spawnParticleSystem(store, systemId, pos, viewers, color);
            return;
        }
        float safeScale = scale > 0f ? scale : 1f;
        Color useColor = color != null ? color : WHITE_COLOR;
        try {
            ParticleUtil.spawnParticleEffect(systemId, pos, 0f, 0f, 0f, safeScale, useColor, viewers, store);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static Vector3d resolveViewerPosition(Store<EntityStore> store, Ref<EntityStore> viewerRef) {
        if (store == null || viewerRef == null || !viewerRef.isValid()) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(viewerRef, TransformComponent.getComponentType());
            if (transform == null) {
                return null;
            }
            Vector3d pos = transform.getPosition();
            return pos == null ? null : pos.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vector3d resolveViewerForward(Store<EntityStore> store, Ref<EntityStore> viewerRef) {
        if (store == null || viewerRef == null || !viewerRef.isValid()) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(viewerRef, TransformComponent.getComponentType());
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
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Vector3d resolveViewerOrigin(Vector3d origin,
                                                Vector3d viewerPos,
                                                Vector3d viewerForward) {
        if (origin == null || viewerPos == null) {
            return origin;
        }
        double distance = distanceBetween(origin, viewerPos);
        if (distance > FLOATING_DAMAGE_FAR_DISTANCE) {
            Vector3d forward = viewerForward;
            if (forward == null) {
                forward = directionFromTo(viewerPos, origin);
            }
            Vector3d horizontalForward = normalizeHorizontal(forward);
            if (horizontalForward == null) {
                horizontalForward = new Vector3d(0.0d, 0.0d, 1.0d);
            }
            return new Vector3d(
                    viewerPos.x + (horizontalForward.x * FLOATING_DAMAGE_FAR_FRONT_OFFSET),
                    viewerPos.y + FLOATING_DAMAGE_FAR_Y_OFFSET,
                    viewerPos.z + (horizontalForward.z * FLOATING_DAMAGE_FAR_FRONT_OFFSET)
            );
        }
        return offsetTowardViewer(origin, viewerPos);
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

    private static Vector3d directionFromTo(Vector3d from, Vector3d to) {
        if (from == null || to == null) {
            return null;
        }
        return new Vector3d(to.x - from.x, to.y - from.y, to.z - from.z);
    }

    private static Vector3d normalizeHorizontal(Vector3d direction) {
        if (direction == null) {
            return null;
        }
        double dx = direction.x;
        double dz = direction.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 0.0001d) {
            return null;
        }
        return new Vector3d(dx / len, 0.0d, dz / len);
    }

    private static Vector3d offsetTowardViewer(Vector3d origin, Vector3d viewerPos) {
        if (origin == null || viewerPos == null) {
            return origin;
        }
        double dx = viewerPos.x - origin.x;
        double dz = viewerPos.z - origin.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 0.0001d) {
            return origin;
        }
        double nx = dx / len;
        double nz = dz / len;
        return new Vector3d(origin.x + (nx * FLOATING_DAMAGE_FRONT_OFFSET), origin.y, origin.z + (nz * FLOATING_DAMAGE_FRONT_OFFSET));
    }

    private static Vector3d computeRightVector(Vector3d origin, Vector3d viewerPos) {
        if (origin == null || viewerPos == null) {
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        double dx = viewerPos.x - origin.x;
        double dz = viewerPos.z - origin.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len <= 0.0001d) {
            return new Vector3d(1.0d, 0.0d, 0.0d);
        }
        double nx = dx / len;
        double nz = dz / len;
        // Right vector for the viewer's perspective (perpendicular on the horizontal plane).
        return new Vector3d(nz, 0.0d, -nx);
    }

    private static Vector3d offsetBy(Vector3d origin, Vector3d dir, double distance) {
        if (origin == null || dir == null) {
            return origin;
        }
        return new Vector3d(origin.x + (dir.x * distance), origin.y + (dir.y * distance), origin.z + (dir.z * distance));
    }

    private static double distanceBetween(Vector3d a, Vector3d b) {
        if (a == null || b == null) {
            return 0.0d;
        }
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Color resolveParticleColor(String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return null;
        }
        String hex = colorHex.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            byte r = (byte) ((rgb >> 16) & 0xFF);
            byte g = (byte) ((rgb >> 8) & 0xFF);
            byte b = (byte) (rgb & 0xFF);
            return new Color(r, g, b);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<Ref<EntityStore>> collectViewerRefs(Visible visible,
                                                            Ref<EntityStore> fallbackRef) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        if (visible != null) {
            addViewerRefs(refs, visible.visibleTo);
            if (refs.isEmpty()) {
                addViewerRefs(refs, visible.newlyVisibleTo);
            }
            if (refs.isEmpty()) {
                addViewerRefs(refs, visible.previousVisibleTo);
            }
        }
        if ((refs.isEmpty()) && fallbackRef != null && fallbackRef.isValid()) {
            refs.add(fallbackRef);
        }
        return refs;
    }

    private static List<Ref<EntityStore>> collectViewerRefsForParticles(Store<EntityStore> store,
                                                                        Visible visible,
                                                                        Ref<EntityStore> fallbackRef) {
        List<Ref<EntityStore>> refs = collectViewerRefs(visible, null);
        if (!refs.isEmpty()) {
            return refs;
        }
        if (store != null && isPlayerRef(store, fallbackRef)) {
            refs.add(fallbackRef);
            return refs;
        }
        if (store == null) {
            return refs;
        }
        return collectAllPlayerRefs(store);
    }

    private static boolean isPlayerRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }
        try {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            return playerRef != null && playerRef.isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static List<Ref<EntityStore>> collectAllPlayerRefs(Store<EntityStore> store) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        if (store == null) {
            return refs;
        }
        ComponentType<EntityStore, PlayerRef> playerType = PlayerRef.getComponentType();
        if (playerType == null) {
            return refs;
        }
        store.forEachChunk(playerType, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref != null && ref.isValid()) {
                    refs.add(ref);
                }
            }
        });
        return refs;
    }

    private static void addViewerRefs(List<Ref<EntityStore>> refs,
                                      Map<Ref<EntityStore>, EntityViewer> viewerMap) {
        if (refs == null || viewerMap == null || viewerMap.isEmpty()) {
            return;
        }
        for (Ref<EntityStore> ref : viewerMap.keySet()) {
            if (ref == null || !ref.isValid()) {
                continue;
            }
            refs.add(ref);
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
        if (trySpawnParticleFloatingDamage(store, targetRef, null, visible, amount, kindId)) {
            return;
        }
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
