package org.jcp.plugin.barrelcrate;

import org.jcp.plugin.barrelcrate.loot.CrateLootService;

import javax.annotation.Nullable;

public final class BarrelCrateServices {

    private static volatile CrateLootService crateLoot;

    private BarrelCrateServices() {}

    public static void setCrateLoot(CrateLootService service) {
        crateLoot = service;
    }

    @Nullable
    public static CrateLootService crateLoot() {
        return crateLoot;
    }
}