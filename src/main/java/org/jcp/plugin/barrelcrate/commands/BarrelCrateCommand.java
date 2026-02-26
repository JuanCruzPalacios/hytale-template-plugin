package org.jcp.plugin.barrelcrate.commands;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import org.jcp.plugin.barrelcrate.BarrelCrateRuntime;

import javax.annotation.Nonnull;

public class BarrelCrateCommand extends AbstractCommandCollection {

    public BarrelCrateCommand(@Nonnull BarrelCrateRuntime runtime) {
        super("bc", "barrelcrate.commands.desc");

        addSubCommand((AbstractCommand) new ToggleCommand(runtime));
        addSubCommand((AbstractCommand) new ReloadCommand(runtime));
    }
}