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

Implemented first-pass formula:

```text
ecsDamageOutput = baseDamage
                x refinementMultiplier
                x socketDamageMultiplier
                x partsMultiplier
                + flatSocketDamage

dominantAffinityBonus = ecsDamageOutput x (dominantElementWeight x 5%)
finalDamage = ecsDamageOutput + dominantAffinityBonus
```

Example:

```text
Weapon dominant element: Fire
Fire affinity weight: 3.5
Damage before affinity: 100
Affinity bonus: 17.5 Fire damage
Final damage: 117.5
```

Current implementation notes:

- Weapon affinity damage is based only on the ECS weapon damage output.
- Only one affinity can apply per weapon hit: the dominant socketed essence element.
- Normal essences add `1.0` affinity weight; greater/concentrated essences add `1.5`.
- If a socket has a stored mutation element, that mutation determines the affinity element.
- If no mutation is stored, the socket falls back to its original essence element.
- Empty and broken sockets do not contribute.
- Ties keep the first dominant element encountered in socket order.
- Monster elemental effectiveness applies only to the additional affinity damage bonus.

## First-Pass Elemental Matchups

Elemental matchups use two tracks:

- **Natural elements** use a one-way ascendant cycle.
- **Life/Void** stay as a separate mutually opposed axis.

```text
Ice       > Fire
Fire      > Water
Water     > Lightning
Lightning > Ice

Life      > Void
Void      > Life
```

First-pass multipliers:

```text
Weakness element:          1.15x to 1.30x affinity bonus damage
Off element:               0.40x to 0.60x affinity bonus damage
Same element:              0.10x to 0.20x affinity bonus damage
Neutral/unmatched:         1.00x affinity bonus damage
```

This keeps the four currently available natural elements in a meaningful counter loop even without Earth, Wind, or Light. Same-element hits are heavily resisted, weakness hits are the clear best choice, and off-elements still deal partial damage instead of feeling fully useless. Void and Life are intentionally separated so undead/void enemies can be made weak to Life without making every creature part of that axis.

Default monster affinity hints:

```text
Void:      void, undead, wraith, skeleton, zombie, ghast, ghoul, shadow, spectre, aberrant, crawler, cultist, spider, cave, rat, mouse, molerat, werewolf
Life:      life, holy, nature, healer, forest, moss, mosshorn, root, rootling, sapling, seedling, sproutling, kweebec, snapdragon, hedera, cactee, deer, cow, bison, moose, mouflon, sheep, goat, horse, boar, pig, piglet, warthog, bunny, rabbit, squirrel, bear_grizzly, fox, wolf_black, tiger, antelope, tortoise, armadillo, ram, chicken, turkey, hyena, meerkat, gecko, larva_silk, earth, toad_rhino, scarak
Fire:      fire, flame, ember, magma, lava, burn, desert, sand, scorpion, camel, rattle, cobra, lizard_sand, snake_rattle, chicken_desert
Water:     water, aqua, ocean, sea, river, whale, shark, crocodile, fish, eel_moray, bluegill, crab, lobster, pike, piranha, minnow, salmon, trout, tang, duck, flamingo, frog, jellyfish, snapjaw, trillodon, trilobite, skrill, snake_marsh
Ice:       ice, frost, snow, frozen, glacier, polar, penguin, yeti, bleached, white
Lightning: lightning, thunder, storm, shock, electric, spark, wind, windwalker, bird, hawk, crow, raven, owl, parrot, pigeon, sparrow, finch, woodpecker, vulture, pterodactyl, archaeopteryx, bat, tetrabird
```

If no monster affinity can be resolved, the hit is neutral.

## Mob Affinity Config

NPC affinity rules now live in `ElementalAffinityConfig`, so servers can add IDs that are not part of the default list without code changes.

Rule formats:

```text
hint:partial_role_name=ELEMENT
role:Exact_Role_ID=ELEMENT
```

Effectiveness override formats:

```text
hint:partial_role_name=ELEMENT:multiplier,ELEMENT:multiplier
role:Exact_Role_ID=ELEMENT:multiplier,ELEMENT:multiplier
```

Example config entries:

```json
{
  "CUSTOM_ROLE_AFFINITIES": [
    "role:Custom_Undead_Boss=VOID",
    "hint:crystal_golem=ICE"
  ],
  "ELEMENT_MULTIPLIERS": [
    "role:Zombie_Burnt=FIRE:0.60,WATER:0.45,ICE:0.45,LIGHTNING:0.45,LIFE:1.30,VOID:0.15",
    "role:Emberwulf=FIRE:0.15,WATER:0.50,ICE:1.25,LIGHTNING:0.45,LIFE:0.50,VOID:0.50",
    "hint:scarak=FIRE:1.30,WATER:0.55,ICE:0.45,LIGHTNING:0.50,LIFE:0.20,VOID:0.50"
  ]
}
```

Scaraks are mapped as Life/insect targets by default, but have an explicit Fire weakness so fire affinity remains their natural counter.

Suggested balance ranges:

```text
Weakness: 1.15x to 1.30x
Off-element partial resistance: 0.40x to 0.60x
Same-element heavy resistance: 0.10x to 0.20x
Neutral: 1.00x
```

Avoid full immunity except for special bosses.

## Mutation System

Use **Resonant Essence** as the mutation support material.

Rules:

```text
Selected filled socket + Resonant Essence support = reroll mutation element
```

Implemented first-pass behavior:

- Resonant Essence appears as an Essence Bench support material.
- Processing with Resonant Essence requires a selected filled essence socket.
- The socketed essence remains unchanged for stats and resonance recipes.
- The socket mutation is stored separately in `SocketReforge.Socket.Mutations`.
- The reroll picks a new affinity element from Fire, Ice, Lightning, Water, Life, or Void.
- Empty, broken, or locked sockets cannot be mutated.

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
