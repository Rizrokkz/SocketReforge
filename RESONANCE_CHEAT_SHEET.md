# Socket Reforge Resonance Cheat Sheet

Source of truth: `src/main/java/irai/mod/reforge/Socket/ResonanceSystem.java` and `src/main/java/irai/mod/reforge/Entity/Events/SocketEffectEST.java`

## Activation Rules

- All sockets must be filled.
- Any broken or locked socket disables resonance.
- Essence order is exact (left to right).
- Resonance is class-gated by weapon type.
- Bow resonances also work on crossbows (backward compatibility in matcher logic).
- Active resonance is flagged as `Legendary` quality metadata.

## Weapon Class Mapping

- `SWORD`: item id contains `sword`
- `AXE`: item id contains `battleaxe` or `axe`
- `MACE`: item id contains `mace` or `club`
- `DAGGER`: item id contains `dagger` or `knife`
- `CROSSBOW`: item id contains `crossbow`
- `BOW`: item id contains `bow` (and not crossbow)
- `STAFF`: item id contains `staff` or `spear`
- `GENERIC`: everything else

## Proc Reference (By Resonance Type)

| Type | Current ECS Effect |
|---|---|
| `BURN_ON_CRIT` | On hit: 22% chance, 0.7s cooldown, +8% bonus damage (min +1). |
| `CHAIN_SLOW` | On hit: 25% chance, 2.0s cooldown, freeze target 1.5s. |
| `EXECUTE` | On hit: +20% damage when target is at or below 25% HP. |
| `ARMOR_SHRED` | On hit: 30% chance, 0.9s cooldown, +10% damage. |
| `THUNDER_STRIKE` | On hit: 20% chance, 1.2s cooldown, +10% bonus damage (min +1). |
| `MULTISHOT_BARRAGE` | Projectile hit only, bow + 5 sockets: 20% chance, 1.6s cooldown, queues 2 extra damage hits. |
| `CROSSBOW_AUTO_RELOAD` | Projectile hit only, crossbow + 5 sockets: 35% chance, 0.45s cooldown, refunds 1 arrow/bolt. |
| `PLUNDERING_BLADE` | Dagger + 5 sockets: 15% chance, 2.5s cooldown, attempts loot steal from NPC drop table. |
| `FROST_NOVA_ON_HIT` | When hit: 25% chance, 4.0s cooldown, freeze attacker 1.5s. |
| `THORNS_SHOCK` | When hit: reflect 6% incoming damage as shock (min 1). |
| `CHEAT_DEATH` | Lethal hit prevention once every 60s; survive at 1 HP. |
| `HEAL_SURGE` | Weapon: heal 10% of dealt damage (min 1), 1.8s cooldown. Armor: heal 5% incoming damage (min 1), 5.0s cooldown. |
| `SHOCK_DODGE` | On successful evasion: 20% chance, 3.5s cooldown, retaliate 4% incoming (min 1). |
| `AURA_BURN` | When hit: 20% chance, 0.9s cooldown, burn attacker for 5% incoming (min 1). |

## Weapon Resonances

### Sword

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Kingsbrand | 3 | FIRE -> LIGHTNING -> LIFE | `THUNDER_STRIKE` | +8% Damage, +6% Crit Chance, +2% Lifesteal |
| Oathblade | 4 | FIRE -> FIRE -> VOID -> LIFE | `BURN_ON_CRIT` | +12% Damage, +10% Crit Damage |
| Winter Duelist | 4 | ICE -> LIGHTNING -> ICE -> WATER | `CHAIN_SLOW` | +2 Damage, +6% Damage, +4% Crit Chance |

### Axe

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Butcher's Mark | 3 | VOID -> FIRE -> FIRE | `EXECUTE` | +10% Damage, +8% Crit Damage |
| Warhowl | 4 | FIRE -> VOID -> LIGHTNING -> FIRE | `ARMOR_SHRED` | +8% Damage, +6% Attack Speed |
| Red Harvest | 5 | LIFE -> VOID -> FIRE -> LIFE -> LIGHTNING | `HEAL_SURGE` | +10% Damage, +5% Lifesteal |

### Mace

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Stormmaul | 3 | LIGHTNING -> LIGHTNING -> ICE | `THUNDER_STRIKE` | +3 Damage, +7% Damage, +5% Crit Chance |
| Tomb Bell | 4 | VOID -> ICE -> VOID -> LIFE | `EXECUTE` | +2 Damage, +8% Damage, +10% Crit Damage |
| Siege Psalm | 5 | FIRE -> LIFE -> LIGHTNING -> ICE -> VOID | `THUNDER_STRIKE` | +2 Damage, +9% Damage, +5% Attack Speed, +5% Crit Chance |

