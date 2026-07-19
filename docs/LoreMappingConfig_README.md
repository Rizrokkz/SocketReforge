# LoreMappingConfig Guide

This file explains the settings in `LoreMappingConfig.json`.

Important:
- `LoreMappingConfig.json` is strict JSON. Do not add `//` comments inside it.
- All settings are string arrays. Each entry follows the format described for its key.
- After editing the config, restart the server or reload the config through the runtime config UI/command.
- The config is version-tolerant: missing entries from newer defaults are auto-merged on load.

## Quick Sample

```json
{
  "LORE_GEM_COLORS": [
    "rock_gem_ruby=red",
    "rock_gem_sapphire=blue"
  ],
  "LORE_COLOR_SPIRITS": [
    "red=Golem_Firesteel,Emberwulf",
    "blue=Whale_Humpback,Shark_Hammerhead"
  ],
  "LORE_CORE_COLORS": [
    "red",
    "blue",
    "black"
  ],
  "LORE_ABILITY_ENTRIES": [
    "Wraith=ON_HIT,0.06,5200,APPLY_INVISIBLE,0.30,0.02",
    "Golem_Firesteel=SIGNATURE_OCTASLASH"
  ]
}
```

## Lore Gem Colors

`LORE_GEM_COLORS`
- Maps item IDs (case-insensitive) to lore color names.
- Used to detect which gem color is embedded in an item.
- Format: `"item_id=color"`

Example:
```json
"LORE_GEM_COLORS": [
  "rock_gem_ruby=red",
  "rock_gem_sapphire=blue",
  "rock_gem_emerald=green",
  "rock_gem_topaz=yellow",
  "rock_gem_diamond=white",
  "rock_gem_voidstone=black",
  "rock_gem_zephyr=cyan"
]
```

Meaning:
- An item with ID `rock_gem_ruby` (case-insensitive) is treated as the `red` lore color.

## Lore Color Spirits

`LORE_COLOR_SPIRITS`
- Defines which spirit IDs are allowed per lore color.
- Format: `"color=SPIRIT1,SPIRIT2,SPIRIT3"`
- An empty list for a color means all spirits are allowed for that color.
- Matching is case-insensitive.

Example:
```json
"LORE_COLOR_SPIRITS": [
  "black=Wraith,Spawn_Void,Horse_Skeleton",
  "blue=Whale_Humpback,Shark_Hammerhead,Trillodon,Crocodile",
  "cyan=Horse,Wolf_Black,Rex_Cave",
  "green=Bison,Tiger_Sabertooth,Mosshorn_Plain,Mosshorn,Toad_Rhino,Bear_Grizzly,Moose_Bull",
  "red=Golem_Firesteel,Emberwulf,Toad_Rhino_Magma",
  "white=Hound_Bleached,Leopard_Snow",
  "yellow=Spirit_Thunder,Camel,Ram,Scorpion"
]
```

Meaning:
- Only `Wraith`, `Spawn_Void`, etc. can roll on `black` lore items.
- Only `Whale_Humpback`, `Shark_Hammerhead`, etc. can roll on `blue` lore items.

## Lore Core Colors

`LORE_CORE_COLORS`
- List of color names that are allowed to roll core lore effects.
- Only colors listed here can grant the "core" ability on a spirit socket.
- Format: `"color"`

Example:
```json
"LORE_CORE_COLORS": [
  "red",
  "blue",
  "black"
]
```

Meaning:
- Only `red`, `blue`, and `black` lore items can spawn with a core ability socket.

## Lore Ability Entries

`LORE_ABILITY_ENTRIES`
- Maps spirit IDs to their ability definitions.
- Supports two formats: standard numeric entries and signature abilities.

### Standard Ability Format

Format: `"spiritId=TRIGGER,PROC_CHANCE,COOLDOWN_MS,EFFECT_TYPE,BASE_VALUE,PER_LEVEL"`

