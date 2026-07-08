package net.astronomy.multilib.event;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity;
import net.astronomy.multilib.core.devtool.MultiblockDevExporter;
import net.astronomy.multilib.core.devtool.MultiblockDevListSessionRegistry;
import net.astronomy.multilib.core.devtool.MultiblockDevOutputPaths;
import net.astronomy.multilib.core.devtool.MultiblockDevTagSessionRegistry;
import net.astronomy.multilib.core.devtool.MultiblockScanResult;
import net.astronomy.multilib.core.devtool.MultiblockScanner;
import net.astronomy.multilib.network.DevExportResultPacket;
import net.astronomy.multilib.network.DevListVisibilityPacket;
import net.astronomy.multilib.network.DevScanResultPacket;
import net.astronomy.multilib.network.RequestDevAutoDetectTogglePacket;
import net.astronomy.multilib.network.RequestDevExportPacket;
import net.astronomy.multilib.network.RequestDevListToggleVisibilityPacket;
import net.astronomy.multilib.network.RequestDevSaveFieldsPacket;
import net.astronomy.multilib.network.RequestDevScanPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Server-side handling for both client -> server packets of the Multiblock Dev Block tool: scan
 * requests and export requests. Same static/{@code ctx.enqueueWork} style as {@code OverlayRequestHandler}.
 */
public final class MultiblockDevPacketHandler {

    private MultiblockDevPacketHandler() {}

