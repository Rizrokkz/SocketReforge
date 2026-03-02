# Tutorial: Working with Item Grids

Item grids are scrollable slot-based containers — the kind you'd use for an inventory, a shop, a chest, or a crafting grid. HyUI's `ItemGridBuilder` handles the heavy lifting: populating slots, handling click events, and updating slots at runtime.

---

## Basic Item Grid

The simplest grid is an empty container you populate with `ItemGridSlot` objects:

```java
import net.hyui.api.builder.ItemGridBuilder;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.inventory.ItemStack;

// Build a 5-column grid with 20 slots
ItemGridBuilder grid = ItemGridBuilder.itemGrid()
    .withId("inv-grid")
    .withColumns(5)
    .withSlots(buildSlots());
```

---

## Populating Slots

Each slot is an `ItemGridSlot`. You can populate it with an `ItemStack`, leave it empty, or mark it as locked:

```java
private List<ItemGridSlot> buildSlots() {
    List<ItemGridSlot> slots = new ArrayList<>();

    for (int i = 0; i < 20; i++) {
        ItemStack item = getItemAtIndex(i); // your own method
        if (item != null) {
            slots.add(ItemGridSlot.withItem(item));
        } else {
            slots.add(ItemGridSlot.empty());
        }
    }

    return slots;
}
```

---

## Embedding the Grid in a Page

Use `withBuilderInjection()` to inject the `ItemGridBuilder` into your HYUIML layout:

```java
String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Inventory">
            <div class="container-contents">
                <p>Your Items</p>
                <div id="grid-container"></div>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withBuilderInjection("grid-container", parent -> {
        parent.add(ItemGridBuilder.itemGrid()
            .withId("inv-grid")
            .withColumns(5)
            .withSlots(buildSlots()));
    })
    .addEventListener("inv-grid", CustomUIEventBindingType.SlotClicking, ctx -> {
        int slotIndex = ctx.getSlotIndex();
        handleSlotClick(playerRef, slotIndex, store);
    })
    .open(store);
```

---

## Handling Slot Events

Item grids emit slot-specific events. The most important ones are:

| Event | When it fires |
|---|---|
| `SlotClicking` | Player left-clicks a slot |
| `SlotDoubleClicking` | Player double-clicks a slot |
| `SlotMouseEntered` | Mouse enters a slot |
| `SlotMouseExited` | Mouse leaves a slot |
| `SlotMouseDragCompleted` | Player finishes dragging to/from a slot |

Retrieve the clicked slot index from `UIContext`:

```java
.addEventListener("inv-grid", CustomUIEventBindingType.SlotClicking, ctx -> {
    int index = ctx.getSlotIndex();
    ItemStack item = playerInventory.getItem(index);

    if (item != null) {
        playerRef.sendMessage(Message.raw("You clicked: " + item.getDisplayName()));
    }
})
```

For right-click on a slot:

```java
.addEventListener("inv-grid", CustomUIEventBindingType.RightClicking, ctx -> {
    int index = ctx.getSlotIndex();
    // handle right click
})
```

---

## Updating Slots at Runtime

Use `ItemGridBuilder`'s helper methods to update individual slots without rebuilding the whole grid:

```java
// Update a single slot
UICommandBuilder update = new UICommandBuilder();
ItemGridSlot updatedSlot = ItemGridSlot.withItem(newItemStack);
update.setObject("#inv-grid[" + slotIndex + "]", updatedSlot);
ctx.sendUpdate(update);
```

HyUI also provides convenience methods on `ItemGridBuilder` for common operations:

```java
// Get all current slots (for state tracking)
List<ItemGridSlot> allSlots = grid.getAllSlots();

// Update a slot by index
grid.updateSlot(slotIndex, ItemGridSlot.withItem(newItem));

// Remove an item from a slot (make it empty)
grid.removeSlot(slotIndex);
```

---

## Full Example — Clickable Shop Grid

```java
List<ShopItem> shopItems = getShopItems(); // your own data

String html = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="Shop">
            <div class="container-contents">
                <p style="color: #FFD700;">Click an item to purchase</p>
                <div id="shop-grid"></div>
                <button id="btn-close" style="anchor-bottom: 12; anchor-right: 12;">
                    Close
                </button>
            </div>
        </div>
    </div>
    """;

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .withBuilderInjection("shop-grid", parent -> {
        List<ItemGridSlot> slots = shopItems.stream()
            .map(item -> ItemGridSlot.withItem(item.toItemStack()))
            .toList();

        parent.add(ItemGridBuilder.itemGrid()
            .withId("shop-grid-slots")
            .withColumns(4)
            .withSlots(slots));
    })
    .addEventListener("shop-grid-slots", CustomUIEventBindingType.SlotClicking, ctx -> {
        int index = ctx.getSlotIndex();
        if (index < shopItems.size()) {
            ShopItem item = shopItems.get(index);
            if (playerHasEnoughCoins(playerRef, item.getPrice())) {
                giveItem(playerRef, item);
                deductCoins(playerRef, item.getPrice());
                playerRef.sendMessage(Message.raw("Purchased: " + item.getName()));
            } else {
                playerRef.sendMessage(Message.raw("Not enough coins!"));
            }
        }
        // Must acknowledge the event
        UICommandBuilder update = new UICommandBuilder();
        ctx.sendUpdate(update);
    })
    .addEventListener("btn-close", CustomUIEventBindingType.Activating, ctx -> {
        PageBuilder.closeForPlayer(playerRef, store);
    })
    .open(store);
```

---

## Item Grid Event Data

When a slot event fires, HyUI passes structured data through `UIContext`. Key fields available:

| Field | Method | Description |
|---|---|---|
| Slot index | `ctx.getSlotIndex()` | Zero-based index of the clicked slot |
| Button type | `ctx.getMouseButton()` | Which mouse button was used |
| Drag data | `ctx.getDragData()` | Data from a completed drag operation |

> See the **Item Grid Event Data** reference page in the HyUI docs for the full list of available context fields.

---

## Styling the Grid

Apply an `ItemGridStyle` to customise scrollbar appearance and slot backgrounds:

```java
ItemGridBuilder.itemGrid()
    .withId("inv-grid")
    .withColumns(5)
    .withSlots(slots)
    .withStyle(new ItemGridStyle()
        .withScrollbar(DefaultStyles.defaultScrollbarStyle())
        .withSlotBackground(new HyUIPatchStyle()
            .setTexturePath("myplugin/slot_bg.png")
            .setBorderSize(2)));
```

---

## Tips

- **Always acknowledge events.** After every `SlotClicking` handler, call `ctx.sendUpdate(update)` even with an empty `UICommandBuilder`, or the client will freeze on "Loading...".
- **Slot indices are zero-based.** Index 0 is the top-left slot.
- **Empty vs. locked slots.** Use `ItemGridSlot.empty()` for an open slot and `ItemGridSlot.locked()` for a slot the player can see but not interact with.
- **Column count matters.** `withColumns(5)` means row 2 starts at index 5, row 3 at index 10, etc.
