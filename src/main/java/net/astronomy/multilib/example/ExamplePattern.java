package net.astronomy.multilib.example;

import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationAxis;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class ExamplePattern {
    public static void registerAll() {
        // 'O' (multilib:example_controller) is the core. .core('O') with no separate .activation(..)
        // call makes 'O' the activation symbol too (MultiblockBuilder.core() defaults activation to
        // the core symbol when none was set) - so placing the controller last, with the rest of the
        // pattern already in place, both triggers and forms the structure. The ghost overlay only
        // ever previews from the core (see GhostOverlayInputHandler), so this also keeps it from
        // being confused with example_simple.json's diamond/emerald blocks, which 'D'/'E' reuse here
        // purely as plain body blocks with no activation/core role.
        MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("multilib", "example"))
                .name("example_multiblock")
                .layer("PPP", " P ", " G ")
                .layer("POP", " P ", " G ")
                .key('P', BlockIngredient.of(ExampleSetup.PART_BLOCK))
                .key('O', BlockIngredient.of(ExampleSetup.CONTROLLER_BLOCK))
                .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
                .core('O')
                .model(ResourceLocation.fromNamespaceAndPath("multilib", "example")).keepVisible('G')
                .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
                .rotations(RotationMode.HORIZONTAL)
                .allowRotation(RotationAxis.values(), 90, 180, 270, -90)
                .ghostOverlayDebug()
                .autoPlace().autoPlaceOverlay()
                .onFormed(ctx -> {
                    Level level = ctx.level();
                    BlockPos origin = ctx.instance().getOrigin();

                    // Check to be on server side before spawn entity
                    if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            // Summon lightning on core block coords
                            lightning.moveTo(Vec3.atBottomCenterOf(origin));
                            serverLevel.addFreshEntity(lightning);
                        }
                    }
                })
                .onBroken(ctx -> {
                    Level level = ctx.level();
                    BlockPos origin = ctx.instance().getOrigin();

                    if (!level.isClientSide()) {
                        // Message to send in chat
                        Component messaggio = Component.literal("§cLa struttura multiblocco è stata distrutta!");

                        // Find player in 30 blocks radius
                        AABB area = new AABB(origin).inflate(30);
                        List<Player> players = level.getEntitiesOfClass(Player.class, area);

                        for (Player player : players) {
                            player.sendSystemMessage(messaggio);
                        }

                        // Also plays the broken glass sound
                        level.playSound(null, origin, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                    }
                })
                .build();
    }
}
