package org.jcp.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jcp.plugin.ExplosivesPackPlugin;
import org.jcp.plugin.api.ExplosivesPackApi;

import javax.annotation.Nonnull;

public final class ExplosivesPackInfoCommand extends AbstractPlayerCommand {

    @Nonnull
    private final ExplosivesPackPlugin plugin;

    public ExplosivesPackInfoCommand(@Nonnull ExplosivesPackPlugin plugin) {
        super("explosivespack", "Shows ExplosivesPack mod/version information.");
        this.plugin = plugin;

        // alias cortito
        addAliases("ep");
        addAliases("hyrust");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String modName = plugin.getManifest().getName();
        String modVersion = String.valueOf(plugin.getManifest().getVersion());

        String msg =
                "§a" + modName + "§r v" + modVersion +
                        " | api=" + ExplosivesPackApi.getApiVersion() +
                        " | active=" + ExplosivesPackApi.isActive() +
                        " | booted=" + ExplosivesPackApi.isBooted();

        context.sendMessage(Message.raw(msg));
    }
}