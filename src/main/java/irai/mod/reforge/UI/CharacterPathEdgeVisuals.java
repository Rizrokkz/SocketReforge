package irai.mod.reforge.UI;

/** Shared texture selection for edge segments; orientation is baked into the UI texture. */
final class CharacterPathEdgeVisuals {
    private CharacterPathEdgeVisuals() {}

    static String orientation(double dx, double dy) {
        if (Math.abs(dy) < 0.5) return "Horizontal";
        if (Math.abs(dx) < 0.5) return "Vertical";
        return dx * dy >= 0.0 ? "DiagonalDown" : "DiagonalUp";
    }

    static String texture(String edgeStyle, String orientation) {
        return texture(edgeStyle, orientation, 0.0, 0.0);
    }

    static String texture(String edgeStyle, String orientation, double dx, double dy) {
        String style = edgeStyle.startsWith("Edge") ? edgeStyle.substring(4) : edgeStyle;
        // UI texture paths resolve from Common/UI/Custom, not relative to the .ui file.
        if (("DiagonalDown".equals(orientation) || "DiagonalUp".equals(orientation))
                && !"Ranked".equals(style)) {
            String width = thinWidth(Math.abs(dx), Math.abs(dy));
            return "Pages/SkillTreeEdge_" + style + "_" + orientation + "_Thin" + width + ".png";
        }
        return "Pages/SkillTreeEdge_" + style + "_" + orientation + ".png";
    }

    private static String thinWidth(double segmentWidth, double segmentHeight) {
        // The diagonal source is square, but the UI stretches it to the segment bounds.
        // Compensate for that non-uniform scaling so shallow slopes keep a solid stroke.
        double width = Math.max(1.0, segmentWidth);
        double height = Math.max(1.0, segmentHeight);
        double targetTextureWidth = 4.0 * 256.0 * Math.hypot(width, height)
                / (Math.sqrt(2.0) * width * height);
        double[] widths = {0.25, 0.5, 1, 2, 4, 8, 16, 32, 64};
        String[] labels = {"025", "05", "1", "2", "4", "8", "16", "32", "64"};
        int closest = 0;
        for (int i = 1; i < widths.length; i++) {
            if (Math.abs(widths[i] - targetTextureWidth)
                    < Math.abs(widths[closest] - targetTextureWidth)) closest = i;
        }
        return labels[closest];
    }
}
