package ru.frend.ai;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class FrendState {
    private static FrendState instance;

    public enum Mode { SEPARATE, CONTROL }

    private Mode mode = Mode.SEPARATE;
    private boolean speedrunEnabled = true;
    private java.util.UUID controlledPlayerUuid;
    private ServerPlayerEntity controlledPlayer;
    private FrendBot bot;
    private SpeedrunEngine engine;
    private long speedrunStartMs = 0;
    private long speedrunEndMs = 0;
    private boolean speedrunDone = false;
    private boolean helping = false;
    private int spawnTicksRemaining = -1;

    // Restored/saved engine values.
    private SpeedrunEngine.Phase savedPhase = SpeedrunEngine.Phase.START;
    private int savedLogs = 0;
    private int savedCobble = 0;
    private BlockPos savedStronghold = null;
    private BlockPos savedPosition = null;

    private FrendState() {}

    public static FrendState get(MinecraftServer server) {
        if (instance == null) instance = new FrendState();
        return instance;
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode m) { this.mode = m; }
    public boolean isSpeedrunEnabled() { return speedrunEnabled; }
    public void setSpeedrunEnabled(boolean v) {
        this.speedrunEnabled = v;
        if (!v) {
            this.engine = null;
            this.spawnTicksRemaining = -1;
            this.helping = false;
        }
    }
    public java.util.UUID getControlledPlayerUuid() { return controlledPlayerUuid; }
    public void setControlledPlayerUuid(java.util.UUID uuid) { this.controlledPlayerUuid = uuid; }
    public void setControlledPlayer(ServerPlayerEntity p) {
        this.controlledPlayer = p;
        if (p != null) this.controlledPlayerUuid = p.getUuid();
    }
    public ServerPlayerEntity getControlledPlayer() { return controlledPlayer; }
    public boolean isControlMode() { return mode == Mode.CONTROL; }

    public ServerPlayerEntity getActor(MinecraftServer server) {
        if (mode == Mode.CONTROL && controlledPlayer != null && !controlledPlayer.isRemoved()) return controlledPlayer;
        return bot;
    }

    public FrendBot getBot() { return bot; }
    public void setBot(FrendBot b) { this.bot = b; }
    public SpeedrunEngine getEngine() { return engine; }

    public void scheduleBotSpawn(int ticks) { this.spawnTicksRemaining = ticks; }
    public void cancelBotSpawn() { this.spawnTicksRemaining = -1; }

    public long getSpeedrunStartMs() { return speedrunStartMs; }
    public long getSpeedrunEndMs() { return speedrunEndMs; }
    public boolean isSpeedrunDone() { return speedrunDone; }
    public boolean isHelping() { return helping; }
    public void setHelping(boolean v) { this.helping = v; }

    public SpeedrunEngine.Phase getSavedPhase() { return savedPhase; }
    public void setSavedPhase(SpeedrunEngine.Phase p) { this.savedPhase = p; }
    public int getSavedLogs() { return savedLogs; }
    public void setSavedLogs(int v) { this.savedLogs = v; }
    public int getSavedCobble() { return savedCobble; }
    public void setSavedCobble(int v) { this.savedCobble = v; }
    public BlockPos getSavedStronghold() { return savedStronghold; }
    public void setSavedStronghold(BlockPos p) { this.savedStronghold = p; }
    public BlockPos getSavedPosition() { return savedPosition; }
    public void setSavedPosition(BlockPos p) { this.savedPosition = p; }
    public void setSpeedrunStartMs(long v) { this.speedrunStartMs = v; }
    public void setSpeedrunEndMs(long v) { this.speedrunEndMs = v; }
    public void setSpeedrunDone(boolean v) { this.speedrunDone = v; }

    public void storeEngine(SpeedrunEngine e) {
        if (e == null) return;
        this.savedPhase = e.getPhase();
        this.savedLogs = e.getLogsCollected();
        this.savedCobble = e.getCobbleCollected();
        this.savedStronghold = e.getStronghold();
        ServerPlayerEntity a = getActor(null);
        if (a != null) this.savedPosition = a.getBlockPos();
    }

    public void restoreEngine(SpeedrunEngine e) {
        if (e == null) return;
        e.setPhase(savedPhase);
        e.setLogsCollected(savedLogs);
        e.setCobbleCollected(savedCobble);
        e.setStronghold(savedStronghold);
        if (savedPosition != null) {
            try {
                ServerPlayerEntity a = getActor(null);
                if (a != null) a.teleportTo((ServerWorld) a.getWorld(), savedPosition.getX() + 0.5, savedPosition.getY(), savedPosition.getZ() + 0.5, a.getYaw(), a.getPitch());
            } catch (Exception ignored) {}
        }
    }

    public void tick(MinecraftServer server) {
        if (!speedrunEnabled) return;
        if (mode == Mode.CONTROL && engine == null && controlledPlayer != null && !controlledPlayer.isRemoved()) {
            engine = new SpeedrunEngine(server, null, this);
            restoreEngine(engine);
            this.speedrunStartMs = (speedrunStartMs > 0) ? speedrunStartMs : System.currentTimeMillis();
            server.getPlayerManager().broadcast(
                net.minecraft.text.Text.literal("§a[frend] Режим контроля: начинаю спидран за игрока..."), false);
        }
        if (spawnTicksRemaining > 0) {
            spawnTicksRemaining--;
            if (spawnTicksRemaining == 0) {
                try {
                    ServerWorld world = server.getOverworld();
                    GameProfile profile = new GameProfile(UUID.randomUUID(), "frend");
                    FrendBot b = new FrendBot(world, profile, server);
                    double x = world.getSpawnPos().getX() + 0.5;
                    double y = world.getSpawnPos().getY();
                    double z = world.getSpawnPos().getZ() + 0.5;
                    b.spawnAt(x, y, z);
                    world.addPlayer(b);
                    this.bot = b;
                    FrendMod.setBot(b);
                    engine = new SpeedrunEngine(server, b, this);
                    restoreEngine(engine);
                    this.speedrunStartMs = (speedrunStartMs > 0) ? speedrunStartMs : System.currentTimeMillis();
                    server.getPlayerManager().broadcast(
                        net.minecraft.text.Text.literal("§a[frend] Привет! Начинаю спидран..."), false);
                } catch (Exception e) {
                    FrendMod.LOGGER.error("Failed to spawn frend bot", e);
                }
            }
        }
        if (mode == Mode.SEPARATE && bot == null && engine == null && spawnTicksRemaining <= 0) {
            // Safety: schedule if needed
        }
        if (engine != null && !speedrunDone) {
            storeEngine(engine);
            engine.tick();
            if (engine.getPhase() == SpeedrunEngine.Phase.FINISHED) {
                finishSpeedrun(server);
            }
        }
        if (helping && bot != null) {
            bot.helpTick(server);
        }
    }

    public void finishSpeedrun(MinecraftServer server) {
        if (speedrunDone) return;
        speedrunDone = true;
        speedrunEndMs = System.currentTimeMillis();
        long seconds = (speedrunEndMs - speedrunStartMs) / 1000;
        server.getPlayerManager().broadcast(
            net.minecraft.text.Text.literal("§6[frend] Спидран завершён за " + seconds + " сек. Теперь помогаю!"), false);
        helping = true;
        FrendPersistence.save(server);
    }

    public void setActorPosition(ServerPlayerEntity a) {}
}
