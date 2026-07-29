package irai.mod.reforge.Common;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Shared armor elemental resistance math for combat, tooltips, and bench previews.
 */
public final class ArmorAffinityResistanceUtils {
    public static final double MATCH_RESIST_PERCENT = 3.0d;
    public static final double GREATER_WEIGHT = 1.5d;
    public static final double RESIST_CAP_PERCENT = 60.0d;

    private ArmorAffinityResistanceUtils() {}

    public static double calculateResistancePercent(List<ItemStack> armorPieces, Essence.Type incomingType) {
        if (armorPieces == null || armorPieces.isEmpty() || incomingType == null) {
            return 0.0d;
        }
        double totalPercent = 0.0d;
        for (ItemStack armor : armorPieces) {
            totalPercent += calculateResistancePercent(SocketManager.getSocketData(armor), incomingType);
        }
        return clampResistance(totalPercent);
    }

    public static double calculateResistancePercent(Player player,
                                                    List<ItemStack> armorPieces,
                                                    Essence.Type incomingType) {
        if (armorPieces == null || armorPieces.isEmpty() || incomingType == null) {
            return 0.0d;
        }
        double totalPercent = 0.0d;
        for (ItemStack armor : armorPieces) {
            totalPercent += EquipmentDurabilityPenaltyUtils.scaleBonus(
                    calculateResistancePercent(SocketManager.getSocketData(armor), incomingType),
                    player,
                    armor,
                    EquipmentDurabilityPenaltyUtils.BrokenKind.ARMOR);
        }
        return clampResistance(totalPercent);
    }

    public static double calculateResistancePercent(SocketData socketData, Essence.Type incomingType) {
        if (socketData == null || incomingType == null || socketData.getSockets().isEmpty()) {
            return 0.0d;
        }
        double totalPercent = 0.0d;
        for (Socket socket : socketData.getSockets()) {
            totalPercent += calculateSocketResistancePercent(socket, incomingType);
        }
        return clampResistance(totalPercent);
    }

    public static Map<Essence.Type, Double> calculateResistancePercentByIncomingType(ItemStack armor) {
        return armor == null || armor.isEmpty()
                ? emptyResistanceMap()
                : calculateResistancePercentByIncomingType(SocketManager.getSocketData(armor));
    }

    public static Map<Essence.Type, Double> calculateResistancePercentByIncomingType(SocketData socketData) {
        EnumMap<Essence.Type, Double> result = emptyResistanceMap();
        if (socketData == null || socketData.getSockets().isEmpty()) {
            return result;
        }
        for (Essence.Type incomingType : Essence.Type.values()) {
            result.put(incomingType, calculateResistancePercent(socketData, incomingType));
        }
        return result;
    }

    public static Map<Essence.Type, Double> calculateResistancePercentByIncomingType(List<ItemStack> armorPieces) {
        EnumMap<Essence.Type, Double> result = emptyResistanceMap();
        if (armorPieces == null || armorPieces.isEmpty()) {
            return result;
        }
        for (Essence.Type incomingType : Essence.Type.values()) {
            result.put(incomingType, calculateResistancePercent(armorPieces, incomingType));
        }
        return result;
    }

    public static Map<Essence.Type, Double> calculateResistancePercentByIncomingType(Player player,
                                                                                     List<ItemStack> armorPieces) {
        EnumMap<Essence.Type, Double> result = emptyResistanceMap();
        if (armorPieces == null || armorPieces.isEmpty()) {
            return result;
        }
        for (Essence.Type incomingType : Essence.Type.values()) {
            result.put(incomingType, calculateResistancePercent(player, armorPieces, incomingType));
        }
        return result;
    }

    public static boolean hasAnyResistance(Map<Essence.Type, Double> resistances) {
        if (resistances == null || resistances.isEmpty()) {
            return false;
        }
        for (double value : resistances.values()) {
            if (value > 0.0001d) {
                return true;
            }
        }
        return false;
    }

    public static String formatSummary(Map<Essence.Type, Double> resistances,
                                       Function<Essence.Type, String> nameResolver) {
        if (!hasAnyResistance(resistances)) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        for (Essence.Type type : Essence.Type.values()) {
            double percent = resistances.getOrDefault(type, 0.0d);
            if (percent <= 0.0001d) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            String name = nameResolver == null ? type.name() : nameResolver.apply(type);
            summary.append(name).append(' ').append(formatPercent(percent));
        }
        return summary.toString();
    }

    private static double calculateSocketResistancePercent(Socket socket, Essence.Type incomingType) {
        if (socket == null || socket.isEmpty() || socket.isBroken() || incomingType == null) {
            return 0.0d;
        }
        Essence.Type armorType = SocketManager.getSocketAffinityType(socket);
        if (armorType == null) {
            return 0.0d;
        }
        double socketWeight = SocketManager.isGreaterEssenceId(socket.getEssenceId()) ? GREATER_WEIGHT : 1.0d;
        if (armorType == incomingType) {
            return MATCH_RESIST_PERCENT * socketWeight;
        }
        return 0.0d;
    }

    private static EnumMap<Essence.Type, Double> emptyResistanceMap() {
        EnumMap<Essence.Type, Double> result = new EnumMap<>(Essence.Type.class);
        for (Essence.Type type : Essence.Type.values()) {
            result.put(type, 0.0d);
        }
        return result;
    }

    private static double clampResistance(double value) {
        return Math.max(0.0d, Math.min(RESIST_CAP_PERCENT, value));
    }

    private static String formatPercent(double percent) {
        double rounded = Math.round(percent * 10.0d) / 10.0d;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001d) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", rounded);
    }
}
