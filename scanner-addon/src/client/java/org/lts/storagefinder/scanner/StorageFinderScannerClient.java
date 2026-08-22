package org.lts.storagefinder.scanner;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refreshes Storage Finder through the same interactions a player can perform manually.
 * It never reads block-entity inventory data or sends custom inventory packets.
 */
public final class StorageFinderScannerClient implements ClientModInitializer {
    public static final String MOD_ID = "storagefinder_scanner";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static final double MAX_REACH = 4.5;
    private static final double MAX_REACH_SQUARED = MAX_REACH * MAX_REACH;
    private static final double MAX_MOVEMENT_SQUARED = 0.25 * 0.25;
    private static final int ROTATE_TICKS = 2;
    private static final int MENU_TIMEOUT_TICKS = 30;
    private static final int READ_TICKS = 4;
    private static final int COOLDOWN_TICKS = 8;
    private static final Vec3[] HIT_OFFSETS = {
            new Vec3(0.5, 0.5, 0.5),
            new Vec3(0.5, 0.85, 0.5),
            new Vec3(0.5, 0.2, 0.5)
    };

    private static KeyMapping toggleKey;
    private static ScanSession session;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.storagefinder_scanner.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(StorageFinderScannerClient::onTick);
        LOGGER.info("Storage Finder Scanner initialized");
    }

    private static void onTick(Minecraft minecraft) {
        while (toggleKey.consumeClick()) {
            if (session == null) {
                start(minecraft);
            } else {
                stop(minecraft, "storagefinder_scanner.stopped");
            }
        }

        if (session != null) {
            session.tick(minecraft);
        }
    }

    private static void start(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (ScreenAccess.get(minecraft) != null || player.isShiftKeyDown()) {
            overlay(player, "storagefinder_scanner.blocked");
            return;
        }

        List<Target> targets = collectTargets(level, player);
        if (targets.isEmpty()) {
            overlay(player, "storagefinder_scanner.none");
            return;
        }

        session = new ScanSession(level, player.position(), targets);
        overlay(player, "storagefinder_scanner.started", targets.size());
    }

    private static List<Target> collectTargets(ClientLevel level, LocalPlayer player) {
        BlockPos origin = player.blockPosition();
        Vec3 eyes = player.getEyePosition();
        int radius = (int) Math.ceil(MAX_REACH);
        Set<BlockPos> canonicalPositions = new HashSet<>();
        List<Target> targets = new ArrayList<>();

        for (BlockPos mutable : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            BlockPos pos = mutable.immutable();
            BlockState state = level.getBlockState(pos);
            if (!isStorage(state)) {
                continue;
            }

            BlockPos canonical = canonicalPos(level, pos, state);
            if (!canonicalPositions.add(canonical)) {
                continue;
            }

            Target target = visiblePhysicalTarget(level, player, canonical);
            if (target != null && eyes.distanceToSqr(target.hit().getLocation()) <= MAX_REACH_SQUARED) {
                targets.add(target);
            }
        }

        targets.sort(Comparator.comparingDouble(target -> eyes.distanceToSqr(target.hit().getLocation())));
        return List.copyOf(targets);
    }

    private static Target visiblePhysicalTarget(ClientLevel level, LocalPlayer player, BlockPos canonical) {
        BlockState state = level.getBlockState(canonical);
        Target direct = visibleHit(level, player, canonical, canonical);
        if (direct != null) {
            return direct;
        }
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(canonical, state);
            return visibleHit(level, player, canonical, other);
        }
        return null;
    }

    private static Target visibleHit(ClientLevel level, LocalPlayer player, BlockPos canonical, BlockPos physical) {
        Vec3 eyes = player.getEyePosition();
        for (Vec3 offset : HIT_OFFSETS) {
            Vec3 destination = Vec3.atLowerCornerOf(physical).add(offset);
            if (eyes.distanceToSqr(destination) > MAX_REACH_SQUARED) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(
                    eyes, destination, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(physical)) {
                return new Target(canonical, hit);
            }
        }
        return null;
    }

    private static boolean isStorage(BlockState state) {
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static BlockPos canonicalPos(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            if (compare(other, pos) < 0 && level.getBlockState(other).getBlock() instanceof ChestBlock) {
                return other.immutable();
            }
        }
        return pos.immutable();
    }

    private static int compare(BlockPos first, BlockPos second) {
        int y = Integer.compare(first.getY(), second.getY());
        if (y != 0) return y;
        int z = Integer.compare(first.getZ(), second.getZ());
        return z != 0 ? z : Integer.compare(first.getX(), second.getX());
    }

    private static void stop(Minecraft minecraft, String translationKey) {
        ScanSession active = session;
        session = null;
        if (active != null && minecraft.player != null) {
            Screen screen = ScreenAccess.get(minecraft);
            if (screen instanceof AbstractContainerScreen<?>) {
                minecraft.player.closeContainer();
            }
            overlay(minecraft.player, translationKey, active.updated, active.targets.size());
        }
    }

    private static void finish(Minecraft minecraft, ScanSession active) {
        session = null;
        if (minecraft.player != null) {
            overlay(minecraft.player, "storagefinder_scanner.finished", active.updated, active.targets.size());
            if (active.failed > 0) {
                overlay(minecraft.player, "storagefinder_scanner.failed", active.failed);
            }
        }
    }

    private static void overlay(LocalPlayer player, String key, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args));
    }

    private enum Phase {
        ROTATING,
        WAITING_FOR_MENU,
        READING,
        COOLDOWN
    }

    private record Target(BlockPos canonical, BlockHitResult hit) {
    }

    private static final class ScanSession {
        private final ClientLevel level;
        private final Vec3 origin;
        private final List<Target> targets;
        private int index;
        private int phaseTicks;
        private int updated;
        private int failed;
        private Phase phase = Phase.ROTATING;

        private ScanSession(ClientLevel level, Vec3 origin, List<Target> targets) {
            this.level = level;
            this.origin = origin;
            this.targets = targets;
        }

        private void tick(Minecraft minecraft) {
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level != level) {
                stop(minecraft, "storagefinder_scanner.dimension");
                return;
            }
            if (player.position().distanceToSqr(origin) > MAX_MOVEMENT_SQUARED) {
                stop(minecraft, "storagefinder_scanner.moved");
                return;
            }

            Screen screen = ScreenAccess.get(minecraft);
            if (phase == Phase.COOLDOWN && screen instanceof AbstractContainerScreen<?>) {
                // A heavily delayed server response can arrive just after the timeout.
                // Treat it as the current target instead of interacting with another container.
                phase = Phase.READING;
                phaseTicks = 0;
                return;
            }
            if (phase == Phase.ROTATING && screen != null) {
                stop(minecraft, "storagefinder_scanner.stopped");
                return;
            }

            Target target = targets.get(index);
            switch (phase) {
                case ROTATING -> rotateAndPrepare(player, target);
                case WAITING_FOR_MENU -> waitForMenu(minecraft);
                case READING -> readAndClose(minecraft);
                case COOLDOWN -> coolDown(minecraft);
            }
        }

        private void rotateAndPrepare(LocalPlayer player, Target target) {
            lookAt(player, target.hit().getLocation());
            phaseTicks++;
            if (phaseTicks < ROTATE_TICKS) {
                return;
            }
            phaseTicks = 0;
            Minecraft minecraft = Minecraft.getInstance();
            Target refreshed = visiblePhysicalTarget(level, player, target.canonical());
            if (refreshed == null || minecraft.gameMode == null || player.isShiftKeyDown()) {
                failed++;
                beginCooldown();
                return;
            }
            minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, refreshed.hit());
            phase = Phase.WAITING_FOR_MENU;
        }

        private void waitForMenu(Minecraft minecraft) {
            Screen screen = ScreenAccess.get(minecraft);
            if (screen instanceof AbstractContainerScreen<?>) {
                phase = Phase.READING;
                phaseTicks = 0;
                return;
            }
            if (screen != null) {
                stop(minecraft, "storagefinder_scanner.stopped");
                return;
            }
            if (++phaseTicks >= MENU_TIMEOUT_TICKS) {
                failed++;
                beginCooldown();
            }
        }

        private void readAndClose(Minecraft minecraft) {
            Screen screen = ScreenAccess.get(minecraft);
            if (!(screen instanceof AbstractContainerScreen<?>)) {
                failed++;
                beginCooldown();
                return;
            }
            if (++phaseTicks >= READ_TICKS) {
                minecraft.player.closeContainer();
                updated++;
                beginCooldown();
            }
        }

        private void coolDown(Minecraft minecraft) {
            if (++phaseTicks < COOLDOWN_TICKS) {
                return;
            }
            index++;
            if (index >= targets.size()) {
                finish(minecraft, this);
                return;
            }
            phase = Phase.ROTATING;
            phaseTicks = 0;
        }

        private void beginCooldown() {
            phase = Phase.COOLDOWN;
            phaseTicks = 0;
        }

        private static void lookAt(LocalPlayer player, Vec3 target) {
            Vec3 eyes = player.getEyePosition();
            double dx = target.x - eyes.x;
            double dy = target.y - eyes.y;
            double dz = target.z - eyes.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setXRot(pitch);
        }
    }
}
