package com.minecraft.mods;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Maps the player's world accessor for releases with getEntityWorld(). */
final class WorldAccess {
    private WorldAccess() {}

    static BlockState blockAt(ServerPlayerEntity player, BlockPos pos) {
        return player.getEntityWorld().getBlockState(pos);
    }
}
