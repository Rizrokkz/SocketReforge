package irai.mod.reforge.Common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Shared weapon elemental damage preview math for tooltips and UI panels.
 */
public final class WeaponElementalDamageUtils {
    public static final double DAMAGE_RATE_PER_SOCKET_WEIGHT = 0.05d;
    public static final double GREATER_WEIGHT = 1.5d;

    private WeaponElementalDamageUtils() {}

    public static List<ElementDamage> calculateElementDamage(ItemStack weapon, double damageOutput) {
        if (weapon == null || weapon.isEmpty()) {
            return List.of();
        }
        return calculateElementDamage(SocketManager.getSocketData(weapon), damageOutput);
    }

    public static List<ElementDamage> calculateElementDamage(SocketData socketData, double damageOutput) {
        if (socketData == null || socketData.getSockets().isEmpty() || damageOutput <= 0.0d) {
            return List.of();
        }

        Map<Essence.Type, Double> weights = new EnumMap<>(Essence.Type.class);
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isEmpty() || socket.isBroken()) {
                continue;
            }
            double socketWeight = SocketManager.isGreaterEssenceId(socket.getEssenceId()) ? GREATER_WEIGHT : 1.0d;
            Essence rawEssence = EssenceRegistry.get().getById(socket.getEssenceId());
            if (rawEssence != null && rawEssence.getType() != null) {
                addWeight(weights, rawEssence.getType(), socketWeight);
            }
            Essence.Type mutationType = SocketManager.parseEssenceType(socket.getMutationElement());
            if (mutationType != null) {
                addWeight(weights, mutationType, socketWeight);
            }
        }

        List<ElementDamage> result = new ArrayList<>();
        for (Essence.Type type : Essence.Type.values()) {
            double weight = weights.getOrDefault(type, 0.0d);
            if (weight <= 0.0d) {
                continue;
            }
            double rate = Math.min(1.0d, weight * DAMAGE_RATE_PER_SOCKET_WEIGHT);
            result.add(new ElementDamage(type, weight, rate, damageOutput * rate));
        }
        return List.copyOf(result);
    }

    public static AffinityDamage calculateAffinityDamage(ItemStack weapon,
                                                        double damageOutput,
                                                        Store<EntityStore> store,
                                                        Ref<EntityStore> targetRef) {
        if (weapon == null || weapon.isEmpty()) {
            return AffinityDamage.none();
        }
        return calculateAffinityDamage(SocketManager.getSocketData(weapon), damageOutput, store, targetRef);
    }

    public static AffinityDamage calculateAffinityDamage(SocketData socketData,
                                                        double damageOutput,
                                                        Store<EntityStore> store,
                                                        Ref<EntityStore> targetRef) {
        if (socketData == null || socketData.getMaxSockets() <= 0 || damageOutput <= 0.0d) {
            return AffinityDamage.none();
        }

        List<ElementDamage> elementDamages = calculateElementDamage(socketData, damageOutput);
        if (elementDamages.isEmpty()) {
            return AffinityDamage.none();
        }

        Essence.Type targetType = ElementalAffinityUtils.resolveTargetAffinity(store, targetRef);
        Essence.Type strongestType = null;
        double strongestWeight = 0.0d;
        double strongestRate = 0.0d;
        double strongestMultiplier = ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER;
        double strongestDelta = 0.0d;
        double strongestComponentDamage = 0.0d;
        float totalDelta = 0f;
        Map<Essence.Type, Double> adjustedElementDamage = new EnumMap<>(Essence.Type.class);

        for (ElementDamage elementDamage : elementDamages) {
            double effectivenessMultiplier = ElementalAffinityUtils.effectivenessMultiplier(
                    elementDamage.type(),
                    store,
                    targetRef);
            double matchupDelta = effectivenessMultiplier - ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER;
            double elementDelta = elementDamage.damage() * matchupDelta;
            double adjustedComponentDamage = elementDamage.damage() * effectivenessMultiplier;
            adjustedElementDamage.put(
                    elementDamage.type(),
                    adjustedElementDamage.getOrDefault(elementDamage.type(), 0.0d) + adjustedComponentDamage);
            totalDelta += (float) elementDelta;
            if (elementDamage.socketWeight() > strongestWeight
                    || (elementDamage.socketWeight() == strongestWeight
                    && adjustedComponentDamage > strongestComponentDamage)) {
                strongestType = elementDamage.type();
                strongestWeight = elementDamage.socketWeight();
                strongestRate = elementDamage.rate();
                strongestMultiplier = effectivenessMultiplier;
                strongestDelta = elementDelta;
                strongestComponentDamage = adjustedComponentDamage;
            }
        }

        String elementDamagePayload = encodeElementDamage(adjustedElementDamage);
        if (strongestType == null || elementDamagePayload.isBlank()) {
            return AffinityDamage.none();
        }
        return new AffinityDamage(
                strongestType,
                strongestWeight,
                strongestRate,
                targetType,
                strongestMultiplier,
                totalDelta,
                elementDamagePayload);
    }

    private static void addWeight(Map<Essence.Type, Double> weights, Essence.Type type, double socketWeight) {
        if (weights == null || type == null || socketWeight <= 0.0d) {
            return;
        }
        weights.put(type, weights.getOrDefault(type, 0.0d) + socketWeight);
    }

    public static String encodeElementDamage(Map<Essence.Type, Double> elementDamage) {
        if (elementDamage == null || elementDamage.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Essence.Type type : Essence.Type.values()) {
            double damage = elementDamage.getOrDefault(type, 0.0d);
            if (damage <= 0.0001d) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(type.name()).append(':').append(Double.toString(damage));
        }
        return sb.toString();
    }

    public static Map<Essence.Type, Double> decodeElementDamage(String payload) {
        EnumMap<Essence.Type, Double> result = new EnumMap<>(Essence.Type.class);
        if (payload == null || payload.isBlank()) {
            return result;
        }
        for (String entry : payload.split(";")) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                Essence.Type type = Essence.Type.valueOf(parts[0].trim().toUpperCase(java.util.Locale.ROOT));
                double damage = Double.parseDouble(parts[1].trim());
                if (damage > 0.0001d) {
                    result.put(type, result.getOrDefault(type, 0.0d) + damage);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed payload entries from older or external damage sources.
            }
        }
        return result;
    }

    public static String mergeElementDamagePayload(String first, String second) {
        Map<Essence.Type, Double> merged = new EnumMap<>(Essence.Type.class);
        mergeElementDamage(merged, decodeElementDamage(first));
        mergeElementDamage(merged, decodeElementDamage(second));
        return encodeElementDamage(merged);
    }

    private static void mergeElementDamage(Map<Essence.Type, Double> target, Map<Essence.Type, Double> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<Essence.Type, Double> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0.0001d) {
                continue;
            }
            target.put(entry.getKey(), target.getOrDefault(entry.getKey(), 0.0d) + entry.getValue());
        }
    }

    public record ElementDamage(Essence.Type type, double socketWeight, double rate, double damage) {}

    public record AffinityDamage(
            Essence.Type type,
            double weight,
            double rate,
            Essence.Type targetType,
            double effectivenessMultiplier,
            float damageDelta,
            String elementDamagePayload) {
        public static AffinityDamage none() {
            return new AffinityDamage(null, 0.0d, 0.0d, null,
                    ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER, 0f, "");
        }
    }
}
