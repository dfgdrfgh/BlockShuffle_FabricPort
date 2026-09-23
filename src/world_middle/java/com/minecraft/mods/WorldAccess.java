package com.minecraft.mods;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Minecraft 1.21.6–1.21.8 expose the player's world through getWorld(). */
final class WorldAccess {
    private WorldAccess() {}

    static BlockState blockAt(ServerPlayerEntity player, BlockPos pos) {
        return player.getWorld().getBlockState(pos);
    }
}
