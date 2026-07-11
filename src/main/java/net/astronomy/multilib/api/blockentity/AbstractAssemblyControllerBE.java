package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyEnergyView;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.core.assembly.AssemblyCapabilities;
import net.astronomy.multilib.core.assembly.AssemblyRegistry;
import net.astronomy.multilib.core.assembly.WorldAssemblyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Convenience controller block entity for the master member of an assembly. On top of the normal
 * single-structure controller behaviour it resolves the {@link AssemblyInstance} this member belongs
 * to and exposes aggregation helpers. Placed on the {@code masterRole} member; nothing here ticks the
 * assembly by itself — call the aggregation helpers from your own {@code serverTick()}.
 */
public abstract class AbstractAssemblyControllerBE extends AbstractMultiblockControllerBE {

    protected AbstractAssemblyControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    /** The assembly this controller's own member sub-structure belongs to, if any. */
    public Optional<AssemblyInstance> getAssembly() {
        return getServerLevel().flatMap(level -> {
            UUID instanceId = getInstanceId();
            if (instanceId == null) return Optional.empty();
            return WorldAssemblyTracker.get(level).getByMember(instanceId);
        });
    }

    public Optional<AssemblyDefinition> getAssemblyDefinition() {
        return getAssembly().flatMap(a -> AssemblyRegistry.get(a.getDefinitionId()));
    }

    public boolean isInAssembly() {
        return getAssembly().isPresent();
    }

    /** Collects a capability across every assembly member's core position. Empty if not in an assembly. */
    public <T> List<T> collectMemberCapabilities(BlockCapability<T, Direction> cap, @Nullable Direction side) {
        return getServerLevel()
                .flatMap(level -> getAssembly().map(a -> AssemblyCapabilities.collect(level, a, cap, side)))
                .orElse(List.of());
    }

    /** Collects a capability across the members of one role only. */
    public <T> List<T> collectRoleCapabilities(String role, BlockCapability<T, Direction> cap, @Nullable Direction side) {
        return getServerLevel()
                .flatMap(level -> getAssembly().map(a -> AssemblyCapabilities.collectForRole(level, a, role, cap, side)))
                .orElse(List.of());
    }

    /** A read-and-distribute energy view over the whole assembly's member energy buffers. */
    public Optional<AssemblyEnergyView> assemblyEnergy() {
        List<IEnergyStorage> members = collectMemberCapabilities(Capabilities.EnergyStorage.BLOCK, null);
        if (members.isEmpty()) return Optional.empty();
        return Optional.of(new AssemblyEnergyView(members));
    }
}
