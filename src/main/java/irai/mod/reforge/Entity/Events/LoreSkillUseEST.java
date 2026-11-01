package irai.mod.reforge.Entity.Events;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Interactions.CoreAbilityRune;
import irai.mod.reforge.Lore.LoreHeldItemUpdateManager;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;

/** Applies lore socket procs when a weapon ability interaction starts. */
public final class LoreSkillUseEST extends EntityEventSystem<EntityStore, InteractionChainStartEvent> {
    public LoreSkillUseEST() {
        super(InteractionChainStartEvent.class);
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
                       InteractionChainStartEvent event) {
        if (store == null || event == null || !isAbilityUse(event.getType())) {
            return;
        }

        InteractionContext interaction = event.getContext();
        if (interaction == null) {
            return;
        }
        Ref<EntityStore> playerRef = interaction.getOwningEntity();
        if (playerRef == null) {
            playerRef = interaction.getEntity();
        }
        if (playerRef == null) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        captureCoreRuneCompatibility(store, playerRef, interaction, event.getType(), commandBuffer);

        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        if (heldContext == null || !heldContext.isValid()) {
            return;
        }
        ItemStack weapon = LoreHeldItemUpdateManager.resolveHeldItem(player, heldContext);
        if (weapon == null || weapon.isEmpty() || !LoreSocketManager.isEquipment(weapon)
                || LoreSocketManager.isRuneDeltaActive(weapon)) {
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(weapon);
        if (data == null) {
            return;
        }

        boolean changed = LoreSocketManager.syncSocketColors(weapon, data);
        Set<String> used = new HashSet<>();
        LoreProcHandler.ProcState procState = new LoreProcHandler.ProcState();
        changed |= LoreProcHandler.applyLoreSkillUseSockets(store, player, playerRef,
                interaction.getTargetEntity(), data, used, procState, commandBuffer);
        if (procState.hasTriggered()) {
            changed |= LoreProcHandler.applyLoreProcChain(store, player, playerRef,
                    interaction.getTargetEntity(), null, data, true, used, commandBuffer);
        }

        if (changed) {
            ItemStack updated = LoreSocketManager.withLoreSocketData(weapon, data);
            LoreHeldItemUpdateManager.applyOrQueue(store, playerRef, player, heldContext, updated);
        }
    }

    private boolean isAbilityUse(InteractionType type) {
        return type == InteractionType.Ability1
                || type == InteractionType.Ability2
                || type == InteractionType.Ability3
                || type == InteractionType.Ability4;
    }

    private void captureCoreRuneCompatibility(Store<EntityStore> store,
                                              Ref<EntityStore> playerRef,
                                              InteractionContext interaction,
                                              InteractionType interactionType,
                                              CommandBuffer<EntityStore> commandBuffer) {
        ItemStack coreRune = interaction.getHeldItem();
        if (coreRune == null || coreRune.isEmpty() || coreRune.getItem() == null
                || !(coreRune.getItem().getAbility() instanceof CoreItemAbility coreAbility)) {
            LoreProcHandler.clearForeignCoreAbilitySupport(store, playerRef);
            LoreProcHandler.captureCoreAbilityNativeModifiers(playerRef, Map.of(), null, null);
            return;
        }

        ImpactModifiers modifiers = ImpactModifiers.collect(interaction, playerRef, store);
        Map<String, Float> attributes = modifiers == null ? Map.of() : modifiers.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            attributes = CoreAbilityRune.readEquippedSupportAttributes(interaction, playerRef, store, coreRune);
        }
        LoreProcHandler.captureCoreAbilityNativeModifiers(playerRef, attributes,
                modifiers == null ? null : modifiers.getOnHitEffectIds(),
                modifiers == null ? null : modifiers.getElementalCauseId());

        if (isSocketReforgeCoreRoot(coreAbility.getCastRootId())) {
            LoreProcHandler.clearForeignCoreAbilitySupport(store, playerRef);
            return;
        }

        LoreProcHandler.beginForeignCoreAbilitySupport(store, playerRef,
                interaction.getTargetEntity(),
                CoreAbilityRune.readEquippedSupportRunestones(interaction, playerRef, store, coreRune),
                interactionType,
                commandBuffer);
    }

    private boolean isSocketReforgeCoreRoot(String rootId) {
        if (rootId == null) {
            return false;
        }
        String normalized = rootId.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("root_ability_rune_")
                || normalized.startsWith("root_ability_omnislash")
                || normalized.startsWith("root_ability_octaslash")
                || normalized.startsWith("root_ability_wolfpack")
                || normalized.equals("root_ability_pummel_rune")
                || normalized.equals("root_ability_bloodrush_rune")
                || normalized.equals("root_ability_soul_reaper");
    }
}
