package org.jcp.plugin.missile;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MissileSpawner {

    private MissileSpawner() {}

    public static void spawnDownward(@Nonnull World world,
                                     @Nonnull String projectileConfigId,
                                     int targetX,
                                     int targetZ,
                                     @Nonnull JavaPlugin plugin) {

        Store<EntityStore> store = world.getEntityStore().getStore();

        // ✅ DEBUG: chequear si el AssetStore tiene el ID
        try {
            DefaultAssetMap<String, Projectile> dam = Projectile.getAssetStore().getAssetMap();
            Map<String, Projectile> all = dam.getAssetMap();

            boolean exists = all.containsKey(projectileConfigId);
            plugin.getLogger().at(Level.INFO).log("MissileSpawner: Projectile asset exists? " + exists + " id=" + projectileConfigId);
            plugin.getLogger().at(Level.INFO).log("MissileSpawner: Projectile assets loaded = " + all.size());

            if (!exists) {
                int shown = 0;
                StringBuilder sb = new StringBuilder("MissileSpawner: sample projectile ids: ");
                for (String k : all.keySet()) {
                    if (shown++ >= 12) break;
                    sb.append(k).append(", ");
                }
                plugin.getLogger().at(Level.SEVERE).log(sb.toString());
            }
        } catch (Throwable t) {
            plugin.getLogger().at(Level.WARNING).log("MissileSpawner: failed to inspect Projectile AssetStore", t);
        }

        double y = ChunkUtil.HEIGHT_MINUS_1;
        Vector3d spawnPos = new Vector3d(targetX + 0.5, y, targetZ + 0.5);

        plugin.getLogger().at(Level.INFO).log("MissileSpawner.spawnDownward: projectile=" + projectileConfigId +
                " spawnPos=" + spawnPos);

        TimeResource time = (TimeResource) store.getResource(TimeResource.getResourceType());
        if (time == null) {
            plugin.getLogger().at(Level.SEVERE).log("MissileSpawner: TimeResource is null (cannot spawn projectile)");
            return;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time,
                projectileConfigId,
                spawnPos,
                new Vector3f(0f, 0f, 0f)
        );

        if (holder == null) {
            plugin.getLogger().at(Level.SEVERE).log("MissileSpawner: assembleDefaultProjectile returned null!");
            return;
        }

        holder.ensureComponent(Intangible.getComponentType());

        ProjectileComponent proj = (ProjectileComponent) holder.getComponent(ProjectileComponent.getComponentType());
        if (proj == null) {
            plugin.getLogger().at(Level.SEVERE).log("MissileSpawner: ProjectileComponent is null on holder!");
            return;
        }

        boolean ok = proj.initialize();
        plugin.getLogger().at(Level.INFO).log("MissileSpawner: proj.initialize() => " + ok);

        if (!ok) {
            plugin.getLogger().at(Level.SEVERE).log(
                    "MissileSpawner: FAILED to initialize projectile (asset not found/loaded): " + projectileConfigId
            );
            return;
        }

        UUID noOwner = new UUID(0L, 0L);
        proj.shoot(holder, noOwner, spawnPos.x, spawnPos.y, spawnPos.z, 0f, 90f);

        plugin.getLogger().at(Level.INFO).log("MissileSpawner: shoot() called, adding entity to store...");
        store.addEntity(holder, AddReason.SPAWN);
        plugin.getLogger().at(Level.INFO).log("MissileSpawner: entity added (spawn requested).");
    }
}
