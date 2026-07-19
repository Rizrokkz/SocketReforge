# Elemental Affinity and Essence Mutation Design

## Core Idea

Separate an essence socket's **essence identity** from its **elemental affinity**.

- **Essence type** controls current socket stat bonuses and resonance recipes.
- **Elemental affinity** controls damage effectiveness against enemies.
- **Resonant Essence** can be used as a support material to reroll a filled socket's elemental mutation.

Example:

```text
Socket: Void Essence
Stat Bonus: Crit Damage
Resonance Type: Void
Mutation Element: Fire
Damage Affinity: Fire
```

This lets players build a Fire-affinity weapon without requiring every socket to contain Fire essence.

## Element Types

Recommended shared element enum:

```text
PHYSICAL
FIRE
ICE
LIGHTNING
WATER
LIFE
VOID
```

`PHYSICAL` is the fallback for weapons with no elemental affinity.

## Socket Data Model

Each essence socket should store:

```text
essenceId
mutationElement
mutationQuality optional
```

Recommended metadata:

```text
SocketReforge.Socket.Values = Essence_Void, Essence_Life, Essence_Lightning
SocketReforge.Socket.Mutations = FIRE, FIRE, FIRE
```

Separate metadata arrays are preferred over encoding values like `Essence_Void|FIRE`, because arrays are cleaner to migrate and easier to inspect.

Existing items should migrate as:

```text
mutationElement = NONE
```

If no mutation exists, elemental affinity falls back to the socket's essence type.

## Weapon Affinity Rules

For each filled socket:

1. If the socket has a mutation element, use that element.
2. Otherwise, fall back to the essence type.
3. Empty or broken sockets contribute nothing.

Example:

```text
Slot 1: Void Essence mutated Fire
Slot 2: Life Essence mutated Fire
Slot 3: Lightning Essence mutated Fire

Weapon Element Profile:
Fire: 3
Dominant Element: Fire
```

Start with a **dominant element** model. Split elemental damage can be added later if needed.

## Damage Formula

Recommended first-pass formula:

```text
finalDamage = baseDamage
            × refinementMultiplier
            × socketDamageMultiplier
            × resonanceMultiplier
            × elementalEffectiveness
```

Example:

```text
Weapon dominant element: Fire
Enemy fire modifier: 1.25
Damage before element: 100
Final damage: 125
```

## Mob Affinity Config

Example config:

```json
{
  "Zombie_Burnt": {
    "FIRE": 0.50,
    "ICE": 1.25,
    "WATER": 1.15,
    "VOID": 1.00
  },
  "Emberwulf": {
    "FIRE": 0.25,
    "ICE": 1.35,
    "WATER": 1.20
  },
  "Cave_Rex": {
    "LIGHTNING": 1.20,
    "VOID": 0.85
  }
}
```

Suggested balance ranges:

```text
Weakness: 1.15x to 1.35x
Resistance: 0.65x to 0.85x
Strong resistance: 0.35x to 0.50x
Neutral: 1.00x
```

Avoid full immunity except for special bosses.

## Mutation System

Use **Resonant Essence** as the mutation support material.

Rules:

```text
Selected filled socket + Resonant Essence support = reroll mutation element
```

Possible mutation results:

```text
Fire
Ice
Lightning
Water
Life
Void
```

Optional future outcomes:

```text
No mutation
Same element
Rare perfect mutation
Unstable mutation
```

Suggested config:

```json
{
  "mutationEnabled": true,
  "mutationRequiresResonantEssence": true,
  "allowSameElementMutation": true,
  "mutationWeights": {
    "FIRE": 1,
    "ICE": 1,
    "LIGHTNING": 1,
    "WATER": 1,
    "LIFE": 1,
    "VOID": 1
  }
}
```

## Resonance Rule

Resonance should continue to use the original essence type, not the mutation.

Example:

```text
Void Essence mutated Fire
Counts as Void for resonance recipes.
Counts as Fire for damage affinity.
```

This preserves existing resonance recipes while adding elemental build control.

## Essence Bench UI Changes

For each essence socket:

- Main icon: actual essence icon.
- Corner overlay or small colored mark: mutation element.
- Socket info should show essence identity, stat bonus, mutation, and resonance contribution.

Example socket info:

```text
Essence: Void
Bonus: +Crit Damage
Mutation: Fire
Elemental Affinity: Fire
Counts as Void for resonance.
```

When Resonant Essence is selected as support:

```text
Process button: Mutate Element
```

If the selected socket has no essence:

```text
Select a filled essence socket to mutate.
```

## Weapon Tooltip Changes

Add elemental profile display:

```text
Elemental Affinity
Dominant: Fire
Fire: 3 sockets
Void: 0 sockets
Lightning: 0 sockets
```

Compact alternative:

```text
Element: Fire III
```

If no mutation or elemental socket exists:

```text
Element: Physical
```

## Combat Feedback

Optional floating damage or combat feedback:

```text
125 Fire Damage
Effective!
```

Resistance example:

```text
72 Fire Damage
Resisted
```

## Suggested Classes

New reusable classes:

```text
ElementType
ElementalAffinityConfig
ElementalAffinityService
ElementalMutationRoller
```

Responsibilities:

- `ElementType`: shared enum for element names.
- `ElementalAffinityConfig`: mob modifiers and mutation weights.
- `ElementalAffinityService`: resolves weapon element profile and enemy effectiveness.
- `ElementalMutationRoller`: rolls mutation results.

## Systems To Touch

Likely implementation areas:

- `SocketData` / `Socket`: store mutation element.
- `SocketManager`: read/write mutation metadata.
- `EssenceBenchUI`: select socket and mutate element with Resonant Essence support.
- Combat damage systems: apply elemental effectiveness during damage calculation.
- `DynamicTooltipUtils`: show mutation and elemental affinity info.
- Runtime config UI: expose affinity and mutation balancing later.

## Implementation Order

Recommended order:

1. Add `ElementType` enum.
2. Add mutation metadata array to socket read/write.
3. Add fallback element resolution from essence type.
4. Add weapon dominant-element resolver.
5. Add mob affinity config with neutral defaults.
6. Apply elemental multiplier in combat damage path.
7. Add Essence Bench mutation support using Resonant Essence.
8. Add tooltip and UI display.
9. Add runtime config controls later.

## Balance Notes

Recommended first version:

- Mutation does not increase raw damage by itself.
- Mutation only changes elemental effectiveness type.
- Enemy weaknesses and resistances create the value.
- Resonant Essence is consumed per mutation roll.
- Same-element rolls are allowed initially unless players dislike the feel.

## Why This Design Works

- Keeps existing socket bonuses intact.
- Keeps resonance recipes intact.
- Adds meaningful build identity.
- Gives Resonant Essence more long-term use.
- Allows Fire-mutated Void, Water-mutated Lightning, and other hybrid builds.
- Keeps future lore spirit and enemy-family interactions open.
