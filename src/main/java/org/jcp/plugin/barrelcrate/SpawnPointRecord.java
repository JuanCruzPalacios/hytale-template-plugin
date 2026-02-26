package org.jcp.plugin.barrelcrate;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import java.util.concurrent.ThreadLocalRandom;

public class SpawnPointRecord {

    public enum PointType { BARREL_T1, BARREL_T2, BARREL_T3, CRATE }
    public enum PointState { ALIVE, DESTROYED, DEFERRED, DISABLED }

    public static final BuilderCodec<SpawnPointRecord> CODEC =
            BuilderCodec.builder(SpawnPointRecord.class, SpawnPointRecord::new)
                    .addField(new KeyedCodec<>("Id", Codec.LONG),
                            (p, v) -> p.id = asLong(v),
                            p -> p.id)
                    .addField(new KeyedCodec<>("Type", Codec.STRING),
                            (p, v) -> p.type = PointType.valueOf(String.valueOf(v)),
                            p -> p.type.name())
                    .addField(new KeyedCodec<>("State", Codec.STRING),
                            (p, v) -> p.state = PointState.valueOf(String.valueOf(v)),
                            p -> p.state.name())
                    .addField(new KeyedCodec<>("X", Codec.INTEGER),
                            (p, v) -> p.x = asInt(v),
                            p -> p.x)
                    .addField(new KeyedCodec<>("Y", Codec.INTEGER),
                            (p, v) -> p.y = asInt(v),
                            p -> p.y)
                    .addField(new KeyedCodec<>("Z", Codec.INTEGER),
                            (p, v) -> p.z = asInt(v),
                            p -> p.z)
                    .addField(new KeyedCodec<>("NextActionAtMs", Codec.LONG),
                            (p, v) -> p.nextActionAtMs = asLong(v),
                            p -> p.nextActionAtMs)
                    .addField(new KeyedCodec<>("DeferredTries", Codec.INTEGER),
                            (p, v) -> p.deferredTries = asInt(v),
                            p -> p.deferredTries)

                    // ✅ nuevo: para reintentar llenar crates
                    .addField(new KeyedCodec<>("CrateFilled", Codec.BOOLEAN),
                            (p, v) -> p.crateFilled = asBool(v),
                            p -> p.crateFilled)
                    .build();

    private long id = ThreadLocalRandom.current().nextLong();
    private PointType type = PointType.BARREL_T1;
    private PointState state = PointState.ALIVE;

    private int x, y, z;
    private long nextActionAtMs;
    private int deferredTries;

    private boolean crateFilled = false;

    public SpawnPointRecord() {}

    public SpawnPointRecord(PointType type, int x, int y, int z) {
        this.id = ThreadLocalRandom.current().nextLong();
        this.type = type;
        this.state = PointState.ALIVE;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nextActionAtMs = 0L;
        this.deferredTries = 0;
        this.crateFilled = false;
    }

    public long getId() { return id; }
    public PointType getType() { return type; }
    public PointState getState() { return state; }
    public void setState(PointState s) { this.state = s; }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public long getNextActionAtMs() { return nextActionAtMs; }
    public void setNextActionAtMs(long v) { this.nextActionAtMs = v; }

    public int getDeferredTries() { return deferredTries; }
    public void setDeferredTries(int v) { this.deferredTries = v; }

    public boolean isCrateFilled() { return crateFilled; }
    public void setCrateFilled(boolean v) { this.crateFilled = v; }

    private static int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private static boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}