### Dagger

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Nightneedle | 3 | VOID -> LIGHTNING -> WATER | `EXECUTE` | +1 Damage, +5% Damage, +8% Attack Speed, +8% Crit Chance |
| Frostfang | 4 | ICE -> VOID -> LIGHTNING -> ICE | `CHAIN_SLOW` | +2 Damage, +6% Damage, +6% Crit Chance |
| Ghoststep | 5 | LIGHTNING -> WATER -> VOID -> LIFE -> ICE | `HEAL_SURGE` | +1 Damage, +6% Damage, +10% Attack Speed, +3% Lifesteal |
| Plundering Blade | 5 | VOID -> LIFE -> WATER -> LIGHTNING -> FIRE | `PLUNDERING_BLADE` | +2 Damage, +7% Damage, +8% Attack Speed, +6% Luck |

### Bow

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Gale String | 3 | LIGHTNING -> WATER -> LIGHTNING | `THUNDER_STRIKE` | +10% Attack Speed, +6% Crit Chance |
| Frostline | 4 | ICE -> WATER -> ICE -> LIGHTNING | `CHAIN_SLOW` | +2 Damage, +8% Damage, +4% Crit Chance |
| Sunshot | 5 | FIRE -> LIGHTNING -> FIRE -> LIFE -> VOID | `BURN_ON_CRIT` | +3 Damage, +12% Damage, +12% Crit Damage |
| Storm Quiver | 5 | LIGHTNING -> ICE -> WATER -> LIGHTNING -> LIFE | `MULTISHOT_BARRAGE` | +3 Damage, +9% Damage, +8% Attack Speed, +5% Crit Chance |
| Clockwork Loader | 5 | VOID -> LIGHTNING -> WATER -> LIFE -> VOID | `CROSSBOW_AUTO_RELOAD` | +2 Damage, +8% Damage, +6% Crit Chance, +6% Attack Speed |

### Staff

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Sagebind | 3 | LIFE -> ICE -> LIGHTNING | `HEAL_SURGE` | +1 Damage, +5% Damage, +6% Attack Speed, +4% Lifesteal |
| Riftbranch | 4 | VOID -> WATER -> LIGHTNING -> ICE | `THUNDER_STRIKE` | +2 Damage, +7% Damage, +6% Crit Chance |
| Star Conduit | 5 | LIFE -> FIRE -> ICE -> LIGHTNING -> VOID | `THUNDER_STRIKE` | +2 Damage, +10% Damage, +6% Crit Chance, +6% Attack Speed, +8% Crit Damage |

### Generic Weapons

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Merciless | 3 | FIRE -> VOID -> LIGHTNING | `EXECUTE` | +2 Damage, +10% Damage, +6% Crit Chance |
| Trinity Edge | 5 | FIRE -> ICE -> LIGHTNING -> LIFE -> VOID | `THUNDER_STRIKE` | +2 Damage, +8% Damage, +4% Attack Speed, +4% Crit Chance, +6% Crit Damage, +3% Lifesteal |

## Armor Resonances

| Resonance | Sockets | Order | Type | Stat Bonuses |
|---|---:|---|---|---|
| Tideguard | 3 | WATER -> LIFE -> WATER | `HEAL_SURGE` | +20 Health, +2 Regen |
| Cryobastion | 3 | ICE -> ICE -> LIFE | `FROST_NOVA_ON_HIT` | +8% Defense, +4% Fire Defense |
| Stormweave | 3 | LIGHTNING -> WATER -> ICE | `SHOCK_DODGE` | +10% Evasion, +4% Defense |
| Black Bulwark | 4 | VOID -> LIFE -> VOID -> WATER | `THORNS_SHOCK` | +10% Defense, +12 Health |
| Sunplate | 4 | FIRE -> LIFE -> WATER -> FIRE | `AURA_BURN` | +10% Fire Defense, +6% Defense |
| Grave Mantle | 4 | VOID -> ICE -> LIFE -> WATER | `FROST_NOVA_ON_HIT` | +8% Defense, +10 Health |
| Glacier Heart | 5 | ICE -> WATER -> ICE -> LIFE -> VOID | `FROST_NOVA_ON_HIT` | +10% Defense, +2 Regen |
| Tempest Shell | 5 | LIGHTNING -> LIGHTNING -> WATER -> ICE -> LIFE | `THORNS_SHOCK` | +12% Evasion, +6% Defense |
| Phoenix Aegis | 5 | FIRE -> LIFE -> FIRE -> WATER -> VOID | `CHEAT_DEATH` | +12% Defense, +12% Fire Defense, +20 Health |
| Worldskin | 5 | LIFE -> WATER -> LIFE -> ICE -> LIGHTNING | `HEAL_SURGE` | +30 Health, +3 Regen, +10% Defense |
