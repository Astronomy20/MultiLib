package net.astronomy.multilib.api.callback;

import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.validation.ValidationResult;

@FunctionalInterface
public interface MultiblockValidator {
    ValidationResult validate(MultiblockContext ctx);
}
