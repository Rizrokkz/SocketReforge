# Resonance + Lore Integration

> **Platform:** Hytale (engine-agnostic interfaces)
> This document covers the bridge between the Resonance System (`ResonanceType` procs)
> and the Lore System (`LoreTrigger` / spirit abilities). Read alongside:
> - `SPIRIT_SYSTEM_IMPLEMENTATION.md` — full Lore system spec
> - `RESONANCE_SYSTEM_DOCUMENTATION.md` — full Resonance system spec

---

## Overview

The Resonance System and the Lore System are parallel systems that, by default, never
interact. This document defines the **one-way bridge** that connects them:

> When a `ResonanceType` proc fires, it emits one or two `LoreTrigger` events into
> `LoreProcHandler`. Spirits socketed on the same item can then react. The bridge is
> strictly one-way — spirits never fire back into resonance.

### Execution order

```
Game event (hit, kill, dodge…)
  └── ResonanceSystem.evaluate()  →  ResonanceType proc fires
        └── ResonanceTriggerBridge.emit()  →  LoreTrigger emitted
              └── LoreProcHandler.onResonanceTriggered()  →  spirit proc fires
                    └── [ON_LORE_PROC chain — loop guard prevents re-entry]
```

---

## Trigger Mapping

Each `ResonanceType` maps to one or two `LoreTrigger` values. Where two are listed, both
fire in order within the same bridge call.

### Offensive resonances

| ResonanceType | LoreTrigger(s) emitted | Emit condition |
|---|---|---|
| `BURN_ON_CRIT` | `ON_CRIT` → `ON_STATUS_APPLY` | Fires ON_CRIT when burn procs; then ON_STATUS_APPLY so spirits can react to the burn being applied |
| `THUNDER_STRIKE` | `ON_HIT` | Lightning bonus damage lands — spirits treat this as a second hit |
| `ARMOR_SHRED` | `ON_HIT` → `ON_STATUS_APPLY` | ON_HIT on shred proc; ON_STATUS_APPLY with `statusId="armor_shred"` so STACK_EXPLOIT spirits can exploit it |
| `CHAIN_SLOW` | `ON_HIT` → `ON_STATUS_APPLY` | ON_HIT when chain lands; ON_STATUS_APPLY with `statusId="slow"` — DEBUFF_SPREAD spirits chain naturally |
| `EXECUTE` | `ON_KILL` | Execute counts as a kill — DAMAGE_AMP_STACK, SOUL_ABSORB, FEAR_NEARBY all benefit |
| `MULTISHOT_BARRAGE` | `ON_HIT` | Each arrow that connects emits ON_HIT individually — up to 3 spirit procs per shot |
| `PLUNDERING_BLADE` | `ON_HIT` | Fires ON_HIT at proc; LOOT_BONUS spirits stack naturally |

### Defensive resonances

| ResonanceType | LoreTrigger(s) emitted | Emit condition |
|---|---|---|
| `FROST_NOVA_ON_HIT` | `ON_DAMAGE_TAKEN` → `ON_STATUS_APPLY` | ON_DAMAGE_TAKEN when nova triggers; ON_STATUS_APPLY with `statusId="freeze"` for spirit chaining |
| `THORNS_SHOCK` | `ON_DAMAGE_TAKEN` | Reflect damage landed — RETRIBUTION_DAMAGE and RAGE_STACK spirits benefit |
| `AURA_BURN` | `ON_DAMAGE_TAKEN` → `ON_STATUS_APPLY` | ON_DAMAGE_TAKEN on trigger; ON_STATUS_APPLY with `statusId="burn"` — DEBUFF_SPREAD spirits spread it |
| `SHOCK_DODGE` | `ON_DODGE` | Directly maps — SHADOW_STEP, COUNTER_DAMAGE, EVASION_STACK all fire here |
| `CHEAT_DEATH` | `ON_NEAR_DEATH` | Survival moment — LAST_STAND_INVULN and VENGEANCE_BUFF stack perfectly |

### Recovery resonances

