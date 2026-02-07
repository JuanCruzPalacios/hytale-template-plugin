package org.jcp.plugin.missile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
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
    private static final Type MAP_TYPE = new TypeToken<Map<String, MissileTableState>>() {}.getType();

    // ✅ BlockType id de tu mesa (igual que usás al spawnear)
    private static final String MISSILE_TABLE_BLOCK_TYPE = "Missile_Table";

    // ✅ ID del projectile asset (Projectiles/Projectile_Config_Super_Rocket.json)
    private static final String SUPER_ROCKET_PROJECTILE = "Projectile_Config_Missile";

    // ✅ ID del SoundEvent de la alarma (SoundEvents/Missile_Alarm.json)
    private static final String MISSILE_ALARM_SOUND = "Missile_Alarm";

    private static JavaPlugin plugin;
    private static Path saveFile;

    // key: "world|x|y|z"
    private static final ConcurrentHashMap<String, MissileTableState> TABLES = new ConcurrentHashMap<>();

    private static ScheduledFuture<Void> ticker;

    private ExplosivesPackMissileFeature() {}

    public static void register(JavaPlugin pl) {
        plugin = pl;
        saveFile = plugin.getDataDirectory().resolve("missile_tables.json");

        // Command (spawn)
        plugin.getCommandRegistry().registerCommand(new MissileTableSpawnCommand());

        loadFromDisk();

        // ✅ Cuando se rompe la mesa, borrar su estado persistente
        plugin.getEventRegistry().register(EventPriority.NORMAL, BreakBlockEvent.class, evt -> {
            try {
                onBreakBlock(evt);
            } catch (Throwable t) {
                plugin.getLogger().at(Level.SEVERE).log("ExplosivesPack: error handling BreakBlockEvent", t);
            }
        });

        startTicker();

        plugin.getLogger().at(Level.INFO).log("ExplosivesPackMissileFeature: loaded tables=" + TABLES.size());
    }

    public static MissileTableState getOrCreate(String world, int x, int y, int z) {
        String key = key(world, x, y, z);
        return TABLES.computeIfAbsent(key, k -> new MissileTableState());
    }

    public static MissileTableState get(String world, int x, int y, int z) {
        return TABLES.get(key(world, x, y, z));
    }

    public static boolean removeTableState(String world, int x, int y, int z) {
        String k = key(world, x, y, z);
        MissileTableState removed = TABLES.remove(k);
        if (removed != null) {
            saveSoon();
            return true;
        }
        return false;
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
            if (!Files.exists(saveFile)) return;

            String json = Files.readString(saveFile, StandardCharsets.UTF_8);
            Map<String, MissileTableState> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded != null) {
                TABLES.clear();
                TABLES.putAll(loaded);
            }
        } catch (Throwable t) {
            plugin.getLogger().at(Level.SEVERE).log("ExplosivesPack: failed to load missile_tables.json", t);
        }
    }

    private static synchronized void saveToDisk() {
        try {
            Files.createDirectories(plugin.getDataDirectory());
            String json = GSON.toJson(TABLES, MAP_TYPE);
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
        World world = Universe.get().getDefaultWorld();
        if (world == null) return;

        long now = System.currentTimeMillis();

        for (Map.Entry<String, MissileTableState> e : TABLES.entrySet()) {
            MissileTableState st = e.getValue();
            if (st == null) continue;

            long launchAt = st.pendingLaunchAtMs;
            if (launchAt <= 0L) continue;

            // parse key para obtener Y del bloque (para ubicar el sonido)
            String[] parts = e.getKey().split("\\|");
            if (parts.length != 4) {
                st.pendingLaunchAtMs = 0L;
                st.alarmPlayed = false;
                continue;
            }

            int by;
            try {
                by = Integer.parseInt(parts[2]);
            } catch (Exception ex) {
                st.pendingLaunchAtMs = 0L;
                st.alarmPlayed = false;
                continue;
            }

            // ✅ Mientras espera: sonar la alarma UNA SOLA VEZ
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

            // ✅ Llegó el momento: lanzar el misil
            int tx = st.targetX;
            int tz = st.targetZ;

            st.pendingLaunchAtMs = 0L;
            st.alarmPlayed = false;
            st.cooldownUntilMs = now + 3_600_000L; // 1 hora

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

    // ✅ EVENT: si se rompe la mesa, borrar estado persistente
    private static void onBreakBlock(BreakBlockEvent evt) {
        if (evt.getBlockType() == null) return;

        String id = evt.getBlockType().getId();
        if (!MISSILE_TABLE_BLOCK_TYPE.equals(id)) return;

        World world = Universe.get().getDefaultWorld();
        if (world == null) return;

        Vector3i pos = evt.getTargetBlock();
        if (pos == null) return;

        boolean removed = removeTableState(world.getName(), pos.x, pos.y, pos.z);

        if (removed) {
            plugin.getLogger().at(Level.INFO).log(
                    "ExplosivesPack: Missile table state removed because block was broken at "
                            + pos.x + "," + pos.y + "," + pos.z
            );
        }
    }

    // Estado persistente
    public static final class MissileTableState {
        public long cooldownUntilMs = 0L;
        public long pendingLaunchAtMs = 0L;
        public int targetX = 0;
        public int targetZ = 0;

        // alarma solo una vez
        public boolean alarmPlayed = false;

        public boolean isOnCooldown(long nowMs) {
            return nowMs < cooldownUntilMs;
        }

        public long cooldownRemainingMs(long nowMs) {
            return Math.max(0L, cooldownUntilMs - nowMs);
        }
    }
}
