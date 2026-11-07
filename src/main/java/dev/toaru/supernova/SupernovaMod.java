package dev.toaru.supernova;

import com.mojang.logging.LogUtils;
import dev.toaru.supernova.net.Net;
import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import java.util.List;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Supernova: a one-skill category ("Stellar") for Re:AcademyCraft 1.5.0.
 * A massive explosion ability with expanding rings of fire and force.
 */
@Mod(SupernovaMod.MOD_ID)
public final class SupernovaMod {
    public static final String MOD_ID = "supernova";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> STELLAR_FACTOR = ITEMS.register("stellar_factor", () -> new Item(new Item.Properties().stacksTo(16)));

    public static AbilityId id(String path) { return AbilityId.parse(MOD_ID + ":" + path); }
    public static final AbilityId CATEGORY = id("stellar"), SUPERNOVA = id("supernova");

    public SupernovaMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerAbilities);
        bus.addListener(this::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) { event.enqueueWork(Net::register); }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(STELLAR_FACTOR);
    }

    private void registerAbilities(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(MOD_ID);
        // Stellar category with one level-4 skill. TOGGLE (not INSTANT): only TOGGLE/HELD skills get tick() and stop(),
        // which the formation and the delayed rings need. An INSTANT skill that returns STARTED is treated as finished at once.
        abilities.category(new CategoryDefinition(CATEGORY, id("stellar_factor"), List.of(0, 0, 0, 1, 0),
                new Presentation(MOD_ID + ".ability.stellar", null, null)));
        abilities.skill(new SkillDefinition(SUPERNOVA, CATEGORY, 4, List.of(), ActivationKind.TOGGLE,
                new Presentation(MOD_ID + ".skill.supernova", MOD_ID + ".skill.supernova.desc", null),
                new TreePosition(120, 60, null)));
        event.behaviors(MOD_ID).behavior(SUPERNOVA, new Supernova());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID)
    public static final class Cleanup {
        private Cleanup() { }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { Supernova.forget(event.getEntity().getUUID()); }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) { Supernova.clearAll(); }
    }
}