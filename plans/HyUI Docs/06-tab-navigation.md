# Tutorial: Tab Navigation

Tab navigation lets you organise a complex page into multiple sections the player can switch between without reopening the UI. HyUI provides `TabNavigationBuilder` and `TabContentBuilder` to make this straightforward.

---

## Basic Tab Structure

A tabbed page has two parts:

1. A **`TabNavigationBuilder`** — the row of tab buttons at the top
2. One or more **`TabContentBuilder`** instances — the content area for each tab

Each tab button references a content panel by ID. When the player clicks a tab, HyUI automatically shows that panel and hides the others.

---

## Minimal Example — Two Tabs

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="My Tabbed Menu">
            <div class="container-contents">
                <div id="tab-nav"></div>
                <div id="tab-content"></div>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withBuilderInjection("tab-nav", parent -> {
        parent.add(TabNavigationBuilder.tabNavigation()
            .withId("main-tabs")
            .addTab("tab-inventory", "Inventory", "content-inventory")
            .addTab("tab-stats",     "Stats",     "content-stats"));
    })
    .withBuilderInjection("tab-content", parent -> {
        // Inventory tab content
        parent.add(TabContentBuilder.tabContent()
            .withId("content-inventory")
            .add(LabelBuilder.label().withText("Your inventory goes here.")));

        // Stats tab content
        parent.add(TabContentBuilder.tabContent()
            .withId("content-stats")
            .add(LabelBuilder.label().withText("Your stats go here.")));
    })
    .open(store);
```

The `addTab(id, label, contentId)` call links each tab button to its content panel by matching the `contentId` to the `withId()` of a `TabContentBuilder`.

---

## Custom Tab Button Styles

By default, tabs use the secondary (unselected) and primary (selected) button styles. You can provide your own `ButtonBuilder` per tab:

```java
TabNavigationBuilder.tabNavigation()
    .withId("main-tabs")
    .addTab(
        "tab-inventory",
        "Inventory",
        "content-inventory",
        ButtonBuilder.textButton()
            .withStyle(MyPluginStyles.tabButtonStyle())
    )
    .addTab(
        "tab-stats",
        "Stats",
        "content-stats",
        ButtonBuilder.textButton()
            .withStyle(MyPluginStyles.tabButtonStyle())
    );
```

---

## Styling the Navigation Bar

Apply a `TabNavigationStyle` to control the overall bar appearance:

```java
TabNavigationBuilder.tabNavigation()
    .withId("main-tabs")
    .withStyle(new TabNavigationStyle()
        .withBackground(new HyUIPatchStyle()
            .setTexturePath("myplugin/tab_bar_bg.png")
            .setBorderSize(2))
        .withTabStyle(new TabStyle()
            .withSelected(new TabStateStyle()
                .withBackground(new HyUIPatchStyle()
                    .setTexturePath("myplugin/tab_selected.png")))
            .withUnselected(new TabStateStyle()
                .withBackground(new HyUIPatchStyle()
                    .setTexturePath("myplugin/tab_unselected.png")))))
    .addTab("tab-a", "Tab A", "content-a")
    .addTab("tab-b", "Tab B", "content-b");
```

---

## Reacting to Tab Changes

Listen for `SelectedTabChanged` on the `TabNavigationBuilder`'s id to run logic whenever the player switches tabs:

```java
.addEventListener("main-tabs", CustomUIEventBindingType.SelectedTabChanged, ctx -> {
    String activeTabId = ctx.getSelectedTabId();

    if ("tab-stats".equals(activeTabId)) {
        // Refresh stats data when the stats tab is opened
        UICommandBuilder update = new UICommandBuilder();
        update.set("#stat-level", Message.raw("Level: " + getLevel(playerRef)));
        update.set("#stat-hp",    Message.raw("HP: "    + getHp(playerRef)));
        ctx.sendUpdate(update);
    } else {
        // Still need to acknowledge the event
        ctx.sendUpdate(new UICommandBuilder());
    }
})
```

---

## Updating Tab Content at Runtime

You can add or update content inside a tab panel without switching away from it, using a standard `UICommandBuilder`:

```java
.addEventListener("refresh-btn", CustomUIEventBindingType.Activating, ctx -> {
    UICommandBuilder update = new UICommandBuilder();
    update.clear("#content-inventory");

    for (MyItem item : getInventory(playerRef)) {
        update.appendInline("#content-inventory",
            "Label { Text: " + item.getName() + "; }");
    }

    ctx.sendUpdate(update);
})
```

---

## Adding and Removing Tabs Dynamically

HyUI supports updating tab navigation at runtime:

```java
// Add a new tab after the page is open
UICommandBuilder update = new UICommandBuilder();
// Append the new tab button into the navigation bar
update.appendInline("#main-tabs", "Button #tab-new { Text: New Tab; }");
// Append the new content panel
update.appendInline("#tab-content", "Group #content-new { ... }");
ctx.sendUpdate(update);

