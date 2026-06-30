package net.astronomy.multilib.core.json;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.json.MultiblockSerializers;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class MultiblockCodecs {

    public static final Codec<BlockIngredient> BLOCK_INGREDIENT_OBJECT = new Codec<>() {
        @Override
        public <T> DataResult<Pair<BlockIngredient, T>> decode(DynamicOps<T> ops, T input) {
            MapLike<T> map = ops.getMap(input).result().orElse(null);
            if (map == null) return DataResult.error(() -> "Expected an object for BlockIngredient");

            // any: true
            T anyVal = map.get("any");
            if (anyVal != null && ops.getBooleanValue(anyVal).result().orElse(false)) {
                return DataResult.success(Pair.of(BlockIngredient.any(), ops.empty()));
            }

            // tag
            T tagVal = map.get("tag");
            if (tagVal != null) {
                return ops.getStringValue(tagVal).flatMap(tagStr -> {
                    ResourceLocation tagId = ResourceLocation.tryParse(tagStr);
                    if (tagId == null) return DataResult.error(() -> "Invalid tag: " + tagStr);
                    TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                    return DataResult.success(Pair.of(BlockIngredient.tag(tagKey), ops.empty()));
                });
            }

            // any_of
            T anyOfVal = map.get("any_of");
            if (anyOfVal != null) {
                return ops.getList(anyOfVal).flatMap(stream -> {
                    List<BlockIngredient> ings = new ArrayList<>();
                    List<DataResult<BlockIngredient>> results = new ArrayList<>();
                    stream.accept(elem -> results.add(
                        BLOCK_INGREDIENT_OBJECT.decode(ops, elem).map(Pair::getFirst)
                    ));
                    for (DataResult<BlockIngredient> r : results) {
                        if (r.isError()) return DataResult.error(r.error().get()::message);
                        ings.add(r.getOrThrow());
                    }
                    return DataResult.success(Pair.of(
                        BlockIngredient.anyOf(ings.toArray(new BlockIngredient[0])), ops.empty()));
                });
            }

            // block (with optional properties)
            T blockVal = map.get("block");
            if (blockVal != null) {
                return ops.getStringValue(blockVal).flatMap(blockStr -> {
                    ResourceLocation blockId = ResourceLocation.tryParse(blockStr);
                    if (blockId == null) return DataResult.error(() -> "Invalid block: " + blockStr);
                    Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
                    if (block == null) {
                        return DataResult.error(() -> "Unknown block: " + blockStr);
                    }

                    T propsVal = map.get("properties");
                    if (propsVal != null) {
                        MapLike<T> propsMap = ops.getMap(propsVal).result().orElse(null);
                        if (propsMap == null) return DataResult.error(() -> "properties must be an object");
                        var builder = BlockIngredient.ofState(block);
                        propsMap.entries().forEach(entry ->
                            MultiLib.LOGGER.warn("[MultiLib] JSON property parsing for StatePropertyIngredient not fully implemented")
                        );
                        return DataResult.success(Pair.of(builder.build(), ops.empty()));
                    }
                    return DataResult.success(Pair.of(BlockIngredient.of(block), ops.empty()));
                });
            }

            // custom type
            T typeVal = map.get("type");
            if (typeVal != null) {
                return ops.getStringValue(typeVal).flatMap(typeStr ->
                    MultiblockSerializers.getIngredient(typeStr)
                        .map(ser -> ser.codec().decode(ops, input).map(p -> Pair.of((BlockIngredient) p.getFirst(), p.getSecond())))
                        .orElse(DataResult.error(() -> "Unknown BlockIngredient type: " + typeStr))
                );
            }

            return DataResult.error(() -> "Could not determine BlockIngredient type");
        }

        @Override
        public <T> DataResult<T> encode(BlockIngredient input, DynamicOps<T> ops, T prefix) {
            return DataResult.error(() -> "BlockIngredient encoding not implemented");
        }
    };

    public static final Codec<BlockIngredient> BLOCK_INGREDIENT = Codec.lazyInitialized(() ->
        Codec.withAlternative(
            BLOCK_INGREDIENT_OBJECT,
            ResourceLocation.CODEC.comapFlatMap(
                id -> {
                    Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
                    if (block == null) return DataResult.error(() -> "Unknown block: " + id);
                    return DataResult.success(BlockIngredient.of(block));
                },
                ing -> {
                    var candidates = ing.getCandidateBlocks();
                    if (candidates.isEmpty()) return ResourceLocation.parse("minecraft:air");
                    return BuiltInRegistries.BLOCK.getKey(candidates.iterator().next());
                }
            )
        )
    );

    public static final Codec<RotationMode> ROTATION_MODE = Codec.STRING.comapFlatMap(
        s -> switch (s.toLowerCase()) {
            case "none" -> DataResult.success(RotationMode.NONE);
            case "horizontal" -> DataResult.success(RotationMode.HORIZONTAL);
            case "all" -> DataResult.success(RotationMode.ALL);
            default -> DataResult.error(() -> "Unknown rotation mode: " + s);
        },
        rm -> rm.name().toLowerCase()
    );

    public static final Codec<FormationMode> FORMATION_MODE = Codec.STRING.comapFlatMap(
        s -> {
            // "manual" is a legacy alias for "wrench" (the old MANUAL constant's semantics).
            String id = s.equalsIgnoreCase("manual") ? "wrench" : s.toLowerCase();
            FormationMode mode = FormationMode.byId(id);
            return mode != null ? DataResult.success(mode) : DataResult.error(() -> "Unknown formation mode: " + s);
        },
        FormationMode::getId
    );

    @SuppressWarnings("unchecked")
    public static final Codec<PatternProvider> PATTERN_PROVIDER = Codec.lazyInitialized(() ->
        Codec.STRING.dispatch(
            "type",
            p -> "unknown",
            type -> MultiblockSerializers.getProvider(type)
                .<MapCodec<? extends PatternProvider>>map(
                    ser -> ((Codec<PatternProvider>) (Codec<?>) ser.codec()).fieldOf("value"))
                .orElseGet(() -> MapCodec.unit(() -> { throw new IllegalStateException("Unknown provider type: " + type); }))
        )
    );

    private MultiblockCodecs() {}
}
