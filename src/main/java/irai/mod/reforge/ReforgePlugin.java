package irai.mod.reforge;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;

import irai.mod.reforge.Commands.CheckNameCommand;
import irai.mod.reforge.Commands.PatchAssetsCommand;
import irai.mod.reforge.Commands.WeaponStatsCommand;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Entity.Events.OpenGuiListener;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Systems.SyncTasks;

public class ReforgePlugin extends JavaPlugin {
    private final EquipmentRefineEST refineEST;
    private ReforgeEquip reforgeEquip;

    // Static reference for commands to access plugin
    private static ReforgePlugin instance;

    // Scheduled tasks
    private Timer autoSaveTimer;
    private Timer weaponSyncTimer;

    private final Config<SFXConfig> sfxconfig;
    private final Config<RefinementConfig> refinementConfig;

    public ReforgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        refineEST = new EquipmentRefineEST();
        this.sfxconfig = this.withConfig("SFXConfig", SFXConfig.CODEC);
        this.refinementConfig = this.withConfig("RefinementConfig", RefinementConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Initialize weapon upgrade tracker with persistence
        File dataFolder = new File(".");
        ReforgeEquip.initialize(dataFolder);

        // Load SFX config first before creating ReforgeEquip
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

        // Register interaction
        reforgeEquip = new ReforgeEquip();
        
        // Inject the loaded SFXConfig into ReforgeEquip
        SFXConfig loadedSfx = this.sfxconfig.get();
        if (loadedSfx != null) {
            reforgeEquip.setSfxConfig(loadedSfx);
            System.out.println("[ReforgePlugin] SFXConfig injected into ReforgeEquip");
        }
        
        this.getCodecRegistry(Interaction.CODEC).register("ReforgeEquip", ReforgeEquip.class, ReforgeEquip.CODEC);

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, OpenGuiListener::openGui);

        // Load refinement config and inject into systems
        try {
            this.refinementConfig.save();
            RefinementConfig refinement = this.refinementConfig.get();
            if (refinement != null) {
                System.out.println("[ReforgePlugin] Loaded RefinementConfig:");
                System.out.println("[ReforgePlugin]   Damage Multipliers: " + java.util.Arrays.toString(refinement.getDamageMultipliers()));
                System.out.println("[ReforgePlugin]   Break Chances: " + java.util.Arrays.toString(refinement.getBreakChances()));
                
                // Inject config into EquipmentRefineEST
                refineEST.setRefinementConfig(refinement);
                System.out.println("[ReforgePlugin] RefinementConfig injected into EquipmentRefineEST");
                
                // Inject config into ReforgeEquip
                reforgeEquip.setRefinementConfig(refinement);
                System.out.println("[ReforgePlugin] RefinementConfig injected into ReforgeEquip");
            }
        } catch (Exception e) {
            System.err.println("[ReforgePlugin] Error loading Refinement config: " + e.getMessage());
            e.printStackTrace();
        }

        // Register ECS damage system
        this.getEntityStoreRegistry().registerSystem(refineEST);

        // Register commands
        CommandRegistry commandRegistry = this.getCommandRegistry();
        this.getCommandRegistry().registerCommand(new WeaponStatsCommand("weaponstats", "Display weapon stats and next upgrade values"));
        commandRegistry.registerCommand(new CheckNameCommand("checkname", "Checks the translation name of the held item", false));
        commandRegistry.registerCommand(new PatchAssetsCommand("patchassets", "Patch weapons from HytaleAssets (auto-detect mods folder)", false));
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

        // Save all weapon data
        System.out.println("[ReforgePlugin] Saving weapon data...");
        ReforgeEquip.saveAll();

        int weaponCount = ReforgeEquip.getTrackedWeaponCount();
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
                    int weaponCount = ReforgeEquip.getTrackedWeaponCount();

                    if (weaponCount > 0) {
                        System.out.println("[ReforgePlugin] Auto-saving " + weaponCount + " weapon upgrades...");
                        ReforgeEquip.saveAll();
                        System.out.println("[ReforgePlugin] ✓ Auto-save complete");
                    }
                } catch (Exception e) {
                    System.err.println("[ReforgePlugin] Auto-save failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // Schedule: 5 minutes delay, then every 5 minutes
        long fiveMinutes = 5 * 60 * 1000;
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
                    Universe universe = Universe.get();
                    Collection<Player> players = List.of();

                    SyncTasks task = new SyncTasks(players);
                    task.run();
                } catch (Exception e) {
                    System.err.println("[ReforgePlugin] Weapon sync failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };

        // Schedule: 30 seconds delay, then every 30 seconds
        long thirtySeconds = 30 * 1000;
        weaponSyncTimer.scheduleAtFixedRate(syncTask, thirtySeconds, thirtySeconds);
    }
}
