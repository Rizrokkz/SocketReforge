package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Common.ItemTypeUtils;
import irai.mod.reforge.Common.ToolAbilityUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

/** Native tool parts crafting page. */
public final class ToolPartsUI {
    private static final String PAGE_UI = "Pages/ToolPartsBench.ui";
    private static final String META_PARTS_TYPE = "SocketReforge.Parts.ProfileType";
    private static final String META_PART1_ID = "SocketReforge.Parts.Part1Id";
    private static final String META_PART2_ID = "SocketReforge.Parts.Part2Id";
    private static final String META_PART3_ID = "SocketReforge.Parts.Part3Id";
    private static final String META_PART1_TIER = "SocketReforge.Parts.Part1Tier";
    private static final String META_PART2_TIER = "SocketReforge.Parts.Part2Tier";
    private static final String META_PART3_TIER = "SocketReforge.Parts.Part3Tier";
    private static final String META_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";

    private ToolPartsUI() {}

    private enum Kind { WOOD, STONE, METAL }
    private enum ContainerKind { HOTBAR, STORAGE }
    private enum ToolKind { PICKAXE, HATCHET, SHOVEL, HOE, SICKLE, SHEARS, MULTITOOL, GENERIC }

    private record PartSlot(String labelKey, Kind kind) {}
    private record ToolProfile(ToolKind kind, PartSlot first, PartSlot second, PartSlot third) {}
    private record ToolEntry(ContainerKind container, short slot, String itemId, String name,
                             String part1Id, String part2Id, String part3Id,
                             int part1Tier, int part2Tier, int part3Tier) {}
    private record MaterialEntry(ContainerKind container, short slot, String itemId, String name,
                                 int quantity, Kind kind, int tier) {}
    private record Snapshot(List<ToolEntry> tools, List<MaterialEntry> materials) {}

    public static void open(Player player) {
        if (player == null || player.getPlayerRef() == null || player.getWorld() == null) return;
        player.getWorld().execute(() -> {
            Snapshot snapshot = snapshot(player);
            if (snapshot.tools().isEmpty()) {
                player.getPlayerRef().sendMessage(Message.raw(t(player, "ui.tool_parts.error_no_tools")));
                return;
            }
            PlayerRef owner = player.getPlayerRef();
            Ref<EntityStore> ref = owner.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            if (store != null) player.getPageManager().openCustomPage(ref, store,
                    new ToolPartsPage(owner, toolKey(snapshot.tools().get(0)), "", "", "",
                            t(player, "ui.tool_parts.status_select")));
        });
    }

    private static Snapshot snapshot(Player player) {
        List<ToolEntry> tools = new ArrayList<>();
        List<MaterialEntry> materials = new ArrayList<>();
        collect(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, tools, materials);
        collect(player, player.getInventory().getStorage(), ContainerKind.STORAGE, tools, materials);
        return new Snapshot(tools, materials);
    }

