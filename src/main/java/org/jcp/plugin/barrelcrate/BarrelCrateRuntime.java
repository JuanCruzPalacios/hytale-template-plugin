package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public final class BarrelCrateRuntime {

    private final JavaPlugin plugin;
    private final BarrelCrateConfigService configService;
    private final ComponentType<ChunkStore, BarrelCrateChunkState> chunkStateComponentType;

    private volatile BarrelCrateConfig config;

    public BarrelCrateRuntime(
            @Nonnull JavaPlugin plugin,
            @Nonnull BarrelCrateConfigService configService,
            @Nonnull ComponentType<ChunkStore, BarrelCrateChunkState> chunkStateComponentType,
            @Nonnull BarrelCrateConfig config
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.chunkStateComponentType = chunkStateComponentType;
        this.config = config;
    }

    public JavaPlugin plugin() { return plugin; }

    public BarrelCrateConfig config() { return config; }

    public ComponentType<ChunkStore, BarrelCrateChunkState> chunkStateComponentType() {
        return chunkStateComponentType;
    }

    public void reloadConfig() {
        this.config = this.configService.loadSync();
    }

    public void setEnabled(boolean enabled) {
        // ✅ IMPORTANTE: mutamos el objeto cargado y guardamos
        BarrelCrateConfig cfg = this.config;
        cfg.setEnabled(enabled);
        this.configService.saveLoaded();
        this.config = cfg;
    }
}