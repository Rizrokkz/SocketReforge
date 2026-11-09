package irai.mod.reforge.Common.UI;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves optional image assets used by native Custom UI documents. */
public final class UICustomAssetUtils {
    private static final Map<String, Boolean> ASSET_EXISTS = new ConcurrentHashMap<>();

    private UICustomAssetUtils() {}

    public static String resolve(String fallbackAsset, String... candidates) {
        if (candidates != null) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank() && exists(candidate)) return candidate;
            }
        }
        return fallbackAsset;
    }

    private static boolean exists(String fileName) {
        return ASSET_EXISTS.computeIfAbsent(fileName, UICustomAssetUtils::existsUncached);
    }

    private static boolean existsUncached(String fileName) {
        try {
            Path path = Paths.get("src", "main", "resources", "Common", "UI", "Custom", fileName);
            if (Files.isRegularFile(path)) return true;
        } catch (Exception ignored) {
        }
        try (InputStream in = UICustomAssetUtils.class.getClassLoader()
                .getResourceAsStream("Common/UI/Custom/" + fileName)) {
            return in != null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
