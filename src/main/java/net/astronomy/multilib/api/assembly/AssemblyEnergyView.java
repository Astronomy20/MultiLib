package net.astronomy.multilib.api.assembly;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/**
 * Read-and-distribute aggregation of the energy buffers of an assembly's members. Not a buffer of
 * its own: {@link #getEnergyStored()} and {@link #getMaxEnergyStored()} sum the members, and
 * {@link #receiveEnergy}/{@link #extractEnergy} fan the operation out across them in order. Build one
 * from {@link net.astronomy.multilib.core.assembly.AssemblyCapabilities#collect} over
 * {@code Capabilities.EnergyStorage.BLOCK}.
 */
public final class AssemblyEnergyView implements IEnergyStorage {
    private final List<IEnergyStorage> members;

    public AssemblyEnergyView(List<IEnergyStorage> members) {
        this.members = members;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int total = 0;
        for (IEnergyStorage m : members) {
            if (total >= toReceive) break;
            total += m.receiveEnergy(toReceive - total, simulate);
        }
        return total;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int total = 0;
        for (IEnergyStorage m : members) {
            if (total >= toExtract) break;
            total += m.extractEnergy(toExtract - total, simulate);
        }
        return total;
    }

    @Override
    public int getEnergyStored() {
        long sum = 0;
        for (IEnergyStorage m : members) sum += m.getEnergyStored();
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    @Override
    public int getMaxEnergyStored() {
        long sum = 0;
        for (IEnergyStorage m : members) sum += m.getMaxEnergyStored();
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    @Override
    public boolean canExtract() {
        for (IEnergyStorage m : members) if (m.canExtract()) return true;
        return false;
    }

    @Override
    public boolean canReceive() {
        for (IEnergyStorage m : members) if (m.canReceive()) return true;
        return false;
    }
}
