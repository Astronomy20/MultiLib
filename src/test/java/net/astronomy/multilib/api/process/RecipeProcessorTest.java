package net.astronomy.multilib.api.process;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link RecipeProcessor} lifecycle contract, exercised with a scripted recipe and a null-level
 * context (the processor itself never touches the level - only the dev's recipe does, and this one
 * doesn't). One-shot input consumption, one-shot output production, HOLD/RESET pause behavior, the
 * setEnabled hard gate, abort semantics and the NBT counter roundtrip are all part of the documented
 * contract, not implementation details.
 */
class RecipeProcessorTest {

    /** Scripted recipe: counts every callback, with switchable canStart/canContinue gates. */
    private static final class ScriptedRecipe implements ProcessRecipe<BlockEntity, Void> {
        final int duration;
        boolean canStart = true;
        boolean canContinue = true;
        int canStartPolls = 0;
        int consumed = 0;
        int produced = 0;

        ScriptedRecipe(int duration) {
            this.duration = duration;
        }

        @Override public int durationTicks() { return duration; }

        @Override public boolean canStart(ProcessContext<BlockEntity, Void> ctx) {
            canStartPolls++;
            return canStart;
        }

        @Override public void consumeInputs(ProcessContext<BlockEntity, Void> ctx) { consumed++; }
        @Override public void produceOutputs(ProcessContext<BlockEntity, Void> ctx) { produced++; }
        @Override public boolean canContinue(ProcessContext<BlockEntity, Void> ctx) { return canContinue; }
    }

    private static ProcessContext<BlockEntity, Void> ctx() {
        return new ProcessContext<>(null, null, null);
    }

    private static RecipeProcessor<BlockEntity, Void> processor(ScriptedRecipe recipe) {
        RecipeProcessor<BlockEntity, Void> p = new RecipeProcessor<BlockEntity, Void>().pollIntervalTicks(1);
        p.setRecipe(recipe);
        return p;
    }

    @Test
    void fullCycleConsumesOnceAndProducesOnce() {
        ScriptedRecipe recipe = new ScriptedRecipe(3);
        RecipeProcessor<BlockEntity, Void> p = processor(recipe);

        int ticks = 0;
        while (recipe.produced == 0 && ticks < 50) {
            p.tick(ctx());
            ticks++;
        }

        assertEquals(1, recipe.consumed, "consumeInputs must fire exactly once per job");
        assertEquals(1, recipe.produced, "produceOutputs must fire exactly once per job");
        assertEquals(RecipeProcessor.ProcessState.IDLE, p.getState(), "back to IDLE after completion");
        assertEquals(0, p.getProgress(), "progress reset after completion");
        assertTrue(ticks <= 3 + 2, "a 3-tick job must not take more than start + duration ticks, took " + ticks);
    }

    @Test
    void idlePollRespectsGate() {
        ScriptedRecipe recipe = new ScriptedRecipe(2);
        recipe.canStart = false;
        RecipeProcessor<BlockEntity, Void> p = processor(recipe);

        for (int i = 0; i < 5; i++) p.tick(ctx());
        assertEquals(RecipeProcessor.ProcessState.IDLE, p.getState());
        assertEquals(0, recipe.consumed, "inputs must never be consumed while canStart is false");
        assertTrue(recipe.canStartPolls > 0, "IDLE must keep polling canStart");
    }

    @Test
    void holdFreezesProgressAndResumes() {
        ScriptedRecipe recipe = new ScriptedRecipe(10);
        RecipeProcessor<BlockEntity, Void> p = processor(recipe).pauseBehavior(RecipeProcessor.PauseBehavior.HOLD);

        p.tick(ctx()); // start
        p.tick(ctx());
        p.tick(ctx());
        int progressBeforePause = p.getProgress();
        assertTrue(progressBeforePause > 0, "some progress before pausing");

        recipe.canContinue = false;
        p.tick(ctx());
        assertEquals(RecipeProcessor.ProcessState.PAUSED, p.getState());
        p.tick(ctx());
        p.tick(ctx());
        assertEquals(progressBeforePause, p.getProgress(), "HOLD must freeze progress");

        recipe.canContinue = true;
        p.tick(ctx());
        p.tick(ctx());
        assertEquals(RecipeProcessor.ProcessState.RUNNING, p.getState());
        assertTrue(p.getProgress() > progressBeforePause, "progress resumes after HOLD");
        assertEquals(0, recipe.produced, "job not done yet");
    }

    @Test
    void resetDropsToIdleAndFiresOnAborted() {
        ScriptedRecipe recipe = new ScriptedRecipe(10);
        int[] aborted = {0};
        RecipeProcessor<BlockEntity, Void> p = processor(recipe)
                .pauseBehavior(RecipeProcessor.PauseBehavior.RESET)
                .onAborted(c -> aborted[0]++);

        p.tick(ctx()); // start
        p.tick(ctx());
        recipe.canContinue = false;
        p.tick(ctx());

        assertEquals(RecipeProcessor.ProcessState.IDLE, p.getState(), "RESET goes straight back to IDLE");
        assertEquals(0, p.getProgress());
        assertEquals(1, aborted[0], "RESET is an abort and must fire the hook");
        assertEquals(0, recipe.produced);
    }

    @Test
    void disabledIsACompleteNoOp() {
        ScriptedRecipe recipe = new ScriptedRecipe(3);
        RecipeProcessor<BlockEntity, Void> p = processor(recipe);
        p.setEnabled(false);

        for (int i = 0; i < 10; i++) p.tick(ctx());
        assertEquals(0, recipe.canStartPolls, "disabled must not even poll canStart");
        assertEquals(0, recipe.consumed);
        assertEquals(RecipeProcessor.ProcessState.IDLE, p.getState());

        p.setEnabled(true);
        p.tick(ctx());
        assertEquals(1, recipe.consumed, "re-enabling resumes normal operation");
    }

    @Test
    void abortDoesNotRefundAndFiresHook() {
        ScriptedRecipe recipe = new ScriptedRecipe(10);
        int[] aborted = {0};
        RecipeProcessor<BlockEntity, Void> p = processor(recipe).onAborted(c -> aborted[0]++);

        p.tick(ctx()); // start (consumes)
        p.tick(ctx());
        p.abort();

        assertEquals(RecipeProcessor.ProcessState.IDLE, p.getState());
        assertEquals(0, p.getProgress());
        assertEquals(1, recipe.consumed, "abort never refunds by itself - that's the onAborted hook's job");
        assertEquals(1, aborted[0]);
    }

    @Test
    void nbtRoundtripPersistsCountersButNotTheRecipe() {
        ScriptedRecipe recipe = new ScriptedRecipe(10);
        RecipeProcessor<BlockEntity, Void> p = processor(recipe);
        p.tick(ctx()); // start
        p.tick(ctx());
        p.tick(ctx());
        int savedProgress = p.getProgress();

        CompoundTag tag = new CompoundTag();
        p.save(tag);

        RecipeProcessor<BlockEntity, Void> restored = new RecipeProcessor<BlockEntity, Void>().pollIntervalTicks(1);
        restored.load(tag);
        assertEquals(RecipeProcessor.ProcessState.RUNNING, restored.getState());
        assertEquals(savedProgress, restored.getProgress());
        assertTrue(restored.getRecipe().isEmpty(), "the recipe object itself is never persisted");

        // Documented no-op: a restored RUNNING processor with no recipe re-assigned holds state.
        restored.tick(ctx());
        assertEquals(RecipeProcessor.ProcessState.RUNNING, restored.getState());
        assertEquals(savedProgress, restored.getProgress());

        // Re-assigning the recipe resumes the counted-down job.
        ScriptedRecipe again = new ScriptedRecipe(10);
        restored.setRecipe(again);
        restored.tick(ctx());
        assertEquals(savedProgress + 1, restored.getProgress());
        assertEquals(0, again.consumed, "resuming a restored job must not re-consume inputs");
    }
}
