package ru.frend.ai;

import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.TypeFilter;
import net.minecraft.potion.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;

public class CombatHelper {
    public static void handle(MinecraftServer server, FrendState state, ServerPlayerEntity actor) {
        if (actor == null) return;
        ServerWorld w = (ServerWorld) actor.getWorld();
        Box box = new Box(actor.getX() - 16, actor.getY() - 8, actor.getZ() - 16,
                          actor.getX() + 16, actor.getY() + 8, actor.getZ() + 16);
        var mobs = w.getEntitiesByType(TypeFilter.instanceOf(HostileEntity.class), box, e -> true);
        if (mobs.isEmpty()) return;

        HostileEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (HostileEntity m : mobs) {
            double d = m.squaredDistanceTo(actor.getX(), actor.getY(), actor.getZ());
            if (d < best) { best = d; nearest = m; }
        }
        if (nearest == null) return;

        float hp = actor.getHealth();
        if (hp <= 6.0F) {
            // Flee away from nearest hostile.
            Vec3d dir = new Vec3d(actor.getX() - nearest.getX(), 0, actor.getZ() - nearest.getZ()).normalize();
            double nx = actor.getX() + dir.x * 8;
            double nz = actor.getZ() + dir.z * 8;
            int ny = (int) actor.getY() + 3; // climb up for safety
            actor.teleportTo(w, nx, ny, nz, actor.getYaw(), actor.getPitch());
            actor.heal(6.0F);
            try { actor.getHungerManager().add(20, 20F); } catch (Exception ignored) {}
            actor.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
            server.getPlayerManager().broadcast(
                net.minecraft.text.Text.literal("§c[frend] Мало HP, убегаю и лечусь."), false);
            return;
        }

        double dist = nearest.squaredDistanceTo(actor.getX(), actor.getY(), actor.getZ());
        if (dist <= 9.0) {
            // Try to kill; direct damage from server-side.
            nearest.damage(w.getDamageSources().playerAttack(actor), 6.0F);
            if (nearest.isDead() || nearest.getHealth() <= 0) {
                actor.heal(2.0F); // reward sustain
            }
        } else if (dist <= 64.0) {
            // Approach/climb strategy.
            actor.teleportTo(w, actor.getX() + (nearest.getX() - actor.getX()) * 0.3,
                             actor.getY() + 2, actor.getZ() + (nearest.getZ() - actor.getZ()) * 0.3,
                             actor.getYaw(), actor.getPitch());
            server.getPlayerManager().broadcast(
                net.minecraft.text.Text.literal("§e[frend] Поднимаюсь/сближаюсь с враждебным мобом."), false);
        }

        // Simple trap: wall a hostile with cobblestone if close and low player HP trend.
        if (dist <= 16.0) {
            BlockPos mp = nearest.getBlockPos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = mp.add(dx, 0, dz);
                    if (w.getBlockState(p).isAir()) w.setBlockState(p, Blocks.COBBLESTONE.getDefaultState());
                }
            }
        }
    }
}