    private static void collect(Player player, ItemContainer container, ContainerKind kind,
                                List<ToolEntry> tools, List<MaterialEntry> materials) {
        if (container == null) return;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty() || item.getItemId() == null) continue;
            String id = item.getItemId();
            String name = UIItemUtils.displayNameOrItemId(item, player);
            if (ItemTypeUtils.isTool(item)) {
                Integer tier1 = item.getFromMetadataOrNull(META_PART1_TIER, Codec.INTEGER);
                Integer tier2 = item.getFromMetadataOrNull(META_PART2_TIER, Codec.INTEGER);
                Integer tier3 = item.getFromMetadataOrNull(META_PART3_TIER, Codec.INTEGER);
                tools.add(new ToolEntry(kind, slot, id, name,
                        item.getFromMetadataOrNull(META_PART1_ID, Codec.STRING),
                        item.getFromMetadataOrNull(META_PART2_ID, Codec.STRING),
                        item.getFromMetadataOrNull(META_PART3_ID, Codec.STRING),
                        tier1 == null ? 0 : tier1, tier2 == null ? 0 : tier2, tier3 == null ? 0 : tier3));
            }
            Kind materialKind = kindOf(id);
            if (materialKind != null) materials.add(new MaterialEntry(kind, slot, id, name,
                    item.getQuantity(), materialKind, tierOf(id, materialKind)));
        }
    }

    private static Kind kindOf(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if (value.startsWith("wood_") || value.contains("_trunk") || value.contains("wood") || value.contains("log")) return Kind.WOOD;
        if (value.startsWith("rock_") || value.contains("rock") || value.contains("stone") || value.contains("gem")) return Kind.STONE;
        if (value.startsWith("ingredient_bar_") || value.startsWith("ore_") || value.contains("bar_")
                || value.contains("ingot") || value.contains("metal") || value.contains("ore")) return Kind.METAL;
        return null;
    }

    private static int tierOf(String id, Kind kind) {
        String value = id.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case WOOD -> containsAny(value, "redwood", "jungle", "bamboo", "palm") ? 3
                    : containsAny(value, "beech", "cedar", "fir", "spruce") ? 2 : 1;
            case STONE -> containsAny(value, "gem", "crystal") ? 4
                    : containsAny(value, "marble", "basalt", "slate", "granite") ? 2 : 1;
            case METAL -> containsAny(value, "adamantite", "onyxium") ? 5
                    : containsAny(value, "thorium", "mithril") ? 4
                    : containsAny(value, "silver", "cobalt") ? 3
                    : containsAny(value, "iron", "bronze") ? 2 : 1;
        };
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private static ToolProfile profileOf(String itemId) {
        String value = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        if (value.contains("multitool")) return profile(ToolKind.MULTITOOL, "handle", "core", "tool_head");
        if (value.contains("pickaxe")) return profile(ToolKind.PICKAXE, "handle", "binding", "pick_head");
        if (value.contains("hatchet")) return profile(ToolKind.HATCHET, "haft", "wedge", "hatchet_head");
        if (value.contains("shovel")) return profile(ToolKind.SHOVEL, "handle", "collar", "spade_head");
        if (value.contains("hoe")) return profile(ToolKind.HOE, "handle", "brace", "hoe_head");
        if (value.contains("sickle")) return profile(ToolKind.SICKLE, "grip", "rivet", "blade");
        if (value.contains("shears")) return profile(ToolKind.SHEARS, "grip", "pivot", "blades");
        return profile(ToolKind.GENERIC, "handle", "core", "head");
    }

    private static ToolProfile profile(ToolKind kind, String first, String second, String third) {
        return new ToolProfile(kind, new PartSlot("ui.tool_parts.slot_" + first, Kind.WOOD),
                new PartSlot("ui.tool_parts.slot_" + second, Kind.STONE),
                new PartSlot("ui.tool_parts.slot_" + third, Kind.METAL));
    }

    private static String applyParts(Player player, Snapshot snapshot, String toolKey,
                                     String firstKey, String secondKey, String thirdKey) {
        ToolEntry tool = resolveTool(snapshot.tools(), toolKey);
        if (tool == null) return t(player, "ui.tool_parts.error_select_tool");
        ToolProfile profile = profileOf(tool.itemId());
        MaterialEntry first = resolveMaterial(snapshot.materials(), firstKey, profile.first().kind());
        MaterialEntry second = resolveMaterial(snapshot.materials(), secondKey, profile.second().kind());
        MaterialEntry third = resolveMaterial(snapshot.materials(), thirdKey, profile.third().kind());
        if (first == null) first = firstMaterial(snapshot.materials(), profile.first().kind());
        if (second == null) second = firstMaterial(snapshot.materials(), profile.second().kind());
        if (third == null) third = firstMaterial(snapshot.materials(), profile.third().kind());
        if (first == null || second == null || third == null) return t(player, "ui.tool_parts.error_missing_materials",
                materialKindLabel(player, profile.first().kind()), materialKindLabel(player, profile.second().kind()),
                materialKindLabel(player, profile.third().kind()));

        ItemStack current = UIInventoryUtils.readItem(player, tool.container() == ContainerKind.HOTBAR, tool.slot());
        if (current == null || current.isEmpty() || !ItemTypeUtils.isTool(current)) return t(player, "ui.tool_parts.error_tool_changed");
        if (!hasMaterial(player, first) || !hasMaterial(player, second) || !hasMaterial(player, third)) {
            return t(player, "ui.tool_parts.error_material_changed");
        }
        consume(player, first);
        consume(player, second);
        consume(player, third);

        ItemStack updated = current.withMetadata(META_PARTS_TYPE, Codec.STRING, profile.kind().name())
                .withMetadata(META_PART1_ID, Codec.STRING, first.itemId())
                .withMetadata(META_PART2_ID, Codec.STRING, second.itemId())
                .withMetadata(META_PART3_ID, Codec.STRING, third.itemId())
                .withMetadata(META_PART1_TIER, Codec.INTEGER, first.tier())
                .withMetadata(META_PART2_TIER, Codec.INTEGER, second.tier())
                .withMetadata(META_PART3_TIER, Codec.INTEGER, third.tier());
        updated = ToolAbilityUtils.applyAbilityMetadata(updated, profile.kind().name(), first.tier(), second.tier(), third.tier());
        ToolAbilityUtils.HatchetThrowStats stats = ToolAbilityUtils.getHatchetThrowStats(updated);
        updated = updated.withMetadata(META_DAMAGE_MULTIPLIER, Codec.DOUBLE, stats.breakPowerMultiplier);
        UIInventoryUtils.writeItem(player, tool.container() == ContainerKind.HOTBAR, tool.slot(), updated);
        DynamicTooltipUtils.refreshPlayerTooltips(player.getPlayerRef());
        CharacterPathService.awardBenchXp(player, 8, "tool crafting");
        String extra = ToolAbilityUtils.describeHatchetThrowStatus(profile.kind().name(), first.tier(), second.tier(), third.tier());
        if (extra != null && !extra.isBlank() && !extra.startsWith(" ")) extra = " " + extra;
        return t(player, "ui.tool_parts.status_applied", tool.name(), first.itemId(), first.tier(),
                second.itemId(), second.tier(), third.itemId(), third.tier(),
                String.format(Locale.ROOT, "%.2f", stats.swingSpeedMultiplier),
                String.format(Locale.ROOT, "%.0f", stats.durabilitySaveChance * 100.0d),
                String.format(Locale.ROOT, "%.2f", stats.breakPowerMultiplier), extra == null ? "" : extra);
    }

    private static List<DropdownEntryInfo> toolOptions(List<ToolEntry> tools) {
        List<DropdownEntryInfo> options = new ArrayList<>(tools.size());
        for (ToolEntry tool : tools) options.add(new DropdownEntryInfo(LocalizableString.fromString(tool.name()), toolKey(tool)));
        return options;
    }

    private static List<DropdownEntryInfo> materialOptions(Player player, List<MaterialEntry> materials, Kind kind) {
        List<DropdownEntryInfo> options = new ArrayList<>();
        options.add(new DropdownEntryInfo(LocalizableString.fromString(t(player, "ui.tool_parts.select_material", materialKindLabel(player, kind))), ""));
        for (MaterialEntry material : materials) if (material.kind() == kind) {
            options.add(new DropdownEntryInfo(LocalizableString.fromString(
                    material.name() + " [T" + material.tier() + "] x" + material.quantity()), materialKey(material)));
        }
        return options;
    }

    private static String toolKey(ToolEntry tool) { return tool.container() + "|" + tool.slot() + "|" + tool.itemId(); }
    private static String materialKey(MaterialEntry material) { return material.container() + "|" + material.slot() + "|" + material.itemId(); }

    private static ToolEntry resolveTool(List<ToolEntry> tools, String key) {
        for (ToolEntry tool : tools) if (toolKey(tool).equals(key)) return tool;
        return tools.isEmpty() ? null : tools.get(0);
    }

    private static MaterialEntry resolveMaterial(List<MaterialEntry> materials, String key, Kind kind) {
        if (key == null || key.isBlank()) return null;
        for (MaterialEntry material : materials) if (material.kind() == kind && materialKey(material).equals(key)) return material;
        return null;
    }

    private static MaterialEntry firstMaterial(List<MaterialEntry> materials, Kind kind) {
        for (MaterialEntry material : materials) if (material.kind() == kind && material.quantity() > 0) return material;
        return null;
    }

    private static boolean hasMaterial(Player player, MaterialEntry material) {
        return UIInventoryUtils.hasItemAmount(player, material.container() == ContainerKind.HOTBAR,
                material.slot(), material.itemId(), 1);
    }

    private static boolean consume(Player player, MaterialEntry material) {
        return UIInventoryUtils.consumeItem(player, material.container() == ContainerKind.HOTBAR,
                material.slot(), material.itemId(), 1);
    }

    private static String label(Player player, PartSlot part) { return t(player, part.labelKey()); }
    private static String materialKindLabel(Player player, Kind kind) {
        return t(player, "ui.tool_parts.material_kind_" + kind.name().toLowerCase(Locale.ROOT));
    }
    private static String t(Player player, String key, Object... args) { return LangLoader.getUITranslation(player, key, args); }

    private static final class ToolPartsPage extends InteractiveCustomUIPage<ToolPartsPage.PageEvent> {
        private String toolKey;
        private String firstKey;
        private String secondKey;
        private String thirdKey;
        private String status;

        private ToolPartsPage(PlayerRef owner, String toolKey, String firstKey, String secondKey,
                              String thirdKey, String status) {
            super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
            this.toolKey = toolKey;
            this.firstKey = firstKey;
            this.secondKey = secondKey;
            this.thirdKey = thirdKey;
            this.status = status;
        }

        @Override
        public void build(Ref<EntityStore> playerRef, UICommandBuilder commands,
                          UIEventBuilder events, Store<EntityStore> store) {
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            commands.append(PAGE_UI);
            populate(player, commands);
            bindEvents(events);
        }

        private void populate(Player player, UICommandBuilder commands) {
            Snapshot snapshot = snapshot(player);
            if (snapshot.tools().isEmpty()) return;
            ToolEntry tool = resolveTool(snapshot.tools(), toolKey);
            if (tool == null) return;
            toolKey = toolKey(tool);
            ToolProfile profile = profileOf(tool.itemId());
            firstKey = normalizeSelection(snapshot, profile.first(), firstKey, tool.part1Id());
            secondKey = normalizeSelection(snapshot, profile.second(), secondKey, tool.part2Id());
            thirdKey = normalizeSelection(snapshot, profile.third(), thirdKey, tool.part3Id());
            commands.set("#ToolPartsTitle.Text", t(player, "ui.tool_parts.title"));
            commands.set("#ToolPartsDescription.Text", t(player, "ui.tool_parts.label_tool") + ": " + tool.name());
            commands.set("#ToolPartsType.Text", t(player, "ui.tool_parts.label_type") + ": "
                    + t(player, "ui.tool_parts.tool_kind_" + profile.kind().name().toLowerCase(Locale.ROOT)));
            commands.set("#ToolPartsEquipment.Entries", toolOptions(snapshot.tools()));
            commands.set("#ToolPartsEquipment.Value", toolKey);
            setMaterialControl(commands, player, snapshot, "#ToolPartsFirst", profile.first(), firstKey, tool.part1Tier());
            setMaterialControl(commands, player, snapshot, "#ToolPartsSecond", profile.second(), secondKey, tool.part2Tier());
            setMaterialControl(commands, player, snapshot, "#ToolPartsThird", profile.third(), thirdKey, tool.part3Tier());
            commands.set("#ToolPartsStatus.Text", status == null ? "" : status);
            commands.set("#ToolPartsNote.Text", t(player, "ui.tool_parts.note_consume"));
            commands.set("#ToolPartsApply.Text", t(player, "ui.tool_parts.button_apply"));
        }

        private void bindEvents(UIEventBuilder events) {
            bindSelection(events, "#ToolPartsEquipment", "equipment");
            bindSelection(events, "#ToolPartsFirst", "first");
            bindSelection(events, "#ToolPartsSecond", "second");
            bindSelection(events, "#ToolPartsThird", "third");
            events.addEventBinding(CustomUIEventBindingType.Activating, "#ToolPartsApply",
                    EventData.of("Action", "apply").append("@Tool", "#ToolPartsEquipment.Value")
                            .append("@First", "#ToolPartsFirst.Value").append("@Second", "#ToolPartsSecond.Value")
                            .append("@Third", "#ToolPartsThird.Value"));
        }

        private void setMaterialControl(UICommandBuilder commands, Player player, Snapshot snapshot,
                                        String selector, PartSlot slot, String value, int equippedTier) {
            commands.set(selector + "Label.Text", label(player, slot));
            commands.set(selector + ".Entries", materialOptions(player, snapshot.materials(), slot.kind()));
            commands.set(selector + ".Value", value == null ? "" : value);
            MaterialEntry selected = resolveMaterial(snapshot.materials(), value, slot.kind());
            int tier = selected == null ? Math.max(0, Math.min(5, equippedTier)) : selected.tier();
            commands.set(selector + "Tier.Text", tier <= 0
                    ? t(player, "ui.tool_parts.tier_none")
                    : t(player, "ui.tool_parts.tier_format", tier));
        }

        private String normalizeSelection(Snapshot snapshot, PartSlot slot, String selected, String existingId) {
            MaterialEntry entry = resolveMaterial(snapshot.materials(), selected, slot.kind());
            if (entry == null) entry = materialById(snapshot.materials(), slot.kind(), existingId);
            if (entry == null) entry = firstMaterial(snapshot.materials(), slot.kind());
            return entry == null ? "" : materialKey(entry);
        }

        private void bindSelection(UIEventBuilder events, String selector, String field) {
            EventData data = EventData.of("Action", "select").append("Field", field)
                    .append("@Tool", "#ToolPartsEquipment.Value")
                    .append("@First", "#ToolPartsFirst.Value").append("@Second", "#ToolPartsSecond.Value")
                    .append("@Third", "#ToolPartsThird.Value");
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false);
        }

        @Override
        public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEvent event) {
            super.handleDataEvent(playerRef, store, event);
            if (event == null) return;
            Player player = store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;
            if ("equipment".equals(event.field)) {
                toolKey = event.tool;
                firstKey = secondKey = thirdKey = "";
                status = t(player, "ui.tool_parts.status_tool_changed");
            } else if ("first".equals(event.field)) {
                firstKey = event.first;
                status = t(player, "ui.tool_parts.status_part_updated");
            } else if ("second".equals(event.field)) {
                secondKey = event.second;
                status = t(player, "ui.tool_parts.status_part_updated");
            } else if ("third".equals(event.field)) {
                thirdKey = event.third;
                status = t(player, "ui.tool_parts.status_part_updated");
            } else if ("apply".equals(event.action)) {
                toolKey = event.tool;
                firstKey = event.first;
                secondKey = event.second;
                thirdKey = event.third;
                status = applyParts(player, snapshot(player), toolKey, firstKey, secondKey, thirdKey);
            } else return;
            UICommandBuilder commands = new UICommandBuilder();
            populate(player, commands);
            sendUpdate(commands, false);
        }

        public static final class PageEvent {
            private String action;
            private String field;
            private String tool;
            private String first;
            private String second;
            private String third;
            private static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                    .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action).add()
                    .append(new KeyedCodec<>("Field", Codec.STRING), (e, v) -> e.field = v, e -> e.field).add()
                    .append(new KeyedCodec<>("Tool", Codec.STRING), (e, v) -> e.tool = v, e -> e.tool).add()
                    .append(new KeyedCodec<>("First", Codec.STRING), (e, v) -> e.first = v, e -> e.first).add()
                    .append(new KeyedCodec<>("Second", Codec.STRING), (e, v) -> e.second = v, e -> e.second).add()
                    .append(new KeyedCodec<>("Third", Codec.STRING), (e, v) -> e.third = v, e -> e.third).add()
                    .build();
        }
    }

    private static MaterialEntry materialById(List<MaterialEntry> materials, Kind kind, String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        for (MaterialEntry material : materials) if (material.kind() == kind && itemId.equalsIgnoreCase(material.itemId())) return material;
        return null;
    }
}
