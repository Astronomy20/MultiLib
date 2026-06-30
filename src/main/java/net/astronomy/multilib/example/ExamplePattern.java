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
        // the core symbol when none was set) — so placing the controller last, with the rest of the
        // pattern already in place, both triggers and forms the structure. The ghost overlay only
        // ever previews from the core (see GhostOverlayInputHandler), so this also keeps it from
        // being confused with example_simple.json's diamond/emerald blocks, which 'D'/'E' reuse here
        // purely as plain body blocks with no activation/core role.
        MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("multilib", "example"))
                .name("example_multiblock")
                .layers("PPP", " P ", " G ")
                .layers("POP", " P ", " G ")
                .key('P', BlockIngredient.of(ExampleSetup.PART_BLOCK))
                .key('O', BlockIngredient.of(ExampleSetup.CONTROLLER_BLOCK))
                .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
                .core('O')
                .model(ResourceLocation.fromNamespaceAndPath("multilib", "example")).keepVisible('G')
                .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
                .rotations(RotationMode.HORIZONTAL)
                .allowRotation(RotationAxis.values(), 90, 180, 270, -90)
                .ghostOverlayDebug()
                .onFormed(ctx -> {
                    Level level = ctx.level();
                    BlockPos origin = ctx.instance().getOrigin();

                    // Controlliamo di essere sul lato Server prima di spawnare entità
                    if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            // Posiziona il fulmine al centro del blocco controller
                            lightning.moveTo(Vec3.atBottomCenterOf(origin));
                            serverLevel.addFreshEntity(lightning);
                        }
                    }
                })
                .onBroken(ctx -> {
                    Level level = ctx.level();
                    BlockPos origin = ctx.instance().getOrigin();

                    if (!level.isClientSide()) {
                        // Crea il messaggio da inviare in chat (puoi personalizzare il testo e il colore)
                        Component messaggio = Component.literal("§cLa struttura multiblocco è stata distrutta!");

                        // Trova tutti i giocatori in un raggio di 30 blocchi dalla struttura e invia il messaggio
                        AABB area = new AABB(origin).inflate(30);
                        List<Player> players = level.getEntitiesOfClass(Player.class, area);

                        for (Player player : players) {
                            player.sendSystemMessage(messaggio);
                        }

                        // Riproduce comunque il suono del vetro rotto di default (opzionale, puoi rimuoverlo)
                        level.playSound(null, origin, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                    }
                })
                .build();
    }
}
