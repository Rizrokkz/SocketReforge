package irai.mod.reforge.Lore;

import java.util.Iterator;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.NPCMarkerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.VelocityThresholdStyle;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;

import it.unimi.dsi.fastutil.Pair;

/**
 * Shared lore proc handler used by damage/kill systems.
 */
public final class LoreProcHandler {
    private static final boolean DEBUG_LORE_PROCS = LoreDebug.ENABLED;
    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<String, Long> READY_NOTIFICATION_AT = new ConcurrentHashMap<>();
    private static final long READY_NOTIFICATION_MIN_COOLDOWN_MS = 1500L;
    private static final Map<String, Set<Ref<EntityStore>>> WOLF_SUMMONS = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> FROZEN_UNTIL = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> HEAL_HOT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> BLEED_DOT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, Long> DRAIN_DOT_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ABILITY1_ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WHIRLWIND_ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> WHIRLWIND_SWING_INDEX = new ConcurrentHashMap<>();
    private static final boolean DISABLE_LORE_DAMAGE_CLAMPS = Boolean.parseBoolean(
            System.getProperty("socketreforge.lore.disableDamageClamps", "true"));
    private static final long ABILITY1_GRACE_MS = 900L;
    private static final Map<UUID, Deque<LoreProcBatch>> LORE_PROC_QUEUE = new ConcurrentHashMap<>();
    private static final Set<UUID> LORE_PROC_RUNNING = ConcurrentHashMap.newKeySet();
    private static final long LORE_QUEUE_RETRY_MS = 150L;
    private static final long LORE_QUEUE_MIN_DELAY_MS = 120L;
    private static final int WOLF_SUMMON_MAX_ATTEMPTS = 6;
    private static final double WOLF_SUMMON_MIN_RADIUS = 1.2d;
    private static final double WOLF_SUMMON_MAX_RADIUS = 2.8d;
    private static final double HEAL_AREA_RADIUS = LoreAbility.BASE_HEAL_AREA_RADIUS;
    private static final int HEAL_HOT_TICKS = LoreAbility.BASE_HOT_TICKS;
    private static final long HEAL_HOT_TICK_MS = LoreAbility.BASE_HOT_TICK_MS;
    private static final long HEAL_AREA_TICK_MS = LoreAbility.BASE_AREA_HEAL_TICK_MS;
    private static final long BLEED_DOT_TICK_MS = 300L;
    private static final long BLEED_BASE_DURATION_MS = 3000L;
    private static final double BLEED_RAMP_PER_TICK = 0.08d;
    private static final double BLEED_BASE_MAX_HP_PCT = 0.005d;
    private static final long DRAIN_DOT_TICK_MS = 300L;
    private static final long DRAIN_BASE_DURATION_MS = 3000L;
    private static final long SIGNATURE_FALLBACK_EFFECT_MS = 2000L;
    private static final long WHIRLWIND_TICK_MS = 500L;
    private static final long WHIRLWIND_SPIN_BURST_MS = 150L;
    private static final long FREEZE_PULSE_MS = 400L;
    private static final int FREEZE_MAX_PULSES = 8;
    private static final boolean FORCE_SIGNATURE_RULES = true;
    private static final int HEAL_MAX_PLAYER_CHECKS = 96;
    private static final long MULTIHIT_HIT_DELAY_MS = 250L;
    private static final int OMNISLASH_BASE_HITS = 2;
    private static final int OMNISLASH_MAX_HITS = 8;
    private static final int OCTASLASH_BASE_HITS = 2;
    private static final int OCTASLASH_MAX_HITS = 8;
    private static final int PUMMEL_BASE_HITS = 2;
    private static final int PUMMEL_MAX_HITS = 3;
    private static final int BLOOD_RUSH_BASE_HITS = 3;
    private static final int BLOOD_RUSH_MAX_HITS = 6;
    private static final int CHARGE_ATTACK_BASE_HITS = 1;
    private static final int CHARGE_ATTACK_MAX_HITS = 3;
    private static final int OMNISLASH_MAX_TARGETS = 8;
    private static final int OMNISLASH_MAX_NPC_CHECKS = 160;
    private static final double OMNISLASH_RADIUS = LoreAbility.BASE_OMNISLASH_RADIUS;
    private static final long OMNISLASH_HIT_DELAY_MS = 500L;
    private static final float OMNISLASH_FINAL_MULTIPLIER = 3.0f;
    private static final float OCTASLASH_FINAL_MULTIPLIER = 3.0f;
    private static final double OMNISLASH_RAMP_PER_HIT = 0.10d;
    private static final double OCTASLASH_RAMP_PER_HIT = 0.10d;
    private static final double SYNERGY_PROC_BONUS = 0.25d;
    private static final double SYNERGY_PROC_MAX = 0.85d;
    private static final double SIGNATURE_GROUNDSLAM_RADIUS = 2.5d;
    private static final double SIGNATURE_WHIRLWIND_RADIUS = LoreAbility.BASE_WHIRLWIND_RADIUS;
    private static final double SIGNATURE_GROUNDSLAM_PCT = 0.40d;
    private static final double SIGNATURE_WHIRLWIND_PCT = 0.28d;
    private static final double SIGNATURE_RAZORSTRIKE_PCT = 0.32d;
    private static final double VORTEXSTRIKE_LUNGE_DISTANCE = 3.0d;
    private static final double VORTEXSTRIKE_LUNGE_FORCE_MIN = 3.5d;
    private static final double VORTEXSTRIKE_LUNGE_FORCE_MAX = 8.0d;
    private static final double VORTEXSTRIKE_PASS_THROUGH_DISTANCE = 0.8d;
    private static final VelocityConfig VORTEXSTRIKE_LUNGE_CONFIG = buildSwordLungeConfig();
    private static final double FINALE_BASE_RADIUS = 4.5d;
    private static final double FINALE_DAMAGE_PCT = 0.35d;
    private static final int FINALE_MAX_TARGETS = 12;
    private static final int FINALE_MAX_STACKS = 6;
    private static final long FINALE_MARK_CHECK_MS = 300L;
    private static final long CHARGE_ATTACK_HIT_DELAY_MS = 550L;
    private static final ScheduledExecutorService OMNISLASH_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "Lore-Omnislash");
                thread.setDaemon(true);
                return thread;
            });
    static {
        LoreVisuals.setScheduler(OMNISLASH_SCHEDULER);
    }
    private static VelocityConfig buildSwordLungeConfig() {
        VelocityConfig config = new VelocityConfig();
        try {
            setVelocityConfigField(config, "groundResistance", 0.94f);
            setVelocityConfigField(config, "groundResistanceMax", 0.82f);
            setVelocityConfigField(config, "airResistance", 0.97f);
            setVelocityConfigField(config, "airResistanceMax", 0.96f);
            setVelocityConfigField(config, "threshold", 3.0f);
            setVelocityConfigField(config, "style", VelocityThresholdStyle.Exp);
        } catch (Exception ignored) {
            // best-effort, defaults still behave reasonably
        }
        return config;
    }

    private static void setVelocityConfigField(VelocityConfig config, String field, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = VelocityConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }
    private static final Object NPC_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> npcQuery;
    private static final Object NPC_MARKER_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> npcMarkerQuery;
    private static final Object STAT_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> statQuery;
    private static final Object TRANSFORM_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> transformQuery;
    private static final Object PLAYER_QUERY_LOCK = new Object();
    private static volatile Query<EntityStore> playerQuery;
    private static final boolean LORE_DISABLE_SIGNATURE_ENERGY = true;
    private static final Map<String, String> SIGNATURE_ROOT_BY_KEY = Map.of(
            "tooltip.lore.signature.groundslam", "Root_Weapon_Mace_Signature_Groundslam",
            "tooltip.lore.signature.vortexstrike", "Root_Weapon_Sword_Signature_Vortexstrike",
            "tooltip.lore.signature.whirlwind", "Root_Weapon_Battleaxe_Signature_Whirlwind",
            "tooltip.lore.signature.razorstrike", "Root_Weapon_Daggers_Signature_Razorstrike",
            "tooltip.lore.signature.big_arrow", "Root_Weapon_Crossbow_Signature_BigArrow",
            "tooltip.lore.signature.volley", "Root_Weapon_Shortbow_Signature_Volley"
    );
    private static final String META_PARTS_DAMAGE_MULTIPLIER = "SocketReforge.Parts.DamageMultiplier";
    private static final long SIGNATURE_RESTORE_DELAY_MS = 450L;
    private static final Object INTERACTION_BUFFER_LOCK = new Object();
    private static volatile Field interactionManagerCommandBufferField;

    private static final String[] BURN_EFFECT_IDS = {
            "Status/Burn", "Status/Lava_Burn", "Weapons/Flame_Staff_Burn",
            "Burn", "Lava_Burn", "Flame_Staff_Burn",
            "EntityEffect_Burning", "EntityEffect_Burn", "Effect_Burn", "Burning"
    };
    private static final String[] FREEZE_EFFECT_IDS = {
            "Lore/Lore_Freeze",
            "Lore_Freeze",
            "Status/Freeze",
            "Freeze", "Frozen",
            "EntityEffect_Frozen", "EntityEffect_Freeze", "Effect_Freeze"
    };
    private static final String[] FREEZE_PARTICLE_IDS = {
            "Block/Ice/Block_Hit_Ice",
            "Block/Ice/Block_Break_Ice",
            "Block_Hit_Ice",
            "Block_Break_Ice",
            "Effect_Snow_Impact",
            "Effect_Snow"
    };
    private static final String[] SHATTER_PARTICLE_IDS = {
            "Combat/Impact/Misc/Ice/Impact_Ice",
            "Impact_Ice",
            "Projectile/Iceball/IceBall_Explosion",
            "IceBall_Explosion",
            "Block/Ice/Block_Break_Ice",
            "Block/Ice/Block_Hit_Ice",
            "Block_Break_Ice",
            "Block_Hit_Ice"
    };
    private static final String[] SHOCK_EFFECT_IDS = {
            "Status/Stun",
            "Shock", "Shocked",
            "EntityEffect_Shocked", "EntityEffect_Shock", "Effect_Shock"
    };
    private static final String[] BLEED_EFFECT_IDS = {
            "Status/Bleed",
            "Bleed", "Bleeding",
            "EntityEffect_Bleed", "EntityEffect_Bleeding", "Effect_Bleed"
    };
    private static final String[] POISON_EFFECT_IDS = {
            "Status/Poison", "Status/Poison_T1", "Status/Poison_T2", "Status/Poison_T3",
            "Poison", "Poisoned",
            "EntityEffect_Poison", "Effect_Poison"
    };
    private static final String[] DRAIN_EFFECT_IDS = {
            "Status/Life_Drain", "Status/Drain_Life",
            "Life_Drain", "Drain_Life",
            "EntityEffect_Poison", "Effect_Poison"
    };
    private static final String[] SLOW_EFFECT_IDS = {
            "Status/Slow", "Deployables/Slowness_Totem_Slow",
            "Slow", "Slowness",
            "EntityEffect_Slow", "EntityEffect_Slowness", "Effect_Slow"
    };
    private static final String[] WEAKNESS_EFFECT_IDS = {
            "Status/Slow",
            "Weakness", "Weakened",
            "EntityEffect_Weakness", "Effect_Weakness"
    };
    private static final String[] BLIND_EFFECT_IDS = {
            "Status/Slow",
            "Blind", "Blindness",
            "EntityEffect_Blind", "EntityEffect_Blindness", "Effect_Blind"
    };
    private static final String[] ROOT_EFFECT_IDS = {
            "Status/Root",
            "Root", "Rooted",
            "EntityEffect_Root", "EntityEffect_Rooted", "Effect_Root"
    };
    private static final String[] STUN_EFFECT_IDS = {
            "Status/Stun", "Projectiles/Bomb/Bomb_Explode_Stun", "Tests/Stick_Stun",
            "Stun", "Stunned",
            "EntityEffect_Stun", "Effect_Stun"
    };
    private static final String[] FEAR_EFFECT_IDS = {
            "Status/Slow",
            "Fear", "Feared",
            "EntityEffect_Fear", "Effect_Fear"
    };
    private static final String[] HASTE_EFFECT_IDS = {
            "Lore/Berserk_Haste", "Berserk_Haste", "Lore_Berserk_Haste",
            "Status/Antidote",
            "Haste", "Speed",
            "EntityEffect_Haste", "EntityEffect_Speed", "Effect_Haste"
    };
    private static final String[] HEAL_TOTEM_EFFECT_IDS = {
            "Deployables/Healing_Totem_Heal",
            "Healing_Totem_Heal"
    };
    private static final String[] HEAL_TOTEM_PARTICLE_IDS = {
            "Deployables/Healing_Totem/Totem_Heal_Simple_Test",
            "Totem_Heal_Simple_Test",
            "Deployables/Healing_Totem/Totem_Heal_AoE",
            "Totem_Heal_AoE",
            "Totem_Heal_Extra"
    };
    private static final String[] SIGNATURE_GROUNDSLAM_PARTICLE_IDS = {
            "Combat/Mace/Signature/Mace_Signature_Ground_Hit",
            "Mace_Signature_Ground_Hit",
            "Combat/Mace/Signature/Mace_Signature_Slash",
            "Mace_Signature_Slash",
            "Combat/Mace/Signature/Impact_Mace_Signature",
            "Impact_Mace_Signature"
    };
    private static final String[] SIGNATURE_GROUNDSLAM_EFFECT_IDS = {
            "Weapons/Mace_Signature",
            "Mace_Signature"
    };
    private static final String[] SIGNATURE_VORTEXSTRIKE_PARTICLE_IDS = {
            "Combat/Sword/Signature/Impact_Sword_Signature_Spin",
            "Impact_Sword_Signature_Spin",
            "Combat/Sword/Signature/Impact_Sword_Signature",
            "Impact_Sword_Signature",
            "Combat/Sword/Signature/Sword_Signature_AoE",
            "Sword_Signature_AoE"
    };
    private static final String[] SIGNATURE_VORTEXSTRIKE_EFFECT_IDS = {
            "Weapons/Sword_Signature_SpinStab",
            "Sword_Signature_SpinStab",
            "Weapons/Mace_Signature",
            "Mace_Signature"
    };
    private static final String[] SIGNATURE_WHIRLWIND_EFFECT_IDS = {
            "Battleaxe_Whirlwind",
            "Weapons/Battleaxe_Signature_Whirlwind",
            "Battleaxe_Signature_Whirlwind",
            "Weapons/Battleaxe_Signature",
            "Battleaxe_Signature"
    };
    private static final String[] SIGNATURE_WHIRLWIND_PARTICLE_IDS = {
            "Lore/Lore_Whirlwind_Spin",
            "Lore_Whirlwind_Spin",
            "Combat/Battleaxe/Signature/Battleaxe_Signature_Whirlwind",
            "Battleaxe_Signature_Whirlwind"
    };
    private static final String[] WHIRLWIND_ANIM_IDS = {
            "WhirlwindChargedSpin",
            "Whirlwind",
            "WhirlwindCharged"
    };
    private static final String[] SIGNATURE_RAZORSTRIKE_PARTICLE_IDS = {
            "Combat/Daggers/Signature/Daggers_Signature_Slash",
            "Daggers_Signature_Slash",
            "Combat/Daggers/Signature/Impact_Daggers_Signature",
            "Impact_Daggers_Signature"
    };
    private static final String[] SIGNATURE_RAZORSTRIKE_EFFECT_IDS = {
            "Weapons/Dagger_Signature",
            "Dagger_Signature"
    };
    private static final String[] CAUSTIC_FINALE_PARTICLE_IDS = {
            "Explosion_Small",
            "Impact_Explosion",
            "Explosion_Medium"
    };
    private static final String[] SHRAPNEL_FINALE_PARTICLE_IDS = {
            "Impact_Explosion",
            "Explosion_Small",
            "Explosion_Medium"
    };
    private static final String[] BURN_FINALE_PARTICLE_IDS = {
            "Explosion_Medium",
            "Explosion_Big",
            "Explosion_Small"
    };
    private static final String[] FINALE_SOUND_IDS = {
            "SFX_Goblin_Lobber_Bomb_Death",
            "SFX_Goblin_Lobber_Bomb_Death_Local"
    };
    private static final long FINALE_MARK_DURATION_MS = 30000L;
    private static final Map<String, Map<Ref<EntityStore>, FinaleMark>> CAUSTIC_FINALE_MARKS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Ref<EntityStore>, FinaleMark>> SHRAPNEL_FINALE_MARKS = new ConcurrentHashMap<>();
    private static final Map<String, Map<Ref<EntityStore>, FinaleMark>> BURN_FINALE_MARKS = new ConcurrentHashMap<>();
    private static final String[] SIGNATURE_VOLLEY_PARTICLE_IDS = {
            "Weapon/Bow/Bow_Signature_Launch",
            "Bow_Signature_Launch"
    };
    private static final String[] SIGNATURE_BIG_ARROW_PARTICLE_IDS = {
            "Weapon/Bow/Bow_Signature_Projectile_Sparks",
            "Bow_Signature_Projectile_Sparks",
            "Weapon/Bow/Bow_Signature_Launch",
            "Bow_Signature_Launch"
    };
    private static final String[] OMNISLASH_SLASH_PARTICLE_IDS = {
            "Combat/Daggers/Signature/Daggers_Signature_Slash",
            "Daggers_Signature_Slash",
            "Combat/Daggers/Signature/Impact_Daggers_Signature_Slash",
            "Impact_Daggers_Signature_Slash",
            "Combat/Daggers/Basic/Impact_Dagger_Slash",
            "Impact_Dagger_Slash",
            "Combat/Mace/Signature/Mace_Signature_Slash",
            "Mace_Signature_Slash"
    };
    private static final String[] BLEED_PARTICLE_IDS = {
            "Combat/Daggers/Basic/Impact_Dagger_Slash",
            "Impact_Dagger_Slash",
            "Combat/Daggers/Signature/Daggers_Signature_Slash",
            "Daggers_Signature_Slash",
            "Combat/Sword/Charged/Sword_Charged_Trail_Praetorian",
            "Sword_Charged_Trail_Praetorian"
    };
    private static final String[] OMNISLASH_TRAIL_EFFECT_IDS = {
            "Weapons/Dagger_Signature",
            "Dagger_Signature",
            "Weapons/Mace_Signature",
            "Mace_Signature"
    };
    private static final String[] BLOOD_RUSH_TRAIL_EFFECT_IDS = {
            "Lore/Lore_BloodRush_Trail",
            "Lore_BloodRush_Trail"
    };
    private static final String[] BLOOD_RUSH_TRAIL_PARTICLE_IDS = {
            "Combat/Sword/Charged/Sword_Charged_Trail_Praetorian",
            "Sword_Charged_Trail_Praetorian",
            "Combat/Sword/Charged/Sword_Charged_Trail_Blade_Praetorian",
            "Sword_Charged_Trail_Blade_Praetorian"
    };
    private static final String[] PUMMEL_TRAIL_EFFECT_IDS = {
            "Lore/Lore_Pummel_Trail",
            "Lore_Pummel_Trail"
    };
    private static final String[] PUMMEL_TRAIL_PARTICLE_IDS = {
            "Lore/Lore_Pummel_Trail",
            "Lore_Pummel_Trail",
            "Combat/Sword/Charged/Sword_Charged_Trail",
            "Sword_Charged_Trail"
    };
    private static final double GROUNDSLAM_AUGMENT_CHANCE_MULTIPLIER = 3.0d;
    private static final Map<UUID, GroundslamContext> GROUNDSLAM_CONTEXTS = new ConcurrentHashMap<>();
    private static final String[] INVISIBLE_EFFECT_IDS = {
            "Status/Antidote",
            "Invisible", "Invisibility",
            "EntityEffect_Invisible", "EntityEffect_Invisibility", "Effect_Invisible"
    };
    private static final String[] SHIELD_EFFECT_IDS = {
            "Status/Immune",
            "Shield", "Barrier",
            "EntityEffect_Shield", "EntityEffect_Barrier", "Effect_Shield"
    };

    public static final class ProcState {
        private boolean triggered;

        public boolean hasTriggered() {
            return triggered;
        }
    }

    private static final class GroundslamContext {
        final UUID playerId;
        final LoreAbility ability;
        final String spiritId;
        final int level;
        final int feedTier;
        int remainingHits;
        long expiresAt;

        GroundslamContext(UUID playerId,
                          LoreAbility ability,
                          String spiritId,
                          int level,
                          int feedTier,
                          int remainingHits,
                          long expiresAt) {
            this.playerId = playerId;
            this.ability = ability;
            this.spiritId = spiritId;
            this.level = level;
            this.feedTier = feedTier;
            this.remainingHits = remainingHits;
            this.expiresAt = expiresAt;
        }
    }

    private static final class PendingLoreProc {
        final LoreAbility ability;
        final String spiritId;
        final int level;
        final int feedTier;
        final int socketIndex;

        PendingLoreProc(LoreAbility ability, String spiritId, int level, int feedTier, int socketIndex) {
            this.ability = ability;
            this.spiritId = spiritId;
            this.level = level;
            this.feedTier = feedTier;
            this.socketIndex = socketIndex;
        }
    }

    private static final class ProcCollectResult {
        final UUID playerId;
        final boolean ability1Active;
        final List<PendingLoreProc> pending = new ArrayList<>();
        final List<PendingLoreProc> eligible = new ArrayList<>();
        boolean synergyCatalyst;

        ProcCollectResult(UUID playerId, boolean ability1Active) {
            this.playerId = playerId;
            this.ability1Active = ability1Active;
        }
    }

    private static final class DamageBaseResult {
        final float amount;
        final boolean skipRefine;

        DamageBaseResult(float amount, boolean skipRefine) {
            this.amount = amount;
            this.skipRefine = skipRefine;
        }
    }

    private static final class LoreProcBatch {
        final Store<EntityStore> store;
        final Ref<EntityStore> selfRef;
        final Ref<EntityStore> otherRef;
        final boolean selfIsAttacker;
        final UUID playerId;
        final List<PendingLoreProc> coreProcs;
        final List<PendingLoreProc> augmentProcs;
        final List<PendingLoreProc> forcedAugments;
        final List<PendingLoreProc> modifierProcs;
        final List<PendingLoreProc> flatProcs;
        final PendingLoreProc groundslam;
        final long durationMs;
        final Damage damage;
        final CommandBuffer<EntityStore> commandBuffer;
        final boolean useEventContext;

        LoreProcBatch(Store<EntityStore> store,
                      Ref<EntityStore> selfRef,
                      Ref<EntityStore> otherRef,
                      boolean selfIsAttacker,
                      UUID playerId,
                      List<PendingLoreProc> coreProcs,
                      List<PendingLoreProc> augmentProcs,
                      List<PendingLoreProc> forcedAugments,
                      List<PendingLoreProc> modifierProcs,
                      PendingLoreProc groundslam,
                      long durationMs,
                      Damage damage,
                      CommandBuffer<EntityStore> commandBuffer,
                      boolean useEventContext) {
            this.store = store;
            this.selfRef = selfRef;
            this.otherRef = otherRef;
            this.selfIsAttacker = selfIsAttacker;
            this.playerId = playerId;
            this.coreProcs = coreProcs;
            this.augmentProcs = augmentProcs;
            this.forcedAugments = forcedAugments;
            this.modifierProcs = modifierProcs;
            this.flatProcs = null;
            this.groundslam = groundslam;
            this.durationMs = durationMs;
            this.damage = damage;
            this.commandBuffer = commandBuffer;
            this.useEventContext = useEventContext;
        }

        LoreProcBatch(Store<EntityStore> store,
                      Ref<EntityStore> selfRef,
                      Ref<EntityStore> otherRef,
                      boolean selfIsAttacker,
                      UUID playerId,
                      List<PendingLoreProc> flatProcs,
                      long durationMs,
                      Damage damage,
                      CommandBuffer<EntityStore> commandBuffer,
                      boolean useEventContext) {
            this.store = store;
            this.selfRef = selfRef;
            this.otherRef = otherRef;
            this.selfIsAttacker = selfIsAttacker;
            this.playerId = playerId;
            this.coreProcs = null;
            this.augmentProcs = null;
            this.forcedAugments = null;
            this.modifierProcs = null;
            this.flatProcs = flatProcs;
            this.groundslam = null;
            this.durationMs = durationMs;
            this.damage = damage;
            this.commandBuffer = commandBuffer;
            this.useEventContext = useEventContext;
        }

        LoreProcBatch withoutEventContext() {
            if (!useEventContext) {
                return this;
            }
            if (flatProcs != null) {
                return new LoreProcBatch(store, selfRef, otherRef, selfIsAttacker, playerId,
                        flatProcs, durationMs, null, null, false);
            }
            return new LoreProcBatch(store, selfRef, otherRef, selfIsAttacker, playerId,
                    coreProcs, augmentProcs, forcedAugments, modifierProcs, groundslam,
                    durationMs, null, null, false);
        }
    }

    private static final class OmnislashSequence {
        final Store<EntityStore> store;
        final Ref<EntityStore> attackerRef;
        final List<Ref<EntityStore>> targets;
        final float perHit;
        final boolean teleportAttacker;
        final boolean skipRefine;
        final int totalHits;
        final String slashParticleId;
        final float finalMultiplier;
        int hitsApplied;

        OmnislashSequence(Store<EntityStore> store,
                          Ref<EntityStore> attackerRef,
                          List<Ref<EntityStore>> targets,
                          float perHit,
                          boolean teleportAttacker,
                          boolean skipRefine,
                          int totalHits,
                          String slashParticleId,
                          float finalMultiplier) {
            this.store = store;
            this.attackerRef = attackerRef;
            this.targets = targets;
            this.perHit = perHit;
            this.teleportAttacker = teleportAttacker;
            this.skipRefine = skipRefine;
            this.totalHits = Math.max(1, totalHits);
            this.slashParticleId = slashParticleId;
            this.finalMultiplier = finalMultiplier <= 0f ? 1.0f : finalMultiplier;
        }
    }

    private static final class FinaleMark {
        final long until;
        final Vector3d position;
        final float amount;
        final int feedTier;
        final String spiritId;

        private FinaleMark(long until, Vector3d position, float amount, int feedTier, String spiritId) {
            this.until = until;
            this.position = position;
            this.amount = amount;
            this.feedTier = feedTier;
            this.spiritId = spiritId;
        }
    }

    private LoreProcHandler() {}

    public static boolean applyLoreSockets(Store<EntityStore> store,
                                           Player self,
                                           Ref<EntityStore> selfRef,
                                           Ref<EntityStore> otherRef,
                                           Damage damage,
                                           LoreSocketData data,
                                           LoreTrigger trigger,
                                           boolean selfIsAttacker,
                                           Set<String> usedSpirits) {
        return applyLoreSocketsInternal(store, self, selfRef, otherRef, damage, data, trigger, selfIsAttacker,
                usedSpirits, null, null);
    }

    public static boolean applyLoreSockets(Store<EntityStore> store,
                                           Player self,
                                           Ref<EntityStore> selfRef,
                                           Ref<EntityStore> otherRef,
                                           Damage damage,
                                           LoreSocketData data,
                                           LoreTrigger trigger,
                                           boolean selfIsAttacker,
                                           Set<String> usedSpirits,
                                           ProcState procState) {
        return applyLoreSocketsInternal(store, self, selfRef, otherRef, damage, data, trigger, selfIsAttacker,
                usedSpirits, procState, null);
    }

    public static boolean applyLoreSockets(Store<EntityStore> store,
                                           Player self,
                                           Ref<EntityStore> selfRef,
                                           Ref<EntityStore> otherRef,
                                           Damage damage,
                                           LoreSocketData data,
                                           LoreTrigger trigger,
                                           boolean selfIsAttacker,
                                           Set<String> usedSpirits,
                                           ProcState procState,
                                           CommandBuffer<EntityStore> commandBuffer) {
        return applyLoreSocketsInternal(store, self, selfRef, otherRef, damage, data, trigger, selfIsAttacker,
                usedSpirits, procState, commandBuffer);
    }

    private static boolean applyLoreSocketsInternal(Store<EntityStore> store,
                                                    Player self,
                                                    Ref<EntityStore> selfRef,
                                                    Ref<EntityStore> otherRef,
                                                    Damage damage,
                                                    LoreSocketData data,
                                                    LoreTrigger trigger,
                                                    boolean selfIsAttacker,
                                                    Set<String> usedSpirits,
                                                    ProcState procState,
                                                    CommandBuffer<EntityStore> commandBuffer) {
        if (store == null || self == null || data == null || trigger == null) {
            return false;
        }
        ProcCollectResult result = collectSocketProcs(store, self, selfRef, otherRef, damage, data, trigger,
                selfIsAttacker, usedSpirits, procState);
        if (result == null || result.pending.isEmpty()) {
            return false;
        }
        if (result.synergyCatalyst) {
            extendSynergyChainProcs(result, data, trigger, usedSpirits, procState);
        }
        int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
        return applyPendingLoreProcs(store, self, selfRef, otherRef, damage, selfIsAttacker, data, result,
                maxLevel, commandBuffer);
    }

    private static ProcCollectResult collectSocketProcs(Store<EntityStore> store,
                                                        Player self,
                                                        Ref<EntityStore> selfRef,
                                                        Ref<EntityStore> otherRef,
                                                        Damage damage,
                                                        LoreSocketData data,
                                                        LoreTrigger trigger,
                                                        boolean selfIsAttacker,
                                                        Set<String> usedSpirits,
                                                        ProcState procState) {
        if (store == null || self == null || data == null || trigger == null) {
            return null;
        }
        UUID playerId = self.getUuid();
        if (playerId == null) {
            return null;
        }
        boolean ability1Active = isAbility1Active(store, selfRef);
        ProcCollectResult result = new ProcCollectResult(playerId, ability1Active);
        if (ability1Active) {
            LoreDebug.logKv("proc.skip", "reason", "Ability1Active", "player", playerId);
            return result;
        }

        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
                continue;
            }
            String spiritId = socket.getSpiritId();
            if (spiritId == null || spiritId.isBlank()) {
                continue;
            }
            String normalized = normalizeSpiritId(spiritId);
            if (usedSpirits != null && usedSpirits.contains(normalized)) {
                continue;
            }

            LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
            if (ability == null || ability.getTrigger() != trigger) {
                continue;
            }
            LoreAbility resolved = applyEffectOverride(ability, socket.getEffectOverride());
            if (resolved == null || resolved.getTrigger() != trigger) {
                continue;
            }
            if (ability1Active && shouldBlockLoreDuringAbility1(resolved)) {
                LoreDebug.logKv("proc.skip", "reason", "Ability1Active", "effect", resolved.getEffectType(),
                        "spirit", spiritId);
                continue;
            }
            if (isCoreEffectType(resolved.getEffectType()) && !isCoreColorForSpirit(spiritId)) {
                LoreDebug.logKv("proc.skip", "reason", "NonCoreColor", "effect", resolved.getEffectType(),
                        "spirit", spiritId);
                continue;
            }
            result.eligible.add(new PendingLoreProc(resolved, spiritId, socket.getLevel(), socket.getFeedTier(), i));
            if (!tryProc(playerId, spiritId, resolved, socket.getFeedTier())) {
                continue;
            }

            result.pending.add(new PendingLoreProc(resolved, spiritId, socket.getLevel(), socket.getFeedTier(), i));
            if (isSynergyCatalyst(resolved, spiritId)) {
                result.synergyCatalyst = true;
            }
            if (procState != null) {
                procState.triggered = true;
            }
            if (usedSpirits != null) {
                usedSpirits.add(normalized);
            }
        }

        return result;
    }

    private static void extendSynergyChainProcs(ProcCollectResult result,
                                                LoreSocketData data,
                                                LoreTrigger trigger,
                                                Set<String> usedSpirits,
                                                ProcState procState) {
        if (result == null || !result.synergyCatalyst || data == null || trigger == null) {
            return;
        }
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
                continue;
            }
            String spiritId = socket.getSpiritId();
            if (spiritId == null || spiritId.isBlank()) {
                continue;
            }
            String normalized = normalizeSpiritId(spiritId);
            if (usedSpirits != null && usedSpirits.contains(normalized)) {
                continue;
            }
            if (isPendingSpirit(result.pending, spiritId)) {
                continue;
            }
            LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
            if (ability == null || ability.getTrigger() != trigger) {
                continue;
            }
            LoreAbility resolved = applyEffectOverride(ability, socket.getEffectOverride());
            if (resolved == null || resolved.getTrigger() != trigger) {
                continue;
            }
            if (result.ability1Active && shouldBlockLoreDuringAbility1(resolved)) {
                continue;
            }
            if (!isSynergyChainTarget(resolved)) {
                continue;
            }
            if (!tryProcSynergy(result.playerId, spiritId, resolved, socket.getFeedTier())) {
                continue;
            }
            result.pending.add(new PendingLoreProc(resolved, spiritId, socket.getLevel(), socket.getFeedTier(), i));
            if (procState != null) {
                procState.triggered = true;
            }
            if (usedSpirits != null) {
                usedSpirits.add(normalized);
            }
        }
    }

    private static boolean applyPendingLoreProcs(Store<EntityStore> store,
                                                 Player self,
                                                 Ref<EntityStore> selfRef,
                                                 Ref<EntityStore> otherRef,
                                                 Damage damage,
                                                 boolean selfIsAttacker,
                                                 LoreSocketData data,
                                                 ProcCollectResult result,
                                                 int maxLevel,
                                                 CommandBuffer<EntityStore> commandBuffer) {
        if (store == null || self == null || data == null || result == null || result.pending.isEmpty()) {
            return false;
        }
        boolean changed = false;
        List<PendingLoreProc> coreProcs = new ArrayList<>();
        List<PendingLoreProc> augmentProcs = new ArrayList<>();
        List<PendingLoreProc> modifierProcs = new ArrayList<>();
        for (PendingLoreProc proc : result.pending) {
            if (isCoreEffect(proc)) {
                coreProcs.add(proc);
            } else if (isAugmentEffect(proc.ability)) {
                augmentProcs.add(proc);
            } else {
                modifierProcs.add(proc);
            }
        }
        java.util.Set<Integer> xpAwarded = new java.util.HashSet<>();
        if (!coreProcs.isEmpty()) {
            List<PendingLoreProc> forcedAugments = collectAlwaysOnAugments(result, augmentProcs);
            PendingLoreProc groundslam = pickGroundslamAugment(result.eligible);
            long durationMs = estimateBatchDurationMs(coreProcs, augmentProcs, forcedAugments, modifierProcs);
            LoreProcBatch batch = new LoreProcBatch(store, selfRef, otherRef, selfIsAttacker, result.playerId,
                    coreProcs, augmentProcs, forcedAugments, modifierProcs, groundslam,
                    durationMs, damage, commandBuffer, true);
            for (PendingLoreProc core : coreProcs) {
                int coreHits = resolveCoreHitCount(core.ability, core.feedTier);
                if (selfIsAttacker && groundslam != null) {
                    armGroundslamAugment(result.playerId, groundslam, coreHits);
                    changed |= applyProcXp(data, groundslam, maxLevel, self, xpAwarded);
                }
                changed |= applyProcXp(data, core, maxLevel, self, xpAwarded);

                for (PendingLoreProc augment : augmentProcs) {
                    if (groundslam != null && augment.socketIndex == groundslam.socketIndex) {
                        continue;
                    }
                    changed |= applyProcXp(data, augment, maxLevel, self, xpAwarded);
                }
                for (PendingLoreProc augment : forcedAugments) {
                    if (groundslam != null && augment.socketIndex == groundslam.socketIndex) {
                        continue;
                    }
                    changed |= applyProcXp(data, augment, maxLevel, self, xpAwarded);
                }
                for (PendingLoreProc modifier : modifierProcs) {
                    changed |= applyProcXp(data, modifier, maxLevel, self, xpAwarded);
                }
            }
            enqueueLoreBatch(batch);
        } else {
            long durationMs = estimateBatchDurationMs(result.pending, null, null, null);
            LoreProcBatch batch = new LoreProcBatch(store, selfRef, otherRef, selfIsAttacker, result.playerId,
                    result.pending, durationMs, damage, commandBuffer, true);
            for (PendingLoreProc proc : result.pending) {
                changed |= applyProcXp(data, proc, maxLevel, self, xpAwarded);
            }
            enqueueLoreBatch(batch);
        }

        return changed;
    }

    private static void enqueueLoreBatch(LoreProcBatch batch) {
        if (batch == null || batch.playerId == null) {
            return;
        }
        Deque<LoreProcBatch> queue = LORE_PROC_QUEUE.computeIfAbsent(batch.playerId, id -> new ArrayDeque<>());
        synchronized (queue) {
            queue.add(batch);
        }
        boolean started = LORE_PROC_RUNNING.add(batch.playerId);
        if (!started) {
            return;
        }
        if (batch.useEventContext && !isAbility1Active(batch.store, batch.selfRef)) {
            synchronized (queue) {
                queue.poll();
            }
            executeBatchInline(batch);
            scheduleNextBatch(batch.playerId, batch.durationMs);
            return;
        }
        // If we couldn't execute immediately (ability1 active), strip event context.
        if (batch.useEventContext) {
            synchronized (queue) {
                queue.poll();
                queue.addFirst(batch.withoutEventContext());
            }
        }
        scheduleProcessQueue(batch.playerId, LORE_QUEUE_RETRY_MS);
    }

    private static void scheduleProcessQueue(UUID playerId, long delayMs) {
        if (playerId == null) {
            return;
        }
        long delay = Math.max(1L, delayMs);
        OMNISLASH_SCHEDULER.schedule(() -> processLoreQueue(playerId), delay, TimeUnit.MILLISECONDS);
    }

    private static void processLoreQueue(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Deque<LoreProcBatch> queue = LORE_PROC_QUEUE.get(playerId);
        if (queue == null) {
            LORE_PROC_RUNNING.remove(playerId);
            LORE_PROC_QUEUE.remove(playerId);
            return;
        }
        LoreProcBatch batch;
        synchronized (queue) {
            if (queue.isEmpty()) {
                LORE_PROC_RUNNING.remove(playerId);
                LORE_PROC_QUEUE.remove(playerId);
                return;
            }
            batch = queue.peek();
        }
        if (batch == null) {
            synchronized (queue) {
                queue.poll();
            }
            scheduleProcessQueue(playerId, LORE_QUEUE_RETRY_MS);
            return;
        }
        Store<EntityStore> store = batch.store;
        if (store == null || store.isShutdown()) {
            synchronized (queue) {
                queue.poll();
            }
            scheduleProcessQueue(playerId, LORE_QUEUE_RETRY_MS);
            return;
        }
        if (isAbility1Active(store, batch.selfRef)) {
            scheduleProcessQueue(playerId, LORE_QUEUE_RETRY_MS);
            return;
        }
        synchronized (queue) {
            queue.poll();
        }
        executeBatchQueued(batch);
        scheduleNextBatch(playerId, batch.durationMs);
    }

    private static void scheduleNextBatch(UUID playerId, long durationMs) {
        long delay = Math.max(LORE_QUEUE_MIN_DELAY_MS, durationMs);
        OMNISLASH_SCHEDULER.schedule(() -> processLoreQueue(playerId), delay, TimeUnit.MILLISECONDS);
    }

    private static void executeBatchInline(LoreProcBatch batch) {
        if (batch == null) {
            return;
        }
        executeBatch(batch, batch.damage, batch.commandBuffer);
    }

    private static void executeBatchQueued(LoreProcBatch batch) {
        if (batch == null) {
            return;
        }
        Store<EntityStore> store = batch.store;
        if (store == null) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> executeBatch(batch, null, null));
    }

    private static void executeBatch(LoreProcBatch batch,
                                     Damage damage,
                                     CommandBuffer<EntityStore> commandBuffer) {
        if (batch == null) {
            return;
        }
        if (batch.coreProcs != null && !batch.coreProcs.isEmpty()) {
            for (PendingLoreProc core : batch.coreProcs) {
                int coreHits = resolveCoreHitCount(core.ability, core.feedTier);
                if (batch.selfIsAttacker && batch.groundslam != null) {
                    armGroundslamAugment(batch.playerId, batch.groundslam, coreHits);
                }
                applyEffectWithSynergy(batch.store, commandBuffer, damage, core.ability, core.level, core.feedTier,
                        batch.selfIsAttacker, batch.selfRef, batch.otherRef, batch.playerId, core.spiritId, 1);

                if (batch.augmentProcs != null) {
                    for (PendingLoreProc augment : batch.augmentProcs) {
                        if (batch.groundslam != null && augment.socketIndex == batch.groundslam.socketIndex) {
                            continue;
                        }
                        applyEffectWithSynergy(batch.store, commandBuffer, damage, augment.ability, augment.level,
                                augment.feedTier, batch.selfIsAttacker, batch.selfRef, batch.otherRef,
                                batch.playerId, augment.spiritId, 1);
                    }
                }
                if (batch.forcedAugments != null) {
                    for (PendingLoreProc augment : batch.forcedAugments) {
                        if (batch.groundslam != null && augment.socketIndex == batch.groundslam.socketIndex) {
                            continue;
                        }
                        applyEffectWithSynergy(batch.store, commandBuffer, damage, augment.ability, augment.level,
                                augment.feedTier, batch.selfIsAttacker, batch.selfRef, batch.otherRef,
                                batch.playerId, augment.spiritId, 1);
                    }
                }
                if (batch.modifierProcs != null) {
                    for (PendingLoreProc modifier : batch.modifierProcs) {
                        applyEffectWithSynergy(batch.store, commandBuffer, damage, modifier.ability, modifier.level,
                                modifier.feedTier, batch.selfIsAttacker, batch.selfRef, batch.otherRef,
                                batch.playerId, modifier.spiritId, 1);
                    }
                }
                if (batch.selfIsAttacker && batch.groundslam != null && core.ability != null
                        && core.ability.getEffectType() == LoreEffectType.SUMMON_WOLF_PACK) {
                    tryApplyGroundslamAugment(batch.store, batch.selfRef, batch.otherRef);
                }
            }
            return;
        }
        if (batch.flatProcs != null) {
            for (PendingLoreProc proc : batch.flatProcs) {
                applyEffectWithSynergy(batch.store, commandBuffer, damage, proc.ability, proc.level, proc.feedTier,
                        batch.selfIsAttacker, batch.selfRef, batch.otherRef, batch.playerId, proc.spiritId, 1);
            }
        }
    }

    public static void applyAbsorbed(Store<EntityStore> store,
                                     Player self,
                                     Ref<EntityStore> selfRef,
                                     Ref<EntityStore> otherRef,
                                     Damage damage,
                                     LoreTrigger trigger,
                                     boolean selfIsAttacker,
                                     Set<String> usedSpirits) {
        applyAbsorbedInternal(store, self, selfRef, otherRef, damage, trigger, selfIsAttacker, usedSpirits,
                null, null);
    }

    public static void applyAbsorbed(Store<EntityStore> store,
                                     Player self,
                                     Ref<EntityStore> selfRef,
                                     Ref<EntityStore> otherRef,
                                     Damage damage,
                                     LoreTrigger trigger,
                                     boolean selfIsAttacker,
                                     Set<String> usedSpirits,
                                     ProcState procState) {
        applyAbsorbedInternal(store, self, selfRef, otherRef, damage, trigger, selfIsAttacker, usedSpirits,
                procState, null);
    }

    public static void applyAbsorbed(Store<EntityStore> store,
                                     Player self,
                                     Ref<EntityStore> selfRef,
                                     Ref<EntityStore> otherRef,
                                     Damage damage,
                                     LoreTrigger trigger,
                                     boolean selfIsAttacker,
                                     Set<String> usedSpirits,
                                     ProcState procState,
                                     CommandBuffer<EntityStore> commandBuffer) {
        applyAbsorbedInternal(store, self, selfRef, otherRef, damage, trigger, selfIsAttacker, usedSpirits,
                procState, commandBuffer);
    }

    private static void applyAbsorbedInternal(Store<EntityStore> store,
                                              Player self,
                                              Ref<EntityStore> selfRef,
                                              Ref<EntityStore> otherRef,
                                              Damage damage,
                                              LoreTrigger trigger,
                                              boolean selfIsAttacker,
                                              Set<String> usedSpirits,
                                              ProcState procState,
                                              CommandBuffer<EntityStore> commandBuffer) {
        if (store == null || self == null || trigger == null) {
            return;
        }
        UUID playerId = self.getUuid();
        if (playerId == null) {
            return;
        }
        boolean ability1Active = isAbility1Active(store, selfRef);
        if (ability1Active) {
            LoreDebug.logKv("absorbed.skip", "reason", "Ability1Active", "player", playerId);
            return;
        }
        int maxLevel = Math.max(1, LoreSocketManager.getConfig().getMaxLevel());
        for (String spiritId : LoreAbsorptionStore.getAbsorbed(playerId)) {
            if (spiritId == null || spiritId.isBlank()) {
                continue;
            }
            String normalized = normalizeSpiritId(spiritId);
            if (usedSpirits != null && usedSpirits.contains(normalized)) {
                continue;
            }
            LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
            if (ability == null || ability.getTrigger() != trigger) {
                continue;
            }
            if (ability1Active && shouldBlockLoreDuringAbility1(ability)) {
                LoreDebug.logKv("absorbed.skip", "reason", "Ability1Active", "effect", ability.getEffectType(),
                        "spirit", spiritId);
                continue;
            }
            if (!tryProc(playerId, spiritId, ability, 0)) {
                continue;
            }
            applyEffect(store, commandBuffer, damage, ability, maxLevel, 0, selfIsAttacker,
                    selfRef, otherRef, playerId, spiritId);
            if (procState != null) {
                procState.triggered = true;
            }
            if (usedSpirits != null) {
                usedSpirits.add(normalized);
            }
        }
    }

    public static boolean applyLoreProcChain(Store<EntityStore> store,
                                             Player self,
                                             Ref<EntityStore> selfRef,
                                             Ref<EntityStore> otherRef,
                                             Damage damage,
                                             LoreSocketData data,
                                             boolean selfIsAttacker,
                                             Set<String> usedSpirits) {
        return applyLoreProcChainInternal(store, self, selfRef, otherRef, damage, data, selfIsAttacker, usedSpirits,
                null);
    }

    public static boolean applyLoreProcChain(Store<EntityStore> store,
                                             Player self,
                                             Ref<EntityStore> selfRef,
                                             Ref<EntityStore> otherRef,
                                             Damage damage,
                                             LoreSocketData data,
                                             boolean selfIsAttacker,
                                             Set<String> usedSpirits,
                                             CommandBuffer<EntityStore> commandBuffer) {
        return applyLoreProcChainInternal(store, self, selfRef, otherRef, damage, data, selfIsAttacker, usedSpirits,
                commandBuffer);
    }

    private static boolean applyLoreProcChainInternal(Store<EntityStore> store,
                                                      Player self,
                                                      Ref<EntityStore> selfRef,
                                                      Ref<EntityStore> otherRef,
                                                      Damage damage,
                                                      LoreSocketData data,
                                                      boolean selfIsAttacker,
                                                      Set<String> usedSpirits,
                                                      CommandBuffer<EntityStore> commandBuffer) {
        boolean changed = false;
        if (data != null) {
            changed = applyLoreSocketsInternal(store, self, selfRef, otherRef, damage, data,
                    LoreTrigger.ON_LORE_PROC, selfIsAttacker, usedSpirits, null, commandBuffer);
        }
        applyAbsorbedInternal(store, self, selfRef, otherRef, damage,
                LoreTrigger.ON_LORE_PROC, selfIsAttacker, usedSpirits, null, commandBuffer);
        return changed;
    }

    private static boolean tryProc(UUID playerId, String spiritId, LoreAbility ability, int feedTier) {
        if (playerId == null || spiritId == null || ability == null) {
            return false;
        }
        double chance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier);
        if (ability.getEffectType() == LoreEffectType.BURN_FINALE) {
            chance = 1.0d;
        }
        if (chance <= 0.0d) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        Long until = COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        long cooldownMs = Math.max(0L, LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier));
        if (cooldownMs > 0L) {
            COOLDOWNS.put(key, now + cooldownMs);
        }
        return true;
    }

    private static boolean tryProcAlwaysOn(UUID playerId, String spiritId, LoreAbility ability, int feedTier) {
        if (playerId == null || spiritId == null || ability == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        Long until = COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        long cooldownMs = Math.max(0L, LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier));
        if (cooldownMs > 0L) {
            COOLDOWNS.put(key, now + cooldownMs);
        }
        return true;
    }

    private static boolean tryProcWithMultiplier(UUID playerId,
                                                 String spiritId,
                                                 LoreAbility ability,
                                                 int feedTier,
                                                 double multiplier) {
        if (playerId == null || spiritId == null || ability == null) {
            return false;
        }
        double baseChance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier);
        if (baseChance <= 0.0d) {
            return false;
        }
        double chance = Math.min(1.0d, baseChance * Math.max(0.0d, multiplier));
        long now = System.currentTimeMillis();
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        Long until = COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        long cooldownMs = Math.max(0L, LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier));
        if (cooldownMs > 0L) {
            COOLDOWNS.put(key, now + cooldownMs);
        }
        return true;
    }

    private static void applyEffect(Store<EntityStore> store,
                                    CommandBuffer<EntityStore> commandBuffer,
                                    Damage damage,
                                    LoreAbility ability,
                                    int level,
                                    int feedTier,
                                    boolean selfIsAttacker,
                                    Ref<EntityStore> attackerOrSelfRef,
                                    Ref<EntityStore> otherRef,
                                    UUID playerId,
                                    String spiritId) {
        applyEffectInternal(store, commandBuffer, damage, ability, level, feedTier, selfIsAttacker,
                attackerOrSelfRef, otherRef, playerId, spiritId, true);
    }

    private static void applyEffectWithSynergy(Store<EntityStore> store,
                                               CommandBuffer<EntityStore> commandBuffer,
                                               Damage damage,
                                               LoreAbility ability,
                                               int level,
                                               int feedTier,
                                               boolean selfIsAttacker,
                                               Ref<EntityStore> attackerOrSelfRef,
                                               Ref<EntityStore> otherRef,
                                               UUID playerId,
                                               String spiritId,
                                               int repeatCount) {
        if (store == null || ability == null) {
            return;
        }
        int repeats = Math.max(1, repeatCount);
        boolean allowRepeats = applyEffectInternal(store, commandBuffer, damage, ability, level, feedTier,
                selfIsAttacker, attackerOrSelfRef, otherRef, playerId, spiritId, true);
        if (!allowRepeats) {
            return;
        }
        for (int i = 1; i < repeats; i++) {
            applyEffectInternal(store, commandBuffer, damage, ability, level, feedTier, selfIsAttacker,
                    attackerOrSelfRef, otherRef, playerId, spiritId, false);
        }
    }

    private static boolean applyEffectInternal(Store<EntityStore> store,
                                               CommandBuffer<EntityStore> commandBuffer,
                                               Damage damage,
                                               LoreAbility ability,
                                               int level,
                                               int feedTier,
                                               boolean selfIsAttacker,
                                               Ref<EntityStore> attackerOrSelfRef,
                                               Ref<EntityStore> otherRef,
                                               UUID playerId,
                                               String spiritId,
                                               boolean primaryProc) {
        if (store == null || ability == null) {
            return false;
        }
        if (primaryProc) {
            logProc(ability, level, feedTier, playerId, spiritId, selfIsAttacker);
        }
        int safeFeedTier = Math.max(0, feedTier);
        double value = Math.max(0.0d, ability.getValueForLevel(level));
        float amount = (float) value;

        Ref<EntityStore> selfRef = attackerOrSelfRef;
        Ref<EntityStore> opponentRef = otherRef;
        Ref<EntityStore> attackerRef = selfIsAttacker ? selfRef : opponentRef;
        Ref<EntityStore> defenderRef = selfIsAttacker ? opponentRef : selfRef;
        if (primaryProc) {
            scheduleReadyNotification(store, selfRef, playerId, spiritId, ability, safeFeedTier);
        }

        boolean isSignatureAbility = selfIsAttacker && isSignatureAbility(ability);
        boolean isVortexstrike = ability.getEffectType() == LoreEffectType.VORTEXSTRIKE;
        boolean allowBuiltInSignature = !LORE_DISABLE_SIGNATURE_ENERGY
                && !isVortexstrike
                && !isWhirlwindSignature(ability);
        boolean ranSignature = false;
        if (primaryProc && selfIsAttacker && isSignatureAbility && allowBuiltInSignature) {
            ranSignature = tryRunBuiltInSignature(store, commandBuffer, ability, attackerRef, playerId, spiritId);
        }
        if (isSignatureAbility && !isVortexstrike) {
            if (primaryProc) {
                applySignatureFallbackVfx(store, attackerRef, defenderRef, ability, safeFeedTier);
            }
            applySignatureFallbackDamage(store, attackerRef, defenderRef, ability, damage, amount,
                    safeFeedTier, selfIsAttacker);
        }

        switch (ability.getEffectType()) {
            case DAMAGE_TARGET -> {
                if (selfIsAttacker && damage != null && opponentRef != null) {
                    damage.setAmount(damage.getAmount() + amount);
                } else if (opponentRef != null) {
                    applyCombatDamage(store, attackerRef, opponentRef, amount, false);
                }
            }
            case DAMAGE_ATTACKER -> {
                if (attackerRef != null) {
                    applyCombatDamage(store, defenderRef, attackerRef, amount, false);
                }
            }
            case HEAL_SELF -> {
                if (selfRef != null) {
                    applyHeal(store, selfRef, amount);
                }
            }
            case HEAL_DEFENDER -> {
                if (defenderRef != null) {
                    applyHeal(store, defenderRef, amount);
                }
            }
            case HEAL_SELF_OVER_TIME -> {
                if (selfRef != null) {
                    applyHealOverTime(store, selfRef, selfRef, amount, false, safeFeedTier, 0.0d);
                }
            }
            case HEAL_AREA -> applyAreaHeal(store, selfRef, amount, safeFeedTier);
            case HEAL_AREA_OVER_TIME -> applyAreaHealOverTime(store, selfRef, amount, safeFeedTier);
            case LIFESTEAL -> {
                if (selfRef != null) {
                    float base = 0f;
                    if (selfIsAttacker && damage != null) {
                        base = damage.getAmount();
                    }
                    double pct = value <= 1.0d ? value : value / 100.0d;
                    float heal = base > 0f ? (float) (base * pct) : amount;
                    if (heal > 0f) {
                        applyHeal(store, selfRef, heal);
                    }
                }
            }
            case APPLY_BURN -> {
                LoreVisuals.tryApplyVisualEffect(store, opponentRef, BURN_EFFECT_IDS);
                markFinaleTarget(BURN_FINALE_MARKS, playerId, spiritId, opponentRef, store, amount, safeFeedTier);
            }
            case APPLY_FREEZE -> {
                if (opponentRef != null) {
                    applyFrozenForDuration(store, opponentRef, resolveStunFreezeDurationMs(value, safeFeedTier));
                }
            }
            case APPLY_SHOCK -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, SHOCK_EFFECT_IDS);
            case APPLY_BLEED -> {
                LoreVisuals.tryApplyVisualEffect(store, opponentRef, BLEED_EFFECT_IDS);
                Ref<EntityStore> bleedSource = selfRef != null ? selfRef : attackerRef;
                applyBleedOverTime(store, bleedSource, opponentRef, amount, safeFeedTier);
                markFinaleTarget(SHRAPNEL_FINALE_MARKS, playerId, spiritId, opponentRef, store, amount, safeFeedTier);
            }
            case APPLY_POISON -> {
                LoreVisuals.tryApplyVisualEffect(store, opponentRef, POISON_EFFECT_IDS);
                markFinaleTarget(CAUSTIC_FINALE_MARKS, playerId, spiritId, opponentRef, store, amount, safeFeedTier);
            }
            case APPLY_SLOW -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, SLOW_EFFECT_IDS);
            case APPLY_WEAKNESS -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, WEAKNESS_EFFECT_IDS);
            case APPLY_BLIND -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, BLIND_EFFECT_IDS);
            case APPLY_ROOT -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, ROOT_EFFECT_IDS);
            case APPLY_STUN -> {
                if (opponentRef != null) {
                    long durationMs = resolveStunFreezeDurationMs(value, safeFeedTier);
                    LoreVisuals.tryApplyTimedVisualEffectOverride(store, opponentRef, durationMs, STUN_EFFECT_IDS);
                }
            }
            case APPLY_FEAR -> LoreVisuals.tryApplyVisualEffect(store, opponentRef, FEAR_EFFECT_IDS);
            case APPLY_HASTE -> LoreVisuals.tryApplyVisualEffect(store, selfRef, HASTE_EFFECT_IDS);
            case APPLY_INVISIBLE -> LoreVisuals.tryApplyVisualEffect(store, selfRef, INVISIBLE_EFFECT_IDS);
            case APPLY_SHIELD -> LoreVisuals.tryApplyVisualEffect(store, selfRef, SHIELD_EFFECT_IDS);
            case DOUBLE_CAST -> {
                float base = selfIsAttacker && damage != null ? damage.getAmount() : amount;
                double scaledValue = LoreAbility.scaleEffectValue(value, safeFeedTier);
                double pct = clampPercentDamage(scaledValue, 0.25d, 1.0d);
                float extra = (float) (base * pct);
                if (selfIsAttacker && damage != null && opponentRef != null) {
                    damage.setAmount(damage.getAmount() + extra);
                } else if (opponentRef != null) {
                    applyCombatDamage(store, attackerRef, opponentRef, extra, false);
                }
            }
            case VORTEXSTRIKE -> {
                applyVortexstrike(store, attackerRef, opponentRef, selfIsAttacker, damage, value, safeFeedTier);
            }
            case MULTI_HIT -> {
                if (opponentRef == null) {
                    return true;
                }
                float base = selfIsAttacker && damage != null ? damage.getAmount() : amount;
                boolean skipRefine = selfIsAttacker && damage != null;
                double pct = clampPercentDamage(value, 0.15d, 0.75d);
                int extraHits = Math.max(1, Math.min(3, calcExtraHits(value) + safeFeedTier));
                float perHit = (float) (base * pct);
                for (int i = 0; i < extraHits; i++) {
                    applyCombatDamage(store, attackerRef, opponentRef, perHit, skipRefine);
                }
            }
            case CRIT_CHARGE -> {
                float base = selfIsAttacker && damage != null ? damage.getAmount() : amount;
                double pct = clampPercentDamage(value, 0.20d, 1.25d);
                float extra = (float) (base * pct);
                if (selfIsAttacker && damage != null && opponentRef != null) {
                    damage.setAmount(damage.getAmount() + extra);
                } else if (opponentRef != null) {
                    applyCombatDamage(store, attackerRef, opponentRef, extra, false);
                }
            }
            case BERSERK -> {
                if (isWhirlwindSignature(ability)) {
                    break;
                }
                LoreVisuals.tryApplyVisualEffect(store, selfRef, HASTE_EFFECT_IDS);
            }
            case CAUSTIC_FINALE -> {
                if (defenderRef == null) {
                    return true;
                }
                if (!hasFinaleMark(CAUSTIC_FINALE_MARKS, playerId, spiritId, defenderRef)) {
                    if (isEntityAlive(store, defenderRef)
                            && hasActiveEffect(store, defenderRef, POISON_EFFECT_IDS)) {
                        markFinaleTarget(CAUSTIC_FINALE_MARKS, playerId, spiritId, defenderRef, store,
                                amount, safeFeedTier);
                    } else {
                        if (DEBUG_LORE_PROCS) {
                            LoreDebug.logKv("finale.skip", "reason", "unmarked", "effect", "CAUSTIC_FINALE");
                        }
                        return true;
                    }
                }
                applyFinaleExplosion(store, attackerRef, defenderRef, amount, safeFeedTier,
                        POISON_EFFECT_IDS, CAUSTIC_FINALE_PARTICLE_IDS, true,
                        playerId, spiritId, true);
                clearFinaleMark(CAUSTIC_FINALE_MARKS, playerId, spiritId, defenderRef);
            }
            case SHRAPNEL_FINALE -> {
                if (defenderRef == null) {
                    return true;
                }
                if (!hasFinaleMark(SHRAPNEL_FINALE_MARKS, playerId, spiritId, defenderRef)) {
                    if (isEntityAlive(store, defenderRef)
                            && hasActiveEffect(store, defenderRef, BLEED_EFFECT_IDS)) {
                        markFinaleTarget(SHRAPNEL_FINALE_MARKS, playerId, spiritId, defenderRef, store,
                                amount, safeFeedTier);
                    } else {
                        if (DEBUG_LORE_PROCS) {
                            LoreDebug.logKv("finale.skip", "reason", "unmarked", "effect", "SHRAPNEL_FINALE");
                        }
                        return true;
                    }
                }
                applyFinaleExplosion(store, attackerRef, defenderRef, amount, safeFeedTier,
                        BLEED_EFFECT_IDS, SHRAPNEL_FINALE_PARTICLE_IDS, true,
                        playerId, spiritId, true);
                clearFinaleMark(SHRAPNEL_FINALE_MARKS, playerId, spiritId, defenderRef);
            }
            case BURN_FINALE -> {
                if (defenderRef == null) {
                    return true;
                }
                if (!hasFinaleMark(BURN_FINALE_MARKS, playerId, spiritId, defenderRef)) {
                    if (isEntityAlive(store, defenderRef)
                            && hasActiveEffect(store, defenderRef, BURN_EFFECT_IDS)) {
                        markFinaleTarget(BURN_FINALE_MARKS, playerId, spiritId, defenderRef, store,
                                amount, safeFeedTier);
                    } else {
                        if (DEBUG_LORE_PROCS) {
                            LoreDebug.logKv("finale.skip", "reason", "unmarked", "effect", "BURN_FINALE");
                        }
                        return true;
                    }
                }
                applyFinaleExplosion(store, attackerRef, defenderRef, amount, safeFeedTier,
                        BURN_EFFECT_IDS, BURN_FINALE_PARTICLE_IDS, false,
                        playerId, spiritId, true);
                clearFinaleMark(BURN_FINALE_MARKS, playerId, spiritId, defenderRef);
            }
            case DRAIN_LIFE -> {
                Ref<EntityStore> drainSource = selfRef != null ? selfRef : attackerRef;
                applyDrainOverTime(store, drainSource, opponentRef, amount, safeFeedTier);
            }
            case CHARGE_ATTACK -> {
                applyChargeAttack(store, attackerRef, opponentRef, selfIsAttacker, damage, amount, value, safeFeedTier);
            }
            case OMNISLASH -> {
                DamageBaseResult baseResult =
                        resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, amount);
                float base = baseResult.amount;
                double scaledValue = LoreAbility.scaleEffectValue(value, safeFeedTier);
                double pct = clampPercentDamage(scaledValue, 0.15d, 0.60d);
                float perHit = (float) (base * pct);
                boolean canTeleport = selfIsAttacker && attackerRef != null;
                int hits = resolveFeedScaledHits(OMNISLASH_BASE_HITS, OMNISLASH_MAX_HITS, safeFeedTier);
                double radius = LoreAbility.scaleRadius(OMNISLASH_RADIUS, safeFeedTier);
                applyOmnislash(store, attackerRef, opponentRef, perHit, canTeleport, baseResult.skipRefine, hits, radius,
                        OMNISLASH_FINAL_MULTIPLIER);
            }
            case OCTASLASH -> applyMultiHitSingleTarget(store, attackerRef, opponentRef, selfIsAttacker, damage, amount,
                    LoreAbility.scaleEffectValue(value, safeFeedTier),
                    resolveFeedScaledHits(OCTASLASH_BASE_HITS, OCTASLASH_MAX_HITS, safeFeedTier), 0.12d, 0.55d,
                    OMNISLASH_SLASH_PARTICLE_IDS, OCTASLASH_FINAL_MULTIPLIER, OCTASLASH_RAMP_PER_HIT);
            case PUMMEL -> {
                int hits = resolveFeedScaledHits(PUMMEL_BASE_HITS, PUMMEL_MAX_HITS, safeFeedTier);
                Ref<EntityStore> trailRef = attackerRef != null ? attackerRef : selfRef;
                if (trailRef != null) {
                    long trailDurationMs = Math.max(250L, (long) hits * MULTIHIT_HIT_DELAY_MS + 200L);
                    LoreVisuals.tryApplyTimedVisualEffectOverride(store, trailRef, trailDurationMs,
                            PUMMEL_TRAIL_EFFECT_IDS);
                    scheduleTrailParticles(store, trailRef, PUMMEL_TRAIL_PARTICLE_IDS, hits, MULTIHIT_HIT_DELAY_MS);
                }
                applyMultiHitSingleTarget(store, attackerRef, opponentRef, selfIsAttacker, damage, amount,
                        value, hits, 0.20d, 0.70d);
            }
            case BLOOD_RUSH -> {
                int hits = resolveFeedScaledHits(BLOOD_RUSH_BASE_HITS, BLOOD_RUSH_MAX_HITS, safeFeedTier);
                Ref<EntityStore> trailRef = attackerRef != null ? attackerRef : selfRef;
                if (trailRef != null) {
                    long trailDurationMs = Math.max(250L, (long) hits * MULTIHIT_HIT_DELAY_MS + 200L);
                    LoreVisuals.tryApplyTimedVisualEffectOverride(store, trailRef, trailDurationMs,
                            BLOOD_RUSH_TRAIL_EFFECT_IDS);
                    scheduleTrailParticles(store, trailRef, BLOOD_RUSH_TRAIL_PARTICLE_IDS, hits, MULTIHIT_HIT_DELAY_MS);
                }
                applyMultiHitSingleTarget(store, attackerRef, opponentRef, selfIsAttacker, damage, amount,
                        value, hits, 0.15d, 0.60d);
            }
            case SUMMON_WOLF_PACK -> trySummonWolfPack(store, attackerOrSelfRef, playerId, spiritId, level, safeFeedTier);
            default -> {
                // no-op
            }
        }
        return !ranSignature;
    }

    private static boolean tryRunBuiltInSignature(Store<EntityStore> store,
                                                  CommandBuffer<EntityStore> commandBuffer,
                                                  LoreAbility ability,
                                                  Ref<EntityStore> attackerRef,
                                                  UUID playerId,
                                                  String spiritId) {
        if (ability == null) {
            return false;
        }
        if (LORE_DISABLE_SIGNATURE_ENERGY) {
            return false;
        }
        LoreEffectType effectType = ability.getEffectType();
        if (effectType == LoreEffectType.CAUSTIC_FINALE
                || effectType == LoreEffectType.SHRAPNEL_FINALE
                || effectType == LoreEffectType.BURN_FINALE) {
            return false;
        }
        String key = ability.getAbilityNameKey();
        if (key == null || key.isBlank()) {
            return false;
        }
        String rootId = SIGNATURE_ROOT_BY_KEY.get(key);
        if (rootId == null || rootId.isBlank()) {
            return false;
        }
        if (store == null || commandBuffer == null || attackerRef == null) {
            logSignatureSkip(rootId, playerId, spiritId, "missing store/commandBuffer/attackerRef");
            return false;
        }
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            logSignatureSkip(rootId, playerId, spiritId, "no attacker player");
            return false;
        }
        PlayerInventoryUtils.HeldItemContext ctx = PlayerInventoryUtils.getHeldItemContext(attacker);
        if (ctx == null || !ctx.isValid()) {
            logSignatureSkip(rootId, playerId, spiritId, "no held item");
            return false;
        }
        ItemStack held = ctx.getItemStack();
        if (held == null || held.isEmpty()) {
            logSignatureSkip(rootId, playerId, spiritId, "empty held item");
            return false;
        }
        InteractionModule module = InteractionModule.get();
        if (module == null) {
            logSignatureSkip(rootId, playerId, spiritId, "interaction module missing");
            return false;
        }
        InteractionManager manager = store.getComponent(attackerRef, module.getInteractionManagerComponent());
        if (manager == null) {
            logSignatureSkip(rootId, playerId, spiritId, "interaction manager missing");
            return false;
        }
        if (hasActiveAbility1Chain(manager)) {
            LoreDebug.log("signature.skip", "reason=Ability1Active");
            return false;
        }
        float originalEnergy = -1f;
        int signatureIndex = -1;
        EntityStatMap statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            signatureIndex = getSignatureEnergyStatIndex(statMap);
            if (signatureIndex >= 0) {
                EntityStatValue value = statMap.get(signatureIndex);
                if (value != null) {
                    originalEnergy = value.get();
                    float max = value.getMax();
                    float target = max > 0f ? max : Math.max(100f, originalEnergy);
                    if (target > originalEnergy) {
                        statMap.addStatValue(signatureIndex, target - originalEnergy);
                    }
                }
            }
        }
        RootInteraction root = RootInteraction.getRootInteractionOrUnknown(rootId);
        if (root == null) {
            logSignatureSkip(rootId, playerId, spiritId, "root interaction missing");
            return false;
        }
        InteractionContext context = InteractionContext.forInteraction(
                manager,
                attackerRef,
                InteractionType.Ability1,
                ctx.getSlot(),
                store
        );
        if (context == null) {
            logSignatureSkip(rootId, playerId, spiritId, "context create failed");
            return false;
        }
        InteractionChain chain = manager.initChain(InteractionType.Ability1, context, root, false);
        if (chain == null) {
            logSignatureSkip(rootId, playerId, spiritId, "initChain failed");
            restoreSignatureEnergy(store, attackerRef, signatureIndex, originalEnergy);
            return false;
        }
        boolean rulesPassed = manager.applyRules(context, chain.getChainData(), InteractionType.Ability1, root);
        if (!rulesPassed) {
            if (!FORCE_SIGNATURE_RULES) {
                logSignatureSkip(rootId, playerId, spiritId, "applyRules failed");
                restoreSignatureEnergy(store, attackerRef, signatureIndex, originalEnergy);
                return false;
            }
            if (DEBUG_LORE_PROCS) {
                LoreDebug.logKv("signature.force", "root", rootId, "player", playerId,
                        "spirit", (spiritId == null ? "unknown" : spiritId));
            }
        }
        manager.queueExecuteChain(chain);
        LoreDebug.logKv("signature.run", "root", rootId, "player", playerId,
                "spirit", (spiritId == null ? "unknown" : spiritId));
        if (signatureIndex >= 0 && originalEnergy >= 0f) {
            scheduleSignatureEnergyRestore(store, attackerRef, signatureIndex, originalEnergy);
        }
        return true;
    }

    private static boolean isSignatureAbility(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        LoreEffectType effectType = ability.getEffectType();
        if (effectType == LoreEffectType.CAUSTIC_FINALE
                || effectType == LoreEffectType.SHRAPNEL_FINALE
                || effectType == LoreEffectType.BURN_FINALE) {
            return false;
        }
        String key = ability.getAbilityNameKey();
        return key != null && !key.isBlank() && SIGNATURE_ROOT_BY_KEY.containsKey(key);
    }

    private static boolean isWhirlwindSignature(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        return "tooltip.lore.signature.whirlwind".equals(ability.getAbilityNameKey());
    }

    private static void applySignatureFallbackVfx(Store<EntityStore> store,
                                                  Ref<EntityStore> attackerRef,
                                                  Ref<EntityStore> defenderRef,
                                                  LoreAbility ability,
                                                  int feedTier) {
        if (store == null || ability == null) {
            return;
        }
        String key = ability.getAbilityNameKey();
        if (key == null || key.isBlank() || !SIGNATURE_ROOT_BY_KEY.containsKey(key)) {
            return;
        }
        Vector3d attackerPos = getPosition(store, attackerRef);
        Vector3d defenderPos = getPosition(store, defenderRef);
        Player attacker = attackerRef == null ? null : store.getComponent(attackerRef, Player.getComponentType());
        switch (key) {
            case "tooltip.lore.signature.groundslam" -> {
                Vector3d pos = attackerPos != null ? attackerPos : defenderPos;
                if (pos == null) {
                    return;
                }
                LoreVisuals.queueSignatureParticles(store, pos, 1.4f, SIGNATURE_GROUNDSLAM_PARTICLE_IDS);
                LoreVisuals.tryApplyTimedVisualEffect(store, attackerRef, SIGNATURE_FALLBACK_EFFECT_MS,
                        SIGNATURE_GROUNDSLAM_EFFECT_IDS);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Mace_T2_Signature_Impact",
                        "SFX_Mace_T2_Signature_Impact_Local",
                        "SFX_Mace_T1_Impact");
            }
            case "tooltip.lore.signature.vortexstrike" -> {
                Vector3d pos = attackerPos != null ? attackerPos : defenderPos;
                if (pos == null) {
                    return;
                }
                LoreVisuals.queueSignatureParticles(store, pos, 1.2f, SIGNATURE_VORTEXSTRIKE_PARTICLE_IDS);
                LoreVisuals.tryApplyTimedVisualEffect(store, attackerRef, SIGNATURE_FALLBACK_EFFECT_MS,
                        SIGNATURE_VORTEXSTRIKE_EFFECT_IDS);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Sword_T2_Signature_Part_1",
                        "SFX_Sword_T2_Signature_Part_1_Local",
                        "SFX_Sword_T2_Signature_Part_2",
                        "SFX_Sword_T2_Signature_Part_2_Local");
            }
            case "tooltip.lore.signature.whirlwind" -> {
                Vector3d pos = attackerPos != null ? attackerPos : defenderPos;
                if (pos == null) {
                    return;
                }
                long durationMs = LoreAbility.scaleDurationMs(LoreAbility.BASE_BERSERK_DURATION_MS, feedTier);
                LoreVisuals.tryRemoveVisualEffectsById(store, attackerRef, SIGNATURE_WHIRLWIND_EFFECT_IDS);
                LoreVisuals.tryRemoveVisualEffectsByContains(store, attackerRef, "whirlwind");
                // Skip built-in whirlwind effect to avoid persistent spin state.
                queueWhirlwindSpinBursts(store, attackerRef, defenderRef, durationMs);
                queueWhirlwindSpinAnimation(store, attackerRef, durationMs);
                queueWhirlwindEffectCleanup(store, attackerRef, durationMs);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Battleaxe_T2_Signature_Swing",
                        "SFX_Battleaxe_T2_Signature_Swing_Local");
            }
            case "tooltip.lore.signature.razorstrike" -> {
                Vector3d pos = defenderPos != null ? defenderPos : attackerPos;
                if (pos == null) {
                    return;
                }
                LoreVisuals.queueSignatureParticles(store, pos, 1.1f, SIGNATURE_RAZORSTRIKE_PARTICLE_IDS);
                LoreVisuals.tryApplyTimedVisualEffect(store, attackerRef, SIGNATURE_FALLBACK_EFFECT_MS,
                        SIGNATURE_RAZORSTRIKE_EFFECT_IDS);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Daggers_T2_Signature_P1",
                        "SFX_Daggers_T2_Signature_P2",
                        "SFX_Daggers_T2_Signature_P3");
            }
            case "tooltip.lore.signature.volley" -> {
                if (attackerPos == null) {
                    return;
                }
                LoreVisuals.queueSignatureParticles(store, attackerPos, 1.0f, SIGNATURE_VOLLEY_PARTICLE_IDS);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Bow_T2_Signature_Shoot",
                        "SFX_Bow_T2_Signature_Shoot_Local",
                        "SFX_Bow_T2_Signature_Nock",
                        "SFX_Bow_T2_Signature_Nock_Local");
            }
            case "tooltip.lore.signature.big_arrow" -> {
                if (attackerPos == null) {
                    return;
                }
                LoreVisuals.queueSignatureParticles(store, attackerPos, 1.0f, SIGNATURE_BIG_ARROW_PARTICLE_IDS);
                LoreVisuals.playSignatureSound(attacker,
                        "SFX_Bow_T2_Signature_Shoot",
                        "SFX_Bow_T2_Signature_Shoot_Local",
                        "SFX_Bow_T2_Draw",
                        "SFX_Bow_T2_Draw_Local");
            }
            default -> {
                // No fallback visuals
            }
        }
    }

    private static void queueWhirlwindSpinBursts(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> defenderRef,
                                                 long durationMs) {
        if (store == null || attackerRef == null) {
            return;
        }
        String resolvedParticleId = LoreVisuals.resolveParticleEffectId(SIGNATURE_WHIRLWIND_PARTICLE_IDS);
        boolean useSlashFallback = false;
        if (resolvedParticleId == null) {
            resolvedParticleId = LoreVisuals.resolveParticleEffectId(OMNISLASH_SLASH_PARTICLE_IDS);
            useSlashFallback = true;
        }
        if (resolvedParticleId == null) {
            return;
        }
        final String particleId = resolvedParticleId;
        final boolean slashFallback = useSlashFallback;
        long safeDuration = Math.max(WHIRLWIND_TICK_MS, durationMs);
        int cycles = (int) Math.max(1, Math.ceil((double) safeDuration / (double) WHIRLWIND_TICK_MS));
        for (int i = 0; i < cycles; i++) {
            long baseDelay = i * WHIRLWIND_TICK_MS;
            for (int j = 0; j < 4; j++) {
                long delay = baseDelay + (j * WHIRLWIND_SPIN_BURST_MS);
                if (slashFallback) {
                    OMNISLASH_SCHEDULER.schedule(
                            () -> queueWhirlwindSlashBurst(store, attackerRef, defenderRef, particleId),
                            delay,
                            TimeUnit.MILLISECONDS);
                } else {
                    OMNISLASH_SCHEDULER.schedule(
                            () -> queueWhirlwindSpinBurst(store, attackerRef, particleId),
                            delay,
                            TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private static void queueWhirlwindSpinAnimation(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    long durationMs) {
        if (store == null || attackerRef == null || durationMs <= 0L) {
            return;
        }
        markWhirlwindActive(store, attackerRef, durationMs);
        long safeDuration = Math.max(WHIRLWIND_TICK_MS, durationMs);
        int cycles = (int) Math.max(1, Math.ceil((double) safeDuration / (double) WHIRLWIND_TICK_MS));
        for (int i = 0; i < cycles; i++) {
            long delay = i * WHIRLWIND_TICK_MS;
            OMNISLASH_SCHEDULER.schedule(
                    () -> queueWhirlwindAnimation(store, attackerRef),
                    delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    private static void queueWhirlwindAnimation(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            playWhirlwindAnimation(store, attackerRef);
            return;
        }
        entityStore.getWorld().execute(() -> playWhirlwindAnimation(store, attackerRef));
    }

    private static void playWhirlwindAnimation(Store<EntityStore> store,
                                               Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null || store.isShutdown()) {
            return;
        }
        if (!isWhirlwindActive(store, attackerRef)) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        int swingIndex = nextWhirlwindSwingIndex(player);
        playSwingAnimation(store, attackerRef, swingIndex);
    }

    private static void queueWhirlwindEffectCleanup(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    long durationMs) {
        if (store == null || attackerRef == null || durationMs <= 0L) {
            return;
        }
        long delay = Math.max(200L, durationMs + 100L);
        OMNISLASH_SCHEDULER.schedule(
                () -> queueWhirlwindCleanup(store, attackerRef),
                delay,
                TimeUnit.MILLISECONDS);
    }

    private static void queueWhirlwindCleanup(Store<EntityStore> store,
                                              Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            stopWhirlwindAnimationSlots(attackerRef, store);
            resetWhirlwindIdle(store, attackerRef);
            LoreVisuals.tryRemoveVisualEffectsById(store, attackerRef, SIGNATURE_WHIRLWIND_EFFECT_IDS);
            LoreVisuals.tryRemoveVisualEffectsByContains(store, attackerRef, "whirlwind");
            LoreVisuals.logActiveEffectIds(store, attackerRef, "whirlwind-cleanup");
            clearWhirlwindActive(store, attackerRef);
            return;
        }
        entityStore.getWorld().execute(
                () -> {
                    stopWhirlwindAnimationSlots(attackerRef, store);
                    resetWhirlwindIdle(store, attackerRef);
                    LoreVisuals.tryRemoveVisualEffectsById(store, attackerRef, SIGNATURE_WHIRLWIND_EFFECT_IDS);
                    LoreVisuals.tryRemoveVisualEffectsByContains(store, attackerRef, "whirlwind");
                    LoreVisuals.logActiveEffectIds(store, attackerRef, "whirlwind-cleanup");
                    clearWhirlwindActive(store, attackerRef);
                });
    }

    private static void stopWhirlwindAnimationSlots(Ref<EntityStore> attackerRef, Store<EntityStore> store) {
        if (store == null || attackerRef == null) {
            return;
        }
        try {
            AnimationUtils.stopAnimation(attackerRef, AnimationSlot.Action, true, store);
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            AnimationUtils.stopAnimation(attackerRef, AnimationSlot.Status, true, store);
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            AnimationUtils.stopAnimation(attackerRef, AnimationSlot.Movement, true, store);
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            AnimationUtils.stopAnimation(attackerRef, AnimationSlot.Emote, true, store);
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            AnimationUtils.stopAnimation(attackerRef, AnimationSlot.Face, true, store);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static void markWhirlwindActive(Store<EntityStore> store,
                                            Ref<EntityStore> attackerRef,
                                            long durationMs) {
        if (store == null || attackerRef == null || durationMs <= 0L) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return;
        }
        long until = System.currentTimeMillis() + Math.max(200L, durationMs + 200L);
        WHIRLWIND_ACTIVE_UNTIL.put(playerId, until);
    }

    private static boolean isWhirlwindActive(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null) {
            return false;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return false;
        }
        Long until = WHIRLWIND_ACTIVE_UNTIL.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            WHIRLWIND_ACTIVE_UNTIL.remove(playerId);
            return false;
        }
        return true;
    }

    private static void clearWhirlwindActive(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        UUID playerId = player.getUuid();
        if (playerId != null) {
            WHIRLWIND_ACTIVE_UNTIL.remove(playerId);
            WHIRLWIND_SWING_INDEX.remove(playerId);
        }
    }

    private static int nextWhirlwindSwingIndex(Player player) {
        if (player == null) {
            return 0;
        }
        UUID playerId = player.getUuid();
        if (playerId == null) {
            return 0;
        }
        int next = WHIRLWIND_SWING_INDEX.getOrDefault(playerId, 0) + 1;
        WHIRLWIND_SWING_INDEX.put(playerId, next);
        return next;
    }

    private static void resetWhirlwindIdle(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null || store.isShutdown()) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        String animSet = resolvePlayerAnimationSet(player);
        String idleAnim = resolveAnimationIdByContains(animSet, "idle");
        if ((idleAnim == null || idleAnim.isBlank()) && !"Battleaxe".equalsIgnoreCase(animSet)) {
            String battleaxeIdle = resolveAnimationIdByContains("Battleaxe", "idle");
            if (battleaxeIdle != null && !battleaxeIdle.isBlank()) {
                animSet = "Battleaxe";
                idleAnim = battleaxeIdle;
            }
        }
        if (idleAnim == null || idleAnim.isBlank()) {
            return;
        }
        try {
            AnimationUtils.playAnimation(attackerRef, AnimationSlot.Action, animSet, idleAnim, false, store);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static void queueWhirlwindSpinBurst(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef,
                                                String particleId) {
        if (store == null || attackerRef == null || particleId == null || particleId.isBlank()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            applyWhirlwindSpinBurst(store, attackerRef, particleId);
            return;
        }
        entityStore.getWorld().execute(() -> applyWhirlwindSpinBurst(store, attackerRef, particleId));
    }

    private static void applyWhirlwindSpinBurst(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef,
                                                String particleId) {
        if (store == null || attackerRef == null || particleId == null || particleId.isBlank() || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, attackerRef)) {
            return;
        }
        Vector3d pos = getPosition(store, attackerRef);
        if (pos == null) {
            return;
        }
        Vector3d spawnPos = pos.clone();
        spawnPos.add(0.0d, 0.6d, 0.0d);
        LoreVisuals.spawnScaledParticle(store, particleId, spawnPos, 1.15f);
    }

    private static void queueWhirlwindSlashBurst(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> defenderRef,
                                                 String particleId) {
        if (store == null || attackerRef == null || particleId == null || particleId.isBlank()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            LoreVisuals.spawnSlashParticles(store, attackerRef, defenderRef, particleId, 1.05f);
            return;
        }
        entityStore.getWorld().execute(() ->
                LoreVisuals.spawnSlashParticles(store, attackerRef, defenderRef, particleId, 1.05f));
    }

    private static void applySignatureFallbackDamage(Store<EntityStore> store,
                                                     Ref<EntityStore> attackerRef,
                                                     Ref<EntityStore> defenderRef,
                                                     LoreAbility ability,
                                                     Damage damage,
                                                     float fallbackAmount,
                                                     int feedTier,
                                                     boolean selfIsAttacker) {
        if (store == null || ability == null) {
            return;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return;
        }
        switch (type) {
            case APPLY_STUN -> {
                DamageBaseResult baseResult =
                        resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
                float base = baseResult.amount;
                if (base <= 0f) {
                    return;
                }
                double pct = clampPercentDamage(LoreAbility.scaleEffectValue(SIGNATURE_GROUNDSLAM_PCT, feedTier), 0.20d, 0.85d);
                float perTarget = (float) (base * pct);
                double radius = LoreAbility.scaleRadius(SIGNATURE_GROUNDSLAM_RADIUS, feedTier);
                applySignatureAoEDamage(store, attackerRef, defenderRef, perTarget, radius, baseResult.skipRefine);
            }
            case BERSERK -> {
                if (isWhirlwindSignature(ability)) {
                    applyWhirlwindDamageOverTime(store, attackerRef, defenderRef, damage, fallbackAmount, feedTier, selfIsAttacker);
                    return;
                }
                DamageBaseResult baseResult =
                        resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
                float base = baseResult.amount;
                if (base <= 0f) {
                    return;
                }
                double pct = clampPercentDamage(LoreAbility.scaleEffectValue(SIGNATURE_WHIRLWIND_PCT, feedTier), 0.15d, 0.70d);
                float perTarget = (float) (base * pct);
                double radius = LoreAbility.scaleRadius(SIGNATURE_WHIRLWIND_RADIUS, feedTier);
                applySignatureAoEDamage(store, attackerRef, defenderRef, perTarget, radius, baseResult.skipRefine);
            }
            case APPLY_BLEED -> {
                if (defenderRef == null) {
                    return;
                }
                DamageBaseResult baseResult =
                        resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
                float base = baseResult.amount;
                if (base <= 0f) {
                    return;
                }
                double pct = clampPercentDamage(LoreAbility.scaleEffectValue(SIGNATURE_RAZORSTRIKE_PCT, feedTier), 0.20d, 0.80d);
                float perHit = (float) (base * pct);
                applyCombatDamage(store, attackerRef, defenderRef, perHit, baseResult.skipRefine);
            }
            default -> {
                // Other signature types already deal damage through their main effect.
            }
        }
    }

    private static void applyWhirlwindDamageOverTime(Store<EntityStore> store,
                                                     Ref<EntityStore> attackerRef,
                                                     Ref<EntityStore> defenderRef,
                                                     Damage damage,
                                                     float fallbackAmount,
                                                     int feedTier,
                                                     boolean selfIsAttacker) {
        if (store == null || attackerRef == null) {
            return;
        }
        DamageBaseResult baseResult =
                resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
        float base = baseResult.amount;
        if (base <= 0f) {
            return;
        }
        double pct = clampPercentDamage(LoreAbility.scaleEffectValue(fallbackAmount, feedTier), 0.15d, 0.85d);
        float perTarget = (float) (base * pct);
        double radius = LoreAbility.scaleRadius(SIGNATURE_WHIRLWIND_RADIUS, feedTier);
        long durationMs = LoreAbility.scaleDurationMs(LoreAbility.BASE_BERSERK_DURATION_MS, feedTier);
        int ticks = (int) Math.max(1, Math.ceil((double) durationMs / (double) WHIRLWIND_TICK_MS));
        for (int i = 0; i < ticks; i++) {
            long delay = i * WHIRLWIND_TICK_MS;
            OMNISLASH_SCHEDULER.schedule(
                    () -> queueWhirlwindDamageTick(store, attackerRef, perTarget, radius, baseResult.skipRefine),
                    delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    private static void queueWhirlwindDamageTick(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 float perTarget,
                                                 double radius,
                                                 boolean skipRefine) {
        if (store == null || attackerRef == null || perTarget <= 0f || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            applyWhirlwindDamageTick(store, attackerRef, perTarget, radius, skipRefine);
            return;
        }
        entityStore.getWorld().execute(() -> applyWhirlwindDamageTick(store, attackerRef, perTarget, radius, skipRefine));
    }

    private static void applyWhirlwindDamageTick(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 float perTarget,
                                                 double radius,
                                                 boolean skipRefine) {
        if (store == null || attackerRef == null || perTarget <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, attackerRef)) {
            return;
        }
        applySignatureAoEDamage(store, attackerRef, null, perTarget, radius, skipRefine);
    }

    private static float resolveSignatureDamageBase(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    boolean selfIsAttacker,
                                                    Damage damage,
                                                    float fallbackAmount) {
        return resolveLoreDamageBase(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
    }

    private static float resolveWeaponDamageBaseForLoreHits(Store<EntityStore> store,
                                                            Ref<EntityStore> attackerRef,
                                                            boolean selfIsAttacker,
                                                            Damage damage,
                                                            float fallbackAmount) {
        if (selfIsAttacker && damage != null) {
            float eventBase = resolveDamageBase(true, damage, fallbackAmount);
            if (eventBase > 0f) {
                return eventBase;
            }
        }
        return resolveWeaponRefinedDamage(store, attackerRef, fallbackAmount);
    }

    private static DamageBaseResult resolveLoreDamageBaseResult(Store<EntityStore> store,
                                                                Ref<EntityStore> attackerRef,
                                                                boolean selfIsAttacker,
                                                                Damage damage,
                                                                float fallbackAmount) {
        if (selfIsAttacker) {
            float rawWeapon = resolveWeaponBaseDamage(store, attackerRef);
            if (rawWeapon > 0f) {
                return new DamageBaseResult(rawWeapon, false);
            }
            float eventBase = resolveDamageBase(true, damage, fallbackAmount);
            if (eventBase > 0f) {
                return new DamageBaseResult(eventBase, true);
            }
            return new DamageBaseResult(fallbackAmount, true);
        }
        float eventBase = resolveDamageBase(false, damage, fallbackAmount);
        if (eventBase > 0f) {
            return new DamageBaseResult(eventBase, true);
        }
        return new DamageBaseResult(fallbackAmount, true);
    }

    private static void applySignatureAoEDamage(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef,
                                                Ref<EntityStore> targetRef,
                                                float perTarget,
                                                double radius,
                                                boolean skipRefine) {
        if (store == null || perTarget <= 0f) {
            return;
        }
        double radiusSq = radius * radius;
        List<Ref<EntityStore>> targets = collectOmnislashTargets(store, attackerRef, targetRef, radius, radiusSq, "signature");
        if (targets.isEmpty()) {
            return;
        }
        for (Ref<EntityStore> target : targets) {
            applyCombatDamage(store, attackerRef, target, perTarget, skipRefine);
        }
    }

    private static void applyVortexstrike(Store<EntityStore> store,
                                          Ref<EntityStore> attackerRef,
                                          Ref<EntityStore> targetRef,
                                          boolean selfIsAttacker,
                                          Damage damage,
                                          double value,
                                          int feedTier) {
        if (store == null || attackerRef == null) {
            return;
        }
        applyVortexstrikeVfx(store, attackerRef, targetRef);
        if (targetRef != null) {
            double lungeDistance = LoreAbility.scaleRadius(VORTEXSTRIKE_LUNGE_DISTANCE, feedTier);
            lungeTowardTarget(store, attackerRef, targetRef, lungeDistance);
        }
        DamageBaseResult baseResult =
                resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, (float) value);
        float base = baseResult.amount;
        if (base <= 0f) {
            return;
        }
        double pct = clampPercentDamage(LoreAbility.scaleEffectValue(value, feedTier), 0.20d, 0.85d);
        float perTarget = (float) (base * pct);
        double radius = LoreAbility.scaleRadius(LoreAbility.BASE_VORTEXSTRIKE_RADIUS, feedTier);
        applySignatureAoEDamage(store, attackerRef, targetRef, perTarget, radius, baseResult.skipRefine);
    }

    private static void applyVortexstrikeVfx(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> targetRef) {
        if (store == null || attackerRef == null) {
            return;
        }
        Vector3d attackerPos = getPosition(store, attackerRef);
        Vector3d targetPos = getPosition(store, targetRef);
        Vector3d pos = targetPos != null ? targetPos : attackerPos;
        if (pos != null) {
            LoreVisuals.queueSignatureParticles(store, pos, 1.2f, SIGNATURE_VORTEXSTRIKE_PARTICLE_IDS);
        }
        LoreVisuals.tryApplyTimedVisualEffect(store, attackerRef, SIGNATURE_FALLBACK_EFFECT_MS,
                SIGNATURE_VORTEXSTRIKE_EFFECT_IDS);
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        LoreVisuals.playSignatureSound(attacker,
                "SFX_Sword_T2_Signature_Part_1",
                "SFX_Sword_T2_Signature_Part_1_Local",
                "SFX_Sword_T2_Signature_Part_2",
                "SFX_Sword_T2_Signature_Part_2_Local");
    }

    private static void logSignatureSkip(String rootId,
                                         UUID playerId,
                                         String spiritId,
                                         String reason) {
        LoreDebug.logKv("signature.skip",
                "reason", reason,
                "root", rootId,
                "player", (playerId == null ? "unknown" : playerId),
                "spirit", (spiritId == null ? "unknown" : spiritId));
    }

    private static void ensureInteractionCommandBuffer(InteractionManager manager,
                                                       CommandBuffer<EntityStore> commandBuffer) {
        if (manager == null || commandBuffer == null) {
            return;
        }
        Field field = interactionManagerCommandBufferField;
        if (field == null) {
            synchronized (INTERACTION_BUFFER_LOCK) {
                field = interactionManagerCommandBufferField;
                if (field == null) {
                    try {
                        field = InteractionManager.class.getDeclaredField("commandBuffer");
                        field.setAccessible(true);
                        interactionManagerCommandBufferField = field;
                    } catch (Throwable ignored) {
                        return;
                    }
                }
            }
        }
        try {
            field.set(manager, commandBuffer);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static boolean hasActiveAbility1Chain(InteractionManager manager) {
        if (manager == null) {
            return false;
        }
        var chains = manager.getChains();
        if (chains == null || chains.isEmpty()) {
            return false;
        }
        for (var chain : chains.values()) {
            if (chain == null) {
                continue;
            }
            InteractionType type = chain.getType();
            InteractionType baseType = chain.getBaseType();
            if (type == InteractionType.Ability1 || baseType == InteractionType.Ability1) {
                return true;
            }
        }
        return false;
    }

    private static void scheduleSignatureEnergyRestore(Store<EntityStore> store,
                                                       Ref<EntityStore> attackerRef,
                                                       int statIndex,
                                                       float originalEnergy) {
        if (store == null || attackerRef == null || statIndex < 0 || originalEnergy < 0f) {
            return;
        }
        OMNISLASH_SCHEDULER.schedule(
                () -> queueSignatureEnergyRestore(store, attackerRef, statIndex, originalEnergy),
                SIGNATURE_RESTORE_DELAY_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private static void queueSignatureEnergyRestore(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    int statIndex,
                                                    float originalEnergy) {
        if (store == null || attackerRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> restoreSignatureEnergy(store, attackerRef, statIndex, originalEnergy));
    }

    private static void restoreSignatureEnergy(Store<EntityStore> store,
                                               Ref<EntityStore> attackerRef,
                                               int statIndex,
                                               float originalEnergy) {
        if (store == null || attackerRef == null || statIndex < 0 || originalEnergy < 0f) {
            return;
        }
        EntityStatMap statMap;
        try {
            statMap = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        } catch (IllegalStateException ignored) {
            return;
        }
        if (statMap == null) {
            return;
        }
        EntityStatValue value = statMap.get(statIndex);
        if (value == null) {
            return;
        }
        float current = value.get();
        if (current < originalEnergy) {
            statMap.addStatValue(statIndex, originalEnergy - current);
        }
    }

    private static void logProc(LoreAbility ability,
                                int level,
                                int feedTier,
                                UUID playerId,
                                String spiritId,
                                boolean selfIsAttacker) {
        if (!DEBUG_LORE_PROCS || ability == null || playerId == null) {
            return;
        }
        double chance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier) * 100.0d;
        long cooldownMs = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier);
        String trigger = ability.getTrigger() == null ? "UNKNOWN" : ability.getTrigger().name();
        String effect = ability.getEffectType() == null ? "UNKNOWN" : ability.getEffectType().name();
        double value = ability.getValueForLevel(Math.max(0, level));
        String side = selfIsAttacker ? "attacker" : "defender";
        LoreDebug.logKv("proc",
                "player", playerId,
                "spirit", (spiritId == null ? "unknown" : spiritId),
                "trigger", trigger,
                "effect", effect,
                "level", Math.max(0, level),
                "feedTier", Math.max(0, feedTier),
                "value", String.format(Locale.ROOT, "%.3f", value),
                "chancePct", String.format(Locale.ROOT, "%.2f", chance),
                "cooldownMs", cooldownMs,
                "side", side);
    }

    private static void scheduleReadyNotification(Store<EntityStore> store,
                                                  Ref<EntityStore> playerRef,
                                                  UUID playerId,
                                                  String spiritId,
                                                  LoreAbility ability,
                                                  int feedTier) {
        if (store == null || playerRef == null || playerId == null || spiritId == null || ability == null) {
            return;
        }
        long cooldownMs = LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier);
        if (cooldownMs < READY_NOTIFICATION_MIN_COOLDOWN_MS) {
            return;
        }
        long readyAt = System.currentTimeMillis() + cooldownMs;
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        long updated = READY_NOTIFICATION_AT.merge(key, readyAt, Math::max);
        if (updated != readyAt) {
            readyAt = updated;
        }
        long delay = Math.max(1L, readyAt - System.currentTimeMillis());
        long finalReadyAt = readyAt;
        OMNISLASH_SCHEDULER.schedule(
                () -> queueReadyNotification(store, playerRef, playerId, spiritId, ability, feedTier, finalReadyAt),
                delay,
                TimeUnit.MILLISECONDS
        );
    }

    private static void queueReadyNotification(Store<EntityStore> store,
                                               Ref<EntityStore> playerRef,
                                               UUID playerId,
                                               String spiritId,
                                               LoreAbility ability,
                                               int feedTier,
                                               long expectedReadyAt) {
        if (store == null || playerRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> sendReadyNotification(store, playerRef, playerId, spiritId,
                ability, feedTier, expectedReadyAt));
    }

    private static void sendReadyNotification(Store<EntityStore> store,
                                              Ref<EntityStore> playerRef,
                                              UUID playerId,
                                              String spiritId,
                                              LoreAbility ability,
                                              int feedTier,
                                              long expectedReadyAt) {
        if (store == null || playerRef == null || playerId == null || spiritId == null || ability == null) {
            return;
        }
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        Long scheduledAt = READY_NOTIFICATION_AT.get(key);
        if (scheduledAt == null || scheduledAt.longValue() != expectedReadyAt) {
            return;
        }
        long now = System.currentTimeMillis();
        Long cooldownUntil = COOLDOWNS.get(key);
        if (cooldownUntil != null && now < cooldownUntil) {
            long delay = Math.max(1L, cooldownUntil - now);
            READY_NOTIFICATION_AT.put(key, cooldownUntil);
            OMNISLASH_SCHEDULER.schedule(
                    () -> queueReadyNotification(store, playerRef, playerId, spiritId, ability, feedTier, cooldownUntil),
                    delay,
                    TimeUnit.MILLISECONDS
            );
            return;
        }
        READY_NOTIFICATION_AT.remove(key);
        Player player;
        try {
            player = store.getComponent(playerRef, Player.getComponentType());
        } catch (IllegalStateException ignored) {
            return;
        }
        if (player == null) {
            return;
        }
        PlayerRef resolved;
        try {
            resolved = player.getPlayerRef();
        } catch (Throwable ignored) {
            return;
        }
        if (resolved == null) {
            return;
        }
        String lang = resolved.getLanguage();
        if (lang == null || lang.isBlank()) {
            lang = "en-US";
        }
        String title = LangLoader.getTranslationForLanguage("notification.lore_ready.title", lang);
        if (title == null || title.isBlank() || title.equals("notification.lore_ready.title")) {
            title = "Lore Ready";
        }
        String bodyTemplate = LangLoader.getTranslationForLanguage("notification.lore_ready.body", lang);
        if (bodyTemplate == null || bodyTemplate.isBlank() || bodyTemplate.equals("notification.lore_ready.body")) {
            bodyTemplate = "{0} is ready.";
        }
        String abilityName = resolveAbilityDisplayName(ability, lang);
        if (abilityName == null || abilityName.isBlank()) {
            abilityName = "Lore Ability";
        }
        String body = bodyTemplate.replace("{0}", abilityName);
        var handler = resolved.getPacketHandler();
        if (handler == null) {
            return;
        }
        NotificationUtil.sendNotification(handler, Message.raw(title), Message.raw(body), NotificationStyle.Success);
    }

    private static String resolveAbilityDisplayName(LoreAbility ability, String langCode) {
        if (ability == null) {
            return null;
        }
        String resolved = ability.resolveAbilityName(langCode);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        LoreEffectType effect = ability.getEffectType();
        if (effect == null) {
            return null;
        }
        String raw = effect.name();
        return toTitleCase(raw.replace('_', ' '));
    }

    private static String toTitleCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String[] parts = raw.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            out.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                out.append(lower.substring(1));
            }
            if (i + 1 < parts.length) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private static void applyMultiHitSingleTarget(Store<EntityStore> store,
                                                  Ref<EntityStore> attackerRef,
                                                  Ref<EntityStore> targetRef,
                                                  boolean selfIsAttacker,
                                                  Damage damage,
                                                  float fallbackAmount,
                                                  double value,
                                                  int hits,
                                                  double minPct,
                                                  double maxPct) {
        applyMultiHitSingleTarget(store, attackerRef, targetRef, selfIsAttacker, damage, fallbackAmount, value, hits,
                minPct, maxPct, null, 1.0f, 0.0d);
    }

    private static void applyMultiHitSingleTarget(Store<EntityStore> store,
                                                  Ref<EntityStore> attackerRef,
                                                  Ref<EntityStore> targetRef,
                                                  boolean selfIsAttacker,
                                                  Damage damage,
                                                  float fallbackAmount,
                                                  double value,
                                                  int hits,
                                                  double minPct,
                                                  double maxPct,
                                                  String[] particleIds,
                                                  float finalMultiplier,
                                                  double rampPerHit) {
        if (store == null || targetRef == null || hits <= 0) {
            return;
        }
        DamageBaseResult baseResult =
                resolveLoreDamageBaseResult(store, attackerRef, selfIsAttacker, damage, fallbackAmount);
        float base = baseResult.amount;
        if (base <= 0f) {
            return;
        }
        double pct = clampPercentDamage(value, minPct, maxPct);
        float perHit = (float) (base * pct);
        if (perHit <= 0f) {
            return;
        }
        String particleId = LoreVisuals.resolveParticleEffectId(particleIds);
        if (hits == 1) {
            applySingleTargetHit(store, attackerRef, targetRef, perHit, 0, baseResult.skipRefine, particleId, 1, finalMultiplier,
                    rampPerHit);
            return;
        }
        scheduleSingleTargetHits(store, attackerRef, targetRef, perHit, hits, MULTIHIT_HIT_DELAY_MS, baseResult.skipRefine,
                particleId, finalMultiplier, rampPerHit);
    }

    private static void applyChargeAttack(Store<EntityStore> store,
                                          Ref<EntityStore> attackerRef,
                                          Ref<EntityStore> targetRef,
                                          boolean selfIsAttacker,
                                          Damage damage,
                                          float fallbackAmount,
                                          double value,
                                          int feedTier) {
        if (store == null || targetRef == null) {
            return;
        }
        float base = resolveDamageBase(selfIsAttacker, damage, fallbackAmount);
        if (base <= 0f) {
            return;
        }
        float chargeBase = resolveChargeAttackBase(store, attackerRef, base);
        double pct = clampPercentDamage(value, 0.25d, 0.85d);
        float perHit = (float) (chargeBase * pct);
        if (perHit <= 0f) {
            return;
        }
        int hits = resolveFeedScaledHits(CHARGE_ATTACK_BASE_HITS, CHARGE_ATTACK_MAX_HITS, feedTier);
        if (hits <= 1) {
            applyChargeAttackHit(store, attackerRef, targetRef, perHit, 0);
            return;
        }
        scheduleChargeAttackHits(store, attackerRef, targetRef, perHit, hits, CHARGE_ATTACK_HIT_DELAY_MS);
    }

    private static int resolveFeedScaledHits(int baseHits, int maxHits, int feedTier) {
        int base = Math.max(1, baseHits);
        int max = Math.max(base, maxHits);
        int tier = Math.max(0, feedTier);
        int hits = base + tier;
        return Math.max(1, Math.min(max, hits));
    }

    private static int resolveSynergyHits(List<PendingLoreProc> pending) {
        if (pending == null || pending.isEmpty()) {
            return 1;
        }
        int max = 1;
        for (PendingLoreProc proc : pending) {
            int hits = resolveSynergyHitsForProc(proc);
            if (hits > max) {
                max = hits;
            }
        }
        return max;
    }

    private static int resolveSynergyHitsForProc(PendingLoreProc proc) {
        if (proc == null || proc.ability == null) {
            return 1;
        }
        LoreEffectType type = proc.ability.getEffectType();
        if (type == null) {
            return 1;
        }
        int feedTier = Math.max(0, proc.feedTier);
        switch (type) {
            case MULTI_HIT -> {
                double value = Math.max(0.0d, proc.ability.getValueForLevel(proc.level));
                int extraHits = Math.max(1, Math.min(3, calcExtraHits(value) + feedTier));
                return Math.max(1, 1 + extraHits);
            }
            case OMNISLASH -> {
                return resolveFeedScaledHits(OMNISLASH_BASE_HITS, OMNISLASH_MAX_HITS, feedTier);
            }
            case OCTASLASH -> {
                return resolveFeedScaledHits(OCTASLASH_BASE_HITS, OCTASLASH_MAX_HITS, feedTier);
            }
            case PUMMEL -> {
                return resolveFeedScaledHits(PUMMEL_BASE_HITS, PUMMEL_MAX_HITS, feedTier);
            }
            case BLOOD_RUSH -> {
                return resolveFeedScaledHits(BLOOD_RUSH_BASE_HITS, BLOOD_RUSH_MAX_HITS, feedTier);
            }
            case CHARGE_ATTACK -> {
                return resolveFeedScaledHits(CHARGE_ATTACK_BASE_HITS, CHARGE_ATTACK_MAX_HITS, feedTier);
            }
            default -> {
                return 1;
            }
        }
    }

    private static long estimateBatchDurationMs(List<PendingLoreProc> primary,
                                                List<PendingLoreProc> secondary,
                                                List<PendingLoreProc> tertiary,
                                                List<PendingLoreProc> quaternary) {
        long max = 0L;
        max = Math.max(max, estimateListDuration(primary));
        max = Math.max(max, estimateListDuration(secondary));
        max = Math.max(max, estimateListDuration(tertiary));
        max = Math.max(max, estimateListDuration(quaternary));
        if (max <= 0L) {
            return LORE_QUEUE_MIN_DELAY_MS;
        }
        return Math.max(LORE_QUEUE_MIN_DELAY_MS, max);
    }

    private static long estimateListDuration(List<PendingLoreProc> procs) {
        if (procs == null || procs.isEmpty()) {
            return 0L;
        }
        long max = 0L;
        for (PendingLoreProc proc : procs) {
            max = Math.max(max, estimateProcDurationMs(proc));
        }
        return max;
    }

    private static long estimateProcDurationMs(PendingLoreProc proc) {
        if (proc == null || proc.ability == null) {
            return 0L;
        }
        LoreEffectType type = proc.ability.getEffectType();
        if (type == null) {
            return 0L;
        }
        int hits = resolveSynergyHitsForProc(proc);
        switch (type) {
            case OMNISLASH -> {
                return (long) hits * OMNISLASH_HIT_DELAY_MS + 200L;
            }
            case OCTASLASH, BLOOD_RUSH, PUMMEL -> {
                return (long) hits * MULTIHIT_HIT_DELAY_MS + 150L;
            }
            case CHARGE_ATTACK -> {
                return (long) hits * CHARGE_ATTACK_HIT_DELAY_MS + 150L;
            }
            case MULTI_HIT -> {
                return (long) hits * MULTIHIT_HIT_DELAY_MS + 120L;
            }
            case SUMMON_WOLF_PACK -> {
                return 400L;
            }
            case CAUSTIC_FINALE, SHRAPNEL_FINALE, BURN_FINALE -> {
                return 350L;
            }
            default -> {
                return 120L;
            }
        }
    }

    private static boolean isCoreEffect(PendingLoreProc proc) {
        if (proc == null || proc.ability == null) {
            return false;
        }
        LoreEffectType type = proc.ability.getEffectType();
        if (type == null) {
            return false;
        }
        if (!isCoreEffectType(type)) {
            return false;
        }
        if (type == LoreEffectType.SUMMON_WOLF_PACK) {
            return true;
        }
        return isCoreColorForSpirit(proc.spiritId);
    }

    private static boolean isCoreEffectType(LoreEffectType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case OMNISLASH, OCTASLASH, CHARGE_ATTACK, SUMMON_WOLF_PACK,
                    CAUSTIC_FINALE, SHRAPNEL_FINALE, BURN_FINALE -> true;
            default -> false;
        };
    }

    private static boolean isCoreColorForSpirit(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return false;
        }
        return LoreGemRegistry.isCoreSpirit(spiritId);
    }

    private static boolean isAugmentEffect(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        if (isGroundslamAugment(ability)) {
            return true;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case MULTI_HIT, BLOOD_RUSH, PUMMEL -> true;
            default -> false;
        };
    }

    private static boolean isAlwaysOnAugment(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case MULTI_HIT, BLOOD_RUSH, PUMMEL -> true;
            default -> false;
        };
    }

    private static boolean isGroundslamAugment(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        String key = ability.getAbilityNameKey();
        if (key != null && key.equalsIgnoreCase("tooltip.lore.signature.groundslam")) {
            return true;
        }
        String fallback = ability.getAbilityNameFallback();
        return fallback != null && fallback.toLowerCase(Locale.ROOT).contains("groundslam");
    }

    private static List<PendingLoreProc> collectAlwaysOnAugments(ProcCollectResult result,
                                                                 List<PendingLoreProc> existing) {
        List<PendingLoreProc> forced = new ArrayList<>();
        if (result == null || result.eligible == null || result.eligible.isEmpty()) {
            return forced;
        }
        for (PendingLoreProc candidate : result.eligible) {
            if (candidate == null || candidate.ability == null) {
                continue;
            }
            if (!isAlwaysOnAugment(candidate.ability)) {
                continue;
            }
            if (containsSocketIndex(existing, candidate.socketIndex)
                    || containsSocketIndex(forced, candidate.socketIndex)) {
                continue;
            }
            if (!tryProcAlwaysOn(result.playerId, candidate.spiritId, candidate.ability, candidate.feedTier)) {
                continue;
            }
            forced.add(candidate);
        }
        return forced;
    }

    private static boolean containsSocketIndex(List<PendingLoreProc> list, int socketIndex) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (PendingLoreProc proc : list) {
            if (proc != null && proc.socketIndex == socketIndex) {
                return true;
            }
        }
        return false;
    }

    private static PendingLoreProc pickGroundslamAugment(List<PendingLoreProc> eligible) {
        if (eligible == null || eligible.isEmpty()) {
            return null;
        }
        PendingLoreProc best = null;
        for (PendingLoreProc proc : eligible) {
            if (proc == null || proc.ability == null || !isGroundslamAugment(proc.ability)) {
                continue;
            }
            if (best == null) {
                best = proc;
                continue;
            }
            if (proc.level > best.level) {
                best = proc;
                continue;
            }
            if (proc.level == best.level && proc.feedTier > best.feedTier) {
                best = proc;
            }
        }
        return best;
    }

    private static int resolveCoreHitCount(LoreAbility ability, int feedTier) {
        if (ability == null) {
            return 1;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return 1;
        }
        return switch (type) {
            case OMNISLASH -> resolveFeedScaledHits(OMNISLASH_BASE_HITS, OMNISLASH_MAX_HITS, feedTier);
            case OCTASLASH -> resolveFeedScaledHits(OCTASLASH_BASE_HITS, OCTASLASH_MAX_HITS, feedTier);
            case CHARGE_ATTACK -> resolveFeedScaledHits(CHARGE_ATTACK_BASE_HITS, CHARGE_ATTACK_MAX_HITS, feedTier);
            case SUMMON_WOLF_PACK -> 1;
            default -> 1;
        };
    }

    private static void armGroundslamAugment(UUID playerId, PendingLoreProc proc, int hits) {
        if (playerId == null || proc == null || proc.ability == null || hits <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long spacing = Math.max(OMNISLASH_HIT_DELAY_MS, MULTIHIT_HIT_DELAY_MS);
        long expiresAt = now + (hits * spacing) + 1200L;
        GroundslamContext ctx = new GroundslamContext(playerId, proc.ability, proc.spiritId,
                proc.level, proc.feedTier, hits, expiresAt);
        GROUNDSLAM_CONTEXTS.put(playerId, ctx);
    }

    private static void tryApplyGroundslamAugment(Store<EntityStore> store,
                                                  Ref<EntityStore> attackerRef,
                                                  Ref<EntityStore> targetRef) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player attacker = store.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }
        UUID playerId = attacker.getUuid();
        if (playerId == null) {
            return;
        }
        GroundslamContext ctx = GROUNDSLAM_CONTEXTS.get(playerId);
        if (ctx == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (ctx.remainingHits <= 0 || now > ctx.expiresAt) {
            GROUNDSLAM_CONTEXTS.remove(playerId);
            return;
        }
        ctx.remainingHits--;
        if (ctx.remainingHits <= 0) {
            GROUNDSLAM_CONTEXTS.remove(playerId);
        }
        if (!tryProcWithMultiplier(playerId, ctx.spiritId, ctx.ability, ctx.feedTier,
                GROUNDSLAM_AUGMENT_CHANCE_MULTIPLIER)) {
            return;
        }
        applySignatureFallbackVfx(store, attackerRef, targetRef, ctx.ability, ctx.feedTier);
        applyEffectInternal(store, null, null, ctx.ability, ctx.level, ctx.feedTier,
                true, attackerRef, targetRef, playerId, ctx.spiritId, false);
    }

    private static boolean applyProcXp(LoreSocketData data,
                                       PendingLoreProc proc,
                                       int maxLevel,
                                       Player self,
                                       java.util.Set<Integer> awarded) {
        if (data == null || proc == null) {
            return false;
        }
        if (awarded != null && !awarded.add(proc.socketIndex)) {
            return false;
        }
        boolean changed = LoreSocketManager.addProcXp(data, proc.socketIndex);
        LoreSocketData.LoreSocket socket = data.getSocket(proc.socketIndex);
        if (socket != null && socket.getLevel() >= maxLevel) {
            absorbSpirit(self, socket);
            changed = true;
        }
        return changed;
    }

    private static boolean isSynergyEligible(LoreAbility ability) {
        return false;
    }

    private static boolean shouldBlockLoreDuringAbility1(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        if (isSignatureAbility(ability)) {
            return true;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case MULTI_HIT, OMNISLASH, OCTASLASH, PUMMEL, BLOOD_RUSH, CHARGE_ATTACK -> true;
            default -> false;
        };
    }

    private static boolean isAbility1Active(Store<EntityStore> store, Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null) {
            return false;
        }
        UUID playerId = null;
        try {
            Player player = store.getComponent(attackerRef, Player.getComponentType());
            if (player != null) {
                playerId = player.getUuid();
            }
        } catch (Throwable ignored) {
            // optional
        }
        long now = System.currentTimeMillis();
        if (playerId != null) {
            Long cachedUntil = ABILITY1_ACTIVE_UNTIL.get(playerId);
            if (cachedUntil != null) {
                if (cachedUntil > now) {
                    return true;
                }
                ABILITY1_ACTIVE_UNTIL.remove(playerId);
            }
        }
        InteractionModule module;
        try {
            module = InteractionModule.get();
        } catch (Throwable ignored) {
            return false;
        }
        if (module == null) {
            return false;
        }
        InteractionManager manager;
        try {
            manager = store.getComponent(attackerRef, module.getInteractionManagerComponent());
        } catch (Throwable ignored) {
            return false;
        }
        if (manager == null) {
            return false;
        }
        boolean active = hasActiveAbility1Chain(manager);
        if (active && playerId != null) {
            ABILITY1_ACTIVE_UNTIL.put(playerId, now + ABILITY1_GRACE_MS);
        }
        return active;
    }

    private static boolean isSynergyCatalyst(LoreAbility ability, String spiritId) {
        if (ability == null) {
            return false;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return false;
        }
        if (type == LoreEffectType.SUMMON_WOLF_PACK) {
            return true;
        }
        return switch (type) {
            case OMNISLASH, OCTASLASH, CHARGE_ATTACK -> isCoreColorForSpirit(spiritId);
            default -> false;
        };
    }

    private static boolean isSynergyChainTarget(LoreAbility ability) {
        if (ability == null) {
            return false;
        }
        LoreEffectType type = ability.getEffectType();
        if (type == null) {
            return false;
        }
        if (isCoreEffectType(type)) {
            return false;
        }
        return true;
    }

    private static boolean tryProcSynergy(UUID playerId, String spiritId, LoreAbility ability, int feedTier) {
        if (playerId == null || spiritId == null || ability == null) {
            return false;
        }
        double baseChance = LoreAbility.scaleProcChance(ability.getProcChance(), feedTier);
        if (baseChance <= 0.0d) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = playerId + "|" + normalizeSpiritId(spiritId);
        Long until = COOLDOWNS.get(key);
        if (until != null && now < until) {
            return false;
        }
        double boosted = Math.min(SYNERGY_PROC_MAX, baseChance + SYNERGY_PROC_BONUS);
        if (ThreadLocalRandom.current().nextDouble() >= boosted) {
            return false;
        }
        long cooldownMs = Math.max(0L, LoreAbility.scaleCooldownMs(ability.getCooldownMs(), feedTier));
        if (cooldownMs > 0L) {
            COOLDOWNS.put(key, now + cooldownMs);
        }
        return true;
    }

    private static boolean isPendingSpirit(List<PendingLoreProc> pending, String spiritId) {
        if (pending == null || pending.isEmpty() || spiritId == null || spiritId.isBlank()) {
            return false;
        }
        String normalized = normalizeSpiritId(spiritId);
        for (PendingLoreProc proc : pending) {
            if (proc == null || proc.spiritId == null) {
                continue;
            }
            if (normalized.equals(normalizeSpiritId(proc.spiritId))) {
                return true;
            }
        }
        return false;
    }

    private static void applyOmnislash(Store<EntityStore> store,
                                       Ref<EntityStore> attackerRef,
                                       Ref<EntityStore> targetRef,
                                       float perHit,
                                       boolean teleportAttacker,
                                       boolean skipRefine,
                                       int totalHits,
                                       double radius,
                                       float finalMultiplier) {
        if (store == null || perHit <= 0f) {
            return;
        }
        double radiusSq = radius * radius;
        List<Ref<EntityStore>> targets = collectOmnislashTargets(store, attackerRef, targetRef, radius, radiusSq, "omnislash");
        LoreDebug.logKv("omnislash.scan",
                "targets", targets.size(),
                "radius", String.format(Locale.ROOT, "%.2f", radius),
                "perHit", String.format(Locale.ROOT, "%.3f", perHit));
        if (targets.isEmpty()) {
            return;
        }
        if (attackerRef != null) {
            long trailDurationMs = Math.max(250L, (long) totalHits * OMNISLASH_HIT_DELAY_MS + 200L);
            LoreVisuals.tryApplyTimedVisualEffectOverride(store, attackerRef, trailDurationMs, OMNISLASH_TRAIL_EFFECT_IDS);
        }
        String slashParticleId = LoreVisuals.resolveParticleEffectId(OMNISLASH_SLASH_PARTICLE_IDS);
        OmnislashSequence sequence = new OmnislashSequence(store, attackerRef, targets, perHit, teleportAttacker,
                skipRefine, totalHits, slashParticleId, finalMultiplier);
        scheduleOmnislash(sequence);
    }

    private static void scheduleOmnislash(OmnislashSequence sequence) {
        if (sequence == null) {
            return;
        }
        for (int i = 0; i < sequence.totalHits; i++) {
            long delay = i * OMNISLASH_HIT_DELAY_MS;
            OMNISLASH_SCHEDULER.schedule(() -> queueOmnislashHit(sequence), delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void queueOmnislashHit(OmnislashSequence sequence) {
        if (sequence == null) {
            return;
        }
        Store<EntityStore> store = sequence.store;
        if (store == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyOmnislashHit(sequence));
    }

    private static void applyOmnislashHit(OmnislashSequence sequence) {
        if (sequence == null) {
            return;
        }
        if (sequence.hitsApplied >= sequence.totalHits) {
            return;
        }
        Store<EntityStore> store = sequence.store;
        if (store == null || store.isShutdown()) {
            return;
        }
        Ref<EntityStore> targetRef = selectOmnislashTarget(store, sequence);
        if (targetRef == null) {
            sequence.hitsApplied = sequence.totalHits;
            return;
        }
        if (sequence.teleportAttacker) {
            teleportToTarget(store, sequence.attackerRef, targetRef);
        }
        int hitIndex = sequence.hitsApplied;
        playSwingAnimation(store, sequence.attackerRef, hitIndex);
        if (sequence.slashParticleId != null) {
            LoreVisuals.spawnSlashParticles(store, sequence.attackerRef, targetRef, sequence.slashParticleId, 1.05f);
        }
        float multiplier = 1.0f;
        if (OMNISLASH_RAMP_PER_HIT > 0.0d && hitIndex > 0) {
            multiplier *= (float) (1.0d + (OMNISLASH_RAMP_PER_HIT * hitIndex));
        }
        if (sequence.totalHits > 1 && hitIndex >= sequence.totalHits - 1) {
            if (sequence.finalMultiplier > 0f) {
                multiplier *= sequence.finalMultiplier;
            }
        }
        float actualPerHit = sequence.perHit * multiplier;
        applyCombatDamage(store, sequence.attackerRef, targetRef, actualPerHit, sequence.skipRefine);
        tryApplyGroundslamAugment(store, sequence.attackerRef, targetRef);
        sequence.hitsApplied++;
    }

    private static void scheduleSingleTargetHits(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> targetRef,
                                                 float perHit,
                                                 int hits,
                                                 long delayMs,
                                                 boolean skipRefine) {
        scheduleSingleTargetHits(store, attackerRef, targetRef, perHit, hits, delayMs, skipRefine, null, 1.0f, 0.0d);
    }

    private static void scheduleSingleTargetHits(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> targetRef,
                                                 float perHit,
                                                 int hits,
                                                 long delayMs,
                                                 boolean skipRefine,
                                                 String particleId,
                                                 float finalMultiplier,
                                                 double rampPerHit) {
        if (store == null || targetRef == null || perHit <= 0f || hits <= 0) {
            return;
        }
        long spacing = Math.max(0L, delayMs);
        for (int i = 0; i < hits; i++) {
            long delay = i * spacing;
            int hitIndex = i;
            OMNISLASH_SCHEDULER.schedule(() -> queueSingleTargetHit(store, attackerRef, targetRef, perHit, hitIndex,
                            skipRefine, particleId, hits, finalMultiplier, rampPerHit),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void queueSingleTargetHit(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> targetRef,
                                             float perHit,
                                             int hitIndex,
                                             boolean skipRefine,
                                             String particleId,
                                             int totalHits,
                                             float finalMultiplier,
                                             double rampPerHit) {
        if (store == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applySingleTargetHit(store, attackerRef, targetRef, perHit, hitIndex,
                skipRefine, particleId, totalHits, finalMultiplier, rampPerHit));
    }

    private static void scheduleTrailParticles(Store<EntityStore> store,
                                               Ref<EntityStore> sourceRef,
                                               String[] particleIds,
                                               int hits,
                                               long delayMs) {
        if (store == null || sourceRef == null || particleIds == null || particleIds.length == 0 || hits <= 0) {
            return;
        }
        String particleId = LoreVisuals.resolveParticleEffectId(particleIds);
        if (particleId == null) {
            LoreDebug.logKv("trail.missing", "ids", String.join(",", particleIds));
            return;
        }
        long spacing = Math.max(0L, delayMs);
        for (int i = 0; i < hits; i++) {
            long delay = i * spacing;
            OMNISLASH_SCHEDULER.schedule(() -> queueTrailParticleSpawn(store, sourceRef, particleId),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void queueTrailParticleSpawn(Store<EntityStore> store,
                                                Ref<EntityStore> sourceRef,
                                                String particleId) {
        if (store == null || store.isShutdown() || sourceRef == null || particleId == null) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> spawnTrailParticle(store, sourceRef, particleId));
    }

    private static void spawnTrailParticle(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           String particleId) {
        if (store == null || sourceRef == null || particleId == null || particleId.isBlank()) {
            return;
        }
        Vector3d pos = getPosition(store, sourceRef);
        if (pos == null) {
            return;
        }
        Vector3d spawnPos = pos.clone();
        spawnPos.add(0.0d, 1.0d, 0.0d);
        try {
            LoreVisuals.spawnScaledParticle(store, particleId, spawnPos, 1.0f);
            LoreDebug.logKv("trail.spawn",
                    "id", particleId,
                    "pos", String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)",
                            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()));
        } catch (Throwable ignored) {
            try {
                ParticleUtil.spawnParticleEffect(particleId, spawnPos, store);
            } catch (Throwable ignored2) {
                // best-effort
            }
        }
    }

    private static void applySingleTargetHit(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> targetRef,
                                             float perHit,
                                             int hitIndex,
                                             boolean skipRefine,
                                             String particleId,
                                             int totalHits,
                                             float finalMultiplier,
                                             double rampPerHit) {
        if (store == null || targetRef == null || perHit <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        float multiplier = 1.0f;
        if (rampPerHit > 0.0d && hitIndex > 0) {
            multiplier *= (float) (1.0d + (rampPerHit * hitIndex));
        }
        if (totalHits > 1 && hitIndex >= totalHits - 1) {
            if (finalMultiplier > 0f) {
                multiplier *= finalMultiplier;
            }
        }
        float actualPerHit = perHit * multiplier;
        playSwingAnimation(store, attackerRef, hitIndex);
        if (particleId != null) {
            LoreVisuals.spawnSlashParticles(store, attackerRef, targetRef, particleId, 1.0f);
        }
        applyCombatDamage(store, attackerRef, targetRef, actualPerHit, skipRefine);
        tryApplyGroundslamAugment(store, attackerRef, targetRef);
    }

    private static void scheduleChargeAttackHits(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 Ref<EntityStore> targetRef,
                                                 float perHit,
                                                 int hits,
                                                 long delayMs) {
        if (store == null || targetRef == null || perHit <= 0f || hits <= 0) {
            return;
        }
        long spacing = Math.max(0L, delayMs);
        for (int i = 0; i < hits; i++) {
            long delay = i * spacing;
            int hitIndex = i;
            OMNISLASH_SCHEDULER.schedule(() -> queueChargeAttackHit(store, attackerRef, targetRef, perHit, hitIndex),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void queueChargeAttackHit(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> targetRef,
                                             float perHit,
                                             int hitIndex) {
        if (store == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyChargeAttackHit(store, attackerRef, targetRef, perHit, hitIndex));
    }

    private static void applyChargeAttackHit(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> targetRef,
                                             float perHit,
                                             int hitIndex) {
        if (store == null || targetRef == null || perHit <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        playChargeAttackAnimation(store, attackerRef, hitIndex);
        applyCombatDamage(store, attackerRef, targetRef, perHit, false);
        tryApplyGroundslamAugment(store, attackerRef, targetRef);
    }

    private static void playSwingAnimation(Store<EntityStore> store,
                                           Ref<EntityStore> attackerRef,
                                           int swingIndex) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        String animSet = resolvePlayerAnimationSet(player);
        String anim = resolveSwingAnimationId(animSet, swingIndex);
        if (anim == null) {
            ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
            String fallbackSet = resolveFallbackAnimationSet(stack);
            if (fallbackSet != null && !fallbackSet.equalsIgnoreCase(animSet)) {
                String fallbackAnim = resolveSwingAnimationId(fallbackSet, swingIndex);
                if (fallbackAnim != null) {
                    animSet = fallbackSet;
                    anim = fallbackAnim;
                }
            }
        }
        if (anim != null) {
            try {
                AnimationUtils.playAnimation(attackerRef, AnimationSlot.Action, animSet, anim, true, store);
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        playSwingSound(store, player);
    }

    private static String resolveSwingAnimationId(String animSet, int swingIndex) {
        if (animSet == null || animSet.isBlank()) {
            return null;
        }
        boolean left = swingIndex % 2 == 0;
        String anim = resolveAnimationId(
                animSet,
                left ? "SwingLeft" : "SwingRight",
                left ? "SwingLeftCharged" : "SwingRightCharged",
                left ? "SwingLeftAttack" : "SwingRightAttack",
                left ? "AttackLeft" : "AttackRight",
                "Attack",
                "Swing",
                "Slash",
                "Strike",
                "Stab"
        );
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "swing");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "attack");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "slash");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "strike");
        if (anim != null) {
            return anim;
        }
        return resolveAnimationIdByContains(animSet, "stab");
    }

    private static String resolveFallbackAnimationSet(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "Item";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null || key.isBlank()) {
            return "Item";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("battleaxe")) {
            return "Battleaxe";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return "Mace";
        }
        if (lower.contains("dagger") || lower.contains("daggers") || lower.contains("knife") || lower.contains("claw")) {
            return "Daggers";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return "Sword";
        }
        if (lower.contains("axe")) {
            return "Axe";
        }
        return "Item";
    }

    private static void playChargeAttackAnimation(Store<EntityStore> store,
                                                  Ref<EntityStore> attackerRef,
                                                  int swingIndex) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        String anim = resolveChargeAnimationId(stack, swingIndex);
        if (anim == null || anim.isBlank()) {
            playSwingAnimation(store, attackerRef, swingIndex);
            return;
        }
        try {
            String animSet = resolvePlayerAnimationSet(player);
            AnimationUtils.playAnimation(attackerRef, AnimationSlot.Action, animSet, anim, true, store);
        } catch (Throwable ignored) {
            playSwingAnimation(store, attackerRef, swingIndex);
            return;
        }
        playChargeSound(store, player);
    }

    private static String resolvePlayerAnimationSet(Player player) {
        if (player == null) {
            return "Item";
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty()) {
            return "Item";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        if (animId == null || animId.isBlank()) {
            return "Item";
        }
        return animId;
    }

    private static String resolveAnimationId(String animSet, String... animIds) {
        if (animSet == null || animSet.isBlank() || animIds == null || animIds.length == 0) {
            return null;
        }
        try {
            var assetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap();
            if (assetMap != null) {
                var anims = assetMap.getAsset(animSet);
                if (anims != null) {
                    var map = anims.getAnimations();
                    if (map != null) {
                        for (String animId : animIds) {
                            if (animId == null || animId.isBlank()) {
                                continue;
                            }
                            if (map.containsKey(animId)) {
                                return animId;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return null;
    }

    private static void playSwingSound(Store<EntityStore> store, Player player) {
        if (store == null || player == null) {
            return;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        String soundId = resolveSwingSoundId(stack);
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex < 0) {
            LoreDebug.logKv("sound.missing", "id", soundId);
            return;
        }
        try {
            SoundUtil.playSoundEvent2dToPlayer(player.getPlayerRef(), soundIndex, SoundCategory.SFX);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static void playChargeSound(Store<EntityStore> store, Player player) {
        if (store == null || player == null) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Weapon_Charge_Swing");
        if (soundIndex >= 0) {
            try {
                SoundUtil.playSoundEvent2dToPlayer(player.getPlayerRef(), soundIndex, SoundCategory.SFX);
                return;
            } catch (Throwable ignored) {
                // fallback below
            }
        }
        playSwingSound(store, player);
    }

    private static String resolveSwingSoundId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "SFX_Unarmed_Swing";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null || key.isBlank()) {
            return "SFX_Unarmed_Swing";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("longsword")) {
            return "SFX_Longsword_Special_Swing";
        }
        if (lower.contains("battleaxe")) {
            return "SFX_Battleaxe_T2_Swing";
        }
        if (lower.contains("axe")) {
            return "SFX_Axe_Iron_Swing";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return "SFX_Mace_T1_Swing";
        }
        if (lower.contains("spear") || lower.contains("staff") || lower.contains("polearm")) {
            return "SFX_Light_Melee_T1_Swing";
        }
        if (lower.contains("dagger") || lower.contains("daggers") || lower.contains("knife") || lower.contains("claw")) {
            return "SFX_Sword_T1_Swing";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return "SFX_Sword_T1_Swing";
        }
        return "SFX_Unarmed_Swing";
    }

    private static String resolveChargeAnimationId(ItemStack stack, int swingIndex) {
        boolean left = swingIndex % 2 == 0;
        if (stack == null || stack.isEmpty()) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("dagger")) {
            return left ? "StabDoubleCharged" : "LungeDoubleCharged";
        }
        if (lower.contains("spear")) {
            return "ThrowCharged";
        }
        if (lower.contains("staff")) {
            return "CastSummonCharged";
        }
        if (lower.contains("bow") || lower.contains("crossbow")) {
            return "ShootCharged";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("battleaxe")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("axe")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        return left ? "SwingLeftCharged" : "SwingRightCharged";
    }

    private static float resolveChargeAttackBase(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 float fallback) {
        if (store == null || attackerRef == null) {
            return fallback;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return fallback;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty()) {
            return fallback;
        }
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return fallback;
        }
        double chargedBase = EquipmentDamageTooltipMath.getChargedBaseDamageFromInteractionVars(item);
        if (chargedBase <= 0.0d) {
            return fallback;
        }
        double normalBase = EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item);
        if (normalBase > 0.0d) {
            double ratio = chargedBase / normalBase;
            ratio = Math.max(0.5d, Math.min(3.0d, ratio));
            double scaled = fallback * ratio;
            if (scaled > 0.0d) {
                return (float) scaled;
            }
        }
        return (float) chargedBase;
    }

    private static Ref<EntityStore> selectOmnislashTarget(Store<EntityStore> store, OmnislashSequence sequence) {
        if (sequence == null || store == null || sequence.targets == null || sequence.targets.isEmpty()) {
            return null;
        }
        int guard = 0;
        while (!sequence.targets.isEmpty() && guard++ < sequence.targets.size() + 2) {
            int idx = sequence.hitsApplied % sequence.targets.size();
            Ref<EntityStore> ref = sequence.targets.get(idx);
            if (ref == null || !isEntityAlive(store, ref)) {
                sequence.targets.remove(idx);
                continue;
            }
            return ref;
        }
        return null;
    }

    private static void teleportToTarget(Store<EntityStore> store,
                                         Ref<EntityStore> attackerRef,
                                         Ref<EntityStore> targetRef) {
        if (store == null || attackerRef == null || targetRef == null) {
            return;
        }
        TransformComponent attackerTransform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (attackerTransform == null || targetTransform == null) {
            return;
        }
        Vector3d targetPos = targetTransform.getPosition();
        if (targetPos == null) {
            return;
        }
        Vector3d dest = targetPos.clone();
        Vector3d attackerPos = attackerTransform.getPosition();
        if (attackerPos != null) {
            Vector3d offset = attackerPos.clone().subtract(targetPos);
            if (offset.length() < 0.01d) {
                offset.assign(1.0d, 0.0d, 0.0d);
            }
            offset.normalize().scale(0.6d);
            dest.add(offset);
        }
        Vector3f rotation = attackerTransform.getRotation() != null
                ? attackerTransform.getRotation().clone()
                : new Vector3f(0f, 0f, 0f);
        queueTeleport(store, attackerRef, dest, rotation);
    }

    private static void lungeTowardTarget(Store<EntityStore> store,
                                          Ref<EntityStore> attackerRef,
                                          Ref<EntityStore> targetRef,
                                          double distance) {
        if (store == null || attackerRef == null || targetRef == null) {
            return;
        }
        if (distance <= 0.05d) {
            return;
        }
        TransformComponent attackerTransform = store.getComponent(attackerRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (attackerTransform == null || targetTransform == null) {
            return;
        }
        Vector3d attackerPos = attackerTransform.getPosition();
        Vector3d targetPos = targetTransform.getPosition();
        if (attackerPos == null || targetPos == null) {
            return;
        }
        Vector3d direction = targetPos.clone().subtract(attackerPos);
        direction.assign(direction.getX(), 0.0d, direction.getZ());
        double dist = direction.length();
        if (dist < 0.05d) {
            return;
        }
        double lungeDist = Math.min(distance, Math.max(0.0d, dist + VORTEXSTRIKE_PASS_THROUGH_DISTANCE));
        if (lungeDist <= 0.08d) {
            return;
        }
        direction.normalize();
        double scale = distance > 0.0d ? (lungeDist / distance) : 1.0d;
        double force = VORTEXSTRIKE_LUNGE_FORCE_MIN
                + (VORTEXSTRIKE_LUNGE_FORCE_MAX - VORTEXSTRIKE_LUNGE_FORCE_MIN) * clampPercent(scale, 0.2d, 1.0d);
        queueLungeForce(store, attackerRef, direction, force);
    }

    private static void queueTeleport(Store<EntityStore> store,
                                      Ref<EntityStore> attackerRef,
                                      Vector3d dest,
                                      Vector3f rotation) {
        if (store == null || attackerRef == null || dest == null || rotation == null) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore != null && entityStore.getWorld() != null) {
            entityStore.getWorld().execute(() -> applyTeleportInternal(store, attackerRef, dest, rotation));
        } else {
            applyTeleportInternal(store, attackerRef, dest, rotation);
        }
    }

    private static void applyTeleportInternal(Store<EntityStore> store,
                                              Ref<EntityStore> attackerRef,
                                              Vector3d dest,
                                              Vector3f rotation) {
        if (store == null || attackerRef == null || dest == null || rotation == null) {
            return;
        }
        if (!isEntityAlive(store, attackerRef)) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player != null) {
            EntityStore entityStore = store.getExternalData();
            Teleport teleport = entityStore != null && entityStore.getWorld() != null
                    ? Teleport.createForPlayer(entityStore.getWorld(), dest, rotation)
                    : Teleport.createForPlayer(dest, rotation);
            store.putComponent(attackerRef, Teleport.getComponentType(), teleport);
            return;
        }
        Teleport teleport = Teleport.createExact(dest, rotation);
        store.putComponent(attackerRef, Teleport.getComponentType(), teleport);
    }

    private static void queueLungeForce(Store<EntityStore> store,
                                        Ref<EntityStore> attackerRef,
                                        Vector3d direction,
                                        double force) {
        if (store == null || attackerRef == null || direction == null || force <= 0.0d) {
            return;
        }
        Vector3d applied = direction.clone().normalize().scale(force);
        EntityStore entityStore = store.getExternalData();
        if (entityStore != null && entityStore.getWorld() != null) {
            entityStore.getWorld().execute(() -> applyLungeForceInternal(store, attackerRef, applied));
        } else {
            applyLungeForceInternal(store, attackerRef, applied);
        }
    }

    private static void applyLungeForceInternal(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef,
                                                Vector3d force) {
        if (store == null || attackerRef == null || force == null) {
            return;
        }
        if (!isEntityAlive(store, attackerRef)) {
            return;
        }
        Velocity velocity = store.ensureAndGetComponent(attackerRef, Velocity.getComponentType());
        if (velocity == null) {
            return;
        }
        velocity.addInstruction(force, VORTEXSTRIKE_LUNGE_CONFIG, com.hypixel.hytale.protocol.ChangeVelocityType.Add);
    }

    private static List<Ref<EntityStore>> collectOmnislashTargets(Store<EntityStore> store,
                                                                  Ref<EntityStore> attackerRef,
                                                                  Ref<EntityStore> targetRef,
                                                                  double radius,
                                                                  double radiusSq,
                                                                  String debugLabel) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (targetRef != null) {
            targets.add(targetRef);
        }
        Vector3d center = resolveCenterPosition(store, targetRef, attackerRef);
        if (center == null) {
            return targets;
        }

        boolean usedSpatial = collectTargetsFromNpcSpatialResource(store, attackerRef, targetRef, center, radius, targets, debugLabel);
        final int[] inspected = new int[]{0};
        if (!usedSpatial) {
            var npcType = NPCEntity.getComponentType();
            var transformType = TransformComponent.getComponentType();
            var markerType = NPCMarkerComponent.getComponentType();
            if (transformType == null) {
                return targets;
            }

            Query<EntityStore> npcEntityQuery = resolveNpcQuery(npcType, transformType);
            Query<EntityStore> markerQuery = resolveNpcMarkerQuery(markerType, transformType);
            Query<EntityStore> statsQuery = resolveStatQuery(EntityStatMap.getComponentType(), transformType);
            Query<EntityStore> transformOnlyQuery = resolveTransformQuery(transformType);
            if (npcEntityQuery != null) {
                collectTargetsForQuery(store, npcEntityQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (markerQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForQuery(store, markerQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (targets.size() < OMNISLASH_MAX_TARGETS && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForLivingQuery(store, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (statsQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForStatQuery(store, statsQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (transformOnlyQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForStatQuery(store, transformOnlyQuery, attackerRef, targetRef, center, targets, inspected,
                        transformType, radiusSq);
            }
        }

        if (DEBUG_LORE_PROCS) {
            String logLabel = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(logLabel + ".scan", "inspected", inspected[0], "targets", targets.size());
            for (int i = 0; i < targets.size(); i++) {
                Ref<EntityStore> ref = targets.get(i);
                Vector3d pos = getPosition(store, ref);
                String entityLabel = resolveDebugEntityLabel(store, ref);
                LoreDebug.logKv(logLabel + ".target", "index", i, "entity", entityLabel, "pos", formatVector(pos));
            }
        }

        return targets;
    }

    private static List<Ref<EntityStore>> collectTargetsAtPosition(Store<EntityStore> store,
                                                                   Ref<EntityStore> attackerRef,
                                                                   Vector3d center,
                                                                   double radius,
                                                                   double radiusSq,
                                                                   String debugLabel) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (store == null || center == null) {
            return targets;
        }

        boolean usedSpatial = collectTargetsFromNpcSpatialResource(store, attackerRef, null, center, radius, targets, debugLabel);
        final int[] inspected = new int[]{0};
        if (!usedSpatial) {
            var npcType = NPCEntity.getComponentType();
            var transformType = TransformComponent.getComponentType();
            var markerType = NPCMarkerComponent.getComponentType();
            if (transformType == null) {
                return targets;
            }

            Query<EntityStore> npcEntityQuery = resolveNpcQuery(npcType, transformType);
            Query<EntityStore> markerQuery = resolveNpcMarkerQuery(markerType, transformType);
            Query<EntityStore> statsQuery = resolveStatQuery(EntityStatMap.getComponentType(), transformType);
            Query<EntityStore> transformOnlyQuery = resolveTransformQuery(transformType);
            if (npcEntityQuery != null) {
                collectTargetsForQuery(store, npcEntityQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (markerQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForQuery(store, markerQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (targets.size() < OMNISLASH_MAX_TARGETS && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForLivingQuery(store, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (statsQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForStatQuery(store, statsQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq);
            }
            if (transformOnlyQuery != null && targets.size() < OMNISLASH_MAX_TARGETS
                    && inspected[0] < OMNISLASH_MAX_NPC_CHECKS) {
                collectTargetsForStatQuery(store, transformOnlyQuery, attackerRef, null, center, targets, inspected,
                        transformType, radiusSq);
            }
        }

        if (DEBUG_LORE_PROCS) {
            String logLabel = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(logLabel + ".scan", "inspected", inspected[0], "targets", targets.size());
            for (int i = 0; i < targets.size(); i++) {
                Ref<EntityStore> ref = targets.get(i);
                Vector3d pos = getPosition(store, ref);
                String entityLabel = resolveDebugEntityLabel(store, ref);
                LoreDebug.logKv(logLabel + ".target", "index", i, "entity", entityLabel, "pos", formatVector(pos));
            }
        }

        return targets;
    }

    private static boolean collectTargetsFromNpcSpatialResource(Store<EntityStore> store,
                                                                Ref<EntityStore> attackerRef,
                                                                Ref<EntityStore> targetRef,
                                                                Vector3d center,
                                                                double radius,
                                                                List<Ref<EntityStore>> targets,
                                                                String debugLabel) {
        if (store == null || center == null || targets == null) {
            return false;
        }
        NPCPlugin npcPlugin;
        try {
            npcPlugin = NPCPlugin.get();
        } catch (Throwable ignored) {
            return false;
        }
        if (npcPlugin == null) {
            return false;
        }
        var resourceType = npcPlugin.getNpcSpatialResource();
        if (resourceType == null) {
            return false;
        }
        SpatialResource<Ref<EntityStore>, EntityStore> spatial;
        try {
            spatial = store.getResource(resourceType);
        } catch (Throwable ignored) {
            return false;
        }
        if (spatial == null) {
            return false;
        }
        SpatialStructure<Ref<EntityStore>> structure = spatial.getSpatialStructure();
        if (structure == null) {
            return false;
        }
        List<Ref<EntityStore>> nearby = SpatialResource.getThreadLocalReferenceList();
        nearby.clear();
        try {
            structure.collect(center, radius, nearby);
        } catch (Throwable ignored) {
            return false;
        }
        for (Ref<EntityStore> ref : nearby) {
            if (targets.size() >= OMNISLASH_MAX_TARGETS) {
                break;
            }
            if (ref == null || ref.equals(attackerRef)) {
                continue;
            }
            if (targetRef != null && ref.equals(targetRef)) {
                continue;
            }
            if (containsRef(targets, ref)) {
                continue;
            }
            targets.add(ref);
        }
        if (DEBUG_LORE_PROCS) {
            String label = debugLabel == null || debugLabel.isBlank() ? "aoe" : debugLabel;
            LoreDebug.logKv(label + ".spatial", "hits", nearby.size());
        }
        return true;
    }

    private static void collectTargetsForQuery(Store<EntityStore> store,
                                               Query<EntityStore> query,
                                               Ref<EntityStore> attackerRef,
                                               Ref<EntityStore> targetRef,
                                               Vector3d center,
                                               List<Ref<EntityStore>> targets,
                                               int[] inspected,
                                               com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                               double radiusSq) {
        if (store == null || query == null || center == null || targets == null || inspected == null) {
            return;
        }
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= OMNISLASH_MAX_TARGETS) {
                    return false;
                }
                if (inspected[0]++ >= OMNISLASH_MAX_NPC_CHECKS) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    private static void collectTargetsForLivingQuery(Store<EntityStore> store,
                                                     Ref<EntityStore> attackerRef,
                                                     Ref<EntityStore> targetRef,
                                                     Vector3d center,
                                                     List<Ref<EntityStore>> targets,
                                                     int[] inspected,
                                                     com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                                     double radiusSq) {
        if (store == null || center == null || targets == null || inspected == null || transformType == null) {
            return;
        }
        Query<EntityStore> query = AllLegacyLivingEntityTypesQuery.INSTANCE;
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= OMNISLASH_MAX_TARGETS) {
                    return false;
                }
                if (inspected[0]++ >= OMNISLASH_MAX_NPC_CHECKS) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                if (chunk.getComponent(i, Player.getComponentType()) != null) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    private static void collectTargetsForStatQuery(Store<EntityStore> store,
                                                   Query<EntityStore> query,
                                                   Ref<EntityStore> attackerRef,
                                                   Ref<EntityStore> targetRef,
                                                   Vector3d center,
                                                   List<Ref<EntityStore>> targets,
                                                   int[] inspected,
                                                   com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType,
                                                   double radiusSq) {
        if (store == null || query == null || center == null || targets == null || inspected == null) {
            return;
        }
        var statType = EntityStatMap.getComponentType();
        if (statType == null) {
            return;
        }
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (targets.size() >= OMNISLASH_MAX_TARGETS) {
                    return false;
                }
                if (inspected[0]++ >= OMNISLASH_MAX_NPC_CHECKS) {
                    return false;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null || ref.equals(attackerRef)) {
                    continue;
                }
                if (targetRef != null && ref.equals(targetRef)) {
                    continue;
                }
                if (chunk.getComponent(i, Player.getComponentType()) != null) {
                    continue;
                }
                TransformComponent npcTransform = chunk.getComponent(i, transformType);
                if (npcTransform == null) {
                    continue;
                }
                Vector3d pos = npcTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                EntityStatMap statMap = chunk.getComponent(i, statType);
                if (statMap == null || getHealthStatIndex(statMap) < 0) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
    }

    static Vector3d resolveCenterPosition(Store<EntityStore> store,
                                          Ref<EntityStore> primary,
                                          Ref<EntityStore> fallback) {
        Vector3d pos = getPosition(store, primary);
        if (pos == null) {
            pos = getPosition(store, fallback);
        }
        return pos;
    }

    private static Vector3d getPosition(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return null;
        }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                return null;
            }
            Vector3d pos = transform.getPosition();
            return pos == null ? null : pos.clone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean containsRef(List<Ref<EntityStore>> refs, Ref<EntityStore> ref) {
        if (refs == null || refs.isEmpty() || ref == null) {
            return false;
        }
        for (Ref<EntityStore> existing : refs) {
            if (ref.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSquared(Vector3d a, Vector3d b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return (dx * dx) + (dy * dy) + (dz * dz);
    }

    private static String formatVector(Vector3d pos) {
        if (pos == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static String resolveDebugEntityLabel(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return "unknown";
        }
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                String name = player.getDisplayName();
                if (name == null || name.isBlank()) {
                    return "Player:" + String.valueOf(player.getPlayerRef());
                }
                return "Player:" + name;
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                String type = npc.getNPCTypeId();
                if (type != null && !type.isBlank()) {
                    return "NPC:" + type;
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return String.valueOf(ref);
    }

    private static Query<EntityStore> resolveNpcQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, NPCEntity> npcType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = npcQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (NPC_QUERY_LOCK) {
            cached = npcQuery;
            if (cached != null) {
                return cached;
            }
            if (npcType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(npcType, transformType);
                npcQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveNpcMarkerQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, NPCMarkerComponent> markerType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = npcMarkerQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (NPC_MARKER_QUERY_LOCK) {
            cached = npcMarkerQuery;
            if (cached != null) {
                return cached;
            }
            if (markerType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(markerType, transformType);
                npcMarkerQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveStatQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, EntityStatMap> statType,
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = statQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (STAT_QUERY_LOCK) {
            cached = statQuery;
            if (cached != null) {
                return cached;
            }
            if (statType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(statType, transformType);
                statQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Query<EntityStore> resolveTransformQuery(
            com.hypixel.hytale.component.ComponentType<EntityStore, TransformComponent> transformType) {
        Query<EntityStore> cached = transformQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (TRANSFORM_QUERY_LOCK) {
            cached = transformQuery;
            if (cached != null) {
                return cached;
            }
            if (transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(transformType);
                transformQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static void trySummonWolfPack(Store<EntityStore> store,
                                          Ref<EntityStore> summonerRef,
                                          UUID playerId,
                                          String spiritId,
                                          int level,
                                          int feedTier) {
        if (store == null || summonerRef == null || playerId == null) {
            return;
        }
        int cap = Math.max(1, level + Math.max(0, feedTier));
        String key = playerId + "|" + normalizeSpiritId(spiritId == null ? "wolf" : spiritId);
        Set<Ref<EntityStore>> summons = WOLF_SUMMONS.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        int active = pruneSummons(store, summons);
        if (active >= cap) {
            cleanupSummonKey(key, summons);
            return;
        }

        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            cleanupSummonKey(key, summons);
            return;
        }

        TransformComponent transform = store.getComponent(summonerRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            cleanupSummonKey(key, summons);
            return;
        }
        Vector3d basePos = transform.getPosition().clone();
        Vector3f rotation = transform.getRotation() != null
                ? transform.getRotation().clone()
                : new Vector3f(0f, 0f, 0f);

        String primaryRole = resolveWolfRoleName(spiritId);
        String fallbackRole = "Wolf_Black";

        for (int attempt = 0; attempt < WOLF_SUMMON_MAX_ATTEMPTS; attempt++) {
            Vector3d spawnPos = buildWolfSpawnPosition(basePos, attempt);
            Ref<EntityStore> spawned = spawnWolf(npcPlugin, store, primaryRole, spawnPos, rotation, playerId);
            if (spawned == null && fallbackRole != null
                    && (primaryRole == null || !fallbackRole.equalsIgnoreCase(primaryRole))) {
                spawned = spawnWolf(npcPlugin, store, fallbackRole, spawnPos, rotation, playerId);
            }
            if (spawned != null) {
                summons.add(spawned);
                break;
            }
        }

        cleanupSummonKey(key, summons);
    }

    private static Ref<EntityStore> spawnWolf(NPCPlugin npcPlugin,
                                              Store<EntityStore> store,
                                              String roleName,
                                              Vector3d position,
                                              Vector3f rotation,
                                              UUID ownerId) {
        if (npcPlugin == null || store == null || position == null || rotation == null) {
            return null;
        }
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        try {
            Pair<Ref<EntityStore>, INonPlayerCharacter> pair =
                    npcPlugin.spawnNPC(store, roleName, null, position.clone(), rotation.clone());
            if (pair == null || pair.first() == null) {
                return null;
            }
            if (pair.second() instanceof NPCEntity npc && ownerId != null) {
                npc.addReservation(ownerId);
            }
            return pair.first();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveAnimationIdByContains(String animSet, String token) {
        if (animSet == null || animSet.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        String tokenLower = token.toLowerCase(Locale.ROOT);
        try {
            var assetMap =
                    com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap();
            if (assetMap != null) {
                var anims = assetMap.getAsset(animSet);
                if (anims != null) {
                    var map = anims.getAnimations();
                    if (map != null) {
                        for (String key : map.keySet()) {
                            if (key == null) {
                                continue;
                            }
                            if (key.toLowerCase(Locale.ROOT).contains(tokenLower)) {
                                return key;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static Vector3d buildWolfSpawnPosition(Vector3d base, int attempt) {
        double angle = ThreadLocalRandom.current().nextDouble(0.0d, Math.PI * 2.0d);
        double radius = WOLF_SUMMON_MIN_RADIUS
                + (ThreadLocalRandom.current().nextDouble() * (WOLF_SUMMON_MAX_RADIUS - WOLF_SUMMON_MIN_RADIUS));
        double offsetX = Math.cos(angle) * radius;
        double offsetZ = Math.sin(angle) * radius;
        double lift = (attempt % 2 == 0) ? 0.1d : 0.25d;
        return new Vector3d(base.getX() + offsetX, base.getY() + lift, base.getZ() + offsetZ);
    }

    private static String resolveWolfRoleName(String spiritId) {
        if (spiritId == null || spiritId.isBlank()) {
            return "Wolf_Black";
        }
        return spiritId.trim();
    }

    private static int pruneSummons(Store<EntityStore> store, Set<Ref<EntityStore>> summons) {
        if (store == null || summons == null || summons.isEmpty()) {
            return 0;
        }
        Iterator<Ref<EntityStore>> iterator = summons.iterator();
        while (iterator.hasNext()) {
            Ref<EntityStore> ref = iterator.next();
            if (ref == null || !isEntityAlive(store, ref)) {
                iterator.remove();
            }
        }
        return summons.size();
    }

    private static boolean isEntityAlive(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return false;
        }
        try {
            return store.getArchetype(ref) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void cleanupSummonKey(String key, Set<Ref<EntityStore>> summons) {
        if (key == null || key.isBlank() || summons == null) {
            return;
        }
        if (summons.isEmpty()) {
            WOLF_SUMMONS.remove(key, summons);
        }
    }

    private static void absorbSpirit(Player player, LoreSocketData.LoreSocket socket) {
        if (player == null || socket == null) {
            return;
        }
        String spiritId = socket.getSpiritId();
        if (spiritId == null || spiritId.isBlank()) {
            return;
        }
        LoreAbsorptionStore.addAbsorbed(player.getUuid(), spiritId);
        socket.setGemItemId("");
        socket.setColor("");
        socket.setSpiritId("");
        socket.setEffectOverride("");
        socket.setLevel(0);
        socket.setXp(0);
        socket.setFeedTier(0);
        socket.setLocked(false);
    }

    private static LoreAbility applyEffectOverride(LoreAbility ability, String overrideRaw) {
        if (ability == null) {
            return null;
        }
        if (overrideRaw == null || overrideRaw.isBlank()) {
            return ability;
        }
        LoreEffectType override = LoreEffectType.fromString(overrideRaw, null);
        if (override == null || override == ability.getEffectType()) {
            return ability;
        }
        return new LoreAbility(
                ability.getSpiritId(),
                ability.getTrigger(),
                ability.getProcChance(),
                ability.getCooldownMs(),
                override,
                ability.getBaseValue(),
                ability.getPerLevel(),
                ability.getAbilityNameKey(),
                ability.getAbilityNameFallback()
        );
    }

    private static void applyCombatDamage(Store<EntityStore> store,
                                          Ref<EntityStore> sourceRef,
                                          Ref<EntityStore> targetRef,
                                          float rawDamage,
                                          boolean skipRefine) {
        if (store == null || targetRef == null || rawDamage <= 0f) {
            LoreDebug.logKv("damage.skip", "reason", "invalid", "rawDamage", rawDamage);
            return;
        }
        if (applyDamageEvent(store, sourceRef, targetRef, rawDamage, skipRefine)) {
            return;
        }
        applyDirectHealthLoss(store, targetRef, rawDamage);
    }

    private static boolean applyDamageEvent(Store<EntityStore> store,
                                            Ref<EntityStore> sourceRef,
                                            Ref<EntityStore> targetRef,
                                            float rawDamage,
                                            boolean skipRefine) {
        try {
            Damage.Source source = sourceRef == null
                    ? Damage.NULL_SOURCE
                    : new Damage.EntitySource(sourceRef);
            Damage damage = new Damage(source, DamageCause.PHYSICAL, rawDamage);
            if (skipRefine) {
                damage.putMetaObject(EquipmentRefineEST.META_SKIP_REFORGE, Boolean.TRUE);
            }
            store.invoke(targetRef, damage);
            LoreDebug.logKv("damage.event", "amount", rawDamage);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyDirectHealthLoss(Store<EntityStore> store, Ref<EntityStore> targetRef, float rawDamage) {
        if (store == null || targetRef == null || rawDamage <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "invalid", "rawDamage", rawDamage);
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            LoreDebug.logKv("damage.direct.skip", "reason", "noStatMap");
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            LoreDebug.logKv("damage.direct.skip", "reason", "noHealthStat");
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.get() <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "targetDead");
            return;
        }
        float maxSpendable = Math.max(0f, health.get() - 0.1f);
        float applied = Math.min(rawDamage, maxSpendable);
        if (applied <= 0f) {
            LoreDebug.logKv("damage.direct.skip", "reason", "applied<=0", "maxSpendable", maxSpendable);
            return;
        }
        statMap.addStatValue(healthStatIndex, -applied);
        LoreDebug.logKv("damage.direct", "applied", applied);
    }

    private static float resolveDamageBase(boolean selfIsAttacker, Damage damage, float fallback) {
        if (!selfIsAttacker || damage == null) {
            return fallback;
        }
        float amount = damage.getAmount();
        float initial = damage.getInitialAmount();
        float base = Math.max(amount, initial);
        if (base <= 0f) {
            base = amount;
        }
        if (base <= 0f) {
            base = fallback;
        }
        return base;
    }

    private static float resolveLoreDamageBase(Store<EntityStore> store,
                                               Ref<EntityStore> attackerRef,
                                               boolean selfIsAttacker,
                                               Damage damage,
                                               float fallback) {
        float eventBase = resolveDamageBase(selfIsAttacker, damage, fallback);
        if (eventBase > 0f) {
            return eventBase;
        }
        float refined = resolveWeaponRefinedDamage(store, attackerRef, fallback);
        if (refined > 0f) {
            return refined;
        }
        return fallback;
    }

    private static float resolveWeaponRefinedDamage(Store<EntityStore> store,
                                                    Ref<EntityStore> attackerRef,
                                                    float fallback) {
        if (store == null || attackerRef == null) {
            return fallback;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return fallback;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty() || !ReforgeEquip.isWeapon(stack)) {
            return fallback;
        }
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return fallback;
        }
        double base = EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item);
        if (base <= 0.0d) {
            return fallback;
        }
        SocketData socketData = SocketManager.getSocketData(stack);
        int level = ReforgeEquip.getLevelFromItem(stack);
        double partsMultiplier = resolvePartsDamageMultiplier(stack);
        double refined = EquipmentDamageTooltipMath.computeBuffedWeaponDamage(stack.getItemId(),
                base, level, socketData, partsMultiplier);
        if (refined <= 0.0d) {
            return fallback;
        }
        return (float) refined;
    }

    private static float resolveWeaponBaseDamage(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef) {
        if (store == null || attackerRef == null) {
            return 0f;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return 0f;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty() || !ReforgeEquip.isWeapon(stack)) {
            return 0f;
        }
        Item item = stack.getItem();
        if (item == null || item == Item.UNKNOWN) {
            return 0f;
        }
        double base = EquipmentDamageTooltipMath.getAverageBaseDamageFromInteractionVars(item);
        if (base <= 0.0d) {
            return 0f;
        }
        return (float) base;
    }

    private static double resolvePartsDamageMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0d;
        }
        Double value = stack.getFromMetadataOrNull(META_PARTS_DAMAGE_MULTIPLIER, Codec.DOUBLE);
        if (value == null) {
            return 1.0d;
        }
        return Math.max(0.5d, Math.min(2.0d, value.doubleValue()));
    }

    private static float resolveMaxHealth(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null) {
            return 0f;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return 0f;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return 0f;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null) {
            return 0f;
        }
        return Math.max(0f, health.getMax());
    }

    private static void applyHeal(Store<EntityStore> store, Ref<EntityStore> targetRef, float amount) {
        if (store == null || targetRef == null || amount <= 0f) {
            return;
        }
        EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (statMap == null) {
            return;
        }
        int healthStatIndex = getHealthStatIndex(statMap);
        if (healthStatIndex < 0) {
            return;
        }
        EntityStatValue health = statMap.get(healthStatIndex);
        if (health == null || health.getMax() <= 0f) {
            return;
        }
        float missing = health.getMax() - health.get();
        if (missing <= 0f) {
            return;
        }
        statMap.addStatValue(healthStatIndex, Math.min(missing, amount));
    }

    private static void applyHealOverTime(Store<EntityStore> store,
                                          Ref<EntityStore> sourceRef,
                                          Ref<EntityStore> targetRef,
                                          float totalAmount,
                                          boolean requireInRange,
                                          int feedTier,
                                          double radiusSq) {
        if (store == null || targetRef == null || totalAmount <= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        Long existing = HEAL_HOT_UNTIL.get(targetRef);
        if (existing != null && existing > now) {
            return;
        }
        int ticks = LoreAbility.resolveHotTicks(feedTier);
        long duration = ticks * HEAL_HOT_TICK_MS;
        long until = now + duration;
        HEAL_HOT_UNTIL.put(targetRef, until);
        scheduleHealHotExpiry(targetRef, until, duration);

        float perTick = totalAmount / (float) ticks;
        if (perTick <= 0f) {
            return;
        }
        for (int i = 0; i < ticks; i++) {
            long delay = i * HEAL_HOT_TICK_MS;
            OMNISLASH_SCHEDULER.schedule(() -> queueHealTick(store, sourceRef, targetRef, perTick,
                    requireInRange, until, radiusSq), delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void applyBleedOverTime(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           Ref<EntityStore> targetRef,
                                           float totalAmount,
                                           int feedTier) {
        if (store == null || targetRef == null) {
            return;
        }
        long duration = LoreAbility.scaleDurationMs(BLEED_BASE_DURATION_MS, feedTier);
        int ticks = (int) Math.ceil((double) duration / (double) BLEED_DOT_TICK_MS);
        ticks = Math.max(1, ticks);
        float basePerTick = 0f;
        float maxHealth = resolveMaxHealth(store, targetRef);
        if (maxHealth > 0f) {
            basePerTick = (float) (maxHealth * BLEED_BASE_MAX_HP_PCT);
            basePerTick = (float) LoreAbility.scaleEffectValue(basePerTick, feedTier);
        } else if (totalAmount > 0f) {
            float scaledTotal = (float) LoreAbility.scaleEffectValue(totalAmount, feedTier);
            basePerTick = scaledTotal / (float) ticks;
        }
        if (basePerTick <= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        long until = now + duration;
        BLEED_DOT_UNTIL.merge(targetRef, until, Math::max);
        scheduleBleedExpiry(targetRef, until, duration);

        for (int i = 0; i < ticks; i++) {
            long delay = i * BLEED_DOT_TICK_MS;
            double ramp = 1.0d + (BLEED_RAMP_PER_TICK * i);
            float perTick = (float) (basePerTick * ramp);
            OMNISLASH_SCHEDULER.schedule(() -> queueBleedTick(store, sourceRef, targetRef, perTick, until),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void scheduleBleedExpiry(Ref<EntityStore> targetRef, long expectedUntil, long delayMs) {
        if (targetRef == null || delayMs <= 0L) {
            return;
        }
        OMNISLASH_SCHEDULER.schedule(() -> expireBleed(targetRef, expectedUntil), delayMs,
                TimeUnit.MILLISECONDS);
    }

    private static void expireBleed(Ref<EntityStore> targetRef, long expectedUntil) {
        if (targetRef == null) {
            return;
        }
        Long current = BLEED_DOT_UNTIL.get(targetRef);
        if (current == null || current > expectedUntil) {
            return;
        }
        BLEED_DOT_UNTIL.remove(targetRef);
    }

    private static void queueBleedTick(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float perTick,
                                       long expectedUntil) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyBleedTick(store, sourceRef, targetRef, perTick, expectedUntil));
    }

    private static void applyBleedTick(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float perTick,
                                       long expectedUntil) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        Long current = BLEED_DOT_UNTIL.get(targetRef);
        if (current == null || current.longValue() != expectedUntil) {
            return;
        }
        if (System.currentTimeMillis() > expectedUntil) {
            return;
        }
        applyCombatDamage(store, sourceRef, targetRef, perTick, true);
        LoreVisuals.spawnBleedParticles(store, sourceRef, targetRef, BLEED_PARTICLE_IDS);
    }

    private static void applyDrainOverTime(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           Ref<EntityStore> targetRef,
                                           float totalAmount,
                                           int feedTier) {
        if (store == null || targetRef == null || totalAmount <= 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        Long existing = DRAIN_DOT_UNTIL.get(targetRef);
        if (existing != null && existing > now) {
            return;
        }
        long duration = LoreAbility.scaleDurationMs(DRAIN_BASE_DURATION_MS, feedTier);
        int ticks = (int) Math.ceil((double) duration / (double) DRAIN_DOT_TICK_MS);
        ticks = Math.max(1, ticks);
        float scaledTotal = (float) LoreAbility.scaleEffectValue(totalAmount, feedTier);
        float perTick = scaledTotal / (float) ticks;
        if (perTick <= 0f) {
            return;
        }
        long until = now + duration;
        DRAIN_DOT_UNTIL.put(targetRef, until);
        scheduleDrainExpiry(store, targetRef, until, duration);
        queueDrainVisualEffect(store, targetRef, duration);

        for (int i = 0; i < ticks; i++) {
            long delay = i * DRAIN_DOT_TICK_MS;
            OMNISLASH_SCHEDULER.schedule(() -> queueDrainTick(store, sourceRef, targetRef, perTick, until),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private static void scheduleDrainExpiry(Store<EntityStore> store,
                                            Ref<EntityStore> targetRef,
                                            long expectedUntil,
                                            long delayMs) {
        if (store == null || targetRef == null || delayMs <= 0L) {
            return;
        }
        OMNISLASH_SCHEDULER.schedule(() -> queueDrainExpiry(store, targetRef, expectedUntil), delayMs,
                TimeUnit.MILLISECONDS);
    }

    private static void queueDrainExpiry(Store<EntityStore> store,
                                         Ref<EntityStore> targetRef,
                                         long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            expireDrain(store, targetRef, expectedUntil);
            return;
        }
        entityStore.getWorld().execute(() -> expireDrain(store, targetRef, expectedUntil));
    }

    private static void expireDrain(Store<EntityStore> store,
                                    Ref<EntityStore> targetRef,
                                    long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        Long current = DRAIN_DOT_UNTIL.get(targetRef);
        if (current == null || current > expectedUntil) {
            return;
        }
        DRAIN_DOT_UNTIL.remove(targetRef);
        LoreVisuals.tryRemoveVisualEffectsById(store, targetRef, DRAIN_EFFECT_IDS);
    }

    private static void queueDrainVisualEffect(Store<EntityStore> store,
                                               Ref<EntityStore> targetRef,
                                               long durationMs) {
        if (store == null || targetRef == null || durationMs <= 0L || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            applyDrainVisualEffect(store, targetRef, durationMs);
            return;
        }
        entityStore.getWorld().execute(() -> applyDrainVisualEffect(store, targetRef, durationMs));
    }

    private static void applyDrainVisualEffect(Store<EntityStore> store,
                                               Ref<EntityStore> targetRef,
                                               long durationMs) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        LoreVisuals.tryRemoveVisualEffectsById(store, targetRef, DRAIN_EFFECT_IDS);
        LoreVisuals.tryApplyTimedVisualEffectOverride(store, targetRef, durationMs, DRAIN_EFFECT_IDS);
    }

    private static void queueDrainTick(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float perTick,
                                       long expectedUntil) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyDrainTick(store, sourceRef, targetRef, perTick, expectedUntil));
    }

    private static void applyDrainTick(Store<EntityStore> store,
                                       Ref<EntityStore> sourceRef,
                                       Ref<EntityStore> targetRef,
                                       float perTick,
                                       long expectedUntil) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        Long current = DRAIN_DOT_UNTIL.get(targetRef);
        if (current == null || current.longValue() != expectedUntil) {
            return;
        }
        if (System.currentTimeMillis() > expectedUntil) {
            return;
        }
        applyCombatDamage(store, sourceRef, targetRef, perTick, true);
        if (sourceRef != null && isEntityAlive(store, sourceRef)) {
            applyHeal(store, sourceRef, perTick);
        }
    }

    private static void applyFinaleExplosion(Store<EntityStore> store,
                                             Ref<EntityStore> attackerRef,
                                             Ref<EntityStore> defenderRef,
                                             float fallbackAmount,
                                             int feedTier,
                                             String[] statusEffectIds,
                                             String[] particleIds,
                                             boolean stackStatus,
                                             UUID playerId,
                                             String spiritId,
                                             boolean markTargets) {
        if (store == null || defenderRef == null) {
            return;
        }
        Vector3d center = getPosition(store, defenderRef);
        FinaleMark mark = null;
        if (statusEffectIds == POISON_EFFECT_IDS) {
            mark = getFinaleMark(CAUSTIC_FINALE_MARKS, playerId, spiritId, defenderRef);
        } else if (statusEffectIds == BLEED_EFFECT_IDS) {
            mark = getFinaleMark(SHRAPNEL_FINALE_MARKS, playerId, spiritId, defenderRef);
        } else {
            mark = getFinaleMark(BURN_FINALE_MARKS, playerId, spiritId, defenderRef);
        }
        if (center == null && mark != null) {
            center = mark.position;
        }
        if (center == null) {
            return;
        }
        double radius = LoreAbility.scaleRadius(FINALE_BASE_RADIUS, feedTier);
        double radiusSq = radius * radius;
        LoreVisuals.queueSignatureParticles(store, center, (float) Math.max(0.8d, radius / 3.5d), particleIds);
        Player attackerPlayer = resolvePlayerFromRef(store, attackerRef);
        if (attackerPlayer != null) {
            LoreVisuals.playSignatureSound(attackerPlayer, FINALE_SOUND_IDS);
        }
        List<Ref<EntityStore>> targets = isEntityAlive(store, defenderRef)
                ? collectOmnislashTargets(store, attackerRef, defenderRef, radius, radiusSq, "finale")
                : collectTargetsAtPosition(store, attackerRef, center, radius, radiusSq, "finale");
        if (targets.isEmpty()) {
            return;
        }
        if (DEBUG_LORE_PROCS) {
            String effectLabel = statusEffectIds == POISON_EFFECT_IDS
                    ? "CAUSTIC_FINALE"
                    : (statusEffectIds == BLEED_EFFECT_IDS ? "SHRAPNEL_FINALE" : "BURN_FINALE");
            String defenderLabel = resolveDebugEntityLabel(store, defenderRef);
            Vector3d defenderPos = getPosition(store, defenderRef);
            LoreDebug.logKv("finale.explode",
                    "effect", effectLabel,
                    "target", defenderLabel,
                    "targetPos", formatVector(defenderPos),
                    "center", formatVector(center),
                    "radius", String.format(Locale.ROOT, "%.2f", radius),
                    "targets", targets.size());
        }

        int eligibleCount = 0;
        for (Ref<EntityStore> target : targets) {
            if (target == null || (attackerRef != null && attackerRef.equals(target))) {
                continue;
            }
            if (isEntityDeadOrDying(store, target)) {
                continue;
            }
            eligibleCount++;
        }
        int stackCount = Math.max(1, Math.min(FINALE_MAX_STACKS, eligibleCount));
        DamageBaseResult baseResult =
                resolveLoreDamageBaseResult(store, attackerRef, true, null, fallbackAmount);
        float base = baseResult.amount;
        float perTarget = base > 0f
                ? (float) (base * clampPercentDamage(LoreAbility.scaleEffectValue(FINALE_DAMAGE_PCT, feedTier), 0.10d, 0.90d))
                : fallbackAmount;

        int applied = 0;
        boolean isBleed = statusEffectIds == BLEED_EFFECT_IDS;
        for (Ref<EntityStore> target : targets) {
            if (target == null || (attackerRef != null && attackerRef.equals(target))) {
                continue;
            }
            if (isEntityDeadOrDying(store, target)) {
                continue;
            }
            applyCombatDamage(store, attackerRef, target, perTarget, baseResult.skipRefine);
            if (statusEffectIds != null && statusEffectIds.length > 0) {
                if (stackStatus) {
                    applyEffectStacks(store, target, statusEffectIds, stackCount);
                } else {
                    LoreVisuals.tryApplyVisualEffect(store, target, statusEffectIds);
                }
                if (isBleed) {
                    int bleedStacks = stackStatus ? stackCount : 1;
                    for (int i = 0; i < bleedStacks; i++) {
                        applyBleedOverTime(store, attackerRef, target, fallbackAmount, feedTier);
                    }
                }
            }
            if (markTargets) {
                if (statusEffectIds == POISON_EFFECT_IDS) {
                    markFinaleTarget(CAUSTIC_FINALE_MARKS, playerId, spiritId, target, store, fallbackAmount, feedTier);
                } else if (statusEffectIds == BLEED_EFFECT_IDS) {
                    markFinaleTarget(SHRAPNEL_FINALE_MARKS, playerId, spiritId, target, store, fallbackAmount, feedTier);
                } else {
                    markFinaleTarget(BURN_FINALE_MARKS, playerId, spiritId, target, store, fallbackAmount, feedTier);
                }
            }
            if (++applied >= FINALE_MAX_TARGETS) {
                break;
            }
        }
    }

    private static void applyEffectStacks(Store<EntityStore> store,
                                          Ref<EntityStore> targetRef,
                                          String[] effectIds,
                                          int stacks) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0 || stacks <= 0) {
            return;
        }
        int count = Math.max(1, stacks);
        for (int i = 0; i < count; i++) {
            LoreVisuals.tryApplyVisualEffect(store, targetRef, effectIds);
        }
    }

    private static void markFinaleTargetsOnHit(Store<EntityStore> store,
                                               Player self,
                                               Ref<EntityStore> targetRef,
                                               LoreSocketData data) {
        if (self == null || targetRef == null || data == null) {
            return;
        }
        UUID playerId = self.getUuid();
        if (playerId == null) {
            return;
        }
        for (int i = 0; i < data.getSocketCount(); i++) {
            LoreSocketData.LoreSocket socket = data.getSocket(i);
            if (socket == null || socket.isEmpty() || !socket.hasSpirit()) {
                continue;
            }
            String spiritId = socket.getSpiritId();
            if (spiritId == null || spiritId.isBlank()) {
                continue;
            }
            LoreAbility ability = LoreAbilityRegistry.getAbility(spiritId);
            if (ability == null) {
                continue;
            }
            LoreAbility resolved = applyEffectOverride(ability, socket.getEffectOverride());
            if (resolved == null) {
                continue;
            }
            LoreEffectType effectType = resolved.getEffectType();
            float amount = (float) Math.max(0.0d, resolved.getValueForLevel(socket.getLevel()));
            int feedTier = Math.max(0, socket.getFeedTier());
            if (effectType == LoreEffectType.BURN_FINALE) {
                markFinaleTarget(BURN_FINALE_MARKS, playerId, spiritId, targetRef, store, amount, feedTier);
            } else if (effectType == LoreEffectType.CAUSTIC_FINALE) {
                markFinaleTarget(CAUSTIC_FINALE_MARKS, playerId, spiritId, targetRef, store, amount, feedTier);
            } else if (effectType == LoreEffectType.SHRAPNEL_FINALE) {
                markFinaleTarget(SHRAPNEL_FINALE_MARKS, playerId, spiritId, targetRef, store, amount, feedTier);
            }
        }
    }

    private static void markFinaleTarget(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap,
                                         UUID playerId,
                                         String spiritId,
                                         Ref<EntityStore> targetRef,
                                         Store<EntityStore> store,
                                         float amount,
                                         int feedTier) {
        if (markMap == null || playerId == null || spiritId == null || targetRef == null) {
            return;
        }
        String key = playerId.toString();
        long until = System.currentTimeMillis() + FINALE_MARK_DURATION_MS;
        Map<Ref<EntityStore>, FinaleMark> marks =
                markMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        Vector3d position = getPosition(store, targetRef);
        FinaleMark previous = marks.get(targetRef);
        boolean isNew = previous == null || System.currentTimeMillis() > previous.until;
        marks.put(targetRef, new FinaleMark(until, position, amount, feedTier, spiritId));
        if (DEBUG_LORE_PROCS) {
            String effectLabel = resolveFinaleEffectLabel(markMap);
            String targetLabel = resolveDebugEntityLabel(store, targetRef);
            LoreDebug.logKv("finale.marked",
                    "effect", effectLabel,
                    "target", targetLabel,
                    "pos", formatVector(position));
        }
        if (isNew) {
            scheduleFinaleMarkCheck(store, playerId, targetRef, markMap);
        }
    }

    private static FinaleMark getFinaleMark(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap,
                                            UUID playerId,
                                            String spiritId,
                                            Ref<EntityStore> targetRef) {
        if (markMap == null || playerId == null || spiritId == null || targetRef == null) {
            return null;
        }
        String key = playerId.toString();
        Map<Ref<EntityStore>, FinaleMark> marks = markMap.get(key);
        if (marks == null || marks.isEmpty()) {
            return null;
        }
        FinaleMark mark = marks.get(targetRef);
        if (mark == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now > mark.until) {
            marks.remove(targetRef);
            if (marks.isEmpty()) {
                markMap.remove(key);
            }
            return null;
        }
        return mark;
    }

    private static boolean hasFinaleMark(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap,
                                         UUID playerId,
                                         String spiritId,
                                         Ref<EntityStore> targetRef) {
        return getFinaleMark(markMap, playerId, spiritId, targetRef) != null;
    }

    private static void clearFinaleMark(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap,
                                        UUID playerId,
                                        String spiritId,
                                        Ref<EntityStore> targetRef) {
        if (markMap == null || playerId == null || spiritId == null || targetRef == null) {
            return;
        }
        String key = playerId.toString();
        Map<Ref<EntityStore>, FinaleMark> marks = markMap.get(key);
        if (marks == null || marks.isEmpty()) {
            return;
        }
        marks.remove(targetRef);
        if (marks.isEmpty()) {
            markMap.remove(key);
        }
    }

    private static String resolveFinaleEffectLabel(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (markMap == CAUSTIC_FINALE_MARKS) {
            return "CAUSTIC_FINALE";
        }
        if (markMap == SHRAPNEL_FINALE_MARKS) {
            return "SHRAPNEL_FINALE";
        }
        if (markMap == BURN_FINALE_MARKS) {
            return "BURN_FINALE";
        }
        return "FINALE";
    }

    private static String[] resolveFinaleStatusIds(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (markMap == CAUSTIC_FINALE_MARKS) {
            return POISON_EFFECT_IDS;
        }
        if (markMap == SHRAPNEL_FINALE_MARKS) {
            return BLEED_EFFECT_IDS;
        }
        return BURN_EFFECT_IDS;
    }

    private static String[] resolveFinaleParticleIds(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (markMap == CAUSTIC_FINALE_MARKS) {
            return CAUSTIC_FINALE_PARTICLE_IDS;
        }
        if (markMap == SHRAPNEL_FINALE_MARKS) {
            return SHRAPNEL_FINALE_PARTICLE_IDS;
        }
        return BURN_FINALE_PARTICLE_IDS;
    }

    private static boolean resolveFinaleStackStatus(Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        return markMap != BURN_FINALE_MARKS;
    }

    private static void scheduleFinaleMarkCheck(Store<EntityStore> store,
                                                UUID playerId,
                                                Ref<EntityStore> targetRef,
                                                Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (store == null || playerId == null || targetRef == null || markMap == null) {
            return;
        }
        OMNISLASH_SCHEDULER.schedule(
                () -> queueFinaleMarkCheck(store, playerId, targetRef, markMap),
                FINALE_MARK_CHECK_MS,
                TimeUnit.MILLISECONDS);
    }

    private static void queueFinaleMarkCheck(Store<EntityStore> store,
                                             UUID playerId,
                                             Ref<EntityStore> targetRef,
                                             Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (store == null || playerId == null || targetRef == null || markMap == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> processFinaleMarkCheck(store, playerId, targetRef, markMap));
    }

    private static void processFinaleMarkCheck(Store<EntityStore> store,
                                               UUID playerId,
                                               Ref<EntityStore> targetRef,
                                               Map<String, Map<Ref<EntityStore>, FinaleMark>> markMap) {
        if (store == null || playerId == null || targetRef == null || markMap == null) {
            return;
        }
        FinaleMark mark = getFinaleMark(markMap, playerId, "finale", targetRef);
        if (mark == null) {
            return;
        }
        if (!isEntityDeadOrDying(store, targetRef)) {
            scheduleFinaleMarkCheck(store, playerId, targetRef, markMap);
            return;
        }

        Ref<EntityStore> attackerRef = resolvePlayerRefByUuid(store, playerId);
        String[] statusIds = resolveFinaleStatusIds(markMap);
        String[] particleIds = resolveFinaleParticleIds(markMap);
        boolean stackStatus = resolveFinaleStackStatus(markMap);
        String spiritLabel = mark.spiritId == null || mark.spiritId.isBlank() ? "finale" : mark.spiritId;
        applyFinaleExplosion(store, attackerRef, targetRef, mark.amount, mark.feedTier,
                statusIds, particleIds, stackStatus, playerId, spiritLabel, true);
        clearFinaleMark(markMap, playerId, "finale", targetRef);
    }

    private static Ref<EntityStore> resolvePlayerRefByUuid(Store<EntityStore> store, UUID playerId) {
        if (store == null || playerId == null) {
            return null;
        }
        Query<EntityStore> query = resolvePlayerQuery();
        if (query == null) {
            return null;
        }
        var playerType = Player.getComponentType();
        if (playerType == null) {
            return null;
        }
        final Ref<EntityStore>[] result = new Ref[]{null};
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                Player player = chunk.getComponent(i, playerType);
                if (player == null) {
                    continue;
                }
                UUID uuid = player.getUuid();
                if (playerId.equals(uuid)) {
                    result[0] = chunk.getReferenceTo(i);
                    return false;
                }
            }
            return true;
        });
        return result[0];
    }

    private static Player resolvePlayerFromRef(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return null;
        }
        try {
            return store.getComponent(ref, Player.getComponentType());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isEntityDeadOrDying(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null) {
            return true;
        }
        if (!isEntityAlive(store, ref)) {
            return true;
        }
        try {
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) {
                return false;
            }
            int healthStatIndex = getHealthStatIndex(statMap);
            if (healthStatIndex < 0) {
                return false;
            }
            EntityStatValue health = statMap.get(healthStatIndex);
            if (health == null) {
                return false;
            }
            return health.get() <= 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean hasActiveEffect(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           String... effectIds) {
        if (store == null || targetRef == null || effectIds == null || effectIds.length == 0) {
            return false;
        }
        EffectControllerComponent controller =
                store.getComponent(targetRef, EffectControllerComponent.getComponentType());
        if (controller == null) {
            return false;
        }
        var active = controller.getActiveEffects();
        if (active == null || active.isEmpty()) {
            return false;
        }
        var assetMap = EntityEffect.getAssetMap();
        if (assetMap == null) {
            return false;
        }
        for (String effectId : effectIds) {
            if (effectId == null || effectId.isBlank()) {
                continue;
            }
            int idx = assetMap.getIndexOrDefault(effectId, -1);
            if (idx >= 0 && active.containsKey(idx)) {
                return true;
            }
        }
        return false;
    }

    private static void applyAreaHeal(Store<EntityStore> store,
                                      Ref<EntityStore> sourceRef,
                                      float amount,
                                      int feedTier) {
        if (store == null || sourceRef == null || amount <= 0f) {
            return;
        }
        float scaledAmount = (float) LoreAbility.scaleEffectValue(amount, feedTier);
        double radius = LoreAbility.scaleRadius(HEAL_AREA_RADIUS, feedTier);
        double radiusSq = radius * radius;
        queueSpawnHealTotemParticles(store, sourceRef, radius);
        int ticks = LoreAbility.resolveAreaHealTicks(feedTier);
        for (int i = 0; i < ticks; i++) {
            long delay = i * Math.max(1L, HEAL_AREA_TICK_MS);
            boolean spawnParticles = false;
            OMNISLASH_SCHEDULER.schedule(
                    () -> queueAreaHealPulse(store, sourceRef, scaledAmount, radiusSq, spawnParticles),
                    delay,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private static void applyAreaHealOverTime(Store<EntityStore> store,
                                              Ref<EntityStore> sourceRef,
                                              float amount,
                                              int feedTier) {
        if (store == null || sourceRef == null || amount <= 0f) {
            return;
        }
        float scaledAmount = (float) LoreAbility.scaleEffectValue(amount, feedTier);
        double radius = LoreAbility.scaleRadius(HEAL_AREA_RADIUS, feedTier);
        double radiusSq = radius * radius;
        List<Ref<EntityStore>> targets = collectNearbyPlayers(store, sourceRef, radiusSq);
        for (Ref<EntityStore> targetRef : targets) {
            applyHealOverTime(store, sourceRef, targetRef, scaledAmount, true, feedTier, radiusSq);
        }
    }

    private static void queueAreaHealPulse(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           float amount,
                                           double radiusSq,
                                           boolean spawnParticles) {
        if (store == null || sourceRef == null || amount <= 0f || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyAreaHealPulse(store, sourceRef, amount, radiusSq, spawnParticles));
    }

    private static void applyAreaHealPulse(Store<EntityStore> store,
                                           Ref<EntityStore> sourceRef,
                                           float amount,
                                           double radiusSq,
                                           boolean spawnParticles) {
        if (store == null || sourceRef == null || amount <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, sourceRef)) {
            return;
        }
        if (spawnParticles) {
            spawnHealTotemParticles(store, sourceRef, Math.sqrt(radiusSq));
        }
        List<Ref<EntityStore>> targets = collectNearbyPlayers(store, sourceRef, radiusSq);
        for (Ref<EntityStore> targetRef : targets) {
            applyHeal(store, targetRef, amount);
            LoreVisuals.tryApplyVisualEffect(store, targetRef, HEAL_TOTEM_EFFECT_IDS);
        }
    }

    private static void scheduleHealHotExpiry(Ref<EntityStore> targetRef, long expectedUntil, long delayMs) {
        if (targetRef == null || delayMs <= 0L) {
            return;
        }
        OMNISLASH_SCHEDULER.schedule(() -> expireHealHot(targetRef, expectedUntil), delayMs,
                TimeUnit.MILLISECONDS);
    }

    private static void expireHealHot(Ref<EntityStore> targetRef, long expectedUntil) {
        if (targetRef == null) {
            return;
        }
        Long current = HEAL_HOT_UNTIL.get(targetRef);
        if (current == null || current > expectedUntil) {
            return;
        }
        HEAL_HOT_UNTIL.remove(targetRef);
    }

    private static void queueHealTick(Store<EntityStore> store,
                                      Ref<EntityStore> sourceRef,
                                      Ref<EntityStore> targetRef,
                                      float perTick,
                                      boolean requireInRange,
                                      long hotUntil,
                                      double radiusSq) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyHealTick(store, sourceRef, targetRef, perTick, requireInRange,
                hotUntil, radiusSq));
    }

    private static void applyHealTick(Store<EntityStore> store,
                                      Ref<EntityStore> sourceRef,
                                      Ref<EntityStore> targetRef,
                                      float perTick,
                                      boolean requireInRange,
                                      long hotUntil,
                                      double radiusSq) {
        if (store == null || targetRef == null || perTick <= 0f || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        if (requireInRange && !isWithinHealRange(store, sourceRef, targetRef, radiusSq)) {
            return;
        }
        if (System.currentTimeMillis() > hotUntil) {
            return;
        }
        applyHeal(store, targetRef, perTick);
    }

    private static boolean isWithinHealRange(Store<EntityStore> store,
                                             Ref<EntityStore> sourceRef,
                                             Ref<EntityStore> targetRef,
                                             double radiusSq) {
        if (store == null || sourceRef == null || targetRef == null) {
            return false;
        }
        Vector3d sourcePos = getPosition(store, sourceRef);
        Vector3d targetPos = getPosition(store, targetRef);
        if (sourcePos == null || targetPos == null) {
            return false;
        }
        return distanceSquared(sourcePos, targetPos) <= radiusSq;
    }

    private static List<Ref<EntityStore>> collectNearbyPlayers(Store<EntityStore> store,
                                                               Ref<EntityStore> sourceRef,
                                                               double radiusSq) {
        List<Ref<EntityStore>> targets = new ArrayList<>();
        if (store == null || sourceRef == null) {
            return targets;
        }
        Vector3d center = getPosition(store, sourceRef);
        if (center == null) {
            return targets;
        }
        Query<EntityStore> query = resolvePlayerQuery();
        if (query == null) {
            return targets;
        }
        var playerType = Player.getComponentType();
        var transformType = TransformComponent.getComponentType();
        if (playerType == null || transformType == null) {
            return targets;
        }
        final int[] inspected = new int[]{0};
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                if (inspected[0]++ >= HEAL_MAX_PLAYER_CHECKS) {
                    return false;
                }
                Player player = chunk.getComponent(i, playerType);
                if (player == null) {
                    continue;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) {
                    continue;
                }
                TransformComponent playerTransform = chunk.getComponent(i, transformType);
                if (playerTransform == null) {
                    continue;
                }
                Vector3d pos = playerTransform.getPosition();
                if (pos == null || distanceSquared(center, pos) > radiusSq) {
                    continue;
                }
                if (containsRef(targets, ref)) {
                    continue;
                }
                targets.add(ref);
            }
            return true;
        });
        return targets;
    }

    private static Query<EntityStore> resolvePlayerQuery() {
        Query<EntityStore> cached = playerQuery;
        if (cached != null) {
            return cached;
        }
        synchronized (PLAYER_QUERY_LOCK) {
            cached = playerQuery;
            if (cached != null) {
                return cached;
            }
            var playerType = Player.getComponentType();
            var transformType = TransformComponent.getComponentType();
            if (playerType == null || transformType == null) {
                return null;
            }
            try {
                cached = Archetype.of(playerType, transformType);
                playerQuery = cached;
                return cached;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static int getHealthStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getHealth();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"health", "Health", "HP", "hp"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    private static int getSignatureEnergyStatIndex(EntityStatMap statMap) {
        int byDefault = DefaultEntityStatTypes.getSignatureEnergy();
        if (byDefault >= 0) {
            EntityStatValue value = statMap.get(byDefault);
            if (value != null) return value.getIndex();
        }
        String[] aliases = {"SignatureEnergy", "signatureEnergy", "signature_energy", "signature"};
        for (String alias : aliases) {
            EntityStatValue value = statMap.get(alias);
            if (value != null) return value.getIndex();
        }
        return -1;
    }

    private static long resolveStunFreezeDurationMs(double value, int feedTier) {
        return LoreAbility.resolveStunFreezeDurationMs(value, feedTier);
    }

    private static void applyFrozenForDuration(Store<EntityStore> store, Ref<EntityStore> targetRef, long durationMs) {
        if (store == null || targetRef == null || durationMs <= 0L) {
            return;
        }
        long until = System.currentTimeMillis() + durationMs;
        FROZEN_UNTIL.merge(targetRef, until, Math::max);
        queueFrozenApply(store, targetRef, until);
        queueFrozenVisualEffect(store, targetRef, durationMs);
        OMNISLASH_SCHEDULER.schedule(() -> queueFrozenRemoval(store, targetRef, until),
                durationMs, TimeUnit.MILLISECONDS);
        LoreDebug.logKv("freeze.apply", "durationMs", durationMs);
    }

    private static void queueFrozenApply(Store<EntityStore> store, Ref<EntityStore> targetRef, long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> applyFrozenComponent(store, targetRef, expectedUntil));
    }

    private static void applyFrozenComponent(Store<EntityStore> store,
                                             Ref<EntityStore> targetRef,
                                             long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        if (System.currentTimeMillis() > expectedUntil) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        store.ensureComponent(targetRef, Frozen.getComponentType());
    }

    private static void queueFrozenRemoval(Store<EntityStore> store, Ref<EntityStore> targetRef, long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> removeFrozenIfExpired(store, targetRef, expectedUntil));
    }

    public static boolean tryApplyFrozenShatter(Store<EntityStore> store,
                                                Ref<EntityStore> attackerRef,
                                                Ref<EntityStore> targetRef,
                                                Damage damage) {
        if (store == null || targetRef == null || damage == null) {
            return false;
        }
        if (damage.getAmount() <= 0f) {
            return false;
        }
        if (!isFrozenActive(store, targetRef)) {
            return false;
        }
        damage.setAmount(damage.getAmount() * 2.0f);
        queueFrozenShatter(store, targetRef, attackerRef);
        LoreDebug.log("freeze.shatter", "doubleDamage=true");
        return true;
    }

    private static boolean isFrozenActive(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null) {
            return false;
        }
        Long until = FROZEN_UNTIL.get(targetRef);
        if (until != null && until > System.currentTimeMillis()) {
            return true;
        }
        return store.getComponent(targetRef, Frozen.getComponentType()) != null;
    }

    private static void queueFrozenShatter(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           Ref<EntityStore> attackerRef) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            applyFrozenShatter(store, targetRef, attackerRef);
            return;
        }
        entityStore.getWorld().execute(() -> applyFrozenShatter(store, targetRef, attackerRef));
    }

    private static void applyFrozenShatter(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           Ref<EntityStore> attackerRef) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        FROZEN_UNTIL.remove(targetRef);
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        store.tryRemoveComponent(targetRef, Frozen.getComponentType());
        LoreVisuals.tryRemoveVisualEffectsById(store, targetRef, FREEZE_EFFECT_IDS);
        LoreVisuals.tryRemoveVisualEffectsByContains(store, targetRef, "freeze", "frozen", "ice");
        Vector3d pos = resolveCenterPosition(store, targetRef, attackerRef);
        if (pos != null) {
            pos.add(0.0d, 0.6d, 0.0d);
            String particleId = LoreVisuals.resolveParticleEffectId(SHATTER_PARTICLE_IDS);
            if (particleId != null) {
                LoreVisuals.spawnScaledParticle(store, particleId, pos, 1.4f);
            }
        }
        Player attacker = attackerRef == null ? null : store.getComponent(attackerRef, Player.getComponentType());
        LoreVisuals.playSignatureSound(attacker, "SFX_Ice_Break");
    }

    private static void removeFrozenIfExpired(Store<EntityStore> store, Ref<EntityStore> targetRef, long expectedUntil) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        Long current = FROZEN_UNTIL.get(targetRef);
        if (current == null || current > expectedUntil) {
            return;
        }
        FROZEN_UNTIL.remove(targetRef);
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        store.tryRemoveComponent(targetRef, Frozen.getComponentType());
        LoreVisuals.tryRemoveVisualEffectsById(store, targetRef, FREEZE_EFFECT_IDS);
        LoreVisuals.tryRemoveVisualEffectsByContains(store, targetRef, "freeze", "frozen", "ice");
        LoreDebug.log("freeze.remove", "status=expired");
    }

    private static void queueFrozenVisualEffect(Store<EntityStore> store,
                                                Ref<EntityStore> targetRef,
                                                long durationMs) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            applyFrozenVisualEffect(store, targetRef, durationMs);
            return;
        }
        entityStore.getWorld().execute(() -> applyFrozenVisualEffect(store, targetRef, durationMs));
    }

    private static void applyFrozenVisualEffect(Store<EntityStore> store,
                                                Ref<EntityStore> targetRef,
                                                long durationMs) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        LoreVisuals.tryRemoveVisualEffectsById(store, targetRef, FREEZE_EFFECT_IDS);
        LoreVisuals.tryRemoveVisualEffectsByContains(store, targetRef, "freeze", "frozen", "ice");
        LoreVisuals.tryApplyTimedVisualEffectOverride(store, targetRef, durationMs, FREEZE_EFFECT_IDS);
        Vector3d pos = resolveCenterPosition(store, targetRef, null);
        if (pos != null) {
            pos.add(0.0d, 0.6d, 0.0d);
            String particleId = LoreVisuals.resolveParticleEffectId(FREEZE_PARTICLE_IDS);
            if (particleId != null) {
                LoreVisuals.spawnScaledParticle(store, particleId, pos, 1.0f);
            }
        }
        int pulses = (int) Math.max(1, Math.min(FREEZE_MAX_PULSES, durationMs / FREEZE_PULSE_MS));
        for (int i = 1; i < pulses; i++) {
            long delay = FREEZE_PULSE_MS * i;
            OMNISLASH_SCHEDULER.schedule(
                    () -> spawnFreezePulse(store, targetRef),
                    delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    private static void spawnFreezePulse(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null || store.isShutdown()) {
            return;
        }
        if (!isEntityAlive(store, targetRef)) {
            return;
        }
        if (!isFrozenActive(store, targetRef)) {
            return;
        }
        Vector3d pos = resolveCenterPosition(store, targetRef, null);
        if (pos == null) {
            return;
        }
        pos.add(0.0d, 0.6d, 0.0d);
        String particleId = LoreVisuals.resolveParticleEffectId(FREEZE_PARTICLE_IDS);
        if (particleId != null) {
            LoreVisuals.spawnScaledParticle(store, particleId, pos, 0.85f);
        }
    }

    private static void queueSpawnHealTotemParticles(Store<EntityStore> store,
                                                     Ref<EntityStore> sourceRef,
                                                     double radius) {
        if (store == null || sourceRef == null || store.isShutdown()) {
            return;
        }
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null || entityStore.getWorld() == null) {
            return;
        }
        entityStore.getWorld().execute(() -> spawnHealTotemParticles(store, sourceRef, radius));
    }

    private static void spawnHealTotemParticles(Store<EntityStore> store,
                                                Ref<EntityStore> sourceRef,
                                                double radius) {
        if (store == null || sourceRef == null) {
            return;
        }
        Vector3d pos = getPosition(store, sourceRef);
        if (pos == null) {
            return;
        }
        String particleId = LoreVisuals.resolveParticleEffectId(HEAL_TOTEM_PARTICLE_IDS);
        if (particleId == null) {
            LoreDebug.logKv("heal.missing", "ids", String.join(",", HEAL_TOTEM_PARTICLE_IDS));
            return;
        }
        double baseRadius = Math.max(0.01d, HEAL_AREA_RADIUS);
        double ratio = Math.max(0.35d, Math.min(1.4d, radius / baseRadius));
        try {
            LoreVisuals.spawnScaledParticle(store, particleId, pos, (float) ratio);
            LoreDebug.logKv("heal.spawn", "id", particleId, "scale", String.format(Locale.ROOT, "%.2f", ratio));
        } catch (Throwable ignored) {
            try {
                ParticleUtil.spawnParticleEffect(particleId, pos, store);
            } catch (Throwable ignored2) {
                // best-effort
            }
        }
    }

    private static String normalizeSpiritId(String spiritId) {
        return spiritId.trim().toLowerCase(Locale.ROOT);
    }

    private static double clampPercent(double value, double min, double max) {
        double pct = normalizePercent(value);
        if (pct < min) {
            return min;
        }
        if (pct > max) {
            return max;
        }
        return pct;
    }

    private static double clampPercentDamage(double value, double min, double max) {
        double pct = normalizePercent(value);
        if (DISABLE_LORE_DAMAGE_CLAMPS) {
            return pct;
        }
        if (pct < min) {
            return min;
        }
        if (pct > max) {
            return max;
        }
        return pct;
    }

    private static double normalizePercent(double value) {
        return value <= 1.0d ? value : value / 100.0d;
    }

    private static int calcExtraHits(double value) {
        int hits = 1 + (int) Math.floor(Math.max(0.0d, value));
        return Math.max(1, Math.min(3, hits));
    }
}
