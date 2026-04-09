package irai.mod.reforge;

import java.io.File;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import com.al3x.HStats;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.builtin.adventure.objectives.events.TreasureChestOpeningEvent;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionConfiguration;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.core.util.Config;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumberConfig;
import irai.mod.DynamicFloatingDamageFormatter.DamageNumbers;
import irai.mod.reforge.Commands.EssenceCommand;
import irai.mod.reforge.Commands.ItemMetaCommand;
import irai.mod.reforge.Commands.LoreAbilityMapCommand;
import irai.mod.reforge.Commands.LoreApplyCommand;
import irai.mod.reforge.Commands.LoreFeedCommand;
import irai.mod.reforge.Commands.LoreSocketCommand;
import irai.mod.reforge.Commands.LoreSpiritMapCommand;
import irai.mod.reforge.Commands.ReforgeAdminCommand;
import irai.mod.reforge.Commands.ResonanceListCommand;
import irai.mod.reforge.Commands.ResonanceRecipeGiveCommand;
import irai.mod.reforge.Commands.ResonanceWorldScanCommand;
import irai.mod.reforge.Commands.RuntimeConfigCommand;
import irai.mod.reforge.Commands.SocketPunchCommand;
import irai.mod.reforge.Commands.SpawnEquipChestCommand;
import irai.mod.reforge.Commands.SpawnEquipEnemyCommand;
import irai.mod.reforge.Commands.ToolPartsCommand;
import irai.mod.reforge.Common.CropEssenceDropUtils;
import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.LeafSaplingDropUtils;
import irai.mod.reforge.Common.WorldDroplistRepairUtils;
import irai.mod.reforge.Common.WorldDroplistRepairUtils.WorldDroplistRepairResult;
import irai.mod.reforge.Config.ConfigService;
import irai.mod.reforge.Config.LootSocketRollConfig;
import irai.mod.reforge.Config.LoreConfig;
import irai.mod.reforge.Config.LoreMappingConfig;
import irai.mod.reforge.Config.RefinementConfig;
import irai.mod.reforge.Config.SFXConfig;
import irai.mod.reforge.Config.SocketConfig;
import irai.mod.reforge.Config.WorldRepairConfig;
import irai.mod.reforge.Entity.Events.ChestWindowSocketLootEST;
import irai.mod.reforge.Entity.Events.DamageNumberEST;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Entity.Events.HatchetThrowEST;
import irai.mod.reforge.Entity.Events.LifeHealthSystem;
import irai.mod.reforge.Entity.Events.LootSocketRoller;
import irai.mod.reforge.Entity.Events.LoreEffectEST;
import irai.mod.reforge.Entity.Events.LoreKillEST;
import irai.mod.reforge.Entity.Events.LorePlayerStateEST;
import irai.mod.reforge.Entity.Events.NPCLootSocketDropEST;
import irai.mod.reforge.Entity.Events.OpenGuiListener;
import irai.mod.reforge.Entity.Events.SalvageMetadataCompatEST;
import irai.mod.reforge.Entity.Events.SocketEffectEST;
import irai.mod.reforge.Entity.Events.SocketStatSystem;
import irai.mod.reforge.Entity.Events.TreasureChestSocketLootListener;
import irai.mod.reforge.Entity.Events.WaterRegenSystem;
import irai.mod.reforge.Interactions.EssenceSocketBench;
import irai.mod.reforge.Interactions.HatchetThrowUse;
import irai.mod.reforge.Interactions.LoreSocketBench;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Interactions.ResonantCompendiumUse;
import irai.mod.reforge.Interactions.ResonantRecipeCombineUse;
import irai.mod.reforge.Interactions.SocketPunchBench;
import irai.mod.reforge.Lore.LoreAbilityRegistry;
import irai.mod.reforge.Lore.LoreAbsorptionStore;
import irai.mod.reforge.Lore.LoreGemRegistry;
import irai.mod.reforge.Lore.LoreSocketManager;
import irai.mod.reforge.Socket.EssenceRegistry;
import irai.mod.reforge.Socket.ResonanceSystem;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Systems.SyncTasks;
import irai.mod.reforge.UI.EssenceBenchUI;
import irai.mod.reforge.UI.LoreFeedBenchUI;
import irai.mod.reforge.UI.LoreSocketBenchUI;
import irai.mod.reforge.UI.RecipeCombineUI;
import irai.mod.reforge.UI.ReforgeBenchUI;
import irai.mod.reforge.UI.ResonantCompendiumUI;
import irai.mod.reforge.UI.RuntimeConfigUI;
import irai.mod.reforge.UI.SocketBenchUI;
import irai.mod.reforge.UI.ToolPartsUI;
import irai.mod.reforge.Util.DynamicTooltipUtils;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.NameResolver;

