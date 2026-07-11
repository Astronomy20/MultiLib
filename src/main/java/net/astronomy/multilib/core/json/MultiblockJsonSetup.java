package net.astronomy.multilib.core.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.astronomy.multilib.api.json.MultiblockSerializers;
import net.astronomy.multilib.api.json.PatternProviderSerializer;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.astronomy.multilib.api.pattern.providers.CompositeProvider;
import net.astronomy.multilib.api.pattern.providers.ConeProvider;
import net.astronomy.multilib.api.pattern.providers.CylinderProvider;
import net.astronomy.multilib.api.pattern.providers.DomeProvider;
import net.astronomy.multilib.api.pattern.providers.HollowCubeProvider;
import net.astronomy.multilib.api.pattern.providers.HollowDomeProvider;
import net.astronomy.multilib.api.pattern.providers.HollowSphereProvider;
import net.astronomy.multilib.api.pattern.providers.PrismProvider;
import net.astronomy.multilib.api.pattern.providers.PyramidProvider;
import net.astronomy.multilib.api.pattern.providers.RingProvider;
import net.astronomy.multilib.api.pattern.providers.SphereProvider;
import net.astronomy.multilib.api.pattern.providers.TorusProvider;
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

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:cone"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("base_radius").forGetter(p -> ((ConeProvider) p).getBaseRadius()),
                    Codec.INT.fieldOf("height").forGetter(p -> ((ConeProvider) p).getHeight()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((ConeProvider) p).getIngredient())
                ).apply(inst, ConeProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:dome"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("radius").forGetter(p -> ((DomeProvider) p).getRadius()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((DomeProvider) p).getIngredient())
                ).apply(inst, DomeProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:hollow_dome"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("radius").forGetter(p -> ((HollowDomeProvider) p).getRadius()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((HollowDomeProvider) p).getIngredient())
                ).apply(inst, HollowDomeProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:ring"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("outer_radius").forGetter(p -> ((RingProvider) p).getOuterRadius()),
                    Codec.INT.optionalFieldOf("inner_radius", 0).forGetter(p -> ((RingProvider) p).getInnerRadius()),
                    Codec.INT.optionalFieldOf("height", 1).forGetter(p -> ((RingProvider) p).getHeight()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((RingProvider) p).getIngredient())
                ).apply(inst, RingProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:torus"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("major_radius").forGetter(p -> ((TorusProvider) p).getMajorRadius()),
                    Codec.INT.fieldOf("minor_radius").forGetter(p -> ((TorusProvider) p).getMinorRadius()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((TorusProvider) p).getIngredient())
                ).apply(inst, TorusProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:prism"; }
            @Override public Codec<? extends PatternProvider> codec() {
                return RecordCodecBuilder.create(inst -> inst.group(
                    Codec.INT.fieldOf("sides").forGetter(p -> ((PrismProvider) p).getSides()),
                    Codec.INT.fieldOf("radius").forGetter(p -> ((PrismProvider) p).getRadius()),
                    Codec.INT.optionalFieldOf("height", 1).forGetter(p -> ((PrismProvider) p).getHeight()),
                    MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.fieldOf("ingredient").forGetter(p -> ((PrismProvider) p).getIngredient())
                ).apply(inst, PrismProvider::new));
            }
        });

        MultiblockSerializers.registerProvider(new PatternProviderSerializer() {
            @Override public String getType() { return "multilib:composite"; }
            @Override public Codec<? extends PatternProvider> codec() {
                Codec<CompositeProvider.Op> opCodec = Codec.STRING.comapFlatMap(
                    s -> switch (s.toLowerCase()) {
                        case "union" -> com.mojang.serialization.DataResult.success(CompositeProvider.Op.UNION);
                        case "subtract" -> com.mojang.serialization.DataResult.success(CompositeProvider.Op.SUBTRACT);
                        case "intersect" -> com.mojang.serialization.DataResult.success(CompositeProvider.Op.INTERSECT);
                        default -> com.mojang.serialization.DataResult.error(() -> "Unknown composite op: " + s);
                    },
                    op -> op.name().toLowerCase());
                Codec<CompositeProvider.Operation> operationCodec = RecordCodecBuilder.create(i -> i.group(
                    opCodec.fieldOf("op").forGetter(CompositeProvider.Operation::op),
                    MultiblockCodecs.PATTERN_PROVIDER.fieldOf("provider").forGetter(CompositeProvider.Operation::provider),
                    Codec.INT.optionalFieldOf("dx", 0).forGetter(CompositeProvider.Operation::dx),
                    Codec.INT.optionalFieldOf("dy", 0).forGetter(CompositeProvider.Operation::dy),
                    Codec.INT.optionalFieldOf("dz", 0).forGetter(CompositeProvider.Operation::dz)
                ).apply(i, CompositeProvider.Operation::new));
                return RecordCodecBuilder.create(inst -> inst.group(
                    operationCodec.listOf().fieldOf("operations").forGetter(p -> ((CompositeProvider) p).getOperations())
                ).apply(inst, CompositeProvider::of));
            }
        });
    }
}
