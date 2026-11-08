package irai.mod.reforge.UI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import irai.mod.reforge.Progression.CharacterPathService.GraphEdge;

/** Builds clipped, obstacle-avoiding paths and reserves their corridors so edges cannot cross. */
final class CharacterPathEdgeRouter {
    record Node(String id, double x, double y, double size, boolean pathOnly) {
        // Route endpoints touch the visible node perimeter. Nodes are rendered after edges,
        // so the innermost pixels are naturally covered by the node ring.
        double radius() { return size / 2.0; }
    }

    record Point(double x, double y) {}
    record RoutedEdge(GraphEdge edge, List<Point> points) {}
    private record Segment(Point from, Point to, String firstNode, String secondNode) {}
    private record Work(GraphEdge edge, Node first, Node second, double length, boolean guideRing) {}
    private record SearchEntry(int cell, int score) {}

    private static final int GRID = 12;
    // Keep link margins near the rendered line width so compact grid layouts fit.
    private static final int CLEARANCE = 6;
    private static final double ROUTING_GRID_MARGIN = 0.25;
    private final Map<String, Node> nodes;
    private final int minX;
    private final int minY;
    private final int columns;
    private final int rows;
    private final boolean[] occupied;
    private final List<Segment> segments = new ArrayList<>();

    private CharacterPathEdgeRouter(Map<String, Node> nodes, int width, int height) {
        this.nodes = nodes;
        // Keep routes inside the visible canvas; every detour remains on-screen.
        minX = 0;
        minY = 0;
        columns = (width + GRID - 1) / GRID + 1;
        rows = (height + GRID - 1) / GRID + 1;
        occupied = new boolean[columns * rows];
    }

    static List<RoutedEdge> route(List<GraphEdge> graph, Map<String, Node> nodes, int width, int height) {
        CharacterPathEdgeRouter router = new CharacterPathEdgeRouter(nodes, width, height);
        List<Work> work = new ArrayList<>();
        for (GraphEdge edge : graph) {
            Node first = nodes.get(edge.first());
            Node second = nodes.get(edge.second());
            if (first == null || second == null) continue;
            work.add(new Work(edge, first, second, distance(first, second),
                    first.pathOnly() && second.pathOnly()));
        }
        // Reserve the octagon first, then short local cluster links, then long bridges.
        work.sort(Comparator.comparing(Work::guideRing).reversed().thenComparingDouble(Work::length));

        List<RoutedEdge> result = new ArrayList<>(work.size());
        for (Work edge : work) {
            List<Point> points = router.plan(edge);
            if (points == null || points.size() < 2) {
                throw new IllegalStateException("Unable to route skill-tree edge without a crossing: "
                        + edge.edge().first() + " -> " + edge.edge().second());
            }
            result.add(new RoutedEdge(edge.edge(), List.copyOf(points)));
            router.reserve(points, edge.first(), edge.second());
        }
        return List.copyOf(result);
    }

    private List<Point> plan(Work work) {
        double base = Math.atan2(work.second().y() - work.first().y(), work.second().x() - work.first().x());
        int[] offsets = {0, 1, -1, 2, -2, 3, -3, 4};
        List<Point> direct = null;
        for (int firstOffset : offsets) {
            double firstAngle = base + firstOffset * Math.PI / 4.0;
            Point start = port(work.first(), firstAngle);
            for (int secondOffset : offsets) {
                double secondAngle = base + Math.PI + secondOffset * Math.PI / 4.0;
                Point end = port(work.second(), secondAngle);
                if (routeIsClear(List.of(start, end), work.first(), work.second())) {
                    direct = List.of(start, end);
                    break;
                }
            }
            if (direct != null) break;
        }
        if (direct != null) return direct;

        for (int firstOffset : offsets) {
            double firstAngle = base + firstOffset * Math.PI / 4.0;
            Point start = port(work.first(), firstAngle);
            for (int secondOffset : offsets) {
                double secondAngle = base + Math.PI + secondOffset * Math.PI / 4.0;
                Point end = port(work.second(), secondAngle);
                List<Point> routed = search(start, end, firstAngle, secondAngle, work.first(), work.second());
                if (routed != null && routeIsClear(routed, work.first(), work.second())) return routed;
            }
        }
        return null;
    }

    private Point port(Node node, double angle) {
        double radius = node.radius();
        return new Point(node.x() + Math.cos(angle) * radius, node.y() + Math.sin(angle) * radius);
    }