public class ReforgePlugin extends JavaPlugin {
    private final EquipmentRefineEST refineEST;
    private final SocketEffectEST socketEffectEST;
    private final LoreEffectEST loreEffectEST;
    private final DamageNumberEST damageNumberEST;
    private final LoreKillEST loreKillEST;
    private final LorePlayerStateEST lorePlayerStateEST;
    private final SocketStatSystem socketStatSystem;
    private final LifeHealthSystem lifeHealthSystem;
    private final WaterRegenSystem waterRegenSystem;
    private final HatchetThrowEST hatchetThrowEST;
    private final SalvageMetadataCompatEST salvageMetadataCompatEST;
    private final ChestWindowSocketLootEST chestWindowSocketLootEST;
    private final NPCLootSocketDropEST npcLootSocketDropEST;
    private ReforgeEquip reforgeEquip;

    // Static reference for commands to access plugin
    private static ReforgePlugin instance;

    // Scheduled tasks
    private Timer autoSaveTimer;
    private Timer weaponSyncTimer;
    private Timer worldRepairTimer;
    private Timer chunkRemovalTimer;
    private final AtomicBoolean worldRepairScheduled = new AtomicBoolean(false);
    private final AtomicBoolean chunkRemovalScheduled = new AtomicBoolean(false);

    private final Config<SFXConfig> sfxconfig;
    private final Config<RefinementConfig> refinementConfig;
    private final Config<SocketConfig> socketConfig;
    private final Config<LootSocketRollConfig> lootSocketRollConfig;
    private final Config<LoreConfig> loreConfig;
    private final Config<LoreMappingConfig> loreMappingConfig;
    private final Config<DamageNumberConfig> damageNumberConfig;
    private final Config<WorldRepairConfig> worldRepairConfig;
    private final ConfigService configService;
    private boolean customCombatTextEnabled = true;
    private boolean systemsRegistered = false;
    private boolean damageNumberSystemRegistered = false;

    public ReforgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        refineEST = new EquipmentRefineEST();
        socketEffectEST = new SocketEffectEST();
        loreEffectEST = new LoreEffectEST();
        damageNumberEST = new DamageNumberEST();
        loreKillEST = new LoreKillEST();
        lorePlayerStateEST = new LorePlayerStateEST();
        socketStatSystem = new SocketStatSystem();
        lifeHealthSystem = new LifeHealthSystem();
        waterRegenSystem = new WaterRegenSystem();
        hatchetThrowEST = new HatchetThrowEST();
        salvageMetadataCompatEST = new SalvageMetadataCompatEST();
        chestWindowSocketLootEST = new ChestWindowSocketLootEST();
        npcLootSocketDropEST = new NPCLootSocketDropEST();
        this.configService = new ConfigService("ReforgePlugin");
        this.sfxconfig = this.withConfig("SFXConfig", SFXConfig.CODEC);
        this.refinementConfig = this.withConfig("RefinementConfig", RefinementConfig.CODEC);
        this.socketConfig = this.withConfig("SocketConfig", SocketConfig.CODEC);
        this.lootSocketRollConfig = this.withConfig("LootSocketRollConfig", LootSocketRollConfig.CODEC);
        this.loreConfig = this.withConfig("LoreConfig", LoreConfig.CODEC);
        this.loreMappingConfig = this.withConfig("LoreMappingConfig", LoreMappingConfig.CODEC);
        this.damageNumberConfig = this.withConfig("DamageNumberConfig", DamageNumberConfig.CODEC);
        this.worldRepairConfig = this.withConfig("WorldRepairConfig", WorldRepairConfig.CODEC);

        this.configService.register("SFXConfig", this.sfxconfig, cfg -> {
            if (reforgeEquip != null) {
                reforgeEquip.setSfxConfig(cfg);
            }
            ReforgeBenchUI.setSfxConfig(cfg);
            socketEffectEST.setSfxConfig(cfg);
        });

        this.configService.register("RefinementConfig", this.refinementConfig, cfg -> {
            refineEST.setRefinementConfig(cfg);
            if (reforgeEquip != null) {
                reforgeEquip.setRefinementConfig(cfg);
            }
            ReforgeBenchUI.setRefinementConfig(cfg);
            DynamicTooltipUtils.setRefinementConfig(cfg);
            NameResolver.setRefinementConfig(cfg);
            EquipmentDamageTooltipMath.setRefinementConfig(cfg);
        });

