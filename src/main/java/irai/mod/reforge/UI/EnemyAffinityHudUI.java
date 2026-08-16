package irai.mod.reforge.UI;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import irai.mod.reforge.Common.ElementalAffinityUtils;
import irai.mod.reforge.Common.EnemyElementalShieldUtils;
import irai.mod.reforge.Lore.LoreDamageUtils;
import irai.mod.reforge.Socket.Essence;

/**
 * Native .ui based enemy affinity HUD shown when a player damages an NPC.
 */
@SuppressWarnings("removal")
public final class EnemyAffinityHudUI {
    private static final boolean ENABLED = true;
    private static final String HUD_KEY = "socket_reforge_enemy_affinity";
    private static final String HUD_DOCUMENT = "Hud/EnemyAffinityHud.ui";
    private static final long DISPLAY_DURATION_NANOS = 5_000_000_000L;
    private static final long REFRESH_INTERVAL_MILLIS = 250L;
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat PERCENT_FORMAT =
            new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static final Map<PlayerRef, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<UUID, NativeEnemyAffinityHud> activeHuds = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> hudLocks = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private static ScheduledExecutorService hideExecutor;

    private EnemyAffinityHudUI() {
    }

    public static void initialize() {
        System.out.println("[SocketReforge] EnemyAffinityHudUI: enabled.");
    }

    public static void showDamage(Store<EntityStore> store,
                                  Ref<EntityStore> attackerRef,
                                  Ref<EntityStore> targetRef,
                                  float damageAmount,
                                  String damageKindId,
                                  float shieldDamageAmount,
                                  boolean shieldActiveHit) {
        if (!ENABLED) {
            return;
        }
        if (store == null || attackerRef == null || targetRef == null
                || (damageAmount <= 0f && shieldDamageAmount <= 0f && !shieldActiveHit)) {
            return;
        }
        Player attacker;
        try {
            attacker = store.getComponent(attackerRef, Player.getComponentType());
        } catch (IllegalStateException ignored) {
            return;
        }
        if (attacker == null || attacker.getPlayerRef() == null || NPCEntity.getComponentType() == null) {
            return;
        }
        NPCEntity npc = getNpcOrNull(store, targetRef);
        if (npc == null) {
            return;
        }

        PlayerRef playerRef = attacker.getPlayerRef();
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        if (isDisabled(playerUuid)) {
            return;
        }
        Object lock = getHudLock(playerUuid);
        synchronized (lock) {
            Session session = sessions.computeIfAbsent(playerRef, ignored -> new Session());
            session.player = attacker;
            session.store = store;
            session.targetRef = targetRef;
            session.damageKindId = damageKindId;
            session.expiresAtNanos = System.nanoTime() + DISPLAY_DURATION_NANOS;

            try {
                HudPayload payload = buildPayload(store, targetRef, damageAmount, damageKindId, shieldDamageAmount, shieldActiveHit, false);
                if (payload == null) {
                    return;
                }
                NativeEnemyAffinityHud active = activeHuds.get(playerUuid);
                if (session.hud == null || session.hud != active) {
                    NativeEnemyAffinityHud hud = new NativeEnemyAffinityHud(playerRef, playerUuid, payload);
                    session.hud = hud;
                    activeHuds.put(playerUuid, hud);
                    attacker.getHudManager().removeCustomHud(playerRef, HUD_KEY);
                    attacker.getHudManager().addCustomHud(playerRef, hud);
                } else {
                    session.hud.updatePayload(payload);
                }
                scheduleHide(playerRef, session.expiresAtNanos);
                scheduleRefresh(playerRef, session.expiresAtNanos);
            } catch (Throwable throwable) {
                logError("update", throwable);
            }
        }
    }

