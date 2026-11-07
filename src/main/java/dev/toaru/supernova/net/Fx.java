package dev.toaru.supernova.net;

import dev.toaru.supernova.client.ClientFx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** Server-side entry point for visual effects. */
public final class Fx {
    private Fx() { }
    private static long nextKey = 1;

    public static Builder make(ServerLevel level, Vec3 at, FxType type, int durationTicks) {
        return new Builder(level, at, type, durationTicks);
    }

    public static void cancel(ServerLevel level, long key) {
        if (key <= 0) return;
        Net.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), new FxCancelMsg(key));
    }

    public static void hint(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    public static final class Builder {
        private final ServerLevel level;
        private final Vec3 at;
        private final FxType type;
        private final int duration;
        private Vec3 aux = Vec3.ZERO;
        private int a = -1, b = -1, c = -1;
        private float power = 1f;
        private double range = 128.0;

        private Builder(ServerLevel level, Vec3 at, FxType type, int duration) {
            this.level = level; this.at = at; this.type = type; this.duration = Math.max(1, duration);
        }

        public Builder aux(Vec3 v) { this.aux = v; return this; }
        public Builder power(float v) { this.power = v; return this; }
        public Builder range(double v) { this.range = v; return this; }

        public long send() {
            long key = nextKey++;
            FxStartMsg msg = new FxStartMsg(key, type, duration, at.x, at.y, at.z, aux.x, aux.y, aux.z, a, b, c, power, 0L);
            Net.CHANNEL.send(PacketDistributor.NEAR.with(
                    () -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, range, level.dimension())), msg);
            return key;
        }
    }
}