        this.configService.register("SocketConfig", this.socketConfig, cfg -> {
            SocketManager.initialize(cfg);
            EssenceRegistry.initialize();
        });

        this.configService.register("LootSocketRollConfig", this.lootSocketRollConfig, cfg -> {
            LootSocketRoller.setConfig(cfg);
            CropEssenceDropUtils.setConfig(cfg);
            NPCLootSocketDropEST.setConfig(cfg);
        });

        this.configService.register("LoreConfig", this.loreConfig, LoreSocketManager::initialize);
        this.configService.register("LoreMappingConfig", this.loreMappingConfig, cfg -> {
            LoreGemRegistry.initialize(cfg);
            LoreAbilityRegistry.initialize(cfg);
        });
        this.configService.register("DamageNumberConfig", this.damageNumberConfig, this::applyDamageNumberConfig);
        this.configService.register("WorldRepairConfig", this.worldRepairConfig, cfg -> {});
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
        LoreSocketBenchUI.initialize();
        LoreFeedBenchUI.initialize();
        ReforgeBenchUI.initialize();
        ToolPartsUI.initialize();
        RecipeCombineUI.initialize();
        ResonantCompendiumUI.initialize();
        RuntimeConfigUI.initialize(this);
        // Initialize weapon upgrade tracker with persistence
        File dataFolder = new File(".");
        ReforgeEquip.initialize(dataFolder);
        LoreAbsorptionStore.initialize(dataFolder);
        // Register interaction
        reforgeEquip = new ReforgeEquip();

        // Load and apply all registered configs.
        this.configService.loadAll();
        // Persist any new refinement fields (e.g., material tiers) so they appear in JSON.
        this.refinementConfig.save().join();
        
        this.getCodecRegistry(Interaction.CODEC).register("ReforgeEquip", ReforgeEquip.class, ReforgeEquip.CODEC);
        
