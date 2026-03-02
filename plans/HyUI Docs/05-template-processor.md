# Tutorial: Template Processor

The Template Processor lets you write reusable HYUIML templates with variable placeholders, then render them with different data at runtime. Instead of building HTML strings by hand with string concatenation, you define a template once and pass a context map to fill it in cleanly.

---

## Why Use the Template Processor?

Without it, dynamic HTML looks like this:

```java
// Messy — fragile, hard to read, easy to break
String html = "<div class=\"page-overlay\"><div class=\"container\" data-hyui-title=\"" 
    + playerName + "'s Stats\"><p>Level: " + level + "</p><p>HP: " + hp + "</p></div></div>";
```

With the Template Processor:

```java
// Clean — template is separate from data
String template = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="{{ playerName }}'s Stats">
            <p>Level: {{ level }}</p>
            <p>HP: {{ hp }}</p>
        </div>
    </div>
    """;

String html = TemplateProcessor.process(template, Map.of(
    "playerName", playerRef.getDisplayName(),
    "level",      String.valueOf(getLevel(playerRef)),
    "hp",         String.valueOf(getHp(playerRef))
));
```

---

## Basic Syntax

Variables are wrapped in double curly braces: `{{ variableName }}`

```html
<p>Welcome, {{ name }}!</p>
<p>You have {{ coins }} coins.</p>
<button id="buy-{{ itemId }}">Buy {{ itemName }}</button>
```

Call `TemplateProcessor.process()` with the template string and a `Map<String, String>`:

```java
import net.hyui.api.template.TemplateProcessor;

String rendered = TemplateProcessor.process(template, Map.of(
    "name",     "Steve",
    "coins",    "150",
    "itemId",   "iron_sword",
    "itemName", "Iron Sword"
));
```

All values in the map must be `String`. Convert numbers, booleans, etc. before passing them in.

---

## Using Templates with Pages

```java
String template = """
    <div class="page-overlay">
        <div class="container" data-hyui-title="{{ title }}">
            <div class="container-contents">
                <p style="color: #FFD700; font-size: 18;">{{ heading }}</p>
                <p>{{ body }}</p>
                <button id="btn-action">{{ actionLabel }}</button>
                <button id="btn-close">Close</button>
            </div>
        </div>
    </div>
    """;

String html = TemplateProcessor.process(template, Map.of(
    "title",       "Guild Invite",
    "heading",     "You've been invited!",
    "body",        "TitanCraft Guild wants you to join.",
    "actionLabel", "Accept Invite"
));

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("btn-action", CustomUIEventBindingType.Activating, ctx -> {
        joinGuild(playerRef, "TitanCraft");
        PageBuilder.closeForPlayer(playerRef, store);
    })
    .addEventListener("btn-close", CustomUIEventBindingType.Activating, ctx -> {
        PageBuilder.closeForPlayer(playerRef, store);
    })
    .open(store);
```

---

## Looping — Generating Repeated Rows

The Template Processor supports a simple loop syntax for repeating a block of HTML for each item in a list.

### Loop Syntax

```html
{% for item in items %}
    <div style="layout: horizontal; spacing: 8;">
        <p>{{ item.name }}</p>
        <p style="color: #AAAAAA;">{{ item.type }}</p>
        <button id="btn-{{ item.id }}">Select</button>
    </div>
{% endfor %}
```

Pass a `List<Map<String, String>>` for the loop variable:

```java
import net.hyui.api.template.TemplateContext;

List<Map<String, String>> itemData = inventory.stream()
    .map(item -> Map.of(
        "name", item.getDisplayName(),
        "type", item.getType(),
        "id",   String.valueOf(item.getId())
    ))
    .toList();

String html = TemplateProcessor.process(template,
    TemplateContext.of("items", itemData));
```

### Mixing Scalars and Lists

Use `TemplateContext.builder()` to combine both:

```java
TemplateContext ctx = TemplateContext.builder()
    .put("title", "My Inventory")
    .putList("items", itemData)
    .build();

String html = TemplateProcessor.process(template, ctx);
```

