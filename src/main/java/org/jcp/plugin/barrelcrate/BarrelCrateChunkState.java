package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class BarrelCrateChunkState implements Component<ChunkStore> {

    public static final BuilderCodec<BarrelCrateChunkState> CODEC =
            BuilderCodec.builder(BarrelCrateChunkState.class, BarrelCrateChunkState::new)
                    .addField(new KeyedCodec<>("Generated", Codec.BOOLEAN),
                            (s, v) -> s.generated = asBool(v),
                            s -> s.generated)

                    // ✅ progreso incremental del spawn inicial
                    .addField(new KeyedCodec<>("InitAttempt", Codec.INTEGER),
                            (s, v) -> s.initAttempt = asInt(v),
                            s -> s.initAttempt)
                    .addField(new KeyedCodec<>("InitPlaced", Codec.INTEGER),
                            (s, v) -> s.initPlaced = asInt(v),
                            s -> s.initPlaced)

                    .addField(new KeyedCodec<>("Points", new ArrayCodec(SpawnPointRecord.CODEC, len -> new SpawnPointRecord[len])),
                            (s, v) -> s.points = (v == null) ? new SpawnPointRecord[0] : (SpawnPointRecord[]) v,
                            s -> s.points)
                    .build();

    private boolean generated = false;

    // progreso del init
    private int initAttempt = 0;
    private int initPlaced = 0;

    private SpawnPointRecord[] points = new SpawnPointRecord[0];

    public BarrelCrateChunkState() {}

    public boolean isGenerated() { return generated; }
    public void setGenerated(boolean v) { this.generated = v; }

    public int getInitAttempt() { return initAttempt; }
    public void setInitAttempt(int v) { this.initAttempt = v; }

    public int getInitPlaced() { return initPlaced; }
    public void setInitPlaced(int v) { this.initPlaced = v; }

    public SpawnPointRecord[] getPoints() { return points; }
    public void setPoints(SpawnPointRecord[] v) { this.points = (v == null) ? new SpawnPointRecord[0] : v; }

    @Override
    public Component<ChunkStore> clone() {
        BarrelCrateChunkState copy = new BarrelCrateChunkState();
        copy.generated = this.generated;
        copy.initAttempt = this.initAttempt;
        copy.initPlaced = this.initPlaced;
        copy.points = (this.points == null) ? new SpawnPointRecord[0] : this.points.clone();
        return copy;
    }

    public void addPoint(SpawnPointRecord p) {
        SpawnPointRecord[] old = this.points;
        SpawnPointRecord[] n = new SpawnPointRecord[old.length + 1];
        System.arraycopy(old, 0, n, 0, old.length);
        n[old.length] = p;
        this.points = n;
    }

    private static int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}