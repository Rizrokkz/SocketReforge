# Lore System — Implementation Status
> **Platform:** Hytale (custom engine, not Bukkit/Spigot)
> All code samples use engine-agnostic Java interfaces and pseudocode.
> Wire against Hytale's modding API once it is publicly available.

---

## Executive Summary
The old Spirit/Affix plan is no longer relevant. This document reflects the **Lore socket** system only. The Lore system adds a separate socket type from essence sockets and enables:
- Lore sockets on loot (up to 3, chest/drop only)
- Lore gems that become vessels for spirits on first kill
- Spirit abilities with triggers, proc chances, cooldowns, and procedural scaling
- Proc-based leveling with feed gates every 5 levels
- Lore absorption at level 100 (player keeps the spirit without socketing)
- Tooltip integration for sockets, spirit, level, and ability text

---

## Current Implementation Update (2026-03-26)
This section reflects the **actual in-mod state** (not the engine-agnostic plan).

### Config Layout
- **Unified mapping** lives in `server/mods/irai.mod.reforge_Socket Reforge/LoreMappingConfig.json`.
  - Replaces the old `LoreAbilityConfig.json` and `LoreGemConfig.json` (both removed).
- `LoreConfig.json` still defines global lore rules (sockets, feed, absorption).
- `LootSocketRollConfig.json` still controls socket roll chances.

### Proc + Synergy Runtime
- Lore procs are **queued and batched** to avoid interrupting Ability1 signatures.
- Built-in signatures are **forced without consuming signature energy**.
- Signature fallback visuals/damage run when signature rules fail.

### DoT + Finale Behavior
- **Bleed** ticks every 0.3s, base duration 3s, ramps up, base damage uses ~0.5% of target max HP.
- **Finales (Burn / Caustic / Shrapnel)**:
  - Target is **marked** when its status is applied.
  - Explosion triggers when the marked target dies (even if death came from DoT).
  - Explosion applies AoE damage and **re-applies the same status** to targets in range (infection).
  - Marks expire after 30s and won’t re-trigger on dead targets.

### Feed Gate XP
- When a socket is **feed-ready**, XP no longer accumulates until fed.

### Explosion SFX
- Finale explosions now use the **Weapon_Bomb** sound:
  - `SFX_Goblin_Lobber_Bomb_Death`
  - `SFX_Goblin_Lobber_Bomb_Death_Local`

---

## Hytale Platform Notes

Hytale runs on its own engine — **not Bukkit, Spigot, or any Minecraft-derived server**. This has direct consequences for how the Lore system is wired:

| Bukkit concept | Hytale equivalent (approximate) |
|---|---|
| `@EventHandler` + `Listener` | Hytale event bus subscription (API TBD) |
| `EntityDamageByEntityEvent` | Hytale combat event (e.g. `CombatHitEvent`) |
| `PotionEffect` | Hytale status effect system |
| `player.setMetadata(...)` | Hytale entity component / tag store |
| `Bukkit.getPluginManager()` | Hytale mod registry |
| `plugin.yml` | Hytale mod manifest |
| YAML config files | Hytale scripting data / JSON asset format |

Until the public modding API releases, all event wiring in this document is written as **engine-agnostic pseudocode interfaces**. The enums (`LoreTrigger`, `LoreEffectType`) are pure Java and require no changes. The handler and executor classes will need their event types swapped for real Hytale API types.

### Zone-Aware Spirit Pools
Hytale's world is divided into Zones (Orbis Zone 1–3, Votum, etc.). The `ON_FIRST_KILL` trigger and `LoreGemRegistry` should be designed to be **zone-aware from the start**:
- A red gem dropped in Zone 1 (Verdant) pulls from a different spirit pool than the same gem in Zone 2 (Howling Sands).
- Spirit names, lore text, and visual effects should reference the zone's aesthetic.
- See the `lore_spirits.json` sample at the end of this document.

### Script-Friendly Config
Hytale's adventure scripting system is data/script-driven. Design `lore_abilities.json` to be readable by both Java (mod layer) and Hytale's native scripting layer. Prefer flat key-value structures over deeply nested objects.

---

## Implemented Components

### Core Model and Registries
- `Lore/LoreTrigger.java` — Trigger types (all 16 categories, see below)
- `Lore/LoreEffectType.java` — Effect categories (~100 named effects, see below)
- `Lore/LoreAbility.java` — Ability definition and localized description rendering
- `Lore/LoreAbilityRegistry.java` — Ability config parsing and fallback generation
- `Lore/LoreGemRegistry.java` — Lore gem detection and spirit pools by gem color + zone
- `Lore/LoreSocketData.java` — Lore socket data model
- `Lore/LoreSocketManager.java` — Read/write entity tags, roll sockets, leveling, feed gating
- `Lore/LoreAbsorptionStore.java` — Per-player absorbed spirit persistence

### Runtime Logic
- `Lore/LoreProcHandler.java` — Proc execution, cooldown checks, absorption logic
- `Entity/Events/LoreEffectEST.java` — Damage event trigger handling
- `Entity/Events/LoreKillEST.java` — First-kill spirit assignment and kill triggers

### Loot Integration
- `Entity/Events/LootSocketRoller.java` rolls lore sockets via `LoreSocketManager.maybeRollLoreSockets(...)`

### Tooltip Integration
- `Util/DynamicTooltipUtils.java` renders lore sockets and spirit details using localized strings

### Config Integration
- `Config/LoreAbilityConfig.java` — Ability entries
- `Config/LoreConfig.java` and `Config/LoreGemConfig.java` — Socket/gem rules
- `HytaleMod.java` (replaces `ReforgePlugin.java`) — Registers event listeners and initializes the absorption store

---

## Lore Flow (Current Behavior)
1. Lore sockets are rolled on loot drops (separate from essence sockets).
2. Lore gems act as empty vessels until the equipped item gets its first kill.
3. On first kill, a spirit is assigned based on gem color **and the current Zone**.
4. Proc triggers grant fixed XP; level rises over time.
5. Every 5 levels, a feed gate requires Resonant Essence to continue.
6. At level 100, the spirit is absorbed by the player and persists without socketing.

