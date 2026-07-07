package net.astronomy.multilib.api.process;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A single timed job a {@link RecipeProcessor} can run: how long it takes, whether it can start, and what
 * happens at the start and end of it. This is the only piece of the process engine a dev implements
 * directly - {@link RecipeProcessor} itself never touches items, fluids, or energy, so all of that lives
 * in the dev's implementation of these methods.
 * <p>
 * A {@code ProcessRecipe} instance represents one already-matched job (e.g. "smelt this specific iron ore
 * stack into this specific ingot"), not a recipe book or a lookup table - matching the right recipe
 * against the controller's current inputs (mirroring vanilla's {@code RecipeManager}, a JSON-driven recipe
 * type, or a hardcoded rule) is entirely up to the dev, done before handing the matched instance to
 * {@link RecipeProcessor#setRecipe}.
 *
 * @param <BE> the dev's controller block entity type
 * @param <D>  the dev's arbitrary data type
 */
public interface ProcessRecipe<BE extends BlockEntity, D> {

    /**
     * How many ticks this job takes to run from a fresh start (progress {@code 0}) to completion. Read
     * once by {@link RecipeProcessor} at the start of a run and compared against accumulated progress
     * every {@code RUNNING} tick thereafter - changing the value returned mid-run has no effect until the
     * next run starts.
     */
    int durationTicks();

    /**
     * Whether this job can begin right now - typically "are the required inputs present in the
     * controller's inventory/tank/energy buffer". Polled by {@link RecipeProcessor} while idle, at most
     * once every {@link RecipeProcessor}'s poll interval rather than every tick. Must not mutate any
     * state; only {@link #consumeInputs} is allowed to do that, and only once this returns {@code true}.
     */
    boolean canStart(ProcessContext<BE, D> ctx);

    /**
     * Removes this job's inputs from the controller's inventory/tank/whatever backs it. Called exactly
     * once per run, immediately after {@link #canStart} returns {@code true} and before the run's first
     * tick of progress is counted. {@link RecipeProcessor} never calls this a second time for the same
     * run, including across a pause/resume cycle.
     */
    void consumeInputs(ProcessContext<BE, D> ctx);

    /**
     * Delivers this job's outputs to the controller's inventory/tank/whatever backs it. Called exactly
     * once per run, when accumulated progress reaches {@link #durationTicks()}, immediately before
     * {@link RecipeProcessor} returns to {@code IDLE}.
     */
    void produceOutputs(ProcessContext<BE, D> ctx);

    /**
     * Whether progress may keep accumulating this tick - the per-tick equivalent of {@link #canStart},
     * for costs that must be paid continuously rather than up front (e.g. "is there enough energy in the
     * buffer for this tick's cost"). Checked once per {@code RUNNING} tick, after inputs have already been
     * consumed. Returning {@code false} triggers whatever {@link RecipeProcessor.PauseBehavior} the
     * processor is configured with; it does not undo {@link #consumeInputs}.
     * <p>
     * Default implementation always returns {@code true}, i.e. a job that never pauses once started.
     */
    default boolean canContinue(ProcessContext<BE, D> ctx) {
        return true;
    }
}