// Remove a tab
UICommandBuilder remove = new UICommandBuilder();
remove.remove("#tab-inventory");
remove.remove("#content-inventory");
ctx.sendUpdate(remove);
```

---

## Full Example — Three-Tab RPG Menu

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Character Menu">
            <div class="container-contents">
                <div id="nav"></div>
                <div id="content" style="anchor-top: 40;"></div>
                <button id="btn-close" style="anchor-bottom: 12; anchor-right: 12;">
                    Close
                </button>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    // Tab navigation bar
    .withBuilderInjection("nav", parent -> {
        parent.add(TabNavigationBuilder.tabNavigation()
            .withId("char-tabs")
            .withStyle(DefaultStyles.defaultTabNavigationStyle())
            .addTab("tab-stats",     "Stats",     "pane-stats")
            .addTab("tab-skills",    "Skills",    "pane-skills")
            .addTab("tab-equipment", "Equipment", "pane-equipment"));
    })
    // Tab content panels
    .withBuilderInjection("content", parent -> {
        // Stats pane
        parent.add(TabContentBuilder.tabContent()
            .withId("pane-stats")
            .add(LabelBuilder.label().withText("Level: ...").withId("stat-level"))
            .add(LabelBuilder.label().withText("HP: ...").withId("stat-hp"))
            .add(LabelBuilder.label().withText("XP: ...").withId("stat-xp")));

        // Skills pane
        parent.add(TabContentBuilder.tabContent()
            .withId("pane-skills")
            .add(LabelBuilder.label().withText("Skills coming soon.")));

        // Equipment pane
        parent.add(TabContentBuilder.tabContent()
            .withId("pane-equipment")
            .add(LabelBuilder.label().withText("Equipment coming soon.")));
    })
    // Populate stats on page open
    .onPageOpen(ctx -> {
        UICommandBuilder init = new UICommandBuilder();
        init.set("#stat-level", Message.raw("Level: " + getLevel(playerRef)));
        init.set("#stat-hp",    Message.raw("HP: "    + getHp(playerRef)));
        init.set("#stat-xp",    Message.raw("XP: "    + getXp(playerRef)));
        ctx.sendUpdate(init);
    })
    // Close button
    .addEventListener("btn-close", CustomUIEventBindingType.Activating, ctx -> {
        PageBuilder.closeForPlayer(playerRef, store);
    })
    .open(store);
```

---

## Tips

- **`contentId` must match `withId()` exactly.** A typo here means the tab clicks but shows nothing.
- **The first tab added is selected by default.** To change the default selected tab, call `.withDefaultTab("tab-id")` on `TabNavigationBuilder` if your HyUI version supports it.
- **Always acknowledge `SelectedTabChanged`.** Even if you do nothing, call `ctx.sendUpdate(new UICommandBuilder())` or the client will freeze.
- **Tab content is always in the DOM.** HyUI shows/hides panels rather than destroying and recreating them. If you need to refresh data when switching tabs, do it in the `SelectedTabChanged` listener.
- **Combine with the Template Processor.** For content-heavy tabs, generate each tab's inner HTML with `TemplateProcessor.process()` and inject it via `TabContentBuilder.fromHtml()`.
