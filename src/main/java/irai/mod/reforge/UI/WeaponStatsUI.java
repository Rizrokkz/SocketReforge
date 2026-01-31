package irai.mod.reforge.UI;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Interactions.ReforgeEquip;

public class WeaponStatsUI extends InteractiveCustomUIPage<WeaponStatsUI.Data> {

    public WeaponStatsUI(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("WeaponStatHUD.ui");

        // Get the player and their held item
        Player player = store.getComponent(ref, Player.getComponentType());
        ItemStack heldItem = player != null ? player.getInventory().getHotbar().getItemStack(player.getInventory().getActiveHotbarSlot()) : null;

        // Default values if no weapon is held
        String weaponName = "No weapon";
        String weaponLevel = "N/A";
        String weaponDamage = "x1.00";
        String weaponProgress = "[   ]";
        String nextLevelDamage = "";
        String degradeChance = "";
        String breakChance = "";
        String upgradeChance = "";
        String jackpotChance = "";
        
        // Max level values
        String weaponNameMax = "No weapon";
        String weaponLevelMax = "N/A";
        String weaponDamageMax = "x1.00";
        String weaponProgressMax = "[   ]";
        
        // Next level preview values
        String nextWeaponName = "No weapon";
        String nextWeaponLevel = "N/A";
        String nextWeaponDamage = "x1.00";
        String nextWeaponProgress = "[   ]";
        
        boolean isMaxLevel = false;

        if (heldItem != null) {
            String weaponId = heldItem.getItemId();

            // Get weapon level from ReforgeEquip
            int level = ReforgeEquip.getLevelFromWeaponId(weaponId);
            isMaxLevel = (level >= 3);

            // Get weapon name (the ID)
            weaponName = weaponId != null ? weaponId : "Unknown";
            weaponNameMax = weaponId != null ? weaponId : "Unknown";

            // Get upgrade name and level
            String upgradeName = ReforgeEquip.getUpgradeName(level);
            if (level > 0) {
                weaponLevel = upgradeName + " +" + level;
                weaponLevelMax = upgradeName + " +" + level;
            } else {
                weaponLevel = "Base (Upgradeable)";
                weaponLevelMax = "Base";
            }

            // Get damage multiplier for current level
            double damageMultiplier = ReforgeEquip.getDamageMultiplier(level);
            weaponDamage = "x" + String.format("%.2f", damageMultiplier);
            weaponDamageMax = "x" + String.format("%.2f", damageMultiplier);

            // Get progress bar with next level info
            if (level < 3) {
                double nextDamageMultiplier = ReforgeEquip.getDamageMultiplier(level + 1);
                double damageIncrease = nextDamageMultiplier - damageMultiplier;
                double damageIncreasePercent = (damageIncrease / damageMultiplier) * 100;
                weaponProgress = ReforgeEquip.createProgressBar(level, 3) + " (" + level + "/3)";
                nextLevelDamage = "→ x" + String.format("%.2f", damageMultiplier) + " (+" + String.format("%.0f", damageIncreasePercent) + "%)";
                weaponProgressMax = ReforgeEquip.createProgressBar(level, 3) + " (MAX)";
                
                // Calculate NEXT level preview data (level + 1)
                int nextLevel = level + 1;
                String nextUpgradeName = ReforgeEquip.getUpgradeName(nextLevel);
                double nextDamageMult = ReforgeEquip.getDamageMultiplier(nextLevel);
                
                // Predict next weapon ID (append level suffix)
                if (nextLevel > 0) {
                    // Try to find the base name and append the next level suffix
                    // Common patterns: Weapon_Axe_Cobalt1 -> Weapon_Axe_Cobalt2
                    String baseName = weaponId;
                    // Remove any existing number suffix
                    if (baseName.matches(".*\\d$")) {
                        baseName = baseName.substring(0, baseName.length() - 1);
                    }
                    nextWeaponName = baseName + nextLevel;
                } else {
                    nextWeaponName = weaponId;
                }
                
                nextWeaponLevel = nextUpgradeName + " +" + nextLevel;
                nextWeaponDamage = "x" + String.format("%.2f", nextDamageMult);
                nextWeaponProgress = ReforgeEquip.createProgressBar(nextLevel, 3) + " (" + nextLevel + "/3)";
            } else {
                weaponProgress = "+++ MAX";
                nextLevelDamage = "MAX";
                weaponProgressMax = "MAX LEVEL REACHED!";
            }

            // Get upgrade chances
            if (level < 3) {
                // Break chance for next upgrade
                double[] breakChances = {0.01, 0.05, 0.10};
                double breakChanceValue = breakChances[level];
                breakChance = String.format("%.0f%%", breakChanceValue * 100);

                // Upgrade weights: {degrade, same, upgrade, jackpot}
                double[][] reforgeWeights = {
                    {0.00, 0.65, 0.34, 0.01},   // 0 → 1
                    {0.35, 0.45, 0.19, 0.01},   // 1 → 2
                    {0.60, 0.30, 0.095, 0.005}, // 2 → 3
                };
                double[] weights = reforgeWeights[level];
                
                // Degrade chance (1st element)
                if (level > 0) {
                    degradeChance = String.format("%.0f%%", weights[0] * 100);
                } else {
                    degradeChance = "0%";
                }
                
                upgradeChance = String.format("%.0f%%", weights[2] * 100);  // 3rd element is upgrade
                jackpotChance = String.format("%.1f%%", weights[3] * 100); // 4th element is jackpot
            } else {
                breakChance = "MAX";
                upgradeChance = "MAX";
                jackpotChance = "MAX";
                degradeChance = "MAX";
            }
        }

        // Toggle visibility based on level
        // Show WeaponStatsBase and WeaponStatsNext groups when not max level
        uiCommandBuilder.set("#WeaponStatsBase.Visible", !isMaxLevel);
        uiCommandBuilder.set("#WeaponStatsNext.Visible", !isMaxLevel);
        // Show WeaponStatsMax group when at max level
        uiCommandBuilder.set("#WeaponStatsMax.Visible", isMaxLevel);

        // Set UI elements for non-max level (Group #WeaponStats)
        if (!isMaxLevel) {
            uiCommandBuilder.set("#WeaponName.TextSpans",  Message.raw("Name: " + getItemNameFromId(weaponName)));
            uiCommandBuilder.set("#WeaponLevel.TextSpans", Message.raw("Level: " + weaponLevel));
            uiCommandBuilder.set("#WeaponDamage.TextSpans", Message.raw("Damage: " + weaponDamage));
            uiCommandBuilder.set("#WeaponProgress.TextSpans", Message.raw("Progress: " + weaponProgress));
            uiCommandBuilder.set("#NextLevelDamage.TextSpans", Message.raw("Next: " + nextLevelDamage));
            uiCommandBuilder.set("#DegradeChance.TextSpans", Message.raw("Degrade: " + degradeChance));
            uiCommandBuilder.set("#BreakChance.TextSpans", Message.raw("Break: " + breakChance));
            uiCommandBuilder.set("#UpgradeChance.TextSpans", Message.raw("Upgrade: " + upgradeChance));
            uiCommandBuilder.set("#JackpotChance.TextSpans", Message.raw("Jackpot: " + jackpotChance));
            
            // Set next weapon preview (Group #WeaponStatsNext)
            uiCommandBuilder.set("#NextWeaponName.TextSpans", Message.raw("Next: " + getItemNameFromId(nextWeaponName)));
            uiCommandBuilder.set("#NextWeaponLevel.TextSpans", Message.raw("Level: " + nextWeaponLevel));
            uiCommandBuilder.set("#NewWeaponDamage.TextSpans", Message.raw("Damage: " + nextWeaponDamage));
            uiCommandBuilder.set("#NextWeaponProgress.TextSpans", Message.raw("Progress: " + nextWeaponProgress));
        }
        
        // Set UI elements for max level (Group #WeaponStatsMax)
        if (isMaxLevel) {
            uiCommandBuilder.set("#WeaponNameMax.TextSpans", Message.raw("Name: " + getItemNameFromId(weaponNameMax)));
            uiCommandBuilder.set("#WeaponLevelMax.TextSpans", Message.raw(weaponLevelMax + " MAX"));
            uiCommandBuilder.set("#WeaponDamageMax.TextSpans", Message.raw("Damage: " + weaponDamageMax));
            uiCommandBuilder.set("#WeaponProgressMax.TextSpans", Message.raw("Progress: " + weaponProgressMax));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        super.handleDataEvent(ref, store, data);

        System.out.println("EVENT - WeaponName: " + data.weaponName);
        System.out.println("EVENT - WeaponLevel: " + data.weaponLevel);
        System.out.println("EVENT - WeaponDamage: " + data.weaponDamage);
        System.out.println("EVENT - WeaponProgress: " + data.weaponProgress);
        System.out.println("EVENT - NextLevelDamage: " + data.nextLevelDamage);
        System.out.println("EVENT - DegradeChance: " + data.degradeChance);
        System.out.println("EVENT - BreakChance: " + data.breakChance);
        System.out.println("EVENT - UpgradeChance: " + data.upgradeChance);
        System.out.println("EVENT - JackpotChance: " + data.jackpotChance);
        System.out.println("EVENT - WeaponNameMax: " + data.weaponNameMax);
        System.out.println("EVENT - WeaponLevelMax: " + data.weaponLevelMax);
        System.out.println("EVENT - WeaponDamageMax: " + data.weaponDamageMax);
        System.out.println("EVENT - WeaponProgressMax: " + data.weaponProgressMax);

        sendUpdate();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("#WeaponName.TextSpans", Codec.STRING), (data, value) -> data.weaponName = value, data -> data.weaponName).add()
                .append(new KeyedCodec<>("#WeaponLevel.TextSpans", Codec.STRING), (data, value) -> data.weaponLevel = value, data -> data.weaponLevel).add()
                .append(new KeyedCodec<>("#WeaponDamage.TextSpans", Codec.STRING), (data, value) -> data.weaponDamage = value, data -> data.weaponDamage).add()
                .append(new KeyedCodec<>("#WeaponProgress.TextSpans", Codec.STRING), (data, value) -> data.weaponProgress = value, data -> data.weaponProgress).add()
                .append(new KeyedCodec<>("#NextLevelDamage.TextSpans", Codec.STRING), (data, value) -> data.nextLevelDamage = value, data -> data.nextLevelDamage).add()
                .append(new KeyedCodec<>("#DegradeChance.TextSpans", Codec.STRING), (data, value) -> data.degradeChance = value, data -> data.degradeChance).add()
                .append(new KeyedCodec<>("#BreakChance.TextSpans", Codec.STRING), (data, value) -> data.breakChance = value, data -> data.breakChance).add()
                .append(new KeyedCodec<>("#UpgradeChance.TextSpans", Codec.STRING), (data, value) -> data.upgradeChance = value, data -> data.upgradeChance).add()
                .append(new KeyedCodec<>("#JackpotChance.TextSpans", Codec.STRING), (data, value) -> data.jackpotChance = value, data -> data.jackpotChance).add()
                .append(new KeyedCodec<>("#WeaponNameMax.TextSpans", Codec.STRING), (data, value) -> data.weaponNameMax = value, data -> data.weaponNameMax).add()
                .append(new KeyedCodec<>("#WeaponLevelMax.TextSpans", Codec.STRING), (data, value) -> data.weaponLevelMax = value, data -> data.weaponLevelMax).add()
                .append(new KeyedCodec<>("#WeaponDamageMax.TextSpans", Codec.STRING), (data, value) -> data.weaponDamageMax = value, data -> data.weaponDamageMax).add()
                .append(new KeyedCodec<>("#WeaponProgressMax.TextSpans", Codec.STRING), (data, value) -> data.weaponProgressMax = value, data -> data.weaponProgressMax).add()
                .build();

        private String weaponName;
        private String weaponLevel;
        private String weaponDamage;
        private String weaponProgress;
        private String nextLevelDamage;
        private String degradeChance;
        private String breakChance;
        private String upgradeChance;
        private String jackpotChance;
        private String weaponNameMax;
        private String weaponLevelMax;
        private String weaponDamageMax;
        private String weaponProgressMax;
    }

    private String getItemNameFromId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "Unknown Item";

        // Split by underscore: ["Weapon", "Axe", "Cobalt1"]
        String[] parts = itemId.split("_");
        if (parts.length < 3) return itemId; // fallback to raw ID

        String type = parts[1];                              // "Axe"
        String materialRaw = parts[2].replaceAll("\\d+$", ""); // "Cobalt1" -> "Cobalt"
        String levelSuffix = parts[2].replaceAll("\\D+", "");  // FIX 2: "Cobalt1" -> "1" (strip all non-digits)

        // Add + prefix to level if > 0
        if (!levelSuffix.isEmpty() && Integer.parseInt(levelSuffix) > 0) {
            return materialRaw + " " + type + " +" + levelSuffix; // "Cobalt Axe +1"
        }
        
        return materialRaw + " " + type; // "Cobalt Axe"
    }

}
