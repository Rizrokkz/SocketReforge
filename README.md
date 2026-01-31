# SocketReforge

A Hytale mod focusing on RPG aspects such as Refine Equipment and Gem Socketing

**Test Link:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)

---

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Commands](#commands)
4. [Usage Guide](#usage-guide)
5. [Configuration](#configuration)
6. [Troubleshooting](#troubleshooting)
7. [Credits](#credits)

---

## Features

### Weapon Reforging System

SocketReforge allows players to upgrade their weapons up to **+3 levels** using Iron Bars as reforge material. Each upgrade increases weapon statistics including damage and attack speed.

**Upgrade Break Chances:**

| Current Level | Break Chance |
|--------------|--------------|
| +0 → +1      | 10%          |
| +1 → +2      | 15%          |
| +2 → +3      | 20%          |

### Gem Socketing

Add sockets to weapons for gem insertion, enabling players to enhance their weapons with various gem bonuses for additional customization.

### Weapon Stats UI

View detailed weapon statistics in-game, including upgrade levels, stat bonuses, and socket information.

---

## Installation

1. Copy `irai.mod.reforge_SocketReforge.jar` to your Hytale server mods folder
2. Ensure all required dependencies are installed
3. Start your server

---

## Commands

| Command | Description |
|---------|-------------|
| `/patchassets` | Patch weapons from Assets.zip/folder/jars |
| `/weaponstats` | View equipped weapon's stats and upgrade level |
| `/importweapons` | Import weapons from the live item registry |
| `/checkname` | Check weapon name validity |

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

---

## Usage Guide

### How to Reforge Weapons

1. **Obtain Iron Bars** - Collect Iron Bars to use as reforge material (1 bar per attempt)
2. **Craft a Reforge Station** - Craft the reforge anvil
3. **Select Your Weapon** - Hold the weapon you wish to upgrade
4. **Apply Iron Bars** - Use the reforge interaction to attempt an upgrade
5. **Risk Assessment** - Be aware of the break chance at each level

### Adding Custom Weapons

To add a custom weapon to the reforge system:

1. Add weapon JSON to your `.jar` mod
2. Include `"Categories": ["Items.Weapons"]` in the JSON
3. Run `/patchassets`

A Hytale mod focusing on RPG aspects such as Refine Equipment and Gem Socketing

**Test Link:** [YouTube](https://www.youtube.com/watch?v=QZYTjpr7mms)

---

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Commands](#commands)
4. [Usage Guide](#usage-guide)
5. [Configuration](#configuration)
6. [Troubleshooting](#troubleshooting)
7. [Credits](#credits)

---

## Features

### Weapon Reforging System

SocketReforge allows players to upgrade their weapons up to **+3 levels** using Iron Bars as reforge material. Each upgrade increases weapon statistics including damage and attack speed.

**Upgrade Break Chances:**

| Current Level | Break Chance |
|--------------|--------------|
| +0 → +1      | 10%          |
| +1 → +2      | 15%          |
| +2 → +3      | 20%          |

### Gem Socketing

Add sockets to weapons for gem insertion, enabling players to enhance their weapons with various gem bonuses for additional customization.

### Weapon Stats UI

View detailed weapon statistics in-game, including upgrade levels, stat bonuses, and socket information.

---

## Installation

1. Copy `irai.mod.reforge_SocketReforge.jar` to your Hytale server mods folder
2. Ensure all required dependencies are installed
3. Start your server

---

## Commands

| Command | Description |
|---------|-------------|
| `/patchassets` | Patch weapons from Assets.zip/folder/jars |
| `/weaponstats` | View equipped weapon's stats and upgrade level |
| `/importweapons` | Import weapons from the live item registry |
| `/checkname` | Check weapon name validity |

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

---

## Usage Guide

### How to Reforge Weapons

1. **Obtain Iron Bars** - Collect Iron Bars to use as reforge material (1 bar per attempt)
2. **Craft a Reforge Station** - Craft the reforge anvil
3. **Select Your Weapon** - Hold the weapon you wish to upgrade
4. **Apply Iron Bars** - Use the reforge interaction to attempt an upgrade
5. **Risk Assessment** - Be aware of the break chance at each level

### Adding Custom Weapons

To add a custom weapon to the reforge system:

1. Add weapon JSON to your `.jar` mod
2. Include `"Categories": ["Items.Weapons"]` in the JSON
3. Run `/patchassets`

**Weapon JSON Requirements:**

- Filename must start with `Weapon_` (e.g., `Weapon_Sword_Custom.json`)
- Must include proper weapon definitions
- Must have a unique item ID

### Viewing Weapon Stats

Use `/weaponstats` to view:

- Current upgrade level
- Stat bonuses from upgrades
- Weapon durability
- Socket information

---

## Configuration

### Auto-Save

Weapon upgrades are automatically saved every 5 minutes. Data persists across server restarts.

### Weapon Sync

Display names sync every 30 seconds to ensure accuracy across all connected players.

---

## Troubleshooting

### Weapons Not Being Detected

1. Ensure weapon JSON files are in your mod's JAR under `Server/Item/Items/Weapon/`
2. Filenames must start with `Weapon_`
3. Run `/patchassets` after adding new weapons

### Assets Path Issues

1. Verify `assets_path.txt` exists in the server root directory
2. Check that the path is correct (use forward slashes on Linux, backslashes on Windows)

### Plugin Not Loading

1. Check server console for error messages
2. Ensure all dependencies are installed correctly
3. Verify the JAR file is in the correct mods folder

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz

---

Buy me a Coffee

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P41T8LCR)

a Hytale mod focusing on RPG aspects such as Refine Equipment and Gem Socketing

**Weapon JSON Requirements:**

- Filename must start with `Weapon_` (e.g., `Weapon_Sword_Custom.json`)
- Must include proper weapon definitions
- Must have a unique item ID

### Viewing Weapon Stats

Use `/weaponstats` to view:

- Current upgrade level
- Stat bonuses from upgrades
- Weapon durability
- Socket information

---

## Configuration

### Auto-Save

Weapon upgrades are automatically saved every 5 minutes. Data persists across server restarts.

### Weapon Sync

Display names sync every 30 seconds to ensure accuracy across all connected players.

---

## Troubleshooting

### Weapons Not Being Detected

1. Ensure weapon JSON files are in your mod's JAR under `Server/Item/Items/Weapon/`
2. Filenames must start with `Weapon_`
3. Run `/patchassets` after adding new weapons

### Assets Path Issues

1. Verify `assets_path.txt` exists in the server root directory
2. Check that the path is correct (use forward slashes on Linux, backslashes on Windows)

### Plugin Not Loading

1. Check server console for error messages
2. Ensure all dependencies are installed correctly
3. Verify the JAR file is in the correct mods folder

---

## Credits

- **Author:** iRaiden
- **GitHub:** https://github.com/Rizrokkz

---

Buy me a Coffee

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P41T8LCR)
