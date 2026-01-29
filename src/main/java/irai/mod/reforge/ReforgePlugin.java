package irai.mod.reforge;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import irai.mod.reforge.Entity.Events.EquipmentRefineEST;
import irai.mod.reforge.Interactions.ReforgeEquip;
import javax.annotation.Nonnull;
import java.util.logging.Logger;


public class ReforgePlugin extends JavaPlugin {

    private final EquipmentRefineEST refineEST;

    public ReforgePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        refineEST = new EquipmentRefineEST();
    }

    @Override
    protected void setup() {
        try {
            this.getCodecRegistry(Interaction.CODEC).register("ReforgeEquip", ReforgeEquip.class, ReforgeEquip.CODEC);
            Logger.getLogger("System: ").info("ReforgeEquip Loaded!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            this.getEntityStoreRegistry().registerSystem(refineEST);
            Logger.getLogger("System: ").info("Equipment Refine Damage Event Loaded!");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new ReforgeCommand());
    }

    @Override
    protected void start() {

    }
}