package irai.mod.reforge.Interactions;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemAbility;
import com.hypixel.hytale.server.core.asset.type.item.config.SupportItemAbility;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Lore.LoreHeldItemUpdateManager;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketManager;

/** Cast entry point shared by the JSON-authored Core Soulstone ability runes. */
public final class CoreAbilityRune extends SimpleInteraction {
    private String ability = "GrandSlam";

    public static final BuilderCodec<CoreAbilityRune> CODEC =
            BuilderCodec.<CoreAbilityRune>builder(CoreAbilityRune.class, CoreAbilityRune::new, SimpleInteraction.CODEC)
                    .append(new KeyedCodec<>("Ability", Codec.STRING),
                            (interaction, value) -> interaction.ability = value,
                            interaction -> interaction.ability)
                    .add()
                    .build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }
        Ref<EntityStore> ownerRef = context.getOwningEntity();
        if (ownerRef == null || !ownerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = ownerRef.getStore();
        if (store == null || store.getComponent(ownerRef, Player.getComponentType()) == null) {
            return;
        }
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        if (targetRef != null && !targetRef.isValid()) {
            targetRef = null;
        }
        ItemStack rune = context.getHeldItem();
        int feedTier = LoreSocketManager.getModifierRunestoneFeedTier(rune);
        ImpactModifiers modifiers = ImpactModifiers.collect(context, ownerRef, store);
        Map<String, Float> abilityAttributes = modifiers == null
                ? null : modifiers.getAttributes();
        if (abilityAttributes == null || abilityAttributes.isEmpty()) {
            abilityAttributes = readEquippedSupportAttributes(context, ownerRef, store, rune);
        }
        LoreProcHandler.captureCoreAbilityNativeModifiers(ownerRef, abilityAttributes,
                modifiers == null ? null : modifiers.getOnHitEffectIds(),
                modifiers == null ? null : modifiers.getElementalCauseId());
        List<LoreSocketManager.SocketedModifierRunestone> supportRunestones =
                readEquippedSupportRunestones(context, ownerRef, store, rune);
        if (ability.equalsIgnoreCase("Volley") || ability.toLowerCase(java.util.Locale.ROOT).startsWith("volley_")) {
            ItemStack weapon = readHeldWeapon(ownerRef, store);
            boolean weaponRuneDelta = LoreSocketManager.isRuneDeltaActive(weapon);
            List<LoreSocketManager.SocketedModifierRunestone> weaponRunestones =
                    LoreSocketManager.getSocketedModifierRunestones(weapon);
            System.out.println("[SocketReforge] Volley support snapshot: section="
                    + context.getHeldItemSectionId() + ", slot=" + context.getHeldItemSlot()
                    + ", core=" + (rune == null ? "null" : rune.getItemId())
                    + ", attributes=" + abilityAttributes
                    + ", supportRunestones=" + supportRunestones
                    + ", weapon=" + (weapon == null ? "null" : weapon.getItemId())
                    + ", weaponRuneDelta=" + weaponRuneDelta
                    + ", weaponModifierRunestones=" + weaponRunestones);
        } else if (!supportRunestones.isEmpty()) {
            System.out.println("[SocketReforge] Core ability support snapshot: ability=" + ability
                    + ", core=" + (rune == null ? "null" : rune.getItemId())
                    + ", attributes=" + abilityAttributes
                    + ", supportRunestones=" + supportRunestones);
        }
        LoreProcHandler.activateCoreAbilityRune(store, ownerRef, targetRef, ability, feedTier,
                abilityAttributes == null ? Map.of() : abilityAttributes, supportRunestones);
    }

    private static ItemStack readHeldWeapon(Ref<EntityStore> ownerRef, Store<EntityStore> store) {
        Player player = ownerRef == null || !ownerRef.isValid() || store == null
                ? null : store.getComponent(ownerRef, Player.getComponentType());
        PlayerInventoryUtils.HeldItemContext heldContext = PlayerInventoryUtils.getHeldItemContext(player);
        return heldContext == null || !heldContext.isValid()
                ? null : LoreHeldItemUpdateManager.resolveHeldItem(player, heldContext);
    }

    public static List<LoreSocketManager.SocketedModifierRunestone> readEquippedSupportRunestones(
            InteractionContext context, Ref<EntityStore> ownerRef, Store<EntityStore> store, ItemStack rune) {
        if (context == null || ownerRef == null || !ownerRef.isValid() || store == null
                || rune == null || !rune.isValid() || rune.getItem() == null
                || !(rune.getItem().getAbility() instanceof CoreItemAbility)) {
            return List.of();
        }
        InventoryComponent.AbilitySlots abilitySlots = store.getComponent(
                ownerRef, InventoryComponent.AbilitySlots.getComponentType());
        ItemContainer abilityInventory = abilitySlots == null ? null : abilitySlots.getInventory();
        int coreSlot = Byte.toUnsignedInt(context.getHeldItemSlot());
        if (abilityInventory == null || coreSlot + 2 >= abilityInventory.getCapacity()) {
            return List.of();
        }

        List<LoreSocketManager.SocketedModifierRunestone> runestones = new ArrayList<>(2);
        for (short slot = (short) (coreSlot + 1); slot <= coreSlot + 2; slot++) {
            ItemStack supportStack = abilityInventory.getItemStack(slot);
            if (supportStack == null || supportStack.isEmpty()
                    || !LoreSocketManager.isModifierRunestone(supportStack)) {
                continue;
            }
            String spiritId = LoreSocketManager.resolveModifierRunestoneSpiritId(supportStack.getItemId());
            if (spiritId == null) {
                continue;
            }
            runestones.add(new LoreSocketManager.SocketedModifierRunestone(
                    supportStack.getItemId(), spiritId, slot - coreSlot - 1,
                    LoreSocketManager.getModifierRunestoneLevel(supportStack),
                    LoreSocketManager.getModifierRunestoneFeedTier(supportStack)));
        }
        return runestones.isEmpty() ? List.of() : List.copyOf(runestones);
    }

    public static Map<String, Float> readEquippedSupportAttributes(InteractionContext context,
                                                                    Ref<EntityStore> ownerRef,
                                                                    Store<EntityStore> store,
                                                                    ItemStack rune) {
        if (context == null || ownerRef == null || !ownerRef.isValid() || store == null
                || rune == null || !rune.isValid() || rune.getItem() == null) {
            return Map.of();
        }
        ItemAbility itemAbility = rune.getItem().getAbility();
        if (!(itemAbility instanceof CoreItemAbility coreAbility)) {
            return Map.of();
        }
        InventoryComponent.AbilitySlots abilitySlots = store.getComponent(
                ownerRef, InventoryComponent.AbilitySlots.getComponentType());
        ItemContainer abilityInventory = abilitySlots == null ? null : abilitySlots.getInventory();
        int coreSlot = Byte.toUnsignedInt(context.getHeldItemSlot());
        if (abilityInventory == null || coreSlot + 2 >= abilityInventory.getCapacity()) {
            return Map.of();
        }

        Map<String, Float> attributes = coreAbility.getAttributes() == null
                ? new HashMap<>() : new HashMap<>(coreAbility.getAttributes());
        for (short slot = (short) (coreSlot + 1); slot <= coreSlot + 2; slot++) {
            ItemStack supportStack = abilityInventory.getItemStack(slot);
            if (supportStack == null || supportStack.isEmpty() || supportStack.getItem() == null) {
                continue;
            }
            ItemAbility supportAbility = supportStack.getItem().getAbility();
            if (!(supportAbility instanceof SupportItemAbility support)
                    || !support.supports(coreAbility)
                    || support.getAttributeModifiers() == null) {
                continue;
            }
            for (Map.Entry<String, StaticModifier[]> entry : support.getAttributeModifiers().entrySet()) {
                float value = attributes.getOrDefault(entry.getKey(), 1.0f);
                StaticModifier[] modifiers = entry.getValue();
                if (modifiers == null) {
                    continue;
                }
                for (StaticModifier modifier : modifiers) {
                    if (modifier == null) {
                        continue;
                    }
                    value = switch (modifier.getCalculationType()) {
                        case ADDITIVE -> value + modifier.getAmount();
                        case MULTIPLICATIVE -> value * modifier.getAmount();
                    };
                }
                attributes.put(entry.getKey(), value);
            }
        }
        return attributes;
    }
}
