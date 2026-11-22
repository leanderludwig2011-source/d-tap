package de.onedtap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Demonstrations-/Prototypmod: Einmal R drücken, um eine D-Tap-Abfolge zu versuchen.
 * Keine Anti-Cheat-Umgehung und keine Erfolgsgarantie.
 * Reagiert nur, wenn das Fadenkreuz auf einen Spieler zeigt (4.5 Blöcke).
 */
public final class OneKeyDTapClient implements ClientModInitializer {
    private static final int SECOND_CRYSTAL_DELAY_TICKS = 5; // ca. 500 ms; serverabhängig
    private static final int MAX_ENTITY_WAIT_TICKS = 12;
    private static final int MAX_BLOCK_WAIT_TICKS = 8;
    private static final double MAX_REACH_SQUARED = 4.5 * 4.5;
    private static final Set<Item> SWORDS = Set.of(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
            Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
    );

    private enum Phase {
        IDLE, PLACE_OBSIDIAN, WAIT_OBSIDIAN,
        PLACE_FIRST_CRYSTAL, WAIT_FIRST_CRYSTAL,
        WAIT_DAMAGE_WINDOW, PLACE_SECOND_CRYSTAL, WAIT_SECOND_CRYSTAL
    }

    private static KeyMapping trigger;
    private static Phase phase = Phase.IDLE;
    private static BlockPos obsidian;
    private static int previousSlot = -1;
    private static int currentTicks = 0;
    private static int firstCrystalEntityId = -1;