---

## Metadata Notes
Lore data is stored via `EntityTagStore.LORE_SOCKET_*` (engine-agnostic tag keys, separate from essence tags). In Hytale this maps to whatever entity component/NBT-equivalent Hytale's API exposes.

---

## Trigger & Effect System

### LoreTrigger.java — Full Enum

Pure Java, no engine dependencies. All 16 triggers across 5 categories.

```java
package com.example.hytalemod.lore;

/**
 * All valid trigger conditions for a LoreAbility.
 *
 * Wire each value to its corresponding Hytale engine event in LoreProcHandler.
 * The string name of each enum value is used as the key in lore_abilities.json.
 */
public enum LoreTrigger {

    // -------------------------------------------------------------------------
    // OFFENSIVE — fire when the player deals damage or uses skills
    // -------------------------------------------------------------------------

    /** Player's melee or ranged attack lands on a target. */
    ON_HIT,

    /** Player's attack rolls a critical hit. */
    ON_CRIT,

    /** Player activates any active skill/ability slot. */
    ON_SKILL_USE,

    // -------------------------------------------------------------------------
    // DEFENSIVE — fire when the player receives or mitigates damage
    // -------------------------------------------------------------------------

    /** Player receives any incoming damage (before reduction). */
    ON_DAMAGE_TAKEN,

    /** Player successfully blocks an attack (shield/parry). */
    ON_BLOCK,

    /** Player's dodge stat causes an attack to fully miss. */
    ON_DODGE,

    // -------------------------------------------------------------------------
    // KILL / DEATH — fire on entity kill or near-death states
    // -------------------------------------------------------------------------

    /** Player kills any entity. */
    ON_KILL,

    /** Player's HP drops below a configured threshold (default 20%). */
    ON_NEAR_DEATH,

    /**
     * First time the player kills a specific mob type.
     * Gate for spirit assignment — checks LoreKillEST's first-kill registry.
     * Spirit pool is selected by gem color + current Zone.
     */
    ON_FIRST_KILL,

    // -------------------------------------------------------------------------
    // MOVEMENT / POSITIONING — fire on locomotion state changes
    // -------------------------------------------------------------------------

    /** Player enters sprint state. */
    ON_SPRINT,

    /** Player leaves the ground (jump). */
    ON_JUMP,

    /** Player enters sneak/crouch mode. */
    ON_SNEAK,

    // -------------------------------------------------------------------------
    // RECOVERY / UTILITY — fire on healing, consumables, or meta events
    // -------------------------------------------------------------------------

    /** Player receives any healing (item, regen tick, or skill). */
    ON_HEAL,

    /** Player consumes a potion or restorative item. */
    ON_POTION_USE,

    /**
     * This spirit's own ability has just fired.
     * Allows chaining two spirits: Spirit A procs -> emits ON_LORE_PROC -> Spirit B reacts.
     */
    ON_LORE_PROC,

    /** A status effect (bleed, burn, poison, etc.) is applied to or by the player. */
    ON_STATUS_APPLY;
}
```

---

### LoreEffectType.java — Full Enum

Pure Java, no engine dependencies. ~100 named effects across 4 categories.

