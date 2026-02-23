package irai.mod.reforge.Socket;

public class Socket {

    private int    slotIndex;
    private String essenceId; // null = empty socket

    public Socket(int slotIndex, String essenceId) {
        this.slotIndex = slotIndex;
        this.essenceId = essenceId;
    }

    public int    getSlotIndex()             { return slotIndex; }
    public String getEssenceId()             { return essenceId; }
    public void   setEssenceId(String id)    { this.essenceId = id; }
    public boolean isEmpty()                 { return essenceId == null; }
}