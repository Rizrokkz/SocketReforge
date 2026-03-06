package irai.mod.reforge.Common.UI;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared HTML template and escaping helpers for custom UIs.
 */
public final class UITemplateUtils {

    private UITemplateUtils() {}

    public static String loadTemplate(Class<?> ownerClass, String templatePath, String missingHtml, String uiName) {
        String fileSystemPath = "src/main/resources/" + templatePath;
        try {
            Path path = Paths.get(fileSystemPath);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] " + uiName + " failed to read filesystem template: " + e.getMessage());
        }

        try (InputStream in = ownerClass.getClassLoader().getResourceAsStream(templatePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] " + uiName + " failed to read classpath template: " + e.getMessage());
        }

        return missingHtml;
    }

    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String resolveCustomUiAsset(String fallbackAsset, String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (customUiAssetExists(candidate)) {
                    return candidate;
                }
            }
        }
        return fallbackAsset;
    }

    private static boolean customUiAssetExists(String fileName) {
        try {
            Path fs = Paths.get("src", "main", "resources", "Common", "UI", "Custom", fileName);
            if (Files.exists(fs) && Files.isRegularFile(fs)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        try (InputStream in = UITemplateUtils.class.getClassLoader().getResourceAsStream("Common/UI/Custom/" + fileName)) {
            return in != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