```java
package com.example.hytalemod.lore;

/**
 * All valid effect types a LoreAbility can produce when it fires.
 *
 * Implementations live in LoreEffectExecutor.
 * The string name of each value is used as the key in lore_abilities.json.
 *
 * Categories: OFFENSIVE, DEFENSIVE, MOVEMENT, UTILITY
 */
public enum LoreEffectType {

    // =========================================================================
    // OFFENSIVE
    // =========================================================================

    /** Flat bonus damage hit on proc. Scales with spirit level. */
    BURST_DAMAGE,
    /** Heals the player for a % of damage dealt. */
    LIFESTEAL,
    /** Applies a bleed DoT to the target (stacks up to a cap). */
    APPLY_BLEED,
    /** Applies a poison DoT to the target. */
    APPLY_POISON,
    /** Applies a burn DoT to the target. */
    APPLY_BURN,
    /** Arcs lightning to up to N nearby enemies. */
    CHAIN_LIGHTNING,
    /** Deals void/shadow damage bypassing a % of armor. */
    VOID_STRIKE,
    /** Shatters a frozen target for massive bonus damage and clears freeze. */
    SHATTER,
    /** Drains stamina/energy from the target. */
    MANA_DRAIN,
    /** Launches the target backward. */
    KNOCKBACK,
    /** Places a debuff mark on the target; other effects interact with marks. */
    MARK_TARGET,
    /** Pulses a damage aura around the player. */
    PROC_AURA_PULSE,
    /** Amplifies the crit damage multiplier for the next N hits. */
    CRIT_DAMAGE_AMP,
    /** Instantly kills targets below a HP threshold. */
    EXECUTE,
    /** Reduces target armor rating for a duration. */
    EXPOSE_ARMOR,
    /** Reduces target damage output for a duration. */
    APPLY_SHRED,
    /** Blinds the target, lowering accuracy. */
    BLIND,
    /** Briefly staggers the target, interrupting their action. */
    STAGGER,
    /** Triggers a second hit at reduced damage. */
    DOUBLE_STRIKE_PROC,
    /** Deals a burst of magical damage. */
    MANA_BURST,
    /** Fires shrapnel fragments hitting all nearby enemies. */
    SPLINTER_SHRAPNEL,
    /** Causes the target to flee in fear. */
    FEAR,
    /** If this hit was a crit, the next attack also crits. */
    ECHOING_CRIT,
    /** Amplifies the damage of the next skill cast. */
    SKILL_DAMAGE_AMP,
    /** Reduces an active cooldown by a flat amount. */
    COOLDOWN_REDUCTION,
    /** Repeats the last skill cast at reduced power. */
    ECHO_CAST,
    /** Refunds a portion of energy spent on the skill. */
    MANA_REFUND,
    /** Refreshes an active buff's duration. */
    BUFF_REFRESH,
    /** Temporarily infuses attacks with an elemental type. */
    ELEMENTAL_INFUSE,
    /** Grants a short haste burst after skill use. */
    PROC_HASTE,
    /** Applies a weakness debuff increasing damage the target takes. */
    APPLY_WEAKNESS,

    // =========================================================================
    // DEFENSIVE
    // =========================================================================

    /** Reduces all incoming damage for a short window. */
    DAMAGE_REDUCTION,
    /** Deals reflected damage to the attacker. */
    RETRIBUTION_DAMAGE,
    /** Spawns an absorb shield orb on the player. */
    SPAWN_SHIELD_ORB,
    /** Heals the player on receiving a hit. */
    HEAL_ON_HIT,
    /** Roots the attacker in place. */
    APPLY_ROOT,
    /** Blinds the attacker. */
    BLIND_ATTACKER,
    /** Short-range blink away from danger. */
    ESCAPE_PROC,
    /** Applies a debuff to the attacker. */
    COUNTER_DEBUFF,
    /** Builds a rage/damage stack per hit taken. */
    RAGE_STACK,
    /** Temporarily removes a resistance from the attacker. */
    RESIST_BREAK,
    /** Passively reflects a % of damage as thorns. */
    THORNS_REFLECT,
    /** Returns a portion of blocked damage to the attacker. */
    BLOCK_DAMAGE_RETURN,
    /** Restores stamina on a successful block. */
    STAMINA_RESTORE,
    /** Opens a narrow parry window for a bonus counter. */
    PARRY_WINDOW,
    /** Stuns the attacker on a successful block. */
    STUN_ATTACKER,
    /** Temporarily boosts armor rating after a block. */
    ARMOR_BUFF,
    /** Triggers a quick counter-strike after a block. */
    RIPOSTE_STRIKE,
    /** Stacks a counter buff each time the player blocks. */
    COUNTER_BUFF_STACK,
    /** Restores energy on a successful block. */
    RESTORE_MANA,
    /** Reflects a ranged projectile back toward its source. */
    DEFLECT_PROJECTILE,
    /** Temporarily boosts movement speed after a dodge. */
    HASTE_BUFF,
    /** Briefly turns the player invisible after dodging. */
    INVISIBLE_PROC,
    /** Stacks evasion chance each consecutive dodge. */
    EVASION_STACK,
    /** Deals counter-damage after a successful dodge. */
    COUNTER_DAMAGE,
    /** Resets a random active cooldown on dodge. */
    RESET_COOLDOWN,
    /** Builds momentum charges usable for a power strike. */
    MOMENTUM_CHARGE,
    /** Dashes the player to a short-range position. */
    SHADOW_STEP,
    /** Refunds stamina on a successful dodge. */
    STAMINA_REFUND,

    // =========================================================================
    // KILL / DEATH
    // =========================================================================

    /** Grants a burst of XP on kill. */
    XP_SURGE,
    /** Heals the player on kill. */
    HEAL_BURST,
    /** Stacks a damage amplifier per kill (expires after inactivity). */
    DAMAGE_AMP_STACK,
    /** Increases loot drop quality/quantity on kill. */
    LOOT_BONUS,
    /** Pulses an execute aura to all nearby low-HP enemies after a kill. */
    EXECUTE_AURA,
    /** Grants a speed boost after a kill. */
    SPEED_BURST,
    /** Extends the duration of all active buffs on kill. */
    BUFF_DURATION_EXTEND,
    /** Absorbs a soul fragment, contributing to absorption progress. */
    SOUL_ABSORB,
    /** Fears all nearby enemies after a kill. */
    FEAR_NEARBY,
    /** Detonates the killed entity's corpse for AoE damage. */
    EXPLODE_CORPSE,
    /** Summons a temporary shade minion from the killed entity. */
    SUMMON_SHADE,
    /** Grants brief invulnerability when HP drops to the threshold. */
    LAST_STAND_INVULN,
    /** Clears all death-mark debuffs when at low HP. */
    DEATH_MARK_CLEANSE,
    /** Creates an absorb barrier on near-death. */
    BARRIER_BURST,
    /** One-time revive proc — resets after a cooldown. */
    REVIVE_PROC,
    /** Makes the player immune to execute-threshold kills for a duration. */
    EXECUTE_IMMUNITY,
    /** Grants a massive damage buff when near death. */
    VENGEANCE_BUFF,
    /** Drains HP from all nearby enemies when near death. */
    DRAIN_NEARBY,
    /** Emits an AoE fear on near-death. */
    SCREAM_AOE_FEAR,
    /** Slows all nearby enemies on near-death. */
    SLOW_PULSE,
    /** Grants temporary invisibility on near-death. */
    FADE,
    /** Assigns a spirit to the Lore gem (internal — first-kill gate). */
    SPIRIT_ASSIGNMENT,
    /** Grants flat Lore XP to the socketed spirit. */
    LORE_XP_GRANT,
    /** Rolls a bonus loot drop entry. */
    BONUS_DROP_ROLL,
    /** Adds one absorption fragment to the spirit's meter. */
    ABSORB_FRAGMENT,
    /** Temporarily boosts stats based on the killed mob type. */
    MOB_KNOWLEDGE_BUFF,
    /** Grants bonus Resonant Essence from the kill. */
    ESSENCE_BONUS,

    // =========================================================================
    // MOVEMENT
    // =========================================================================

    /** Multiplies sprint speed beyond base. */
    SPEED_AMPLIFY,
    /** Raises dodge chance while sprinting. */
    EVASION_UP,
    /** Deals bonus damage proportional to current momentum/speed. */
    MOMENTUM_DAMAGE,
    /** Raises dodge chance briefly (blur effect). */
    BLUR,
    /** Leaves a damaging trail on the ground while sprinting. */
    GROUND_TRAIL_EFFECT,
    /** On-hit while sprinting: applies a knockback ram. */
    RAM_KNOCKBACK,
    /** Passively regenerates stamina faster while sprinting. */
    STAMINA_REGEN,
    /** Damages all surrounding enemies while sprinting. */
    WHIRLWIND_AURA,
    /** Amplifies aerial (jump) attack damage. */
    AERIAL_STRIKE_AMP,
    /** Slows fall speed on jump. */
    FEATHER_FALL,
    /** Deals AoE damage on landing. */
    SLAM_DAMAGE,
    /** Unlocks an extra dodge input while airborne. */
    AIR_DODGE_UNLOCK,
    /** Briefly suspends the player in the air. */
    HOVER_PROC,
    /** Pulse of gravity on landing, pulling enemies inward. */
    GRAVITY_PULSE,
    /** AoE knockback on landing. */
    PLATFORM_CLEAR,
    /** Amplifies backstab / sneak-attack damage. */
    BACKSTAB_AMP,
    /** Suppresses nearby enemy sounds (stealth assist). */
    SILENCE_AURA,
    /** Removes footstep sounds while sneaking. */
    FOOTSTEP_MUTE,
    /** Raises critical hit chance while sneaking. */
    CRIT_CHANCE_UP,
    /** Fully conceals the player while sneaking. */
    VANISH,
    /** Speeds up trap placement actions. */
    TRAP_PLANT_SPEED,
    /** Triggers a pickpocket attempt on nearby entities. */
    PICKPOCKET_PROC,
    /** Reveals nearby hidden or invisible entities. */
    DETECT_HIDDEN,

    // =========================================================================
    // UTILITY / RECOVERY
    // =========================================================================

    /** Converts over-healing into a temporary absorb shield. */
    OVERHEAL_SHIELD,
    /** Amplifies the potency of all incoming healing. */
    HEAL_AMP,
    /** Restores energy whenever the player is healed. */
    MANA_ON_HEAL,
    /** Pulses a heal to all nearby allies on heal. */
    HEAL_PULSE_AOE,
    /** Stacks a regeneration buff per heal event. */
    REGENERATION_STACK,
    /** Removes one random negative debuff on heal. */
    CLEANSE_DEBUFF,
    /** Grants a short damage boost after receiving healing. */
    SURGE_OF_LIFE,
    /** Increases the effectiveness of the consumed potion. */
    POTION_AMP,
    /** Grants an energy burst after consuming a potion. */
    POTION_MANA_BURST,
    /** Short haste surge triggered by potion use. */
    HASTE_SURGE,
    /** Resets an HP-threshold gate (second wind) after a potion. */
    SECOND_WIND,
    /** Raises elemental or physical resistances after a potion. */
    RESISTANCE_UP,
    /** Pulses the player's active auras on potion use. */
    AURA_BURST,
    /** Instantly resets all active cooldowns on potion use. */
    INSTANT_COOLDOWN_RESET,
    /** Gives this ability a chance to fire a second time. */
    PROC_ECHO,
    /** Grants bonus XP to the spirit that fired. */
    XP_BONUS,
    /** Cuts the cooldown of this ability's next proc. */
    COOLDOWN_CUT,
    /** Feeds Resonant Essence into the spirit to unlock the next gate. */
    MANA_FEED,
    /** Triggers a secondary spirit's ability (chain event). */
    SYNERGY_TRIGGER,
    /** Amplifies all active buff values. */
    BUFF_AMPLIFY,
    /** Extends the duration of all status effects applied. */
    STATUS_DURATION_EXTEND,
    /** Opens a window of immunity to the triggering status. */
    IMMUNITY_WINDOW,
    /** Applies a counter status to whoever inflicted the status. */
    COUNTER_STATUS,
    /** Deals burst damage when a debuff is cleansed. */
    BURST_ON_CLEANSE,
    /** Increases damage for each stack of a given status on the target. */
    STACK_EXPLOIT,
    /** Deals bonus damage based on the number of active debuffs on the target. */
    CATALYST_DAMAGE,
    /** Spreads the applied status to nearby enemies. */
    DEBUFF_SPREAD;
}
```

