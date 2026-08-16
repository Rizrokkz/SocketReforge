package irai.mod.reforge.Common;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Vector3f;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.math.range.FloatRange;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAppearanceCondition;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;

import irai.mod.reforge.Socket.Essence;

public final class WeaponAffinityAppearanceInjector {
    public static final String PRIMARY_STAT_ID = "SRAffinityWeaponVisual_Primary";
    public static final String SECONDARY_STAT_ID = "SRAffinityWeaponVisual_Secondary";
    public static final String AFFINITY_SPIN_ANIMATION = "Items/Weapons/Affinity_Spin.blockyanim";
    private static final String[] ORBIT_NODE_NAMES = {
            "Attachment_Spin1",
            "Attachment_Spin2",
            "Attachment_Spin3",
            "Attachment_Spin4"
    };
    private static final String[] UNSAFE_INTERACTION_TOKENS = {
            "***",
            "staff_cast",
            "magical_cast"
    };
    private static final float PARTICLE_SCALE = 0.2f;

    private static final Field ITEM_APPEARANCE_CONDITIONS_FIELD =
            findField(Item.class, "itemAppearanceConditions");
    private static final Field ANIMATION_FIELD =
            findField(Item.class, "animation");
    private static final Field CONDITION_FIELD =
            findField(ItemAppearanceCondition.class, "condition");
    private static final Field CONDITION_VALUE_TYPE_FIELD =
            findField(ItemAppearanceCondition.class, "conditionValueType");
    private static final Field PARTICLES_FIELD =
            findField(ItemAppearanceCondition.class, "particles");
    private static final Field FIRST_PERSON_PARTICLES_FIELD =
            findField(ItemAppearanceCondition.class, "firstPersonParticles");
    private static final Field RENDER_DUAL_WIELDED_FIELD =
            findField(ItemWeapon.class, "renderDualWielded");

    private WeaponAffinityAppearanceInjector() {}

    public static PatchResult applyToLoadedItemAssetMap(java.util.Set<String> spinReadyModelPaths) {
        PatchResult result = new PatchResult();
        DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
        if (assetMap == null || assetMap.getAssetMap() == null || !reflectionReady()) {
            return result;
        }
        for (Map.Entry<String, Item> entry : assetMap.getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (!looksLikeWeapon(entry.getKey(), item)) {
                continue;
            }
            if (hasUnsafeInteractionReplacementHooks(entry.getKey(), item)) {
                result.unverifiedModelIds.add(entry.getKey() + " (unsafe interaction vars)");
                continue;
            }
            if (!isSpinReady(item, spinReadyModelPaths)) {
                result.unverifiedModelIds.add(entry.getKey());
                continue;
            }
            try {
                injectAffinityConditions(item);
                ensureAffinityAnimation(item);
                if (looksLikeDualWeapon(entry.getKey())) {
                    enableDualRenderedWeapon(item);
                }
                item.invalidatePacketCache();
                result.patchedIds.add(entry.getKey());
            } catch (Throwable t) {
                result.failedIds.add(entry.getKey());
                System.err.println("[SocketReforge] Weapon affinity startup injection failed for "
                        + entry.getKey() + ": " + describeFailure(t));
            }
        }
        return result;
    }

    public static boolean isEligibleWeapon(String itemId, Item item) {
        return looksLikeWeapon(itemId, item) && !hasUnsafeInteractionReplacementHooks(itemId, item);
    }

    public static boolean hasUnsafeInteractionReplacementHooks(String itemId, Item item) {
        if (item == null) {
            return false;
        }
        if (containsUnsafeInteractionToken(itemId)) {
            return true;
        }
        Map<?, ?> interactions = item.getInteractions();
        if (mapContainsUnsafeInteractionToken(interactions)) {
            return true;
        }
        Map<?, ?> vars = item.getInteractionVars();
        return mapContainsUnsafeInteractionToken(vars);
    }

    public static int statValueForAffinity(Essence.Type affinity) {
        if (affinity == null) {
            return 0;
        }
        return switch (affinity) {
            case FIRE -> 1;
            case ICE -> 2;
            case LIFE -> 3;
            case LIGHTNING -> 4;
            case VOID -> 5;
            case WATER -> 6;
        };
    }

    private static boolean reflectionReady() {
        return ITEM_APPEARANCE_CONDITIONS_FIELD != null
                && CONDITION_FIELD != null
                && CONDITION_VALUE_TYPE_FIELD != null
                && PARTICLES_FIELD != null
                && FIRST_PERSON_PARTICLES_FIELD != null;
    }

