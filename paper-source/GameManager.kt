package com.minecraft.mods

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.scheduler.BukkitTask
import java.util.*

object GameManager {

    enum class GameState {
        WAITING,
        RUNNING,
        ENDED
    }

    data class PlayerData(
        val uuid: UUID,
        var targetBlock: Material,
        var completed: Boolean = false
    )

    var state = GameState.WAITING
    var round = 1
    var players = mutableMapOf<UUID, PlayerData>()
    private var currentTimer: BukkitTask? = null
    private var actionBarTask: BukkitTask? = null
    private var bossBar: BossBar? = null
    private var roundTimeTotal: Int = 300

    private var halfTimeAnnounced = false
    private var lastSecondAnnounced = -1

    private val config = BlockShuffle.instance.config

    fun startGame() {
        val minPlayers = config.getInt("min-players", 1)
        
        if (Bukkit.getOnlinePlayers().size < minPlayers) {
            Bukkit.broadcastMessage("§cNot enough players to start Block Shuffle. Minimum required: $minPlayers")
            state = GameState.ENDED
            return
        }

        state = GameState.RUNNING
        round = 1
        players.clear()

        Bukkit.broadcastMessage("§a\uD83D\uDFE2 Block Shuffle started!")

        for (player in Bukkit.getOnlinePlayers()) {
            players[player.uniqueId] = PlayerData(
                player.uniqueId,
                getWeightedBlock()
            )
            player.sendMessage("§eYour block: §6${players[player.uniqueId]!!.targetBlock}")
            // beacon activate sound at the start of game
            player.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
        }
        startTimer()
        startActionBar()
    }

    fun startTimer() {
        currentTimer?.cancel()
        removeBossBar()
        halfTimeAnnounced = false
        lastSecondAnnounced = -1
        
        roundTimeTotal = config.getInt("round-time", 300)
        var timeLeft = roundTimeTotal

        bossBar = Bukkit.createBossBar(
            "§eRound $round - Time Left: §6${formatTime(timeLeft)}",
            BarColor.GREEN,
            BarStyle.SOLID
        )

        players.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.let { player ->
                bossBar?.addPlayer(player)
            }
        }
        
        bossBar?.progress = 1.0
        
