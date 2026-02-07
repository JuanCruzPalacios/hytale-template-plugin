package org.jcp.plugin.missile;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public final class MissileTablePage extends InteractiveCustomUIPage<MissileTablePage.Event> {

    private final BlockPosition tablePos;

    private String cachedX = "";
    private String cachedZ = "";

    public MissileTablePage(@Nonnull PlayerRef playerRef, @Nonnull BlockPosition tablePos) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.tablePos = tablePos;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerEntityRef,
                      @Nonnull UICommandBuilder ui,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        ui.append("Pages/ExplosivesPack/MissileTablePage.ui");

        Map<String, String> xData = new HashMap<>();
        xData.put("@TargetX", "#TargetX.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TargetX", new EventData(xData), false);

        Map<String, String> zData = new HashMap<>();
        zData.put("@TargetZ", "#TargetZ.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TargetZ", new EventData(zData), false);

        Map<String, String> launchData = new HashMap<>();
        launchData.put("Action", "Launch");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LaunchButton", new EventData(launchData), true);

        Map<String, String> closeData = new HashMap<>();
        closeData.put("Action", "Close");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", new EventData(closeData), false);

        ui.set("#Error.Visible", false);
        ui.set("#Status.Visible", false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> playerEntityRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull Event data) {

        Player player = (Player) store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) return;

        boolean updated = false;
        if (data.targetX != null && !data.targetX.trim().isEmpty()) {
            cachedX = data.targetX.trim();
            updated = true;
        }
        if (data.targetZ != null && !data.targetZ.trim().isEmpty()) {
            cachedZ = data.targetZ.trim();
            updated = true;
        }
        if (updated && (data.action == null || data.action.trim().isEmpty())) {
            sendUpdate(new UICommandBuilder(), null, false);
            return;
        }

        if ("Close".equals(data.action)) {
            close();
            return;
        }
        if (!"Launch".equals(data.action)) {
            sendUpdate(new UICommandBuilder(), null, false);
            return;
        }

        World world = Universe.get().getDefaultWorld();
        if (world == null) {
            showErrorOnly();
            player.sendMessage(Message.raw("[MissileTable] No world available."));
            return;
        }

        int bx = tablePos.x;
        int by = tablePos.y;
        int bz = tablePos.z;

        ExplosivesPackMissileFeature.MissileTableState st =
                ExplosivesPackMissileFeature.getOrCreate(world.getName(), bx, by, bz);

        long now = System.currentTimeMillis();
        if (st.isOnCooldown(now)) {
            long sec = (st.cooldownRemainingMs(now) + 999) / 1000;
            showErrorOnly();
            player.sendMessage(Message.raw("[MissileTable] Table is recharging. Remaining: " + sec + "s"));
            return;
        }

        String sx = (cachedX == null) ? "" : cachedX.trim();
        String sz = (cachedZ == null) ? "" : cachedZ.trim();

        int x, z;
        try {
            x = Integer.parseInt(sx);
            z = Integer.parseInt(sz);
        } catch (Exception e) {
            showErrorOnly();
            player.sendMessage(Message.raw("[MissileTable] Invalid coordinates. Enter integers for X and Z."));
            player.sendMessage(Message.raw("[MissileTable] Debug: X='" + sx + "' Z='" + sz + "'"));
            return;
        }

        CombinedItemContainer inv = player.getInventory().getCombinedHotbarFirst();
        ItemStackTransaction tx = inv.removeItemStack(new ItemStack("Missile", 1), true, true);
        if (!tx.succeeded()) {
            showErrorOnly();
            player.sendMessage(Message.raw("[MissileTable] Missing required item: Missile"));
            return;
        }

        st.targetX = x;
        st.targetZ = z;
        st.pendingLaunchAtMs = now + 60_000L;
        st.cooldownUntilMs = now + 3_600_000L;

        // ✅ para que la alarma suene 1 vez en este lanzamiento
        st.alarmPlayed = false;

        ExplosivesPackMissileFeature.saveSoon();

        showStatusOnly();
        player.sendMessage(Message.raw("[MissileTable] Missile scheduled at X=" + x + " Z=" + z + " (ETA 60s)"));
        close();
    }

    private void showErrorOnly() {
        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#Error.Visible", true);
        ui.set("#Status.Visible", false);
        sendUpdate(ui, null, false);
    }

    private void showStatusOnly() {
        UICommandBuilder ui = new UICommandBuilder();
        ui.set("#Error.Visible", false);
        ui.set("#Status.Visible", true);
        sendUpdate(ui, null, false);
    }

    public static final class Event {
        static final String KEY_ACTION = "Action";
        static final String KEY_TARGET_X = "@TargetX";
        static final String KEY_TARGET_Z = "@TargetZ";

        public static final BuilderCodec<Event> CODEC;

        static {
            BuilderCodec.Builder b = (BuilderCodec.Builder) BuilderCodec.builder(Event.class, Event::new);

            b = (BuilderCodec.Builder) b.append(
                    new KeyedCodec(KEY_ACTION, (Codec) Codec.STRING),
                    (Object entry, Object v) -> ((Event) entry).action = (String) v,
                    (Object entry) -> ((Event) entry).action
            ).add();

            b = (BuilderCodec.Builder) b.append(
                    new KeyedCodec(KEY_TARGET_X, (Codec) Codec.STRING),
                    (Object entry, Object v) -> ((Event) entry).targetX = (String) v,
                    (Object entry) -> ((Event) entry).targetX
            ).add();

            b = (BuilderCodec.Builder) b.append(
                    new KeyedCodec(KEY_TARGET_Z, (Codec) Codec.STRING),
                    (Object entry, Object v) -> ((Event) entry).targetZ = (String) v,
                    (Object entry) -> ((Event) entry).targetZ
            ).add();

            CODEC = (BuilderCodec<Event>) b.build();
        }

        String action;
        String targetX;
        String targetZ;
    }
}
