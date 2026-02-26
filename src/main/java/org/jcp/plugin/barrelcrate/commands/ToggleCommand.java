package org.jcp.plugin.barrelcrate.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgumentType;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jcp.plugin.barrelcrate.BarrelCrateRuntime;

import javax.annotation.Nonnull;

public class ToggleCommand extends AbstractPlayerCommand {

    private final BarrelCrateRuntime runtime;

    // ✅ Se registra así (NO registerArgument)
    private final RequiredArg<String> enabledArg =
            withRequiredArg("enabled", "barrelcrate.commands.toggle.enabled.desc", (ArgumentType<String>) ArgTypes.STRING);

    public ToggleCommand(@Nonnull BarrelCrateRuntime runtime) {
        super("toggle", "barrelcrate.commands.toggle.desc");
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

        String raw = context.get(enabledArg);
        boolean enabled = parseBooleanStrict(raw);

        runtime.setEnabled(enabled);
        context.sendMessage(Message.raw("BarrelCrate: enabled=" + enabled));
    }

    private static boolean parseBooleanStrict(String s) {
        if (s == null) throw new IllegalArgumentException("enabled is null");
        String v = s.trim().toLowerCase();
        return switch (v) {
            case "true", "1", "on", "enable", "enabled", "yes", "y" -> true;
            case "false", "0", "off", "disable", "disabled", "no", "n" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean: " + s);
        };
    }
}