| ResonanceType | LoreTrigger(s) emitted | Emit condition |
|---|---|---|
| `HEAL_SURGE` | `ON_HEAL` | Surge heal landed — OVERHEAL_SHIELD and HEAL_AMP spirits chain from this |
| `CROSSBOW_AUTO_RELOAD` | `ON_SKILL_USE` | Reload treated as a skill action — COOLDOWN_REDUCTION and PROC_HASTE react |

### Emit rules

| Rule | Detail |
|---|---|
| Order | Resonance effect fires first → LoreTrigger emitted second → spirit proc fires third |
| Loop guard | `ON_LORE_PROC` must NOT re-emit a resonance trigger — one resonance proc per event chain max |
| ICD | Resonance trigger emission shares the resonance ICD — if the resonance is on cooldown, no LoreTrigger fires |
| Context | Pass `source = "resonance"` in `LoreProcContext` so spirit executor can optionally handle resonance-sourced procs differently |

---

## New Classes

### ResonanceTriggerBridge.java

```java
package com.example.hytalemod.lore;

import com.example.hytalemod.platform.IHytaleEntityContext;

/**
 * One-way bridge: when a ResonanceType proc fires, emit the
 * corresponding LoreTrigger(s) into LoreProcHandler.
 *
 * Call ResonanceTriggerBridge.emit(...) at the END of each
 * ResonanceType's effect block in your resonance execution pipeline.
 *
 * Rules:
 *  - Resonance fires first, LoreTrigger emitted second.
 *  - ON_LORE_PROC must NOT re-enter this bridge (loop guard in LoreProcHandler).
 *  - Emission is gated by the resonance's own ICD — if the resonance
 *    is on cooldown, this method is never reached.
 *  - source="resonance" is set in context so spirits can optionally
 *    distinguish resonance-sourced procs from direct procs.
 */
public class ResonanceTriggerBridge {

    private final LoreProcHandler loreProcHandler;

    public ResonanceTriggerBridge(LoreProcHandler loreProcHandler) {
        this.loreProcHandler = loreProcHandler;
    }

    /**
     * Call this after a resonance proc fires.
     *
     * @param player    The player who owns the item.
     * @param type      The ResonanceType that just fired.
     * @param target    The entity that was hit or hit the player (may be null).
     * @param procValue The scaled proc value (damage, heal amount, etc.).
     * @param statusId  The status ID applied by this proc, if any. Null if none.
     */
    public void emit(IHytaleEntityContext player, ResonanceType type,
                     IHytaleEntityContext target, double procValue, String statusId) {
        switch (type) {

            // -----------------------------------------------------------------
            // OFFENSIVE
            // -----------------------------------------------------------------

            case BURN_ON_CRIT -> {
                // 1. Crit proc fired — spirits with ON_CRIT react
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_CRIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
                // 2. Burn was applied — spirits with ON_STATUS_APPLY react
                if (statusId != null) {
                    loreProcHandler.onResonanceTriggered(
                        player, LoreTrigger.ON_STATUS_APPLY,
                        LoreProcContext.resonanceStatus(player, statusId)
                    );
                }
            }

            case THUNDER_STRIKE -> {
                // Lightning bonus hit — treat as a second ON_HIT
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
            }

            case ARMOR_SHRED -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
                // "armor_shred" status — STACK_EXPLOIT spirits can exploit it
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_STATUS_APPLY,
                    LoreProcContext.resonanceStatus(player, "armor_shred")
                );
            }

            case CHAIN_SLOW -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_STATUS_APPLY,
                    LoreProcContext.resonanceStatus(player, "slow")
                );
            }

            case EXECUTE -> {
                // Execute counts as a kill — DAMAGE_AMP_STACK, SOUL_ABSORB, FEAR_NEARBY benefit
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_KILL,
                    LoreProcContext.resonanceKill(player, target)
                );
            }

            case MULTISHOT_BARRAGE -> {
                // Call emit() once per arrow that lands, not once per shot
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
            }

            case PLUNDERING_BLADE -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HIT,
                    LoreProcContext.resonanceHit(player, target, procValue)
                );
            }

            // -----------------------------------------------------------------
            // DEFENSIVE
            // -----------------------------------------------------------------

            case FROST_NOVA_ON_HIT -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_DAMAGE_TAKEN,
                    LoreProcContext.resonanceHurt(player, target, procValue)
                );
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_STATUS_APPLY,
                    LoreProcContext.resonanceStatus(player, "freeze")
                );
            }

            case THORNS_SHOCK -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_DAMAGE_TAKEN,
                    LoreProcContext.resonanceHurt(player, target, procValue)
                );
            }

            case AURA_BURN -> {
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_DAMAGE_TAKEN,
                    LoreProcContext.resonanceHurt(player, target, procValue)
                );
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_STATUS_APPLY,
                    LoreProcContext.resonanceStatus(player, "burn")
                );
            }

            case SHOCK_DODGE -> {
                // Directly maps — SHADOW_STEP, COUNTER_DAMAGE, EVASION_STACK all fire here
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_DODGE,
                    LoreProcContext.resonanceEmpty(player)
                );
            }

            case CHEAT_DEATH -> {
                // Survival moment — LAST_STAND_INVULN and VENGEANCE_BUFF stack here
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_NEAR_DEATH,
                    LoreProcContext.resonanceEmpty(player)
                );
            }

            // -----------------------------------------------------------------
            // RECOVERY
            // -----------------------------------------------------------------

            case HEAL_SURGE -> {
                // Surge heal landed — OVERHEAL_SHIELD and HEAL_AMP spirits chain
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_HEAL,
                    LoreProcContext.resonanceHeal(player, procValue)
                );
            }

            case CROSSBOW_AUTO_RELOAD -> {
                // Reload as skill use — COOLDOWN_REDUCTION and PROC_HASTE react
                loreProcHandler.onResonanceTriggered(
                    player, LoreTrigger.ON_SKILL_USE,
                    LoreProcContext.resonanceEmpty(player)
                );
            }

            case NONE -> {
                // No trigger to emit
            }
        }
    }
}
```

