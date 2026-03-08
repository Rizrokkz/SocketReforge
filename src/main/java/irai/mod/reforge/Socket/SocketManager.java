package irai.mod.reforge.Socket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Interactions.ReforgeEquip;

/**
 * Core logic for socket punching and essence operations.
 * Stateless — all item state lives in SocketData.
 */
public class SocketManager {

    public enum PunchResult { SUCCESS, FAIL, BREAK }
    public enum RemoveResult { SUCCESS, FAIL_TOOL_DESTROYED }

    private static SocketConfig config = new SocketConfig();
    private static final Random RNG    = new Random();
    private static final String META_SOCKETS_MAX = "SocketReforge.Socket.Max";
    private static final String META_SOCKETS_VALUES = "SocketReforge.Socket.Values";
    private static final String META_ESSENCE_EFFECTS = "SocketReforge.Essence.Effects";
    private static final String META_ESSENCE_TIER_MAP = "SocketReforge.Essence.TierMap";
    private static final String META_ESSENCE_EFFECT_LINES = "SocketReforge.Essence.EffectLines";
    private static final String META_ESSENCE_BONUS_STATS = "SocketReforge.Essence.Bonus.Stats";
    private static final String META_ESSENCE_BONUS_FLAT = "SocketReforge.Essence.Bonus.Flat";
    private static final String META_ESSENCE_BONUS_PERCENT = "SocketReforge.Essence.Bonus.Percent";
    private static final String GREATER_ESSENCE_SUFFIX = "_Concentrated";

    // ── Config ────────────────────────────────────────────────────────────────

    public static void setConfig(SocketConfig cfg) { config = cfg; }
    public static SocketConfig getConfig()         { return config; }

    /** Initialize the socket system with config. Called from ReforgePlugin. */
    public static void initialize(SocketConfig cfg) {
        config = cfg;
    }

    // ── Supporting material modifiers ────────────────────────────────────────

    public enum SupportMaterial {
        NONE,
        SOCKET_STABILIZER,   // Reduces lock chance
        SOCKET_REINFORCER,   // +20% success chance
        SOCKET_GUARANTOR,    // 100% success for 1st socket
        SOCKET_EXPANDER      // 25% chance for bonus socket
    }

    // ── Socket data from item ─────────────────────────────────────────────────

    /**
     * Gets socket data from item metadata.
     * Returns null if item is not socket-compatible.
     */
    public static SocketData getSocketData(ItemStack item) {
        if (item == null || item.isEmpty()) return null;

        boolean isWeapon = ReforgeEquip.isWeapon(item);
        boolean isArmor  = !isWeapon && ReforgeEquip.isArmor(item);
        if (!isWeapon && !isArmor) return null;

        Integer maxFromMeta = item.getFromMetadataOrNull(META_SOCKETS_MAX, Codec.INTEGER);
        String[] socketsFromMeta = item.getFromMetadataOrNull(META_SOCKETS_VALUES, Codec.STRING_ARRAY);

        int defaultMax = isWeapon
                ? config.getMaxSocketsWeapon()
                : config.getMaxSocketsArmor();
        int maxSockets = maxFromMeta != null && maxFromMeta >= 0 ? maxFromMeta : Math.max(0, defaultMax);

        SocketData socketData = new SocketData(maxSockets);
        if (socketsFromMeta == null || socketsFromMeta.length == 0) {
            return socketData;
        }

        int count = Math.min(socketsFromMeta.length, socketData.getMaxSockets());
        for (int i = 0; i < count; i++) {
            socketData.addSocket();
            String essenceId = socketsFromMeta[i];
            if (essenceId != null && !essenceId.isBlank()) {
                if (essenceId.equals("x")) {
                    // Broken socket - mark as broken
                    socketData.getSockets().get(i).setBroken(true);
                } else {
                    socketData.setEssenceAt(i, essenceId);
                }
            }
        }

        return socketData;
    }

