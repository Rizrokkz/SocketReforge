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

import irai.mod.reforge.Commands.CommandUtils;
import irai.mod.reforge.Interactions.ReforgeEquip;

/**
 * Weapon stats page that only targets selectors present in WeaponStatHUD.ui.
 */
public class WeaponStatsUI extends InteractiveCustomUIPage<WeaponStatsUI.Data> {

    public WeaponStatsUI(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("WeaponStatHUD.ui");

        Player player = store.getComponent(ref, Player.getComponentType());
        ItemStack heldItem = player != null
                ? player.getInventory().getHotbar().getItemStack(player.getInventory().getActiveHotbarSlot())
                : null;

        String weaponName = "No weapon";
        String weaponLevel = "N/A";
        String weaponDamage = "x1.00";
        String weaponProgress = "[   ]";

        String nextWeaponName = "No weapon";
        String nextWeaponLevel = "N/A";
        String nextWeaponDamage = "x1.00";

        String weaponNameMax = "No weapon";
        String weaponLevelMax = "N/A";
        String weaponDamageMax = "x1.00";

        boolean isMaxLevel = false;

        if (heldItem != null && !heldItem.isEmpty()) {
            String weaponId = heldItem.getItemId();
            int level = ReforgeEquip.getLevelFromItem(heldItem);
            isMaxLevel = (level >= 3);

            // Get the proper display name from the item's metadata/properties
            weaponName = getItemDisplayName(heldItem, level);
            weaponNameMax = weaponName;

            String upgradeName = ReforgeEquip.getUpgradeName(level);
            weaponLevel = level > 0 ? (upgradeName + " +" + level) : "Base (Upgradeable)";
            weaponLevelMax = level > 0 ? (upgradeName + " +" + level) : "Base";

            double damageMultiplier = ReforgeEquip.getDamageMultiplier(level);
            weaponDamage = "x" + String.format("%.2f", damageMultiplier);
            weaponDamageMax = weaponDamage;

            if (level < 3) {
                double nextDamageMultiplier = ReforgeEquip.getDamageMultiplier(level + 1);
                double damageIncreasePct = ((nextDamageMultiplier - damageMultiplier) / damageMultiplier) * 100.0;
                weaponProgress = ReforgeEquip.createProgressBar(level, 3) + " (" + level + "/3) -> +" + String.format("%.0f", damageIncreasePct) + "%";

                int nextLevel = level + 1;
                nextWeaponName = getItemDisplayName(heldItem, nextLevel);
                nextWeaponLevel = ReforgeEquip.getUpgradeName(nextLevel) + " +" + nextLevel;
                nextWeaponDamage = "x" + String.format("%.2f", nextDamageMultiplier);
            } else {
                weaponProgress = "+++ MAX";
            }
        }

        uiCommandBuilder.set("#WeaponStatsBase.Visible", !isMaxLevel);
        uiCommandBuilder.set("#WeaponStatsNext.Visible", !isMaxLevel);
        uiCommandBuilder.set("#WeaponStatsMax.Visible", isMaxLevel);

        if (!isMaxLevel) {
            uiCommandBuilder.set("#WeaponName.TextSpans", Message.raw("Name: " + weaponName));
            uiCommandBuilder.set("#WeaponLevel.TextSpans", Message.raw("Level: " + weaponLevel));
            uiCommandBuilder.set("#WeaponDamage.TextSpans", Message.raw("Damage: " + weaponDamage));
            uiCommandBuilder.set("#WeaponProgress.TextSpans", Message.raw("Progress: " + weaponProgress));

            uiCommandBuilder.set("#NextWeaponName.TextSpans", Message.raw("Next: " + nextWeaponName));
            uiCommandBuilder.set("#NextWeaponLevel.TextSpans", Message.raw("Level: " + nextWeaponLevel));
            uiCommandBuilder.set("#NewWeaponDamage.TextSpans", Message.raw("Damage: " + nextWeaponDamage));
        } else {
            uiCommandBuilder.set("#WeaponNameMax.TextSpans", Message.raw("Name: " + weaponNameMax));
            uiCommandBuilder.set("#WeaponLevelMax.TextSpans", Message.raw(weaponLevelMax + " MAX"));
            uiCommandBuilder.set("#WeaponDamageMax.TextSpans", Message.raw("Damage: " + weaponDamageMax));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        super.handleDataEvent(ref, store, data);
        sendUpdate();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("#WeaponName.TextSpans", Codec.STRING), (data, value) -> data.weaponName = value, data -> data.weaponName).add()
                .append(new KeyedCodec<>("#WeaponLevel.TextSpans", Codec.STRING), (data, value) -> data.weaponLevel = value, data -> data.weaponLevel).add()
                .append(new KeyedCodec<>("#WeaponDamage.TextSpans", Codec.STRING), (data, value) -> data.weaponDamage = value, data -> data.weaponDamage).add()
                .append(new KeyedCodec<>("#WeaponProgress.TextSpans", Codec.STRING), (data, value) -> data.weaponProgress = value, data -> data.weaponProgress).add()
                .append(new KeyedCodec<>("#NextWeaponName.TextSpans", Codec.STRING), (data, value) -> data.nextWeaponName = value, data -> data.nextWeaponName).add()
                .append(new KeyedCodec<>("#NextWeaponLevel.TextSpans", Codec.STRING), (data, value) -> data.nextWeaponLevel = value, data -> data.nextWeaponLevel).add()
                .append(new KeyedCodec<>("#NewWeaponDamage.TextSpans", Codec.STRING), (data, value) -> data.newWeaponDamage = value, data -> data.newWeaponDamage).add()
                .append(new KeyedCodec<>("#WeaponNameMax.TextSpans", Codec.STRING), (data, value) -> data.weaponNameMax = value, data -> data.weaponNameMax).add()
                .append(new KeyedCodec<>("#WeaponLevelMax.TextSpans", Codec.STRING), (data, value) -> data.weaponLevelMax = value, data -> data.weaponLevelMax).add()
                .append(new KeyedCodec<>("#WeaponDamageMax.TextSpans", Codec.STRING), (data, value) -> data.weaponDamageMax = value, data -> data.weaponDamageMax).add()
                .build();

        private String weaponName;
        private String weaponLevel;
        private String weaponDamage;
        private String weaponProgress;
        private String nextWeaponName;
        private String nextWeaponLevel;
        private String newWeaponDamage;
        private String weaponNameMax;
        private String weaponLevelMax;
        private String weaponDamageMax;
    }

