package org.jcp.plugin.barrelcrate.spawn;

import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.jcp.plugin.barrelcrate.BarrelCrateConfig;

import java.util.HashSet;

public class SpawnValidator {

    public record Result(boolean ok, int yTop, int yPlace) {}

    private final BarrelCrateConfig cfg;

    private final HashSet<Integer> allowedSurfaceIds = new HashSet<>();
    private final HashSet<Integer> denySurfaceIds = new HashSet<>();
    private final String[] denyKeywordsLower;

    public SpawnValidator(BarrelCrateConfig cfg) {
        this.cfg = cfg;

        for (String k : cfg.getAllowedSurfaceBlocks()) {
            int id = BlockKeyUtil.tryResolveBlockId(k);
            if (id > 0) allowedSurfaceIds.add(id);
        }
        for (String k : cfg.getDenySurfaceBlocks()) {
            int id = BlockKeyUtil.tryResolveBlockId(k);
            if (id > 0) denySurfaceIds.add(id);
        }

        String[] kws = cfg.getArtificialBlockKeyDenyKeywords();
        denyKeywordsLower = new String[kws.length];
        for (int i = 0; i < kws.length; i++) {
            denyKeywordsLower[i] = kws[i].toLowerCase();
        }
    }

    public Result validate(WorldChunk wc, int lx, int lz) {
        int yTop = wc.getHeight(lx, lz);
        if (yTop <= 1) return new Result(false, 0, 0);

        int baseId = wc.getBlock(lx, yTop, lz);

        if (!denySurfaceIds.isEmpty() && denySurfaceIds.contains(baseId)) return new Result(false, 0, 0);
        if (!allowedSurfaceIds.isEmpty() && !allowedSurfaceIds.contains(baseId)) return new Result(false, 0, 0);

        int yPlace = yTop + 1;

        // anti-agua/lava real: chequear fluid section
        if (hasFluid(wc, lx, yTop + 1, lz)) return new Result(false, 0, 0);
        if (hasFluid(wc, lx, yPlace, lz)) return new Result(false, 0, 0);
        if (hasFluid(wc, lx, yPlace + 1, lz)) return new Result(false, 0, 0);

        // espacio libre arriba
        if (wc.getBlock(lx, yPlace, lz) != 0) return new Result(false, 0, 0);
        if (wc.getBlock(lx, yPlace + 1, lz) != 0) return new Result(false, 0, 0);

        // evitar líquidos cerca (fluids)
        int liquidR = cfg.getAvoidLiquidsRadius();
        if (liquidR > 0) {
            if (scanForFluidsNearby(wc, lx, yPlace, lz, liquidR)) return new Result(false, 0, 0);
        }

        // anti-cueva: chequear aire debajo
        int depth = cfg.getCaveAirCheckDepth();
        if (depth > 0) {
            for (int dy = 1; dy <= depth; dy++) {
                int id = wc.getBlock(lx, yTop - dy, lz);
                if (id == 0) return new Result(false, 0, 0);
            }
        }

        // anti-árbol: logs/leaves cerca (heurística por key)
        int treeR = cfg.getTreeScanRadius();
        if (treeR > 0) {
            for (int dx = -treeR; dx <= treeR; dx++) {
                for (int dz = -treeR; dz <= treeR; dz++) {
                    int id = wc.getBlock(lx + dx, yPlace, lz + dz);
                    if (id == 0) continue;

                    String key = BlockKeyUtil.tryResolveBlockKey(id);
                    if (key == null) continue;
                    String lk = key.toLowerCase();
                    if (lk.contains("leaf") || lk.contains("leaves") || lk.contains("log") || lk.contains("trunk")) {
                        return new Result(false, 0, 0);
                    }
                }
            }
        }

        // anti-artificial: scan por keywords
        int artR = cfg.getArtificialScanRadius();
        if (artR > 0 && denyKeywordsLower.length > 0) {
            for (int dx = -artR; dx <= artR; dx++) {
                for (int dz = -artR; dz <= artR; dz++) {
                    int id = wc.getBlock(lx + dx, yTop, lz + dz);
                    if (id == 0) continue;

                    String key = BlockKeyUtil.tryResolveBlockKey(id);
                    if (key == null) continue;

                    String lk = key.toLowerCase();
                    for (String kw : denyKeywordsLower) {
                        if (lk.contains(kw)) return new Result(false, 0, 0);
                    }
                }
            }
        }

        return new Result(true, yTop, yPlace);
    }

    private boolean scanForFluidsNearby(WorldChunk wc, int lx, int y, int lz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (hasFluid(wc, lx + dx, y, lz + dz)) return true;
                if (hasFluid(wc, lx + dx, y + 1, lz + dz)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"removal", "deprecation"})
    private static boolean hasFluid(WorldChunk wc, int x, int y, int z) {
        return wc.getFluidId(x, y, z) != 0;
    }
}