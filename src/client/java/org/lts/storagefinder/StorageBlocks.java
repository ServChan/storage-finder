package org.lts.storagefinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class StorageBlocks {
    public static final String CHEST = "chest";
    public static final String BARREL = "barrel";
    public static final String ENDER_CHEST = "ender_chest";
    public static final String ENDER_CONTENTS = "ender_contents";
    private static final String SHULKER_PREFIX = "shulker:";

    private StorageBlocks() {
    }

    public static String family(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ChestBlock) {
            return CHEST;
        }
        if (block instanceof BarrelBlock) {
            return BARREL;
        }
        if (block instanceof EnderChestBlock) {
            return ENDER_CHEST;
        }
        if (block instanceof ShulkerBoxBlock) {
            return SHULKER_PREFIX + BuiltInRegistries.BLOCK.getKey(block);
        }
        return null;
    }

    public static boolean isStorage(BlockState state) {
        return family(state) != null;
    }

    public static boolean isEnder(String family) {
        return ENDER_CHEST.equals(family);
    }

    public static boolean matchesRecord(String recordFamily, String currentFamily) {
        return currentFamily != null && (recordFamily == null || recordFamily.equals(currentFamily));
    }

    public static boolean isConfirmedGone(ClientLevel level, BlockPos pos, BlockState state) {
        if (isStorage(state)) {
            return true;
        }
        if (state.isAir()) {
            return true;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            cursor.setWithOffset(pos, direction);
            if (!level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) {
                continue;
            }
            if (!level.getBlockState(cursor).isSolidRender()) {
                return true;
            }
        }
        return false;
    }
}
