package irai.mod.reforge;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import irai.mod.reforge.Commands.ReforgeCommand;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Systems.SyncTasks;
import irai.mod.reforge.Systems.WeaponUpgradeTracker;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class ReforgePlugin extends JavaPlugin {
    private final EquipmentRefineEST refineEST;
    private ReforgeEquip reforgeEquip;

    // Scheduled tasks
    private Timer autoSaveTimer;
    private Timer weaponSyncTimer;

    private final Config<SFXConfig> sfxconfig;

    public ReforgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        refineEST = new EquipmentRefineEST();
        this.sfxconfig = this.withConfig("SFXConfig", SFXConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Initialize weapon upgrade tracker with persistence
        File dataFolder = getDataDirectory().toFile();
        WeaponUpgradeTracker.initialize(dataFolder);

        // Register interaction
        reforgeEquip = new ReforgeEquip();
        this.getCodecRegistry(Interaction.CODEC).register("ReforgeEquip", ReforgeEquip.class, ReforgeEquip.CODEC);

        try {
            this.sfxconfig.save();
            SFXConfig cfg = this.sfxconfig.get();
            if (cfg != null) {
                System.out.println("[ReforgePlugin] Loaded SFX: start=" + cfg.getSFX_START() +
                        ", success=" + cfg.getSFX_SUCCESS() +
                        ", benches=" + String.join(", ", cfg.getBenches()));
            }
            Logger.getLogger("System: ").info("SFX Loaded!");
        } catch (Exception e) {
            System.err.println("[ReforgePlugin] Error loading SFX config: " + e.getMessage());
            e.printStackTrace();
        }

        // Register ECS damage system
        this.getEntityStoreRegistry().registerSystem(refineEST);

        // Register commands
        CommandRegistry commandRegistry = this.getCommandRegistry();
        //commandRegistry.registerCommand(new ReforgeCommand());
    }

    @Override
    protected void start() {
        // Start auto-save task (saves every 5 minutes)
        startAutoSaveTask();
        // Start weapon sync task (syncs every 30 seconds)
        startWeaponSyncTask();
    }

    protected void stop() {
        System.out.println("═══════════════════════════════════════════════");
        System.out.println("[ReforgePlugin] Shutting down...");

        // Stop scheduled tasks
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
            System.out.println("[ReforgePlugin] ✓ Auto-save task stopped");
        }

        if (weaponSyncTimer != null) {
            weaponSyncTimer.cancel();
            System.out.println("[ReforgePlugin] ✓ Weapon sync task stopped");
        }

        // Create backup before final save
        System.out.println("[ReforgePlugin] Creating backup...");
        WeaponUpgradeTracker.createBackup();

        // Save all weapon data
        System.out.println("[ReforgePlugin] Saving weapon data...");
        WeaponUpgradeTracker.saveAll();

        int weaponCount = WeaponUpgradeTracker.getTrackedWeaponCount();
        System.out.println("[ReforgePlugin] ✓ Saved " + weaponCount + " weapon upgrades");

        System.out.println("[ReforgePlugin] Shutdown complete!");
        System.out.println("═══════════════════════════════════════════════");
    }

    /**
     * Gets the SFX config instance
     */
    public Config<SFXConfig> getSfxconfig() {
        return sfxconfig;
    }

    /**
     * Starts the auto-save task that periodically saves weapon data.
     * Runs every 5 minutes.
     */
    private void startAutoSaveTask() {
        autoSaveTimer = new Timer("ReforgeAutoSave", true);

        TimerTask autoSaveTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    int weaponCount = WeaponUpgradeTracker.getTrackedWeaponCount();

                    if (weaponCount > 0) {
                        System.out.println("[ReforgePlugin] Auto-saving " + weaponCount + " weapon upgrades...");
                        WeaponUpgradeTracker.saveAll();
                        System.out.println("[ReforgePlugin] ✓ Auto-save complete");
                    }
                } catch (Exception e) {
                    System.err.println("[ReforgePlugin] Auto-save failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // Schedule: 5 minutes delay, then every 5 minutes
        long fiveMinutes = 5 * 60 * 1000; // 5 minutes in milliseconds
        autoSaveTimer.scheduleAtFixedRate(autoSaveTask, fiveMinutes, fiveMinutes);
    }

    /**
     * Starts the weapon sync task that keeps weapon display names updated.
     * Runs every 30 seconds.
     */
    private void startWeaponSyncTask() {
        weaponSyncTimer = new Timer("ReforgeWeaponSync", true);

        TimerTask syncTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    // Get current list of players in the universe
                    Universe universe = Universe.get();
                    Collection<Player> players = List.of();

                    // Run sync task for all online players
                    SyncTasks task = new SyncTasks(players);
                    task.run();
                } catch (Exception e) {
                    System.err.println("[ReforgePlugin] Weapon sync failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // Schedule: 30 seconds delay, then every 30 seconds
        long thirtySeconds = 30 * 1000; // 30 seconds in milliseconds
        weaponSyncTimer.scheduleAtFixedRate(syncTask, thirtySeconds, thirtySeconds);
    }
}