package irai.mod.reforge.Interactions;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.AbilityCostType;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Checks and spends the active core rune's declared ability cost. */
public final class CoreRuneCost extends SimpleInteraction {
    public static final BuilderCodec<CoreRuneCost> CODEC =
            BuilderCodec.builder(CoreRuneCost.class, CoreRuneCost::new, SimpleInteraction.CODEC).build();

    @Override
    protected void tick0(boolean firstRun, float time, @NonNullDecl InteractionType type,
                         @NonNullDecl InteractionContext context,
                         @NonNullDecl CooldownHandler cooldownHandler) {
        super.tick0(firstRun, time, type, context, cooldownHandler);
        if (!firstRun) {
            return;
        }

        ItemStack rune = context.getHeldItem();
        if (rune == null || rune.isEmpty() || rune.getItem() == null
                || !(rune.getItem().getAbility() instanceof CoreItemAbility ability)) {
            fail(context);
            return;
        }

        float cost = ability.getCost();
        AbilityCostType costType = ability.getCostType();
        if (cost <= 0.0f || costType == null || costType == AbilityCostType.None) {
            return;
        }

        ImpactModifiers castModifiers = context.getMetaStore()
                .getIfPresentMetaObject(ImpactModifiers.CAST_SNAPSHOT);
        if (castModifiers != null) {
            cost *= castModifiers.getAttribute("Cost", 1.0f);
        }
        if (cost <= 0.0f) {
            return;
        }

        Ref<EntityStore> entityRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (entityRef == null || !entityRef.isValid() || commandBuffer == null) {
            fail(context);
            return;
        }

        Player player = commandBuffer.getComponent(entityRef, Player.getComponentType());
        if (player != null && player.getGameMode() == GameMode.Creative) {
            return;
        }

        int statIndex = EntityStatType.getAssetMap().getIndex(costType.name());
        if (statIndex == Integer.MIN_VALUE) {
            fail(context);
            return;
        }

        EntityStatMap statMap = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
        EntityStatValue stat = statMap == null ? null : statMap.get(statIndex);
        if (stat == null || stat.get() - cost < stat.getMin()) {
            fail(context);
            return;
        }

        Int2FloatOpenHashMap costChange = new Int2FloatOpenHashMap(1);
        costChange.put(statIndex, -cost);
        statMap.processStatChanges(EntityStatMap.Predictable.SELF, costChange,
                ValueType.Absolute, ChangeStatBehaviour.Add);
    }

    private static void fail(InteractionContext context) {
        if (context.getState() != null) {
            context.getState().state = InteractionState.Failed;
        }
    }
}
