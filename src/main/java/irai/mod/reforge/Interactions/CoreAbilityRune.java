package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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
        LoreProcHandler.activateCoreAbilityRune(store, ownerRef, targetRef, ability, feedTier);
    }
}