---

## Modified Classes

### LoreProcHandler.java — add `onResonanceTriggered()`

Add this method and the `resonanceActive` guard flag to the existing `LoreProcHandler`.

```java
// Inside LoreProcHandler.java

/**
 * Guard flag — prevents ON_LORE_PROC from looping back into the bridge.
 * Set to true for the entire duration of a resonance-sourced trigger chain.
 */
private boolean resonanceActive = false;

/**
 * Entry point called exclusively by ResonanceTriggerBridge.
 * Identical to fireTrigger() but sets a loop guard so that any
 * ON_LORE_PROC chain spawned here cannot re-emit a resonance trigger.
 */
public void onResonanceTriggered(IHytaleEntityContext player,
                                  LoreTrigger trigger,
                                  LoreProcContext ctx) {
    if (resonanceActive) return; // loop guard — already inside a resonance chain
    resonanceActive = true;
    try {
        fireTrigger(player, trigger, ctx);
    } finally {
        resonanceActive = false;
    }
}

// Modify the existing onLoreProc() to respect the guard:
public void onLoreProc(IHytaleEntityContext player) {
    if (resonanceActive) return; // don't chain back into resonance
    fireTrigger(player, LoreTrigger.ON_LORE_PROC, LoreProcContext.empty(player));
}
```

---

### LoreProcContext.java — add `source` field + resonance factories

Add a `source` field and four new factory methods to the existing `LoreProcContext`.

