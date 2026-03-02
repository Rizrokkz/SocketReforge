# Tutorial: Page Building

Pages are full-screen UIs that lock the player's mouse and prevent in-game interaction — think crafting menus, shop screens, or configuration dialogs. HyUI makes building them straightforward using **HYUIML**, an HTML-like syntax that compiles to Hytale's native `.ui` format.

---

## The Four-Step Pattern

Every HyUI page follows the same structure:

1. Write the layout in HYUIML
2. Create a `PageBuilder` and load your layout
3. Wire up event listeners
4. Call `.open(store)`

---

## Step 1 — Write the Layout

HYUIML looks like HTML. The outermost wrapper is always a `page-overlay` containing a `container`:

```html
<div class="page-overlay">
    <div class="container" data-hyui-title="My Menu">
        <div class="container-contents">
            <p>Welcome to the menu!</p>
            <button id="myBtn">Click Me</button>
        </div>
    </div>
</div>
```

> **Important:** Give every interactive element a unique `id`. This is how you attach event listeners to it.

---

## Step 2 — Create the PageBuilder

Pass your HYUIML string via `.fromHtml()`:

```java
import net.hyui.api.builder.PageBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;

String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="My Menu">
            <div class="container-contents">
                <p>Welcome to the menu!</p>
                <button id="myBtn">Click Me</button>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    // ... add listeners ...
    .open(store);
```

You can also load from a file in your asset pack:

```java
PageBuilder.pageForPlayer(playerRef)
    .fromFile("Pages/MyMenu.html")
    .open(store);
```

---

## Step 3 — Wire Up Event Listeners

Use `.addEventListener()` to bind a Java lambda to any element by its `id`:

```java
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("myBtn", CustomUIEventBindingType.Activating, (ctx) -> {
        playerRef.sendMessage(Message.raw("Button clicked!"));
    })
    .open(store);
```

### Common Event Types

| Event | When it fires |
|---|---|
| `Activating` | Left click / primary activation |
| `RightClicking` | Right click |
| `DoubleClicking` | Double click |
| `ValueChanged` | Slider or input value changed |
| `MouseEntered` | Mouse cursor enters the element |
| `MouseExited` | Mouse cursor leaves the element |
| `Dismissing` | Page is closed/dismissed |
| `FocusGained` | Element receives keyboard focus |
| `FocusLost` | Element loses keyboard focus |

---

## Step 4 — Open the Page

```java
.open(store);
```

To close it programmatically from anywhere:

```java
PageBuilder.closeForPlayer(playerRef, store);
```

---

## Full Example — A Simple Confirmation Dialog

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Confirm Action">
            <div class="container-contents">
                <p>Are you sure you want to proceed?</p>
                <button id="btn-confirm">Confirm</button>
                <button id="btn-cancel">Cancel</button>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("btn-confirm", CustomUIEventBindingType.Activating, ctx -> {
        performAction(playerRef);
        PageBuilder.closeForPlayer(playerRef, store);
        playerRef.sendMessage(Message.raw("Action confirmed!"));
    })
    .addEventListener("btn-cancel", CustomUIEventBindingType.Activating, ctx -> {
        PageBuilder.closeForPlayer(playerRef, store);
        playerRef.sendMessage(Message.raw("Cancelled."));
    })
    .open(store);
```

---

## Updating a Page at Runtime

Once a page is open, use `UICommandBuilder` to push changes without reopening:

```java
UICommandBuilder update = new UICommandBuilder();

// Change text content
update.set("#myLabel.TextSpans", Message.raw("Updated text!"));

// Add a new element
update.append("#myList", "Components/ListItem.html");

// Remove an element
update.remove("#old-element");

// Clear an element's children
update.clear("#container");
```

Send the update to the client using `sendUpdate()` inside your event handler via `UIContext`:

```java
.addEventListener("refreshBtn", CustomUIEventBindingType.Activating, ctx -> {
    UICommandBuilder update = new UICommandBuilder();
    update.set("#status", Message.raw("Refreshed at " + System.currentTimeMillis()));
    ctx.sendUpdate(update);
})
```

> **Note:** You must always either open a new UI or call `sendUpdate()` after every event — otherwise the client will display "Loading..." forever and lock the player out.

---

## Page Lifetime

Control whether the player can close the page themselves:

```java
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withLifetime(CustomPageLifetime.CanDismiss)  // player can press Escape
    .open(store);
```

| Value | Behaviour |
|---|---|
| `CantClose` | Page cannot be closed by the player |
| `CanDismiss` | Player can dismiss (e.g. press Escape) |
| `CanDismissOrCloseThroughInteraction` | Can dismiss or close via in-game interaction |

---

## HYUIML Quick Reference

| HTML element | What it produces |
|---|---|
| `<div class="page-overlay">` | Full-screen overlay container |
| `<div class="container">` | Standard Hytale window frame |
| `<div class="container-contents">` | Content area inside the frame |
| `<p>` | Text label |
| `<button id="...">` | Interactive button |
| `<img src="...">` | Asset-backed image |
| `<img class="dynamic-image" src="url">` | Runtime-downloaded image |
| `<input type="text">` | Text input field |
| `<input type="number">` | Numeric input field |

Use the `style` attribute for positioning:

```html
<button id="closeBtn" style="anchor-bottom: 12; anchor-right: 12;">Close</button>
<p style="font-size: 18; color: #FFD700;">Gold heading text</p>
<div style="layout: horizontal; spacing: 8;">...</div>
```

---

## Tips

- Always provide a `data-hyui-title` on the container — it sets the window title bar text.
- Use `anchor-bottom`/`anchor-right` to pin elements to the bottom or right edge of the container.
- Use `layout: horizontal` or `layout: vertical` on `<div>` elements to control child flow.
- `spacing` controls the gap between children in a layout div.
