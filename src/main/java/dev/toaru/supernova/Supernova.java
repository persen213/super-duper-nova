package dev.toaru.supernova;

import dev.toaru.supernova.net.Fx;
import dev.toaru.supernova.net.FxType;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.StopReason;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.ActivationContext;
import io.github.pinchan4273.reacademycraft.api.mc.AcademySafety;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Supernova (level 4, one activation; registered as TOGGLE so the timed phases run - pressing the key again cancels it):
 * Cinematic phases (total ~8s):
 * 1. Gathering (2s): star forms at aimed spot
 * 2. Compression (2s): star compresses, rings appear, geometry forms
 * 3. Silence (1s): frozen moment, star intensely white, environment darkens
 * 4. Detonation (1s): massive explosion with expanding rings of fire and force
 * 5. Afterglow (2s): rings expand and fade, nebula remnant forms, star contracts
 */
public final class Supernova implements SkillBehavior {
    private static final class State {
        Vec3 centre;
        long fxKey;
        final Set<UUID> affectedEntities = new HashSet<>();
        int currentRing = 0;
        int lastRingTick = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    // Phase duration helpers (ticks)
    private static int gatheringTicks() { return Cfg.gatheringTicks(); }
    private static int compressionTicks() { return Cfg.compressionTicks(); }
    private static int silenceTicks() { return Cfg.silenceTicks(); }
    private static int detonationTicks() { return Cfg.detonationTicks(); }
    private static int afterglowTicks() { return Cfg.afterglowTicks(); }
    private static int totalTicks() { return Cfg.totalTicks(); }

    // Phase constants
    private static final int PHASE_GATHERING = 0;
    private static final int PHASE_COMPRESSION = 1;
    private static final int PHASE_SILENCE = 2;
    private static final int PHASE_DETONATION = 3;
    private static final int PHASE_AFTERGLOW = 4;

    @Override public ActivationOutcome start(ActivationContext c) {
        ServerPlayer p = c.naturalUser();
        if (p == null) return ActivationOutcome.REFUSED_INVALID;

        Vec3 centre = Targeting.point(p, 40).add(0, 4.0, 0);
        if (!c.pay(Cfg.CP.get().floatValue(), Cfg.OVERLOAD.get().floatValue())) return ActivationOutcome.REFUSED_RESOURCE;

        ServerLevel level = p.serverLevel();
        State s = new State();
        s.centre = centre;
        s.fxKey = Fx.make(level, centre, FxType.SUPERNOVA, totalTicks()).range(220.0)
                .aux(new Vec3(Cfg.RADIUS.get(), gatheringTicks(), 0)).send();
        STATES.put(p.getUUID(), s);
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.5f);
        return ActivationOutcome.STARTED;
    }

    @Override public boolean tick(ActivationContext c) {
        ServerPlayer p = c.naturalUser();
        State s = p == null ? null : STATES.get(p.getUUID());
        int age = c.age();
        if (p == null || s == null || age >= totalTicks()) return false;

        ServerLevel level = p.serverLevel();
        AbilityActor actor = c.actor();
        Vec3 centre = s.centre;
        double radius = Cfg.RADIUS.get();

        // Phase 0: GATHERING (0-2s) - star forms at aimed spot
        if (age < gatheringTicks()) {
            if (age % 5 == 0) {
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BLAZE_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.8f + age * 0.02f);
            }
            return true;
        }

