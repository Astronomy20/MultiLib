package net.astronomy.multilib.compat.guideme;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.structure.MultiblockStructureExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * GuideME integration.
 *
 * <p>GuideME does not expose a Java registration API for "structures to preview" - instead, guide
 * pages embed a {@code <GameScene><ImportStructure src="..."/></GameScene>} tag that points at a
 * plain structure NBT/SNBT file shipped next to the page. This helper generates that file directly
 * from a {@link MultiblockDefinition} (via {@link MultiblockStructureExporter}), so mod devs don't
 * have to hand-build the structure in a test world, plus a ready-to-paste GameScene markdown
 * snippet that references it and highlights the core block.
 */
public final class GuideMEHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("multilib-guideme");

    private GuideMEHelper() {}

    /** Exports the definition's pattern as SNBT text, or empty if it can't be exported (see {@link MultiblockStructureExporter}). */
    public static Optional<String> exportStructure(MultiblockDefinition definition) {
        return MultiblockStructureExporter.exportToSnbt(definition);
    }

    /**
     * Writes the definition's structure to {@code targetFile} (typically next to the GuideME page
     * that will reference it, e.g. via {@link #buildGameSceneMarkdown}). Returns {@code false}
     * without throwing if the definition can't be exported or the write fails.
     */
    public static boolean writeStructureFile(MultiblockDefinition definition, Path targetFile) {
        Optional<String> snbt = exportStructure(definition);
        if (snbt.isEmpty()) {
            LOGGER.warn("[MultiLib/GuideME] Could not export a structure for '{}' (shapeless, functional, or empty pattern)",
                    definition.getId());
            return false;
        }
        try {
            Path parent = targetFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(targetFile, snbt.get(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[MultiLib/GuideME] Failed to write structure file for '{}' to {}", definition.getId(), targetFile, e);
            return false;
        }
    }

    /**
     * Builds a ready-to-paste GameScene markdown snippet referencing {@code structureFileName}
     * (the path used in the page's {@code <ImportStructure src="...">}, relative to the page as
     * GuideME expects). If the definition has a core symbol, its position is highlighted with a
     * {@code BlockAnnotation}.
     */
    public static String buildGameSceneMarkdown(MultiblockDefinition definition, String structureFileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<GameScene zoom=\"1\">\n");
        sb.append("  <ImportStructure src=\"").append(structureFileName).append("\" />\n");
        MultiblockStructureExporter.findCorePosition(definition).ifPresent(pos -> sb
                .append("  <BlockAnnotation x=\"").append(pos[0])
                .append("\" y=\"").append(pos[1])
                .append("\" z=\"").append(pos[2])
                .append("\" color=\"#4CAF50\">").append(definition.getId()).append("</BlockAnnotation>\n"));
        sb.append("</GameScene>\n");
        return sb.toString();
    }
}
