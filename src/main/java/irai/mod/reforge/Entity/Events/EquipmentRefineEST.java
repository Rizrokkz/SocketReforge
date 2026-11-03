package irai.mod.reforge.Entity.Events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Common.AscensionUtils;
import irai.mod.reforge.Common.EnemyElementalShieldUtils;
import irai.mod.reforge.Common.EquipmentDurabilityPenaltyUtils;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.WeaponElementalDamageUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Interactions.CoreRuneDamageContext;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Lore.LoreDamageUtils;
import irai.mod.reforge.Lore.CoreRuneHandler;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Util.DamageNumberFormatter;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumberMeta;


@SuppressWarnings("removal")
public class EquipmentRefineEST extends DamageEventSystem {
    private static final String META_PARTS_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";
    private static final double VOID_T5_HP_SACRIFICE_RATE_PER_ESSENCE = 0.01d;
    private static final boolean DEBUG_DAMAGE_LOG = Boolean.parseBoolean(
            System.getProperty("socketreforge.debug.damage", "false"));
    public static final MetaKey<Boolean> META_SKIP_REFORGE =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:skip_reforge", Codec.BOOLEAN);
    public static final MetaKey<String> META_WEAPON_ELEMENTAL_DAMAGE =
            Damage.META_REGISTRY.registerMetaObject(d -> "", false,
                    "socketreforge:weapon_elemental_damage", Codec.STRING);
    public static final MetaKey<Boolean> META_SOCKET_ELEMENTAL_COMPONENT =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:elemental_component", Codec.BOOLEAN);
    public static final MetaKey<Boolean> META_SOCKET_ELEMENTAL_AFFINITY_HANDLED =
            Damage.META_REGISTRY.registerMetaObject(d -> Boolean.FALSE, false,
                    "socketreforge:elemental_affinity_handled", Codec.BOOLEAN);

    // Refinement config - will be injected from plugin
    private RefinementConfig refinementConfig;

