package irai.mod.reforge.Progression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import irai.mod.reforge.Progression.CharacterPathService.Bonus;
import irai.mod.reforge.Progression.CharacterPathService.Category;
import irai.mod.reforge.Progression.CharacterPathService.GraphEdge;
import irai.mod.reforge.Progression.CharacterPathService.NodeBonus;
import irai.mod.reforge.Progression.CharacterPathService.NodeDefinition;
import irai.mod.reforge.Progression.CharacterPathService.WeaponClass;

/** Persistent authoring draft for the character path editor. */
public final class CharacterPathEditorService {
    public static final int CANVAS_WIDTH = 2400;
    public static final int CANVAS_HEIGHT = 1600;
    public static final int MIN_CANVAS_WIDTH = 600;
    public static final int MIN_CANVAS_HEIGHT = 600;
    public static final int CANVAS_X_OFFSET = 0;
    public static final int MAX_CANVAS_WIDTH = 10000;
    public static final int MAX_CANVAS_HEIGHT = 10000;
    public enum NodeType {
        SMALL(30, 4, "S"),
        MEDIUM(42, 3, "M"),
        KEYSTONE(58, 1, "K"),
        START(66, 1, "+"),
        PATH(20, 1, ".");

        public final int size;
        public final int defaultMaxRank;
        public final String glyph;

        NodeType(int size, int defaultMaxRank, String glyph) {
            this.size = size;
            this.defaultMaxRank = defaultMaxRank;
            this.glyph = glyph;
        }
    }

    /** Custom UI-root texture for the tree node's size and allocation state. */
    public static String nodeFrameTexturePath(NodeType type, boolean allocated) {
        NodeType safeType = type == null ? NodeType.SMALL : type;
        String sizeName = switch (safeType) {
            case MEDIUM -> "Medium";
            case KEYSTONE, START -> "KeyStone";
            case PATH -> "Path";
            default -> "Small";
        };
        String allocationSuffix = allocated && safeType != NodeType.PATH ? "_Allocated" : "";
        // Node templates live under Common/UI/Custom/Pages, so resolve textures beside them.
        return "Pages/SkillTreeNode_" + sizeName + allocationSuffix + ".png";
    }

