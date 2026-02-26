package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public class BarrelCrateConfigService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Config<BarrelCrateConfig> config;
    private final Path filePath;

    public BarrelCrateConfigService(@Nonnull Path dataDir) {
        this.config = new Config<>(dataDir, "barrelcrate_config", BarrelCrateConfig.CODEC);
        this.filePath = dataDir.resolve("barrelcrate_config.json");

        // ✅ crear SIEMPRE el archivo si no existe (al iniciar el plugin)
        ensureCreatedOnStartup();
    }

    private void ensureCreatedOnStartup() {
        try {
            // carga defaults o el existente
            config.load().join();

            // si no existe, lo persistimos
            if (!Files.exists(filePath)) {
                config.save().join();
                LOGGER.at(Level.INFO).log("Created default barrelcrate_config.json at %s", filePath);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.SEVERE).withCause(t).log("Failed to create barrelcrate_config.json on startup");
        }
    }

    /** Carga (y si no existe el archivo, lo crea). */
    public BarrelCrateConfig loadSync() {
        try {
            BarrelCrateConfig loaded = config.load().join();

            // extra safety: si por alguna razón no existe, guardamos
            if (!Files.exists(filePath)) {
                config.save().join();
            }

            return loaded;
        } catch (Throwable t) {
            LOGGER.at(Level.SEVERE).withCause(t).log("Failed to load barrelcrate_config.json, using defaults in-memory");
            BarrelCrateConfig defaults = new BarrelCrateConfig();

            try {
                // intentamos dejar el config en un estado guardable
                config.load().join();
                if (!Files.exists(filePath)) {
                    config.save().join();
                }
            } catch (Throwable ignored) {}

            return defaults;
        }
    }

    /** Guarda el objeto actualmente cargado (mutado). */
    public void saveLoaded() {
        try {
            config.save().join();
        } catch (Throwable t) {
            LOGGER.at(Level.SEVERE).withCause(t).log("Failed to save barrelcrate_config.json");
        }
    }

    public BarrelCrateConfig getLoaded() {
        return config.get();
    }
}