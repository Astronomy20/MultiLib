package net.astronomy.multilib.compat.kubejs;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.callback.MultiblockEventContext;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Grab-bag of script-friendly helpers exposed to KubeJS as the global {@code MultiblockUtils}
 * binding (see {@link MultiLibKubeJSPlugin#registerBindings}) - the JS-facing equivalent of what
 * {@code MultiblockCodecs} is for the JSON datapack format: translate a raw Java concept a script
 * shouldn't need to touch directly (a {@code SoundEvent}/{@code EntityType} instance, e.g. - more may
 * be added later) into a plain id/value. Not tied to any one feature - new unrelated helpers belong
 * here too, not in a differently-named class per feature.
 * <p>
 * Takes {@link MultiblockEventContext} rather than one method per {@code MultiblockFormedContext}/
 * {@code MultiblockBrokenContext} - a first attempt overloaded on those two concrete types instead,
 * and Rhino's overload resolution turned out unable to disambiguate two unrelated types sharing an
 * arg count, throwing {@code EvaluatorException: ... is ambiguous} at the call site. Routing both
 * through one shared interface means there's only ever one applicable method per arg count, so
 * there's nothing left to disambiguate.
 */
public final class MultiblockUtils {
    private MultiblockUtils() {}

    /**
     * Resolves {@code blockOrTagId} (e.g. {@code "minecraft:iron_block"}, or {@code "#forge:storage_blocks/iron"}
     * for a tag) into a {@link BlockIngredient} for {@code MultiblockBuilder#key(char, BlockIngredient)}.
     * Exists purely to dodge a Rhino overload-resolution ambiguity: {@code MultiblockBuilder.key} has both
     * a {@code (char, String)} and a {@code (char, Block)} overload, and KubeJS/Rhino's implicit
     * string-to-Block coercion makes a bare string argument to {@code .key(...)} genuinely ambiguous to
     * the interpreter ({@code EvaluatorException: ... is ambiguous}) - every generated KubeJS scaffold hit
     * this. Routing the id through this named helper first means {@code .key(...)} receives an already-
     * concrete {@code BlockIngredient}, which only has one 2-arg overload accepting it (same fix already
     * applied to {@code playSound} above for a different Rhino ambiguity). A fully-qualified
     * {@code net.astronomy.multilib....BlockIngredient.parse(...)} call from the script itself was tried
     * first and doesn't work at all in KubeJS's sandboxed Rhino - unlike classic Rhino, it doesn't expose
     * bare top-level Java package names ({@code net}, {@code com}, ...) as globals, so that call threw
     * {@code ReferenceError: "net" is not defined} instead.
     */
    public static BlockIngredient block(String blockOrTagId) {
        return BlockIngredient.parse(blockOrTagId);
    }

    public static void playSound(MultiblockEventContext ctx, String soundId) {
        playSound(ctx, soundId, 1.0F, 1.0F);
    }

    public static void playSound(MultiblockEventContext ctx, String soundId, float volume, float pitch) {
        BlockPos pos = ctx.position();
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) {
            MultiLib.LOGGER.warn("[MultiLib/KubeJS] Unknown sound id '{}', skipping MultiblockUtils.playSound", soundId);
            return;
        }
        ctx.level().playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Spawns {@code entityId} (e.g. {@code "minecraft:lightning_bolt"}) at {@link MultiblockEventContext#position()}. */
    public static void summon(MultiblockEventContext ctx, String entityId) {
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.position();
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            MultiLib.LOGGER.warn("[MultiLib/KubeJS] Unknown entity id '{}', skipping MultiblockUtils.summon", entityId);
            return;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            MultiLib.LOGGER.warn("[MultiLib/KubeJS] Entity type '{}' is disabled, skipping MultiblockUtils.summon", entityId);
            return;
        }
        entity.moveTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(entity);
    }
}
