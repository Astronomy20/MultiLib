/**
 * Optional process-engine API for building timed "recipe job" machines on top of a formed multiblock
 * instance - the kind of state machine GregTech/Mekanism/Oritech-style machines all reimplement from
 * scratch: poll for a runnable recipe, consume its inputs, count progress tick by tick, optionally pause
 * on a per-tick cost, then produce outputs and go idle again.
 * <p>
 * This package is deliberately agnostic of items, fluids, and energy - nothing here imports a capability,
 * an inventory, or an energy storage type. The dev supplies a {@link net.astronomy.multilib.api.process.ProcessRecipe}
 * that knows how to check/consume/produce whatever it wants against whatever backs the controller, and
 * hands ticks to a {@link net.astronomy.multilib.api.process.RecipeProcessor}, which only tracks state and
 * progress.
 * <p>
 * Three pieces:
 * <ul>
 *     <li>{@link net.astronomy.multilib.api.process.ProcessRecipe} - what the dev implements: how long a
 *     job takes, whether it can start, what happens when it starts and finishes, and (optionally) whether
 *     it can keep going each tick.</li>
 *     <li>{@link net.astronomy.multilib.api.process.ProcessContext} - the thin bundle
 *     ({@code ServerLevel}, controller block entity, arbitrary dev data) handed to every
 *     {@link net.astronomy.multilib.api.process.ProcessRecipe} method call.</li>
 *     <li>{@link net.astronomy.multilib.api.process.RecipeProcessor} - the state machine itself. One
 *     instance per controller block entity, driven entirely by the dev calling
 *     {@link net.astronomy.multilib.api.process.RecipeProcessor#tick(net.astronomy.multilib.api.process.ProcessContext)}
 *     once per game tick. See its class javadoc for the full IDLE/RUNNING/PAUSED lifecycle.</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * A {@code RecipeProcessor} is typically a field on the dev's controller block entity subclass, driven
 * from the multiblock definition's {@code onTick} callback (MultiLib already calls this once per game tick
 * for every formed instance of that definition - see {@code MultiblockBuilder#onTick}):
 *
 * <pre>{@code
 * public class FurnaceControllerBE extends AbstractMultiblockControllerBE {
 *     // One processor per controller BE - no static/shared state.
 *     public final RecipeProcessor<FurnaceControllerBE, Void> processor =
 *             new RecipeProcessor<FurnaceControllerBE, Void>(20) // poll canStart() at most every 20 ticks
 *                     .pauseBehavior(RecipeProcessor.PauseBehavior.HOLD)
 *                     .onAborted(ctx -> {
 *                         // Refund policy is entirely the dev's job - RecipeProcessor never refunds.
 *                         MyInventoryUtil.refundLastConsumedInputs(ctx.controller());
 *                     });
 *
 *     public FurnaceControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
 *         super(type, pos, state);
 *     }
 *
 *     @Override
 *     protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {
 *         processor.save(tag); // progress/state/enabled only - the recipe itself is not serializable here
 *     }
 *
 *     @Override
 *     protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {
 *         processor.load(tag);
 *         // If processor.getState() != IDLE at this point, re-resolve and reassign the in-progress
 *         // recipe (e.g. from a dev-owned "recipeId" tag) via processor.setRecipe(...) before the next
 *         // tick, otherwise the processor holds state/progress as-is and waits.
 *     }
 * }
 * }</pre>
 *
 * <pre>{@code
 * MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("mymod", "furnace"))
 *         .name("furnace_multiblock")
 *         .layer("PPP", " P ", " G ")
 *         .key('P', BlockIngredient.of(MySetup.PART_BLOCK))
 *         .key('O', BlockIngredient.of(MySetup.CONTROLLER_BLOCK))
 *         .core('O')
 *         .onTick(ctx -> {
 *             ctx.instance().getCorePos().ifPresent(corePos -> {
 *                 if (ctx.level().getBlockEntity(corePos) instanceof FurnaceControllerBE controller) {
 *                     // Redstone-gate the whole job, and (re)assign a recipe when idle - both entirely
 *                     // up to the dev; RecipeProcessor has no opinion on either.
 *                     controller.processor.setEnabled(!ctx.level().hasNeighborSignal(corePos));
 *                     if (controller.processor.getRecipe().isEmpty()) {
 *                         MyRecipeMatcher.findMatching(controller).ifPresent(controller.processor::setRecipe);
 *                     }
 *                     controller.processor.tick(new ProcessContext<>(ctx.level(), controller, null));
 *                 }
 *             });
 *         })
 *         .build();
 * }</pre>
 */
package net.astronomy.multilib.api.process;
