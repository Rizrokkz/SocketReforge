package irai.mod.reforge.Socket;

public class Socket {

    private int    slotIndex;
    private String essenceId; // null = empty socket
    private boolean locked;    // true = socket is locked (failed to add essence)
    private boolean broken;    // true = socket was broken during punching

    public Socket(int slotIndex, String essenceId) {
        this.slotIndex = slotIndex;
        this.essenceId = essenceId;
        this.locked = false;
        this.broken = false;
    }

    public int    getSlotIndex()             { return slotIndex; }
    public String getEssenceId()             { return essenceId; }
    public void   setEssenceId(String id)    { this.essenceId = id; }
    public boolean isEmpty()                  { return essenceId == null; }
    public boolean isLocked()                 { return locked; }
    public void   setLocked(boolean locked)   { this.locked = locked; }
    public boolean isBroken()                 { return broken; }
    public void   setBroken(boolean broken)   { this.broken = broken; }
}