```java
// Inside LoreProcContext.java

// Add source field to the class:
public final String source; // "direct" | "resonance" | "lore_chain"

// Update the private constructor:
private LoreProcContext(IHytaleEntityContext player, IHytaleEntityContext target,
                        double value, String extra, String source) {
    this.player = player;
    this.target = target;
    this.value  = value;
    this.extra  = extra;
    this.source = source;
}

// Update existing factories to pass "direct":
public static LoreProcContext hit(IHytaleEntityContext player,
                                   IHytaleEntityContext target, double damage) {
    return new LoreProcContext(player, target, damage, null, "direct");
}
public static LoreProcContext hurt(IHytaleEntityContext player,
                                    IHytaleEntityContext source, double damage) {
    return new LoreProcContext(player, source, damage, null, "direct");
}
public static LoreProcContext kill(IHytaleEntityContext player,
                                    IHytaleEntityContext entity) {
    return new LoreProcContext(player, entity, 0, null, "direct");
}
public static LoreProcContext heal(IHytaleEntityContext player, double amount) {
    return new LoreProcContext(player, null, amount, null, "direct");
}
public static LoreProcContext status(IHytaleEntityContext player, String statusId) {
    return new LoreProcContext(player, null, 0, statusId, "direct");
}
public static LoreProcContext empty(IHytaleEntityContext player) {
    return new LoreProcContext(player, null, 0, null, "direct");
}

// New resonance-sourced factories:
public static LoreProcContext resonanceHit(IHytaleEntityContext player,
                                            IHytaleEntityContext target, double damage) {
    return new LoreProcContext(player, target, damage, null, "resonance");
}
public static LoreProcContext resonanceHurt(IHytaleEntityContext player,
                                             IHytaleEntityContext source, double damage) {
    return new LoreProcContext(player, source, damage, null, "resonance");
}
public static LoreProcContext resonanceKill(IHytaleEntityContext player,
                                             IHytaleEntityContext entity) {
    return new LoreProcContext(player, entity, 0, null, "resonance");
}
public static LoreProcContext resonanceHeal(IHytaleEntityContext player, double amount) {
    return new LoreProcContext(player, null, amount, null, "resonance");
}
public static LoreProcContext resonanceStatus(IHytaleEntityContext player, String statusId) {
    return new LoreProcContext(player, null, 0, statusId, "resonance");
}
public static LoreProcContext resonanceEmpty(IHytaleEntityContext player) {
    return new LoreProcContext(player, null, 0, null, "resonance");
}
```

---

## Wiring — call site example

In your resonance execution pipeline, after each `ResonanceType` effect fires:

```java
// Inside your resonance effect executor (wherever ResonanceType procs are handled)

case BURN_ON_CRIT -> {
    // 1. Apply the burn
    target.applyStatus("burn", scaledValue, 80);

    // 2. Emit to Lore system — spirits react
    resonanceTriggerBridge.emit(
        player,
        ResonanceType.BURN_ON_CRIT,
        target,
        scaledValue,
        "burn"      // statusId that was just applied
    );
}

case EXECUTE -> {
    // 1. Deal execute damage
    if (target.getHealth() <= target.getMaxHealth() * executeThreshold)
        target.damage(target.getHealth() + 1, player);

    // 2. Emit — execute counts as a kill for spirits
    resonanceTriggerBridge.emit(
        player,
        ResonanceType.EXECUTE,
        target,
        scaledValue,
        null
    );
}

case CHEAT_DEATH -> {
    // 1. Survive at 1 HP
    player.setHealth(1.0);

    // 2. Emit near-death — LAST_STAND_INVULN and VENGEANCE_BUFF stack here
    resonanceTriggerBridge.emit(
        player,
        ResonanceType.CHEAT_DEATH,
        null,
        0,
        null
    );
}

case MULTISHOT_BARRAGE -> {
    // Call once per arrow that connects, not once per shot
    for (IHytaleEntityContext arrowTarget : hitTargets) {
        arrowTarget.damage(scaledValue, player);
        resonanceTriggerBridge.emit(
            player,
            ResonanceType.MULTISHOT_BARRAGE,
            arrowTarget,
            scaledValue,
            null
        );
    }
}
```

---

## Registration in HytaleMod.java

```java
// In your mod entry point — order matters

LoreSynergyBus       synergyBus       = new LoreSynergyBus();
LoreEffectExecutor   effectExecutor   = new LoreEffectExecutor(synergyBus);
LoreProcHandler      loreProcHandler  = new LoreProcHandler(
    eventBus, socketManager, absorptionStore, effectExecutor
);

// Bridge wires the two systems — must be created after LoreProcHandler
ResonanceTriggerBridge resonanceBridge = new ResonanceTriggerBridge(loreProcHandler);

// Pass resonanceBridge into your resonance execution pipeline
ResonanceEffectExecutor resonanceExecutor = new ResonanceEffectExecutor(resonanceBridge);
```

---

## Synergy Examples

The three most powerful combinations this bridge enables:

### 1. Cheat Death + Iron Will — stacked survival

