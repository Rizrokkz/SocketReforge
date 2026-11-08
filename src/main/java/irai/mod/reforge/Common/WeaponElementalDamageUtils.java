package irai.mod.reforge.Common;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hypixel.hytale.builtin.abilities.element.Element;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumberMeta;
import irai.mod.reforge.Entity.Events.DamageStackDelayEST;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Entity.Events.NativeAffinityDamageMergeEST;
import irai.mod.reforge.Util.DamageNumberFormatter;

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

        Map<Essence.Type, Double> weights = calculateAffinityWeights(socketData);

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

        Map<Essence.Type, Double> weights = calculateAffinityWeights(socketData);
        if (weights.isEmpty()) {
            return AffinityDamage.none();
        }

        Essence.Type targetType = ElementalAffinityUtils.resolveTargetAffinity(store, targetRef);
        Essence.Type strongestType = null;
        double strongestWeight = 0.0d;
        double strongestRate = 0.0d;
        double strongestMultiplier = ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER;
        // Keep the first affinity that reaches the highest weight, preserving socket order for ties.
        for (Map.Entry<Essence.Type, Double> entry : weights.entrySet()) {
            if (entry.getValue() > strongestWeight) {
                strongestType = entry.getKey();
                strongestWeight = entry.getValue();
            }
        }
        if (strongestType == null) {
            return AffinityDamage.none();
        }
        strongestRate = Math.min(1.0d, strongestWeight * DAMAGE_RATE_PER_SOCKET_WEIGHT);
        strongestMultiplier = ElementalAffinityUtils.effectivenessMultiplier(strongestType, store, targetRef);
        double bonusDamage = damageOutput * strongestRate * strongestMultiplier;
        if (!(bonusDamage > 0.0001d)) {
            return AffinityDamage.none();
        }
        String elementDamagePayload = encodeElementDamage(Map.of(strongestType, bonusDamage));
        return new AffinityDamage(
                strongestType,
                strongestWeight,
                strongestRate,
                targetType,
                strongestMultiplier,
                (float) bonusDamage,
                elementDamagePayload);
    }

    /** Applies a passive character-path multiplier while preserving native element metadata. */
    public static AffinityDamage scaleAffinityDamage(AffinityDamage affinityDamage, double multiplier) {
        if (affinityDamage == null || affinityDamage.type() == null
                || affinityDamage.elementalDamage() <= 0.0f || !Double.isFinite(multiplier)
                || multiplier <= 0.0d) {
            return affinityDamage == null ? AffinityDamage.none() : affinityDamage;
        }
        float scaledDamage = (float) (affinityDamage.elementalDamage() * multiplier);
        String payload = encodeElementDamage(Map.of(affinityDamage.type(), (double) scaledDamage));
        return new AffinityDamage(affinityDamage.type(), affinityDamage.weight(), affinityDamage.rate(),
                affinityDamage.targetType(), affinityDamage.effectivenessMultiplier(), scaledDamage, payload);
    }

    private static Map<Essence.Type, Double> calculateAffinityWeights(SocketData socketData) {
        Map<Essence.Type, Double> weights = new LinkedHashMap<>();
        if (socketData == null || socketData.getSockets() == null) {
            return weights;
        }
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isEmpty() || socket.isBroken()) {
                continue;
            }
            Essence.Type affinity = SocketManager.parseEssenceType(socket.getMutationElement());
            if (affinity == null) {
                Essence rawEssence = EssenceRegistry.get().getById(socket.getEssenceId());
                affinity = rawEssence == null ? null : rawEssence.getType();
            }
            if (affinity == null) {
                continue;
            }
            double socketWeight = SocketManager.isGreaterEssenceId(socket.getEssenceId())
                    ? GREATER_WEIGHT : 1.0d;
            addWeight(weights, affinity, socketWeight);
        }
        return weights;
    }

    /** Returns the aggregate socket weight for one effective weapon affinity. */
    public static double getAffinityWeight(SocketData socketData, Essence.Type type) {
        if (type == null) return 0.0d;
        return calculateAffinityWeights(socketData).getOrDefault(type, 0.0d);
    }

    /**
     * Calculates only the matching essence's bonus for a status damage instance.
     * This uses the same per-socket rate and target effectiveness as weapon affinity damage.
     */
    public static float calculateAffinityBonusForType(ItemStack weapon,
                                                      Essence.Type type,
                                                      double baseDamage,
                                                      Store<EntityStore> store,
                                                      Ref<EntityStore> targetRef) {
        if (weapon == null || weapon.isEmpty() || type == null || baseDamage <= 0.0d) return 0.0f;
        SocketData socketData = SocketManager.getSocketData(weapon);
        double weight = getAffinityWeight(socketData, type);
        if (weight <= 0.0d) return 0.0f;
        double rate = Math.min(1.0d, weight * DAMAGE_RATE_PER_SOCKET_WEIGHT);
        double effectiveness = ElementalAffinityUtils.effectivenessMultiplier(type, store, targetRef);
        return (float) (baseDamage * rate * effectiveness);
    }

    /**
     * Dispatches the affinity bonus as a separately typed native damage event. This keeps the
     * weapon's physical hit intact while allowing native DamageCause resistance, element reactions,
     * and element on-hit effects to process the bonus.
     */
    public static boolean applyNativeAffinityDamage(CommandBuffer<EntityStore> commandBuffer,
                                                    Ref<EntityStore> targetRef,
                                                    Damage originalDamage,
                                                    AffinityDamage affinityDamage) {
        if (commandBuffer == null || targetRef == null || originalDamage == null
                || affinityDamage == null || affinityDamage.type() == null
                || affinityDamage.elementalDamage() <= 0.0001f) {
            return false;
        }

        DamageCauseResolution resolution = resolveNativeDamageCause(affinityDamage.type());
        if (resolution.cause() == null) {
            return false;
        }

        Damage elementalDamage = new Damage(
                originalDamage.getSource(), resolution.cause(), affinityDamage.elementalDamage());
        long affinityGroupId = NativeAffinityDamageMergeEST.registerElementalComponent(originalDamage);
        elementalDamage.putMetaObject(NativeAffinityDamageMergeEST.META_AFFINITY_GROUP_ID, affinityGroupId);
        Integer expectedComponents = originalDamage.getIfPresentMetaObject(
                NativeAffinityDamageMergeEST.META_EXPECTED_ELEMENTAL_COMPONENTS);
        elementalDamage.putMetaObject(NativeAffinityDamageMergeEST.META_EXPECTED_ELEMENTAL_COMPONENTS,
                expectedComponents == null ? 1 : expectedComponents);
        elementalDamage.putMetaObject(EquipmentRefineEST.META_SOCKET_ELEMENTAL_COMPONENT, Boolean.TRUE);
        elementalDamage.putMetaObject(EquipmentRefineEST.META_SKIP_REFORGE, Boolean.TRUE);
        elementalDamage.putMetaObject(EquipmentRefineEST.META_SOCKET_ELEMENTAL_AFFINITY_HANDLED, Boolean.TRUE);
        elementalDamage.putMetaObject(EquipmentRefineEST.META_WEAPON_ELEMENTAL_DAMAGE,
                affinityDamage.elementDamagePayload());
        elementalDamage.putMetaObject(DamageStackDelayEST.META_RAW_AMOUNT, affinityDamage.elementalDamage());
        elementalDamage.putMetaObject(DamageStackDelayEST.META_BONUS_BASE_AMOUNT, affinityDamage.elementalDamage());
        markDamageKind(elementalDamage, affinityDamage.type());

        if (resolution.elementId() != null) {
            String[] onHitEffects = resolution.onHitEffectId() == null
                    || resolution.onHitEffectId().isBlank()
                    ? new String[0]
                    : new String[]{resolution.onHitEffectId()};
            elementalDamage.putMetaObject(ImpactModifiers.DAMAGE_MODIFIERS,
                    new ImpactModifiers(Map.of(), onHitEffects, resolution.elementId()));
        }

        commandBuffer.invoke(targetRef, elementalDamage);
        return true;
    }

    private static DamageCauseResolution resolveNativeDamageCause(Essence.Type type) {
        String elementId = nativeElementId(type);
        Element element = elementId == null || Element.getAssetMap() == null
                ? null : Element.getAssetMap().getAsset(elementId);
        if (element != null) {
            DamageCause nativeCause = DamageCause.getAssetMap().getAsset(element.getDamageCauseId());
            if (nativeCause != null) {
                return new DamageCauseResolution(nativeCause, element.getId(), element.getOnHitEffectId());
            }
        }

        String customCauseId = customDamageCauseId(type);
        DamageCause customCause = customCauseId == null || DamageCause.getAssetMap() == null
                ? null : DamageCause.getAssetMap().getAsset(customCauseId);
        if (customCause != null) {
            return new DamageCauseResolution(customCause, null, null);
        }

        // Custom Socket Reforge elements without a native Element asset still use the native
        // generic Elemental cause; their exact identity remains in the damage payload.
        DamageCause elementalCause = DamageCause.getAssetMap() == null
                ? null : DamageCause.getAssetMap().getAsset("Elemental");
        if (elementalCause != null) {
            return new DamageCauseResolution(elementalCause, null, null);
        }
        return new DamageCauseResolution(DamageCause.PHYSICAL, null, null);
    }

    private static String nativeElementId(Essence.Type type) {
        return switch (type) {
            case FIRE -> "Fire";
            case WATER -> "Water";
            case LIGHTNING -> "Lightning";
            // Socket Reforge supplies native-format Element assets for affinities that the base
            // game does not define. Keep them namespaced to avoid collisions with other mods.
            case ICE -> "SocketReforge_Ice";
            case LIFE -> "SocketReforge_Life";
            case VOID -> "SocketReforge_Void";
        };
    }

    private static String customDamageCauseId(Essence.Type type) {
        if (type == null) {
            return null;
        }
        String name = type.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void markDamageKind(Damage damage, Essence.Type type) {
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
        Map<Essence.Type, Double> result = new LinkedHashMap<>();
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
            float elementalDamage,
            String elementDamagePayload) {
        public static AffinityDamage none() {
            return new AffinityDamage(null, 0.0d, 0.0d, null,
                    ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER, 0f, "");
        }
    }

    private record DamageCauseResolution(DamageCause cause, String elementId, String onHitEffectId) {}
}
