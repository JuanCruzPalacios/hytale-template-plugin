package org.jcp.plugin.missile;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MissileTablePageSupplier implements OpenCustomUIInteraction.CustomPageSupplier {

    public static final BuilderCodec<MissileTablePageSupplier> CODEC =
            ((BuilderCodec.Builder) BuilderCodec.builder(MissileTablePageSupplier.class, MissileTablePageSupplier::new))
                    .build();

    @Override
    public @Nullable CustomUIPage tryCreate(@Nonnull Ref<EntityStore> playerEntityRef,
                                            @Nonnull ComponentAccessor<EntityStore> accessor,
                                            @Nonnull PlayerRef playerRef,
                                            @Nonnull InteractionContext context) {

        BlockPosition target = context.getTargetBlock();
        if (target == null) return null;

        return new MissileTablePage(playerRef, target);
    }
}