---

### IHytaleEventBus.java — Engine Abstraction Interface

Define this interface now; swap in real Hytale API types later. All handler wiring goes through this.

```java
package com.example.hytalemod.platform;

/**
 * Abstraction over Hytale's event bus.
 *
 * Replace method signatures with real Hytale API types when the modding
 * API is publicly available. The rest of the Lore system calls only this
 * interface — nothing else needs to change.
 */
public interface IHytaleEventBus {

    void onPlayerHit(PlayerHitHandler handler);
    void onPlayerHurt(PlayerHurtHandler handler);
    void onEntityDeath(EntityDeathHandler handler);
    void onMovementState(MovementStateHandler handler);
    void onItemConsumed(ItemConsumedHandler handler);
    void onHealthRegain(HealthRegainHandler handler);
    void onStatusApplied(StatusAppliedHandler handler);

    // Functional interfaces — replace Object parameters with real Hytale types
    @FunctionalInterface interface PlayerHitHandler     { void handle(Object attacker, Object target, double damage, boolean isCrit); }
    @FunctionalInterface interface PlayerHurtHandler    { void handle(Object victim, Object source, double damage, HitMitigation mitigation); }
    @FunctionalInterface interface EntityDeathHandler   { void handle(Object entity, Object killer); }
    @FunctionalInterface interface MovementStateHandler { void handle(Object player, MovementState state); }
    @FunctionalInterface interface ItemConsumedHandler  { void handle(Object player, Object item); }
    @FunctionalInterface interface HealthRegainHandler  { void handle(Object player, double amount, HealSource source); }
    @FunctionalInterface interface StatusAppliedHandler { void handle(Object player, String statusId, boolean appliedByPlayer); }

    enum HitMitigation { NONE, BLOCKED, DODGED, PARRIED }
    enum MovementState { SPRINT_START, SPRINT_END, JUMP, LAND, SNEAK_START, SNEAK_END }
    enum HealSource    { ITEM, REGEN_TICK, SKILL, ENVIRONMENTAL }
}
```

---

### IHytaleEntityContext.java — Player/Entity Abstraction

