package irai.mod.reforge.UI;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ResonantCompendiumUtils;
import irai.mod.reforge.Common.ResonantCompendiumUtils.CompendiumEntry;
import irai.mod.reforge.Common.ResonantRecipeUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.NameResolver;

/** Native, categorized interface for the Resonant Compendium. */
public final class ResonantCompendiumUI {
    private static final String PAGE_UI = "Pages/ResonantCompendium.ui";
    private static final String FAMILY_CARD_UI = "Pages/ResonantCompendiumFamilyCard.ui";
    private static final String RARITY_CARD_UI = "Pages/ResonantCompendiumRarityCard.ui";
    private static final String RECIPE_CARD_UI = "Pages/ResonantCompendiumRecipeCard.ui";
    private static final String RECIPE_CARD_SMALL_UI = "Pages/ResonantCompendiumRecipeCardSmall.ui";
    private static final String RECIPE_PATTERN_ICON_UI = "Pages/ResonantCompendiumPatternIcon.ui";
    private static final String RECIPE_PATTERN_ICON_SMALL_UI = "Pages/ResonantCompendiumPatternIconSmall.ui";
    private static final String RECIPE_GRID_ROW_5_UI = "Pages/ResonantCompendiumRecipeGridRow5.ui";
    private static final String RECIPE_GRID_ROW_3_UI = "Pages/ResonantCompendiumRecipeGridRow3.ui";

    private static final List<String> FAMILY_ORDER = List.of(
            "Sword", "Axe", "Mace", "Dagger", "Bow", "Crossbow", "Staff",
            "Gun", "Glaive", "Knuckle", "Weapon", "Armor", "Other");
    private static final List<String> RARITY_ORDER = List.of("ALL", "COMMON", "RARE", "LEGENDARY", "OTHER");

    private static final class RecipeTypeIndex {
        private static final Map<String, String> TYPES = build();

        private static Map<String, String> build() {
            Map<String, String> types = new HashMap<>();
            for (ResonanceSystem.RecipeDisplay display : ResonanceSystem.getSeededRecipeDisplays()) {
                if (display == null || display.name() == null || display.name().isBlank()) continue;
                if (display.appliesTo() != null) {
                    types.put(ResonantRecipeUtils.normalizeRecipeName(display.name()), display.appliesTo());
                }
            }
            return Map.copyOf(types);
        }
    }

    private ResonantCompendiumUI() {}

    private static final class Entry {
        final String key;
        final String name;
        final String pattern;
        final String usages;
        final ResonantRecipeUtils.PatternStats stats;
        final int quantity;
        final Set<String> families;

        Entry(String key, String name, String pattern, String usages,
              ResonantRecipeUtils.PatternStats stats, int quantity, Set<String> families) {
            this.key = key;
            this.name = name == null ? "" : name;
            this.pattern = pattern == null ? "" : pattern;
            this.usages = usages == null ? "" : usages;
            this.stats = stats;
            this.quantity = Math.max(1, quantity);
            this.families = families == null ? Set.of("Other") : Set.copyOf(families);
        }

        boolean complete() {
            return stats != null && stats.isComplete();
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;
        final int completeCount;
        final int totalQuantity;

        Snapshot(List<Entry> entries, int completeCount, int totalQuantity) {
            this.entries = entries;
            this.completeCount = completeCount;
            this.totalQuantity = totalQuantity;
        }
    }

