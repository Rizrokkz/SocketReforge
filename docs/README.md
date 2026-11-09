# SocketReforge

A Hytale mod focusing on RPG aspects such as Equipment Refinement, Gem Socketing, Essence Systems, and Spirit-based Lore abilities.

**Test Link:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)  
**Version:** 1.3.7a

---

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Commands](#commands)
4. [Usage Guide](#usage-guide)
5. [Configuration](#configuration)
6. [Architecture](#architecture)
7. [Changelog](#changelog)
8. [Credits](#credits)

---

## Features

### Weapon Reforging System

SocketReforge allows players to upgrade weapons and armor using refinement materials. The system supports levels beyond **+3** with tiered material costs (Refinement Glob, Glob Plus, Resonant Glob).

**Reforge Outcomes:**

Each reforge attempt rolls one of four possible outcomes based on weighted probabilities:

| Outcome | Effect |
|---------|--------|
| **Degrade** | Item drops one level |
| **Same** | No change to item level |
| **Upgrade** | Item gains one level |
| **Jackpot** | Item gains two levels |

**Default Reforge Weights:**

| Transition | Degrade | Same | Upgrade | Jackpot |
|-----------|---------|------|---------|---------|
| +0 → +1 | 0% | 65% | 34% | 1% |
| +1 → +2 | 35% | 45% | 19% | 1% |
| +2 → +3 | 60% | 30% | 9.5% | 0.5% |

**Default Break Chances** (item is destroyed):

| Transition | Break Chance |
|-----------|-------------|
| +0 → +1 | 1% |
| +1 → +2 | 5% |
| +2 → +3 | 7.5% |

**Default Damage Multipliers:**

| Level | Name | Multiplier | Bonus |
|-------|-----------|-----------|-------|
| +0 | Base | x1.00 | — |
| +1 | Sharp | x1.10 | +10% |
| +2 | Deadly | x1.15 | +15% |
| +3 | Legendary | x1.25 | +25% |

### Socket System

Add sockets to weapons and armor for essence insertion.

- **Socket Punch Bench** - Punch sockets into items
- **Max Sockets:** 4 (default), with bonus chance for 5th
- **Support Materials:** Socket Puncher, Stabilizer, Expander, Diffuser

**Socket Punching Mechanics:**

| Current Sockets | Success Chance | Break Chance |
|-----------------|----------------|--------------|
| 0 | 90% | 5% |
| 1 | 75% | 10% |
| 2 | 55% | 20% |
| 3 | 35% | 35% |

### Essence System

Socket essences into punched sockets to add powerful effects.

**Essence Types:**

| Essence | Weapon Effect | Armor Effect |
|---------|---------------|--------------|
| **Fire** | +2-12% Damage | +3-15% Fire Defense |
| **Ice** | +2-5% Slow/Freeze | +2-12% Cold Defense |
| **Lightning** | +3-15% Attack Speed | +2-8% Crit Chance |
| **Life** | +2-10% Lifesteal | +10-50 HP |
| **Void** | +5-25% Crit Damage | +5-25% Evasion |
| **Water** | +2-10% Evasion | +10-50% Regen |

- **Tier System:** Consecutive essences of same type build tier (max T5)
- **Greater Essences:** Concentrated variants with enhanced effects

### Resonance System

A runeword-like mechanic that activates powerful effects when specific essence combinations are socketed in exact order.

**Resonance Types:**
- BURN_ON_CRIT, CHAIN_SLOW, EXECUTE, ARMOR_SHRED, THUNDER_STRIKE
- MULTISHOT_BARRAGE, CROSSBOW_AUTO_RELOAD, PLUNDERING_BLADE
- FROST_NOVA_ON_HIT, THORNS_SHOCK, CHEAT_DEATH, HEAL_SURGE, SHOCK_DODGE, AURA_BURN

- **Seeded Resonance:** Uses main world seed to shuffle patterns per server
- **Resonance Compendium:** Craftable item to compile and combine recipes

### Lore System

Adds lore sockets and spirit abilities that proc based on combat events.

**Lore System Flow:**
1. Lore sockets roll on loot drops
2. Lore gems act as empty vessels until first kill
3. Spirit assigned based on gem color and zone
4. Spirit levels up via proc triggers
5. Feed gates every 5 levels require Resonant Essence
6. At level 100, spirit is absorbed by player

**Spirit Triggers:** ON_HIT, ON_CRIT, ON_SKILL_USE, ON_DAMAGE_TAKEN, ON_BLOCK, ON_DODGE, ON_KILL, ON_NEAR_DEATH, ON_FIRST_KILL, ON_SPRINT, ON_JUMP, ON_SNEAK, ON_HEAL, ON_POTION_USE, ON_LORE_PROC, ON_STATUS_APPLY

### Dynamic Floating Damage Numbers

Custom configurable floating damage text. Can be toggled on/off via runtime config.

---

## Installation

1. Copy `irai.mod.reforge_SocketReforge.jar` to your Hytale server `mods` folder
2. Start server — config files are generated automatically
3. Customize configs as needed and restart
4. After major updates, run `/reforgeconfig` (OP) and reset defaults

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
| `/reforgeconfig` | Open runtime config UI (tune all settings live) |
| `/reforgeadmin` | OP tools for held-item refinement/socket metadata |
| `/loreabilities` | Dump lore spirit ability map |
| `/lorecolors` | Dump lore spirit to gem color map |
| `/loreapply` | OP: apply a lore spirit to held weapon |
| `/spawnequipchest` | Spawn test chest with equipment-loot |
| `/spawnequipenemy` | Spawn equipment-eligible enemy NPCs |
| `/itemmeta` | View or modify item metadata |
| `/resonancecombos` | List seeded resonance combinations |
| `/resonancerecipe` | OP: give resonant recipe shards |
| `/resonanceworldscan` | Scan world containers for resonance |

---

## Usage Guide

### How to Reforge Items

1. **Obtain Materials** — Collect Iron Bars or Refinement Globs
2. **Find a Reforge Bench** — Interact with a Reforge Bench block
3. **Hold Your Item** — Equip the item you wish to upgrade
4. **Use the Interaction** — Trigger the reforge interaction
5. **Assess the Outcome** — Degrade, Same, Upgrade, Jackpot, or Shatter

### How to Socket Items

1. **Obtain Socket Puncher** — Craft at Salvage Bench
2. **Find Socket Punch Bench** — Interact with the bench
3. **Add Sockets** — Use Socket Puncher to punch sockets
4. **Obtain Essences** — Collect or craft essences
5. **Socket Essences** — Use Essence Socket Bench to insert essences

### Lore System Quick Start

1. Configure Spirit ↔ Gem matches in your config
2. Obtain a lore gem and socket it into your weapon
3. Find an NPC bound to that gem color
4. Land the first kill while holding the weapon — spirit binds
5. Spirit procs in combat and gains XP
6. Feed Resonant Essence at level gates (every 5 levels)
7. At level 100, spirit absorbs into player

---

## Configuration

### Generated Config Files

- `SFXConfig.json` - Sound effects
- `RefinementConfig.json` - Refinement rates and labels
- `SocketConfig.json` - Socket punch chances
- `LootSocketRollConfig.json` - Loot socket chances
- `LoreConfig.json` - Lore system rules
- `LoreMappingConfig.json` - Spirit/gem mappings
- `DamageNumberConfig.json` - Floating damage settings
- `WorldRepairConfig.json` - World repair settings

### Runtime Config

Use `/reforgeconfig` (OP) to tune settings live:
- Socket, refinement, and loot settings
- Damage number toggle
- All changes save instantly

---

## Architecture

### Package Structure

```
irai.mod.reforge
├── ReforgePlugin.java              # Main plugin entry point
├── Commands/                       # Command handlers
├── Config/                         # Configuration codecs
├── Entity/Events/                  # ECS event systems
├── Interactions/                   # Interaction handlers
├── Lore/                           # Lore/spirit system
├── Socket/                         # Socket/essence system
├── Systems/                        # Background systems
├── UI/                             # Custom UI pages
└── Util/                           # Utilities
```

### Key Systems

- **ECS Damage System** — Applies damage/defense multipliers
- **Codec-based Configs** — Type-safe configuration
- **Metadata-based Storage** — Item data stored in NBT
- **Auto-Save** — Data saved every 5 minutes

---

## Changelog

For full changelog, see [CHANGELOG.md](./CHANGELOG.md).

### Recent Changes

**v1.3.7a** - German localization, custom damage number toggle, runtime config fixes  
**v1.3.7** - Refinement expansion beyond +3, tiered materials, configurable labels  
**v1.3.6** - Lore system, lore socket punching/reroll, Void Spawn drops  
**v1.3.5** - Custom floating damage numbers  
**v1.3.4** - Localization support, Socket Expander/Diffuser  

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz

---

Buy me a Coffee

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P41T8LCR)
