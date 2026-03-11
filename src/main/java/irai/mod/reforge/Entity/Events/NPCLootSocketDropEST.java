package irai.mod.reforge.Entity.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.NPCDamageSystems;

import irai.mod.reforge.Common.LootInjectionUtils;
import irai.mod.reforge.Config.LootSocketRollConfig;
/**
 * Replaces default NPC death drop generation so loot-table equipment uses
 * world-loot socket rolling before item entities are spawned.
 */
@SuppressWarnings("removal")
public final class NPCLootSocketDropEST extends DeathSystems.OnDeathSystem {
    private static volatile List<LootInjectionUtils.LootInjectionRule> npcWaterEssenceRules = List.of(
            LootInjectionUtils.rule("Ingredient_Water_Essence", 0.05d, 1, 5)
    );
    private static volatile List<LootInjectionUtils.LootInjectionRule> npcLightningEssenceRules = List.of(
            LootInjectionUtils.rule("Ingredient_Lightning_Essence", 0.05d, 1, 5)
    );
    private static volatile int npcWaterEssenceMinQuantity = 1;
    private static volatile int npcWaterEssenceMaxQuantity = 5;
    private static volatile int npcLightningEssenceMinQuantity = 1;
    private static volatile int npcLightningEssenceMaxQuantity = 5;
    private static final String[] FLYING_ROLE_HINTS = {
            "bee",
            "pter",
            "pterodactyl",
            "pteranodon",
            "bat",
            "bird",
            "eagle",
            "hawk",
            "vulture",
            "gull",
            "raven",
            "crow",
            "dragon",
            "wyvern",
            "moth",
            "wasp",
            "hornet",
            "mosquito",
            "butterfly",
            "scarak"
    };

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

        long worldSeed = resolveWorldSeed(npc);
        long seedBase = mixSeed(worldSeed, seedFromUuid(npc.getUuid()));
        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            long seed = mixSeed(seedBase, i);
            drops.set(i, LootSocketRoller.maybeSocketizeLootStack(stack, LootSocketRoller.LootSource.NPC_DROP, seed));
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

        if (isAquaticRole(role)) {
            LootInjectionUtils.injectByRules(drops, npcWaterEssenceRules);
        }
        if (isFlyingRole(role)) {
            LootInjectionUtils.injectByRules(drops, npcLightningEssenceRules);
        }
        return drops;
    }

    public static void setConfig(LootSocketRollConfig config) {
        if (config == null) {
            return;
        }
        double waterChance = clamp01(config.getNpcWaterEssenceChance());
        double lightningChance = clamp01(config.getNpcLightningEssenceChance());
        npcWaterEssenceMinQuantity = Math.max(0, config.getNpcWaterEssenceMinQuantity());
        npcWaterEssenceMaxQuantity = Math.max(npcWaterEssenceMinQuantity, config.getNpcWaterEssenceMaxQuantity());
        npcLightningEssenceMinQuantity = Math.max(0, config.getNpcLightningEssenceMinQuantity());
        npcLightningEssenceMaxQuantity = Math.max(npcLightningEssenceMinQuantity, config.getNpcLightningEssenceMaxQuantity());
        npcWaterEssenceRules = waterChance <= 0.0d
                ? List.of()
                : List.of(LootInjectionUtils.rule("Ingredient_Water_Essence", waterChance, npcWaterEssenceMinQuantity, npcWaterEssenceMaxQuantity));
        npcLightningEssenceRules = lightningChance <= 0.0d
                ? List.of()
                : List.of(LootInjectionUtils.rule("Ingredient_Lightning_Essence", lightningChance, npcLightningEssenceMinQuantity, npcLightningEssenceMaxQuantity));
    }

    private static boolean isAquaticRole(Role role) {
        if (role == null) {
            return false;
        }
        return role.isBreathesInWater();
    }

    private static boolean isFlyingRole(Role role) {
        if (role == null) {
            return false;
        }
        return matchesFlyingHint(role.getRoleName())
                || matchesFlyingHint(role.getAppearanceName())
                || matchesFlyingHint(role.getNameTranslationKey())
                || matchesFlyingHint(role.getDropListId())
                || matchesFlyingHint(role.getLabel());
    }

    private static boolean matchesFlyingHint(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        for (String hint : FLYING_ROLE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private static long resolveWorldSeed(NPCEntity npc) {
        World mainWorld = resolveMainWorld(npc);
        if (mainWorld == null) {
            return 0L;
        }
        long seed = 0L;
        WorldConfig config = mainWorld.getWorldConfig();
        if (config != null) {
            seed = config.getSeed();
        }
        String name = mainWorld.getName();
        if (name != null && !name.isBlank()) {
            seed = mixSeed(seed, name.hashCode());
        }
        return seed;
    }

    private static World resolveMainWorld(NPCEntity npc) {
        try {
            Universe universe = Universe.get();
            if (universe != null) {
                World defaultWorld = universe.getDefaultWorld();
                if (defaultWorld != null) {
                    return defaultWorld;
                }
            }
        } catch (Throwable ignored) {
        }
        if (npc != null) {
            return npc.getWorld();
        }
        return null;
    }

    private static long seedFromUuid(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
    }

    private static long mixSeed(long seed, long value) {
        return seed * 31L + value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
