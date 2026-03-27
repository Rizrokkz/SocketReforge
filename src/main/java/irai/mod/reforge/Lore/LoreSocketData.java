package irai.mod.reforge.Lore;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for lore sockets stored on equipment.
 */
public final class LoreSocketData {
    public static final class LoreSocket {
        private final int slotIndex;
        private String gemItemId;
        private String color;
        private String spiritId;
        private String effectOverride;
        private int level;
        private int xp;
        private int feedTier;
        private boolean locked;

        private LoreSocket(int slotIndex) {
            this.slotIndex = slotIndex;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public String getGemItemId() {
            return gemItemId;
        }

        public void setGemItemId(String gemItemId) {
            this.gemItemId = gemItemId;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getSpiritId() {
            return spiritId;
        }

        public void setSpiritId(String spiritId) {
            this.spiritId = spiritId;
        }

        public String getEffectOverride() {
            return effectOverride;
        }

        public void setEffectOverride(String effectOverride) {
            this.effectOverride = effectOverride;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = Math.max(0, level);
        }

        public int getXp() {
            return xp;
        }

        public void setXp(int xp) {
            this.xp = Math.max(0, xp);
        }

        public int getFeedTier() {
            return feedTier;
        }

        public void setFeedTier(int feedTier) {
            this.feedTier = Math.max(0, feedTier);
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        public boolean isEmpty() {
            return gemItemId == null || gemItemId.isBlank();
        }

        public boolean hasSpirit() {
            return spiritId != null && !spiritId.isBlank();
        }
    }

    private int maxSockets;
    private final List<LoreSocket> sockets;

    public LoreSocketData(int maxSockets) {
        this.maxSockets = Math.max(0, maxSockets);
        this.sockets = new ArrayList<>();
    }

    public int getMaxSockets() {
        return maxSockets;
    }

    public void setMaxSockets(int maxSockets) {
        this.maxSockets = Math.max(0, maxSockets);
    }

    public List<LoreSocket> getSockets() {
        return sockets;
    }

    public int getSocketCount() {
        return sockets.size();
    }

    public LoreSocket getSocket(int index) {
        if (index < 0 || index >= sockets.size()) {
            return null;
        }
        return sockets.get(index);
    }

    public void ensureSocketCount(int count) {
        int target = Math.max(0, count);
        while (sockets.size() < target) {
            sockets.add(new LoreSocket(sockets.size()));
        }
        while (sockets.size() > target && !sockets.isEmpty()) {
            sockets.remove(sockets.size() - 1);
        }
    }
}
