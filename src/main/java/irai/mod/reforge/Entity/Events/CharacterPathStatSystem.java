package irai.mod.reforge.Entity.Events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Progression.CharacterPathService.Bonus;

/** Applies character-path max-stat bonuses and passive regeneration to players. */
public final class CharacterPathStatSystem extends EntityTickingSystem<EntityStore>
        implements EntityStatsSystems.StatModifyingSystem {

    private static final String HEALTH_MODIFIER = "socket_reforge.path.max_health";
    private static final String MANA_MODIFIER = "socket_reforge.path.max_mana";
    private static final String STAMINA_MODIFIER = "socket_reforge.path.max_stamina";

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public boolean isParallel(int from, int to) {
        return false;
    }

    @Override
    public void tick(float time, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Player player = store.getComponent(chunk.getReferenceTo(index), Player.getComponentType());
            EntityStatMap stats = store.getComponent(chunk.getReferenceTo(index), EntityStatMap.getComponentType());
            if (player == null || stats == null) return;

            int health = resolveIndex(stats, DefaultEntityStatTypes.getHealth(), "Health", "health", "HP", "hp");
            int mana = resolveIndex(stats, DefaultEntityStatTypes.getMana(), "Mana", "mana", "MP", "mp");
            int stamina = resolveIndex(stats, DefaultEntityStatTypes.getStamina(), "Stamina", "stamina");

            applyMaxBonus(stats, health, HEALTH_MODIFIER,
                    CharacterPathService.bonus(player, Bonus.MAX_HEALTH_FLAT));
            applyMaxBonus(stats, mana, MANA_MODIFIER,
                    CharacterPathService.bonus(player, Bonus.MAX_MANA_FLAT));
            applyMaxBonus(stats, stamina, STAMINA_MODIFIER,
                    CharacterPathService.bonus(player, Bonus.MAX_STAMINA_FLAT));

            if (time <= 0.0f) return;
            regenerate(stats, health, (float) CharacterPathService.bonus(player, Bonus.HEALTH_REGEN_PER_SECOND), time);
            regenerate(stats, mana, (float) CharacterPathService.bonus(player, Bonus.MANA_REGEN_PER_SECOND), time);
            regenerate(stats, stamina, (float) CharacterPathService.bonus(player, Bonus.STAMINA_REGEN_PER_SECOND), time);
        } catch (Throwable exception) {
            System.err.println("[SocketReforge] CharacterPathStatSystem tick error: " + exception.getMessage());
        }
    }

    private static void applyMaxBonus(EntityStatMap stats, int statIndex, String modifierId, double bonus) {
        if (statIndex < 0) return;
        float amount = (float) Math.max(0.0d, bonus);
        if (amount > 0.0f) {
            stats.putModifier(statIndex, modifierId, new StaticModifier(
                    Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, amount));
        } else {
            stats.removeModifier(statIndex, modifierId);
        }
        EntityStatValue value = stats.get(statIndex);
        if (value != null && value.get() > value.getMax()) {
            stats.setStatValue(statIndex, value.getMax());
        }
    }

    private static void regenerate(EntityStatMap stats, int statIndex, float perSecond, float time) {
        if (statIndex < 0 || perSecond <= 0.0f) return;
        EntityStatValue value = stats.get(statIndex);
        if (value == null || value.get() >= value.getMax()) return;
        stats.addStatValue(statIndex, perSecond * time);
    }

    private static int resolveIndex(EntityStatMap stats, int defaultIndex, String... aliases) {
        if (defaultIndex >= 0) {
            EntityStatValue value = stats.get(defaultIndex);
            if (value != null) return value.getIndex();
        }
        for (String alias : aliases) {
            EntityStatValue value = stats.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }
}
