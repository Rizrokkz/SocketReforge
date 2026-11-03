package irai.mod.reforge.Interactions;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Entity.Events.DamageStackDelayEST;
import irai.mod.reforge.Lore.LoreDamageUtils;

/** Snapshots native core-rune casts so hit interactions can scale their coefficients by weapon base damage. */
public final class CoreRuneDamageContext {
    private static final long CONTEXT_TTL_MILLIS = 8_000L;
    private static final ConcurrentHashMap<EntityKey, CastSnapshot> MELEE_CASTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EntityKey, CastSnapshot> PROJECTILE_CASTS = new ConcurrentHashMap<>();

    private CoreRuneDamageContext() {}

    /** Called by the shared rune cast interaction for both player and NPC native-rune hits. */
    public static void captureCast(Store<EntityStore> store,
                                   Ref<EntityStore> ownerRef,
                                   String ability,
                                   long activeWindowMillis) {
        captureCast(store, ownerRef, ability, activeWindowMillis, null);
    }

    /**
     * Captures a core-rune cast and records whether its authored Damage attribute already
     * contains weapon base damage. Those casts still receive equipment/refinement bonuses, but
     * must not be multiplied by weapon base a second time at hit application.
     */
    public static void captureCast(Store<EntityStore> store,
                                   Ref<EntityStore> ownerRef,
                                   String ability,
                                   long activeWindowMillis,
                                   String runeItemId) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return;
        }

        String normalizedAbility = normalizeAbility(ability);
        if (!usesRuneWeaponDamage(normalizedAbility)) {
            return;
        }

        boolean npc = NPCEntity.getComponentType() != null
                && store.getComponent(ownerRef, NPCEntity.getComponentType()) != null;
        Player player = store.getComponent(ownerRef, Player.getComponentType());
        if (!npc && player == null) {
            return;
        }

        ItemStack weapon = npc
                ? InventoryComponent.getItemInHand(store, ownerRef)
                : PlayerInventoryUtils.getHeldItem(player);
        double baseDamage = weapon == null || weapon.isEmpty() || weapon.getItem() == null
                ? 0.0d
                : EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(weapon.getItem());
        if (!Double.isFinite(baseDamage) || baseDamage <= 0.0d) {
            if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
                System.out.printf(Locale.ROOT,
                        "[SocketReforge][CoreRuneDamage] stage=cast-snapshot-miss ability=%s weapon=%s base=%.4f%n",
                        normalizedAbility, weapon == null ? "none" : weapon.getItemId(), baseDamage);
            }
            return;
        }

        EntityKey key = new EntityKey(store, ownerRef.getIndex());
        long ttlMillis = npc ? CONTEXT_TTL_MILLIS
                : Math.max(1200L, Math.min(10000L, activeWindowMillis));
        boolean damageAttributeIncludesWeaponBase = !npc
                && "Rune_GrandSlam_1".equalsIgnoreCase(runeItemId);
        CastSnapshot snapshot = new CastSnapshot(normalizedAbility, weapon.getItemId(),
                (float) baseDamage, damageAttributeIncludesWeaponBase,
                System.currentTimeMillis() + ttlMillis);
        boolean meleePath = usesMeleeDamagePath(normalizedAbility);
        boolean projectilePath = usesProjectileDamagePath(normalizedAbility);
        MELEE_CASTS.remove(key);
        PROJECTILE_CASTS.remove(key);
        if (meleePath) {
            putSnapshot(MELEE_CASTS, key, snapshot, ttlMillis);
        }
        if (projectilePath) {
            putSnapshot(PROJECTILE_CASTS, key, snapshot, ttlMillis);
        }

        if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
            System.out.printf(Locale.ROOT,
                    "[SocketReforge][CoreRuneDamage] stage=cast-snapshot ability=%s weapon=%s "
                            + "weaponBase=%.4f damageAttributeIncludesWeaponBase=%s npc=%s ttlMs=%d%n",
                    normalizedAbility, snapshot.weaponId, snapshot.weaponBaseDamage,
                    snapshot.damageAttributeIncludesWeaponBase, npc, ttlMillis);
        }
    }

    /**
     * Replaces the rune's unscaled interaction coefficient with that coefficient times the
     * weapon's native base damage. This runs before the normal player equipment bonus pipeline.
     */
    public static float getCapturedWeaponBaseDamage(Store<EntityStore> store,
                                                    Ref<EntityStore> ownerRef) {
        CastSnapshot snapshot = getActiveSnapshot(MELEE_CASTS, store, ownerRef);
        if (snapshot == null) {
            snapshot = getActiveSnapshot(PROJECTILE_CASTS, store, ownerRef);
        }
        return snapshot == null ? 0.0f : snapshot.weaponBaseDamage;
    }

    public static boolean applyWeaponBaseDamage(Store<EntityStore> store,
                                                Ref<EntityStore> ownerRef,
                                                Damage damage) {
        if (store == null || ownerRef == null || !ownerRef.isValid() || damage == null) {
            return false;
        }
        boolean npc = NPCEntity.getComponentType() != null
                && store.getComponent(ownerRef, NPCEntity.getComponentType()) != null;
        if (!npc && store.getComponent(ownerRef, Player.getComponentType()) == null) {
            return false;
        }

        // LoreDamageUtils already receives explicit weapon-scaled lore hit amounts. Reapplying
        // this cast snapshot to those events would multiply weapon damage a second time.
        if (Boolean.TRUE.equals(damage.getIfPresentMetaObject(LoreDamageUtils.META_LORE_DAMAGE))) {
            return false;
        }

        boolean projectileHit = damage.getSource() instanceof Damage.ProjectileSource;
        // Player projectile hits already carry their native projectile damage. Keep that path
        // untouched; this snapshot is for direct native rune interactions such as Whirlwind.
        if (projectileHit && !npc) {
            return false;
        }
        CastSnapshot snapshot = getActiveSnapshot(
                projectileHit ? PROJECTILE_CASTS : MELEE_CASTS, store, ownerRef);
        if (snapshot == null) {
            return false;
        }
        if (snapshot.damageAttributeIncludesWeaponBase) {
            if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
                System.out.printf(Locale.ROOT,
                        "[SocketReforge][CoreRuneDamage] stage=weapon-base-skipped ability=%s "
                                + "weapon=%s reason=damage-attribute-already-includes-weapon-base%n",
                        snapshot.ability, snapshot.weaponId);
            }
            return false;
        }

        Float rawAmount = damage.getIfPresentMetaObject(DamageStackDelayEST.META_RAW_AMOUNT);
        float interactionCoefficient = rawAmount != null && Float.isFinite(rawAmount) && rawAmount > 0.0f
                ? rawAmount : damage.getAmount();
        if (!Float.isFinite(interactionCoefficient) || interactionCoefficient <= 0.0f) {
            return false;
        }

        float calculatedDamage = interactionCoefficient * snapshot.weaponBaseDamage;
        damage.setAmount(calculatedDamage);
        if (DamageStackDelayEST.isDamageCalculationDebugEnabled()) {
            System.out.printf(Locale.ROOT,
                    "[SocketReforge][CoreRuneDamage] stage=weapon-base-applied ability=%s weapon=%s "
                            + "interactionRaw=%.4f weaponBase=%.4f applied=%.4f projectile=%s npc=%s%n",
                    snapshot.ability, snapshot.weaponId, interactionCoefficient,
                    snapshot.weaponBaseDamage, calculatedDamage, projectileHit, npc);
        }
        return true;
    }

    private static boolean usesRuneWeaponDamage(String ability) {
        return ability.equals("pummel") || ability.startsWith("big_arrow_")
                || ability.startsWith("volley") || ability.equals("bloodrush")
                || ability.equals("grand_slam") || ability.equals("grandslam")
                || ability.equals("octaslash") || ability.equals("omnislash")
                || ability.equals("razorstrike") || ability.startsWith("vortexstrike")
                || ability.equals("whirlwind") || ability.equals("soul_reaper");
    }

    private static boolean usesMeleeDamagePath(String ability) {
        return !ability.startsWith("big_arrow_") && !ability.startsWith("volley")
                && !ability.equals("soul_reaper");
    }

    private static boolean usesProjectileDamagePath(String ability) {
        return ability.startsWith("big_arrow_") || ability.startsWith("volley")
                || ability.equals("soul_reaper") || ability.equals("octaslash")
                || ability.equals("omnislash") || ability.equals("razorstrike");
    }

    private static void putSnapshot(ConcurrentHashMap<EntityKey, CastSnapshot> snapshots,
                                    EntityKey key,
                                    CastSnapshot snapshot,
                                    long ttlMillis) {
        snapshots.put(key, snapshot);
        CompletableFuture.delayedExecutor(ttlMillis, TimeUnit.MILLISECONDS)
                .execute(() -> snapshots.remove(key, snapshot));
    }

    private static CastSnapshot getActiveSnapshot(ConcurrentHashMap<EntityKey, CastSnapshot> snapshots,
                                                   Store<EntityStore> store,
                                                   Ref<EntityStore> ownerRef) {
        if (store == null || ownerRef == null || !ownerRef.isValid()) {
            return null;
        }
        EntityKey key = new EntityKey(store, ownerRef.getIndex());
        CastSnapshot snapshot = snapshots.get(key);
        if (snapshot != null && System.currentTimeMillis() > snapshot.expiresAtMillis) {
            snapshots.remove(key, snapshot);
            return null;
        }
        return snapshot;
    }

    private static String normalizeAbility(String ability) {
        return ability == null ? "" : ability.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
    }

    private static final class EntityKey {
        private final Store<EntityStore> store;
        private final int index;

        private EntityKey(Store<EntityStore> store, int index) {
            this.store = store;
            this.index = index;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EntityKey key && store == key.store && index == key.index;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(store) + index;
        }
    }

    private record CastSnapshot(String ability,
                                String weaponId,
                                float weaponBaseDamage,
                                boolean damageAttributeIncludesWeaponBase,
                                long expiresAtMillis) {}
}
