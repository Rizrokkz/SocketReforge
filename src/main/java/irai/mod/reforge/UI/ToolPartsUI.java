package irai.mod.reforge.UI;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.ItemTypeUtils;
import irai.mod.reforge.Common.ToolAbilityUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIInventoryUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

public final class ToolPartsUI {
    private ToolPartsUI() {}

    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String HYUI_EVENT_BINDING = "com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/ToolPartsBench.html";

    private static final String META_PARTS_TYPE = "SocketReforge.Parts.ProfileType";
    private static final String META_PART1_ID = "SocketReforge.Parts.Part1Id";
    private static final String META_PART2_ID = "SocketReforge.Parts.Part2Id";
    private static final String META_PART3_ID = "SocketReforge.Parts.Part3Id";
    private static final String META_PART1_TIER = "SocketReforge.Parts.Part1Tier";
    private static final String META_PART2_TIER = "SocketReforge.Parts.Part2Tier";
    private static final String META_PART3_TIER = "SocketReforge.Parts.Part3Tier";
    private static final String META_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, SelectionState> pendingSelections = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, String> pendingStatus = new ConcurrentHashMap<>();

    private static boolean hyuiAvailable = false;

    private enum MaterialKind { WOOD, STONE, METAL }
    private enum ContainerKind { HOTBAR, STORAGE }
    private enum ToolKind { PICKAXE, HATCHET, SHOVEL, HOE, SICKLE, SHEARS, MULTITOOL, GENERIC }

    private static final class PartSlot {
        final String labelKey; final MaterialKind kind;
        PartSlot(String labelKey, MaterialKind kind) { this.labelKey = labelKey; this.kind = kind; }
    }

    private static final class ToolProfile {
        final ToolKind kind; final PartSlot slot1; final PartSlot slot2; final PartSlot slot3;
        ToolProfile(ToolKind kind, PartSlot slot1, PartSlot slot2, PartSlot slot3) {
            this.kind = kind; this.slot1 = slot1; this.slot2 = slot2; this.slot3 = slot3;
        }
    }

    private static final class EquipmentEntry {
        final ContainerKind container; final short slot; final String itemId; final String name;
        final String part1Id; final String part2Id; final String part3Id;
        final int part1Tier; final int part2Tier; final int part3Tier;
        EquipmentEntry(ContainerKind container, short slot, String itemId, String name,
                       String part1Id, String part2Id, String part3Id,
                       int part1Tier, int part2Tier, int part3Tier) {
            this.container = container; this.slot = slot; this.itemId = itemId; this.name = name;
            this.part1Id = part1Id; this.part2Id = part2Id; this.part3Id = part3Id;
            this.part1Tier = part1Tier; this.part2Tier = part2Tier; this.part3Tier = part3Tier;
        }
    }

    private static final class MaterialEntry {
        final ContainerKind container; final short slot; final String itemId; final String name;
        final int qty; final MaterialKind kind; final int tier;
        MaterialEntry(ContainerKind container, short slot, String itemId, String name, int qty, MaterialKind kind, int tier) {
            this.container = container; this.slot = slot; this.itemId = itemId; this.name = name;
            this.qty = qty; this.kind = kind; this.tier = tier;
        }
    }

    private static final class Snapshot {
        final List<EquipmentEntry> equipments; final List<MaterialEntry> materials;
        Snapshot(List<EquipmentEntry> equipments, List<MaterialEntry> materials) {
            this.equipments = equipments; this.materials = materials;
        }
    }

