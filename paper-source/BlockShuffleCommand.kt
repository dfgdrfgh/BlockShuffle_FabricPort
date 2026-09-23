package com.minecraft.mods

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BlockShuffleCommand: CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: /bs <start|quit>")
            return true
        }

        when (args[0].lowercase()) {
            "start" -> {
                if (GameManager.state == GameManager.GameState.RUNNING) {
                    sender.sendMessage("§cGame already started!")
                    return true
                }

                GameManager.startGame()
                return true
            }
            "quit" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cOnly players can quit the game!")
                    return true
                }

                if (GameManager.state != GameManager.GameState.RUNNING) {
                    sender.sendMessage("§cNo game is currently running!")
                    return true
                }

                if (!GameManager.players.containsKey(sender.uniqueId)) {
                    sender.sendMessage("§cYou are not in the game!")
                    return true
                }

                GameManager.quitGame(sender.uniqueId)
                return true
            }
            "help" -> {
                sender.sendMessage(" ")
                sender.sendMessage("§e------ §6Block Shuffle Help §e--------")
                sender.sendMessage("")
                sender.sendMessage("§6/bs start §7- Start the Block Shuffle game")
                sender.sendMessage("§6/bs quit §7- Quit the current game")
                sender.sendMessage("§6/bs about §7- Information about the plugin")
                sender.sendMessage(" ")
                sender.sendMessage("§e-----------------------------------")
                return true
            }
            "about" -> {
                //TODO: Link to more info
                sender.sendMessage(" ")
                sender.sendMessage("§e------ §6About Block Shuffle §e---------")
                sender.sendMessage(" ")
                sender.sendMessage("§eBlock Shuffle Plugin §61.0.00 §eby §9§nMohammad Faizan§e")
                sender.sendMessage("§eA fun Minecraft minigame where players must find and stand on specific blocks!")
                sender.sendMessage(" ")
                sender.sendMessage("§e-----------------------------------")
                return true
            }
            else -> {
                sender.sendMessage("§cUsage: /bs <start|quit>")
                return true
            }
        }
    }
}