    public static void open(Player player, short heldSlot) {
        if (player == null || player.getPlayerRef() == null) return;
        Ref<EntityStore> entityRef = player.getPlayerRef().getReference();
        Store<EntityStore> store = entityRef == null ? null : entityRef.getStore();
        if (entityRef == null || store == null) {
            player.getPlayerRef().sendMessage(Message.raw("<color=#FF5555>Could not open the Resonant Compendium.</color>"));
            return;
        }
        try {
            player.getPageManager().openCustomPage(entityRef, store,
                    new NativeCompendiumPage(player.getPlayerRef(), heldSlot));
        } catch (Exception e) {
            System.err.println("[SocketReforge] Native ResonantCompendiumUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Snapshot collectSnapshot(ItemStack compendium) {
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(compendium);
        List<Entry> entries = new ArrayList<>();
        int completeCount = 0;
        int totalQuantity = 0;
        for (Map.Entry<String, CompendiumEntry> record : data.entrySet()) {
            CompendiumEntry value = record.getValue();
            if (value == null) continue;
            String name = value.name == null ? "" : value.name;
            String pattern = value.pattern == null ? "" : value.pattern;
            ResonantRecipeUtils.PatternStats stats = ResonantRecipeUtils.getPatternStats(pattern);
            if (stats.isComplete()) completeCount++;
            int quantity = Math.max(1, value.quantity);
            totalQuantity += quantity;
            entries.add(new Entry(encodeKey(record.getKey()), name, pattern,
                    value.usages, stats, quantity, resolveFamilies(name)));
        }
        entries.sort(Comparator.comparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
        return new Snapshot(entries, completeCount, totalQuantity);
    }

    private static Set<String> resolveFamilies(String recipeName) {
        String type = resolveRecipeTypeFromSystem(recipeName);
        if (type == null || type.isBlank()) return Set.of("Other");
        String normalized = type.trim();
        if (normalized.equalsIgnoreCase("Armor")) return Set.of("Armor");
        if (normalized.equalsIgnoreCase("Weapon")) return Set.of("Weapon");

        Set<String> families = new LinkedHashSet<>();
        for (String part : normalized.split("[/,]")) {
            String family = canonicalFamily(part);
            if (family != null) families.add(family);
        }
        return families.isEmpty() ? Set.of("Other") : Set.copyOf(families);
    }

    private static String canonicalFamily(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("sword")) return "Sword";
        if (value.startsWith("axe")) return "Axe";
        if (value.startsWith("mace")) return "Mace";
        if (value.startsWith("dagger")) return "Dagger";
        if (value.startsWith("bow")) return "Bow";
        if (value.startsWith("crossbow")) return "Crossbow";
        if (value.startsWith("staff")) return "Staff";
        if (value.startsWith("gun")) return "Gun";
        if (value.startsWith("glaive")) return "Glaive";
        if (value.startsWith("knuckle")) return "Knuckle";
        if (value.equals("weapon")) return "Weapon";
        if (value.equals("armor")) return "Armor";
        return "Other";
    }

    private static String resolveRecipeTypeFromSystem(String recipeName) {
        if (recipeName == null || recipeName.isBlank()) return "";
        return RecipeTypeIndex.TYPES.getOrDefault(ResonantRecipeUtils.normalizeRecipeName(recipeName), "");
    }

    private static boolean familyMatches(Entry entry, String selectedFamily) {
        if (entry == null || selectedFamily == null || selectedFamily.equals("ALL")) return true;
        if (entry.families.contains(selectedFamily)) return true;
        // Generic weapon recipes are valid in every concrete weapon-family view.
        return !selectedFamily.equals("Armor") && entry.families.contains("Weapon");
    }

    private static String rarityFor(Entry entry) {
        if (entry == null || entry.stats == null) return "OTHER";
        return switch (entry.stats.totalSlots()) {
            case 3 -> "COMMON";
            case 4 -> "RARE";
            case 5 -> "LEGENDARY";
            default -> "OTHER";
        };
    }

    private static String rarityLabel(Player player, String rarity) {
        return switch (rarity == null ? "" : rarity) {
            case "ALL" -> t(player, "ui.compendium.native.all_rarities", "All rarities");
            case "COMMON" -> t(player, "ui.compendium.native.rarity_common", "Common · 3 sockets");
            case "RARE" -> t(player, "ui.compendium.native.rarity_rare", "Rare · 4 sockets");
            case "LEGENDARY" -> t(player, "ui.compendium.native.rarity_legendary", "Legendary · 5 sockets");
            default -> t(player, "ui.compendium.native.rarity_other", "Other socket tiers");
        };
    }

    private static String familyLabel(Player player, String family) {
        if ("ALL".equals(family)) return t(player, "ui.compendium.native.all_families", "All");
        return switch (family) {
            case "Weapon" -> t(player, "ui.compendium.native.general_weapons", "General weapons");
            case "Armor" -> t(player, "ui.compendium.native.armor", "Armor");
            case "Other" -> t(player, "ui.compendium.native.other", "Other");
            default -> ResonanceSystem.localizeAppliesTo(family, player);
        };
    }

    private static List<String> familiesIn(Snapshot snapshot) {
        Set<String> present = new LinkedHashSet<>();
        for (Entry entry : snapshot.entries) present.addAll(entry.families);
        List<String> result = new ArrayList<>();
        result.add("ALL");
        for (String family : FAMILY_ORDER) if (present.contains(family)) result.add(family);
        for (String family : present) if (!result.contains(family)) result.add(family);
        return result;
    }

    private static List<String> raritiesIn(Snapshot snapshot, String family) {
        Set<String> present = new LinkedHashSet<>();
        for (Entry entry : snapshot.entries) {
            if (!entry.complete() && familyMatches(entry, family)) present.add(rarityFor(entry));
        }
        List<String> result = new ArrayList<>();
        result.add("ALL");
        for (String rarity : RARITY_ORDER) if (!"ALL".equals(rarity) && present.contains(rarity)) result.add(rarity);
        return result;
    }

    private static List<Entry> incompleteEntries(Snapshot snapshot, String family, String rarity) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : snapshot.entries) {
            if (entry.complete() || !familyMatches(entry, family)) continue;
            if (rarity != null && !"ALL".equals(rarity) && !rarity.equals(rarityFor(entry))) continue;
            result.add(entry);
        }
        return result;
    }

