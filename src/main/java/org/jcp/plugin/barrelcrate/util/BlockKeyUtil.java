package org.jcp.plugin.barrelcrate.util;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public final class BlockKeyUtil {
    private BlockKeyUtil() {}

    public static int idOf(String blockKey) {
        return BlockType.getBlockIdOrUnknown(blockKey, "Missing block '%s'", new Object[] { blockKey });
    }

    public static String keyOf(int blockId) {
        BlockType bt = (BlockType) BlockType.getAssetMap().getAssetOrDefault(blockId, BlockType.UNKNOWN);
        return bt.getId();
    }
}
