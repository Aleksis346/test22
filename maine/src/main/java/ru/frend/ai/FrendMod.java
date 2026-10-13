package ru.frend.ai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class FrendMod implements ModInitializer {
    public static final String MOD_ID = "ai_speedrun_frend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static FrendBot bot;

    @Override
    public void onInitialize() {
        LOGGER.info("AI Speedrun Frend initializing");

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FrendPersistence.save(server);
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            FrendState st = FrendState.get(srv);
            java.util.UUID uuid = sender.getUuid();
            if (st.getControlledPlayerUuid() != null && st.getControlledPlayerUuid().equals(uuid)) {
                st.setControlledPlayer(sender);
                st.setMode(FrendState.Mode.CONTROL);
            } else if (!st.isSpeedrunEnabled()) {
                sender.sendMessage(net.minecraft.text.Text.literal("§e[frend] Спидран отключён. Включить: !frend speedrun on"), false);
            } else {
                sender.sendMessage(net.minecraft.text.Text.literal("§e[frend] Выберите режим: !frend mode control или !frend mode separate"), false);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            FrendPersistence.load(server);
            // Open singleplayer world to LAN automatically.
            if (server instanceof net.minecraft.server.integrated.IntegratedServer integrated) {
                try {
                    boolean ok = integrated.publishServer(GameType.SURVIVAL, true, 0);
                    LOGGER.info("Open to LAN result: {}", ok);
                } catch (Exception e) {
                    LOGGER.warn("Could not open world to LAN automatically", e);
                }
            }
            // Spawn bot after a short delay via tick handler.
            FrendState st = FrendState.get(server);
            if (st.getMode() == FrendState.Mode.SEPARATE) {
                st.scheduleBotSpawn(40);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FrendState state = FrendState.get(server);
            state.tick(server);
        });

        // Chat command handling.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString();
            if (text == null) return;
            String lower = text.toLowerCase();
            if (!lower.contains("!frend")) return;
            if (bot == null) return;
            bot.handleChat(sender, text);
        });

        // Detect ender dragon death for speedrun timing handled by engine.
    }

    public static void setBot(FrendBot b) { bot = b; }
    public static FrendBot getBot() { return bot; }
}
