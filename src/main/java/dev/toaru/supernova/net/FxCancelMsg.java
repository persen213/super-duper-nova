package dev.toaru.supernova.net;

import dev.toaru.supernova.client.ClientFx;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server to client: end an effect early. */
public final class FxCancelMsg {
    public final long key;

    public FxCancelMsg(long key) { this.key = key; }

    public void encode(FriendlyByteBuf buf) { buf.writeLong(key); }

    public static FxCancelMsg decode(FriendlyByteBuf buf) { return new FxCancelMsg(buf.readLong()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFx.cancel(this.key)));
        c.setPacketHandled(true);
    }
}