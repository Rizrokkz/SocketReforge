package irai.mod.reforge.Common.UI;

import java.util.Locale;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import irai.mod.reforge.Lore.LoreSocketData;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.Socket;
import irai.mod.reforge.Socket.SocketManager;

/**
 * Shared icon and color resolution for socket-related UIs.
 */
public final class UISocketVisualUtils {

    private UISocketVisualUtils() {}

    public static String loreColorHex(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return "#2b2b3a";
        }
        if (socket.isLocked()) {
            return "#3a3a3a";
        }
        return loreColorHex(socket.getColor());
    }

    public static String loreColorHex(String color) {
        if (color == null || color.isBlank()) {
            return "#2b2b3a";
        }
        String trimmed = color.trim();
        if (trimmed.startsWith("#")) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("red") || lower.contains("ruby")) return "#FF5555";
        if (lower.contains("blue") || lower.contains("sapphire")) return "#5599FF";
        if (lower.contains("green") || lower.contains("emerald")) return "#55FF77";
        if (lower.contains("purple") || lower.contains("amethyst")) return "#AA55FF";
        if (lower.contains("yellow") || lower.contains("topaz")) return "#FFFF55";
        if (lower.contains("orange")) return "#FFAA00";
        if (lower.contains("black") || lower.contains("onyx")) return "#555555";
        if (lower.contains("white") || lower.contains("diamond")) return "#FFFFFF";
        if (lower.contains("cyan") || lower.contains("opal")) return "#55FFFF";
        return "#2b2b3a";
    }

    public static String loreColorKey(LoreSocketData.LoreSocket socket) {
        if (socket == null) {
            return "none";
        }
        if (socket.isLocked()) {
            return "locked";
        }
        String color = socket.getColor();
        if (color == null || color.isBlank()) {
            return "none";
        }
        String lower = color.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("red") || lower.contains("ruby")) return "red";
        if (lower.contains("blue") || lower.contains("sapphire")) return "blue";
        if (lower.contains("green") || lower.contains("emerald")) return "green";
        if (lower.contains("purple") || lower.contains("amethyst")) return "purple";
        if (lower.contains("yellow") || lower.contains("topaz")) return "yellow";
        if (lower.contains("orange")) return "orange";
        if (lower.contains("black") || lower.contains("onyx")) return "black";
        if (lower.contains("white") || lower.contains("diamond")) return "white";
        if (lower.contains("cyan") || lower.contains("opal")) return "cyan";
        return "none";
    }

    public static String loreGemOverlayIcon(LoreSocketData.LoreSocket socket) {
        if (socket == null || socket.isEmpty()) {
            return null;
        }
        String byItem = loreGemIconByItemId(socket.getGemItemId());
        if (byItem != null) {
            return byItem;
        }
        String byColor = loreGemIconByColor(socket.getColor());
        if (byColor != null) {
            return byColor;
        }
        return UITemplateUtils.resolveCustomUiAsset(
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png",
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png");
    }

    public static String loreGemIconByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (lower.contains("ruby")) return loreGemIconByColor("red");
        if (lower.contains("sapphire")) return loreGemIconByColor("blue");
        if (lower.contains("emerald")) return loreGemIconByColor("green");
        if (lower.contains("diamond")) return loreGemIconByColor("white");
        if (lower.contains("topaz")) return loreGemIconByColor("yellow");
        if (lower.contains("voidstone") || lower.contains("onyx")) return loreGemIconByColor("black");
        if (lower.contains("zephyr") || lower.contains("opal")) return loreGemIconByColor("cyan");
        return null;
    }

    public static String loreGemIconByColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String lower = color.toLowerCase(Locale.ROOT);
        String icon;
        if (lower.contains("red") || lower.contains("ruby")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Ruby.png";
        } else if (lower.contains("blue") || lower.contains("sapphire")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Sapphire.png";
        } else if (lower.contains("green") || lower.contains("emerald")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Emerald.png";
        } else if (lower.contains("white") || lower.contains("diamond")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Diamond.png";
        } else if (lower.contains("yellow") || lower.contains("topaz")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Topaz.png";
        } else if (lower.contains("black") || lower.contains("voidstone") || lower.contains("onyx")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Voidstone.png";
        } else if (lower.contains("cyan") || lower.contains("opal") || lower.contains("zephyr")) {
            icon = "Icons/ItemsGenerated/Rock_Gem_Zephyr.png";
        } else {
            return null;
        }
        return UITemplateUtils.resolveCustomUiAsset(
                "Icons/ItemsGenerated/Plant_Fruit_Spiral_Tree.png",
                icon);
    }

    public static String filledSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_fille.png", "socket_filled.png");
    }

    public static String brokenSocketIconName() {
        return UITemplateUtils.resolveCustomUiAsset("socket_empty.png", "socket_broken.png", "socket_Broken.png");
    }

    public static String socketBackgroundIcon(boolean punched, boolean broken, String brokenIconName) {
        if (broken) {
            return brokenIconName;
        }
        return punched ? "socket_empty.png" : "slot_bg.png";
    }

    public static String socketColorHex(Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return "#FFFFFF";
        }
        if (socket.isBroken()) {
            return "#B22222";
        }
        Essence.Type type = essenceType(socket.getEssenceId());
        if (type != null) {
            return switch (type) {
                case FIRE -> "#FFAA00";
                case ICE -> "#55FFFF";
                case LIFE -> "#55FF55";
                case LIGHTNING -> "#FFFF55";
                case VOID -> "#AA55FF";
                case WATER -> "#5555FF";
            };
        }
        return "#FFFFFF";
    }

    public static String socketPreviewAccent(Socket socket, boolean punched, boolean broken, boolean filled) {
        if (broken) {
            return "#8A2020";
        }
        if (filled) {
            return socketColorHex(socket);
        }
        if (punched) {
            return "#5A451E";
        }
        return "#2b2b3a";
    }

    public static String socketPreviewColorKey(Socket socket, boolean punched, boolean broken, boolean filled) {
        if (broken) {
            return "broken";
        }
        if (!punched) {
            return "none";
        }
        if (!filled || socket == null) {
            return "open";
        }
        Essence.Type type = essenceType(socket.getEssenceId());
        if (type != null) {
            return switch (type) {
                case FIRE -> "fire";
                case ICE -> "ice";
                case LIFE -> "life";
                case LIGHTNING -> "lightning";
                case VOID -> "void";
                case WATER -> "water";
            };
        }
        return "open";
    }

    public static String essenceIconName(Socket socket) {
        if (socket == null || socket.isEmpty()) {
            return filledSocketIconName();
        }
        String essenceId = socket.getEssenceId();
        String itemIcon = iconFromEssenceId(essenceId);
        if (itemIcon != null && !itemIcon.isBlank()) {
            return itemIcon;
        }
        Essence.Type type = essenceType(essenceId);
        if (type == null) {
            return filledSocketIconName();
        }
        String base = "essence_" + type.name().toLowerCase(Locale.ROOT);
        boolean greater = essenceId != null && SocketManager.isGreaterEssenceId(essenceId);
        if (greater) {
            return UITemplateUtils.resolveCustomUiAsset(
                    filledSocketIconName(),
                    base + "_concentrated.png",
                    base + "_greater.png",
                    base + "_concentrated_icon.png",
                    base + "_greater_icon.png",
                    base + ".png");
        }
        return UITemplateUtils.resolveCustomUiAsset(
                filledSocketIconName(),
                base + ".png",
                base + "_icon.png");
    }

    public static String iconFromEssenceId(String essenceId) {
        String itemId = essenceItemId(essenceId);
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Item item = Item.getAssetMap().getAssetMap().get(itemId);
            if (item == null || item == Item.UNKNOWN) {
                return null;
            }
            String icon = item.getIcon();
            if (icon == null || icon.isBlank() || Item.UNKNOWN_TEXTURE.equals(icon)) {
                return null;
            }
            String uiIcon = uiIconPath(icon);
            return uiIcon != null ? uiIcon : icon;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String essenceItemId(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return null;
        }
        String lower = essenceId.toLowerCase(Locale.ROOT);
        if (lower.contains("ingredient_") && lower.contains("essence")) {
            return essenceId;
        }
        String cleaned = essenceId.trim();
        if (lower.startsWith("essence_")) {
            cleaned = cleaned.substring("Essence_".length());
        }
        boolean greater = SocketManager.isGreaterEssenceId(essenceId) || lower.endsWith("_concentrated");
        if (greater && cleaned.toLowerCase(Locale.ROOT).endsWith("_concentrated")) {
            cleaned = cleaned.substring(0, cleaned.length() - "_Concentrated".length());
        }
        if (cleaned.isBlank()) {
            return null;
        }
        return "Ingredient_" + cleaned + "_Essence" + (greater ? "_Concentrated" : "");
    }

    public static String uiIconPath(String iconPath) {
        if (iconPath == null || iconPath.isBlank()) {
            return null;
        }
        String normalized = iconPath.replace('\\', '/');
        if (normalized.startsWith("Common/Icons/")) {
            return normalized.substring("Common/".length());
        }
        if (normalized.startsWith("Icons/")) {
            return normalized;
        }
        return "Icons/" + normalized;
    }

    private static Essence.Type essenceType(String essenceId) {
        if (essenceId == null || essenceId.isBlank()) {
            return null;
        }
        try {
            Essence essence = EssenceRegistry.get().getById(essenceId);
            if (essence != null) {
                return essence.getType();
            }
        } catch (Exception ignored) {
        }
        String lower = essenceId.toLowerCase(Locale.ROOT);
        if (lower.contains("fire")) return Essence.Type.FIRE;
        if (lower.contains("ice")) return Essence.Type.ICE;
        if (lower.contains("life")) return Essence.Type.LIFE;
        if (lower.contains("lightning")) return Essence.Type.LIGHTNING;
        if (lower.contains("void")) return Essence.Type.VOID;
        if (lower.contains("water")) return Essence.Type.WATER;
        return null;
    }
}