```java
package com.example.hytalemod.platform;

import java.util.List;

/**
 * Abstraction over a Hytale player or entity.
 * Replace with real Hytale API types when available.
 */
public interface IHytaleEntityContext {

    double getHealth();
    double getMaxHealth();
    void   setHealth(double value);

    /** Apply a named status effect with a magnitude and duration (ticks). */
    void   applyStatus(String statusId, double magnitude, int durationTicks);
    void   removeStatus(String statusId);
    boolean hasStatus(String statusId);

    /** Apply an impulse vector (knockback, blink, etc.). */
    void   applyImpulse(double x, double y, double z);

    /** Entity tag store — maps to Hytale's entity component/NBT-equivalent. */
    void   setTag(String key, Object value);
    Object getTag(String key);

    /** Current Zone ID, e.g. "zone1_verdant", "zone2_sands", "zone3_tundra". */
    String getZoneId();

    List<IHytaleEntityContext> getNearbyEntities(double radius);

    void damage(double amount, IHytaleEntityContext source);
}
```

---

### LoreProcContext.java — Shared Event Context

Replaces raw engine event objects. Passed from `LoreProcHandler` into `LoreEffectExecutor`.

```java
package com.example.hytalemod.lore;

import com.example.hytalemod.platform.IHytaleEntityContext;

/**
 * Carries contextual data from the triggering event into LoreEffectExecutor.
 * All fields are engine-agnostic.
 */
public class LoreProcContext {

    public final IHytaleEntityContext player;
    public final IHytaleEntityContext target;  // may be null
    public final double               value;   // damage dealt, heal amount, etc.
    public final String               extra;   // statusId, movementState name, etc.

    private LoreProcContext(IHytaleEntityContext player, IHytaleEntityContext target,
                            double value, String extra) {
        this.player = player;
        this.target = target;
        this.value  = value;
        this.extra  = extra;
    }

    public static LoreProcContext hit(IHytaleEntityContext player, IHytaleEntityContext target, double damage) {
        return new LoreProcContext(player, target, damage, null);
    }
    public static LoreProcContext hurt(IHytaleEntityContext player, IHytaleEntityContext source, double damage) {
        return new LoreProcContext(player, source, damage, null);
    }
    public static LoreProcContext kill(IHytaleEntityContext player, IHytaleEntityContext entity) {
        return new LoreProcContext(player, entity, 0, null);
    }
    public static LoreProcContext heal(IHytaleEntityContext player, double amount) {
        return new LoreProcContext(player, null, amount, null);
    }
    public static LoreProcContext status(IHytaleEntityContext player, String statusId) {
        return new LoreProcContext(player, null, 0, statusId);
    }
    public static LoreProcContext movement(IHytaleEntityContext player, String state) {
        return new LoreProcContext(player, null, 0, state);
    }
    public static LoreProcContext empty(IHytaleEntityContext player) {
        return new LoreProcContext(player, null, 0, null);
    }
}
```

---

### LoreProcHandler.java — Engine-Agnostic Event Wiring

```java
package com.example.hytalemod.lore;

import com.example.hytalemod.platform.IHytaleEntityContext;
import com.example.hytalemod.platform.IHytaleEventBus;
import com.example.hytalemod.platform.IHytaleEventBus.*;

/**
 * Subscribes to Hytale engine events and routes them into the Lore proc system.
 * Receives IHytaleEventBus — swap in the real implementation when API is available.
 */
public class LoreProcHandler {

    private final LoreSocketManager  socketManager;
    private final LoreAbsorptionStore absorptionStore;
    private final LoreEffectExecutor  effectExecutor;

    public LoreProcHandler(IHytaleEventBus eventBus,
                           LoreSocketManager socketManager,
                           LoreAbsorptionStore absorptionStore,
                           LoreEffectExecutor effectExecutor) {
        this.socketManager   = socketManager;
        this.absorptionStore = absorptionStore;
        this.effectExecutor  = effectExecutor;
        registerListeners(eventBus);
    }

    // -------------------------------------------------------------------------
    // Listener registration
    // -------------------------------------------------------------------------

    private void registerListeners(IHytaleEventBus bus) {

        // OFFENSIVE
        bus.onPlayerHit((attacker, target, damage, isCrit) -> {
            IHytaleEntityContext player = (IHytaleEntityContext) attacker;
            IHytaleEntityContext victim = (IHytaleEntityContext) target;
            LoreProcContext ctx = LoreProcContext.hit(player, victim, damage);
            fireTrigger(player, LoreTrigger.ON_HIT, ctx);
            if (isCrit) fireTrigger(player, LoreTrigger.ON_CRIT, ctx);
        });

        // DEFENSIVE
        bus.onPlayerHurt((victim, source, damage, mitigation) -> {
            IHytaleEntityContext player = (IHytaleEntityContext) victim;
            LoreProcContext ctx = LoreProcContext.hurt(player, (IHytaleEntityContext) source, damage);
            fireTrigger(player, LoreTrigger.ON_DAMAGE_TAKEN, ctx);
            switch (mitigation) {
                case BLOCKED, PARRIED -> fireTrigger(player, LoreTrigger.ON_BLOCK, ctx);
                case DODGED           -> fireTrigger(player, LoreTrigger.ON_DODGE, ctx);
                default               -> {}
            }
            // Near-death check
            double remaining = player.getHealth() - damage;
            if (remaining > 0 && remaining <= player.getMaxHealth() * 0.20)
                fireTrigger(player, LoreTrigger.ON_NEAR_DEATH, ctx);
        });

        // KILL
        bus.onEntityDeath((entity, killer) -> {
            if (killer == null) return;
            IHytaleEntityContext player = (IHytaleEntityContext) killer;
            LoreProcContext ctx = LoreProcContext.kill(player, (IHytaleEntityContext) entity);
            fireTrigger(player, LoreTrigger.ON_KILL, ctx);
            if (LoreKillEST.isFirstKill(player, entity))
                fireTrigger(player, LoreTrigger.ON_FIRST_KILL, ctx);
        });

        // MOVEMENT
        bus.onMovementState((playerObj, state) -> {
            IHytaleEntityContext player = (IHytaleEntityContext) playerObj;
            LoreProcContext ctx = LoreProcContext.movement(player, state.name());
            switch (state) {
                case SPRINT_START -> fireTrigger(player, LoreTrigger.ON_SPRINT, ctx);
                case JUMP         -> fireTrigger(player, LoreTrigger.ON_JUMP, ctx);
                case SNEAK_START  -> fireTrigger(player, LoreTrigger.ON_SNEAK, ctx);
                default           -> {}
            }
        });

        // RECOVERY
        bus.onHealthRegain((playerObj, amount, source) -> {
            IHytaleEntityContext player = (IHytaleEntityContext) playerObj;
            LoreProcContext ctx = LoreProcContext.heal(player, amount);
            fireTrigger(player, LoreTrigger.ON_HEAL, ctx);
            if (source == HealSource.ITEM)
                fireTrigger(player, LoreTrigger.ON_POTION_USE, ctx);
        });

        // STATUS
        bus.onStatusApplied((playerObj, statusId, appliedByPlayer) -> {
            IHytaleEntityContext player = (IHytaleEntityContext) playerObj;
            fireTrigger(player, LoreTrigger.ON_STATUS_APPLY,
                LoreProcContext.status(player, statusId));
        });
    }

    // -------------------------------------------------------------------------
    // Public hooks — call from skill pipeline, ability system, etc.
    // -------------------------------------------------------------------------

    public void onSkillUse(IHytaleEntityContext player) {
        fireTrigger(player, LoreTrigger.ON_SKILL_USE, LoreProcContext.empty(player));
    }

    public void onLoreProc(IHytaleEntityContext player) {
        fireTrigger(player, LoreTrigger.ON_LORE_PROC, LoreProcContext.empty(player));
    }

    // -------------------------------------------------------------------------
    // Core dispatch
    // -------------------------------------------------------------------------

    private void fireTrigger(IHytaleEntityContext player, LoreTrigger trigger, LoreProcContext ctx) {
        for (LoreSocketData socket : socketManager.getActiveSockets(player)) {
            LoreAbility ability = socket.getAbility();
            if (ability == null) continue;
            if (ability.getTrigger() != trigger) continue;
            if (!socketManager.isCooldownReady(player, socket.getSlotKey())) continue;
            if (Math.random() >= ability.getProcChance()) continue;

            effectExecutor.execute(player, ability.getEffectType(), ability.getScaledValue(), ctx);
            socketManager.stampCooldown(player, socket.getSlotKey(), ability.getCooldownTicks());
            socketManager.grantProcXp(player, socket.getSlotKey());

            // Spirit chaining
            onLoreProc(player);
        }
    }
}
```

