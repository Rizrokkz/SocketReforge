package irai.mod.reforge;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class ReforgeCommand extends CommandBase {


    public ReforgeCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    public ReforgeCommand(){
        super("test", "test fire");
    }
    @Override
    protected void executeSync(@NonNullDecl CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (!(sender instanceof Player)) return;
        sender.sendMessage(Message.raw("Hello"));
    }
}