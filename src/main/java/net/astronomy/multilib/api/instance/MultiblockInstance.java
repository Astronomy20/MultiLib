package net.astronomy.multilib.api.instance;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.matching.MatchData;
import net.astronomy.multilib.core.matching.TransformData;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MultiblockInstance {
    private final UUID id;
    private final ResourceLocation definitionId;
    private final BlockPos origin;
    private final TransformData transform;
    private final Set<BlockPos> positions;
    private final Map<Character, Set<BlockPos>> symbolPositions;

    public MultiblockInstance(UUID id, ResourceLocation definitionId, BlockPos origin,
                              TransformData transform, MatchData matchData) {
        this.id = id;
        this.definitionId = definitionId;
        this.origin = origin;
        this.transform = transform;
        this.positions = Set.copyOf(matchData.positions());
        this.symbolPositions = matchData.symbolPositions().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Set.copyOf(e.getValue())
                ));
    }

    public UUID getId() { return id; }
    public ResourceLocation getDefinitionId() { return definitionId; }
    public BlockPos getOrigin() { return origin; }
    public TransformData getTransform() { return transform; }
    public boolean contains(BlockPos pos) { return positions.contains(pos); }
    public Set<BlockPos> getPositions() { return positions; }
    public Set<BlockPos> getPositionsFor(char symbol) { return symbolPositions.getOrDefault(symbol, Set.of()); }

    public Optional<BlockPos> getCorePos() {
        return MultiblockRegistry.get(definitionId)
                .filter(MultiblockDefinition::hasCore)
                .map(def -> {
                    Set<BlockPos> corePositions = symbolPositions.getOrDefault(def.getCoreSymbol(), Set.of());
                    return corePositions.isEmpty() ? null : corePositions.iterator().next();
                });
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("id", NbtUtils.createUUID(id));
        tag.putString("definition", definitionId.toString());
        tag.putLong("origin", origin.asLong());
        tag.putInt("rotation", transform.rotation());
        tag.putBoolean("vertical", transform.vertical());
        tag.putString("axis", transform.axis());

        tag.putLongArray("positions", positions.stream().mapToLong(BlockPos::asLong).toArray());

        CompoundTag symbolTag = new CompoundTag();
        symbolPositions.forEach((symbol, posSet) ->
                symbolTag.putLongArray(String.valueOf(symbol),
                        posSet.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put("symbolPositions", symbolTag);

        return tag;
    }

    public static MultiblockInstance load(CompoundTag tag) {
        if (!tag.contains("id", Tag.TAG_INT_ARRAY)) return null;
        if (!tag.contains("definition", Tag.TAG_STRING)) return null;

        UUID id;
        try {
            id = NbtUtils.loadUUID(tag.get("id"));
        } catch (Exception e) {
            MultiLib.LOGGER.warn("[MultiLib] Failed to load MultiblockInstance UUID", e);
            return null;
        }

        String defStr = tag.getString("definition");
        ResourceLocation definitionId = ResourceLocation.tryParse(defStr);
        if (definitionId == null) return null;

        if (MultiblockRegistry.get(definitionId).isEmpty()) return null;

        BlockPos origin = BlockPos.of(tag.getLong("origin"));
        int rotation = tag.getInt("rotation");
        boolean vertical = tag.getBoolean("vertical");
        String axis = tag.getString("axis");
        TransformData transform = new TransformData(rotation, vertical, axis);

        long[] posArray = tag.getLongArray("positions");
        Set<BlockPos> positions = new HashSet<>();
        for (long l : posArray) positions.add(BlockPos.of(l));

        CompoundTag symbolTag = tag.getCompound("symbolPositions");
        Map<Character, Set<BlockPos>> symbolPositions = new HashMap<>();
        for (String key : symbolTag.getAllKeys()) {
            if (key.isEmpty()) continue;
            char symbol = key.charAt(0);
            long[] symPosArray = symbolTag.getLongArray(key);
            Set<BlockPos> symPosSet = new HashSet<>();
            for (long l : symPosArray) symPosSet.add(BlockPos.of(l));
            symbolPositions.put(symbol, Collections.unmodifiableSet(symPosSet));
        }

        MatchData matchData = new MatchData(
                origin,
                transform,
                Collections.unmodifiableSet(positions),
                symbolPositions.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
        );

        return new MultiblockInstance(id, definitionId, origin, transform, matchData);
    }
}