---

### LoreEffectExecutor.java — Engine-Agnostic Effect Dispatch

All engine-specific imports removed. All interactions go through `IHytaleEntityContext`.

```java
package com.example.hytalemod.lore;

import com.example.hytalemod.platform.IHytaleEntityContext;

/**
 * Maps LoreEffectType values to game logic via IHytaleEntityContext.
 * Unimplemented cases log a warning; fill them in as the Hytale API matures.
 */
public class LoreEffectExecutor {

    private final LoreSynergyBus synergyBus;

    public LoreEffectExecutor(LoreSynergyBus synergyBus) {
        this.synergyBus = synergyBus;
    }

    public void execute(IHytaleEntityContext player, LoreEffectType effect,
                        double scaledValue, LoreProcContext ctx) {
        switch (effect) {

            // OFFENSIVE
            case BURST_DAMAGE      -> { if (ctx.target != null) ctx.target.damage(scaledValue, player); }
            case LIFESTEAL         -> { double h = ctx.value * (scaledValue / 100.0);
                                        player.setHealth(Math.min(player.getHealth() + h, player.getMaxHealth())); }
            case APPLY_BLEED       -> { if (ctx.target != null) ctx.target.applyStatus("bleed",   scaledValue, 100); }
            case APPLY_POISON      -> { if (ctx.target != null) ctx.target.applyStatus("poison",  scaledValue, 120); }
            case APPLY_BURN        -> { if (ctx.target != null) ctx.target.applyStatus("burn",    scaledValue,  80); }
            case CHAIN_LIGHTNING   -> {
                if (ctx.target != null)
                    ctx.target.getNearbyEntities(6).stream().filter(e -> e != player).limit(3)
                        .forEach(e -> e.damage(scaledValue * 0.6, player));
            }
            case KNOCKBACK         -> { if (ctx.target != null) ctx.target.applyImpulse(scaledValue, 0.4, scaledValue); }
            case FEAR              -> { if (ctx.target != null) ctx.target.applyStatus("fear",    1.0, (int)(scaledValue * 20)); }
            case STAGGER           -> { if (ctx.target != null) ctx.target.applyStatus("stagger", 1.0, 10); }
            case MARK_TARGET       -> { if (ctx.target != null) ctx.target.setTag("lore_mark", scaledValue); }
            case EXECUTE           -> {
                if (ctx.target != null) {
                    double threshold = ctx.target.getMaxHealth() * (scaledValue / 100.0);
                    if (ctx.target.getHealth() <= threshold)
                        ctx.target.damage(ctx.target.getHealth() + 1, player);
                }
            }
            case DOUBLE_STRIKE_PROC -> player.setTag("lore_double_strike", scaledValue);

            // DEFENSIVE
            case RETRIBUTION_DAMAGE -> { if (ctx.target != null) ctx.target.damage(ctx.value * (scaledValue / 100.0), player); }
            case SPAWN_SHIELD_ORB   -> player.setTag("lore_shield_orb", scaledValue);
            case ESCAPE_PROC        -> player.applyImpulse(-scaledValue, 0.3, -scaledValue);
            case ARMOR_BUFF         -> player.applyStatus("armor_up",   scaledValue, (int)(scaledValue * 20));
            case RIPOSTE_STRIKE     -> { if (ctx.target != null) ctx.target.damage(scaledValue, player); }
            case HEAL_ON_HIT        -> player.setHealth(Math.min(player.getHealth() + scaledValue, player.getMaxHealth()));
            case STUN_ATTACKER      -> { if (ctx.target != null) ctx.target.applyStatus("stun", 1.0, (int)(scaledValue * 20)); }
            case DEFLECT_PROJECTILE -> player.setTag("lore_deflect", scaledValue);
            case SHADOW_STEP        -> player.applyImpulse(scaledValue, 0.1, scaledValue);

            // KILL / DEATH
            case HEAL_BURST        -> player.setHealth(Math.min(player.getHealth() + scaledValue, player.getMaxHealth()));
            case SPEED_BURST       -> player.applyStatus("haste",        scaledValue,  60);
            case LAST_STAND_INVULN -> player.applyStatus("invulnerable", 1.0, (int)(scaledValue * 20));
            case REVIVE_PROC       -> player.setTag("lore_revive", true);
            case BARRIER_BURST     -> player.applyStatus("barrier",      scaledValue, 200);
            case VENGEANCE_BUFF    -> player.applyStatus("vengeance",    scaledValue, 100);
            case EXPLODE_CORPSE    -> {
                if (ctx.target != null)
                    ctx.target.getNearbyEntities(scaledValue).stream().filter(e -> e != player)
                        .forEach(e -> e.damage(scaledValue * 1.5, player));
            }
            case SUMMON_SHADE      -> player.setTag("lore_summon_shade", ctx.target != null ? ctx.target.getZoneId() : "");
            case FEAR_NEARBY       -> player.getNearbyEntities(8).stream().filter(e -> e != player)
                                        .forEach(e -> e.applyStatus("fear", 1.0, 60));
            case SOUL_ABSORB       -> {
                Object c = player.getTag("lore_absorb_count");
                player.setTag("lore_absorb_count", (c instanceof Integer i ? i : 0) + 1);
            }
            case LORE_XP_GRANT     -> player.setTag("lore_xp_grant", scaledValue);

            // MOVEMENT
            case SPEED_AMPLIFY     -> player.applyStatus("haste",        scaledValue,  40);
            case FEATHER_FALL      -> player.applyStatus("slow_falling", 1.0,           60);
            case SLAM_DAMAGE       -> player.getNearbyEntities(4).stream().filter(e -> e != player)
                                        .forEach(e -> e.damage(scaledValue, player));
            case VANISH            -> player.applyStatus("invisible",    1.0, (int)(scaledValue * 20));
            case BACKSTAB_AMP      -> player.setTag("lore_backstab", scaledValue);
            case GRAVITY_PULSE     -> player.getNearbyEntities(scaledValue).stream().filter(e -> e != player)
                                        .forEach(e -> e.applyImpulse(0, -0.5, 0));
            case WHIRLWIND_AURA    -> player.getNearbyEntities(3).stream().filter(e -> e != player)
                                        .forEach(e -> e.damage(scaledValue * 0.5, player));
            case DETECT_HIDDEN     -> player.applyStatus("truesight",    1.0, (int)(scaledValue * 20));

            // UTILITY / RECOVERY
            case OVERHEAL_SHIELD   -> {
                double excess = (player.getHealth() + scaledValue) - player.getMaxHealth();
                if (excess > 0) player.setTag("lore_shield_orb", excess);
                else player.setHealth(Math.min(player.getHealth() + scaledValue, player.getMaxHealth()));
            }
            case CLEANSE_DEBUFF      -> player.removeStatus("bleed"); // iterate all negatives via Hytale API
            case INSTANT_COOLDOWN_RESET -> player.setTag("lore_reset_cooldowns", true);
            case PROC_ECHO           -> { /* LoreProcHandler handles re-fire */ }
            case SYNERGY_TRIGGER     -> synergyBus.emit(player, scaledValue);
            case SURGE_OF_LIFE       -> player.applyStatus("damage_amp",  scaledValue,  60);
            case RESISTANCE_UP       -> player.applyStatus("resist_all",  scaledValue, 100);
            case DEBUFF_SPREAD       -> {
                if (ctx.extra != null)
                    player.getNearbyEntities(scaledValue).stream().filter(e -> e != player)
                        .forEach(e -> e.applyStatus(ctx.extra, 1.0, 60));
            }
            case STACK_EXPLOIT       -> {
                if (ctx.target != null && ctx.target.hasStatus("lore_mark"))
                    ctx.target.damage(scaledValue * 2.0, player);
            }

            default -> System.out.println("[LoreSystem] Unimplemented LoreEffectType: " + effect.name());
        }
    }
}
```