    /**
     * Writes socket data into item metadata.
     * Also stores the essence tier effects in metadata for tooltip access.
     * The returned ItemStack must be put back into an inventory/container slot.
     */
    public static ItemStack withSocketData(ItemStack item, SocketData socketData) {
        if (item == null || item.isEmpty() || socketData == null) return item;

        String[] encoded = encodeSockets(socketData);
        boolean isWeapon = ReforgeEquip.isWeapon(item);
        ResonanceSystem.ResonanceResult resonance = ResonanceSystem.evaluate(item, socketData);
        String resonanceTooltipEffect = resonance.active()
                ? ResonanceSystem.buildDetailedEffect(resonance, isWeapon)
                : "";
        
        // Calculate tier map and effects for metadata storage
        Map<Essence.Type, Integer> tierMap = calculateConsecutiveTiers(socketData);
        String[] effectTypes = new String[tierMap.size()];
        String[] effectTiers = new String[tierMap.size()];
        String[] effectLines = new String[tierMap.size()];
        
        int idx = 0;
        for (Map.Entry<Essence.Type, Integer> entry : tierMap.entrySet()) {
            Essence.Type type = entry.getKey();
            int tier = SocketEffectMath.clampTier(entry.getValue());
            effectTypes[idx] = type.name();
            effectTiers[idx] = String.valueOf(tier);
            effectLines[idx] = describeEssenceEffect(type, tier, isWeapon, socketData);
            idx++;
        }

        // Store deterministic stat bonuses in metadata so runtime systems do not recalculate random values.
        Map<EssenceEffect.StatType, double[]> statBonuses = calculateDeterministicBonuses(item, socketData, isWeapon);
        List<String> statKeys = new ArrayList<>();
        List<String> flatValues = new ArrayList<>();
        List<String> percentValues = new ArrayList<>();
        for (Map.Entry<EssenceEffect.StatType, double[]> entry : statBonuses.entrySet()) {
            double flat = entry.getValue()[0];
            double percent = entry.getValue()[1];
            if (flat == 0.0 && percent == 0.0) {
                continue;
            }
            statKeys.add(entry.getKey().name());
            flatValues.add(String.valueOf(flat));
            percentValues.add(String.valueOf(percent));
        }
        
        return item
                .withMetadata(META_SOCKETS_MAX, Codec.INTEGER, socketData.getMaxSockets())
                .withMetadata(META_SOCKETS_VALUES, Codec.STRING_ARRAY, encoded)
                .withMetadata(META_ESSENCE_EFFECTS, Codec.STRING_ARRAY, effectTypes)
                .withMetadata(META_ESSENCE_TIER_MAP, Codec.STRING_ARRAY, effectTiers)
                .withMetadata(META_ESSENCE_EFFECT_LINES, Codec.STRING_ARRAY, effectLines)
                .withMetadata(META_ESSENCE_BONUS_STATS, Codec.STRING_ARRAY, statKeys.toArray(String[]::new))
                .withMetadata(META_ESSENCE_BONUS_FLAT, Codec.STRING_ARRAY, flatValues.toArray(String[]::new))
                .withMetadata(META_ESSENCE_BONUS_PERCENT, Codec.STRING_ARRAY, percentValues.toArray(String[]::new))
                .withMetadata(ResonanceSystem.META_RESONANCE_NAME, Codec.STRING, resonance.active() ? resonance.name() : "")
                .withMetadata(ResonanceSystem.META_RESONANCE_EFFECT, Codec.STRING, resonanceTooltipEffect)
                .withMetadata(ResonanceSystem.META_RESONANCE_TYPE, Codec.STRING, resonance.active() ? resonance.type().name() : ResonanceSystem.ResonanceType.NONE.name())
                .withMetadata(ResonanceSystem.META_RESONANCE_QUALITY, Codec.STRING, resonance.active() ? ResonanceSystem.LEGENDARY_QUALITY : "")
                // Best effort: there is no runtime ItemStack quality setter, so we persist a metadata flag.
                .withMetadata(ResonanceSystem.META_RESONANCE_QUALITY_INDEX, Codec.INTEGER,
                        resonance.active() ? ResonanceSystem.LEGENDARY_QUALITY_INDEX : 0);
    }
    
    /**
     * Gets the essence effects stored in item metadata.
     * @return Array of effect types (e.g., ["FIRE", "ICE"]) or null if not present
     */
    public static String[] getEssenceEffects(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return item.getFromMetadataOrNull(META_ESSENCE_EFFECTS, Codec.STRING_ARRAY);
    }
    
    /**
     * Gets the essence tiers stored in item metadata.
     * @return Array of tiers as strings (e.g., ["3", "1"]) or null if not present
     */
    public static String[] getEssenceTiers(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return item.getFromMetadataOrNull(META_ESSENCE_TIER_MAP, Codec.STRING_ARRAY);
    }

