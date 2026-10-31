package irai.mod.reforge.Interactions;

import java.util.List;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketManager;

/** Captures ability-slot modifier runestones for Octaslash's native projectile hits. */
public final class OctaslashRuneSupport extends SimpleInteraction {
    public static final BuilderCodec<OctaslashRuneSupport> CODEC =
            BuilderCodec.builder(OctaslashRuneSupport.class, OctaslashRuneSupport::new, SimpleInteraction.CODEC)
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
        if (!CoreAbilityRune.canUseWithHeldWeapon(store, ownerRef, context.getHeldItem())) {
            return;
        }
        List<LoreSocketManager.SocketedModifierRunestone> supportRunestones =
                CoreAbilityRune.readEquippedSupportRunestones(context, ownerRef, store, context.getHeldItem());
        LoreProcHandler.startOctaslashSupportWindow(store, ownerRef, supportRunestones);
    }
}
