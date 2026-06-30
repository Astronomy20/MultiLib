package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.UUID;

public final class MultiblockPartComponent {
    private final BlockEntity owner;
    private UUID instanceId = null;

    public MultiblockPartComponent(BlockEntity owner) {
        this.owner = owner;
    }

    public boolean isPartOfStructure() { return instanceId != null; }
    public UUID getInstanceId() { return instanceId; }

    public Optional<MultiblockInstance> getInstance(ServerLevel level) {
        if (instanceId == null) return Optional.empty();
        return WorldMultiblockTracker.get(level).getById(instanceId);
    }

    public Optional<AbstractMultiblockControllerBE> getController(ServerLevel level) {
        return getInstance(level).flatMap(instance -> {
            Optional<BlockPos> corePos = instance.getCorePos();
            if (corePos.isEmpty()) return Optional.empty();
            BlockEntity be = level.getBlockEntity(corePos.get());
            if (be instanceof AbstractMultiblockControllerBE ctrl) return Optional.of(ctrl);
            return Optional.empty();
        });
    }

    public void onJoinedStructure(MultiblockInstance instance) {
        this.instanceId = instance.getId();
        if (owner.getLevel() != null) {
            MultiblockRegistry.get(instance.getDefinitionId()).ifPresent(def -> {
                if (!def.hasModel()) return;
                boolean isCore = instance.getCorePos().map(owner.getBlockPos()::equals).orElse(false);
                if (!isCore) {
                    AbstractMultiblockPartBlock.setModelHidden(owner.getLevel(), owner.getBlockPos(), true);
                }
            });
        }
        if (owner instanceof IMultiblockPart part) {
            part.onJoinedStructure(instance);
        }
    }

    public void onLeftStructure() {
        this.instanceId = null;
        if (owner.getLevel() != null) {
            AbstractMultiblockPartBlock.setModelHidden(owner.getLevel(), owner.getBlockPos(), false);
        }
        if (owner instanceof IMultiblockPart part) {
            part.onLeftStructure();
        }
    }

    public void saveToTag(CompoundTag tag) {
        if (instanceId != null) {
            tag.put("multilib_instanceId", NbtUtils.createUUID(instanceId));
        }
    }

    public void loadFromTag(CompoundTag tag) {
        if (tag.contains("multilib_instanceId")) {
            this.instanceId = NbtUtils.loadUUID(tag.get("multilib_instanceId"));
        }
    }
}