    public static String[] getEssenceEffectLines(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        return item.getFromMetadataOrNull(META_ESSENCE_EFFECT_LINES, Codec.STRING_ARRAY);
    }

    public static String describeEssenceEffect(Essence.Type type, int tier, boolean isWeapon, SocketData socketData) {
        double multiplier = getTypeEffectMultiplier(socketData, type);
        return SocketEffectMath.describeEffect(type, tier, isWeapon, socketData, multiplier);
    }

    public static boolean isGreaterEssenceId(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return false;
        }
        String lower = essenceId.trim().toLowerCase(Locale.ROOT);
        return lower.contains("_concentrated") || lower.contains("greater");
    }

    public static boolean isGreaterEssenceItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String lower = itemId.trim().toLowerCase(Locale.ROOT);
        return (lower.contains("essence") && lower.contains("concentrated")) || lower.contains("greater");
    }

    public static String buildEssenceId(String essenceType, boolean isGreater) {
        if (essenceType == null || essenceType.isBlank()) {
            return null;
        }
        String cleaned = essenceType.trim();
        if (cleaned.startsWith("Essence_")) {
            cleaned = cleaned.substring("Essence_".length());
        }
        if (cleaned.endsWith("_Concentrated")) {
            cleaned = cleaned.substring(0, cleaned.length() - "_Concentrated".length());
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return null;
        }
        String canonical = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        return "Essence_" + canonical + (isGreater ? GREATER_ESSENCE_SUFFIX : "");
    }

    public static String resolveEssenceTypeFromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return "FIRE";
        if (lower.contains("ice")) return "ICE";
        if (lower.contains("life")) return "LIFE";
        if (lower.contains("lightning")) return "LIGHTNING";
        if (lower.contains("void")) return "VOID";
        if (lower.contains("water")) return "WATER";
        return null;
    }

    public static String resolveEssenceIdFromItemId(String itemId) {
        String type = resolveEssenceTypeFromItemId(itemId);
        if (type == null) {
            return null;
        }
        return buildEssenceId(type, isGreaterEssenceItemId(itemId));
    }

    public static String getResonanceName(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        String value = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_NAME, Codec.STRING);
        return value == null || value.isBlank() ? null : value;
    }

    public static String getResonanceEffect(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        String value = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_EFFECT, Codec.STRING);
        return value == null || value.isBlank() ? null : value;
    }

    public static String getResonanceType(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        String value = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_TYPE, Codec.STRING);
        return value == null || value.isBlank() ? null : value;
    }

    public static boolean hasResonance(ItemStack item) {
        return getResonanceName(item) != null;
    }

    public static boolean isResonanceLegendary(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        String quality = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_QUALITY, Codec.STRING);
        if (quality != null && ResonanceSystem.LEGENDARY_QUALITY.equalsIgnoreCase(quality.trim())) {
            return true;
        }
        Integer qualityIndex = item.getFromMetadataOrNull(ResonanceSystem.META_RESONANCE_QUALITY_INDEX, Codec.INTEGER);
        return qualityIndex != null && qualityIndex >= ResonanceSystem.LEGENDARY_QUALITY_INDEX;
    }

    /**
     * Reads a stored essence bonus from metadata.
     * Returns [flatBonus, percentBonus] for the requested stat.
     * Falls back to deterministic recalculation when metadata is missing.
     */
    public static double[] getStoredStatBonus(ItemStack item, EssenceEffect.StatType stat) {
        if (item == null || item.isEmpty() || stat == null) {
            return new double[] {0.0, 0.0};
        }

        // Prefer live deterministic recomputation from current socket layout so gameplay
        // stays in sync even if older metadata was generated with previous formulas.
        SocketData socketData = getSocketData(item);
        if (socketData != null) {
            boolean isWeapon = ReforgeEquip.isWeapon(item);
            Map<EssenceEffect.StatType, double[]> computed = calculateDeterministicBonuses(item, socketData, isWeapon);
            double[] live = computed.get(stat);
            if (live != null) {
                return new double[] {live[0], live[1]};
            }
            return new double[] {0.0, 0.0};
        }

        // Fallback: if socket parsing is unavailable, use persisted metadata values.
        String[] statKeys = item.getFromMetadataOrNull(META_ESSENCE_BONUS_STATS, Codec.STRING_ARRAY);
        String[] flatValues = item.getFromMetadataOrNull(META_ESSENCE_BONUS_FLAT, Codec.STRING_ARRAY);
        String[] percentValues = item.getFromMetadataOrNull(META_ESSENCE_BONUS_PERCENT, Codec.STRING_ARRAY);

        if (statKeys != null && flatValues != null && percentValues != null) {
            int count = Math.min(statKeys.length, Math.min(flatValues.length, percentValues.length));
            for (int i = 0; i < count; i++) {
                if (!stat.name().equals(statKeys[i])) {
                    continue;
                }
                double flat = parseDoubleOrZero(flatValues[i]);
                double percent = parseDoubleOrZero(percentValues[i]);
                return new double[] {flat, percent};
            }
        }
        return new double[] {0.0, 0.0};
    }

    private static double parseDoubleOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static Map<EssenceEffect.StatType, double[]> calculateDeterministicBonuses(ItemStack item, SocketData socketData, boolean isWeapon) {
        Map<EssenceEffect.StatType, double[]> totals = calculateBaseTierBonuses(socketData, isWeapon);
        if (socketData == null) {
            return totals;
        }

        ResonanceSystem.ResonanceResult resonance = ResonanceSystem.evaluate(item, socketData);
        if (resonance.active()) {
            for (Map.Entry<EssenceEffect.StatType, double[]> entry : resonance.bonuses().entrySet()) {
                double[] values = entry.getValue();
                if (values == null) {
                    continue;
                }
                if (values[0] != 0.0) {
                    addFlat(totals, entry.getKey(), values[0]);
                }
                if (values[1] != 0.0) {
                    addPercent(totals, entry.getKey(), values[1]);
                }
            }
        }

        return totals;
    }

    private static Map<EssenceEffect.StatType, double[]> calculateBaseTierBonuses(SocketData socketData, boolean isWeapon) {
        Map<EssenceEffect.StatType, double[]> totals = new EnumMap<>(EssenceEffect.StatType.class);
        if (socketData == null) {
            return totals;
        }

        Map<Essence.Type, Integer> tiers = calculateConsecutiveTiers(socketData);
        for (Map.Entry<Essence.Type, Integer> entry : tiers.entrySet()) {
            Essence.Type type = entry.getKey();
            int tierValue = SocketEffectMath.clampTier(entry.getValue());
            double multiplier = getTypeEffectMultiplier(socketData, type);

            if (isWeapon) {
                switch (type) {
                    case FIRE, WATER -> {
                        double[] split = SocketEffectMath.splitWeaponDamagePoints(socketData, type, tierValue);
                        addPercent(totals, EssenceEffect.StatType.DAMAGE, split[0] * multiplier);
                        addFlat(totals, EssenceEffect.StatType.DAMAGE, split[1] * multiplier);
                    }
                    case ICE -> addFlat(totals, EssenceEffect.StatType.DAMAGE, SocketEffectMath.weaponIceDamageFlat(tierValue) * multiplier);
                    case LIFE -> addPercent(totals, EssenceEffect.StatType.LIFE_STEAL, SocketEffectMath.weaponLifeStealPercent(tierValue) * multiplier);
                    case LIGHTNING -> {
                        addPercent(totals, EssenceEffect.StatType.ATTACK_SPEED, SocketEffectMath.weaponLightningAttackSpeedPercent(tierValue) * multiplier);
                        addPercent(totals, EssenceEffect.StatType.CRIT_CHANCE, SocketEffectMath.weaponLightningCritChancePercent(tierValue) * multiplier);
                    }
                    case VOID -> addPercent(totals, EssenceEffect.StatType.CRIT_DAMAGE, SocketEffectMath.weaponVoidCritDamagePercent(tierValue) * multiplier);
                    default -> {
                        // No weapon stat persisted for this essence type.
                    }
                }
            } else {
                switch (type) {
                    case LIFE -> addFlat(totals, EssenceEffect.StatType.HEALTH, SocketEffectMath.armorLifeHealthFlat(tierValue) * multiplier);
                    case WATER -> addFlat(totals, EssenceEffect.StatType.REGENERATION, SocketEffectMath.armorWaterRegenFlat(tierValue) * multiplier);
                    case FIRE -> addPercent(totals, EssenceEffect.StatType.FIRE_DEFENSE, SocketEffectMath.armorFireDefensePercent(tierValue) * multiplier);
                    case ICE -> addPercent(totals, EssenceEffect.StatType.MOVEMENT_SPEED, SocketEffectMath.armorIceSlowPercent(tierValue) * multiplier);
                    case LIGHTNING -> addPercent(totals, EssenceEffect.StatType.EVASION, SocketEffectMath.armorLightningEvasionPercent(tierValue) * multiplier);
                    case VOID -> addPercent(totals, EssenceEffect.StatType.DEFENSE, SocketEffectMath.armorVoidDefensePercent(tierValue) * multiplier);
                    default -> {
                        // No armor stat persisted for this essence type.
                    }
                }
            }
        }
        return totals;
    }

    private static void addFlat(Map<EssenceEffect.StatType, double[]> totals, EssenceEffect.StatType stat, double value) {
        double[] values = totals.computeIfAbsent(stat, ignored -> new double[] {0.0, 0.0});
        values[0] += value;
    }

    private static void addPercent(Map<EssenceEffect.StatType, double[]> totals, EssenceEffect.StatType stat, double value) {
        double[] values = totals.computeIfAbsent(stat, ignored -> new double[] {0.0, 0.0});
        values[1] += value;
    }

    private static double getTypeEffectMultiplier(SocketData socketData, Essence.Type type) {
        if (socketData == null || type == null) {
            return 1.0;
        }
        int totalForType = 0;
        int greaterForType = 0;
        for (Socket socket : socketData.getSockets()) {
            if (socket == null || socket.isEmpty() || socket.isBroken()) {
                continue;
            }
            Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
            if (essence == null || essence.getType() != type) {
                continue;
            }
            totalForType++;
            if (isGreaterEssenceId(socket.getEssenceId())) {
                greaterForType++;
            }
        }
        if (totalForType <= 0) {
            return 1.0;
        }
        double ratio = (double) greaterForType / (double) totalForType;
        return 1.0 + (0.5 * ratio);
    }

    private static String[] encodeSockets(SocketData socketData) {
        String[] values = new String[socketData.getCurrentSocketCount()];
        for (int i = 0; i < socketData.getCurrentSocketCount(); i++) {
            Socket socket = socketData.getSockets().get(i);
            if (socket.isBroken()) {
                values[i] = "x";
            } else if (socket.isEmpty()) {
                values[i] = "";
            } else {
                values[i] = socket.getEssenceId();
            }
        }
        return values;
    }

    // ── Punch socket ──────────────────────────────────────────────────────────

    /**
     * Attempts to punch a new socket into the given SocketData.
     * Mutates socketData on SUCCESS (socket added).
     */
    public static PunchResult punchSocket(SocketData socketData, SupportMaterial support) {
        if (!socketData.canAddSocket()) return PunchResult.FAIL;

        int current = socketData.getCurrentSocketCount();

        double successChance = config.getSuccessChance(current);
        double breakChance   = config.getBreakChance(current);

        // Apply supporting material modifiers
        switch (support) {
            case SOCKET_STABILIZER -> breakChance   *= 0.50;
            case SOCKET_REINFORCER -> successChance  = Math.min(1.0, successChance + 0.20);
            case SOCKET_GUARANTOR  -> { if (current == 0) successChance = 1.0; }
            default -> { /* NONE / SOCKET_EXPANDER has no success modifier */ }
        }

        float roll = RNG.nextFloat();

        if (roll < breakChance) {
            // Item broke during punch attempt
            return PunchResult.BREAK;
        }

        if (roll < breakChance + (1.0 - successChance)) {
            return PunchResult.FAIL;
        }

        // Success
        socketData.addSocket();

        // SOCKET_EXPANDER: 25% chance for an extra bonus socket
        if (support == SupportMaterial.SOCKET_EXPANDER
                && socketData.canAddSocket()
                && RNG.nextFloat() < 0.25f) {
            socketData.addSocket();
        }

        return PunchResult.SUCCESS;
    }

    // ── Socket essence ────────────────────────────────────────────────────────

    /**
     * Fills the first empty socket with an essence. Always succeeds if an empty slot exists.
     */
    public static boolean socketEssence(SocketData socketData, String essenceId) {
        return socketData.socketEssence(essenceId);
    }

    // ── Remove essence ────────────────────────────────────────────────────────

    /**
     * Attempts to remove an essence from the given slot.
     * The essence is always destroyed on removal regardless of success/fail.
     * On FAIL the removal tool is also destroyed.
     */
    public static RemoveResult removeEssence(SocketData socketData, int slotIndex) {
        String removed = socketData.removeEssence(slotIndex);
        if (removed == null) return RemoveResult.FAIL_TOOL_DESTROYED;

        float roll = RNG.nextFloat();
        if (roll < config.getEssenceRemovalSuccessChance()) {
            return RemoveResult.SUCCESS;
        }

        // Tool destroyed but socket is already cleared above
        return RemoveResult.FAIL_TOOL_DESTROYED;
    }

    // ── Stat bonus calculation ────────────────────────────────────────────────

    /**
     * Returns the total FLAT + PERCENTAGE modifier for a given stat across
     * all socketed essences. Used by SocketEffectEST.
     *
     * @return [flatBonus, percentBonus]
     */
    public static double[] calculateSocketBonus(
            SocketData socketData,
            EssenceEffect.StatType stat
    ) {
        double flat    = 0;
        double percent = 0;

        for (Socket socket : socketData.getSockets()) {
            if (socket.isEmpty()) continue;
            Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
            if (essence == null) continue;
            double multiplier = isGreaterEssenceId(socket.getEssenceId()) ? 1.5 : 1.0;

            for (EssenceEffect effect : essence.getEffects()) {
                if (effect.getStat() != stat) continue;
                if (effect.getType() == EssenceEffect.EffectType.FLAT) {
                    flat += effect.getValue() * multiplier;
                } else {
                    percent += effect.getValue() * multiplier;
                }
            }
        }

        return new double[]{ flat, percent };
    }

    // ── Tier-based stat calculation (consecutive essences) ──────────────────────

    /**
     * Calculates tiered bonuses based on CONSECUTIVE essence types.
     * Example: "Life", "Life", "Life", "Fire" = Tier 3 Life, Tier 1 Fire
     * 
     * @param socketData The socket data to calculate from
     * @param stat The stat to calculate bonus for
     * @param isWeapon Whether the item is a weapon (affects Life essence)
     * @return [flatBonus, percentBonus]
     */
    public static double[] calculateTieredBonus(
            SocketData socketData,
            EssenceEffect.StatType stat,
            boolean isWeapon
    ) {
        if (socketData == null || stat == null) {
            return new double[] {0.0, 0.0};
        }
        Map<EssenceEffect.StatType, double[]> base = calculateBaseTierBonuses(socketData, isWeapon);
        return base.getOrDefault(stat, new double[] {0.0, 0.0});
    }

    /**
     * Calculates the tier for each essence type based on consecutive count.
     * Returns a map of Essence Type -> Tier (1-5).
     */
    public static Map<Essence.Type, Integer> calculateConsecutiveTiers(SocketData socketData) {
        Map<Essence.Type, Integer> tierMap = new LinkedHashMap<>();

        Essence.Type currentType = null;
        int consecutiveCount = 0;

        for (Socket socket : socketData.getSockets()) {
            if (socket.isEmpty() || socket.isBroken()) {
                // Reset on empty/broken socket
                if (currentType != null && consecutiveCount > 0) {
                    int previous = tierMap.getOrDefault(currentType, 0);
                    tierMap.put(currentType, Math.max(previous, Math.min(consecutiveCount, 5)));
                }
                currentType = null;
                consecutiveCount = 0;
                continue;
            }

            String essenceId = socket.getEssenceId();
            Essence essence = EssenceRegistry.get().getById(essenceId);
            if (essence == null) continue;

            Essence.Type essenceType = essence.getType();

            if (essenceType == currentType) {
                consecutiveCount++;
            } else {
                // Save previous type if any
                if (currentType != null && consecutiveCount > 0) {
                    int previous = tierMap.getOrDefault(currentType, 0);
                    tierMap.put(currentType, Math.max(previous, Math.min(consecutiveCount, 5)));
                }
                currentType = essenceType;
                consecutiveCount = 1;
            }
        }

        // Don't forget the last sequence
        if (currentType != null && consecutiveCount > 0) {
            int previous = tierMap.getOrDefault(currentType, 0);
            tierMap.put(currentType, Math.max(previous, Math.min(consecutiveCount, 5)));
        }

        return tierMap;
    }
}
