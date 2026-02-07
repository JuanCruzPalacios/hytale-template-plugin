package org.jcp.plugin.missile;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.Executor;

public final class MissileTableSpawnCommand extends AbstractWorldCommand {

    public static final String PERMISSION = "explosivespack.missiletable.spawn";
    private static final String BLOCK_TYPE_KEY = "Missile_Table";

    public MissileTableSpawnCommand() {
        super("missiletable", "Spawn a Missile Table at your position (admin only)");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> store) {
        Player sender;
        try {
            sender = context.senderAs(Player.class);
        } catch (Exception e) {
            context.sendMessage(Message.raw("Only players can use this command."));
            return;
        }

        if (!sender.hasPermission(PERMISSION, true)) {
            context.sendMessage(Message.raw("You do not have permission: " + PERMISSION));
            return;
        }

        TransformComponent transform = (TransformComponent) store.getComponent(sender.getReference(), TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get player position."));
            return;
        }

        Vector3d p = transform.getPosition();
        Vector3i pos = new Vector3i((int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));

        BlockType blockType = (BlockType) BlockType.getAssetStore().getAssetMap().getAsset(BLOCK_TYPE_KEY);
        if (blockType == null) {
            context.sendMessage(Message.raw("BlockType not found: " + BLOCK_TYPE_KEY));
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);

        world.getChunkAsync(chunkIndex).thenAcceptAsync((WorldChunk chunk) -> {
            // ✅ SOLO el bloque
            chunk.setBlock(pos.x, pos.y, pos.z, blockType);

            // inicializa estado persistente de esa mesa
            ExplosivesPackMissileFeature.getOrCreate(world.getName(), pos.x, pos.y, pos.z);
            ExplosivesPackMissileFeature.saveSoon();

            context.sendMessage(Message.raw("Missile Table spawned at " + pos.x + ", " + pos.y + ", " + pos.z));
        }, (Executor) world);
    }
}
