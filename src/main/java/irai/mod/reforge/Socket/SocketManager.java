package irai.mod.reforge.Socket;

import java.util.Random;

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
        
        // Calculate tier map and effects for metadata storage
        java.util.Map<Essence.Type, Integer> tierMap = calculateConsecutiveTiers(socketData);
        String[] effectTypes = new String[tierMap.size()];
        String[] effectTiers = new String[tierMap.size()];
        
        int idx = 0;
        for (java.util.Map.Entry<Essence.Type, Integer> entry : tierMap.entrySet()) {
            effectTypes[idx] = entry.getKey().name();
            effectTiers[idx] = String.valueOf(entry.getValue());
            idx++;
        }
        
        return item
                .withMetadata(META_SOCKETS_MAX, Codec.INTEGER, socketData.getMaxSockets())
                .withMetadata(META_SOCKETS_VALUES, Codec.STRING_ARRAY, encoded)
                .withMetadata(META_ESSENCE_EFFECTS, Codec.STRING_ARRAY, effectTypes)
                .withMetadata(META_ESSENCE_TIER_MAP, Codec.STRING_ARRAY, effectTiers);
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

            for (EssenceEffect effect : essence.getEffects()) {
                if (effect.getStat() != stat) continue;
                if (effect.getType() == EssenceEffect.EffectType.FLAT) {
                    flat += effect.getValue();
                } else {
                    percent += effect.getValue();
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
        double flat = 0;
        double percent = 0;

        // Calculate consecutive tiers
        java.util.Map<Essence.Type, Integer> tierMap = calculateConsecutiveTiers(socketData);

        for (java.util.Map.Entry<Essence.Type, Integer> entry : tierMap.entrySet()) {
            Essence.Type type = entry.getKey();
            int tier = entry.getValue();

            double[] effects = EssenceRegistry.getTierEffect(type, tier, isWeapon);

            // Map stat type to effect index
            switch (stat) {
                case DAMAGE:
                    if (type == Essence.Type.FIRE || type == Essence.Type.ICE) {
                        // Fire: DAMAGE % and DAMAGE flat
                        // Ice: MOVEMENT_SPEED % and DAMAGE flat (cold damage)
                        percent += effects[0]; // % for Fire
                        flat += effects[1];   // flat for Fire/Ice
                    }
                    break;
                case ATTACK_SPEED:
                    if (type == Essence.Type.LIGHTNING) {
                        percent += effects[0]; // ATK Speed %
                    }
                    break;
                case CRIT_CHANCE:
                    if (type == Essence.Type.LIGHTNING) {
                        percent += effects[1]; // Crit %
                    }
                    break;
                case CRIT_DAMAGE:
                    if (type == Essence.Type.VOID) {
                        percent += effects[0];
                    }
                    break;
                case LIFE_STEAL:
                    if (type == Essence.Type.LIFE && isWeapon) {
                        percent += effects[0];
                    }
                    break;
                case HEALTH:
                    if (type == Essence.Type.LIFE && !isWeapon) {
                        flat += effects[1];
                    }
                    break;
                case EVASION:
                    if (type == Essence.Type.WATER && !isWeapon) {
                        percent += effects[0];
                    }
                    break;
                case MOVEMENT_SPEED:
                    if (type == Essence.Type.ICE) {
                        percent += effects[0]; // Slow % (negative)
                    }
                    break;
                default:
                    break;
            }
        }

        return new double[]{ flat, percent };
    }

    /**
     * Calculates the tier for each essence type based on consecutive count.
     * Returns a map of Essence Type -> Tier (1-5).
     */
    public static java.util.Map<Essence.Type, Integer> calculateConsecutiveTiers(SocketData socketData) {
        java.util.Map<Essence.Type, Integer> tierMap = new java.util.LinkedHashMap<>();

        Essence.Type currentType = null;
        int consecutiveCount = 0;

        for (Socket socket : socketData.getSockets()) {
            if (socket.isEmpty() || socket.isBroken()) {
                // Reset on empty/broken socket
                if (currentType != null && consecutiveCount > 0) {
                    tierMap.put(currentType, Math.min(consecutiveCount, 5));
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
                    tierMap.put(currentType, Math.min(consecutiveCount, 5));
                }
                currentType = essenceType;
                consecutiveCount = 1;
            }
        }

        // Don't forget the last sequence
        if (currentType != null && consecutiveCount > 0) {
            tierMap.put(currentType, Math.min(consecutiveCount, 5));
        }

        return tierMap;
    }
}
