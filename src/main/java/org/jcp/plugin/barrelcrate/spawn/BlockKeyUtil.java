package org.jcp.plugin.barrelcrate.spawn;

import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilidad rápida para resolver BlockType keys <-> blockIds.
 * Usa el AssetMap del engine (O(1)) y cachea resultados.
 */
public final class BlockKeyUtil {

    private static final int MISSING_INDEX = Integer.MIN_VALUE;

    private static final ConcurrentHashMap<String, Integer> KEY_TO_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> ID_TO_KEY = new ConcurrentHashMap<>();

    private BlockKeyUtil() {}

    /**
     * @return blockId o -1 si no existe el key.
     */
    public static int tryResolveBlockId(String blockKey) {
        if (blockKey == null || blockKey.isEmpty()) return -1;

        Integer cached = KEY_TO_ID.get(blockKey);
        if (cached != null) return cached;

        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        if (map == null) return -1;

        int idx = map.getIndex(blockKey);
        if (idx == MISSING_INDEX) {
            KEY_TO_ID.put(blockKey, -1);
            return -1;
        }

        KEY_TO_ID.put(blockKey, idx);
        ID_TO_KEY.putIfAbsent(idx, blockKey);
        return idx;
    }

    /**
     * @return blockKey o null si no existe el id (o id inválido).
     */
    public static String tryResolveBlockKey(int blockId) {
        if (blockId <= 0) return null;

        String cached = ID_TO_KEY.get(blockId);
        if (cached != null) return cached;

        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        if (map == null) return null;

        // ✅ Esto existe en el engine: getAssetOrDefault(index, default)
        BlockType bt = map.getAssetOrDefault(blockId, BlockType.UNKNOWN);
        if (bt == null) return null;

        String key = bt.getId(); // normalmente "Terrain_Grass", "Furniture_..." etc.
        if (key == null || key.isEmpty() || "Unknown".equals(key)) return null;

        ID_TO_KEY.put(blockId, key);
        KEY_TO_ID.putIfAbsent(key, blockId);
        return key;
    }
}