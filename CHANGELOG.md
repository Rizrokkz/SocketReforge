# Changelog - SocketReforge

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] - 2026-02-27

### New Feature: Socket Punching & Essence Socketting

This major update introduces a complete socket system using specialized benches and materials.

#### Socket Punch Bench

- **New Bench: Socket Punch Bench**
  - Crafted using 8 Iron Bars + 3 Emeralds at a Workbench (Tier 2)
  - Used to punch sockets into weapons and armor
  - Custom 3D model and animations
  - Interaction: `SocketPunchBench`

- **Socket Puncher Material**
  - Crafted at Salvage Bench: 1 Iron Bar → 15 Socket Punchers
  - Required material for each socket punch attempt
  - Configurable success/break chances per socket level

- **Socket Stabilizer Material** (Optional Enhancement)
  - Crafted at Alchemy Bench: 15 Socket Punchers + 1 Emerald Gem
  - Provides +15% success chance and -5% break chance
  - Recommended for higher-risk socket punches

- **Socket Punching Mechanics**
  - Max 4 sockets per item (5th possible as rare bonus)
  - Configurable success chances: 90% → 75% → 55% → 35%
  - Configurable break chances: 2% → 5% → 10% → 18%
  - Bonus 5th socket: 1% chance when punching 4th socket
  - Broken sockets can be repaired with Voidheart

#### Essence Socket Bench

- **New Bench: Essence Socket Bench**
  - Crafted using 8 Iron Bars + 3 Wood at a Workbench (Tier 2)
  - Used to socket essences into punched sockets
  - Custom 3D model and animations
  - Interaction: `EssenceSocket`

- **Essence Types** (6 varieties)
  - **Fire**: +2-12% Damage or +3-15 Flat Damage
  - **Ice**: +2-5% Slow/Freeze, +2-12 Cold Damage
  - **Lightning**: +3-15% Attack Speed, +2-8% Crit
  - **Life**: +2-10% Lifesteal (weapons) or +10-50 HP (armor)
  - **Void**: +5-25% Crit Damage
  - **Water**: +2-10% Evasion (armor only)

- **Tier System**
  - Consecutive essences of the same type build tier (max T5)
  - Example: "Life, Life, Life, Fire" = Tier 3 Life, Tier 1 Fire
  - Higher tiers provide significantly stronger effects

- **Voidheart**
  - Used to repair broken sockets
  - Consumed automatically when repairing while socketing

#### Configuration (SocketConfig.json)

- `MAX_SOCKETS`: [4, 4] - Max sockets for weapons and armor
- `PUNCH_SUCCESS_CHANCES`: [0.9, 0.75, 0.55, 0.35] - Success rate per socket level
- `PUNCH_BREAK_CHANCES`: [0.02, 0.05, 0.1, 0.18] - Break chance per socket level
- `ESSENCE_REMOVAL_SUCCESS`: [0.7] - Essence removal success rate
- `ESSENCE_REMOVAL_DESTROY`: [0.3] - Essence destroy on removal

### UI Improvements

- **SocketPunchUI Redesign**
  - Complete overhaul of socket punching interface
  - Improved visual feedback for socket operations
  - Better progress tracking and display
  - Added stat preview before punching

- **WeaponStatsUI Improvements**
  - Enhanced weapon statistics display
  - Better socket information visualization
  - Improved stat comparison tools

- **EssenceSocketUI Updates**
  - Enhanced essence socket interface
  - Better socket state visualization

### New Commands

- **ItemMetaCommand Added**
  - `/itemmeta` command to view and manage item metadata
  - Inspect refinement levels, socket data, and essence information
  - Debug tool for item property analysis

### New Utility Classes

- **DynamicTooltipUtils** - Dynamic tooltip generation for socketed items
- **LangLoader** - Centralized language string loading
- **NameResolver** - Advanced name resolution with caching
- **ReforgeLogger** - Dedicated logging system for reforge operations

### Technical Improvements

- **SocketData Enhancement** - New socket data handling with tier calculations
- **EssenceRegistry** - Centralized essence type definitions and tier effects
- **SocketEffectEST** - Entity Component System for applying essence effects
- **DynamicTooltips Integration** - Full compatibility with DynamicTooltipsLib mod
  - Socket and essence information displayed as in-game tooltips
  - Per-item metadata tooltips via Provider-based approach
  - Auto-refreshes tooltips when sockets/essences are modified
- Event system improvements for better interaction handling

---

## [1.1.0] - 2026-02-23

### Major Overhaul

#### Metadata-Based Refinement System

This update completely overhauls how refinement data is stored, moving from item ID suffixes to NBT metadata.

- **Metadata-Based Refinement**
  - Refinement levels (0-3) are now stored in item NBT metadata instead of creating duplicate item IDs
  - Item IDs no longer change when upgrading (e.g., `Weapon_Axe_Cobalt` stays the same at all levels)
  - Cleaner data model with `RefinementData` class for storing level information
  - `RefinementManager` handles all refinement operations centrally

- **Refinement Items**
  - Refinement Glob - Crafted at Salvage Bench Using 1 Iron Ore = 15 Globs
  - Refinement Globs used during refinement 3
  
- **PatchAssets Removed**
  - `/patchassets` command has been **completely removed**
  - No longer needed since items are no longer duplicated for each upgrade level
  - Weapons and armor work out of the box without any patching required
  - No more `assets_path.txt` configuration needed

- **New Configuration**
  - `SocketConfig.json` for socket punching and essence settings
  - Configurable max sockets, success/break chances, removal rates

