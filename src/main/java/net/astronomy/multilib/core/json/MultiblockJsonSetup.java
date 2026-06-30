package net.astronomy.multilib.core.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.astronomy.multilib.api.json.MultiblockSerializers;
import net.astronomy.multilib.api.json.PatternProviderSerializer;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.astronomy.multilib.api.pattern.providers.CylinderProvider;
import net.astronomy.multilib.api.pattern.providers.HollowCubeProvider;
import net.astronomy.multilib.api.pattern.providers.HollowSphereProvider;
import net.astronomy.multilib.api.pattern.providers.PyramidProvider;
import net.astronomy.multilib.api.pattern.providers.SphereProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Optional;

public final class MultiblockJsonSetup {
    private static final MultiblockJsonLoader LOADER = new MultiblockJsonLoader();

    private MultiblockJsonSetup() {}

    public static void registerReloadListener(IEventBus gameEventBus) {
        gameEventBus.addListener(MultiblockJsonSetup::onAddReloadListeners);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(LOADER);
    }

    public static void registerBuiltInProviders() {
        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:sphere"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("radius").forGetter(p -> ((SphereProvider) p).getRadius()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((SphereProvider) p).getIngredient())
                ).apply(inst, SphereProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:cylinder"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("radius").forGetter(p -> ((CylinderProvider) p).getRadius()),
                    Codec.INT.fieldOf("height").forGetter(p -> ((CylinderProvider) p).getHeight()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((CylinderProvider) p).getIngredient())
                ).apply(inst, CylinderProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:hollow_sphere"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("radius").forGetter(p -> ((HollowSphereProvider) p).getRadius()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((HollowSphereProvider) p).getIngredient())
                ).apply(inst, HollowSphereProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:hollow_cube"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("width").forGetter(p -> ((HollowCubeProvider) p).getWidth()),
                    Codec.INT.fieldOf("height").forGetter(p -> ((HollowCubeProvider) p).getHeight()),
                    Codec.INT.fieldOf("depth").forGetter(p -> ((HollowCubeProvider) p).getDepth()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("shell").forGetter(p -> ((HollowCubeProvider) p).getShell()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.optionalFieldOf("interior").forGetter(p -> Optional.ofNullable(((HollowCubeProvider) p).getInterior()))
                ).apply(inst, (w, h, d, shell, interiorOpt) -> new HollowCubeProvider(w, h, d, shell, interiorOpt.orElse(null))));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:pyramid"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("base_size").forGetter(p -> ((PyramidProvider) p).getBaseSize()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((PyramidProvider) p).getIngredient())
                ).apply(inst, PyramidProvider::new));
            }
        });
    }
}
