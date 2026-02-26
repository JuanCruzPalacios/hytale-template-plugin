package org.jcp.plugin.barrelcrate.spawn;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jcp.plugin.barrelcrate.BarrelCrateIds;
import org.jcp.plugin.barrelcrate.SpawnPointRecord;

public class PlacementService {

    private final int barrelT1Id = BlockKeyUtil.tryResolveBlockId(BarrelCrateIds.BARREL_T1_BLOCK);
    private final int barrelT2Id = BlockKeyUtil.tryResolveBlockId(BarrelCrateIds.BARREL_T2_BLOCK);
    private final int crateId    = BlockKeyUtil.tryResolveBlockId(BarrelCrateIds.CRATE_BLOCK);

    /**
     * ✅ IMPORTANTE:
     * NO toca el WorldChunk directo durante el tick.
     * Encola el setBlock usando commandBuffer.run(...) (igual que BlockSpawnerPlugin).
     */
    public boolean place(CommandBuffer<ChunkStore> commandBuffer, WorldChunk chunk,
                         SpawnPointRecord.PointType type, int worldX, int y, int worldZ) {

        if (commandBuffer == null || chunk == null) return false;

        int localX = worldX & 15;
        int localZ = worldZ & 15;

        int id;
        switch (type) {
            case BARREL_T1 -> id = barrelT1Id;
            case BARREL_T2 -> id = barrelT2Id;
            case CRATE -> id = crateId;
            default -> { return false; }
        }

        if (id <= 0) return false;

        BlockType bt = (BlockType) BlockType.getAssetMap().getAssetOrDefault(id, BlockType.UNKNOWN);
        if (bt == null || bt == BlockType.UNKNOWN) return false;

        // Capturas final para el lambda
        final int fx = localX;
        final int fy = y;
        final int fz = localZ;
        final int fid = id;
        final BlockType fbt = bt;

        // ✅ Encolar la modificación del mundo fuera del processing
        commandBuffer.run(_store -> {
            // rotation=0, filler=0, settings/flags=0
            chunk.setBlock(fx, fy, fz, fid, fbt, 0, 0, 0);
        });

        return true;
    }

    public boolean isOurBlock(int blockId) {
        return blockId == barrelT1Id || blockId == barrelT2Id || blockId == crateId;
    }

    public SpawnPointRecord.PointType typeFromBlockId(int blockId) {
        if (blockId == barrelT1Id) return SpawnPointRecord.PointType.BARREL_T1;
        if (blockId == barrelT2Id) return SpawnPointRecord.PointType.BARREL_T2;
        if (blockId == crateId) return SpawnPointRecord.PointType.CRATE;
        return null;
    }

    public int getCrateId() { return crateId; }
}