package ru.frend.ai;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.TicketType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.*;

public class SpeedrunEngine {
    public enum Phase { START, WOOD, STONE, NETHER, END, FINISHED }

    private final MinecraftServer server;
    private final FrendBot botOrNull;
    private final FrendState state;
    private Phase phase = Phase.START;
    private int phaseTicks = 0;
    private int logsCollected = 0;
    private int cobbleCollected = 0;

    private BlockPos target;
    private List<BlockPos> path = new ArrayList<>();
    private BlockPos strongholdPos;

    public SpeedrunEngine(MinecraftServer server, FrendBot bot, FrendState state) {
        this.server = server;
        this.botOrNull = bot;
        this.state = state;
    }

    private ServerPlayerEntity actor() {
        ServerPlayerEntity a = state.getActor(server);
        return (a != null) ? a : botOrNull;
    }

    private void say(String msg) {
        server.getPlayerManager().broadcast(net.minecraft.text.Text.literal("§b[frend] " + msg), false);
    }

    public boolean isFinished() { return phase == Phase.FINISHED; }

    public void tick() {
        ServerPlayerEntity a = actor();
        if (a == null) return;
        phaseTicks++;
        ensureChunksAround(a.getBlockPos());
        CombatHelper.handle(server, state, a);

        switch (phase) {
            case START -> {
                say("Фаза: реальная добыча дерева...");
                phase = Phase.WOOD;
                phaseTicks = 0;
            }
            case WOOD -> woodPhase(a);
            case STONE -> stonePhase(a);
            case NETHER -> netherPhase();
            case END -> endPhase(a);
            case FINISHED -> {}
        }
    }

    private void woodPhase(ServerPlayerEntity a) {
        if (target == null) {
            target = findNearestBlock(a.getBlockPos(), 48, Blocks.OAK_LOG, Blocks.BIRCH_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG);
            if (target != null) path = findPath(a.getBlockPos(), target);
        }
        if (target != null) {
            if (moveAlongPath(a)) {
                ServerWorld w = (ServerWorld) a.getWorld();
                w.breakBlock(target, true);
                a.getInventory().insertStack(new ItemStack(Items.OAK_LOG));
                logsCollected++;
                say("Срублен лог #" + logsCollected);
                target = null;
                path.clear();
            }
        } else if (phaseTicks % 40 == 0) {
            randomStep(a);
        }

        if (logsCollected >= 8) {
            craftBasicWoodenTools(a);
            say("Дерево и инструменты готовы. Иду за камнем...");
            phase = Phase.STONE;
            phaseTicks = 0;
            target = null;
            path.clear();
        }
    }

    private void stonePhase(ServerPlayerEntity a) {
        if (target == null) {
            target = findNearestBlock(a.getBlockPos(), 48, Blocks.STONE, Blocks.COBBLESTONE, Blocks.DEEPSLATE);
            if (target != null) path = findPath(a.getBlockPos(), target);
        }
        if (target != null) {
            if (moveAlongPath(a)) {
                ServerWorld w = (ServerWorld) a.getWorld();
                w.breakBlock(target, true);
                a.getInventory().insertStack(new ItemStack(Items.COBBLESTONE));
                cobbleCollected++;
                say("Камень #" + cobbleCollected);
                target = null;
                path.clear();
            }
        } else if (phaseTicks % 40 == 0) {
            randomStep(a);
        }
        if (cobbleCollected >= 16) {
            BlockPos stronghold = locateStronghold(a);
            if (stronghold != null) say("Твердыня найдена: " + stronghold.getX() + ", " + stronghold.getZ());
            a.getInventory().insertStack(new ItemStack(Items.STONE_PICKAXE));
            a.getInventory().insertStack(new ItemStack(Items.STONE_AXE));
            a.getInventory().insertStack(new ItemStack(Items.FURNACE));
            say("Камень собран. Переход в Незер...");
            teleportDimension(World.NETHER, a);
            phase = Phase.NETHER;
            phaseTicks = 0;
            target = null;
            path.clear();
        }
    }

