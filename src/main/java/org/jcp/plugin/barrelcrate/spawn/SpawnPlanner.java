package org.jcp.plugin.barrelcrate.spawn;

import org.jcp.plugin.barrelcrate.BarrelCrateConfig;

import java.util.Random;

public class SpawnPlanner {
    private final Random random = new Random();

    // Defaults equivalentes a lo hardcodeado antes (45 / 30 / 25)
    private int weightBarrelT1 = 45;
    private int weightBarrelT2 = 30;
    private int weightCrate = 25;

    public int[] pickLocalXZ() {
        int lx = random.nextInt(16);
        int lz = random.nextInt(16);
        return new int[]{lx, lz};
    }

    /**
     * Actualiza pesos desde config (siempre llamalo antes de spawnear, o cuando cambie la config).
     */
    public void applyConfig(BarrelCrateConfig cfg) {
        if (cfg == null) return;
        setWeights(cfg.getSpawnWeightBarrelT1(), cfg.getSpawnWeightBarrelT2(), cfg.getSpawnWeightCrate());
    }

    public void setWeights(int barrelT1, int barrelT2, int crate) {
        this.weightBarrelT1 = Math.max(0, barrelT1);
        this.weightBarrelT2 = Math.max(0, barrelT2);
        this.weightCrate = Math.max(0, crate);
    }

    public SpawnChoice pickType() {
        int w1 = weightBarrelT1;
        int w2 = weightBarrelT2;
        int wc = weightCrate;

        int total = w1 + w2 + wc;

        // fallback seguro si alguien pone todo en 0
        if (total <= 0) {
            // vuelve al comportamiento anterior
            w1 = 45; w2 = 30; wc = 25;
            total = 100;
        }

        int roll = random.nextInt(total); // 0..total-1

        if (roll < w1) return SpawnChoice.BARREL_T1;
        roll -= w1;

        if (roll < w2) return SpawnChoice.BARREL_T2;
        return SpawnChoice.CRATE;
    }

    public enum SpawnChoice {
        BARREL_T1,
        BARREL_T2,
        CRATE
    }
}