package org.jcp.plugin.missile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class ExplosivesPackMissileFeature {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Type ROOT_TYPE = new TypeToken<MissileTablesFile>() {}.getType();

    private static final String SUPER_ROCKET_PROJECTILE = "Projectile_Config_Missile";
    private static final String MISSILE_ALARM_SOUND = "Missile_Alarm";

    private static JavaPlugin plugin;
    private static Path saveFile;

    private static final ConcurrentHashMap<String, MissileTableState> TABLES = new ConcurrentHashMap<>();

    private static volatile MissileSettings SETTINGS = MissileSettings.defaultSettings();

    private static ScheduledFuture<Void> ticker;

    private ExplosivesPackMissileFeature() {}

    public static void register(JavaPlugin pl) {
        plugin = pl;
        saveFile = plugin.getDataDirectory().resolve("missile_tables.json");

        plugin.getCommandRegistry().registerCommand(new MissileTableSpawnCommand());

        loadFromDisk();
        startTicker();

        plugin.getLogger().at(Level.INFO).log(
                "ExplosivesPackMissileFeature: loaded tables=" + TABLES.size() +
                        " cooldownMs=" + SETTINGS.cooldownMs +
                        " launchDelayMs=" + SETTINGS.launchDelayMs
        );
    }

    public static MissileTableState getOrCreate(String world, int x, int y, int z) {
        String key = key(world, x, y, z);
        return TABLES.computeIfAbsent(key, k -> new MissileTableState());
    }

    public static MissileTableState get(String world, int x, int y, int z) {
        return TABLES.get(key(world, x, y, z));
    }

    public static MissileSettings getSettings() {
        return SETTINGS;
    }

    public static void saveSoon() {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(ExplosivesPackMissileFeature::saveToDisk, 200, TimeUnit.MILLISECONDS);
    }

    private static String key(String world, int x, int y, int z) {
        return world.toLowerCase() + "|" + x + "|" + y + "|" + z;
    }

    private static void loadFromDisk() {
        try {
            Files.createDirectories(plugin.getDataDirectory());

            if (!Files.exists(saveFile)) {
                saveToDisk();
                return;
            }

            String json = Files.readString(saveFile, StandardCharsets.UTF_8);

            MissileTablesFile root = null;
            try {
                root = GSON.fromJson(json, ROOT_TYPE);
            } catch (JsonSyntaxException ignored) {}

            if (root != null && (root.tables != null || root.settings != null)) {
                MissileSettings loadedSettings = (root.settings != null) ? root.settings : MissileSettings.defaultSettings();
                loadedSettings.sanitize();
                SETTINGS = loadedSettings;

                if (root.tables != null) {
                    TABLES.clear();
                    TABLES.putAll(root.tables);
                }
                return;
            }

            // compat formato viejo (solo tables)
            Type oldMapType = new TypeToken<Map<String, MissileTableState>>() {}.getType();
            Map<String, MissileTableState> old = GSON.fromJson(json, oldMapType);
            if (old != null) {
                TABLES.clear();
                TABLES.putAll(old);
            }

            SETTINGS = MissileSettings.defaultSettings();
            saveToDisk();

        } catch (Throwable t) {
            plugin.getLogger().at(Level.SEVERE).log("ExplosivesPack: failed to load missile_tables.json", t);
        }
    }

    private static synchronized void saveToDisk() {
        try {
            Files.createDirectories(plugin.getDataDirectory());

            MissileTablesFile root = new MissileTablesFile();
            root.settings = (SETTINGS != null) ? SETTINGS : MissileSettings.defaultSettings();
            root.settings.sanitize();

            root.tables = new ConcurrentHashMap<>(TABLES);

            String json = GSON.toJson(root, ROOT_TYPE);
            Files.writeString(saveFile, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().at(Level.SEVERE).log("ExplosivesPack: failed to save missile_tables.json", e);
        }
    }

    private static void startTicker() {
        if (ticker != null) return;

        ticker = (ScheduledFuture<Void>) (ScheduledFuture<?>) HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Throwable t) {
                plugin.getLogger().at(Level.SEVERE).log("ExplosivesPack: missile table tick failed", t);
            }
        }, 1, 1, TimeUnit.SECONDS);

        plugin.getTaskRegistry().registerTask(ticker);
    }

    private static void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, MissileTableState> e : TABLES.entrySet()) {
            MissileTableState st = e.getValue();
            if (st == null) continue;

            long launchAt = st.pendingLaunchAtMs;
            if (launchAt <= 0L) continue;

            String[] parts = e.getKey().split("\\|");
            if (parts.length != 4) {
                st.pendingLaunchAtMs = 0L;
                st.alarmPlayed = false;
                continue;
            }

            String worldName = parts[0];
            int by;
            try {
                by = Integer.parseInt(parts[2]);
            } catch (Exception ex) {
                st.pendingLaunchAtMs = 0L;
                st.alarmPlayed = false;
                continue;
            }

            World world = Universe.get().getWorld(worldName);
            if (world == null) continue;

            if (now < launchAt) {
                if (!st.alarmPlayed) {
                    int tx = st.targetX;
                    int tz = st.targetZ;

                    double sx = tx + 0.5;
                    double sy = by + 0.5;
                    double sz = tz + 0.5;

                    int soundIndex = SoundEvent.getAssetMap().getIndex(MISSILE_ALARM_SOUND);

                    world.execute(() -> {
                        try {
                            SoundUtil.playSoundEvent3d(
                                    soundIndex,
                                    SoundCategory.SFX,
                                    sx, sy, sz,
                                    world.getEntityStore().getStore()
                            );
                        } catch (Throwable t) {
                            plugin.getLogger().at(Level.WARNING).log("Failed to play alarm sound: " + MISSILE_ALARM_SOUND, t);
                        }
                    });

                    st.alarmPlayed = true;
                    saveSoon();
                }
                continue;
            }

            int tx = st.targetX;
            int tz = st.targetZ;

            st.pendingLaunchAtMs = 0L;
            st.alarmPlayed = false;

            long cooldownMs = (SETTINGS != null) ? SETTINGS.cooldownMs : MissileSettings.DEFAULT_COOLDOWN_MS;
            st.cooldownUntilMs = now + cooldownMs;

            world.execute(() -> {
                try {
                    MissileSpawner.spawnDownward(world, SUPER_ROCKET_PROJECTILE, tx, tz, plugin);
                } catch (Throwable t) {
                    plugin.getLogger().at(Level.SEVERE).log("MissileSpawner threw exception", t);
                }
            });

            saveSoon();
        }
    }

    // -------------------------------------------------------------------------
    // Persisted root file
    // -------------------------------------------------------------------------

    public static final class MissileTablesFile {
        public MissileSettings settings;
        public Map<String, MissileTableState> tables;
    }

    public static final class MissileSettings {
        public static final long DEFAULT_COOLDOWN_MS = 3_600_000L; // 1h
        public static final long MIN_COOLDOWN_MS = 1_000L;        // 1s

        public static final long DEFAULT_LAUNCH_DELAY_MS = 60_000L; // 60s
        public static final long MIN_LAUNCH_DELAY_MS = 1_000L;      // 1s

        /** Cooldown entre disparos (ms). */
        public long cooldownMs = DEFAULT_COOLDOWN_MS;

        /** ETA / delay entre apretar Launch y que salga el misil (ms). */
        public long launchDelayMs = DEFAULT_LAUNCH_DELAY_MS;

        public static MissileSettings defaultSettings() {
            MissileSettings s = new MissileSettings();
            s.cooldownMs = DEFAULT_COOLDOWN_MS;
            s.launchDelayMs = DEFAULT_LAUNCH_DELAY_MS;
            return s;
        }

        public void sanitize() {
            if (cooldownMs < MIN_COOLDOWN_MS) cooldownMs = MIN_COOLDOWN_MS;
            if (cooldownMs > 7L * 24L * 3600L * 1000L) cooldownMs = 7L * 24L * 3600L * 1000L;

            if (launchDelayMs < MIN_LAUNCH_DELAY_MS) launchDelayMs = MIN_LAUNCH_DELAY_MS;
            if (launchDelayMs > 30L * 60L * 1000L) launchDelayMs = 30L * 60L * 1000L; // clamp 30 min
        }
    }

    public static final class MissileTableState {
        public long cooldownUntilMs = 0L;
        public long pendingLaunchAtMs = 0L;
        public int targetX = 0;
        public int targetZ = 0;
        public boolean alarmPlayed = false;

        public boolean isOnCooldown(long nowMs) {
            return nowMs < cooldownUntilMs;
        }

        public long cooldownRemainingMs(long nowMs) {
            return Math.max(0L, cooldownUntilMs - nowMs);
        }
    }
}