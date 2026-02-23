package irai.mod.reforge.Socket;

import java.util.ArrayList;
import java.util.List;

/**
 * Stored in item NBT under key "Sockets".
 */
public class SocketData {

    private int maxSockets;
    private List<Socket> sockets;

    public SocketData(int maxSockets) {
        this.maxSockets = maxSockets;
        this.sockets = new ArrayList<>();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getMaxSockets() { return maxSockets; }
    public List<Socket> getSockets() { return sockets; }

    public int getCurrentSocketCount() { return sockets.size(); }

    public boolean canAddSocket() { return sockets.size() < maxSockets; }

    public boolean hasEmptySocket() {
        return sockets.stream().anyMatch(s -> s.getEssenceId() == null);
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /** Adds a new empty socket. Returns false if at max. */
    public boolean addSocket() {
        if (!canAddSocket()) return false;
        sockets.add(new Socket(sockets.size(), null));
        return true;
    }

    /** Fills the first empty socket with the given essence. Returns false if none available. */
    public boolean socketEssence(String essenceId) {
        for (Socket socket : sockets) {
            if (socket.getEssenceId() == null) {
                socket.setEssenceId(essenceId);
                return true;
            }
        }
        return false;
    }

    /** Removes essence from the socket at the given index. Returns the essence ID or null. */
    public String removeEssence(int slotIndex) {
        for (Socket socket : sockets) {
            if (socket.getSlotIndex() == slotIndex) {
                String id = socket.getEssenceId();
                socket.setEssenceId(null);
                return id;
            }
        }
        return null;
    }

    /** Sets essence for a specific socket index. Returns false if slot does not exist. */
    public boolean setEssenceAt(int slotIndex, String essenceId) {
        for (Socket socket : sockets) {
            if (socket.getSlotIndex() == slotIndex) {
                socket.setEssenceId(essenceId);
                return true;
            }
        }
        return false;
    }

    // ── Stat bonus calculation ────────────────────────────────────────────────

    /**
     * Calculates the total stat bonus from all socketed essences.
     * @param statType The stat type to calculate (DAMAGE, DEFENSE, etc.)
     * @param effectType The effect type (FLAT or PERCENTAGE)
     * @return The total bonus value
     */
    public double getTotalStatBonus(StatType statType, EffectType effectType) {
        double total = 0;

        for (Socket socket : sockets) {
            if (socket.isEmpty()) continue;
            Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
            if (essence == null) continue;

            for (EssenceEffect effect : essence.getEffects()) {
                // Convert EssenceEffect.StatType to StatType for comparison
                StatType effectStat = convertStatType(effect.getStat());
                EffectType effectEff = convertEffectType(effect.getType());
                
                if (effectStat == statType && effectEff == effectType) {
                    total += effect.getValue();
                }
            }
        }

        return total;
    }

    /** Converts EssenceEffect.StatType to SocketData.StatType */
    private StatType convertStatType(EssenceEffect.StatType stat) {
        switch (stat) {
            case ATTACK_SPEED: return StatType.ATTACK_SPEED;
            case DAMAGE: return StatType.DAMAGE;
            case CRIT_CHANCE: return StatType.CRIT_CHANCE;
            case CRIT_DAMAGE: return StatType.CRIT_DAMAGE;
            case HEALTH: return StatType.HEALTH;
            case DEFENSE: return StatType.DEFENSE;
            case EVASION: return StatType.EVASION;
            case LIFE_STEAL: return StatType.LIFE_STEAL;
            case MOVEMENT_SPEED: return StatType.MOVEMENT_SPEED;
            case LUCK: return StatType.LUCK;
            default: return null;
        }
    }

    /** Converts EssenceEffect.EffectType to SocketData.EffectType */
    private EffectType convertEffectType(EssenceEffect.EffectType type) {
        switch (type) {
            case FLAT: return EffectType.FLAT;
            case PERCENTAGE: return EffectType.PERCENTAGE;
            default: return null;
        }
    }

    // ── Serialisation helpers (used by SocketManager with item metadata) ──────

    public static SocketData fromDefaults(String itemType) {
        // Weapons get 4 max sockets, armour gets 2
        int max = itemType != null && itemType.toLowerCase().contains("armor") ? 2 : 4;
        return new SocketData(max);
    }
}
