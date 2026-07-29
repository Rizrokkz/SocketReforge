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
import irai.mod.reforge.Common.EquipmentDurabilityPenaltyUtils;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.WeaponElementalDamageUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Lore.LoreDamageUtils;
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
        boolean loreDamage = Boolean.TRUE.equals(damage.getIfPresentMetaObject(LoreDamageUtils.META_LORE_DAMAGE));

        // Get target (entity receiving damage)
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

        // Get damage source
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        LoreProcHandler.enforceSignatureEnergyLock(store, attackerRef);
        LoreDamageUtils.traceSignatureEnergy(store, attackerRef, targetRef, "EquipmentRefineEST.before");
        boolean skipRefine = loreDamage || Boolean.TRUE.equals(damage.getIfPresentMetaObject(META_SKIP_REFORGE));

        // ── Attacker weapon bonus (damage multiplier) ─────────────────────────
        if (!skipRefine) {
            Player attacker = store.getComponent(attackerRef, Player.getComponentType());
            if (attacker != null) {
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    float baseDamage = damage.getAmount();
                    int upgradeLevel = ReforgeEquip.getLevelFromItem(weapon);
                    int clampedLevel = clampLevel(upgradeLevel);
                    double refinementMultiplier = getDamageMultiplier(clampedLevel);
                    double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(weapon);
                    double socketMultiplier = calculateSocketDamageBonus(weapon);
                    double socketFlat = calculateSocketFlatDamage(weapon);
                    double attackSpeedPercent = calculateSocketAttackSpeedPercent(weapon);
                    double partsMultiplier = getPartsDamageMultiplier(weapon);
                    int voidTier = getRawEssenceTier(weapon, Essence.Type.VOID);
                    double critChancePercent = calculateSocketCritChancePercent(weapon);
                    double critDamagePercent = calculateSocketCritDamagePercent(weapon);

                    float newDamage = (float) ((baseDamage
                            * refinementMultiplier
                            * softcoreMultiplier
                            * socketMultiplier
                            * partsMultiplier) + socketFlat);
                    if (attackSpeedPercent > 0.0) {
                        // Runtime fallback: convert attack speed bonus into effective DPS multiplier.
                        newDamage = (float) (newDamage * (1.0 + (attackSpeedPercent / 100.0)));
                    }

                    boolean isCrit = critChancePercent > 0.0
                            && ThreadLocalRandom.current().nextDouble(100.0) < critChancePercent;
                    if (isCrit) {
                        DamageNumberMeta.markCritical(damage);
                    }
                    if (isCrit && critDamagePercent > 0.0) {
                        newDamage = (float) (newDamage * (1.0 + (critDamagePercent / 100.0)));
                    }

                    int equippedVoidEssenceCount = countEquippedVoidEssences(attacker, weapon);
                    float bloodPactDamage = applyVoidTierFiveBloodPact(store, attackerRef, voidTier, equippedVoidEssenceCount);
                    if (bloodPactDamage > 0f) {
                        newDamage += bloodPactDamage;
                    }

                    WeaponElementalDamageUtils.AffinityDamage affinityDamage =
                            WeaponElementalDamageUtils.calculateAffinityDamage(weapon, newDamage, store, targetRef);
                    if (affinityDamage.elementDamagePayload() != null && !affinityDamage.elementDamagePayload().isBlank()) {
                        damage.putMetaObject(META_WEAPON_ELEMENTAL_DAMAGE, affinityDamage.elementDamagePayload());
                    }
                    if (affinityDamage.type() != null && Math.abs(affinityDamage.damageDelta()) > 0.0001f) {
                        newDamage = Math.max(0f, newDamage + affinityDamage.damageDelta());
                        markAffinityDamageKind(damage, affinityDamage.type());
                    }
                    double brokenWeaponMultiplier = EquipmentDurabilityPenaltyUtils.weaponMultiplier(attacker, weapon);
                    if (brokenWeaponMultiplier != 1.0d) {
                        newDamage = (float) Math.max(0.0d, newDamage * brokenWeaponMultiplier);
                    }

                    damage.setAmount(newDamage);

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
                                + " affinityDelta=" + affinityDamage.damageDelta()
                                + " brokenWeaponMultiplier=" + brokenWeaponMultiplier
                                + " final=" + newDamage);
                    }
                }
            }
        }

        // ── Defender armor bonus (defense / damage reduction) ─────────────────
        if (skipRefine && !isPhysicalBleedLoreDamage(damage)) {
            Player attacker = store.getComponent(attackerRef, Player.getComponentType());
            if (attacker != null) {
                ItemStack weapon = findWeaponInHotbar(attacker);
                if (weapon != null && ReforgeEquip.isWeapon(weapon)) {
                    float newDamage = damage.getAmount();
                    WeaponElementalDamageUtils.AffinityDamage affinityDamage =
                            WeaponElementalDamageUtils.calculateAffinityDamage(weapon, newDamage, store, targetRef);
                    if (affinityDamage.elementDamagePayload() != null && !affinityDamage.elementDamagePayload().isBlank()) {
                        damage.putMetaObject(META_WEAPON_ELEMENTAL_DAMAGE,
                                WeaponElementalDamageUtils.mergeElementDamagePayload(
                                        damage.getIfPresentMetaObject(META_WEAPON_ELEMENTAL_DAMAGE),
                                        affinityDamage.elementDamagePayload()));
                    }
                    if (affinityDamage.type() != null && Math.abs(affinityDamage.damageDelta()) > 0.0001f) {
                        newDamage = Math.max(0f, newDamage + affinityDamage.damageDelta());
                        markAffinityDamageKind(damage, affinityDamage.type());
                    }
                    double brokenWeaponMultiplier = EquipmentDurabilityPenaltyUtils.weaponMultiplier(attacker, weapon);
                    if (brokenWeaponMultiplier != 1.0d) {
                        newDamage = (float) Math.max(0.0d, newDamage * brokenWeaponMultiplier);
                    }
                    damage.setAmount(newDamage);
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

        LoreDamageUtils.traceSignatureEnergy(store, attackerRef, targetRef, "EquipmentRefineEST.after");
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
                double multiplier = getDefenseMultiplier(clampedLevel) * ReforgeEquip.getSoftcoreStatMultiplier(armor);
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

    private void markAffinityDamageKind(Damage damage, Essence.Type type) {
        DamageNumberFormatter.DamageKind kind = switch (type) {
            case FIRE -> DamageNumberFormatter.DamageKind.BURN;
            case ICE -> DamageNumberFormatter.DamageKind.ICE;
            case LIGHTNING -> DamageNumberFormatter.DamageKind.SHOCK;
            case WATER -> DamageNumberFormatter.DamageKind.WATER;
            case VOID -> DamageNumberFormatter.DamageKind.VOID;
            case LIFE -> DamageNumberFormatter.DamageKind.LIFE;
        };
        DamageNumberMeta.markKind(damage, kind);
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