---

## Conditionals

Use `{% if %}` blocks to include or exclude sections based on a boolean flag:

```html
<p>Welcome back, {{ playerName }}!</p>

{% if isAdmin %}
    <button id="btn-admin">Admin Panel</button>
{% endif %}

{% if hasItems %}
    <div id="item-list">
        {% for item in items %}
            <p>{{ item.name }}</p>
        {% endfor %}
    </div>
{% else %}
    <p style="color: #888888;">Your inventory is empty.</p>
{% endif %}
```

Pass boolean flags as the strings `"true"` or `"false"`:

```java
TemplateContext ctx = TemplateContext.builder()
    .put("playerName", playerRef.getDisplayName())
    .put("isAdmin",    String.valueOf(isAdmin(playerRef)))
    .put("hasItems",   String.valueOf(!inventory.isEmpty()))
    .putList("items",  itemData)
    .build();
```

---

## Storing Templates in Files

For complex layouts, store your template in a `.html` file in your plugin's resources and load it at runtime:

```
src/main/resources/
  templates/
    inventory.html
    shop.html
    player-stats.html
```

```java
import net.hyui.api.template.TemplateLoader;

// Load from classpath resources
String template = TemplateLoader.loadFromResource("templates/inventory.html");

String html = TemplateProcessor.process(template, ctx);
```

This keeps your Java code clean and makes templates easy to edit without recompiling.

---

## Full Example — Dynamic Player Stats Page

**Template (`templates/player-stats.html`):**

```html
<div class="page-overlay">
    <div class="container" data-hyui-title="{{ playerName }}'s Stats">
        <div class="container-contents">

            <p style="font-size: 18; color: #FFD700;">{{ playerName }}</p>
            <p>Level: {{ level }}</p>
            <p>Guild: {{ guild }}</p>

            <p style="color: #AAAAAA; font-size: 12;">— Equipment —</p>

            {% if hasEquipment %}
                {% for item in equipment %}
                    <div style="layout: horizontal; spacing: 8;">
                        <p style="width: 120;">{{ item.slot }}</p>
                        <p style="color: {{ item.rarityColor }};">{{ item.name }}</p>
                    </div>
                {% endfor %}
            {% else %}
                <p style="color: #888888;">No equipment equipped.</p>
            {% endif %}

            <button id="btn-close" style="anchor-bottom: 12; anchor-right: 12;">
                Close
            </button>
        </div>
    </div>
</div>
```

**Java:**

```java
List<Map<String, String>> equipmentData = getEquipment(target).stream()
    .map(eq -> Map.of(
        "slot",        eq.getSlot(),
        "name",        eq.getName(),
        "rarityColor", getRarityColor(eq.getRarity())
    ))
    .toList();

String template = TemplateLoader.loadFromResource("templates/player-stats.html");

TemplateContext ctx = TemplateContext.builder()
    .put("playerName",    target.getDisplayName())
    .put("level",         String.valueOf(getLevel(target)))
    .put("guild",         getGuildName(target))
    .put("hasEquipment",  String.valueOf(!equipmentData.isEmpty()))
    .putList("equipment", equipmentData)
    .build();

String html = TemplateProcessor.process(template, ctx);

PageBuilder.pageForPlayer(playerRef)
    .fromHtml(html)
    .addEventListener("btn-close", CustomUIEventBindingType.Activating, c -> {
        PageBuilder.closeForPlayer(playerRef, store);
    })
    .open(store);
```

---

## Tips

- **All values are strings.** The template processor does simple text substitution — no arithmetic or type coercion.
- **Variable names are case-sensitive.** `{{ PlayerName }}` and `{{ playerName }}` are different.
- **Unknown variables render as empty string.** A missing key will silently replace `{{ varName }}` with nothing. Check your context map carefully.
- **Use files for large templates.** Inline template strings longer than ~20 lines become hard to read and maintain. Move them to resource files early.
- **Nested object access.** In loop blocks, `{{ item.name }}` accesses the `name` key from each map in your list.
