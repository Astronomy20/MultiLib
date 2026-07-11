package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.process.RecipeProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that complements {@link ProcessHudProvider}'s progress bar with WHAT is currently
 * running, via {@link net.astronomy.multilib.api.process.ProcessRecipe#getDisplayName()}. Uses the same
 * {@link HudProcessSource} hook as {@link ProcessHudProvider} - register both together for a full
 * "Processing: Iron Ingot [===  ] 40%" picture, or this alone for just the name. Shows nothing if the
 * core isn't a {@link HudProcessSource}, has no current recipe, or that recipe declares no display name.
 * Register per-definition: {@code MultiblockHudRegistry.register(definitionId, new RecipeHudProvider())}.
 */
public final class RecipeHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudProcessSource source)) return;

        source.getHudProcessor()
                .flatMap(RecipeProcessor::getRecipe)
                .flatMap(recipe -> recipe.getDisplayName())
                .ifPresent(name -> out.accept(new HudEntry.KeyValue(
                        Component.translatable("multilib.hud.recipe"), name)));
    }
}