    @Override
    public void onInitializeClient() {
        trigger = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.onedtap.trigger", InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_R, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(OneKeyDTapClient::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            phase = Phase.IDLE;
            previousSlot = -1;
            return;
        }
        while (trigger.consumeClick()) {
            if (phase != Phase.IDLE) {
                finish(mc, "Abgebrochen");
            } else if (mc.screen == null) {
                begin(mc);
            }
        }
        if (phase == Phase.IDLE) return;
        if (mc.screen != null || mc.player.isDeadOrDying()) {
            finish(mc, "Abbruch: Menü geöffnet oder Spieler tot");
            return;
        }
        currentTicks++;
        switch (phase) {
            case PLACE_OBSIDIAN -> placeObsidian(mc);
            case WAIT_OBSIDIAN -> {
                if (mc.level.getBlockState(obsidian).is(Blocks.OBSIDIAN)) {
                    setPhase(Phase.PLACE_FIRST_CRYSTAL);
                } else if (currentTicks > MAX_BLOCK_WAIT_TICKS) {
                    finish(mc, "Obsidian konnte nicht platziert werden");
                }
            }
            case PLACE_FIRST_CRYSTAL -> placeCrystal(mc, true);
            case WAIT_FIRST_CRYSTAL -> awaitCrystal(mc, true);
            case WAIT_DAMAGE_WINDOW -> {
                if (currentTicks >= SECOND_CRYSTAL_DELAY_TICKS) {
                    setPhase(Phase.PLACE_SECOND_CRYSTAL);
                }
            }
            case PLACE_SECOND_CRYSTAL -> placeCrystal(mc, false);
            case WAIT_SECOND_CRYSTAL -> awaitCrystal(mc, false);
            default -> { }
        }
    }

    private static void begin(Minecraft mc) {
        // Kein stillschweigendes Auto-Targeting oder Angreifen durch Wände:
        // Ziel muss sich unter dem Fadenkreuz befinden.
        if (!(mc.hitResult instanceof EntityHitResult result)
                || !(result.getEntity() instanceof Player target)
                || result.getEntity() == mc.player) {
            say(mc, "Bitte einen Spieler direkt anvisieren");
            return;
        }
        LocalPlayer self = mc.player;
        if (self.distanceToSqr(target) > MAX_REACH_SQUARED) {
            say(mc, "Ziel außerhalb der Reichweite");
            return;
        }
        int sword = findHotbarItem(self, SWORDS);
        int crystal = findHotbarItem(self, Set.of(Items.END_CRYSTAL));
        if (sword < 0 || crystal < 0) {
            say(mc, "Du brauchst Schwert und End Crystals in der Hotbar");
            return;
        }
        obsidian = chooseBase(mc, target);
        if (obsidian == null) {
            say(mc, "Kein geeigneter Platz für Obsidian / Crystal");
            return;
        }
        if (!mc.level.getBlockState(obsidian).is(Blocks.OBSIDIAN)
                && findHotbarItem(self, Set.of(Items.OBSIDIAN)) < 0) {
            say(mc, "Du brauchst Obsidian in der Hotbar");
            return;
        }
        previousSlot = self.getInventory().getSelectedSlot();
        firstCrystalEntityId = -1;
        self.getInventory().setSelectedSlot(sword);
        mc.gameMode.attack(self, target);
        self.swing(InteractionHand.MAIN_HAND);
        setPhase(Phase.PLACE_OBSIDIAN);
        say(mc, "D-Tap versucht … R = abbrechen");
    }

    private static BlockPos chooseBase(Minecraft mc, Player target) {
        // Bestehender Obsidianblock unter dem Ziel ist am verlässlichsten.
        BlockPos standingOn = target.blockPosition().below();
        if (mc.level.getBlockState(standingOn).is(Blocks.OBSIDIAN)
                && inReach(mc, standingOn)) return standingOn;

        // Vorhersage: in Richtung des Angriffs ca. 1–2 Blöcke voraus.
        Vec3 direction = target.position().subtract(mc.player.position());
        direction = new Vec3(direction.x, 0, direction.z);
        if (direction.lengthSqr() < 0.001) return null;
        direction = direction.normalize();
        int feetY = target.blockPosition().getY();
        for (double ahead : new double[] {1.2, 1.8, 0.7}) {
            Vec3 predicted = target.position().add(direction.scale(ahead));
            BlockPos candidate = new BlockPos((int) Math.floor(predicted.x), feetY,
                    (int) Math.floor(predicted.z));
            if (inReach(mc, candidate)
                    && mc.level.getBlockState(candidate).isAir()
                    && mc.level.getBlockState(candidate.above()).isAir()
                    && !mc.level.getBlockState(candidate.below()).isAir()) {
                return candidate;
            }
            if (mc.level.getBlockState(candidate).is(Blocks.OBSIDIAN)
                    && inReach(mc, candidate)) return candidate;
        }
        return null;
    }

    private static boolean inReach(Minecraft mc, BlockPos pos) {
        return mc.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_REACH_SQUARED;
    }

    private static void placeObsidian(Minecraft mc) {
        if (mc.level.getBlockState(obsidian).is(Blocks.OBSIDIAN)) {
            setPhase(Phase.PLACE_FIRST_CRYSTAL);
            return;
        }
        int slot = findHotbarItem(mc.player, Set.of(Items.OBSIDIAN));
        if (slot == -1) {
            finish(mc, "Kein Obsidian mehr vorhanden");
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        BlockPos support = obsidian.below();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, topFace(support));
        mc.player.swing(InteractionHand.MAIN_HAND);
        setPhase(Phase.WAIT_OBSIDIAN);
    }

    private static void placeCrystal(Minecraft mc, boolean first) {
        if (!mc.level.getBlockState(obsidian).is(Blocks.OBSIDIAN)) {
            finish(mc, "Obsidian fehlt");
            return;
        }
        int slot = findHotbarItem(mc.player, Set.of(Items.END_CRYSTAL));
        if (slot == -1) {
            finish(mc, "Keine End Crystals mehr in der Hotbar");
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, topFace(obsidian));
        mc.player.swing(InteractionHand.MAIN_HAND);
        setPhase(first ? Phase.WAIT_FIRST_CRYSTAL : Phase.WAIT_SECOND_CRYSTAL);
    }

    private static void awaitCrystal(Minecraft mc, boolean first) {
        EndCrystal found = findNewCrystal(mc);
        if (found != null) {
            // Explosion nur auslösen, wenn das Crystal tatsächlich gespawnt wurde.
            int sword = findHotbarItem(mc.player, SWORDS);
            if (sword >= 0) mc.player.getInventory().setSelectedSlot(sword);
            mc.gameMode.attack(mc.player, found);
            mc.player.swing(InteractionHand.MAIN_HAND);
            if (first) {
                firstCrystalEntityId = found.getId();
                setPhase(Phase.WAIT_DAMAGE_WINDOW);
            } else {
                finish(mc, "Zweites Crystal angegriffen – D-Tap abgeschlossen");
            }
        } else if (currentTicks > MAX_ENTITY_WAIT_TICKS) {
            finish(mc, "Crystal nicht gespawnt: Reichweite / Sicht / Server prüfen");
        }
    }

    private static EndCrystal findNewCrystal(Minecraft mc) {
        // Ein Crystal hat seinen Mittelpunkt oberhalb der Obsidian-Basis.
        AABB search = new AABB(obsidian.above()).inflate(0.35, 0.4, 0.35);
        for (EndCrystal c : mc.level.getEntitiesOfClass(EndCrystal.class, search)) {
            if (c.getId() != firstCrystalEntityId && c.isAlive()
                    && mc.player.distanceToSqr(c) <= MAX_REACH_SQUARED) return c;
        }
        return null;
    }

    private static BlockHitResult topFace(BlockPos block) {
        return new BlockHitResult(
                new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5),
                Direction.UP, block, false);
    }

    private static int findHotbarItem(LocalPlayer player, Set<Item> candidates) {
        for (int slot = 0; slot < 9; slot++) {
            if (candidates.contains(player.getInventory().getItem(slot).getItem())) return slot;
        }
        return -1;
    }

    private static void setPhase(Phase next) {
        phase = next;
        currentTicks = 0;
    }

    private static void finish(Minecraft mc, String message) {
        if (mc.player != null && previousSlot >= 0 && previousSlot < 9) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        obsidian = null;
        firstCrystalEntityId = -1;
        setPhase(Phase.IDLE);
        say(mc, message);
    }

    private static void say(Minecraft mc, String msg) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[OneKeyDTap] " + msg), true);
        }
    }
}
