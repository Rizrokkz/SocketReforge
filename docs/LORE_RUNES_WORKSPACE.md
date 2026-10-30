# Lore Rune Workspace

Lore rune assets stay inside Hytale's normal asset-type folders, grouped by family:

```text
Server/Item/Items/Ingredient/Soulstone/Core/       Core ability runes from max-level lore
Server/Item/Items/Ingredient/Soulstone/Modifiers/  Passive modifier runestones
Server/Item/Items/Ingredient/Soulstone/Combat/     BloodRush, Octaslash, Omnislash, Pummel, Soul Reaper
Server/Item/Items/Ingredient/Soulstone/Summons/    Wolf Pack runes

Server/Item/Interactions/LoreRunes/<family>/
Server/Item/RootInteractions/LoreRunes/<family>/
Server/ProjectileConfigs/LoreRunes/<family>/
Server/Item/Animations/LoreRunes/<family>/
Server/Entity/Effects/Lore/  Shared lore effects and trails
```

The asset filenames remain the asset IDs. JSON references still use those IDs, not folder paths.

## Core rune spirit mapping

The authoritative mapping is `CORE_RUNESTONES` in `LoreSocketManager`:

| Spirit | Core rune | Feed progression |
| --- | --- | --- |
| `Whale_Humpback` | Grand Slam | `Rune_GrandSlam` |
| `Horse_Skeleton` | Razorstrike | `Rune_Razorstrike` |
| `Skeleton_Burnt_Praetorian` | Whirlwind | `Rune_Whirlwind` |
| `Spirit_Thunder` | Vortexstrike | `Rune_Vortexstrike` |
| `Camel` | Big Arrow | `Rune_Big_Arrow_1` through `_3` |
| `Horse` | Volley | `Rune_Volley_3` through `_6` |
| `Bear_Grizzly` | Berserk | `Rune_Berserk` |
| `Spirit_Manafont` | Mana Font | `Rune_Mana_Font_1` through `_4` |

Modifier spirit mappings and legacy extracted runestone support are in `MODIFIER_RUNESTONES` in the same class. Core rune execution and hit effects are handled by `LoreProcHandler`; localized item names and descriptions are in `Server/Languages/*/irai.lang`.

## Where to change a rune

1. Edit the item definition under `Soulstone/<family>/` for its slot, cost, cooldown, and cast root.
2. Follow `Cast` to `RootInteractions/LoreRunes/<family>/`.
3. Follow the root's interaction IDs to `Interactions/LoreRunes/<family>/` for the sequence and effects.
4. Edit `ProjectileConfigs/LoreRunes/<family>/` when that sequence launches projectiles.
5. Keep item, root, interaction, and projectile filenames stable when changing behavior; references use their asset IDs.
