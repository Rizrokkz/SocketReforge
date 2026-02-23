package irai.mod.reforge.Commands;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import irai.mod.reforge.UI.EssenceSocketUI;

/**
 * Command to open the Essence Socketing UI.
 * Usage: /essencesocket
 */
public class EssenceSocketCommand extends AbstractPlayerCommand {

    public EssenceSocketCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = commandContext.senderAs(Player.class);

        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage page = 
                            player.getPageManager().getCustomPage();
                    if (page == null) {
                        page = new EssenceSocketUI(playerRef);
                        player.getPageManager().openCustomPage(ref, store, page);
                    }
                } catch (Exception e) {
                    System.err.println("[EssenceSocketCommand] Error opening UI: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, world);
    }
}
