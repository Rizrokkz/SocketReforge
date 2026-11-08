package irai.mod.reforge.UI;

import irai.mod.reforge.Progression.CharacterPathEditorService;
import irai.mod.reforge.Progression.CharacterPathEditorService.Document;
import irai.mod.reforge.Progression.CharacterPathEditorService.EditorNode;

/**
 * Fits a skill-tree graph into the scroll panel without changing its saved
 * canvas coordinates. This mirrors Pathforge's bounds-based tree layout and
 * avoids depending on client scroll restoration during page construction.
 */
final class CharacterPathTreeViewport {
    private static final int PADDING = 48;

    private CharacterPathTreeViewport() {}

    static View create(Document document, int requestedZoom, boolean fitZoom,
                       int viewportWidth, int viewportHeight,
                       int minZoom, int maxZoom) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        if (document != null && document.nodes != null) {
            for (EditorNode node : document.nodes) {
                if (node == null) continue;
                int size = CharacterPathEditorService.type(node).size;
                minX = Math.min(minX, node.x);
                minY = Math.min(minY, node.y);
                maxX = Math.max(maxX, node.x + size);
                maxY = Math.max(maxY, node.y + size);
            }
        }

        boolean hasNodes = minX != Integer.MAX_VALUE;
        if (!hasNodes) {
            int canvasWidth = document == null ? 0 : document.canvasWidth;
            int canvasHeight = document == null ? 0 : document.canvasHeight;
            minX = canvasWidth / 2;
            minY = canvasHeight / 2;
            maxX = minX + 1;
            maxY = minY + 1;
        }

        int zoom = clamp(requestedZoom, minZoom, maxZoom);
        if (fitZoom && hasNodes) {
            int graphWidth = Math.max(1, maxX - minX);
            int graphHeight = Math.max(1, maxY - minY);
            double fitScale = Math.min(
                    Math.max(1, viewportWidth - PADDING * 2) / (double) graphWidth,
                    Math.max(1, viewportHeight - PADDING * 2) / (double) graphHeight);
            int fitPercent = (int) Math.floor(fitScale * 100.0);
            fitPercent = Math.floorDiv(fitPercent, 5) * 5;
            zoom = clamp(fitPercent, minZoom, maxZoom);
        }

        int graphWidth = Math.max(1, scaled(maxX - minX, zoom));
        int graphHeight = Math.max(1, scaled(maxY - minY, zoom));
        int canvasWidth = document == null ? 0 : scaled(document.canvasWidth, zoom);
        int canvasHeight = document == null ? 0 : scaled(document.canvasHeight, zoom);
        int graphLeft = Math.max(PADDING, (viewportWidth - graphWidth) / 2);
        int graphTop = Math.max(PADDING, (viewportHeight - graphHeight) / 2);
        int contentWidth = Math.max(Math.max(viewportWidth, canvasWidth),
                graphLeft + graphWidth + PADDING);
        int contentHeight = Math.max(Math.max(viewportHeight, canvasHeight),
                graphTop + graphHeight + PADDING);
        int offsetX = graphLeft - scaled(minX, zoom);
        int offsetY = graphTop - scaled(minY, zoom);
        return new View(minX, minY, maxX, maxY, zoom, contentWidth, contentHeight,
                offsetX, offsetY);
    }

    private static int scaled(int value, int zoom) {
        return (int) Math.round(value * zoom / 100.0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class View {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;
        final int zoomPercent;
        final int contentWidth;
        final int contentHeight;
        private final int offsetX;
        private final int offsetY;

        private View(int minX, int minY, int maxX, int maxY, int zoomPercent,
                     int contentWidth, int contentHeight, int offsetX, int offsetY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.zoomPercent = zoomPercent;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        int x(int logicalX) {
            return offsetX + scaled(logicalX, zoomPercent);
        }

        int y(int logicalY) {
            return offsetY + scaled(logicalY, zoomPercent);
        }

        boolean hasSameGraphBounds(View other) {
            return other != null && minX == other.minX && minY == other.minY
                    && maxX == other.maxX && maxY == other.maxY;
        }
    }
}
