package org.jcp.plugin.interaction;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector4d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import com.hypixel.hytale.server.core.entity.ExplosionUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.meta.DynamicMetaStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.InteractionTypeUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Igual que ExplodeInteraction, pero con toggle:
 * - Si SelfDamageProjectiles=true y la entidad que explota es un proyectil,
 *   usa EnvironmentSource("explosion") para que pueda dañarse el shooter.
 *
 * JSON:
 * {
 *   "Type": "ExplodeSelective",
 *   "SelfDamageProjectiles": true,
 *   "Config": { ... ExplosionConfig ... }
 * }
 */
public final class ExplodeSelectiveInteraction extends SimpleInstantInteraction {

    @Nonnull
    public static final BuilderCodec<ExplodeSelectiveInteraction> CODEC =
            BuilderCodec.builder(ExplodeSelectiveInteraction.class, ExplodeSelectiveInteraction::new, SimpleInstantInteraction.CODEC)
                    .documentation("ExplodeInteraction with optional self-damage for projectile explosions.")
                    .appendInherited(
                            new KeyedCodec<>("Config", ExplosionConfig.CODEC),
                            (interaction, cfg) -> interaction.config = cfg,
                            interaction -> interaction.config,
                            (interaction, parent) -> interaction.config = parent.config
                    )
                    .addValidator(Validators.nonNull())
                    .documentation("Explosion config.")
                    .add()
                    .appendInherited(
                            new KeyedCodec<>("SelfDamageProjectiles", Codec.BOOLEAN),
                            (interaction, flag) -> interaction.selfDamageProjectiles = flag,
                            interaction -> interaction.selfDamageProjectiles,
                            (interaction, parent) -> interaction.selfDamageProjectiles = parent.selfDamageProjectiles
                    )
                    .documentation("If true, projectile-triggered explosions use Environment source (so the shooter can be damaged).")
                    .add()
                    .build();

    @Nonnull
    public static final Damage.EnvironmentSource DAMAGE_SOURCE_EXPLOSION = new Damage.EnvironmentSource("explosion");

    @Nullable
    private ExplosionConfig config;

    private boolean selfDamageProjectiles = false;

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        assert this.config != null;

        DynamicMetaStore<InteractionContext> metaStore = context.getMetaStore();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        assert commandBuffer != null;

        Ref<EntityStore> ref = context.getEntity();
        Ref<EntityStore> ownerRef = context.getOwningEntity();

        World world = ((EntityStore) commandBuffer.getExternalData()).getWorld();
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        Vector3d position;

        BlockPosition blockPosition = (BlockPosition) metaStore.getIfPresentMetaObject(Interaction.TARGET_BLOCK);
        Vector4d hitLocation = (Vector4d) metaStore.getIfPresentMetaObject(Interaction.HIT_LOCATION);

        if (hitLocation != null) {
            position = new Vector3d(hitLocation.x, hitLocation.y, hitLocation.z);
        } else if (InteractionTypeUtils.isCollisionType(type) && blockPosition != null) {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockPosition.x, blockPosition.z);
            Ref<ChunkStore> chunkReference = ((ChunkStore) chunkStore.getExternalData()).getChunkReference(chunkIndex);

            if (chunkReference == null || !chunkReference.isValid()) return;

            WorldChunk worldChunkComponent = (WorldChunk) chunkStore.getComponent(chunkReference, WorldChunk.getComponentType());
            assert worldChunkComponent != null;

            BlockChunk blockChunkComponent = (BlockChunk) chunkStore.getComponent(chunkReference, BlockChunk.getComponentType());
            assert blockChunkComponent != null;

            BlockType blockType = worldChunkComponent.getBlockType(blockPosition.x, blockPosition.y, blockPosition.z);
            if (blockType == null) return;

            BlockSection blockSection = blockChunkComponent.getSectionAtBlockY(blockPosition.y);
            int rotationIndex = blockSection.getRotationIndex(blockPosition.x, blockPosition.y, blockPosition.z);

            position = new Vector3d();
            blockType.getBlockCenter(rotationIndex, position);
            position.add(blockPosition.x, blockPosition.y, blockPosition.z);
        } else {
            TransformComponent transformComponent = (TransformComponent) commandBuffer.getComponent(ref, TransformComponent.getComponentType());
            assert transformComponent != null;
            position = transformComponent.getPosition();
        }

        Archetype<EntityStore> archetype = commandBuffer.getArchetype(ref);
        boolean isProjectile = archetype.contains(Projectile.getComponentType());

        // ✅ Solo cambia esto respecto a vanilla:
        // si es proyectil y SelfDamageProjectiles=true => EnvironmentSource
        Damage.Source damageSource;
        if (isProjectile && this.selfDamageProjectiles) {
            damageSource = DAMAGE_SOURCE_EXPLOSION;
        } else if (isProjectile) {
            damageSource = new Damage.ProjectileSource(ownerRef, ref);
        } else {
            damageSource = DAMAGE_SOURCE_EXPLOSION;
        }

        ExplosionUtils.performExplosion(
                damageSource,
                position,
                this.config,
                isProjectile ? ref : null,               // ignoreRef (no dañar la entidad proyectil)
                commandBuffer,
                (ComponentAccessor) chunkStore
        );
    }
}
