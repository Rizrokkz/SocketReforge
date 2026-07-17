package irai.mod.reforge.Common.UI;

/**
 * Small reusable HTML fragments for HyUI templates.
 */
public final class UIHtmlUtils {

    private UIHtmlUtils() {}

    public static String button(String id, String label, int width, int height, boolean disabled) {
        return "<button id=\"" + UITemplateUtils.escapeHtml(id)
                + "\" style=\"anchor-width:" + Math.max(1, width)
                + "; anchor-height:" + Math.max(1, height) + ";\""
                + (disabled ? " disabled=\"true\"" : "")
                + ">" + UITemplateUtils.escapeHtml(label) + "</button>";
    }

    public static String pagerButton(String id, String label, int width, int height) {
        return button(id, label, width, height, false);
    }

    public static String secondaryPagerButton(String id, String label, int width, int height) {
        return "<button id=\"" + UITemplateUtils.escapeHtml(id)
                + "\" class=\"small-secondary-button\" style=\"anchor-width:" + Math.max(1, width)
                + "; anchor-height:" + Math.max(1, height) + ";\">"
                + UITemplateUtils.escapeHtml(label)
                + "</button>";
    }

    public static String pager(String prevId,
                               String prevLabel,
                               String pageLabel,
                               String nextId,
                               String nextLabel,
                               int width,
                               int height,
                               int buttonWidth,
                               int buttonHeight,
                               boolean secondaryButtons) {
        if (width <= 0) {
            width = 320;
        }
        int labelWidth = Math.max(80, width - (buttonWidth * 2) - 16);
        String prev = secondaryButtons
                ? secondaryPagerButton(prevId, prevLabel, buttonWidth, buttonHeight)
                : pagerButton(prevId, prevLabel, buttonWidth, buttonHeight);
        String next = secondaryButtons
                ? secondaryPagerButton(nextId, nextLabel, buttonWidth, buttonHeight)
                : pagerButton(nextId, nextLabel, buttonWidth, buttonHeight);
        return "<div style=\"anchor-width:" + width
                + "; anchor-height:" + Math.max(1, height)
                + "; layout-mode:left; spacing:8;\">"
                + prev
                + "<p style=\"anchor-width:" + labelWidth + "; text-align:center;\">"
                + UITemplateUtils.escapeHtml(pageLabel)
                + "</p>"
                + next
                + "</div>";
    }

    public static String progressBar(String id,
                                     int value,
                                     int width,
                                     int height,
                                     String fillTexture,
                                     String trackTexture) {
        int clamped = Math.max(0, Math.min(100, value));
        return "<progress id=\"" + UITemplateUtils.escapeHtml(id)
                + "\" max=\"100\" value=\"" + clamped + "\""
                + " data-hyui-bar-texture-path=\"" + UITemplateUtils.escapeHtml(fillTexture) + "\""
                + " style=\"anchor-width:" + Math.max(1, width)
                + "; anchor-height:" + Math.max(1, height)
                + "; background-image:url('" + UITemplateUtils.escapeHtml(trackTexture) + "');"
                + " background-size:100% 100%; background-repeat:no-repeat;\"></progress>";
    }
}
