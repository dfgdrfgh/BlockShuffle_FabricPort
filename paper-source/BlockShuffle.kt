package com.minecraft.mods

import org.bukkit.plugin.java.JavaPlugin

class BlockShuffle : JavaPlugin() {

    companion object {
        lateinit var instance: BlockShuffle
    }

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        server.pluginManager.registerEvents(PlayerListener(), this)
        getCommand("blockshuffle")?.setExecutor(BlockShuffleCommand())

        logger.info("BlockShuffle enabled!")
    }

    override fun onDisable() {
        GameManager.cleanup()
        logger.info("BlockShuffle disabled!")
    }
}
