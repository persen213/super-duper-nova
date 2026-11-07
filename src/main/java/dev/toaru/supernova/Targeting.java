package dev.toaru.supernova;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.TargetRelation;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Aiming, target validation and movement helpers. Copied from blackhole addon. */
public final class Targeting {
    private Targeting() { }

    private static boolean eligible(ServerPlayer p, Entity x, boolean projectiles) {
        if (x == p || !x.isAlive() || x.isSpectator()) return false;
        if (x instanceof LivingEntity) return true;
        return projectiles && x instanceof Projectile pr && pr.getOwner() != p;
    }

    public static @Nullable Entity entity(ServerPlayer p, double range, boolean projectiles) {
        Vec3 from = p.getEyePosition(), look = p.getLookAngle(), to = from.add(look.scale(range));
        ServerLevel level = p.serverLevel();
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double bestSq = block.getType() == HitResult.Type.MISS ? range * range : block.getLocation().distanceToSqr(from);
        Entity best = null;
        AABB search = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        for (Entity e : level.getEntities(p, search, x -> eligible(p, x, projectiles))) {
            AABB box = e.getBoundingBox().inflate(0.3 + e.getPickRadius());
            double dsq;
            if (box.contains(from)) {
                dsq = 0;
            } else {
                Optional<Vec3> hit = box.clip(from, to);
                if (hit.isEmpty()) continue;
                dsq = from.distanceToSqr(hit.get());
            }
            if (dsq < bestSq) { bestSq = dsq; best = e; }
        }
        return best;
    }

    public static @Nullable LivingEntity living(ServerPlayer p, double range) {
        return entity(p, range, false) instanceof LivingEntity le ? le : null;
    }

    public static Vec3 point(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition(), to = from.add(p.getLookAngle().scale(range));
        ServerLevel level = p.serverLevel();
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
        BlockHitResult down = level.clip(new ClipContext(to, to.add(0, -24, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return down.getType() != HitResult.Type.MISS ? down.getLocation() : to;
    }

    public static Vec3 centre(Entity e) { return e.position().add(0, e.getBbHeight() / 2.0, 0); }

    public static List<LivingEntity> livingWithin(ServerLevel level, Vec3 at, double radius, @Nullable Entity exclude) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e != exclude && e.isAlive() && !e.isSpectator() && centre(e).distanceToSqr(at) <= radius * radius);
    }

    public static List<Projectile> projectilesWithin(ServerLevel level, Vec3 at, double radius, @Nullable Entity owner) {
        return level.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && e.getOwner() != owner && e.position().distanceToSqr(at) <= radius * radius);
    }

    public static boolean bossLike(LivingEntity e) {
        return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e.getMaxHealth() >= 200f;
    }

    public static double controlFactor(LivingEntity e) {
        if (bossLike(e)) return 0.25;
        return e.getMaxHealth() >= 100f ? 0.5 : 1.0;
    }

    public static boolean hostile(AbilityActor actor, Entity e, AbilityId skill) {
        TargetRelation r = AcademySafety.relation(actor, e);
        if (r == TargetRelation.SELF || r == TargetRelation.ALLY || r == TargetRelation.NONE) return false;
        return AcademySafety.mayAttack(actor, e, skill);
    }

    public static boolean affectable(AbilityActor actor, Entity e, AbilityId skill) {
        TargetRelation r = AcademySafety.relation(actor, e);
        if (r == TargetRelation.SELF || r == TargetRelation.NONE) return false;
        return r == TargetRelation.ALLY || AcademySafety.mayAttack(actor, e, skill);
    }

    public static Vec3 horizontal(Vec3 v) { return new Vec3(v.x, 0, v.z); }

    public static void setMotion(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
    }

    public static Vec3 capLength(Vec3 v, double max) {
        double len = v.length();
        return len > max && len > 1e-9 ? v.scale(max / len) : v;
    }

    public static Vec3 flatDir(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        if (d.lengthSqr() < 1e-6) {
            Vec3 f = new Vec3(fallback.x, 0, fallback.z);
            return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
        }
        return d.normalize();
    }
}