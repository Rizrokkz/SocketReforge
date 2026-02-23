# UI Fix Plan for SocketReforge

## Problem
The SocketPunchUI.ui and EssenceSocketUI.ui files fail to load with error:
```
Crash - Failed to load CustomUI documents
```

## Analysis

### Working File (WeaponStatHUD.ui)
- Uses `$Common = "Common.ui"` import but doesn't reference any styles from it
- Background syntax: `Background: @MyTex;`
- Style syntax: `Style: (HorizontalAlignment: Center, VerticalAlignment: Center, FontSize: 32, ...);`
- Only uses Label and Group elements
- No Button elements

### Potential Issues in SocketPunchUI.ui and EssenceSocketUI.ui
1. **Button element syntax** - May be incorrect or unsupported
2. **Background syntax** - Was using `Background: (Color: @PanelBg)` instead of `Background: @PanelBg`
3. **Style references** - Were using `$Common.@StyleName` which may not exist

## Solution Options

### Option 1: Simplify UI Files
Remove Button elements and use only Label/Group elements like the working file.
- Replace Button with clickable Group elements
- Use inline styles only

### Option 2: Fix Button Syntax
If Button is supported, ensure correct syntax:
```
Button #ButtonName {
  Anchor: (Width: 200, Height: 40);
  Text: "Button Text";
  Style: (HorizontalAlignment: Center, VerticalAlignment: Center, FontSize: 14, Alignment: Center);
}
```

### Option 3: Use HyUI Mod
Install and use the HyUI mod from CurseForge which provides additional UI components.

## Recommended Approach
1. First try fixing the Button syntax to match Hytale's expected format
2. If that fails, replace Button with Group elements that can handle click events
3. Ensure all Background and Style syntax matches the working WeaponStatHUD.ui file

## Files to Modify
- `src/main/resources/Common/UI/Custom/SocketPunchUI.ui`
- `src/main/resources/Common/UI/Custom/EssenceSocketUI.ui`

## Next Steps
1. Test if the current syntax works after fixes
2. If still failing, check server logs for specific parsing errors
3. Consider using the Hytale UI Builder tool to regenerate the UI files