    private static void injectAffinityConditions(Item item) throws IllegalAccessException {
        for (Essence.Type affinity : Essence.Type.values()) {
            int value = statValueForAffinity(affinity);
            appendCondition(item, PRIMARY_STAT_ID, createCondition(affinity, value, EntityPart.PrimaryItem));
            appendCondition(item, SECONDARY_STAT_ID, createCondition(affinity, value, EntityPart.SecondaryItem));
        }
    }

    private static boolean isSpinReady(Item item, java.util.Set<String> spinReadyModelPaths) {
        if (spinReadyModelPaths == null || spinReadyModelPaths.isEmpty()) {
            return false;
        }
        String model = item == null ? null : item.getModel();
        if (model == null || model.isBlank()) {
            return false;
        }
        return spinReadyModelPaths.contains(model.replace('\\', '/'));
    }

    private static void ensureAffinityAnimation(Item item) throws IllegalAccessException {
        if (ANIMATION_FIELD == null || item == null) {
            return;
        }
        Object current = ANIMATION_FIELD.get(item);
        if (current == null || current.toString().isBlank()) {
            ANIMATION_FIELD.set(item, AFFINITY_SPIN_ANIMATION);
        }
    }

    private static void appendCondition(Item item,
                                        String statId,
                                        ItemAppearanceCondition condition) throws IllegalAccessException {
        Object rawConditions = ITEM_APPEARANCE_CONDITIONS_FIELD.get(item);
        Map<String, ItemAppearanceCondition[]> conditions = new LinkedHashMap<>();
        if (rawConditions instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key
                        && entry.getValue() instanceof ItemAppearanceCondition[] value) {
                    conditions.put(key, compactConditions(value));
                }
            }
        }

        ItemAppearanceCondition[] existing = compactConditions(conditions.get(statId));
        normalizeAffinityConditions(existing);
        if (containsCondition(existing, condition)) {
            ITEM_APPEARANCE_CONDITIONS_FIELD.set(item, conditions);
            return;
        }

        ItemAppearanceCondition[] updated;
        if (existing == null || existing.length == 0) {
            updated = new ItemAppearanceCondition[] {condition};
        } else {
            updated = Arrays.copyOf(existing, existing.length + 1);
            updated[existing.length] = condition;
        }
        conditions.put(statId, updated);
        ITEM_APPEARANCE_CONDITIONS_FIELD.set(item, conditions);
    }

    private static boolean containsCondition(ItemAppearanceCondition[] existing,
                                             ItemAppearanceCondition condition) throws IllegalAccessException {
        if (existing == null || existing.length == 0) {
            return false;
        }
        FloatRange targetRange = (FloatRange) CONDITION_FIELD.get(condition);
        for (ItemAppearanceCondition current : existing) {
            if (current == null) {
                continue;
            }
            FloatRange currentRange = (FloatRange) CONDITION_FIELD.get(current);
            if (currentRange != null
                    && targetRange != null
                    && currentRange.getInclusiveMin() == targetRange.getInclusiveMin()
                    && currentRange.getInclusiveMax() == targetRange.getInclusiveMax()) {
                return true;
            }
        }
        return false;
    }

    private static ItemAppearanceCondition[] compactConditions(ItemAppearanceCondition[] conditions) {
        if (conditions == null || conditions.length == 0) {
            return new ItemAppearanceCondition[0];
        }
        return Arrays.stream(conditions)
                .filter(condition -> condition != null)
                .toArray(ItemAppearanceCondition[]::new);
    }

    private static void normalizeAffinityConditions(ItemAppearanceCondition[] conditions) {
        if (conditions == null || conditions.length == 0 || CONDITION_VALUE_TYPE_FIELD == null || PARTICLES_FIELD == null) {
            return;
        }
        for (ItemAppearanceCondition condition : conditions) {
            if (!isAffinityCondition(condition)) {
                continue;
            }
            try {
                CONDITION_VALUE_TYPE_FIELD.set(condition, ValueType.Absolute);
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean isAffinityCondition(ItemAppearanceCondition condition) {
        if (condition == null) {
            return false;
        }
        try {
            Object rawParticles = PARTICLES_FIELD.get(condition);
            if (!(rawParticles instanceof ModelParticle[] particles) || particles.length == 0 || particles[0] == null) {
                return false;
            }
            String systemId = particles[0].getSystemId();
            return systemId != null && systemId.startsWith("Weapon_Affinity_");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String describeFailure(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    public static final class PatchResult {
        private final List<String> patchedIds = new ArrayList<>();
        private final List<String> failedIds = new ArrayList<>();
        private final List<String> unverifiedModelIds = new ArrayList<>();

        public int patchedCount() {
            return patchedIds.size();
        }

        public List<String> patchedIds() {
            return List.copyOf(patchedIds);
        }

        public List<String> failedIds() {
            return List.copyOf(failedIds);
        }

        public List<String> unverifiedModelIds() {
            return List.copyOf(unverifiedModelIds);
        }
    }

    private static ItemAppearanceCondition createCondition(Essence.Type affinity,
                                                           int statValue,
                                                           EntityPart targetPart) {
        try {
            ItemAppearanceCondition condition = new ItemAppearanceCondition();
            CONDITION_FIELD.set(condition, new FloatRange(statValue, statValue));
            CONDITION_VALUE_TYPE_FIELD.set(condition, ValueType.Absolute);
            ModelParticle[] particles = createOrbitParticles(affinity, targetPart);
            PARTICLES_FIELD.set(condition, particles);
            FIRST_PERSON_PARTICLES_FIELD.set(condition, particles);
            return condition;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static ModelParticle[] createOrbitParticles(Essence.Type affinity, EntityPart targetPart) {
        String systemId = particleSystemId(affinity);
        ModelParticle[] particles = new ModelParticle[ORBIT_NODE_NAMES.length];
        for (int i = 0; i < ORBIT_NODE_NAMES.length; i++) {
            particles[i] = new ModelParticle(
                    systemId,
                    targetPart,
                    ORBIT_NODE_NAMES[i],
                    null,
                    PARTICLE_SCALE,
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    null,
                    false);
        }
        return particles;
    }

    private static String particleSystemId(Essence.Type affinity) {
        return switch (affinity) {
            case FIRE -> "Weapon_Affinity_Fire";
            case ICE -> "Weapon_Affinity_Ice";
            case LIFE -> "Weapon_Affinity_Life";
            case LIGHTNING -> "Weapon_Affinity_Lightning";
            case VOID -> "Weapon_Affinity_Void";
            case WATER -> "Weapon_Affinity_Water";
        };
    }

    private static boolean looksLikeWeapon(String itemId, Item item) {
        if (item == null || item.getWeapon() == null || item.getArmor() != null) {
            return false;
        }
        String normalizedId = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        if (normalizedId.startsWith("template_")
            || normalizedId.startsWith("test_")
            || normalizedId.startsWith("debug_")
            || normalizedId.startsWith("weapon_arrow")
            || normalizedId.contains("spawn_marker")
            || normalizedId.contains("camera")
            || normalizedId.contains("_projectile")
            || normalizedId.contains("_bomb")
            || normalizedId.contains("staff")
            || normalizedId.contains("wand")
            || normalizedId.contains("spellbook")
            || normalizedId.contains("shield")
                || normalizedId.contains("buckler")) {
            return false;
        }

        String model = item.getModel();
        if (model == null || model.isBlank()) {
            return false;
        }
        String normalizedModel = model.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalizedModel.contains("/staff/")
                || normalizedModel.contains("/wand/")
                || normalizedModel.contains("/spellbook/")
                || normalizedModel.contains("staff")
                || normalizedModel.contains("wand")
                || normalizedModel.contains("spellbook")) {
            return false;
        }
        return normalizedModel.endsWith(".blockymodel")
                && (normalizedModel.startsWith("items/weapons/")
                || normalizedModel.startsWith("npc/"));
    }

    private static boolean looksLikeDualWeapon(String itemId) {
        if (itemId == null) {
            return false;
        }
        String normalizedId = itemId.toLowerCase(Locale.ROOT);
        return normalizedId.contains("dagger")
                || normalizedId.contains("daggers")
                || normalizedId.contains("dual")
                || normalizedId.contains("twin");
    }

    private static boolean mapContainsUnsafeInteractionToken(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (containsUnsafeInteractionToken(String.valueOf(entry.getKey()))
                    || containsUnsafeInteractionToken(String.valueOf(entry.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsafeInteractionToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : UNSAFE_INTERACTION_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static void enableDualRenderedWeapon(Item item) {
        if (item == null || RENDER_DUAL_WIELDED_FIELD == null || item.getWeapon() == null) {
            return;
        }
        try {
            RENDER_DUAL_WIELDED_FIELD.setBoolean(item.getWeapon(), true);
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            System.err.println("[SocketReforge] Weapon affinity appearance field unavailable: "
                    + type.getSimpleName() + "." + name + " (" + t.getMessage() + ")");
            return null;
        }
    }
}
