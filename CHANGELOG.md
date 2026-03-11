# Changelog - SocketReforge

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.1] - 2026-03-10

### Added

- **Runtime Config Editor (HyUI, OP-only)** - `/reforgeconfig` (aliases `configui`, `runtimeconfig`) for live tuning of socket, refinement, loot, and weather settings with instant save/apply and reload-from-disk support.
- **Loot Socket Roll Config** - New `LootSocketRollConfig` for chest/NPC socket roll tuning, resonance roll chance, and broken-socket clamp ranges.
- **Weather Event Spawns** - Spirit_Thunder rain spawns with `WeatherEventConfig` controls for timing, counts, distance, and despawn delay.
- **Thorium Builder's Hammer** - New high-durability hammer; stronger break-chance reduction in the Reforge bench and essence refunds on successful clear in the Essence bench.
- **Resonance Recipe Drops** - Resonant loot rolls now drop a `Resonant_Recipe` shard with a partial recipe hint instead of pre-socketed equipment.

### Changed

- **Reforge Bench UI** - Shows support durability and base damage/defense preview; hammer support now distinguishes Iron vs Thorium durability loss and break multipliers.
- **Essence Bench UI** - Shows support durability; Thorium hammer adds refund messaging on clear.
- **Thorium Hammer Risk** - Essence clear has a slim chance to reduce max sockets.
- **World Loot Resonance** - Chest/NPC loot can roll fully socketed resonance layouts based on new resonance chance settings.

### Fixed

- **Socket Bench Support Selection** - Selecting "None" no longer auto-selects the first stabilizer.
- **World Loot Injection** - Rolled chests are now marked in-world (custom flag) so injected socket materials don't respawn after restart.

---

## [1.3.0] - 2026-03-08

### New Feature: Socket Loot System

Equipment with sockets can now drop from various sources in the game world!

- **NPC Loot Drops** — Defeated enemies can now drop socketed equipment
- **Treasure Chests** — Found chests have a chance to contain socketed gear
- **Configurable Rolls** — Server operators can configure drop rates and socket chances

### New Feature: Enhanced Tooltips

- **Average Damage Display** — Tooltips now show average damage calculations for weapons
- Takes into account base damage, refinement bonuses, socket bonuses, and essence effects
- Helps players understand their weapon's true damage output

### New Feature: Greater Essences

- **Concentrated Essences** — New higher-tier essence variants available
- Provides stronger effects than regular essences
- Crafted through the essence bench system

### Optimization

- **Plugin Loading** — Optimized startup performance
- Reduced initialization time and memory footprint

---

## [1.2.9] - 2026-03-06

### Hotfix

- **Salvage Bench Metadata Compatibility**
  - Fixed salvage processing for equipment that contains SocketReforge metadata.
  - Fixed salvage processing for items with tooltip-related metadata overlays.
  - Salvage input now sanitizes metadata in-place for recipe matching.
  - Salvage recipe refresh now happens immediately after sanitize (no need to remove/re-add item).

- **Metadata Safety on Cancel**
  - Original item metadata is now restored when salvage is canceled and the item is taken back.
  - Prevents permanent metadata loss when players change their mind before processing.

- **Logging**
  - Removed per-tick salvage compatibility debug spam from normal runtime logs.
  - Kept one-time reflection failure diagnostics for troubleshooting.

---

## [1.2.8] - 2026-03-05

### Added

- **Extended Resonance System**
  - Added 3 new resonance effects for 5-socket configurations
  - Added dagger weapon class support for resonance
  - Added bow weapon class support for resonance
  - Added crossbow weapon class support for resonance

### Fixed

- **Life Essence Values**
  - Fixed Life essence adding incorrect stat values
  - Corrected HP bonus calculations for armor Life essences

- **UI Log Spam**
  - Removed verbose logging from bench UI operations
  - Cleaned up server console output for Socket Punch, Essence, and Reforge bench UIs

---

## [1.2.7] - 2026-03-05

### New Feature: Resonance System

This update introduces the **Resonance System** — a runeword-like mechanic that activates powerful special effects when specific essence combinations are socketed in exact order.

#### How Resonance Works

- Resonance activates only when **all sockets are filled** with essences
- The essence **order matters** — different sequences produce different results
- Higher quality resonances unlock at higher socket counts
- Legendary-quality resonances provide the most powerful effects

#### Resonance Features

- **11 Unique Resonance Effects** — Each with distinct combat bonuses
- **Quality Levels** — Common, Rare, Epic, and Legendary tiers
- **Weapon & Armor Specific** — Different resonances for different equipment types
- **No Extra Items Required** — Resonance is free once the right combination is found!

#### Examples (Discover the rest yourself!)

- Some resonances trigger on critical hits
- Some provide chain effects to nearby enemies
- Some grant defensive bonuses like thorns or dodge
- Some have execute mechanics for finishing enemies

<em>Experiment with different essence orders to discover all resonance combinations!</em>

---

## [1.2.6] - 2026-03-04

### Added

- **OP-only Bench Commands**
  - Added `/socketpunch` to open Socket Punch UI
  - Added `/essence` to open Essence Socket UI
  - Added `/reforgeadmin` (alias `/rfadmin`) for held-item admin edits:
    - `/reforgeadmin refine <0-3>`
    - `/reforgeadmin sockets <current> [max]`
    - `/reforgeadmin addmax <amount>`
  - Admin edits now refresh related socket/refinement tooltips after updates

