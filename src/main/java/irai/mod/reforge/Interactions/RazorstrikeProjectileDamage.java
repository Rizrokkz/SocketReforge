package irai.mod.reforge.Interactions;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Entity.Events.DamageStackDelayEST;

/** Keeps Razorstrike's secondary rock impact damage tied to the weapon's unrefined base. */
public final class RazorstrikeProjectileDamage {
    private static final long MARKER_TTL_MILLIS = 8_000L;
    private static final ConcurrentHashMap<ProjectileKey, Marker> ROCK_PROJECTILES =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<OwnerKey, CastMarker> RECENT_RAZORSTRIKE_CASTS =
            new ConcurrentHashMap<>();

    private RazorstrikeProjectileDamage() {}

    /** Captures a cast-level fallback so projectile hits can still be normalized if spawn tracking misses. */
    public static void registerRazorstrikeCast(Ref<EntityStore> ownerRef,
                                               float weaponBaseDamage,
                                               float projectileDamageAttribute) {
        if (ownerRef == null || !ownerRef.isValid()
                || !Float.isFinite(weaponBaseDamage) || weaponBaseDamage <= 0.0f
                || !Float.isFinite(projectileDamageAttribute) || projectileDamageAttribute <= 0.0f) {
            return;
        }
        Store<EntityStore> store = ownerRef.getStore();
        if (store == null) {
            return;
        }
        UUIDComponent uuidComponent = store.getComponent(ownerRef, UUIDComponent.getComponentType());
        if (uuidComponent == null || uuidComponent.getUuid() == null) {
            return;
        }

        OwnerKey key = new OwnerKey(store, uuidComponent.getUuid());
        CastMarker marker = new CastMarker(weaponBaseDamage, projectileDamageAttribute,
                System.currentTimeMillis() + MARKER_TTL_MILLIS);
        RECENT_RAZORSTRIKE_CASTS.put(key, marker);
        if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
            System.out.printf(java.util.Locale.ROOT,
                    "[SocketReforge][DamageCalc] stage=razorstrike-cast-register owner=%s "
                            + "base=%.4f projectileDamage=%.4f%n",
                    uuidComponent.getUuid(), weaponBaseDamage, projectileDamageAttribute);
        }
        CompletableFuture.delayedExecutor(MARKER_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> RECENT_RAZORSTRIKE_CASTS.remove(key, marker));
    }

    public static void registerRockProjectile(Ref<EntityStore> projectileRef, float weaponBaseDamage) {
        if (projectileRef == null || !projectileRef.isValid()
                || !Float.isFinite(weaponBaseDamage) || weaponBaseDamage <= 0.0f) {
            return;
        }

        ProjectileKey key = keyFor(projectileRef);
        if (key == null) {
            return;
        }
        Marker marker = new Marker(weaponBaseDamage, System.currentTimeMillis() + MARKER_TTL_MILLIS);
        ROCK_PROJECTILES.put(key, marker);
        if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
            System.out.printf(java.util.Locale.ROOT,
                    "[SocketReforge][DamageCalc] stage=razorstrike-rock-register projectileIndex=%d "
                            + "base=%.4f%n",
                    key.index, weaponBaseDamage);
        }
        CompletableFuture.delayedExecutor(MARKER_TTL_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> ROCK_PROJECTILES.remove(key, marker));
    }

    /**
     * Native projectile collision damage scales with launch energy. For Razorstrike rocks that
     * default Earth hit overwhelms the authored hit interaction, so normalize it to the weapon's
     * captured base damage before refinement and other equipment bonuses run.
     */
    public static boolean normalizeEarthImpact(Damage damage, Store<EntityStore> store) {
        if (damage == null || damage.getCause() == null
                || !"earth".equalsIgnoreCase(damage.getCause().getId())
                || !(damage.getSource() instanceof Damage.ProjectileSource projectileSource)) {
            return false;
        }

        Ref<EntityStore> projectileRef = projectileSource.getProjectile();
        if (projectileRef == null || !projectileRef.isValid()) {
            return false;
        }
        ProjectileKey key = keyFor(projectileRef);
        if (key == null) {
            return false;
        }
        Marker marker = ROCK_PROJECTILES.get(key);
        if (marker != null && System.currentTimeMillis() > marker.expiresAtMillis) {
            ROCK_PROJECTILES.remove(key, marker);
            marker = null;
        }

        String matchedBy = "projectile";
        float weaponBaseDamage = marker == null ? 0.0f : marker.weaponBaseDamage;
        if (weaponBaseDamage <= 0.0f) {
            CastMarker castMarker = findCastMarker(store, projectileSource, projectileRef);
            if (castMarker != null) {
                weaponBaseDamage = castMarker.weaponBaseDamage;
                matchedBy = "cast-snapshot";
            }
        }
        if (weaponBaseDamage <= 0.0f) {
            if (DamageStackDelayEST.isDamageCalculationDebugEnabled() && damage.getAmount() >= 100.0f) {
                System.out.printf(java.util.Locale.ROOT,
                        "[SocketReforge][DamageCalc] stage=razorstrike-rock-marker-miss "
                                + "amount=%.4f projectileIndex=%d%n",
                        damage.getAmount(), key.index);
            }
            return false;
        }

        float before = damage.getAmount();
        damage.setAmount(weaponBaseDamage);
        if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
            System.out.printf(java.util.Locale.ROOT,
                    "[SocketReforge][DamageCalc] stage=razorstrike-rock-base before=%.4f after=%.4f "
                            + "projectileIndex=%d matchedBy=%s%n",
                    before, weaponBaseDamage, key.index, matchedBy);
        }
        return true;
    }

    private static CastMarker findCastMarker(Store<EntityStore> store,
                                             Damage.ProjectileSource projectileSource,
                                             Ref<EntityStore> projectileRef) {
        if (store == null || projectileSource == null || projectileRef == null) {
            return null;
        }
        Ref<EntityStore> ownerRef = projectileSource.getRef();
        if (ownerRef == null || !ownerRef.isValid()) {
            return null;
        }
        UUIDComponent ownerUuid = store.getComponent(ownerRef, UUIDComponent.getComponentType());
        ImpactModifiers projectileModifiers = store.getComponent(
                projectileRef, ImpactModifiers.getComponentType());
        if (ownerUuid == null || ownerUuid.getUuid() == null || projectileModifiers == null) {
            return null;
        }

        OwnerKey ownerKey = new OwnerKey(store, ownerUuid.getUuid());
        CastMarker castMarker = RECENT_RAZORSTRIKE_CASTS.get(ownerKey);
        if (castMarker == null) {
            return null;
        }
        if (System.currentTimeMillis() > castMarker.expiresAtMillis) {
            RECENT_RAZORSTRIKE_CASTS.remove(ownerKey, castMarker);
            return null;
        }

        float projectileDamage = projectileModifiers.getAttribute("Damage", Float.NaN);
        float tolerance = Math.max(0.01f, Math.abs(castMarker.projectileDamageAttribute) * 0.001f);
        return Float.isFinite(projectileDamage)
                && Math.abs(projectileDamage - castMarker.projectileDamageAttribute) <= tolerance
                ? castMarker : null;
    }

    private static ProjectileKey keyFor(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        Store<EntityStore> store = ref.getStore();
        return store == null ? null : new ProjectileKey(store, ref.getIndex());
    }

    /** Ref wrappers are not value-equal, so use the underlying store identity and entity index. */
    private static final class ProjectileKey {
        private final Store<EntityStore> store;
        private final int index;

        private ProjectileKey(Store<EntityStore> store, int index) {
            this.store = store;
            this.index = index;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ProjectileKey key && store == key.store && index == key.index;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(store) + index;
        }
    }

    private static final class OwnerKey {
        private final Store<EntityStore> store;
        private final UUID uuid;

        private OwnerKey(Store<EntityStore> store, UUID uuid) {
            this.store = store;
            this.uuid = uuid;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OwnerKey key && store == key.store && uuid.equals(key.uuid);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(store) + uuid.hashCode();
        }
    }

    private record Marker(float weaponBaseDamage, long expiresAtMillis) {}
    private record CastMarker(float weaponBaseDamage, float projectileDamageAttribute, long expiresAtMillis) {}
}
