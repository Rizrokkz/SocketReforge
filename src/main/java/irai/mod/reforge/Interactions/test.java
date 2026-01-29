package irai.mod.reforge.Interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Map;

@SuppressWarnings("removal")
public class test extends SimpleInteraction {
    public static final BuilderCodec<ReforgeEquip> CODEC =
            BuilderCodec.builder(ReforgeEquip.class, () -> new ReforgeEquip(), SimpleInteraction.CODEC).build();
    @Override
    protected void tick0(boolean firstRun,
                         float time,
                         @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun || type != InteractionType.Use) return;

        // ── Player ─────────────────────────────
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        Store<EntityStore> store = owningEntity.getStore();
        Player player = store.getComponent(owningEntity, Player.getComponentType());
        if (player == null) return;
        World world = player.getWorld();
        if (world == null) return;

        // ── Held item ──────────────────────────
        ItemStack heldItem = context.getHeldItem(); //getheld Item
        if (heldItem == null) return;
        String scannedheldItemId = heldItem.getItemId(); //getheld ID
        // ── Max tier check ────────────────────

        Map<String, String[]> upgradeMap = Map.ofEntries(
                Map.entry("Weapon_Sword_Crude",  new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Crude1"}),
                Map.entry("Weapon_Sword_Crude1", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Crude2"}),
                Map.entry("Weapon_Sword_Crude2", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Crude3"}),
                Map.entry("Weapon_Sword_Iron",  new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Iron1"}),
                Map.entry("Weapon_Sword_Iron1", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Iron2"}),
                Map.entry("Weapon_Sword_Iron2", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Iron3"}),
                Map.entry("Weapon_Sword_Copper",  new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Copper1"}),
                Map.entry("Weapon_Sword_Copper1", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Copper2"}),
                Map.entry("Weapon_Sword_Copper2", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Copper3"}),
                Map.entry("Weapon_Sword_Cobalt",  new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Cobalt1"}),
                Map.entry("Weapon_Sword_Cobalt1", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Cobalt2"}),
                Map.entry("Weapon_Sword_Cobalt2", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Cobalt3"}),
                Map.entry("Weapon_Sword_Thorium",  new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Thorium1"}),
                Map.entry("Weapon_Sword_Thorium1", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Thorium2"}),
                Map.entry("Weapon_Sword_Thorium2", new String[]{scannedheldItemId,scannedheldItemId,"Weapon_Sword_Thorium3"})
        );
        String[] upgradeList = upgradeMap.get(scannedheldItemId);
        if (upgradeList == null) {
            player.sendMessage(Message.raw("Weapon already maxed or invalid"));
            return;
        }

        // ── Material requirement ───────────────
        final String materialId = "Ingredient_Bar_Iron";
        final int materialCost = 5;


        if (!hasEnoughInInventoryOrHotbar(player, materialId, materialCost)) {
            player.sendMessage(Message.raw("Not enough Iron Bars"));
            return;
        }
        else{
            // ── Weighted reforge roll ──────────────

            String newItemId = rollWeightedItem(
                    upgradeList,
                    new double[]{0.5, 0.3, 0.2}
            );
            player.sendMessage(Message.raw(
                    "Reforging" + newItemId));
            ItemStack reforgedSword = new ItemStack(
                    newItemId,
                    1,
                    heldItem.getDurability(),
                    heldItem.getMaxDurability(),
                    null
            );

            // ── Consume materials (exactly once) ───
            consumeFromInventoryOrHotbar(player, materialId, materialCost);

            // ── Replace held item ──────────────────
            short slot = context.getHeldItemSlot();

            player.getInventory().getHotbar()
                    .removeItemStackFromSlot(slot, 1, false, false);

            player.getInventory().getHotbar()
                    .addItemStackToSlot(slot, reforgedSword);

            player.sendMessage(Message.raw(
                    "Reforged " + reforgedSword.getItem().getTranslationKey()
            ));
        }
// ── Target block (Reforgebench) ─────────
        BlockPosition target = context.getTargetBlock();
        if (target == null) return;
        Ref<ChunkStore> chunk = BlockModule.getBlockEntity(world, target.x, target.y, target.z);
        BlockState state = BlockState.getBlockState(chunk, chunk.getStore());
        if (!state.getBlockType().getId().equals("Reforgebench")) return;
    }

    private boolean consumeFromInventoryOrHotbar(Player player, String itemId, int amount)
    {
        // 1️⃣ Try storage first
        var storage = player.getInventory().getStorage();
        for (short slot = 0; slot < storage.getCapacity(); slot++) {
            ItemStack stack = storage.getItemStack(slot);
            if (stack == null) continue;

            if (stack.getItemId().equalsIgnoreCase(itemId)
                    && stack.getQuantity() >= amount) {

                storage.removeItemStackFromSlot(
                        slot,
                        amount,
                        false, // NOT simulate
                        false
                );
                return true;
            }
        }

        // 2️⃣ Try hotbar
        var hotbar = player.getInventory().getHotbar();
        for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
            ItemStack stack = hotbar.getItemStack(slot);
            if (stack == null) continue;

            if (stack.getItemId().equalsIgnoreCase(itemId)
                    && stack.getQuantity() >= amount) {

                hotbar.removeItemStackFromSlot(
                        slot,
                        amount,
                        false, // NOT simulate
                        false
                );
                return true;
            }
        }

        // 3️⃣ Not found
        return false;
    }

    private boolean hasEnoughInInventoryOrHotbar(Player player, String itemId, int amount) {
        int found = 0;

        var storage = player.getInventory().getStorage();
        for (short i = 0; i < storage.getCapacity(); i++) {
            ItemStack s = storage.getItemStack(i);
            if (s != null && s.getItemId().equalsIgnoreCase(itemId)) {
                found += s.getQuantity();
                if (found >= amount) return true;
            }
        }

        var hotbar = player.getInventory().getHotbar();
        for (short i = 0; i < hotbar.getCapacity(); i++) {
            ItemStack s = hotbar.getItemStack(i);
            if (s != null && s.getItemId().equalsIgnoreCase(itemId)) {
                found += s.getQuantity();
                if (found >= amount) return true;
            }
        }

        return false;
    }

    private String rollWeightedItem(String[] items, double[] weights) {
        double r = Math.random();
        double cumulative = 0;

        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) {
                return items[i];
            }
        }
        return items[items.length - 1];
    }

}
