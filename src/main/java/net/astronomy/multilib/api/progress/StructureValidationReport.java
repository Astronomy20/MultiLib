package net.astronomy.multilib.api.progress;

import java.util.List;

/**
 * Superset of {@link StructureProgress}: not just what's missing, but also what's placed but wrong -
 * see {@link MultiblockProgressAPI#computeDetailed}. {@code formed} is a convenience for "no missing and
 * no mismatched positions", equivalent to what {@link net.astronomy.multilib.core.matching.PatternMatcher}
 * would report as a success.
 */
public record StructureValidationReport(boolean formed, List<MissingBlock> missing, List<StructureMismatch> mismatches) {

    public int missingCount() { return missing.size(); }
    public int mismatchCount() { return mismatches.size(); }
}
