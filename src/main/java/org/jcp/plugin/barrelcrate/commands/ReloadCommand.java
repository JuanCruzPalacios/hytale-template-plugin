package org.jcp.plugin.barrelcrate.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jcp.plugin.barrelcrate.BarrelCrateRuntime;

import javax.annotation.Nonnull;

public class ReloadCommand extends AbstractPlayerCommand {

    private final BarrelCrateRuntime runtime;

    public ReloadCommand(@Nonnull BarrelCrateRuntime runtime) {
        super("reload", "barrelcrate.commands.reload.desc");
        this.runtime = runtime;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        CommandUtil.requirePermission((PermissionHolder) context.sender(), HytalePermissions.fromCommand("bc"));

        runtime.reloadConfig();
        context.sendMessage(Message.raw("BarrelCrate: config reloaded."));
    }
}