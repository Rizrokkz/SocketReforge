package irai.mod.reforge.UI;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.logger.HytaleLogger;

import irai.mod.DynamicFloatingDamageFormatter.DamageNumbers;

/**
 * Screen-space damage popup HUD mode. Uses dedicated labels per damage lane to avoid color/style bleed.
 */
@SuppressWarnings("removal")
public final class DamagePopupHudUI {
    private static final String HUD_KEY = "socket_reforge_damage_popup";
    private static final String HUD_DOCUMENT = "Hud/DamagePopupHud.ui";
    private static final int SLOT_COUNT = 4;
    private static final long SLOT_DURATION_MILLIS = 1_150L;
    private static final long DOT_SLOT_DURATION_MILLIS = 2_500L;
    private static final HytaleLogger LOGGER = HytaleLogger.get("SocketReforge.DamagePopupHud");

    private static final Map<PlayerRef, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<UUID, DamagePopupHud> activeHuds = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> hudLocks = new ConcurrentHashMap<>();
    private static ScheduledExecutorService hideExecutor;

    private DamagePopupHudUI() {
    }

    public static void show(Player player, float amount, String kindId) {
        showInternal(player, amount, kindId, false);
    }

    public static void showTaken(Player player, float amount, String kindId) {
        showInternal(player, amount, kindId, true);
    }

