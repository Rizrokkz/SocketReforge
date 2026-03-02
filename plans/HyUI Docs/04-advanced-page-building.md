# Tutorial: Advanced Page Building

This tutorial covers techniques for building more complex, dynamic pages — including runtime element injection, passing data between pages, reading input field values, and using the escape hatch to access raw `UICommandBuilder` instances.

---

## 1. Dynamic Element Injection at Build Time

Use `withBuilderInjection()` to inject Java-built elements into a HYUIML container at page-open time. This is the recommended approach when you need to generate rows, cards, or lists dynamically from server-side data.

```java
List<Player> players = getOnlinePlayers();

String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Player List">
            <div class="container-contents">
                <p>Online Players</p>
                <div id="player-list"></div>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withBuilderInjection("player-list", parent -> {
        for (Player p : players) {
            parent.add(LabelBuilder.label()
                .withText(p.getDisplayName())
                .withStyle(new HyUIStyle().setTextColor("#FFFFFF")));
        }
    })
    .open(store);
```

---

## 2. Injecting Elements After the Page Opens (Runtime Injection)

Use `UICommandBuilder` to add or remove elements after the page is already open. This is useful for live updates without closing and reopening the page.

```java
.addEventListener("loadMoreBtn", CustomUIEventBindingType.Activating, ctx -> {
    List<String> newItems = fetchMoreItems();

    UICommandBuilder update = new UICommandBuilder();
    for (String item : newItems) {
        // appendInline adds HYUIML/native UI markup inline
        update.appendInline("#item-list",
            "Label { Text: " + item + "; Style: (TextColor: #CCCCCC;); }");
    }
    ctx.sendUpdate(update);
})
```

> **Tip:** For complex elements (images, custom panels), prefer `.append("#selector", "path/to/Component.ui")` over `appendInline` to keep code clean and leverage the asset pack.

---

## 3. Reading Input Field Values

Capture what the player typed in a text field by reading it from the `UIContext` when a button is clicked:

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Search">
            <div class="container-contents">
                <input type="text" id="search-field" />
                <button id="search-btn">Search</button>
                <div id="results"></div>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("search-btn", CustomUIEventBindingType.Activating, ctx -> {
        String query = ctx.getElementValue("search-field");
        List<String> results = searchItems(query);

        UICommandBuilder update = new UICommandBuilder();
        update.clear("#results");
        for (String result : results) {
            update.appendInline("#results",
                "Label { Text: " + result + "; }");
        }
        ctx.sendUpdate(update);
    })
    .open(store);
```

### Reading Slider and Number Field Values

```java
.addEventListener("slider", CustomUIEventBindingType.ValueChanged, ctx -> {
    float value = ctx.getFloatValue("volume-slider");
    applyVolume(playerRef, value);
    UICommandBuilder update = new UICommandBuilder();
    update.set("#volume-label", Message.raw("Volume: " + (int)(value * 100) + "%"));
    ctx.sendUpdate(update);
})
```

---

## 4. Passing Data Between Pages

When navigating from one page to another, close the current page and open the new one, passing data via constructor or a shared state object:

```java
// Page 1 — list of items
.addEventListener("item-row-3", CustomUIEventBindingType.Activating, ctx -> {
    PageBuilder.closeForPlayer(playerRef, store);
    ItemDetailPage.open(playerRef, store, items.get(3));
})

// Page 2 — item detail
public class ItemDetailPage {
    public static void open(PlayerRef playerRef, Store store, MyItem item) {
        String html = """
            <div class="page-overlay">
                <div class="container" data-hyui-title="Item Detail">
                    <div class="container-contents">
                        <p id="item-name"></p>
                        <p id="item-desc"></p>
                        <button id="btn-back">Back</button>
                    </div>
                </div>
            </div>
            """;

        PageBuilder.pageForPlayer(playerRef)
            .fromHtml(html)
            .onPageOpen(ctx -> {
                UICommandBuilder init = new UICommandBuilder();
                init.set("#item-name", Message.raw(item.getName()));
                init.set("#item-desc", Message.raw(item.getDescription()));
                ctx.sendUpdate(init);
            })
            .addEventListener("btn-back", CustomUIEventBindingType.Activating, ctx -> {
                PageBuilder.closeForPlayer(playerRef, store);
                MyListPage.open(playerRef, store);  // go back
            })
            .open(store);
    }
}
```

---

## 5. Conditional UI State

Show or hide elements based on player state by updating element visibility at runtime:

```java
.addEventListener("toggle-advanced", CustomUIEventBindingType.Activating, ctx -> {
    boolean show = ctx.getToggleState(); // track this in your own boolean
    UICommandBuilder update = new UICommandBuilder();

    if (show) {
        update.append("#advanced-section", "Sections/Advanced.html");
    } else {
        update.clear("#advanced-section");
    }

    ctx.sendUpdate(update);
})
```

---

## 6. The Escape Hatch — Raw `UICommandBuilder`

HyUI's builder API covers most use cases, but Hytale's underlying protocol has properties not yet wrapped by HyUI. Use the escape hatch to access the raw `UICommandBuilder` at any point in the builder chain:

```java
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withRawCommands(rawBuilder -> {
        // Access any UICommandBuilder method directly
        rawBuilder.set("#my-element.SomeUnwrappedProperty", "value");
    })
    .open(store);
```

You can also get the raw builder inside an event handler via `UIContext`:

```java
.addEventListener("myBtn", CustomUIEventBindingType.Activating, ctx -> {
    UICommandBuilder raw = ctx.getRawCommandBuilder();
    raw.set("#special-element.NativeProperty", someValue);
    ctx.sendUpdate(raw);
})
```

---

## 7. `onPageOpen` — Initialising Values After Open

Use `.onPageOpen()` to run logic immediately after the page loads on the client. This is the right place to populate text fields, set initial slider positions, or do a first data load:

```java
PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .onPageOpen(ctx -> {
        UICommandBuilder init = new UICommandBuilder();
        init.set("#welcome-text", Message.raw("Hello, " + playerRef.getDisplayName() + "!"));
        init.set("#coin-count", Message.raw(String.valueOf(getCoins(playerRef))));
        ctx.sendUpdate(init);
    })
    .addEventListener("buyBtn", CustomUIEventBindingType.Activating, ctx -> {
        // ... handle purchase
    })
    .open(store);
```

---

## 8. Periodic Refresh (HUD-style Counters)

For pages that display live data (timers, health bars, resource counts), use a server-side scheduled task to push updates:

```java
// Open the page
PageBuilder pageRef = PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .open(store);

// Schedule periodic updates (pseudo-code — use your server's task API)
scheduler.scheduleRepeating(20L, () -> {
    if (!playerRef.isOnline()) return;

    UICommandBuilder update = new UICommandBuilder();
    update.set("#timer", Message.raw(getRemainingTime()));
    pageRef.sendUpdate(playerRef, store, update);
});
```

---

## Tips

- **Keep pages stateless.** Store game state on the server side (in your own objects), not in the UI. The UI is just a view.
- **Close before opening a new page.** Call `PageBuilder.closeForPlayer()` before opening a different page for the same player.
- **`appendInline` is sent verbatim.** HyUI doesn't validate inline markup — typos will silently fail on the client. Test carefully.
- **Use `.onPageOpen()` for initial data.** Don't try to populate dynamic content in `withBuilderInjection` if the data comes from an async source; use `onPageOpen` with a `sendUpdate` instead.
- **Escape hatches are stable.** Raw `UICommandBuilder` methods are the same underlying protocol calls that HyUI itself uses — they won't suddenly break.
