# SocketPunchUI Redesign Plan

## Overview

This document outlines a comprehensive redesign of the SocketPunchUI to match the structure and styling patterns used in WeaponStatHUD.ui while fulfilling the requirements specified in the socket_punching_architecture.md plan.

## Current State Analysis

### Current SocketPunchUI.ui Issues

1. **Basic Styling**: Uses default `$Common.@TextButton` without custom styling
2. **Flat Layout**: All elements positioned absolutely without logical grouping
3. **No Visual Hierarchy**: Socket indicators are simple colored boxes
4. **No Custom Textures**: Missing the visual polish from custom textures
5. **Inconsistent Element IDs**: Some selectors don't match Java code expectations

### WeaponStatHUD.ui Strengths to Adopt

1. **Custom Styles**: Uses `@CustomLabelStyle` and `@CustomStyle` with proper font settings
2. **PatchStyle Textures**: Uses `@MyTex = PatchStyle(TexturePath: "output_bg.png")` for visual polish
3. **Logical Grouping**: Uses separate `Group` elements for different states
4. **LayoutMode**: Uses `LayoutMode: Left` and `LayoutMode: Top` for better organization
5. **Visibility Control**: Uses `Visible: false` for conditional display

## Proposed Design

### UI Layout Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│  @MyTex Background                                                  │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                    SOCKET PUNCH BENCH                          │  │
│  │                    Subtitle text here                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐      │
│  │  EQUIPMENT      │  │   MATERIAL      │  │   SUPPORT       │      │
│  │  [Slot Button]  │  │  [Slot Button]  │  │  [Slot Button]  │      │
│  │                 │  │                 │  │    Optional     │      │
│  │  Item Name      │  │  Material Name  │  │  Support Name   │      │
│  │  Socket Info    │  │  Status         │  │  Status         │      │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘      │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  OUTPUT PANEL                                                  │  │
│  │  ┌────┐ ┌────┐ ┌────┐ ┌────┐                                 │  │
│  │  │ S1 │ │ S2 │ │ S3 │ │ S4 │   Socket Preview               │  │
│  │  └────┘ └────┘ └────┘ └────┘                                 │  │
│  │                                                                │  │
│  │  Sockets: 2/4    Success: 75%    Break: 5%                   │  │
│  │  ─────────────────────────────────────────────               │  │
│  │  Warning/Status Message                                       │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│              ┌─────────────────────────┐                             │
│              │     PUNCH SOCKET        │                             │
│              └─────────────────────────┘                             │
└─────────────────────────────────────────────────────────────────────┘
```

### Component Hierarchy

```mermaid
graph TD
    A[Group - Root Center] --> B[Group #SocketPunchRoot]
    B --> C[Group #HeaderSection]
    B --> D[Group #InputSection]
    B --> E[Group #OutputSection]
    B --> F[Group #ActionSection]
    
    C --> C1[Label #TitleText]
    C --> C2[Label #SubtitleText]
    
    D --> D1[Group #EquipmentSlotGroup]
    D --> D2[Group #MaterialSlotGroup]
    D --> D3[Group #SupportSlotGroup]
    
    D1 --> D1a[$Common.@TextButton #EquipmentSlot]
    D1 --> D1b[Label #EquipmentItemName]
    D1 --> D1c[Label #EquipmentItemSockets]
    
    D2 --> D2a[$Common.@TextButton #PuncherSlot]
    D2 --> D2b[Label #PuncherItemName]
    D2 --> D2c[Label #PuncherStatus]
    
    D3 --> D3a[$Common.@TextButton #SupportSlot]
    D3 --> D3b[Label #SupportItemName]
    D3 --> D3c[Label #SupportStatus]
    
    E --> E1[Group #SocketPreviewRow]
    E --> E2[Group #StatsRow]
    E --> E3[Label #WarningText]
    
    E1 --> E1a[Group #Socket1]
    E1 --> E1b[Group #Socket2]
    E1 --> E1c[Group #Socket3]
    E1 --> E1d[Group #Socket4]
    
    E2 --> E2a[Label #SocketCountLabel]
    E2 --> E2b[Label #SuccessLabel]
    E2 --> E2c[Label #BreakLabel]
    
    F --> F1[$Common.@TextButton #PunchSocketButton]
```

### Style Definitions

```hyui
// Custom texture for background
@MyTex = PatchStyle(TexturePath: "output_bg.png");

// Custom label style for headers
@HeaderLabelStyle = LabelStyle(
  FontSize: 22,
  TextColor: #FFFFFF,
  RenderBold: true,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

// Custom label style for values
@ValueLabelStyle = LabelStyle(
  FontSize: 15,
  TextColor: #E8E8EF,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

// Custom label style for stats
@StatLabelStyle = LabelStyle(
  FontSize: 12,
  TextColor: #A9A9B5,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

// Custom button style for slots
@SlotButtonStyle = TextButtonStyle(
  Default: (Background: @MyTex, LabelStyle: @ValueLabelStyle),
  Hovered: (Background: $Common.@DefaultSquareButtonHoveredBackground, LabelStyle: @ValueLabelStyle),
  Pressed: (Background: $Common.@DefaultSquareButtonPressedBackground, LabelStyle: @ValueLabelStyle),
  Disabled: (Background: $Common.@DefaultSquareButtonDisabledBackground, LabelStyle: @ValueLabelStyle),
  Sounds: $Common.@ButtonSounds
);

// Custom button style for main action
@ActionButtonStyle = TextButtonStyle(
  Default: (Background: @MyTex, LabelStyle: @HeaderLabelStyle),
  Hovered: (Background: $Common.@DefaultSquareButtonHoveredBackground, LabelStyle: @HeaderLabelStyle),
  Pressed: (Background: $Common.@DefaultSquareButtonPressedBackground, LabelStyle: @HeaderLabelStyle),
  Sounds: $Common.@ButtonSounds
);
```

### Color Scheme

| Element | Color Code | Usage |
|---------|------------|-------|
| Background | `#17171A` | Main panel background |
| Panel Background | `#222222` | Section backgrounds |
| Primary Text | `#FFFFFF` | Titles and important values |
| Secondary Text | `#A0A0A8` | Subtitles and descriptions |
| Stat Labels | `#A9A9B5` | Label text |
| Success | `#62D66A` | Success chance, positive indicators |
| Danger | `#FF6666` | Break chance, warnings |
| Warning | `#FFB347` | Warning messages |
| Socket Empty | `#333333` | Empty socket slots |
| Socket Filled | `#00E5C8` | Filled socket slots |
| Socket Available | `#444444` | Available but empty slots |

### Element ID Mapping

The following table maps UI element IDs to their Java code usage:

| UI Element ID | Java Usage | Description |
|---------------|------------|-------------|
| `#EquipmentSlot` | Event binding | Button to select held equipment |
| `#EquipmentItemName` | `cmd.set("#EquipmentItemName.Text", ...)` | Display equipment name |
| `#EquipmentItemSockets` | `cmd.set("#EquipmentItemSockets.Text", ...)` | Display socket count |
| `#PuncherSlot` | Event binding | Button to check material |
| `#PuncherItemName` | `cmd.set("#PuncherItemName.Text", ...)` | Display puncher status |
| `#SupportSlot` | Event binding | Button to toggle support |
| `#SupportItemName` | `cmd.set("#SupportItemName.Text", ...)` | Display support status |
| `#Socket1` - `#Socket4` | `cmd.set("#SocketN.Background", ...)` | Socket indicators |
| `#SocketCountValue` | `cmd.set("#SocketCountValue.Text", ...)` | Socket count display |
| `#SuccessValue` | `cmd.set("#SuccessValue.Text", ...)` | Success percentage |
| `#BreakValue` | `cmd.set("#BreakValue.Text", ...)` | Break percentage |
| `#WarningText` | `cmd.set("#WarningText.Text", ...)` | Warning/status messages |
| `#PunchSocketButton` | Event binding | Main action button |

### Proposed .ui File Structure

```hyui
// Socket Punching UI - Redesigned
$Common = "Common.ui";
@MyTex = PatchStyle(TexturePath: "output_bg.png");

// Style definitions
@HeaderLabelStyle = LabelStyle(
  FontSize: 22,
  TextColor: #FFFFFF,
  RenderBold: true,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@SubHeaderLabelStyle = LabelStyle(
  FontSize: 12,
  TextColor: #A0A0A8,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@ValueLabelStyle = LabelStyle(
  FontSize: 15,
  TextColor: #E8E8EF,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@StatLabelStyle = LabelStyle(
  FontSize: 12,
  TextColor: #A9A9B5,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@SlotLabelStyle = LabelStyle(
  FontSize: 11,
  TextColor: #AAAAAA,
  HorizontalAlignment: Center,
  VerticalAlignment: Center
);

@SocketValueStyle = LabelStyle(
  FontSize: 15,
  TextColor: #FFFFFF,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@SuccessValueStyle = LabelStyle(
  FontSize: 15,
  TextColor: #62D66A,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@DangerValueStyle = LabelStyle(
  FontSize: 15,
  TextColor: #FF6666,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@WarningStyle = LabelStyle(
  FontSize: 12,
  TextColor: #FFB347,
  HorizontalAlignment: Left,
  VerticalAlignment: Center
);

@SlotButtonStyle = TextButtonStyle(
  Default: (Background: @MyTex, LabelStyle: @ValueLabelStyle),
  Hovered: (Background: $Common.@DefaultSquareButtonHoveredBackground, LabelStyle: @ValueLabelStyle),
  Pressed: (Background: $Common.@DefaultSquareButtonPressedBackground, LabelStyle: @ValueLabelStyle),
  Disabled: (Background: $Common.@DefaultSquareButtonDisabledBackground, LabelStyle: @ValueLabelStyle),
  Sounds: $Common.@ButtonSounds
);

@ActionButtonStyle = TextButtonStyle(
  Default: (Background: @MyTex, LabelStyle: @HeaderLabelStyle),
  Hovered: (Background: $Common.@DefaultSquareButtonHoveredBackground, LabelStyle: @HeaderLabelStyle),
  Pressed: (Background: $Common.@DefaultSquareButtonPressedBackground, LabelStyle: @HeaderLabelStyle),
  Sounds: $Common.@ButtonSounds
);

Group {
  LayoutMode: Center;
  
  Group #SocketPunchRoot {
    LayoutMode: Top;
    Anchor: (Width: 560, Height: 480);
    Background: @MyTex;
    
    // Header Section
    Group #HeaderSection {
      LayoutMode: Top;
      Anchor: (Width: 560, Height: 60);
      
      Label #TitleText {
        Anchor: (Top: 14, Left: 20);
        Text: "Socket Punch Bench";
        Style: @HeaderLabelStyle;
      }
      
      Label #SubtitleText {
        Anchor: (Top: 44, Left: 20);
        Text: "Use your held item and materials to add sockets";
        Style: @SubHeaderLabelStyle;
      }
    }
    
    // Input Section - Three Slot Columns
    Group #InputSection {
      LayoutMode: Left;
      Anchor: (Width: 560, Height: 160);
      
      // Equipment Slot Column
      Group #EquipmentSlotGroup {
        LayoutMode: Top;
        Anchor: (Width: 180, Height: 160);
        
        $Common.@TextButton #EquipmentSlot {
          Anchor: (Width: 170, Height: 52, Top: 10, Left: 5);
          Text: "Equipment";
          Style: @SlotButtonStyle;
          Padding: (Full: 4);
        }
        
        Label #EquipmentPlaceholder {
          Anchor: (Top: 70, Left: 10);
          Text: "Hold weapon/armor";
          Style: @SlotLabelStyle;
        }
        
        Label #EquipmentItemName {
          Anchor: (Top: 88, Left: 10);
          Text: "";
          Style: @ValueLabelStyle;
        }
        
        Label #EquipmentItemSockets {
          Anchor: (Top: 108, Left: 10);
          Text: "";
          Style: @SuccessValueStyle;
        }
      }
      
      // Material Slot Column
      Group #MaterialSlotGroup {
        LayoutMode: Top;
        Anchor: (Width: 180, Height: 160);
        
        $Common.@TextButton #PuncherSlot {
          Anchor: (Width: 170, Height: 52, Top: 10, Left: 5);
          Text: "Material";
          Style: @SlotButtonStyle;
          Padding: (Full: 4);
        }
        
        Label #PuncherPlaceholder {
          Anchor: (Top: 70, Left: 10);
          Text: "Socket Puncher";
          Style: @SlotLabelStyle;
        }
        
        Label #PuncherItemName {
          Anchor: (Top: 88, Left: 10);
          Text: "";
          Style: @ValueLabelStyle;
        }
        
        Label #PuncherStatus {
          Anchor: (Top: 108, Left: 10);
          Text: "";
          Style: @StatLabelStyle;
        }
      }
      
      // Support Slot Column
      Group #SupportSlotGroup {
        LayoutMode: Top;
        Anchor: (Width: 180, Height: 160);
        
        $Common.@TextButton #SupportSlot {
          Anchor: (Width: 170, Height: 52, Top: 10, Left: 5);
          Text: "Support";
          Style: @SlotButtonStyle;
          Padding: (Full: 4);
        }
        
        Label #SupportPlaceholder {
          Anchor: (Top: 70, Left: 10);
          Text: "Optional Booster";
          Style: @SlotLabelStyle;
        }
        
        Label #SupportItemName {
          Anchor: (Top: 88, Left: 10);
          Text: "";
          Style: @ValueLabelStyle;
        }
        
        Label #SupportStatus {
          Anchor: (Top: 108, Left: 10);
          Text: "";
          Style: @StatLabelStyle;
        }
      }
    }
    
    // Output Section - Socket Preview and Stats
    Group #OutputSection {
      LayoutMode: Top;
      Anchor: (Width: 560, Height: 180);
      Background: (Color: #1A1A1E);
      
      // Socket Preview Row
      Group #SocketPreviewRow {
        LayoutMode: Left;
        Anchor: (Width: 560, Height: 60);
        
        Label #SocketPreviewLabel {
          Anchor: (Top: 10, Left: 20);
          Text: "Socket Preview";
          Style: @StatLabelStyle;
        }
        
        Group #Socket1 {
          Anchor: (Width: 42, Height: 42, Top: 10, Left: 140);
          Background: (Color: #333333);
        }
        
        Group #Socket2 {
          Anchor: (Width: 42, Height: 42, Top: 10, Left: 188);
          Background: (Color: #333333);
        }
        
        Group #Socket3 {
          Anchor: (Width: 42, Height: 42, Top: 10, Left: 236);
          Background: (Color: #333333);
        }
        
        Group #Socket4 {
          Anchor: (Width: 42, Height: 42, Top: 10, Left: 284);
          Background: (Color: #333333);
        }
      }
      
      // Stats Row
      Group #StatsRow {
        LayoutMode: Left;
        Anchor: (Width: 560, Height: 50);
        
        Group #SocketCountGroup {
          LayoutMode: Top;
          Anchor: (Width: 150, Height: 50);
          
          Label #SocketCountLabel {
            Anchor: (Top: 5, Left: 20);
            Text: "Sockets";
            Style: @StatLabelStyle;
          }
          
          Label #SocketCountValue {
            Anchor: (Top: 22, Left: 20);
            Text: "0 / 4";
            Style: @SocketValueStyle;
          }
        }
        
        Group #SuccessGroup {
          LayoutMode: Top;
          Anchor: (Width: 150, Height: 50);
          
          Label #SuccessLabel {
            Anchor: (Top: 5, Left: 10);
            Text: "Success";
            Style: @StatLabelStyle;
          }
          
          Label #SuccessValue {
            Anchor: (Top: 22, Left: 10);
            Text: "0%";
            Style: @SuccessValueStyle;
          }
        }
        
        Group #BreakGroup {
          LayoutMode: Top;
          Anchor: (Width: 150, Height: 50);
          
          Label #BreakLabel {
            Anchor: (Top: 5, Left: 10);
            Text: "Break Risk";
            Style: @StatLabelStyle;
          }
          
          Label #BreakValue {
            Anchor: (Top: 22, Left: 10);
            Text: "0%";
            Style: @DangerValueStyle;
          }
        }
      }
      
      // Warning Text
      Label #WarningText {
        Anchor: (Top: 120, Left: 20);
        Text: "";
        Style: @WarningStyle;
      }
    }
    
    // Action Section
    Group #ActionSection {
      LayoutMode: Center;
      Anchor: (Width: 560, Height: 60);
      
      $Common.@TextButton #PunchSocketButton {
        Anchor: (Width: 200, Height: 48);
        Text: "Punch Socket";
        Style: @ActionButtonStyle;
        Padding: (Full: 8);
      }
    }
  }
}
```

## Java Code Updates Required

The Java code in [`SocketPunchUI.java`](src/main/java/irai/mod/reforge/UI/SocketPunchUI.java) needs to be updated to match the new element IDs:

### Changes to refreshView method:

1. Update element ID references:
   - `#EquipmentName` → `#EquipmentItemName`
   - `#EquipmentSockets` → `#EquipmentItemSockets`
   - `#PuncherName` → `#PuncherItemName`
   - `#SupportName` → `#SupportItemName`
   - `#SocketCount` → `#SocketCountValue`

2. Add new status labels:
   - `#PuncherStatus` for material availability
   - `#SupportStatus` for support material status

### Changes to handleSlotAction:

1. Add material slot handling for inventory checks
2. Improve support material toggle feedback

## Implementation Steps

1. **Phase 1: Create New .ui File**
   - Create the new [`SocketPunchUI.ui`](src/main/resources/Common/UI/Custom/SocketPunchUI.ui) with the proposed structure
   - Include all style definitions
   - Test UI loading

2. **Phase 2: Update Java Code**
   - Update element ID references in [`SocketPunchUI.java`](src/main/java/irai/mod/reforge/UI/SocketPunchUI.java)
   - Add new status display logic
   - Test event bindings

3. **Phase 3: Visual Polish**
   - Fine-tune colors and spacing
   - Add hover effects
   - Test with different socket states

4. **Phase 4: Integration Testing**
   - Test full socket punching workflow
   - Verify all UI updates correctly
   - Test edge cases

## Comparison: Before vs After

| Aspect | Before | After |
|--------|--------|-------|
| Layout | Single column, absolute positioning | Three-column input, organized sections |
| Styling | Default button styles | Custom styles with textures |
| Socket Display | Simple colored boxes | Organized preview row with labels |
| Stats Display | Inline labels | Grouped stat sections |
| Visual Hierarchy | Flat | Clear sections with backgrounds |
| Texture Usage | None | Uses `output_bg.png` for polish |
| Element Organization | Scattered | Logical grouping with `Group` elements |

## Questions for User

1. Should the socket preview show filled sockets with essence type colors?
2. Do you want animation effects when socketing succeeds/fails?
3. Should there be a confirmation dialog before punching?
4. Do you want to add a help/info button with instructions?

---

## Next Steps

After approval:
1. Switch to Code mode to implement the new UI
2. Create the updated `.ui` file
3. Update the Java controller code
4. Test the complete workflow
