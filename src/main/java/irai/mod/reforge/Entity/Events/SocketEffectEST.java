package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Entity Stat Type (EST) that applies socket effects to damage calculations.
 * Integrates with the existing damage system to apply essence bonuses.
 */
@SuppressWarnings("removal")
public class SocketEffectEST extends DamageEventSystem {
    private static final Map<UUID, Long> FROZEN_UNTIL = new ConcurrentHashMap<>();
    private static final long FREEZE_DURATION_MILLIS = 2000L;
    private static final double FREEZE_DAMAGE_PENALTY = 0.30d;
    private static final double ICE_SLOW_DAMAGE_PENALTY_CAP = 50.0d;
    private static final double FIRE_BURN_REFLECT_RATIO = 0.05d;
    private static final float FIRE_BURN_MIN_DAMAGE = 1.0f;
    private static final Map<String, Long> RESONANCE_COOLDOWNS = new ConcurrentHashMap<>();
    private static final float RESONANCE_CHEAT_DEATH_BUFFER_HP = 1.0f;
    private static final String[] BURN_EFFECT_IDS = {
            "EntityEffect_Burning", "EntityEffect_Burn", "Effect_Burn", "Burning", "Burn"
    };
    private static final String[] FREEZE_EFFECT_IDS = {
            "EntityEffect_Frozen", "EntityEffect_Freeze", "Effect_Freeze", "Frozen", "Freeze"
    };
    private static final String[] SHOCK_EFFECT_IDS = {
            "EntityEffect_Shocked", "EntityEffect_Shock", "Effect_Shock", "Shocked", "Shock"
    };
    private static final String[] REGEN_EFFECT_IDS = {
            "EntityEffect_Regeneration", "Effect_Regeneration", "Regeneration"
    };
    private static final String[] SHIELD_EFFECT_IDS = {
            "EntityEffect_Invulnerable", "EntityEffect_Shielded", "Effect_Shielded", "Shielded", "Invulnerable"
    };
    private static final String MULTISHOT_PROC_SFX_ID = "SFX_Weapon_Bench_Craft";
    private static final String PLUNDER_SUCCESS_SFX_ID = "SFX_Mace_T1_Block_Impact";
    private static final boolean PLUNDER_DEBUG_LOG = false;
    private static final boolean STORM_QUIVER_DEBUG_LOG = true;
    private SFXConfig sfxConfig = new SFXConfig();
    private static final EnumSet<ResonanceSystem.ResonanceType> WEAPON_RESONANCE_HANDLERS = EnumSet.of(
            ResonanceSystem.ResonanceType.BURN_ON_CRIT,
            ResonanceSystem.ResonanceType.CHAIN_SLOW,
            ResonanceSystem.ResonanceType.EXECUTE,
            ResonanceSystem.ResonanceType.ARMOR_SHRED,
            ResonanceSystem.ResonanceType.THUNDER_STRIKE,
            ResonanceSystem.ResonanceType.MULTISHOT_BARRAGE,
            ResonanceSystem.ResonanceType.CROSSBOW_AUTO_RELOAD,
            ResonanceSystem.ResonanceType.PLUNDERING_BLADE,
            ResonanceSystem.ResonanceType.HEAL_SURGE
    );
    private static final EnumSet<ResonanceSystem.ResonanceType> ARMOR_RESONANCE_HANDLERS = EnumSet.of(
            ResonanceSystem.ResonanceType.FROST_NOVA_ON_HIT,
            ResonanceSystem.ResonanceType.THORNS_SHOCK,
            ResonanceSystem.ResonanceType.CHEAT_DEATH,
            ResonanceSystem.ResonanceType.HEAL_SURGE,
            ResonanceSystem.ResonanceType.SHOCK_DODGE,
            ResonanceSystem.ResonanceType.AURA_BURN
    );

