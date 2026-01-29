package irai.mod.reforge.UI;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;

/**
 * Renders custom tooltips for upgraded weapons.
 * Shows upgrade level, damage bonus, and visual indicators.
 */
public class WeaponTooltip {
    
    // ══════════════════════════════════════════════════════════════════════════════
    // Tooltip Generation
    // ══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Generates a formatted tooltip for a weapon.
     * Shows upgrade level and damage multiplier.
     */
    public static void showWeaponTooltip(Player player, ItemStack weapon, short slot) {
        int level = WeaponUpgradeTracker.getUpgradeLevel(player, weapon, slot);
        
        if (level == 0) {
            // Base weapon - show upgrade potential
            player.sendMessage(Message.raw(""));
            player.sendMessage(Message.raw("[Upgradeable Weapon]"));
            player.sendMessage(Message.raw("Take to a Reforgebench to upgrade!"));
            player.sendMessage(Message.raw(""));
        } else {
            // Upgraded weapon - show stats
            showUpgradedWeaponTooltip(player, level);
        }
    }
    
    /**
     * Shows detailed tooltip for an upgraded weapon.
     */
    private static void showUpgradedWeaponTooltip(Player player, int level) {
        String upgradeName = WeaponUpgradeTracker.getUpgradeName(level);
        double multiplier = WeaponUpgradeTracker.getDamageMultiplier(level);
        String color = getColorForLevel(level);
        String stars = getStarsForLevel(level);
        
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(color + "|  " + stars + " " + upgradeName.toUpperCase() + " WEAPON " + stars + "  |"));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(color + "| Upgrade Level: " + color + "+" + level + "        " + color + "|"));
        player.sendMessage(Message.raw(color + "| Damage Bonus: §6+" + String.format("%.0f", (multiplier - 1.0) * 100) + "%       " + color + "|"));
        player.sendMessage(Message.raw(color + "============================="));
        player.sendMessage(Message.raw(""));
    }
    
    /**
     * Shows a compact inline tooltip (for hotbar switching).
     */
    public static void showCompactTooltip(Player player, ItemStack weapon, short slot) {
        int level = WeaponUpgradeTracker.getUpgradeLevel(player, weapon, slot);
        
        if (level == 0) {
            return; // Don't show anything for base weapons
        }
        
        String color = getColorForLevel(level);
        String upgradeName = WeaponUpgradeTracker.getUpgradeName(level);
        double multiplier = WeaponUpgradeTracker.getDamageMultiplier(level);
        
        String tooltip = color + "⚔ " + upgradeName + " +" + level + " (+" +
                         String.format("%.0f", (multiplier - 1.0) * 100) + "% damage)";
        
        // Send as action bar or title (depends on API availability)
        sendActionBar(player, tooltip);
    }
    
    /**
     * Shows upgrade success animation.
     */
    public static void showUpgradeSuccess(Player player, int oldLevel, int newLevel) {
        String color = getColorForLevel(newLevel);
        
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(color + "        WEAPON UPGRADED!        "));
        player.sendMessage(Message.raw("      +" + oldLevel + "   ->  " + color + "+" + newLevel));
        player.sendMessage(Message.raw(color + "++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }
    
    /**
     * Shows upgrade failure animation.
     */
    public static void showUpgradeFailure(Player player, int oldLevel, int newLevel) {
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw("     WEAPON DEGRADED!           "));
        player.sendMessage(Message.raw("    +" + oldLevel + " ->   +" + newLevel));
        player.sendMessage(Message.raw("++++++++++++++++++++++++++++++++"));
        player.sendMessage(Message.raw(""));
    }
    
    /**
     * Shows weapon shatter animation.
     */
    public static void showWeaponShatter(Player player) {
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw("      WEAPON SHATTERED!  "));
        player.sendMessage(Message.raw("    The weapon broke into pieces..."));
        player.sendMessage(Message.raw("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
        player.sendMessage(Message.raw(""));
    }
    
    // ══════════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Gets the color code for an upgrade level.
     */
    private static String getColorForLevel(int level) {
        switch (level) {
            case 1: return ""; // Green for +1
            case 2: return ""; // Aqua for +2
            case 3: return ""; // Gold for +3
            default: return ""; // White for base
        }
    }
    
    /**
     * Gets star decoration for an upgrade level.
     */
    private static String getStarsForLevel(int level) {
        switch (level) {
            case 1: return "*";
            case 2: return "**";
            case 3: return "***";
            default: return "";
        }
    }
    
    /**
     * Sends a message to the action bar (above hotbar).
     * Fallback to chat if action bar is not available.
     */
    private static void sendActionBar(Player player, String message) {
        try {
            // Try to send to action bar if API supports it
            // player.sendActionBar(Message.raw(message));
            
            // Fallback: send to chat
            player.sendMessage(Message.raw(message));
        } catch (Exception e) {
            // Action bar not available, use chat
            player.sendMessage(Message.raw(message));
        }
    }
    
    /**
     * Creates a progress bar for upgrade level.
     */
    public static String createProgressBar(int level, int maxLevel) {
        StringBuilder bar = new StringBuilder("[");
        
        for (int i = 0; i < maxLevel; i++) {
            if (i < level) {
                bar.append("+"); // Filled
            } else {
                bar.append(" "); // Empty
            }
        }
        
        bar.append("]");
        return bar.toString();
    }
    
    /**
     * Shows weapon stats in a detailed format.
     */
    public static void showDetailedStats(Player player, ItemStack weapon, short slot) {
        int level = WeaponUpgradeTracker.getUpgradeLevel(player, weapon, slot);
        String itemId = weapon.getItemId();
        String upgradeName = WeaponUpgradeTracker.getUpgradeName(level);
        double multiplier = WeaponUpgradeTracker.getDamageMultiplier(level);
        String color = getColorForLevel(level);
        String progressBar = createProgressBar(level, 3);
        
        player.sendMessage(Message.raw(""));
        player.sendMessage(Message.raw("============================="));
        player.sendMessage(Message.raw("  Weapon: " + itemId));
        player.sendMessage(Message.raw("  Upgrade: " + color + upgradeName + " +" + level));
        player.sendMessage(Message.raw("  Progress: " + progressBar + " (" + level + "/3)"));
        player.sendMessage(Message.raw("  Damage: x" + String.format("%.2f", multiplier) + " (" + String.format("%.0f", multiplier * 100) + "%)"));
        
        if (level < 3) {
            double nextMultiplier = WeaponUpgradeTracker.getDamageMultiplier(level + 1);
            player.sendMessage(Message.raw("  Next Level: x" + String.format("%.2f", nextMultiplier) + " (" + String.format("%.0f", nextMultiplier * 100) + "%)"));
        } else {
            player.sendMessage(Message.raw("   MAX LEVEL ACHIEVED   "));
        }
        
        player.sendMessage(Message.raw("=============================="));
        player.sendMessage(Message.raw(""));
    }
}
