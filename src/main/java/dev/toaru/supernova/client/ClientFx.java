package dev.toaru.supernova.client;

import dev.toaru.supernova.net.FxStartMsg;
import dev.toaru.supernova.net.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/** Client-side registry of active visual effects. */
public final class ClientFx {
    private ClientFx() { }

    private static long NEXT_KEY = 1;
    public static final Map<Long, Effect> ACTIVE = new HashMap<>();

    public static long nextKey() { return NEXT_KEY++; }

    public static void start(FxStartMsg msg) {
        Vec3 pos = new Vec3(msg.x, msg.y, msg.z);
        Vec3 aux = new Vec3(msg.ax, msg.ay, msg.az);
        ACTIVE.put(msg.key, new Effect(msg.key, msg.type, pos, msg.duration, aux, 220.0));
    }

    public static void cancel(long key) { ACTIVE.remove(key); }

    public static void tick() {
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        ACTIVE.values().removeIf(e -> e.isExpired(now));
    }

    public static final class Effect {
        public final long key;
        public final FxType type;
        public final Vec3 pos;
        public final int duration;
        public final Vec3 aux;
        public final double range;
        private final long startTime;

        public Effect(long key, FxType type, Vec3 pos, int duration, Vec3 aux, double range) {
            this.key = key;
            this.type = type;
            this.pos = pos;
            this.duration = duration;
            this.aux = aux;
            this.range = range;
            this.startTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        }

        public double age(long now, float partial) {
            return (now - startTime) + partial;
        }

        public boolean isExpired(long now) {
            return now - startTime >= duration;
        }
    }
}