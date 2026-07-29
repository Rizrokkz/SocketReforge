package irai.mod.reforge.Socket;

import java.util.Locale;
import java.util.Objects;

public class Socket {

    private int    slotIndex;
    private String essenceId; // null = empty socket
    private String mutationElement; // null = use essence type for affinity
    private boolean locked;    // true = socket is locked (failed to add essence)
    private boolean broken;    // true = socket was broken during punching

    public Socket(int slotIndex, String essenceId) {
        this.slotIndex = slotIndex;
        this.essenceId = essenceId;
        this.mutationElement = null;
        this.locked = false;
        this.broken = false;
    }

    public int    getSlotIndex()             { return slotIndex; }
    public String getEssenceId()             { return essenceId; }
    public void   setEssenceId(String id)    {
        if (!Objects.equals(this.essenceId, id)) {
            this.mutationElement = null;
        }
        this.essenceId = id;
        if (id == null || id.isBlank()) {
            this.mutationElement = null;
        }
    }
    public boolean isEmpty()                  { return essenceId == null; }
    public String getMutationElement()        { return mutationElement; }
    public void   setMutationElement(String mutationElement) {
        if (mutationElement == null || mutationElement.isBlank()) {
            this.mutationElement = null;
            return;
        }
        this.mutationElement = mutationElement.trim().toUpperCase(Locale.ROOT);
    }
    public boolean isLocked()                 { return locked; }
    public void   setLocked(boolean locked)   { this.locked = locked; }
    public boolean isBroken()                 { return broken; }
    public void   setBroken(boolean broken)   {
        this.broken = broken;
        if (broken) {
            this.mutationElement = null;
        }
    }
}