    private List<Point> search(Point start, Point end, double startAngle, double endAngle,
                               Node source, Node target) {
        Point startGrid = gridPoint(new Point(start.x() + Math.cos(startAngle) * GRID * 2.0,
                start.y() + Math.sin(startAngle) * GRID * 2.0));
        Point endGrid = gridPoint(new Point(end.x() + Math.cos(endAngle) * GRID * 2.0,
                end.y() + Math.sin(endAngle) * GRID * 2.0));
        if (!clearSegment(start, startGrid, source.id(), target.id())
                || !clearSegment(endGrid, end, source.id(), target.id())) return null;

        boolean[] blocked = buildBlockedCells(source, target);
        int startCell = cell(startGrid);
        int endCell = cell(endGrid);
        if (startCell < 0 || endCell < 0 || blocked[startCell] || blocked[endCell]) return null;

        int total = columns * rows;
        int[] score = new int[total];
        int[] parent = new int[total];
        Arrays.fill(score, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        PriorityQueue<SearchEntry> open = new PriorityQueue<>(Comparator.comparingInt(SearchEntry::score));
        score[startCell] = 0;
        open.add(new SearchEntry(startCell, heuristic(startCell, endCell)));
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        int expansions = 0;
        boolean found = false;
        while (!open.isEmpty() && expansions++ < total) {
            SearchEntry entry = open.poll();
            int current = entry.cell();
            if (entry.score() != score[current] + heuristic(current, endCell)) continue;
            if (current == endCell) { found = true; break; }
            int currentX = current % columns;
            int currentY = current / columns;
            for (int i = 0; i < dx.length; i++) {
                int nextX = currentX + dx[i];
                int nextY = currentY + dy[i];
                if (nextX < 0 || nextY < 0 || nextX >= columns || nextY >= rows) continue;
                int next = nextY * columns + nextX;
                if (blocked[next]) continue;
                int nextScore = score[current] + 1;
                if (nextScore >= score[next]) continue;
                score[next] = nextScore;
                parent[next] = current;
                open.add(new SearchEntry(next, nextScore + heuristic(next, endCell)));
            }
        }
        if (!found) return null;

        List<Point> reverse = new ArrayList<>();
        for (int cursor = endCell; cursor != -1; cursor = parent[cursor]) {
            reverse.add(point(cursor));
            if (cursor == startCell) break;
        }
        java.util.Collections.reverse(reverse);
        List<Point> points = new ArrayList<>();
        points.add(start);
        points.add(startGrid);
        points.addAll(reverse);
        points.add(endGrid);
        points.add(end);
        return simplify(points);
    }

    private boolean[] buildBlockedCells(Node source, Node target) {
        boolean[] blocked = Arrays.copyOf(occupied, occupied.length);
        for (Node node : nodes.values()) {
            if (node.id().equals(source.id()) || node.id().equals(target.id())) continue;
            blockCircle(blocked, node.x(), node.y(), node.radius() + CLEARANCE + GRID * ROUTING_GRID_MARGIN);
        }
        return blocked;
    }

    private void blockCircle(boolean[] blocked, double x, double y, double radius) {
        int centerX = gridX(x);
        int centerY = gridY(y);
        int cells = (int) Math.ceil(radius / GRID) + 1;
        for (int gy = Math.max(0, centerY - cells); gy <= Math.min(rows - 1, centerY + cells); gy++) {
            for (int gx = Math.max(0, centerX - cells); gx <= Math.min(columns - 1, centerX + cells); gx++) {
                Point p = gridCoordinate(gx, gy);
                if (squared(p.x() - x, p.y() - y) <= radius * radius) blocked[gy * columns + gx] = true;
            }
        }
    }

    private boolean clearSegment(Point from, Point to, String sourceId, String targetId) {
        if (distance(from, to) < 1.0) return true;
        for (Node node : nodes.values()) {
            if (node.id().equals(sourceId) || node.id().equals(targetId)) continue;
            if (pointSegmentDistanceSquared(node.x(), node.y(), from, to)
                    < squared(node.radius() + CLEARANCE)) return false;
        }
        for (Segment segment : segments) {
            if (segmentDistanceSquared(from, to, segment.from(), segment.to()) < squared(CLEARANCE)) return false;
        }
        return true;
    }

    private boolean routeIsClear(List<Point> points, Node source, Node target) {
        if (points.size() < 2
                || !leavesNode(points.get(0), points.get(1), source)
                || !leavesNode(points.get(points.size() - 1), points.get(points.size() - 2), target)) {
            return false;
        }
        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1);
            Point to = points.get(i);
            if (!clearSegment(from, to, source.id(), target.id())) return false;
            if (pointSegmentDistanceSquared(source.x(), source.y(), from, to)
                    < squared(Math.max(0.0, source.radius() - 0.25))
                    || pointSegmentDistanceSquared(target.x(), target.y(), from, to)
                    < squared(Math.max(0.0, target.radius() - 0.25))) return false;
        }
        return true;
    }

    /** The first/last segment must leave its endpoint radially, never pass through the node. */
    private static boolean leavesNode(Point endpoint, Point outside, Node node) {
        double radialX = endpoint.x() - node.x();
        double radialY = endpoint.y() - node.y();
        double travelX = outside.x() - endpoint.x();
        double travelY = outside.y() - endpoint.y();
        return radialX * travelX + radialY * travelY > 0.01;
    }

    private void reserve(List<Point> points, Node source, Node target) {
        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1);
            Point to = points.get(i);
            segments.add(new Segment(from, to, source.id(), target.id()));
            int samples = Math.max(1, (int) Math.ceil(distance(from, to) / (GRID / 3.0)));
            for (int sample = 0; sample <= samples; sample++) {
                double t = sample / (double) samples;
                double x = from.x() + (to.x() - from.x()) * t;
                double y = from.y() + (to.y() - from.y()) * t;
                if (insideNode(x, y, source) || insideNode(x, y, target)) continue;
                int gx = gridX(x);
                int gy = gridY(y);
                if (gx >= 0 && gy >= 0 && gx < columns && gy < rows) {
                    occupied[gy * columns + gx] = true;
                }
            }
        }
    }

    private static boolean insideNode(double x, double y, Node node) {
        double visibleRadius = Math.max(0.0, node.size() / 2.0 - 1.0);
        return squared(x - node.x(), y - node.y()) <= visibleRadius * visibleRadius;
    }

    private Point gridPoint(Point point) { return gridCoordinate(gridX(point.x()), gridY(point.y())); }
    private Point gridCoordinate(int x, int y) { return new Point(minX + x * GRID, minY + y * GRID); }
    private int gridX(double x) { return (int) Math.round((x - minX) / GRID); }
    private int gridY(double y) { return (int) Math.round((y - minY) / GRID); }
    private int cell(Point point) {
        int x = gridX(point.x());
        int y = gridY(point.y());
        return x < 0 || y < 0 || x >= columns || y >= rows ? -1 : y * columns + x;
    }
    private Point point(int cell) { return gridCoordinate(cell % columns, cell / columns); }
    private int heuristic(int first, int second) {
        return Math.abs(first % columns - second % columns) + Math.abs(first / columns - second / columns);
    }

    private static List<Point> simplify(List<Point> source) {
        List<Point> result = new ArrayList<>();
        for (Point point : source) {
            if (!result.isEmpty() && distance(result.get(result.size() - 1), point) < 1.0) continue;
            while (result.size() >= 2 && collinear(result.get(result.size() - 2), result.get(result.size() - 1), point)) {
                result.remove(result.size() - 1);
            }
            result.add(point);
        }
        return result;
    }

    private static boolean collinear(Point a, Point b, Point c) {
        double area = (b.x() - a.x()) * (c.y() - b.y()) - (b.y() - a.y()) * (c.x() - b.x());
        return Math.abs(area) < 0.01;
    }

    private static double segmentDistanceSquared(Point a, Point b, Point c, Point d) {
        if (segmentsIntersect(a, b, c, d)) return 0.0;
        return Math.min(Math.min(pointSegmentDistanceSquared(a.x(), a.y(), c, d),
                        pointSegmentDistanceSquared(b.x(), b.y(), c, d)),
                Math.min(pointSegmentDistanceSquared(c.x(), c.y(), a, b),
                        pointSegmentDistanceSquared(d.x(), d.y(), a, b)));
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        double o1 = orientation(a, b, c);
        double o2 = orientation(a, b, d);
        double o3 = orientation(c, d, a);
        double o4 = orientation(c, d, b);
        return ((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0))
                && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0));
    }

    private static double orientation(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private static double pointSegmentDistanceSquared(double x, double y, Point a, Point b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double length = dx * dx + dy * dy;
        if (length < 1.0e-8) return squared(x - a.x(), y - a.y());
        double t = Math.max(0.0, Math.min(1.0, ((x - a.x()) * dx + (y - a.y()) * dy) / length));
        return squared(x - (a.x() + t * dx), y - (a.y() + t * dy));
    }

    private static double distance(Node a, Node b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static double distance(Point a, Point b) { return Math.hypot(a.x() - b.x(), a.y() - b.y()); }
    private static double squared(double x, double y) { return x * x + y * y; }
    private static double squared(double value) { return value * value; }
}