    static {
        validateResonanceCoverage();
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    public void setSfxConfig(SFXConfig sfxConfig) {
        if (sfxConfig != null) {
            this.sfxConfig = sfxConfig;
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        try {
            float beforeDefenderSocketDamage = damage.getAmount();
            // Get target (entity receiving damage)
            Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

            // Get damage source
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) {
                return;
            }

            Ref<EntityStore> attackerRef = entitySource.getRef();
            Player attackerPlayer = null;
            ItemStack attackerWeapon = null;
            ResonanceSystem.ResonanceType attackerResonanceType = ResonanceSystem.ResonanceType.NONE;

            // Apply attacker socket bonuses (damage increase)
            Player attacker = store.getComponent(attackerRef, Player.getComponentType());
            attackerPlayer = attacker;
            if (attacker != null) {
                applyFreezePenaltyIfPresent(attacker, damage);
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    attackerWeapon = weapon;
                    attackerResonanceType = resolveResonanceType(weapon);
                    // NOTE: attacker damage (refine + sockets) is applied in EquipmentRefineEST
                    // to avoid multi-system write races on Damage amount.
                }
            }

            // Apply defender socket bonuses (damage reduction from armor sockets)
            Player defenderPlayer = store.getComponent(targetRef, Player.getComponentType());
            if (defenderPlayer != null) {
                // Apply Ice Freeze effect on hit if attacker has Max Tier Ice Essence.
                // This is independent of defender armor and is applied as a short debuff.
                if (attacker != null) {
                    applyIceFreezeOnHit(attacker, defenderPlayer);
                }

                List<ItemStack> armorPieces = getAllEquippedArmor(defenderPlayer);
                if (!armorPieces.isEmpty()) {
                    SocketStatSystem.DefensiveBonuses defensiveBonuses = SocketStatSystem.getDefensiveBonuses(defenderPlayer);

                    // Evasion is a full dodge chance from armor sockets.
                    double evasionChance = Math.max(0.0, Math.min(100.0, defensiveBonuses.evasionPercent()));
                    if (evasionChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < evasionChance) {
                        applyShockDodgeOnEvasion(store, defenderPlayer, attacker, attackerRef, armorPieces, damage.getAmount());
                        damage.setAmount(0f);
                        return;
                    }

                    double defenseMultiplier = 1.0 + (defensiveBonuses.defensePercent() / 100.0);
                    double flatReduction = calculateSocketFlatDefense(armorPieces);
                    double slowPercent = 0.0;
                    // ICE armor slow is enemy-only: it should penalize the attacker, never the defender.
                    if (attacker != null && !isSamePlayer(attacker, defenderPlayer)) {
                        slowPercent = SocketArmorBonusHelper.getScaledPercentBonus(defenderPlayer, EssenceEffect.StatType.MOVEMENT_SPEED);
                        slowPercent = Math.max(0.0, Math.min(ICE_SLOW_DAMAGE_PENALTY_CAP, slowPercent));
                    }
                    double fireDefensePercent = isFireDamage(damage) ? defensiveBonuses.fireDefensePercent() : 0.0;
                    double fireDefenseMultiplier = Math.max(0.0, 1.0 - (fireDefensePercent / 100.0));

                    if (defenseMultiplier != 1.0 || flatReduction != 0 || fireDefenseMultiplier != 1.0 || slowPercent > 0.0) {
                        float damageAmount = damage.getAmount();

                        // Ice armor slow: model as reduced attacker hit potency.
                        if (slowPercent > 0.0) {
                            damageAmount = (float) (damageAmount * Math.max(0.0, 1.0 - (slowPercent / 100.0)));
                        }

                        float reducedDamage = (float) Math.max(0, (damageAmount / defenseMultiplier) - flatReduction);
                        
                        // Fire defense only applies to fire-like damage causes.
                        reducedDamage = (float) (reducedDamage * fireDefenseMultiplier);

                        damage.setAmount(reducedDamage);
                    }

                    // Apply Fire Burn effect on hit if Fire Essence is Max Tier
                    applyFireBurnOnHit(store, defenderPlayer, attackerRef, damage.getAmount());

                    applyArmorResonanceOnHit(store, defenderPlayer, targetRef, attacker, attackerRef, armorPieces, damage);
                }
            }

            if (attacker != null && targetRef != null && attackerWeapon != null) {
                applyWeaponResonanceOnHit(
                        store,
                        commandBuffer,
                        attacker,
                        attackerRef,
                        defenderPlayer,
                        targetRef,
                        attackerWeapon,
                        attackerResonanceType,
                        damage
                );
            }

            // Log final per-hit damage after socket processing for easier balancing/debugging.
            if (attackerPlayer != null) {
                applyWeaponLifeSteal(store, attackerRef, attackerWeapon, damage.getAmount());

                System.out.println("[SocketReforge][DEF_DMG] attacker=" + attackerPlayer.getUuid()
                        + " beforeDefenderSocket=" + beforeDefenderSocketDamage
                        + " final=" + damage.getAmount());
            }
        } catch (Throwable t) {
            System.err.println("[SocketReforge] SocketEffectEST handle error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private ResonanceSystem.ResonanceType parseResonanceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ResonanceSystem.ResonanceType.NONE;
        }
        try {
            return ResonanceSystem.ResonanceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ResonanceSystem.ResonanceType.NONE;
        }
    }

    /**
     * Resolves resonance from live socket layout first, then falls back to stored metadata.
     * This keeps ECS behavior resilient when legacy items are missing resonance metadata.
     */
    private ResonanceSystem.ResonanceType resolveResonanceType(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return ResonanceSystem.ResonanceType.NONE;
        }
        try {
            SocketData socketData = SocketManager.getSocketData(item);
            if (socketData != null) {
                ResonanceSystem.ResonanceResult result = ResonanceSystem.evaluate(item, socketData);
                if (result != null && result.active() && result.type() != null) {
                    return result.type();
                }
            }
        } catch (Throwable ignored) {
            // Fall through to metadata-based resolution.
        }
        return parseResonanceType(SocketManager.getResonanceType(item));
    }

