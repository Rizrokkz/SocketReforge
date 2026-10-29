# Omnislash Rune Assets

The Omnislash cast roots are editable combo lists:

- `Ability_Omnislash_Rune_Cast_3.json`
- `Ability_Omnislash_Rune_Cast_6.json`
- `Ability_Omnislash_Rune_Cast_8.json`
- `Ability_Omnislash_Rune_Cast_12.json`

Each root starts its feed stage, then runs the matching numbered slash interactions in order. The `Ability_Omnislash_Rune_Slash_XX.json` files control that strike's animation, sound, wait interval, projectile choice, and projectile scale. Their `Simple.RunTime` values start at 0.65 seconds and ramp down to 0.20 seconds.

The slash projectile assets are `Projectile_Config_Omnislash_Slash_0.json`, `_45.json`, `_90.json`, and `_135.json`. They control the slash model, rotation, launch settings, and hit interaction. The projectile is visual; `OmnislashRuneStep` handles target selection, teleporting, and the existing damage ramp for each strike.

To change the combo length, edit the slash interaction list in the cast root. To customize an individual hit, edit its numbered slash interaction and, if needed, its referenced projectile config.
