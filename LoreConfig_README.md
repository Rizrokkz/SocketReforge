# LoreConfig Guide

This file explains the settings in `LoreConfig.json`.

Important:
- `LoreConfig.json` is strict JSON. Do not add `//` comments inside it.
- Numeric settings are stored as arrays with one value, for example `"LORE_LEVEL_MAX": [30.0]`.
- After editing the config, restart the server or reload the config through the runtime config UI/command.

## Quick Sample

```json
{
  "LORE_SOCKET_CHEST_CHANCE": [0.0],
  "LORE_SOCKET_DROP_CHANCE": [0.0],
  "LORE_SOCKET_MIN": [3.0],
  "LORE_SOCKET_MAX": [3.0],
  "LORE_LEVEL_MAX": [30.0],
  "LORE_XP_PER_PROC": [1.0],
  "LORE_BASE_XP_PER_LEVEL": [10.0],
  "LORE_XP_GROWTH_PER_LEVEL": [2.0],
  "LORE_FEED_INTERVAL": [3.0],
  "LORE_FEED_BASE": [1.0],
  "LORE_FEED_MULTIPLIER": [2.0],
  "LORE_BLEED_MAX_HP_PCT_PER_TICK": [0.0],
  "LORE_BLEED_RAMP_PER_TICK": [0.08],
  "LORE_BLEED_WEAPON_BASE_CAP_PCT": [0.0],
  "LORE_BLEED_TOTAL_CURRENT_HP_PCT": [0.0005],
  "LORE_BLEED_WEAPON_REFERENCE_BASE": [200.0],
  "LORE_BLEED_WEAPON_SCALE_MIN": [0.75],
  "LORE_BLEED_WEAPON_SCALE_MAX": [1.5],
  "LORE_STATUS_REAPPLY_RULES": [
    "*=5, FIBONACCI",
    "stun=6, LINEAR"
  ],
  "LORE_FEED_ITEM_IDS": [
    "Ingredient_Resonant_Essence",
    "Ingredient_Ghastly_Essence"
  ],
  "LORE_CLEAR_ITEM_IDS": [
    "Ingredient_Ghastly_Essence"
  ],
  "LORE_NPC_STATUS_RESISTANCES": [
    "Drex_Dummy|bleed=1.0|burn=0.25|poison=0.25",
    "FireBoss|burn=1.0",
    "*|freeze=0.20"
  ],
  "LORE_STATUS_COUNTER_NPC_IDS": [
    "Drex_Dummy|*",
    "FireBoss|burn|poison",
    "*|stun"
  ]
}
```

## Core Progression

`LORE_SOCKET_CHEST_CHANCE`
- Chance for lore sockets on treasure chest gear.

`LORE_SOCKET_DROP_CHANCE`
- Chance for lore sockets on NPC drop gear.

`LORE_SOCKET_MIN`
- Minimum number of lore sockets when lore sockets roll.

`LORE_SOCKET_MAX`
- Maximum number of lore sockets when lore sockets roll.

`LORE_LEVEL_MAX`
- Maximum lore level per spirit.

`LORE_XP_PER_PROC`
- XP gained when a lore proc successfully grants XP.

`LORE_BASE_XP_PER_LEVEL`
- Starting XP requirement per level.

`LORE_XP_GROWTH_PER_LEVEL`
- Extra XP growth applied each level.

## Feed Settings

`LORE_FEED_INTERVAL`
- How many levels between feed requirements.

`LORE_FEED_BASE`
- Base feed cost.

`LORE_FEED_MULTIPLIER`
- Feed cost scaling multiplier.

`LORE_FEED_ITEM_IDS`
- Item IDs that count as valid lore feed materials.

Example:
```json
"LORE_FEED_ITEM_IDS": [
  "Ingredient_Resonant_Essence",
  "Ingredient_Ghastly_Essence"
]
```

`LORE_CLEAR_ITEM_IDS`
- Item IDs that can clear/remove a spirit.

Example:
```json
"LORE_CLEAR_ITEM_IDS": [
  "Ingredient_Ghastly_Essence"
]
```

