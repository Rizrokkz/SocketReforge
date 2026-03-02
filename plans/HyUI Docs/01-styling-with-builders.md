# Tutorial: Styling with Builders

Welcome to the world of builder-based styling. This is where you graduate from stringing together `.withStyle(new HyUIStyle().setTextColor("#ff0000"))` and enter a realm where buttons have opinions about their hover states and checkboxes know when they are checked.

This tutorial covers:

- The `.withStyle(BsonSerializable)` method and why it exists
- Style classes in the `au.ellie.hyui.types` package
- Using `DefaultStyles` for consistent Hytale-native appearance
- Building your own style classes
- When to use style classes vs. `HyUIStyle`

---

## 1. What is `.withStyle(BsonSerializable)`?

Every builder that supports styling has a `.withStyle()` method. It accepts any class that implements `BsonSerializable`, meaning it can be serialized to BSON — the data format Hytale's UI system uses internally.

There are two main types of styles you'll use:

- **`HyUIStyle`** — A general-purpose style for text, colors, alignment, etc.
- **Style classes** — Specialized classes like `ButtonStyle`, `CheckBoxStyle`, `DropdownBoxStyle`, etc.

```java
// General styling
LabelBuilder.label()
    .withText("Hello")
    .withStyle(new HyUIStyle()
        .setTextColor("#ff0000")
        .setFontSize(20)
        .setRenderBold(true));

// Specialized button styling
ButtonBuilder.textButton()
    .withText("Click Me")
    .withStyle(ButtonStyle.primaryStyle());
```

---

## 2. Why Style Classes?

Style classes solve a specific problem: **stateful styling**. A button needs to look different when it's hovered, pressed, or disabled. A checkbox needs different visuals when checked vs. unchecked.

`HyUIStyle` handles simple cases, but when you need multiple states with different backgrounds, labels, and sounds, you need a dedicated style class:

```java
ButtonStyle style = new ButtonStyle()
    .withDefault(new ButtonStyleState()
        .withBackground(new HyUIPatchStyle().setTexturePath("button_default.png")))
    .withHovered(new ButtonStyleState()
        .withBackground(new HyUIPatchStyle().setTexturePath("button_hover.png")))
    .withPressed(new ButtonStyleState()
        .withBackground(new HyUIPatchStyle().setTexturePath("button_pressed.png")))
    .withDisabled(new ButtonStyleState()
        .withBackground(new HyUIPatchStyle().setTexturePath("button_disabled.png")))
    .withSounds(DefaultStyles.buttonSounds());

ButtonBuilder.textButton()
    .withText("Styled Button")
    .withStyle(style);
```

This is verbose, but it gives you complete control. Fortunately, `DefaultStyles` handles the common cases.

---

## 3. The `DefaultStyles` Class

`DefaultStyles` contains pre-configured style definitions that match Hytale's native UI appearance. Use it when you want your UI to look like it belongs in the game.

```java
// Hytale's primary button style
ButtonBuilder.textButton()
    .withText("Primary")
    .withStyle(DefaultStyles.primaryTextButtonStyle());

// Hytale's destructive (red/danger) button style
ButtonBuilder.textButton()
    .withText("Delete")
    .withStyle(DefaultStyles.destructiveTextButtonStyle());

// Default dropdown style
DropdownBoxBuilder.dropdownBox()
    .addOption("Option 1", "opt1")
    .withStyle(DefaultStyles.defaultDropdownBoxStyle());
```

Many style classes also provide static convenience methods that are equivalent to `DefaultStyles`:

```java
// These are equivalent
ButtonStyle.primaryStyle();
DefaultStyles.primaryButtonStyle();
```

---

## 4. Available Style Classes

All style classes live in the `au.ellie.hyui.types` package.

### Button Styles

| Class | Purpose |
|---|---|
| `ButtonStyle` | For icon buttons, raw buttons, etc. |
| `TextButtonStyle` | For text buttons with label states |
| `ButtonSounds` | Sound effects for button interactions |

### Input Styles

| Class | Purpose |
|---|---|
| `InputFieldStyle` | Text fields and number fields |
| `InputFieldDecorationStyle` | Decorative borders/backgrounds for input fields |
| `CheckBoxStyle` | Checkbox appearance (checked/unchecked states) |
| `SliderStyle` | Slider appearance |
| `ColorPickerStyle` | Color picker appearance |
| `ColorPickerDropdownBoxStyle` | Dropdown-style color picker |

### Dropdown Styles

| Class | Purpose |
|---|---|
| `DropdownBoxStyle` | Standard dropdown boxes |
| `FileDropdownBoxStyle` | File selection dropdowns |
| `DropdownBoxSounds` | Sound effects for dropdown interactions |

### Navigation Styles

| Class | Purpose |
|---|---|
| `TabStyle` | Individual tab appearance |
| `TabNavigationStyle` | Tab navigation bar |
| `TabStateStyle` | Tab state indicators |

### Other Styles

| Class | Purpose |
|---|---|
| `TextTooltipStyle` | Tooltip appearance |
| `PopupMenuLayerStyle` | Popup menu styling |
| `MenuItemStyle` | Menu item appearance |
| `ItemGridStyle` | Item grid appearance |
| `ScrollbarStyle` | Scrollbar appearance |
| `HyUIPatchStyle` | 9-patch backgrounds with borders |
| `HyUIStyle` | General text and alignment styles |
| `SpriteFrame` | Sprite animation frames |

