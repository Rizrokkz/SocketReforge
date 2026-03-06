package irai.mod.reforge.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import irai.mod.reforge.Interactions.ReforgeEquip;
import it.unimi.dsi.fastutil.Pair;

/**
 * Temporary test command:
 * Spawns NPCs in front of the player and keeps only ones whose loot-table
 * droplists can roll equipment.
 *
 * Usage:
 * /spawnequipenemy [count]
 */
@SuppressWarnings("removal")
public class SpawnEquipEnemyCommand extends CommandBase {
    private static final int FRONT_DISTANCE_BLOCKS = 5;
    private static final int LANE_SPACING_BLOCKS = 2;
    private static final int MAX_SPAWN_COUNT = 6;
    private static final int MAX_ROLE_ATTEMPTS_PER_SPAWN = 72;
    private static final int DROPLIST_SAMPLE_ROLLS = 20;

    private static final Map<String, Boolean> DROPLIST_EQUIPMENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ROLE_ELIGIBILITY_CACHE = new ConcurrentHashMap<>();

    private final OptionalArg<Integer> countArg;

    public SpawnEquipEnemyCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("speqenemy");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));
        this.countArg = this.withOptionalArg("count", "1-6", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }
        if (!CommandUtils.isOperator(player)) {
            context.sendMessage(Message.raw("OP only command."));
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            context.sendMessage(Message.raw("World is not available."));
            return;
        }

        Integer requestedCount = countArg.provided(context) ? countArg.get(context) : 1;
        int count = Math.max(1, Math.min(MAX_SPAWN_COUNT, requestedCount == null ? 1 : requestedCount));
        if (requestedCount != null && requestedCount != count) {
            context.sendMessage(Message.raw("Count clamped to " + count + "."));
        }

        world.execute(() -> spawnEligibleEnemies(context, player, count));
    }

    private static void spawnEligibleEnemies(CommandContext context, Player player, int count) {
        if (context == null || player == null || player.getWorld() == null) {
            return;
        }
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            context.sendMessage(Message.raw("NPC plugin is not available."));
            return;
        }
        ItemModule itemModule = ItemModule.get();
        if (itemModule == null || !itemModule.isEnabled()) {
            context.sendMessage(Message.raw("Item module is not available."));
            return;
        }

        TransformComponent transform = player.getTransformComponent();
        if (transform == null || transform.getPosition() == null) {
            context.sendMessage(Message.raw("Unable to read your position."));
            return;
        }

        List<String> roleTemplates = collectRoleTemplates(npcPlugin);
        if (roleTemplates.isEmpty()) {
            context.sendMessage(Message.raw("No spawnable NPC role templates were found."));
            return;
        }

        Store<EntityStore> store = player.getWorld().getEntityStore() != null
                ? player.getWorld().getEntityStore().getStore()
                : null;
        if (store == null) {
            context.sendMessage(Message.raw("Entity store is not available."));
            return;
        }

        Vector3d playerPos = transform.getPosition().clone();
        Vector3f playerRot = transform.getRotation() != null ? transform.getRotation().clone() : new Vector3f(0f, 0f, 0f);

        int spawned = 0;
        List<String> spawnedDetails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vector3d spawnPos = getSpawnPositionInFront(playerPos, playerRot, i, count);
            SpawnedEnemy spawnedEnemy = spawnEligibleEnemy(store, npcPlugin, roleTemplates, spawnPos, playerRot);
            if (spawnedEnemy == null) {
                continue;
            }
            spawned++;
            spawnedDetails.add(spawnedEnemy.roleName + " [" + spawnedEnemy.dropListId + "]");
        }

        if (spawned <= 0) {
            context.sendMessage(Message.raw(
                    "No equipment-eligible enemies were found after " + MAX_ROLE_ATTEMPTS_PER_SPAWN + " role attempts."));
            return;
        }

        context.sendMessage(Message.raw(
                "Spawned " + spawned + "/" + count + " equipment-eligible enemies: " + String.join(", ", spawnedDetails)));
    }

    private static SpawnedEnemy spawnEligibleEnemy(Store<EntityStore> store,
                                                   NPCPlugin npcPlugin,
                                                   List<String> roleTemplates,
                                                   Vector3d position,
                                                   Vector3f rotation) {
        if (store == null || npcPlugin == null || roleTemplates == null || roleTemplates.isEmpty() || position == null || rotation == null) {
            return null;
        }

        int attempts = Math.min(MAX_ROLE_ATTEMPTS_PER_SPAWN, roleTemplates.size());
        for (int attempt = 0; attempt < attempts; attempt++) {
            String roleName = roleTemplates.get(ThreadLocalRandom.current().nextInt(roleTemplates.size()));
            if (roleName == null || roleName.isBlank()) {
                continue;
            }

            Boolean cachedEligibility = ROLE_ELIGIBILITY_CACHE.get(roleName);
            if (cachedEligibility != null && !cachedEligibility) {
                continue;
            }

            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> pair;
            try {
                pair = npcPlugin.spawnNPC(store, roleName, null, position.clone(), rotation.clone());
            } catch (Throwable ignored) {
                continue;
            }
            if (pair == null || pair.first() == null || !(pair.second() instanceof NPCEntity npc)) {
                continue;
            }

            String dropListId = resolveDropListId(npc);
            boolean eligible = dropListId != null && !dropListId.isBlank() && isEquipmentDroplist(dropListId);
            ROLE_ELIGIBILITY_CACHE.put(roleName, eligible);
            if (eligible) {
                return new SpawnedEnemy(roleName, dropListId);
            }

            try {
                store.removeEntity(pair.first(), RemoveReason.UNLOAD);
            } catch (Throwable ignored) {
                // Best effort for temporary test command.
            }
        }

        return null;
    }

    private static String resolveDropListId(NPCEntity npc) {
        if (npc == null || npc.getRole() == null) {
            return null;
        }
        String dropListId = npc.getRole().getDropListId();
        if (dropListId == null || dropListId.isBlank()) {
            return null;
        }
        return dropListId;
    }

    private static boolean isEquipmentDroplist(String dropListId) {
        if (dropListId == null || dropListId.isBlank()) {
            return false;
        }
        Boolean cached = DROPLIST_EQUIPMENT_CACHE.get(dropListId);
        if (cached != null) {
            return cached;
        }

        ItemModule itemModule = ItemModule.get();
        if (itemModule == null || !itemModule.isEnabled()) {
            DROPLIST_EQUIPMENT_CACHE.put(dropListId, false);
            return false;
        }

        boolean hasEquipment = false;
        for (int i = 0; i < DROPLIST_SAMPLE_ROLLS; i++) {
            List<ItemStack> drops = itemModule.getRandomItemDrops(dropListId);
            if (containsEquipment(drops)) {
                hasEquipment = true;
                break;
            }
        }
        DROPLIST_EQUIPMENT_CACHE.put(dropListId, hasEquipment);
        return hasEquipment;
    }

    private static boolean containsEquipment(List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return false;
        }
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (ReforgeEquip.isWeapon(stack) || ReforgeEquip.isArmor(stack)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> collectRoleTemplates(NPCPlugin npcPlugin) {
        if (npcPlugin == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>(npcPlugin.getRoleTemplateNames(true));
        if (roles.isEmpty()) {
            roles = new ArrayList<>(npcPlugin.getRoleTemplateNames(false));
        }
        roles.removeIf(role -> role == null || role.isBlank());
        return roles;
    }

    private static Vector3d getSpawnPositionInFront(Vector3d playerPos, Vector3f playerRot, int index, int total) {
        double yawRad = Math.toRadians(playerRot.getYaw());
        int forwardX = (int) Math.round(-Math.sin(yawRad));
        int forwardZ = (int) Math.round(Math.cos(yawRad));
        if (forwardX == 0 && forwardZ == 0) {
            forwardZ = 1;
        }
        int sideX = -forwardZ;
        int sideZ = forwardX;

        int baseX = (int) Math.floor(playerPos.getX()) + (forwardX * FRONT_DISTANCE_BLOCKS);
        int baseY = (int) Math.floor(playerPos.getY());
        int baseZ = (int) Math.floor(playerPos.getZ()) + (forwardZ * FRONT_DISTANCE_BLOCKS);

        double centerOffset = (index - ((total - 1) / 2.0d)) * LANE_SPACING_BLOCKS;
        int x = baseX + (int) Math.round(sideX * centerOffset);
        int z = baseZ + (int) Math.round(sideZ * centerOffset);
        return new Vector3d(x + 0.5d, baseY + 0.1d, z + 0.5d);
    }

    private static final class SpawnedEnemy {
        private final String roleName;
        private final String dropListId;

        private SpawnedEnemy(String roleName, String dropListId) {
            this.roleName = roleName == null ? "unknown" : roleName;
            this.dropListId = dropListId == null ? "" : dropListId;
        }
    }
}
