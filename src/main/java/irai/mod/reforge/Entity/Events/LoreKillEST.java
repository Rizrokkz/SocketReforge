package irai.mod.reforge.Entity.Events;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Lore.LoreAbsorptionStore;
import irai.mod.reforge.Lore.LoreAbility;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreEffectType;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreProcHandler;
import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Lore.LoreTrigger;

/**
 * Assigns lore spirits on first kill and triggers on-kill procs.
 */
@SuppressWarnings("removal")
public final class LoreKillEST extends DeathSystems.OnDeathSystem {
    private static final LoreEffectType[] EFFECT_TIER_1 = new LoreEffectType[] {
            LoreEffectType.HEAL_SELF,
            LoreEffectType.HEAL_DEFENDER,
            LoreEffectType.APPLY_SLOW,
            LoreEffectType.APPLY_WEAKNESS,
            LoreEffectType.APPLY_BLIND
    };
    private static final LoreEffectType[] EFFECT_TIER_2 = new LoreEffectType[] {
            LoreEffectType.APPLY_HASTE,
            LoreEffectType.APPLY_SHIELD,
            LoreEffectType.APPLY_INVISIBLE,
            LoreEffectType.LIFESTEAL
    };
    private static final LoreEffectType[] EFFECT_TIER_3 = new LoreEffectType[] {
            LoreEffectType.DAMAGE_TARGET,
            LoreEffectType.DAMAGE_ATTACKER,
            LoreEffectType.APPLY_BLEED,
            LoreEffectType.APPLY_POISON
    };
    private static final LoreEffectType[] EFFECT_TIER_4 = new LoreEffectType[] {
            LoreEffectType.APPLY_BURN,
            LoreEffectType.APPLY_SHOCK,
            LoreEffectType.APPLY_FREEZE,
            LoreEffectType.APPLY_ROOT
    };
    private static final LoreEffectType[] EFFECT_TIER_5 = new LoreEffectType[] {
            LoreEffectType.APPLY_STUN,
            LoreEffectType.APPLY_FEAR,
            LoreEffectType.DOUBLE_CAST,
            LoreEffectType.MULTI_HIT,
            LoreEffectType.CRIT_CHARGE,
            LoreEffectType.BERSERK
    };
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref,
                                 DeathComponent deathComponent,
                                 Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        if (ref == null || deathComponent == null || store == null) {
            return;
        }
        Damage deathInfo = deathComponent.getDeathInfo();
        if (deathInfo == null) {
            return;
        }
        Damage.Source source = deathInfo.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) {
            return;
        }
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null) {
            return;
        }
        if (attackerRef.equals(ref)) {
            return;
        }
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }

        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(attacker);
        if (ctx == null || !ctx.isValid()) {
            return;
        }
        ItemStack weapon = ctx.getItemStack();
        if (weapon == null || weapon.isEmpty() || !LoreSocketManager.isEquipment(weapon)) {
            return;
        }

        LoreSocketData data = LoreSocketManager.getLoreSocketData(weapon);
        if (data == null) {
            return;
        }

        String descriptor = resolveSpiritDescriptor(store, ref, deathComponent);
        String npcRoleId = resolveNpcRoleId(store, ref);
        WorldMapTracker tracker = attacker.getWorldMapTracker();
        WorldMapTracker.ZoneDiscoveryInfo zoneInfo = tracker == null ? null : tracker.getCurrentZone();
        String zoneName = zoneInfo == null ? null : zoneInfo.zoneName();
        String regionName = zoneInfo == null ? null : zoneInfo.regionName();
        String biomeName = tracker == null ? null : tracker.getCurrentBiomeName();
        boolean changed = assignSpirits(attacker, data, descriptor, npcRoleId, zoneName, regionName, biomeName);
        boolean firstKill = changed;

        Set<String> used = new HashSet<>();
        LoreProcHandler.ProcState procState = new LoreProcHandler.ProcState();
        if (firstKill) {
            changed |= LoreProcHandler.applyLoreSockets(store, attacker, attackerRef, ref,
                    null, data, LoreTrigger.ON_FIRST_KILL, true, used, procState, commandBuffer);
        }
        changed |= LoreProcHandler.applyLoreSockets(store, attacker, attackerRef, ref,
                null, data, LoreTrigger.ON_KILL, true, used, procState, commandBuffer);
        if (firstKill) {
            LoreProcHandler.applyAbsorbed(store, attacker, attackerRef, ref,
                    null, LoreTrigger.ON_FIRST_KILL, true, used, procState, commandBuffer);
        }
        LoreProcHandler.applyAbsorbed(store, attacker, attackerRef, ref,
                null, LoreTrigger.ON_KILL, true, used, procState, commandBuffer);

        if (procState.hasTriggered()) {
            changed |= LoreProcHandler.applyLoreProcChain(store, attacker, attackerRef, ref,
                    null, data, true, used, commandBuffer);
        }

        if (changed) {
            ItemStack updated = LoreSocketManager.withLoreSocketData(weapon, data);
            updateHeldItem(attacker, ctx, updated);
        }
    }

    private boolean assignSpirits(Player attacker,
                                  LoreSocketData data,
                                  String descriptor,
                                  String npcRoleId,
                                  String zoneName,
                                  String regionName,
                                  String biomeName) {
        if (attacker == null || data == null) {
            return false;
        }
        boolean changed = false;
        String npcSpiritId = npcRoleId == null || npcRoleId.isBlank() ? null : npcRoleId.trim();
        String npcAssignedColor = npcSpiritId == null ? null : LoreGemRegistry.resolveSpiritColor(npcSpiritId);
        boolean assignedThisKill = false;
        Set<LoreEffectType> usedEffects = collectUsedEffects(data);
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || socket.hasSpirit()) {
                continue;
            }
            String color = socket.getColor();
            String gemColor = LoreGemRegistry.resolveColor(socket.getGemItemId());
            if (gemColor == null || gemColor.isBlank()) {
                // No resolved gem color means we cannot validate compatibility.
                continue;
            }
            if (color == null || color.isBlank()) {
                color = gemColor;
                if (color != null && !color.isBlank()) {
                    socket.setColor(color);
                    changed = true;
                }
            } else if (gemColor != null && !gemColor.isBlank()
                    && !color.trim().equalsIgnoreCase(gemColor.trim())) {
                continue;
            }

            if (color == null || color.isBlank()) {
                if (npcAssignedColor != null && !npcAssignedColor.isBlank()) {
                    color = npcAssignedColor;
                    socket.setColor(color);
                    changed = true;
                } else {
                    color = "unknown";
                    socket.setColor(color);
                    changed = true;
                }
            }

            if (!assignedThisKill) {
                String spiritId;
                if (npcSpiritId != null && !npcSpiritId.isBlank()) {
                    if (npcAssignedColor != null && !npcAssignedColor.isBlank()
                            && color != null && !color.isBlank()
                            && !npcAssignedColor.equalsIgnoreCase(color)) {
                        continue;
                    }
                    spiritId = npcSpiritId;
                } else {
                spiritId = pickSpiritForKill(descriptor, color, zoneName, regionName, biomeName);
            }
                spiritId = ensureUniqueSpirit(attacker, spiritId, color, descriptor, zoneName, regionName, biomeName, i);
                if (spiritId == null || spiritId.isBlank()) {
                    continue;
                }

                LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
                LoreEffectType override = pickEffectOverride(ability, usedEffects, spiritId, i);
                if (override != null && ability != null && override != ability.getEffectType()) {
                    socket.setEffectOverride(override.name());
                    usedEffects.add(override);
                } else {
                    socket.setEffectOverride("");
                    if (ability != null && ability.getEffectType() != null) {
                        usedEffects.add(ability.getEffectType());
                    }
                }
                socket.setSpiritId(spiritId);
                socket.setLevel(Math.max(1, socket.getLevel()));
                socket.setXp(0);
                socket.setFeedTier(0);
                socket.setLocked(true);
                assignedThisKill = true;
                changed = true;
            }
        }
        return changed;
    }

    private String pickSpiritForKill(String descriptor,
                                     String gemColor,
                                     String zoneName,
                                     String regionName,
                                     String biomeName) {
        if (descriptor == null || descriptor.isBlank()) {
            return null;
        }
        String trimmed = descriptor.trim();
        if (trimmed.equalsIgnoreCase("unknown")) {
            return null;
        }
        String candidate = LoreGemRegistry.resolveSpawnableSpiritId(trimmed);
        if (candidate != null && !candidate.isBlank()) {
            String assignedColor = LoreGemRegistry.resolveSpiritColor(candidate);
            if (gemColor == null || gemColor.isBlank()
                    || assignedColor == null || assignedColor.isBlank()
                    || assignedColor.equalsIgnoreCase(gemColor)) {
                return candidate;
            }
            return null;
        }
        String fallback = trimmed;
        String assignedColor = LoreGemRegistry.resolveSpiritColor(fallback);
        if (gemColor == null || gemColor.isBlank()
                || assignedColor == null || assignedColor.isBlank()
                || assignedColor.equalsIgnoreCase(gemColor)) {
            return fallback;
        }
        return null;
    }

    private String resolveNpcRoleId(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null) {
            return null;
        }
        try {
            NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (npc == null) {
                return null;
            }
            Role role = npc.getRole();
            String[] candidates = {
                    safeString(npc.getRoleName()),
                    safeString(npc.getNPCTypeId()),
                    role == null ? "" : safeString(role.getRoleName()),
                    role == null ? "" : safeString(role.getNameTranslationKey()),
                    role == null ? "" : safeString(role.getAppearanceName()),
                    role == null ? "" : safeString(role.getLabel()),
                    role == null ? "" : safeString(role.getDropListId())
            };
            String firstFallback = null;
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (firstFallback == null) {
                    firstFallback = candidate.trim();
                }
                String resolved = LoreGemRegistry.resolveSpawnableSpiritId(candidate);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
                if (LoreGemRegistry.isSpawnableSpirit(candidate)) {
                    return candidate.trim();
                }
            }
            if (firstFallback != null && !firstFallback.isBlank()) {
                return firstFallback;
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        return null;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String ensureUniqueSpirit(Player attacker,
                                      String spiritId,
                                      String color,
                                      String descriptor,
                                      String zoneName,
                                      String regionName,
                                      String biomeName,
                                      int slotIndex) {
        if (attacker == null || spiritId == null || spiritId.isBlank()) {
            return spiritId;
        }
        if (!LoreAbsorptionStore.isAbsorbed(attacker.getUuid(), spiritId)) {
            return spiritId;
        }

        List<String> pool = LoreGemRegistry.getAllowedSpirits(color, null, null, null);
        for (String candidate : pool) {
            if (!LoreAbsorptionStore.isAbsorbed(attacker.getUuid(), candidate)) {
                return candidate;
            }
        }

        if (descriptor == null || descriptor.isBlank()) {
            return spiritId;
        }
        String suffix = Integer.toHexString(Math.abs((descriptor + slotIndex).hashCode()));
        return spiritId + "_" + suffix;
    }

    private String resolveSpiritDescriptor(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           DeathComponent deathComponent) {
        if (store == null || targetRef == null) {
            return "unknown";
        }
        try {
            NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (npc != null) {
                Role role = npc.getRole();
                if (role != null) {
                    String[] candidates = {
                            role.getRoleName(),
                            role.getAppearanceName(),
                            role.getLabel(),
                            role.getDropListId(),
                            role.getNameTranslationKey()
                    };
                    String resolved = firstNonBlank(candidates);
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fall through.
        }

        try {
            Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
            if (targetPlayer != null) {
                String display = targetPlayer.getDisplayName();
                if (display != null && !display.isBlank()) {
                    return display;
                }
            }
        } catch (Throwable ignored) {
            // Fall through.
        }

        if (deathComponent != null && deathComponent.getDeathCause() != null) {
            String causeId = deathComponent.getDeathCause().getId();
            if (causeId != null && !causeId.isBlank()) {
                return causeId;
            }
        }

        return "unknown";
    }

    private String firstNonBlank(String[] values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            return value.trim();
        }
        return null;
    }

    private Set<LoreEffectType> collectUsedEffects(LoreSocketData data) {
        if (data == null) {
            return EnumSet.noneOf(LoreEffectType.class);
        }
        Set<LoreEffectType> used = EnumSet.noneOf(LoreEffectType.class);
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
                continue;
            }
            LoreEffectType effect = resolveSocketEffectType(socket);
            if (effect != null) {
                used.add(effect);
            }
        }
        return used;
    }

    private LoreEffectType pickEffectOverride(LoreAbility ability,
                                              Set<LoreEffectType> used,
                                              String spiritId,
                                              int slotIndex) {
        if (ability == null || used == null || used.isEmpty()) {
            return null;
        }
        if (ability.getAbilityNameKey() != null && !ability.getAbilityNameKey().isBlank()) {
            return null;
        }
        LoreEffectType base = ability.getEffectType();
        if (base == null || !used.contains(base)) {
            return null;
        }
        LoreEffectType[] pool = getEffectTierPool(base);
        if (pool == null || pool.length == 0) {
            return null;
        }
        int hash = Math.abs(((spiritId == null ? "" : spiritId) + ":" + slotIndex).hashCode());
        int start = Math.floorMod(hash, pool.length);
        for (int i = 0; i < pool.length; i++) {
            LoreEffectType candidate = pool[(start + i) % pool.length];
            if (candidate == null || candidate == base) {
                continue;
            }
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private LoreEffectType resolveSocketEffectType(LoreSocketData.LoreSocket socket) {
        if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
            return null;
        }
        String overrideRaw = socket.getEffectOverride();
        if (overrideRaw != null && !overrideRaw.isBlank()) {
            LoreEffectType override = LoreEffectType.fromString(overrideRaw, null);
            if (override != null) {
                return override;
            }
        }
        LoreAbility ability = LoreAbilityRegistry.getAbility(socket.getSpiritId());
        return ability == null ? null : ability.getEffectType();
    }

    private LoreEffectType[] getEffectTierPool(LoreEffectType effectType) {
        if (effectType == null) {
            return EFFECT_TIER_3;
        }
        return switch (effectType) {
            case HEAL_SELF, HEAL_DEFENDER, HEAL_SELF_OVER_TIME, HEAL_AREA, HEAL_AREA_OVER_TIME,
                    APPLY_SLOW, APPLY_WEAKNESS, APPLY_BLIND -> EFFECT_TIER_1;
            case APPLY_HASTE, APPLY_SHIELD, APPLY_INVISIBLE, LIFESTEAL -> EFFECT_TIER_2;
            case DAMAGE_TARGET, DAMAGE_ATTACKER, APPLY_BLEED, APPLY_POISON, DRAIN_LIFE -> EFFECT_TIER_3;
            case APPLY_BURN, APPLY_SHOCK, APPLY_FREEZE, APPLY_ROOT -> EFFECT_TIER_4;
            case APPLY_STUN, APPLY_FEAR, DOUBLE_CAST, MULTI_HIT, CRIT_CHARGE, BERSERK,
                    SUMMON_WOLF_PACK, CHARGE_ATTACK, OMNISLASH, OCTASLASH, VORTEXSTRIKE, PUMMEL, BLOOD_RUSH,
                    CAUSTIC_FINALE, SHRAPNEL_FINALE, BURN_FINALE -> EFFECT_TIER_5;
        };
    }

    private void updateHeldItem(Player player, PlayerInventoryUtils.HeldItemContext ctx, ItemStack updated) {
        if (player == null || ctx == null || updated == null) {
            return;
        }
        if (ctx.getContainer() != null && ctx.getSlot() >= 0) {
            ctx.getContainer().setItemStackForSlot(ctx.getSlot(), updated);
            return;
        }
        PlayerInventoryUtils.setSelectedHotbarItem(player, updated);
    }
}
