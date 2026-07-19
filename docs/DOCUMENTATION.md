# SocketReforge Documentation

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
   - [Weapon Reforging System](#weapon-reforging-system)
   - [Socket System](#socket-system)
   - [Essence System](#essence-system)
   - [Resonance System](#resonance-system)
   - [Lore System](#lore-system)
   - [Dynamic Floating Damage Numbers](#dynamic-floating-damage-numbers)
3. [Installation](#installation)
4. [Commands](#commands)
5. [Configuration](#configuration)
6. [Architecture](#architecture)
7. [Building](#building)
8. [Changelog](#changelog)

---

## Overview

SocketReforge is a comprehensive Hytale mod that adds RPG elements including equipment refinement, gem socketing, essence combinations, and spirit-based abilities. It transforms standard equipment into customizable gear with multiple progression systems.

**Version:** 1.3.7a  
**Platform:** Hytale (custom engine)  
**Author:** iRaiden

---

## Features

### Weapon Reforging System

Allows players to upgrade weapons and armor beyond **+3 levels** using refinement materials. Each upgrade increases weapon damage or armor defense through configurable multipliers. The system supports tiered material costs with auto-split tier thresholds.

**Reforging Materials:**

| Material | Tier Band | Description |
|----------|-----------|-------------|
| **Refinement Glob** | Early | Basic refinement material |
| **Refinement Glob Plus** | Mid | Enhanced refinement material |
| **Resonant Glob** | Late | High-tier refinement material |

**Reforge Outcomes:**

| Outcome | Effect |
|---------|--------|
| **Degrade** | Item drops one level |
| **Same** | No change to item level |
| **Upgrade** | Item gains one level |
| **Jackpot** | Item gains two levels |

**Default Reforge Weights (0→1):**

| Degrade | Same | Upgrade | Jackpot |
|--------|------|---------|--------|
| 0% | 65% | 34% | 1% |

**Default Damage Multipliers:**

| Level | Name | Multiplier | Bonus |
|-------|------|-----------|-------|
| +0 | Base | x1.00 | — |
| +1 | Sharp | x1.10 | +10% |
| +2 | Deadly | x1.15 | +15% |
| +3 | Legendary | x1.25 | +25% |

*Note: Supports higher levels beyond +3 with configurable multipliers.*

**Default Armor Defense Multipliers:**

| Level | Name | Multiplier |
|-------|------|-----------|
| +0 | Base | x1.00 |
| +1 | Reinforced | x1.05 |
| +2 | Fortified | x1.10 |
| +3 | Impenetrable | x1.15 |

**Refinement Labels:**

The system supports configurable prefix/suffix labels per level for both weapons and armor. Default labels are auto-filled:
- Weapons: Sharp, Deadly, Legendary, etc.
- Armor: Reinforced, Fortified, Impenetrable, etc.

---

### Socket System

Add sockets to weapons and armor for gem/essence insertion.

**Socket Punch Bench:**

- **Crafted:** 8 Iron Bars + 3 Emeralds at Workbench (Tier 2)
- **Interaction:** `SocketPunchBench`
- **Max Sockets:** 4 (default), with 1% bonus chance for 5th socket

**Socket Punching Mechanics:**

| Current Sockets | Success Chance | Break Chance |
|-----------------|----------------|--------------|
| 0 | 90% | 5% |
| 1 | 75% | 10% |
| 2 | 55% | 20% |
| 3 | 35% | 35% |

**Support Materials:**

- **Socket Puncher** - Required material for each socket punch attempt (crafted at Salvage Bench)
- **Socket Stabilizer** - Provides +15% success chance, -5% break chance (crafted at Alchemy Bench)
- **Socket Expander** - Increases max sockets by 1 (capped)
- **Socket Diffuser** - Decreases max sockets by 1

---

### Essence System

Socket essences into punched sockets to add powerful effects.

**Essence Socket Bench:**

- **Crafted:** 8 Iron Bars + 3 Wood at Workbench (Tier 2)
- **Interaction:** `EssenceSocketBench`

**Essence Types:**

| Essence | Weapon Effect | Armor Effect |
|---------|---------------|--------------|
| **Fire** | +2-12% Damage | +3-15% Fire Defense |
| **Ice** | +2-5% Slow/Freeze | +2-12% Cold Defense |
| **Lightning** | +3-15% Attack Speed | +2-8% Crit Chance |
| **Life** | +2-10% Lifesteal | +10-50 HP |
| **Void** | +5-25% Crit Damage | +5-25% Evasion |
| **Water** | +2-10% Evasion | +10-50% Regen |

**Tier System:**

- Consecutive essences of the same type build tier (max T5)
- Example: "Life, Life, Life, Fire" = Tier 3 Life, Tier 1 Fire
- Higher tiers provide significantly stronger effects

**Repair Materials:**

- **Voidheart** - Used to repair broken sockets auto-consumed during socketing

---

### Resonance System

A runeword-like mechanic that activates powerful effects when specific essence combinations are socketed in exact order.

**How Resonance Works:**

- Activates only when **all sockets are filled** with essences
- The essence **order matters** — different sequences produce different results
- Higher socket counts unlock stronger resonances

**Resonance Types:**

| Type | Effect |
|------|-------|
| **BURN_ON_CRIT** | Chance to deal bonus burn damage on crit |
| **CHAIN_SLOW** | Chance to freeze target on hit |
| **EXECUTE** | Bonus damage against low-HP targets |
| **ARMOR_SHRED** | Chance to deal bonus damage on hit |
| **THUNDER_STRIKE** | Chance to deal bonus lightning damage |
| **MULTISHOT_BARRAGE** | Chance to fire extra arrows (bows) |
| **CROSSBOW_AUTO_RELOAD** | Chance to refund ammo (crossbows) |
| **PLUNDERING_BLADE** | Chance to steal from NPC drop tables |
| **FROST_NOVA_ON_HIT** | Chance to freeze attacker when hit |
| **THORNS_SHOCK** | Reflect damage as shock when hit |
| **CHEAT_DEATH** | Survive lethal hits with 1 HP |
| **HEAL_SURGE** | Heal on hit/dealt damage |
| **SHOCK_DODGE** | Retaliate on dodge |
| **AURA_BURN** | Burn attacker when hit |

**Weapon Resonances Examples:**

| Name | Essence Pattern | Effect |
|------|----------------|--------|
| Kingsbrand | Fire + Lightning + Life | Thunder Strike |
| Butcher's Mark | Void + Fire + Fire | Execute |
| Nightneedle | Void + Lightning + Water | Execute |
| Gale String | Lightning + Water + Lightning | Thunder Strike |

**Armor Resonances Examples:**

| Name | Essence Pattern | Effect |
|------|----------------|--------|
| Tideguard | Water + Life + Water | Heal Surge |
| Cryobastion | Ice + Ice + Life | Frost Nova on Hit |
| Phoenix Aegis | Fire + Life + Fire + Water + Void | Cheat Death |

**Seeded Resonance:**

- Uses main world seed to shuffle essence patterns
- Each server has unique resonance combinations

---

### Lore System

Adds lore sockets and spirit abilities that proc based on combat events.

**Lore System Flow:**

1. Lore sockets are rolled on loot drops (separate from essence sockets)
2. Lore gems act as empty vessels until the equipped item gets its first kill
3. On first kill, a spirit is assigned based on gem color **and the current Zone**
4. Proc triggers grant fixed XP; level rises over time
5. Every 5 levels, a feed gate requires Resonant Essence to continue
6. At level 100, the spirit is absorbed by the player

**Lore Socket Punching:**

- Uses Ghastly Essence as support material
- Success/fail/break roll feedback

**Lore Socket Color Reroll:**

- Uses Resonant Essence to reroll slot colors
- Auto-consumes 1 per eligible slot
- Skips filled/locked slots

**Socket Bench Integration:**

- Lore socket colors now render inside Socket Punch bench preview
- Matches Essence Bench styling (colored frames + overlay icons)

**Spirit Triggers (16 types):**

- **Offensive:** ON_HIT, ON_CRIT, ON_SKILL_USE
- **Defensive:** ON_DAMAGE_TAKEN, ON_BLOCK, ON_DODGE
- **Kill/Death:** ON_KILL, ON_NEAR_DEATH, ON_FIRST_KILL
- **Movement:** ON_SPRINT, ON_JUMP, ON_SNEAK
- **Utility:** ON_HEAL, ON_POTION_USE, ON_LORE_PROC, ON_STATUS_APPLY

**Spirit Effects (100+ types):**

Including damage effects, healing, status applications, movement modifiers, and utility abilities.

---

### Dynamic Floating Damage Numbers

Custom configurable floating damage text system. Can be toggled on/off via the runtime config command.

**Features:**

- Per-damage-kind styling
- Custom labels, icons, and formats
- DoT (Damage over Time) tracking
- UI asset customizable
- **Toggle Control** - Custom damage numbers can be turned off in `/reforgeconfig` command

**Built-in Kinds:**

- PHYSICAL
- CRITICAL
- HEALING
- FIRE
- ICE
- LIGHTNING
- VOID
- LIFE_STEAL
- REGEN
- ABSORB

**Configuration:**

The `USE_CUSTOM_COMBAT_TEXT` setting in `DamageNumberConfig.json` controls whether custom damage numbers are displayed. This can also be toggled live via the `/reforgeconfig` command (OP-only).

---

## Installation

1. Copy `irai.mod.reforge_SocketReforge.jar` to your Hytale server `mods` folder
2. Start server — config files are generated automatically:
   - `SFXConfig.json`
   - `RefinementConfig.json`
   - `SocketConfig.json`
   - `LootSocketRollConfig.json`
   - `LoreConfig.json`
   - `LoreMappingConfig.json`
   - `DamageNumberConfig.json`
   - `WorldRepairConfig.json`
3. Customize configs as needed and restart
4. Optionally install HyUI for enhanced UI experiences
5. After major updates, run `/reforgeconfig` (OP) and reset defaults to populate new settings

### Localization Support

SocketReforge supports multiple languages. The system currently includes:
- **English (en-US)** - Default
- **German (de-DE)** - Full localization coverage

Language files are loaded automatically based on player settings. Tooltips and UI elements are localized.

**Optional Dependencies:**

- **HyUI 0.9.0+** - Enhanced UI framework (recommended)

---

## Commands

### Player Commands

| Command | Description |
|---------|-------------|
| `/toolpartsui` | Open modular tool parts bench UI |
| `/socketpunch` | Open socket punch bench UI |
| `/essence` | Open essence socket bench UI |
| `/loregem` | Open lore gem socketing UI |
| `/lorefeed` | Open lore feed UI |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/reforgeconfig` | Open runtime config UI - tune socket, refinement, loot, and damage number settings live |
| `/reforgeadmin` | OP tools for held-item refinement/socket metadata |
| `/loreabilities` | Dump lore spirit ability map |
| `/lorecolors` | Dump lore spirit to gem color map |
| `/loreapply` | OP: apply a lore spirit to held weapon |
| `/spawnequipchest` | Spawn test chest with equipment-loot |
| `/spawnequipenemy` | Spawn equipment-eligible enemy NPCs |
| `/itemmeta` | View or modify item metadata |
| `/showmeta` | View or modify item metadata (alias) |

### Runtime Config

The `/reforgeconfig` command (also available as `/runtimeconfig` or `/configui`) provides a live runtime configuration UI for OP players. It supports tuning:

- **Socket Settings** - Max sockets, punch success/break chances
- **Refinement Settings** - Max refine level, tier thresholds, per-level weights, multipliers
- **Loot Settings** - Socket roll chances, resonance chance
- **Damage Numbers** - Toggle custom damage numbers on/off
- **Weather Events** - Timing, counts, despawn delay (legacy)

Changes are saved instantly and can be reloaded from disk.

### Resonance Commands

| Command | Description |
|---------|-------------|
| `/resonancecombos` | List seeded resonance combinations |
| `/resonancerecipe` | OP: give resonant recipe shards |
| `/resonanceworldscan` | Scan main world containers for resonance |

### Legacy Commands (removed in 1.1.0+)

| Command | Description | Status |
|--------|-------------|--------|
| `/patchassets` | Patch weapons | Removed |
| `/weaponstats` | View weapon stats | Removed |
| `/checkname` | Check weapon name | Removed |

---

## Configuration

### RefinementConfig.json

Controls refinement rates, multipliers, break chances, and tiered material system.

```json
{
  "DAMAGE_MULTIPLIERS": [1.0, 1.10, 1.15, 1.25],
  "DEFENSE_MULTIPLIERS": [1.0, 1.05, 1.10, 1.15],
  "BREAK_CHANCES": [0.01, 0.05, 0.075],
  "ARMOR_BREAK_CHANCES": [0.10, 0.05, 0.02],
  "WEIGHTS_0_TO_1": [0.00, 0.65, 0.34, 0.01],
  "WEIGHTS_1_TO_2": [0.35, 0.45, 0.19, 0.01],
  "WEIGHTS_2_TO_3": [0.60, 0.30, 0.095, 0.005],
  "MAX_REFINEMENT_LEVEL": 3,
  "TIER_THRESHOLDS": [100, 500, 1000],
  "MATERIAL_TIER_MAPPING": {
    "Refinement_Glob": "early",
    "Refinement_Glob_Plus": "mid",
    "Resonant_Glob": "late"
  },
  "WEAPON_LABELS": {
    "0": { "prefix": "", "suffix": "" },
    "1": { "prefix": "Sharp", "suffix": "" },
    "2": { "prefix": "Deadly", "suffix": "" },
    "3": { "prefix": "Legendary", "suffix": "" }
  },
  "ARMOR_LABELS": {
    "0": { "prefix": "", "suffix": "" },
    "1": { "prefix": "Reinforced", "suffix": "" },
    "2": { "prefix": "Fortified", "suffix": "" },
    "3": { "prefix": "Impenetrable", "suffix": "" }
  }
}
```

| Field | Description |
|-------|-------------|
| `DAMAGE_MULTIPLIERS` | Damage multiplier per level |
| `DEFENSE_MULTIPLIERS` | Defense multiplier per level |
| `BREAK_CHANCES` | Weapon shatter chance per transition |
| `ARMOR_BREAK_CHANCES` | Armor break chance per transition |
| `WEIGHTS_X_TO_Y` | Outcome weights [degrade, same, upgrade, jackpot] |
| `MAX_REFINEMENT_LEVEL` | Maximum upgrade level allowed |
| `TIER_THRESHOLDS` | Experience thresholds for tier splits |
| `MATERIAL_TIER_MAPPING` | Maps materials to tier bands (early/mid/late) |
| `WEAPON_LABELS` | Configurable prefix/suffix per weapon level |
| `ARMOR_LABELS` | Configurable prefix/suffix per armor level |

### SocketConfig.json

Controls socket punch chances and max sockets.

```json
{
  "MAX_SOCKETS": ["4", "4"],
  "PUNCH_SUCCESS_CHANCES": [0.90, 0.75, 0.55, 0.35],
  "PUNCH_BREAK_CHANCES": [0.05, 0.10, 0.20, 0.35],
  "ESSENCE_REMOVAL_SUCCESS": [0.70],
  "ESSENCE_REMOVAL_DESTROY": [0.30],
  "BONUS_SOCKET_CHANCE": [0.01],
  "MAX_REDUCE_CHANCE": [0.25]
}
```

### SFXConfig.json

Controls sound effects for events.

```json
{
  "BENCHES": ["Reforgebench"],
  "SFX_START": "SFX_Mace_T1_Block_Impact",
  "SFX_SUCCESS": "SFX_Weapon_Bench_Craft",
  "SFX_JACKPOT": "SFX_Workbench_Upgrade_Complete_Default",
  "SFX_FAIL": "SFX_Armour_Bench_Craft",
  "SFX_NO_CHANGE": "SFX_Workbench_Craft",
  "SFX_SHATTER": "SFX_Door_Temple_Light_Open"
}
```

### LootSocketRollConfig.json

Controls socket roll chances for loot.

```json
{
  "CHEST_SOCKET_CHANCE": 0.25,
  "CHEST_MIN_SOCKETS": 1,
  "CHEST_MAX_SOCKETS": 3,
  "NPC_SOCKET_CHANCE": 0.15,
  "NPC_MIN_SOCKETS": 1,
  "NPC_MAX_SOCKETS": 2,
  "RESONANCE_CHANCE": 0.10,
  "GREATER_ESSENCE_CHANCE": 0.15,
  "BROKEN_SOCKET_CHANCE": 0.15
}
```

### LoreConfig.json

Controls lore socket rules.

```json
{
  "MAX_LORE_SOCKETS": 3,
  "LORE_FEED_MATERIAL": "Resonant_Essence",
  "XP_PER_PROC": 10,
  "XP_TO_LEVEL": [50, 100, 150, 200, 250],
  "ABSORPTION_LEVEL": 100
}
```

### DamageNumberConfig.json

Controls floating damage display.

```json
{
  "USE_CUSTOM_COMBAT_TEXT": true,
  "DEFAULTS": {
    "FONT": "default",
    "SCALE": 1.0,
    "LIFETIME": 1.5,
    "OFFSET_Y": 1.0
  }
}
```

### WorldRepairConfig.json

Controls automatic world repair and regeneration features.

```json
{
  "AUTO_FIX_EMPTY_DROPLISTS_ON_START": false,
  "AUTO_FIX_EMPTY_DROPLISTS_DONE": false,
  "AUTO_REMOVE_CHUNK_ON_START": false,
  "AUTO_REMOVE_CHUNK_DONE": false,
  "AUTO_REMOVE_CHUNK_X": 0,
  "AUTO_REMOVE_CHUNK_Z": 0,
  "AUTO_REGEN_REGION_ON_START": false,
  "AUTO_REGEN_REGION_DONE": false,
  "AUTO_REGEN_REGION_FILE": ""
}
```

| Field | Description |
|-------|-------------|
| `AUTO_FIX_EMPTY_DROPLISTS_ON_START` | Auto-repair empty droplists on server start |
| `AUTO_REMOVE_CHUNK_ON_START` | Auto-remove chunk for regeneration |
| `AUTO_REGEN_REGION_ON_START` | Auto-regenerate region file |

---

## Architecture

### Package Structure

```
irai.mod.reforge
├── ReforgePlugin.java              # Main plugin entry point
├── Commands/                    # Command handlers
│   ├── EssenceCommand.java
│   ├── ItemMetaCommand.java
│   ├── LoreCommand.java
│   ├── ReforgeAdminCommand.java
│   ├── RuntimeConfigCommand.java
│   └── SocketPunchCommand.java
├── Config/                      # Configuration codecs
│   ├── ConfigService.java
│   ├── LoreConfig.java
│   ├── LootSocketRollConfig.java
│   ├── RefinementConfig.java
│   ├── SFXConfig.java
│   └── SocketConfig.java
├── Entity/Events/               # ECS event systems
│   ├── DamageNumberEST.java
│   ├── EquipmentRefineEST.java
│   ├── LoreEffectEST.java
│   ├── LootSocketRoller.java
│   ├── SocketEffectEST.java
│   └── SocketStatSystem.java
├── Interactions/               # Interaction handlers
│   ├── EssenceSocketBench.java
│   ├── ReforgeEquip.java
│   ├── SocketPunchBench.java
│   └── LoreSocketBench.java
├── Lore/                      # Lore system (spirits)
│   ├── LoreAbility.java
│   ├── LoreAbilityRegistry.java
│   ├── LoreGemRegistry.java
│   ├── LoreSocketManager.java
│   └── LoreTrigger.java
├── Socket/                    # Socket/essence system
│   ├── EssenceRegistry.java
│   ├── EssenceType.java
│   ├── ResonanceSystem.java
│   └── SocketManager.java
├── Systems/                   # Background systems
│   ├── SyncTasks.java
│   └── WeaponPersistence.java
├── UI/                       # Custom UI pages
│   ├── EssenceBenchUI.java
│   ├── LoreSocketBenchUI.java
│   ├── ReforgeBenchUI.java
│   ├── SocketBenchUI.java
│   └── RuntimeConfigUI.java
└── Util/                     # Utilities
    ├── DynamicTooltipUtils.java
    ├── EquipmentTooltipProvider.java
    ├── LangLoader.java
    └── NameResolver.java
```

### Key Systems

- **ECS Damage System** (`EquipmentRefineEST`) - Applies damage/defense multipliers during combat
- **Codec-based Configs** - Uses Hytale's BuilderCodec for type-safe configuration
- **Metadata-Based Storage** - Item level stored in NBT, not item ID
- **Auto-Save** - Weapon data saved every 5 minutes

---

## Building

### Build Requirements

- Java 17+
- Gradle

### Build Commands

```powershell
# Build mod JAR
.\gradlew.bat jar

# Build dynamic damage formatter distribution
.\gradlew.bat dynamicFormatterDist
```

### Output Locations

- Mod JAR: `build/libs/irai.mod.reforge_SocketReforge.jar`
- DynamicFormatter Core: `dist/DynamicFloatingDamageFormatter/DynamicFloatingDamageFormatter-core.jar`
- DynamicFormatter Adapter: `dist/DynamicFloatingDamageFormatter/DynamicFloatingDamageFormatter-with-adapter.jar`

---

## Changelog

For a detailed list of all changes, see [CHANGELOG.md](./CHANGELOG.md).

### Recent Changes

#### Version 1.3.7a (2026-04-09)
- **Added:** German localization (de-DE) for UI/tooltips
- **Added:** Custom damage number toggle in runtime config
- **Fixed:** Runtime config numeric input handling (decimal support)

#### Version 1.3.7 (2026-04-08)
- **Added:** Refinement expansion beyond +3 levels
- **Added:** Refinement material tiers (Glob, Glob Plus, Resonant Glob)
- **Added:** Runtime config enhancements for tier thresholds and weights
- **Added:** Configurable refinement labels per level
- **Fixed:** Damage numbers position (render at target, not player)

#### Version 1.3.6 (2026-04-07)
- **Added:** Lore system with sockets and spirit abilities
- **Added:** Lore socket punching with Ghastly Essence
- **Added:** Lore socket color reroll with Resonant Essence
- **Added:** Void Spawn Ghastly Essence drop (1%)

#### Version 1.3.5 (2026-03-29)
- **Added:** Custom floating damage numbers

#### Version 1.3.4 (2026-03-27)
- **Added:** Localization support
- **Added:** Socket Expander and Diffuser
- **Added:** Spellbook resonance support (staffs)

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz
- **Test Video:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)

---

*For support, feature requests, or bug reports, please visit the GitHub repository.*