---

### lore_abilities.json — Config Entries (Hytale-Friendly Format)

Flat JSON — readable by both the Java mod layer and Hytale's native scripting.
One sample entry per trigger type (all 16 triggers covered).

```json
{
  "abilities": {

    "phantom_bite": {
      "trigger": "ON_HIT",
      "effect": "APPLY_BLEED",
      "proc_chance": 0.18,
      "cooldown_ticks": 40,
      "base_value": 3.0,
      "level_scale": 0.05,
      "description": "Each strike has a chance to cause Bleeding."
    },

    "soulshatter": {
      "trigger": "ON_CRIT",
      "effect": "SHATTER",
      "proc_chance": 1.0,
      "cooldown_ticks": 200,
      "base_value": 8.0,
      "level_scale": 0.12,
      "description": "Critical hits shatter frozen targets for massive damage."
    },

    "echo_weave": {
      "trigger": "ON_SKILL_USE",
      "effect": "ECHO_CAST",
      "proc_chance": 0.25,
      "cooldown_ticks": 100,
      "base_value": 0.5,
      "level_scale": 0.005,
      "description": "Skills have a chance to echo at half power."
    },

    "blood_ward": {
      "trigger": "ON_DAMAGE_TAKEN",
      "effect": "SPAWN_SHIELD_ORB",
      "proc_chance": 0.12,
      "cooldown_ticks": 60,
      "base_value": 5.0,
      "level_scale": 0.10,
      "description": "Taking damage may conjure a protective shield orb."
    },

    "windstep": {
      "trigger": "ON_DODGE",
      "effect": "HASTE_BUFF",
      "proc_chance": 1.0,
      "cooldown_ticks": 80,
      "base_value": 1.0,
      "level_scale": 0.0,
      "description": "Dodging grants a burst of speed."
    },

    "predator": {
      "trigger": "ON_KILL",
      "effect": "DAMAGE_AMP_STACK",
      "proc_chance": 1.0,
      "cooldown_ticks": 0,
      "base_value": 0.04,
      "level_scale": 0.001,
      "description": "Each kill stacks a damage bonus."
    },

    "iron_will": {
      "trigger": "ON_NEAR_DEATH",
      "effect": "LAST_STAND_INVULN",
      "proc_chance": 1.0,
      "cooldown_ticks": 6000,
      "base_value": 3.0,
      "level_scale": 0.05,
      "description": "Near death, briefly become invulnerable."
    },

    "verdant_striker": {
      "trigger": "ON_FIRST_KILL",
      "effect": "SPIRIT_ASSIGNMENT",
      "proc_chance": 1.0,
      "cooldown_ticks": 0,
      "base_value": 1.0,
      "level_scale": 0.0,
      "zone_pool": "zone1_verdant",
      "description": "The first kill in Verdant Grove awakens a spirit within the gem."
    },

    "gale_runner": {
      "trigger": "ON_SPRINT",
      "effect": "GROUND_TRAIL_EFFECT",
      "proc_chance": 1.0,
      "cooldown_ticks": 20,
      "base_value": 2.0,
      "level_scale": 0.03,
      "description": "Sprinting leaves a damaging trail."
    },

    "falling_star": {
      "trigger": "ON_JUMP",
      "effect": "SLAM_DAMAGE",
      "proc_chance": 0.40,
      "cooldown_ticks": 60,
      "base_value": 6.0,
      "level_scale": 0.08,
      "description": "Landing from a jump may send out a shockwave."
    },

    "shadow_step_spirit": {
      "trigger": "ON_SNEAK",
      "effect": "VANISH",
      "proc_chance": 0.30,
      "cooldown_ticks": 200,
      "base_value": 2.0,
      "level_scale": 0.02,
      "description": "Sneaking may briefly render you invisible."
    },

    "bloom_spirit": {
      "trigger": "ON_HEAL",
      "effect": "OVERHEAL_SHIELD",
      "proc_chance": 1.0,
      "cooldown_ticks": 100,
      "base_value": 0.5,
      "level_scale": 0.005,
      "description": "Excess healing converts into a protective shield."
    },

    "root_draught": {
      "trigger": "ON_POTION_USE",
      "effect": "RESISTANCE_UP",
      "proc_chance": 1.0,
      "cooldown_ticks": 300,
      "base_value": 2.0,
      "level_scale": 0.02,
      "description": "Consuming a potion bolsters your resistances."
    },

    "resonance_chain": {
      "trigger": "ON_LORE_PROC",
      "effect": "SYNERGY_TRIGGER",
      "proc_chance": 0.20,
      "cooldown_ticks": 200,
      "base_value": 1.0,
      "level_scale": 0.0,
      "description": "When any spirit procs, this spirit may echo in sympathy."
    },

    "plague_herald": {
      "trigger": "ON_STATUS_APPLY",
      "effect": "DEBUFF_SPREAD",
      "proc_chance": 0.30,
      "cooldown_ticks": 60,
      "base_value": 5.0,
      "level_scale": 0.05,
      "description": "Applying a status may spread it to nearby enemies."
    }
  }
}
```

