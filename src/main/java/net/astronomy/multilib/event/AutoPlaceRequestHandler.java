package net.astronomy.multilib.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoPlaceRequestHandler {

    public static void handleRequest(RequestAutoPlacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos corePos = packet.corePos();

            MultiblockDefinition definition = findAutoPlaceDefinitionAt(level, corePos);
            if (definition == null) return;
            if (definition.getLayers().isEmpty()) return;

            if (PatternMatcher.matches(level, corePos, definition) instanceof MatchResult.Success) {
                return;
            }

            List<List<String>> layers = definition.getLayers();
            Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
            char coreSymbol = definition.getCoreSymbol();
            BlockPos origin = findSymbolOrigin(corePos, layers, coreSymbol);
            Set<Character> freeBlockSymbols = definition.getFreeBlocks().keySet();

            int placed = 0;
            int missing = 0;

            for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
                List<String> layer = layers.get(layerIdx);
                int height = layer.size();
                if (height == 0) continue;
                int width = layer.get(0).length();
                int centerX = width / 2;
                int centerZ = height / 2;
                int relY = (layers.size() - 1) - layerIdx;

                for (int row = 0; row < height; row++) {
                    String line = layer.get(row);
                    for (int col = 0; col < Math.min(width, line.length()); col++) {
                        char symbol = line.charAt(col);
                        if (symbol == ' ') continue;
                        if (freeBlockSymbols.contains(symbol)) continue;
                        BlockIngredient ingredient = blockMap.get(symbol);
                        if (ingredient == null) continue;

                        int relX = col - centerX;
                        int relZ = row - centerZ;
                        BlockPos targetPos = origin.offset(relX, relY, relZ);

                        BlockState actual = level.getBlockState(targetPos);
                        if (!actual.isAir()) continue;

                        BlockState expectedState = getRepresentativeState(ingredient);
                        if (expectedState == null) continue;

                        if (tryPlace(player, level, targetPos, expectedState)) {
                            placed++;
                        } else {
                            missing++;
                        }
                    }
                }
            }

            if (placed > 0 || missing > 0) {
                BlockActivationHandler.triggerFormationAt(level, corePos);
                Component message = missing > 0
                    ? Component.literal("Placed " + placed + " block(s), missing " + missing + " item(s) to finish")
                    : Component.literal("Placed " + placed + " block(s)");
                player.displayClientMessage(message, true);
            }
        });
    }

    private static boolean tryPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player.isCreative()) {
            level.setBlockAndUpdate(pos, state);
            return true;
        }

        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;

        ItemStack found = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                found = stack;
                break;
            }
        }
        if (found == null) return false;

        found.shrink(1);
        level.setBlockAndUpdate(pos, state);
        return true;
    }

    // Only definitions opted into autoPlace() and whose core symbol matches the clicked block are
    // considered here, mirroring GhostOverlayInputHandler's trigger detection.
    private static MultiblockDefinition findAutoPlaceDefinitionAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition def : MultiblockRegistry.getCandidatesFor(state.getBlock())) {
            if (def.isAutoPlace() && def.matchesCore(state)) {
                return def;
            }
        }
        return null;
    }

    /** Mirrors OverlayRequestHandler.findSymbolOrigin. */
    private static BlockPos findSymbolOrigin(BlockPos clickedPos, List<List<String>> layers, char symbol) {
        int layersCount = layers.size();
        for (int layerIdx = 0; layerIdx < layersCount; layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    if (line.charAt(col) != symbol) continue;
                    int relX = col - centerX;
                    int relY = (layersCount - 1) - layerIdx;
                    int relZ = row - centerZ;
                    return clickedPos.offset(-relX, -relY, -relZ);
                }
            }
        }
        return clickedPos;
    }

    private static BlockState getRepresentativeState(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (!candidates.isEmpty()) return candidates.iterator().next().defaultBlockState();
        return null;
    }
}
