package org.jcp.plugin.barrelcrate.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jcp.plugin.barrelcrate.BarrelCrateServices;
import org.jcp.plugin.barrelcrate.loot.CrateLootService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class BCOpenCrateInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<BCOpenCrateInteraction> CODEC =
            BuilderCodec.builder(BCOpenCrateInteraction.class, BCOpenCrateInteraction::new, SimpleBlockInteraction.CODEC)
                    .documentation("BarrelCrate: Open container + ensure filled + break on empty when closed.")
                    .build();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public BCOpenCrateInteraction() {
        super("BC_OpenCrate");
    }

    @Override
    protected void interactWithBlock(@Nonnull World world,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull InteractionType type,
                                     @Nonnull InteractionContext context,
                                     @Nullable ItemStack itemInHand,
                                     @Nonnull Vector3i pos,
                                     @Nonnull CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        @SuppressWarnings({"removal","deprecation"})
        Object bs = world.getState(pos.x, pos.y, pos.z, true);

        if (!(bs instanceof ItemContainerState itemState)) {
            player.sendMessage(Message.translation("server.interactions.invalidBlockState")
                    .param("interaction", getClass().getSimpleName())
                    .param("blockState", (bs != null) ? bs.getClass().getSimpleName() : "null"));
            LOGGER.at(Level.WARNING).log("BCOpenCrate: invalid state at %s (%s)", pos, (bs == null ? "null" : bs.getClass().getName()));
            return;
        }

        // ✅ llenar antes de abrir
        ensureFilledIfEmpty(itemState, pos);

        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (!itemState.isAllowViewing() || !itemState.canOpen(ref, (ComponentAccessor<EntityStore>) commandBuffer)) {
            LOGGER.at(Level.INFO).log("BCOpenCrate: canOpen denied at %s", pos);
            return;
        }

        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) return;
        UUID uuid = uuidComponent.getUuid();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) return;

        @SuppressWarnings({"removal","deprecation"})
        int rotation = chunk.getRotationIndex(pos.x, pos.y, pos.z);

        ContainerBlockWindow window = new ContainerBlockWindow(
                pos.x, pos.y, pos.z,
                rotation,
                blockType,
                itemState.getItemContainer()
        );

        Map<UUID, ContainerBlockWindow> windows = itemState.getWindows();

        if (windows.putIfAbsent(uuid, window) == null) {
            if (player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, new Window[]{window})) {

                window.registerCloseEvent(evt -> {
                    try {
                        windows.remove(uuid, window);

                        @SuppressWarnings({"removal","deprecation"})
                        Object bs2 = world.getState(pos.x, pos.y, pos.z, true);
                        if (!(bs2 instanceof ItemContainerState itemState2)) {
                            return;
                        }

                        ItemContainer c = itemState2.getItemContainer();
                        boolean empty = isContainerEmptyStrict(c);
                        if (!empty) return;

                        World w = ((EntityStore) commandBuffer.getExternalData()).getWorld();
                        if (w == null) return;

                        ChunkStore chunkStore = w.getChunkStore();
                        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
                        if (chunkRef == null) {
                            return;
                        }

                        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();

                        BlockHarvestUtils.performBlockBreak(
                                ref,
                                null,
                                new Vector3i(pos.x, pos.y, pos.z),
                                chunkRef,
                                (ComponentAccessor<EntityStore>) commandBuffer,
                                (ComponentAccessor<ChunkStore>) chunkStoreStore
                        );

                    } catch (Throwable t) {
                        LOGGER.at(Level.SEVERE).withCause(t).log("BCOpenCrate: close hook failed at " + pos);
                    }
                });

            } else {
                windows.remove(uuid, window);
            }
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType type,
                                             @Nonnull InteractionContext context,
                                             @Nullable ItemStack itemInHand,
                                             @Nonnull World world,
                                             @Nonnull Vector3i pos) {
        // no-op
    }

    private static void ensureFilledIfEmpty(ItemContainerState state, Vector3i pos) {
        try {
            ItemContainer c = state.getItemContainer();
            if (!isContainerEmptyStrict(c)) return;

            CrateLootService loot = BarrelCrateServices.crateLoot();
            if (loot == null) {
                LOGGER.at(Level.WARNING).log("BCOpenCrate: loot service not set; cannot fill at %s", pos);
                return;
            }

            short cap = c.getCapacity();

            // ✅ método nuevo del service
            List<ItemStack> drops = loot.rollCrateGuaranteed(cap);

            if (drops == null || drops.isEmpty()) {
                LOGGER.at(Level.WARNING).log("BCOpenCrate: loot roll returned empty at %s cap=%s", pos, Short.valueOf(cap));
                return;
            }

            c.clear();
            int slot = 0;
            for (ItemStack s : drops) {
                if (s == null || s.isEmpty() || s.getQuantity() <= 0) continue;
                if (slot >= cap) break;
                c.setItemStackForSlot((short) slot, s, false);
                slot++;
            }

        } catch (Throwable t) {
            LOGGER.at(Level.SEVERE).withCause(t).log("BCOpenCrate: ensureFilled failed at " + pos);
        }
    }

    private static boolean isContainerEmptyStrict(ItemContainer c) {
        short cap = c.getCapacity();
        for (short i = 0; i < cap; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == null) continue;
            if (s.isEmpty()) continue;
            if (s.getQuantity() <= 0) continue;
            return false;
        }
        return true;
    }
}