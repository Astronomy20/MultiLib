package net.astronomy.multilib;

import net.astronomy.multilib.api.state.MultiblockStateRegistry;
import net.astronomy.multilib.api.state.StandardMultiblockState;
import net.astronomy.multilib.client.ClientConfig;
import net.astronomy.multilib.client.network.ClientPacketHandler;
import net.astronomy.multilib.core.capability.IOPortCapabilityHandler;
import net.astronomy.multilib.core.json.MultiblockJsonSetup;
import net.astronomy.multilib.event.AutoPlacePreviewRequestHandler;
import net.astronomy.multilib.event.AutoPlaceRequestHandler;
import net.astronomy.multilib.event.MultiblockDevPacketHandler;
import net.astronomy.multilib.event.MultiblockPreferencePacketHandler;
import net.astronomy.multilib.event.OverlayRequestHandler;
import net.astronomy.multilib.network.AutoPlacePreviewDataPacket;
import net.astronomy.multilib.network.DevExportResultPacket;
import net.astronomy.multilib.network.DevListVisibilityPacket;
import net.astronomy.multilib.network.DevLoadListPacket;
import net.astronomy.multilib.network.DevLoadResultPacket;
import net.astronomy.multilib.network.DevScanResultPacket;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.astronomy.multilib.network.RequestAutoPlacePreviewPacket;
import net.astronomy.multilib.network.RequestDevAutoDetectTogglePacket;
import net.astronomy.multilib.network.RequestDevExportPacket;
import net.astronomy.multilib.network.RequestDevListToggleVisibilityPacket;
import net.astronomy.multilib.network.RequestDevLoadListPacket;
import net.astronomy.multilib.network.RequestDevLoadPacket;
import net.astronomy.multilib.network.RequestDevRenderTogglePacket;
import net.astronomy.multilib.network.RequestDevSaveFieldsPacket;
import net.astronomy.multilib.network.RequestDevScanPacket;
import net.astronomy.multilib.network.RequestOverlayPacket;
import net.astronomy.multilib.network.RequestSetPreferredDefinitionPacket;
import net.astronomy.multilib.network.PreferredDefinitionResultPacket;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
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

        modEventBus.addListener(this::loadComplete);

        NeoForge.EVENT_BUS.register(this);

        // Both files live under config/multilib/ (instead of the default config/multilib-*.toml)
        // so the mod's config is grouped in its own folder.
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, MODID + "/client.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, MODID + "/common.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("All multiblock definitions loaded");

            // Only registers a listener here - KubeJSMultiblockSetup itself does nothing until the
            // first MultiblockDefinitionsReloadedEvent (initial datapack load, and every /reload after
            // that), by which point block/item registries - including ones populated by KubeJS's own
            // scripts - are long closed. See compat/kubejs/KubeJSMultiblockSetup and
            // roadmap/phase-9-kubejs-scripting.md for the full rationale.
            if (ModList.get().isLoaded("kubejs")) {
                try {
                    Class.forName("net.astronomy.multilib.compat.kubejs.KubeJSMultiblockSetup")
                            .getMethod("init")
                            .invoke(null);
                } catch (ClassNotFoundException e) {
                    // compat/kubejs/** is excluded from the source set because kubejs_version isn't set
                    // in gradle.properties on this build - nothing to initialize.
                } catch (ReflectiveOperationException e) {
                    LOGGER.error("[MultiLib] Failed to initialize KubeJS compat", e);
                }
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    /**
     * Freezes {@link MultiblockStateRegistry} (fase 2) and, if FTB Quests is present, initializes the
     * {@code compat/ftbquests} module. {@code FMLLoadCompleteEvent} (not {@code FMLCommonSetupEvent})
     * guarantees every mod's states are registered before the freeze, and before FTB Quests' editor
     * reads {@code MultiblockStateRegistry.getAll()} for its dropdown.
     * <p>
     * FTB Quests init is invoked via reflection rather than a direct reference to
     * {@code net.astronomy.multilib.compat.ftbquests.FtbQuestsCompat}. Unlike JEI/REI/EMI/Patchouli -
     * which are auto-discovered by their host mod and never referenced from this class - FTB Quests
     * requires an explicit {@code init()} call to register its task type. If {@code ftbquests_version}
     * is unset in gradle.properties, {@code compat/ftbquests/**} is excluded from the source set
     * entirely (see build.gradle), so a normal import/reference to {@code FtbQuestsCompat} here would
     * fail to compile even for consumers who never enabled the integration. Reflection breaks that
     * compile-time link: this class compiles identically whether or not the package exists, and the
     * class is only touched at runtime, gated on {@code ModList.get().isLoaded("ftbquests")}.
     */
    private void loadComplete(FMLLoadCompleteEvent event) {
        // StandardMultiblockState's static initializer (which registers UNFORMED/IDLE/RUNNING/ERROR)
        // only runs the first time something touches that class. Nothing in MultiLib's own startup
        // referenced it before this point - the first real touch was previously a controller block
        // entity's <init> on chunk load, which happens well after this freeze() call and crashed with
        // "registration after freeze". Force the touch here, before freezing, same as any third-party
        // mod is asked to do for its own custom states.
        StandardMultiblockState.touch();
        MultiblockStateRegistry.freeze();

        if (ModList.get().isLoaded("ftbquests")) {
            try {
                Class.forName("net.astronomy.multilib.compat.ftbquests.FtbQuestsCompat")
                        .getMethod("init")
                        .invoke(null);
            } catch (ClassNotFoundException e) {
                // compat/ftbquests/** is excluded from the source set because ftbquests_version isn't
                // set in gradle.properties on this build - nothing to initialize.
            } catch (ReflectiveOperationException e) {
                LOGGER.error("[MultiLib] Failed to initialize FTB Quests compat", e);
            }
        }
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
        registrar.playToServer(
            RequestDevScanPacket.TYPE,
            RequestDevScanPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleScanRequest
        );
        registrar.playToServer(
            RequestDevExportPacket.TYPE,
            RequestDevExportPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleExportRequest
        );
        registrar.playToServer(
            RequestDevSaveFieldsPacket.TYPE,
            RequestDevSaveFieldsPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleSaveFieldsRequest
        );
        registrar.playToClient(
            DevScanResultPacket.TYPE,
            DevScanResultPacket.STREAM_CODEC,
            ClientPacketHandler::handleDevScanResult
        );
        registrar.playToClient(
            DevExportResultPacket.TYPE,
            DevExportResultPacket.STREAM_CODEC,
            ClientPacketHandler::handleDevExportResult
        );
        registrar.playToServer(
            RequestDevAutoDetectTogglePacket.TYPE,
            RequestDevAutoDetectTogglePacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleAutoDetectToggleRequest
        );
        registrar.playToServer(
            RequestDevListToggleVisibilityPacket.TYPE,
            RequestDevListToggleVisibilityPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleListToggleRequest
        );
        registrar.playToClient(
            DevListVisibilityPacket.TYPE,
            DevListVisibilityPacket.STREAM_CODEC,
            ClientPacketHandler::handleDevListVisibility
        );
        registrar.playToServer(
            RequestDevRenderTogglePacket.TYPE,
            RequestDevRenderTogglePacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleRenderToggleRequest
        );
        registrar.playToServer(
            RequestDevLoadListPacket.TYPE,
            RequestDevLoadListPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleLoadListRequest
        );
        registrar.playToClient(
            DevLoadListPacket.TYPE,
            DevLoadListPacket.STREAM_CODEC,
            ClientPacketHandler::handleDevLoadList
        );
        registrar.playToServer(
            RequestDevLoadPacket.TYPE,
            RequestDevLoadPacket.STREAM_CODEC,
            MultiblockDevPacketHandler::handleLoadRequest
        );
        registrar.playToClient(
            DevLoadResultPacket.TYPE,
            DevLoadResultPacket.STREAM_CODEC,
            ClientPacketHandler::handleDevLoadResult
        );
        registrar.playToServer(
            RequestSetPreferredDefinitionPacket.TYPE,
            RequestSetPreferredDefinitionPacket.STREAM_CODEC,
            MultiblockPreferencePacketHandler::handleSetRequest
        );
        registrar.playToClient(
            PreferredDefinitionResultPacket.TYPE,
            PreferredDefinitionResultPacket.STREAM_CODEC,
            ClientPacketHandler::handlePreferredDefinitionResult
        );
    }
}
