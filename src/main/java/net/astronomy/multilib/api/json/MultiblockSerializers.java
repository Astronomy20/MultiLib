package net.astronomy.multilib.api.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MultiblockSerializers {
    private static final Map<String, BlockIngredientSerializer> INGREDIENT_SERIALIZERS = new LinkedHashMap<>();
    private static final Map<String, PatternProviderSerializer> PROVIDER_SERIALIZERS = new LinkedHashMap<>();

    private MultiblockSerializers() {}

    public static void registerIngredient(BlockIngredientSerializer serializer) {
        INGREDIENT_SERIALIZERS.put(serializer.getType(), serializer);
    }

    public static void registerProvider(PatternProviderSerializer serializer) {
        PROVIDER_SERIALIZERS.put(serializer.getType(), serializer);
    }

    public static Optional<BlockIngredientSerializer> getIngredient(String type) {
        return Optional.ofNullable(INGREDIENT_SERIALIZERS.get(type));
    }

    public static Optional<PatternProviderSerializer> getProvider(String type) {
        return Optional.ofNullable(PROVIDER_SERIALIZERS.get(type));
    }

    public static Map<String, BlockIngredientSerializer> getAllIngredientSerializers() {
        return Collections.unmodifiableMap(INGREDIENT_SERIALIZERS);
    }

    public static Map<String, PatternProviderSerializer> getAllProviderSerializers() {
        return Collections.unmodifiableMap(PROVIDER_SERIALIZERS);
    }
}
