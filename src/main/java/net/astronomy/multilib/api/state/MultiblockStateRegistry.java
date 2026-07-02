package net.astronomy.multilib.api.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MultiblockStateRegistry {
    private static final Map<ResourceLocation, MultiblockState> STATES = new LinkedHashMap<>();
    private static volatile boolean frozen = false;

    private MultiblockStateRegistry() {}

    public static MultiblockState register(ResourceLocation id) {
        return register(id, null);
    }

    /** @param nameTranslationKey optional display name (e.g. shown in the FTB Quests state picker); null if unset. */
    public static MultiblockState register(ResourceLocation id, String nameTranslationKey) {
        if (frozen) {
            throw new IllegalStateException(
                "MultiblockState registration after freeze: " + id +
                ". Register during your mod's constructor or FMLCommonSetupEvent, not lazily at first use.");
        }
        return STATES.computeIfAbsent(id, k -> new MultiblockState(k, nameTranslationKey));
    }

    public static Collection<MultiblockState> getAll() {
        return Collections.unmodifiableCollection(STATES.values());
    }

    public static Optional<MultiblockState> get(ResourceLocation id) {
        return Optional.ofNullable(STATES.get(id));
    }

    /** Called by MultiLib during FMLLoadCompleteEvent. */
    public static void freeze() { frozen = true; }
}
