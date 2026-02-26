package org.jcp.plugin.barrelcrate.spawn;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jcp.plugin.barrelcrate.BarrelCrateChunkState;
import org.jcp.plugin.barrelcrate.BarrelCrateConfig;
import org.jcp.plugin.barrelcrate.BarrelCrateRuntime;
import org.jcp.plugin.barrelcrate.BarrelCrateServices;
import org.jcp.plugin.barrelcrate.SpawnPointRecord;
import org.jcp.plugin.barrelcrate.loot.CrateLootService;

import java.util.List;

public class BarrelCrateTickSystem extends EntityTickingSystem<ChunkStore> {

    private static final int INIT_ATTEMPTS_PER_TICK = 2;
    private static final long CRATE_RECHECK_MS = 1000L;

    private final BarrelCrateRuntime runtime;
    private final ComponentType<ChunkStore, WorldChunk> worldChunkType;
    private final ComponentType<ChunkStore, BarrelCrateChunkState> stateType;
    private final Query<ChunkStore> query;

    private final SpawnPlanner planner = new SpawnPlanner();

    private CrateLootService crateLoot() {
        return BarrelCrateServices.crateLoot();
    }

    public BarrelCrateTickSystem(BarrelCrateRuntime runtime) {
        this.runtime = runtime;

        this.worldChunkType = WorldChunk.getComponentType();
        if (this.worldChunkType == null) {
            throw new IllegalStateException("WorldChunk component type is null (register BarrelCrateFeature in BootEvent).");
        }

        this.stateType = runtime.chunkStateComponentType();
        this.query = Query.and(this.worldChunkType, this.stateType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {

        BarrelCrateConfig cfg = runtime.config();
        if (!cfg.isEnabled()) return;

        planner.applyConfig(cfg);

        WorldChunk worldChunk = chunk.getComponent(index, worldChunkType);
        BarrelCrateChunkState state = chunk.getComponent(index, stateType);
        if (worldChunk == null || state == null) return;

        long now = System.currentTimeMillis();

        boolean allowHeavy = ((now / cfg.getTickIntervalMs() + worldChunk.getIndex()) % 3 == 0);

        PlacementService placement = new PlacementService();
        SpawnValidator validator = new SpawnValidator(cfg);

        if (!state.isGenerated()) {
            if (!allowHeavy) return;
            doInitialIncremental(commandBuffer, worldChunk, state, cfg, validator, placement, now);
            if (!state.isGenerated()) return;
        }

        for (SpawnPointRecord p : state.getPoints()) {
            if (p == null) continue;
            if (p.getState() == SpawnPointRecord.PointState.DISABLED) continue;

            int lx = p.getX() & 15;
            int lz = p.getZ() & 15;

            // -----------------------
            // BARRELS
            // -----------------------
            if (p.getType() != SpawnPointRecord.PointType.CRATE) {

                if (p.getState() == SpawnPointRecord.PointState.ALIVE) {
                    int currentId = worldChunk.getBlock(lx, p.getY(), lz);
                    if (!placement.isOurBlock(currentId)) {
                        p.setState(SpawnPointRecord.PointState.DESTROYED);
                        p.setNextActionAtMs(now + cfg.getBarrelCooldownSeconds() * 1000L);
                        p.setDeferredTries(0);
                    }
                }

                if (allowHeavy) {
                    handleBarrelRespawn(commandBuffer, worldChunk, p, cfg, placement, validator, now);
                }
                continue;
            }

            // -----------------------
            // CRATE
            // -----------------------

            if (p.getState() == SpawnPointRecord.PointState.ALIVE) {
                int currentId = worldChunk.getBlock(lx, p.getY(), lz);
                if (currentId != placement.getCrateId()) {
                    p.setState(SpawnPointRecord.PointState.DESTROYED);
                    p.setNextActionAtMs(now + cfg.getCrateRefillCooldownSeconds() * 1000L);
                    p.setDeferredTries(0);
                    p.setCrateFilled(false);
                    continue;
                }
            }

            if (allowHeavy) {
                handleCrateRespawn(commandBuffer, worldChunk, p, cfg, placement, validator, now);
            }

            if (p.getState() != SpawnPointRecord.PointState.ALIVE) continue;

            long at = p.getNextActionAtMs();
            if (at > 0 && now < at) continue;

            CrateState st = getCrateStateStrict(worldChunk, p);

            if (st == CrateState.NOT_READY) {
                p.setNextActionAtMs(now + CRATE_RECHECK_MS);
                continue;
            }

            if (st == CrateState.NOT_EMPTY) {
                p.setCrateFilled(true);
                p.setNextActionAtMs(0L);
                continue;
            }

            // EMPTY real
            if (!p.isCrateFilled()) {
                scheduleFillCrate(commandBuffer, worldChunk, p);
                p.setNextActionAtMs(now + CRATE_RECHECK_MS);
                continue;
            }

            // jugador la vació al 100% -> romper y cooldown
            breakCrateVanilla(commandBuffer, store, worldChunk, p);

            p.setState(SpawnPointRecord.PointState.DESTROYED);
            p.setCrateFilled(false);
            p.setDeferredTries(0);
            p.setNextActionAtMs(now + cfg.getCrateRefillCooldownSeconds() * 1000L);
        }
    }

    private void doInitialIncremental(CommandBuffer<ChunkStore> commandBuffer,
                                      WorldChunk worldChunk,
                                      BarrelCrateChunkState state,
                                      BarrelCrateConfig cfg,
                                      SpawnValidator validator,
                                      PlacementService placement,
                                      long now) {

        if (state.getInitAttempt() == 0 && state.getInitPlaced() == 0) {
            float chance = cfg.getChunkSpawnChance();
            if (chance <= 0f) { state.setGenerated(true); return; }
            if (chance < 1f) {
                double r = chunkRandom01(worldChunk.getX(), worldChunk.getZ());
                if (r >= chance) { state.setGenerated(true); return; }
            }
        }

        int target = cfg.getSpawnPerChunk();
        int attemptsMax = Math.max(target * cfg.getSpawnAttemptsMultiplier(), target);

        int chunkX = worldChunk.getX();
        int chunkZ = worldChunk.getZ();

        int attempt = state.getInitAttempt();
        int placed = state.getInitPlaced();

        int localBudget = INIT_ATTEMPTS_PER_TICK;

        while (localBudget-- > 0 && attempt < attemptsMax && placed < target) {
            attempt++;

            int[] xz = planner.pickLocalXZ();
            int localX = xz[0];
            int localZ = xz[1];

            SpawnValidator.Result res = validator.validate(worldChunk, localX, localZ);
            if (!res.ok()) continue;

            int worldX = (chunkX << 4) + localX;
            int worldZ = (chunkZ << 4) + localZ;
            int yPlace = res.yPlace();

            SpawnPlanner.SpawnChoice choice = planner.pickType();
            SpawnPointRecord.PointType type = switch (choice) {
                case BARREL_T1 -> SpawnPointRecord.PointType.BARREL_T1;
                case BARREL_T2 -> SpawnPointRecord.PointType.BARREL_T2;
                case CRATE -> SpawnPointRecord.PointType.CRATE;
            };

            boolean ok = placement.place(commandBuffer, worldChunk, type, worldX, yPlace, worldZ);
            if (!ok) continue;

            SpawnPointRecord point = new SpawnPointRecord(type, worldX, yPlace, worldZ);
            point.setState(SpawnPointRecord.PointState.ALIVE);

            if (type == SpawnPointRecord.PointType.CRATE) {
                point.setCrateFilled(false);
                point.setNextActionAtMs(now);
            }

            state.addPoint(point);
            placed++;
        }

        state.setInitAttempt(attempt);
        state.setInitPlaced(placed);

        if (placed >= target || attempt >= attemptsMax) state.setGenerated(true);
    }

    private static double chunkRandom01(int chunkX, int chunkZ) {
        long s = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        s += 0x9E3779B97F4A7C15L;
        s = (s ^ (s >>> 30)) * 0xBF58476D1CE4E5B9L;
        s = (s ^ (s >>> 27)) * 0x94D049BB133111EBL;
        s = s ^ (s >>> 31);
        return ((s >>> 11) * 0x1.0p-53);
    }

    // -----------------------
    // Respawns
    // -----------------------

    private void handleBarrelRespawn(CommandBuffer<ChunkStore> commandBuffer, WorldChunk worldChunk, SpawnPointRecord p,
                                     BarrelCrateConfig cfg, PlacementService placement, SpawnValidator validator, long now) {
        if (p.getState() != SpawnPointRecord.PointState.DESTROYED && p.getState() != SpawnPointRecord.PointState.DEFERRED) return;
        if (now < p.getNextActionAtMs()) return;

        int lx = p.getX() & 15;
        int lz = p.getZ() & 15;

        SpawnValidator.Result res = validator.validate(worldChunk, lx, lz);
        if (!res.ok() || res.yPlace() != p.getY()) {
            defer(p, cfg, now);
            return;
        }

        boolean ok = placement.place(commandBuffer, worldChunk, p.getType(), p.getX(), p.getY(), p.getZ());
        if (ok) {
            p.setState(SpawnPointRecord.PointState.ALIVE);
            p.setDeferredTries(0);
        } else {
            defer(p, cfg, now);
        }
    }

    private void handleCrateRespawn(CommandBuffer<ChunkStore> commandBuffer, WorldChunk worldChunk, SpawnPointRecord p,
                                    BarrelCrateConfig cfg, PlacementService placement, SpawnValidator validator, long now) {

        if (p.getState() != SpawnPointRecord.PointState.DESTROYED && p.getState() != SpawnPointRecord.PointState.DEFERRED) return;
        if (now < p.getNextActionAtMs()) return;

        int lx = p.getX() & 15;
        int lz = p.getZ() & 15;

        SpawnValidator.Result res = validator.validate(worldChunk, lx, lz);
        if (!res.ok() || res.yPlace() != p.getY()) {
            defer(p, cfg, now);
            return;
        }

        boolean ok = placement.place(commandBuffer, worldChunk, SpawnPointRecord.PointType.CRATE, p.getX(), p.getY(), p.getZ());
        if (ok) {
            p.setState(SpawnPointRecord.PointState.ALIVE);
            p.setDeferredTries(0);
            p.setCrateFilled(false);
            p.setNextActionAtMs(now);
        } else {
            defer(p, cfg, now);
        }
    }

    private void defer(SpawnPointRecord p, BarrelCrateConfig cfg, long now) {
        int tries = p.getDeferredTries() + 1;
        p.setDeferredTries(tries);

        if (tries >= cfg.getMaxDeferredRetries()) {
            p.setState(SpawnPointRecord.PointState.DISABLED);
            return;
        }

        p.setState(SpawnPointRecord.PointState.DEFERRED);
        p.setNextActionAtMs(now + cfg.getDeferredRetrySeconds() * 1000L);
    }

    // -----------------------
    // CRATE FILL (loot JSON)
    // -----------------------

    private boolean scheduleFillCrate(CommandBuffer<ChunkStore> commandBuffer, WorldChunk worldChunk, SpawnPointRecord p) {
        final int lx = p.getX() & 15;
        final int ly = p.getY();
        final int lz = p.getZ() & 15;

        ItemContainerBlockState pre = getItemContainerState(worldChunk, lx, ly, lz);
        if (pre == null) return false;

        ItemContainer c0 = pre.getItemContainer();
        final short cap = c0.getCapacity();
        if (cap <= 0) return false;

        CrateLootService loot = crateLoot();
        if (loot == null) return false;

        // ✅ método nuevo
        List<ItemStack> drops = loot.rollCrateGuaranteed(cap);
        if (drops == null || drops.isEmpty()) return false;

        final WorldChunk fChunk = worldChunk;
        final List<ItemStack> fDrops = drops;

        commandBuffer.run(_store -> {
            ItemContainerBlockState icbs = getItemContainerState(fChunk, lx, ly, lz);
            if (icbs == null) return;

            ItemContainer c = icbs.getItemContainer();
            c.clear();

            int slot = 0;
            for (ItemStack stack : fDrops) {
                if (stack == null) continue;
                if (isEmptyStack(stack)) continue;
                if (stack.getQuantity() <= 0) continue;
                if (slot >= cap) break;

                c.setItemStackForSlot((short) slot, stack, false);
                slot++;
            }
        });

        return true;
    }

    // -----------------------
    // BREAK VANILLA
    // -----------------------

    private void breakCrateVanilla(CommandBuffer<ChunkStore> commandBuffer,
                                   Store<ChunkStore> chunkStore,
                                   WorldChunk worldChunk,
                                   SpawnPointRecord p) {

        final Vector3i target = new Vector3i(p.getX(), p.getY(), p.getZ());
        final Ref<ChunkStore> chunkRef = worldChunk.getReference();
        final World world = worldChunk.getWorld();

        if (world == null || chunkRef == null) return;

        final Store<EntityStore> entityStore = world.getEntityStore().getStore();

        commandBuffer.run(_cs -> BlockHarvestUtils.performBlockBreak(
                null,
                null,
                target,
                chunkRef,
                entityStore,
                _cs
        ));
    }

    // -----------------------
    // CRATE EMPTY DETECTION
    // -----------------------

    private enum CrateState { NOT_READY, EMPTY, NOT_EMPTY }

    private CrateState getCrateStateStrict(WorldChunk worldChunk, SpawnPointRecord p) {
        ItemContainerBlockState icbs = getItemContainerState(worldChunk, p.getX() & 15, p.getY(), p.getZ() & 15);
        if (icbs == null) return CrateState.NOT_READY;

        ItemContainer c = icbs.getItemContainer();
        short cap = c.getCapacity();

        for (short i = 0; i < cap; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == null) continue;
            if (isEmptyStack(s)) continue;
            if (s.getQuantity() <= 0) continue;
            return CrateState.NOT_EMPTY;
        }
        return CrateState.EMPTY;
    }

    private static boolean isEmptyStack(ItemStack s) {
        try {
            return s.isEmpty();
        } catch (Throwable ignored) {
            try {
                String id = s.getItemId();
                return id == null || id.equals("Empty");
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }

    @SuppressWarnings({"removal", "deprecation"})
    private static ItemContainerBlockState getItemContainerState(WorldChunk worldChunk, int lx, int y, int lz) {
        Object state = worldChunk.getState(lx, y, lz);
        if (state instanceof ItemContainerBlockState icbs) return icbs;
        return null;
    }
}