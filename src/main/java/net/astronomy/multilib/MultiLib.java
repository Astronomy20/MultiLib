package net.astronomy.multilib;

import net.astronomy.multilib.client.ClientConfig;
import net.astronomy.multilib.client.network.ClientPacketHandler;
import net.astronomy.multilib.core.capability.IOPortCapabilityHandler;
import net.astronomy.multilib.core.json.MultiblockJsonSetup;
import net.astronomy.multilib.event.AutoPlacePreviewRequestHandler;
import net.astronomy.multilib.event.AutoPlaceRequestHandler;
import net.astronomy.multilib.event.OverlayRequestHandler;
import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.astronomy.multilib.network.RequestAutoPlacePreviewPacket;
import net.astronomy.multilib.network.RequestOverlayPacket;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(MultiLib.MODID)
public class MultiLib {
    public static final String MODID = "multilib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MultiLib(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        modEventBus.addListener(MultiLib::registerPayloads);

        modEventBus.addListener(IOPortCapabilityHandler::register);

        MultiblockJsonSetup.registerBuiltInProviders();
        MultiblockJsonSetup.registerReloadListener(NeoForge.EVENT_BUS);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("All multiblock definitions loaded");
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            RequestOverlayPacket.TYPE,
            RequestOverlayPacket.STREAM_CODEC,
            OverlayRequestHandler::handleRequest
        );
        registrar.playToServer(
            RequestAutoPlacePacket.TYPE,
            RequestAutoPlacePacket.STREAM_CODEC,
            AutoPlaceRequestHandler::handleRequest
        );
        registrar.playToServer(
            RequestAutoPlacePreviewPacket.TYPE,
            RequestAutoPlacePreviewPacket.STREAM_CODEC,
            AutoPlacePreviewRequestHandler::handleRequest
        );
        // playToClient handler is only invoked on the client side by NeoForge
        registrar.playToClient(
            OverlayDataPacket.TYPE,
            OverlayDataPacket.STREAM_CODEC,
            ClientPacketHandler::handleOverlayData
        );
        registrar.playToClient(
            AutoPlacePreviewDataPacket.TYPE,
            AutoPlacePreviewDataPacket.STREAM_CODEC,
            ClientPacketHandler::handleAutoPlacePreviewData
        );
    }
}