    private static List<Entry> completeEntries(Snapshot snapshot, String family) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : snapshot.entries) if (entry.complete() && familyMatches(entry, family)) result.add(entry);
        return result;
    }

    private static List<Entry> combineCandidates(Snapshot snapshot, Entry base) {
        if (base == null || base.complete()) return List.of();
        List<Entry> candidates = new ArrayList<>();
        boolean[] baseMask = ResonantRecipeUtils.revealMaskFromPattern(base.pattern);
        String normalizedName = ResonantRecipeUtils.normalizeRecipeName(base.name);
        for (Entry candidate : snapshot.entries) {
            if (candidate == null || candidate.complete() || candidate.key.equals(base.key)) continue;
            if (!ResonantRecipeUtils.normalizeRecipeName(candidate.name).equals(normalizedName)) continue;
            boolean[] candidateMask = ResonantRecipeUtils.revealMaskFromPattern(candidate.pattern);
            if (ResonantRecipeUtils.countNewReveals(baseMask, candidateMask) > 0) candidates.add(candidate);
        }
        return candidates;
    }

    private static Entry findEntry(List<Entry> entries, String key) {
        if (entries == null || key == null) return null;
        for (Entry entry : entries) if (entry.key.equals(key)) return entry;
        return null;
    }

    private static String localizeRecipeName(Player player, String rawName) {
        return rawName == null || rawName.isBlank() ? "" : ResonanceSystem.getLocalizedName(rawName, player);
    }

    private static String recipePatternSummary(Entry entry) {
        if (entry == null) return "";
        List<String> tokens = parsePatternTokens(entry.pattern);
        if (tokens.isEmpty()) return "—";
        return String.join("  ·  ", tokens.stream().map(token ->
                token == null || token.isBlank() || token.equalsIgnoreCase("x") ? "?" : titleCase(token)).toList());
    }

    private static List<String> parsePatternTokens(String pattern) {
        List<String> tokens = new ArrayList<>();
        if (pattern == null || pattern.isBlank()) return tokens;
        int cursor = 0;
        while (cursor < pattern.length()) {
            int start = pattern.indexOf('[', cursor);
            if (start < 0) break;
            int end = pattern.indexOf(']', start + 1);
            if (end < 0) break;
            tokens.add(pattern.substring(start + 1, end).trim());
            cursor = end + 1;
        }
        if (tokens.isEmpty()) {
            for (String part : pattern.split(",")) if (!part.isBlank()) tokens.add(part.trim());
        }
        return tokens;
    }

    private static String titleCase(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String encodeKey(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String t(Player player, String key, String fallback) {
        String translated = LangLoader.getUITranslation(player, key);
        return translated == null || translated.isBlank() || translated.equals(key) ? fallback : translated;
    }

    private static String localizedRecipeDescription(Player player, String recipeName) {
        String recipeType = resolveRecipeTypeFromSystem(recipeName);
        boolean isWeapon = recipeType == null || !recipeType.trim().equalsIgnoreCase("Armor");
        String description = ResonanceSystem.buildRecipeDescription(recipeName, isWeapon, player);
        return description == null ? "" : description.trim();
    }

    private static ProcessResult processExtract(Player player, short slot, String encodedKey) {
        String dataKey = decodeKey(encodedKey);
        if (dataKey == null) return new ProcessResult(t(player, "ui.compendium.error_pick_recipe", "Choose a recipe first."));
        CompendiumContext context = resolveCompendium(player, slot);
        if (context == null) return new ProcessResult(t(player, "ui.compendium.error_compendium_moved", "The compendium is no longer in that hotbar slot."));
        Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(context.compendium);
        CompendiumEntry entry = data.get(dataKey);
        if (entry == null) return new ProcessResult(t(player, "ui.compendium.error_recipe_missing", "That recipe is no longer stored."));

        boolean complete = ResonantRecipeUtils.getPatternStats(entry.pattern).isComplete();
        ItemStack recipe = buildRecipeItem(player, entry.name, entry, complete);
        if (!UIInventoryUtils.addItemToInventory(player, recipe)) {
            return new ProcessResult(t(player, "ui.compendium.error_inventory_full", "Your inventory is full."));
        }
        int remaining = Math.max(0, entry.quantity - 1);
        if (remaining == 0) data.remove(dataKey);
        else entry.quantity = remaining;
        saveCompendium(player, context, data);
        if (remaining > 0) {
            String message = LangLoader.getUITranslation(player, "ui.compendium.status_extracted_remaining", remaining);
            return new ProcessResult(message == null || message.isBlank()
                    || message.equals("ui.compendium.status_extracted_remaining")
                    ? "Extracted one shard. Remaining: " + remaining : message);
        }
        return new ProcessResult(t(player, "ui.compendium.status_extracted", "Recipe shard extracted."));
    }

    private static ItemStack buildRecipeItem(Player player, String recipeName, CompendiumEntry entry, boolean complete) {
        String safeName = recipeName == null ? "" : recipeName.trim();
        String displayName = localizeRecipeName(player, safeName);
        String pattern = entry == null || entry.pattern == null ? "" : entry.pattern;
        String usages = entry == null || entry.usages == null ? "" : entry.usages;
        ItemStack recipe = new ItemStack(ResonantRecipeUtils.RECIPE_ITEM_ID, 1)
                .withMetadata(NameResolver.KEY_DISPLAY_NAME, Codec.STRING,
                        LangLoader.getUITranslation(player, "ui.compendium.recipe_display_name", displayName))
                .withMetadata(ResonantRecipeUtils.META_RECIPE_NAME, Codec.STRING, safeName)
                .withMetadata(ResonantRecipeUtils.META_RECIPE_PATTERN, Codec.STRING, pattern);
        String type = resolveRecipeTypeFromSystem(safeName);
        if (type != null && !type.isBlank()) recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_TYPE, Codec.STRING, type);
        if (!usages.isBlank()) recipe = recipe.withMetadata(ResonantRecipeUtils.META_RECIPE_USAGES, Codec.STRING, usages);
        ItemStack ready = complete ? ResonantRecipeUtils.ensureRecipeUsages(recipe) : recipe;
        return DynamicTooltipUtils.applyNativeTooltip(ready, LangLoader.getPlayerLanguage(player));
    }

    private static CompendiumContext resolveCompendium(Player player, short slot) {
        if (player == null || player.getInventory() == null) return null;
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null || slot < 0 || slot >= hotbar.getCapacity()) return null;
        ItemStack compendium = hotbar.getItemStack(slot);
        return ResonantCompendiumUtils.isCompendiumItem(compendium) ? new CompendiumContext(slot, compendium) : null;
    }

    private static void saveCompendium(Player player, CompendiumContext context, Map<String, CompendiumEntry> data) {
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar == null) return;
        hotbar.setItemStackForSlot(context.slot,
                ResonantCompendiumUtils.saveCompendiumData(context.compendium, data));
        if (DynamicTooltipUtils.isAvailable()) DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
    }

    private record CompendiumContext(short slot, ItemStack compendium) {}
    private record ProcessResult(String status) {}

    private static final class NativeCompendiumPage extends InteractiveCustomUIPage<NativeCompendiumPage.PageEvent> {
        private final PlayerRef owner;
        private final short heldSlot;
        private String family = "ALL";
        private String rarity = "ALL";
        private String selectedKey;
        private String combineBaseKey;
        private String combineCandidateKey;
        private String status = "";
        private boolean dismissed;
        private Snapshot snapshotCache;
        private String renderedSelectedKey;
        private String renderedCandidateKey;

        NativeCompendiumPage(PlayerRef owner, short heldSlot) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.owner = owner;
            this.heldSlot = heldSlot;
        }

        @Override
        public void build(Ref<EntityStore> playerRef, UICommandBuilder commands,
                          UIEventBuilder events, Store<EntityStore> store) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            commands.append(PAGE_UI);
            populate(player, commands, events, false);
        }

        @Override
        public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEvent event) {
            super.handleDataEvent(playerRef, store, event);
            if (dismissed || event == null || event.action == null) return;
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            switch (event.action) {
                case "family" -> {
                    family = event.target == null ? "ALL" : event.target;
                    rarity = "ALL";
                    selectedKey = null;
                    combineBaseKey = null;
                    combineCandidateKey = null;
                    refreshFilteredLists(player, true);
                }
                case "rarity" -> {
                    rarity = event.target == null ? "ALL" : event.target;
                    selectedKey = null;
                    combineBaseKey = null;
                    combineCandidateKey = null;
                    refreshFilteredLists(player, false);
                }
                case "recipe_select" -> {
                    selectedKey = event.target;
                    combineBaseKey = null;
                    combineCandidateKey = null;
                    refreshSelection(player);
                }
                case "extract" -> {
                    ProcessResult result = processExtract(player, heldSlot, event.target);
                    status = result.status;
                    snapshotCache = null;
                    selectedKey = event.target;
                    combineBaseKey = null;
                    combineCandidateKey = null;
                    refresh(player);
                }
                case "combine" -> {
                    Snapshot current = currentSnapshot(player);
                    Entry base = findEntry(current.entries, event.target);
                    if (base != null && !base.complete()) {
                        selectedKey = event.target;
                        combineBaseKey = event.target;
                        combineCandidateKey = null;
                        status = "";
                    }
                    refreshRightPane(player);
                }
                case "candidate_select" -> {
                    combineCandidateKey = event.target;
                    refreshSelection(player);
                }
                case "combine_confirm" -> combineSelected(player);
                case "combine_cancel" -> {
                    combineBaseKey = null;
                    combineCandidateKey = null;
                    status = "";
                    refreshRightPane(player);
                }
                case "close" -> player.getPageManager().closePage(playerRef, store);
                default -> { }
            }
        }

        @Override
        public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
            dismissed = true;
            super.onDismiss(playerRef, store);
        }

        private Snapshot currentSnapshot(Player player) {
            if (snapshotCache != null) return snapshotCache;
            CompendiumContext context = resolveCompendium(player, heldSlot);
            snapshotCache = context == null ? new Snapshot(List.of(), 0, 0) : collectSnapshot(context.compendium);
            return snapshotCache;
        }

        private void refresh(Player player) {
            if (dismissed) return;
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            populate(player, commands, events, true);
            sendUpdate(commands, events, false);
        }

        /** Selection only changes highlights, actions, and the detail panel; keep recipe grids intact. */
        private void refreshSelection(Player player) {
            if (dismissed) return;
            Snapshot snapshot = currentSnapshot(player);
            Entry selected = findEntry(snapshot.entries, selectedKey);
            UICommandBuilder commands = new UICommandBuilder();
            updateSelectedRecipeDetails(player, commands, selected);
            List<Entry> shards = incompleteEntries(snapshot, family, rarity);
            updateRecipeCardSelection(commands, "#ShardRecipeList", shards, 5,
                    renderedSelectedKey, false, true, false);
            updateRecipeCardSelection(commands, "#ShardRecipeList", shards, 5,
                    selectedKey, true, true, false);

            List<Entry> rightEntries = combineBaseKey == null
                    ? completeEntries(snapshot, family)
                    : combineCandidates(snapshot, findEntry(snapshot.entries, combineBaseKey));
            boolean candidateList = combineBaseKey != null;
            updateRecipeCardSelection(commands, "#RightRecipeList", rightEntries, 3,
                    candidateList ? renderedCandidateKey : renderedSelectedKey,
                    false, !candidateList, candidateList);
            updateRecipeCardSelection(commands, "#RightRecipeList", rightEntries, 3,
                    candidateList ? combineCandidateKey : selectedKey,
                    true, !candidateList, candidateList);
            commands.set("#CombineSelectedButton.Visible", combineBaseKey != null && combineCandidateKey != null);
            renderedSelectedKey = selectedKey;
            renderedCandidateKey = combineCandidateKey;
            sendUpdate(commands, new UIEventBuilder(), false);
        }

        private void refreshFilteredLists(Player player, boolean rebuildRarities) {
            if (dismissed) return;
            Snapshot snapshot = currentSnapshot(player);
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();

            List<String> families = familiesIn(snapshot);
            for (int i = 0; i < families.size(); i++) {
                commands.set("#FamilyList[" + i + "] #FamilySelected.Visible", families.get(i).equals(family));
            }
            commands.set("#RecipeListTitle.Text", familyLabel(player, family) + " · " + rarityLabel(player, rarity));
            commands.set("#CompendiumStatus.Text", status == null ? "" : status);

            List<String> rarities = raritiesIn(snapshot, family);
            if (!rarities.contains(rarity)) rarity = "ALL";
            if (rebuildRarities) {
                commands.clear("#RarityList");
                for (int i = 0; i < rarities.size(); i++) {
                    String value = rarities.get(i);
                    commands.append("#RarityList", RARITY_CARD_UI);
                    String base = "#RarityList[" + i + "]";
                    commands.set(base + " #RarityButton.Text", rarityLabel(player, value));
                    commands.set(base + " #RaritySelected.Visible", value.equals(rarity));
                    commands.set(base + " #RarityCount.Text", String.valueOf(countRarity(snapshot, family, value)));
                    events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RarityButton",
                            EventData.of("Action", "rarity").append("Target", value));
                }
            } else {
                for (int i = 0; i < rarities.size(); i++) {
                    commands.set("#RarityList[" + i + "] #RaritySelected.Visible", rarities.get(i).equals(rarity));
                }
            }

            commands.clear("#ShardRecipeList");
            commands.clear("#RightRecipeList");
            List<Entry> shards = incompleteEntries(snapshot, family, rarity);
            appendRecipeGrid(player, commands, events, "#ShardRecipeList", shards, 5, true, false);
            commands.set("#ShardEmpty.Text", shards.isEmpty()
                    ? t(player, "ui.compendium.native.empty_shards", "No incomplete recipes in this group.") : "");
            commands.set("#CompletedRecipeDescription.Visible", false);
            commands.set("#CompletedDescriptionText.Text", "");
            commands.set("#RightPaneTitle.Text", t(player, "ui.compendium.native.completed", "Completed Recipes"));
            commands.set("#RightPaneHint.Text", t(player, "ui.compendium.native.completed_hint", "Completed recipes ready to extract."));
            List<Entry> complete = completeEntries(snapshot, family);
            appendRecipeGrid(player, commands, events, "#RightRecipeList", complete, 3, true, false);
            commands.set("#CompletedEmpty.Text", complete.isEmpty()
                    ? t(player, "ui.compendium.native.empty_complete", "No completed recipes in this family yet.") : "");
            commands.set("#CombineSelectedButton.Visible", false);
            commands.set("#CombineCancelButton.Visible", false);
            updateSelectedRecipeDetails(player, commands, null);
            renderedSelectedKey = selectedKey;
            renderedCandidateKey = combineCandidateKey;
            sendUpdate(commands, events, false);
        }

        private void refreshRightPane(Player player) {
            if (dismissed) return;
            Snapshot snapshot = currentSnapshot(player);
            UICommandBuilder commands = new UICommandBuilder();
            UIEventBuilder events = new UIEventBuilder();
            commands.clear("#RightRecipeList");
            commands.set("#CompendiumStatus.Text", status == null ? "" : status);
            updateSelectedRecipeDetails(player, commands, findEntry(snapshot.entries, selectedKey));

            if (combineBaseKey != null) {
                Entry base = findEntry(snapshot.entries, combineBaseKey);
                List<Entry> candidates = combineCandidates(snapshot, base);
                if (!candidates.stream().anyMatch(entry -> entry.key.equals(combineCandidateKey))) combineCandidateKey = null;
                commands.set("#RightPaneTitle.Text", t(player, "ui.compendium.native.candidates", "Combination Candidates"));
                commands.set("#RightPaneHint.Text", t(player, "ui.compendium.native.candidate_hint",
                        "Choose a compatible shard, then confirm the merge."));
                appendRecipeGrid(player, commands, events, "#RightRecipeList", candidates, 3, false, true);
                commands.set("#CompletedEmpty.Text", candidates.isEmpty()
                        ? t(player, "ui.compendium.native.empty_candidates", "No compatible shard can reveal another socket.") : "");
                commands.set("#CombineSelectedButton.Visible", combineCandidateKey != null);
                commands.set("#CombineSelectedButton.Text", t(player, "ui.compendium.native.combine_selected", "COMBINE SELECTED"));
                commands.set("#CombineCancelButton.Visible", true);
                events.addEventBinding(CustomUIEventBindingType.Activating, "#CombineSelectedButton", EventData.of("Action", "combine_confirm"));
                events.addEventBinding(CustomUIEventBindingType.Activating, "#CombineCancelButton", EventData.of("Action", "combine_cancel"));
            } else {
                List<Entry> complete = completeEntries(snapshot, family);
                commands.set("#RightPaneTitle.Text", t(player, "ui.compendium.native.completed", "Completed Recipes"));
                commands.set("#RightPaneHint.Text", t(player, "ui.compendium.native.completed_hint", "Completed recipes ready to extract."));
                appendRecipeGrid(player, commands, events, "#RightRecipeList", complete, 3, true, false);
                commands.set("#CompletedEmpty.Text", complete.isEmpty()
                        ? t(player, "ui.compendium.native.empty_complete", "No completed recipes in this family yet.") : "");
                commands.set("#CombineSelectedButton.Visible", false);
                commands.set("#CombineCancelButton.Visible", false);
            }
            List<Entry> shards = incompleteEntries(snapshot, family, rarity);
            updateRecipeCardSelection(commands, "#ShardRecipeList", shards, 5,
                    renderedSelectedKey, false, true, false);
            updateRecipeCardSelection(commands, "#ShardRecipeList", shards, 5,
                    selectedKey, true, true, false);
            renderedSelectedKey = selectedKey;
            renderedCandidateKey = combineCandidateKey;
            sendUpdate(commands, events, false);
        }

        private void updateSelectedRecipeDetails(Player player, UICommandBuilder commands, Entry selected) {
            boolean showDescription = combineBaseKey == null && selected != null && selected.complete();
            commands.set("#CompletedRecipeDescription.Visible", showDescription);
            commands.set("#CompletedDescriptionText.Text",
                    showDescription ? localizedRecipeDescription(player, selected.name) : "");
            commands.set("#SelectedRecipeName.Text", selected == null
                    ? t(player, "ui.compendium.native.select_recipe", "Select a recipe")
                    : localizeRecipeName(player, selected.name));
            commands.set("#SelectedRecipeDetails.Text", selected == null ? "" :
                    selected.stats.revealedSlots() + "/" + selected.stats.totalSlots() + " sockets revealed   ·   x" + selected.quantity
                            + (selected.complete() ? "   ·   " + formatUsages(player, selected)
                                    : "   ·   " + t(player, "ui.compendium.usages_incomplete", "Incomplete")));
        }

        private void updateRecipeCardSelection(UICommandBuilder commands, String list,
                                               List<Entry> entries, int columns,
                                               String key, boolean selected,
                                               boolean showActions, boolean candidate) {
            if (key == null) return;
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                if (!key.equals(entry.key)) continue;
                int row = index / columns;
                int column = index % columns;
                String base = list + "[" + row + "] #RecipeGridCell" + column + "[0]";
                commands.set(base + " #RecipeSelected.Visible", selected);
                commands.set(base + " #RecipeSelectedFrame.Visible", selected);
                commands.set(base + " #RecipeExtractTool.Visible", showActions && !candidate && selected);
                commands.set(base + " #RecipeCombineTool.Visible", showActions && !candidate && selected && !entry.complete());
                return;
            }
        }

        private void populate(Player player, UICommandBuilder commands, UIEventBuilder events, boolean replace) {
            Snapshot snapshot = currentSnapshot(player);
            if (replace) {
                commands.clear("#FamilyList");
                commands.clear("#RarityList");
                commands.clear("#ShardRecipeList");
                commands.clear("#RightRecipeList");
            }
            commands.set("#CompendiumTitle.Text", t(player, "ui.compendium.native.title", "Resonant Compendium"));
            commands.set("#CompendiumClose.Text", t(player, "ui.compendium.native.close", "CLOSE"));
            String summary = LangLoader.getUITranslation(player, "ui.compendium.summary", snapshot.entries.size(),
                    snapshot.completeCount, Math.max(0, snapshot.entries.size() - snapshot.completeCount), snapshot.totalQuantity);
            commands.set("#CompendiumSummary.Text", summary);
            commands.set("#FamilyHeading.Text", t(player, "ui.compendium.native.families", "WEAPON FAMILIES"));
            commands.set("#RarityHeading.Text", t(player, "ui.compendium.native.rarities", "RECIPE RARITY"));
            commands.set("#RecipeListTitle.Text", familyLabel(player, family) + " · " + rarityLabel(player, rarity));
            commands.set("#RightPaneTitle.Text", t(player, combineBaseKey == null
                    ? "ui.compendium.native.completed" : "ui.compendium.native.candidates",
                    combineBaseKey == null ? "Completed Recipes" : "Combination Candidates"));
            commands.set("#RightPaneHint.Text", combineBaseKey == null
                    ? t(player, "ui.compendium.native.completed_hint", "Completed recipes ready to extract.")
                    : t(player, "ui.compendium.native.candidate_hint", "Choose a compatible shard, then confirm the merge."));
            commands.set("#CompendiumStatus.Text", status == null ? "" : status);
            events.addEventBinding(CustomUIEventBindingType.Activating, "#CompendiumClose", EventData.of("Action", "close"));

            Entry selected = findEntry(snapshot.entries, selectedKey);
            updateSelectedRecipeDetails(player, commands, selected);
            commands.set("#CompletedDescriptionTitle.Text",
                    t(player, "ui.compendium.native.description_heading", "RECIPE DESCRIPTION"));
            List<String> families = familiesIn(snapshot);
            for (int i = 0; i < families.size(); i++) {
                String value = families.get(i);
                commands.append("#FamilyList", FAMILY_CARD_UI);
                String base = "#FamilyList[" + i + "]";
                commands.set(base + " #FamilyButton.Text", familyLabel(player, value));
                commands.set(base + " #FamilySelected.Visible", value.equals(family));
                commands.set(base + " #FamilyCount.Text", String.valueOf(countFamily(snapshot, value)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #FamilyButton",
                        EventData.of("Action", "family").append("Target", value));
            }

            List<String> rarities = raritiesIn(snapshot, family);
            if (!rarities.contains(rarity)) rarity = "ALL";
            for (int i = 0; i < rarities.size(); i++) {
                String value = rarities.get(i);
                commands.append("#RarityList", RARITY_CARD_UI);
                String base = "#RarityList[" + i + "]";
                commands.set(base + " #RarityButton.Text", rarityLabel(player, value));
                commands.set(base + " #RaritySelected.Visible", value.equals(rarity));
                commands.set(base + " #RarityCount.Text", String.valueOf(countRarity(snapshot, family, value)));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RarityButton",
                        EventData.of("Action", "rarity").append("Target", value));
            }

            List<Entry> shards = incompleteEntries(snapshot, family, rarity);
            appendRecipeGrid(player, commands, events, "#ShardRecipeList", shards, 5, true, false);
            if (shards.isEmpty()) commands.set("#ShardEmpty.Text", t(player, "ui.compendium.native.empty_shards", "No incomplete recipes in this group."));
            else commands.set("#ShardEmpty.Text", "");

            if (combineBaseKey != null) {
                Entry base = findEntry(snapshot.entries, combineBaseKey);
                List<Entry> candidates = combineCandidates(snapshot, base);
                if (!candidates.stream().anyMatch(entry -> entry.key.equals(combineCandidateKey))) combineCandidateKey = null;
                appendRecipeGrid(player, commands, events, "#RightRecipeList", candidates, 3, false, true);
                commands.set("#CompletedEmpty.Text", candidates.isEmpty()
                        ? t(player, "ui.compendium.native.empty_candidates", "No compatible shard can reveal another socket.") : "");
                commands.set("#CombineSelectedButton.Visible", combineCandidateKey != null);
                commands.set("#CombineCancelButton.Visible", true);
                commands.set("#CombineSelectedButton.Text", t(player, "ui.compendium.native.combine_selected", "COMBINE SELECTED"));
                events.addEventBinding(CustomUIEventBindingType.Activating, "#CombineSelectedButton", EventData.of("Action", "combine_confirm"));
                events.addEventBinding(CustomUIEventBindingType.Activating, "#CombineCancelButton", EventData.of("Action", "combine_cancel"));
            } else {
                List<Entry> complete = completeEntries(snapshot, family);
                appendRecipeGrid(player, commands, events, "#RightRecipeList", complete, 3, true, false);
                commands.set("#CompletedEmpty.Text", complete.isEmpty()
                        ? t(player, "ui.compendium.native.empty_complete", "No completed recipes in this family yet.") : "");
                commands.set("#CombineSelectedButton.Visible", false);
                commands.set("#CombineCancelButton.Visible", false);
            }
            renderedSelectedKey = selectedKey;
            renderedCandidateKey = combineCandidateKey;
        }

        private void appendRecipeGrid(Player player, UICommandBuilder commands, UIEventBuilder events,
                                      String list, List<Entry> entries, int columns,
                                      boolean showActions, boolean candidate) {
            String rowDocument = columns == 5 ? RECIPE_GRID_ROW_5_UI : RECIPE_GRID_ROW_3_UI;
            for (int index = 0; index < entries.size(); index++) {
                int row = index / columns;
                int column = index % columns;
                if (column == 0) commands.append(list, rowDocument);
                String cell = list + "[" + row + "] #RecipeGridCell" + column;
                commands.append(cell, columns == 5 ? RECIPE_CARD_UI : RECIPE_CARD_SMALL_UI);
                appendRecipeCard(player, commands, events, cell + "[0]", entries.get(index), showActions, candidate, columns == 3);
            }
        }

        private void appendRecipeCard(Player player, UICommandBuilder commands, UIEventBuilder events,
                                      String base, Entry entry, boolean showActions, boolean candidate, boolean compact) {
            boolean selected = entry.key.equals(selectedKey)
                    || (candidate && entry.key.equals(combineCandidateKey));
            commands.set(base + " #RecipeIcon.ItemId", ResonantRecipeUtils.RECIPE_ITEM_ID);
            commands.set(base + " #RecipeName.Text", localizeRecipeName(player, entry.name));
            commands.set(base + " #RecipeMeta.Text", "x" + entry.quantity);
            commands.set(base + " #RecipeSelected.Visible", selected);
            commands.set(base + " #RecipeSelectedFrame.Visible", selected);
            commands.set(base + " #RecipeExtractTool.Visible", showActions && !candidate && selected);
            commands.set(base + " #RecipeCombineTool.Visible", showActions && !candidate && selected && !entry.complete());
            commands.set(base + " #RecipeExtract.TooltipText", t(player, "ui.compendium.native.extract", "Extract recipe"));
            commands.set(base + " #RecipeCombine.TooltipText", t(player, "ui.compendium.native.combine", "Combine recipe"));
            List<String> patternTokens = parsePatternTokens(entry.pattern);
            String patternList = base + " #RecipePatternIconList";
            for (int slot = 0; slot < patternTokens.size(); slot++) {
                commands.append(patternList, compact ? RECIPE_PATTERN_ICON_SMALL_UI : RECIPE_PATTERN_ICON_UI);
                String icon = patternList + "[" + slot + "] #PatternEssence";
                String itemId = recipePatternItemId(patternTokens.get(slot));
                if (itemId != null) {
                    commands.set(icon + ".ItemId", itemId);
                    commands.set(icon + ".Visible", true);
                }
            }
            events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RecipeSelect",
                    EventData.of("Action", candidate ? "candidate_select" : "recipe_select").append("Target", entry.key));
            if (showActions && !candidate && entry.complete()) {
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RecipeExtract",
                        EventData.of("Action", "extract").append("Target", entry.key));
            }
            if (showActions && !candidate && !entry.complete()) {
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RecipeExtract",
                        EventData.of("Action", "extract").append("Target", entry.key));
                events.addEventBinding(CustomUIEventBindingType.Activating, base + " #RecipeCombine",
                        EventData.of("Action", "combine").append("Target", entry.key));
            }
        }

        private String recipePatternItemId(String token) {
            if (token == null) return null;
            return switch (token.trim().toLowerCase(Locale.ROOT)) {
                case "fire" -> "Ingredient_Fire_Essence";
                case "ice" -> "Ingredient_Ice_Essence";
                case "lightning" -> "Ingredient_Lightning_Essence";
                case "life" -> "Ingredient_Life_Essence";
                case "void" -> "Ingredient_Void_Essence";
                case "water" -> "Ingredient_Water_Essence";
                default -> null;
            };
        }

        private int countRarity(Snapshot snapshot, String selectedFamily, String selectedRarity) {
            return incompleteEntries(snapshot, selectedFamily, selectedRarity).size();
        }

        private String formatUsages(Player player, Entry entry) {
            if (entry.usages == null || entry.usages.isBlank()) return ResonantRecipeUtils.DEFAULT_RECIPE_USAGES;
            return entry.usages;
        }

        private void combineSelected(Player player) {
            if (combineBaseKey == null || combineCandidateKey == null) return;
            String baseKey = decodeKey(combineBaseKey);
            String candidateKey = decodeKey(combineCandidateKey);
            if (baseKey == null || candidateKey == null) return;

            CompendiumContext context = resolveCompendium(player, heldSlot);
            if (context == null) {
                status = t(player, "ui.recipe_combine.error_compendium_moved", "The compendium is no longer in that hotbar slot.");
            } else {
                Map<String, CompendiumEntry> data = ResonantCompendiumUtils.getCompendiumData(context.compendium);
                ResonantCompendiumUtils.CombineResult result =
                        ResonantCompendiumUtils.combineEntries(data, baseKey, candidateKey);
                if (!result.succeeded()) {
                    status = combineFailureMessage(player, result, data.get(baseKey));
                } else {
                    saveCompendium(player, context, data);
                    CharacterPathService.awardBenchXp(player, 10, "recipe research");
                    String progress = LangLoader.getUITranslation(player, "ui.recipe_combine.progress_revealed",
                            result.revealedSlots(), result.totalSlots());
                    if (result.revealedSlots() >= result.totalSlots()) {
                        String suffix = LangLoader.getUITranslation(player, "ui.recipe_combine.progress_complete_suffix");
                        if (suffix != null && !suffix.isBlank()
                                && !suffix.equals("ui.recipe_combine.progress_complete_suffix")) {
                            progress += " " + suffix;
                        }
                    }
                    status = LangLoader.getUITranslation(player, "ui.recipe_combine.status_combined",
                            progress, result.gainedSlots());
                }
            }
            combineBaseKey = null;
            combineCandidateKey = null;
            snapshotCache = null;
            refresh(player);
        }

        private String combineFailureMessage(Player player,
                ResonantCompendiumUtils.CombineResult result,
                CompendiumEntry baseEntry) {
            return switch (result.failure()) {
                case ENTRY_MISSING -> t(player, "ui.recipe_combine.error_compendium_missing", "That recipe is no longer stored.");
                case SAME_ENTRY -> t(player, "ui.recipe_combine.error_same_shard", "Choose two different recipe shards.");
                case NAME_MISMATCH -> t(player, "ui.recipe_combine.error_mismatch", "These recipe shards are not compatible.");
                case BASE_COMPLETE -> {
                    String usages = baseEntry == null || baseEntry.usages == null || baseEntry.usages.isBlank()
                            ? ResonantRecipeUtils.DEFAULT_RECIPE_USAGES : baseEntry.usages;
                    yield t(player, "ui.recipe_combine.status_complete_usages", usages);
                }
                case NO_NEW_DATA -> t(player, "ui.recipe_combine.error_no_new_data", "The selected shard reveals no new recipe data.");
                case NONE -> "";
            };
        }

        private static int countFamily(Snapshot snapshot, String family) {
            return (int) snapshot.entries.stream().filter(entry -> familyMatches(entry, family)).count();
        }

        public static final class PageEvent {
            private String action;
            private String target;

            public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value, event -> event.action).add()
                    .append(new KeyedCodec<>("Target", Codec.STRING), (event, value) -> event.target = value, event -> event.target).add()
                    .build();
        }
    }
}
