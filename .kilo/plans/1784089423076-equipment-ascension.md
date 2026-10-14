# Equipment Ascension System Plan

## Overview
Add an ascension system that allows maxed-out refined equipment to ascend to tiers T1-T3 via interaction with `Furniture_Temple_Light_Statue`. Ascension resets refinement to 0 but uses the previous refinement bonus as the new base for future refinement.

## Scope
- Make `Furniture_Temple_Light_Statue` interactable
- Add ascension logic for maxed refinement equipment (T1-T3)
- Configurable material costs per tier via config
- Simple chat-based confirmation UI
- Ascension tier displayed as tooltip line

## Implementation Steps

### 1. Add Ascension Metadata Key
**File:** `src/main/java/irai/mod/reforge/states/MetadataKeys.java`

Add new metadata key:
```java
public static final String ASCENSION_TIER = "SocketReforge.Ascension.Tier";
```

### 2. Create AscensionConfig
**File:** `src/main/java/irai/irai/mod/reforge/Config/AscensionConfig.java`

Config class with:
- `enabled`: boolean (default true)
- `maxTier`: int (default 3)
- `tierCosts`: Map<Integer, String> mapping tier -> item ID required (e.g., `{1: "Resonant_Glob", 2: "Resonant_Essence_Greater", 3: "Prismatic_Core"}`)
- `tierStatMultiplier`: double (1.0 = base multiplier applied per tier)

Codec registration following existing config patterns.

### 3. Create StatueInteraction Class
**File:** `src/main/java/irai/mod/reforge/Interactions/StatueInteraction.java`

```java
public class StatueInteraction extends SimpleInteraction {
    public static final BuilderCodec<StatueInteraction> CODEC = ...;
    
    @Override
    protected void tick0(boolean firstRun, float time, InteractionType type, 
                         InteractionContext context, CooldownHandler cooldownHandler) {
        // 1. Get player from context
        // 2. Get held item
        // 3. Check if item is weapon or armor
        // 4. Check if refinement is maxed
        // 5. Check current ascension tier (read metadata)
        // 6. Check if player has required material for next tier
        // 7. Send confirmation message with requirements
        // 8. On confirm: consume material, reset refinement, increment tier
    }
}
```

### 4. Register Interaction
**File:** `src/main/java/irai/mod/reforge/ReforgePlugin.java`

Add to `setup()`:
```java
this.getCodecRegistry(Interaction.CODEC).register("AscensionStatue", StatueInteraction.class, StatueInteraction.CODEC);
```

### 5. Create Interaction JSON Files
**File:** `src/main/resources/Common/Interactions/AscensionStatue.json`
```json
{ "Type": "AscensionStatue" }
```

**File:** `src/main/resources/Common/RootInteractions/AscensionStatue.json`
```json
{ "Interactions": ["AscensionStatue"], "RequireNewClick": true }
```

**File:** `src/main/resources/Common/Item/Blocks/Furniture_Temple_Light_Statue.json`
Add interaction block to existing furniture item JSON.

### 6. Add Ascension Logic to ReforgeEquip
**File:** `src/main/java/irai/mod/reforge/Interactions/ReforgeEquip.java`

Add methods:
- `getItemAscensionTier(ItemStack item)`: read `ASCENSION_TIER` metadata (default 0)
- `setItemAscensionTier(ItemStack item, int tier)`: write tier to metadata
- `resetRefinementWithAscensionBonus(ItemStack item)`: 
  - Calculate current refinement bonus (stat multiplier from max level)
  - Reset refinement level to 0
  - Store base multiplier bonus for future refinement calculations
- `canAscend(ItemStack item)`: check if item is max refinement and tier < max

### 7. Update Refinement Multiplier Calculation
**File:** `src/main/java/irai/mod/reforge/Config/RefinementConfig.java`

Modify multiplier calculation to include ascension tier base bonus:
```java
double getMultiplierForLevel(int level, int ascensionTier) {
    double base = getMultiplierForLevel(level);
    double ascensionBonus = 1.0 + (ascensionTier * tierStatMultiplier);
    return base * ascensionBonus;
}
```

### 8. Add Tooltip Line for Ascension Tier
**File:** `src/main/java/irai/mod/reforge/Util/DynamicTooltipUtils.java` or similar

Add tooltip line showing ascension tier when > 0:
```
Ascension: T1 / T2 / T3
```

### 9. Add RuntimeConfig Integration
**File:** `src/main/java/irai/mod/reforge/UI/RuntimeConfigUI.java`

Add category for editing ascension config:
- Enable/disable toggle
- Max tier setting
- Per Tier naming
- Tier cost item IDs

## Validation Plan

1. **Unit Test**: Create max refinement weapon, verify it can ascend
2. **Material Check**: Verify material is consumed on ascension
3. **Refinement Reset**: Verify refinement resets to 0 but stats are preserved via base bonus
4. **Tier Limit**: Verify T3 is max and cannot go higher
5. **Non-Max Items**: Verify non-max refinement items cannot ascend
6. **Tooltip**: Verify ascension tier shows in tooltip
7. **Config**: Verify tier costs can be changed via RuntimeConfig

## Open Questions

1. Should ascension be reversible (degrade tier)? No.
2. What happens if player interacts without required material? (Show requirements only, or error message) Should also check if the equipment is at max refinement before proceeding to check if player has materials and only show as message
3. Should there be a success/failure chance for ascension? Always success once the requirements are met.