    private void applyWeaponResonanceOnHit(Store<EntityStore> store,
                                           CommandBuffer<EntityStore> commandBuffer,
                                           Player attacker,
                                           Ref<EntityStore> attackerRef,
                                           Player defenderPlayer,
                                           Ref<EntityStore> defenderRef,
                                           ItemStack weapon,
                                           ResonanceSystem.ResonanceType resonanceType,
                                           Damage damage) {
        if (store == null || attacker == null || attackerRef == null || defenderRef == null || weapon == null || damage == null) {
            return;
        }
        if (resonanceType == null || resonanceType == ResonanceSystem.ResonanceType.NONE) {
            return;
        }
        if (defenderPlayer != null && isSamePlayer(attacker, defenderPlayer)) {
            return;
        }

        float dealt = Math.max(0f, damage.getAmount());
        if (dealt <= 0f) {
            return;
        }

        switch (resonanceType) {
            case BURN_ON_CRIT -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.22
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_burn")) {
                    markResonanceCooldown(attacker.getUuid(), "w_burn", 700L);
                    damage.setAmount((float) (damage.getAmount() + Math.max(1.0f, dealt * 0.08f)));
                    tryApplyVisualEffect(store, defenderRef, BURN_EFFECT_IDS);
                }
            }
            case CHAIN_SLOW -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.25
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_chain_slow")) {
                    markResonanceCooldown(attacker.getUuid(), "w_chain_slow", 2000L);
                    if (defenderPlayer != null) {
                        markFrozen(defenderPlayer, 1500L);
                    }
                    tryApplyVisualEffect(store, defenderRef, FREEZE_EFFECT_IDS);
                }
            }
            case EXECUTE -> {
                float healthRatio = getHealthRatio(store, defenderRef);
                if (healthRatio > 0f && healthRatio <= 0.25f) {
                    damage.setAmount((float) (damage.getAmount() * 1.20f));
                    tryApplyVisualEffect(store, defenderRef, BURN_EFFECT_IDS);
                }
            }
            case ARMOR_SHRED -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.30
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_shred")) {
                    markResonanceCooldown(attacker.getUuid(), "w_shred", 900L);
                    damage.setAmount((float) (damage.getAmount() * 1.10f));
                    tryApplyVisualEffect(store, defenderRef, SHOCK_EFFECT_IDS);
                }
            }
            case THUNDER_STRIKE -> {
                if (ThreadLocalRandom.current().nextDouble() < 0.20
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_thunder")) {
                    markResonanceCooldown(attacker.getUuid(), "w_thunder", 1200L);
                    damage.setAmount((float) (damage.getAmount() + Math.max(1.0f, dealt * 0.10f)));
                    tryApplyVisualEffect(store, defenderRef, SHOCK_EFFECT_IDS);
                }
            }
            case MULTISHOT_BARRAGE -> {
                if (!isProjectileDamage(damage)
                        || !isBowWeapon(weapon)
                        || !hasAtLeastFiveSockets(weapon)) {
                    break;
                }
                if (ThreadLocalRandom.current().nextDouble() < 0.20
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_multishot")) {
                    markResonanceCooldown(attacker.getUuid(), "w_multishot", 1600L);
                    if (sfxConfig != null) {
                        sfxConfig.playSound(attacker, MULTISHOT_PROC_SFX_ID);
                    }
                    int queued = queueStormQuiverExtraDamageHits(commandBuffer, attackerRef, defenderRef, damage, 2);
                    logStormQuiver("Proc: queued extra damage hits=" + queued
                            + " perHit=" + dealt
                            + " weapon=" + weapon.getItemId());
                    tryApplyVisualEffect(store, defenderRef, SHOCK_EFFECT_IDS);
                }
            }
            case CROSSBOW_AUTO_RELOAD -> {
                if (!isProjectileDamage(damage)
                        || !isCrossbowWeapon(weapon)
                        || !hasAtLeastFiveSockets(weapon)) {
                    break;
                }
                if (ThreadLocalRandom.current().nextDouble() < 0.35
                        && !isResonanceOnCooldown(attacker.getUuid(), "w_crossbow_reload")) {
                    markResonanceCooldown(attacker.getUuid(), "w_crossbow_reload", 450L);
                    if (refundOneArrow(attacker)) {
                        tryApplyVisualEffect(store, attackerRef, REGEN_EFFECT_IDS);
                    }
                }
            }
            case PLUNDERING_BLADE -> {
                if (!isDaggerWeapon(weapon) || !hasAtLeastFiveSockets(weapon)) {
                    logPlunder("Skipped: weapon is not a valid 5-socket dagger. itemId="
                            + (weapon == null ? "null" : weapon.getItemId()));
                    break;
                }
                double plunderRoll = ThreadLocalRandom.current().nextDouble();
                boolean plunderCooldown = isResonanceOnCooldown(attacker.getUuid(), "w_plunder");
                logPlunder("Attempt: attacker=" + attacker.getUuid()
                        + ", roll=" + String.format(Locale.ROOT, "%.4f", plunderRoll)
                        + ", threshold=0.1500"
                        + ", cooldown=" + plunderCooldown);
                if (plunderRoll < 0.15 && !plunderCooldown) {
                    if (tryPlunderNpcLoot(store, defenderRef, attacker)) {
                        markResonanceCooldown(attacker.getUuid(), "w_plunder", 2500L);
                        logPlunder("Success: attacker=" + attacker.getUuid() + " cooldown=2500ms applied.");
                        if (sfxConfig != null) {
                            sfxConfig.playSound(attacker, PLUNDER_SUCCESS_SFX_ID);
                        }
                        tryApplyVisualEffect(store, attackerRef, REGEN_EFFECT_IDS);
                    } else {
                        logPlunder("Failed: loot steal attempt did not yield an item.");
                    }
                } else if (plunderCooldown) {
                    logPlunder("Blocked: resonance cooldown active.");
                } else {
                    logPlunder("Missed: proc roll did not pass.");
                }
            }
            case HEAL_SURGE -> {
                if (!isResonanceOnCooldown(attacker.getUuid(), "w_heal")) {
                    markResonanceCooldown(attacker.getUuid(), "w_heal", 1800L);
                    float heal = Math.max(1.0f, dealt * 0.10f);
                    applyDirectHeal(store, attackerRef, heal);
                    tryApplyVisualEffect(store, attackerRef, REGEN_EFFECT_IDS);
                }
            }
            default -> {
                // Other resonance types are armor-centric or passive.
            }
        }
    }

    private void applyArmorResonanceOnHit(Store<EntityStore> store,
                                          Player defender,
                                          Ref<EntityStore> defenderRef,
                                          Player attacker,
                                          Ref<EntityStore> attackerRef,
                                          List<ItemStack> armorPieces,
                                          Damage damage) {
        if (store == null || defender == null || defenderRef == null || armorPieces == null || armorPieces.isEmpty() || damage == null) {
            return;
        }

        boolean hasFrostNova = hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.FROST_NOVA_ON_HIT);
        boolean hasThornsShock = hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.THORNS_SHOCK);
        boolean hasCheatDeath = hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.CHEAT_DEATH);
        boolean hasHealSurge = hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.HEAL_SURGE);
        boolean hasAuraBurn = hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.AURA_BURN);

        if (hasCheatDeath && applyCheatDeath(store, defenderRef, defender, damage)) {
            tryApplyVisualEffect(store, defenderRef, SHIELD_EFFECT_IDS);
        }

        if (hasHealSurge && !isResonanceOnCooldown(defender.getUuid(), "a_heal")) {
            markResonanceCooldown(defender.getUuid(), "a_heal", 5000L);
            float heal = Math.max(1.0f, damage.getAmount() * 0.05f);
            applyDirectHeal(store, defenderRef, heal);
            tryApplyVisualEffect(store, defenderRef, REGEN_EFFECT_IDS);
        }

        if (attacker == null || attackerRef == null || isSamePlayer(attacker, defender)) {
            return;
        }

        float incoming = Math.max(0f, damage.getAmount());
        if (incoming <= 0f) {
            return;
        }

        if (hasFrostNova && ThreadLocalRandom.current().nextDouble() < 0.25
                && !isResonanceOnCooldown(defender.getUuid(), "a_frost_nova")) {
            markResonanceCooldown(defender.getUuid(), "a_frost_nova", 4000L);
            markFrozen(attacker, 1500L);
            tryApplyVisualEffect(store, attackerRef, FREEZE_EFFECT_IDS);
        }

        if (hasThornsShock) {
            float reflect = Math.max(1.0f, incoming * 0.06f);
            applyDirectHealthLoss(store, attackerRef, reflect);
            tryApplyVisualEffect(store, attackerRef, SHOCK_EFFECT_IDS);
        }

        if (hasAuraBurn && ThreadLocalRandom.current().nextDouble() < 0.20
                && !isResonanceOnCooldown(defender.getUuid(), "a_burn")) {
            markResonanceCooldown(defender.getUuid(), "a_burn", 900L);
            float burn = Math.max(1.0f, incoming * 0.05f);
            applyDirectHealthLoss(store, attackerRef, burn);
            tryApplyVisualEffect(store, attackerRef, BURN_EFFECT_IDS);
        }

    }

    private void applyShockDodgeOnEvasion(Store<EntityStore> store,
                                          Player defender,
                                          Player attacker,
                                          Ref<EntityStore> attackerRef,
                                          List<ItemStack> armorPieces,
                                          float incomingDamage) {
        if (store == null || defender == null || attacker == null || attackerRef == null || armorPieces == null || armorPieces.isEmpty()) {
            return;
        }
        if (isSamePlayer(attacker, defender)) {
            return;
        }
        if (!hasArmorResonance(armorPieces, ResonanceSystem.ResonanceType.SHOCK_DODGE)) {
            return;
        }
        if (isResonanceOnCooldown(defender.getUuid(), "a_shock_dodge")) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.20d) {
            return;
        }

        markResonanceCooldown(defender.getUuid(), "a_shock_dodge", 3500L);
        float base = Math.max(1.0f, incomingDamage);
        float shock = Math.max(1.0f, base * 0.04f);
        applyDirectHealthLoss(store, attackerRef, shock);
        tryApplyVisualEffect(store, attackerRef, SHOCK_EFFECT_IDS);
    }

    private static void validateResonanceCoverage() {
        EnumSet<ResonanceSystem.ResonanceType> covered = EnumSet.noneOf(ResonanceSystem.ResonanceType.class);
        covered.addAll(WEAPON_RESONANCE_HANDLERS);
        covered.addAll(ARMOR_RESONANCE_HANDLERS);

        EnumSet<ResonanceSystem.ResonanceType> missing = EnumSet.allOf(ResonanceSystem.ResonanceType.class);
        missing.remove(ResonanceSystem.ResonanceType.NONE);
        missing.removeAll(covered);

        if (!missing.isEmpty()) {
            System.err.println("[SocketReforge] Missing ECS resonance handlers for: " + missing);
        }
    }

    private boolean hasArmorResonance(List<ItemStack> armorPieces, ResonanceSystem.ResonanceType expected) {
        if (armorPieces == null || armorPieces.isEmpty() || expected == null) {
            return false;
        }
        for (ItemStack armor : armorPieces) {
            ResonanceSystem.ResonanceType found = resolveResonanceType(armor);
            if (found == expected) {
                return true;
            }
        }
        return false;
    }

    private boolean applyCheatDeath(Store<EntityStore> store,
                                    Ref<EntityStore> defenderRef,
                                    Player defender,
                                    Damage damage) {
        if (store == null || defenderRef == null || defender == null || damage == null) {
            return false;
        }
        float incoming = damage.getAmount();
        if (incoming <= 0f) {
            return false;
        }
        if (isResonanceOnCooldown(defender.getUuid(), "a_cheat_death")) {
            return false;
        }

        EntityStatMap statMap = store.getComponent(defenderRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return false;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return false;
        }

        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null) {
            return false;
        }
        float currentHealth = health.get();
        if (currentHealth <= 0f || incoming < currentHealth) {
            return false;
        }

        float remaining = Math.max(0f, currentHealth - RESONANCE_CHEAT_DEATH_BUFFER_HP);
        markResonanceCooldown(defender.getUuid(), "a_cheat_death", 60000L);
        damage.setAmount(Math.max(0f, remaining));
        return true;
    }

    private boolean isResonanceOnCooldown(UUID uuid, String key) {
        if (uuid == null || key == null || key.isBlank()) {
            return false;
        }
        String mapKey = uuid + ":" + key;
        long now = System.currentTimeMillis();
        Long until = RESONANCE_COOLDOWNS.get(mapKey);
        return until != null && until > now;
    }

    private void markResonanceCooldown(UUID uuid, String key, long cooldownMillis) {
        if (uuid == null || key == null || key.isBlank() || cooldownMillis <= 0L) {
            return;
        }
        String mapKey = uuid + ":" + key;
        RESONANCE_COOLDOWNS.put(mapKey, System.currentTimeMillis() + cooldownMillis);
    }

    private void markFrozen(Player player, long durationMillis) {
        if (player == null || durationMillis <= 0L) {
            return;
        }
        UUID uuid = player.getUuid();
        if (uuid == null) {
            return;
        }
        long until = System.currentTimeMillis() + durationMillis;
        FROZEN_UNTIL.merge(uuid, until, Math::max);
    }

    private float getHealthRatio(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        if (store == null || entityRef == null) {
            return 0f;
        }
        EntityStatMap statMap = store.getComponent(entityRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return 0f;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, health.get() / health.getMax()));
    }

    private void applyDirectHeal(Store<EntityStore> store, Ref<EntityStore> targetRef, float amount) {
        if (store == null || targetRef == null || amount <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return;
        }
        float missing = health.getMax() - health.get();
        if (missing <= 0f) {
            return;
        }
        statMap.addStatValue(healthStatIndex, Math.min(missing, amount));
    }

    private void tryApplyVisualEffect(Store<EntityStore> store, Ref<EntityStore> targetRef, String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            return;
        }
        try {
            EntityEffect effect = resolveEntityEffect(effectIds);
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
            // Visual effects are optional; fail silently.
        }
    }

    private EntityEffect resolveEntityEffect(String... effectIds) {
        if (effectIds == null || effectIds.length == 0) {
            return null;
        }
        try {
            var assetMap = EntityEffect.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            for (String effectId : effectIds) {
                if (effectId == null || effectId.isBlank()) {
                    continue;
                }
                int index = assetMap.getIndex(effectId);
                if (index < 0) {
                    continue;
                }
                EntityEffect effect = assetMap.getAsset(index);
                if (effect != null) {
                    return effect;
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    /**
     * Applies Burn effect to attacker if defender has Max Tier Fire Essence.
     */
    private void applyFireBurnOnHit(Store<EntityStore> store,
                                    Player defender,
                                    Ref<EntityStore> attackerRef,
                                    float finalIncomingDamage) {
        if (store == null || defender == null || attackerRef == null || finalIncomingDamage <= 0f) {
            return;
        }

        // Check if defender has armor with Fire Essence Tier 5
        List<ItemStack> armorPieces = getAllEquippedArmor(defender);
        boolean hasFireTierFive = false;
        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
                Integer fireTier = tierMap.get(Essence.Type.FIRE);
                if (fireTier != null && fireTier >= 5) {
                    hasFireTierFive = true;
                    break;
                }
            }
        }
        if (!hasFireTierFive) {
            return;
        }

        float burnDamage = Math.max(FIRE_BURN_MIN_DAMAGE, (float) (finalIncomingDamage * FIRE_BURN_REFLECT_RATIO));
        applyDirectHealthLoss(store, attackerRef, burnDamage);
    }

    /**
     * Applies Freeze effect to defender if attacker has Max Tier Ice Essence.
     */
    private void applyIceFreezeOnHit(Player attacker, Player defender) {
        if (attacker == null || defender == null) {
            return;
        }
        if (isSamePlayer(attacker, defender)) {
            return;
        }

        // Check if attacker has weapon with Ice Essence Tier 5
        ItemStack weapon = findWeaponInHotbar(attacker);
        if (weapon == null || weapon.isEmpty() || !ReforgeEquip.isWeapon(weapon)) {
            return;
        }

        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData != null && socketData.getMaxSockets() > 0) {
             Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
             Integer iceTier = tierMap.get(Essence.Type.ICE);
             if (iceTier != null && iceTier >= 5) {
                 UUID defenderUuid = defender.getUuid();
                 if (defenderUuid != null) {
                     FROZEN_UNTIL.put(defenderUuid, System.currentTimeMillis() + FREEZE_DURATION_MILLIS);
                 }
             }
        }
    }

    private void applyFreezePenaltyIfPresent(Player attacker, Damage damage) {
        if (attacker == null || damage == null) {
            return;
        }
        UUID attackerUuid = attacker.getUuid();
        if (attackerUuid == null) {
            return;
        }

        Long until = FROZEN_UNTIL.get(attackerUuid);
        if (until == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= until) {
            FROZEN_UNTIL.remove(attackerUuid);
            return;
        }

        float penalized = (float) (damage.getAmount() * (1.0 - FREEZE_DAMAGE_PENALTY));
        damage.setAmount(Math.max(0f, penalized));
    }

    private boolean isFireDamage(Damage damage) {
        if (damage == null) {
            return false;
        }
        try {
            var cause = damage.getCause();
            if (cause == null) {
                return false;
            }
            String causeId = cause.getId();
            if (causeId == null || causeId.isBlank()) {
                return false;
            }
            String lower = causeId.toLowerCase(Locale.ROOT);
            return lower.contains("fire")
                    || lower.contains("burn")
                    || lower.contains("lava")
                    || lower.contains("flame");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyDirectHealthLoss(Store<EntityStore> store, Ref<EntityStore> targetRef, float rawDamage) {
        if (store == null || targetRef == null || rawDamage <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.get() <= 0f) {
            return;
        }
        float maxSpendable = Math.max(0f, health.get() - 0.1f);
        float applied = Math.min(rawDamage, maxSpendable);
        if (applied <= 0f) {
            return;
        }
        statMap.addStatValue(healthStatIndex, -applied);
    }

    private int queueStormQuiverExtraDamageHits(CommandBuffer<EntityStore> commandBuffer,
                                                Ref<EntityStore> attackerRef,
                                                Ref<EntityStore> defenderRef,
                                                Damage originalDamage,
                                                int extraHitCount) {
        if (commandBuffer == null || defenderRef == null || originalDamage == null || extraHitCount <= 0) {
            return 0;
        }
        float perHitDamage = Math.max(0.1f, originalDamage.getAmount());
        Damage.Source source = attackerRef == null
                ? originalDamage.getSource()
                : new Damage.EntitySource(attackerRef);
        DamageCause cause = originalDamage.getCause() == null ? DamageCause.PROJECTILE : originalDamage.getCause();

        int queued = 0;
        for (int i = 0; i < extraHitCount; i++) {
            Damage extraDamage = new Damage(source, cause, perHitDamage);
            commandBuffer.invoke(defenderRef, extraDamage);
            queued++;
        }
        return queued;
    }

    private boolean isSamePlayer(Player a, Player b) {
        if (a == null || b == null) {
            return false;
        }
        UUID ua = a.getUuid();
        UUID ub = b.getUuid();
        return ua != null && ua.equals(ub);
    }

    /**
     * Calculates the total defense multiplier from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketDefenseBonus(List<ItemStack> armorPieces) {
        double totalPercent = 0.0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.DEFENSE);
            totalPercent += bonuses[1];
        }

        return 1.0 + (totalPercent / 100.0);
    }

    /**
     * Calculates the total flat defense reduction from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketFlatDefense(List<ItemStack> armorPieces) {
        double totalReduction = 0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.DEFENSE);
            totalReduction += bonuses[0];
        }

        return totalReduction;
    }

    /**
     * Applies Health bonus from Life Essence to the player.
     */
    private void applyHealthBonus(Player player) {
        List<ItemStack> armorPieces = getAllEquippedArmor(player);
        double healthBonus = 0;

        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            if (socketData != null && socketData.getMaxSockets() > 0) {
                // Calculate Health bonus (flat)
                // We assume it's a flat bonus based on EssenceEffect.StatType.HEALTH
                double[] bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.HEALTH, false);
                healthBonus += bonuses[0]; // Flat bonus
            }
        }

        if (healthBonus > 0) {
            // Apply health bonus (Heal player)
            // Assuming player has getHealth() and setHealth() or similar
            // For now, we'll log it
            System.out.println("[SocketReforge] Applying " + healthBonus + " HP.");
            // player.setHealth(player.getHealth() + (float)healthBonus);
        }
    }

    /**
     * Weapon Lifesteal:
     * Heals attacker by final dealt damage * lifesteal%.
     */
    private void applyWeaponLifeSteal(
            Store<EntityStore> store,
            Ref<EntityStore> attackerRef,
            ItemStack weapon,
            float finalDamageDealt) {
        if (store == null || attackerRef == null || weapon == null || weapon.isEmpty()) {
            return;
        }
        if (finalDamageDealt <= 0f) {
            return;
        }
        if (!ReforgeEquip.isWeapon(weapon)) {
            return;
        }

        double lifeStealPercent = getLifeStealPercent(weapon);
        if (lifeStealPercent <= 0.0d) {
            return;
        }

        EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }

        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }

        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return;
        }

        float missing = health.getMax() - health.get();
        if (missing <= 0f) {
            return;
        }

        float heal = (float) (finalDamageDealt * (lifeStealPercent / 100.0d));
        if (heal <= 0f) {
            return;
        }
        heal = Math.min(heal, missing);
        statMap.addStatValue(healthStatIndex, heal);
    }

    private double getLifeStealPercent(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0.0d;
        }
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 0.0d;
        }

        // Prefer persisted values; fall back to tier calculation for older items.
        double[] bonuses = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.LIFE_STEAL);
        if (bonuses[0] == 0.0d && bonuses[1] == 0.0d) {
            bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.LIFE_STEAL, true);
        }
        return Math.max(0.0d, bonuses[1]);
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

    /**
     * Calculates the total fire defense multiplier from socketed armor essences using tier-based calculation.
     */
    private double calculateSocketFireDefenseBonus(List<ItemStack> armorPieces) {
        double totalPercent = 0.0;

        for (ItemStack armor : armorPieces) {
            double[] bonuses = SocketManager.getStoredStatBonus(armor, EssenceEffect.StatType.FIRE_DEFENSE);
            totalPercent += bonuses[1];
        }

        return Math.max(0.0, 1.0 - (totalPercent / 100.0));
    }

    private boolean isProjectileDamage(Damage damage) {
        if (damage == null) {
            return false;
        }
        try {
            Damage.Source source = damage.getSource();
            if (source instanceof Damage.ProjectileSource) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to cause-ID based detection.
        }
        try {
            var cause = damage.getCause();
            if (cause == null) {
                return false;
            }
            String causeId = cause.getId();
            if (causeId == null || causeId.isBlank()) {
                return false;
            }
            String lower = causeId.toLowerCase(Locale.ROOT);
            return lower.contains("projectile")
                    || lower.contains("arrow")
                    || lower.contains("bolt");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasAtLeastFiveSockets(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null) {
            return false;
        }
        return socketData.getMaxSockets() >= 5 && socketData.getCurrentSocketCount() >= 5;
    }

    private boolean isBowWeapon(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        String itemId = weapon.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("bow") && !lower.contains("crossbow");
    }

    private boolean isCrossbowWeapon(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        String itemId = weapon.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return itemId.toLowerCase(Locale.ROOT).contains("crossbow");
    }

    private boolean isDaggerWeapon(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        String itemId = weapon.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("dagger") || lower.contains("knife");
    }

    private boolean tryPlunderNpcLoot(Store<EntityStore> store, Ref<EntityStore> defenderRef, Player attacker) {
        if (store == null || defenderRef == null || attacker == null) {
            logPlunder("Abort: invalid context (store/defenderRef/attacker null).");
            return false;
        }

        NPCEntity npc = store.getComponent(defenderRef, NPCEntity.getComponentType());
        if (npc == null) {
            logPlunder("Abort: target is not an NPCEntity. targetRef=" + defenderRef);
            return false;
        }
        if (npc.getRole() == null) {
            logPlunder("Abort: NPC has no role. targetRef=" + defenderRef);
            return false;
        }

        String dropListId = npc.getRole().getDropListId();
        if (dropListId == null || dropListId.isBlank()) {
            logPlunder("Abort: NPC role has no dropListId. targetRef=" + defenderRef);
            return false;
        }
        ItemDropList dropList = resolveItemDropList(dropListId);
        if (dropList == null || dropList.getContainer() == null) {
            logPlunder("Abort: drop list not found or has no container. dropListId=" + dropListId);
            return false;
        }

        // Loot tables can legitimately roll no drops; retry a few times so this resonance
        // feels reliable when it procs.
        List<ItemDrop> rolledDrops = new ArrayList<>();
        for (int attempt = 0; attempt < 3 && rolledDrops.isEmpty(); attempt++) {
            dropList.getContainer().populateDrops(rolledDrops, ThreadLocalRandom.current()::nextDouble, dropListId);
        }
        if (rolledDrops.isEmpty()) {
            logPlunder("Abort: drop list rolled empty after retries. dropListId=" + dropListId);
            return false;
        }

        ItemDrop stolenDrop = rolledDrops.get(ThreadLocalRandom.current().nextInt(rolledDrops.size()));
        if (stolenDrop == null || stolenDrop.getItemId() == null || stolenDrop.getItemId().isBlank()) {
            logPlunder("Abort: rolled drop was invalid. dropListId=" + dropListId);
            return false;
        }

        int quantity = Math.max(1, stolenDrop.getRandomQuantity(ThreadLocalRandom.current()));
        ItemStack stolenItem;
        try {
            stolenItem = new ItemStack(stolenDrop.getItemId(), quantity, stolenDrop.getMetadata());
        } catch (Throwable ignored) {
            stolenItem = new ItemStack(stolenDrop.getItemId(), quantity);
        }

        boolean added = addItemToPlayerInventory(attacker, stolenItem);
        logPlunder("Loot roll: itemId=" + stolenDrop.getItemId()
                + ", qty=" + quantity
                + ", addedToInventory=" + added);
        return added;
    }

    private ItemDropList resolveItemDropList(String dropListId) {
        if (dropListId == null || dropListId.isBlank()) {
            return null;
        }
        try {
            var assetMap = ItemDropList.getAssetMap();
            if (assetMap == null) {
                return null;
            }
            return assetMap.getAsset(dropListId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean addItemToPlayerInventory(Player player, ItemStack item) {
        if (player == null || player.getInventory() == null || item == null || item.isEmpty()) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null && hotbar.canAddItemStack(item)) {
            hotbar.addItemStack(item);
            return true;
        }
        ItemContainer storage = player.getInventory().getStorage();
        if (storage != null && storage.canAddItemStack(item)) {
            storage.addItemStack(item);
            return true;
        }
        return false;
    }

    private boolean refundOneArrow(Player player) {
        if (player == null || player.getInventory() == null) {
            return false;
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer storage = player.getInventory().getStorage();

        if (incrementFirstArrowStack(hotbar)) {
            return true;
        }
        if (incrementFirstArrowStack(storage)) {
            return true;
        }

        ItemStack fallbackArrow = new ItemStack("Weapon_Arrow_Crude", 1);
        if (hotbar != null && hotbar.canAddItemStack(fallbackArrow)) {
            hotbar.addItemStack(fallbackArrow);
            return true;
        }
        if (storage != null && storage.canAddItemStack(fallbackArrow)) {
            storage.addItemStack(fallbackArrow);
            return true;
        }
        return false;
    }

    private boolean incrementFirstArrowStack(ItemContainer container) {
        if (container == null) {
            return false;
        }
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!isArrowAmmo(stack)) {
                continue;
            }
            container.setItemStackForSlot(slot, stack.withQuantity(stack.getQuantity() + 1));
            return true;
        }
        return false;
    }

    private boolean isArrowAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("arrow") || lower.contains("bolt");
    }

    private void logPlunder(String message) {
        if (!PLUNDER_DEBUG_LOG || message == null || message.isBlank()) {
            return;
        }
        System.out.println("[SocketReforge][PLUNDER] " + message);
    }

    private void logStormQuiver(String message) {
        if (!STORM_QUIVER_DEBUG_LOG || message == null || message.isBlank()) {
            return;
        }
        System.out.println("[SocketReforge][STORM] " + message);
    }

    /**
     * Looks in the player's hotbar for the currently held weapon.
     */
    private ItemStack findWeaponInHotbar(Player player) {
        return PlayerInventoryUtils.findFirstInHotbar(player, ReforgeEquip::isWeapon);
    }

    /**
     * Gets all equipped armor pieces from the player.
     */
    private List<ItemStack> getAllEquippedArmor(Player player) {
        return PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
    }
}
