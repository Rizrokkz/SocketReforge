package irai.mod.reforge;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;

import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Commands.ReforgeAdminCommand;
import irai.mod.reforge.Commands.SocketPunchCommand;
import irai.mod.reforge.Commands.EssenceCommand;
import irai.mod.reforge.Commands.WeaponPartsCommand;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Entity.Events.LifeHealthSystem;
import irai.mod.reforge.Entity.Events.OpenGuiListener;
import irai.mod.reforge.Entity.Events.SocketEffectEST;
import irai.mod.reforge.Entity.Events.SocketStatSystem;
import irai.mod.reforge.Entity.Events.WaterRegenSystem;
import irai.mod.reforge.Interactions.EssenceSocketBench;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Interactions.SocketPunchBench;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Systems.SyncTasks;
import irai.mod.reforge.UI.EssenceBenchUI;
import irai.mod.reforge.UI.ReforgeBenchUI;
import irai.mod.reforge.UI.SocketBenchUI;
import irai.mod.reforge.UI.WeaponPartsUI;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;

public class ReforgePlugin extends JavaPlugin {
    private final EquipmentRefineEST refineEST;
    private final SocketEffectEST socketEffectEST;
    private final SocketStatSystem socketStatSystem;
    private final LifeHealthSystem lifeHealthSystem;
    private final WaterRegenSystem waterRegenSystem;
    private ReforgeEquip reforgeEquip;

    // Static reference for commands to access plugin
    private static ReforgePlugin instance;

    // Scheduled tasks
    private Timer autoSaveTimer;
    private Timer weaponSyncTimer;

    private final Config<SFXConfig> sfxconfig;
    private final Config<RefinementConfig> refinementConfig;
    private final Config<SocketConfig> socketConfig;

    public ReforgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        refineEST = new EquipmentRefineEST();
        socketEffectEST = new SocketEffectEST();
        socketStatSystem = new SocketStatSystem();
        lifeHealthSystem = new LifeHealthSystem();
        waterRegenSystem = new WaterRegenSystem();
        this.sfxconfig = this.withConfig("SFXConfig", SFXConfig.CODEC);
        this.refinementConfig = this.withConfig("RefinementConfig", RefinementConfig.CODEC);
        this.socketConfig = this.withConfig("SocketConfig", SocketConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Initialize language file loader for name resolution
        LangLoader.initialize();
        // Tooltip refresh is handled automatically by the provider approach
        DynamicTooltipUtils.init();
        // Initialize EquipmentListUI for HyUI integration
        SocketBenchUI.initialize();
        EssenceBenchUI.initialize();
        ReforgeBenchUI.initialize();
        WeaponPartsUI.initialize();
        // Initialize weapon upgrade tracker with persistence
        File dataFolder = new File(".");
        ReforgeEquip.initialize(dataFolder);
        
        // Initialize Socket Stat System (handles both health bonus and regeneration)

        // Load SFX config first before creating ReforgeEquip
        try {
            this.sfxconfig.save();
            SFXConfig cfg = this.sfxconfig.get();
            if (cfg != null) {
                Logger.getLogger("System: ").info("SFX Loaded!");
            }
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
            ReforgeBenchUI.setSfxConfig(loadedSfx);
        }
        
        this.getCodecRegistry(Interaction.CODEC).register("ReforgeEquip", ReforgeEquip.class, ReforgeEquip.CODEC);
        
        // Register Bench interactions
        this.getCodecRegistry(Interaction.CODEC).register("SocketPunchBench", SocketPunchBench.class, SocketPunchBench.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("EssenceSocketBench", EssenceSocketBench.class, EssenceSocketBench.CODEC);
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, OpenGuiListener::openGui);
        this.getCommandRegistry().registerCommand(new WeaponPartsCommand("partsui", "Open modular weapon parts bench UI", false));
        this.getCommandRegistry().registerCommand(new SocketPunchCommand("socketpunch", "Open socket punch bench UI", false));
        this.getCommandRegistry().registerCommand(new EssenceCommand("essence", "Open essence socket bench UI", false));
        this.getCommandRegistry().registerCommand(new ReforgeAdminCommand("reforgeadmin", "OP tools for held-item refinement/socket metadata", false));

        // Load refinement config and inject into systems
        try {
            this.refinementConfig.save();
            RefinementConfig refinement = this.refinementConfig.get();
            if (refinement != null) {
                // Inject config into EquipmentRefineEST
                refineEST.setRefinementConfig(refinement);

                // Inject config into ReforgeEquip
                reforgeEquip.setRefinementConfig(refinement);
                ReforgeBenchUI.setRefinementConfig(refinement);
            }
        } catch (Exception e) {
            System.err.println("[ReforgePlugin] Error loading Refinement config: " + e.getMessage());
            e.printStackTrace();
        }

        // Load socket config and initialize socket system
        try {
            this.socketConfig.save();
            SocketConfig socketCfg = this.socketConfig.get();
            if (socketCfg != null) {
                // Initialize socket system
                SocketManager.initialize(socketCfg);
                EssenceRegistry.initialize();

            }
        } catch (Exception e) {
            System.err.println("[ReforgePlugin] Error loading Socket config: " + e.getMessage());
            e.printStackTrace();
        }

        // Register ECS damage systems
        this.getEntityStoreRegistry().registerSystem(refineEST);
        this.getEntityStoreRegistry().registerSystem(socketEffectEST);
        this.getEntityStoreRegistry().registerSystem(socketStatSystem);
        this.getEntityStoreRegistry().registerSystem(lifeHealthSystem);
        this.getEntityStoreRegistry().registerSystem(waterRegenSystem);


    }

    @Override
    protected void start() {
        // Refresh all players to ensure tooltip changes are applied
        startTooltipRefreshTimer();
    }

    protected void stop() {
        // Stop scheduled tasks
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }

        if (weaponSyncTimer != null) {
            weaponSyncTimer.cancel();
        }
    }

    /**
     * Gets the SFX config instance
     */
    public Config<SFXConfig> getSfxconfig() {
        return sfxconfig;
    }

    
    private Timer tooltipRefreshTimer;
    private void startTooltipRefreshTimer() {
        tooltipRefreshTimer = new Timer("RefreshTooltips", true);
        tooltipRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    DynamicTooltipUtils.refreshAllPlayers();
                } catch (Exception e) {
                    System.err.println("Tooltips Refresh failed: " + e.getMessage());
                }
            }
        }, 0, 3000);
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
                        ReforgeEquip.saveAll();
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
