package org.jcp.plugin;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.jcp.plugin.interaction.ExplodeSelectiveInteraction;
import org.jcp.plugin.missile.ExplosivesPackMissileFeature;
import org.jcp.plugin.missile.MissileTablePageSupplier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ExplosivesPackPlugin extends JavaPlugin {

    static {
        OpenCustomUIInteraction.PAGE_CODEC.register(
                "MissileTable",
                MissileTablePageSupplier.class,
                (Codec) MissileTablePageSupplier.CODEC
        );

        Interaction.CODEC.register(
                "ExplodeSelective",
                ExplodeSelectiveInteraction.class,
                ExplodeSelectiveInteraction.CODEC
        );

        Interaction.CODEC.register(
                "BC_OpenCrate",
                org.jcp.plugin.barrelcrate.interaction.BCOpenCrateInteraction.class,
                org.jcp.plugin.barrelcrate.interaction.BCOpenCrateInteraction.CODEC
        );
    }

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

        // ✅ Misiles
        try {
            ExplosivesPackMissileFeature.register(this);
        } catch (Throwable t) {
            getLogger().at(Level.SEVERE).withCause(t).log("ExplosivesPack: failed to register missile feature");
        }

        // ✅ BootEvent: assets cargados + world pipeline listo
        getEventRegistry().register(EventPriority.NORMAL, BootEvent.class, evt -> {

            // 1) Patch tools
            try {
                patchAllToolsForReinforcedRocks();
            } catch (Throwable t) {
                getLogger().at(Level.SEVERE).withCause(t).log("ExplosivesPack: failed to patch tool specs for reinforced rocks");
            }

            // 2) ✅ Registrar BarrelCrate acá (NO en setup)
            try {
                org.jcp.plugin.barrelcrate.BarrelCrateFeature.register(this);
                getLogger().at(Level.INFO).log("ExplosivesPack: BarrelCrate feature registered (BootEvent)");
            } catch (Throwable t) {
                getLogger().at(Level.SEVERE).withCause(t).log("ExplosivesPack: failed to register BarrelCrate feature (BootEvent)");
            }
        });
    }

    private void patchAllToolsForReinforcedRocks() throws Exception {
        Field specsField = ItemTool.class.getDeclaredField("specs");
        specsField.setAccessible(true);

        int patchedTools = 0;
        int injectedSpecs = 0;

        DefaultAssetMap<String, Item> dam = Item.getAssetStore().getAssetMap();
        Map<String, Item> allItems = dam.getAssetMap();

        for (Item item : allItems.values()) {
            ItemTool tool = item.getTool();
            if (tool == null) continue;

            ItemToolSpec[] specs = tool.getSpecs();
            if (specs == null || specs.length == 0) continue;

            ItemToolSpec baseSpec = findSpec(specs, "VolcanicRocks");
            if (baseSpec == null) continue;

            Set<String> existing = new HashSet<>();
            for (ItemToolSpec s : specs) {
                if (s != null && s.getGatherType() != null) existing.add(s.getGatherType());
            }

            ArrayList<ItemToolSpec> out = new ArrayList<>(specs.length + REINFORCED_GATHER_TYPES.length);
            for (ItemToolSpec s : specs) out.add(s);

            boolean changed = false;
            for (String gt : REINFORCED_GATHER_TYPES) {
                if (existing.contains(gt)) continue;

                ItemToolSpec injected = new ItemToolSpec(gt, baseSpec.getPower(), baseSpec.getQuality());
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
                "ExplosivesPack: patched " + patchedTools + " tools, injected " + injectedSpecs + " reinforced gather specs."
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