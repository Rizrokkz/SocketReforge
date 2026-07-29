package irai.mod.reforge.Common;

import com.hypixel.hytale.server.core.asset.type.gameplay.BrokenPenalties;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.ItemDurabilityConfig;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Applies Hytale's configured broken-equipment penalties to Socket Reforge ECS stat paths.
 */
public final class EquipmentDurabilityPenaltyUtils {
    private EquipmentDurabilityPenaltyUtils() {}

    public static double weaponMultiplier(Player player, ItemStack weapon) {
        return brokenMultiplier(player, weapon, BrokenKind.WEAPON);
    }

    public static double armorMultiplier(Player player, ItemStack armor) {
        return brokenMultiplier(player, armor, BrokenKind.ARMOR);
    }

    public static double scaleBonus(double bonus, Player player, ItemStack equipment, BrokenKind kind) {
        if (bonus == 0.0d) {
            return 0.0d;
        }
        return bonus * brokenMultiplier(player, equipment, kind);
    }

    private static double brokenMultiplier(Player player, ItemStack equipment, BrokenKind kind) {
        if (equipment == null || equipment.isEmpty() || !equipment.isBroken()) {
            return 1.0d;
        }
        BrokenPenalties penalties = resolveBrokenPenalties(player);
        if (penalties == null) {
            return 1.0d;
        }
        double penalty = switch (kind) {
            case WEAPON -> penalties.getWeapon(0.0d);
            case ARMOR -> penalties.getArmor(0.0d);
            case TOOL -> penalties.getTool(0.0d);
        };
        return Math.max(0.0d, 1.0d - Math.max(0.0d, penalty));
    }

    private static BrokenPenalties resolveBrokenPenalties(Player player) {
        if (player == null) {
            return null;
        }
        World world = player.getWorld();
        if (world == null) {
            return null;
        }
        GameplayConfig gameplayConfig = world.getGameplayConfig();
        if (gameplayConfig == null) {
            return null;
        }
        ItemDurabilityConfig durabilityConfig = gameplayConfig.getItemDurabilityConfig();
        return durabilityConfig == null ? null : durabilityConfig.getBrokenPenalties();
    }

    public enum BrokenKind {
        WEAPON,
        ARMOR,
        TOOL
    }
}
