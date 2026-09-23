package com.minecraft.mods;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlockShuffle implements ModInitializer {
    public static final String ID = "blockshuffle";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> BlockShuffleCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            GameConfig.load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            GameManager.cleanup();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(GameManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, instance) ->
            GameManager.disconnect(handler.getPlayer().getUuid(), handler.getPlayer().getName().getString()));
        LOGGER.info("Block Shuffle Fabric port loaded");
    }

    public static MinecraftServer server() {
        return server;
    }
}
