package net.astronomy.multilib.compat.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin for MultiLib - bridges {@code api/hud}'s viewer-agnostic {@code MultiblockHudProvider}s
 * onto Jade's tooltip. Auto-discovered by Jade via {@link WailaPlugin} once Jade is installed; classloads
 * safely without it since Jade only scans for (and touches) classes carrying this annotation when Jade
 * itself is present - unlike {@code compat/top}, nothing here needs a {@code ModList} guard.
 * <p>
 * Registers against the base {@link Block} class in both halves (server data + client component) so the
 * bridge runs for every block Jade probes; {@link MultiblockJadeServerProvider}/
 * {@link MultiblockJadeComponentProvider} return immediately (writing/rendering nothing) for any block
 * that isn't part of a formed multiblock instance and isn't a tracked definition's core - the common
 * case for the overwhelming majority of blocks in the world.
 */
@WailaPlugin
public final class MultiblockJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MultiblockJadeServerProvider.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MultiblockJadeComponentProvider.INSTANCE, Block.class);
    }
}
