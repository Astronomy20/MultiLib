package net.astronomy.multilib.api.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractMultiblockPartBE extends BlockEntity implements IMultiblockPart {
    private final MultiblockPartComponent multiblock = new MultiblockPartComponent(this);

    protected AbstractMultiblockPartBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public MultiblockPartComponent getMultiblockComponent() { return multiblock; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        multiblock.saveToTag(tag);
        savePart(tag, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        multiblock.loadFromTag(tag);
        loadPart(tag, registries);
    }

    protected void savePart(CompoundTag tag, HolderLookup.Provider registries) {}
    protected void loadPart(CompoundTag tag, HolderLookup.Provider registries) {}
}
