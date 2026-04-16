package irai.mod.reforge.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Config.CrossModConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.ReforgePlugin;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Optional EndlessLeveling bridge that feeds SocketReforge crit stats into
 * EndlessLeveling Precision / Ferocity through an archetype passive source.
 *
 * This class uses reflection only so SocketReforge stays standalone when
 * EndlessLeveling is not installed.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class EndlessLevelingCritCompat {
    private static final String API_CLASS_NAME = "com.airijko.endlessleveling.api.EndlessLevelingAPI";
    private static final String PLUGIN_CLASS_NAME = "com.airijko.endlessleveling.EndlessLeveling";
    private static final String PLAYER_DATA_CLASS_NAME = "com.airijko.endlessleveling.player.PlayerData";
    private static final String PASSIVE_SOURCE_CLASS_NAME = "com.airijko.endlessleveling.passives.archetype.ArchetypePassiveSource";
    private static final String PASSIVE_TYPE_CLASS_NAME = "com.airijko.endlessleveling.enums.ArchetypePassiveType";
    private static final String ATTRIBUTE_TYPE_CLASS_NAME = "com.airijko.endlessleveling.enums.SkillAttributeType";
    private static final String PASSIVE_CATEGORY_CLASS_NAME = "com.airijko.endlessleveling.enums.PassiveCategory";
    private static final String STACKING_STYLE_CLASS_NAME = "com.airijko.endlessleveling.enums.PassiveStackingStyle";
    private static final String PASSIVE_TIER_CLASS_NAME = "com.airijko.endlessleveling.enums.PassiveTier";
    private static final String PASSIVE_DEFINITION_CLASS_NAME = "com.airijko.endlessleveling.races.RacePassiveDefinition";

    private static final String SOURCE_NAME = "SocketReforgeEndlessCritPassiveSource";
    private static final String TAG_PRECISION = "socketreforge_precision";
    private static final String TAG_FEROCITY = "socketreforge_ferocity";
    private static final double EPSILON = 0.0001d;

    private static final Object REGISTRATION_LOCK = new Object();
    private static volatile boolean registered;
    private static volatile boolean reflectionReady;
    private static final Map<UUID, CritSyncState> LAST_SYNCED_STATE = new ConcurrentHashMap<>();

    private static Method apiGet;
    private static Method registerArchetypePassiveSource;
    private static Method unregisterArchetypePassiveSource;
    private static Method playerDataGetUuid;
    private static Method passiveDefinitionTag;
    private static Method pluginGetInstance;
    private static Method pluginGetArchetypePassiveManager;
    private static Method clearSnapshot;
    private static Constructor<?> passiveDefinitionConstructor;

    private static Class<?> passiveSourceInterface;
    private static Object passiveTypeInnateAttributeGain;
    private static Object attributePrecision;
    private static Object attributeFerocity;
    private static Object passiveCategoryPassiveStat;
    private static Object stackingStyleAdditive;
    private static Object passiveTierCommon;

    private static Object passiveSourceProxy;

    private EndlessLevelingCritCompat() {}

    private record CritSyncState(boolean enabled, double critChance, double critDamage) {}

    public static void tryRegister() {
        if (registered) {
            return;
        }
        synchronized (REGISTRATION_LOCK) {
            if (registered) {
                return;
            }
            try {
                ensureReflection();
                Object api = apiGet.invoke(null);
                if (api == null) {
                    return;
                }
                if (passiveSourceProxy == null) {
                    passiveSourceProxy = Proxy.newProxyInstance(
                            EndlessLevelingCritCompat.class.getClassLoader(),
                            new Class<?>[] {passiveSourceInterface},
                            (proxy, method, args) -> handleProxyCall(proxy, method, args));
                }
                unregisterExistingProxy(api);
                Object result = registerArchetypePassiveSource.invoke(api, passiveSourceProxy);
                registered = !(result instanceof Boolean value) || value;
                if (registered) {
                    System.out.println("[SocketReforge] EndlessLeveling crit sync enabled.");
                }
            } catch (ClassNotFoundException ignored) {
                // EndlessLeveling not installed - keep silent.
            } catch (Throwable t) {
                System.out.println("[SocketReforge] EndlessLeveling crit sync unavailable: " + t.getMessage());
            }
        }
    }

    private static Object handleProxyCall(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method == null ? "" : method.getName();
        return switch (name) {
            case "collect", "onCollect" -> {
                collect(args);
                yield null;
            }
            case "toString" -> SOURCE_NAME;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> null;
        };
    }

    public static void onPlayerTick(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = resolvePlayerUuid(player);
        if (uuid == null) {
            return;
        }

        boolean enabled = isEnabled();
        double critChance = enabled ? getEquippedCritBonus(player, EssenceEffect.StatType.CRIT_CHANCE) : 0.0d;
        double critDamage = enabled ? getEquippedCritBonus(player, EssenceEffect.StatType.CRIT_DAMAGE) : 0.0d;

        CritSyncState next = new CritSyncState(enabled, critChance, critDamage);
        CritSyncState previous = LAST_SYNCED_STATE.get(uuid);
        if (!hasStateChanged(previous, next)) {
            return;
        }

        LAST_SYNCED_STATE.put(uuid, next);
        clearPassiveSnapshot(uuid);
    }

    private static void collect(Object[] args) {
        if (!isEnabled() || args == null || args.length < 3) {
            return;
        }
        Object playerData = args[0];
        Object rawDefinitions = args[2];
        if (playerData == null || !(rawDefinitions instanceof Map definitions)) {
            return;
        }

        try {
            UUID uuid = (UUID) playerDataGetUuid.invoke(playerData);
            Player player = resolvePlayer(uuid);
            if (player == null) {
                return;
            }

            double critChance = getEquippedCritBonus(player, EssenceEffect.StatType.CRIT_CHANCE);
            double critDamage = getEquippedCritBonus(player, EssenceEffect.StatType.CRIT_DAMAGE);

            removeTaggedDefinition(definitions, TAG_PRECISION);
            removeTaggedDefinition(definitions, TAG_FEROCITY);

            if (critChance > 0.0d) {
                addDefinition(definitions, attributePrecision, critChance, TAG_PRECISION);
            }
            if (critDamage > 0.0d) {
                addDefinition(definitions, attributeFerocity, critDamage, TAG_FEROCITY);
            }
        } catch (Throwable ignored) {
            // Keep the compat path fail-soft.
        }
    }

    private static boolean hasStateChanged(CritSyncState previous, CritSyncState next) {
        if (previous == null) {
            return true;
        }
        if (previous.enabled != next.enabled) {
            return true;
        }
        return Math.abs(previous.critChance - next.critChance) > EPSILON
                || Math.abs(previous.critDamage - next.critDamage) > EPSILON;
    }

    private static boolean isEnabled() {
        ReforgePlugin plugin = ReforgePlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        CrossModConfig config = plugin.getCrossModRuntimeConfig();
        return config == null || config.isEndlessLevelingCritSyncEnabled();
    }

    private static UUID resolvePlayerUuid(Player player) {
        if (player == null) {
            return null;
        }
        try {
            PlayerRef ref = player.getPlayerRef();
            if (ref != null && ref.isValid()) {
                UUID uuid = ref.getUuid();
                if (uuid != null) {
                    return uuid;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to deprecated UUID accessor below.
        }
        try {
            return player.getUuid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearPassiveSnapshot(UUID uuid) {
        if (uuid == null) {
            return;
        }
        try {
            ensureReflection();
            Object plugin = pluginGetInstance.invoke(null);
            if (plugin == null) {
                return;
            }
            Object passiveManager = pluginGetArchetypePassiveManager.invoke(plugin);
            if (passiveManager == null) {
                return;
            }
            clearSnapshot.invoke(passiveManager, uuid);
        } catch (ClassNotFoundException ignored) {
            // EndlessLeveling not installed.
        } catch (Throwable ignored) {
            // Keep the compat path fail-soft.
        }
    }

    private static Player resolvePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Universe universe = Universe.get();
        if (universe == null) {
            return null;
        }
        PlayerRef playerRef = universe.getPlayer(uuid);
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }
        return playerRef.getComponent(Player.getComponentType());
    }

    private static double getEquippedCritBonus(Player player, EssenceEffect.StatType statType) {
        if (player == null || statType == null) {
            return 0.0d;
        }
        double total = 0.0d;

        PlayerInventoryUtils.HeldItemContext held = PlayerInventoryUtils.getHeldItemContext(player);
        if (held != null && held.isValid()) {
            total += getItemCritBonus(held.getItemStack(), statType);
        }

        List<ItemStack> armorPieces = PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
        if (armorPieces != null) {
            for (ItemStack armor : armorPieces) {
                total += getItemCritBonus(armor, statType);
            }
        }
        return Math.max(0.0d, total);
    }

    private static double getItemCritBonus(ItemStack item, EssenceEffect.StatType statType) {
        if (item == null || item.isEmpty() || statType == null) {
            return 0.0d;
        }
        double[] values = SocketManager.getStoredStatBonus(item, statType);
        if (values == null || values.length < 2) {
            return 0.0d;
        }
        return Math.max(0.0d, values[1]);
    }

    private static void unregisterExistingProxy(Object api) {
        if (api == null || passiveSourceProxy == null || unregisterArchetypePassiveSource == null) {
            return;
        }
        try {
            while (Boolean.TRUE.equals(unregisterArchetypePassiveSource.invoke(api, passiveSourceProxy))) {
                // Remove any lingering duplicate registrations for the same proxy before re-registering.
            }
        } catch (Throwable ignored) {
            // Keep the compat path fail-soft.
        }
    }

    private static void addDefinition(Map definitionsByType, Object attributeType, double value, String tag) throws Exception {
        if (attributeType == null || value <= 0.0d) {
            return;
        }
        Object definitionsKey = passiveTypeInnateAttributeGain;
        Object rawList = definitionsByType.get(definitionsKey);
        List list;
        if (rawList instanceof List existing) {
            list = existing;
        } else {
            list = new ArrayList();
            definitionsByType.put(definitionsKey, list);
        }

        Object definition = passiveDefinitionConstructor.newInstance(
                passiveTypeInnateAttributeGain,
                value,
                Collections.emptyMap(),
                attributeType,
                null,
                tag,
                passiveCategoryPassiveStat,
                stackingStyleAdditive,
                passiveTierCommon,
                Collections.emptyMap());
        list.add(definition);
    }

    private static void removeTaggedDefinition(Map definitionsByType, String tag) throws Exception {
        if (definitionsByType == null || tag == null || tag.isBlank()) {
            return;
        }
        Object rawList = definitionsByType.get(passiveTypeInnateAttributeGain);
        if (!(rawList instanceof List list) || list.isEmpty()) {
            return;
        }
        list.removeIf(entry -> hasTag(entry, tag));
    }

    private static boolean hasTag(Object definition, String tag) {
        if (definition == null || tag == null || passiveDefinitionTag == null) {
            return false;
        }
        try {
            Object value = passiveDefinitionTag.invoke(definition);
            return tag.equals(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureReflection() throws Exception {
        if (reflectionReady) {
            return;
        }

        Class<?> apiClass = Class.forName(API_CLASS_NAME);
        Class<?> pluginClass = Class.forName(PLUGIN_CLASS_NAME);
        Class<?> playerDataClass = Class.forName(PLAYER_DATA_CLASS_NAME);
        passiveSourceInterface = Class.forName(PASSIVE_SOURCE_CLASS_NAME);
        Class<? extends Enum> passiveTypeClass = (Class<? extends Enum>) Class.forName(PASSIVE_TYPE_CLASS_NAME);
        Class<? extends Enum> attributeTypeClass = (Class<? extends Enum>) Class.forName(ATTRIBUTE_TYPE_CLASS_NAME);
        Class<? extends Enum> passiveCategoryClass = (Class<? extends Enum>) Class.forName(PASSIVE_CATEGORY_CLASS_NAME);
        Class<? extends Enum> stackingStyleClass = (Class<? extends Enum>) Class.forName(STACKING_STYLE_CLASS_NAME);
        Class<? extends Enum> passiveTierClass = (Class<? extends Enum>) Class.forName(PASSIVE_TIER_CLASS_NAME);
        Class<?> passiveDefinitionClass = Class.forName(PASSIVE_DEFINITION_CLASS_NAME);

        apiGet = apiClass.getMethod("get");
        registerArchetypePassiveSource = apiClass.getMethod("registerArchetypePassiveSource", passiveSourceInterface);
        unregisterArchetypePassiveSource = apiClass.getMethod("unregisterArchetypePassiveSource", passiveSourceInterface);
        playerDataGetUuid = playerDataClass.getMethod("getUuid");
        passiveDefinitionTag = passiveDefinitionClass.getMethod("tag");
        pluginGetInstance = pluginClass.getMethod("getInstance");
        pluginGetArchetypePassiveManager = pluginClass.getMethod("getArchetypePassiveManager");

        passiveDefinitionConstructor = passiveDefinitionClass.getConstructor(
                passiveTypeClass,
                double.class,
                Map.class,
                attributeTypeClass,
                Class.forName("com.airijko.endlessleveling.enums.DamageLayer"),
                String.class,
                passiveCategoryClass,
                stackingStyleClass,
                passiveTierClass,
                Map.class);
        clearSnapshot = pluginGetArchetypePassiveManager.getReturnType().getMethod("clearSnapshot", UUID.class);

        passiveTypeInnateAttributeGain = Enum.valueOf(passiveTypeClass, "INNATE_ATTRIBUTE_GAIN");
        attributePrecision = Enum.valueOf(attributeTypeClass, "PRECISION");
        attributeFerocity = Enum.valueOf(attributeTypeClass, "FEROCITY");
        passiveCategoryPassiveStat = Enum.valueOf(passiveCategoryClass, "PASSIVE_STAT");
        stackingStyleAdditive = Enum.valueOf(stackingStyleClass, "ADDITIVE");
        passiveTierCommon = Enum.valueOf(passiveTierClass, "COMMON");

        reflectionReady = true;
    }
}