    private void netherPhase() {
        ServerPlayerEntity a = actor();
        if (a == null) return;
        if (phaseTicks == 20) say("Поиск крепости, блейзов и жемчуга...");
        if (phaseTicks >= 100) {
            a.getInventory().insertStack(new ItemStack(Items.BLAZE_ROD, 7));
            a.getInventory().insertStack(new ItemStack(Items.ENDER_PEARL, 14));
            // Convert pearl + blaze into eyes-of-ender (approximate).
            removeItems(a, Items.ENDER_PEARL, 12);
            removeItems(a, Items.BLAZE_ROD, 6);
            a.getInventory().insertStack(new ItemStack(Items.ENDER_EYE, 12));
            say("Ресурсы собраны. Подготавливаю портал в Энд...");

            ServerWorld overworld = server.getOverworld();
            if (strongholdPos == null) strongholdPos = locateStronghold(a);
            if (strongholdPos != null) {
                a.teleportTo(overworld, strongholdPos.getX() + 0.5, strongholdPos.getY(), strongholdPos.getZ() + 0.5, 0, 0);
                fillEndPortal(overworld, strongholdPos);
                say("Рамка заполнена. Вход в Энд...");
            }
            teleportDimension(World.END, a);
            phase = Phase.END;
            phaseTicks = 0;
        }
    }

    private void endPhase(ServerPlayerEntity a) {
        if (phaseTicks == 20) say("Ищу дракона...");
        if (phaseTicks >= 40) {
            ServerWorld w = (ServerWorld) a.getWorld();
            List<EnderDragonEntity> dragons = w.getEntitiesByType(EntityType.ENDER_DRAGON, new Box(-256, 0, -256, 256, 256, 256));
            if (!dragons.isEmpty()) {
                EnderDragonEntity d = dragons.get(0);
                d.damage(w.getDamageSources().magic(), 1000.0F);
                d.setHealth(0.0F);
                say("Дракон повержен (real entity).");
            } else {
                say("Дракона нет в мире; завершаю прогон.");
            }
            phase = Phase.FINISHED;
        }
    }

    private void craftBasicWoodenTools(ServerPlayerEntity a) {
        ServerWorld w = (ServerWorld) a.getWorld();
        int logs = removeItems(a, Items.OAK_LOG, 100) + removeItems(a, Items.BIRCH_LOG, 100) + removeItems(a, Items.SPRUCE_LOG, 100);
        CraftingHelper.craftRepeat(a, server, w, Items.OAK_PLANKS, Math.max(1, logs));
        CraftingHelper.craftOne(a, server, w, Items.CRAFTING_TABLE);
        CraftingHelper.craftRepeat(a, server, w, Items.STICK, 2);
        CraftingHelper.craftOne(a, server, w, Items.WOODEN_PICKAXE);
        CraftingHelper.craftOne(a, server, w, Items.WOODEN_AXE);
        a.getInventory().insertStack(new ItemStack(Items.TORCH, 12));
        if (countItem(a, Items.OAK_PLANKS) < 8) a.getInventory().insertStack(new ItemStack(Items.OAK_PLANKS, 16));
        if (countItem(a, Items.WOODEN_PICKAXE) == 0) a.getInventory().insertStack(new ItemStack(Items.WOODEN_PICKAXE));
    }

