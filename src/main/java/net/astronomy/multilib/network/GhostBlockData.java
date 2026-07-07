package net.astronomy.multilib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record GhostBlockData(BlockPos pos, BlockState expectedState, Status status) {

    /**
     * CORE: the structure's core/main block position, always highlighted even when already correct.
     * PLACEABLE: a missing position the player's currently held item can fill via auto-place.
     * WRONG_STATE: the right block is already there, but with the wrong blockstate properties (e.g.
     * facing) - as opposed to WRONG, an entirely different block.
     */
    public enum Status { MISSING, WRONG, WRONG_STATE, CORE, PLACEABLE }

    public static final StreamCodec<ByteBuf, GhostBlockData> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        GhostBlockData::pos,
        ByteBufCodecs.VAR_INT.map(Block::stateById, Block::getId),
        GhostBlockData::expectedState,
        ByteBufCodecs.VAR_INT.map(id -> Status.values()[id], Status::ordinal),
        GhostBlockData::status,
        GhostBlockData::new
    );
}
