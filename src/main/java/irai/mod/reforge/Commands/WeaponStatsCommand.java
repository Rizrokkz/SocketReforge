package irai.mod.reforge.Commands;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import irai.mod.reforge.UI.WeaponStatsUI;

import irai.mod.reforge.Interactions.ReforgeEquip;


/**
 * Command to check weapon upgrade stats.
 * Usage: /weaponstats
 */
public class WeaponStatsCommand extends AbstractPlayerCommand {

    public WeaponStatsCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Player player = commandContext.senderAs(Player.class);

        PacketHandler packetHandler = playerRef.getPacketHandler();

        // Code moved from OpenGuiListener
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage page = player.getPageManager().getCustomPage();
                if (page == null) {
                    page = new WeaponStatsUI(playerRef, CustomPageLifetime.CanDismiss);
                    player.getPageManager().openCustomPage(ref, store, page);
                }
            }
        }, world);
    }
}
