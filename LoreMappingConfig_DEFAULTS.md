# LoreMappingConfig Defaults

Complete default values for `LoreMappingConfig.json`, extracted from `LoreMappingConfig.java`.

## Default Configuration

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
    "black=Wraith,Spawn_Void,Horse_Skeleton,Skeleton_Burnt_Praetorian,Skeleton_Burnt_Soldier,Zombie_Burnt,Cow_Undead",
    "blue=Whale_Humpback,Shark_Hammerhead,Trillodon,Crocodile",
    "cyan=Horse,Wolf_Black,Rex_Cave",
    "green=Bison,Tiger_Sabertooth,Mosshorn_Plain,Mosshorn,Toad_Rhino,Bear_Grizzly,Moose_Bull",
    "red=Golem_Firesteel,Emberwulf,Toad_Rhino_Magma",
    "white=Hound_Bleached,Leopard_Snow",
    "yellow=Spirit_Thunder,Camel,Ram,Scorpion"
  ],
  "LORE_CORE_COLORS": [
    "red",
    "blue",
    "black"
  ],
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
}
```

## Gem-to-Spirit Compatibility

| Gem Color | Item ID | Spirits |
|-----------|---------|---------|
| black | `rock_gem_voidstone` | Wraith, Spawn_Void, Horse_Skeleton, Skeleton_Burnt_Praetorian, Skeleton_Burnt_Soldier, Zombie_Burnt, Cow_Undead |
| blue | `rock_gem_sapphire` | Whale_Humpback, Shark_Hammerhead, Trillodon, Crocodile |
| cyan | `rock_gem_zephyr` | Horse, Wolf_Black, Rex_Cave |
| green | `rock_gem_emerald` | Bison, Tiger_Sabertooth, Mosshorn_Plain, Mosshorn, Toad_Rhino, Bear_Grizzly, Moose_Bull |
| red | `rock_gem_ruby` | Golem_Firesteel, Emberwulf, Toad_Rhino_Magma |
| white | `rock_gem_diamond` | Hound_Bleached, Leopard_Snow |
| yellow | `rock_gem_topaz` | Spirit_Thunder, Camel, Ram, Scorpion |

## Core Lore Eligibility

| Color | Can Roll Core Effect |
|-------|---------------------|
| red | Yes |
| blue | Yes |
| black | Yes |
| cyan | No |
| green | No |
| white | No |
| yellow | No |

## Ability Entries

| Spirit | Type | Trigger | Proc | CD (ms) | Effect | Base | Per Lvl |
|--------|------|---------|------|---------|--------|------|---------|
| Wraith | Standard | ON_HIT | 0.06 | 5200 | APPLY_INVISIBLE | 0.30 | 0.02 |
| Spawn_Void | Standard | ON_HIT | 0.06 | 7000 | APPLY_FEAR | 2.0 | 0.10 |
| Horse_Skeleton | Signature | SIGNATURE_RAZORSTRIKE | - | - | - | - | - |
| Skeleton_Burnt_Praetorian | Signature | SIGNATURE_WHIRLWIND | - | - | - | - | - |
| Skeleton_Burnt_Soldier | Standard | ON_HIT | 0.07 | 5000 | APPLY_BURN | 2.0 | 0.10 |
| Zombie_Burnt | Standard | ON_HIT | 0.07 | 5200 | APPLY_BLEED | 2.0 | 0.10 |
| Cow_Undead | Signature | SIGNATURE_SHRAPNEL | - | - | - | - | - |
| Whale_Humpback | Signature | SIGNATURE_GROUNDSLAM | - | - | - | - | - |
| Shark_Hammerhead | Signature | SIGNATURE_PUMMEL | - | - | - | - | - |
| Trillodon | Standard | ON_HIT | 0.06 | 7000 | HEAL_AREA_OVER_TIME | 1.6 | 0.08 |
| Crocodile | Signature | SIGNATURE_CAUSTIC_FINALE | - | - | - | - | - |
| Horse | Signature | SIGNATURE_VOLLEY | - | - | - | - | - |
| Rex_Cave | Signature | SIGNATURE_OMNISLASH | - | - | - | - | - |
| Bison | Standard | ON_CRIT | 0.10 | 4500 | CRIT_CHARGE | 0.35 | 0.02 |
| Tiger_Sabertooth | Standard | ON_HIT | 0.07 | 4800 | DRAIN_LIFE | 2.0 | 0.10 |
| Mosshorn_Plain | Signature | SIGNATURE_AREA_HEAL | - | - | - | - | - |
| Mosshorn | Standard | ON_DAMAGED | 0.08 | 5000 | HEAL_SELF_OVER_TIME | 1.4 | 0.08 |
| Toad_Rhino | Standard | ON_DAMAGED | 0.07 | 6000 | APPLY_SHIELD | 0.30 | 0.02 |
| Bear_Grizzly | Standard | ON_DAMAGED | 0.08 | 4000 | DAMAGE_ATTACKER | 2.6 | 0.12 |
| Moose_Bull | Standard | ON_HIT | 0.06 | 5000 | APPLY_HASTE | 0.30 | 0.02 |
| Golem_Firesteel | Signature | SIGNATURE_OCTASLASH | - | - | - | - | - |
| Emberwulf | Signature | SIGNATURE_BLOOD_RUSH | - | - | - | - | - |
| Toad_Rhino_Magma | Signature | SIGNATURE_BURN_FINALE | - | - | - | - | - |
| Hound_Bleached | Standard | ON_HIT | 0.07 | 4800 | APPLY_SLOW | 1.6 | 0.08 |
| Leopard_Snow | Standard | ON_HIT | 0.06 | 5200 | APPLY_FREEZE | 1.4 | 0.08 |
| Spirit_Thunder | Signature | SIGNATURE_VORTEXSTRIKE | - | - | - | - | - |
| Camel | Signature | SIGNATURE_BIG_ARROW | - | - | - | - | - |
| Ram | Standard | ON_HIT | 0.07 | 5200 | APPLY_SHOCK | 1.8 | 0.10 |
| Scorpion | Standard | ON_HIT | 0.08 | 4800 | APPLY_POISON | 2.0 | 0.10 |

## Key Reference

| Key | Default Count | Description |
|-----|--------------|-------------|
| `LORE_GEM_COLORS` | 7 | Item ID to color mappings |
| `LORE_COLOR_SPIRITS` | 7 | Color to spirit pool mappings |
| `LORE_CORE_COLORS` | 3 | Colors that can roll core effects |
| `LORE_ABILITY_ENTRIES` | 30 | Spirit ability definitions |

## Notes

- All arrays are string arrays. Do not use nested JSON objects.
- Matching is case-insensitive for item IDs and spirit IDs.
- The config supports partial overrides. Missing entries from newer defaults are auto-merged on load.
- `injectMissingDefaults()` merges new defaults by key for `LORE_GEM_COLORS`, `LORE_COLOR_SPIRITS`, and `LORE_ABILITY_ENTRIES`. `LORE_CORE_COLORS` uses unique-value merge.