- **Reforge Bench HyUI Page**
  - Added external HTML-driven Reforge bench page (`ReforgeBench.html`)
  - Keeps compatibility with existing refinement logic and SFX pipeline

### Changed

- **Reforge Bench UI Layout**
  - Refined preview data is now split into clearer sections/columns
  - Key metadata is shown in dedicated lines (`Name`, `Refinement Level`, current stat line)
  - Refinement chances are listed line-by-line
  - Expected damage outcome text is split into separate lines for readability

- **Process Bar/Timing Updates**
  - Reforge process now uses a timed 1-second progress flow
  - Reforge progress bar uses `boost_fill.png` with `boost_track.png` as track
  - Process button is disabled during active processing

- **Refinement Support Item**
  - `Tool_Hammer_Iron` can be used as optional support material in refinement
  - Hammer reduces break chance and loses 5% durability per refinement process
  - Hammer is consumed when durability reaches zero

### Fixed

- **Socket/Essence Bench Behavior**
  - Fixed `Ingredient_Voidheart` support detection in Essence bench UI dropdowns
  - Fixed post-process tooltip refresh issues after socket/essence updates
  - Fixed handling for broken sockets: repair-first flow now applies when required
  - Essence socketing can proceed when there is at least one available non-broken socket
  - Added hammer-based essence clearing failure behavior: durability still drops on fail and a random socket can break

- **Effect/Combat Corrections**
  - Updated Void essence crit-damage progression to tiered 5% intervals up to 25% at T5
  - Added T5 Void Blood Pact behavior (HP sacrifice contributes to bonus damage)
  - Added T5 armor name prefixes for essence states:
    - Fire T5: `Infernal`
    - Ice T5: `Glacial`
  - Corrected several effect application paths (including lightning/defensive checks) in ECS handlers

- **Logging**
  - Removed noisy Reforge bench template-path info spam from server logs

---

## [1.2.5] - 2026-03-01

### New Feature: HyUI Integration & Enhanced Socket System

This update adds optional HyUI support for better UI experiences and introduces new socket management features.

#### HyUI Integration

- **Optional HyUI Support**
  - UI now uses HyUI framework when available
  - Falls back gracefully when HyUI is not installed
  - Enhanced UI responsiveness and interactivity
  - EquipmentListUI, SocketBenchUI, and EssenceBenchUI now leverage HyUI components

#### Socket Management

- **Iron Building Hammer**
  - New item for clearing sockets from equipment
  - Crafted at appropriate bench (configurable)
  - Allows players to reset socket configurations
  - Provides alternative to essence removal for socket management

### Bug Fixes

- **Damage Calculation Fixes**
  - Fixed various damage calculation issues with socketed equipment
  - Improved damage multiplier application for refined weapons
  - Corrected essence effect damage application

### New Effects & Enhancements

- **Regeneration System**
  - Added new regeneration effects for armor
  - Life essence now provides HP regeneration on armor
  - Water essence provides natural regen benefits

- **Tier 5 Essence Effects Extended**
  - **Fire Essence on Armor**: Now provides fire damage reflection/bonus
  - **Ice Essence on Weapons**: Extended freeze effects at T5
  - Full T5 effects now available for all essence types on both weapons and armor

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

## [1.3.2] - 2026-03-11

### Added

#### Loot & Drops

- **Crop Essence Drops** - Farmable crop droplists now inject Water essence (default 5%) and Stamina crops inject Lightning essence (default 15%).
- **Aquatic/Flying Essence Drops** - Aquatic NPCs can drop Water essence and flying NPCs can drop Lightning essence (both configurable).
- **Essence Drop Controls** - LootSocketRollConfig now includes chance + min/max quantity settings for crop/NPC Water and Lightning essence drops, with runtime config UI controls.
- **Socketed Essence Rolls** - World loot can roll with filled essences (greater essence chance supported).

#### Resonance

- **Seeded Resonance Combinations** - Resonance essence order is now generated per main world seed so each server has unique combinations.
- **Resonance Recipe Support** - Completed recipe shards can now be used as support in the Essence bench to trigger resonance.
- **Resonance Migration** - Legacy resonant items and recipe shards are migrated to seeded patterns while preserving revealed slots.

#### Admin & Tools

- **Resonance Admin Tools** - Added `/resonancecombos` to list seeded combos and `/resonanceworldscan` to migrate stored items.

### Changed

#### Socket Defaults

- **Socket Caps Defaults** - Weapon and armor max sockets now default to 4.

#### Loot Defaults

- **Essence Drop Defaults** - Water essence quantities default to 1–2; Lightning essence quantities default to 2–3; NPC Lightning default chance is now 15%.
- **NPC Water Essences** - Water essence drops are now restricted to aquatic roles only.

#### Resonance Rules

- **Resonance Requirements** - Direct resonance without a completed recipe is blocked; recipe usage is only consumed on successful resonance.
- **Recipe Roll Pity** - After 150 failed rolls, the next resonant recipe roll is guaranteed per player.
- **Recipe Combine Logic** - Combine use prioritizes shards that reveal missing slots first.

### Fixed

#### Loot

- **Loot Injection Minimums** - Min quantity can now be 0 for injection rules.
- **Live Essence Drop Tuning** - Existing crop droplists update their essence weights/quantities when config changes are applied.

#### Resonance

- **Recipe Combine Consume** - Combine now avoids consuming shards when no new slots would be revealed.

### Removed

- **Weather Event System** - Removed Spirit_Thunder weather spawns, WeatherEventConfig, and all related runtime config UI controls.

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