**Item:** Phoenix Aegis armor (`CHEAT_DEATH` resonance) + `iron_will` spirit (`ON_NEAR_DEATH` → `LAST_STAND_INVULN`)

```
Player drops to 0 HP
  └── CHEAT_DEATH fires → player survives at 1 HP
        └── Bridge emits ON_NEAR_DEATH
              └── iron_will spirit procs → LAST_STAND_INVULN (brief invulnerability)
```

Surviving a lethal hit triggers both, giving a stacked invulnerability window. Feels
intentional and earned — two systems working together toward the same fantasy.

---

### 2. Execute + Predator — kill snowball

**Item:** Butcher's Mark axe (`EXECUTE` resonance) + `predator` spirit (`ON_KILL` → `DAMAGE_AMP_STACK`)

```
Player executes a low-HP enemy
  └── EXECUTE fires → bonus kill damage
        └── Bridge emits ON_KILL
              └── predator spirit procs → +4% DAMAGE_AMP_STACK
```

Executes count as kills, so every execution stacks the damage amp. High-tempo combat
with a natural snowball — the more you kill, the faster you kill.

---

### 3. Aura Burn + Plague Herald — fire spread

**Item:** Sunplate armor (`AURA_BURN` resonance) + `plague_herald` spirit (`ON_STATUS_APPLY` → `DEBUFF_SPREAD`)

```
Player is hit
  └── AURA_BURN fires → burn applied to attacker (statusId="burn")
        └── Bridge emits ON_STATUS_APPLY("burn")
              └── plague_herald spirit procs → DEBUFF_SPREAD (burn spreads to nearby enemies)
```

The armor's aura applies burn, which fires `ON_STATUS_APPLY`, which spreads burn to
nearby enemies. A walking fire hazard build — purely defensive armor that turns into
area denial.

---

## Optional: Spirit ability that explicitly requires resonance source

Some spirit abilities can check `ctx.source` to only fire when triggered by resonance,
not by direct combat. Add this as an optional field to `lore_abilities.json`:

```json
"resonance_echo": {
  "trigger": "ON_HIT",
  "effect": "BURST_DAMAGE",
  "proc_chance": 0.50,
  "cooldown_ticks": 20,
  "base_value": 4.0,
  "level_scale": 0.06,
  "require_source": "resonance",
  "description": "Resonance hits have a chance to echo for bonus damage."
}
```

And in `LoreEffectExecutor`, gate on source before firing:

```java
// Inside fireTrigger() in LoreProcHandler, before executing the effect:
String requiredSource = ability.getRequiredSource(); // null if not set
if (requiredSource != null && !requiredSource.equals(ctx.source)) continue;
```

This allows spirits designed specifically to amplify resonance procs — a unique
design space that neither system could express alone.

---

## Testing Recommendations

1. Trigger each `ResonanceType` proc and verify the correct `LoreTrigger` fires in `LoreProcHandler`.
2. Confirm `resonanceActive` guard prevents infinite loops — socket two spirits, one with `ON_LORE_PROC`, and verify it does not cycle.
3. Verify `MULTISHOT_BARRAGE` emits `ON_HIT` once per arrow, not once per shot.
4. Verify `EXECUTE` emit counts toward `predator` spirit's kill stack.
5. Test `ctx.source == "resonance"` filtering with a `require_source` spirit.
6. Confirm resonance ICD prevents the bridge from emitting when resonance is on cooldown.
7. Test all three synergy examples in isolation: Cheat Death + Iron Will, Execute + Predator, Aura Burn + Plague Herald.

---

## Files Modified by this Integration

| File | Change |
|---|---|
| `Lore/ResonanceTriggerBridge.java` | New — owns the full emit() dispatch |
| `Lore/LoreProcHandler.java` | Add `resonanceActive` flag + `onResonanceTriggered()` method; guard `onLoreProc()` |
| `Lore/LoreProcContext.java` | Add `source` field; add 6 resonance factory methods; update existing factories |
| `HytaleMod.java` | Instantiate `ResonanceTriggerBridge` after `LoreProcHandler`; pass to resonance executor |
| `lore_abilities.json` | Optional: add `require_source` field to spirits that should only fire on resonance procs |
