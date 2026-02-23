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
        SOCKET_STABILIZER,   // -50% break chance
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
                socketData.setEssenceAt(i, essenceId);
            }
        }

        return socketData;
    }

    /**
     * Writes socket data into item metadata.
     * The returned ItemStack must be put back into an inventory/container slot.
     */
    public static ItemStack withSocketData(ItemStack item, SocketData socketData) {
        if (item == null || item.isEmpty() || socketData == null) return item;

        String[] encoded = encodeSockets(socketData);
        return item
                .withMetadata(META_SOCKETS_MAX, Codec.INTEGER, socketData.getMaxSockets())
                .withMetadata(META_SOCKETS_VALUES, Codec.STRING_ARRAY, encoded);
    }

    private static String[] encodeSockets(SocketData socketData) {
        String[] values = new String[socketData.getCurrentSocketCount()];
        for (int i = 0; i < socketData.getCurrentSocketCount(); i++) {
            Socket socket = socketData.getSockets().get(i);
            values[i] = socket.isEmpty() ? "" : socket.getEssenceId();
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
}