| Field | Description |
|-------|-------------|
| `spiritId` | The NPC/entity ID of the spirit (case-insensitive). |
| `TRIGGER` | When the ability can proc. See supported triggers below. |
| `PROC_CHANCE` | Chance per trigger check to proc (0.0 to 1.0). |
| `COOLDOWN_MS` | Cooldown in milliseconds between successful procs. |
| `EFFECT_TYPE` | The effect to apply. See supported effects below. |
| `BASE_VALUE` | Starting value for the effect. |
| `PER_LEVEL` | Value added per spirit level. |

Example:
```json
"Wraith=ON_HIT,0.06,5200,APPLY_INVISIBLE,0.30,0.02"
```

Meaning:
- `Wraith` has a 6% chance to proc on hit, with a 5.2s cooldown.
- Applies `APPLY_INVISIBLE` with a base value of `0.30` and `+0.02` per level.

### Signature Ability Format

Format: `"spiritId=SIGNATURE_ABILITY_NAME"`

Example:
```json
"Horse_Skeleton=SIGNATURE_RAZORSTRIKE"
```

Meaning:
- `Horse_Skeleton` uses the predefined `SIGNATURE_RAZORSTRIKE` ability.

### Supported Triggers

- `ON_HIT` - Procs when the wielder hits an entity.
- `ON_CRIT` - Procs only on critical hits.
- `ON_DAMAGED` - Procs when the wielder takes damage.

### Supported Effect Types

- `APPLY_INVISIBLE` - Makes the target invisible.
- `APPLY_FEAR` - Applies fear to the target.
- `APPLY_BURN` - Applies burn damage over time.
- `APPLY_BLEED` - Applies bleed damage over time.
- `APPLY_POISON` - Applies poison damage over time.
- `APPLY_FREEZE` - Freezes the target.
- `APPLY_SHOCK` - Applies shock damage over time.
- `APPLY_SLOW` - Slows the target.
- `APPLY_WEAKNESS` - Weakens the target.
- `APPLY_BLIND` - Blinds the target.
- `APPLY_ROOT` - Roots the target in place.
- `APPLY_STUN` - Stuns the target.
- `APPLY_HASTE` - Grants haste to the wielder.
- `APPLY_SHIELD` - Grants a shield to the wielder.
- `DRAIN_LIFE` - Drains life from target to wielder.
- `HEAL_SELF_OVER_TIME` - Heals the wielder over time.
- `HEAL_AREA_OVER_TIME` - Heals nearby allies over time.
- `CRIT_CHARGE` - Grants a stacking critical hit chance buff.
- `DAMAGE_ATTACKER` - Deals damage back to the attacker.

### Signature Ability Names

The following predefined signatures are available:

- `SIGNATURE_RAZORSTRIKE`
- `SIGNATURE_WHIRLWIND`
- `SIGNATURE_SHRAPNEL`
- `SIGNATURE_GROUNDSLAM`
- `SIGNATURE_PUMMEL`
- `SIGNATURE_CAUSTIC_FINALE`
- `SIGNATURE_VOLLEY`
- `SIGNATURE_OMNISLASH`
- `SIGNATURE_AREA_HEAL`
- `SIGNATURE_OCTASLASH`
- `SIGNATURE_BLOOD_RUSH`
- `SIGNATURE_BURN_FINALE`
- `SIGNATURE_VORTEXSTRIKE`
- `SIGNATURE_BIG_ARROW`

### Full Default Ability Table

