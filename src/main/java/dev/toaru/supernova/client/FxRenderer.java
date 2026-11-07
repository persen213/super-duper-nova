package dev.toaru.supernova.client;

import dev.toaru.supernova.Cfg;
import dev.toaru.supernova.SupernovaMod;
import dev.toaru.supernova.client.ClientFx.Effect;
import dev.toaru.supernova.net.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Supernova rendering - cinematic 5-phase visual sequence:
 * - Gathering (0-2s): growing star core with pulsing corona, accretion disk
 * - Compression (2-4s): star compresses, accretion disk forms, relativistic jets, magnetic fields
 * - Silence (1s): frozen moment, star intensely white, environment darkens
 * - Detonation (1s): 4 expanding rings of fire/force with chromatic aberration
 * - Afterglow (2s): fading rings, nebula remnant, neutron star remnant
 */
@Mod.EventBusSubscriber(modid = SupernovaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FxRenderer {
    private FxRenderer() { }

    private static final double MAX_DISTANCE = 220.0;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ClientFx.ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.isPaused() ? 0f : event.getPartialTick();
        long now = mc.level.getGameTime();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // Pass 1: Dark geometry (star core, shadows)
        Geo dark = new Geo(buffers.getBuffer(BhRenderTypes.DARK), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawDark(fx, fx.age(now, partial), dark, cam);
        }
        buffers.endBatch(BhRenderTypes.DARK);

        // Pass 2: Additive glow (rings, fire, plasma, jets, fields)
        Geo glow = new Geo(buffers.getBuffer(BhRenderTypes.GLOW), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawGlow(fx, fx.age(now, partial), glow, cam);
        }
        buffers.endBatch(BhRenderTypes.GLOW);

        // Pass 3: Distortion/lensing (gravitational lensing, heat haze)
        Geo distort = new Geo(buffers.getBuffer(BhRenderTypes.DISTORTION), pose, cam);
        for (Effect fx : ClientFx.ACTIVE.values()) {
            if (fx.pos.distanceTo(cam) <= MAX_DISTANCE) drawDistortion(fx, fx.age(now, partial), distort, cam);
        }
        buffers.endBatch(BhRenderTypes.DISTORTION);
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static double smoothstep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double smoothstep3(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static int segments(Effect fx, Vec3 cam) {
        double dist = fx.pos.distanceTo(cam);
        return dist < 50 ? 64 : (dist < 120 ? 40 : 28);
    }

    /** Determine current phase and progress for a given effect age. */
    private static PhaseInfo phaseInfo(Effect fx, double age) {
        int gatheringTicks = Cfg.gatheringTicks();
        int compressionTicks = Cfg.compressionTicks();
        int silenceTicks = Cfg.silenceTicks();
        int detonationTicks = Cfg.detonationTicks();
        int afterglowTicks = Cfg.afterglowTicks();
        int totalTicks = gatheringTicks + compressionTicks + silenceTicks + Cfg.detonationTicks() + Cfg.afterglowTicks();

        if (age < Cfg.gatheringTicks()) {
            double progress = clamp01(age / (double) Cfg.gatheringTicks());
            return new PhaseInfo(Phase.GATHERING, progress, 0, 0);
        } else if (age < Cfg.gatheringTicks() + Cfg.compressionTicks()) {
            double ageInPhase = age - Cfg.gatheringTicks();
            double progress = clamp01(ageInPhase / (double) Cfg.compressionTicks());
            return new PhaseInfo(Phase.COMPRESSION, progress, 0, 0);
        } else if (age < Cfg.gatheringTicks() + Cfg.compressionTicks() + Cfg.silenceTicks()) {
            double progress = clamp01((age - Cfg.gatheringTicks() - Cfg.compressionTicks()) / (double) Cfg.silenceTicks());
            return new PhaseInfo(Phase.SILENCE, progress, 0, 0);
        } else if (age < Cfg.gatheringTicks() + Cfg.compressionTicks() + Cfg.silenceTicks() + Cfg.detonationTicks()) {
            double ageInDetonation = age - Cfg.gatheringTicks() - Cfg.compressionTicks() - Cfg.silenceTicks();
            double progress = clamp01(ageInDetonation / (double) Cfg.detonationTicks());
            int ringDelay = Math.max(1, Cfg.ticks(Cfg.RING_DELAY));
            int currentRing = Math.min((int) (ageInDetonation / ringDelay), Math.max(0, Cfg.RING_COUNT.get().intValue() - 1));
            double ringProgress = clamp01((ageInDetonation - currentRing * ringDelay) / (double) ringDelay);
            return new PhaseInfo(Phase.DETONATION, progress, currentRing, ringProgress);
        } else {
            double progress = clamp01((age - Cfg.gatheringTicks() - Cfg.compressionTicks() - Cfg.silenceTicks() - Cfg.detonationTicks()) / (double) Cfg.afterglowTicks());
            return new PhaseInfo(Phase.AFTERGLOW, progress, 0, 0);
        }
    }

    private enum Phase { GATHERING, COMPRESSION, SILENCE, DETONATION, AFTERGLOW }
    private record PhaseInfo(Phase phase, double progress, int currentRing, double ringProgress) {}

    private static void drawDark(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        Vec3 c = fx.pos;
        PhaseInfo pi = phaseInfo(fx, age);

        if (pi.phase == Phase.GATHERING || pi.phase == Phase.COMPRESSION) {
            // Collapsing core - nearly black
            double coreR = fx.aux.x * 0.25 * smoothstep(pi.progress);
            g.color(0.01f, 0.01f, 0.02f, 1.0f).sphere(fx.pos, fx.aux.x * 0.3 * smoothstep(pi.progress), 12, seg / 2);
            // Event horizon
            g.color(0f, 0f, 0f, 1.0f).sphere(fx.pos, fx.aux.x * 0.15 * smoothstep(pi.progress), 10, seg / 2);
        }
        // Explosion and afterglow phases have no dark geometry
    }

    private static void drawGlow(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        Vec3 c = fx.pos;
        PhaseInfo pi = phaseInfo(fx, age);

        switch (pi.phase) {
            case GATHERING -> drawGatheringGlow(fx, age, g, cam, seg, c, pi);
            case COMPRESSION -> drawCompressionGlow(fx, age, g, cam, seg, c, pi);
            case SILENCE -> drawSilenceGlow(fx, age, g, cam, seg, c, pi);
            case DETONATION -> drawDetonationGlow(fx, age, g, cam, seg, c, pi);
            case AFTERGLOW -> drawAfterglowGlow(fx, age, g, cam, seg, c, pi);
        }
    }

    // ===== PHASE 1: GATHERING (0-2s) =====
    private static void drawGatheringGlow(Effect fx, double age, Geo g, Vec3 cam, int seg, Vec3 c, PhaseInfo pi) {
        double progress = pi.progress;
        double ease = smoothstep(pi.progress);
        double coreR = fx.aux.x * 0.35 * pi.progress;
        double maxR = fx.aux.x;

        // === CORE: white-hot center ===
        g.color(1.0f, 1.0f, 0.95f, (float) (1.0 * pi.progress)).sphere(fx.pos, coreR * 0.6, 12, seg / 2);
        g.color(1.0f, 0.98f, 0.8f, (float) (0.9 * pi.progress)).sphere(fx.pos, coreR, 10, seg / 2);
        g.color(1.0f, 0.8f, 0.4f, (float) (0.7 * pi.progress)).sphere(fx.pos, coreR * 1.4, 8, seg / 2);

        // === ACCRETION DISK: tilted, multi-temperature ===
        Vec3 diskNormal = new Vec3(Math.sin(0.4) * Math.cos(0.8), Math.cos(0.4), Math.sin(0.4) * Math.sin(0.8)).normalize();
        int diskRings = seg >= 64 ? 28 : (seg >= 40 ? 18 : 10);
        for (int k = 0; k < diskRings; k++) {
            double u = (k + age * 0.02) % 1.0; // rotating
            double temp = 1.0 - u; // 1.0 = hottest (inner), 0.0 = coolest (outer)
            double radius = coreR * (2.0 + 6.0 * u);
            double thickness = coreR * (0.04 + 0.06 * u);
            double omega = 0.35 / Math.pow(1.0 + 3.0 * u, 1.5);

            // Temperature-based color: white -> yellow -> orange -> red
            float rCol, gCol, bCol;
            if (temp > 0.7) { rCol = 1.0f; gCol = (float) lerp(0.95, 0.8, (temp-0.7)/0.3); bCol = (float) lerp(0.8, 0.3, (temp-0.7)/0.3); }
            else if (temp > 0.4) { rCol = 1.0f; gCol = (float) lerp(0.8, 0.4, (temp-0.4)/0.3); bCol = (float) lerp(0.3, 0.05, (temp-0.4)/0.3); }
            else { rCol = 1.0f; gCol = (float) lerp(0.4, 0.1, temp/0.4); bCol = 0.02f; }

            double pulse = 0.85 + 0.15 * Math.sin(age * 0.12 + k * 0.7);
            float alpha = (float) (0.6 * (1.0 - k * 0.025) * pi.progress);
            g.color(rCol, gCol, bCol, alpha).ring(fx.pos, diskNormal, radius, thickness * 0.5, seg, age * omega + k * 0.5, true);

            // Disk surface highlight
            g.color(1.0f, 0.9f, 0.7f, (float) (0.15 * pulse)).ring(fx.pos, diskNormal, radius, coreR * 0.015, seg, age * omega + k * 0.5, false);
        }

        // === RELATIVISTIC JETS (both poles) ===
        Vec3 jetAxis = new Vec3(Math.sin(0.4) * Math.cos(0.8), Math.cos(0.4), Math.sin(0.4) * Math.sin(0.8)).normalize();
        double jetLength = fx.aux.x * 0.8 * pi.progress;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 dir = jetAxis.scale(side);
            Vec3 base = fx.pos.add(jetAxis.scale(fx.aux.x * 0.4));
            Vec3 tip = fx.pos.add(jetAxis.scale(side * jetLength));

            // Jet core (collimated, brilliant white-blue)
            g.color(1.0f, 0.95f, 0.85f, 0.7f).ribbon(base, tip, fx.aux.x * 0.12);
            // Jet sheath (wider, blue)
            g.color(0.6f, 0.8f, 1.0f, 0.35f).ribbon(base, tip, fx.aux.x * 0.22);
            // Shock diamonds along jet
            for (int d = 1; d <= 5; d++) {
                double pos = d / 6.0;
                Vec3 diamond = base.add(jetAxis.scale(side * jetLength * pos));
                double size = fx.aux.x * 0.1 * (1.0 + 0.3 * Math.sin(age * 0.3 + d * 1.2));
                g.color(1.0f, 0.9f, 0.7f, (float) (0.4 * (1.0 - pos))).sphere(diamond, size, 8, 12);
            }
        }

        // === MAGNETIC FIELD LINES ===
        for (int fl = 0; fl < 8; fl++) {
            double phi = fl * Math.PI * 2.0 / 8 + age * 0.005;
            Vec3 startDir = new Vec3(Math.cos(phi), 0, Math.sin(phi)).normalize();

            Vec3 prev = null;
            for (int i = 0; i <= 30; i++) {
                double t = i / 30.0;
                double r0 = fx.aux.x * 1.2;
                double radius = r0 * Math.sin(Math.acos(0)) * Math.sin(Math.acos(0)); // simplified dipole
                if (radius < fx.aux.x * 0.4) radius = fx.aux.x * 0.4;

                double angle = phi + age * 0.008 + t * 0.3;
                Vec3 pt = fx.pos.add(new Vec3(Math.cos(angle) * radius * Math.sqrt(0.5), 0, Math.sin(angle) * radius * Math.sqrt(0.5)));

                if (prev != null) {
                    double fade = (1.0 - t) * 0.4;
                    g.color(0.4f, 0.6f, 1.0f, (float) (0.08 * fade)).ribbon(prev, pt, 0.01 + 0.015 * t);
                }
                prev = pt;
            }
        }

        // === PULSING CORONA RINGS ===
        for (int i = 0; i < 4; i++) {
            double ringPhase = age * (0.03 + i * 0.008) + i * 1.5;
            double tilt = Math.sin(ringPhase * 0.3) * 0.5;
            Vec3 normal = new Vec3(Math.sin(tilt), Math.cos(tilt), 0).normalize();
            double ringR = coreR * (1.8 + i * 0.6);
            double thickness = coreR * 0.03;
            double pulse = Math.sin(ringPhase) * 0.12 + 1.0;
            float alpha = (float) (0.4 * (1.0 - i * 0.18) * pulse);
            g.color(1.0f, 0.7f, 0.2f, alpha).ring(fx.pos, new Vec3(0, 1, 0), ringR * pulse, coreR * 0.1, seg, ringPhase, false);
        }

        // === GRAVITATIONAL LENSING RINGS (photon sphere) ===
        Vec3 toCam = cam.subtract(fx.pos).normalize();
        double photonR = fx.aux.x * 0.45 * pi.progress;
        g.color(1.0f, 0.98f, 0.85f, (float) (0.85 * pi.progress)).ring(fx.pos, toCam, fx.aux.x * 0.47, fx.aux.x * 0.008, seg, 0, false);
        g.color(1.0f, 0.8f, 0.4f, (float) (0.35 * pi.progress)).ring(fx.pos, toCam, fx.aux.x * 0.52, fx.aux.x * 0.025, seg, 0, false);

        // === INFALLING MATTER STREAMS ===
        for (int arm = 0; arm < 6; arm++) {
            double base = arm * Math.PI * 2.0 / 6;
            Vec3 prev = null;
            for (int j = 0; j <= 20; j++) {
                double s = j / 20.0;
                double rad = coreR * 1.5 + (fx.aux.x * 0.6 - coreR * 1.5) * Math.pow(s, 1.5);
                double ang = base + age * 0.035 + s * 4.0;
                double h = Math.sin(base * 2.5 + s * 3.0) * (1.0 - s) * fx.aux.x * 0.1;
                Vec3 pt = fx.pos.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
                if (prev != null) {
                    g.color(1f, (float) lerp(0.55, 0.95, s), (float) lerp(0.15, 0.8, s), (float) (0.5 * pi.progress * (0.25 + 0.75 * s)));
                    g.ribbon(prev, pt, 0.04 + 0.05 * s);
                }
                prev = pt;
            }
        }

        // === GRAVITATIONAL WAVES ===
        for (int w = 0; w < 3; w++) {
            double ph = ((age * 0.9 + w * 40.0) % 120.0) / 120.0;
            g.color(0.55f, 0.65f, 1.0f, (float) ((1.0 - ph) * 0.30 * pi.progress))
                    .ring(fx.pos, new Vec3(0, 1, 0), ph * fx.aux.x, 0.06 + 0.12 * ph, seg, 0, false);
        }
    }

    // ===== PHASE 2: COMPRESSION (2-4s) =====
    private static void drawCompressionGlow(Effect fx, double age, Geo g, Vec3 cam, int seg, Vec3 c, PhaseInfo pi) {
        double progress = pi.progress;
        double ease = smoothstep(progress);
        double maxR = fx.aux.x;

        // === CORE: collapsing star ===
        double coreR = fx.aux.x * 0.35 * (1.0 - pi.progress);
        g.color(0.0f, 0.0f, 0.05f, 1.0f).sphere(fx.pos, fx.aux.x * 0.3 * (1.0 - pi.progress), 12, seg / 2);
        // Photon sphere - bright white
        double photonR = fx.aux.x * 0.45 * (1.0 + 0.2 * Math.sin(age * 0.1));
        g.color(1.0f, 1.0f, 0.95f, (float) (0.8 * (1.0 - pi.progress))).sphere(fx.pos, photonR, 14, seg / 2);

        // === ACCRETION DISK: fully formed ===
        renderAccretionDisk(g, fx, age, seg);

        // === RELATIVISTIC JETS ===
        Vec3 jetAxis = new Vec3(Math.sin(0.4) * Math.cos(0.8), Math.cos(0.4), Math.sin(0.4) * Math.sin(0.8)).normalize();
        double jetLength = fx.aux.x * 0.8;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 dir = jetAxis.scale(side);
            Vec3 base = fx.pos.add(jetAxis.scale(fx.aux.x * 0.4));
            Vec3 tip = fx.pos.add(jetAxis.scale(side * jetLength));

            g.color(1.0f, 0.95f, 0.85f, 0.7f).ribbon(base, tip, fx.aux.x * 0.12);
            g.color(0.6f, 0.8f, 1.0f, 0.35f).ribbon(base, tip, fx.aux.x * 0.22);
            for (int d = 1; d <= 5; d++) {
                double pos = d / 6.0;
                Vec3 diamond = base.add(jetAxis.scale(side * jetLength * pos));
                double size = fx.aux.x * 0.1 * (1.0 + 0.3 * Math.sin(age * 0.3 + d * 1.2));
                g.color(1.0f, 0.9f, 0.7f, (float) (0.4 * (1.0 - pos))).sphere(diamond, size, 8, 12);
            }
        }

        // === MAGNETIC FIELD LINES ===
        for (int fl = 0; fl < 8; fl++) {
            double phi = fl * Math.PI * 2.0 / 8 + age * 0.005;
            Vec3 startDir = new Vec3(Math.cos(phi), 0, Math.sin(phi)).normalize();

            Vec3 prev = null;
            for (int i = 0; i <= 30; i++) {
                double t = i / 30.0;
                double r0 = fx.aux.x * 1.2;
                double radius = r0 * Math.sin(Math.acos(0)) * Math.sin(Math.acos(0));
                if (radius < fx.aux.x * 0.4) radius = fx.aux.x * 0.4;

                double angle = phi + age * 0.008 + t * 0.3;
                Vec3 pt = fx.pos.add(new Vec3(Math.cos(angle) * radius * Math.sqrt(0.5), 0, Math.sin(angle) * radius * Math.sqrt(0.5)));

                if (prev != null) {
                    double fade = (1.0 - t) * 0.4;
                    g.color(0.4f, 0.6f, 1.0f, (float) (0.08 * fade)).ribbon(prev, pt, 0.01 + 0.015 * t);
                }
                prev = pt;
            }
        }

        // === PULSING CORONA RINGS ===
        for (int i = 0; i < 5; i++) {
            double ringPhase = age * (0.03 + i * 0.008) + i * 1.5;
            double tilt = Math.sin(ringPhase * 0.3) * 0.5;
            Vec3 normal = new Vec3(Math.sin(tilt), Math.cos(tilt), 0).normalize();
            double ringR = fx.aux.x * 0.45 * (1.0 + i * 0.08);
            double thickness = fx.aux.x * 0.012 + i * 0.008;
            float alpha = (float) (0.35 * (1.0 - i * 0.1));
            g.color(0.6f + i * 0.1f, 0.3f + i * 0.1f, 1.0f, alpha).ring(fx.pos, normal, ringR, thickness, seg, ringPhase, true);
        }

        // === INFALL STREAMS ===
        for (int arm = 0; arm < 8; arm++) {
            double base = arm * Math.PI * 2.0 / 8;
            Vec3 prev = null;
            for (int j = 0; j <= 20; j++) {
                double s = j / 20.0;
                double rad = fx.aux.x * 0.4 + (fx.aux.x * 0.7 - fx.aux.x * 0.4) * Math.pow(1.0 - s, 1.8);
                double ang = base + age * 0.05 + s * 5.5;
                double h = Math.sin(base * 2.5 + s * 4.0) * (1.0 - s) * fx.aux.x * 0.12;
                Vec3 pt = fx.pos.add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
                if (prev != null) {
                    g.color(1f, (float) lerp(0.5, 0.95, s), (float) lerp(0.1, 0.8, s), (float) (0.4 * (0.3 + 0.7 * s)));
                    g.ribbon(prev, pt, 0.04 + 0.05 * s);
                }
                prev = pt;
            }
        }
    }

    // ===== PHASE 3: SILENCE (1s) =====
    private static void drawSilenceGlow(Effect fx, double age, Geo g, Vec3 cam, int seg, Vec3 c, PhaseInfo pi) {
        double maxR = fx.aux.x;

        // Frozen moment - star at peak brightness, everything frozen
        double coreR = 0.5;
        g.color(1.0f, 1.0f, 0.95f, 1.0f).sphere(fx.pos, coreR * 1.2f, 14, seg / 2);
        g.color(1.0f, 1.0f, 1.0f, 1.0f).sphere(fx.pos, coreR, 16, seg / 2);

        // Photon sphere - brilliant white
        g.color(1.0f, 1.0f, 1.0f, 1.0f).sphere(fx.pos, fx.aux.x * 0.45, 16, seg / 2);

        // Frozen rings - don't animate
        for (int i = 0; i < 5; i++) {
            double tilt = 0.4 + i * 0.15;
            Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(i * 1.2), Math.cos(tilt), Math.sin(tilt) * Math.sin(i * 1.2)).normalize();
            double ringR = fx.aux.x * 0.45 * (1.0 + i * 0.08);
            double thickness = fx.aux.x * 0.012 + i * 0.008;
            g.color(1.0f, 0.7f, 0.3f, 0.35f).ring(fx.pos, normal, ringR, thickness, seg, i * 1.5, true);
        }
    }

    // ===== PHASE 4: DETONATION (1s) =====
    private static void drawDetonationGlow(Effect fx, double age, Geo g, Vec3 cam, int seg, Vec3 c, PhaseInfo pi) {
        double maxR = fx.aux.x;
        int totalRings = Cfg.RING_COUNT.get().intValue();
        int ringDelay = Cfg.ticks(Cfg.RING_DELAY);
        int currentRing = pi.currentRing;
        double ringProgress = pi.ringProgress;

        // === PREVIOUS RINGS (fading) ===
        for (int r = 0; r < currentRing; r++) {
            double rProgress = 1.0; // fully expanded
            double rFade = 1.0 - (currentRing - r) * 0.15;
            double rR = maxR * (r + 1) / (double) totalRings;
            drawRingSet(fx, g, seg, maxR, r, rR, rFade, totalRings, age, 1.0);
        }

        // === CURRENT RING (expanding) ===
        double ringR = maxR * (currentRing + 1) / (double) totalRings * ringProgress;
        if (ringR > 0.1) {
            drawRingSet(fx, g, seg, maxR, currentRing, ringR, 1.0, totalRings, age, ringProgress);
        }

        // === GLOBAL SHOCKWAVE ===
        double shockProgress = pi.progress;
        double shockR = maxR * shockProgress * 1.15;
        double shockFade = 1.0 - shockProgress;
        if (shockR > 0.5) {
            g.color(0.4f, 0.6f, 1.0f, (float) (0.12 * shockFade)).sphere(fx.pos, shockR, 16, seg / 2);
            g.color(0.6f, 0.8f, 1.0f, (float) (0.6 * shockFade)).ring(fx.pos, new Vec3(0, 1, 0), shockR, fx.aux.x * 0.02, seg, 0, false);
            g.color(1.0f, 0.9f, 0.7f, (float) (0.35 * shockFade)).ring(fx.pos, new Vec3(0, 1, 0), shockR * 0.98, fx.aux.x * 0.012, seg, 0, false);
        }

        // === FINAL SUPERNOVA REMNANT ===
        if (pi.progress > 0.85) {
            double nebProgress = (pi.progress - 0.85) / 0.15;
            double nebR = maxR * 1.2 * (0.8 + nebProgress * 0.5);
            float nebFade = (float) (0.15 * (1.0 - nebProgress * 0.5));
            g.color(0.3f, 0.1f, 0.5f, nebFade).sphere(fx.pos, nebR, 12, seg / 2);
            g.color(0.6f, 0.2f, 0.8f, nebFade * 0.7f).sphere(fx.pos, nebR * 0.8, 10, seg / 2);
            for (int f = 0; f < 12; f++) {
                double ang = f * Math.PI * 2.0 / 12 + age * 0.02;
                Vec3 fil = fx.pos.add(new Vec3(Math.cos(ang) * nebR * 0.9, Math.sin(f * 0.7 + age * 0.15) * fx.aux.x * 0.3, Math.sin(ang) * nebR * 0.9));
                g.color(0.8f, 0.4f, 1.0f, (float) (0.25 * (1.0 - nebProgress))).sphere(fil, fx.aux.x * 0.03, 4, 8);
            }
        }
    }

    // ===== PHASE 5: AFTERGLOW (2s) =====
    private static void drawAfterglowGlow(Effect fx, double age, Geo g, Vec3 cam, int seg, Vec3 c, PhaseInfo pi) {
        double maxR = fx.aux.x;
        double nebProgress = pi.progress;
        double nebR = fx.aux.x * 1.25 * (0.9 + nebProgress * 0.4);
        float nebFade = (float) (0.18 * (1.0 - nebProgress * 0.4));

        // Nebula shells
        g.color(0.25f, 0.05f, 0.45f, nebFade).sphere(fx.pos, nebR, 14, seg / 2);
        g.color(0.45f, 0.1f, 0.6f, nebFade * 0.7f).sphere(fx.pos, nebR * 0.78, 10, seg / 2);
        g.color(0.7f, 0.3f, 0.9f, nebFade * 0.4f).sphere(fx.pos, nebR * 0.55, 8, seg / 2);

        // Filamentary structure
        for (int f = 0; f < 16; f++) {
            double ang = f * Math.PI * 2.0 / 16 + age * 0.015;
            double len = nebR * (0.7 + 0.3 * Math.sin(f * 0.5 + age * 0.08));
            Vec3 fil = fx.pos.add(new Vec3(Math.cos(ang) * len * 0.9, Math.sin(f * 0.7 + age * 0.12) * fx.aux.x * 0.4, Math.sin(ang) * len * 0.9));
            g.color(0.9f, 0.5f, 1.0f, (float) (0.3 * (1.0 - nebProgress))).sphere(fil, fx.aux.x * 0.025, 4, 8);
        }

        // Central neutron star / black hole remnant
        g.color(0.02f, 0.02f, 0.05f, 1.0f).sphere(fx.pos, fx.aux.x * 0.08, 10, seg / 2);
    }

    // ===== DISTORTION PASS =====
    private static void drawDistortion(Effect fx, double age, Geo g, Vec3 cam) {
        int seg = segments(fx, cam);
        Vec3 c = fx.pos;
        PhaseInfo pi = phaseInfo(fx, age);

        if (pi.phase == Phase.GATHERING && pi.progress < 0.3) return;

        double maxR = fx.aux.x;
        double lensR = maxR * (pi.phase == Phase.GATHERING ? pi.progress * 0.5 : 0.6 + pi.progress * 0.4);

        // Gravitational lensing shells
        for (int i = 0; i < 6; i++) {
            double shellR = lensR * (0.95 + i * 0.015);
            double wave = Math.sin(age * 0.08 + shellR * 0.3 + i * 1.0) * 0.008;
            double alpha = 0.025 * (1.0 - i * 0.12) * (pi.phase == Phase.DETONATION ? 0.7 : 1.0);
            g.color(0.35f + i * 0.05f, 0.45f + i * 0.05f, 1.0f, (float) (alpha * (1.0 + wave * 40)))
                    .sphere(c, shellR + wave, 12, seg);
        }

        // Heat haze during explosion
        if (pi.phase == Phase.DETONATION) {
            double progress = pi.progress;
            double hazeR = maxR * (0.8 + progress * 1.5);
            for (int i = 0; i < 4; i++) {
                double shellR = hazeR * (0.9 + i * 0.025);
                double noise = Math.sin(age * 0.15 + i * 2.0) * 0.015;
                g.color(1.0f, 0.9f, 0.7f, (float) (0.018 * (1.0 - progress) * (1.0 + noise * 30)))
                        .sphere(c, shellR + noise, 10, seg);
            }
        }
    }

    // ===== HELPER METHODS =====

    private static void drawRingSet(Effect fx, Geo g, int seg, double maxR, int ringIndex, double ringR, double fade, int totalRings, double age, double ringProgress) {
        int reverseIndex = totalRings - 1 - ringIndex;
        double innerTemp = 0.3 + 0.7 * (1.0 - ringIndex / (double) totalRings);

        // Outer fire/plasma rings
        for (int layer = 0; layer < 5; layer++) {
            double layerR = ringR * (1.0 - layer * 0.02);
            double layerThick = maxR * (0.012 + layer * 0.004);
            double layerFade = fade * (1.0 - layer * 0.1);
            if (layerFade <= 0) continue;

            float rCol, gCol, bCol;
            if (reverseIndex < 2) {
                rCol = 1.0f; gCol = (float) lerp(1.0, 0.85, innerTemp); bCol = (float) lerp(0.7, 0.2, innerTemp);
            } else if (reverseIndex < 4) {
                rCol = 1.0f; gCol = (float) lerp(0.85, 0.4, innerTemp); bCol = (float) lerp(0.2, 0.02, innerTemp);
            } else {
                rCol = 1.0f; gCol = (float) lerp(0.4, 0.1, innerTemp); bCol = 0.02f;
            }
            g.color(rCol, gCol, bCol, (float) (0.7 * layerFade * (1.0 - layer * 0.15)))
                    .ring(fx.pos, new Vec3(0, 1, 0), ringR, layerThick, seg, 0, false);
        }

        // Inner force/plasma rings
        for (int layer = 0; layer < 3; layer++) {
            double layerR = ringR * 0.78;
            double layerThick = maxR * (0.01 + layer * 0.005);
            double layerFade = fade * (0.6 - layer * 0.15);
            if (layerFade <= 0) continue;
            g.color(0.5f + layer * 0.1f, 0.7f + layer * 0.1f, 1.0f, (float) (0.5 * layerFade))
                    .ring(fx.pos, new Vec3(0, 1, 0), ringR * 0.82, layerThick, seg, 0, false);
        }

        // Chromatic aberration
        g.color(1.0f, 0.3f, 0.2f, (float) (0.15 * fade)).ring(fx.pos, new Vec3(0, 1, 0), ringR * 1.02, fx.aux.x * 0.008, seg, 0, false);
        g.color(0.2f, 0.8f, 0.3f, (float) (0.1 * fade)).ring(fx.pos, new Vec3(0, 1, 0), ringR * 1.04, fx.aux.x * 0.008, seg, 0, false);
        g.color(0.2f, 0.3f, 1.0f, (float) (0.1 * fade)).ring(fx.pos, new Vec3(0, 1, 0), ringR * 0.98, fx.aux.x * 0.008, seg, 0, false);

        // Sparks
        if (ringProgress > 0.15 && ringProgress < 0.85) {
            int sparkCount = seg >= 56 ? 24 : (seg >= 40 ? 16 : 10);
            for (int i = 0; i < sparkCount; i++) {
                double ang = i * Math.PI * 2.0 / sparkCount + age * (0.12 + ringIndex * 0.02);
                double height = Math.sin(ang * 3.0 + age * 0.4) * maxR * 0.08;
                Vec3 sparkPos = fx.pos.add(new Vec3(Math.cos(ang) * ringR * 1.02, height, Math.sin(ang) * ringR * 1.02));
                g.color(1.0f, 0.9f, 0.3f, (float) (0.8 * fade)).sphere(sparkPos, maxR * 0.018, 4, 6);
            }
        }

        // Magnetic ripples
        for (int m = 0; m < 2; m++) {
            Vec3 mNormal = m == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            double mR = ringR * (0.95 + m * 0.05);
            g.color(0.6f, 0.4f, 1.0f, (float) (0.18 * fade)).ring(fx.pos, mNormal, mR, fx.aux.x * 0.006, seg, age * 0.05, false);
        }
    }

    private static void renderAccretionDisk(Geo g, Effect fx, double age, int seg) {
        Vec3 diskNormal = new Vec3(Math.sin(0.4) * Math.cos(0.8), Math.cos(0.4), Math.sin(0.4) * Math.sin(0.8)).normalize();
        int diskRings = seg >= 64 ? 28 : (seg >= 40 ? 18 : 10);
        for (int k = 0; k < diskRings; k++) {
            double u = (k + age * 0.02) % 1.0;
            double temp = 1.0 - u;
            double radius = fx.aux.x * 0.4 * (2.0 + 6.0 * u);
            double thickness = fx.aux.x * 0.04 * (1.0 + u);
            double omega = 0.35 / Math.pow(1.0 + 3.0 * u, 1.5);

            float rCol, gCol, bCol;
            if (temp > 0.7) { rCol = 1.0f; gCol = (float) lerp(0.95, 0.8, (temp-0.7)/0.3); bCol = (float) lerp(0.8, 0.3, (temp-0.7)/0.3); }
            else if (temp > 0.4) { rCol = 1.0f; gCol = (float) lerp(0.8, 0.4, (temp-0.4)/0.3); bCol = (float) lerp(0.3, 0.05, (temp-0.4)/0.3); }
            else { rCol = 1.0f; gCol = (float) lerp(0.4, 0.1, temp/0.4); bCol = 0.02f; }

            double pulse = 0.85 + 0.15 * Math.sin(age * 0.12 + k * 0.7);
            float alpha = (float) (0.6 * (1.0 - k * 0.025));
            g.color(rCol, gCol, bCol, alpha).ring(fx.pos, diskNormal, radius, thickness * 0.5, seg, age * omega + k * 0.5, true);
            g.color(1.0f, 0.9f, 0.7f, (float) (0.15 * pulse)).ring(fx.pos, diskNormal, radius, fx.aux.x * 0.015, seg, age * omega + k * 0.5, false);
        }
    }
}
