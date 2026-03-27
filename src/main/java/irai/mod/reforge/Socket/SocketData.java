package irai.mod.reforge.Socket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Util.DynamicTooltipUtils;

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
    public int getLockedSocketCount() { 
        return (int) sockets.stream().filter(Socket::isLocked).count(); 
    }
    public List<Socket> getSockets() { return sockets; }
    
    /**
     * Sets the maximum number of sockets.
     */
    public void setMaxSockets(int max) { 
        this.maxSockets = Math.max(0, max);
    }
    
    /**
     * Reduces the maximum number of sockets by 1 (used when item breaks during punching).
     * @return true if max sockets was reduced, false if already at 0
     */
    public boolean reduceMaxSockets() {
        if (maxSockets > 0) {
            maxSockets--;
            // Also remove last socket if there are more sockets than new max
            while (sockets.size() > maxSockets && !sockets.isEmpty()) {
                sockets.remove(sockets.size() - 1);
            }
            return true;
        }
        return false;
    }
    
    /**
     * Marks the last socket as broken (used when item breaks during punching).
     * This keeps the socket slot but marks it as unusable.
     * @return true if a socket was broken, false if no sockets available
     */
    public boolean breakSocket() {
        if (!sockets.isEmpty()) {
            // Mark the last socket as broken
            Socket lastSocket = sockets.get(sockets.size() - 1);
            lastSocket.setBroken(true);
            lastSocket.setEssenceId(null);
            return true;
        }
        return false;
    }
    
    /**
     * Checks if there are any broken sockets.
     * @return true if there is at least one broken socket
     */
    public boolean hasBrokenSocket() {
        return sockets.stream().anyMatch(Socket::isBroken);
    }
    
    /**
     * Repairs the first broken socket found.
     * This makes the socket available for use again (empty but not broken).
     * @return true if a socket was repaired, false if no broken sockets
     */
    public boolean repairBrokenSocket() {
        for (Socket socket : sockets) {
            if (socket.isBroken()) {
                socket.setBroken(false);
                // Keep it empty, player needs to socket an essence again
                socket.setEssenceId(null);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns the number of available (unlocked and unfilled) socket slots.
     */
    public int getOpenSocketCount() {
        return Math.max(0, maxSockets - sockets.size());
    }

    public int getCurrentSocketCount() { return sockets.size(); }

    public boolean canAddSocket() { return sockets.size() < maxSockets; }

    public boolean hasEmptySocket() {
        return sockets.stream().anyMatch(s -> s.getEssenceId() == null && !s.isLocked());
    }
    
    /**
     * Checks if there's a socket available for filling (empty or broken).
     * @return true if there's an available socket
     */
    public boolean hasAvailableSocket() {
        return sockets.stream().anyMatch(s -> (s.getEssenceId() == null || s.isBroken()) && !s.isLocked());
    }
    
    /**
     * Lock the first empty socket (called when essence socketting fails).
     * Returns true if a socket was locked, false if no empty sockets available.
     */
    public boolean lockEmptySocket() {
        for (Socket socket : sockets) {
            if (socket.getEssenceId() == null && !socket.isLocked()) {
                socket.setLocked(true);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Salvage a locked socket (restore it to usable state).
     * Returns true if a socket was salvaged, false if no locked sockets.
     */
    public boolean salvageLockedSocket() {
        for (Socket socket : sockets) {
            if (socket.isLocked()) {
                socket.setLocked(false);
                return true;
            }
        }
        return false;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /** Adds a new empty socket. Returns false if at max. */
    public boolean addSocket() {
        if (!canAddSocket()) return false;
        sockets.add(new Socket(sockets.size(), null));
        return true;
    }

    /** Fills the first empty socket with the given essence. Skips broken sockets. Returns false if none available. */
    public boolean socketEssence(String essenceId) {
        for (Socket socket : sockets) {
            // Only fill empty sockets, skip broken ones
            if (socket.getEssenceId() == null && !socket.isBroken() && !socket.isLocked()) {
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

    /**
     * Calculates tiered stat bonuses based on CONSECUTIVE essence types.
     * This is used for DynamicTooltips and actual effect application.
     * 
     * @param statType The stat type to calculate
     * @param isWeapon Whether the item is a weapon (affects Life essence)
     * @return The total bonus value
     */
    public double getTieredStatBonus(StatType statType, boolean isWeapon) {
        EssenceEffect.StatType effectStat = convertStatTypeToEffectStat(statType);
        if (effectStat == null) {
            return 0.0;
        }
        double[] bonus = SocketManager.calculateTieredBonus(this, effectStat, isWeapon);
        if (bonus == null) {
            return 0.0;
        }
        return isPercentageStat(statType) ? bonus[1] : bonus[0];
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
            case REGENERATION: return StatType.REGENERATION;
            case FIRE_DEFENSE: return StatType.FIRE_DEFENSE;
            case LIFE_STEAL: return StatType.LIFE_STEAL;
            case MOVEMENT_SPEED: return StatType.MOVEMENT_SPEED;
            case LUCK: return StatType.LUCK;
            default: return null;
        }
    }

    /** Converts SocketData.StatType to EssenceEffect.StatType. */
    private EssenceEffect.StatType convertStatTypeToEffectStat(StatType stat) {
        switch (stat) {
            case ATTACK_SPEED: return EssenceEffect.StatType.ATTACK_SPEED;
            case DAMAGE: return EssenceEffect.StatType.DAMAGE;
            case CRIT_CHANCE: return EssenceEffect.StatType.CRIT_CHANCE;
            case CRIT_DAMAGE: return EssenceEffect.StatType.CRIT_DAMAGE;
            case HEALTH: return EssenceEffect.StatType.HEALTH;
            case DEFENSE: return EssenceEffect.StatType.DEFENSE;
            case EVASION: return EssenceEffect.StatType.EVASION;
            case REGENERATION: return EssenceEffect.StatType.REGENERATION;
            case FIRE_DEFENSE: return EssenceEffect.StatType.FIRE_DEFENSE;
            case LIFE_STEAL: return EssenceEffect.StatType.LIFE_STEAL;
            case MOVEMENT_SPEED: return EssenceEffect.StatType.MOVEMENT_SPEED;
            case LUCK: return EssenceEffect.StatType.LUCK;
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
        // Weapons and armor both get 4 max sockets
        int max = 4;
        return new SocketData(max);
    }

    /**
     * Registers tooltips for this socket data to DynamicTooltipsLib if available.
     * Reads tier info from item metadata for consistency.
     * Shows color-coded socket indicators.
     * @param item The item stack to read metadata from
     * @param itemId The item ID to add tooltips to
     * @param isWeapon Whether the item is a weapon (affects Life essence effects)
     */
    public void registerTooltips(ItemStack item, String itemId, boolean isWeapon) {
        if (!DynamicTooltipUtils.isAvailable()) {
            return;
        }

        // Build socket colors array for color-coded display
        // Also track which sockets are broken
        String[] socketColors = new String[sockets.size()];
        boolean[] brokenSockets = new boolean[sockets.size()];
        for (int i = 0; i < sockets.size(); i++) {
            Socket socket = sockets.get(i);
            if (socket.isBroken()) {
                brokenSockets[i] = true;
            } else if (!socket.isEmpty()) {
                Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
                if (essence != null) {
                    socketColors[i] = getEssenceColor(essence.getType());
                }
            }
        }
        
        // Register colored socket display with broken socket info
        DynamicTooltipUtils.registerColoredSocketTooltip(itemId, null, maxSockets, socketColors, brokenSockets);

        // Try to get tier info from metadata first
        String[] effectTypes = SocketManager.getEssenceEffects(item);
        String[] effectTiers = SocketManager.getEssenceTiers(item);
        
        // Fall back to calculating from socket data if not in metadata
        Map<Essence.Type, Integer> tierMap;
        if (effectTypes != null && effectTiers != null && effectTypes.length == effectTiers.length) {
            tierMap = new LinkedHashMap<>();
            for (int i = 0; i < effectTypes.length; i++) {
                try {
                    Essence.Type type = Essence.Type.valueOf(effectTypes[i]);
                    int tier = Integer.parseInt(effectTiers[i]);
                    tierMap.put(type, tier);
                } catch (Exception e) {
                    // Ignore invalid entries
                }
            }
        } else {
            tierMap = SocketManager.calculateConsecutiveTiers(this);
        }

        // Add stat bonuses from metadata so tooltip values match applied gameplay values.
        List<String> statLines = new ArrayList<>();
        for (StatType stat : StatType.values()) {
            EssenceEffect.StatType effectStat = convertStatTypeToEffectStat(stat);
            if (effectStat == null) continue;

            double[] bonus = SocketManager.getStoredStatBonus(item, effectStat);
            double flatBonus = bonus[0];
            double percentBonus = bonus[1];

            if (percentBonus != 0) {
                statLines.add(stat.getDisplayName() + ": +" + (int) percentBonus + "%");
            }
            if (flatBonus != 0) {
                statLines.add(stat.getDisplayName() + ": +" + (int) flatBonus);
            }
        }

        // Register stat bonus lines if we have any
        if (!statLines.isEmpty()) {
            String[] lines = statLines.toArray(new String[0]);
            DynamicTooltipUtils.registerTooltip(itemId, "socket_stats", lines);
        }

        // Add individual essence info for display with colors
        for (Map.Entry<Essence.Type, Integer> entry : tierMap.entrySet()) {
            String essenceName = entry.getKey().name();
            int tier = entry.getValue();
            String color = getEssenceColor(entry.getKey());
            String coloredName = color + essenceName + " T" + tier + "</color>";
            DynamicTooltipUtils.addEssenceTooltip(itemId, coloredName, 
                getEffectDescription(entry.getKey(), tier, isWeapon));
        }
    }
    
    /**
     * Overload for backward compatibility.
     */
    public void registerTooltips(String itemId, boolean isWeapon) {
        // This version doesn't have item metadata access, use empty map
        if (!DynamicTooltipUtils.isAvailable()) {
            return;
        }

        // Build socket colors array
        String[] socketColors = new String[sockets.size()];
        boolean[] brokenSockets = new boolean[sockets.size()];
        for (int i = 0; i < sockets.size(); i++) {
            Socket socket = sockets.get(i);
            if (socket.isBroken()) {
                brokenSockets[i] = true;
            } else if (!socket.isEmpty()) {
                Essence essence = EssenceRegistry.get().getById(socket.getEssenceId());
                if (essence != null) {
                    socketColors[i] = getEssenceColor(essence.getType());
                }
            }
        }
        
        DynamicTooltipUtils.registerColoredSocketTooltip(itemId, null, maxSockets, socketColors, brokenSockets);

        // Calculate tier map
        Map<Essence.Type, Integer> tierMap = SocketManager.calculateConsecutiveTiers(this);

        // Add stat bonuses
        List<String> statLines = new ArrayList<>();
        for (StatType stat : StatType.values()) {
            double tieredBonus = getTieredStatBonus(stat, isWeapon);
            if (tieredBonus != 0) {
                boolean isPercent = isPercentageStat(stat);
                String prefix = stat.getDisplayName() + ": +";
                statLines.add(prefix + (int)tieredBonus + (isPercent ? "%" : ""));
            }
        }

        if (!statLines.isEmpty()) {
            DynamicTooltipUtils.registerTooltip(itemId, "socket_stats", statLines.toArray(new String[0]));
        }

        // Note: The no-ItemStack overload can't read item metadata, so some tooltip
        // details available in the ItemStack version won't be available here.

        // Add essence info
        for (Map.Entry<Essence.Type, Integer> entry : tierMap.entrySet()) {
            String color = getEssenceColor(entry.getKey());
            String coloredName = color + entry.getKey().name() + " T" + entry.getValue() + "</color>";
            DynamicTooltipUtils.addEssenceTooltip(itemId, coloredName, 
                getEffectDescription(entry.getKey(), entry.getValue(), isWeapon));
        }
    }

    /**
     * Gets the color code for an essence type.
     */
    private String getEssenceColor(Essence.Type type) {
        switch (type) {
            case FIRE: return "<color is=\"#FFAA00\">";   // Orange
            case ICE: return "<color is=\"#55FFFF\">";    // Cyan
            case LIFE: return "<color is=\"#55FF55\">";    // Green
            case LIGHTNING: return "<color is=\"#FFFF55\">"; // Yellow
            case VOID: return "<color is=\"#AA55FF\">";    // Purple
            case WATER: return "<color is=\"#5555FF\">";    // Blue
            default: return "<color is=\"#FFFFFF\">";     // White
        }
    }

    /**
     * Overload for backward compatibility - assumes weapon by default.
     */
    public void registerTooltips(String itemId) {
        registerTooltips(itemId, false);
    }

    /**
     * Checks if a stat is a percentage-based stat.
     */
    private boolean isPercentageStat(StatType stat) {
        return stat == StatType.DAMAGE || stat == StatType.ATTACK_SPEED || 
               stat == StatType.CRIT_CHANCE || stat == StatType.CRIT_DAMAGE ||
               stat == StatType.EVASION || stat == StatType.LIFE_STEAL ||
               stat == StatType.DEFENSE;
    }

    /**
     * Gets a description of the effect for an essence type and tier.
     * Uses actual values from EssenceRegistry to ensure consistency.
     */
    private String getEffectDescription(Essence.Type type, int tier, boolean isWeapon) {
        return SocketManager.describeEssenceEffect(type, tier, isWeapon, this);
    }
}