```json
"LORE_ABILITY_ENTRIES": [
  "Wraith=ON_HIT,0.06,5200,APPLY_INVISIBLE,0.30,0.02",
  "Spawn_Void=ON_HIT,0.06,7000,APPLY_FEAR,2.0,0.10",
  "Horse_Skeleton=SIGNATURE_RAZORSTRIKE",
  "Skeleton_Burnt_Praetorian=SIGNATURE_WHIRLWIND",
  "Skeleton_Burnt_Soldier=ON_HIT,0.07,5000,APPLY_BURN,2.0,0.10",
  "Zombie_Burnt=ON_HIT,0.07,5200,APPLY_BLEED,2.0,0.10",
  "Cow_Undead=SIGNATURE_SHRAPNEL",
  "Whale_Humpback=SIGNATURE_GROUNDSLAM",
  "Shark_Hammerhead=SIGNATURE_PUMMEL",
  "Trillodon=ON_HIT,0.06,7000,HEAL_AREA_OVER_TIME,1.6,0.08",
  "Crocodile=SIGNATURE_CAUSTIC_FINALE",
  "Horse=SIGNATURE_VOLLEY",
  "Rex_Cave=SIGNATURE_OMNISLASH",
  "Bison=ON_CRIT,0.10,4500,CRIT_CHARGE,0.35,0.02",
  "Tiger_Sabertooth=ON_HIT,0.07,4800,DRAIN_LIFE,2.0,0.10",
  "Mosshorn_Plain=SIGNATURE_AREA_HEAL",
  "Mosshorn=ON_DAMAGED,0.08,5000,HEAL_SELF_OVER_TIME,1.4,0.08",
  "Toad_Rhino=ON_DAMAGED,0.07,6000,APPLY_SHIELD,0.30,0.02",
  "Bear_Grizzly=ON_DAMAGED,0.08,4000,DAMAGE_ATTACKER,2.6,0.12",
  "Moose_Bull=ON_HIT,0.06,5000,APPLY_HASTE,0.30,0.02",
  "Golem_Firesteel=SIGNATURE_OCTASLASH",
  "Emberwulf=SIGNATURE_BLOOD_RUSH",
  "Toad_Rhino_Magma=SIGNATURE_BURN_FINALE",
  "Hound_Bleached=ON_HIT,0.07,4800,APPLY_SLOW,1.6,0.08",
  "Leopard_Snow=ON_HIT,0.06,5200,APPLY_FREEZE,1.4,0.08",
  "Spirit_Thunder=SIGNATURE_VORTEXSTRIKE",
  "Camel=SIGNATURE_BIG_ARROW",
  "Ram=ON_HIT,0.07,5200,APPLY_SHOCK,1.8,0.10",
  "Scorpion=ON_HIT,0.08,4800,APPLY_POISON,2.0,0.10"
]
```

## Adding Custom Gems, Spirits, or Abilities

1. Add the gem to `LORE_GEM_COLORS`.
2. Add the spirit to `LORE_COLOR_SPIRITS` under the matching color.
3. Add the ability to `LORE_ABILITY_ENTRIES`.
4. If the ability should be able to roll as a core effect, add the color to `LORE_CORE_COLORS`.

## Recommended Starting Setup

If you want a simple starting point:

```json
{
  "LORE_GEM_COLORS": [
    "rock_gem_ruby=red",
    "rock_gem_sapphire=blue",
    "rock_gem_emerald=green",
    "rock_gem_topaz=yellow",
    "rock_gem_diamond=white",
    "rock_gem_voidstone=black",
    "rock_gem_zephyr=cyan"
  ],
  "LORE_COLOR_SPIRITS": [
    "red=Golem_Firesteel,Emberwulf,Toad_Rhino_Magma",
    "blue=Whale_Humpback,Shark_Hammerhead,Trillodon,Crocodile"
  ],
  "LORE_CORE_COLORS": [
    "red",
    "blue"
  ],
  "LORE_ABILITY_ENTRIES": [
    "Golem_Firesteel=SIGNATURE_OCTASLASH",
    "Whale_Humpback=SIGNATURE_GROUNDSLAM",
    "Shark_Hammerhead=SIGNATURE_PUMMEL"
  ]
}
```

This sets up a minimal lore mapping with two colors, limited spirit pools, and signature abilities only.
