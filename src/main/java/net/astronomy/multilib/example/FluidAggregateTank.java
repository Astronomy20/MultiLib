package net.astronomy.multilib.example;

import net.astronomy.multilib.api.aggregate.AggregateGroup;
import net.astronomy.multilib.api.component.FluidTankComponent;

/**
 * Small, fluid-specific view both {@link ExampleRedTankBlockEntity} and
 * {@link ExampleGreenTankBlockEntity} implement, purely so
 * {@link net.astronomy.multilib.client.render.FluidAggregateTankRenderer} can render either one without
 * caring which shape policy it uses. Deliberately kept out of {@code api.aggregate} - that package knows
 * nothing about fluids, and never needs to.
 */
public interface FluidAggregateTank {

    AggregateGroup getAggregateGroup();

    FluidTankComponent getTank();
}
