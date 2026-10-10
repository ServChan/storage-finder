package org.lts.storagefinder.macro;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.storagefinder.MinecraftScreenAccess;
import org.lts.storagefinder.ModLog;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.StorageIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AutoPeekClient implements ClientModInitializer {
    public static final String MOD_ID = "storagefinder_macro";
    public static final ModLog LOGGER = ModLog.of("Macro");
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static final long MUTE_MILLIS = 5_000L;
    private static final double MUTE_RADIUS_SQUARED = 1.6 * 1.6;
    private static final double INTERACTION_MARGIN = 0.25;
    private static final Vec3[] HIT_OFFSETS = {
            new Vec3(0.5, 0.5, 0.5),
            new Vec3(0.5, 0.85, 0.5),
            new Vec3(0.5, 0.15, 0.5),
            new Vec3(0.15, 0.5, 0.5),
            new Vec3(0.85, 0.5, 0.5),
            new Vec3(0.5, 0.5, 0.15),
            new Vec3(0.5, 0.5, 0.85)
    };

    private static final Map<BlockPos, Long> retryAfter = new HashMap<>();
    private static final List<Muted> muted = new ArrayList<>();
    private static KeyMapping toggleKey;
    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;
    private static BlockPos target;
    private static AbstractContainerMenu hidden;
    private static boolean recorded;
    private static ClientLevel level;

    @Override
    public void onInitializeClient() {
        AutoPeekConfig.load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.storagefinder_macro.toggle",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F8,
                KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(AutoPeekClient::onTick);
        LOGGER.info("Macro edition enabled; auto-peek toggles with its own key");
    }

    private static void onTick(Minecraft minecraft) {
        AutoPeekConfig config = AutoPeekConfig.current();
        while (toggleKey.consumeClick()) {
            config.enabled = !config.enabled;
            AutoPeekConfig.save();
            if (!config.enabled) {
                closeHidden(minecraft);
            }
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable(
                        config.enabled ? "storagefinder_macro.enabled" : "storagefinder_macro.disabled"));
            }
        }
        if (minecraft.level != level) {
            hidden = null;
            target = null;
            phase = Phase.IDLE;
            retryAfter.clear();
            muted.clear();
            level = minecraft.level;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        muted.removeIf(entry -> entry.until() < now);
        switch (phase) {
            case IDLE -> {
                if (config.enabled && canAct(minecraft, player, config)) {
                    tryPeek(minecraft, player, config, now);
                }
            }
            case WAITING -> {
                if (++phaseTicks >= config.menuTimeoutTicks) {
                    retryAfter.put(target, now + config.failedRetrySeconds * 1_000L);
                    startCooldown();
                }
            }
            case OPEN -> tickOpen(minecraft, player, config, now);
            case COOLDOWN -> {
                if (++phaseTicks >= config.cooldownTicks) {
                    phase = Phase.IDLE;
                    phaseTicks = 0;
                }
            }
        }
    }

    private static boolean canAct(Minecraft minecraft, LocalPlayer player, AutoPeekConfig config) {
        if (MinecraftScreenAccess.getScreen(minecraft) != null || minecraft.gameMode == null
                || player.containerMenu != player.inventoryMenu || player.isSpectator()
                || player.isShiftKeyDown() || player.isSecondaryUseActive() || player.isUsingItem()
                || player.isSleeping() || player.isDeadOrDying()) {
            return false;
        }
        return !config.pauseWhileUsingKeys
                || !minecraft.options.keyUse.isDown() && !minecraft.options.keyAttack.isDown();
    }

    private static void tryPeek(Minecraft minecraft, LocalPlayer player, AutoPeekConfig config, long now) {
        double reach = Math.min(config.reach, player.blockInteractionRange() - INTERACTION_MARGIN);
        double reachSquared = reach * reach;
        Vec3 eyes = player.getEyePosition();
        BlockPos origin = BlockPos.containing(eyes);
        int radius = (int) Math.ceil(reach);
        long rescanMillis = config.rescanSeconds * 1_000L;
        BlockPos bestCanonical = null;
        BlockHitResult bestHit = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (Vec3.atCenterOf(cursor).distanceToSqr(eyes) > (reach + 1.0) * (reach + 1.0)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (!peekable(state.getBlock(), config)) {
                continue;
            }
            BlockPos physical = cursor.immutable();
            BlockPos canonical = StorageIndex.canonicalPos(minecraft, physical);
            Long retry = retryAfter.get(canonical);
            if (retry != null && retry > now) {
                continue;
            }
            long indexed = StorageFinderClient.lastIndexedAt(minecraft, canonical);
            if (indexed > 0L && now - indexed < rescanMillis) {
                continue;
            }
            BlockHitResult hit = visibleHit(player, eyes, physical, reachSquared);
            if (hit == null) {
                continue;
            }
            double distance = eyes.distanceToSqr(hit.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCanonical = canonical;
                bestHit = hit;
            }
        }
        if (bestHit == null) {
            return;
        }
        target = bestCanonical;
        retryAfter.put(target, now + rescanMillis);
        for (BlockPos physical : StorageIndex.physicalBlocks(minecraft, target)) {
            muted.add(new Muted(Vec3.atCenterOf(physical), now + MUTE_MILLIS));
        }
        phase = Phase.WAITING;
        phaseTicks = 0;
        recorded = false;
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bestHit);
    }

    private static boolean peekable(Block block, AutoPeekConfig config) {
        if (block instanceof ChestBlock) {
            return !(config.skipTrappedChests && block instanceof TrappedChestBlock);
        }
        return block instanceof BarrelBlock || block instanceof ShulkerBoxBlock;
    }

    private static BlockHitResult visibleHit(LocalPlayer player, Vec3 eyes, BlockPos physical, double reachSquared) {
        for (Vec3 offset : HIT_OFFSETS) {
            Vec3 destination = Vec3.atLowerCornerOf(physical).add(offset);
            if (eyes.distanceToSqr(destination) > reachSquared) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(
                    eyes, destination, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(physical)
                    && eyes.distanceToSqr(hit.getLocation()) <= reachSquared) {
                return hit;
            }
        }
        return null;
    }

    private static void tickOpen(Minecraft minecraft, LocalPlayer player, AutoPeekConfig config, long now) {
        if (player.containerMenu != hidden) {
            hidden = null;
            startCooldown();
            return;
        }
        if (MinecraftScreenAccess.getScreen(minecraft) != null) {
            recordIfReady(minecraft, now, config);
            closeHidden(minecraft);
            return;
        }
        recordIfReady(minecraft, now, config);
        phaseTicks++;
        if (recorded && phaseTicks >= config.holdTicks || phaseTicks >= config.menuTimeoutTicks) {
            closeHidden(minecraft);
        }
    }

    private static void recordIfReady(Minecraft minecraft, long now, AutoPeekConfig config) {
        if (recorded || hidden == null || hidden.getStateId() == 0) {
            return;
        }
        recorded = true;
        if (!StorageFinderClient.recordWithoutScreen(minecraft, target, hidden)) {
            retryAfter.put(target, now + config.failedRetrySeconds * 1_000L);
        }
    }

    private static void closeHidden(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        AbstractContainerMenu menu = hidden;
        hidden = null;
        if (player != null && menu != null && player.containerMenu == menu) {
            player.connection.send(new ServerboundContainerClosePacket(menu.containerId));
            player.containerMenu = player.inventoryMenu;
        }
        if (phase == Phase.OPEN || phase == Phase.WAITING) {
            startCooldown();
        }
    }

    private static void startCooldown() {
        phase = Phase.COOLDOWN;
        phaseTicks = 0;
    }

    public static boolean interceptMenu(MenuType<?> type, Minecraft minecraft, int containerId) {
        LocalPlayer player = minecraft.player;
        if (phase != Phase.WAITING || player == null) {
            return false;
        }
        if (MinecraftScreenAccess.getScreen(minecraft) != null) {
            player.connection.send(new ServerboundContainerClosePacket(containerId));
            retryAfter.remove(target);
            startCooldown();
            return true;
        }
        AbstractContainerMenu menu = type.create(containerId, player.getInventory());
        player.containerMenu = menu;
        hidden = menu;
        phase = Phase.OPEN;
        phaseTicks = 0;
        return true;
    }

    public static boolean interceptServerClose(Minecraft minecraft, int containerId) {
        LocalPlayer player = minecraft.player;
        if (hidden == null || player == null || hidden.containerId != containerId || player.containerMenu != hidden) {
            return false;
        }
        recordIfReady(minecraft, System.currentTimeMillis(), AutoPeekConfig.current());
        player.containerMenu = player.inventoryMenu;
        hidden = null;
        startCooldown();
        return true;
    }

    public static boolean shouldMute(Holder<SoundEvent> sound, double x, double y, double z) {
        if (muted.isEmpty() || !AutoPeekConfig.current().muteSounds) {
            return false;
        }
        String path = sound.value().location().getPath();
        if (!(path.endsWith(".open") || path.endsWith(".close"))
                || !(path.contains("chest") || path.contains("barrel") || path.contains("shulker_box"))) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Muted entry : muted) {
            if (entry.until() >= now && entry.center().distanceToSqr(x, y, z) <= MUTE_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private enum Phase {
        IDLE,
        WAITING,
        OPEN,
        COOLDOWN
    }

    private record Muted(Vec3 center, long until) {
    }
}
