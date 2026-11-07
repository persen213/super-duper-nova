package dev.toaru.supernova;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

/** All balance numbers. Created at config/supernova-common.toml on first launch. */
public final class Cfg {
    private Cfg() { }
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static DoubleValue d(String name, double def, double min, double max) { return B.defineInRange(name, def, min, max); }
    private static IntValue i(String name, int def, int min, int max) { return B.defineInRange(name, def, min, max); }

    // Gameplay
    public static final DoubleValue CP = d("cp_cost", 8000, 0, 100000);
    public static final DoubleValue OVERLOAD = d("overload_cost", 200, 0, 5000);
    public static final IntValue COOLDOWN = i("cooldown_seconds", 180, 0, 3600);
    public static final DoubleValue RADIUS = d("explosion_radius", 24, 5, 64);
    public static final DoubleValue DAMAGE = d("explosion_damage", 30, 0, 500);
    public static final DoubleValue KNOCKBACK = d("explosion_knockback", 3.0, 0, 10);
    public static final DoubleValue BOSS_DMG = d("boss_damage_fraction", 0.35, 0, 1);
    public static final DoubleValue BOSS_KB = d("boss_knockback_fraction", 0.3, 0, 1);

    // Cinematic phase durations (seconds)
    public static final DoubleValue GATHERING = d("gathering_seconds", 2.0, 0.5, 10);
    public static final DoubleValue COMPRESSION = d("compression_seconds", 2.0, 0.5, 10);
    public static final DoubleValue SILENCE = d("silence_seconds", 1.0, 0.2, 5);
    public static final DoubleValue DETONATION = d("detonation_seconds", 1.0, 0.2, 5);
    public static final DoubleValue AFTERGLOW = d("afterglow_seconds", 2.0, 0.5, 10);

    // Legacy - kept for compatibility
    public static final DoubleValue FORMATION = d("formation_seconds", 1.0, 0.2, 5);
    public static final DoubleValue RING_COUNT = d("ring_count", 4, 1, 8);
    public static final DoubleValue RING_DELAY = d("ring_delay", 0.3, 0.1, 2);

    public static final ForgeConfigSpec SPEC = B.build();

    public static int ticks(DoubleValue seconds) { return (int) Math.round(seconds.get() * 20.0); }
    public static int ticks(IntValue seconds) { return (int) Math.round(seconds.get() * 20.0); }

    // Phase duration helpers (ticks)
    public static int gatheringTicks() { return ticks(GATHERING); }
    public static int compressionTicks() { return ticks(COMPRESSION); }
    public static int silenceTicks() { return ticks(SILENCE); }
    public static int detonationTicks() { return ticks(DETONATION); }
    public static int afterglowTicks() { return ticks(AFTERGLOW); }
    public static int totalTicks() {
        return gatheringTicks() + compressionTicks() + silenceTicks() + detonationTicks() + afterglowTicks();
    }
}
