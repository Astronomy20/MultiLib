package net.astronomy.multilib.api.process;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A timed-job state machine for a single formed multiblock controller, in the shape GregTech/Mekanism/
 * Oritech-style machines all reimplement: poll for a runnable recipe, consume its inputs once, count
 * progress tick by tick (optionally pausing on a per-tick cost check), then produce its outputs once and
 * go back to idle.
 * <p>
 * A {@code RecipeProcessor} never ticks itself - MultiLib already drives per-instance tick callbacks (see
 * {@code MultiblockDefinition#getTickCallback()} / {@code MultiblockBuilder#onTick}), so the dev calls
 * {@link #tick(ProcessContext)} once per game tick from their own {@code onTick} callback or controller BE
 * tick method. One instance of this class is meant to be held per controller block entity; there is no
 * static/shared state, so as many independent jobs as there are formed controllers can run concurrently.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *             (recipe set, canStart() true - consumeInputs() called)
 *   IDLE ------------------------------------------------------------&gt; RUNNING
 *    ^                                                                   |  |
 *    |                (progress reaches durationTicks() - produceOutputs())  |
 *    +-------------------------------------------------------------------+  | canContinue() false
 *    |                                                                      v
 *    |                                                                   PAUSED  (PauseBehavior.HOLD)
 *    |                              canContinue() true again                |
 *    +------------------------------------------------------------------------+
 *
 *   canContinue() false with PauseBehavior.RESET drops straight back to IDLE (see below) instead of
 *   entering PAUSED. abort() and setEnabled(false) can interrupt from any state (see their docs).
 * </pre>
 *
 * <h3>IDLE</h3>
 * Nothing runs. Every {@link #tick} call increments an internal poll counter; once it reaches
 * {@code pollIntervalTicks} (default {@code 20}, i.e. once a second) it resets and, if a recipe has been
 * assigned via {@link #setRecipe}, calls {@link ProcessRecipe#canStart}. If that returns {@code true},
 * {@link ProcessRecipe#consumeInputs} is called exactly once, progress resets to {@code 0}, and the state
 * becomes {@code RUNNING} - all within that same tick. If no recipe is assigned, or {@code canStart}
 * returns {@code false}, nothing happens and the poll counter starts counting down again.
 *
 * <h3>RUNNING</h3>
 * Every tick, {@link ProcessRecipe#canContinue} is checked first. If it returns {@code false}, the
 * configured {@link PauseBehavior} decides what happens (see below) and progress is not incremented that
 * tick. Otherwise progress is incremented by one. Once progress reaches
 * {@link ProcessRecipe#durationTicks()}, {@link ProcessRecipe#produceOutputs} is called exactly once,
 * progress resets to {@code 0}, and the state returns to {@code IDLE} (the poll counter is also reset, so
 * the very next tick after completion is not itself an automatic poll tick).
 *
 * <h3>PAUSED (only reachable with {@link PauseBehavior#HOLD})</h3>
 * Progress is frozen exactly where it was. Every tick, {@link ProcessRecipe#canContinue} is re-checked;
 * once it returns {@code true} again the state returns to {@code RUNNING}, and progress resumes
 * accumulating starting the tick after that (the tick that flips the state back does not itself also
 * count a tick of progress, to keep the transition unambiguous). Nothing about the recipe's inputs is
 * re-consumed or refunded while paused - a recipe that was already validly consumed stays consumed.
 *
 * <p>With {@link PauseBehavior#RESET} instead, a failed {@code canContinue} check drops progress straight
 * to {@code 0} and returns to {@code IDLE} in the same tick - functionally identical to calling
 * {@link #abort()} at that instant, including invoking whatever {@link #onAborted} handler is registered,
 * except triggered automatically by the recipe's own per-tick cost check rather than by the dev. There is
 * no intermediate {@code PAUSED} state in this mode: the job is simply over, and starting it again means
 * going through {@code canStart}/{@code consumeInputs} from scratch.
 *
 * <h2>Disabling and aborting</h2>
 * {@link #setEnabled(boolean)} is a hard external gate - typically wired by the dev to a redstone signal
 * or a GUI toggle. While disabled, {@link #tick} is a complete no-op: no {@link ProcessRecipe} method is
 * ever called, and the current state/progress is left exactly as it was, frozen, until re-enabled. This
 * is stricter than {@link PauseBehavior#HOLD} (which still polls {@code canContinue} every tick) and is
 * meant for "this machine is switched off", not "this machine's inputs momentarily ran dry".
 * <p>
 * {@link #abort()} forcibly drops any in-progress job back to {@code IDLE} with progress reset to
 * {@code 0}, regardless of the current state. It intentionally does <b>not</b> refund whatever
 * {@link ProcessRecipe#consumeInputs} already removed - refunding (if any) is entirely the dev's call,
 * made from the {@link #onAborted} hook registered via {@link #onAborted(Consumer)}. {@code abort()} takes
 * no {@link ProcessContext} argument (so it can be called from places that don't have one in hand, e.g. a
 * GUI button handler), so the context handed to the {@link #onAborted} callback is whichever
 * {@link ProcessContext} was passed to the most recent {@link #tick} call; if {@link #tick} has never been
 * called yet, the hook is not invoked at all.
 *
 * <h2>Persistence</h2>
 * {@link #save(CompoundTag)} and {@link #load(CompoundTag)} persist the current {@link ProcessState},
 * accumulated progress, and the {@link #setEnabled enabled} flag - enough for a job to survive a chunk
 * unload/world reload without silently losing its place. They deliberately do <b>not</b> persist the
 * assigned {@link ProcessRecipe} itself, since a recipe instance is an arbitrary dev object (potentially
 * referencing capability handles, item stacks, or other things that don't have a generic NBT
 * representation MultiLib could serialize on the dev's behalf). After {@link #load}, {@link #getRecipe()}
 * is always empty - if the restored state is {@code RUNNING} or {@code PAUSED}, the dev is responsible for
 * re-resolving and reassigning the in-progress recipe (e.g. from their own saved recipe id) via
 * {@link #setRecipe} before the next {@link #tick} call that should resume it. Until a recipe is
 * reassigned, {@link #tick} on a {@code RUNNING}/{@code PAUSED} processor with no recipe set is a no-op
 * that holds state and progress exactly as loaded.
 *
 * @param <BE> the dev's controller block entity type
 * @param <D>  the dev's arbitrary data type
 */
public final class RecipeProcessor<BE extends BlockEntity, D> {

    /** The three states this state machine can be in. See the class javadoc for the full lifecycle. */
    public enum ProcessState { IDLE, RUNNING, PAUSED }

    /** How a {@code RUNNING} job reacts to {@link ProcessRecipe#canContinue} returning {@code false}. */
    public enum PauseBehavior {
        /** Freeze progress in a {@code PAUSED} state; resume counting once {@code canContinue} is true again. */
        HOLD,
        /** Drop progress to {@code 0} and return straight to {@code IDLE}, as if {@link #abort()} were called. */
        RESET
    }

    private ProcessState state = ProcessState.IDLE;
    private int progress = 0;
    private int pollTicker = 0;
    private int pollIntervalTicks;
    private boolean enabled = true;
    private PauseBehavior pauseBehavior = PauseBehavior.HOLD;

    private @Nullable ProcessRecipe<BE, D> recipe;
    private @Nullable Consumer<ProcessContext<BE, D>> onAbortedHandler;
    private @Nullable ProcessContext<BE, D> lastContext;

    /** Equivalent to {@code new RecipeProcessor<>(20)} - polls for a startable recipe once a second. */
    public RecipeProcessor() {
        this(20);
    }

    /**
     * @param pollIntervalTicks how often, while {@code IDLE}, to check {@link ProcessRecipe#canStart}
     *                          (values {@code <= 0} are clamped up to {@code 1}, i.e. checked every tick)
     */
    public RecipeProcessor(int pollIntervalTicks) {
        this.pollIntervalTicks = Math.max(1, pollIntervalTicks);
    }

    // ---- Configuration (fluent) ----

    public RecipeProcessor<BE, D> pollIntervalTicks(int ticks) {
        this.pollIntervalTicks = Math.max(1, ticks);
        return this;
    }

    public RecipeProcessor<BE, D> pauseBehavior(PauseBehavior behavior) {
        this.pauseBehavior = behavior;
        return this;
    }

    /**
     * Registers the hook called whenever a job is torn down without completing - via {@link #abort()}, or
     * automatically when {@link PauseBehavior#RESET} is configured and {@link ProcessRecipe#canContinue}
     * returns {@code false}. This is the dev's only chance to refund whatever {@link ProcessRecipe#consumeInputs}
     * already took, since {@code RecipeProcessor} itself never does so.
     */
    public RecipeProcessor<BE, D> onAborted(Consumer<ProcessContext<BE, D>> handler) {
        this.onAbortedHandler = handler;
        return this;
    }

    /**
     * Assigns (or clears, with {@code null}) the recipe this processor will try to run next time it's
     * {@code IDLE} and polls {@link ProcessRecipe#canStart}. Matching the right recipe against the
     * controller's current inputs is entirely the dev's responsibility, done before calling this -
     * {@code RecipeProcessor} has no notion of a recipe book or lookup table.
     * <p>
     * Safe to call at any time, including while {@code RUNNING}/{@code PAUSED}: doing so only changes
     * which recipe is consulted the <i>next</i> time the processor goes looking for a new job (e.g. once
     * the currently running one completes or is aborted) - it does not affect the job already in
     * progress, since that job's inputs were already consumed against the previously assigned recipe.
     */
    public void setRecipe(@Nullable ProcessRecipe<BE, D> recipe) {
        this.recipe = recipe;
    }

    public Optional<ProcessRecipe<BE, D>> getRecipe() {
        return Optional.ofNullable(recipe);
    }

    /**
     * Hard external gate, e.g. wired by the dev to a redstone signal or a GUI toggle. While disabled,
     * {@link #tick} is a complete no-op - no {@link ProcessRecipe} method is ever invoked and the current
     * state/progress is frozen as-is until re-enabled. See the class javadoc for how this differs from
     * {@link PauseBehavior#HOLD}.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ---- Driving ----

    /**
     * Advances this state machine by exactly one tick. Must be called at most once per game tick, by the
     * dev, from their own {@code onTick} callback or controller BE tick method - {@code RecipeProcessor}
     * never schedules or throttles its own ticking. See the class javadoc for the full per-state
     * behavior. A no-op if {@link #isEnabled()} is {@code false}, aside from remembering {@code ctx} for a
     * possible later {@link #abort()} call.
     * <p>
     * Any exception thrown by a {@link ProcessRecipe} method called from here propagates straight out of
     * this method - it is not caught or swallowed. Note that MultiLib's own callback dispatch (e.g. the
     * {@code onFormed}/{@code onBroken} callbacks fired around structure formation) does catch and log
     * exceptions thrown by dev callbacks at that dispatch layer; whether the {@code onTick} callback that
     * calls {@code tick()} is similarly wrapped depends on that callback's own registration path, so a dev
     * whose {@link ProcessRecipe} implementation can throw should not rely on this method to protect them.
     */
    public void tick(ProcessContext<BE, D> ctx) {
        this.lastContext = ctx;
        if (!enabled) return;

        switch (state) {
            case IDLE -> tickIdle(ctx);
            case RUNNING -> tickRunning(ctx);
            case PAUSED -> tickPaused(ctx);
        }
    }

    private void tickIdle(ProcessContext<BE, D> ctx) {
        if (recipe == null) return;
        pollTicker++;
        if (pollTicker < pollIntervalTicks) return;
        pollTicker = 0;
        if (!recipe.canStart(ctx)) return;
        recipe.consumeInputs(ctx);
        progress = 0;
        state = ProcessState.RUNNING;
    }

    private void tickRunning(ProcessContext<BE, D> ctx) {
        if (recipe == null) return;
        if (!recipe.canContinue(ctx)) {
            handlePause(ctx);
            return;
        }
        progress++;
        if (progress >= recipe.durationTicks()) {
            recipe.produceOutputs(ctx);
            progress = 0;
            pollTicker = 0;
            state = ProcessState.IDLE;
        }
    }

    private void tickPaused(ProcessContext<BE, D> ctx) {
        if (recipe == null) return;
        if (!recipe.canContinue(ctx)) return;
        state = ProcessState.RUNNING;
    }

    private void handlePause(ProcessContext<BE, D> ctx) {
        if (pauseBehavior == PauseBehavior.HOLD) {
            state = ProcessState.PAUSED;
            return;
        }
        progress = 0;
        pollTicker = 0;
        state = ProcessState.IDLE;
        if (onAbortedHandler != null) {
            onAbortedHandler.accept(ctx);
        }
    }

    /**
     * Forcibly tears down whatever job is in progress (if any) and returns to {@code IDLE} with progress
     * reset to {@code 0}. Does <b>not</b> refund inputs already taken by {@link ProcessRecipe#consumeInputs}
     * - see the class javadoc's "Disabling and aborting" section for the refund hook. A no-op if already
     * {@code IDLE}.
     */
    public void abort() {
        if (state == ProcessState.IDLE) return;
        progress = 0;
        pollTicker = 0;
        state = ProcessState.IDLE;
        if (onAbortedHandler != null && lastContext != null) {
            onAbortedHandler.accept(lastContext);
        }
    }

    // ---- Queries ----

    public ProcessState getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    /** {@code 0} if no recipe is currently assigned, otherwise {@link ProcessRecipe#durationTicks()}. */
    public int getDurationTicks() {
        return recipe != null ? recipe.durationTicks() : 0;
    }

    /** {@link #getProgress()} divided by {@link #getDurationTicks()}, clamped to {@code [0, 1]}; {@code 0} if no recipe is assigned. */
    public float getProgressFraction() {
        int duration = getDurationTicks();
        if (duration <= 0) return 0f;
        return Math.min(1f, (float) progress / duration);
    }

    // ---- Persistence ----

    /**
     * Persists {@link #getState()}, {@link #getProgress()}, and {@link #isEnabled()} into {@code tag}. Does
     * <b>not</b> persist the assigned {@link ProcessRecipe} - see the class javadoc's "Persistence" section.
     */
    public void save(CompoundTag tag) {
        tag.putString("state", state.name());
        tag.putInt("progress", progress);
        tag.putBoolean("enabled", enabled);
    }

    /**
     * Restores state/progress/enabled previously written by {@link #save(CompoundTag)}. Always clears the
     * assigned recipe (see the class javadoc's "Persistence" section) and the internal poll counter, so
     * the very next {@code IDLE} tick after a load starts a fresh poll countdown.
     */
    public void load(CompoundTag tag) {
        ProcessState loaded;
        try {
            loaded = ProcessState.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            loaded = ProcessState.IDLE;
        }
        this.state = loaded;
        this.progress = tag.getInt("progress");
        this.enabled = !tag.contains("enabled") || tag.getBoolean("enabled");
        this.pollTicker = 0;
        this.recipe = null;
    }
}
