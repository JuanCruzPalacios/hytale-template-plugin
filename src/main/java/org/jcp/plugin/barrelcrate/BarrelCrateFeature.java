package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jcp.plugin.barrelcrate.commands.BarrelCrateCommand;
import org.jcp.plugin.barrelcrate.loot.CrateLootService;
import org.jcp.plugin.barrelcrate.spawn.BarrelCrateEnsureStateSystem;
import org.jcp.plugin.barrelcrate.spawn.BarrelCrateTickSystem;

import java.nio.file.Path;

public final class BarrelCrateFeature {

    private static BarrelCrateRuntime RUNTIME;

    private BarrelCrateFeature() {}

    public static BarrelCrateRuntime runtime() { return RUNTIME; }

    public static void register(JavaPlugin plugin) {
        if (RUNTIME != null) return;

        Path dataDir = plugin.getDataDirectory().resolve("barrelcrate");
        BarrelCrateConfigService configService = new BarrelCrateConfigService(dataDir);
        BarrelCrateConfig config = configService.loadSync();

        ComponentType<ChunkStore, BarrelCrateChunkState> chunkStateComponentType =
                plugin.getChunkStoreRegistry().registerComponent(
                        BarrelCrateChunkState.class,
                        "BarrelCrateState",
                        BarrelCrateChunkState.CODEC
                );

        CrateLootService lootService = new CrateLootService(dataDir);
        BarrelCrateServices.setCrateLoot(lootService);

        RUNTIME = new BarrelCrateRuntime(plugin, configService, chunkStateComponentType, config);

        // ✅ EnsureState necesita el ComponentType, TickSystem necesita runtime
        plugin.getChunkStoreRegistry().registerSystem(new BarrelCrateEnsureStateSystem(chunkStateComponentType));
        plugin.getChunkStoreRegistry().registerSystem(new BarrelCrateTickSystem(RUNTIME));

        plugin.getCommandRegistry().registerCommand(new BarrelCrateCommand(RUNTIME));
    }
}