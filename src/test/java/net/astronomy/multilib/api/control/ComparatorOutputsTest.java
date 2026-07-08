package net.astronomy.multilib.api.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla comparator convention {@link ComparatorOutputs} promises: 0 only when truly empty,
 * at least 1 for any non-empty amount, 15 only when full.
 */
class ComparatorOutputsTest {

    @Test
    void emptyIsZero() {
        assertEquals(0, ComparatorOutputs.fromFraction(0.0));
        assertEquals(0, ComparatorOutputs.fromStoredEnergy(0, 100_000));
        assertEquals(0, ComparatorOutputs.fromFluid(0, 16_000));
        assertEquals(0, ComparatorOutputs.scaled(0, 100));
    }

    @Test
    void anyNonEmptyAmountIsAtLeastOne() {
        assertTrue(ComparatorOutputs.fromStoredEnergy(1, 1_000_000) >= 1);
        assertTrue(ComparatorOutputs.fromFluid(1, 16_000) >= 1);
        assertTrue(ComparatorOutputs.scaled(1, 10_000) >= 1);
        assertTrue(ComparatorOutputs.fromFraction(0.0001) >= 1);
    }

    @Test
    void fullIsFifteen() {
        assertEquals(15, ComparatorOutputs.fromFraction(1.0));
        assertEquals(15, ComparatorOutputs.fromStoredEnergy(100_000, 100_000));
        assertEquals(15, ComparatorOutputs.fromFluid(16_000, 16_000));
        assertEquals(15, ComparatorOutputs.scaled(100, 100));
    }

    @Test
    void nearlyFullIsNotFifteen() {
        // 15 must mean "full", mirroring how 0 means "empty" - vanilla containers behave the same.
        assertTrue(ComparatorOutputs.fromStoredEnergy(99_999, 100_000) < 15);
    }

    @Test
    void fractionIsClamped() {
        assertEquals(15, ComparatorOutputs.fromFraction(2.5));
        assertEquals(0, ComparatorOutputs.fromFraction(-1.0));
    }

    @Test
    void midpointIsMonotonic() {
        int prev = -1;
        for (int amount = 0; amount <= 100; amount++) {
            int level = ComparatorOutputs.scaled(amount, 100);
            assertTrue(level >= prev, "signal must never decrease as the amount grows");
            prev = level;
        }
    }

    @Test
    void zeroCapacityIsSafe() {
        assertEquals(0, ComparatorOutputs.fromStoredEnergy(0, 0));
        assertEquals(0, ComparatorOutputs.fromFluid(0, 0));
        assertEquals(0, ComparatorOutputs.scaled(0, 0));
    }
}