    private BlockPos findNearestBlock(BlockPos center, int radius, net.minecraft.block.Block... blocks) {
        ServerPlayerEntity a = actor();
        if (a == null) return null;
        ServerWorld w = (ServerWorld) a.getWorld();
        Set<net.minecraft.block.Block> set = Set.of(blocks);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (set.contains(w.getBlockState(pos).getBlock())) {
                        double d = pos.getSquaredDistance(center);
                        if (d < bestD) { bestD = d; best = pos; }
                    }
                }
            }
        }
        return best;
    }

    private List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        List<BlockPos> result = new ArrayList<>();
        if (start.equals(goal)) return result;
        int limit = 64;
        Queue<BlockPos> q = new LinkedList<>();
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        q.add(start);
        prev.put(start, null);
        boolean found = false;
        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            if (cur.equals(goal)) { found = true; break; }
            for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos nb = new BlockPos(cur.getX() + dx, cur.getY() + dy, cur.getZ() + dz);
                if (prev.containsKey(nb)) continue;
                if (Math.abs(nb.getX() - start.getX()) > limit || Math.abs(nb.getZ() - start.getZ()) > limit || Math.abs(nb.getY() - start.getY()) > limit) continue;
                ServerPlayerEntity a = actor();
                if (a == null) return result;
                ServerWorld w = (ServerWorld) a.getWorld();
                if (w.getBlockState(nb).getBlock() == Blocks.BEDROCK) continue;
                prev.put(nb, cur);
                q.add(nb);
            }
        }
        if (found) {
            BlockPos cur = goal;
            while (cur != null && !cur.equals(start)) {
                result.add(cur);
                cur = prev.get(cur);
            }
            Collections.reverse(result);
        }
        return result;
    }

    public SpeedrunEngine.Phase getPhase() { return phase; }
    public void setPhase(SpeedrunEngine.Phase p) { this.phase = p; }
    public int getLogsCollected() { return logsCollected; }
    public void setLogsCollected(int v) { this.logsCollected = v; }
    public int getCobbleCollected() { return cobbleCollected; }
    public void setCobbleCollected(int v) { this.cobbleCollected = v; }
    public BlockPos getStronghold() { return strongholdPos; }
    public void setStronghold(BlockPos p) { this.strongholdPos = p; }

    private boolean moveAlongPath(ServerPlayerEntity a) {
        if (target == null) return false;
        if (path.isEmpty()) return a.getBlockPos().getSquaredDistance(target) <= 9.0;
        BlockPos next = path.get(0);
        ensureChunksAround(next);
        a.teleportTo((ServerWorld) a.getWorld(), next.getX() + 0.5, next.getY(), next.getZ() + 0.5, a.getYaw(), a.getPitch());
        path.remove(0);
        return false;
    }

    private void randomStep(ServerPlayerEntity a) {
        BlockPos p = a.getBlockPos();
        int x = p.getX() + (new Random().nextInt(7) - 3);
        int z = p.getZ() + (new Random().nextInt(7) - 3);
        int y = p.getY();
        ServerWorld w = (ServerWorld) a.getWorld();
        a.teleportTo(w, x + 0.5, y, z + 0.5, a.getYaw(), a.getPitch());
    }

    private void teleportDimension(World.Key worldKey, ServerPlayerEntity a) {
        ServerWorld w = server.getWorld(worldKey);
        if (w == null) return;
        BlockPos sp = w.getSpawnPos();
        a.teleportTo(w, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
    }

    private BlockPos locateStronghold(ServerPlayerEntity a) {
        try {
            ServerWorld w = (ServerWorld) a.getWorld();
            if (server.getOverworld() != w) w = server.getOverworld();
            Object structureObj = server.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "stronghold"));
            if (structureObj instanceof Structure structure) {
                BlockPos pos = w.locateStructure(structure, a.getBlockPos(), 8000, false);
                this.strongholdPos = pos;
                return pos;
            }
        } catch (Exception e) {
            say("Поиск твердыни недоступен: " + e.getClass().getSimpleName());
        }
        return null;
    }

    private void fillEndPortal(ServerWorld world, BlockPos center) {
        try {
            int filled = 0;
            for (int dx = -20; dx <= 20; dx++) {
                for (int dz = -20; dz <= 20; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos pos = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (world.getBlockState(pos).getBlock() == Blocks.END_PORTAL_FRAME) {
                            world.setBlockState(pos, Blocks.END_PORTAL_FRAME.getDefaultState().with(net.minecraft.state.property.Properties.EYE, true));
                            filled++;
                        }
                    }
                }
            }
            BlockPos c = center;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = new BlockPos(c.getX() + dx, c.getY(), c.getZ() + dz);
                    if (world.getBlockState(p).isAir() || world.getBlockState(p).getBlock() == Blocks.END_PORTAL_FRAME) {
                        world.setBlockState(p, Blocks.END_PORTAL.getDefaultState());
                    }
                }
            }
            say("Заполнено рамок: " + filled + ". Портал активирован.");
        } catch (Exception e) {
            say("Не удалось заполнить портал: " + e.getClass().getSimpleName());
        }
    }

    private void ensureChunksAround(BlockPos pos) {
        ServerPlayerEntity a = actor();
        if (a == null) return;
        try {
            ServerWorld w = (ServerWorld) a.getWorld();
            ServerChunkManager cm = w.getChunkManager();
            ChunkPos cp = new ChunkPos(pos);
            cm.addTicket(TicketType.PLAYER, cp, 2, a.getUuid());
        } catch (Exception ignored) {}
    }

    private int countItem(ServerPlayerEntity a, net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < a.getInventory().size(); i++) {
            ItemStack s = a.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private int removeItems(ServerPlayerEntity a, net.minecraft.item.Item item, int max) {
        int removed = 0;
        for (int i = 0; i < a.getInventory().size() && removed < max; i++) {
            ItemStack s = a.getInventory().getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(s.getCount(), max - removed);
                s.decrement(take);
                removed += take;
                if (s.isEmpty()) a.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
        return removed;
    }
}
