# SocketReforge Resonance System Documentation

## Overview

The Resonance System in SocketReforge implements a runeword-like mechanic where specific combinations of essences in sockets create powerful set effects. Resonance activates only when all sockets on an item are filled and match an exact essence pattern in the correct order.

## Core Concepts

### Essences and Sockets
- Items can have sockets that hold essences
- Essences come in different types (Fire, Ice, Lightning, Water, Life, Void)
- Essences can be regular or greater (concentrated) variants
- Socket data tracks which essence is in each socket

### Resonance Activation
- Requires all sockets to be filled with essences
- Essence types must match a predefined pattern exactly
- Order matters - the sequence must match precisely
- Only applies to weapons or armor (not both)

### Seeding System
- Resonance patterns can be seeded using a world seed
- This makes resonance combinations unique to each server
- When seeded, essence patterns are shuffled within weapon/armor groups
- Bonuses and resonance types remain associated with their original names

## Key Components

### ResonanceType Enum
Defines the special proc effects that can activate:
- `BURN_ON_CRIT` - Chance to deal bonus damage on crit
- `CHAIN_SLOW` - Chance to freeze target on hit
- `EXECUTE` - Bonus damage against low-HP targets
- `ARMOR_SHRED` - Chance to deal bonus damage on hit
- `THUNDER_STRIKE` - Chance to deal bonus damage on hit
- `MULTISHOT_BARRAGE` - Chance to fire extra arrows (bows)
- `CROSSBOW_AUTO_RELOAD` - Chance to refund ammo (crossbows)
- `PLUNDERING_BLADE` - Chance to steal from NPC drop tables
- `FROST_NOVA_ON_HIT` - Chance to freeze attacker when hit
- `THORNS_SHOCK` - Reflect damage as shock when hit
- `CHEAT_DEATH` - Survive lethal hits with 1 HP
- `HEAL_SURGE` - Heal on hit/dealt damage
- `SHOCK_DODGE` - Retaliate on dodge
- `AURA_BURN` - Burn attacker when hit
- `NONE` - No special effect

### Data Structures

#### ResonanceRecipe
- `name`: The resonance name (e.g., "Kingsbrand")
- `pattern`: Array of Essence.Type representing the required sequence
- `appliesTo`: String indicating what item types it applies to

#### ResonanceResult
- `name`: Localized resonance name
- `effect`: Description of the resonance effect
- `type`: The ResonanceType enum value
- `bonuses`: Map of stat bonuses (flat and percentage values)

#### Definition (Internal)
Internal representation containing:
- Name, effect, type
- Scope (WEAPON or ARMOR)
- Required weapon class (for weapons)
- Essence pattern array
- Stat bonuses map

## Resonance Definitions

The system contains predefined resonance patterns for different weapon types and armor:

### Weapon Resonances

#### Swords
- **Kingsbrand** (Fire + Lightning + Life): Thunder Strike - Damage/Crit enhanced; lightning strikes
- **Oathblade** (Fire + Fire + Void + Life): Burn on Crit - Burning crits with heavy damage scaling
- **Winter Duelist** (Ice + Lightning + Ice + Water): Chain Slow - Hits chain slow and punish chilled targets

#### Axes
- **Butcher's Mark** (Void + Fire + Fire): Execute - Execute bonus against low-health enemies
- **Warhowl** (Fire + Void + Lightning + Fire): Armor Shred - Armor-shredding impacts with aggressive tempo
- **Red Harvest** (Life + Void + Fire + Life + Lightning): Heal Surge - Sustain spikes during combat momentum

#### Maces
- **Stormmaul** (Lightning + Lightning + Ice): Thunder Strike - Crushing blows can trigger shock strikes
- **Tomb Bell** (Void + Ice + Void + Life): Execute - Execution pressure with dark impact
- **Siege Psalm** (Fire + Life + Lightning + Ice + Void): Thunder Strike - Balanced offense and thunder proc potential

#### Daggers
- **Nightneedle** (Void + Lightning + Water): Execute - Rapid crit pressure with execution finish
- **Frostfang** (Ice + Void + Lightning + Ice): Chain Slow - Crits apply chilling pressure
- **Ghoststep** (Lightning + Water + Void + Life + Ice): Heal Surge - High tempo with sustain surges
- **Plundering Blade** (Void + Life + Water + Lightning + Fire): Plundering Blade - Strikes can steal loot from enemy drop tables

