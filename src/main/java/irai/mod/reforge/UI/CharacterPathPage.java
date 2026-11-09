package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;

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
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.Progression.CharacterPathEditorService;
import irai.mod.reforge.Progression.CharacterPathEditorService.Document;
import irai.mod.reforge.Progression.CharacterPathEditorService.EditorNode;
import irai.mod.reforge.Progression.CharacterPathEditorService.NodeType;
import irai.mod.reforge.Progression.CharacterPathService;
import irai.mod.reforge.Progression.CharacterPathService.Bonus;
import irai.mod.reforge.Progression.CharacterPathService.Category;
import irai.mod.reforge.Progression.CharacterPathService.GraphEdge;
import irai.mod.reforge.Progression.CharacterPathService.Node;
import irai.mod.reforge.Progression.CharacterPathService.NodeBonus;
import irai.mod.reforge.Progression.CharacterPathService.Profile;
import irai.mod.reforge.Progression.CharacterPathService.WeaponClass;

/** Player-facing allocation view for the published skill-tree editor draft. */
public final class CharacterPathPage extends InteractiveCustomUIPage<CharacterPathPage.PageEvent> {
    private static final String PAGE_UI = "Pages/SocketReforge_SkillTree_Player.ui";
    private static final String NODE_UI = "Pages/SocketReforge_SkillTree_Node.ui";
    private static final String EDGE_UI = "Pages/SocketReforge_SkillTree_Edge.ui";
    private static final String PATH_UI = "Pages/SocketReforge_SkillTree_Path.ui";
    private static final int TREE_VIEWPORT_WIDTH = 1880;
    private static final int TREE_VIEWPORT_HEIGHT = 980;
    private static final int MIN_ZOOM = 10;
    private static final int MAX_ZOOM = 150;
    private static final int ZOOM_STEP = 10;
    private static final java.lang.reflect.Method UI_SET_BSON_VALUE = resolveUiSetBsonValue();

    private final PlayerRef owner;
    private final Map<String, Integer> renderedNodeChildIndices = new HashMap<>();
    private final Map<String, Node> renderedNodesById = new HashMap<>();
    private final Map<String, NodeType> renderedNodeTypes = new HashMap<>();
    private final Map<String, String> renderedNodeRingStyles = new HashMap<>();
    private final Map<String, String> renderedNodeFillStyles = new HashMap<>();
    private final Map<String, Boolean> renderedNodeFrameStates = new HashMap<>();
    private final Map<String, Boolean> renderedNodeSelectionTints = new HashMap<>();
    private final Map<String, Boolean> renderedNodeAllocatedGlows = new HashMap<>();
    private final Map<String, Boolean> renderedSearchGlowStates = new HashMap<>();
    private final Map<String, String> renderedPathIcons = new HashMap<>();
    private final Map<String, NodeTooltipState> renderedNodeTooltipStates = new HashMap<>();
    private final Map<Integer, GraphEdge> renderedEdgeConnections = new HashMap<>();
    private final Map<Integer, String> renderedEdgeStyles = new HashMap<>();
    private final Map<Integer, String> renderedEdgeOrientations = new HashMap<>();
    private final Map<Integer, EdgeDimensions> renderedEdgeSizes = new HashMap<>();
    private String selectedNodeId;
    private String pathChoicePopupNodeId;
    private String nodeSearchQuery = "";
    private Set<String> searchMatchNodeIds = Set.of();
    private boolean searchRequestReceived;
    private int zoomPercent;
    private boolean centerOnNextPopulate;
    private boolean bonusTotalsVisible;
    private CharacterPathTreeViewport.View treeViewport;
    private volatile boolean dismissed;

    private record EdgeDimensions(double width, double height) {}
    private record NodeTooltipState(int rank, boolean unlocked, boolean hasPoints,
                                    String pathChoice, NodeType type) {}

    public CharacterPathPage(PlayerRef owner, String selectedNodeId, int zoomPercent) {
        super(owner, CustomPageLifetime.CanDismiss, PageEvent.CODEC);
        this.owner = owner;
        this.selectedNodeId = isVisible(selectedNodeId) ? selectedNodeId : firstVisibleNodeId();
        this.zoomPercent = clamp(zoomPercent, MIN_ZOOM, MAX_ZOOM);
        // Start at the requested zoom so scrollbars remain available for testing.
        // The player can invoke bounds-fit explicitly from the FIT button.
        centerOnNextPopulate = false;
    }

    public static void open(PlayerRef owner, String selectedNodeId, int zoomPercent) {
        if (owner == null || !owner.isValid()) return;
        Ref<EntityStore> playerRef = owner.getReference();
        if (playerRef == null || !playerRef.isValid()) return;
        Store<EntityStore> store = playerRef.getStore();
        Player player = store == null ? null : store.getComponent(playerRef, Player.getComponentType());
        if (player != null) player.getPageManager().openCustomPage(playerRef, store,
                new CharacterPathPage(owner, selectedNodeId, zoomPercent));
    }

