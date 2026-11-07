package dev.toaru.supernova.client;

import dev.toaru.supernova.SupernovaMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only upkeep: drops finished visual effects, and clears them all when leaving a world or server. */
@Mod.EventBusSubscriber(modid = SupernovaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() { }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientFx.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { ClientFx.ACTIVE.clear(); }
}