#### Bows
- **Gale String** (Lightning + Water + Lightning): Thunder Strike - Fast volleys with shock strikes
- **Frostline** (Ice + Water + Ice + Lightning): Chain Slow - Arrows punish targets with chained slow
- **Sunshot** (Fire + Lightning + Fire + Life + Void): Burn on Crit - Explosive burn-style crit spikes
- **Storm Quiver** (Lightning + Ice + Water + Lightning + Life): Multishot Barrage - Charged bow shots can split into 3-arrow burst
- **Clockwork Loader** (Void + Lightning + Water + Life + Void): Crossbow Auto Reload - Crossbow bolts can be refunded directly back into quiver

#### Staves
- **Sagebind** (Life + Ice + Lightning): Heal Surge - Spellflow sustain and utility
- **Riftbranch** (Void + Water + Lightning + Ice): Thunder Strike - Void-channel lightning bursts
- **Star Conduit** (Life + Fire + Ice + Lightning + Void): Thunder Strike - All-round legendary spell weapon profile

#### Generic Weapons
- **Merciless** (Fire + Void + Lightning): Execute - Reliable execute-focused offense
- **Trinity Edge** (Fire + Ice + Lightning + Life + Void): Thunder Strike - Balanced universal weapon resonance

### Armor Resonances
- **Tideguard** (Water + Life + Water): Heal Surge - Max health and regeneration surge
- **Cryobastion** (Ice + Ice + Life): Frost Nova on Hit - Defensive shell with frost nova retaliation
- **Stormweave** (Lightning + Water + Ice): Shock Dodge - Evasion-biased anti-melee defense
- **Black Bulwark** (Void + Life + Void + Water): Thorns Shock - Thorns-style retaliation package
- **Sunplate** (Fire + Life + Water + Fire): Aura Burn - Burning aura defensive profile
- **Grave Mantle** (Void + Ice + Life + Water): Frost Nova on Hit - Cold retaliation with resilience
- **Glacier Heart** (Ice + Water + Ice + Life + Void): Frost Nova on Hit - Frost nova chance on taking hits
- **Tempest Shell** (Lightning + Lightning + Water + Ice + Life): Thorns Shock - Evasion package with shock retaliation
- **Phoenix Aegis** (Fire + Life + Fire + Water + Void): Cheat Death - Cheat-death shield with high mitigation
- **Worldskin** (Life + Water + Life + Ice + Lightning): Heal Surge - Tank package: health, regen, mitigation

## Mechanics

### Pattern Matching
1. Extract the sequence of essence types from filled sockets
2. Check if item is weapon or armor
3. Determine weapon class (for weapons)
4. Find matching Definition where:
   - Scope matches (weapon/armor)
   - Weapon class matches (if applicable)
   - Pattern length matches socket count
   - Essence types match in exact order

### Seeding Process
When a seed is configured:
1. Group definitions by pattern length (separately for weapons and armor)
2. Shuffle essence patterns within each group using the seed
3. Maintain association between names/effects/types and their new patterns
4. This creates server-unique resonance combinations while preserving balance

### Greater Essence Scaling
- Greater essences provide enhanced resonance effects
- Multiplier = 1.0 + (0.5 * ratio_of_greater_essences)
- Example: 2 greater essences out of 4 sockets = 1.0 + (0.5 * 0.5) = 1.25x bonus scaling
- Applies to all stat bonuses from the resonance

### Random Resonance Generation
The system can generate random resonances with these methods:
- `rollRandomResonanceRecipe(ItemStack item)` - Based on item type
- `rollRandomResonanceRecipe()` - Completely random
- `rollRandomResonanceRecipeBySockets(int socketCount)` - Specific socket count
- Uses weighted distribution: 3-socket (25x), 4-socket (10x), 5-socket (1x)

## API Methods

### Public Static Methods

#### Seed Management
- `setResonanceSeed(long seed)` - Configure the resonance seed (use world seed)
- `isResonanceSeedConfigured()` - Check if seed has been set

#### Pattern and Result Lookup
- `getPatternForRecipeName(String name)` - Get essence pattern for a resonance name
- `getResultForRecipeName(String name)` - Get full resonance result for a name

#### Localization
- `getLocalizedName(String name, Object player)` - Get localized resonance name
- `getLocalizedEffect(String name, String rawEffect, Object player)` - Get localized effect description
- `localizeAppliesTo(String raw, Object player)` - Localize applies-to text