        // Register Bench interactions
        this.getCodecRegistry(Interaction.CODEC).register("SocketPunchBench", SocketPunchBench.class, SocketPunchBench.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("EssenceSocketBench", EssenceSocketBench.class, EssenceSocketBench.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("LoreSocketBench", LoreSocketBench.class, LoreSocketBench.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("HatchetThrowUse", HatchetThrowUse.class, HatchetThrowUse.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("ResonantRecipeCombineUse", ResonantRecipeCombineUse.class, ResonantRecipeCombineUse.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("ResonantCompendiumUse", ResonantCompendiumUse.class, ResonantCompendiumUse.CODEC);
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (event != null) {
                LangLoader.getPlayerLanguage(event.getPlayer());
            }
            OpenGuiListener.openGui(event);
        });
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, DamageBlockEvent.class, LeafSaplingDropUtils::onDamageBlock);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, BreakBlockEvent.class, LeafSaplingDropUtils::onBreakBlock);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, DamageBlockEvent.class, CropEssenceDropUtils::onDamageBlock);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, BreakBlockEvent.class, CropEssenceDropUtils::onBreakBlock);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, PlayerMouseButtonEvent.class, hatchetThrowEST::onPlayerMouseButton);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, PlayerInteractEvent.class, hatchetThrowEST::onPlayerInteract);
        this.getEventRegistry().registerGlobal(EventPriority.FIRST, DrainPlayerFromWorldEvent.class, hatchetThrowEST::onDrainPlayerFromWorld);
        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, TreasureChestSocketLootListener::onPlayerInteract);
        this.getEventRegistry().registerGlobal(TreasureChestOpeningEvent.class, TreasureChestSocketLootListener::onTreasureChestOpening);
        System.out.println("[SocketReforge] Hatchet input listeners registered");
        this.getCommandRegistry().registerCommand(new ToolPartsCommand("toolpartsui", "Open modular tool parts bench UI", false));
        this.getCommandRegistry().registerCommand(new SocketPunchCommand("socketpunch", "Open socket punch bench UI", false));
        this.getCommandRegistry().registerCommand(new EssenceCommand("essence", "Open essence socket bench UI", false));
        this.getCommandRegistry().registerCommand(new RuntimeConfigCommand("reforgeconfig", "Open live runtime config UI", false));
        this.getCommandRegistry().registerCommand(new ReforgeAdminCommand("reforgeadmin", "OP tools for held-item refinement/socket metadata", false));
        this.getCommandRegistry().registerCommand(new LoreSocketCommand("loregem", "Open lore gem socketing UI", false));
        this.getCommandRegistry().registerCommand(new LoreFeedCommand("lorefeed", "Open lore feed UI", false));
        this.getCommandRegistry().registerCommand(new LoreApplyCommand("loreapply", "OP: apply a lore spirit to the held weapon", false));
        this.getCommandRegistry().registerCommand(new LoreAbilityMapCommand("loreabilities", "Dump lore spirit ability map", false));
        this.getCommandRegistry().registerCommand(new LoreSpiritMapCommand("lorecolors", "Dump lore spirit to gem color map", false));
        this.getCommandRegistry().registerCommand(new SpawnEquipChestCommand("spawnequipchest", "Spawn a test chest with equipment-likely loot", false));
        this.getCommandRegistry().registerCommand(new SpawnEquipEnemyCommand("spawnequipenemy", "Spawn equipment-eligible enemy test NPCs", false));
        this.getCommandRegistry().registerCommand(new ItemMetaCommand("itemmeta", "View or modify item metadata", false));
        this.getCommandRegistry().registerCommand(new ItemMetaCommand("showmeta", "View or modify item metadata", false));
        this.getCommandRegistry().registerCommand(new ResonanceListCommand("resonancecombos", "List seeded resonance combinations", false));
        this.getCommandRegistry().registerCommand(new ResonanceRecipeGiveCommand("resonancerecipe", "OP: give resonant recipe shards", false));
        this.getCommandRegistry().registerCommand(new ResonanceWorldScanCommand("resonanceworldscan", "Scan main world containers for resonant migrations", false));
        // Register ECS damage systems
        this.getEntityStoreRegistry().registerSystem(refineEST);
        this.getEntityStoreRegistry().registerSystem(socketEffectEST);
        this.getEntityStoreRegistry().registerSystem(loreEffectEST);
        if (customCombatTextEnabled) {
            registerDamageNumberSystem();
        } else {
            damageNumberSystemRegistered = false;
        }
        this.getEntityStoreRegistry().registerSystem(loreKillEST);
        this.getEntityStoreRegistry().registerSystem(lorePlayerStateEST);
        this.getEntityStoreRegistry().registerSystem(socketStatSystem);
        this.getEntityStoreRegistry().registerSystem(lifeHealthSystem);
        this.getEntityStoreRegistry().registerSystem(waterRegenSystem);
        this.getEntityStoreRegistry().registerSystem(hatchetThrowEST);
        this.getEntityStoreRegistry().registerSystem(salvageMetadataCompatEST);
        this.getEntityStoreRegistry().registerSystem(chestWindowSocketLootEST);
        this.getEntityStoreRegistry().registerSystem(npcLootSocketDropEST);

        systemsRegistered = true;
        
        //HSTATS
        new HStats("2ec5204c-3635-430d-9d75-bb4529430f77", "1.3.7a");
    }

    private void applyDamageNumberConfig(DamageNumberConfig cfg) {
        DamageNumbers.applyConfig(cfg);
        boolean useCustom = cfg == null || cfg.isUseCustomCombatText();
        customCombatTextEnabled = useCustom;
        DamageNumberEST.setCustomCombatTextEnabled(useCustom);
        if (systemsRegistered) {
            if (useCustom) {
                registerDamageNumberSystem();
            } else {
                unregisterDamageNumberSystem();
            }
        }
        if (useCustom) {
            disableBuiltinCombatText();
        } else {
            enableBuiltinCombatText();
            DamageNumberEST.resetCombatTextComponentsForAllViewers();
        }
    }

    private void disableBuiltinCombatText() {
        try {
            com.hypixel.hytale.component.ComponentRegistry registry = resolveComponentRegistry();
            if (registry == null) return;
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class systemClass = Class.forName(
                    "com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$EntityUIEvents");
            if (registry.hasSystemClass(systemClass)) {
                registry.unregisterSystem(systemClass);
                System.out.println("[SocketReforge] Disabled built-in combat text system.");
            }
        } catch (Throwable t) {
            System.out.println("[SocketReforge] Unable to disable built-in combat text system: " + t.getMessage());
        }
    }

    private void enableBuiltinCombatText() {
        try {
            com.hypixel.hytale.component.ComponentRegistry registry = resolveComponentRegistry();
            if (registry == null) return;
            @SuppressWarnings({"rawtypes", "unchecked"})
            Class systemClass = Class.forName(
                    "com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems$EntityUIEvents");
            if (!registry.hasSystemClass(systemClass)) {
                Object system = systemClass.getDeclaredConstructor().newInstance();
                registerSystemReflective(registry, system);
                System.out.println("[SocketReforge] Enabled built-in combat text system.");
            }
        } catch (Throwable t) {
            System.out.println("[SocketReforge] Unable to enable built-in combat text system: " + t.getMessage());
        }
    }

    private void registerDamageNumberSystem() {
        try {
            Object registry = resolveComponentRegistry();
            if (registry == null) {
                return;
            }
            if (!hasSystemClassReflective(registry, DamageNumberEST.class)) {
                registerSystemReflective(registry, damageNumberEST);
            }
            damageNumberSystemRegistered = true;
        } catch (Throwable t) {
            System.out.println("[SocketReforge] Unable to register custom combat text system: " + t.getMessage());
        }
    }

    private void unregisterDamageNumberSystem() {
        try {
            Object registry = resolveComponentRegistry();
            if (registry == null) {
                return;
            }
            if (hasSystemClassReflective(registry, DamageNumberEST.class)) {
                unregisterSystemReflective(registry, DamageNumberEST.class);
            }
            damageNumberSystemRegistered = false;
        } catch (Throwable t) {
            System.out.println("[SocketReforge] Unable to unregister custom combat text system: " + t.getMessage());
        }
    }

    private boolean hasSystemClassReflective(Object registry, Class<?> systemClass) throws Exception {
        Object result = registry.getClass().getMethod("hasSystemClass", Class.class).invoke(registry, systemClass);
        return result instanceof Boolean value && value;
    }

    private void unregisterSystemReflective(Object registry, Class<?> systemClass) throws Exception {
        registry.getClass().getMethod("unregisterSystem", Class.class).invoke(registry, systemClass);
    }

    private void registerSystemReflective(Object registry, Object system) throws Exception {
        Class<?> systemType = com.hypixel.hytale.component.system.ISystem.class;
        registry.getClass().getMethod("registerSystem", systemType).invoke(registry, system);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private com.hypixel.hytale.component.ComponentRegistry resolveComponentRegistry() throws Exception {
        Object proxy = this.getEntityStoreRegistry();
        java.lang.reflect.Field registryField = proxy.getClass().getDeclaredField("registry");
        registryField.setAccessible(true);
        Object registryObj = registryField.get(proxy);
        if (registryObj instanceof com.hypixel.hytale.component.ComponentRegistry registry) {
            return registry;
        }
        return null;
    }

    @Override
    protected void start() {
        logDroplistPatchStatus();
        autoRegenRegionIfNeeded();
        scheduleChunkRemovalIfNeeded();
        injectHatchetUseInteractions();
        injectResonantRecipeUseInteraction();
        injectResonantCompendiumUseInteraction();
        configureResonanceSeedFromMainWorld();
        CropEssenceDropUtils.onServerStart();
        scheduleWorldRepairIfNeeded();
    }

    protected void stop() {
        // Stop scheduled tasks
        if (autoSaveTimer != null) {
            autoSaveTimer.cancel();
        }

        if (weaponSyncTimer != null) {
            weaponSyncTimer.cancel();
        }

        if (worldRepairTimer != null) {
            worldRepairTimer.cancel();
        }

        if (chunkRemovalTimer != null) {
            chunkRemovalTimer.cancel();
        }
    }

    /**
     * Gets the SFX config instance
     */
    public Config<SFXConfig> getSfxconfig() {
        return sfxconfig;
    }

    public static ReforgePlugin getInstance() {
        return instance;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public SocketConfig getSocketRuntimeConfig() {
        return socketConfig.get();
    }

    public RefinementConfig getRefinementRuntimeConfig() {
        return refinementConfig.get();
    }


    public LootSocketRollConfig getLootSocketRollRuntimeConfig() {
        return lootSocketRollConfig.get();
    }

    public LoreConfig getLoreRuntimeConfig() {
        return loreConfig.get();
    }

    public DamageNumberConfig getDamageNumberRuntimeConfig() {
        return damageNumberConfig.get();
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
                    List<Player> players = List.of();

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

    private void injectHatchetUseInteractions() {
        try {
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            if (assetMap == null || assetMap.getAssetMap() == null) {
                System.out.println("[SocketReforge] Hatchet interaction injection skipped: item asset map unavailable");
                return;
            }

            int hatchetCount = 0;
            int updatedCount = 0;
            for (Map.Entry<String, Item> entry : assetMap.getAssetMap().entrySet()) {
                if (!isHatchetItem(entry.getKey(), entry.getValue())) {
                    continue;
                }
                hatchetCount++;
                boolean changed = false;
                changed |= ensureItemInteraction(entry.getValue(), InteractionType.Secondary, "HatchetThrowUse");
                changed |= removeItemInteraction(entry.getValue(), InteractionType.Use, "HatchetThrowUse");
                if (changed) {
                    updatedCount++;
                }
            }

            System.out.println("[SocketReforge] Hatchet secondary interaction ready for "
                    + hatchetCount
                    + " items ("
                    + updatedCount
                    + " updated)");
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to inject hatchet interactions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void injectResonantRecipeUseInteraction() {
        try {
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            if (assetMap == null || assetMap.getAssetMap() == null) {
                System.out.println("[SocketReforge] Resonant recipe interaction injection skipped: item asset map unavailable");
                return;
            }

            Item recipeItem = assetMap.getAssetMap().get("Resonant_Recipe");
            if (recipeItem == null || recipeItem == Item.UNKNOWN) {
                System.out.println("[SocketReforge] Resonant recipe interaction injection skipped: item not found");
                return;
            }

            boolean changed = ensureItemInteraction(recipeItem, InteractionType.Use, "ResonantRecipeCombineUse");
            if (changed) {
                System.out.println("[SocketReforge] Resonant recipe combine interaction enabled");
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to inject resonant recipe interaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void injectResonantCompendiumUseInteraction() {
        try {
            DefaultAssetMap<String, Item> assetMap = Item.getAssetMap();
            if (assetMap == null || assetMap.getAssetMap() == null) {
                System.out.println("[SocketReforge] Resonant compendium interaction injection skipped: item asset map unavailable");
                return;
            }

            Item compendiumItem = assetMap.getAssetMap().get("Resonant_Compendium");
            if (compendiumItem == null || compendiumItem == Item.UNKNOWN) {
                System.out.println("[SocketReforge] Resonant compendium interaction injection skipped: item not found");
                return;
            }

            boolean changed = ensureItemInteraction(compendiumItem, InteractionType.Use, "ResonantCompendiumUse");
            changed |= ensureItemInteraction(compendiumItem, InteractionType.Secondary, "ResonantCompendiumUse");
            if (changed) {
                System.out.println("[SocketReforge] Resonant compendium interaction enabled");
            }
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to inject resonant compendium interaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void configureResonanceSeedFromMainWorld() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                System.out.println("[SocketReforge] Resonance seed not set (Universe unavailable).");
                return;
            }
            World world = universe.getDefaultWorld();
            if (world == null) {
                System.out.println("[SocketReforge] Resonance seed not set (default world unavailable).");
                return;
            }
            WorldConfig config = world.getWorldConfig();
            if (config == null) {
                System.out.println("[SocketReforge] Resonance seed not set (world config unavailable).");
                return;
            }
            ResonanceSystem.setResonanceSeed(config.getSeed());
            System.out.println("[SocketReforge] Resonance combinations seeded from main world.");
        } catch (Exception e) {
            System.err.println("[SocketReforge] Failed to set resonance seed: " + e.getMessage());
        }
    }

    private void scheduleWorldRepairIfNeeded() {
        WorldRepairConfig cfg = worldRepairConfig.get();
        if (cfg == null) {
            System.out.println("[SocketReforge] World repair skipped (config unavailable).");
            return;
        }

        if (!cfg.isAutoFixEmptyDroplistsOnStart() || cfg.isAutoFixEmptyDroplistsDone()) {
            return;
        }

        if (worldRepairTimer != null) {
            worldRepairTimer.cancel();
        }

        System.out.println("[SocketReforge] World repair queued (AUTO_FIX_EMPTY_DROPLISTS_ON_START=true).");
        worldRepairTimer = new Timer("ReforgeWorldRepair", true);
        long initialDelayMs = 2_000L;
        long retryDelayMs = 5_000L;
        worldRepairTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tryScheduleWorldRepair();
            }
        }, initialDelayMs, retryDelayMs);
    }

    private void scheduleChunkRemovalIfNeeded() {
        WorldRepairConfig cfg = worldRepairConfig.get();
        if (cfg == null) {
            System.out.println("[SocketReforge] Chunk removal skipped (config unavailable).");
            return;
        }

        if (!cfg.isAutoRemoveChunkOnStart() || cfg.isAutoRemoveChunkDone()) {
            return;
        }

        if (chunkRemovalTimer != null) {
            chunkRemovalTimer.cancel();
        }

        System.out.println("[SocketReforge] Chunk removal queued (AUTO_REMOVE_CHUNK_ON_START=true).");
        chunkRemovalTimer = new Timer("ReforgeChunkRemoval", true);
        long initialDelayMs = 2_000L;
        long retryDelayMs = 5_000L;
        chunkRemovalTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                tryScheduleChunkRemoval();
            }
        }, initialDelayMs, retryDelayMs);
    }

    private void tryScheduleChunkRemoval() {
        if (!chunkRemovalScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            Universe universe = Universe.get();
            if (universe == null) {
                chunkRemovalScheduled.set(false);
                return;
            }

            World world = universe.getDefaultWorld();
            if (world == null) {
                chunkRemovalScheduled.set(false);
                return;
            }

            world.execute(() -> runChunkRemoval(world));
        } catch (Exception e) {
            chunkRemovalScheduled.set(false);
            System.err.println("[SocketReforge] Failed to schedule chunk removal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runChunkRemoval(World world) {
        try {
            WorldRepairConfig cfg = worldRepairConfig.get();
            if (cfg == null) {
                System.out.println("[SocketReforge] Chunk removal skipped (config unavailable).");
                return;
            }

            ChunkStore chunkStore = world.getChunkStore();
            if (chunkStore == null) {
                System.out.println("[SocketReforge] Chunk removal skipped (chunk store unavailable).");
                return;
            }

            IChunkSaver saver = chunkStore.getSaver();
            if (saver == null) {
                System.out.println("[SocketReforge] Chunk removal retrying (chunk saver unavailable).");
                return;
            }

            int chunkX = cfg.getAutoRemoveChunkX();
            int chunkZ = cfg.getAutoRemoveChunkZ();
            saver.removeHolder(chunkX, chunkZ).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    System.err.println("[SocketReforge] Chunk removal failed for (" + chunkX + "," + chunkZ + "): "
                            + throwable.getMessage());
                    throwable.printStackTrace();
                    return;
                }

                System.out.println("[SocketReforge] Chunk removed for regeneration: (" + chunkX + "," + chunkZ + ")");
                cfg.setAutoRemoveChunkDone(true);
                cfg.setAutoRemoveChunkOnStart(false);
                configService.saveAndApply("WorldRepairConfig");
                stopChunkRemovalTimer();
            });
        } catch (Exception e) {
            System.err.println("[SocketReforge] Chunk removal crashed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            chunkRemovalScheduled.set(false);
        }
    }

    private void stopChunkRemovalTimer() {
        if (chunkRemovalTimer != null) {
            chunkRemovalTimer.cancel();
            chunkRemovalTimer = null;
        }
    }

    private void autoRegenRegionIfNeeded() {
        WorldRepairConfig cfg = worldRepairConfig.get();
        if (cfg == null) {
            System.out.println("[SocketReforge] Region regen skipped (config unavailable).");
            return;
        }

        if (!cfg.isAutoRegenRegionOnStart() || cfg.isAutoRegenRegionDone()) {
            return;
        }

        String regionPath = cfg.getAutoRegenRegionFile();
        if (regionPath == null || regionPath.isBlank()) {
            System.out.println("[SocketReforge] Region regen skipped (AUTO_REGEN_REGION_FILE is blank).");
            return;
        }

        File regionFile = new File(regionPath);
        if (!regionFile.exists()) {
            System.out.println("[SocketReforge] Region regen skipped (file not found): " + regionFile.getPath());
            return;
        }

        File backupFile = resolveRegionBackupFile(regionFile);
        if (backupFile == null) {
            System.out.println("[SocketReforge] Region regen skipped (no backup filename available): "
                    + regionFile.getPath());
            return;
        }

        if (!regionFile.renameTo(backupFile)) {
            System.out.println("[SocketReforge] Region regen failed (rename failed): " + regionFile.getPath());
            return;
        }

        System.out.println("[SocketReforge] Region file renamed for regeneration: "
                + regionFile.getPath()
                + " -> "
                + backupFile.getPath());

        cfg.setAutoRegenRegionDone(true);
        cfg.setAutoRegenRegionOnStart(false);
        configService.saveAndApply("WorldRepairConfig");
    }

    private void logDroplistPatchStatus() {
        boolean patched = false;
        String version = null;
        try {
            Class<?> validatorClass = Class.forName("com.hypixel.hytale.assetstore.AssetKeyValidator");
            try {
                Field activeField = validatorClass.getDeclaredField("PATCH_ACTIVE");
                activeField.setAccessible(true);
                patched = activeField.getBoolean(null);
            } catch (NoSuchFieldException ignored) {
                patched = false;
            }
            try {
                Field versionField = validatorClass.getDeclaredField("PATCH_VERSION");
                versionField.setAccessible(true);
                Object value = versionField.get(null);
                if (value != null) {
                    version = value.toString();
                }
            } catch (NoSuchFieldException ignored) {
                // No version marker found.
            }
        } catch (Exception ignored) {
            patched = false;
        }

        if (patched) {
            String suffix = version == null || version.isBlank() ? "" : (" (" + version + ")");
            System.out.println("[SocketReforge] Droplist patch active" + suffix + ".");
        } else {
            System.out.println("[SocketReforge] Droplist patch NOT detected. Empty droplist chunks may still crash.");
            System.out.println("[SocketReforge] See server/patches/README.txt for the quick patch steps.");
        }
    }

    private static File resolveRegionBackupFile(File regionFile) {
        File parent = regionFile.getParentFile();
        String baseName = regionFile.getName();
        File candidate = parent == null
                ? new File(baseName + ".bak")
                : new File(parent, baseName + ".bak");
        if (!candidate.exists()) {
            return candidate;
        }

        for (int i = 1; i <= 9; i++) {
            candidate = parent == null
                    ? new File(baseName + ".bak" + i)
                    : new File(parent, baseName + ".bak" + i);
            if (!candidate.exists()) {
                return candidate;
            }
        }

        return null;
    }

    private void tryScheduleWorldRepair() {
        if (!worldRepairScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            Universe universe = Universe.get();
            if (universe == null) {
                worldRepairScheduled.set(false);
                return;
            }

            World world = universe.getDefaultWorld();
            if (world == null) {
                worldRepairScheduled.set(false);
                return;
            }

            world.execute(() -> runWorldDroplistRepair(world));
        } catch (Exception e) {
            worldRepairScheduled.set(false);
            System.err.println("[SocketReforge] Failed to schedule world repair: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runWorldDroplistRepair(World world) {
        try {
            WorldDroplistRepairResult result = WorldDroplistRepairUtils.clearEmptyDroplists(world);
            if (!result.isSuccess()) {
                System.err.println("[SocketReforge] World repair failed: " + result.error());
                return;
            }

            System.out.println("[SocketReforge] World repair completed: "
                    + result.droplistsCleared()
                    + " empty droplists cleared across "
                    + result.containersScanned()
                    + " containers.");

            WorldRepairConfig cfg = worldRepairConfig.get();
            if (cfg != null) {
                cfg.setAutoFixEmptyDroplistsDone(true);
                cfg.setAutoFixEmptyDroplistsOnStart(false);
                configService.saveAndApply("WorldRepairConfig");
            }

            stopWorldRepairTimer();
        } catch (Exception e) {
            System.err.println("[SocketReforge] World repair crashed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            worldRepairScheduled.set(false);
        }
    }

    private void stopWorldRepairTimer() {
        if (worldRepairTimer != null) {
            worldRepairTimer.cancel();
            worldRepairTimer = null;
        }
    }

    private static boolean isHatchetItem(String itemId, Item item) {
        if (item == null || item == Item.UNKNOWN || item.getTool() == null) {
            return false;
        }
        return itemId != null && itemId.toLowerCase(Locale.ROOT).contains("hatchet");
    }

    private static boolean ensureItemInteraction(Item item, InteractionType type, String rootInteractionId)
            throws ReflectiveOperationException {
        Map<InteractionType, String> currentInteractions = item.getInteractions();
        String currentRootId = currentInteractions == null ? null : currentInteractions.get(type);
        InteractionConfiguration interactionConfig = item.getInteractionConfig();

        if (rootInteractionId.equals(currentRootId) && interactionConfig != null) {
            return false;
        }

        EnumMap<InteractionType, String> updatedInteractions = new EnumMap<>(InteractionType.class);
        if (currentInteractions != null) {
            updatedInteractions.putAll(currentInteractions);
        }
        updatedInteractions.put(type, rootInteractionId);

        setField(item, "interactions", updatedInteractions);
        if (interactionConfig == null) {
            setField(item, "interactionConfig", InteractionConfiguration.DEFAULT);
        }
        setField(item, "cachedPacket", null);
        return true;
    }

    private static boolean removeItemInteraction(Item item, InteractionType type, String rootInteractionId)
            throws ReflectiveOperationException {
        Map<InteractionType, String> currentInteractions = item.getInteractions();
        if (currentInteractions == null || !rootInteractionId.equals(currentInteractions.get(type))) {
            return false;
        }

        EnumMap<InteractionType, String> updatedInteractions = new EnumMap<>(InteractionType.class);
        updatedInteractions.putAll(currentInteractions);
        updatedInteractions.remove(type);

        setField(item, "interactions", updatedInteractions);
        setField(item, "cachedPacket", null);
        return true;
    }

    private static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