## Bleed Damage Tuning

`LORE_BLEED_MAX_HP_PCT_PER_TICK`
- Caps bleed per tick as a percent of target max HP.

`LORE_BLEED_RAMP_PER_TICK`
- Extra bleed ramp scaling over time.

`LORE_BLEED_WEAPON_BASE_CAP_PCT`
- Optional weapon-based cap contribution.

`LORE_BLEED_TOTAL_CURRENT_HP_PCT`
- Extra total bleed scaling using current HP.

`LORE_BLEED_WEAPON_REFERENCE_BASE`
- Reference weapon damage used for bleed scaling.

`LORE_BLEED_WEAPON_SCALE_MIN`
- Minimum weapon scaling multiplier.

`LORE_BLEED_WEAPON_SCALE_MAX`
- Maximum weapon scaling multiplier.

## Status Reapply Rules

`LORE_STATUS_REAPPLY_RULES`
- Controls how many failed reapply attempts are needed before a boss-countered status can land again.
- Format: `"status=step, PATTERN"`
- `PATTERN` supports `LINEAR` and `FIBONACCI`
- `*` works as a wildcard default

Examples:
```json
"LORE_STATUS_REAPPLY_RULES": [
  "bleed=5, FIBONACCI",
  "burn=5, LINEAR",
  "*=4, LINEAR"
]
```

Meaning:
- `bleed=5, FIBONACCI` = bleed starts at 5 required reapply attempts and grows with Fibonacci
- `burn=5, LINEAR` = burn starts at 5 and grows linearly
- `*=4, LINEAR` = every status without its own rule uses 4 and linear growth

Supported status names:
- `bleed`
- `burn`
- `poison`
- `freeze`
- `shock`
- `slow`
- `weakness`
- `blind`
- `root`
- `stun`
- `fear`
- `drain`

Legacy fallback:
- Older configs may still use `LORE_BLEED_REAPPLY_STEP` and `LORE_BLEED_REAPPLY_PATTERN`
- New configs should use `LORE_STATUS_REAPPLY_RULES`

## NPC Status Resistances

`LORE_NPC_STATUS_RESISTANCES`
- Controls partial resistance or full immunity per NPC.
- Format: `"NPC_ID|status=value|status=value"`
- Values go from `0.0` to `1.0`
- `1.0` means immune
- `*` can be used as a wildcard NPC ID

Examples:
```json
"LORE_NPC_STATUS_RESISTANCES": [
  "Drex_Dummy|bleed=1.0|burn=0.25|poison=0.25",
  "FireBoss|burn=1.0",
  "*|freeze=0.20"
]
```

Meaning:
- `Drex_Dummy` is immune to bleed and takes reduced burn/poison status power
- `FireBoss` is immune to burn
- Every NPC gets 20% freeze resistance unless overridden higher

## NPC Status Counter List

`LORE_STATUS_COUNTER_NPC_IDS`
- Enables the boss-style reapply counter system on matching NPCs.
- Format: `"NPC_ID|status|status"` or `"NPC_ID|*"`
- If no statuses are listed, it defaults to all statuses
- `*` can be used as a wildcard NPC ID

Examples:
```json
"LORE_STATUS_COUNTER_NPC_IDS": [
  "Drex_Dummy|*",
  "FireBoss|burn|poison",
  "*|stun"
]
```

Meaning:
- `Drex_Dummy|*` = all supported statuses use the counter system on `Drex_Dummy`
- `FireBoss|burn|poison` = only burn and poison are countered on `FireBoss`
- `*|stun` = every NPC uses the counter system for stun

Legacy fallback:
- Older configs may still use `LORE_BLEED_COUNTER_NPC_IDS`
- New configs should use `LORE_STATUS_COUNTER_NPC_IDS`

## Recommended Starting Setup

If you want a simple starting point:

```json
"LORE_STATUS_REAPPLY_RULES": [
  "*=5, FIBONACCI"
],
"LORE_STATUS_COUNTER_NPC_IDS": [
  "Drex_Dummy|*"
]
```

This makes every supported status use the same counter pacing on `Drex_Dummy`.