    /**
     * Gets the display name from the item's metadata/properties.
     * Falls back to parsing the item ID if the display name is not available.
     */
    private String getItemDisplayName(ItemStack item, int refinementLevel) {
        if (item == null || item.isEmpty()) return "Unknown Item";
        
        // First check if we have a stored display name in refinement metadata
        String metadataName = ReforgeEquip.getDisplayNameFromMetadata(item);
        if (metadataName != null && !metadataName.isEmpty()) {
            return metadataName;
        }
        
        // Try to get the proper display name from the item's translation properties
        String displayName = CommandUtils.getItemDisplayName(item);
        
        if (displayName != null && !displayName.isEmpty() && !displayName.equals(item.getItemId())) {
            // We got a proper display name from translation properties
            if (refinementLevel > 0) {
                return displayName + " +" + refinementLevel;
            }
            return displayName;
        }
        
        // Fallback: parse the item ID
        String itemId = item.getItemId();
        if (itemId == null || itemId.isEmpty()) return "Unknown Item";

        String[] parts = itemId.split("_");
        if (parts.length < 3) {
            if (refinementLevel > 0) {
                return itemId + " +" + refinementLevel;
            }
            return itemId;
        }

        String type = parts[1];
        String materialRaw = parts[2].replaceAll("\\d+$", "");
        if (refinementLevel > 0) {
            return materialRaw + " " + type + " +" + refinementLevel;
        }
        return materialRaw + " " + type;
    }
    
    /**
     * Legacy method for backwards compatibility.
     * @deprecated Use {@link #getItemDisplayName(ItemStack, int)} instead.
     */
    @Deprecated
    private String getItemNameFromId(String itemId, int refinementLevel) {
        if (itemId == null || itemId.isEmpty()) return "Unknown Item";

        String[] parts = itemId.split("_");
        if (parts.length < 3) return itemId;

        String type = parts[1];
        String materialRaw = parts[2].replaceAll("\\d+$", "");
        if (refinementLevel > 0) {
            return materialRaw + " " + type + " +" + refinementLevel;
        }
        return materialRaw + " " + type;
    }
}
