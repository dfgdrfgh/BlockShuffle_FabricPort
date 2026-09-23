package com.minecraft.mods

import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener: Listener {

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        // Only check when player actually moves to a different block
        if (event.from.blockX == event.to.blockX && 
            event.from.blockY == event.to.blockY && 
            event.from.blockZ == event.to.blockZ) {
            return
        }

        if (GameManager.state != GameManager.GameState.RUNNING) return

        val player = event.player
        val data = GameManager.players[player.uniqueId] ?: return
        if(data.completed) return

        val blockBelow = event.to.block.getRelative(0, -1, 0).type

        if(blockBelow == data.targetBlock) {
            data.completed = true
            player.sendMessage("§a✔ You are standing on the given block!")

            player.playSound(player.location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f)

            if(GameManager.players.values.all { it.completed }) {
                GameManager.endRound()
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (GameManager.state == GameManager.GameState.RUNNING) {
            val player = event.player
            if (GameManager.players.containsKey(player.uniqueId)) {
                GameManager.handlePlayerDisconnect(player.uniqueId)
            }
        }
    }
}