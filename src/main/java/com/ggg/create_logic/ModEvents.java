package com.ggg.create_logic;

import com.ggg.create_logic.metal.Metal;
import com.ggg.create_logic.network.UpdateCodeHandler;
import com.ggg.create_logic.network.UpdateCodePayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ModMain.MOD_ID)
public class ModEvents {
    private static boolean isStarted = false;
    private static boolean isStopped = false;
    public static MinecraftServer server = null;
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (isStarted) {
            isStopped = true;
            Metal.terminate();
        }
    }
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        if (isStopped) {
            isStopped = false;
            Metal.launch();
            return;
        }
        if (isStarted) return;
        isStarted = true;
        Metal.launch();
    }
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                UpdateCodePayload.TYPE,
                UpdateCodePayload.STREAM_CODEC,
                UpdateCodeHandler::handle
        );
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        Metal.tickAll();
    }
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }
}