    @Override
    public void build(Ref<EntityStore> playerRef, UICommandBuilder commands,
                      UIEventBuilder events, Store<EntityStore> store) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;
        commands.append(PAGE_UI);
        populateTree(commands, events, player, false);
    }

    private void populateTree(UICommandBuilder commands, UIEventBuilder events,
                              Player player, boolean clearCanvas) {
        boolean centerNow = centerOnNextPopulate;
        centerOnNextPopulate = false;
        if (clearCanvas) {
            commands.clear("#TreeCanvas");
            renderedNodeChildIndices.clear();
            renderedNodesById.clear();
            renderedNodeTypes.clear();
            renderedNodeRingStyles.clear();
            renderedNodeFillStyles.clear();
            renderedNodeFrameStates.clear();
            renderedNodeSelectionTints.clear();
            renderedNodeAllocatedGlows.clear();
            renderedSearchGlowStates.clear();
            renderedPathIcons.clear();
            renderedNodeTooltipStates.clear();
            renderedEdgeConnections.clear();
            renderedEdgeStyles.clear();
            renderedEdgeOrientations.clear();
            renderedEdgeSizes.clear();
        }
        int points = CharacterPathService.availablePoints(player);
        int level = CharacterPathService.level(player);
        Profile profile = CharacterPathService.profile(player);
        Document draft = CharacterPathEditorService.snapshot();
        searchMatchNodeIds = findSearchMatches(nodeSearchQuery, draft, player);
        CharacterPathTreeViewport.View previousViewport = treeViewport;
        treeViewport = CharacterPathTreeViewport.create(draft, zoomPercent, centerNow,
                TREE_VIEWPORT_WIDTH, TREE_VIEWPORT_HEIGHT, MIN_ZOOM, MAX_ZOOM);
        zoomPercent = treeViewport.zoomPercent;
        boolean resetScroll = centerNow || previousViewport == null
                || !previousViewport.hasSameGraphBounds(treeViewport);
        commands.set("#TreeCanvas.KeepScrollPosition", !resetScroll);
        commands.set("#TreePointsLabel.Text", "Skill points: " + points);
        commands.set("#TreeLevelLabel.Text", "Character level " + level + "  |  "
                + (profile.xp % CharacterPathService.XP_PER_LEVEL) + "/"
                + CharacterPathService.XP_PER_LEVEL + " XP");
        commands.set("#AvailablePoints.Text", "Available points: " + points);
        commands.set("#TreeZoomLabel.Text", "Zoom " + zoomPercent + "%");
        commands.set("#TreeCanvas.ContentWidth", treeViewport.contentWidth);
        commands.set("#TreeCanvas.ContentHeight", treeViewport.contentHeight);
        if (resetScroll) commands.set("#TreeCanvas.KeepScrollPosition", true);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeZoomIn",
                EventData.of("Action", "tree_zoom").append("Target", "in"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeZoomOut",
                EventData.of("Action", "tree_zoom").append("Target", "out"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeZoomReset",
                EventData.of("Action", "tree_reset"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeCenter",
                EventData.of("Action", "tree_center"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeBonusToggle",
                EventData.of("Action", "tree_bonus_toggle"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BonusTotalsClose",
                EventData.of("Action", "tree_bonus_hide"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DetailAction",
                EventData.of("Action", "tree_allocate"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PathChoiceHealth",
                EventData.of("Action", "tree_path_choice").append("Target", "HEALTH"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PathChoiceMana",
                EventData.of("Action", "tree_path_choice").append("Target", "MANA"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PathChoiceStamina",
                EventData.of("Action", "tree_path_choice").append("Target", "STAMINA"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PathChoiceCancel",
                EventData.of("Action", "tree_path_choice_cancel"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeRespecAction",
                EventData.of("Action", "tree_respec"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeRespecNodeAction",
                EventData.of("Action", "tree_respec_node"));
        EventData searchData = EventData.of("Action", "tree_search")
                .append("@Query", "#TreeNodeSearch.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TreeNodeSearch", searchData, false);
        events.addEventBinding(CustomUIEventBindingType.FocusLost, "#TreeNodeSearch", searchData, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeSearchSubmit",
                EventData.of("Action", "tree_search_submit").append("@Query", "#TreeNodeSearch.Value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TreeSearchClear",
                EventData.of("Action", "tree_search_clear"));

        int childIndex = appendEdges(commands, player, draft, 0);
        appendNodes(commands, events, player, draft, childIndex);
        fillTreeOverlay(commands, player, points, profile);
        commands.set("#PathChoicePopup.Visible", pathChoicePopupNodeId != null);
        if (pathChoicePopupNodeId != null) {
            Node choiceNode = findNode(pathChoicePopupNodeId);
            if (choiceNode != null) commands.set("#PathChoicePrompt.Text",
                    "Choose the +1 stat for " + choiceNode.name + ". This choice is saved to your character.");
        }
    }

    @Override
    public void onDismiss(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        dismissed = true;
        super.onDismiss(playerRef, store);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> playerRef, Store<EntityStore> store, PageEvent event) {
        super.handleDataEvent(playerRef, store, event);
        if (event == null || event.action == null) return;
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;
        boolean progressionChanged = false;
        boolean popupChanged = false;
        switch (event.action) {
            case "tree_search" -> {
                String query = event.query == null ? "" : event.query.trim();
                boolean unchanged = searchRequestReceived && query.equals(nodeSearchQuery);
                nodeSearchQuery = query;
                searchRequestReceived = true;
                if (unchanged) return;
                searchMatchNodeIds = findSearchMatches(nodeSearchQuery,
                        CharacterPathEditorService.snapshot(), player);
                refreshSearchHighlights(false);
                return;
            }
            case "tree_search_submit" -> {
                nodeSearchQuery = event.query == null ? "" : event.query.trim();
                searchRequestReceived = true;
                searchMatchNodeIds = findSearchMatches(nodeSearchQuery,
                        CharacterPathEditorService.snapshot(), player);
                refreshSearchHighlights(false, true);
                return;
            }
            case "tree_search_clear" -> {
                nodeSearchQuery = "";
                searchRequestReceived = false;
                searchMatchNodeIds = Set.of();
                refreshSearchHighlights(true);
                return;
            }
            case "tree_select" -> {
                if (event.nodeId == null || !renderedNodeChildIndices.containsKey(event.nodeId)) return;
                String previousSelectedNodeId = selectedNodeId;
                selectedNodeId = event.nodeId;
                pathChoicePopupNodeId = null;
                updateSelectionInPlace(player, previousSelectedNodeId);
                return;
            }
            case "tree_allocate" -> {
                Node selected = renderedNodesById.get(selectedNodeId);
                if (selected != null && selected.pathOnly) {
                    if (CharacterPathService.isUnlocked(player, selected.id)
                            && CharacterPathService.rank(player, selected.id) == 0
                            && CharacterPathService.availablePoints(player) > 0) {
                        pathChoicePopupNodeId = selected.id;
                        popupChanged = true;
                    } else {
                        owner.sendMessage(Message.raw("That path node is locked, already allocated, or you have no skill points."));
                    }
                } else if (selected != null && CharacterPathService.allocate(player, selected.id)) {
                    progressionChanged = true;
                    owner.sendMessage(Message.raw("Allocated " + displayNodeName(selected, player) + ". "
                            + CharacterPathService.availablePoints(player) + " skill points remain."));
                } else owner.sendMessage(Message.raw("That node is locked, maxed, or you have no skill points."));
            }
            case "tree_path_choice" -> {
                Node selected = renderedNodesById.get(pathChoicePopupNodeId);
                boolean allocated = selected != null
                        && CharacterPathService.allocatePath(player, selected.id, event.target);
                pathChoicePopupNodeId = null;
                popupChanged = true;
                progressionChanged = allocated;
                if (allocated) owner.sendMessage(Message.raw("Allocated " + displayNodeName(selected, player) + ". "
                        + CharacterPathService.availablePoints(player) + " skill points remain."));
                else owner.sendMessage(Message.raw("Could not allocate that path node. Check its connection and available skill points."));
            }
            case "tree_path_choice_cancel" -> {
                pathChoicePopupNodeId = null;
                popupChanged = true;
            }
            case "tree_respec" -> {
                int refunded = CharacterPathService.respec(player);
                progressionChanged = refunded > 0;
                if (refunded > 0) owner.sendMessage(Message.raw("Respec complete. Refunded " + refunded
                        + " point" + (refunded == 1 ? "" : "s") + "; earned XP was kept."));
                else owner.sendMessage(Message.raw("No allocated skill points to refund."));
            }
            case "tree_respec_node" -> {
                Node selected = renderedNodesById.get(selectedNodeId);
                String selectedName = selected == null ? "" : displayNodeName(selected, player);
                int selectedRanks = selected == null ? 0 : CharacterPathService.rank(player, selected.id);
                int refunded = selected == null ? -1 : CharacterPathService.respec(player, selected.id);
                progressionChanged = refunded > 0;
                if (refunded > 0) {
                    int dependentRefunded = Math.max(0, refunded - selectedRanks);
                    String message = "Refunded " + refunded + " point" + (refunded == 1 ? "" : "s")
                            + " from " + selectedName;
                    if (dependentRefunded > 0) message += ", including " + dependentRefunded
                            + " point" + (dependentRefunded == 1 ? "" : "s")
                            + " from nodes no longer connected to an allocated core";
                    owner.sendMessage(Message.raw(message + ". Earned XP was kept."));
                }
                else owner.sendMessage(Message.raw("The selected node has no allocated ranks to refund."));
            }
            case "tree_center" -> {
                centerOnNextPopulate = true;
            }
            case "tree_bonus_toggle" -> bonusTotalsVisible = !bonusTotalsVisible;
            case "tree_bonus_hide" -> bonusTotalsVisible = false;
            case "tree_zoom" -> zoomPercent = clamp(zoomPercent
                    + ("in".equalsIgnoreCase(event.target) ? ZOOM_STEP : -ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
            case "tree_reset" -> zoomPercent = 100;
            default -> { return; }
        }
        UICommandBuilder commands = new UICommandBuilder();
        if ("tree_zoom".equals(event.action) || "tree_reset".equals(event.action)
                || "tree_center".equals(event.action)) {
            UIEventBuilder events = new UIEventBuilder();
            populateTree(commands, events, player, true);
            sendUpdate(commands, events, false);
        } else if (progressionChanged) {
            refreshTreeInPlace(player);
        } else if (popupChanged) {
            refreshPathChoicePopup(commands);
            sendUpdate(commands, false);
        } else if ("tree_bonus_toggle".equals(event.action) || "tree_bonus_hide".equals(event.action)) {
            commands.set("#BonusTotalsPopup.Visible", bonusTotalsVisible);
            commands.set("#TreeBonusToggle.Text", bonusTotalsVisible ? "HIDE TOTALS" : "BONUS TOTALS");
            sendUpdate(commands, false);
        }
    }

    /** Update the selected-node presentation without clearing the scrollable canvas. */
    private void updateSelectionInPlace(Player player, String previousSelectedNodeId) {
        Integer nextIndex = renderedNodeChildIndices.get(selectedNodeId);
        if (nextIndex == null) return;

        UICommandBuilder commands = new UICommandBuilder();
        Node next = renderedNodesById.get(selectedNodeId);
        if (next == null) return;
        Integer previousIndex = renderedNodeChildIndices.get(previousSelectedNodeId);
        Profile profile = CharacterPathService.profile(player);
        int points = CharacterPathService.availablePoints(player);

        if (previousIndex != null && previousSelectedNodeId != null
                && !previousSelectedNodeId.equals(selectedNodeId)) {
            Node previous = renderedNodesById.get(previousSelectedNodeId);
            setNodeSelectionRing(commands, previousIndex, previous, profile, points, false);
        }
        setNodeSelectionRing(commands, nextIndex, next, profile, points, true);

        fillSelectedNodeOverlay(commands, profile, points);
        commands.set("#PathChoicePopup.Visible", false);
        sendUpdate(commands, false);
    }

    private void refreshSearchHighlights(boolean clearInput) {
        refreshSearchHighlights(clearInput, false);
    }

    private void refreshSearchHighlights(boolean clearInput, boolean revealFirstMatch) {
        UICommandBuilder commands = new UICommandBuilder();
        if (clearInput) commands.set("#TreeNodeSearch.Value", "");
        commands.set("#TreeSearchMatches.Text", searchStatusText());
        for (Map.Entry<String, Integer> entry : renderedNodeChildIndices.entrySet()) {
            boolean visible = searchMatchNodeIds.contains(entry.getKey());
            if (java.util.Objects.equals(renderedSearchGlowStates.get(entry.getKey()), visible)) continue;
            commands.set(nested(entry.getValue(), "NodeSearchGlow", "Visible"), visible);
            renderedSearchGlowStates.put(entry.getKey(), visible);
        }
        if (revealFirstMatch && !searchMatchNodeIds.isEmpty()) {
            Integer firstMatchIndex = null;
            for (Node node : CharacterPathService.nodes()) {
                if (searchMatchNodeIds.contains(node.id)) {
                    firstMatchIndex = renderedNodeChildIndices.get(node.id);
                    if (firstMatchIndex != null) break;
                }
            }
            if (firstMatchIndex != null) {
                commands.set("#TreeCanvas.ScrollChildIndexIntoView", firstMatchIndex);
            }
        }
        sendUpdate(commands, false);
    }

    private void setNodeSelectionRing(UICommandBuilder commands, int index, Node node,
                                      Profile profile, int points, boolean selected) {
        if (node == null) return;
        if (node.pathOnly) {
            boolean ranked = profile.ranks.getOrDefault(node.id, 0) > 0;
            boolean available = !ranked && isNodeUnlocked(profile, node) && points > 0;
            commands.set(nested(index, "PathRingSelected", "Visible"), selected);
            commands.set(nested(index, "PathRingRanked", "Visible"), !selected && ranked);
            commands.set(nested(index, "PathRingAvailable", "Visible"), !selected && available);
            setNodeAllocatedGlow(commands, index, node.id, !selected && ranked);
            return;
        }
        int rank = profile.ranks.getOrDefault(node.id, 0);
        boolean unlocked = isNodeUnlocked(profile, node);
        String defaultRing = node.core ? "RingCore"
                : rank > 0 ? "RingRanked"
                : unlocked ? "RingCategory" + node.category.name() : "RingLocked";
        setNodeRingStyle(commands, index, node.id, selected ? "RingSelected" : defaultRing);
        NodeType type = renderedNodeTypes.get(node.id);
        if (type != null) setNodeFrameIfChanged(commands, index, node.id, type, rank > 0 || selected);
        setNodeSelectionTint(commands, index, node.id, selected);
        setNodeAllocatedGlow(commands, index, node.id, !selected && rank > 0);
    }

    /** Patch combat progression state without replacing any scrollable canvas children. */
    private void refreshTreeInPlace(Player player) {
        UICommandBuilder commands = new UICommandBuilder();
        int points = CharacterPathService.availablePoints(player);
        Profile profile = CharacterPathService.profile(player);
        commands.set("#TreePointsLabel.Text", "Skill points: " + points);
        commands.set("#TreeLevelLabel.Text", "Character level " + CharacterPathService.level(player)
                + "  |  " + (profile.xp % CharacterPathService.XP_PER_LEVEL) + "/"
                + CharacterPathService.XP_PER_LEVEL + " XP");
        commands.set("#AvailablePoints.Text", "Available points: " + points);

        Set<String> availablePathEdges = availablePathEdges(profile, points);
        Map<String, String> edgeStyleByConnection = new HashMap<>();
        for (Map.Entry<Integer, GraphEdge> entry : renderedEdgeConnections.entrySet()) {
            int index = entry.getKey();
            GraphEdge edge = entry.getValue();
            String key = edgeKey(edge.first(), edge.second());
            String visibleStyle = edgeStyleByConnection.computeIfAbsent(key, ignored -> edgeStyle(
                    edgeColor(profile, renderedNodesById.get(edge.first()),
                            renderedNodesById.get(edge.second()), availablePathEdges)));
            if (visibleStyle.equals(renderedEdgeStyles.get(index))) continue;
            String orientation = renderedEdgeOrientations.getOrDefault(index, "Horizontal");
            EdgeDimensions dimensions = renderedEdgeSizes.getOrDefault(index, new EdgeDimensions(1.0, 1.0));
            commands.setObject(child(index, ".Background"), textureBackground(
                    CharacterPathEdgeVisuals.texture(visibleStyle, orientation,
                            dimensions.width(), dimensions.height())));
            renderedEdgeStyles.put(index, visibleStyle);
        }

        for (Node node : CharacterPathService.nodes()) {
            Integer index = renderedNodeChildIndices.get(node.id);
            if (index == null) continue;
            int rank = profile.ranks.getOrDefault(node.id, 0);
            boolean unlocked = isNodeUnlocked(profile, node);
            boolean selected = node.id.equals(selectedNodeId);
            NodeType tooltipType = renderedNodeTypes.getOrDefault(node.id, NodeType.SMALL);
            NodeTooltipState tooltipState = tooltipState(node, tooltipType, profile, points, rank, unlocked);
            if (!tooltipState.equals(renderedNodeTooltipStates.get(node.id))) {
                setNodeTooltip(commands, nested(index, "NodeHit", "TooltipText"),
                        node, tooltipType, profile, points);
                renderedNodeTooltipStates.put(node.id, tooltipState);
            }
            if (node.pathOnly) {
                commands.set(nested(index, "PathRingSelected", "Visible"), selected);
                commands.set(nested(index, "PathRingRanked", "Visible"), !selected && rank > 0);
                commands.set(nested(index, "PathRingAvailable", "Visible"), !selected && rank == 0
                        && unlocked && points > 0);
                setNodeAllocatedGlow(commands, index, node.id, !selected && rank > 0);
                String choice = profile.pathChoices.get(node.id);
                String icon = rank > 0 ? CharacterPathService.pathIcon(choice) : "";
                if (!icon.equals(renderedPathIcons.get(node.id))) {
                    commands.set(nested(index, "PathItemIcon", "ItemId"), icon);
                    commands.set(nested(index, "PathItemIcon", "Visible"), !icon.isBlank());
                    renderedPathIcons.put(node.id, icon);
                }
            } else {
                String ring = selected ? "RingSelected" : node.core ? "RingCore"
                        : rank > 0 ? "RingRanked"
                        : unlocked ? "RingCategory" + node.category.name() : "RingLocked";
                String fill = rank > 0 ? "FillCategory" + node.category.name()
                        : node.core ? "FillCore"
                        : unlocked ? "FillAvailable" + node.category.name() : "FillLocked";
                setNodeRingStyle(commands, index, node.id, ring);
                setNodeFillStyle(commands, index, node.id, fill);
                setNodeFrameIfChanged(commands, index, node.id, tooltipType, rank > 0 || selected);
                setNodeSelectionTint(commands, index, node.id, selected);
                setNodeAllocatedGlow(commands, index, node.id, !selected && rank > 0);
            }
        }
        fillTreeOverlay(commands, player, points, profile);
        commands.set("#PathChoicePopup.Visible", pathChoicePopupNodeId != null);
        if (pathChoicePopupNodeId != null) {
            Node choiceNode = renderedNodesById.get(pathChoicePopupNodeId);
            if (choiceNode != null) commands.set("#PathChoicePrompt.Text",
                    "Choose the +1 stat for " + choiceNode.name + ". This choice is saved to your character.");
        }
        sendUpdate(commands, false);
    }

    private static void setNodeFrame(UICommandBuilder commands, int index, NodeType type,
                                     boolean allocatedOrSelected) {
        commands.setObject(nested(index, "NodeFrame", "Background"), textureBackground(
                CharacterPathEditorService.nodeFrameTexturePath(type, allocatedOrSelected)));
    }

    private void setNodeFrameIfChanged(UICommandBuilder commands, int index, String nodeId,
                                       NodeType type, boolean allocatedOrSelected) {
        if (java.util.Objects.equals(renderedNodeFrameStates.get(nodeId), allocatedOrSelected)) return;
        setNodeFrame(commands, index, type, allocatedOrSelected);
        renderedNodeFrameStates.put(nodeId, allocatedOrSelected);
    }

    private void setNodeRingStyle(UICommandBuilder commands, int index, String nodeId, String style) {
        String previous = renderedNodeRingStyles.get(nodeId);
        if (style.equals(previous)) return;
        if (previous != null) commands.set(nested(index, previous, "Visible"), false);
        commands.set(nested(index, style, "Visible"), true);
        renderedNodeRingStyles.put(nodeId, style);
    }

    private void setNodeFillStyle(UICommandBuilder commands, int index, String nodeId, String style) {
        String previous = renderedNodeFillStyles.get(nodeId);
        if (style.equals(previous)) return;
        if (previous != null) commands.set(nested(index, previous, "Visible"), false);
        commands.set(nested(index, style, "Visible"), true);
        renderedNodeFillStyles.put(nodeId, style);
    }

    private void setNodeSelectionTint(UICommandBuilder commands, int index, String nodeId, boolean visible) {
        if (java.util.Objects.equals(renderedNodeSelectionTints.get(nodeId), visible)) return;
        commands.set(nested(index, "NodeSelectionTint", "Visible"), visible);
        renderedNodeSelectionTints.put(nodeId, visible);
    }

    private void setNodeAllocatedGlow(UICommandBuilder commands, int index, String nodeId, boolean visible) {
        if (java.util.Objects.equals(renderedNodeAllocatedGlows.get(nodeId), visible)) return;
        commands.set(nested(index, "NodeAllocatedGlow", "Visible"), visible);
        renderedNodeAllocatedGlows.put(nodeId, visible);
    }

    private static NodeTooltipState tooltipState(Node node, NodeType type, Profile profile,
                                                 int points, int rank, boolean unlocked) {
        return new NodeTooltipState(rank, unlocked, points > 0,
                node.pathOnly ? profile.pathChoices.get(node.id) : null, type);
    }

    private static PatchStyle textureBackground(String texturePath) {
        return new PatchStyle(Value.of(texturePath), Value.of(0));
    }

    private static String edgeStyle(String color) {
        return "#665333".equals(color) ? "EdgePath"
                : "#4d9ed8".equals(color) ? "EdgeRanked"
                : "#63aa7c".equals(color) ? "EdgeAvailable" : "EdgeDefault";
    }

    private int appendEdges(UICommandBuilder commands, Player player, Document draft, int firstChildIndex) {
        List<CharacterPathEdgeRouter.RoutedEdge> routes;
        try {
            routes = CharacterPathEdgeRouter.route(CharacterPathService.graphEdges(), locations(draft),
                    draft.canvasWidth, draft.canvasHeight);
        } catch (IllegalStateException exception) {
            return firstChildIndex;
        }
        Profile profile = CharacterPathService.profile(player);
        int availablePoints = CharacterPathService.availablePoints(player);
        Set<String> availablePathEdges = availablePathEdges(profile, availablePoints);
        int index = firstChildIndex;
        for (CharacterPathEdgeRouter.RoutedEdge route : routes) {
            Node first = findNode(route.edge().first());
            Node second = findNode(route.edge().second());
            String color = edgeColor(profile, first, second, availablePathEdges);
            List<CharacterPathEdgeRouter.Point> routePoints = route.points();
            for (int i = 1; i < routePoints.size(); i++) {
                CharacterPathEdgeRouter.Point from = routePoints.get(i - 1);
                CharacterPathEdgeRouter.Point to = routePoints.get(i);
                index = appendEdgeSegment(commands, index,
                        (int) Math.round(from.x()), (int) Math.round(from.y()),
                        (int) Math.round(to.x()), (int) Math.round(to.y()), color, route.edge());
            }
        }
        return index;
    }

    private int appendEdgeSegment(UICommandBuilder commands, int index,
                                  int x1, int y1, int x2, int y2, String color, GraphEdge edge) {
        int screenX1 = canvasX(x1);
        int screenY1 = canvasY(y1);
        int screenX2 = canvasX(x2);
        int screenY2 = canvasY(y2);
        double dx = screenX2 - screenX1;
        double dy = screenY2 - screenY1;
        String orientation = CharacterPathEdgeVisuals.orientation(dx, dy);
        int thickness = Math.max(3, scaled("#4d9ed8".equals(color) ? 8 : 6));
        int left;
        int top;
        int width;
        int height;
        if ("Horizontal".equals(orientation)) {
            width = Math.max(1, (int) Math.round(Math.abs(dx))) + 2;
            height = thickness;
            left = Math.min(screenX1, screenX2) - 1;
            top = (int) Math.round((screenY1 + screenY2) / 2.0 - height / 2.0);
        } else if ("Vertical".equals(orientation)) {
            width = thickness;
            height = Math.max(1, (int) Math.round(Math.abs(dy))) + 2;
            left = (int) Math.round((screenX1 + screenX2) / 2.0 - width / 2.0);
            top = Math.min(screenY1, screenY2) - 1;
        } else {
            width = Math.max(1, (int) Math.round(Math.abs(dx))) + 2;
            height = Math.max(1, (int) Math.round(Math.abs(dy))) + 2;
            left = Math.min(screenX1, screenX2) - 1;
            top = Math.min(screenY1, screenY2) - 1;
        }
        appendEdge(commands, index, left, top, width, height, color, edge, orientation);
        return index + 1;
    }

    private void appendEdge(UICommandBuilder commands, int index, int x, int y,
                            int width, int height, String color, GraphEdge edge, String orientation) {
        commands.append("#TreeCanvas", EDGE_UI);
        renderedEdgeConnections.put(index, edge);
        renderedEdgeOrientations.put(index, orientation);
        renderedEdgeSizes.put(index, new EdgeDimensions(width, height));
        commands.setObject(child(index, ".Anchor"), anchor(x, y, width, height));
        commands.setObject(child(index, ".Background"), textureBackground(
                CharacterPathEdgeVisuals.texture(edgeStyle(color), orientation,
                        width, height)));
        renderedEdgeStyles.put(index, edgeStyle(color));
    }

    private int appendNodes(UICommandBuilder commands, UIEventBuilder events, Player player,
                            Document draft, int index) {
        Map<String, EditorNode> placements = editorNodes(draft);
        Profile profile = CharacterPathService.profile(player);
        int points = CharacterPathService.availablePoints(player);
        for (Node node : CharacterPathService.nodes()) {
            EditorNode placement = placements.get(node.id);
            if (placement == null) continue;
            NodeType type = CharacterPathEditorService.type(placement);
            int size = type.size;
            int rank = profile.ranks.getOrDefault(node.id, 0);
            boolean searchMatch = searchMatchNodeIds.contains(node.id);
            boolean selected = node.id.equals(selectedNodeId);
            boolean unlocked = isNodeUnlocked(profile, node);
            if (node.pathOnly) {
                commands.append("#TreeCanvas", PATH_UI);
                renderedNodeChildIndices.put(node.id, index);
                renderedNodesById.put(node.id, node);
                commands.setObject(child(index, ".Anchor"), anchor(canvasX(placement.x), canvasY(placement.y),
                        scaled(size), scaled(size)));
                commands.set(nested(index, "NodeSearchGlow", "Visible"), searchMatch);
                renderedSearchGlowStates.put(node.id, searchMatch);
                commands.set(nested(index, "NodeAllocatedGlow", "Visible"), rank > 0 && !selected);
                commands.set(nested(index, "PathRingSelected", "Visible"), selected);
                commands.set(nested(index, "PathRingRanked", "Visible"), !selected && rank > 0);
                commands.set(nested(index, "PathRingAvailable", "Visible"), !selected && rank == 0 && unlocked
                        && points > 0);
                String pathChoice = profile.pathChoices.get(node.id);
                String pathIcon = rank > 0 ? CharacterPathService.pathIcon(pathChoice) : "";
                commands.set(nested(index, "PathItemIcon", "ItemId"), pathIcon);
                commands.set(nested(index, "PathItemIcon", "Visible"), !pathIcon.isBlank());
                renderedPathIcons.put(node.id, pathIcon);
                renderedNodeTypes.put(node.id, type);
                renderedNodeAllocatedGlows.put(node.id, rank > 0 && !selected);
                renderedNodeTooltipStates.put(node.id, tooltipState(node, type, profile, points, rank, unlocked));
                int pathIconSize = Math.max(8, scaled(12));
                commands.setObject(nested(index, "PathItemIcon", "Anchor"), anchor(
                        (scaled(size) - pathIconSize) / 2, (scaled(size) - pathIconSize) / 2,
                        pathIconSize, pathIconSize));
                setNodeSearchGlowAnchor(commands, index, scaled(size));
                setNodeTooltip(commands, nested(index, "NodeHit", "TooltipText"), node, type, profile, points);
                events.addEventBinding(CustomUIEventBindingType.Activating, nested(index, "NodeHit", null),
                        EventData.of("Action", "tree_select").append("NodeId", node.id));
                index++;
                continue;
            }
            String ringStyle = selected ? "RingSelected" : node.core ? "RingCore"
                    : rank > 0 ? "RingRanked"
                    : unlocked ? "RingCategory" + node.category.name() : "RingLocked";
            String fillStyle = rank > 0
                    ? "FillCategory" + node.category.name()
                    : node.core ? "FillCore" : unlocked ? "FillAvailable" + node.category.name() : "FillLocked";
            int renderedSize = scaled(size);
            commands.append("#TreeCanvas", NODE_UI);
            renderedNodeChildIndices.put(node.id, index);
            renderedNodesById.put(node.id, node);
            commands.setObject(child(index, ".Anchor"), anchor(canvasX(placement.x), canvasY(placement.y),
                    scaled(size), scaled(size)));
            setNodeFrame(commands, index, type, rank > 0 || selected);
            commands.set(nested(index, "NodeSearchGlow", "Visible"), searchMatch);
            renderedSearchGlowStates.put(node.id, searchMatch);
            commands.set(nested(index, "NodeAllocatedGlow", "Visible"), rank > 0 && !selected);
            commands.set(nested(index, "NodeSelectionTint", "Visible"), selected);
            commands.set(nested(index, ringStyle, "Visible"), true);
            commands.set(nested(index, fillStyle, "Visible"), true);
            renderedNodeTypes.put(node.id, type);
            renderedNodeRingStyles.put(node.id, ringStyle);
            renderedNodeFillStyles.put(node.id, fillStyle);
            renderedNodeFrameStates.put(node.id, rank > 0 || selected);
            renderedNodeSelectionTints.put(node.id, selected);
            renderedNodeAllocatedGlows.put(node.id, rank > 0 && !selected);
            renderedNodeTooltipStates.put(node.id, tooltipState(node, type, profile, points, rank, unlocked));
            String iconItemId = placement.iconItemId == null ? "" : placement.iconItemId;
            String iconTexturePath = placement.iconTexturePath == null ? "" : placement.iconTexturePath;
            commands.set(nested(index, "NodeItemIcon", "ItemId"), iconItemId);
            commands.set(nested(index, "NodeItemIcon", "Visible"),
                    iconTexturePath.isBlank() && !iconItemId.isBlank());
            int iconSize = Math.max(10, scaled((int) Math.round(size * 0.58)));
            commands.setObject(nested(index, "NodeItemIcon", "Anchor"), anchor(
                    (renderedSize - iconSize) / 2, (renderedSize - iconSize) / 2,
                    iconSize, iconSize));
            if (!iconTexturePath.isBlank()) {
                commands.setObject(nested(index, "NodeTextureIcon", "Background"), textureBackground(
                        CharacterPathEditorService.nodeIconUiPath(iconTexturePath)));
            }
            commands.set(nested(index, "NodeTextureIcon", "Visible"), !iconTexturePath.isBlank());
            commands.setObject(nested(index, "NodeTextureIcon", "Anchor"), anchor(
                    (renderedSize - iconSize) / 2, (renderedSize - iconSize) / 2,
                    iconSize, iconSize));
            setNodeSearchGlowAnchor(commands, index, renderedSize);
            commands.set(nested(index, "NodeHit", "Text"), "");
            setNodeTooltip(commands, nested(index, "NodeHit", "TooltipText"), node, type, profile, points);
            events.addEventBinding(CustomUIEventBindingType.Activating, nested(index, "NodeHit", null),
                    EventData.of("Action", "tree_select").append("NodeId", node.id));
            index++;
        }
        return index;
    }

    private void fillTreeOverlay(UICommandBuilder commands, Player player, int points, Profile profile) {
        Set<String> visibleNodeIds = renderedNodeChildIndices.keySet();
        commands.set("#DetailBonusesBody.Text", activeBonusSummary(profile, visibleNodeIds));
        commands.set("#BonusTotalsBody.Text", activeBonusTotalsSummary(profile, visibleNodeIds));
        commands.set("#BonusTotalsPopup.Visible", bonusTotalsVisible);
        commands.set("#TreeBonusToggle.Text", bonusTotalsVisible ? "HIDE TOTALS" : "BONUS TOTALS");
        commands.set("#TreeSearchMatches.Text", searchStatusText());
        Node selected = renderedNodesById.get(selectedNodeId);
        if (selected == null || !renderedNodeChildIndices.containsKey(selected.id)) {
            commands.set("#TreeActionSelectedNode.Text", "No node selected");
            commands.set("#DetailAction.Text", "NO TREE PUBLISHED");
            commands.set("#DetailBonusesBody.Text", "No bonuses allocated yet.");
            return;
        }
        int rank = profile.ranks.getOrDefault(selected.id, 0);
        boolean unlocked = isNodeUnlocked(profile, selected);
        boolean canAllocate = unlocked && rank < selected.maxRank && points > 0;
        commands.set("#TreeActionSelectedNode.Text", displayNodeName(selected, profile));
        commands.set("#AvailablePoints.Text", "Available points: " + points);
        commands.set("#DetailAction.Text", rank >= selected.maxRank ? "MAX RANK"
                : selected.pathOnly ? canAllocate ? "CHOOSE PATH STAT" : "PATH NODE (LOCKED)"
                : canAllocate ? "ALLOCATE RANK" : "ALLOCATE (LOCKED)");
    }

    private void fillSelectedNodeOverlay(UICommandBuilder commands, Profile profile, int points) {
        Node selected = renderedNodesById.get(selectedNodeId);
        if (selected == null || !renderedNodeChildIndices.containsKey(selected.id)) {
            commands.set("#TreeActionSelectedNode.Text", "No node selected");
            commands.set("#DetailAction.Text", "NO TREE PUBLISHED");
            return;
        }
        int rank = profile.ranks.getOrDefault(selected.id, 0);
        boolean canAllocate = isNodeUnlocked(profile, selected)
                && rank < selected.maxRank && points > 0;
        commands.set("#TreeActionSelectedNode.Text", displayNodeName(selected, profile));
        commands.set("#DetailAction.Text", rank >= selected.maxRank ? "MAX RANK"
                : selected.pathOnly ? canAllocate ? "CHOOSE PATH STAT" : "PATH NODE (LOCKED)"
                : canAllocate ? "ALLOCATE RANK" : "ALLOCATE (LOCKED)");
    }

    private void refreshPathChoicePopup(UICommandBuilder commands) {
        commands.set("#PathChoicePopup.Visible", pathChoicePopupNodeId != null);
        if (pathChoicePopupNodeId == null) return;
        Node choiceNode = renderedNodesById.get(pathChoicePopupNodeId);
        if (choiceNode != null) commands.set("#PathChoicePrompt.Text",
                "Choose the +1 stat for " + choiceNode.name + ". This choice is saved to your character.");
    }

    private record TooltipSpan(String text, String color, boolean bold) {}

    private static void setNodeTooltip(UICommandBuilder commands, String tooltipTextSelector,
                                       Node node, NodeType type, Profile profile, int points) {
        List<TooltipSpan> spans = nodeTooltip(node, type, profile, points);
        String spansSelector = tooltipTextSelector.substring(0,
                tooltipTextSelector.length() - "TooltipText".length()) + "TooltipTextSpans";
        if (UI_SET_BSON_VALUE != null) {
            try {
                BsonArray values = new BsonArray();
                for (TooltipSpan span : spans) {
                    BsonDocument value = new BsonDocument("Text", new BsonString(span.text()));
                    if (span.color() != null) value.append("Color", new BsonString(span.color()));
                    if (span.bold()) value.append("IsBold", BsonBoolean.TRUE);
                    values.add(value);
                }
                UI_SET_BSON_VALUE.invoke(commands, spansSelector, values);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Keep the page usable if this server build does not expose span data.
            }
        }
    }

    private static java.lang.reflect.Method resolveUiSetBsonValue() {
        try {
            java.lang.reflect.Method method = UICommandBuilder.class.getDeclaredMethod(
                    "setBsonValue", String.class, org.bson.BsonValue.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static List<TooltipSpan> nodeTooltip(Node node, NodeType type, Profile profile, int points) {
        int rank = profile.ranks.getOrDefault(node.id, 0);
        boolean unlocked = isNodeUnlocked(profile, node);
        List<TooltipSpan> lines = new ArrayList<>();
        addTooltipLine(lines, "◆", "#F3C75D", "", "#F3C75D",
                displayNodeName(node, profile), "#F3C75D", true);
        String typeName = switch (type == null ? NodeType.SMALL : type) {
            case SMALL -> "Small node";
            case MEDIUM -> "Medium node";
            case KEYSTONE -> "Keystone";
            case START -> "Starting point";
            case PATH -> "Path node";
        };
        StringBuilder typeLine = new StringBuilder(typeName);
        if (!node.pathOnly) typeLine.append(" - ").append(categoryLabel(node.category));
        if (node.weaponClass != null) typeLine.append(" - ").append(node.weaponClass.displayName);
        addTooltipLine(lines, "●", "#67C9E8", "NODE", "#94D9EB", typeLine.toString(), "#D5DEE8", false);
        if (node.description != null && !node.description.isBlank()) {
            addTooltipLine(lines, "i", "#86B9E8", "INFO", "#AFC7DF", node.description, "#D5DEE8", false);
        }
        if (node.weaponClass != null) {
            addTooltipLine(lines, "↗", "#BD9AFF", "WEAPON", "#BD9AFF",
                    "Bonuses apply while wielding " + node.weaponClass.displayName + '.', "#D5DEE8", false);
        }
        addTooltipLine(lines, "◆", "#F3C75D", "RANK", "#F3C75D",
                rank + " / " + node.maxRank + "  •  " + (node.maxRank == 1 ? "1 point" : "1 point per rank"),
                "#E4EAF0", true);
        if (node.pathOnly) {
            String choice = profile.pathChoices.get(node.id);
            addTooltipLine(lines, "✦", "#76D99A", "BONUS", "#76D99A",
                    rank > 0 ? formatPathEffect(choice, rank)
                            : "Choose +1 max Health, Mana, or Stamina when allocating",
                    "#C6F1D2", false);
        } else {
            addTooltipLine(lines, "+", "#76D99A", "PER RANK", "#76D99A",
                    formatNodeEffects(node, 1), "#C6F1D2", false);
            addTooltipLine(lines, "●", "#60D4C5", "CURRENT", "#60D4C5",
                    formatNodeEffects(node, rank), "#BDECE5", false);
            addTooltipLine(lines, "◆", "#A9D875", "AT MAX", "#A9D875",
                    formatNodeEffects(node, node.maxRank), "#D9EDBF", false);
        }
        if (node.core) {
            addTooltipLine(lines, "★", "#F3C75D", "START", "#F3C75D",
                    "Starting point", "#E4EAF0", false);
        } else if (!node.connections().isEmpty()) {
            addTooltipLine(lines, "↔", "#83B9E8", "LINKS", "#83B9E8",
                    node.connections().stream().map(id -> nodeName(id, profile))
                            .collect(Collectors.joining(", ")), "#D5DEE8", false);
        }
        String state = rank >= node.maxRank ? "Maximum rank reached"
                : !unlocked ? "Locked - allocate a connected node first"
                : points <= 0 ? "Available - earn a skill point from bench processes"
                : node.pathOnly ? "Available - choose a stat to allocate"
                : "Available to allocate";
        String stateColor = rank >= node.maxRank ? "#F3C75D"
                : !unlocked ? "#E78383" : points <= 0 ? "#E9C66B" : "#76D99A";
        addTooltipLine(lines, rank >= node.maxRank ? "◆" : !unlocked ? "!" : "●",
                stateColor, "STATUS", stateColor, state, "#E4EAF0", true);

        return lines;
    }

    private static void addTooltipLine(List<TooltipSpan> lines, String marker, String markerColor,
                                       String label, String labelColor, String value,
                                       String valueColor, boolean boldValue) {
        if (value == null || value.isBlank()) return;
        lines.add(new TooltipSpan(marker + " ", markerColor, true));
        if (label != null && !label.isBlank()) {
            lines.add(new TooltipSpan(label + " ", labelColor, true));
        }
        lines.add(new TooltipSpan(value, valueColor, boldValue));
        lines.add(new TooltipSpan("\n", null, false));
    }

    private int scaled(int value) { return Math.max(1, Math.round(value * zoomPercent / 100.0f)); }
    private int canvasX(int value) { return treeViewport == null ? scaled(value) : treeViewport.x(value); }
    private int canvasY(int value) { return treeViewport == null ? scaled(value) : treeViewport.y(value); }
    private static String child(int index, String suffix) { return "#TreeCanvas[" + index + "]" + suffix; }
    private static String nested(int index, String id, String property) {
        String selector = "#TreeCanvas[" + index + "] #" + id;
        return property == null ? selector : selector + "." + property;
    }
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }

    private static Map<String, EditorNode> editorNodes(Document draft) {
        Map<String, EditorNode> result = new HashMap<>();
        for (EditorNode node : draft.nodes) if (node != null && node.id != null) result.put(node.id, node);
        return result;
    }

    private static Map<String, CharacterPathEdgeRouter.Node> locations(Document draft) {
        Map<String, CharacterPathEdgeRouter.Node> result = new HashMap<>();
        for (EditorNode node : draft.nodes) {
            if (node == null || node.id == null) continue;
            NodeType type = CharacterPathEditorService.type(node);
            result.put(node.id, new CharacterPathEdgeRouter.Node(node.id,
                    node.x + type.size / 2.0, node.y + type.size / 2.0,
                    type.size, type == NodeType.PATH));
        }
        return result;
    }

    private static boolean isVisible(String nodeId) {
        if (nodeId == null) return false;
        EditorNode node = editorNodes(CharacterPathEditorService.snapshot()).get(nodeId);
        return node != null && findNode(nodeId) != null;
    }

    private static String firstVisibleNodeId() {
        Document draft = CharacterPathEditorService.snapshot();
        Map<String, EditorNode> placements = editorNodes(draft);
        return CharacterPathService.nodes().stream().filter(node -> placements.containsKey(node.id))
                .map(node -> node.id).findFirst().orElse(null);
    }

    private static String nodeName(String id, Player player) {
        Node node = findNode(id);
        return node == null ? id : displayNodeName(node, player);
    }

    private static String nodeName(String id, Profile profile) {
        Node node = findNode(id);
        return node == null ? id : displayNodeName(node, profile);
    }

    private static String displayNodeName(Node node, Player player) {
        if (node == null) return "";
        return displayNodeName(node, CharacterPathService.rank(player, node.id),
                CharacterPathService.pathChoice(player, node.id));
    }

    private static String displayNodeName(Node node, Profile profile) {
        if (node == null) return "";
        int rank = profile == null || profile.ranks == null ? 0 : profile.ranks.getOrDefault(node.id, 0);
        String choice = profile == null || profile.pathChoices == null
                ? null : profile.pathChoices.get(node.id);
        return displayNodeName(node, rank, choice);
    }

    private static String displayNodeName(Node node, int rank, String choice) {
        if (!node.pathOnly || rank <= 0) return node.name;
        String choiceName = pathChoiceDisplayName(choice);
        return choiceName == null ? node.name : choiceName;
    }

    private static String pathChoiceDisplayName(String choice) {
        Bonus bonus = CharacterPathService.pathBonus(choice);
        if (bonus == null) return null;
        return switch (bonus) {
            case MAX_HEALTH_FLAT -> "HP Up";
            case MAX_MANA_FLAT -> "MP Up";
            case MAX_STAMINA_FLAT -> "STM Up";
            default -> null;
        };
    }

    private static Node findNode(String id) {
        if (id == null) return null;
        return CharacterPathService.nodes().stream().filter(node -> node.id.equals(id)).findFirst().orElse(null);
    }

    private static String activeBonusSummary(Profile profile, Set<String> visibleNodeIds) {
        if (profile == null || profile.ranks == null || profile.ranks.isEmpty()) return "No bonuses allocated yet.";
        StringBuilder summary = new StringBuilder();
        for (Node node : CharacterPathService.nodes()) {
            int rank = profile.ranks.getOrDefault(node.id, 0);
            if (rank <= 0 || !visibleNodeIds.contains(node.id)) continue;
            if (summary.length() > 0) summary.append('\n');
            summary.append(displayNodeName(node, profile)).append(' ').append(rank).append('/').append(node.maxRank)
                    .append(node.weaponClass == null ? "" : " [" + node.weaponClass.displayName + "]")
                    .append(" (").append(node.pathOnly
                            ? formatPathEffect(profile.pathChoices.get(node.id), rank)
                            : formatNodeEffects(node, rank)).append(')');
        }
        return summary.length() == 0 ? "No bonuses allocated yet." : summary.toString();
    }

    private static Set<String> findSearchMatches(String query, Document draft, Player player) {
        if (query == null || query.isBlank() || draft == null) return Set.of();
        String normalizedQuery = query.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        Bonus pathStatSearch = pathChoiceSearchBonus(normalizedQuery);
        Map<String, EditorNode> placements = editorNodes(draft);
        Set<String> matches = new java.util.LinkedHashSet<>();
        String[] queryTerms = normalizedQuery.split("\\s+");
        for (Node node : CharacterPathService.nodes()) {
            if (!placements.containsKey(node.id)) continue;
            if (node.pathOnly && pathStatSearch != null) {
                // A path node matches only after allocation, and only for the stat it was assigned.
                if (CharacterPathService.rank(player, node.id) > 0
                        && CharacterPathService.pathBonus(CharacterPathService.pathChoice(player, node.id))
                        == pathStatSearch) matches.add(node.id);
                continue;
            }
            StringBuilder searchable = new StringBuilder();
            appendSearchTerm(searchable, node.name);
            appendSearchTerm(searchable, node.id);
            appendSearchTerm(searchable, node.description);
            appendSearchTerm(searchable, node.category == null ? null : node.category.name());
            appendSearchTerm(searchable, categoryLabel(node.category));
            if (node.weaponClass != null) {
                appendSearchTerm(searchable, node.weaponClass.name());
                appendSearchTerm(searchable, node.weaponClass.displayName);
            }
            for (NodeBonus effect : node.bonuses) {
                appendSearchTerm(searchable, effect.bonus().name());
                appendSearchTerm(searchable, bonusLabel(effect.bonus()));
            }
            if (node.pathOnly) {
                appendSearchTerm(searchable, "path");
                appendSearchTerm(searchable, "health hp hp up health up max health max hp");
                appendSearchTerm(searchable, "mana mp mp up mana up max mana");
                appendSearchTerm(searchable, "stamina stm stm up stamina up max stamina");
            }
            String normalizedSearchable = searchable.toString().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ');
            boolean matchesEveryTerm = true;
            for (String term : queryTerms) {
                if (!normalizedSearchable.contains(term)) {
                    matchesEveryTerm = false;
                    break;
                }
            }
            if (matchesEveryTerm) matches.add(node.id);
        }
        return matches;
    }

    private static Bonus pathChoiceSearchBonus(String query) {
        if (query == null || query.isBlank()) return null;
        String compactQuery = query.replaceAll("[^a-z0-9]", "");
        return switch (compactQuery) {
            case "health", "hp", "hpup", "healthup", "maxhealth", "maxhp", "maxhealthflat" -> Bonus.MAX_HEALTH_FLAT;
            case "mana", "mp", "mpup", "manaup", "maxmana", "maxmp", "maxmanaflat" -> Bonus.MAX_MANA_FLAT;
            case "stamina", "stm", "stmup", "staminaup", "maxstamina", "maxstm", "maxstaminaflat" -> Bonus.MAX_STAMINA_FLAT;
            default -> null;
        };
    }

    private static void appendSearchTerm(StringBuilder searchable, String value) {
        if (value == null || value.isBlank()) return;
        searchable.append(' ').append(value).append(' ')
                .append(value.replace('_', ' '));
    }

    private String searchStatusText() {
        if (!searchRequestReceived) return "Type a query, then click GO";
        if (nodeSearchQuery == null || nodeSearchQuery.isBlank()) return "Search received, but query is empty";
        int count = searchMatchNodeIds.size();
        String query = nodeSearchQuery.length() > 22
                ? nodeSearchQuery.substring(0, 19) + "..." : nodeSearchQuery;
        return count == 0 ? "NO NODES FOUND: \"" + query + "\""
                : count + " NODES FOUND: \"" + query + "\"";
    }

    private void setNodeSearchGlowAnchor(UICommandBuilder commands, int index, int nodeSize) {
        int glowSize = nodeSize + Math.max(10, scaled(10));
        int glowOffset = (nodeSize - glowSize) / 2;
        commands.setObject(nested(index, "NodeSearchGlow", "Anchor"),
                anchor(glowOffset, glowOffset, glowSize, glowSize));
    }

    private static String activeBonusTotalsSummary(Profile profile, Set<String> visibleNodeIds) {
        if (profile == null || profile.ranks == null || profile.ranks.isEmpty()) {
            return "No bonuses allocated yet.";
        }
        Map<Bonus, Double> general = new EnumMap<>(Bonus.class);
        Map<WeaponClass, Map<Bonus, Double>> weaponTotals = new EnumMap<>(WeaponClass.class);
        for (Node node : CharacterPathService.nodes()) {
            int rank = profile.ranks.getOrDefault(node.id, 0);
            if (rank <= 0 || !visibleNodeIds.contains(node.id)) continue;
            if (node.pathOnly) {
                Bonus pathBonus = CharacterPathService.pathBonus(profile.pathChoices.get(node.id));
                if (pathBonus != null) general.merge(pathBonus, (double) rank, Double::sum);
                continue;
            }
            Map<Bonus, Double> totals = node.weaponClass == null ? general
                    : weaponTotals.computeIfAbsent(node.weaponClass, ignored -> new EnumMap<>(Bonus.class));
            for (NodeBonus effect : node.bonuses) {
                totals.merge(effect.bonus(), effect.perRank() * rank, Double::sum);
            }
        }

        StringBuilder summary = new StringBuilder();
        appendBonusTotals(summary, "Character-wide", general);
        for (WeaponClass weaponClass : WeaponClass.values()) {
            Map<Bonus, Double> totals = weaponTotals.get(weaponClass);
            if (totals == null || totals.isEmpty()) continue;
            if (summary.length() > 0) summary.append("\n\n");
            summary.append(weaponClass.displayName).append(": ");
            StringJoiner joined = new StringJoiner(" | ");
            for (Bonus bonus : Bonus.values()) {
                Double amount = totals.get(bonus);
                if (amount != null && Math.abs(amount) > 0.000001) joined.add(formatBonusTotal(bonus, amount));
            }
            summary.append(joined);
        }
        return summary.length() == 0 ? "No bonuses allocated yet." : summary.toString();
    }

    private static void appendBonusTotals(StringBuilder output, String heading, Map<Bonus, Double> totals) {
        boolean hasTotals = totals.values().stream().anyMatch(amount -> Math.abs(amount) > 0.000001);
        if (!hasTotals) return;
        if (output.length() > 0) output.append("\n\n");
        output.append(heading);
        for (Bonus bonus : Bonus.values()) {
            Double amount = totals.get(bonus);
            if (amount == null || Math.abs(amount) <= 0.000001) continue;
            output.append('\n').append(formatBonusTotal(bonus, amount));
        }
    }

    private static String formatBonusTotal(Bonus bonus, double amount) {
        boolean percent = isPercentBonus(bonus);
        double displayAmount = percent ? amount * 100.0 : amount;
        String sign = displayAmount < 0.0 ? "-" : "+";
        return sign + formatAmount(Math.abs(displayAmount)) + (percent ? "% " : " ") + bonusLabel(bonus);
    }

    private static boolean isPercentBonus(Bonus bonus) {
        return switch (bonus) {
            case DAMAGE_PERCENT, DAMAGE_RESISTANCE, CRIT_DAMAGE_PERCENT, CRIT_CHANCE_PERCENT,
                    ATTACK_SPEED_PERCENT, LORE_XP_GAIN_PERCENT,
                    ELEMENTAL_DAMAGE, ELEMENTAL_DOT_CRIT_CHANCE, ELEMENTAL_DOT_CRIT_DAMAGE,
                    ELEMENTAL_STACK_BASE, POISON_STACK_BASE, BLEED_STACK_BASE,
                    REFINING_CHANCE, REFINING_BREAK_PROTECTION, REFINING_DEGRADE_PROTECTION,
                    REFINING_SAME_CHANCE, REFINING_UPGRADE_CHANCE, REFINING_JACKPOT_CHANCE,
                    SOCKET_CHANCE -> true;
            default -> false;
        };
    }

    private static String bonusLabel(Bonus bonus) {
        return switch (bonus) {
            case MAX_HEALTH_FLAT -> "max HP";
            case HEALTH_REGEN_PER_SECOND -> "HP/s regen";
            case MAX_MANA_FLAT -> "max MP";
            case MANA_REGEN_PER_SECOND -> "MP/s regen";
            case MAX_STAMINA_FLAT -> "max stamina";
            case STAMINA_REGEN_PER_SECOND -> "stamina/s regen";
            case DAMAGE_PERCENT -> "damage";
            case DAMAGE_RESISTANCE -> "damage resistance";
            case CRIT_DAMAGE_PERCENT -> "critical damage";
            case CRIT_CHANCE_PERCENT -> "critical chance";
            case ATTACK_SPEED_PERCENT -> "attack speed (multi-hit chance)";
            case LORE_XP_GAIN_PERCENT -> "lore XP gain";
            case ELEMENTAL_DAMAGE -> "elemental damage";
            case ELEMENTAL_DOT_CRIT_CHANCE -> "elemental status critical chance";
            case ELEMENTAL_DOT_CRIT_DAMAGE -> "elemental status critical damage";
            case ELEMENTAL_STACK_BASE -> "elemental stack base";
            case POISON_STACK_BASE -> "poison stack base";
            case BLEED_STACK_BASE -> "bleed stack base";
            case REFINING_CHANCE -> "refining upgrade guarantee";
            case REFINING_BREAK_PROTECTION -> "refining break protection";
            case REFINING_DEGRADE_PROTECTION -> "refining degrade protection";
            case REFINING_SAME_CHANCE -> "refining safe outcome weight";
            case REFINING_UPGRADE_CHANCE -> "refining upgrade weight";
            case REFINING_JACKPOT_CHANCE -> "refining jackpot weight";
            case SOCKET_CHANCE -> "socket chance";
        };
    }

    private static String formatNodeEffects(Node node, int rank) {
        if (node == null || rank <= 0) return "no bonus";
        List<String> entries = new ArrayList<>();
        for (NodeBonus effect : node.bonuses) {
            double amount = effect.perRank() * rank;
            Bonus bonus = effect.bonus();
            entries.add(formatAmount(isPercentBonus(bonus) ? amount * 100.0 : amount)
                    + (isPercentBonus(bonus) ? "% " : " ") + bonusLabel(bonus));
        }
        return entries.isEmpty() ? "no bonus" : String.join(" + ", entries);
    }

    private static String formatPathEffect(String choice, int rank) {
        if (rank <= 0) return "No stat chosen yet";
        Bonus bonus = CharacterPathService.pathBonus(choice);
        if (bonus == Bonus.MAX_MANA_FLAT) return "+1 max Mana";
        if (bonus == Bonus.MAX_STAMINA_FLAT) return "+1 max Stamina";
        return "+1 max Health";
    }

    private static String formatAmount(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String categoryLabel(Category category) {
        if (category == null) return "Path";
        return switch (category) {
            case VITALITY -> "Vitality"; case ARCANA -> "Arcana"; case ENDURANCE -> "Endurance";
            case COMBAT -> "Combat"; case GUARD -> "Guard"; case DAGGERS -> "Daggers";
            case SWORDS -> "Swords"; case AXES -> "Axes"; case MACES -> "Maces";
            case SPEARS -> "Spears"; case SHORTBOWS -> "Shortbows"; case CROSSBOWS -> "Crossbows"; case STAFFS -> "Staves";
        };
    }
    private static Set<String> availablePathEdges(Profile profile, int points) {
        Set<String> availableEdges = new HashSet<>();
        if (profile == null || points < 1) return availableEdges;

        for (Node candidate : CharacterPathService.nodes()) {
            int rank = profile.ranks.getOrDefault(candidate.id, 0);
            if (rank > 0 || rank >= candidate.maxRank
                    || !isNodeUnlocked(profile, candidate)) continue;

            // Only show the edge into an available node. Following edges beyond it
            // would imply that the player can skip that node's point cost.
            if (candidate.core) {
                continue;
            }
            for (String connectedId : candidate.connections()) {
                if (profile.ranks.getOrDefault(connectedId, 0) > 0) {
                    availableEdges.add(edgeKey(candidate.id, connectedId));
                }
            }
        }
        return availableEdges;
    }

    private static String edgeKey(String first, String second) {
        return first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first;
    }

    private static boolean isNodeUnlocked(Profile profile, Node node) {
        if (node == null || profile == null) return false;
        if (node.core) return true;
        for (String connectedId : node.connections()) {
            if (profile.ranks.getOrDefault(connectedId, 0) > 0) return true;
        }
        return false;
    }

    private static String edgeColor(Profile profile, Node first, Node second, Set<String> availablePathEdges) {
        if (first == null || second == null) return "#303844";
        if (availablePathEdges.contains(edgeKey(first.id, second.id))) return "#63aa7c";
        if (profile.ranks.getOrDefault(first.id, 0) > 0 || profile.ranks.getOrDefault(second.id, 0) > 0) return "#4d9ed8";
        if (first.pathOnly && second.pathOnly) return "#665333";
        return "#303844";
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public static final class PageEvent {
        public static final BuilderCodec<PageEvent> CODEC = BuilderCodec.<PageEvent>builder(PageEvent.class, PageEvent::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (event, value) -> event.action = value, event -> event.action).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (event, value) -> event.target = value, event -> event.target).add()
                .append(new KeyedCodec<>("NodeId", Codec.STRING), (event, value) -> event.nodeId = value, event -> event.nodeId).add()
                .append(new KeyedCodec<>("@Query", Codec.STRING), (event, value) -> event.query = value, event -> event.query).add()
                .build();
        public String action;
        public String target;
        public String nodeId;
        public String query;
        public PageEvent() {}
    }
}