    private static final class SelectionState {
        final String equipmentKey; final String s1; final String s2; final String s3;
        SelectionState(String equipmentKey, String s1, String s2, String s3) {
            this.equipmentKey = equipmentKey; this.s1 = s1; this.s2 = s2; this.s3 = s3;
        }
    }

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "ToolPartsUI");
    }

    public static void open(Player player) {
        if (player == null) return;
        if (!hyuiAvailable) {
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.tool_parts.hyui_missing")));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        Snapshot snap = snapshot(player);
        if (snap.equipments.isEmpty()) {
            player.sendMessage(Message.raw("<color=#FF5555>" + LangLoader.getUITranslation(player, "ui.tool_parts.error_no_tools")));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        SelectionState st = pendingSelections.remove(ref);
        if (st == null) st = new SelectionState(equipmentKeyOf(snap.equipments.get(0)), null, null, null);
        String status = pendingStatus.remove(ref);
        if (status == null || status.isBlank()) {
            status = LangLoader.getUITranslation(player, "ui.tool_parts.status_select");
        }
        openPage(player, snap, st, status);
    }

    private static Snapshot snapshot(Player player) {
        List<EquipmentEntry> eq = new ArrayList<>();
        List<MaterialEntry> mats = new ArrayList<>();
        collect(player, player.getInventory().getHotbar(), ContainerKind.HOTBAR, eq, mats);
        collect(player, player.getInventory().getStorage(), ContainerKind.STORAGE, eq, mats);
        return new Snapshot(eq, mats);
    }

    private static void collect(Player player, ItemContainer container, ContainerKind kind, List<EquipmentEntry> eq, List<MaterialEntry> mats) {
        if (container == null) return;
        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack s = container.getItemStack(i);
            if (s == null || s.isEmpty()) continue;
            String id = s.getItemId();
            if (id == null || id.isBlank()) continue;
            String name = UIItemUtils.displayNameOrItemId(s, player);
            if (ItemTypeUtils.isTool(s)) {
                String p1 = s.getFromMetadataOrNull(META_PART1_ID, Codec.STRING);
                String p2 = s.getFromMetadataOrNull(META_PART2_ID, Codec.STRING);
                String p3 = s.getFromMetadataOrNull(META_PART3_ID, Codec.STRING);
                Integer t1 = s.getFromMetadataOrNull(META_PART1_TIER, Codec.INTEGER);
                Integer t2 = s.getFromMetadataOrNull(META_PART2_TIER, Codec.INTEGER);
                Integer t3 = s.getFromMetadataOrNull(META_PART3_TIER, Codec.INTEGER);
                eq.add(new EquipmentEntry(kind, i, id, name, p1, p2, p3,
                        t1 == null ? 0 : t1, t2 == null ? 0 : t2, t3 == null ? 0 : t3));
            }
            MaterialKind mk = kindOf(id);
            if (mk != null) mats.add(new MaterialEntry(kind, i, id, name, s.getQuantity(), mk, tierOf(id, mk)));
        }
    }

    private static ToolProfile profileOf(String itemId) {
        String v = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        if (v.contains("multitool")) return new ToolProfile(ToolKind.MULTITOOL, new PartSlot("ui.tool_parts.slot_handle", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_core", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_tool_head", MaterialKind.METAL));
        if (v.contains("pickaxe")) return new ToolProfile(ToolKind.PICKAXE, new PartSlot("ui.tool_parts.slot_handle", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_binding", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_pick_head", MaterialKind.METAL));
        if (v.contains("hatchet")) return new ToolProfile(ToolKind.HATCHET, new PartSlot("ui.tool_parts.slot_haft", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_wedge", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_hatchet_head", MaterialKind.METAL));
        if (v.contains("shovel")) return new ToolProfile(ToolKind.SHOVEL, new PartSlot("ui.tool_parts.slot_handle", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_collar", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_spade_head", MaterialKind.METAL));
        if (v.contains("hoe")) return new ToolProfile(ToolKind.HOE, new PartSlot("ui.tool_parts.slot_handle", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_brace", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_hoe_head", MaterialKind.METAL));
        if (v.contains("sickle")) return new ToolProfile(ToolKind.SICKLE, new PartSlot("ui.tool_parts.slot_grip", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_rivet", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_blade", MaterialKind.METAL));
        if (v.contains("shears")) return new ToolProfile(ToolKind.SHEARS, new PartSlot("ui.tool_parts.slot_grip", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_pivot", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_blades", MaterialKind.METAL));
        return new ToolProfile(ToolKind.GENERIC, new PartSlot("ui.tool_parts.slot_handle", MaterialKind.WOOD), new PartSlot("ui.tool_parts.slot_core", MaterialKind.STONE), new PartSlot("ui.tool_parts.slot_head", MaterialKind.METAL));
    }

    private static MaterialKind kindOf(String id) {
        String v = id.toLowerCase(Locale.ROOT);
        if (v.startsWith("wood_") || v.contains("_trunk") || v.contains("wood") || v.contains("log")) return MaterialKind.WOOD;
        if (v.startsWith("rock_") || v.contains("rock") || v.contains("stone") || v.contains("gem")) return MaterialKind.STONE;
        if (v.startsWith("ingredient_bar_") || v.startsWith("ore_") || v.contains("bar_") || v.contains("ingot") || v.contains("metal") || v.contains("ore")) return MaterialKind.METAL;
        return null;
    }

    private static int tierOf(String id, MaterialKind kind) {
        String v = id.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case WOOD -> containsAny(v, "redwood", "jungle", "bamboo", "palm") ? 3 : containsAny(v, "beech", "cedar", "fir", "spruce") ? 2 : 1;
            case STONE -> containsAny(v, "gem", "crystal") ? 4 : containsAny(v, "marble", "basalt", "slate", "granite") ? 2 : 1;
            case METAL -> containsAny(v, "adamantite", "onyxium") ? 5 : containsAny(v, "thorium", "mithril") ? 4 : containsAny(v, "silver", "cobalt") ? 3 : containsAny(v, "iron", "bronze") ? 2 : 1;
        };
    }

    private static boolean containsAny(String v, String... terms) { for (String t : terms) if (v.contains(t)) return true; return false; }
    private static void openPage(Player player, Snapshot snap, SelectionState state, String statusText) {
        try {
            Class<?> pb = Class.forName(HYUI_PAGE_BUILDER);
            Class<?> eb = Class.forName(HYUI_EVENT_BINDING);
            Method pageForPlayer = pb.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pb.getMethod("fromHtml", String.class);
            Method addListener = pb.getMethod("addEventListener", String.class, eb, java.util.function.BiConsumer.class);
            Method withLifetime = pb.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pb.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            Object valueChanged = eb.getField("ValueChanged").get(null);
            Object activating = eb.getField("Activating").get(null);
            Object pageBuilder = pageForPlayer.invoke(null, player.getPlayerRef());
            pageBuilder = fromHtml.invoke(pageBuilder, buildHtml(player, snap, state, statusText));

            final Player fp = player;
            addListener.invoke(pageBuilder, "equipmentDropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = fp.getPlayerRef();
                        pendingSelections.put(ref, new SelectionState(extractEventValue(eventObj), null, null, null));
                        pendingStatus.put(ref, LangLoader.getUITranslation(fp, "ui.tool_parts.status_tool_changed"));
                        fp.getWorld().execute(() -> openWithSync(fp));
                    });

            addListener.invoke(pageBuilder, "slot1Dropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = fp.getPlayerRef();
                        SelectionState base = stateFromContext(ctxObj, pendingSelections.get(ref));
                        pendingSelections.put(ref, new SelectionState(base.equipmentKey, extractEventValue(eventObj), base.s2, base.s3));
                        pendingStatus.put(ref, LangLoader.getUITranslation(fp, "ui.tool_parts.status_part_updated"));
                        fp.getWorld().execute(() -> openWithSync(fp));
                    });

            addListener.invoke(pageBuilder, "slot2Dropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = fp.getPlayerRef();
                        SelectionState base = stateFromContext(ctxObj, pendingSelections.get(ref));
                        pendingSelections.put(ref, new SelectionState(base.equipmentKey, base.s1, extractEventValue(eventObj), base.s3));
                        pendingStatus.put(ref, LangLoader.getUITranslation(fp, "ui.tool_parts.status_part_updated"));
                        fp.getWorld().execute(() -> openWithSync(fp));
                    });

            addListener.invoke(pageBuilder, "slot3Dropdown", valueChanged,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = fp.getPlayerRef();
                        SelectionState base = stateFromContext(ctxObj, pendingSelections.get(ref));
                        pendingSelections.put(ref, new SelectionState(base.equipmentKey, base.s1, base.s2, extractEventValue(eventObj)));
                        pendingStatus.put(ref, LangLoader.getUITranslation(fp, "ui.tool_parts.status_part_updated"));
                        fp.getWorld().execute(() -> openWithSync(fp));
                    });

            addListener.invoke(pageBuilder, "applyPartsButton", activating,
                    (java.util.function.BiConsumer<Object, Object>) (eventObj, ctxObj) -> {
                        PlayerRef ref = fp.getPlayerRef();
                        SelectionState current = stateFromContext(ctxObj, pendingSelections.get(ref));
                        String result = applyParts(fp, snapshot(fp), current);
                        pendingSelections.put(ref, current);
                        pendingStatus.put(ref, result);
                        fp.getWorld().execute(() -> openWithSync(fp));
                    });

            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object page = openMethod.invoke(pageBuilder, getStore(player.getPlayerRef()));
            openPages.put(player.getPlayerRef(), page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] ToolPartsUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static SelectionState stateFromContext(Object ctxObj, SelectionState fallback) {
        String eq = pickValue(getCtx(ctxObj, "equipmentDropdown"), fallback == null ? null : fallback.equipmentKey, null);
        String s1 = pickValue(getCtx(ctxObj, "slot1Dropdown"), fallback == null ? null : fallback.s1, null);
        String s2 = pickValue(getCtx(ctxObj, "slot2Dropdown"), fallback == null ? null : fallback.s2, null);
        String s3 = pickValue(getCtx(ctxObj, "slot3Dropdown"), fallback == null ? null : fallback.s3, null);
        return new SelectionState(eq, s1, s2, s3);
    }

    private static String buildHtml(Player player, Snapshot snap, SelectionState state, String statusText) {
        EquipmentEntry eq = resolveEquipment(snap.equipments, state.equipmentKey);
        if (eq == null) eq = snap.equipments.get(0);
        ToolProfile p = profileOf(eq.itemId);

        String s1 = resolveSlotSelectionKey(snap.materials, p.slot1.kind, state.s1, eq.part1Id);
        String s2 = resolveSlotSelectionKey(snap.materials, p.slot2.kind, state.s2, eq.part2Id);
        String s3 = resolveSlotSelectionKey(snap.materials, p.slot3.kind, state.s3, eq.part3Id);

        MaterialEntry m1 = resolveMaterial(snap.materials, s1, p.slot1.kind);
        MaterialEntry m2 = resolveMaterial(snap.materials, s2, p.slot2.kind);
        MaterialEntry m3 = resolveMaterial(snap.materials, s3, p.slot3.kind);

        int t1 = m1 != null ? m1.tier : clampTier(eq.part1Tier);
        int t2 = m2 != null ? m2.tier : clampTier(eq.part2Tier);
        int t3 = m3 != null ? m3.tier : clampTier(eq.part3Tier);

        String tpl = LangLoader.replaceUiTokens(player, loadTemplate());
        return tpl
                .replace("{{equipmentOptions}}", equipmentOptions(snap.equipments, equipmentKeyOf(eq)))
                .replace("{{toolName}}", esc(eq.name))
                .replace("{{toolType}}", esc(toolKindLabel(player, p.kind)))
                .replace("{{slot1Label}}", esc(slotLabel(player, p.slot1)))
                .replace("{{slot2Label}}", esc(slotLabel(player, p.slot2)))
                .replace("{{slot3Label}}", esc(slotLabel(player, p.slot3)))
                .replace("{{slot1Image}}", "hilt.png")
                .replace("{{slot2Image}}", "guard.png")
                .replace("{{slot3Image}}", "blade.png")
                .replace("{{slot1Options}}", materialOptions(player, snap.materials, p.slot1.kind, s1))
                .replace("{{slot2Options}}", materialOptions(player, snap.materials, p.slot2.kind, s2))
                .replace("{{slot3Options}}", materialOptions(player, snap.materials, p.slot3.kind, s3))
                .replace("{{slot1Tier}}", formatTier(player, t1))
                .replace("{{slot2Tier}}", formatTier(player, t2))
                .replace("{{slot3Tier}}", formatTier(player, t3))
                .replace("{{slot1Color}}", tierColor(t1))
                .replace("{{slot2Color}}", tierColor(t2))
                .replace("{{slot3Color}}", tierColor(t3))
                .replace("{{statusText}}", esc(statusText));
    }

    private static String resolveSlotSelectionKey(List<MaterialEntry> mats, MaterialKind kind, String selectedKey, String existingItemId) {
        MaterialEntry selected = resolveMaterial(mats, selectedKey, kind);
        if (selected != null) return materialKeyOf(selected);
        MaterialEntry byId = findByItemId(mats, kind, existingItemId);
        return byId == null ? null : materialKeyOf(byId);
    }

    private static MaterialEntry findByItemId(List<MaterialEntry> mats, MaterialKind kind, String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        for (MaterialEntry m : mats) if (m.kind == kind && itemId.equalsIgnoreCase(m.itemId)) return m;
        return null;
    }

    private static String equipmentOptions(List<EquipmentEntry> entries, String selected) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            EquipmentEntry e = entries.get(i);
            String key = equipmentKeyOf(e);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selected) || (selected == null && i == 0)) sb.append(" selected=\"true\"");
            sb.append(">").append(esc(e.name)).append("</option>");
        }
        return sb.toString();
    }

    private static String materialOptions(Player player, List<MaterialEntry> entries, MaterialKind kind, String selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("<option value=\"\"").append(selected == null || selected.isBlank() ? " selected=\"true\"" : "")
                .append(">").append(esc(t(player, "ui.tool_parts.select_material", materialKindLabel(player, kind)))).append("</option>");
        for (MaterialEntry e : entries) {
            if (e.kind != kind) continue;
            String key = materialKeyOf(e);
            sb.append("<option value=\"").append(key).append("\"");
            if (key.equals(selected)) sb.append(" selected=\"true\"");
            sb.append(">").append(esc(e.name)).append(" [T").append(e.tier).append("] x").append(e.qty).append("</option>");
        }
        return sb.toString();
    }

    private static String tierColor(int t) {
        return switch (t) {
            case 1 -> "#6B7280";
            case 2 -> "#22C55E";
            case 3 -> "#3B82F6";
            case 4 -> "#A855F7";
            case 5 -> "#F59E0B";
            default -> "#374151";
        };
    }

    private static int clampTier(int tier) { return Math.max(0, Math.min(5, tier)); }
    private static String applyParts(Player player, Snapshot snap, SelectionState state) {
        EquipmentEntry eq = resolveEquipment(snap.equipments, state.equipmentKey);
        if (eq == null) return t(player, "ui.tool_parts.error_select_tool");
        ToolProfile p = profileOf(eq.itemId);

        MaterialEntry m1 = resolveMaterial(snap.materials, state.s1, p.slot1.kind);
        MaterialEntry m2 = resolveMaterial(snap.materials, state.s2, p.slot2.kind);
        MaterialEntry m3 = resolveMaterial(snap.materials, state.s3, p.slot3.kind);
        if (m1 == null) m1 = firstMaterialOfKind(snap.materials, p.slot1.kind);
        if (m2 == null) m2 = firstMaterialOfKind(snap.materials, p.slot2.kind);
        if (m3 == null) m3 = firstMaterialOfKind(snap.materials, p.slot3.kind);
        if (m1 == null || m2 == null || m3 == null) {
            return t(player, "ui.tool_parts.error_missing_materials",
                    materialKindLabel(player, p.slot1.kind),
                    materialKindLabel(player, p.slot2.kind),
                    materialKindLabel(player, p.slot3.kind));
        }

        ItemStack current = readTool(player, eq);
        if (current == null || current.isEmpty() || !ItemTypeUtils.isTool(current)) return t(player, "ui.tool_parts.error_tool_changed");
        if (!hasMaterial(player, m1, 1) || !hasMaterial(player, m2, 1) || !hasMaterial(player, m3, 1)) return t(player, "ui.tool_parts.error_material_changed");

        consume(player, m1, 1);
        consume(player, m2, 1);
        consume(player, m3, 1);

        ItemStack updated = current
                .withMetadata(META_PARTS_TYPE, Codec.STRING, p.kind.name())
                .withMetadata(META_PART1_ID, Codec.STRING, m1.itemId)
                .withMetadata(META_PART2_ID, Codec.STRING, m2.itemId)
                .withMetadata(META_PART3_ID, Codec.STRING, m3.itemId)
                .withMetadata(META_PART1_TIER, Codec.INTEGER, m1.tier)
                .withMetadata(META_PART2_TIER, Codec.INTEGER, m2.tier)
                .withMetadata(META_PART3_TIER, Codec.INTEGER, m3.tier);
        updated = ToolAbilityUtils.applyAbilityMetadata(updated, p.kind.name(), m1.tier, m2.tier, m3.tier);
        ToolAbilityUtils.HatchetThrowStats partStats = ToolAbilityUtils.getHatchetThrowStats(updated);
        updated = updated.withMetadata(META_DAMAGE_MULTIPLIER, Codec.DOUBLE, partStats.breakPowerMultiplier);
        writeTool(player, eq, updated);
        DynamicTooltipUtils.refreshAllPlayers();

        String extra = ToolAbilityUtils.describeHatchetThrowStatus(p.kind.name(), m1.tier, m2.tier, m3.tier);
        if (extra != null && !extra.isBlank() && !extra.startsWith(" ")) {
            extra = " " + extra;
        }
        String savePercent = String.format(Locale.ROOT, "%.0f", partStats.durabilitySaveChance * 100.0d);
        return t(player, "ui.tool_parts.status_applied",
                eq.name,
                m1.itemId, m1.tier,
                m2.itemId, m2.tier,
                m3.itemId, m3.tier,
                String.format(Locale.ROOT, "%.2f", partStats.swingSpeedMultiplier),
                savePercent,
                String.format(Locale.ROOT, "%.2f", partStats.breakPowerMultiplier),
                extra == null ? "" : extra);
    }

    private static EquipmentEntry resolveEquipment(List<EquipmentEntry> entries, String key) {
        if (entries.isEmpty()) return null;
        if (key == null || key.isBlank()) return entries.get(0);
        for (EquipmentEntry e : entries) if (key.equals(equipmentKeyOf(e))) return e;
        Integer idx = tryParseInt(key);
        return (idx != null && idx >= 0 && idx < entries.size()) ? entries.get(idx) : null;
    }

    private static MaterialEntry resolveMaterial(List<MaterialEntry> entries, String key, MaterialKind expected) {
        if (key == null || key.isBlank()) return null;
        for (MaterialEntry e : entries) {
            if (!key.equals(materialKeyOf(e))) continue;
            return e.kind == expected ? e : null;
        }
        Integer idx = tryParseInt(key);
        if (idx == null) return null;
        if (idx > 0) {
            int visible = 0;
            for (MaterialEntry e : entries) {
                if (e.kind != expected) continue;
                visible++;
                if (visible == idx) return e;
            }
        }
        if (idx >= 0 && idx < entries.size()) {
            MaterialEntry e = entries.get(idx);
            return e.kind == expected ? e : null;
        }
        return null;
    }

    private static Integer tryParseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static MaterialEntry firstMaterialOfKind(List<MaterialEntry> entries, MaterialKind kind) {
        for (MaterialEntry e : entries) if (e.kind == kind && e.qty > 0) return e;
        return null;
    }

    private static ItemContainer container(Player p, ContainerKind kind) {
        return UIInventoryUtils.getContainer(p, kind == ContainerKind.HOTBAR);
    }

    private static ItemStack readTool(Player p, EquipmentEntry eq) {
        return UIInventoryUtils.readItem(p, eq.container == ContainerKind.HOTBAR, eq.slot);
    }

    private static void writeTool(Player p, EquipmentEntry eq, ItemStack s) {
        UIInventoryUtils.writeItem(p, eq.container == ContainerKind.HOTBAR, eq.slot, s);
    }

    private static boolean hasMaterial(Player p, MaterialEntry m, int amount) {
        return UIInventoryUtils.hasItemAmount(p, m.container == ContainerKind.HOTBAR, m.slot, m.itemId, amount);
    }

    private static boolean consume(Player p, MaterialEntry m, int amount) {
        return UIInventoryUtils.consumeItem(p, m.container == ContainerKind.HOTBAR, m.slot, m.itemId, amount);
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                ToolPartsUI.class,
                TEMPLATE_PATH,
                "<div><p>Tool parts UI template missing.</p></div>",
                "ToolPartsUI");
    }

    private static String extractEventValue(Object eventObj) {
        return HyUIReflectionUtils.extractEventValue(eventObj);
    }

    private static String getCtx(Object ctxObj, String id) { return getContextValue(ctxObj, id, "#" + id + ".value"); }

    private static String getContextValue(Object ctxObj, String... keys) {
        return HyUIReflectionUtils.getContextValue(ctxObj, keys);
    }

    private static String pickValue(String preferred, String fallback, String defaultValue) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return defaultValue;
    }

    private static String equipmentKeyOf(EquipmentEntry e) { return e == null ? null : e.container + "|" + e.slot + "|" + e.itemId; }
    private static String materialKeyOf(MaterialEntry e) { return e == null ? null : e.container + "|" + e.slot + "|" + e.itemId; }

    private static Object getStore(PlayerRef ref) throws Exception {
        return HyUIReflectionUtils.getStore(ref);
    }

    private static void closePageIfOpen(PlayerRef ref) {
        HyUIReflectionUtils.closePageIfOpen(openPages, ref);
    }

    private static String esc(String t) {
        return UITemplateUtils.escapeHtml(t);
    }

    private static String t(Player player, String key, Object... params) {
        return LangLoader.getUITranslation(player, key, params);
    }

    private static String toolKindLabel(Player player, ToolKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case PICKAXE -> t(player, "ui.tool_parts.tool_kind_pickaxe");
            case HATCHET -> t(player, "ui.tool_parts.tool_kind_hatchet");
            case SHOVEL -> t(player, "ui.tool_parts.tool_kind_shovel");
            case HOE -> t(player, "ui.tool_parts.tool_kind_hoe");
            case SICKLE -> t(player, "ui.tool_parts.tool_kind_sickle");
            case SHEARS -> t(player, "ui.tool_parts.tool_kind_shears");
            case MULTITOOL -> t(player, "ui.tool_parts.tool_kind_multitool");
            case GENERIC -> t(player, "ui.tool_parts.tool_kind_generic");
        };
    }

    private static String slotLabel(Player player, PartSlot slot) {
        if (slot == null || slot.labelKey == null) {
            return "";
        }
        return t(player, slot.labelKey);
    }

    private static String materialKindLabel(Player player, MaterialKind kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case WOOD -> t(player, "ui.tool_parts.material_kind_wood");
            case STONE -> t(player, "ui.tool_parts.material_kind_stone");
            case METAL -> t(player, "ui.tool_parts.material_kind_metal");
        };
    }

    private static String formatTier(Player player, int tier) {
        if (tier <= 0) {
            return t(player, "ui.tool_parts.tier_none");
        }
        return t(player, "ui.tool_parts.tier_format", tier);
    }
}
