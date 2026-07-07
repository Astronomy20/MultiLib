package net.astronomy.multilib.api.port;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockPartBlock;
import net.minecraft.world.level.block.EntityBlock;

/**
 * Minimal base block for port/hatch blocks. Adds nothing over {@link AbstractMultiblockPartBlock}
 * beyond declaring {@link EntityBlock} - a port always needs a block entity (its
 * {@link AbstractPortBlockEntity}) to track which controller it currently belongs to, unlike a plain
 * structural part block which may have none at all. No ticker: ports never tick (see
 * {@link AbstractPortBlockEntity}'s class docs), so the default {@link EntityBlock#getTicker} (which
 * returns {@code null}) is left untouched.
 * <p>
 * A concrete subclass only needs to implement {@link EntityBlock#newBlockEntity}, the same way
 * {@code AbstractMultiblockControllerBlock} subclasses do for controllers.
 */
public abstract class AbstractPortBlock extends AbstractMultiblockPartBlock implements EntityBlock {

    protected AbstractPortBlock(Properties properties) {
        super(properties);
    }
}
