# SocketReforge

A Hytale mod focusing on RPG aspects such as Equipment Refinement and Gem Socketing.

**Test Link:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)

---

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Commands](#commands)
4. [Usage Guide](#usage-guide)
5. [Configuration](#configuration)
6. [Architecture](#architecture)
7. [Troubleshooting](#troubleshooting)
8. [Credits](#credits)

---

## Features

### Weapon Reforging System

SocketReforge allows players to upgrade their weapons up to **+3 levels** using Iron Bars as reforge material. Each upgrade increases weapon damage through configurable multipliers.

**Reforge Outcomes:**

Each reforge attempt rolls one of four possible outcomes based on weighted probabilities:

| Outcome | Effect |
|---------|--------|
| **Degrade** | Weapon drops one level |
| **Same** | No change to weapon level |
| **Upgrade** | Weapon gains one level |
| **Jackpot** | Weapon gains two levels |

**Default Reforge Weights:**

| Transition | Degrade | Same | Upgrade | Jackpot |
|-----------|---------|------|---------|---------|
| +0 → +1 | 0% | 65% | 34% | 1% |
| +1 → +2 | 35% | 45% | 19% | 1% |
| +2 → +3 | 60% | 30% | 9.5% | 0.5% |

**Default Break Chances** (weapon is destroyed):

| Transition | Break Chance |
|-----------|-------------|
| +0 → +1 | 1% |
| +1 → +2 | 5% |
| +2 → +3 | 7.5% |

**Default Damage Multipliers:**

| Level | Multiplier | Bonus |
|-------|-----------|-------|
| +0 (Base) | x1.00 | — |
| +1 (Sharp) | x1.10 | +10% |
| +2 (Deadly) | x1.15 | +15% |
| +3 (Legendary) | x1.25 | +25% |

### Gem Socketing

Add sockets to weapons for gem insertion, enabling players to enhance their weapons with various gem bonuses for additional customization.

### Weapon Stats UI

A custom in-game UI panel (`WeaponStatHUD.ui`) displaying:

- Current weapon name and upgrade level
- Damage multiplier and progress bar
- Next level damage preview
- Reforge outcome probabilities (degrade, break, upgrade, jackpot)
- Max level weapon stats comparison

### Sound Effects System

Configurable sound effects for every reforge event:

| Event | Default Sound |
|-------|--------------|
| Reforge Start | `SFX_Mace_T1_Block_Impact` |
| Success | `SFX_Weapon_Bench_Craft` |
| Jackpot | `SFX_Workbench_Upgrade_Complete_Default` |
| Fail | `SFX_Armour_Bench_Craft` |
| No Change | `SFX_Workbench_Craft` |
| Shatter | `SFX_Door_Temple_Light_Open` |

---

## Installation

1. Copy `irai.mod.reforge_Socket Reforge.jar` to your Hytale server `mods` folder
2. Start your server — config files (`SFXConfig.json`, `RefinementConfig.json`) are generated automatically
3. Customize configs as needed and restart

---

## Commands

| Command | Description |
|---------|-------------|
| `/patchassets` | Patch weapons from Assets.zip, local folders, and mod JARs |
| `/weaponstats` | Open the Weapon Stats UI for the currently held weapon |
| `/checkname` | Check the translation name of the held item |

### Setting Assets Path

The `/patchassets` command requires the path to your Hytale `Assets.zip`. Configure it using ONE of the following methods:

**Option 1: assets_path.txt**

Create a file called `assets_path.txt` in the server root directory containing the full path:

```
F:\XboxGames\Hytale\install\release\package\game\latest\Assets.zip
```

**Option 2: Environment Variable**

Set the `ASSETSPATH` environment variable:

- **Windows:** `set ASSETSPATH=F:\XboxGames\Hytale\install\release\package\game\latest\Assets.zip`
- **Linux:** `export ASSETSPATH=/path/to/Assets.zip`

**Option 3: No path set**

Only local Assets folder/zip and mod JARs will be scanned.

---

## Usage Guide

### How to Reforge Weapons

1. **Obtain Iron Bars** — Collect Iron Bars to use as reforge material (1 bar per attempt)
2. **Find a Reforge Bench** — Interact with a Reforge Bench block
3. **Hold Your Weapon** — Equip the weapon you wish to upgrade in your hotbar
4. **Use the Interaction** — Trigger the reforge interaction to attempt an upgrade
5. **Assess the Outcome** — The result can be a degrade, no change, upgrade, jackpot, or weapon shatter

### Upgrade Level Names

| Level | Name | Display |
|-------|------|---------|
| +0 | *(base)* | No prefix |
| +1 | Sharp | ★ |
| +2 | Deadly | ★★ |
| +3 | Legendary | ★★★ |

### Weapon ID Convention

Upgrade level is embedded in the weapon item ID:

- `Weapon_Axe_Cobalt` → Base (+0)
- `Weapon_Axe_Cobalt1` → +1
- `Weapon_Axe_Cobalt2` → +2
- `Weapon_Axe_Cobalt3` → +3

### Adding Custom Weapons

To add a custom weapon to the reforge system:

1. Add weapon JSON to your `.jar` mod under `Server/Item/Items/Weapon/`
2. Include `"Categories": ["Items.Weapons"]` in the JSON
3. Filename must start with `Weapon_` (e.g., `Weapon_Sword_Custom.json`)
4. Create level variants: `Weapon_Sword_Custom1.json`, `Weapon_Sword_Custom2.json`, `Weapon_Sword_Custom3.json`
5. Run `/patchassets` to register them

### Viewing Weapon Stats

Use `/weaponstats` to open the Weapon Stats UI showing:

- Current upgrade level and progress bar
- Damage multiplier and bonus percentage
- Next level preview
- Reforge outcome probabilities

---

## Configuration

### RefinementConfig.json

Controls all refinement rates and multipliers. Generated automatically on first run.

```json
{
  "DAMAGE_MULTIPLIERS": [1.0, 1.10, 1.15, 1.25],
  "BREAK_CHANCES": [0.01, 0.05, 0.075],
  "WEIGHTS_0_TO_1": [0.00, 0.65, 0.34, 0.01],
  "WEIGHTS_1_TO_2": [0.35, 0.45, 0.19, 0.01],
  "WEIGHTS_2_TO_3": [0.60, 0.30, 0.095, 0.005]
}
```

| Field | Description |
|-------|-------------|
| `DAMAGE_MULTIPLIERS` | Damage multiplier per level [+0, +1, +2, +3] |
| `BREAK_CHANCES` | Weapon shatter chance per transition [0→1, 1→2, 2→3] |
| `WEIGHTS_X_TO_Y` | Outcome weights [degrade, same, upgrade, jackpot] |

### SFXConfig.json

Controls sound effects and valid reforge bench blocks.

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

| Field | Description |
|-------|-------------|
| `BENCHES` | Array of valid reforge bench block IDs |
| `SFX_*` | Sound event IDs for each reforge outcome |

### Auto-Save

Weapon upgrades are automatically saved every 5 minutes. Data persists across server restarts.

### Weapon Sync

Display names sync every 30 seconds to ensure accuracy across all connected players.

---

## Architecture

### Plugin Structure

```
irai.mod.reforge
├── ReforgePlugin.java              # Main plugin entry point
├── Commands/
│   ├── CheckNameCommand.java       # /checkname command
│   ├── CommandUtils.java           # Shared command utilities
│   ├── PatchAssetsCommand.java     # /patchassets command
│   └── WeaponStatsCommand.java     # /weaponstats command
├── Config/
│   ├── RefinementConfig.java       # Refinement rates config (codec-based)
│   └── SFXConfig.java              # Sound effects config (codec-based)
├── Entity/Events/
│   ├── ContainerEventListener.java # Inventory sync for upgraded weapons
│   ├── EquipmentRefineEST.java     # ECS damage multiplier system
│   └── OpenGuiListener.java        # Player ready event handler
├── Interactions/
│   └── ReforgeEquip.java           # Core reforge interaction logic
├── Systems/
│   ├── SyncTasks.java              # Periodic weapon display sync
│   └── WeaponPersistence.java      # Weapon data persistence
├── UI/
│   └── WeaponStatsUI.java          # Custom weapon stats UI page
└── states/
    └── ReforgeState.java           # Reforge state definitions
```

### Key Systems

- **ECS Damage System** (`EquipmentRefineEST`) — Hooks into Hytale's Entity Component System to apply damage multipliers based on weapon upgrade level during combat
- **Weapon Detection** — Multi-layered check: item ID prefix (`Weapon_`), category (`Items.Weapons`), and weapon structure fields (via reflection). Results cached for 5 minutes
- **Codec-based Configs** — Uses Hytale's `BuilderCodec` system for type-safe, auto-serialized configuration
- **ID-based Level Tracking** — Upgrade level is embedded in the weapon item ID suffix, eliminating the need for external persistence

---

## Troubleshooting

### Weapons Not Being Detected

1. Ensure weapon JSON files are in your mod's JAR under `Server/Item/Items/Weapon/`
2. Filenames must start with `Weapon_`
3. Include `"Categories": ["Items.Weapons"]` in the weapon JSON
4. Run `/patchassets` after adding new weapons
5. Check that level variants exist (e.g., `Weapon_Name1.json`, `Weapon_Name2.json`, `Weapon_Name3.json`)

### Assets Path Issues

1. Verify `assets_path.txt` exists in the server root directory
2. Check that the path points to a valid `Assets.zip`
3. Alternatively, set the `ASSETSPATH` environment variable

### Plugin Not Loading

1. Check server console for error messages
2. Verify the JAR file is in the correct `mods` folder
3. Ensure the JAR filename matches the expected pattern: `irai.mod.reforge_Socket Reforge.jar`

### Config Not Loading

1. Delete the existing config JSON files and restart the server to regenerate defaults
2. Verify JSON syntax is valid
3. Check server console for `[ReforgePlugin]` error messages

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz

---

Buy me a Coffee

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P41T8LCR)