#### Resonance Generation
- `buildRandomResonanceSocketData(ItemStack item)` - Create random resonance socket data
- `buildRandomResonanceSocketData(ItemStack item, double greaterEssenceChance)` - With greater essence chance
- `rollRandomResonanceRecipe(ItemStack item)` - Get random resonance recipe for item
- `rollRandomResonanceRecipe()` - Get completely random resonance recipe
- `rollRandomResonanceRecipeBySockets(int socketCount)` - Get random recipe with specific socket count

#### Resonance Evaluation
- `evaluate(ItemStack item, SocketData socketData)` - Check if socket data creates resonance
- `applyGreaterEssenceScaling(ResonanceResult resonance, SocketData socketData)` - Apply greater essence scaling
- `buildDetailedEffect(ResonanceResult resonance, boolean isWeapon)` - Build tooltip effect string

#### Utility Methods
- `getSeededRecipeDisplays()` - Get list of all resonances for display
- `classifyWeapon(String itemId)` - Determine weapon class from item ID

## Implementation Details

### Bonus Format
Bonuses are stored as `Map<EssenceEffect.StatType, double[]>` where:
- `values[0]` = flat bonus amount
- `values[1]` = percentage bonus amount

### Stat Types
- DAMAGE, DEFENSE, FIRE_DEFENSE, HEALTH, MOVEMENT_SPEED, REGENERATION
- CRIT_CHANCE, CRIT_DAMAGE, ATTACK_SPEED, LIFE_STEAL, EVASION, LUCK

### Localization Keys
- Resonance names: `resonance.{key}.name`
- Resonance effects: `resonance.{key}.effect`
- Applies-to: `resonance.applies.{key}`
- Stat labels: `resonance.stat.{stat_type}`
- Proc descriptions: `resonance.proc.{proc_type}`
- Detail formatting: `resonance.detail.{stats_and_proc|stats_only|proc_only}`

### Pattern Format
Patterns are displayed as `[Essence][Essence]...` where each essence is capitalized (e.g., `[Fire][Lightning][Life]`)

## Usage Examples

### Checking for Resonance
```java
SocketData socketData = ...; // From item
ItemStack item = ...; // The item being checked
ResonanceResult result = ResonanceSystem.evaluate(item, socketData);
if (result.active()) {
    String name = result.name();
    String effect = result.effect();
    ResonanceType type = result.type();
    Map<EssenceEffect.StatType, double[]> bonuses = result.bonuses();
    // Apply resonance effects
}
```

### Generating Random Resonance Gear
```java
ItemStack weapon = ...; // A weapon item
SocketData resonanceSocketData = ResonanceSystem.buildRandomResonanceSocketData(weapon, 0.3);
// 30% chance for each socket to be greater essence
// Apply socketData to weapon
```

### Getting Resonance Information
```java
String resonanceName = "Kingsbrand";
Essence.Type[] pattern = ResonanceSystem.getPatternForRecipeName(resonanceName);
// Returns [Fire, Lightning, Life]
ResonanceResult result = ResonanceSystem.getResultForRecipeName(resonanceName);
// Returns full ResonanceResult for Kingsbrand
```

## Balance Notes

### Recipe Weights
Different socket counts have different weights for random generation:
- 3-socket resonances: 25x weight (most common)
- 4-socket resonances: 10x weight
- 5-socket resonances: 1x weight (rarest)

### Greater Essence Scaling
The scaling formula ensures that greater essences provide meaningful but not overpowering bonuses:
- 0 greater essences: 1.0x multiplier
- 1 greater essence out of 3: ~1.17x multiplier
- 2 greater essences out of 3: ~1.33x multiplier
- 3 greater essences out of 3: 1.5x multiplier

### Seeding Implications
When seeding is used:
- Resonance names and effects stay the same
- Essence patterns are shuffled within categories
- This prevents players from memorizing exact patterns across servers
- Balance is maintained as the same types of resonances exist, just with different patterns

## Related Systems

### Essence System
- Essences are defined in `EssenceRegistry`
- Essence types: Fire, Ice, Lightning, Water, Life, Void
- Greater essences have enhanced effects when used in resonances

### Socket System
- Sockets are managed by `SocketManager`
- Socket data tracks essence IDs in each socket
- Socket breaking, locking, and other mechanics apply

### Localization System
- Uses `LangLoader` for translations
- Supports multiple languages through language files
- Fallback to English if translations missing

## Configuration

The resonance system is primarily configured through:
1. The `DEFINITIONS` list in `ResonanceSystem.java` - Contains all resonance patterns
2. World seed - Set via `setResonanceSeed(long seed)` for server uniqueness
3. Language files - For localization of names and effects

No external configuration files are required for basic operation.