    /** Keeps a texture path stored relative to the Common/UI/Custom asset root. */
    public static String nodeIconUiPath(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) return "";
        String path = texturePath.trim().replace('\\', '/');
        while (path.startsWith("./")) path = path.substring(2);
        return path;
    }

    public static final class EditorNode {
        public String id;
        public String name;
        /** Native item id used as the node's visual icon. Empty means intentionally no icon. */
        public String iconItemId;
        /** Bundled or native texture asset path; overrides iconItemId when present. */
        public String iconTexturePath;
        public String type;
        public String category;
        /** Legacy single-bonus fields retained so existing editor drafts still load. */
        public String bonus;
        public double perRank;
        public List<EditorBonus> bonuses = new ArrayList<>();
        public int selectedBonusIndex;
        public int maxRank;
        public int x;
        public int y;

        public EditorNode() {}
    }

    public static final class EditorBonus {
        public String bonus;
        public double perRank;

        public EditorBonus() {}
        public EditorBonus(String bonus, double perRank) {
            this.bonus = bonus;
            this.perRank = perRank;
        }
    }

    public static final class EditorEdge {
        public String first;
        public String second;

        public EditorEdge() {}
        EditorEdge(String first, String second) { this.first = first; this.second = second; }
    }

    public static final class Document {
        public int version = 5;
        public int nextNodeNumber = 1;
        public int canvasWidth = CANVAS_WIDTH;
        public int canvasHeight = CANVAS_HEIGHT;
        public boolean appliedToPlayers;
        public List<EditorNode> nodes = new ArrayList<>();
        public List<EditorEdge> edges = new ArrayList<>();

        public Document() {}
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Bonus> EDITABLE_BONUSES = List.of(
            Bonus.MAX_HEALTH_FLAT, Bonus.HEALTH_REGEN_PER_SECOND,
            Bonus.MAX_MANA_FLAT, Bonus.MANA_REGEN_PER_SECOND,
            Bonus.MAX_STAMINA_FLAT, Bonus.STAMINA_REGEN_PER_SECOND,
            Bonus.REFINING_CHANCE, Bonus.REFINING_BREAK_PROTECTION,
            Bonus.REFINING_DEGRADE_PROTECTION, Bonus.REFINING_SAME_CHANCE,
            Bonus.REFINING_UPGRADE_CHANCE, Bonus.REFINING_JACKPOT_CHANCE, Bonus.SOCKET_CHANCE,
            Bonus.DAMAGE_PERCENT, Bonus.DAMAGE_RESISTANCE,
            Bonus.CRIT_CHANCE_PERCENT, Bonus.CRIT_DAMAGE_PERCENT, Bonus.ATTACK_SPEED_PERCENT,
            Bonus.LORE_XP_GAIN_PERCENT,
            Bonus.ELEMENTAL_DAMAGE, Bonus.ELEMENTAL_DOT_CRIT_CHANCE,
            Bonus.ELEMENTAL_DOT_CRIT_DAMAGE, Bonus.ELEMENTAL_STACK_BASE,
            Bonus.POISON_STACK_BASE, Bonus.BLEED_STACK_BASE);

    private static File documentFile;
    private static Document document = new Document();
    private static boolean pendingPositionChange;

    private CharacterPathEditorService() {}

    public static synchronized void initialize(File dataDirectory) {
        documentFile = new File(dataDirectory, "skill_tree_editor.json");
        document = load(documentFile);
        normalize(document);
        if (document.appliedToPlayers) applyDocument();
    }

    public static synchronized Document snapshot() {
        Document copy = new Document();
        copy.version = document.version;
        copy.nextNodeNumber = document.nextNodeNumber;
        copy.canvasWidth = document.canvasWidth;
        copy.canvasHeight = document.canvasHeight;
        copy.appliedToPlayers = document.appliedToPlayers;
        for (EditorNode node : document.nodes) copy.nodes.add(copyNode(node));
        for (EditorEdge edge : document.edges) copy.edges.add(new EditorEdge(edge.first, edge.second));
        return copy;
    }

    /** Copies one node without duplicating the entire editor graph. */
    public static synchronized EditorNode nodeSnapshot(String id) {
        EditorNode node = find(id);
        return node == null ? null : copyNode(node);
    }

    public static synchronized EditorNode addNode(int x, int y) {
        EditorNode node = new EditorNode();
        node.id = String.format(Locale.ROOT, "edit_node_%03d", document.nextNodeNumber++);
        node.name = "Node " + (document.nextNodeNumber - 1);
        node.type = NodeType.SMALL.name();
        node.category = Category.COMBAT.name();
        node.iconItemId = defaultIconItemId(node);
        node.bonus = Bonus.DAMAGE_PERCENT.name();
        node.perRank = defaultAmount(Bonus.DAMAGE_PERCENT);
        node.bonuses.add(new EditorBonus(node.bonus, node.perRank));
        node.maxRank = NodeType.SMALL.defaultMaxRank;
        node.x = x;
        node.y = y;
        document.nodes.add(node);
        changed();
        return copyNode(node);
    }

    /** Creates a full copy of a node, including all bonus entries and rank settings. */
    public static synchronized EditorNode duplicateNode(String id, int x, int y) {
        EditorNode source = find(id);
        if (source == null) return null;
        EditorNode copy = copyNode(source);
        int number = document.nextNodeNumber++;
        copy.id = String.format(Locale.ROOT, "edit_node_%03d", number);
        copy.name = (source.name == null || source.name.isBlank() ? "Node" : source.name) + " Copy";
        copy.x = x;
        copy.y = y;
        document.nodes.add(copy);
        changed();
        return copyNode(copy);
    }

    /**
     * Duplicates a selected group at the requested coordinates, retaining its internal links.
     * The validator sees the complete candidate draft before it is committed.
     */
    public static synchronized Map<String, EditorNode> duplicateNodes(
            Map<String, int[]> positions, Predicate<Document> validator) {
        if (positions == null || positions.isEmpty()) return Map.of();
        Document candidate = snapshot();
        Map<String, EditorNode> sources = new LinkedHashMap<>();
        for (String sourceId : positions.keySet()) {
            EditorNode source = find(sourceId);
            int[] position = positions.get(sourceId);
            if (source == null || position == null || position.length < 2) return Map.of();
            int size = type(source).size;
            if (position[0] < 0 || position[1] < 0
                    || position[0] > candidate.canvasWidth - CANVAS_X_OFFSET - size
                    || position[1] > candidate.canvasHeight - size) return Map.of();
            sources.put(sourceId, copyNode(source));
        }

        Map<String, EditorNode> copies = new LinkedHashMap<>();
        for (Map.Entry<String, EditorNode> entry : sources.entrySet()) {
            EditorNode copy = copyNode(entry.getValue());
            int number = candidate.nextNodeNumber++;
            copy.id = String.format(Locale.ROOT, "edit_node_%03d", number);
            copy.name = (entry.getValue().name == null || entry.getValue().name.isBlank()
                    ? "Node" : entry.getValue().name) + " Copy";
            int[] position = positions.get(entry.getKey());
            copy.x = position[0];
            copy.y = position[1];
            candidate.nodes.add(copy);
            copies.put(entry.getKey(), copy);
        }

        List<EditorEdge> mirroredEdges = new ArrayList<>();
        for (EditorEdge edge : candidate.edges) {
            if (edge == null) continue;
            EditorNode firstCopy = copies.get(edge.first);
            EditorNode secondCopy = copies.get(edge.second);
            if (firstCopy != null && secondCopy != null) {
                mirroredEdges.add(new EditorEdge(firstCopy.id, secondCopy.id));
            }
        }
        candidate.edges.addAll(mirroredEdges);
        if (validator != null && !validator.test(candidate)) return Map.of();

        document = candidate;
        changed();
        Map<String, EditorNode> result = new LinkedHashMap<>();
        copies.forEach((sourceId, copy) -> result.put(sourceId, copyNode(copy)));
        return result;
    }

    /** Copies editable node properties while preserving the target identity, position and links. */
    public static synchronized boolean copyNodeProperties(
            String sourceId, String targetId, Predicate<Document> validator) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return false;
        Document candidate = snapshot();
        EditorNode source = candidate.nodes.stream()
                .filter(node -> node != null && sourceId.equals(node.id)).findFirst().orElse(null);
        EditorNode target = candidate.nodes.stream()
                .filter(node -> node != null && targetId.equals(node.id)).findFirst().orElse(null);
        if (source == null || target == null) return false;
        EditorNode properties = copyNode(source);
        target.name = properties.name;
        target.iconItemId = properties.iconItemId;
        target.iconTexturePath = properties.iconTexturePath;
        target.type = properties.type;
        target.category = properties.category;
        target.bonus = properties.bonus;
        target.perRank = properties.perRank;
        target.bonuses = properties.bonuses;
        target.selectedBonusIndex = properties.selectedBonusIndex;
        target.maxRank = properties.maxRank;
        if (validator != null && !validator.test(candidate)) return false;
        document = candidate;
        changed();
        return true;
    }

    public static synchronized boolean moveNode(String id, int dx, int dy, int maxX, int maxY) {
        EditorNode node = find(id);
        if (node == null) return false;
        int size = type(node).size;
        node.x = clamp(node.x + dx, 0, Math.max(0, maxX - size));
        node.y = clamp(node.y + dy, 0, Math.max(0, maxY - size));
        changed();
        return true;
    }

    /** Places a node at exact canvas coordinates, clamped to the canvas bounds. */
    public static synchronized boolean setNodePosition(String id, int x, int y) {
        EditorNode node = find(id);
        if (node == null) return false;
        int size = type(node).size;
        int nextX = clamp(x, 0, document.canvasWidth - CANVAS_X_OFFSET - size);
        int nextY = clamp(y, 0, document.canvasHeight - size);
        if (node.x == nextX && node.y == nextY) return true;
        node.x = nextX;
        node.y = nextY;
        changed();
        return true;
    }

    /** Updates a dragged node in memory; the editor commits once when the drag ends. */
    public static synchronized boolean previewNodePosition(String id, int x, int y) {
        EditorNode node = find(id);
        if (node == null) return false;
        int size = type(node).size;
        int nextX = clamp(x, 0, document.canvasWidth - CANVAS_X_OFFSET - size);
        int nextY = clamp(y, 0, document.canvasHeight - size);
        if (node.x == nextX && node.y == nextY) return true;
        node.x = nextX;
        node.y = nextY;
        pendingPositionChange = true;
        return true;
    }

    public static synchronized void commitPositionPreview() {
        if (!pendingPositionChange) return;
        pendingPositionChange = false;
        changed();
    }

    public static synchronized boolean removeNode(String id) {
        if (find(id) == null) return false;
        document.nodes.removeIf(node -> node == null || id.equals(node.id));
        document.edges.removeIf(edge -> edge == null || id.equals(edge.first) || id.equals(edge.second));
        changed();
        return true;
    }

    public static synchronized boolean renameNode(String id, String name) {
        EditorNode node = find(id);
        if (node == null || name == null) return false;
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 48) return false;
        if (normalized.equals(node.name)) return true;
        node.name = normalized;
        changed();
        return true;
    }

    /** Empty item ids deliberately hide the icon. IDs use the native asset identifier format. */
    public static synchronized boolean setNodeIcon(String id, String itemId) {
        EditorNode node = find(id);
        if (node == null || itemId == null) return false;
        String normalized = itemId.trim();
        if (normalized.length() > 128 || (!normalized.isEmpty()
                && !normalized.matches("[A-Za-z0-9_.-]+"))) return false;
        if (normalized.equals(node.iconItemId) && (node.iconTexturePath == null || node.iconTexturePath.isBlank())) return true;
        node.iconItemId = normalized;
        node.iconTexturePath = "";
        changed();
        return true;
    }

    /** Sets a bundled/native UI texture path. Empty clears the override and restores the item icon. */
    public static synchronized boolean setNodeTexturePath(String id, String texturePath) {
        EditorNode node = find(id);
        if (node == null || texturePath == null || type(node) == NodeType.PATH) return false;
        String normalized = texturePath.trim().replace('\\', '/');
        if (normalized.length() > 512 || normalized.startsWith("/") || normalized.contains("://")
                || normalized.matches("^[A-Za-z]:.*")
                || (!normalized.isEmpty() && !normalized.matches("[A-Za-z0-9_./-]+"))) return false;
        for (String segment : normalized.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) return false;
        }
        if (normalized.equals(node.iconTexturePath)) return true;
        node.iconTexturePath = normalized;
        changed();
        return true;
    }

    public static synchronized boolean cycleType(String id, int direction) {
        EditorNode node = find(id);
        if (node == null) return false;
        NodeType[] types = NodeType.values();
        NodeType current = type(node);
        NodeType next = types[Math.floorMod(current.ordinal() + direction, types.length)];
        return setNodeType(id, next.name());
    }

    public static synchronized boolean setNodeType(String id, String typeName) {
        EditorNode node = find(id);
        if (node == null || typeName == null) return false;
        NodeType current = type(node);
        NodeType next;
        try { next = NodeType.valueOf(typeName); }
        catch (IllegalArgumentException exception) { return false; }
        if (current == next) return true;
        String previousDefaultIcon = defaultIconItemId(node);
        boolean useTierIcon = usesDefaultIcon(node, previousDefaultIcon);
        node.type = next.name();
        if (useTierIcon) node.iconItemId = next == NodeType.PATH ? "" : defaultIconItemId(node);
        if (next == NodeType.PATH) {
            node.category = Category.VITALITY.name();
            node.bonuses = new ArrayList<>(List.of(new EditorBonus(Bonus.MAX_HEALTH_FLAT.name(), 1.0)));
            node.selectedBonusIndex = 0;
            node.maxRank = 1;
            syncLegacyBonus(node);
        } else {
            normalizeBonuses(node, current);
            if (node.bonuses.isEmpty()) {
                node.bonuses.add(new EditorBonus(Bonus.DAMAGE_PERCENT.name(), defaultAmount(Bonus.DAMAGE_PERCENT)));
            }
            for (EditorBonus entry : node.bonuses) {
                Bonus entryBonus = bonus(entry);
                double oldDefault = defaultAmount(entryBonus) * typeScale(current);
                if (current == NodeType.PATH || Math.abs(entry.perRank - oldDefault) < 0.0000001) {
                    entry.perRank = defaultAmount(entryBonus) * typeScale(next);
                }
            }
            syncLegacyBonus(node);
            node.maxRank = next.defaultMaxRank;
        }
        changed();
        return true;
    }

    /** Sets the one-rank stat bonus carried by a small, selectable path node. */
    public static synchronized boolean setPathBonus(String id, String statName) {
        EditorNode node = find(id);
        if (node == null || type(node) != NodeType.PATH || statName == null) return false;

        Category nextCategory;
        Bonus nextBonus;
        switch (statName.trim().toUpperCase(Locale.ROOT)) {
            case "HEALTH", "HP" -> {
                nextCategory = Category.VITALITY;
                nextBonus = Bonus.MAX_HEALTH_FLAT;
            }
            case "MANA", "MP" -> {
                nextCategory = Category.ARCANA;
                nextBonus = Bonus.MAX_MANA_FLAT;
            }
            case "STAMINA" -> {
                nextCategory = Category.ENDURANCE;
                nextBonus = Bonus.MAX_STAMINA_FLAT;
            }
            default -> { return false; }
        }

        node.type = NodeType.PATH.name();
        node.category = nextCategory.name();
        node.maxRank = 1;
        node.bonuses = new ArrayList<>(List.of(new EditorBonus(nextBonus.name(), 1.0)));
        node.selectedBonusIndex = 0;
        syncLegacyBonus(node);
        node.iconItemId = "";
        changed();
        return true;
    }

    public static synchronized boolean cycleCategory(String id, int direction) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        Category[] values = Category.values();
        Category current = category(node);
        Category next = values[Math.floorMod(current.ordinal() + direction, values.length)];
        return setNodeCategory(id, next.name());
    }

    public static synchronized boolean setNodeCategory(String id, String categoryName) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH || categoryName == null) return false;
        Category next;
        try { next = Category.valueOf(categoryName); }
        catch (IllegalArgumentException exception) { return false; }
        if (category(node) == next) return true;
        String previousDefaultIcon = defaultIconItemId(node);
        boolean useTierIcon = usesDefaultIcon(node, previousDefaultIcon);
        node.category = next.name();
        if (useTierIcon) node.iconItemId = defaultIconItemId(node);
        changed();
        return true;
    }

    public static synchronized boolean cycleBonus(String id, int direction) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        EditorBonus entry = selectedBonus(node);
        if (entry == null) return false;
        Bonus current = bonus(entry);
        int index = Math.max(0, EDITABLE_BONUSES.indexOf(current));
        Set<Bonus> used = new LinkedHashSet<>();
        for (EditorBonus existing : node.bonuses) if (existing != entry) used.add(bonus(existing));
        Bonus next = null;
        for (int offset = 1; offset <= EDITABLE_BONUSES.size(); offset++) {
            Bonus candidate = EDITABLE_BONUSES.get(Math.floorMod(index + direction * offset, EDITABLE_BONUSES.size()));
            if (!used.contains(candidate)) { next = candidate; break; }
        }
        if (next == null) return false;
        return setSelectedBonusType(id, next.name());
    }

    public static synchronized boolean setSelectedBonusIndex(String id, int index) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        if (index < 0 || index >= node.bonuses.size()) return false;
        if (node.selectedBonusIndex == index) return true;
        node.selectedBonusIndex = index;
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean setSelectedBonusType(String id, String bonusName) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH || bonusName == null) return false;
        normalizeBonuses(node, type(node));
        Bonus next;
        try { next = Bonus.valueOf(bonusName); }
        catch (IllegalArgumentException exception) { return false; }
        if (!EDITABLE_BONUSES.contains(next)) return false;
        EditorBonus entry = selectedBonus(node);
        if (entry == null) return false;
        for (EditorBonus other : node.bonuses) {
            if (other != entry && bonus(other) == next) return false;
        }
        if (bonus(entry) == next) return true;
        entry.bonus = next.name();
        entry.perRank = defaultAmount(next) * typeScale(type(node));
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean addBonus(String id) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        if (node.bonuses.size() >= EDITABLE_BONUSES.size()) return false;
        Set<Bonus> used = new LinkedHashSet<>();
        for (EditorBonus entry : node.bonuses) used.add(bonus(entry));
        Bonus next = EDITABLE_BONUSES.stream().filter(candidate -> !used.contains(candidate)).findFirst().orElse(null);
        if (next == null) return false;
        node.bonuses.add(new EditorBonus(next.name(), defaultAmount(next) * typeScale(type(node))));
        node.selectedBonusIndex = node.bonuses.size() - 1;
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean removeSelectedBonus(String id) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        if (node.bonuses.size() <= 1) return false;
        node.bonuses.remove(Math.max(0, Math.min(node.selectedBonusIndex, node.bonuses.size() - 1)));
        node.selectedBonusIndex = Math.min(node.selectedBonusIndex, node.bonuses.size() - 1);
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean selectBonus(String id, int direction) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        if (node.bonuses.isEmpty()) return false;
        node.selectedBonusIndex = Math.floorMod(node.selectedBonusIndex + direction, node.bonuses.size());
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean adjustAmount(String id, int direction) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        normalizeBonuses(node, type(node));
        EditorBonus selected = selectedBonus(node);
        if (selected == null) return false;
        Bonus bonus = bonus(selected);
        double value = selected.perRank + amountStep(bonus) * direction;
        return setSelectedAmount(id, value);
    }

    public static synchronized boolean setSelectedAmount(String id, double value) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH || !Double.isFinite(value)) return false;
        normalizeBonuses(node, type(node));
        EditorBonus selected = selectedBonus(node);
        if (selected == null) return false;
        Bonus bonus = bonus(selected);
        double maximum = isPercent(bonus) ? 10.0 : 100_000.0;
        double normalized = Math.round(clamp(value, 0.0, maximum) * 1_000_000.0) / 1_000_000.0;
        if (Math.abs(selected.perRank - normalized) < 0.0000001) return true;
        selected.perRank = normalized;
        syncLegacyBonus(node);
        changed();
        return true;
    }

    public static synchronized boolean adjustMaxRank(String id, int direction) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        return setMaxRank(id, node.maxRank + direction);
    }

    public static synchronized boolean setMaxRank(String id, int value) {
        EditorNode node = find(id);
        if (node == null || type(node) == NodeType.PATH) return false;
        int next = clamp(value, 1, 20);
        if (node.maxRank == next) return true;
        node.maxRank = next;
        changed();
        return true;
    }

    /** Adds an edge; returns false when it already exists or the endpoints are invalid. */
    public static synchronized boolean connect(String first, String second) {
        if (first == null || second == null || first.equals(second)
                || find(first) == null || find(second) == null || hasEdge(first, second)) return false;
        document.edges.add(new EditorEdge(first, second));
        changed();
        return true;
    }

    public static synchronized boolean disconnect(String first, String second) {
        int before = document.edges.size();
        document.edges.removeIf(edge -> edge != null
                && ((first.equals(edge.first) && second.equals(edge.second))
                || (first.equals(edge.second) && second.equals(edge.first))));
        if (before == document.edges.size()) return false;
        changed();
        return true;
    }

    /** Removes every link attached to one node and persists the graph once. */
    public static synchronized int disconnectNodeEdges(String id) {
        if (id == null) return 0;
        int before = document.edges.size();
        document.edges.removeIf(edge -> edge != null
                && (id.equals(edge.first) || id.equals(edge.second)));
        int removed = before - document.edges.size();
        if (removed > 0) changed();
        return removed;
    }

    public static synchronized boolean hasEdge(String first, String second) {
        return document.edges.stream().filter(edge -> edge != null).anyMatch(edge ->
                (first.equals(edge.first) && second.equals(edge.second))
                        || (first.equals(edge.second) && second.equals(edge.first)));
    }

    public static synchronized boolean applyToPlayers() {
        document.appliedToPlayers = true;
        if (!applyDocument()) return false;
        save();
        return true;
    }

    public static synchronized int canvasWidth() { return document.canvasWidth; }
    public static synchronized int canvasHeight() { return document.canvasHeight; }
    public static int canvasXOffset() { return CANVAS_X_OFFSET; }

    /** Resize the authoring canvas without cutting any existing node off its bounds. */
    public static synchronized boolean setCanvasSize(int width, int height) {
        if (width < MIN_CANVAS_WIDTH || width > MAX_CANVAS_WIDTH
                || height < MIN_CANVAS_HEIGHT || height > MAX_CANVAS_HEIGHT) return false;
        boolean nodesFit = document.nodes.stream().filter(Objects::nonNull).allMatch(node -> {
            int size = type(node).size;
            return node.x >= 0 && node.y >= 0
                    && node.x + size <= width - CANVAS_X_OFFSET
                    && node.y + size <= height;
        });
        if (!nodesFit) return false;
        if (width == document.canvasWidth && height == document.canvasHeight) return true;
        document.canvasWidth = width;
        document.canvasHeight = height;
        changed();
        return true;
    }

    public static List<Bonus> editableBonuses() { return EDITABLE_BONUSES; }
    public static String bonusLabel(Bonus bonus) {
        if (bonus == Bonus.ATTACK_SPEED_PERCENT) return "Attack Speed (Multi-hit Chance)";
        if (bonus == Bonus.LORE_XP_GAIN_PERCENT) return "Lore XP Gain";
        return bonus.name().replace('_', ' ');
    }

    public static NodeType type(EditorNode node) {
        try { return NodeType.valueOf(node == null ? "SMALL" : node.type); }
        catch (Exception ignored) { return NodeType.SMALL; }
    }

    public static Bonus bonus(EditorNode node) {
        try {
            Bonus bonus = Bonus.valueOf(node == null ? "DAMAGE_PERCENT" : node.bonus);
            return editableBonuses().contains(bonus) ? bonus : Bonus.DAMAGE_PERCENT;
        } catch (Exception ignored) { return Bonus.DAMAGE_PERCENT; }
    }

    public static Category category(EditorNode node) {
        try { return Category.valueOf(node == null ? "COMBAT" : node.category); }
        catch (Exception ignored) { return Category.COMBAT; }
    }

    public static String formatAmount(EditorNode node) {
        if (node == null) return "No player bonus";
        normalizeBonuses(node, type(node));
        EditorBonus selected = selectedBonus(node);
        if (selected == null) return "No player bonus";
        Bonus bonus = bonus(selected);
        double value = selected.perRank;
        if (isPercent(bonus)) return String.format(Locale.ROOT, "%.2f%% / rank", value * 100.0);
        return String.format(Locale.ROOT, "%.3f / rank", value);
    }

    private static boolean applyDocument() {
        List<NodeDefinition> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (EditorNode node : document.nodes) {
            if (node == null || node.id == null || node.id.isBlank()) continue;
            ids.add(node.id);
            NodeType type = type(node);
            Category category = category(node);
            WeaponClass weaponClass = weaponClass(category);
            normalizeBonuses(node, type);
            List<NodeBonus> bonuses = node.bonuses.stream()
                    .map(entry -> new NodeBonus(bonus(entry), Math.max(0.0, entry.perRank))).toList();
            String name = node.name == null || node.name.isBlank() ? node.id : node.name;
            String description = type == NodeType.PATH
                    ? String.format(Locale.ROOT, "Path node · %s. Allocate it to continue along this branch.", formatAllBonuses(node))
                    : String.format(Locale.ROOT, "%s; maximum rank %d.", formatAllBonuses(node), Math.max(1, node.maxRank));
            nodes.add(new NodeDefinition(node.id, category, name, description,
                    type == NodeType.PATH ? 1 : clamp(node.maxRank, 1, 20),
                    type == NodeType.START, type == NodeType.PATH, weaponClass, bonuses));
        }
        for (EditorEdge edge : document.edges) {
            if (edge != null && ids.contains(edge.first) && ids.contains(edge.second)
                    && !edge.first.equals(edge.second)) edges.add(new GraphEdge(edge.first, edge.second));
        }
        CharacterPathService.replaceTree(nodes, edges);
        return true;
    }

    private static WeaponClass weaponClass(Category category) {
        if (category == null) return null;
        return switch (category) {
            case DAGGERS -> WeaponClass.DAGGERS;
            case SWORDS -> WeaponClass.SWORDS;
            case AXES -> WeaponClass.AXES;
            case MACES -> WeaponClass.MACES;
            case SPEARS -> WeaponClass.SPEARS;
            case SHORTBOWS -> WeaponClass.SHORTBOWS;
            case CROSSBOWS -> WeaponClass.CROSSBOWS;
            case STAFFS -> WeaponClass.STAFFS;
            default -> null;
        };
    }

    private static EditorNode find(String id) {
        if (id == null) return null;
        return document.nodes.stream().filter(node -> node != null && id.equals(node.id)).findFirst().orElse(null);
    }

    private static void changed() {
        pendingPositionChange = false;
        normalize(document);
        if (document.appliedToPlayers) applyDocument();
        save();
    }

    private static void normalize(Document target) {
        if (target.nodes == null) target.nodes = new ArrayList<>();
        if (target.edges == null) target.edges = new ArrayList<>();
        target.version = 5;
        target.nextNodeNumber = Math.max(1, target.nextNodeNumber);
        target.canvasWidth = clamp(target.canvasWidth <= 0 ? CANVAS_WIDTH : target.canvasWidth,
                MIN_CANVAS_WIDTH, MAX_CANVAS_WIDTH);
        target.canvasHeight = clamp(target.canvasHeight <= 0 ? CANVAS_HEIGHT : target.canvasHeight,
                MIN_CANVAS_HEIGHT, MAX_CANVAS_HEIGHT);

        Map<String, EditorNode> uniqueNodes = new LinkedHashMap<>();
        for (EditorNode node : target.nodes) {
            if (node == null || node.id == null || node.id.isBlank()) continue;
            node.name = node.name == null || node.name.isBlank() ? node.id : node.name;
            NodeType type = type(node);
            node.type = type.name();
            if (node.iconItemId == null) {
                node.iconItemId = type == NodeType.PATH ? "" : defaultIconItemId(node);
            } else {
                node.iconItemId = node.iconItemId.trim();
                if (node.iconItemId.length() > 128
                        || (!node.iconItemId.isEmpty() && !node.iconItemId.matches("[A-Za-z0-9_.-]+"))) {
                    node.iconItemId = "";
                }
            }
            if (node.iconTexturePath == null) node.iconTexturePath = "";
            else {
                node.iconTexturePath = node.iconTexturePath.trim().replace('\\', '/');
                if (node.iconTexturePath.length() > 512 || node.iconTexturePath.startsWith("/")
                        || node.iconTexturePath.contains("://") || node.iconTexturePath.matches("^[A-Za-z]:.*")
                        || (!node.iconTexturePath.isEmpty()
                        && !node.iconTexturePath.matches("[A-Za-z0-9_./-]+"))) {
                    node.iconTexturePath = "";
                } else {
                    for (String segment : node.iconTexturePath.split("/", -1)) {
                        if (segment.equals(".") || segment.equals("..")) {
                            node.iconTexturePath = "";
                            break;
                        }
                    }
                }
            }
            node.x = clamp(node.x, 0, target.canvasWidth - CANVAS_X_OFFSET - type.size);
            node.y = clamp(node.y, 0, target.canvasHeight - type.size);
            if (type == NodeType.PATH) {
                if (node.category == null || node.category.isBlank()) node.category = Category.VITALITY.name();
                normalizePathBonus(node);
                node.maxRank = 1;
            } else {
                Category category = category(node);
                node.category = category.name();
                NodeType previousType = type(node);
                normalizeBonuses(node, previousType);
                for (EditorBonus entry : node.bonuses) {
                    Bonus bonus = bonus(entry);
                    entry.perRank = clamp(entry.perRank, 0.0, isPercent(bonus) ? 10.0 : 100_000.0);
                }
                syncLegacyBonus(node);
                node.maxRank = clamp(node.maxRank <= 0 ? type.defaultMaxRank : node.maxRank, 1, 20);
            }
            uniqueNodes.putIfAbsent(node.id, node);
            if (node.id.startsWith("edit_node_")) {
                try { target.nextNodeNumber = Math.max(target.nextNodeNumber,
                        Integer.parseInt(node.id.substring("edit_node_".length())) + 1); }
                catch (NumberFormatException ignored) {}
            }
        }
        target.nodes = new ArrayList<>(uniqueNodes.values());
        Set<String> ids = uniqueNodes.keySet();
        Set<String> uniqueEdges = new LinkedHashSet<>();
        List<EditorEdge> validEdges = new ArrayList<>();
        for (EditorEdge edge : target.edges) {
            if (edge == null || edge.first == null || edge.second == null || edge.first.equals(edge.second)
                    || !ids.contains(edge.first) || !ids.contains(edge.second)) continue;
            String key = edge.first.compareTo(edge.second) < 0
                    ? edge.first + "|" + edge.second : edge.second + "|" + edge.first;
            if (uniqueEdges.add(key)) validEdges.add(edge);
        }
        target.edges = validEdges;
    }

    private static Document load(File file) {
        if (!file.isFile()) return new Document();
        try {
            Document loaded = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Document.class);
            return loaded == null ? new Document() : loaded;
        } catch (Exception exception) {
            System.err.println("[SocketReforge] Failed to load skill-tree editor draft: " + exception.getMessage());
            return new Document();
        }
    }

    private static void save() {
        if (documentFile == null) return;
        try {
            Files.createDirectories(documentFile.toPath().getParent());
            Files.writeString(documentFile.toPath(), GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("[SocketReforge] Failed to save skill-tree editor draft: " + exception.getMessage());
        }
    }

    private static EditorNode copyNode(EditorNode source) {
        EditorNode copy = new EditorNode();
        copy.id = source.id;
        copy.name = source.name;
        copy.iconItemId = source.iconItemId;
        copy.iconTexturePath = source.iconTexturePath;
        copy.type = source.type;
        copy.category = source.category;
        copy.bonus = source.bonus;
        copy.perRank = source.perRank;
        copy.bonuses = new ArrayList<>();
        if (source.bonuses != null) for (EditorBonus bonus : source.bonuses) {
            if (bonus != null) copy.bonuses.add(new EditorBonus(bonus.bonus, bonus.perRank));
        }
        copy.selectedBonusIndex = source.selectedBonusIndex;
        copy.maxRank = source.maxRank;
        copy.x = source.x;
        copy.y = source.y;
        return copy;
    }

    private static boolean usesDefaultIcon(EditorNode node, String previousDefault) {
        return node.iconItemId == null
                || (type(node) == NodeType.PATH && node.iconItemId.isBlank())
                || (!node.iconItemId.isBlank() && node.iconItemId.equals(previousDefault));
    }

    /** Picks recognizable, valid vanilla item assets by class and node tier. */
    public static String defaultIconItemId(EditorNode node) {
        if (node == null || type(node) == NodeType.PATH) return "";
        String tier = switch (type(node)) {
            case SMALL -> "Copper";
            case MEDIUM -> "Iron";
            case KEYSTONE -> "Thorium";
            case START -> "Onyxium";
            case PATH -> "";
        };
        Category category = category(node);
        return switch (category) {
            case VITALITY -> "Armor_" + tier + "_Chest";
            case GUARD -> "Armor_" + tier + "_Head";
            case ENDURANCE -> "Armor_" + tier + "_Legs";
            case ARCANA -> "Weapon_Staff_" + tier;
            case DAGGERS -> "Weapon_Daggers_" + tier;
            case SWORDS, COMBAT -> "Weapon_Sword_" + tier;
            case AXES -> "Weapon_Axe_" + tier;
            case MACES -> "Weapon_Mace_" + tier;
            case SPEARS -> "Weapon_Spear_" + tier;
            case SHORTBOWS -> "Weapon_Shortbow_" + tier;
            case CROSSBOWS -> type(node) == NodeType.KEYSTONE || type(node) == NodeType.START
                    ? "Weapon_Crossbow_Ancient_Steel" : "Weapon_Crossbow_Iron";
            case STAFFS -> "Weapon_Staff_" + tier;
        };
    }

    private static double defaultAmount(Bonus bonus) {
        return switch (bonus) {
            case MAX_HEALTH_FLAT -> 4.0;
            case HEALTH_REGEN_PER_SECOND, MANA_REGEN_PER_SECOND, STAMINA_REGEN_PER_SECOND -> 0.10;
            case MAX_MANA_FLAT, MAX_STAMINA_FLAT -> 3.0;
            case DAMAGE_PERCENT -> 0.005;
            case ATTACK_SPEED_PERCENT -> 0.05;
            case LORE_XP_GAIN_PERCENT -> 0.10;
            case REFINING_CHANCE, REFINING_BREAK_PROTECTION, REFINING_DEGRADE_PROTECTION,
                    REFINING_SAME_CHANCE, REFINING_UPGRADE_CHANCE, REFINING_JACKPOT_CHANCE,
                    SOCKET_CHANCE -> 0.005;
            case DAMAGE_RESISTANCE, CRIT_CHANCE_PERCENT, ELEMENTAL_DOT_CRIT_CHANCE -> 0.003;
            case CRIT_DAMAGE_PERCENT, ELEMENTAL_DOT_CRIT_DAMAGE -> 0.015;
            case ELEMENTAL_DAMAGE, ELEMENTAL_STACK_BASE, POISON_STACK_BASE, BLEED_STACK_BASE -> 0.01;
            default -> 0.0;
        };
    }

    private static double typeScale(NodeType type) {
        return switch (type) {
            case SMALL -> 1.0;
            case MEDIUM -> 2.0;
            case KEYSTONE -> 4.0;
            case START -> 5.0;
            case PATH -> 0.0;
        };
    }

    private static double amountStep(Bonus bonus) {
        return switch (bonus) {
            case MAX_HEALTH_FLAT, MAX_MANA_FLAT, MAX_STAMINA_FLAT -> 1.0;
            case HEALTH_REGEN_PER_SECOND, MANA_REGEN_PER_SECOND, STAMINA_REGEN_PER_SECOND -> 0.05;
            case ATTACK_SPEED_PERCENT -> 0.005;
            case LORE_XP_GAIN_PERCENT -> 0.01;
            default -> 0.0025;
        };
    }

    private static boolean isPercent(Bonus bonus) {
        return switch (bonus) {
            case REFINING_CHANCE, REFINING_BREAK_PROTECTION, REFINING_DEGRADE_PROTECTION,
                    REFINING_SAME_CHANCE, REFINING_UPGRADE_CHANCE, REFINING_JACKPOT_CHANCE,
                    SOCKET_CHANCE, DAMAGE_PERCENT, DAMAGE_RESISTANCE,
                    CRIT_CHANCE_PERCENT, CRIT_DAMAGE_PERCENT, ATTACK_SPEED_PERCENT, LORE_XP_GAIN_PERCENT,
                    ELEMENTAL_DAMAGE, ELEMENTAL_DOT_CRIT_CHANCE, ELEMENTAL_DOT_CRIT_DAMAGE,
                    ELEMENTAL_STACK_BASE, POISON_STACK_BASE, BLEED_STACK_BASE -> true;
            default -> false;
        };
    }

    private static boolean editableBonus(String name) {
        try { return EDITABLE_BONUSES.contains(Bonus.valueOf(name)); }
        catch (Exception ignored) { return false; }
    }

    private static Bonus bonus(EditorBonus entry) {
        try {
            Bonus bonus = Bonus.valueOf(entry == null ? null : entry.bonus);
            return EDITABLE_BONUSES.contains(bonus) ? bonus : Bonus.DAMAGE_PERCENT;
        } catch (Exception ignored) { return Bonus.DAMAGE_PERCENT; }
    }

    private static EditorBonus selectedBonus(EditorNode node) {
        if (node == null || node.bonuses == null || node.bonuses.isEmpty()) return null;
        node.selectedBonusIndex = clamp(node.selectedBonusIndex, 0, node.bonuses.size() - 1);
        return node.bonuses.get(node.selectedBonusIndex);
    }

    private static void normalizeBonuses(EditorNode node, NodeType nodeType) {
        if (node == null) return;
        if (node.bonuses == null) node.bonuses = new ArrayList<>();
        if (node.bonuses.isEmpty() && nodeType != NodeType.PATH) {
            Bonus legacy = editableBonus(node.bonus) ? bonus(node) : Bonus.DAMAGE_PERCENT;
            double legacyAmount = node.perRank;
            if (!editableBonus(node.bonus)) legacyAmount = defaultAmount(legacy) * typeScale(nodeType);
            node.bonuses.add(new EditorBonus(legacy.name(), legacyAmount));
        }
        List<EditorBonus> clean = new ArrayList<>();
        Set<Bonus> seen = new LinkedHashSet<>();
        for (EditorBonus entry : node.bonuses) {
            if (entry == null) continue;
            Bonus bonus = bonus(entry);
            if (!seen.add(bonus)) continue;
            entry.bonus = bonus.name();
            entry.perRank = clamp(entry.perRank, 0.0, isPercent(bonus) ? 10.0 : 100_000.0);
            clean.add(entry);
        }
        node.bonuses = clean;
        node.selectedBonusIndex = node.bonuses.isEmpty() ? 0
                : clamp(node.selectedBonusIndex, 0, node.bonuses.size() - 1);
        syncLegacyBonus(node);
    }

    private static void normalizePathBonus(EditorNode node) {
        if (node.bonuses == null || node.bonuses.isEmpty()) {
            Bonus fallback = editableBonus(node.bonus) ? bonus(node) : Bonus.MAX_HEALTH_FLAT;
            if (fallback != Bonus.MAX_HEALTH_FLAT && fallback != Bonus.MAX_MANA_FLAT
                    && fallback != Bonus.MAX_STAMINA_FLAT) fallback = Bonus.MAX_HEALTH_FLAT;
            node.bonuses = new ArrayList<>(List.of(new EditorBonus(fallback.name(), 1.0)));
        }
        Bonus stat = bonus(node.bonuses.get(0));
        if (stat != Bonus.MAX_HEALTH_FLAT && stat != Bonus.MAX_MANA_FLAT && stat != Bonus.MAX_STAMINA_FLAT) {
            stat = Bonus.MAX_HEALTH_FLAT;
            node.bonuses = new ArrayList<>(List.of(new EditorBonus(stat.name(), 1.0)));
        } else {
            node.bonuses = new ArrayList<>(List.of(new EditorBonus(stat.name(), 1.0)));
        }
        node.category = switch (stat) {
            case MAX_MANA_FLAT -> Category.ARCANA.name();
            case MAX_STAMINA_FLAT -> Category.ENDURANCE.name();
            default -> Category.VITALITY.name();
        };
        node.selectedBonusIndex = 0;
        node.maxRank = 1;
        syncLegacyBonus(node);
    }

    private static void syncLegacyBonus(EditorNode node) {
        EditorBonus selected = selectedBonus(node);
        node.bonus = selected == null ? null : selected.bonus;
        node.perRank = selected == null ? 0.0 : selected.perRank;
    }

    private static String formatAllBonuses(EditorNode node) {
        normalizeBonuses(node, type(node));
        List<String> entries = new ArrayList<>();
        for (EditorBonus entry : node.bonuses) entries.add(bonus(entry).name().replace('_', ' ')
                + " " + amountFor(entry) + " per rank");
        return entries.isEmpty() ? "No bonus" : String.join(" + ", entries);
    }

    private static String amountFor(EditorBonus entry) {
        Bonus bonus = bonus(entry);
        return isPercent(bonus) ? String.format(Locale.ROOT, "%.2f%%", entry.perRank * 100.0)
                : String.format(Locale.ROOT, "%.3f", entry.perRank);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
