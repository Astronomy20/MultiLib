package net.astronomy.multilib.api.ingredient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

public interface BlockIngredient {
    boolean matches(BlockState state);
    Set<Block> getCandidateBlocks();

    /**
     * Context-aware counterpart of {@link #matches(BlockState)}, used by the pattern matchers (which
     * always have {@code level}/{@code pos} in scope already) instead of the plain overload. Defaults
     * to delegating to {@link #matches(BlockState)} - only {@link AbilityBlockIngredient} (a block
     * matched by a runtime capability rather than its identity/tag) needs the extra context and
     * overrides this instead.
     */
    default boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return matches(state);
    }

    /**
     * Whether {@code state}'s block is one this ingredient could ever accept, ignoring blockstate
     * properties (facing, etc.) - i.e. "right block, though maybe the wrong orientation" as opposed to
     * "wrong block entirely". Used to tell apart {@code WRONG} from {@code WRONG_STATE} in the ghost
     * overlay/mismatch report. Defaults to a {@link #getCandidateBlocks()} membership check, which is
     * exact for every built-in ingredient type that has a fixed candidate set; a custom
     * {@link BlockIngredient} whose {@link #getCandidateBlocks()} can't enumerate every accepted block
     * (e.g. a fully dynamic predicate) will under-report here rather than over-report - never worse
     * than always falling back to a plain {@code WRONG}.
     */
    default boolean matchesBlockType(BlockState state) {
        return getCandidateBlocks().contains(state.getBlock());
    }

    /**
     * The {@link BlockState} previews (JEI/REI/EMI 3D model, ghost overlay) should render for this
     * ingredient. Defaults to the first candidate block's default state - blocks with meaningful
     * facing/property variants (furnaces, droppers, etc.) should override this to force a specific
     * orientation, e.g. via {@link StatePropertyIngredient}, which already applies any properties
     * required by {@code .require(...)}.
     */
    @Nullable
    default BlockState getRenderState() {
        Set<Block> candidates = getCandidateBlocks();
        if (candidates.isEmpty()) return null;
        return candidates.iterator().next().defaultBlockState();
    }

    static BlockIngredient of(Block block) {
        return new SingleBlockIngredient(block);
    }

    static StatePropertyIngredient.Builder ofState(Block block) {
        return StatePropertyIngredient.forBlock(block);
    }

    static BlockIngredient tag(TagKey<Block> tag) {
        return new TagBlockIngredient(tag);
    }

    static BlockIngredient anyOf(BlockIngredient... ingredients) {
        return new AnyOfBlockIngredient(Arrays.asList(ingredients));
    }

    static BlockIngredient predicate(Predicate<BlockState> predicate) {
        return new PredicateBlockIngredient(predicate);
    }

    static BlockIngredient any() {
        return AnyBlockIngredient.INSTANCE;
    }

    /**
     * Matches any block whose block entity currently exposes {@code capability} on at least one side
     * (checked at match-time, not registration-time) - e.g. "any block that exposes an energy storage
     * capability", regardless of which mod added it. Unlike {@link #tag}, this needs no shared tag
     * convention between MultiLib and every addon that might supply a compatible block: a third-party
     * block nobody tagged still matches as long as it genuinely exposes the capability when queried.
     * {@code previewBlock} is only used for previews (JEI/REI/EMI, ghost overlay) since there's no
     * single "the" block for a capability - pick whichever concrete block is the canonical example.
     */
    static BlockIngredient ability(BlockCapability<?, net.minecraft.core.Direction> capability, Block previewBlock) {
        return new AbilityBlockIngredient(capability, previewBlock);
    }

    /**
     * Parses a single block id ({@code "minecraft:iron_block"}) or, with a {@code #} prefix, a block
     * tag ({@code "#forge:storage_blocks/iron"}) - the same two forms the JSON datapack format takes
     * as separate {@code "block"}/{@code "tag"} key fields (see {@code MultiblockCodecs}), collapsed
     * into one string since that's the more natural shape for a scripting call site. Exists mainly for
     * {@code MultiblockBuilder#key(char, String)}, so KubeJS scripts have the same tag/block coverage
     * Java (via {@link #of}/{@link #tag}) and JSON already do, without needing a Java enum/class
     * reference for either.
     *
     * @throws IllegalArgumentException if the id is malformed or (for a plain block id) unregistered
     */
    static BlockIngredient parse(String spec) {
        if (spec.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.parse(spec.substring(1));
            return tag(TagKey.create(Registries.BLOCK, tagId));
        }
        ResourceLocation blockId = ResourceLocation.parse(spec);
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown block: " + blockId));
        return of(block);
    }
}