        currentTimer = Bukkit.getScheduler().runTaskTimer(BlockShuffle.instance, Runnable {
            if (state != GameState.RUNNING) {
                currentTimer?.cancel()
                removeBossBar()
                return@Runnable
            }
            checkActivePlayers()

            // Halftime announcement
            if (!halfTimeAnnounced && timeLeft <= roundTimeTotal / 2) {
                Bukkit.broadcastMessage("§e\u23F1 Half time! §6${formatTime(timeLeft)} §eremaining.")
                // Play sound to all players
                players.keys.forEach { uuid ->
                    Bukkit.getPlayer(uuid)?.playSound(
                        Bukkit.getPlayer(uuid)?.location ?: return@forEach,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        1.0f,
                        1.0f
                    )
                }
                halfTimeAnnounced = true
            }

            // last 10 seconds countdown
            if (timeLeft in 1..10 && timeLeft != lastSecondAnnounced) {
                lastSecondAnnounced = timeLeft

                players.values.filter { !it.completed }
                    .forEach { data ->
                        val player = Bukkit.getPlayer(data.uuid) ?: return@forEach
                        player.sendActionBar(Component.text("§c⚠ §e$timeLeft §c⚠"))
                        val pitch = 0.5f + (11 - timeLeft) * 0.1f // 0.5f to 1.5f
                        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, pitch)
                    }
            } else if (timeLeft > 10 && lastSecondAnnounced in 1..10) {
                lastSecondAnnounced = -1
            }

            if (timeLeft <= 0) {
                currentTimer?.cancel()
                currentTimer = null
                removeBossBar()
                endRound()
                return@Runnable
            }

            val progress = timeLeft.toDouble() / roundTimeTotal.toDouble()
            bossBar?.progress = progress.coerceIn(0.0, 1.0)
            bossBar?.setTitle("§eRound $round - Time Left: §6${formatTime(timeLeft)}")

            when {
                timeLeft <= 30 -> bossBar?.color = BarColor.RED
                timeLeft <= 60 -> bossBar?.color = BarColor.YELLOW
                else -> bossBar?.color = BarColor.GREEN
            }
            
            timeLeft--
        }, 0L, 20L)
    }

    private  fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }

    private fun startActionBar() {
        // Cancel existing action bar task if any
        actionBarTask?.cancel()
        
        actionBarTask = Bukkit.getScheduler().runTaskTimer(
            BlockShuffle.instance,
            Runnable {
                if (state != GameState.RUNNING) {
                    // Clear action bar for all players when game ends
                    players.keys.forEach { uuid ->
                        Bukkit.getPlayer(uuid)?.sendActionBar(Component.empty())
                    }
                    actionBarTask?.cancel()
                    actionBarTask = null
                    return@Runnable
                }

                // Skip action bar update during countdown (last 10 seconds)
                // The countdown timer handles action bar updates during that time
                if (lastSecondAnnounced in 1..10) {
                    return@Runnable
                }

                players.forEach { (uuid, data) ->
                    val player = Bukkit.getPlayer(uuid) ?: return@forEach
                    
                    // Don't show action bar if player completed
                    if (data.completed) {
                        player.sendActionBar(Component.text("§a✔ Completed!"))
                    } else {
                        val blockName = data.targetBlock.name.replace("_", " ").lowercase()
                            .split(" ").joinToString(" ") { it.capitalize() }
                        player.sendActionBar(
                            Component.text("§eFind: §6$blockName")
                        )
                    }
                }
            },
            0L,
            20L
        )
    }
    
    private fun stopActionBar() {
        actionBarTask?.cancel()
        actionBarTask = null

        players.keys.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.sendActionBar(Component.empty())
        }
    }
    
    private fun removeBossBar() {
        bossBar?.removeAll()
        bossBar = null
    }

    private fun updateBossBarPlayers() {
        bossBar?.let { bar ->
            bar.players.forEach { player ->
                if (!players.containsKey(player.uniqueId)) {
                    bar.removePlayer(player)
                }
            }
            players.keys.forEach { uuid ->
                Bukkit.getPlayer(uuid)?.let { player ->
                    if (!bar.players.contains(player)) {
                        bar.addPlayer(player)
                    }
                }
            }
        }
    }

    private fun checkActivePlayers() {
        val offlinePlayers = players.keys.filter { Bukkit.getPlayer(it) == null }
        offlinePlayers.forEach { uUID ->
            players.remove(uUID)
        }
        updateBossBarPlayers()

        if (players.isEmpty() && state == GameState.RUNNING) {
            Bukkit.broadcastMessage("§cGame ended - all players have left the game.")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
        }
    }

    fun endRound() {
        val failed = players.values.filter { !it.completed }

        failed.forEach {
            val p = Bukkit.getPlayer(it.uuid)
            p?.sendMessage("§cYou failed this round")
            players.remove(it.uuid)
        }

        if (players.isEmpty()) {
            Bukkit.broadcastMessage("§cAll players failed! Game ended.")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
            return
        }

        // SOLO
        if (players.size == 1) {
            val player = players.values.first()
            if(player.completed) {
                nextRound()
            } else {
                Bukkit.getPlayer(player.uuid)?.sendMessage("You failed! Try again.")
                state = GameState.ENDED
                currentTimer?.cancel()
                currentTimer = null
                removeBossBar()
                stopActionBar()
            }
            return
        }

        nextRound()
    }

    fun nextRound() {
        round++
        Bukkit.broadcastMessage("§e\uD83D\uDD01 Round $round!")

        players.values.forEach {
            it.completed = false
            it.targetBlock = getWeightedBlock()
            val player = Bukkit.getPlayer(it.uuid)
            player?.sendMessage("§aNew block: §6 ${it.targetBlock}")
            player?.playSound(player.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f)
        }

        startTimer()
    }

    fun endGame() {
        val winner = players.values.firstOrNull() ?: return
        val player = Bukkit.getPlayer(winner.uuid) ?: return

        Bukkit.broadcastMessage("§6${player.name} wins Block Shuffle!")
        state = GameState.ENDED
        currentTimer?.cancel()
        currentTimer = null
        removeBossBar()
        stopActionBar()
    }

    fun quitGame(playerUuid: UUID) {
        if (state != GameState.RUNNING) return

        val player = Bukkit.getPlayer(playerUuid) ?: return
        val playerData = players.remove(playerUuid) ?: return
        
        bossBar?.removePlayer(player)

        Bukkit.broadcastMessage("§e${player.name} has left this Block Shuffle game.")

        if (players.isEmpty()) {
            Bukkit.broadcastMessage("§cGame ended - no players remaining.")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
            return
        }

        // In solo mode, end the game
        if (players.size == 1) {
            val remainingPlayer = Bukkit.getPlayer(players.values.first().uuid)
            remainingPlayer?.sendMessage("§aYou are the last player remaining!")
            Bukkit.broadcastMessage("§a${remainingPlayer?.name} is the last player remaining!")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
            return
        }

        // Multiple players still in game - continue
        Bukkit.broadcastMessage("§e${player.name} has quit the game. ${players.size} players remaining.")
    }

    fun handlePlayerDisconnect(playerUuid: UUID) {
        if (state != GameState.RUNNING) return

        val playerData = players.remove(playerUuid) ?: return
        val playerName = Bukkit.getOfflinePlayer(playerUuid).name ?: "Unknown"

        updateBossBarPlayers()

        Bukkit.broadcastMessage("§e$playerName has disconnected from the game.")

        // If no players left, end the game
        if (players.isEmpty()) {
            Bukkit.broadcastMessage("§cGame ended - all players have left.")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
            return
        }

        // In solo mode, end the game
        if (players.size == 1) {
            val remainingPlayer = Bukkit.getPlayer(players.values.first().uuid)
            remainingPlayer?.sendMessage("§aYou are the last player remaining!")
            Bukkit.broadcastMessage("§a${remainingPlayer?.name} is the last player remaining!")
            state = GameState.ENDED
            currentTimer?.cancel()
            currentTimer = null
            removeBossBar()
            stopActionBar()
            return
        }

        Bukkit.broadcastMessage("§e$playerName has disconnected. ${players.size} players remaining.")
    }

    fun cleanup() {
        currentTimer?.cancel()
        currentTimer = null
        state = GameState.ENDED
        round = 1
        players.clear()
        removeBossBar()
        stopActionBar()
    }

    private fun isAllowed(material: Material) : Boolean {
        val config = BlockShuffle.instance.config

        if (config.getStringList("blacklist").contains(material.name)) return false

        val allowSection = config.getConfigurationSection("allow") ?: return true
        val allowNether = allowSection.getBoolean("nether", true)
        val allowEnd = allowSection.getBoolean("end", false)

        if (!allowNether && material.name.contains("NETHER", ignoreCase = true)) return false

        if (!allowEnd && material.name.contains("END", ignoreCase = true) && !material.name.contains("END_ROD")) return false

        if (!material.isBlock || !material.isSolid) return false

        if (material == Material.AIR ||
            material == Material.WATER ||
            material == Material.LAVA ||
            material.name.endsWith("_AIR") ||
            material.name.contains("WATER") ||
            material.name.contains("LAVA")) return false
        return true
    }

    private fun getBlockWeight(material: Material): Int {
        val config = BlockShuffle.instance.config
        val categoryWeights = config.getConfigurationSection("weight-categories") ?: return 5 // default weight
        
        val name = material.name.uppercase()

        val individualWeights = config.getConfigurationSection("weights")
        if (individualWeights != null && individualWeights.contains(material.name)) {
            return individualWeights.getInt(material.name, 5)
        }

        if (name == "STONE" || name == "COBBLESTONE" || name == "DIRT" || name == "GRASS_BLOCK") {
            return categoryWeights.getInt("common-blocks", 10)
        }

        if (name.contains("EMERALD") || name.contains("ANCIENT_DEBRIS") || name.contains("NETHERITE")) {
            return categoryWeights.getInt("ultra-rare", 1)
        }

        if (name.contains("DIAMOND") || name.contains("LAPIS") || name.contains("REDSTONE_ORE")) {
            return categoryWeights.getInt("rare-ores", 2)
        }

        if (name.contains("IRON_ORE") || name.contains("COPPER_ORE") || name.contains("GOLD_ORE")) {
            return categoryWeights.getInt("uncommon-ores", 4)
        }

        if (name.contains("COAL_ORE")) {
            return categoryWeights.getInt("common-ores", 6)
        }

        if (name.contains("NETHER") && !name.contains("NETHERITE")) {
            return categoryWeights.getInt("nether-blocks", 5)
        }

        if (name.contains("END") && !name.contains("END_ROD")) {
            return categoryWeights.getInt("end-blocks", 3)
        }

        if (name.contains("GLASS") || name.contains("STAINED") || name.contains("WOOL") || name.contains("CARPET")) {
            return categoryWeights.getInt("decorative", 7)
        }

        if (name.contains("STONE") || name.contains("BRICK") || name.contains("CONCRETE") || 
            name.contains("TERRACOTTA") || name.contains("LOG") || name.contains("PLANK") ||
            name.contains("WOOD") || name.contains("SAND") || name.contains("GRAVEL") || 
            name.contains("CLAY") || (name.contains("DIRT")) ||
            (name.contains("GRASS"))) {
            return categoryWeights.getInt("building-blocks", 8)
        }

        return categoryWeights.getInt("default", 5)
    }
    
    private fun getWeightedBlock(): Material {
        val config = BlockShuffle.instance.config
        val reduction = config.getDouble("difficulty.weight-reduction-per-round", 0.15)

        val allAllowedBlocks = Material.entries.filter { isAllowed(it) }
        
        if (allAllowedBlocks.isEmpty()) {
            BlockShuffle.instance.logger.warning("No allowed blocks found! Using STONE as fallback")
            return Material.STONE
        }

        val weightedList = mutableListOf<Material>()

        for (material in allAllowedBlocks) {
            val baseWeight = getBlockWeight(material)
            val scaledWeight = (baseWeight - (round - 1) * reduction).toInt().coerceAtLeast(1)
            
            if (scaledWeight > 0) {
                repeat(scaledWeight) {
                    weightedList.add(material)
                }
            }
        }

        if (weightedList.isEmpty()) {
            BlockShuffle.instance.logger.warning("No valid weighted blocks found! Using first allowed block")
            return allAllowedBlocks.firstOrNull() ?: Material.STONE
        }

        return weightedList.random()
    }

}