    private static void showInternal(Player player, float amount, String kindId, boolean taken) {
        if (player == null || player.getPlayerRef() == null || amount <= 0f) {
            return;
        }
        PlayerRef playerRef = player.getPlayerRef();
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        String lane = laneFor(kindId);
        String dotLane = dotLaneFor(lane, kindId);
        long nowMillis = System.currentTimeMillis();
        long expiresAtMillis = nowMillis + (dotLane == null ? SLOT_DURATION_MILLIS : DOT_SLOT_DURATION_MILLIS);
        Object lock = getHudLock(playerUuid);
        synchronized (lock) {
            Session session = sessions.computeIfAbsent(playerRef, ignored -> new Session());
            session.player = player;
            if (taken) {
                session.taken = new TakenPayload(DamageNumbers.formatAmountOnly(amount, kindId), lane, expiresAtMillis);
            } else if (dotLane == null) {
                String text = DamageNumbers.formatAmountOnly(amount, kindId);
                pushNewestToBottom(session, new SlotPayload(text, lane, expiresAtMillis));
            } else {
                DotPayload current = session.dots.get(dotLane);
                double total = (current == null ? 0.0d : current.total) + amount;
                session.dots.put(dotLane, new DotPayload(dotLane, total, kindId, expiresAtMillis));
            }
            DamagePopupHud active = activeHuds.get(playerUuid);
            if (session.hud == null || session.hud != active) {
                DamagePopupHud hud = new DamagePopupHud(playerRef, playerUuid, session);
                session.hud = hud;
                activeHuds.put(playerUuid, hud);
                try {
                    player.getHudManager().removeCustomHud(playerRef, HUD_KEY);
                } catch (Throwable ignored) {
                }
                try {
                    player.getHudManager().addCustomHud(playerRef, hud);
                } catch (Throwable throwable) {
                    activeHuds.remove(playerUuid);
                    session.hud = null;
                    logError("add", throwable);
                }
            } else {
                session.hud.updateAllSlots();
            }
            if (taken) {
                scheduleTakenHide(playerRef, expiresAtMillis);
            } else if (dotLane == null) {
                scheduleSlotHide(playerRef, expiresAtMillis);
            } else {
                scheduleDotHide(playerRef, dotLane, expiresAtMillis);
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

    private static void pushNewestToBottom(Session session, SlotPayload payload) {
        if (session == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT - 1; i++) {
            session.slots[i] = session.slots[i + 1] == null ? SlotPayload.empty() : session.slots[i + 1];
        }
        session.slots[SLOT_COUNT - 1] = payload == null ? SlotPayload.empty() : payload;
    }

    private static void scheduleSlotHide(PlayerRef playerRef, long expectedExpiresAtMillis) {
        getHideExecutor().schedule(() -> {
            Session session = sessions.get(playerRef);
            if (session == null || session.player == null || session.hud == null) {
                return;
            }
            if (!session.hasPayload(expectedExpiresAtMillis)) {
                return;
            }
            try {
                session.player.getWorld().execute(() -> {
                    Session current = sessions.get(playerRef);
                    if (current == null || current.hud == null) {
                        return;
                    }
                    if (!current.clearPayload(expectedExpiresAtMillis)) {
                        return;
                    }
                    current.hud.updateAllSlots();
                    if (current.isEmpty()) {
                        removeSession(playerRef, current);
                    }
                });
            } catch (Throwable ignored) {
                removeSession(playerRef, session);
            }
        }, SLOT_DURATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void scheduleDotHide(PlayerRef playerRef, String dotLane, long expectedExpiresAtMillis) {
        getHideExecutor().schedule(() -> {
            Session session = sessions.get(playerRef);
            if (session == null || session.player == null || session.hud == null || dotLane == null) {
                return;
            }
            DotPayload payload = session.dots.get(dotLane);
            if (payload == null || payload.expiresAtMillis != expectedExpiresAtMillis) {
                return;
            }
            try {
                session.player.getWorld().execute(() -> {
                    Session current = sessions.get(playerRef);
                    if (current == null || current.hud == null) {
                        return;
                    }
                    DotPayload currentPayload = current.dots.get(dotLane);
                    if (currentPayload == null || currentPayload.expiresAtMillis != expectedExpiresAtMillis) {
                        return;
                    }
                    current.dots.remove(dotLane);
                    current.hud.updateAllSlots();
                    if (current.isEmpty()) {
                        removeSession(playerRef, current);
                    }
                });
            } catch (Throwable ignored) {
                removeSession(playerRef, session);
            }
        }, DOT_SLOT_DURATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void scheduleTakenHide(PlayerRef playerRef, long expectedExpiresAtMillis) {
        getHideExecutor().schedule(() -> {
            Session session = sessions.get(playerRef);
            if (session == null || session.player == null || session.hud == null) {
                return;
            }
            TakenPayload payload = session.taken;
            if (payload == null || payload.expiresAtMillis != expectedExpiresAtMillis) {
                return;
            }
            try {
                session.player.getWorld().execute(() -> {
                    Session current = sessions.get(playerRef);
                    if (current == null || current.hud == null) {
                        return;
                    }
                    TakenPayload currentPayload = current.taken;
                    if (currentPayload == null || currentPayload.expiresAtMillis != expectedExpiresAtMillis) {
                        return;
                    }
                    current.taken = TakenPayload.empty();
                    current.hud.updateAllSlots();
                    if (current.isEmpty()) {
                        removeSession(playerRef, current);
                    }
                });
            } catch (Throwable ignored) {
                removeSession(playerRef, session);
            }
        }, SLOT_DURATION_MILLIS, TimeUnit.MILLISECONDS);
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
                Thread thread = new Thread(runnable, "SocketReforge-DamagePopupHud");
                thread.setDaemon(true);
                return thread;
            });
        }
        return hideExecutor;
    }

    private static Object getHudLock(UUID playerUuid) {
        return hudLocks.computeIfAbsent(playerUuid, ignored -> new Object());
    }

    private static String laneFor(String kindId) {
        String normalized = kindId == null ? "" : kindId.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CRITICAL" -> "Critical";
            case "BURN", "FIRE" -> "Fire";
            case "ICE", "FREEZE", "FROST" -> "Ice";
            case "SHOCK", "LIGHTNING" -> "Lightning";
            case "WATER" -> "Water";
            case "LIFE", "HEAL" -> "Life";
            case "VOID", "BLEED" -> "Void";
            case "POISON", "TOXIC" -> "Poison";
            case "SHIELD" -> "Shield";
            default -> "Flat";
        };
    }

    private static String dotLaneFor(String lane, String kindId) {
        String normalized = kindId == null ? "" : kindId.trim().toUpperCase(Locale.ROOT);
        if ("BURN".equals(normalized)) {
            return "Burn";
        }
        if ("BLEED".equals(normalized)) {
            return "Bleed";
        }
        if ("POISON".equals(normalized) || "TOXIC".equals(normalized)) {
            return "Poison";
        }
        if ("DRAIN".equals(normalized) || "LEECH".equals(normalized)) {
            return "Drain";
        }
        return DamageNumbers.isDotKind(kindId) ? lane : null;
    }

    private static String iconLaneFor(String lane) {
        return switch (lane) {
            case "Fire", "Ice", "Lightning", "Water", "Life", "Void" -> lane;
            case "Poison" -> "Life";
            case "Shield" -> "Ice";
            default -> "";
        };
    }

    private record SlotPayload(String text, String lane, long expiresAtMillis) {
        private static SlotPayload empty() {
            return new SlotPayload("", "Flat", 0L);
        }
    }

    private record DotPayload(String dotLane, double total, String kindId, long expiresAtMillis) {
    }

    private record TakenPayload(String text, String lane, long expiresAtMillis) {
        private static TakenPayload empty() {
            return new TakenPayload("", "Flat", 0L);
        }
    }

    private static final class Session {
        private final SlotPayload[] slots = new SlotPayload[SLOT_COUNT];
        private final Map<String, DotPayload> dots = new ConcurrentHashMap<>();
        private TakenPayload taken = TakenPayload.empty();
        private Player player;
        private DamagePopupHud hud;

        private Session() {
            for (int i = 0; i < SLOT_COUNT; i++) {
                slots[i] = SlotPayload.empty();
            }
        }

        private boolean isEmpty() {
            for (SlotPayload slot : slots) {
                if (slot != null && slot.expiresAtMillis > 0L && slot.text != null && !slot.text.isBlank()) {
                    return false;
                }
            }
            return dots.isEmpty()
                    && (taken == null || taken.expiresAtMillis <= 0L || taken.text == null || taken.text.isBlank());
        }

        private boolean hasPayload(long expiresAtMillis) {
            for (SlotPayload slot : slots) {
                if (slot != null && slot.expiresAtMillis == expiresAtMillis) {
                    return true;
                }
            }
            return false;
        }

        private boolean clearPayload(long expiresAtMillis) {
            for (int i = 0; i < slots.length; i++) {
                SlotPayload slot = slots[i];
                if (slot != null && slot.expiresAtMillis == expiresAtMillis) {
                    slots[i] = SlotPayload.empty();
                    return true;
                }
            }
            return false;
        }
    }

    private static final class DamagePopupHud extends CustomUIHud {
        private static final String[] LANES = {
                "Flat", "Critical", "Fire", "Ice", "Lightning", "Water", "Life", "Void", "Poison", "Shield"
        };
        private static final String[] ICON_LANES = {
                "Fire", "Ice", "Lightning", "Water", "Life", "Void"
        };
        private static final String[] DOT_LANES = {
                "Burn", "Bleed", "Poison", "Drain"
        };

        private final Map<String, Object> lastUiState = new ConcurrentHashMap<>();
        private final AtomicBoolean built = new AtomicBoolean(false);
        private final UUID playerUuid;
        private final Session session;

        private DamagePopupHud(PlayerRef playerRef, UUID playerUuid, Session session) {
            super(playerRef, HUD_KEY);
            this.playerUuid = playerUuid;
            this.session = session;
        }

        private void updateSlot(int slot) {
            Object lock = getHudLock(playerUuid);
            synchronized (lock) {
                if (activeHuds.get(playerUuid) != this || !built.get()) {
                    return;
                }
                UICommandBuilder cmd = new UICommandBuilder();
                if (applySlot(cmd, slot)) {
                    try {
                        update(false, cmd);
                    } catch (Throwable throwable) {
                        logError("update", throwable);
                    }
                }
            }
        }

        private void updateAllSlots() {
            Object lock = getHudLock(playerUuid);
            synchronized (lock) {
                if (activeHuds.get(playerUuid) != this || !built.get()) {
                    return;
                }
                UICommandBuilder cmd = new UICommandBuilder();
                boolean changed = false;
                changed |= applyTakenSlot(cmd);
                for (int i = 0; i < SLOT_COUNT; i++) {
                    changed |= applySlot(cmd, i);
                }
                changed |= applyDotSlots(cmd);
                if (changed) {
                    try {
                        update(false, cmd);
                    } catch (Throwable throwable) {
                        logError("update", throwable);
                    }
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
            applyTakenSlot(cmd);
            applyDotSlots(cmd);
            for (int i = 0; i < SLOT_COUNT; i++) {
                applySlot(cmd, i);
            }
        }

        private boolean applyTakenSlot(UICommandBuilder cmd) {
            TakenPayload payload = session.taken == null ? TakenPayload.empty() : session.taken;
            String lane = payload.lane == null ? "Flat" : payload.lane;
            boolean changed = false;
            for (String candidate : LANES) {
                boolean visible = candidate.equals(lane) && payload.text != null && !payload.text.isBlank();
                changed |= setVisible(cmd, "#DamageTaken" + candidate + ".Visible", visible);
                changed |= setText(cmd, "#DamageTaken" + candidate + ".Text", visible ? "-" + payload.text : "");
            }
            return changed;
        }

        private boolean applyDotSlots(UICommandBuilder cmd) {
            boolean changed = false;
            for (String dotLane : DOT_LANES) {
                DotPayload payload = session.dots.get(dotLane);
                String text = payload == null ? "" : dotText(payload);
                boolean visible = payload != null && payload.total > 0.0d;
                changed |= setVisible(cmd, "#DamagePopupDot" + dotLane + "Icon.Visible", visible);
                changed |= setVisible(cmd, "#DamagePopupDot" + dotLane + ".Visible", visible);
                changed |= setText(cmd, "#DamagePopupDot" + dotLane + ".Text", visible ? text : "");
            }
            return changed;
        }

        private String dotText(DotPayload payload) {
            return DamageNumbers.formatAmountOnly(payload.total, payload.kindId);
        }

        private boolean applySlot(UICommandBuilder cmd, int slot) {
            if (slot < 0 || slot >= SLOT_COUNT) {
                return false;
            }
            SlotPayload payload = session.slots[slot] == null ? SlotPayload.empty() : session.slots[slot];
            String lane = payload.lane == null ? "Flat" : payload.lane;
            String iconLane = iconLaneFor(lane);
            boolean changed = false;
            for (String candidate : LANES) {
                boolean visible = candidate.equals(lane) && payload.text != null && !payload.text.isBlank();
                changed |= setVisible(cmd, "#DamagePopupSlot" + slot + candidate + ".Visible", visible);
                changed |= setText(cmd, "#DamagePopupSlot" + slot + candidate + ".Text", visible ? payload.text : "");
            }
            for (String candidate : ICON_LANES) {
                boolean visible = candidate.equals(iconLane) && payload.text != null && !payload.text.isBlank();
                changed |= setVisible(cmd, "#DamagePopupSlot" + slot + "Icon" + candidate + ".Visible", visible);
            }
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

        private boolean setVisible(UICommandBuilder cmd, String selector, boolean visible) {
            Object previous = lastUiState.get(selector);
            if (previous instanceof Boolean previousValue && previousValue == visible) {
                return false;
            }
            lastUiState.put(selector, visible);
            cmd.set(selector, visible);
            return true;
        }
    }

    private static void logError(String action, Throwable throwable) {
        try {
            String message = throwable == null ? "null" : throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            LOGGER.at(Level.SEVERE).log("[SocketReforge] DamagePopupHudUI " + action + " error: " + message);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } catch (Throwable ignored) {
        }
    }
}
