package org.jcp.plugin;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec; // ✅ ESTA ES LA CORRECTA
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ExplosivesPackPlugin extends JavaPlugin {

    private static final String[] REINFORCED_GATHER_TYPES = new String[] {
            "ReinforcedRocks_T1",
            "ReinforcedRocks_T2",
            "ReinforcedRocks_T3",
            "ReinforcedRocks_T4",
            "ReinforcedRocks_T5"
    };

    public ExplosivesPackPlugin(com.hypixel.hytale.server.core.plugin.JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getEventRegistry().register(EventPriority.NORMAL, BootEvent.class, evt -> {
            try {
                patchAllToolsForReinforcedRocks();
            } catch (Throwable t) {
                getLogger().at(Level.SEVERE).log("ExplosivesPack: failed to patch tool specs for reinforced rocks", t);
            }
        });
    }

    private void patchAllToolsForReinforcedRocks() throws Exception {
        Field specsField = ItemTool.class.getDeclaredField("specs");
        specsField.setAccessible(true);

        int patchedTools = 0;
        int injectedSpecs = 0;

        // ✅ DefaultAssetMap -> Map<String, Item>
        DefaultAssetMap<String, Item> dam = Item.getAssetStore().getAssetMap();
        Map<String, Item> allItems = dam.getAssetMap();

        for (Item item : allItems.values()) {
            ItemTool tool = item.getTool();
            if (tool == null) continue;

            ItemToolSpec[] specs = tool.getSpecs();
            if (specs == null || specs.length == 0) continue;

            ItemToolSpec rocksSpec = findSpec(specs, "VolcanicRocks");
            if (rocksSpec == null) continue;

            Set<String> existing = new HashSet<>();
            for (ItemToolSpec s : specs) {
                if (s != null && s.getGatherType() != null) existing.add(s.getGatherType());
            }

            ArrayList<ItemToolSpec> out = new ArrayList<>(specs.length + REINFORCED_GATHER_TYPES.length);
            for (ItemToolSpec s : specs) out.add(s);

            boolean changed = false;
            for (String gt : REINFORCED_GATHER_TYPES) {
                if (existing.contains(gt)) continue;

                // Copia el comportamiento de "VolcanicRocks" para que piquen igual
                ItemToolSpec injected = new ItemToolSpec(gt, rocksSpec.getPower(), rocksSpec.getQuality());
                out.add(injected);

                injectedSpecs++;
                changed = true;
            }

            if (changed) {
                specsField.set(tool, out.toArray(new ItemToolSpec[0]));
                patchedTools++;
            }
        }

        getLogger().at(Level.INFO).log(
                String.format("ExplosivesPack: patched %d tools, injected %d reinforced gather specs.", patchedTools, injectedSpecs)
        );
    }

    private static ItemToolSpec findSpec(ItemToolSpec[] specs, String gatherType) {
        for (ItemToolSpec s : specs) {
            if (s == null) continue;
            if (gatherType.equals(s.getGatherType())) return s;
        }
        return null;
    }
}
