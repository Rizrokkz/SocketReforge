package irai.mod.reforge.Entity.Events;

import java.util.List;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Common.EquipmentDurabilityPenaltyUtils;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Aggregates socket bonuses from equipped armor and applies global balancing.
 * Balancing is tuned for 4 equipped armor pieces.
 */
public final class SocketArmorBonusHelper {

    private SocketArmorBonusHelper() {}

    // Rebalance multipliers for 4-piece armor stacking.
    // Health is left unscaled so Life essence tooltip values match live max-health bonuses.
    private static final double REGEN_SCALE = 0.35;
    private static final double DEFENSE_SCALE = 0.65;
    private static final double BLOCK_CHANCE_SCALE = 0.70;
    private static final double EVASION_SCALE = 0.60;
    private static final double SLOW_SCALE = 0.60;

    // Hard caps after scaling.
    private static final double REGEN_CAP = 3.5;
    private static final double DEFENSE_CAP = 35.0;
    private static final double BLOCK_CHANCE_CAP = 40.0;
    private static final double EVASION_CAP = 25.0;
    private static final double SLOW_CAP = 30.0;

    public static List<ItemStack> getEquippedArmor(Player player) {
        return PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
    }

    public static double getScaledFlatBonus(Player player, EssenceEffect.StatType stat) {
        double total = 0.0;
        for (ItemStack armor : getEquippedArmor(player)) {
            total += EquipmentDurabilityPenaltyUtils.scaleBonus(
                    SocketManager.getStoredStatBonus(armor, stat)[0],
                    player,
                    armor,
                    EquipmentDurabilityPenaltyUtils.BrokenKind.ARMOR);
        }
        return applyBalance(stat, total);
    }

    public static double getScaledPercentBonus(Player player, EssenceEffect.StatType stat) {
        double total = 0.0;
        for (ItemStack armor : getEquippedArmor(player)) {
            total += EquipmentDurabilityPenaltyUtils.scaleBonus(
                    SocketManager.getStoredStatBonus(armor, stat)[1],
                    player,
                    armor,
                    EquipmentDurabilityPenaltyUtils.BrokenKind.ARMOR);
        }
        return applyBalance(stat, total);
    }

    private static double applyBalance(EssenceEffect.StatType stat, double raw) {
        if (raw <= 0.0) return 0.0;

        return switch (stat) {
            case HEALTH -> raw;
            case REGENERATION -> Math.min(REGEN_CAP, raw * REGEN_SCALE);
            case DEFENSE -> Math.min(DEFENSE_CAP, raw * DEFENSE_SCALE);
            case BLOCK_CHANCE -> Math.min(BLOCK_CHANCE_CAP, raw * BLOCK_CHANCE_SCALE);
            case EVASION -> Math.min(EVASION_CAP, raw * EVASION_SCALE);
            case MOVEMENT_SPEED -> Math.min(SLOW_CAP, raw * SLOW_SCALE);
            default -> raw;
        };
    }
}
