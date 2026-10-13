package ru.frend.ai;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class FrendBot extends FakePlayer {
    private final MinecraftServer server;

    public FrendBot(ServerWorld world, GameProfile profile, MinecraftServer server) {
        super(world, profile);
        this.server = server;
        this.networkHandler = new net.fabricmc.fabric.impl.event.interaction.FakePlayerNetworkHandler(this);
    }

    public void spawnAt(double x, double y, double z) {
        this.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
    }

    public void handleChat(net.minecraft.server.network.ServerPlayerEntity sender, String text) {
        String cmd = text.trim().toLowerCase();
        if (!cmd.contains("!frend")) return;
        String after = text.substring(text.toLowerCase().indexOf("!frend") + "!frend".length()).trim();

        if (after.isEmpty() || after.equals("help") || after.equals("РїРѕРјРѕС‰СЊ")) {
            say("РљРѕРјР°РЅРґС‹: !frend come | !frend say <С‚РµРєСЃС‚> | !frend status | !frend stop | !frend start");
            say("Р РµР¶РёРјС‹: !frend mode control (СѓРїСЂР°РІР»СЏС‚СЊ РёРіСЂРѕРєРѕРј) | !frend mode separate (РѕС‚РґРµР»СЊРЅС‹Р№ Р±РѕС‚).");
            return;
        }
        if (after.startsWith("come") || after.startsWith("РёРґРё") || after.startsWith("СЃСЋРґР°")) {
            this.teleportTo(sender.getWorld(), sender.getX(), sender.getY(), sender.getZ(), sender.getYaw(), sender.getPitch());
            say("РРґСѓ Рє С‚РµР±Рµ.");
            return;
        }
        if (after.startsWith("say ")) {
            say(after.substring(4));
            return;
        }
        if (after.startsWith("mode ")) {
            String m = after.substring(5).trim().toLowerCase();
            FrendState st = FrendState.get(server);
            if (m.equals("control") || m.equals("РєРѕРЅС‚СЂРѕР»СЊ")) {
                st.setMode(FrendState.Mode.CONTROL);
                st.setControlledPlayer(sender);
                st.cancelBotSpawn();
                say("Р РµР¶РёРј: СѓРїСЂР°РІР»СЏСЋ С‚РѕР±РѕР№ (" + sender.getName().getString() + ").");
                return;
            }
            if (m.equals("separate") || m.equals("РѕС‚РґРµР»СЊРЅРѕ")) {
                st.setMode(FrendState.Mode.SEPARATE);
                st.setControlledPlayer(null);
                if (st.getBot() == null) st.scheduleBotSpawn(40);
                say("Р РµР¶РёРј: РѕС‚РґРµР»СЊРЅС‹Р№ Р±РѕС‚ frend.");
                return;
            }
        }
        if (after.startsWith("speedrun ")) {
            String sub = after.substring(8).trim().toLowerCase();
            FrendState st2 = FrendState.get(server);
            if (sub.equals("on") || sub.equals("enable") || sub.equals("вкл") || sub.equals("включить")) {
                st2.setSpeedrunEnabled(true);
                if (st2.getMode() == FrendState.Mode.SEPARATE && st2.getBot() == null) st2.scheduleBotSpawn(40);
                say("Спидран включён.");
                return;
            }
            if (sub.equals("off") || sub.equals("disable") || sub.equals("выкл") || sub.equals("выключить")) {
                st2.setSpeedrunEnabled(false);
                say("Спидран отключён.");
                return;
            }
        }
        if (after.equals("status")) {
            FrendState st = FrendState.get(server);
            say("Состояние: спидран завершён=" + st.isSpeedrunDone() + "; помощь=" + st.isHelping() + "; speedrun=" + st.isSpeedrunEnabled());
            return;
        }
        }
        if (after.equals("stop")) {
            FrendState.get(server).setHelping(false);
            say("РћСЃС‚Р°РЅРѕРІРёР»СЃСЏ.");
            return;
        }
        if (after.equals("start")) {
            FrendState.get(server).setHelping(true);
            say("РЎРЅРѕРІР° РїРѕРјРѕРіР°СЋ.");
            return;
        }
        say("РќРµ РїРѕРЅСЏР» РєРѕРјР°РЅРґСѓ. РќР°РїРёС€Рё !frend help");
    }

    public void helpTick(MinecraftServer server) {
        // Simple follow: teleport closer to nearest player occasionally.
        net.minecraft.server.PlayerManager pm = server.getPlayerManager();
        net.minecraft.server.network.ServerPlayerEntity nearest = null;
        double best = 1024;
        for (net.minecraft.server.network.ServerPlayerEntity p : pm.getPlayerList()) {
            if (p == this) continue;
            double d = p.squaredDistanceTo(this.getX(), this.getY(), this.getZ());
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest != null && best > 4 && best < 4096) {
            this.teleportTo(nearest.getWorld(), nearest.getX() + 1.5, nearest.getY(), nearest.getZ() + 1.5, nearest.getYaw(), nearest.getPitch());
        }
    }

    public void setHelping(boolean v) { FrendState.get(server).setHelping(v); }

    public void say(String msg) {
        server.getPlayerManager().broadcast(
            net.minecraft.text.Text.literal("В§b[frend] " + msg), false);
    }
}