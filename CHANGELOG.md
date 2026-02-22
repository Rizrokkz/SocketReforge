# Changelog - SocketReforge

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