    public static void closeForDisconnect(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        Session session = sessions.remove(playerRef);
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid != null) {
            activeHuds.remove(playerUuid);
            hudLocks.remove(playerUuid);
        }
        if (session != null && session.player != null) {
            try {
                session.player.getHudManager().removeCustomHud(playerRef, HUD_KEY);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isEnabled(Player player) {
        if (player == null || player.getPlayerRef() == null || player.getPlayerRef().getUuid() == null) {
            return true;
        }
        return !isDisabled(player.getPlayerRef().getUuid());
    }

    public static boolean setEnabled(Player player, boolean enabled) {
        if (player == null || player.getPlayerRef() == null || player.getPlayerRef().getUuid() == null) {
            return true;
        }
        UUID playerUuid = player.getPlayerRef().getUuid();
        if (enabled) {
            disabledPlayers.remove(playerUuid);
        } else {
            disabledPlayers.add(playerUuid);
            closeForDisconnect(player.getPlayerRef());
        }
        return enabled;
    }

    public static boolean toggle(Player player) {
        return setEnabled(player, !isEnabled(player));
    }

    private static boolean isDisabled(UUID playerUuid) {
        return playerUuid != null && disabledPlayers.contains(playerUuid);
    }

    private static HudPayload buildPayload(Store<EntityStore> store,
                                           Ref<EntityStore> targetRef,
                                           float damageAmount,
                                           String damageKindId,
                                           float shieldDamageAmount,
                                           boolean shieldActiveHit,
                                           boolean predictDamage) {
        if (store == null || targetRef == null) {
            return null;
        }
        try {
            NPCEntity npc = getNpcOrNull(store, targetRef);
            if (npc == null) {
                return null;
            }
            String enemyName = npcName(npc);
            float maxHp = LoreDamageUtils.resolveMaxHealth(store, targetRef);
            float currentHp = LoreDamageUtils.resolveCurrentHealth(store, targetRef);
            if (predictDamage && damageAmount > 0f) {
                currentHp = Math.max(0f, currentHp - damageAmount);
            }
            double hpValue = maxHp <= 0f ? 0.0d : Math.max(0.0d, Math.min(1.0d, currentHp / maxHp));
            String hpText = maxHp <= 0f
                    ? "HP -"
                    : NUMBER_FORMAT.format(currentHp) + " / " + NUMBER_FORMAT.format(maxHp);
            Map<Essence.Type, Double> multipliers = ElementalAffinityUtils.resolveElementMultipliers(store, targetRef);
            double elementalShield = EnemyElementalShieldUtils.shieldRatio(store, targetRef);
            return new HudPayload(
                    enemyName,
                    hpValue,
                    hpText,
                    lastDamageText(damageAmount, shieldDamageAmount, shieldActiveHit),
                    shieldDamageAmount > 0f || shieldActiveHit ? "#70e4ff" : damageColor(damageKindId),
                    multipliers,
                    elementalShield);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static NPCEntity getNpcOrNull(Store<EntityStore> store, Ref<EntityStore> targetRef) {
        if (store == null || targetRef == null || NPCEntity.getComponentType() == null) {
            return null;
        }
        try {
            return store.getComponent(targetRef, NPCEntity.getComponentType());
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static void scheduleHide(PlayerRef playerRef, long expectedExpiresAtNanos) {
        getHideExecutor().schedule(() -> {
            Session session = sessions.get(playerRef);
            if (session == null || session.expiresAtNanos != expectedExpiresAtNanos || session.player == null) {
                return;
            }
            try {
                session.player.getWorld().execute(() -> {
                    Session current = sessions.get(playerRef);
                    if (current == null || current.expiresAtNanos != expectedExpiresAtNanos || current.player == null) {
                        return;
                    }
                    current.player.getHudManager().removeCustomHud(playerRef, HUD_KEY);
                    sessions.remove(playerRef);
                    UUID playerUuid = playerRef.getUuid();
                    if (playerUuid != null) {
                        activeHuds.remove(playerUuid);
                        hudLocks.remove(playerUuid);
                    }
                });
            } catch (Throwable ignored) {
                sessions.remove(playerRef);
                UUID playerUuid = playerRef.getUuid();
                if (playerUuid != null) {
                    activeHuds.remove(playerUuid);
                    hudLocks.remove(playerUuid);
                }
            }
        }, DISPLAY_DURATION_NANOS, TimeUnit.NANOSECONDS);
    }

    private static void scheduleRefresh(PlayerRef playerRef, long expectedExpiresAtNanos) {
        getHideExecutor().schedule(() -> {
            Session session = sessions.get(playerRef);
            if (session == null
                    || session.expiresAtNanos != expectedExpiresAtNanos
                    || session.player == null
                    || session.hud == null
                    || session.store == null
                    || session.targetRef == null
                    || System.nanoTime() >= expectedExpiresAtNanos) {
                return;
            }
            try {
                session.player.getWorld().execute(() -> {
                    Session current = sessions.get(playerRef);
                    if (current == null
                            || current.expiresAtNanos != expectedExpiresAtNanos
                            || current.hud == null
                            || current.store == null
                            || current.targetRef == null
                            || System.nanoTime() >= expectedExpiresAtNanos) {
                        return;
                    }
                    try {
                        HudPayload refreshed = buildPayload(
                                current.store,
                                current.targetRef,
                                0f,
                                current.damageKindId,
                                0f,
                                false,
                                false);
                        if (refreshed == null) {
                            removeSession(playerRef, current);
                            return;
                        }
                        HudPayload previous = current.hud.payload;
                        HudPayload payload = previous == null
                                ? refreshed
                                : new HudPayload(
                                        refreshed.enemyName(),
                                        refreshed.hpValue(),
                                        refreshed.hpText(),
                                        previous.lastDamageText(),
                                        previous.lastDamageColor(),
                                        refreshed.multipliers(),
                                        refreshed.elementalShield());
                        current.hud.updatePayload(payload);
                    } catch (IllegalStateException ignored) {
                        removeSession(playerRef, current);
                        return;
                    } catch (Throwable throwable) {
                        logError("refresh", throwable);
                    }
                    scheduleRefresh(playerRef, expectedExpiresAtNanos);
                });
            } catch (Throwable ignored) {
                // If the player/world is no longer valid, the hide task will clean this up.
            }
        }, REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void removeSession(PlayerRef playerRef, Session session) {
        sessions.remove(playerRef);
        UUID playerUuid = playerRef == null ? null : playerRef.getUuid();
        if (playerUuid != null) {
            activeHuds.remove(playerUuid);
            hudLocks.remove(playerUuid);
        }
        if (session != null && session.player != null && playerRef != null) {
            try {
                session.player.getHudManager().removeCustomHud(playerRef, HUD_KEY);
            } catch (Throwable ignored) {
            }
        }
    }

    private static synchronized ScheduledExecutorService getHideExecutor() {
        if (hideExecutor == null) {
            hideExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SocketReforge-EnemyAffinityHud");
                thread.setDaemon(true);
                return thread;
            });
        }
        return hideExecutor;
    }

    private static Object getHudLock(UUID playerUuid) {
        return hudLocks.computeIfAbsent(playerUuid, ignored -> new Object());
    }

    private static String npcName(NPCEntity npc) {
        Role role = npc.getRole();
        String[] candidates = {
                role == null ? null : role.getLabel(),
                role == null ? null : role.getAppearanceName(),
                npc.getRoleName(),
                role == null ? null : role.getRoleName(),
                npc.getNPCTypeId()
        };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return prettifyId(candidate);
            }
        }
        return "Enemy";
    }

    private static String prettifyId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return "Enemy";
        }
        String[] parts = trimmed.replace(':', '_').replace('-', '_').split("_+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? trimmed : sb.toString();
    }

    private static String lastDamageText(float damageAmount, float shieldDamageAmount, boolean shieldActiveHit) {
        if (shieldDamageAmount > 0f && damageAmount > 0f) {
            return "S-" + NUMBER_FORMAT.format(shieldDamageAmount)
                    + " HP-" + NUMBER_FORMAT.format(damageAmount);
        }
        if (shieldDamageAmount > 0f) {
            return "S-" + NUMBER_FORMAT.format(shieldDamageAmount);
        }
        if (shieldActiveHit && damageAmount <= 0f) {
            return "Guarded";
        }
        return damageAmount <= 0f ? "" : "HP-" + NUMBER_FORMAT.format(damageAmount);
    }

    private static String damageColor(String kindId) {
        if (kindId == null) {
            return "#ffffff";
        }
        return switch (kindId.toLowerCase(Locale.ROOT)) {
            case "fire", "burn" -> "#ff8a3d";
            case "water" -> "#4dbbff";
            case "ice", "freeze" -> "#70e4ff";
            case "lightning", "shock" -> "#ffe45c";
            case "life" -> "#55e05a";
            case "void", "bleed" -> "#a852ff";
            case "critical" -> "#ff4d4d";
            default -> "#ffffff";
        };
    }

    private static String multiplierColor(double multiplier) {
        if (multiplier > 1.0001d) {
            return "#ffd36a";
        }
        if (multiplier < 0.9999d) {
            return "#9ab0c8";
        }
        return "#ffffff";
    }

    private static String affinityPercentText(double multiplier) {
        double percent = (multiplier - ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER) * 100.0d;
        if (percent > 0.05d) {
            return "+" + PERCENT_FORMAT.format(percent) + "%";
        }
        if (percent < -0.05d) {
            return PERCENT_FORMAT.format(percent) + "%";
        }
        return "0%";
    }

    private static void logError(String action, Throwable throwable) {
        Throwable safe = throwable == null ? new IllegalStateException("unknown") : throwable;
        System.err.println("[SocketReforge] EnemyAffinityHudUI " + action + " error: "
                + safe.getClass().getSimpleName() + ": " + safe.getMessage());
        safe.printStackTrace();
    }

    private record HudPayload(String enemyName,
                              double hpValue,
                              String hpText,
                              String lastDamageText,
                              String lastDamageColor,
                              Map<Essence.Type, Double> multipliers,
                              double elementalShield) {
    }

    private static final class Session {
        private Player player;
        private NativeEnemyAffinityHud hud;
        private Store<EntityStore> store;
        private Ref<EntityStore> targetRef;
        private String damageKindId;
        private long expiresAtNanos;
    }

    private static final class NativeEnemyAffinityHud extends CustomUIHud {
        private final Map<String, Object> lastUiState = new ConcurrentHashMap<>();
        private final AtomicBoolean built = new AtomicBoolean(false);
        private final UUID playerUuid;
        private HudPayload payload;

        private NativeEnemyAffinityHud(PlayerRef playerRef, UUID playerUuid, HudPayload payload) {
            super(playerRef, HUD_KEY);
            this.playerUuid = playerUuid;
            this.payload = payload;
        }

        private void updatePayload(HudPayload payload) {
            Object lock = getHudLock(playerUuid);
            synchronized (lock) {
                if (activeHuds.get(playerUuid) != this || !built.get()) {
                    return;
                }
                this.payload = payload;
                UICommandBuilder cmd = new UICommandBuilder();
                if (applyPayload(cmd)) {
                    update(false, cmd);
                }
            }
        }

        @Override
        protected void build(UICommandBuilder cmd) {
            if (activeHuds.get(playerUuid) != this) {
                return;
            }
            if (!built.compareAndSet(false, true)) {
                return;
            }
            cmd.append(HUD_DOCUMENT);
            applyPayload(cmd);
        }

        private boolean applyPayload(UICommandBuilder cmd) {
            HudPayload safePayload = payload == null
                    ? new HudPayload("Enemy", 0.0d, "HP -", "", "#ffffff", Map.of(), 0.0d)
                    : payload;
            boolean changed = false;
            changed |= setText(cmd, "#EnemyName.Text", safePayload.enemyName());
            changed |= setText(cmd, "#EnemyHpText.Text", safePayload.hpText());
            changed |= setText(cmd, "#LastDamage.Text", safePayload.lastDamageText());
            changed |= setText(cmd, "#LastDamage.Style.TextColor", safePayload.lastDamageColor());
            changed |= setDouble(cmd, "#EnemyHpBar.Value", safePayload.hpValue());
            changed |= setDouble(cmd, "#EnemyShieldBar.Value", Math.max(0.0d, Math.min(1.0d, safePayload.elementalShield())));

            Map<Essence.Type, Double> multipliers = safePayload.multipliers() == null
                    ? new EnumMap<>(Essence.Type.class)
                    : safePayload.multipliers();
            changed |= applyAffinity(cmd, Essence.Type.FIRE, "Fire", multipliers);
            changed |= applyAffinity(cmd, Essence.Type.ICE, "Ice", multipliers);
            changed |= applyAffinity(cmd, Essence.Type.LIGHTNING, "Lightning", multipliers);
            changed |= applyAffinity(cmd, Essence.Type.LIFE, "Life", multipliers);
            changed |= applyAffinity(cmd, Essence.Type.VOID, "Void", multipliers);
            changed |= applyAffinity(cmd, Essence.Type.WATER, "Water", multipliers);
            return changed;
        }

        private boolean applyAffinity(UICommandBuilder cmd,
                                      Essence.Type type,
                                      String idPrefix,
                                      Map<Essence.Type, Double> multipliers) {
            double multiplier = multipliers.getOrDefault(type, ElementalAffinityUtils.DEFAULT_NEUTRAL_MULTIPLIER);
            boolean changed = false;
            changed |= setText(cmd, "#Affinity" + idPrefix + "Value.Text", affinityPercentText(multiplier));
            changed |= setText(cmd, "#Affinity" + idPrefix + "Value.Style.TextColor", multiplierColor(multiplier));
            return changed;
        }

        private boolean setText(UICommandBuilder cmd, String selector, String value) {
            String safeValue = value == null ? "" : value;
            if (Objects.equals(lastUiState.get(selector), safeValue)) {
                return false;
            }
            lastUiState.put(selector, safeValue);
            cmd.set(selector, safeValue);
            return true;
        }

        private boolean setDouble(UICommandBuilder cmd, String selector, double value) {
            double safeValue = Double.isFinite(value) ? value : 0.0d;
            Object previous = lastUiState.get(selector);
            if (previous instanceof Double previousValue && Math.abs(previousValue - safeValue) < 0.0001d) {
                return false;
            }
            lastUiState.put(selector, safeValue);
            cmd.set(selector, safeValue);
            return true;
        }

    }
}
