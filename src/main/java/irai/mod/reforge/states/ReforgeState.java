package irai.mod.reforge.states;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class ReforgeState implements Component<ChunkStore> {
    @Override
    public ReforgeState clone() {
        return new ReforgeState();
    }
}
