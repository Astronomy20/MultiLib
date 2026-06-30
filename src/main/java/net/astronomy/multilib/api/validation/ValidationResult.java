package net.astronomy.multilib.api.validation;

import net.minecraft.core.BlockPos;

import java.util.List;

public sealed interface ValidationResult permits ValidationResult.Valid, ValidationResult.Invalid {
    record Valid() implements ValidationResult {}
    record Invalid(String message, List<BlockPos> problematicPositions) implements ValidationResult {
        public Invalid(String message) {
            this(message, List.of());
        }
    }

    static ValidationResult valid() { return new Valid(); }
    static ValidationResult invalid(String message) { return new Invalid(message); }
    static ValidationResult invalid(String message, List<BlockPos> positions) { return new Invalid(message, positions); }

    default boolean isValid() { return this instanceof Valid; }
}
