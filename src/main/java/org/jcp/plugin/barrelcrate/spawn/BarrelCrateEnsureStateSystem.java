package org.jcp.plugin.barrelcrate.spawn;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jcp.plugin.barrelcrate.BarrelCrateChunkState;

import javax.annotation.Nonnull;

public class BarrelCrateEnsureStateSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, WorldChunk> worldChunkType;
    private final ComponentType<ChunkStore, BarrelCrateChunkState> stateType;
    private final Query<ChunkStore> query;

    public BarrelCrateEnsureStateSystem(
            @Nonnull ComponentType<ChunkStore, BarrelCrateChunkState> stateType
    ) {
        this.worldChunkType = WorldChunk.getComponentType();
        if (this.worldChunkType == null) {
            // Si registrás en BootEvent, esto NO debería pasar.
            throw new IllegalStateException("WorldChunk component type is null (register BarrelCrateFeature in BootEvent).");
        }

        if (stateType == null) throw new IllegalArgumentException("stateType is null");
        this.stateType = stateType;

        // ✅ Solo necesitamos WorldChunk para mirar flags (no hace falta que el state exista aún)
        this.query = Query.and(this.worldChunkType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        WorldChunk wc = commandBuffer.getComponent(ref, worldChunkType);
        if (wc == null) return;

        // ✅ CLAVE: solo chunks nuevos (worldgen)
        if (!wc.is(ChunkFlag.NEWLY_GENERATED)) {
            return;
        }

        BarrelCrateChunkState existing = commandBuffer.getComponent(ref, stateType);
        if (existing == null) {
            commandBuffer.addComponent(ref, stateType);
        }
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason removeReason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer
    ) {
        // no-op
    }
}