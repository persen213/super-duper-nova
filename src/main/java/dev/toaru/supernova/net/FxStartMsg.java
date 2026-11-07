package dev.toaru.supernova.net;

import dev.toaru.supernova.client.ClientFx;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: start one visual effect. */
public final class FxStartMsg {
    public final long key;
    public final FxType type;
    public final int duration;
    public final double x, y, z;
    public final double ax, ay, az;
    public final int a, b, c;
    public final float power;
    public final long ref;

    public FxStartMsg(long key, FxType type, int duration, double x, double y, double z, double ax, double ay, double az,
                      int a, int b, int c, float power, long ref) {
        this.key = key; this.type = type; this.duration = duration;
        this.x = x; this.y = y; this.z = z; this.ax = ax; this.ay = ay; this.az = az;
        this.a = a; this.b = b; this.c = c; this.power = power; this.ref = ref;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(key);
        buf.writeByte(type.ordinal());
        buf.writeVarInt(duration);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat((float) ax); buf.writeFloat((float) ay); buf.writeFloat((float) az);
        buf.writeInt(a); buf.writeInt(b); buf.writeInt(c);
        buf.writeFloat(power);
        buf.writeLong(ref);
    }

    public static FxStartMsg decode(FriendlyByteBuf buf) {
        long key = buf.readLong();
        FxType[] types = FxType.values();
        FxType type = types[buf.readUnsignedByte() % types.length];
        int duration = buf.readVarInt();
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        double ax = buf.readFloat(), ay = buf.readFloat(), az = buf.readFloat();
        int a = buf.readInt(), b = buf.readInt(), c = buf.readInt();
        float power = buf.readFloat();
        long ref = buf.readLong();
        return new FxStartMsg(key, type, duration, x, y, z, ax, ay, az, a, b, c, power, ref);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFx.start(this)));
        c.setPacketHandled(true);
    }
}