package org.jcp.plugin.barrelcrate.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loot service "hardcodeado" (runtime) basado en un archivo JSON persistente.
 *
 * Ahora SOLO maneja la crate (caja).
 *
 * Archivo: plugins/<tuPlugin>/crate_loot.json
 */
public final class CrateLootService {

    public static final String POOL_CRATE = "crate";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private volatile long lastLoadedMtime = -1L;
    private volatile LootConfig config = LootConfig.defaultConfig();

    public CrateLootService(Path dataDir) {
        this.file = dataDir.resolve("crate_loot.json");
        ensureExists();
        reloadIfChanged();
    }

    public void reloadIfChanged() {
        try {
            long mtime = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
            if (mtime == lastLoadedMtime) return;

            String json = Files.readString(file, StandardCharsets.UTF_8);
            LootConfig loaded = GSON.fromJson(json, LootConfig.class);
            if (loaded == null) loaded = LootConfig.defaultConfig();
            loaded.sanitize();

            this.config = loaded;
            this.lastLoadedMtime = mtime;
        } catch (IOException | JsonSyntaxException e) {
            // mantener config anterior si el json está roto
        }
    }

    /**
     * Genera stacks para una crate.
     * ✅ Garantiza al menos 1 item válido si maxSlots > 0 y hay items válidos en config.
     */
    public List<ItemStack> rollCrateGuaranteed(int maxSlots) {
        return rollGuaranteedFromPool(POOL_CRATE, maxSlots);
    }

    /**
     * Roll desde pool con garantía de al menos 1 stack si existe loot válido.
     */
    public List<ItemStack> rollGuaranteedFromPool(String poolId, int maxSlots) {
        reloadIfChanged();

        LootPool pool = getPool(poolId);
        if (pool == null || pool.items == null || pool.items.isEmpty()) return List.of();
        if (maxSlots <= 0) return List.of();

        List<LootEntry> valid = filterValid(pool.items);
        if (valid.isEmpty()) return List.of();

        int minItems = Math.max(1, pool.minItems); // ✅ min 1 garantizado
        int maxItems = Math.max(minItems, pool.maxItems);
        int rolls = randIntInclusive(minItems, maxItems);
        rolls = Math.min(rolls, maxSlots);

        int totalWeight = 0;
        for (LootEntry e : valid) totalWeight += e.weight;
        if (totalWeight <= 0) return List.of();

        ArrayList<ItemStack> out = new ArrayList<>(rolls);

        // roll con reemplazo
        for (int i = 0; i < rolls; i++) {
            LootEntry picked = pickWeighted(valid, totalWeight);
            if (picked == null) continue;

            int qMin = Math.max(1, picked.min);
            int qMax = Math.max(qMin, picked.max);
            int qty = randIntInclusive(qMin, qMax);

            out.add(new ItemStack(picked.itemId, qty, null));
        }

        // ✅ Garantía final: si por algún motivo quedó vacío, forzar 1 ítem válido
        if (out.isEmpty()) {
            LootEntry picked = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));
            int qty = Math.max(1, picked.min);
            out.add(new ItemStack(picked.itemId, qty, null));
        }

        return out;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private LootPool getPool(String poolId) {
        LootConfig c = this.config;
        if (c == null) return null;

        LootPools pools = c.pools;
        if (pools == null) return null;

        if (POOL_CRATE.equalsIgnoreCase(poolId)) return pools.crate;

        // compat / fallback: extra pools si querés (no usados ahora)
        if (pools.extra != null) return pools.extra.get(poolId);

        return null;
    }

    private static List<LootEntry> filterValid(List<LootEntry> items) {
        DefaultAssetMap<String, Item> map = Item.getAssetMap();
        ArrayList<LootEntry> out = new ArrayList<>(items.size());

        for (LootEntry e : items) {
            if (e == null) continue;
            if (e.weight <= 0) continue;
            if (e.itemId == null || e.itemId.isBlank()) continue;

            // ✅ validar existencia real del item en AssetStore
            Item it = map.getAsset(e.itemId);
            if (it == null || it == Item.UNKNOWN) continue;

            out.add(e);
        }
        return out;
    }

    private static LootEntry pickWeighted(List<LootEntry> items, int totalWeight) {
        int r = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int acc = 0;
        for (LootEntry e : items) {
            acc += e.weight;
            if (r <= acc) return e;
        }
        return null;
    }

    private static int randIntInclusive(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }

    private void ensureExists() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                String json = GSON.toJson(LootConfig.defaultConfig());
                Files.writeString(file, json, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------
    // DTOs (config)
    // -------------------------------------------------------------------------

    public static final class LootConfig {
        public int configVersion = 1;

        /** Pools conocidos + opcional extra */
        public LootPools pools;

        public static LootConfig defaultConfig() {
            LootConfig c = new LootConfig();
            c.pools = new LootPools();

            // ✅ crate: loot para llenar
            c.pools.crate = new LootPool();
            c.pools.crate.minItems = 1;
            c.pools.crate.maxItems = 3;
            c.pools.crate.items = new ArrayList<>();
            c.pools.crate.items.add(new LootEntry("Ingredient_Bar_Copper", 50, 1, 3));
            c.pools.crate.items.add(new LootEntry("Ingredient_Bar_Iron",   35, 1, 2));
            c.pools.crate.items.add(new LootEntry("Ingredient_Bar_Gold",   15, 1, 1));

            // opcional: extra pools por id
            c.pools.extra = new HashMap<>();

            return c;
        }

        public void sanitize() {
            if (pools == null) pools = new LootPools();
            pools.sanitize();
        }
    }

    public static final class LootPools {
        public LootPool crate;
        public java.util.Map<String, LootPool> extra;

        public void sanitize() {
            if (crate == null) crate = new LootPool();
            crate.sanitize(false);

            if (extra == null) extra = new HashMap<>();
            for (LootPool p : extra.values()) {
                if (p != null) p.sanitize(false);
            }
        }
    }

    public static final class LootPool {
        public int minItems = 1;
        public int maxItems = 3;
        public List<LootEntry> items = new ArrayList<>();

        public void sanitize(boolean allowZero) {
            if (allowZero) {
                if (minItems < 0) minItems = 0;
            } else {
                if (minItems < 1) minItems = 1;
            }
            if (maxItems < minItems) maxItems = minItems;
            if (items == null) items = new ArrayList<>();
        }
    }

    public static final class LootEntry {
        public String itemId;
        public int weight = 1;
        public int min = 1;
        public int max = 1;

        public LootEntry() {}

        public LootEntry(String itemId, int weight, int min, int max) {
            this.itemId = itemId;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }
    }
}