package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers the Multiblock Dev Block/Item/BlockEntityType/MenuType - but only when
 * {@link CommonConfig#DEV_MODE} is enabled (checked once, at {@link RegisterEvent} time, per registry).
 * <p>
 * Unlike every other gated dev-facing feature in this mod (which reads {@code DEV_MODE} at the moment of
 * use, always registered - see {@code WrenchFeedbackHandler}), this tool must not exist in the registry
 * at all when disabled (roadmap Design 9 / requisito 10): the block/item are a dev-only authoring aid,
 * not something a production build of a host mod should ship any trace of.
 * <p>
 * <b>Correction, confirmed by log evidence, not just theory:</b> the original assumption here - that
 * {@code ModConfigSpec}-backed COMMON config values are always safely readable by the time any
 * {@link RegisterEvent} fires - is false in this project's actual runtime behavior, not just a rare
 * race. Every single {@code onRegister} invocation logged
 * {@code "CommonConfig.DEV_MODE wasn't loaded yet when checked"} (89 occurrences in one run, one per
 * registry {@link RegisterEvent} dispatch) - NeoForge's own COMMON config load reliably lags behind
 * {@code RegisterEvent} dispatch here, every time, not intermittently. A plain try/catch that falls
 * back to {@code false} (an earlier version of this fix) avoided the crash this caused (see below) but
 * made the dev block impossible to ever register, even with {@code devMode=true} in the config file -
 * so {@link #isDevModeEnabled()} now falls back to reading the raw TOML file directly
 * ({@link #readDevModeFromRawConfigFile()}) whenever {@code ModConfigSpec} isn't ready yet, instead of
 * just defaulting to "disabled".
 * <p>
 * Background on why the try/catch exists at all: verified by decompiling
 * {@code ModConfigSpec$ConfigValue.getRaw()}, it calls
 * {@code Preconditions.checkState(loadedConfig != null, "Cannot get config value before config is loaded.")}
 * and throws {@link IllegalStateException} if the backing config file hasn't been loaded yet. NeoForge
 * dispatches {@link RegisterEvent} across registries via parallel processing
 * ({@code CommonModLoader#begin}); an uncaught exception from a {@code RegisterEvent} listener
 * previously corrupted the shared registry-loading pipeline enough to produce an unrelated-looking
 * crash much later (NeoForge's vanilla-attribute registry bake, "unbound value: neoforge:swim_speed"),
 * with no mention of MultiLib anywhere in the report.
 * <p>
 * Two risks accepted deliberately (not "fixed") per roadmap Design 9:
 * <ol>
 *   <li>Orphaned block in a saved world if a world is saved with the dev block placed and devMode is
 *       later turned off - that position becomes an unknown/missing block on next load. The tool is
 *       meant to be used transiently (place, export, break), not left down long-term.</li>
 *   <li>Registry mismatch in dedicated multiplayer if client and server have different {@code devMode}
 *       values (CommonConfig isn't synced between the two) - NeoForge typically rejects/degrades the
 *       connection. All sides of a shared dedicated server must use the same {@code devMode} value.</li>
 * </ol>
 * Do not "fix" either by silently hiding the block instead - document any future workaround here too.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class MultiblockDevRegistry {

    public static MultiblockDevBlock DEV_BLOCK;
    public static BlockItem DEV_ITEM;
    public static BlockEntityType<MultiblockDevBlockEntity> DEV_BLOCK_ENTITY_TYPE;
    public static MenuType<MultiblockDevMenu> DEV_MENU_TYPE;
    public static MultiblockDevWrenchItem DEV_WRENCH_ITEM;

    private MultiblockDevRegistry() {}

    /**
     * Safe substitute for {@code CommonConfig.DEV_MODE.get()}: if {@code ModConfigSpec} isn't loaded
     * yet (see the class javadoc - confirmed to happen on every {@link RegisterEvent} in this project,
     * not just rarely), falls back to reading the raw {@code devMode} line straight out of
     * {@code config/multilib/common.toml} instead of just assuming "disabled".
     */
    private static boolean isDevModeEnabled() {
        try {
            return CommonConfig.DEV_MODE.get();
        } catch (IllegalStateException e) {
            return readDevModeFromRawConfigFile();
        }
    }

    /**
     * One-off, {@code RegisterEvent}-safe fallback: reads {@code devMode} directly out of
     * {@code <configDir>/multilib/common.toml} (the exact path {@code MultiLib#registerConfig} uses -
     * see {@code MultiLib.java}), bypassing {@code ModConfigSpec} entirely. Deliberately minimal (a
     * single-line scan, not a TOML parser) since this only ever needs one boolean out of one known key;
     * a missing file (fresh install, config not generated yet) is treated as {@code false}, matching
     * {@link CommonConfig#DEV_MODE}'s own default.
     */
    private static boolean readDevModeFromRawConfigFile() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(MultiLib.MODID).resolve("common.toml");
        if (!Files.isRegularFile(file)) return false;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.startsWith("devMode")) continue;
                int eq = trimmed.indexOf('=');
                return eq >= 0 && trimmed.substring(eq + 1).trim().equalsIgnoreCase("true");
            }
        } catch (IOException e) {
            MultiLib.LOGGER.warn("[MultiLib] Failed to read the raw devMode fallback from {}", file, e);
        }
        return false;
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!isDevModeEnabled()) return;

        event.register(Registries.BLOCK, helper -> {
            DEV_BLOCK = new MultiblockDevBlock(BlockBehaviour.Properties.of().strength(0.5F).noOcclusion());
            helper.register(id("multiblock_dev_block"), DEV_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            // getDescriptionId() overridden to reuse the block's own translation key - a plain BlockItem
            // looks up its own separate "item.multilib.multiblock_dev_block" key by default, which meant
            // one identical string had to be duplicated across both a "block." and an "item." lang entry
            // for no reason (the item's displayed name is always just the block's name here).
            DEV_ITEM = new BlockItem(DEV_BLOCK, new Item.Properties()) {
                @Override
                public String getDescriptionId() {
                    return getBlock().getDescriptionId();
                }
            };
            helper.register(id("multiblock_dev_block"), DEV_ITEM);
            DEV_WRENCH_ITEM = new MultiblockDevWrenchItem(new Item.Properties().stacksTo(1));
            helper.register(id("multiblock_dev_wrench"), DEV_WRENCH_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            DEV_BLOCK_ENTITY_TYPE = BlockEntityType.Builder.of(MultiblockDevBlockEntity::create, DEV_BLOCK).build(null);
            helper.register(id("multiblock_dev_block"), DEV_BLOCK_ENTITY_TYPE);
        });
        event.register(Registries.MENU, helper -> {
            DEV_MENU_TYPE = IMenuTypeExtension.create(
                    (containerId, inventory, buf) -> new MultiblockDevMenu(containerId, inventory, buf.readBlockPos()));
            helper.register(id("multiblock_dev_block"), DEV_MENU_TYPE);
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!isDevModeEnabled()) return;
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            if (DEV_ITEM != null) event.accept(DEV_ITEM);
            if (DEV_WRENCH_ITEM != null) event.accept(DEV_WRENCH_ITEM);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }

    /** Client-side registration of the GUI Screen - kept in a nested class purely to scope the {@link Dist#CLIENT} annotation. */
    @EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
    public static final class ClientSetup {
        private ClientSetup() {}

        @SubscribeEvent
        public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            if (!isDevModeEnabled() || DEV_MENU_TYPE == null) return;
            event.register(DEV_MENU_TYPE, net.astronomy.multilib.client.devtool.MultiblockDevScreen::new);
        }
    }
}
