# Socket Punching UI Implementation Plan

Based on Hytale GUI Documentation (https://hytale-docs.pages.dev/gui/)

## Overview

The socket punching system requires a 4-slot inventory-based UI:
- **Slot 1**: Equipment (weapon/armor) input
- **Slot 2**: Main material (Socket Puncher) input
- **Slot 3**: Supporting material (Socket Stabilizer) input - optional
- **Slot 4**: Output slot for result

## UI Architecture Options

### Option 1: Processing Bench System (Recommended)

The processing bench system provides built-in inventory management and is already implemented:

**Advantages:**
- Built-in inventory slots (3 input + 1 output)
- Automatic item container management
- Standard Hytale bench interaction pattern
- Players are familiar with bench UIs

**Implementation:**
- Use `Bench_Socket_Punch.json` with processing bench type
- Custom interaction `SocketPunchBench` handles the logic
- UI is automatically provided by the bench system

**Files:**
- `src/main/resources/Server/Item/Items/Bench/Bench_Socket_Punch.json`
- `src/main/java/irai/mod/reforge/Interactions/SocketPunchBench.java`

### Option 2: Custom UI Window with Container

Create a custom window with inventory slots using Hytale's window system:

**Components:**
1. **Window Definition** - `.window` file defining the layout
2. **Window Handler** - Java class extending `CustomWindow` or similar
3. **Container Provider** - Provides the item container for slots

**Structure:**
```
src/main/resources/Common/UI/Windows/
└── SocketPunchWindow.window  # Window layout definition

src/main/java/irai/mod/reforge/UI/
└── SocketPunchWindow.java    # Window handler class
```

### Option 3: InteractiveCustomUIPage with Slot Widgets

Use `InteractiveCustomUIPage` with custom slot widgets:

**Advantages:**
- More control over UI layout
- Can add custom graphics and styling

**Disadvantages:**
- More complex implementation
- Need to handle item synchronization manually

## Recommended Implementation: Processing Bench

The processing bench approach is recommended because:

1. **Already Implemented**: The `Bench_Socket_Punch.json` and `SocketPunchBench.java` are created
2. **Standard Pattern**: Follows Hytale's established bench interaction pattern
3. **Built-in Inventory**: Automatic slot management and item container handling
4. **User Familiar**: Players understand how benches work

### Current Implementation Status

**Completed:**
- [x] `Bench_Socket_Punch.json` - Bench block definition
- [x] `SocketPunchBench.java` - Custom interaction handler
- [x] `SocketPunchBench.json` - Interaction definition
- [x] `Socket_Puncher.json` - Main material item
- [x] `Socket_Stabilizer.json` - Support material item
- [x] Language entries for all items

**The bench provides:**
- 3 input slots (equipment, main material, support material)
- 1 output slot
- Automatic UI rendering
- Item container management

### How Players Use It

1. **Place the bench** in the world
2. **Right-click** to open the bench UI
3. **Place items** in the slots:
   - Slot 1: Equipment to punch
   - Slot 2: Socket Puncher (required)
   - Slot 3: Socket Stabilizer (optional)
4. **Wait for processing** or click to confirm
5. **Collect result** from output slot

## Alternative: Command-based UI

If a command-based UI is preferred (`/socketpunch`), we need to create a custom window:

### Window Definition Structure

```hytale
// SocketPunchWindow.window
$Common = "Common.ui";

Window {
    Title: "Socket Punching";
    Size: (Width: 400, Height: 300);
    
    Container {
        Slots: 4;
        InputSlots: 3;
        OutputSlots: 1;
    }
    
    // UI elements for displaying stats
    Label #Title {
        Text: "SOCKET PUNCHING";
        Style: (FontSize: 24, TextColor: #FFFF55);
    }
    
    // Slot references
    Slot #EquipmentSlot { Index: 0; }
    Slot #MainMaterialSlot { Index: 1; }
    Slot #SupportMaterialSlot { Index: 2; }
    Slot #OutputSlot { Index: 3; }
    
    // Stats display
    Label #SuccessChance { ... }
    Label #BreakChance { ... }
    
    // Action button
    Button #PunchButton {
        Text: "PUNCH SOCKET";
        Event: "punch";
    }
}
```

### Window Handler Class

```java
public class SocketPunchWindow extends CustomWindow {
    // Handle slot changes
    // Handle button clicks
    // Process socket punch logic
}
```

## Next Steps

1. **Test the Processing Bench** implementation first
2. If bench works correctly, the socket system is complete
3. If command-based UI is still needed, implement custom window

## UI Files Reference

### Current UI Files

| File | Purpose | Status |
|------|---------|--------|
| `SocketPunchUI.ui` | Custom page UI definition | Created but not used with bench |
| `SocketPunchUI.java` | Custom page handler | Created but not used with bench |
| `Bench_Socket_Punch.json` | Bench block with processing UI | **Primary UI** |
| `SocketPunchBench.java` | Bench interaction handler | **Primary logic** |

### Recommended Action

**Test the processing bench implementation first** - it already provides the 4-slot inventory UI that was requested. The bench system handles:
- Slot management
- Item validation
- UI rendering
- Player interaction

If additional customization is needed, we can extend the bench UI or create a custom window.