---

## 5. Basic Styling — Applying Default Styles

The fastest path to a polished UI is using `DefaultStyles`. Here's a practical example of a styled panel with inputs and buttons:

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Settings">
            <div class="container-contents" id="contents">
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withBuilderInjection("contents", parent -> {
        parent.add(LabelBuilder.label()
            .withText("Player Name")
            .withStyle(new HyUIStyle().setTextColor("#FFFFFF").setFontSize(14)));

        parent.add(TextFieldBuilder.textField()
            .withPlaceholder("Enter name...")
            .withStyle(DefaultStyles.defaultInputFieldStyle()));

        parent.add(ButtonBuilder.textButton()
            .withText("Save")
            .withStyle(DefaultStyles.primaryTextButtonStyle())
            .withId("saveBtn"));
    })
    .addEventListener("saveBtn", CustomUIEventBindingType.Activating, ctx -> {
        playerRef.sendMessage(Message.raw("Saved!"));
    })
    .open(store);
```

---

## 6. Style Classes Deep Dive — Stateful Styling

Here's a full example of a checkbox that looks different when checked vs. unchecked:

```java
CheckBoxStyle checkStyle = new CheckBoxStyle()
    .withChecked(new CheckBoxStateStyle()
        .withBackground(new HyUIPatchStyle()
            .setTexturePath("ui/checkbox_checked.png")
            .setBorderSize(2)))
    .withUnchecked(new CheckBoxStateStyle()
        .withBackground(new HyUIPatchStyle()
            .setTexturePath("ui/checkbox_unchecked.png")
            .setBorderSize(2)));

CheckBoxBuilder.checkBox()
    .withStyle(checkStyle)
    .withId("myCheckbox");
```

For sliders with custom track and handle textures:

```java
SliderStyle sliderStyle = new SliderStyle()
    .withTrack(new HyUIPatchStyle().setTexturePath("ui/slider_track.png"))
    .withHandle(new HyUIPatchStyle().setTexturePath("ui/slider_handle.png"))
    .withFilledTrack(new HyUIPatchStyle().setTexturePath("ui/slider_filled.png"));

SliderBuilder.slider()
    .withMin(0).withMax(100).withValue(50)
    .withStyle(sliderStyle)
    .withId("volumeSlider");
```

---

## 7. Custom Styles — Building Your Own

For a fully branded UI, define your style objects once and reuse them across your plugin:

```java
// styles/MyPluginStyles.java
public class MyPluginStyles {

    public static TextButtonStyle primaryButton() {
        return new TextButtonStyle()
            .withDefault(new TextButtonStateStyle()
                .withBackground(new HyUIPatchStyle()
                    .setTexturePath("myplugin/btn_primary.png")
                    .setBorderSize(4))
                .withLabel(new HyUIStyle()
                    .setTextColor("#FFFFFF")
                    .setFontSize(14)
                    .setRenderBold(true)))
            .withHovered(new TextButtonStateStyle()
                .withBackground(new HyUIPatchStyle()
                    .setTexturePath("myplugin/btn_primary_hover.png")
                    .setBorderSize(4)))
            .withPressed(new TextButtonStateStyle()
                .withBackground(new HyUIPatchStyle()
                    .setTexturePath("myplugin/btn_primary_pressed.png")
                    .setBorderSize(4)))
            .withSounds(DefaultStyles.buttonSounds());
    }

    public static HyUIStyle headerLabel() {
        return new HyUIStyle()
            .setTextColor("#FFD700")
            .setFontSize(18)
            .setRenderBold(true);
    }

    public static HyUIStyle bodyLabel() {
        return new HyUIStyle()
            .setTextColor("#CCCCCC")
            .setFontSize(13);
    }
}
```

Then use them anywhere:

```java
ButtonBuilder.textButton()
    .withText("Confirm")
    .withStyle(MyPluginStyles.primaryButton());

LabelBuilder.label()
    .withText("Welcome!")
    .withStyle(MyPluginStyles.headerLabel());
```

---

## 8. When to Use What

| Situation | Use |
|---|---|
| Simple text color/size/bold | `HyUIStyle` |
| Labels and static text | `HyUIStyle` |
| Buttons, checkboxes, sliders | Dedicated style class |
| Hover/pressed/disabled states | Dedicated style class |
| Sound effects on interaction | `ButtonSounds` / `DropdownBoxSounds` |
| Match Hytale's native look | `DefaultStyles` |
| Reusable branded styles | Custom class wrapping style builders |

---

## Best Practices

- **Define styles once.** Create a `MyPluginStyles` utility class so you don't repeat style definitions across your pages.
- **Use `DefaultStyles` for prototyping.** Swap in custom textures later without changing the logic.
- **Match states.** If you define a `.withDefault()` on a `ButtonStyle`, always define `.withHovered()` and `.withPressed()` too — missing states can cause visual glitches.
- **Texture paths are relative** to your plugin's asset pack. Double-check that paths exist before testing.
- **`HyUIPatchStyle` needs a border size.** Forget `setBorderSize()` and your texture will stretch incorrectly.
