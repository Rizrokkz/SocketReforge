package irai.mod.reforge.Systems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles saving and loading weapon upgrade data to/from JSON files.
 * Allows persistence across server restarts.
 */
public class WeaponPersistence {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String SAVE_FILE = "weapon_upgrades.json";

    /**
     * Saves all weapon upgrade data to a JSON file.
     *
     * @param data Map of weapon keys to WeaponSaveData
     * @param saveDirectory Directory to save the file in
     */
    public static void saveToFile(Map<String, WeaponSaveData> data, File saveDirectory) {
        File saveFile = new File(saveDirectory, SAVE_FILE);

        try {
            // Ensure directory exists
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs();
            }

            // Write JSON to file
            try (Writer writer = new FileWriter(saveFile)) {
                GSON.toJson(data, writer);
            }

            System.out.println("[WeaponPersistence] Saved " + data.size() + " weapon upgrades to " + saveFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("[WeaponPersistence] Failed to save weapon data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads weapon upgrade data from a JSON file.
     *
     * @param saveDirectory Directory where the save file is located
     * @return Map of weapon keys to WeaponSaveData, or empty map if load fails
     */
    public static Map<String, WeaponSaveData> loadFromFile(File saveDirectory) {
        File saveFile = new File(saveDirectory, SAVE_FILE);

        if (!saveFile.exists()) {
            System.out.println("[WeaponPersistence] No save file found, starting fresh");
            return new HashMap<>();
        }

        try {
            // Read JSON from file
            try (Reader reader = new FileReader(saveFile)) {
                Type type = new TypeToken<Map<String, WeaponSaveData>>(){}.getType();
                Map<String, WeaponSaveData> data = GSON.fromJson(reader, type);

                if (data == null) {
                    data = new HashMap<>();
                }

                System.out.println("[WeaponPersistence] Loaded " + data.size() + " weapon upgrades from " + saveFile.getAbsolutePath());
                return data;
            }

        } catch (IOException e) {
            System.err.println("[WeaponPersistence] Failed to load weapon data: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Creates a backup of the current save file.
     */
    public static void createBackup(File saveDirectory) {
        File saveFile = new File(saveDirectory, SAVE_FILE);
        if (!saveFile.exists()) {
            return;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupFile = new File(saveDirectory, "weapon_upgrades_backup_" + timestamp + ".json");

        try {
            copyFile(saveFile, backupFile);
            System.out.println("[WeaponPersistence] Created backup: " + backupFile.getName());
        } catch (IOException e) {
            System.err.println("[WeaponPersistence] Failed to create backup: " + e.getMessage());
        }
    }

    /**
     * Copies a file from source to destination.
     */
    private static void copyFile(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    /**
     * Data class for saving weapon information.
     */
    public static class WeaponSaveData {
        public final int level;
        public final String itemId;
        public final UUID instanceUUID;  // ADD THIS
        public final long lastModified;
        public final String playerName;

        public WeaponSaveData(int level, String itemId, UUID instanceUUID,
                              long lastModified, String playerName) {
            this.level = level;
            this.itemId = itemId;
            this.instanceUUID = instanceUUID;  // ADD THIS
            this.lastModified = lastModified;
            this.playerName = playerName;
        }
    }
}