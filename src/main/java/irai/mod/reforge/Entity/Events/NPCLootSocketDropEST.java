package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;

/**
 * Replaces default NPC death drop generation so loot-table equipment uses
 * world-loot socket rolling before item entities are spawned.
 */
@SuppressWarnings("removal")
public final class NPCLootSocketDropEST extends DeathSystems.OnDeathSystem {
    @Override
    public Query<EntityStore> getQuery() {
        // Avoid static/init-time component type lookups that can be null during plugin bootstrap.
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        // Run first, then disable the built-in drop pass to avoid duplicates.
        return Set.of(new SystemDependency<>(Order.BEFORE, NPCDamageSystems.DropDeathItems.class));
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref,
                                 DeathComponent deathComponent,
                                 Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || deathComponent == null || store == null || commandBuffer == null) {
            return;
        }
        if (deathComponent.getItemsLossMode() != DeathConfig.ItemsLossMode.ALL) {
            return;
        }

        var npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return;
        }
        NPCEntity npc = commandBuffer.getComponent(ref, npcType);
        if (npc == null) {
            return;
        }
        Role role = npc.getRole();
        if (role == null) {
            return;
        }

        List<ItemStack> drops = collectDrops(npc, role);
        if (drops.isEmpty()) {
            return;
        }

        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            drops.set(i, LootSocketRoller.maybeSocketizeLootStack(stack, LootSocketRoller.LootSource.NPC_DROP));
        }

        var transformType = TransformComponent.getComponentType();
        var headRotationType = HeadRotation.getComponentType();
        if (transformType == null || headRotationType == null) {
            return;
        }
        TransformComponent transform = store.getComponent(ref, transformType);
        HeadRotation headRotation = store.getComponent(ref, headRotationType);
        if (transform == null || headRotation == null || transform.getPosition() == null || headRotation.getRotation() == null) {
            return;
        }

        Vector3d dropPosition = transform.getPosition().clone().add(0.0d, 1.0d, 0.0d);
        Vector3f dropRotation = headRotation.getRotation().clone();
        var holders = ItemComponent.generateItemDrops(store, drops, dropPosition, dropRotation);
        if (holders != null && holders.length > 0) {
            commandBuffer.addEntities(holders, AddReason.SPAWN);
        }

        // Prevent the engine NPC drop system from also emitting the unmodified list.
        deathComponent.setItemsLossMode(DeathConfig.ItemsLossMode.NONE);
    }

    private static List<ItemStack> collectDrops(NPCEntity npc, Role role) {
        List<ItemStack> drops = new ArrayList<>();

        if (role.isPickupDropOnDeath()) {
            Inventory inventory = npc.getInventory();
            if (inventory != null && inventory.getStorage() != null) {
                drops.addAll(inventory.getStorage().dropAllItemStacks());
            }
        }

        String dropListId = role.getDropListId();
        if (dropListId == null || dropListId.isBlank()) {
            return drops;
        }

        ItemModule itemModule = ItemModule.get();
        if (itemModule == null || !itemModule.isEnabled()) {
            return drops;
        }

        List<ItemStack> rolledDrops = itemModule.getRandomItemDrops(dropListId);
        if (rolledDrops != null && !rolledDrops.isEmpty()) {
            drops.addAll(rolledDrops);
        }
        return drops;
    }
}
