package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("removal")
public class EquipmentRefineEST extends DamageEventSystem {

    // Weapon ID pattern - matches items with "Weapon" keyword
    private static final Pattern WEAPON_PATTERN = Pattern.compile("^(.*)Weapon(.*)$", Pattern.CASE_INSENSITIVE);

    // Upgrade level pattern - matches numeric suffix
    private static final Pattern UPGRADE_LEVEL_PATTERN = Pattern.compile("^(.+?)(\\d+)$");

    // Damage multipliers per upgrade level (0..3)
    private static final double[] DAMAGE_MULTIPLIERS = {
            1.0,   // base (no +)
            1.10,  // +1 (10% increase)
            1.15,  // +2 (15% increase)
            1.25   // +3 (25% increase)
    };

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {

        // Get target (entity receiving damage)
        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);

        // Get damage source
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();

        // Check if attacker is a player
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }

        String attackerName = attacker.getPlayerRef().getUsername();

        // Find weapon in attacker's hotbar
        ItemStack weapon = findWeaponInHotbar(attacker);
        if (weapon == null) {
            System.out.println("[DEBUG] " + attackerName + " has NO WEAPON in hotbar");
            return;
        }

        String itemId = weapon.getItemId();

        // ═══════════════════════════════════════════════════════════════════════
        // DETAILED DEBUG OUTPUT
        // ═══════════════════════════════════════════════════════════════════════
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("[DEBUG] WEAPON FOUND:");
        System.out.println("[DEBUG]   Item ID: '" + itemId + "'");
        System.out.println("[DEBUG]   Item ID Length: " + (itemId != null ? itemId.length() : "null"));
        System.out.println("[DEBUG]   Item ID Class: " + (itemId != null ? itemId.getClass().getName() : "null"));

        // Check if it matches weapon pattern
        boolean isWeapon = WEAPON_PATTERN.matcher(itemId).matches();
        System.out.println("[DEBUG]   Matches WEAPON_PATTERN: " + isWeapon);

        // Try to extract upgrade level with detailed logging
        int level = getUpgradeLevelDebug(itemId);
        System.out.println("[DEBUG]   Extracted Level: " + level);

        int clampedLevel = Math.max(0, Math.min(level, DAMAGE_MULTIPLIERS.length - 1));
        double multiplier = DAMAGE_MULTIPLIERS[clampedLevel];

        System.out.println("[DEBUG]   Clamped Level: " + clampedLevel);
        System.out.println("[DEBUG]   Multiplier: " + multiplier);

        // Store original damage
        float originalDamage = damage.getAmount();
        float newDamage = (float) (originalDamage * multiplier);

        System.out.println("[DEBUG]   Original Damage: " + originalDamage);
        System.out.println("[DEBUG]   New Damage: " + newDamage);
        System.out.println("═══════════════════════════════════════════════");

        // Apply multiplier
        damage.setAmount(newDamage);
    }

    /**
     * Debug version of getUpgradeLevel with extensive logging
     */
    private int getUpgradeLevelDebug(String itemId) {
        System.out.println("[DEBUG getUpgradeLevel] Input: '" + itemId + "'");

        if (itemId == null) {
            System.out.println("[DEBUG getUpgradeLevel] Item ID is NULL -> returning 0");
            return 0;
        }

        Matcher matcher = UPGRADE_LEVEL_PATTERN.matcher(itemId);
        System.out.println("[DEBUG getUpgradeLevel] Pattern: " + UPGRADE_LEVEL_PATTERN.pattern());
        System.out.println("[DEBUG getUpgradeLevel] Matches pattern: " + matcher.matches());

        // Reset matcher after matches() call (it consumes the match)
        matcher = UPGRADE_LEVEL_PATTERN.matcher(itemId);

        if (!matcher.matches()) {
            System.out.println("[DEBUG getUpgradeLevel] No numeric suffix found -> returning 0");
            return 0;
        }

        System.out.println("[DEBUG getUpgradeLevel] Group count: " + matcher.groupCount());
        System.out.println("[DEBUG getUpgradeLevel] Group 0 (full): '" + matcher.group(0) + "'");
        System.out.println("[DEBUG getUpgradeLevel] Group 1 (base): '" + matcher.group(1) + "'");
        System.out.println("[DEBUG getUpgradeLevel] Group 2 (number): '" + matcher.group(2) + "'");

        try {
            int level = Integer.parseInt(matcher.group(2));
            System.out.println("[DEBUG getUpgradeLevel] Parsed level: " + level);
            return level;
        } catch (NumberFormatException e) {
            System.out.println("[DEBUG getUpgradeLevel] NumberFormatException: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Looks in the player's hotbar for the first item whose ID contains "Weapon".
     */
    private ItemStack findWeaponInHotbar(Player player) {
        ItemContainer hotbar = player.getInventory().getHotbar();

        System.out.println("[DEBUG findWeaponInHotbar] Hotbar capacity: " + hotbar.getCapacity());

        // Try to get currently selected slot first
        try {
            short selectedSlot = player.getInventory().getActiveHotbarSlot();
            ItemStack selectedItem = hotbar.getItemStack(selectedSlot);

            System.out.println("[DEBUG findWeaponInHotbar] Selected slot: " + selectedSlot);
            System.out.println("[DEBUG findWeaponInHotbar] Selected item: " +
                    (selectedItem != null ? selectedItem.getItemId() : "null"));

            if (selectedItem != null && isWeapon(selectedItem.getItemId())) {
                System.out.println("[DEBUG findWeaponInHotbar] Found weapon in selected slot!");
                return selectedItem;
            }
        } catch (Exception e) {
            System.out.println("[DEBUG findWeaponInHotbar] Exception getting selected slot: " + e.getMessage());
        }

        // Fallback: search entire hotbar
        System.out.println("[DEBUG findWeaponInHotbar] Searching entire hotbar...");
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null) {
                continue;
            }

            String id = stack.getItemId();
            System.out.println("[DEBUG findWeaponInHotbar] Slot " + slot + ": " + id);

            if (isWeapon(id)) {
                System.out.println("[DEBUG findWeaponInHotbar] Found weapon in slot " + slot + "!");
                return stack;
            }
        }

        System.out.println("[DEBUG findWeaponInHotbar] No weapon found in hotbar");
        return null;
    }

    private boolean isWeapon(String itemId) {
        return itemId != null && WEAPON_PATTERN.matcher(itemId).matches();
    }

    /**
     * Simplified version for normal use (no debug output)
     */
    private int getUpgradeLevel(String itemId) {
        if (itemId == null) {
            return 0;
        }

        Matcher matcher = UPGRADE_LEVEL_PATTERN.matcher(itemId);
        if (!matcher.matches()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
