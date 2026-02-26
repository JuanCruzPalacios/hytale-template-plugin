package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class BarrelCrateConfig {

    public static final BuilderCodec<BarrelCrateConfig> CODEC =
            BuilderCodec.builder(BarrelCrateConfig.class, BarrelCrateConfig::new)

                    .addField(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                            (c, v) -> c.enabled = asBool(v),
                            c -> c.enabled)

                    // probabilidad de que un chunk nuevo haga spawn (0..1)
                    .addField(new KeyedCodec<>("ChunkSpawnChance", Codec.FLOAT),
                            (c, v) -> c.chunkSpawnChance = clamp01(asFloat(v)),
                            c -> c.chunkSpawnChance)

                    .addField(new KeyedCodec<>("SpawnPerChunk", Codec.INTEGER),
                            (c, v) -> c.spawnPerChunk = asInt(v),
                            c -> c.spawnPerChunk)

                    .addField(new KeyedCodec<>("SpawnAttemptsMultiplier", Codec.INTEGER),
                            (c, v) -> c.spawnAttemptsMultiplier = asInt(v),
                            c -> c.spawnAttemptsMultiplier)

                    // ✅ NUEVO: pesos por tipo (reemplaza hardcode del SpawnPlanner)
                    .addField(new KeyedCodec<>("SpawnWeightBarrelT1", Codec.INTEGER),
                            (c, v) -> c.spawnWeightBarrelT1 = clamp0(asInt(v)),
                            c -> c.spawnWeightBarrelT1)

                    .addField(new KeyedCodec<>("SpawnWeightBarrelT2", Codec.INTEGER),
                            (c, v) -> c.spawnWeightBarrelT2 = clamp0(asInt(v)),
                            c -> c.spawnWeightBarrelT2)

                    .addField(new KeyedCodec<>("SpawnWeightCrate", Codec.INTEGER),
                            (c, v) -> c.spawnWeightCrate = clamp0(asInt(v)),
                            c -> c.spawnWeightCrate)

                    .addField(new KeyedCodec<>("BarrelCooldownSeconds", Codec.INTEGER),
                            (c, v) -> c.barrelCooldownSeconds = asInt(v),
                            c -> c.barrelCooldownSeconds)

                    .addField(new KeyedCodec<>("CrateRefillCooldownSeconds", Codec.INTEGER),
                            (c, v) -> c.crateRefillCooldownSeconds = asInt(v),
                            c -> c.crateRefillCooldownSeconds)

                    .addField(new KeyedCodec<>("DeferredRetrySeconds", Codec.INTEGER),
                            (c, v) -> c.deferredRetrySeconds = asInt(v),
                            c -> c.deferredRetrySeconds)

                    .addField(new KeyedCodec<>("MaxDeferredRetries", Codec.INTEGER),
                            (c, v) -> c.maxDeferredRetries = asInt(v),
                            c -> c.maxDeferredRetries)

                    .addField(new KeyedCodec<>("TickIntervalMs", Codec.INTEGER),
                            (c, v) -> c.tickIntervalMs = asInt(v),
                            c -> c.tickIntervalMs)

                    .addField(new KeyedCodec<>("AllowedSurfaceBlocks", Codec.STRING_ARRAY),
                            (c, v) -> c.allowedSurfaceBlocks = (v == null) ? new String[0] : (String[]) v,
                            c -> c.allowedSurfaceBlocks)

                    .addField(new KeyedCodec<>("DenySurfaceBlocks", Codec.STRING_ARRAY),
                            (c, v) -> c.denySurfaceBlocks = (v == null) ? new String[0] : (String[]) v,
                            c -> c.denySurfaceBlocks)

                    .addField(new KeyedCodec<>("AvoidLiquidsRadius", Codec.INTEGER),
                            (c, v) -> c.avoidLiquidsRadius = asInt(v),
                            c -> c.avoidLiquidsRadius)

                    .addField(new KeyedCodec<>("CaveAirCheckDepth", Codec.INTEGER),
                            (c, v) -> c.caveAirCheckDepth = asInt(v),
                            c -> c.caveAirCheckDepth)

                    .addField(new KeyedCodec<>("TreeScanRadius", Codec.INTEGER),
                            (c, v) -> c.treeScanRadius = asInt(v),
                            c -> c.treeScanRadius)

                    .addField(new KeyedCodec<>("ArtificialScanRadius", Codec.INTEGER),
                            (c, v) -> c.artificialScanRadius = asInt(v),
                            c -> c.artificialScanRadius)

                    .addField(new KeyedCodec<>("ArtificialBlockKeyDenyKeywords", Codec.STRING_ARRAY),
                            (c, v) -> c.artificialBlockKeyDenyKeywords = (v == null) ? new String[0] : (String[]) v,
                            c -> c.artificialBlockKeyDenyKeywords)
                    .build();

    private boolean enabled = false;

    private float chunkSpawnChance = 0.25f;

    private int spawnPerChunk = 3;
    private int spawnAttemptsMultiplier = 6;

    // ✅ NUEVO: pesos default equivalentes a lo hardcodeado
    private int spawnWeightBarrelT1 = 45;
    private int spawnWeightBarrelT2 = 30;
    private int spawnWeightCrate = 25;

    private int barrelCooldownSeconds = 900;
    private int crateRefillCooldownSeconds = 1200;

    private int deferredRetrySeconds = 120;
    private int maxDeferredRetries = 15;

    private int tickIntervalMs = 1000;

    private String[] allowedSurfaceBlocks = new String[] {
            "Terrain_Grass", "Terrain_Dirt", "Terrain_Sand",
            "Terrain_Stone", "Terrain_Stone_Limestone", "Terrain_Stone_Sandstone"
    };

    private String[] denySurfaceBlocks = new String[] {
            "Terrain_Water", "Terrain_Lava", "Terrain_Ice"
    };

    private int avoidLiquidsRadius = 6;
    private int caveAirCheckDepth = 8;
    private int treeScanRadius = 3;
    private int artificialScanRadius = 7;

    private String[] artificialBlockKeyDenyKeywords = new String[] {
            "Furniture","Structure","Building","Brick","Plank","Road","Path",
            "Cobble","Tile","Torch","Lamp","Fence","Door","Window","Workbench"
    };

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }

    public float getChunkSpawnChance() { return chunkSpawnChance; }
    public void setChunkSpawnChance(float v) { chunkSpawnChance = clamp01(v); }

    public int getSpawnPerChunk() { return spawnPerChunk; }
    public int getSpawnAttemptsMultiplier() { return spawnAttemptsMultiplier; }

    // ✅ nuevos getters
    public int getSpawnWeightBarrelT1() { return spawnWeightBarrelT1; }
    public int getSpawnWeightBarrelT2() { return spawnWeightBarrelT2; }
    public int getSpawnWeightCrate() { return spawnWeightCrate; }

    public int getBarrelCooldownSeconds() { return barrelCooldownSeconds; }
    public int getCrateRefillCooldownSeconds() { return crateRefillCooldownSeconds; }

    public int getDeferredRetrySeconds() { return deferredRetrySeconds; }
    public int getMaxDeferredRetries() { return maxDeferredRetries; }

    public int getTickIntervalMs() { return tickIntervalMs; }

    public String[] getAllowedSurfaceBlocks() { return allowedSurfaceBlocks; }
    public String[] getDenySurfaceBlocks() { return denySurfaceBlocks; }

    public int getAvoidLiquidsRadius() { return avoidLiquidsRadius; }
    public int getCaveAirCheckDepth() { return caveAirCheckDepth; }
    public int getTreeScanRadius() { return treeScanRadius; }
    public int getArtificialScanRadius() { return artificialScanRadius; }

    public String[] getArtificialBlockKeyDenyKeywords() { return artificialBlockKeyDenyKeywords; }

    private static int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static float asFloat(Object v) {
        if (v == null) return 0f;
        if (v instanceof Float f) return f;
        if (v instanceof Number n) return n.floatValue();
        return Float.parseFloat(String.valueOf(v));
    }

    private static boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static int clamp0(int v) {
        return Math.max(0, v);
    }
}