        // Phase 1: COMPRESSION (2-4s) - star compresses, rings appear, geometry forms
        int compressionStart = gatheringTicks();
        int compressionEnd = gatheringTicks() + compressionTicks();
        if (age < compressionEnd) {
            if (age % 10 == 0) {
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.6f + (age - gatheringTicks()) * 0.01f);
            }
            return true;
        }

        // Phase 2: SILENCE (4-5s) - frozen moment, star intensely white
        int silenceStart = gatheringTicks() + compressionTicks();
        int silenceEnd = compressionEnd + silenceTicks();
        if (age < silenceEnd) {
            if (age == silenceStart) {
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.END_PORTAL_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.3f);
            }
            return true;
        }

        // Phase 3: DETONATION (5-6s) - massive explosion with expanding rings
        int detonationStart = silenceEnd;
        int detonationEnd = silenceEnd + detonationTicks();
        if (age < detonationEnd) {
            int explosionAge = age - detonationStart;
            int ringDelay = Cfg.ticks(Cfg.RING_DELAY);
            int ringCount = Cfg.RING_COUNT.get().intValue();
            int currentRing = explosionAge / ringDelay;

            if (currentRing >= Cfg.RING_COUNT.get().intValue()) return true;

            if (explosionAge % ringDelay == 0 && currentRing != s.lastRingTick) {
                s.lastRingTick = currentRing;
                s.currentRing = currentRing;
                emitRing(c, level, c.actor(), centre, radius, currentRing, ringCount);
            }

            return true;
        }

        // Phase 4: AFTERGLOW (6-8s) - rings expand and fade, nebula remnant
        int afterglowStart = detonationEnd;
        if (age < totalTicks()) {
            if (age == afterglowStart) {
                level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.WITHER_DEATH, net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.4f);
            }
            return true;
        }

        return false;
    }

    private void emitRing(ActivationContext c, ServerLevel level, AbilityActor actor, Vec3 centre, double maxRadius, int ringIndex, int totalRings) {
        double ringRadius = maxRadius * (ringIndex + 1) / (double) totalRings;
        double dmg = Cfg.DAMAGE.get() * (1.0 - ringIndex * 0.1);
        double kb = Cfg.KNOCKBACK.get() * (1.0 - ringIndex * 0.15);
        double bossDmg = Cfg.BOSS_DMG.get();
        double bossKb = Cfg.BOSS_KB.get();

        int hits = 0;
        for (LivingEntity e : Targeting.livingWithin(level, centre, ringRadius + 2.0, null)) {
            if (!Targeting.hostile(c.actor(), e, SupernovaMod.SUPERNOVA)) continue;
            double d = Targeting.centre(e).distanceTo(centre);
            if (d > ringRadius + 2.0) continue;
            double falloff = 1.0 - 0.5 * Math.min(1.0, d / ringRadius);
            double bossMul = Targeting.bossLike(e) ? bossDmg : 1.0;
            if (!AcademySafety.attack(c.actor(), e, SupernovaMod.SUPERNOVA, (float) (dmg * falloff * bossMul))) continue;
            Vec3 away = Targeting.centre(e).subtract(centre);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : away.normalize();
            double k = kb * falloff * (Targeting.bossLike(e) ? bossKb : Targeting.controlFactor(e));
            Targeting.setMotion(e, new Vec3(away.x * k, 0.5 + away.y * k * 0.5, away.z * k));
            hits++;
        }

        if (hits > 0) c.addProficiency(0.01f * Math.min(hits, 3));

        // Visual ring effect
        Fx.make(level, centre, FxType.SUPERNOVA_RING, 20).range(220.0)
                .aux(new Vec3(ringRadius, 0, ringIndex)).send();

        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f - ringIndex * 0.2f, 0.6f + ringIndex * 0.05f);
    }

    @Override public void stop(ActivationContext c, StopReason reason) {
        ServerPlayer p = c.naturalUser();
        State s = p == null ? null : STATES.remove(p.getUUID());
        if (p != null && s != null) {
            ServerLevel level = p.serverLevel();
            Fx.cancel(level, s.fxKey);

            if (reason == StopReason.FINISHED || reason == StopReason.SWITCHED_OFF) {
                level.playSound(null, s.centre.x, s.centre.y, s.centre.z, SoundEvents.WITHER_DEATH, net.minecraft.sounds.SoundSource.PLAYERS, 3.0f, 0.4f);
            }
        }
        c.setCooldown(Cfg.ticks(Cfg.COOLDOWN));
    }

    public static void forget(UUID player) { STATES.remove(player); }
    public static void clearAll() { STATES.clear(); }
}