    public static void handleScanRequest(RequestDevScanPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos devBlockPos = packet.devBlockPos();

            if (!(level.getBlockEntity(devBlockPos) instanceof MultiblockDevBlockEntity be)) {
                PacketDistributor.sendToPlayer(player, new DevScanResultPacket(
                        devBlockPos, false, "The Multiblock Dev Block no longer exists at this position.",
                        emptyScan()));
                return;
            }

            be.setOffset(packet.offset());
            be.setSize(new net.minecraft.core.Vec3i(
                    Math.max(packet.sizeX(), 1), Math.max(packet.sizeY(), 1), Math.max(packet.sizeZ(), 1)));
            be.setPath(packet.path());
            be.setDisplayName(packet.displayName());

            MultiblockScanner.ScanOutcome outcome = be.detectAndStore();
            if (outcome.result().isEmpty()) {
                PacketDistributor.sendToPlayer(player, buildScanPacket(devBlockPos, outcome));
                return;
            }

            BoundingBox box = be.getAbsoluteBoundingBox();
            MultiblockDevTagSessionRegistry.set(player.getUUID(), new MultiblockDevTagSessionRegistry.Session(
                    devBlockPos,
                    new BlockPos(box.minX(), box.minY(), box.minZ()),
                    new BlockPos(box.maxX(), box.maxY(), box.maxZ())));

            // Use the block entity's own resolved copy (tag applied), not outcome.result() - detectAndStore()
            // stores the tag-resolved version in getLastScan(), while the ScanOutcome carries the raw scan.
            PacketDistributor.sendToPlayer(player, new DevScanResultPacket(
                    devBlockPos, true, "", be.getLastScan().orElseGet(MultiblockDevPacketHandler::emptyScan)));
        });
    }

    /**
     * Applies the GUI field set to the block entity - sent on every keystroke in the offset/size fields
     * (not just on GUI close) so auto-detect always scans against whatever's currently typed, not
     * whatever was last explicitly saved. Path/displayName never trigger a rescan themselves
     * (they don't affect what area gets read), but if offset or size actually changed and auto-detect is
     * on, the stale scan is thrown out and immediately redone against the area as it is right now -
     * otherwise the developer would be looking at a scan of an area that no longer matches what's typed
     * until the next periodic {@code MultiblockTickHandler} tick happened to catch up.
     */
    public static void handleSaveFieldsRequest(RequestDevSaveFieldsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos devBlockPos = packet.devBlockPos();

            if (!(level.getBlockEntity(devBlockPos) instanceof MultiblockDevBlockEntity be)) return;

            net.minecraft.core.Vec3i newSize = new net.minecraft.core.Vec3i(
                    Math.max(packet.sizeX(), 1), Math.max(packet.sizeY(), 1), Math.max(packet.sizeZ(), 1));
            boolean areaChanged = !be.getOffset().equals(packet.offset()) || !be.getSize().equals(newSize);

            be.setOffset(packet.offset());
            be.setSize(newSize);
            be.setPath(packet.path());
            be.setDisplayName(packet.displayName());
            be.setVariantName(packet.variantName());

            // Offset/size may have just changed - refresh the tagging session so it still matches the
            // area the developer is actually looking at (tagging works without ever running Detect).
            BoundingBox box = be.getAbsoluteBoundingBox();
            MultiblockDevTagSessionRegistry.set(player.getUUID(), new MultiblockDevTagSessionRegistry.Session(
                    devBlockPos,
                    new BlockPos(box.minX(), box.minY(), box.minZ()),
                    new BlockPos(box.maxX(), box.maxY(), box.maxZ())));

            if (areaChanged && be.isAutoDetectOn()) {
                MultiblockScanner.ScanOutcome outcome = be.detectAndStore();
                PacketDistributor.sendToPlayer(player, buildScanPacket(devBlockPos, outcome));
                // In case a *different* player is the one currently watching this block's HUD list -
                // the edit above always comes from whoever has the GUI open, not necessarily them.
                sendScanIfWatching(player, level.dimension(), devBlockPos, outcome);
            }
        });
    }

    /**
     * Flips {@code MultiblockDevBlockEntity#isAutoDetectOn()}. Turning it on immediately runs one scan
     * (rather than waiting for the next {@code MultiblockTickHandler} interval) so the toggle feels
     * responsive, and pushes it straight to this player if they're the one currently watching this exact
     * block's HUD list.
     */
    public static void handleAutoDetectToggleRequest(RequestDevAutoDetectTogglePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(level.getBlockEntity(packet.devBlockPos()) instanceof MultiblockDevBlockEntity be)) return;

            be.setAutoDetectOn(!be.isAutoDetectOn());
            if (be.isAutoDetectOn()) {
                sendScanIfWatching(player, level.dimension(), packet.devBlockPos(), be.detectAndStore());
            }
        });
    }

    /**
     * Flips {@code MultiblockDevBlockEntity#isRenderOn()} - the BE's own sync path
     * ({@code propagateRenderPreviewIfApplicable}) takes care of actually showing/hiding the client-side
     * area preview from there, using whatever offset/size is already persisted on the block.
     */
    public static void handleRenderToggleRequest(net.astronomy.multilib.network.RequestDevRenderTogglePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(level.getBlockEntity(packet.devBlockPos()) instanceof MultiblockDevBlockEntity be)) return;
            be.setRenderOn(!be.isRenderOn());
        });
    }

    /**
     * Toggles whether this player currently has {@code devBlockPos}'s HUD list shown (see
     * {@link MultiblockDevListSessionRegistry}), and tells the client either to hide the HUD outright or
     * to show it with whatever scan is already on record.
     */
    public static void handleListToggleRequest(RequestDevListToggleVisibilityPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();

            Optional<MultiblockDevListSessionRegistry.Session> newSession = MultiblockDevListSessionRegistry.toggle(
                    level.getServer(), player.getUUID(), level.dimension(), packet.devBlockPos());
            if (newSession.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new DevListVisibilityPacket(false, packet.devBlockPos()));
                return;
            }

            PacketDistributor.sendToPlayer(player, new DevListVisibilityPacket(true, packet.devBlockPos()));
            if (level.getBlockEntity(packet.devBlockPos()) instanceof MultiblockDevBlockEntity be) {
                sendScanIfWatching(player, packet.devBlockPos(), be);
            }
        });
    }

    /**
     * Sends {@code be}'s currently cached scan to {@code player}, only if they're the one currently
     * watching {@code devBlockPos}'s HUD list. Nothing was just (re)scanned here - if nothing's cached
     * yet (Detect never ran), the client's own default "no scan yet" state is already correct, so there's
     * nothing to send.
     */
    private static void sendScanIfWatching(ServerPlayer player, BlockPos devBlockPos, MultiblockDevBlockEntity be) {
        MultiblockDevListSessionRegistry.get(player.getUUID())
                .filter(session -> session.devBlockPos().equals(devBlockPos))
                .ifPresent(session -> be.getLastScan().ifPresent(scan ->
                        PacketDistributor.sendToPlayer(player, new DevScanResultPacket(devBlockPos, true, "", scan))));
    }

    /**
     * Sends the outcome of a scan that was just (re)run to {@code player}, only if they're the one
     * currently watching {@code devBlockPos}'s HUD list - unlike the {@code MultiblockDevBlockEntity}
     * overload, this always sends something, success or failure. Skipping the failure case (as the
     * BE-only overload above correctly does when nothing's cached yet) was the actual bug behind the HUD
     * getting stuck showing stale content once the area legitimately became empty (e.g. the last block in
     * it got broken): {@code getLastScan()} turns empty on failure too, so "only send if present" silently
     * dropped every failed re-scan instead of telling the client to clear what it was showing.
     * <p>
     * Package-private (not {@code private}): also called from {@code MultiblockTickHandler}'s periodic
     * auto-detect sweep, in the same package, for exactly the same reason.
     */
    static void sendScanIfWatching(ServerPlayer player, ResourceKey<Level> dimension,
                                    BlockPos devBlockPos, MultiblockScanner.ScanOutcome outcome) {
        MultiblockDevListSessionRegistry.get(player.getUUID())
                .filter(session -> session.dimension().equals(dimension) && session.devBlockPos().equals(devBlockPos))
                .ifPresent(session -> PacketDistributor.sendToPlayer(player, buildScanPacket(devBlockPos, outcome)));
    }

    /** Builds the packet reporting a just-run scan's outcome, success or failure, with the appropriate specific message for each {@link MultiblockScanner.FailureReason}. */
    static DevScanResultPacket buildScanPacket(BlockPos devBlockPos, MultiblockScanner.ScanOutcome outcome) {
        if (outcome.result().isPresent()) {
            return new DevScanResultPacket(devBlockPos, true, "", outcome.result().get());
        }
        String message = switch (outcome.failureReason()) {
            case EMPTY_AREA -> MultiblockScanner.EMPTY_AREA_MESSAGE;
            case TOO_MANY_BLOCK_TYPES -> "Too many distinct block types in this area (max "
                    + MultiblockScanner.maxSymbols() + ").";
            case INCOMPLETE_MULTIPART_BLOCK -> "A multi-part block (door/bed/tall plant/etc.) is only "
                    + "partially inside the area - resize the area to include it fully.";
            case NONE -> "Scan failed.";
        };
        return new DevScanResultPacket(devBlockPos, false, message, emptyScan());
    }

    public static void handleExportRequest(RequestDevExportPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos devBlockPos = packet.devBlockPos();
            RequestDevExportPacket.Format format = packet.format();

            if (!(level.getBlockEntity(devBlockPos) instanceof MultiblockDevBlockEntity be)) {
                failExport(player, format, "The Multiblock Dev Block no longer exists at this position.");
                return;
            }

            Optional<MultiblockScanResult> scanOpt = be.getLastScan();
            if (scanOpt.isEmpty()) {
                failExport(player, format, "No scan yet - click Detect before exporting.");
                return;
            }
            MultiblockScanResult scan = scanOpt.get();

            // The GUI's own "Path" field stays the actual ResourceLocation path - matches standard
            // Minecraft convention (namespace:path, e.g. "minecraft:diamond_sword"): namespace is the
            // constant, mod-like identifier (CommonConfig.DEVTOOL_NAMESPACE, fixed and shared by every
            // export regardless of format), path is what varies per multiblock (e.g. config "example" +
            // typed "test" -> id "example:test", translation key "multiblock.example.test"). Display Name
            // is purely cosmetic - it's written only as the lang entry's *value* (see writeAndRespond),
            // never contributing to the id or the key itself.
            String path = be.getPath();
            if (path.isBlank()) {
                failExport(player, format, "Path is required before exporting.");
                return;
            }
            String namespace = CommonConfig.DEVTOOL_NAMESPACE.get();
            String displayName = be.getDisplayName();
            String variantName = be.getVariantName();

            MinecraftServer server = level.getServer();
            Path checkFile = switch (format) {
                case JAVA -> MultiblockDevOutputPaths.javaOutputFile(path);
                case KUBEJS -> MultiblockDevOutputPaths.kubeJsOutputFile(path);
                case JSON -> MultiblockDevOutputPaths.jsonOutputFile(server, namespace, path).path();
            };
            if (needsConfirmation(player, format, packet.force(), checkFile, namespace, path)) return;

            switch (format) {
                case JAVA -> exportJava(player, namespace, path, displayName, variantName, scan);
                case JSON -> exportJson(player, server, namespace, path, displayName, variantName, scan);
                case KUBEJS -> exportKubeJs(player, namespace, path, displayName, variantName, scan);
            }
        });
    }

    /**
     * Reports an export attempt that never got as far as resolving a file path (dev block gone, no scan,
     * missing fields). Previously these responses carried an empty {@code resolvedPath}, which the GUI
     * used as its sole signal to render anything at all - so the error text was silently dropped and
     * clicking Export appeared to do nothing.
     */
    private static void failExport(ServerPlayer player, RequestDevExportPacket.Format format, String message) {
        PacketDistributor.sendToPlayer(player, new DevExportResultPacket("", "", format, false, false, false, message));
    }

    private static void exportJava(ServerPlayer player, String namespace, String path,
                                    String displayName, String variantName, MultiblockScanResult scan) {
        Path outFile = MultiblockDevOutputPaths.javaOutputFile(path);
        String text = MultiblockDevExporter.toJavaSource(namespace, path, scan, variantName);
        // Its own separate lang tree, under the Java output dir itself - deliberately NOT the shared
        // devtoolResourcePackRootDir() JSON exports use, so re-exporting the same namespace:path as both
        // formats doesn't clobber whichever one wrote its lang entry last. The Java export is only ever a
        // one-off scaffold a developer copies into their own mod project by hand (formationMode/
        // validator/callbacks/etc. still need filling in) - it was never meant to be a live, in-game-tested
        // resourcepack the way the JSON export's world datapack is, so this doesn't need to be a real
        // resourcepack location either; the standalone copy-paste snippet (see writeLangSnippet) is what
        // actually matters for carrying this into the real project.
        Path langFile = MultiblockDevOutputPaths.langFile(MultiblockDevOutputPaths.javaRootDir(), namespace);
        writeAndRespond(player, RequestDevExportPacket.Format.JAVA, text, outFile, false,
                langFile, langKey(namespace, path), MultiblockDevExporter.resolveDisplayText(path, displayName), null);
    }

    private static void exportKubeJs(ServerPlayer player, String namespace, String path,
                                      String displayName, String variantName, MultiblockScanResult scan) {
        Path outFile = MultiblockDevOutputPaths.kubeJsOutputFile(path);
        String text = MultiblockDevExporter.toKubeJsScript(namespace, path, scan, variantName);
        // KubeJS reads lang/assets from its own single kubejs/assets tree, NOT from
        // server_scripts/<devtoolNamespace> (where the generated script itself lives) - see
        // kubeJsAssetsRootDir's own javadoc.
        Path langFile = MultiblockDevOutputPaths.langFile(MultiblockDevOutputPaths.kubeJsAssetsRootDir(), namespace);
        writeAndRespond(player, RequestDevExportPacket.Format.KUBEJS, text, outFile, false,
                langFile, langKey(namespace, path), MultiblockDevExporter.resolveDisplayText(path, displayName), null);
    }

    /** {@code multiblock.<namespace>.<path>} - the key {@code MultiblockBuilder.build()} always auto-derives from the definition's id. */
    private static String langKey(String namespace, String path) {
        return "multiblock." + namespace + "." + path;
    }

    /**
     * The output file for every format is named after the path alone (see
     * {@link MultiblockDevOutputPaths#javaOutputFile}/{@code #kubeJsOutputFile}/{@code #jsonOutputFile}),
     * so two different multiblocks sharing a path (or the same path re-exported after
     * {@link CommonConfig#DEVTOOL_NAMESPACE} itself changed) would otherwise silently overwrite each
     * other's file. Unless {@code force} is set (the developer already confirmed the overwrite - see
     * {@link RequestDevExportPacket#force}), this asks for confirmation instead of writing whenever
     * {@code checkFile} already exists and was last written for a *different* {@code namespace:path} id -
     * re-exporting the same multiblock to update it (same id, including after loading it via the GUI's
     * Load tab) is always allowed without confirmation.
     */
    private static boolean needsConfirmation(ServerPlayer player, RequestDevExportPacket.Format format, boolean force,
                                              Path checkFile, String namespace, String path) {
        if (force) return false;
        String currentId = namespace + ":" + path;
        Optional<String> existingId = MultiblockDevOutputPaths.readExistingExportId(checkFile);
        if (existingId.isPresent() && !existingId.get().equals(currentId)) {
            PacketDistributor.sendToPlayer(player, new DevExportResultPacket("", "", format, false, false, true,
                    "Path '" + path + "' is already used by a different multiblock ("
                            + existingId.get() + "). Export again to overwrite it."));
            return true;
        }
        return false;
    }

    private static void exportJson(ServerPlayer player, MinecraftServer server, String namespace, String path,
                                    String displayName, String variantName, MultiblockScanResult scan) {
        MultiblockDevOutputPaths.JsonOutputResult resolved = MultiblockDevOutputPaths.jsonOutputFile(server, namespace, path);
        String text = MultiblockDevExporter.toJsonDefinition(namespace, path, scan, variantName);

        if (!resolved.isDevSource()) {
            try {
                MultiblockDevOutputPaths.ensureWorldDatapackScaffold(server);
            } catch (IOException e) {
                MultiLib.LOGGER.error("[MultiLib] Dev block: failed to set up the world datapack scaffold for JSON export", e);
                // Still attempt the write below - ensureParentDirs (inside writeAndRespond) may still
                // succeed even if pack.mcmeta creation failed, and the clipboard copy must happen
                // regardless (see writeAndRespond).
            }
        }
        // NOT written under jsonRootDir()'s own assets/ subfolder - that's inside the world's *datapack*,
        // and a datapack's assets/ folder is never read by Minecraft's client at all (only data/ is,
        // server-side), so a lang entry written there would silently never show up in-game either. Uses
        // the same dedicated dev resourcepack Java exports do instead.
        try {
            MultiblockDevOutputPaths.ensureDevtoolResourcePackScaffold();
        } catch (IOException e) {
            MultiLib.LOGGER.error("[MultiLib] Dev block: failed to set up the dev resourcepack scaffold for JSON export", e);
        }
        Path langFile = MultiblockDevOutputPaths.langFile(MultiblockDevOutputPaths.devtoolResourcePackRootDir(), namespace);
        // Writing the file alone isn't enough to make it load - see enableAndReloadWorldDatapack's own
        // javadoc. Only needed on the world-datapack branch: a DEVTOOL_JSON_OUTPUT_DIR override points
        // outside the world save entirely, so there's no world datapack list to enable it in.
        Runnable onWriteSucceeded = resolved.isDevSource() ? null
                : () -> MultiblockDevOutputPaths.enableAndReloadWorldDatapack(server);
        writeAndRespond(player, RequestDevExportPacket.Format.JSON, text, resolved.path(), resolved.isDevSource(),
                langFile, langKey(namespace, path), MultiblockDevExporter.resolveDisplayText(path, displayName), onWriteSucceeded);
    }

    /**
     * Writes {@code text} to {@code outFile}, always reporting {@code text} back to the client
     * regardless of whether the write succeeded - a local I/O failure must never cost the developer the
     * generated text itself (roadmap: "isolamento del fallimento di scrittura file dal copy-to-clipboard").
     * Also merges {@code langKey -> langValue} into {@code langFile} (see
     * {@link MultiblockDevOutputPaths#mergeLangEntry}) and writes a standalone, single-entry copy of the
     * same entry right next to {@code outFile} (see {@link MultiblockDevOutputPaths#writeLangSnippet}) -
     * easy to copy-paste into a real mod project without digging through the shared dev-tool lang file -
     * but only if the main write above actually succeeded, and never lets a failure in either turn a
     * successful export into a reported failure - both are a convenience on top of the real export, not a
     * requirement for it. Same for {@code onWriteSucceeded} (nullable) - fired only after a successful
     * write, used by the JSON world-datapack branch to enable/reload the datapack so the export actually
     * takes effect.
     */
    private static void writeAndRespond(ServerPlayer player, RequestDevExportPacket.Format format, String text, Path outFile, boolean isDevSource,
                                         Path langFile, String langKey, String langValue, Runnable onWriteSucceeded) {
        boolean succeeded;
        String errorMessage = "";
        try {
            MultiblockDevOutputPaths.ensureParentDirs(outFile);
            Files.writeString(outFile, text, StandardCharsets.UTF_8);
            succeeded = true;
        } catch (IOException e) {
            MultiLib.LOGGER.error("[MultiLib] Dev block: failed to write export to {}", outFile, e);
            succeeded = false;
            errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        if (succeeded) {
            try {
                MultiblockDevOutputPaths.mergeLangEntry(langFile, langKey, langValue);
            } catch (IOException e) {
                MultiLib.LOGGER.error("[MultiLib] Dev block: failed to write lang entry to {}", langFile, e);
            }
            // The standalone single-entry lang "template" only exists to be copy-pasted into a real mod
            // project alongside the Java scaffold. JSON/KubeJS exports already write a *functional* lang
            // entry (dev resourcepack / kubejs assets tree, via mergeLangEntry above) that works in-game
            // as-is, so a template file next to those exports is just noise.
            if (format == RequestDevExportPacket.Format.JAVA) {
                try {
                    MultiblockDevOutputPaths.writeLangSnippet(outFile, langKey, langValue);
                } catch (IOException e) {
                    MultiLib.LOGGER.error("[MultiLib] Dev block: failed to write lang snippet next to {}", outFile, e);
                }
            }
            if (onWriteSucceeded != null) {
                onWriteSucceeded.run();
            }
        }
        // Reported only through the GUI's own status line (see MultiblockDevScreen#renderExportStatus) -
        // no chat message. A relative (game-dir-rooted) path instead of the full absolute one, so the
        // status line reads as a short "Saved: ..." confirmation instead of an unwieldy full filesystem
        // path.
        PacketDistributor.sendToPlayer(player, new DevExportResultPacket(
                text, relativizeToGameDir(outFile), format, isDevSource, succeeded, false, errorMessage));
    }

    /** Shortens the absolute path in the chat feedback to just what's under the game dir (falls back to the absolute path for anything outside it, e.g. a config-overridden absolute output dir). */
    private static String relativizeToGameDir(Path outFile) {
        Path gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path absolute = outFile.toAbsolutePath().normalize();
        if (!absolute.startsWith(gameDir)) {
            return absolute.toString();
        }
        return gameDir.relativize(absolute).toString();
    }

    private static MultiblockScanResult emptyScan() {
        return new MultiblockScanResult(java.util.List.of(), new LinkedHashMap<>(), null, null);
    }

    /** Answers the GUI's Load tab with every export currently found, across all three formats (see {@link net.astronomy.multilib.core.devtool.MultiblockDevExportLoader#list}). */
    public static void handleLoadListRequest(net.astronomy.multilib.network.RequestDevLoadListPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var entries = net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.list(player.serverLevel().getServer());
            PacketDistributor.sendToPlayer(player, new net.astronomy.multilib.network.DevLoadListPacket(packet.devBlockPos(), entries));
        });
    }

    /**
     * Loads one specific export picked in the Load tab into the dev-block (path/display-name and its
     * full scan geometry), so it shows up in the GUI exactly as if freshly scanned - see
     * {@link MultiblockDevBlockEntity#loadExisting}. From this point, re-exporting the same
     * {@code namespace:path} is allowed (the collision check in {@link #needsConfirmation} only ever
     * refuses a *different* id). Also resizes the dev-block's area to the loaded pattern's own
     * dimensions, clears and re-stamps the pattern's blocks into the world at that area, tags the
     * core/activation block for the glow renderer (see {@link MultiblockDevBlockEntity#tagFromScan}),
     * and turns the area-preview ("Render") on - so the developer sees the actual structure appear
     * around the dev block, ready to tweak by hand and re-Detect, instead of only seeing it described
     * in the GUI's text summary.
     */
    public static void handleLoadRequest(net.astronomy.multilib.network.RequestDevLoadPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos devBlockPos = packet.devBlockPos();

            if (!(level.getBlockEntity(devBlockPos) instanceof MultiblockDevBlockEntity be)) {
                sendLoadFailure(player, devBlockPos, "The Multiblock Dev Block no longer exists at this position.");
                return;
            }

            // packet.namespace()/packet.path() together locate the multiblock - either currently
            // *registered* (any mod, any source - see MultiblockDevExportLoader#loadFromRegistry, tried
            // first) or one of this dev tool's own file exports (only ever under the fixed
            // CommonConfig.DEVTOOL_NAMESPACE - see MultiblockDevExportLoader#load's own comment).
            var loaded = net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.load(
                    level.getServer(), packet.format(), packet.namespace(), packet.path(), packet.variantName());
            if (loaded.isEmpty()) {
                sendLoadFailure(player, devBlockPos, "Could not read '" + packet.namespace() + ":" + packet.path()
                        + "' (" + packet.format() + ") - it may have been deleted or unregistered.");
                return;
            }

            var lm = loaded.get();
            // lm.path() populates the GUI's "Path" field - lm.namespace() itself is discarded here: the
            // GUI has no field for it (always CommonConfig.DEVTOOL_NAMESPACE on export, regardless of
            // what namespace the loaded multiblock actually came from - see
            // MultiblockDevExporter/handleExportRequest for where namespace/path get assigned on export).
            be.loadExisting(lm.path(), lm.displayName(), lm.variantName(), lm.scan());
            be.placePatternInWorld(lm.scan());
            be.tagFromScan(lm.scan());
            be.setRenderOn(true);
            // Load drops a live structure into the world to be tweaked by hand - switching auto-detect
            // on here means those hand-edits (and breaks - see MultiblockDevBreakHandler) keep the scan,
            // tag glow and HUD list current on their own, instead of requiring a manual Detect after
            // every change. Same effect as pressing the GUI's Detect toggle right after loading.
            be.setAutoDetectOn(true);
            PacketDistributor.sendToPlayer(player, new net.astronomy.multilib.network.DevLoadResultPacket(
                    devBlockPos, true, "", lm.path(), lm.displayName(), lm.variantName(), lm.scan()));

            // Re-applied one tick later: the client only computes the tag glow's outlined positions from
            // its own (client-side) world state at the moment it receives this sync (see
            // MultiblockDevBlockEntity#propagateTagToClientIfApplicable), and that packet can be
            // processed before the many individual block-placement updates placePatternInWorld just
            // triggered have actually reached and been applied on that same client - which read as "no
            // core/activation shown" even though the tag itself was set correctly. Scheduling a second,
            // identical sync for the next tick (by which point the client's world reflects the placed
            // blocks) costs one redundant packet but removes the race instead of guessing at packet
            // ordering.
            level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
                if (level.getBlockEntity(devBlockPos) == be && !be.isRemoved()) {
                    be.tagFromScan(lm.scan());
                }
            }));
        });
    }

    private static void sendLoadFailure(ServerPlayer player, BlockPos devBlockPos, String message) {
        PacketDistributor.sendToPlayer(player, new net.astronomy.multilib.network.DevLoadResultPacket(
                devBlockPos, false, message, "", "", "", emptyScan()));
    }
}
