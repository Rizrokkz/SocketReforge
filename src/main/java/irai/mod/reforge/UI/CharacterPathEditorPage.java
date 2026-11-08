package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Progression.CharacterPathEditorService;
import irai.mod.reforge.Progression.CharacterPathEditorService.Document;
import irai.mod.reforge.Progression.CharacterPathEditorService.EditorEdge;
import irai.mod.reforge.Progression.CharacterPathEditorService.EditorBonus;
import irai.mod.reforge.Progression.CharacterPathEditorService.EditorNode;
import irai.mod.reforge.Progression.CharacterPathEditorService.NodeType;
import irai.mod.reforge.Progression.CharacterPathService.Category;
import irai.mod.reforge.Progression.CharacterPathService.GraphEdge;

/** Blank canvas authoring page for the player skill tree. */
public final class CharacterPathEditorPage extends InteractiveCustomUIPage<CharacterPathEditorPage.PageEvent> {
    public static final int BASE_WIDTH = CharacterPathEditorService.CANVAS_WIDTH;
    public static final int BASE_HEIGHT = CharacterPathEditorService.CANVAS_HEIGHT;
    public static final int CANVAS_X_OFFSET = CharacterPathEditorService.CANVAS_X_OFFSET;

    private static final String PAGE_UI = "Pages/Editor/SocketReforge_SkillTree.ui";
    private static final String EDITOR_NODE_UI = "Pages/Editor/SocketReforge_SkillTree_EditorNode_";
    private static final String GRID_MINOR_UI = "Pages/Editor/SocketReforge_SkillTree_GridMinor.ui";
    private static final String GRID_MAJOR_UI = "Pages/Editor/SocketReforge_SkillTree_GridMajor.ui";
    private static final String GRID_CENTER_UI = "Pages/Editor/SocketReforge_SkillTree_GridCenter.ui";
    private static final String EDGE_UI = "Pages/SocketReforge_SkillTree_Edge.ui";
    private static final String CLEARANCE_UI = "Pages/Editor/SocketReforge_SkillTree_Clearance.ui";
    private static final String SELECTION_UI = "Pages/Editor/SocketReforge_SkillTree_Selection.ui";
    private static final String MOVE_TARGET_UI = "Pages/Editor/SocketReforge_SkillTree_MoveTarget.ui";
    private static final String MOVE_TARGETS_LAYER_UI = "Pages/Editor/SocketReforge_SkillTree_MoveTargets.ui";
    private static final int TREE_VIEWPORT_WIDTH = 1480;
    private static final int TREE_VIEWPORT_HEIGHT = 900;
    private static final int NODE_CLEARANCE = 8;
    private static final int GRID_SPACING = 120;
    private static final int MIN_ZOOM = 10;
    private static final int MAX_ZOOM = 400;
    private static final int COORDINATE_LINK_DETACH_LIMIT = 5;
    private static final Map<UUID, CharacterPathEditorPage> ACTIVE_PAGES = new ConcurrentHashMap<>();

    private final PlayerRef owner;
    private final Map<String, Integer> renderedNodeChildIndices = new HashMap<>();
    private final List<Integer> renderedEdgeChildIndices = new ArrayList<>();
    private final Set<String> selectedNodeIds = new LinkedHashSet<>();
    private String selectedNodeId;
    private String hoveredNodeId;
    private String connectFirstNodeId;
    private String copyPropertiesSourceId;
    private boolean connectMode;
    private boolean positionDragActive;
    private boolean pointerDragActive;
    private boolean pointerPositionChanged;
    private boolean suppressNextNodeActivation;
    private boolean moveMode;
    private boolean multiSelectMode;
    private boolean mirrorVertically;
    private boolean gridPlacementArmed;
    private boolean gridVisible;
    private boolean coordinateGridPreview;
    private String coordinateBlockedNodeId;
    private int coordinateLinkBlockCount;
    private boolean lastMoveBlockedByDetachableLinks;
    private boolean canvasSizePopupVisible;
    private boolean centerOnNextPopulate;
    private CharacterPathTreeViewport.View treeViewport;
    private double dragRemainderX;
    private double dragRemainderY;
    private int renderedClearanceChildIndex = -1;
    private int renderedPrimarySelectionHaloChildIndex = -1;
    private boolean renderedMoveTargetsLayer;
    private int zoomPercent = 80;
    private int renderedZoomPercent = 80;
    private long lastZoomRenderAtMillis;
    private int canvasWidth = BASE_WIDTH;
    private int canvasHeight = BASE_HEIGHT;
    private int canvasWidthInput = BASE_WIDTH;
    private int canvasHeightInput = BASE_HEIGHT;
    private String status = "Blank canvas. Add a node to begin.";

    public CharacterPathEditorPage(PlayerRef owner) {
        super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.owner = owner;
        Document document = CharacterPathEditorService.snapshot();
        canvasWidth = document.canvasWidth;
        canvasHeight = document.canvasHeight;
        // Keep the configured editor zoom on open; bounds-fit remains explicit.
        centerOnNextPopulate = false;
        if (owner != null && owner.getUuid() != null) {
            ACTIVE_PAGES.put(owner.getUuid(), this);
        }
    }