### Migration Notes

- Locate the mods directory and remove all patched folders from previous version

### Removed

- `/patchassets` command — No longer needed
- `PatchAssetsCommand.java` — Deleted
- Item duplication system — Replaced with metadata storage

---

## [1.0.2] - 2024-XX-XX

### Fixed

#### Bug Fixes

- **Name Bug Fix**
  - Fixed issue with item name handling during reforging process
  - Improved display name consistency for upgraded items

---

## [1.0.1] - 2026-02-22

### Added

#### Armor Support

- **Armor Reforging System**
  - Extended reforging system to support armor items
  - Armor items identified by "Armor_" prefix, Items.Armor category, or structural armor properties
  - Defense multipliers per upgrade level (default: +5%, +10%, +15%)
  - Armor break chances per upgrade transition (default: 10%, 5%, 2%)
  - Display names: Base (""), +1 ("Reinforced"), +2 ("Fortified"), +3 ("Impenetrable")

- **Dynamic Armor Detection**
  - PatchAssets command now scans armor items from Assets.zip and mod JARs
  - Automatically discovers armor parent template names during scanning
  - Structural armor properties detection (ArmorStats, DefenseProperties, ProtectionProperties, etc.)
  - Generates upgrade variants for armor items (Armor_*+1, Armor_*+2, Armor_*+3)

- **ECS Defense System**
  - EquipmentRefineEST now applies defense multipliers for armor
  - Armor break chance applied when armor takes damage
  - Both weapons (damage) and armor (defense) receive bonuses from reforging

- **Configuration Updates**
  - Added DEFENSE_MULTIPLIERS config (default: 0.05, 0.10, 0.15)
  - Added ARMOR_BREAK_CHANCES config (default: 10, 5, 2)
  - Generalized RefinementConfig to support both weapon and armor settings

- **UI Updates**
  - WeaponStats command now displays armor stats and defense bonuses
  - Generalized item type handling in stats display

---

## [1.0.0] - 2024-XX-XX

### Added

#### Core Features

- **Weapon Reforging System**
  - Upgrade weapons up to +3 levels using Iron Bars as reforge material
  - Each upgrade increases weapon statistics (damage and attack speed)
  - Risk-based system with break chances at each level
  - Multiple upgrade outcomes: Degrade, Same, Upgrade, Jackpot

- **Weapon Stats UI**
  - View detailed weapon statistics in-game
  - Display upgrade levels, stat bonuses, and socket information
  - Progress bar visualization for upgrade levels
  - Color-coded display for different upgrade tiers

- **Gem Socketing**
  - Add sockets to weapons for gem insertion
  - Enable players to enhance weapons with various gem bonuses

#### Configuration System

- **RefinementConfig** - Configurable refinement rates
  - Damage multipliers per upgrade level (default: 1.0, 1.10, 1.15, 1.25)
  - Break chances per upgrade transition (default: 1%, 5%, 7.5%)
  - Reforge weights for each transition level (degrade/same/upgrade/jackpot)

- **SFXConfig** - Sound effects configuration
  - Customizable sounds for reforge start, success, failure, jackpot
  - Configurable reforge bench block validation

#### Commands

- `/patchassets` - Patch weapons from Hytale Assets (Assets.zip/folder/jars)
- `/weaponstats` - View equipped weapon's stats, upgrade level, and next upgrade values
- `/importweapons` - Import weapons from the live item registry
- `/checkname` - Check weapon name validity for reforging

#### Data Management

- **Auto-Save System**
  - Automatic weapon upgrade saving every 5 minutes
  - Data persists across server restarts

- **Weapon Sync System**
  - Display names sync every 30 seconds
  - Ensures accuracy across all connected players

#### ECS Damage System

- **EquipmentRefineEST** - Entity Component System for damage modification
  - Applies damage multipliers based on weapon upgrade level
  - Automatic weapon detection in player's hotbar
  - Clamped multipliers for valid upgrade levels (0-3)

### Technical Details

#### Weapon Detection
- Checks item IDs starting with "Weapon_"
- Validates item Categories containing "Items.Weapons"
- Verifies item has Weapon structure (WeaponStats, DamageProperties, etc.)
- Automatic exclusion of arrows from refinement

#### Upgrade Level System
- Level determined by weapon ID suffix (e.g., Weapon_Axe_Cobalt1 = +1)
- No persistent tracking needed - level embedded in item ID
- Display names: Base (""), +1 ("Sharp"), +2 ("Deadly"), +3 ("Legendary")

#### Caching System
- Weapon category/structure checks cached for 5 minutes
- Reduces repeated reflection overhead

#### Plugin Architecture
- Built on Hytale's JavaPlugin framework
- Event-driven architecture with global event listeners
- Timer-based scheduled tasks for auto-save and sync

---

## [Unreleased]

### Planned Features

- Additional weapon types support
- Enhanced gem socketing mechanics
- Server-wide leaderboards for reforging achievements
- Custom reforge animations
- Multi-language support

---

## Installation

1. Copy `irai.mod.reforge_SocketReforge Hytale server.jar` to your mods folder
2. Ensure all required dependencies are installed
3. Start your server

## Upgrading from Previous Versions

### From Alpha/Beta versions:
- Backup your existing weapon data
- Replace the old JAR with the new version
- Update configuration files if needed
- Restart the server

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz
- **Test Video:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)

---

*For support, feature requests, or bug reports, please visit the GitHub repository.*
