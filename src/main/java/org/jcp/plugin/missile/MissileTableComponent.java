package org.jcp.plugin.missile;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class MissileTableComponent implements Component<EntityStore> {

    // === Datos persistentes ===
    private long cooldownUntilMs = 0L;
    private long pendingLaunchAtMs = 0L;
    private int targetX = 0;
    private int targetZ = 0;

    // === CODEC persistente ===
    // IMPORTANTE: NO casteamos a (Codec) porque eso rompe los genéricos y lo vuelve Object.
    public static final BuilderCodec<MissileTableComponent> CODEC =
            ((BuilderCodec.Builder) BuilderCodec
                    .builder(MissileTableComponent.class, MissileTableComponent::new)
                    .append(new KeyedCodec<>("CooldownUntilMs", Codec.LONG),
                            (c, v) -> c.cooldownUntilMs = (v == null) ? 0L : v.longValue(),
                            c -> Long.valueOf(c.cooldownUntilMs))
                    .add()
                    .append(new KeyedCodec<>("PendingLaunchAtMs", Codec.LONG),
                            (c, v) -> c.pendingLaunchAtMs = (v == null) ? 0L : v.longValue(),
                            c -> Long.valueOf(c.pendingLaunchAtMs))
                    .add()
                    .append(new KeyedCodec<>("TargetX", Codec.INTEGER),
                            (c, v) -> c.targetX = (v == null) ? 0 : v.intValue(),
                            c -> Integer.valueOf(c.targetX))
                    .add()
                    .append(new KeyedCodec<>("TargetZ", Codec.INTEGER),
                            (c, v) -> c.targetZ = (v == null) ? 0 : v.intValue(),
                            c -> Integer.valueOf(c.targetZ))
                    .add())
                    .build();

    public long getCooldownUntilMs() { return cooldownUntilMs; }
    public void setCooldownUntilMs(long v) { cooldownUntilMs = v; }

    public long getPendingLaunchAtMs() { return pendingLaunchAtMs; }
    public void setPendingLaunchAtMs(long v) { pendingLaunchAtMs = v; }

    public int getTargetX() { return targetX; }
    public void setTargetX(int v) { targetX = v; }

    public int getTargetZ() { return targetZ; }
    public void setTargetZ(int v) { targetZ = v; }

    public boolean isOnCooldown(long nowMs) { return nowMs < cooldownUntilMs; }
    public long cooldownRemainingMs(long nowMs) { return Math.max(0L, cooldownUntilMs - nowMs); }

    @Override
    @Nonnull
    public Component<EntityStore> clone() {
        MissileTableComponent c = new MissileTableComponent();
        c.cooldownUntilMs = this.cooldownUntilMs;
        c.pendingLaunchAtMs = this.pendingLaunchAtMs;
        c.targetX = this.targetX;
        c.targetZ = this.targetZ;
        return c;
    }
}