---

### lore_spirits.json — Zone-Aware Spirit Pool (Sample)

Gem color + Zone determines which spirit pool is drawn from on `ON_FIRST_KILL`.

```json
{
  "spirit_pools": {

    "zone1_verdant": {
      "gem_colors": ["green", "white"],
      "spirits": [
        { "id": "thornweald",  "ability": "phantom_bite", "flavor": "A spirit of the deep root, hungry and patient." },
        { "id": "canopy_rush", "ability": "gale_runner",  "flavor": "Born from the wind that races through the high branches." },
        { "id": "bloom_echo",  "ability": "bloom_spirit", "flavor": "It remembers every wound the forest healed." }
      ]
    },

    "zone2_sands": {
      "gem_colors": ["amber", "red"],
      "spirits": [
        { "id": "dune_stalker",  "ability": "predator",    "flavor": "Patient as the desert. Merciless as the sun." },
        { "id": "mirageweave",   "ability": "echo_weave",  "flavor": "It learned deception from the heat shimmer." },
        { "id": "searing_brand", "ability": "soulshatter", "flavor": "Forged in the first fire beneath the sand." }
      ]
    },

    "zone3_tundra": {
      "gem_colors": ["blue", "white"],
      "spirits": [
        { "id": "frostfall",   "ability": "falling_star", "flavor": "It descends with the avalanche, unstoppable." },
        { "id": "pale_shroud", "ability": "iron_will",    "flavor": "Survived every winter. It knows how to endure." },
        { "id": "sleetbind",   "ability": "blood_ward",   "flavor": "Freezes the wound shut before you feel it." }
      ]
    }
  }
}
```

---

## Next Steps (Optional)
- Wire `IHytaleEventBus` methods to real Hytale API event types when the modding API releases.
- Add a UI flow to feed lore gems (consume Resonant Essence at gates).
- Add a UI page for inserting/removing lore gems.
- Expand `lore_spirits.json` with additional zone pools (Votum, etc.).
- Implement remaining `LoreEffectExecutor` cases using real Hytale status/entity APIs.
- Register `LoreProcHandler` in `HytaleMod.java` (mod entry point).
- Extend `DynamicTooltipUtils.java` to show trigger name, proc chance, and zone flavor text.

---

## Testing Recommendations
1. Roll chest/NPC loot and confirm lore sockets appear on eligible items.
2. Socket a lore gem and verify first kill (in the correct Zone) assigns a spirit.
3. Confirm proc triggers and cooldowns fire correctly for all 16 trigger types.
4. Verify feed gates every 5 levels and absorption at level 100.
5. Confirm tooltips show: socket count, spirit name, level, ability text, zone flavor.
6. Test each trigger category in isolation (sprint, jump, sneak, heal, near-death).
7. Test `ON_LORE_PROC` chaining — two spirits on the same item must not infinite-loop.
8. Verify zone pool selection: same gem color yields different spirits in different Zones.