    // Default damage multipliers per upgrade level (0..3) - used if config not loaded
    private static final double[] DEFAULT_DAMAGE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.10,  // +1 (10% increase)
            1.15,  // +2 (15% increase)
            1.25   // +3 (25% increase)
    };

    // Default defense multipliers per upgrade level (0..3) - used if config not loaded
    private static final double[] DEFAULT_DEFENSE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.08,  // +1 (8% increase)
            1.12,  // +2 (12% increase)
            1.20   // +3 (20% increase)
    };

    /**
     * Sets the refinement config for this system.
     * @param config The refinement configuration to use
     */
    public void setRefinementConfig(RefinementConfig config) {
        this.refinementConfig = config;
    }

    /**
     * Gets the damage multiplier for a given weapon level.
     * Uses config if available, otherwise uses defaults.
     */
    private double getDamageMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDamageMultiplier(level);
        }
        if (level < 0 || level >= DEFAULT_DAMAGE_MULTIPLIERS.length) return 1.0;
        return DEFAULT_DAMAGE_MULTIPLIERS[level];
    }

    /**
     * Gets the defense multiplier for a given armor level.
     * Uses config if available, otherwise uses defaults.
     */
    private double getDefenseMultiplier(int level) {
        if (refinementConfig != null) {
            return refinementConfig.getDefenseMultiplier(level);
        }
        if (level < 0 || level >= DEFAULT_DEFENSE_MULTIPLIERS.length) return 1.0;
        return DEFAULT_DEFENSE_MULTIPLIERS[level];
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
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
        if (damage != null && Boolean.TRUE.equals(damage.getIfPresentMetaObject(
                EnemyElementalShieldUtils.META_NATIVE_SHIELD_FEEDBACK_EVENT))) {
            return;
        }
        float amountBeforeFilter = damage == null ? 0.0f : damage.getAmount();
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (LoreProcHandler.shouldCancelOmnislashRechainSlamDamage(store, targetRef, damage)) {
            damage.setCancelled(true);
            return;
        }

        boolean loreDamage = Boolean.TRUE.equals(damage.getIfPresentMetaObject(LoreDamageUtils.META_LORE_DAMAGE));
        boolean statusDamage = LoreDamageUtils.isStatusDamage(damage);
        if (statusDamage) {
            damage.putMetaObject(LoreDamageUtils.META_STATUS_DAMAGE, Boolean.TRUE);
        }
        boolean applyLoreEquipmentBonuses = Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(LoreDamageUtils.META_APPLY_EQUIPMENT_DAMAGE_BONUSES));
        boolean elementalComponent = Boolean.TRUE.equals(
                damage.getIfPresentMetaObject(META_SOCKET_ELEMENTAL_COMPONENT));

        // Get target (entity receiving damage)
        // Get damage source
        Damage.Source source = damage.getSource();
        Ref<EntityStore> attackerRef;
        boolean projectileSource = source instanceof Damage.ProjectileSource;
        if (source instanceof Damage.EntitySource entitySource) {
            attackerRef = entitySource.getRef();
        } else if (source instanceof Damage.ProjectileSource projectileDamageSource) {
            attackerRef = projectileDamageSource.getRef();
        } else {
            return;
        }
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        boolean runeWeaponBaseApplied = !statusDamage && !elementalComponent
                && CoreRuneDamageContext.applyWeaponBaseDamage(store, attackerRef, damage);
        boolean npcRuneDamage = attacker == null && runeWeaponBaseApplied;
        if (!statusDamage && !npcRuneDamage && !elementalComponent) {
            LoreProcHandler.enforceSignatureEnergyLock(store, attackerRef);
            LoreDamageUtils.traceSignatureEnergy(store, attackerRef, targetRef, "EquipmentRefineEST.before");
        }
        Float rawAmount = damage.getIfPresentMetaObject(DamageStackDelayEST.META_RAW_AMOUNT);
        float baseDamage = runeWeaponBaseApplied
                ? damage.getAmount()
                : rawAmount != null && rawAmount > 0.0f ? rawAmount : damage.getAmount();
        if (runeWeaponBaseApplied) {
            damage.putMetaObject(DamageStackDelayEST.META_BONUS_BASE_AMOUNT, baseDamage);
            DamageStackDelayEST.logDamageStage("core-rune:weapon-base", damage,
                    rawAmount == null ? amountBeforeFilter : rawAmount, baseDamage,
                    "native rune coefficient scaled by weapon base before equipment bonuses");
        }
        boolean shattered = false;
        if (!statusDamage && !loreDamage && attacker != null && targetRef != null) {
            // Shatter changes only the incoming base hit; bonuses are calculated afterwards.
            shattered = LoreProcHandler.tryApplyFrozenShatter(store, attackerRef, targetRef, damage);
            if (shattered) {
                baseDamage = damage.getAmount();
            }
        }
        damage.putMetaObject(DamageStackDelayEST.META_BONUS_BASE_AMOUNT, baseDamage);
        DamageStackDelayEST.logDamageStage("equipment:frozen-shatter", damage,
                rawAmount == null ? amountBeforeFilter : rawAmount, baseDamage,
                "triggered=" + shattered);
        ItemStack attackerWeapon = attacker == null ? null : findWeaponInHotbar(attacker);
        if (!statusDamage && !elementalComponent && !shattered
                && attackerWeapon != null && ReforgeEquip.isWeapon(attackerWeapon)) {
            LoreProcHandler.addWeaponFreezeBuildup(store, targetRef, attackerWeapon, damage.getAmount());
        }
        // Player projectile damage otherwise follows the native pipeline unchanged. Run the
        // weapon's freeze buildup and Frozen Shatter checks above so projectile hits participate too.
        if (projectileSource && !runeWeaponBaseApplied && !elementalComponent) {
            return;
        }
        boolean skipRefine = statusDamage || (loreDamage && !applyLoreEquipmentBonuses)
                || Boolean.TRUE.equals(damage.getIfPresentMetaObject(META_SKIP_REFORGE))
                || elementalComponent;

        // ── Attacker weapon bonus (damage multiplier) ─────────────────────────
        if (!skipRefine) {
            if (attacker != null) {
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    int upgradeLevel = ReforgeEquip.getLevelFromItem(weapon);
                    int clampedLevel = clampLevel(upgradeLevel);
                    double refinementMultiplier = getDamageMultiplier(clampedLevel);
                    double ascensionMultiplier = AscensionUtils.getAscensionBaseMultiplier(weapon);
                    double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(weapon);
                    double socketMultiplier = calculateSocketDamageBonus(weapon);
                    double socketFlat = calculateSocketFlatDamage(weapon);
                    double attackSpeedPercent = calculateSocketAttackSpeedPercent(weapon);
                    double partsMultiplier = getPartsDamageMultiplier(weapon);
                    int voidTier = getRawEssenceTier(weapon, Essence.Type.VOID);
                    double critChancePercent = calculateSocketCritChancePercent(weapon);
                    double critDamagePercent = calculateSocketCritDamagePercent(weapon);

                    boolean chargedCrit = CoreRuneHandler.consumeCriticalChargeGuarantee(attacker, weapon);
                    boolean isCrit = chargedCrit || (critChancePercent > 0.0
                            && ThreadLocalRandom.current().nextDouble(100.0) < critChancePercent);
                    if (isCrit) {
                        DamageNumberMeta.markCritical(damage);
                    }
                    double effectiveCritDamagePercent = chargedCrit
                            ? Math.max(50.0d, critDamagePercent)
                            : critDamagePercent;
                    // Calculate each percentage from the same post-Shatter base.
                    double additivePercent = (refinementMultiplier - 1.0d)
                            + (ascensionMultiplier - 1.0d)
                            + (softcoreMultiplier - 1.0d)
                            + (socketMultiplier - 1.0d)
                            + (partsMultiplier - 1.0d)
                            + (attackSpeedPercent / 100.0d)
                            + (isCrit ? effectiveCritDamagePercent / 100.0d : 0.0d);
                    // Flat socket damage is balanced around a normal 1.0-base hit. Applying the
                    // full flat amount to every tiny multi-hit ability hit lets low coefficients
                    // (for example Razorstrike's repeated rock spikes) outscale a single hit.
                    double socketFlatScale = Math.min(1.0d, Math.max(0.0d, baseDamage));
                    double appliedSocketFlat = socketFlat * socketFlatScale;
                    float newDamage = (float) (baseDamage + (baseDamage * additivePercent) + appliedSocketFlat);

                    int equippedVoidEssenceCount = countEquippedVoidEssences(attacker, weapon);
                    float bloodPactDamage = applyVoidTierFiveBloodPact(store, attackerRef, voidTier, equippedVoidEssenceCount);
                    if (bloodPactDamage > 0f) {
                        newDamage += bloodPactDamage;
                    }

                    double brokenWeaponMultiplier = EquipmentDurabilityPenaltyUtils.weaponMultiplier(attacker, weapon);
                    if (brokenWeaponMultiplier != 1.0d) {
                        newDamage += (float) (baseDamage * (brokenWeaponMultiplier - 1.0d));
                    }
                    newDamage = Math.max(0.0f, newDamage);

                    // Essence and resonance affinity add a percent of the original hit base;
                    // weapon refinement, socket bonuses, and crit must not inflate that base.
                    WeaponElementalDamageUtils.AffinityDamage affinityDamage =
                            WeaponElementalDamageUtils.calculateAffinityDamage(weapon, baseDamage, store, targetRef);
                    if (affinityDamage.type() != null) {
                        damage.putMetaObject(META_SOCKET_ELEMENTAL_AFFINITY_HANDLED, Boolean.TRUE);
                    }

                    DamageStackDelayEST.logDamageStage("equipment:weapon", damage, baseDamage, newDamage,
                            String.format(java.util.Locale.ROOT,
                                    "weapon=%s shattered=%s refine=%.4f ascension=%.4f softcore=%.4f socket=%.4f parts=%.4f "
                                            + "socketFlat=%.4f socketFlatScale=%.4f socketFlatApplied=%.4f "
                                            + "attackSpeedPct=%.2f crit=%s critDamagePct=%.2f "
                                    + "additivePercent=%.4f bloodPact=%.4f affinity=%s affinityBonus=%.4f broken=%.4f",
                                    weapon.getItemId(), shattered, refinementMultiplier, ascensionMultiplier, softcoreMultiplier,
                                    socketMultiplier, partsMultiplier, socketFlat, socketFlatScale, appliedSocketFlat,
                                    attackSpeedPercent, isCrit,
                                    effectiveCritDamagePercent, additivePercent, bloodPactDamage, affinityDamage.type(),
                                    affinityDamage.elementalDamage(), brokenWeaponMultiplier));

                    damage.setAmount(newDamage);
                    WeaponElementalDamageUtils.applyNativeAffinityDamage(
                            commandBuffer, targetRef, damage, affinityDamage);

                    if (DEBUG_DAMAGE_LOG) {
                        System.out.println("[SocketReforge][ATK_DMG] attacker=" + attacker.getUuid()
                                + " weapon=" + weapon.getItemId()
                                + " base=" + baseDamage
                                + " refineMult=" + refinementMultiplier
                                + " softcoreMult=" + softcoreMultiplier
                                + " socketMult=" + socketMultiplier
                                + " partsMult=" + partsMultiplier
                                + " socketFlat=" + socketFlat
                                + " atkSpd=" + attackSpeedPercent
                                + " critChance=" + critChancePercent
                                + " critDamage=" + critDamagePercent
                                + " critApplied=" + isCrit
                                + " voidTier=" + voidTier
                                + " voidEssences=" + equippedVoidEssenceCount
                                + " bloodPact=" + bloodPactDamage
                                + " affinity=" + affinityDamage.type()
                                + " affinityWeight=" + affinityDamage.weight()
                                + " affinityRate=" + affinityDamage.rate()
                                + " targetAffinity=" + affinityDamage.targetType()
                                + " affinityMultiplier=" + affinityDamage.effectivenessMultiplier()
                                + " affinityBonus=" + affinityDamage.elementalDamage()
                                + " brokenWeaponMultiplier=" + brokenWeaponMultiplier
                                + " final=" + newDamage);
                    }
                }
            }
        }

        // ── Defender armor bonus (defense / damage reduction) ─────────────────
        if (skipRefine && !statusDamage && !elementalComponent && !isPhysicalBleedLoreDamage(damage)) {
            if (attacker != null) {
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    float newDamage = damage.getAmount();
                    float bonusBase = DamageStackDelayEST.getBonusBaseDamage(damage, newDamage);
                    WeaponElementalDamageUtils.AffinityDamage affinityDamage =
                            WeaponElementalDamageUtils.calculateAffinityDamage(weapon, bonusBase, store, targetRef);
                    if (affinityDamage.type() != null) {
                        damage.putMetaObject(META_SOCKET_ELEMENTAL_AFFINITY_HANDLED, Boolean.TRUE);
                    }
                    double brokenWeaponMultiplier = EquipmentDurabilityPenaltyUtils.weaponMultiplier(attacker, weapon);
                    if (brokenWeaponMultiplier != 1.0d) {
                        newDamage += (float) (bonusBase * (brokenWeaponMultiplier - 1.0d));
                    }
                    damage.setAmount(Math.max(0.0f, newDamage));
                    WeaponElementalDamageUtils.applyNativeAffinityDamage(
                            commandBuffer, targetRef, damage, affinityDamage);
                }
            }
        }

        Player defender = store.getComponent(targetRef, Player.getComponentType());
        if (defender != null) {
            List<ItemStack> armorPieces = getAllEquippedArmor(defender);
            if (!armorPieces.isEmpty()) {
                double avgDefenseMultiplier = calculateAverageDefenseMultiplier(defender, armorPieces);

                // Get the highest level armor for display purposes
                int highestLevel = 0;
                for (ItemStack armor : armorPieces) {
                    String armorId = getItemId(armor);
                    if (armorId != null) {
                        int level = ReforgeEquip.getLevelFromItem(armor);
                        if (level > highestLevel) highestLevel = level;
                    }
                }
                int clampedLevel = clampLevel(highestLevel);

                float reducedDamage = (float) (damage.getAmount() / avgDefenseMultiplier);
                damage.setAmount(reducedDamage);
            }
        }

        if (!statusDamage && !npcRuneDamage && !elementalComponent) {
            LoreDamageUtils.traceSignatureEnergy(store, attackerRef, targetRef, "EquipmentRefineEST.after");
        }
        DamageStackDelayEST.logDamageStage("equipment:filter-total", damage,
                amountBeforeFilter, damage.getAmount(),
                "loreDamage=" + loreDamage + ",applyLoreEquipmentBonuses=" + applyLoreEquipmentBonuses
                        + ",skipRefine=" + skipRefine);
    }

    /**
     * Looks in the player's hotbar for the currently held weapon.
     */
    private ItemStack findWeaponInHotbar(Player player) {
        return PlayerInventoryUtils.findFirstInHotbar(player, ReforgeEquip::isWeapon);
    }

    /**
     * Gets all equipped armor pieces from the player's armor equipment slots only.
     * Does NOT check inventory - only actual equipped armor.
     * Returns a list of armor ItemStacks.
     */
    private List<ItemStack> getAllEquippedArmor(Player player) {
        return PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
    }

    /**
     * Calculates the average defense multiplier from all equipped armor pieces.
     */
    private double calculateAverageDefenseMultiplier(Player player, List<ItemStack> armorPieces) {
        if (armorPieces.isEmpty()) {
            return 1.0; // No armor, no bonus
        }

        double totalMultiplier = 0.0;
        int count = 0;

        for (ItemStack armor : armorPieces) {
            String armorId = getItemId(armor);
            if (armorId != null) {
                int level = ReforgeEquip.getLevelFromItem(armor);
                int clampedLevel = clampLevel(level);
                double multiplier = getDefenseMultiplier(clampedLevel) * AscensionUtils.getAscensionBaseMultiplier(armor) * ReforgeEquip.getSoftcoreStatMultiplier(armor);
                double armorMultiplier = EquipmentDurabilityPenaltyUtils.armorMultiplier(player, armor);
                totalMultiplier += 1.0d + ((multiplier - 1.0d) * armorMultiplier);
                count++;
            }
        }

        if (count == 0) {
            return 1.0;
        }

        return totalMultiplier / count;
    }

    /**
     * Safely gets item ID from an ItemStack.
     */
    private String getItemId(ItemStack item) {
        if (item == null) return null;

        try {
            return item.getItemId();
        } catch (Exception e1) {
            try {
                if (item.getItem() != null) {
                    return item.getItem().getId();
                }
            } catch (Exception e2) {
                // Fallback: return null
            }
        }
        return null;
    }

    /**
     * Reads socket percentage damage bonus from weapon metadata (with fallback).
     */
    private double calculateSocketDamageBonus(ItemStack weapon) {
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 1.0;
        }
        double[] bonuses = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.DAMAGE);
        if (bonuses[0] == 0.0 && bonuses[1] == 0.0) {
            bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.DAMAGE, true);
        }
        return 1.0 + (bonuses[1] / 100.0);
    }

    /**
     * Reads socket flat damage bonus from weapon metadata (with fallback).
     */
    private double calculateSocketFlatDamage(ItemStack weapon) {
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) {
            return 0.0;
        }
        double[] bonuses = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.DAMAGE);
        if (bonuses[0] == 0.0 && bonuses[1] == 0.0) {
            bonuses = SocketManager.calculateTieredBonus(socketData, EssenceEffect.StatType.DAMAGE, true);
        }
        return bonuses[0];
    }

    private boolean isPhysicalBleedLoreDamage(Damage damage) {
        return damage != null
                && Boolean.TRUE.equals(damage.getIfPresentMetaObject(LoreDamageUtils.META_LORE_DAMAGE))
                && DamageNumberFormatter.DamageKind.BLEED == DamageNumberMeta.readKind(damage);
    }

    /**
     * Deterministic attack speed from Lightning tier.
     * Tier scaling: +1% per tier (T1..T5 => 1..5%).
     */
    private double calculateSocketAttackSpeedPercent(ItemStack weapon) {
        double[] stored = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.ATTACK_SPEED);
        if (stored[1] > 0.0) {
            return Math.max(0.0, Math.min(100.0, stored[1]));
        }
        int lightningTier = getEssenceTier(weapon, Essence.Type.LIGHTNING);
        return Math.max(0.0, Math.min(100.0, lightningTier));
    }

    /**
     * Deterministic crit chance from Lightning tier.
     * Tier scaling: +1% per tier (T1..T5 => 1..5%).
     */
    private double calculateSocketCritChancePercent(ItemStack weapon) {
        double[] stored = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.CRIT_CHANCE);
        if (stored[1] > 0.0) {
            return Math.max(0.0, Math.min(100.0, stored[1]));
        }
        int lightningTier = getEssenceTier(weapon, Essence.Type.LIGHTNING);
        return Math.max(0.0, Math.min(100.0, lightningTier));
    }

    /**
     * Deterministic crit damage from Void tier.
     * Tier scaling: +5% per tier (T1..T5 => 5..25%).
     */
    private double calculateSocketCritDamagePercent(ItemStack weapon) {
        double[] stored = SocketManager.getStoredStatBonus(weapon, EssenceEffect.StatType.CRIT_DAMAGE);
        if (stored[1] > 0.0) {
            return Math.max(0.0, Math.min(200.0, stored[1]));
        }
        int voidTier = getEssenceTier(weapon, Essence.Type.VOID);
        return Math.max(0.0, Math.min(25.0, voidTier * 5.0));
    }

    private int getEssenceTier(ItemStack weapon, Essence.Type type) {
        if (weapon == null || weapon.isEmpty() || type == null) return 0;
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) return 0;
        Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(socketData);
        Integer tier = tierMap.get(type);
        if (tier == null) return 0;
        return Math.max(0, Math.min(5, tier));
    }

    private int getRawEssenceTier(ItemStack weapon, Essence.Type type) {
        if (weapon == null || weapon.isEmpty() || type == null) return 0;
        SocketData socketData = SocketManager.getSocketData(weapon);
        if (socketData == null || socketData.getMaxSockets() == 0) return 0;
        Map<Essence.Type, Integer> tierMap = SocketManager.calculateRawConsecutiveTiers(socketData);
        Integer tier = tierMap.get(type);
        if (tier == null) return 0;
        return Math.max(0, Math.min(5, tier));
    }

    /**
     * Void T5 blood pact:
     * consumes 1% of attacker's max HP per equipped Void essence
     * (weapon + equipped armor) and adds that amount to outgoing damage.
     */
    private float applyVoidTierFiveBloodPact(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             int voidTier,
                                             int equippedVoidEssenceCount) {
        if (voidTier < 5 || equippedVoidEssenceCount <= 0 || store == null || attackerRef == null) {
            return 0f;
        }
        EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }

        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return 0f;
        }

        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.get() <= 0f) {
            return 0f;
        }

        float currentHealth = health.get();
        float maxHealth = health.getMax();
        if (maxHealth <= 0f) {
            return 0f;
        }
        double hpCostRate = VOID_T5_HP_SACRIFICE_RATE_PER_ESSENCE * equippedVoidEssenceCount;
        float hpCost = (float) (maxHealth * hpCostRate);
        if (hpCost <= 0f) {
            return 0f;
        }

        // All-or-nothing: do not scale down at low HP.
        // Keep a tiny buffer to avoid immediate self-KO from this effect alone.
        float maxSpendable = Math.max(0f, currentHealth - 0.1f);
        if (hpCost > maxSpendable) {
            return 0f;
        }

        statMap.addStatValue(healthStatIndex, -hpCost);
        return hpCost;
    }

    private int countEquippedVoidEssences(Player attacker, ItemStack weapon) {
        int total = countVoidEssences(weapon);
        if (attacker == null) {
            return total;
        }
        for (ItemStack armor : getAllEquippedArmor(attacker)) {
            total += countVoidEssences(armor);
        }
        return total;
    }

    private int countVoidEssences(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        SocketData socketData = SocketManager.getSocketData(item);
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isBroken() || socket.isLocked()) {
                continue;
            }
            String essenceId = socket.getEssenceId();
            if (essenceId == null || essenceId.isBlank()) {
                continue;
            }
            Essence essence = EssenceRegistry.get().getById(essenceId);
            if (essence != null && essence.getType() == Essence.Type.VOID) {
                count++;
            }
        }
        return count;
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

    private int clampLevel(int level) {
        int max = refinementConfig != null ? refinementConfig.getMaxLevel() : 3;
        return Math.max(0, Math.min(level, max));
    }

    private double getPartsDamageMultiplier(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 1.0;
        }
        Double value = weapon.getFromMetadataOrNull(META_PARTS_DAMAGE_MULTIPLIER, Codec.DOUBLE);
        if (value == null) {
            return 1.0;
        }
        return Math.max(0.5, Math.min(2.0, value));
    }

}
