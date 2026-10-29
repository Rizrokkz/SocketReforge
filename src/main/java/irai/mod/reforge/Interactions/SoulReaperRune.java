package irai.mod.reforge.Interactions;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import irai.mod.reforge.Lore.SoulReaperTracker;

/** Arms the Soul Reaper tracker for the NPC struck by the projectile. */
public final class SoulReaperRune extends SimpleInteraction {
    public static final BuilderCodec<SoulReaperRune> CODEC =
            BuilderCodec.builder(SoulReaperRune.class, SoulReaperRune::new, SimpleInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        Ref<EntityStore> ownerRef = context.getOwningEntity();
        Ref<EntityStore> targetRef = context.getTargetEntity();
        if (targetRef == null) {
            targetRef = context.getMetaStore().getIfPresentMetaObject(Interaction.TARGET_ENTITY);
        }
        if (ownerRef == null || !ownerRef.isValid() || targetRef == null || !targetRef.isValid()) {
            return;
        }

        Store<EntityStore> store = ownerRef.getStore();
        if (store == null) {
            return;
        }
        Player player = store.getComponent(ownerRef, Player.getComponentType());
        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (player != null && player.getUuid() != null && npc != null) {
            SoulReaperTracker.arm(player.getUuid(), targetRef);
        }
    }
}
