package net.astronomy.multilib.example.tank;

import net.astronomy.multilib.api.component.FluidTankComponent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * Presents several real, independent {@link FluidTankComponent}s - one per block of a formed
 * {@code multilib:expandable_tank} instance, each holding its own actual content - as a SINGLE logical
 * tank whose capacity/amount is their sum. Fill/drain distribute across the underlying per-block tanks
 * transparently, so it doesn't matter which specific block a bucket or pipe touches: every interaction
 * reads/writes the same combined total, and each block's own share is still exactly what gets saved to
 * (and loaded from) that block's own NBT - see {@link ExampleTankBlockEntity}. Constructed fresh per
 * capability lookup (cheap - just wraps the list, no copying of fluid data) rather than cached, since the
 * set of member tanks itself changes whenever the structure grows/shrinks.
 */
public class ExampleTankAggregateFluidHandler implements IFluidHandler {

    private final List<FluidTankComponent> tanks;

    public ExampleTankAggregateFluidHandler(List<FluidTankComponent> tanks) {
        this.tanks = tanks;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        FluidStack combined = FluidStack.EMPTY;
        for (FluidTankComponent t : tanks) {
            if (t.isEmpty()) continue;
            if (combined.isEmpty()) {
                combined = t.getFluid().copy();
            } else if (FluidStack.isSameFluidSameComponents(combined, t.getFluid())) {
                combined.grow(t.getFluidAmount());
            }
            // A tank holding a DIFFERENT fluid than what's already been summed is skipped rather than
            // mixed in - shouldn't normally happen (fill() below only ever tops up matching/empty
            // tanks), but reporting a nonsensical combined stack would be worse than under-reporting.
        }
        return combined;
    }

    @Override
    public int getTankCapacity(int tank) {
        int total = 0;
        for (FluidTankComponent t : tanks) total += t.getCapacity();
        return total;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        for (FluidTankComponent t : tanks) {
            if (t.isFluidValid(stack)) return true;
        }
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        int filled = 0;
        int remaining = resource.getAmount();
        for (FluidTankComponent t : tanks) {
            if (remaining <= 0) break;
            int f = t.fill(resource.copyWithAmount(remaining), action);
            filled += f;
            remaining -= f;
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack result = FluidStack.EMPTY;
        int remaining = resource.getAmount();
        for (FluidTankComponent t : tanks) {
            if (remaining <= 0) break;
            FluidStack drained = t.drain(resource.copyWithAmount(remaining), action);
            if (drained.isEmpty()) continue;
            if (result.isEmpty()) result = drained.copy();
            else result.grow(drained.getAmount());
            remaining -= drained.getAmount();
        }
        return result;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        FluidStack result = FluidStack.EMPTY;
        int remaining = maxDrain;
        for (FluidTankComponent t : tanks) {
            if (remaining <= 0) break;
            if (t.isEmpty()) continue;
            // Once a fluid type has been picked (from whichever tank drains first), stay consistent -
            // only keep draining that same fluid from the rest, same reasoning as getFluidInTank above.
            if (!result.isEmpty() && !FluidStack.isSameFluidSameComponents(result, t.getFluid())) continue;
            FluidStack drained = t.drain(remaining, action);
            if (drained.isEmpty()) continue;
            if (result.isEmpty()) result = drained.copy();
            else result.grow(drained.getAmount());
            remaining -= drained.getAmount();
        }
        return result;
    }
}