    public static void open(PlayerRef owner) {
        if (owner == null || !owner.isValid()) return;
        Ref<EntityStore> playerRef = owner.getReference();
        if (playerRef == null || !playerRef.isValid()) return;
        Store<EntityStore> store = playerRef.getStore();
        Player player = store == null ? null : store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(playerRef, store, new CharacterPathEditorPage(owner));
        }
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commands,
                      UIEventBuilder events, Store<EntityStore> store) {
        if (store.getComponent(playerRef, Player.getComponentType()) == null) return;
        commands.append(PAGE_UI);
        populate(commands, events, false);
    }

    private void populate(UICommandBuilder commands, UIEventBuilder events, boolean clearCanvas) {
        boolean centerNow = centerOnNextPopulate;
        centerOnNextPopulate = false;
        Document document = CharacterPathEditorService.snapshot();
        CharacterPathTreeViewport.View previousViewport = treeViewport;
        treeViewport = CharacterPathTreeViewport.create(document, zoomPercent, centerNow,
                TREE_VIEWPORT_WIDTH, TREE_VIEWPORT_HEIGHT, MIN_ZOOM, MAX_ZOOM);
        zoomPercent = treeViewport.zoomPercent;
        boolean resetScroll = centerNow || previousViewport == null
                || !previousViewport.hasSameGraphBounds(treeViewport);
        if (selectedNode(document) == null) gridPlacementArmed = false;
        canvasWidth = document.canvasWidth;
        canvasHeight = document.canvasHeight;
        renderedNodeChildIndices.clear();
        renderedEdgeChildIndices.clear();
        renderedClearanceChildIndex = -1;
        renderedPrimarySelectionHaloChildIndex = -1;
        renderedMoveTargetsLayer = false;
        if (clearCanvas) commands.clear("#TreeCanvas");
        // Pathforge lays out the graph inside a bounds-sized canvas. Keep the
        // graph bounds centered at the initial scroll origin rather than
        // asking the client to restore a scroll offset while creating the page.
        commands.set("#TreeCanvas.KeepScrollPosition", !resetScroll);
        commands.set("#TreeCanvas.ContentWidth", treeViewport.contentWidth);
        commands.set("#TreeCanvas.ContentHeight", treeViewport.contentHeight);
        if (resetScroll) commands.set("#TreeCanvas.KeepScrollPosition", true);
        commands.set("#EditorNodeCount.Text", "Nodes: " + document.nodes.size());
        commands.set("#EditorLinkCount.Text", "Links: " + document.edges.size());
        commands.set("#EditorApplied.Text", document.appliedToPlayers
                ? "Draft is applied to players" : "Draft only · players unchanged");
        commands.set("#EditorZoomLabel.Text", "Canvas " + zoomPercent + "%");
        commands.set("#EditorZoomSlider.Value", zoomPercent);
        renderedZoomPercent = zoomPercent;
        commands.set("#EditorCanvasSize.Text", "Canvas size: " + document.canvasWidth + " x " + document.canvasHeight);
        commands.set("#CanvasSizePopup.Visible", canvasSizePopupVisible);
        commands.set("#CanvasWidthInput.Value", (double) canvasWidthInput);
        commands.set("#CanvasHeightInput.Value", (double) canvasHeightInput);
        commands.set("#EditorStatus.Text", status);
        commands.set("#ToolLink.Text", connectMode ? "L*" : "L");
        commands.set("#ToolMove.Text", moveMode ? "M*" : "M");
        commands.set("#ToolSelect.Text", multiSelectMode ? "S*" : "S");
        commands.set("#ToolGrid.Text", gridPlacementArmed ? "G*" : "G");
        commands.set("#ToolGridVisibility.Text", gridVisible ? "V*" : "V");
        commands.set("#ToolCopyProperties.Text", copyPropertiesSourceId == null ? "P" : "P*");
        commands.set("#ToolMirrorFlip.Text", mirrorVertically ? "F*" : "F");

        bind(events, "#ToolAdd", "editor_add");
        bind(events, "#ToolDuplicate", "editor_duplicate");
        bind(events, "#ToolMirrorFlip", "editor_mirror_flip");
        bind(events, "#ToolCopyProperties", "editor_copy_properties");
        bind(events, "#ToolMove", "editor_move_mode");
        bind(events, "#ToolSelect", "editor_multi_select");
        bind(events, "#ToolSelectAll", "editor_select_all");
        bind(events, "#ToolClearSelection", "editor_clear_selection");
        bind(events, "#ToolGrid", "editor_grid_placement");
        bind(events, "#ToolGridVisibility", "editor_grid_toggle");
        bind(events, "#ToolRemove", "editor_remove");
        bind(events, "#ToolLink", "editor_connect_mode");
        bind(events, "#EditCanvasSize", "editor_canvas_size_open");
        bind(events, "#CancelCanvasSize", "editor_canvas_size_cancel");
        bind(events, "#EditorCenter", "editor_center");
        EventData canvasSizeData = EventData.of("Action", "editor_canvas_size_apply")
                .append("@CanvasWidth", "#CanvasWidthInput.Value")
                .append("@CanvasHeight", "#CanvasHeightInput.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyCanvasSize", canvasSizeData);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorZoomSlider",
                EventData.of("Action", "editor_zoom_value").append("@Zoom", "#EditorZoomSlider.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.MouseButtonReleased, "#EditorZoomSlider",
                EventData.of("Action", "editor_zoom_commit"), false);
        bind(events, "#EditorZoomReset", "editor_zoom", "Target", "reset");
        if (selectedNodeId != null) {
            bindSelectedNodeEvents(events);
        }
        bind(events, "#AddBonus", "editor_add_bonus");
        bind(events, "#RemoveBonus", "editor_remove_bonus");
        bind(events, "#ApplyDraft", "editor_apply");
        bind(events, "#PreviewPlayer", "editor_player_view");
        events.addEventBinding(CustomUIEventBindingType.Dismissing, "#SkillTreeRoot",
                EventData.of("Action", "editor_dismiss"), false);

        int nextChild = appendGrid(commands, document, 0);
        nextChild = appendEdges(commands, document, nextChild);
        nextChild = appendClearance(commands, document, nextChild);
        nextChild = appendSelectionHalos(commands, document, nextChild);
        nextChild = appendNodes(commands, events, document, nextChild);
        nextChild = appendMoveTargets(commands, events, document, nextChild);
        fillSelection(commands, document);
    }

    private int appendGrid(UICommandBuilder commands, Document document, int firstChildIndex) {
        if (!gridVisible && !gridPlacementArmed && !coordinateGridPreview) return firstChildIndex;
        int index = firstChildIndex;
        int spacing = gridCellSize();
        int centerGridX = document.canvasWidth / 2;
        List<Integer> xBoundaries = centeredGridBoundaries(document.canvasWidth, spacing);
        for (int i = 1; i < xBoundaries.size() - 1; i++) {
            int x = xBoundaries.get(i);
            int step = Math.round((x - centerGridX) / (float) spacing);
            if (step == 0) continue;
            int major = Math.abs(step) % 5 == 0 ? 2 : 1;
            appendGridLine(commands, index, anchor(canvasX(x), canvasY(0), major,
                    scaled(document.canvasHeight)), major == 2);
            index++;
        }
        int centerGridY = document.canvasHeight / 2;
        List<Integer> yBoundaries = centeredGridBoundaries(document.canvasHeight, spacing);
        for (int i = 1; i < yBoundaries.size() - 1; i++) {
            int y = yBoundaries.get(i);
            int step = Math.round((y - centerGridY) / (float) spacing);
            if (step == 0) continue;
            int major = Math.abs(step) % 5 == 0 ? 2 : 1;
            appendGridLine(commands, index, anchor(canvasX(0), canvasY(y),
                    scaled(document.canvasWidth), major), major == 2);
            index++;
        }
        int centerX = canvasX(centerGridX);
        int centerY = canvasY(centerGridY);
        appendCenterGridLine(commands, index++, anchor(centerX - 1, canvasY(0),
                3, scaled(document.canvasHeight)));
        appendCenterGridLine(commands, index++, anchor(canvasX(0), centerY - 1,
                scaled(document.canvasWidth), 3));
        return index;
    }

    private int appendEdges(UICommandBuilder commands, Document document, int index) {
        if (document.edges.isEmpty()) return index;
        List<GraphEdge> edges = document.edges.stream()
                .filter(edge -> edge != null && edge.first != null && edge.second != null)
                .map(edge -> new GraphEdge(edge.first, edge.second)).toList();
        try {
            List<CharacterPathEdgeRouter.RoutedEdge> routes = CharacterPathEdgeRouter.route(
                    edges, locations(document), document.canvasWidth, document.canvasHeight);
            for (CharacterPathEdgeRouter.RoutedEdge route : routes) {
                List<CharacterPathEdgeRouter.Point> points = route.points();
                for (int i = 1; i < points.size(); i++) {
                    index = appendSegment(commands, index, points.get(i - 1), points.get(i));
                }
            }
            return index;
        } catch (IllegalStateException exception) {
            status = "A link has no clear route. Move nodes or remove the link.";
            commands.set("#EditorStatus.Text", status);
            return index;
        }
    }

    private int appendSegment(UICommandBuilder commands, int index,
                              CharacterPathEdgeRouter.Point from, CharacterPathEdgeRouter.Point to) {
        EdgeSegmentVisual visual = edgeSegmentVisual(from, to);
        renderedEdgeChildIndices.add(index);
        appendEdge(commands, index, visual.anchor(), visual.orientation(), visual.width(), visual.height());
        return index + 1;
    }

    private EdgeSegmentVisual edgeSegmentVisual(CharacterPathEdgeRouter.Point from,
                                                CharacterPathEdgeRouter.Point to) {
        int x1 = canvasX((int) Math.round(from.x()));
        int y1 = canvasY((int) Math.round(from.y()));
        int x2 = canvasX((int) Math.round(to.x()));
        int y2 = canvasY((int) Math.round(to.y()));
        double dx = x2 - x1;
        double dy = y2 - y1;
        String orientation = CharacterPathEdgeVisuals.orientation(dx, dy);
        int thickness = Math.max(3, scaled(6));
        int left;
        int top;
        int width;
        int height;
        if ("Horizontal".equals(orientation)) {
            width = Math.max(1, (int) Math.round(Math.abs(dx))) + 2;
            height = thickness;
            left = Math.min(x1, x2) - 1;
            top = (int) Math.round((y1 + y2) / 2.0 - height / 2.0);
        } else if ("Vertical".equals(orientation)) {
            width = thickness;
            height = Math.max(1, (int) Math.round(Math.abs(dy))) + 2;
            left = (int) Math.round((x1 + x2) / 2.0 - width / 2.0);
            top = Math.min(y1, y2) - 1;
        } else {
            width = Math.max(1, (int) Math.round(Math.abs(dx))) + 2;
            height = Math.max(1, (int) Math.round(Math.abs(dy))) + 2;
            left = Math.min(x1, x2) - 1;
            top = Math.min(y1, y2) - 1;
        }
        return new EdgeSegmentVisual(anchor(left, top, width, height), orientation, width, height);
    }

    private boolean updateRenderedEdges(UICommandBuilder commands,
                                        List<CharacterPathEdgeRouter.RoutedEdge> routes) {
        List<EdgeSegmentVisual> visuals = new ArrayList<>();
        if (routes != null) {
            for (CharacterPathEdgeRouter.RoutedEdge route : routes) {
                if (route == null || route.points() == null) continue;
                List<CharacterPathEdgeRouter.Point> points = route.points();
                for (int i = 1; i < points.size(); i++) {
                    visuals.add(edgeSegmentVisual(points.get(i - 1), points.get(i)));
                }
            }
        }
        if (visuals.size() != renderedEdgeChildIndices.size()) return false;

        for (int i = 0; i < visuals.size(); i++) {
            EdgeSegmentVisual visual = visuals.get(i);
            int childIndex = renderedEdgeChildIndices.get(i);
            commands.setObject(child(childIndex, ".Anchor"), visual.anchor());
            commands.setObject(child(childIndex, ".Background"), textureBackground(
                    CharacterPathEdgeVisuals.texture("EdgeEditor", visual.orientation(),
                            visual.width(), visual.height())));
        }
        return true;
    }

    private int appendClearance(UICommandBuilder commands, Document document, int index) {
        EditorNode selected = document.nodes.stream().filter(node -> node != null
                && selectedNodeId != null && selectedNodeId.equals(node.id)).findFirst().orElse(null);
        int size = selected == null ? 1 : scaled(CharacterPathEditorService.type(selected).size);
        int radius = Math.max(1, scaled(NODE_CLEARANCE));
        int diameter = size + radius * 2;
        int centerX = selected == null ? 0 : canvasX(selected.x) + size / 2;
        int centerY = selected == null ? 0 : canvasY(selected.y) + size / 2;
        commands.append("#TreeCanvas", CLEARANCE_UI);
        commands.setObject(child(index, ".Anchor"), anchor(centerX - diameter / 2,
                centerY - diameter / 2, diameter, diameter));
        commands.set(child(index, ".Visible"), selected != null && positionDragActive);
        renderedClearanceChildIndex = index;
        return index + 1;
    }

    /** Add visible canvas-space markers for grouped selections and the pending link endpoint. */
    private int appendSelectionHalos(UICommandBuilder commands, Document document, int index) {
        EditorNode primary = selectedNode(document);
        boolean primaryIsLinkSource = primary != null && connectMode
                && primary.id.equals(connectFirstNodeId);
        commands.append("#TreeCanvas", SELECTION_UI);
        renderedPrimarySelectionHaloChildIndex = index;
        if (primary != null) {
            int size = scaled(CharacterPathEditorService.type(primary).size);
            int padding = Math.max(4, scaled(10));
            int diameter = size + padding * 2;
            int centerX = canvasX(primary.x) + size / 2;
            int centerY = canvasY(primary.y) + size / 2;
            commands.setObject(child(index, ".Anchor"), anchor(centerX - diameter / 2,
                    centerY - diameter / 2, diameter, diameter));
        } else {
            commands.setObject(child(index, ".Anchor"), anchor(0, 0, 1, 1));
        }
        commands.set(nested(index, "NodeLinkSourceGlow", "Visible"), primaryIsLinkSource);
        commands.set(nested(index, "NodePrimaryGroupGlow", "Visible"), false);
        commands.set(nested(index, "NodeGroupGlow", "Visible"), false);
        commands.set(child(index, ".Visible"), primary != null);
        index++;

        for (EditorNode node : document.nodes) {
            if (node == null || node.id == null) continue;
            boolean linkSource = connectMode && node.id.equals(connectFirstNodeId);
            boolean isPrimary = node.id.equals(selectedNodeId);
            boolean secondary = selectedNodeIds.size() > 1
                    && selectedNodeIds.contains(node.id) && !isPrimary;
            if (isPrimary || (!linkSource && !secondary)) continue;

            int size = scaled(CharacterPathEditorService.type(node).size);
            int padding = Math.max(4, scaled(10));
            int diameter = size + padding * 2;
            int centerX = canvasX(node.x) + size / 2;
            int centerY = canvasY(node.y) + size / 2;
            commands.append("#TreeCanvas", SELECTION_UI);
            commands.setObject(child(index, ".Anchor"), anchor(centerX - diameter / 2,
                    centerY - diameter / 2, diameter, diameter));
            commands.set(nested(index, "NodeLinkSourceGlow", "Visible"), linkSource);
            commands.set(nested(index, "NodePrimaryGroupGlow", "Visible"), false);
            commands.set(nested(index, "NodeGroupGlow", "Visible"), secondary);
            commands.set(child(index, ".Visible"), true);
            index++;
        }
        return index;
    }

    private int appendNodes(UICommandBuilder commands, UIEventBuilder events, Document document, int index) {
        for (EditorNode node : document.nodes) {
            if (node == null || node.id == null) continue;
            NodeType type = CharacterPathEditorService.type(node);
            Category category = CharacterPathEditorService.category(node);
            int size = scaled(type.size);
            boolean selected = node.id.equals(selectedNodeId);
            String nodeUi = type == NodeType.PATH
                    ? EDITOR_NODE_UI + "PATH.ui"
                    : EDITOR_NODE_UI + category.name() + ".ui";
            commands.append("#TreeCanvas", nodeUi);
            commands.setObject(child(index, ".Anchor"), anchor(canvasX(node.x), canvasY(node.y), size, size));
            setNodeFrame(commands, index, type, selected);
            String iconItemId = type == NodeType.PATH || node.iconItemId == null ? "" : node.iconItemId;
            String iconTexturePath = type == NodeType.PATH || node.iconTexturePath == null
                    ? "" : node.iconTexturePath;
            commands.set(nested(index, "NodeItemIcon", "ItemId"), iconItemId);
            commands.set(nested(index, "NodeItemIcon", "Visible"),
                    iconTexturePath.isBlank() && !iconItemId.isBlank());
            int iconSize = Math.max(10, (int) Math.round(size * 0.58));
            commands.setObject(nested(index, "NodeItemIcon", "Anchor"),
                    anchor((size - iconSize) / 2, (size - iconSize) / 2, iconSize, iconSize));
            if (!iconTexturePath.isBlank()) {
                commands.setObject(nested(index, "NodeTextureIcon", "Background"), textureBackground(
                        CharacterPathEditorService.nodeIconUiPath(iconTexturePath)));
            }
            commands.set(nested(index, "NodeTextureIcon", "Visible"), !iconTexturePath.isBlank());
            commands.setObject(nested(index, "NodeTextureIcon", "Anchor"),
                    anchor((size - iconSize) / 2, (size - iconSize) / 2, iconSize, iconSize));
            commands.set(nested(index, "NodeSelectionTint", "Visible"), selectedNodeIds.contains(node.id));
            commands.set(nested(index, "NodeHit", "Text"), "");
            events.addEventBinding(CustomUIEventBindingType.Activating, nested(index, "NodeHit", null),
                    EventData.of("Action", "editor_select").append("NodeId", node.id));
            events.addEventBinding(CustomUIEventBindingType.MouseEntered, nested(index, "NodeHit", null),
                    EventData.of("Action", "editor_hover_enter").append("NodeId", node.id), false);
            events.addEventBinding(CustomUIEventBindingType.MouseExited, nested(index, "NodeHit", null),
                    EventData.of("Action", "editor_hover_exit").append("NodeId", node.id), false);
            renderedNodeChildIndices.put(node.id, index);
            index++;
        }
        return index;
    }

    /** Prebuild hidden grid targets so arming placement is only a visibility update. */
    private int appendMoveTargets(UICommandBuilder commands, UIEventBuilder events,
                                  Document document, int index) {
        EditorNode selected = selectedNode(document);
        if (selected == null) return index;

        commands.append("#TreeCanvas", MOVE_TARGETS_LAYER_UI);
        commands.setObject("#GridMoveTargetsLayer.Anchor",
                anchor(0, 0, treeViewport.contentWidth, treeViewport.contentHeight));
        commands.set("#GridMoveTargetsLayer.Visible", gridPlacementArmed);
        renderedMoveTargetsLayer = true;

        appendMoveTargetCells(commands, events, document, selected);
        return index + 1;
    }

    private void appendMoveTargetCells(UICommandBuilder commands, UIEventBuilder events,
                                       Document document, EditorNode selected) {
        int cellSize = gridCellSize();
        int nodeSize = CharacterPathEditorService.type(selected).size;
        int maxX = document.canvasWidth - CANVAS_X_OFFSET - nodeSize;
        int maxY = document.canvasHeight - nodeSize;
        List<Integer> xBoundaries = centeredGridBoundaries(document.canvasWidth, cellSize);
        List<Integer> yBoundaries = centeredGridBoundaries(document.canvasHeight, cellSize);
        int targetIndex = 0;
        for (int yi = 0; yi < yBoundaries.size() - 1; yi++) {
            int y = yBoundaries.get(yi);
            int height = yBoundaries.get(yi + 1) - y;
            for (int xi = 0; xi < xBoundaries.size() - 1; xi++) {
                int x = xBoundaries.get(xi);
                int width = xBoundaries.get(xi + 1) - x;
                int nodeX = Math.max(0, Math.min(maxX, x + width / 2 - nodeSize / 2));
                int nodeY = Math.max(0, Math.min(maxY, y + height / 2 - nodeSize / 2));
                commands.append("#GridMoveTargetsLayer", MOVE_TARGET_UI);
                String targetChild = "#GridMoveTargetsLayer[" + targetIndex++ + "]";
                commands.setObject(targetChild + ".Anchor", anchor(
                        canvasX(x), canvasY(y), scaled(width), scaled(height)));
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        targetChild + " #MoveTarget",
                        EventData.of("Action", "editor_place")
                                .append("Target", nodeX + "," + nodeY), false);
            }
        }
    }

    private static int gridCellSize() {
        return GRID_SPACING;
    }

    private static List<Integer> centeredGridBoundaries(int extent, int spacing) {
        int center = extent / 2;
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        for (int step = center / spacing; step >= 1; step--) {
            boundaries.add(center - step * spacing);
        }
        boundaries.add(center);
        for (int position = center + spacing; position < extent; position += spacing) {
            boundaries.add(position);
        }
        boundaries.add(extent);
        return boundaries;
    }

    private void fillSelection(UICommandBuilder commands, Document document) {
        EditorNode selected = document.nodes.stream().filter(node -> node != null
                && node.id.equals(selectedNodeId)).findFirst().orElse(null);
        commands.set("#PositionControls.Visible", selected != null);
        commands.set("#NodeTypeControl.Visible", selected != null);
        commands.set("#NodeIdentityControls.Visible", selected != null);
        commands.set("#PathBonusControls.Visible", selected != null
                && CharacterPathEditorService.type(selected) == NodeType.PATH);
        commands.set("#NodeSettingsControls.Visible", selected != null
                && CharacterPathEditorService.type(selected) != NodeType.PATH);
        if (selected == null) {
            commands.set("#EditorSelectedName.Text", "No node selected");
            commands.set("#EditorSelectedType.Text", "Add a node or click one on the canvas.");
            commands.set("#EditorSelectedBonus.Text", "");
            commands.set("#EditorSelectedPosition.Text", "");
            commands.set("#EditorSelectedRank.Text", "");
            commands.set("#NodeXControl.Value", 0.0f);
            commands.set("#NodeYControl.Value", 0.0f);
            return;
        }
        NodeType type = CharacterPathEditorService.type(selected);
        commands.set("#NodeNameInput.Value", selected.name == null ? "" : selected.name);
        commands.set("#NodeIconInput.Value", selected.iconItemId == null ? "" : selected.iconItemId);
        commands.set("#NodeTexturePathInput.Value",
                selected.iconTexturePath == null ? "" : selected.iconTexturePath);
        commands.set("#NodeIconInput.Visible", type != NodeType.PATH);
        commands.set("#ApplyNodeIcon.Visible", type != NodeType.PATH);
        commands.set("#NodeTexturePathInput.Visible", type != NodeType.PATH);
        commands.set("#ApplyNodeTexturePath.Visible", type != NodeType.PATH);
        commands.set("#NodeXControl.Max", (float) Math.max(0, document.canvasWidth - type.size));
        commands.set("#NodeYControl.Max", (float) Math.max(0, document.canvasHeight - type.size));
        commands.set("#NodeXControl.Value", (float) selected.x);
        commands.set("#NodeYControl.Value", (float) selected.y);
        commands.set("#NodeTypeDropdown.Value", type.name());
        commands.set("#PathBonusDropdown.Value", switch (selected.bonus == null ? "MAX_HEALTH_FLAT" : selected.bonus) {
            case "MAX_MANA_FLAT" -> "MANA";
            case "MAX_STAMINA_FLAT" -> "STAMINA";
            default -> "HEALTH";
        });
        commands.set("#EditorSelectedName.Text", selected.name + "  ·  " + selected.id);
        commands.set("#EditorSelectedType.Text", "Type: " + type.name() + "   |   Category: "
                + CharacterPathEditorService.category(selected).name());
        if (selectedNodeIds.size() > 1) {
            commands.set("#EditorSelectedType.Text", "Type: " + type.name() + "   |   Category: "
                    + CharacterPathEditorService.category(selected).name()
                    + "   |   Selected: " + selectedNodeIds.size());
        }
        if (type == NodeType.PATH) {
            commands.set("#EditorSelectedBonus.Text", "Default stat: "
                    + CharacterPathEditorService.bonusLabel(CharacterPathEditorService.bonus(selected))
                    + " / " + CharacterPathEditorService.formatAmount(selected));
        } else {
            commands.set("#NodeCategoryDropdown.Value", CharacterPathEditorService.category(selected).name());
            List<EditorBonus> bonuses = selected.bonuses == null ? List.of() : selected.bonuses;
            int index = Math.max(0, Math.min(selected.selectedBonusIndex, Math.max(0, bonuses.size() - 1)));
            EditorBonus active = bonuses.isEmpty() ? null : bonuses.get(index);
            populateBonusSlotDropdown(commands, bonuses, index);
            commands.set("#BonusTypeDropdown.Value", active == null ? "DAMAGE_PERCENT" : active.bonus);
            boolean percent = active != null && isPercentBonus(active.bonus);
            commands.set("#BonusAmountInput.Value", active == null ? 0.0
                    : percent ? active.perRank * 100.0 : active.perRank);
            commands.set("#BonusAmountUnit.Text", percent ? "% / rank" : "/ rank");
            commands.set("#MaxRankInput.Value", (double) selected.maxRank);
            String activeText = active == null ? "No bonus configured"
                    : bonusLabel(active.bonus) + " · " + CharacterPathEditorService.formatAmount(selected);
            commands.set("#EditorSelectedBonus.Text", "Selected bonus " + (bonuses.isEmpty() ? 0 : index + 1)
                    + "/" + bonuses.size() + ": " + activeText);
        }
        commands.set("#EditorSelectedPosition.Text", "Canvas position: " + selected.x + ", " + selected.y);
        commands.set("#EditorSelectedRank.Text", "Maximum rank: " + selected.maxRank);
    }

    private static void populateBonusSlotDropdown(
            UICommandBuilder commands, List<EditorBonus> bonuses, int selectedIndex) {
        if (bonuses == null || bonuses.isEmpty()) {
            commands.set("#BonusSlotDropdown.Entries", List.<DropdownEntryInfo>of());
            return;
        }

        List<DropdownEntryInfo> entries = new ArrayList<>(bonuses.size());
        for (int i = 0; i < bonuses.size(); i++) {
            entries.add(new DropdownEntryInfo(
                    LocalizableString.fromString("Slot " + (i + 1)), Integer.toString(i)));
        }
        commands.set("#BonusSlotDropdown.Entries", entries);
        commands.set("#BonusSlotDropdown.Value", Integer.toString(selectedIndex));
    }

    private void bindSelectedNodeEvents(UIEventBuilder events) {
        if (selectedNodeId == null) return;
        bindDropdown(events, "#NodeTypeDropdown", "editor_type", selectedNodeId);
        bindDropdown(events, "#PathBonusDropdown", "editor_path_bonus", selectedNodeId);
        bindDropdown(events, "#NodeCategoryDropdown", "editor_category", selectedNodeId);
        bindDropdown(events, "#BonusSlotDropdown", "editor_bonus_slot", selectedNodeId);
        bindDropdown(events, "#BonusTypeDropdown", "editor_bonus_type", selectedNodeId);
        bindCoordinateControl(events, "#NodeXControl", "editor_position_x", selectedNodeId);
        bindCoordinateControl(events, "#NodeYControl", "editor_position_y", selectedNodeId);
        bindNumberField(events, "#BonusAmountInput", "editor_bonus_amount", selectedNodeId);
        bindNumberField(events, "#MaxRankInput", "editor_rank", selectedNodeId);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RenameNode",
                EventData.of("Action", "editor_rename").append("NodeId", selectedNodeId)
                        .append("@Name", "#NodeNameInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyNodeIcon",
                EventData.of("Action", "editor_icon").append("NodeId", selectedNodeId)
                        .append("@IconItemId", "#NodeIconInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyNodeTexturePath",
                EventData.of("Action", "editor_icon_texture").append("NodeId", selectedNodeId)
                        .append("@IconTexturePath", "#NodeTexturePathInput.Value"), false);
    }

    private static Map<String, CharacterPathEdgeRouter.Node> locations(Document document) {
        return locations(document, null, null);
    }

    private static Map<String, CharacterPathEdgeRouter.Node> locations(
            Document document, String overrideId, NodeType overrideType) {
        Map<String, CharacterPathEdgeRouter.Node> result = new HashMap<>();
        for (EditorNode node : document.nodes) {
            if (node == null || node.id == null) continue;
            NodeType type = node.id.equals(overrideId) && overrideType != null
                    ? overrideType : CharacterPathEditorService.type(node);
            result.put(node.id, new CharacterPathEdgeRouter.Node(node.id,
                    node.x + type.size / 2.0, node.y + type.size / 2.0,
                    type.size, type == NodeType.PATH));
        }
        return result;
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEvent event) {
        super.handleDataEvent(playerRef, store, event);
        if (event == null || event.action == null) return;
        if ("editor_select".equals(event.action) && suppressNextNodeActivation) {
            suppressNextNodeActivation = false;
            return;
        }
        if (!"editor_position_x".equals(event.action) && !"editor_position_y".equals(event.action)
                && !"editor_hover_enter".equals(event.action) && !"editor_hover_exit".equals(event.action)) {
            resetCoordinateLinkBlockCount();
            CharacterPathEditorService.commitPositionPreview();
            if (!"editor_position_commit".equals(event.action)) coordinateGridPreview = false;
        }
        if ("editor_hover_enter".equals(event.action)) {
            hoveredNodeId = event.nodeId;
            return;
        }
        if ("editor_hover_exit".equals(event.action)) {
            if (event.nodeId != null && event.nodeId.equals(hoveredNodeId)) hoveredNodeId = null;
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;
        if ("editor_grid_placement".equals(event.action)) {
            toggleGridPlacement();
            refreshGridPlacementOverlay();
            return;
        }
        if ("editor_copy_properties".equals(event.action)) {
            String previousLinkSource = connectFirstNodeId;
            beginCopyProperties();
            refreshCopyPropertiesToggleInPlace(previousLinkSource);
            return;
        }
        if ("editor_select".equals(event.action) && copyPropertiesSourceId != null) {
            selectNode(event.nodeId);
            return;
        }
        if ("editor_connect_mode".equals(event.action)) {
            toggleConnectMode();
            refreshSelectionInPlace();
            return;
        }
        if ("editor_select".equals(event.action)) {
            boolean canRefreshInPlace = !multiSelectMode && selectedNodeIds.size() <= 1
                    && copyPropertiesSourceId == null;
            String previousSelection = selectedNodeId;
            List<GraphEdge> edgesBefore = graphEdges(CharacterPathEditorService.snapshot());
            selectNode(event.nodeId);
            List<GraphEdge> edgesAfter = graphEdges(CharacterPathEditorService.snapshot());
            if (canRefreshInPlace && edgesBefore.equals(edgesAfter)) {
                Set<String> changedSelection = new LinkedHashSet<>();
                if (previousSelection != null) changedSelection.add(previousSelection);
                if (selectedNodeId != null) changedSelection.add(selectedNodeId);
                refreshSelectionInPlace(changedSelection);
            } else {
                refreshFullPage();
            }
            return;
        }
        switch (event.action) {
            case "editor_select" -> selectNode(event.nodeId);
            case "editor_add" -> addNode();
            case "editor_duplicate" -> duplicateNode();
            case "editor_mirror_flip" -> toggleMirrorFlip();
            case "editor_move_mode" -> toggleMoveMode();
            case "editor_multi_select" -> toggleMultiSelectMode();
            case "editor_select_all" -> selectAllNodes();
            case "editor_clear_selection" -> clearSelection();
            case "editor_grid_toggle" -> gridVisible = !gridVisible;
            case "editor_canvas_size_open" -> {
                Document document = CharacterPathEditorService.snapshot();
                canvasWidthInput = document.canvasWidth;
                canvasHeightInput = document.canvasHeight;
                canvasSizePopupVisible = true;
            }
            case "editor_canvas_size_cancel" -> canvasSizePopupVisible = false;
            case "editor_canvas_size_apply" -> applyCanvasSize(event);
            case "editor_place" -> {
                if (gridPlacementArmed) {
                    placeSelectedAt(event.target);
                    gridPlacementArmed = false;
                }
            }
            case "editor_position_x" -> {
                if (event.coordinateValue != null) {
                    int target = Math.round(event.coordinateValue);
                    boolean firstPreview = !coordinateGridPreview;
                    coordinateGridPreview = true;
                    boolean linksDetached = moveSelectedByCoordinate(target, null);
                    if (firstPreview || linksDetached) refreshPositionEditor();
                    else sendSelectedPositionUpdate();
                }
                return;
            }
            case "editor_position_y" -> {
                if (event.coordinateValue != null) {
                    int target = Math.round(event.coordinateValue);
                    boolean firstPreview = !coordinateGridPreview;
                    coordinateGridPreview = true;
                    boolean linksDetached = moveSelectedByCoordinate(null, target);
                    if (firstPreview || linksDetached) refreshPositionEditor();
                    else sendSelectedPositionUpdate();
                }
                return;
            }
            case "editor_position_commit" -> {
                if (!coordinateGridPreview) return;
                coordinateGridPreview = false;
                positionDragActive = gridPlacementArmed || pointerDragActive
                        || (moveMode && selectedNode() != null);
                CharacterPathEditorService.commitPositionPreview();
                status = "Node position committed.";
            }
            case "editor_bonus_amount" -> {
                EditorNode selected = selectedNode();
                if (selected != null && event.numberValue != null) {
                    double amount = isPercentBonus(selected.bonus)
                            ? event.numberValue / 100.0 : event.numberValue;
                    status = CharacterPathEditorService.setSelectedAmount(selected.id, amount)
                            ? "Bonus amount updated." : "Could not update the bonus amount.";
                }
            }
            case "editor_rank" -> {
                if (event.numberValue != null) {
                    status = CharacterPathEditorService.setMaxRank(event.nodeId,
                            (int) Math.round(event.numberValue)) ? "Maximum rank updated." : "Could not update maximum rank.";
                }
            }
            case "editor_dismiss" -> {
                endPointerDrag();
                positionDragActive = false;
                clearMouseState(owner);
                ACTIVE_PAGES.remove(owner.getUuid(), this);
                return;
            }
            case "editor_center" -> {
                centerOnNextPopulate = true;
                status = "Fitted the graph bounds to the canvas.";
            }
            case "editor_zoom" -> adjustZoom(event.target);
            case "editor_zoom_value" -> {
                if (event.zoomValue != null) {
                    zoomPercent = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, event.zoomValue));
                    status = "Canvas zoom set to " + zoomPercent + "%.";
                    long now = System.currentTimeMillis();
                    if (now - lastZoomRenderAtMillis < 100) return;
                    lastZoomRenderAtMillis = now;
                }
            }
            case "editor_zoom_commit" -> {
                lastZoomRenderAtMillis = System.currentTimeMillis();
                status = "Canvas zoom set to " + zoomPercent + "%.";
            }
            case "editor_remove" -> removeNode();
            case "editor_rename" -> {
                if (CharacterPathEditorService.renameNode(event.nodeId, event.name)) status = "Node renamed.";
                else status = "Enter a node name between 1 and 48 characters.";
            }
            case "editor_icon" -> {
                if (CharacterPathEditorService.setNodeIcon(event.nodeId, event.iconItemId)) {
                    status = event.iconItemId.isBlank() ? "Node icon cleared." : "Node icon updated.";
                } else status = "Use a valid item ID containing letters, numbers, _, . or -.";
            }
            case "editor_icon_texture" -> {
                if (CharacterPathEditorService.setNodeTexturePath(event.nodeId, event.iconTexturePath)) {
                    status = event.iconTexturePath.isBlank()
                            ? "Texture override cleared; item icon restored." : "Texture icon updated.";
                } else status = "Use a bundled or native asset path (no URL or parent-directory traversal).";
            }
            case "editor_type" -> {
                updateNodeType(event.nodeId, event.selection);
            }
            case "editor_path_bonus" -> updatePathBonus(event.nodeId, event.selection);
            case "editor_category" -> {
                status = CharacterPathEditorService.setNodeCategory(event.nodeId, event.selection)
                        ? "Node category updated." : "Could not update node category.";
            }
            case "editor_bonus_slot" -> {
                try {
                    status = CharacterPathEditorService.setSelectedBonusIndex(event.nodeId,
                            Integer.parseInt(event.selection)) ? "Selected bonus slot." : "Could not select that bonus slot.";
                } catch (NumberFormatException ignored) { status = "Invalid bonus slot."; }
            }
            case "editor_bonus_type" -> {
                status = CharacterPathEditorService.setSelectedBonusType(event.nodeId, event.selection)
                        ? "Bonus type updated." : "That bonus is already used on this node.";
            }
            case "editor_add_bonus" -> addBonus();
            case "editor_remove_bonus" -> removeBonus();
            case "editor_apply" -> {
                if (CharacterPathEditorService.applyToPlayers()) {
                    status = "Draft applied to players and saved.";
                    owner.sendMessage(Message.raw(status));
                } else status = "Could not apply the current draft.";
            }
            case "editor_player_view" -> {
                CharacterPathPage.open(owner, null, 100);
                return;
            }
            default -> { return; }
        }

        switch (event.action) {
            case "editor_bonus_amount", "editor_rank", "editor_rename", "editor_path_bonus",
                    "editor_bonus_slot", "editor_bonus_type",
                    "editor_add_bonus", "editor_remove_bonus", "editor_apply" ->
                    refreshEditorPanelInPlace(false);
            case "editor_category", "editor_icon", "editor_icon_texture" ->
                    refreshEditorPanelInPlace(true);
            default -> refreshFullPage();
        }
    }

    /** Panel edits do not rebuild the graph, reroute edges, or recreate grid targets. */
    private void refreshEditorPanelInPlace(boolean refreshIcon) {
        Document document = CharacterPathEditorService.snapshot();
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#EditorStatus.Text", status);
        commands.set("#EditorApplied.Text", document.appliedToPlayers
                ? "Draft is applied to players" : "Draft only · players unchanged");
        fillSelection(commands, document);

        if (refreshIcon && selectedNodeId != null) {
            EditorNode selected = selectedNode(document);
            Integer childIndex = renderedNodeChildIndices.get(selectedNodeId);
            if (selected != null && childIndex != null) {
                String itemId = selected.iconItemId == null ? "" : selected.iconItemId;
                String texturePath = selected.iconTexturePath == null ? "" : selected.iconTexturePath;
                boolean hasTexture = !texturePath.isBlank();
                commands.set(nested(childIndex, "NodeItemIcon", "ItemId"), itemId);
                commands.set(nested(childIndex, "NodeItemIcon", "Visible"),
                        !hasTexture && !itemId.isBlank()
                                && CharacterPathEditorService.type(selected) != NodeType.PATH);
                if (hasTexture) {
                    commands.setObject(nested(childIndex, "NodeTextureIcon", "Background"),
                            textureBackground(CharacterPathEditorService.nodeIconUiPath(texturePath)));
                }
                commands.set(nested(childIndex, "NodeTextureIcon", "Visible"),
                        hasTexture && CharacterPathEditorService.type(selected) != NodeType.PATH);
            }
        }
        sendUpdate(commands, false);
    }

    /** Refreshes selection state without rebuilding hundreds of hidden grid hit targets. */
    private void refreshSelectionInPlace() {
        refreshSelectionInPlace(null);
    }

    private void refreshSelectionInPlace(Set<String> changedSelection) {
        Document document = CharacterPathEditorService.snapshot();
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        appendSelectionRefresh(commands, events, document, changedSelection);
        sendUpdate(commands, events, false);
    }

    private void refreshCopyPropertiesToggleInPlace(String previousLinkSource) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#ToolCopyProperties.Text", copyPropertiesSourceId == null ? "P" : "P*");
        commands.set("#ToolLink.Text", connectMode ? "L*" : "L");
        commands.set("#ToolGrid.Text", gridPlacementArmed ? "G*" : "G");
        commands.set("#EditorStatus.Text", status);
        if (renderedMoveTargetsLayer) {
            commands.set("#GridMoveTargetsLayer.Visible", gridPlacementArmed);
        }
        if (renderedClearanceChildIndex >= 0) {
            commands.set(child(renderedClearanceChildIndex, ".Visible"), positionDragActive);
        }
        if (previousLinkSource != null && renderedPrimarySelectionHaloChildIndex >= 0) {
            commands.set(nested(renderedPrimarySelectionHaloChildIndex,
                    "NodeLinkSourceGlow", "Visible"), connectMode
                    && previousLinkSource.equals(selectedNodeId));
        }
        sendUpdate(commands, false);
    }

    private void appendSelectionRefresh(UICommandBuilder commands,
                                       UIEventBuilder events,
                                       Document document,
                                       Set<String> changedSelection) {
        EditorNode primary = selectedNode(document);

        commands.set("#ToolLink.Text", connectMode ? "L*" : "L");
        commands.set("#ToolGrid.Text", gridPlacementArmed ? "G*" : "G");
        commands.set("#ToolCopyProperties.Text", copyPropertiesSourceId == null ? "P" : "P*");
        commands.set("#EditorStatus.Text", status);
        commands.set("#EditorLinkCount.Text", "Links: " + document.edges.size());
        commands.set("#EditorApplied.Text", document.appliedToPlayers
                ? "Draft is applied to players" : "Draft only · players unchanged");
        if (renderedMoveTargetsLayer) {
            commands.set("#GridMoveTargetsLayer.Visible", gridPlacementArmed);
        }
        if (renderedClearanceChildIndex >= 0) {
            int size = primary == null ? 1 : scaled(CharacterPathEditorService.type(primary).size);
            int padding = Math.max(1, scaled(NODE_CLEARANCE));
            int centerX = primary == null ? 0 : canvasX(primary.x) + size / 2;
            int centerY = primary == null ? 0 : canvasY(primary.y) + size / 2;
            commands.setObject(child(renderedClearanceChildIndex, ".Anchor"),
                    anchor(centerX - size / 2 - padding, centerY - size / 2 - padding,
                            size + padding * 2, size + padding * 2));
            commands.set(child(renderedClearanceChildIndex, ".Visible"),
                    primary != null && positionDragActive);
        }
        for (EditorNode node : document.nodes) {
            if (node == null || node.id == null) continue;
            if (changedSelection != null && !changedSelection.contains(node.id)) continue;
            Integer childIndex = renderedNodeChildIndices.get(node.id);
            if (childIndex == null) continue;
            boolean selected = node.id.equals(selectedNodeId);
            setNodeFrame(commands, childIndex, CharacterPathEditorService.type(node), selected);
            commands.set(nested(childIndex, "NodeSelectionTint", "Visible"),
                    selectedNodeIds.contains(node.id));
        }

        if (renderedPrimarySelectionHaloChildIndex >= 0) {
            String selector = child(renderedPrimarySelectionHaloChildIndex, "");
            if (primary == null) {
                commands.setObject(selector + ".Anchor", anchor(0, 0, 1, 1));
            } else {
                int size = scaled(CharacterPathEditorService.type(primary).size);
                int padding = Math.max(4, scaled(10));
                int diameter = size + padding * 2;
                int centerX = canvasX(primary.x) + size / 2;
                int centerY = canvasY(primary.y) + size / 2;
                commands.setObject(selector + ".Anchor", anchor(centerX - diameter / 2,
                        centerY - diameter / 2, diameter, diameter));
            }
            commands.set(nested(renderedPrimarySelectionHaloChildIndex,
                    "NodeLinkSourceGlow", "Visible"), primary != null && connectMode
                    && primary.id.equals(connectFirstNodeId));
            commands.set(nested(renderedPrimarySelectionHaloChildIndex,
                    "NodePrimaryGroupGlow", "Visible"), false);
            commands.set(nested(renderedPrimarySelectionHaloChildIndex,
                    "NodeGroupGlow", "Visible"), false);
            commands.set(selector + ".Visible", primary != null);
        }

        fillSelection(commands, document);
        bindSelectedNodeEvents(events);
    }

    private void refreshFullPage() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populate(commands, events, true);
        sendUpdate(commands, events, false);
    }

    private void selectNode(String id) {
        if (id == null) return;
        hoveredNodeId = id;
        gridPlacementArmed = false;
        positionDragActive = false;
        if (copyPropertiesSourceId != null) {
            String sourceId = copyPropertiesSourceId;
            copyPropertiesSourceId = null;
            copyPropertiesTo(sourceId, id);
            return;
        }
        if (!connectMode) {
            if (multiSelectMode) {
                if (selectedNodeIds.remove(id)) {
                    if (id.equals(selectedNodeId)) {
                        selectedNodeId = selectedNodeIds.stream().reduce((first, second) -> second).orElse(null);
                    }
                    status = selectedNodeIds.isEmpty()
                            ? "Selection cleared."
                            : "Removed " + id + " · " + selectedNodeIds.size() + " nodes selected.";
                } else {
                    selectedNodeIds.add(id);
                    selectedNodeId = id;
                    status = "Added " + id + " · " + selectedNodeIds.size()
                            + " selected. Teal rings mark the group; gold marks its anchor.";
                }
            } else {
                setSingleSelection(id);
                status = "Selected " + id + ".";
            }
            return;
        }
        if (connectFirstNodeId == null) {
            connectFirstNodeId = id;
            setSingleSelection(id);
            status = "Orange ring marks the link source. Choose a target to link or unlink.";
            return;
        }
        if (connectFirstNodeId.equals(id)) {
            status = "Choose a different node for the other endpoint.";
            return;
        }
        String first = connectFirstNodeId;
        connectFirstNodeId = null;
        setSingleSelection(id);
        if (CharacterPathEditorService.hasEdge(first, id)) {
            CharacterPathEditorService.disconnect(first, id);
            status = "Link removed.";
            return;
        }
        if (!CharacterPathEditorService.connect(first, id)) {
            status = "Could not create that link.";
            return;
        }
        try {
            Document candidate = CharacterPathEditorService.snapshot();
            CharacterPathEdgeRouter.route(graphEdges(candidate), locations(candidate),
                    candidate.canvasWidth, candidate.canvasHeight);
            status = "Nodes linked with a clear route.";
        } catch (IllegalStateException exception) {
            CharacterPathEditorService.disconnect(first, id);
            status = "Link rejected: it would cross another link or node.";
        }
    }

    private void beginCopyProperties() {
        if (copyPropertiesSourceId != null) {
            copyPropertiesSourceId = null;
            status = "Property copy canceled.";
            return;
        }
        EditorNode source = selectedNode();
        if (source == null) {
            status = "Select a source node before copying its properties.";
            return;
        }
        connectMode = false;
        connectFirstNodeId = null;
        gridPlacementArmed = false;
        positionDragActive = false;
        copyPropertiesSourceId = source.id;
        status = "Copying properties from " + source.name
                + ". Click a destination node; its position, identity and links will stay.";
    }

    private void copyPropertiesTo(String sourceId, String targetId) {
        if (sourceId.equals(targetId)) {
            status = "Choose a different destination node; property copy canceled.";
            refreshSelectionInPlace(Set.of(sourceId));
            return;
        }
        Document before = CharacterPathEditorService.snapshot();
        EditorNode source = findNode(before, sourceId);
        EditorNode target = findNode(before, targetId);
        if (source == null || target == null) {
            status = "Property copy canceled because the source or destination no longer exists.";
            refreshSelectionInPlace(Set.of(sourceId, targetId));
            return;
        }
        String sourceName = source.name;
        String targetName = target.name;
        boolean hadGroupSelection = selectedNodeIds.size() > 1;
        List<CharacterPathEdgeRouter.RoutedEdge> validatedRoutes = new ArrayList<>();
        boolean copied = CharacterPathEditorService.copyNodeProperties(sourceId, targetId, candidate -> {
            EditorNode candidateTarget = findNode(candidate, targetId);
            if (candidateTarget == null) return false;
            int targetSize = CharacterPathEditorService.type(candidateTarget).size;
            candidateTarget.x = Math.max(0, Math.min(candidateTarget.x,
                    candidate.canvasWidth - CANVAS_X_OFFSET - targetSize));
            candidateTarget.y = Math.max(0, Math.min(candidateTarget.y,
                    candidate.canvasHeight - targetSize));
            if (!movedNodesHaveClearance(Set.of(targetId), candidate)) return false;
            try {
                validatedRoutes.clear();
                validatedRoutes.addAll(CharacterPathEdgeRouter.route(graphEdges(candidate), locations(candidate),
                        candidate.canvasWidth, candidate.canvasHeight));
                return true;
            } catch (IllegalStateException exception) {
                return false;
            }
        });
        if (!copied) {
            status = "Property copy blocked: the copied node size or links would overlap the graph.";
            refreshSelectionInPlace(Set.of(sourceId, targetId));
            return;
        }
        setSingleSelection(targetId);
        status = "Copied properties from " + sourceName + " to " + targetName
                + "; the destination position, identity and links were preserved.";
        if (hadGroupSelection || !refreshCopiedPropertiesInPlace(sourceId, targetId, validatedRoutes)) {
            refreshFullPage();
        }
    }

    private boolean refreshCopiedPropertiesInPlace(String sourceId,
                                                    String targetId,
                                                    List<CharacterPathEdgeRouter.RoutedEdge> routes) {
        Document document = CharacterPathEditorService.snapshot();
        EditorNode target = findNode(document, targetId);
        Integer targetChildIndex = renderedNodeChildIndices.get(targetId);
        if (target == null || targetChildIndex == null || treeViewport == null) return false;

        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        if (!updateRenderedEdges(commands, routes)) return false;
        updateCopiedNodeVisual(commands, targetChildIndex, target);
        rebindMoveTargetCells(events, document, target);

        Set<String> changedSelection = new LinkedHashSet<>();
        if (sourceId != null) changedSelection.add(sourceId);
        changedSelection.add(targetId);
        appendSelectionRefresh(commands, events, document, changedSelection);
        sendUpdate(commands, events, false);
        return true;
    }

    private void updateCopiedNodeVisual(UICommandBuilder commands, int childIndex, EditorNode node) {
        NodeType type = CharacterPathEditorService.type(node);
        int size = scaled(type.size);
        commands.setObject(child(childIndex, ".Anchor"), anchor(canvasX(node.x), canvasY(node.y), size, size));
        setNodeFrame(commands, childIndex, type, node.id.equals(selectedNodeId));

        boolean pathNode = type == NodeType.PATH;
        String itemId = pathNode || node.iconItemId == null ? "" : node.iconItemId;
        String texturePath = pathNode || node.iconTexturePath == null ? "" : node.iconTexturePath;
        int iconSize = Math.max(10, (int) Math.round(size * 0.58));
        Anchor iconAnchor = anchor((size - iconSize) / 2, (size - iconSize) / 2, iconSize, iconSize);
        commands.set(nested(childIndex, "NodeItemIcon", "ItemId"), itemId);
        commands.set(nested(childIndex, "NodeItemIcon", "Visible"),
                texturePath.isBlank() && !itemId.isBlank());
        commands.setObject(nested(childIndex, "NodeItemIcon", "Anchor"), iconAnchor);
        if (!texturePath.isBlank()) {
            commands.setObject(nested(childIndex, "NodeTextureIcon", "Background"), textureBackground(
                    CharacterPathEditorService.nodeIconUiPath(texturePath)));
        }
        commands.set(nested(childIndex, "NodeTextureIcon", "Visible"), !texturePath.isBlank());
        commands.setObject(nested(childIndex, "NodeTextureIcon", "Anchor"), iconAnchor);
    }

    private void rebindMoveTargetCells(UIEventBuilder events, Document document, EditorNode selected) {
        if (!renderedMoveTargetsLayer || selected == null) return;
        int nodeSize = CharacterPathEditorService.type(selected).size;
        int maxX = document.canvasWidth - CANVAS_X_OFFSET - nodeSize;
        int maxY = document.canvasHeight - nodeSize;
        List<Integer> xBoundaries = centeredGridBoundaries(document.canvasWidth, gridCellSize());
        List<Integer> yBoundaries = centeredGridBoundaries(document.canvasHeight, gridCellSize());
        int targetIndex = 0;
        for (int yi = 0; yi < yBoundaries.size() - 1; yi++) {
            int y = yBoundaries.get(yi);
            int height = yBoundaries.get(yi + 1) - y;
            for (int xi = 0; xi < xBoundaries.size() - 1; xi++) {
                int x = xBoundaries.get(xi);
                int width = xBoundaries.get(xi + 1) - x;
                int nodeX = Math.max(0, Math.min(maxX, x + width / 2 - nodeSize / 2));
                int nodeY = Math.max(0, Math.min(maxY, y + height / 2 - nodeSize / 2));
                String targetChild = "#GridMoveTargetsLayer[" + targetIndex++ + "]";
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        targetChild + " #MoveTarget",
                        EventData.of("Action", "editor_place")
                                .append("Target", nodeX + "," + nodeY), false);
            }
        }
    }

    private static EditorNode findNode(Document document, String id) {
        if (document == null || document.nodes == null || id == null) return null;
        return document.nodes.stream().filter(node -> node != null && id.equals(node.id))
                .findFirst().orElse(null);
    }

    private void addNode() {
        copyPropertiesSourceId = null;
        Document document = CharacterPathEditorService.snapshot();
        EditorNode selected = selectedNode(document);
        int size = NodeType.SMALL.size;
        int[] position = findFreePositionNear(document, selected, size);
        if (position == null) { status = "No clear space is available near the selected node."; return; }
        EditorNode node = CharacterPathEditorService.addNode(position[0], position[1]);
        boolean linked = !connectMode || selected == null || linkNewNode(selected.id, node.id);
        setSingleSelection(node.id);
        gridPlacementArmed = false;
        positionDragActive = false;
        status = "Added " + node.name + (selected == null ? " at the canvas center" : " beside " + selected.name)
                + (linked ? "." : "; its link could not be routed.");
    }

    private boolean moveSelectedTo(Integer x, Integer y) {
        EditorNode selected = selectedNode();
        positionDragActive = true;
        if (selected == null) { status = "Select a node before moving it."; return false; }
        int targetX = x == null ? selected.x : x;
        int targetY = y == null ? selected.y : y;
        return moveSelectionBy(targetX - selected.x, targetY - selected.y);
    }

    /** Detaches a node's links only after repeated slider moves that those links alone block. */
    private boolean moveSelectedByCoordinate(Integer x, Integer y) {
        EditorNode selected = selectedNode();
        boolean singleNodeSelection = selected != null && selectedNodeIds.size() == 1
                && selectedNodeIds.contains(selected.id);
        moveSelectedTo(x, y);
        if (!singleNodeSelection || !lastMoveBlockedByDetachableLinks) {
            resetCoordinateLinkBlockCount();
            return false;
        }

        if (!selected.id.equals(coordinateBlockedNodeId)) {
            coordinateBlockedNodeId = selected.id;
            coordinateLinkBlockCount = 0;
        }
        coordinateLinkBlockCount++;
        if (coordinateLinkBlockCount < COORDINATE_LINK_DETACH_LIMIT) return false;

        int detached = CharacterPathEditorService.disconnectNodeEdges(selected.id);
        resetCoordinateLinkBlockCount();
        if (detached == 0) return false;

        boolean movedAfterDetaching = moveSelectedTo(x, y);
        if (movedAfterDetaching) {
            EditorNode current = selectedNode();
            status = "Detached " + detached + " link" + (detached == 1 ? "" : "s")
                    + " after repeated blocked coordinate moves; moved "
                    + (current == null ? "the node" : current.name) + ".";
        } else {
            status = "Detached " + detached + " link" + (detached == 1 ? "" : "s")
                    + " after repeated blocked coordinate moves, but this position is still blocked.";
        }
        return true;
    }

    private void resetCoordinateLinkBlockCount() {
        coordinateBlockedNodeId = null;
        coordinateLinkBlockCount = 0;
    }

    private void toggleMoveMode() {
        copyPropertiesSourceId = null;
        moveMode = !moveMode;
        if (!moveMode) endPointerDrag(false);
        positionDragActive = moveMode && selectedNode() != null;
        CharacterPathEditorService.commitPositionPreview();
        status = moveMode
                ? "Move mode on. Drag any selected node to move the selection as a group."
                : "Move mode off.";
    }

    private void toggleMultiSelectMode() {
        multiSelectMode = !multiSelectMode;
        if (selectedNodeId != null) selectedNodeIds.add(selectedNodeId);
        status = multiSelectMode
                ? "Multi-select on. Click nodes to add or remove them, enable M, then drag any selected node."
                : "Multi-select off. Canvas clicks select one node.";
    }

    private void toggleMirrorFlip() {
        mirrorVertically = !mirrorVertically;
        status = mirrorVertically
                ? "Group duplicate mirror set to top/bottom across the canvas center."
                : "Group duplicate mirror set to left/right across the canvas center.";
    }

    private void selectAllNodes() {
        copyPropertiesSourceId = null;
        Document document = CharacterPathEditorService.snapshot();
        selectedNodeIds.clear();
        for (EditorNode node : document.nodes) {
            if (node != null && node.id != null) selectedNodeIds.add(node.id);
        }
        if (selectedNodeId == null || !selectedNodeIds.contains(selectedNodeId)) {
            selectedNodeId = selectedNodeIds.stream().findFirst().orElse(null);
        }
        multiSelectMode = true;
        gridPlacementArmed = false;
        positionDragActive = false;
        status = "Selected all " + selectedNodeIds.size() + " nodes. Enable M, then drag any selected node to move the group.";
    }

    private void clearSelection() {
        selectedNodeIds.clear();
        selectedNodeId = null;
        copyPropertiesSourceId = null;
        positionDragActive = false;
        gridPlacementArmed = false;
        status = "Selection cleared.";
    }

    private void setSingleSelection(String id) {
        selectedNodeIds.clear();
        selectedNodeId = id;
        if (id != null) selectedNodeIds.add(id);
    }

    private void toggleGridPlacement() {
        copyPropertiesSourceId = null;
        if (selectedNode() == null) {
            gridPlacementArmed = false;
            status = "Select a node before arming grid placement.";
            return;
        }
        gridPlacementArmed = !gridPlacementArmed;
        if (gridPlacementArmed) {
            endPointerDrag(false);
            positionDragActive = true;
            status = "Grid placement armed. Click one canvas cell to place the selected node.";
        } else {
            positionDragActive = pointerDragActive || (moveMode && selectedNode() != null);
            status = "Grid placement off. Canvas clicks select nodes normally.";
        }
    }

    /** Toggle only the grid hit targets; rebuilding nodes and routed links made this tool feel delayed. */
    private void refreshGridPlacementOverlay() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#ToolGrid.Text", gridPlacementArmed ? "G*" : "G");
        commands.set("#EditorStatus.Text", status);
        if (renderedClearanceChildIndex >= 0) {
            commands.set(child(renderedClearanceChildIndex, ".Visible"), positionDragActive);
        }
        if (renderedMoveTargetsLayer) {
            commands.set("#GridMoveTargetsLayer.Visible", gridPlacementArmed);
        } else if (gridPlacementArmed) {
            appendMoveTargets(commands, events, CharacterPathEditorService.snapshot(), 0);
        }
        sendUpdate(commands, events, false);
    }

    /** Starts a free drag on press when move mode is active. */
    public static void onMouseButton(PlayerMouseButtonEvent event) {
        if (event == null || event.getPlayer() == null || event.getPlayer().getUuid() == null
                || event.getMouseButton() == null
                || event.getMouseButton().mouseButtonType != MouseButtonType.Left) return;
        UUID playerId = event.getPlayer().getUuid();
        boolean pressed = event.getMouseButton().state == MouseButtonState.Pressed;
        CharacterPathEditorPage page = ACTIVE_PAGES.get(playerId);
        if (page == null) return;
        if (pressed) page.beginPointerDrag();
        else page.endPointerDrag();
    }

    /** Mouse motion packets include the buttons currently held, keeping state correct during drags. */
    public static void onMouseMotion(PlayerMouseMotionEvent event) {
        if (event == null || event.getPlayer() == null || event.getPlayer().getUuid() == null
                || event.getMouseMotion() == null) return;
        UUID playerId = event.getPlayer().getUuid();
        MouseButtonType[] heldButtons = event.getMouseMotion().mouseButtonType;
        boolean pressed = heldButtons != null && Arrays.asList(heldButtons).contains(MouseButtonType.Left);
        CharacterPathEditorPage page = ACTIVE_PAGES.get(playerId);
        if (page == null) return;
        if (!pressed) {
            page.endPointerDrag();
            return;
        }
        if (!page.pointerDragActive) page.beginPointerDrag();
        if (!page.pointerDragActive || event.getMouseMotion().relativeMotion == null) return;
        page.dragByMouseMotion(event.getMouseMotion().relativeMotion.x,
                event.getMouseMotion().relativeMotion.y);
    }

    public static void clearMouseState(PlayerRef playerRef) {
        if (playerRef != null && playerRef.getUuid() != null) {
            CharacterPathEditorPage page = ACTIVE_PAGES.remove(playerRef.getUuid());
            if (page != null) page.endPointerDrag(false);
        }
    }

    private void beginPointerDrag() {
        suppressNextNodeActivation = false;
        if (!moveMode || gridPlacementArmed || selectedNode() == null
                || selectedNodeId == null || !selectedNodeIds.contains(hoveredNodeId)) return;
        positionDragActive = true;
        pointerDragActive = true;
        pointerPositionChanged = false;
        dragRemainderX = 0;
        dragRemainderY = 0;
    }

    private void endPointerDrag() {
        endPointerDrag(true);
    }

    private void endPointerDrag(boolean refreshCanvas) {
        if (!pointerDragActive) return;
        pointerDragActive = false;
        dragRemainderX = 0;
        dragRemainderY = 0;
        CharacterPathEditorService.commitPositionPreview();
        if (pointerPositionChanged && refreshCanvas && owner != null && owner.isValid()
                && owner.getUuid() != null
                && ACTIVE_PAGES.get(owner.getUuid()) == this) {
            pointerPositionChanged = false;
            suppressNextNodeActivation = true;
            refreshPositionEditor();
        } else {
            pointerPositionChanged = false;
            suppressNextNodeActivation = false;
        }
    }

    private void dragByMouseMotion(int screenDx, int screenDy) {
        if (!moveMode || !pointerDragActive || (screenDx == 0 && screenDy == 0)) return;
        double canvasUnitsPerScreenPixel = 100.0 / zoomPercent;
        dragRemainderX += screenDx * canvasUnitsPerScreenPixel;
        dragRemainderY += screenDy * canvasUnitsPerScreenPixel;
        int dx = (int) Math.round(dragRemainderX);
        int dy = (int) Math.round(dragRemainderY);
        dragRemainderX -= dx;
        dragRemainderY -= dy;
        if (!moveSelectionBy(dx, dy)) return;
        pointerPositionChanged = true;
        sendSelectedPositionUpdate();
    }

    private void sendSelectedPositionUpdate() {
        EditorNode selected = selectedNode();
        Integer nodeIndex = selected == null ? null : renderedNodeChildIndices.get(selected.id);
        if (selected == null || nodeIndex == null) return;
        UICommandBuilder commands = new UICommandBuilder();
        Document document = CharacterPathEditorService.snapshot();
        for (EditorNode node : document.nodes) {
            if (node == null || !selectedNodeIds.contains(node.id)) continue;
            Integer childIndex = renderedNodeChildIndices.get(node.id);
            if (childIndex == null) continue;
            int nodeSize = scaled(CharacterPathEditorService.type(node).size);
            commands.setObject(child(childIndex, ".Anchor"),
                    anchor(canvasX(node.x), canvasY(node.y), nodeSize, nodeSize));
        }
        int size = scaled(CharacterPathEditorService.type(selected).size);
        if (renderedClearanceChildIndex >= 0) {
            int radius = Math.max(1, scaled(NODE_CLEARANCE));
            int diameter = size + radius * 2;
            int centerX = canvasX(selected.x) + size / 2;
            int centerY = canvasY(selected.y) + size / 2;
            commands.setObject(child(renderedClearanceChildIndex, ".Anchor"),
                    anchor(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter));
            commands.set(child(renderedClearanceChildIndex, ".Visible"), positionDragActive);
        }
        commands.set("#NodeXControl.Value", (float) selected.x);
        commands.set("#NodeYControl.Value", (float) selected.y);
        commands.set("#EditorSelectedPosition.Text", "Canvas position: " + selected.x + ", " + selected.y);
        commands.set("#EditorStatus.Text", status);
        sendUpdate(commands, false);
    }

    private void refreshPositionEditor() {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populate(commands, events, true);
        sendUpdate(commands, events, false);
    }

    private boolean moveSelectedByLegacy(int dx, int dy) {
        EditorNode selected = selectedNode();
        if (selected == null || (dx == 0 && dy == 0)) return false;
        int size = CharacterPathEditorService.type(selected).size;
        int nextX = Math.max(0, Math.min(canvasWidth - size, selected.x + dx));
        int nextY = Math.max(0, Math.min(canvasHeight - size, selected.y + dy));
        if (selected.x == nextX && selected.y == nextY) return false;
        Document before = CharacterPathEditorService.snapshot();
        boolean clear = positionIsClear(selected.id, nextX, nextY, size, before);
        if (!CharacterPathEditorService.previewNodePosition(selected.id, nextX, nextY)) return false;
        try {
            Document after = CharacterPathEditorService.snapshot();
            CharacterPathEdgeRouter.route(graphEdges(after), locations(after), after.canvasWidth, after.canvasHeight);
            status = clear
                    ? "Canvas position: " + nextX + ", " + nextY
                    : "Moved to " + nextX + ", " + nextY + " · clearance overlap";
        } catch (IllegalStateException exception) {
            status = "Moved to " + nextX + ", " + nextY + " · links need rerouting";
        }
        return true;
    }

    private boolean moveSelectedBy(int dx, int dy) {
        return moveSelectionBy(dx, dy);
    }

    /** Applies one shared translation to every selected node while preserving their layout. */
    private boolean moveSelectionBy(int requestedDx, int requestedDy) {
        lastMoveBlockedByDetachableLinks = false;
        if (requestedDx == 0 && requestedDy == 0) return false;
        Document before = CharacterPathEditorService.snapshot();
        EditorNode primary = selectedNode(before);
        if (primary == null) { status = "Select a node before moving it."; return false; }
        int primaryX = primary.x;
        int primaryY = primary.y;

        Set<String> movingIds = new LinkedHashSet<>(selectedNodeIds);
        movingIds.add(primary.id);
        movingIds.removeIf(id -> before.nodes.stream().noneMatch(node -> node != null && id.equals(node.id)));
        int minDx = Integer.MIN_VALUE;
        int maxDx = Integer.MAX_VALUE;
        int minDy = Integer.MIN_VALUE;
        int maxDy = Integer.MAX_VALUE;
        for (EditorNode node : before.nodes) {
            if (node == null || !movingIds.contains(node.id)) continue;
            int size = CharacterPathEditorService.type(node).size;
            minDx = Math.max(minDx, -node.x);
            maxDx = Math.min(maxDx,
                    before.canvasWidth - CharacterPathEditorService.CANVAS_X_OFFSET - size - node.x);
            minDy = Math.max(minDy, -node.y);
            maxDy = Math.min(maxDy, before.canvasHeight - size - node.y);
        }
        int dx = Math.max(minDx, Math.min(maxDx, requestedDx));
        int dy = Math.max(minDy, Math.min(maxDy, requestedDy));
        if (dx == 0 && dy == 0) return false;

        Document candidate = before;
        for (EditorNode node : candidate.nodes) {
            if (node != null && movingIds.contains(node.id)) {
                node.x += dx;
                node.y += dy;
            }
        }
        if (!movedNodesHaveClearance(movingIds, candidate)) {
            status = "Move blocked: keep clearance around the selected group.";
            return false;
        }
        try {
            CharacterPathEdgeRouter.route(graphEdges(candidate), locations(candidate),
                    candidate.canvasWidth, candidate.canvasHeight);
        } catch (IllegalStateException exception) {
            lastMoveBlockedByDetachableLinks = movingIds.size() == 1
                    && routeWorksWithoutNodeLinks(candidate, primary.id);
            status = "Move blocked: a link would cross another link or node.";
            return false;
        }
        for (EditorNode node : candidate.nodes) {
            if (node != null && movingIds.contains(node.id)) {
                CharacterPathEditorService.previewNodePosition(node.id, node.x, node.y);
            }
        }
        status = movingIds.size() == 1
                ? "Moved node to " + (primaryX + dx) + ", " + (primaryY + dy) + "."
                : "Moved " + movingIds.size() + " nodes by " + dx + ", " + dy + ".";
        return true;
    }

    private static boolean routeWorksWithoutNodeLinks(Document document, String nodeId) {
        List<GraphEdge> remainingEdges = graphEdges(document).stream()
                .filter(edge -> !nodeId.equals(edge.first()) && !nodeId.equals(edge.second()))
                .toList();
        try {
            CharacterPathEdgeRouter.route(remainingEdges, locations(document),
                    document.canvasWidth, document.canvasHeight);
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private static boolean movedNodesHaveClearance(Set<String> movingIds, Document document) {
        List<EditorNode> moved = document.nodes.stream()
                .filter(node -> node != null && movingIds.contains(node.id)).toList();
        for (EditorNode node : moved) {
            int size = CharacterPathEditorService.type(node).size;
            for (EditorNode other : document.nodes) {
                if (other == null || node.id.equals(other.id) || movingIds.contains(other.id)) continue;
                int otherSize = CharacterPathEditorService.type(other).size;
                double distance = Math.hypot(node.x + size / 2.0 - (other.x + otherSize / 2.0),
                        node.y + size / 2.0 - (other.y + otherSize / 2.0));
                if (distance < (size + otherSize) / 2.0 + NODE_CLEARANCE) return false;
            }
        }
        return true;
    }

    private static boolean duplicatedNodesHaveClearance(Set<String> duplicatedIds, Document document) {
        List<EditorNode> duplicated = document.nodes.stream()
                .filter(node -> node != null && duplicatedIds.contains(node.id)).toList();
        for (EditorNode node : duplicated) {
            int size = CharacterPathEditorService.type(node).size;
            for (EditorNode other : document.nodes) {
                if (other == null || node.id.equals(other.id)) continue;
                int otherSize = CharacterPathEditorService.type(other).size;
                double distance = Math.hypot(node.x + size / 2.0 - (other.x + otherSize / 2.0),
                        node.y + size / 2.0 - (other.y + otherSize / 2.0));
                if (distance < (size + otherSize) / 2.0 + NODE_CLEARANCE) return false;
            }
        }
        return true;
    }

    private void placeSelectedAt(String encodedPosition) {
        if (encodedPosition == null) return;
        int separator = encodedPosition.indexOf(',');
        if (separator < 0) return;
        try {
            EditorNode selected = selectedNode();
            if (selected == null) {
                status = "Select a node before placing it.";
                return;
            }
            int x = Integer.parseInt(encodedPosition.substring(0, separator));
            int y = Integer.parseInt(encodedPosition.substring(separator + 1));
            if (moveSelectedBy(x - selected.x, y - selected.y)) {
                CharacterPathEditorService.commitPositionPreview();
                positionDragActive = true;
            }
        } catch (NumberFormatException ignored) {
            status = "Invalid canvas position.";
        }
    }

    private void applyCanvasSize(PageEvent event) {
        if (event.canvasWidthValue == null || event.canvasHeightValue == null
                || !Double.isFinite(event.canvasWidthValue) || !Double.isFinite(event.canvasHeightValue)) {
            status = "Enter a valid canvas width and height.";
            return;
        }
        canvasWidthInput = (int) Math.round(event.canvasWidthValue);
        canvasHeightInput = (int) Math.round(event.canvasHeightValue);
        if (canvasWidthInput < CharacterPathEditorService.MIN_CANVAS_WIDTH
                || canvasWidthInput > CharacterPathEditorService.MAX_CANVAS_WIDTH
                || canvasHeightInput < CharacterPathEditorService.MIN_CANVAS_HEIGHT
                || canvasHeightInput > CharacterPathEditorService.MAX_CANVAS_HEIGHT) {
            status = "Use sizes from 600 to 10,000.";
            return;
        }
        Document current = CharacterPathEditorService.snapshot();
        try {
            CharacterPathEdgeRouter.route(graphEdges(current), locations(current),
                    canvasWidthInput, canvasHeightInput);
        } catch (IllegalStateException exception) {
            status = "Resize blocked: all nodes and links must fit with clear routes.";
            return;
        }
        if (!CharacterPathEditorService.setCanvasSize(canvasWidthInput, canvasHeightInput)) {
            status = "Use sizes from 600 to 10,000 that keep every node inside the canvas.";
            return;
        }
        Document resized = CharacterPathEditorService.snapshot();
        canvasWidth = resized.canvasWidth;
        canvasHeight = resized.canvasHeight;
        canvasWidthInput = canvasWidth;
        canvasHeightInput = canvasHeight;
        canvasSizePopupVisible = false;
        status = "Canvas resized to " + canvasWidth + " x " + canvasHeight + ".";
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        endPointerDrag(false);
        positionDragActive = false;
        CharacterPathEditorService.commitPositionPreview();
        clearMouseState(owner);
        if (owner != null && owner.getUuid() != null) ACTIVE_PAGES.remove(owner.getUuid(), this);
        super.onDismiss(playerRef, store);
    }

    private boolean linkNewNode(String first, String second) {
        if (!CharacterPathEditorService.connect(first, second)) return false;
        try {
            Document candidate = CharacterPathEditorService.snapshot();
            CharacterPathEdgeRouter.route(graphEdges(candidate), locations(candidate),
                    candidate.canvasWidth, candidate.canvasHeight);
            return true;
        } catch (IllegalStateException exception) {
            CharacterPathEditorService.disconnect(first, second);
            return false;
        }
    }

    private void adjustZoom(String target) {
        if ("reset".equals(target)) zoomPercent = 100;
        status = "Canvas zoom set to " + zoomPercent + "%.";
    }

    private static int[] findFreePositionNear(Document document, EditorNode selected, int size) {
        int maxX = document.canvasWidth - size;
        int maxY = document.canvasHeight - size;
        if (document.nodes.isEmpty()) return new int[] {
                (document.canvasWidth - size) / 2, (document.canvasHeight - size) / 2};

        double originX = selected == null ? document.canvasWidth / 2.0
                : selected.x + CharacterPathEditorService.type(selected).size / 2.0;
        double originY = selected == null ? document.canvasHeight / 2.0
                : selected.y + CharacterPathEditorService.type(selected).size / 2.0;
        int selectedSize = selected == null ? size : CharacterPathEditorService.type(selected).size;
        int firstRadius = (selectedSize + size) / 2 + NODE_CLEARANCE;
        int radiusStep = Math.max(48, size + NODE_CLEARANCE * 2);
        NodeType candidateType = java.util.Arrays.stream(NodeType.values())
                .filter(type -> type.size == size).findFirst().orElse(NodeType.SMALL);

        for (int radius = firstRadius; radius <= Math.max(document.canvasWidth, document.canvasHeight); radius += radiusStep) {
            for (int angle = 0; angle < 360; angle += 15) {
                double radians = Math.toRadians(angle);
                int x = Math.max(0, Math.min(maxX, (int) Math.round(originX + Math.cos(radians) * radius - size / 2.0)));
                int y = Math.max(0, Math.min(maxY, (int) Math.round(originY + Math.sin(radians) * radius - size / 2.0)));
                if (!positionIsClear("__placement_probe", x, y, size, document)) continue;
                EditorNode probe = new EditorNode();
                probe.id = "__placement_probe";
                probe.type = candidateType.name();
                probe.x = x;
                probe.y = y;
                document.nodes.add(probe);
                boolean routesRemainClear;
                try {
                    CharacterPathEdgeRouter.route(graphEdges(document), locations(document),
                            document.canvasWidth, document.canvasHeight);
                    routesRemainClear = true;
                } catch (IllegalStateException exception) {
                    routesRemainClear = false;
                } finally {
                    document.nodes.remove(probe);
                }
                if (routesRemainClear) return new int[] {x, y};
            }
        }
        return null;
    }

    private EditorNode selectedNode(Document document) {
        if (document == null || document.nodes == null || selectedNodeId == null) return null;
        return document.nodes.stream().filter(node -> node != null && selectedNodeId.equals(node.id))
                .findFirst().orElse(null);
    }

    private void duplicateNode() {
        copyPropertiesSourceId = null;
        Document before = CharacterPathEditorService.snapshot();
        EditorNode selected = selectedNode(before);
        if (selected == null) { status = "Select a node to duplicate first."; return; }
        LinkedHashSet<String> groupIds = new LinkedHashSet<>(selectedNodeIds);
        groupIds.add(selected.id);
        groupIds.removeIf(id -> findNode(before, id) == null);
        if (groupIds.size() > 1) {
            duplicateMirroredGroup(before, selected, groupIds);
            return;
        }
        int[] position = findFreePositionNear(before, selected, CharacterPathEditorService.type(selected).size);
        if (position == null) { status = "No clear space is available beside the selected node."; return; }
        EditorNode copy = CharacterPathEditorService.duplicateNode(selected.id, position[0], position[1]);
        if (copy == null) { status = "Could not duplicate the selected node."; return; }
        setSingleSelection(copy.id);
        gridPlacementArmed = false;
        positionDragActive = false;
        if (connectMode) {
            if (linkNewNode(selected.id, copy.id)) status = "Duplicated and linked " + selected.name + ".";
            else status = "Duplicated the node, but its link could not be routed.";
        } else {
            status = "Duplicated " + selected.name + " with all bonuses and ranks.";
        }
    }

    private void duplicateMirroredGroup(Document before, EditorNode selected, Set<String> sourceIds) {
        Map<String, int[]> mirroredPositions = new LinkedHashMap<>();
        for (EditorNode node : before.nodes) {
            if (node == null || !sourceIds.contains(node.id)) continue;
            int size = CharacterPathEditorService.type(node).size;
            int mirroredX = mirrorVertically ? node.x
                    : before.canvasWidth - node.x - size;
            int mirroredY = mirrorVertically ? before.canvasHeight - node.y - size : node.y;
            int maxX = before.canvasWidth - CharacterPathEditorService.CANVAS_X_OFFSET - size;
            int maxY = before.canvasHeight - size;
            if (mirroredX < 0 || mirroredX > maxX || mirroredY < 0 || mirroredY > maxY) {
                status = "Mirror blocked: the selected group does not fit on the opposite side of the canvas.";
                return;
            }
            mirroredPositions.put(node.id, new int[] {mirroredX, mirroredY});
        }
        if (mirroredPositions.size() != sourceIds.size()) {
            status = "Mirror canceled because part of the selected group could not be found.";
            return;
        }

        Set<String> originalIds = new LinkedHashSet<>();
        for (EditorNode node : before.nodes) if (node != null) originalIds.add(node.id);
        String[] validationFailure = {"Mirror blocked: the duplicate would overlap another node."};
        Map<String, EditorNode> copies = CharacterPathEditorService.duplicateNodes(mirroredPositions, candidate -> {
            Set<String> copiedIds = new LinkedHashSet<>();
            for (EditorNode node : candidate.nodes) {
                if (node != null && !originalIds.contains(node.id)) copiedIds.add(node.id);
            }
            if (!duplicatedNodesHaveClearance(copiedIds, candidate)) return false;
            try {
                CharacterPathEdgeRouter.route(graphEdges(candidate), locations(candidate),
                        candidate.canvasWidth, candidate.canvasHeight);
                return true;
            } catch (IllegalStateException exception) {
                validationFailure[0] = "Mirror blocked: a duplicated link would cross another link or node.";
                return false;
            }
        });
        if (copies.isEmpty()) {
            status = validationFailure[0];
            return;
        }

        selectedNodeIds.clear();
        for (String sourceId : mirroredPositions.keySet()) {
            EditorNode copy = copies.get(sourceId);
            if (copy != null) selectedNodeIds.add(copy.id);
        }
        EditorNode mirroredAnchor = copies.get(selected.id);
        selectedNodeId = mirroredAnchor == null ? selectedNodeIds.stream().findFirst().orElse(null)
                : mirroredAnchor.id;
        multiSelectMode = selectedNodeIds.size() > 1;
        gridPlacementArmed = false;
        positionDragActive = false;
        copyPropertiesSourceId = null;

        if (connectMode && mirroredAnchor != null) {
            boolean linked = linkNewNode(selected.id, mirroredAnchor.id);
            status = "Mirrored " + copies.size() + " nodes " + mirrorDirection() + " across the canvas center, preserving internal links"
                    + (linked ? " and linked the group anchors." : "; the anchor link could not be routed.");
        } else {
            status = "Mirrored " + copies.size() + " nodes " + mirrorDirection() + " across the canvas center, preserving internal links.";
        }
    }

    private String mirrorDirection() {
        return mirrorVertically ? "top/bottom" : "left/right";
    }

    private void removeNode() {
        EditorNode selected = selectedNode();
        if (selected == null) { status = "Select a node first."; return; }
        copyPropertiesSourceId = null;
        String removed = selected.name;
        CharacterPathEditorService.removeNode(selected.id);
        selectedNodeIds.remove(selected.id);
        selectedNodeId = selectedNodeIds.stream().findFirst().orElse(null);
        gridPlacementArmed = false;
        connectFirstNodeId = null;
        positionDragActive = false;
        status = "Removed " + removed + " and its links.";
    }

    private void toggleConnectMode() {
        copyPropertiesSourceId = null;
        connectMode = !connectMode;
        connectFirstNodeId = null;
        if (connectMode) {
            multiSelectMode = false;
            if (selectedNodeId != null) setSingleSelection(selectedNodeId);
        }
        status = connectMode
                ? "Link mode: choose a source (orange ring), then a target. Existing links are removed."
                : "Link mode closed.";
    }

    private void updateNodeType(String nodeId, String selectedType) {
        Document before = CharacterPathEditorService.snapshot();
        EditorNode selected = selectedNode(before);
        if (selected == null || !selected.id.equals(nodeId)) {
            status = "Select a node before changing its type.";
            return;
        }
        NodeType previous = CharacterPathEditorService.type(selected);
        NodeType next;
        try { next = NodeType.valueOf(selectedType); }
        catch (Exception ignored) { status = "Unknown node type."; return; }
        if (previous == next) return;
        if (selected.x + next.size > canvasWidth || selected.y + next.size > canvasHeight) {
            status = "Type change rejected: move the node inward to make room first.";
            return;
        }
        if (!positionIsClear(selected.id, selected.x, selected.y, next.size,
                before)) {
            status = "Type change rejected: the larger node would overlap another node.";
            return;
        }
        try {
            CharacterPathEdgeRouter.route(graphEdges(before), locations(before, nodeId, next),
                    before.canvasWidth, before.canvasHeight);
        } catch (IllegalStateException exception) {
            status = "Type change rejected: a link would cross another link or node.";
            return;
        }
        status = CharacterPathEditorService.setNodeType(nodeId, next.name())
                ? "Node type updated." : "Could not update node type.";
    }

    private void updatePathBonus(String nodeId, String selection) {
        Document before = CharacterPathEditorService.snapshot();
        EditorNode selected = selectedNode(before);
        if (selected == null || !selected.id.equals(nodeId)
                || CharacterPathEditorService.type(selected) != NodeType.PATH) {
            status = "Select a path node before assigning its stat.";
            return;
        }
        status = CharacterPathEditorService.setPathBonus(nodeId, selection)
                ? "Path node bonus set to " + selection.toLowerCase(java.util.Locale.ROOT) + " +1."
                : "Choose health, mana, or stamina.";
    }

    private void addBonus() {
        EditorNode selected = selectedNode();
        if (selected == null) { status = "Select a node first."; return; }
        status = CharacterPathEditorService.addBonus(selected.id)
                ? "Added a different bonus to this node." : "No unused bonus types remain, or this is a path node.";
    }

    private void removeBonus() {
        EditorNode selected = selectedNode();
        if (selected == null) { status = "Select a node first."; return; }
        status = CharacterPathEditorService.removeSelectedBonus(selected.id)
                ? "Removed the selected bonus." : "A node must keep at least one bonus.";
    }

    private EditorNode selectedNode() {
        return CharacterPathEditorService.nodeSnapshot(selectedNodeId);
    }

    private static boolean positionIsClear(String movingId, int x, int y, int size, Document document) {
        for (EditorNode other : document.nodes) {
            if (other == null || movingId.equals(other.id)) continue;
            int otherSize = CharacterPathEditorService.type(other).size;
            double centerDistance = Math.hypot(x + size / 2.0 - (other.x + otherSize / 2.0),
                    y + size / 2.0 - (other.y + otherSize / 2.0));
            if (centerDistance < (size + otherSize) / 2.0 + NODE_CLEARANCE) return false;
        }
        return true;
    }

    private static List<GraphEdge> graphEdges(Document document) {
        List<GraphEdge> edges = new ArrayList<>();
        for (EditorEdge edge : document.edges) {
            if (edge != null && edge.first != null && edge.second != null) {
                edges.add(new GraphEdge(edge.first, edge.second));
            }
        }
        return edges;
    }

    private static String bonusLabel(String value) {
        try {
            irai.mod.reforge.Progression.CharacterPathService.Bonus bonus =
                    irai.mod.reforge.Progression.CharacterPathService.Bonus.valueOf(value);
            return switch (bonus) {
                case REFINING_CHANCE -> "Refining Upgrade Guarantee";
                case REFINING_BREAK_PROTECTION -> "Refining Break Protection";
                case REFINING_DEGRADE_PROTECTION -> "Refining Degrade Protection";
                case REFINING_SAME_CHANCE -> "Refining Safe Outcome Weight";
                case REFINING_UPGRADE_CHANCE -> "Refining Upgrade Weight";
                case REFINING_JACKPOT_CHANCE -> "Refining Jackpot Weight";
                case SOCKET_CHANCE -> "Socketing Chance";
                case ATTACK_SPEED_PERCENT -> "Attack Speed (Multi-hit Chance)";
                default -> CharacterPathEditorService.bonusLabel(bonus);
            };
        }
        catch (Exception ignored) { return "Damage Percent"; }
    }

    private static boolean isPercentBonus(String value) {
        irai.mod.reforge.Progression.CharacterPathService.Bonus bonus;
        try { bonus = irai.mod.reforge.Progression.CharacterPathService.Bonus.valueOf(value); }
        catch (Exception ignored) { return false; }
        return switch (bonus) {
            case REFINING_CHANCE, REFINING_BREAK_PROTECTION, REFINING_DEGRADE_PROTECTION,
                    REFINING_SAME_CHANCE, REFINING_UPGRADE_CHANCE, REFINING_JACKPOT_CHANCE,
                    SOCKET_CHANCE, DAMAGE_PERCENT, DAMAGE_RESISTANCE,
                    CRIT_CHANCE_PERCENT, CRIT_DAMAGE_PERCENT, ATTACK_SPEED_PERCENT,
                    ELEMENTAL_DAMAGE, ELEMENTAL_DOT_CRIT_CHANCE, ELEMENTAL_DOT_CRIT_DAMAGE,
                    ELEMENTAL_STACK_BASE, POISON_STACK_BASE, BLEED_STACK_BASE -> true;
            default -> false;
        };
    }

    private static void bind(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action));
    }

    private static void bind(UIEventBuilder events, String selector, String action, String key, String value) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append(key, value));
    }

    private static void bindDropdown(UIEventBuilder events, String selector, String action, String nodeId) {
        EventData data = EventData.of("Action", action).append("NodeId", nodeId)
                .append("@Selection", selector + ".Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false);
    }

    private static void bindNumberField(UIEventBuilder events, String selector, String action, String nodeId) {
        EventData data = EventData.of("Action", action).append("NodeId", nodeId)
                .append("@Number", selector + ".Value");
        events.addEventBinding(CustomUIEventBindingType.FocusLost, selector, data, false);
    }

    private static void bindCoordinateControl(
            UIEventBuilder events, String selector, String action, String nodeId) {
        EventData data = EventData.of("Action", action).append("NodeId", nodeId)
                .append("@Coordinate", selector + ".Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false);
    }

    private int scaled(int value) {
        return Math.max(1, (int) Math.round(value * zoomPercent / 100.0));
    }

    private int canvasX(int x) { return treeViewport == null ? scaled(x) : treeViewport.x(x); }
    private int canvasY(int y) { return treeViewport == null ? scaled(y) : treeViewport.y(y); }

    private static String child(int index, String suffix) { return "#TreeCanvas[" + index + "]" + suffix; }

    private static String nested(int index, String elementId, String property) {
        String selector = "#TreeCanvas[" + index + "] #" + elementId;
        return property == null ? selector : selector + "." + property;
    }

    private static void setNodeFrame(UICommandBuilder commands, int index, NodeType type,
                                     boolean allocatedOrSelected) {
        commands.setObject(nested(index, "NodeFrame", "Background"), textureBackground(
                CharacterPathEditorService.nodeFrameTexturePath(type, allocatedOrSelected)));
    }

    private static PatchStyle textureBackground(String texturePath) {
        return new PatchStyle(Value.of(texturePath), Value.of(0));
    }

    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    private void appendEdge(UICommandBuilder commands, int index, Anchor placement, String orientation,
                            double segmentWidth, double segmentHeight) {
        commands.append("#TreeCanvas", EDGE_UI);
        commands.setObject(child(index, ".Anchor"), placement);
        commands.setObject(child(index, ".Background"), textureBackground(
                CharacterPathEdgeVisuals.texture("EdgeEditor", orientation, segmentWidth, segmentHeight)));
    }

    private void appendGridLine(UICommandBuilder commands, int index, Anchor placement, boolean major) {
        commands.append("#TreeCanvas", major ? GRID_MAJOR_UI : GRID_MINOR_UI);
        commands.setObject(child(index, ".Anchor"), placement);
    }

    private void appendCenterGridLine(UICommandBuilder commands, int index, Anchor placement) {
        commands.append("#TreeCanvas", GRID_CENTER_UI);
        commands.setObject(child(index, ".Anchor"), placement);
    }

    private record EdgeSegmentVisual(Anchor anchor, String orientation, int width, int height) {}

    public static final class PageEvent {
        public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value,
                        event -> event.action).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (event, value) -> event.target = value,
                        event -> event.target).add()
                .append(new KeyedCodec<>("NodeId", Codec.STRING), (event, value) -> event.nodeId = value,
                        event -> event.nodeId).add()
                .append(new KeyedCodec<>("@Name", Codec.STRING), (event, value) -> event.name = value,
                        event -> event.name).add()
                .append(new KeyedCodec<>("@IconItemId", Codec.STRING), (event, value) -> event.iconItemId = value,
                        event -> event.iconItemId).add()
                .append(new KeyedCodec<>("@IconTexturePath", Codec.STRING),
                        (event, value) -> event.iconTexturePath = value, event -> event.iconTexturePath).add()
                .append(new KeyedCodec<>("@Selection", Codec.STRING), (event, value) -> event.selection = value,
                        event -> event.selection).add()
                .append(new KeyedCodec<>("@Number", Codec.DOUBLE), (event, value) -> event.numberValue = value,
                        event -> event.numberValue).add()
                .append(new KeyedCodec<>("@Coordinate", Codec.FLOAT),
                        (event, value) -> event.coordinateValue = value, event -> event.coordinateValue).add()
                .append(new KeyedCodec<>("@Zoom", Codec.INTEGER),
                        (event, value) -> event.zoomValue = value, event -> event.zoomValue).add()
                .append(new KeyedCodec<>("@CanvasWidth", Codec.DOUBLE),
                        (event, value) -> event.canvasWidthValue = value, event -> event.canvasWidthValue).add()
                .append(new KeyedCodec<>("@CanvasHeight", Codec.DOUBLE),
                        (event, value) -> event.canvasHeightValue = value, event -> event.canvasHeightValue).add()
                .build();
        public String action;
        public String target;
        public String nodeId;
        public String name;
        public String iconItemId;
        public String iconTexturePath;
        public String selection;
        public Double numberValue;
        public Float coordinateValue;
        public Integer zoomValue;
        public Double canvasWidthValue;
        public Double canvasHeightValue;
        public PageEvent() {}
    }
}
