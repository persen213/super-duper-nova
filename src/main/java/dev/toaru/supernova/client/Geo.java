package dev.toaru.supernova.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Procedural geometry drawn with plain coloured quads. Copied from blackhole addon. */
public final class Geo {
    private final VertexConsumer out;
    private final Matrix4f pose;
    private final double cx, cy, cz;
    private float r = 1f, g = 1f, b = 1f, a = 1f;

    public Geo(VertexConsumer out, Matrix4f pose, Vec3 camera) {
        this.out = out; this.pose = pose; this.cx = camera.x; this.cy = camera.y; this.cz = camera.z;
    }

    public Geo color(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a; return this; }

    private void v(Vec3 p) {
        out.vertex(pose, (float) (p.x - cx), (float) (p.y - cy), (float) (p.z - cz)).color(r, g, b, a).endVertex();
    }

    private void quad(Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4) {
        v(p1); v(p2); v(p3); v(p4);
        v(p4); v(p3); v(p2); v(p1);
    }

    /** Two perpendicular unit vectors that are perpendicular to {@code n}. */
    static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.normalize();
        Vec3 helper = Math.abs(nn.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = nn.cross(helper).normalize();
        Vec3 v = nn.cross(u).normalize();
        return new Vec3[] {u, v};
    }

    /** A thin straight beam: two crossed ribbons, so it has width from every side. */
    public Geo ribbon(Vec3 from, Vec3 to, double width) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-10) return this;
        Vec3[] uv = basis(d);
        Vec3 n1 = uv[0].scale(width), n2 = uv[1].scale(width);
        quad(from.subtract(n1), from.add(n1), to.add(n1), to.subtract(n1));
        quad(from.subtract(n2), from.add(n2), to.add(n2), to.subtract(n2));
        return this;
    }

    /** A ring around {@code centre} lying in the plane with normal {@code normal}. */
    public Geo ring(Vec3 centre, Vec3 normal, double radius, double halfWidth, int segments, double phase, boolean dashed) {
        Vec3 nn = normal.normalize();
        Vec3[] uv = basis(nn);
        double inner = Math.max(1e-4, radius - halfWidth), outer = radius + halfWidth;
        Vec3 lift = nn.scale(halfWidth * 0.6);
        for (int i = 0; i < segments; i++) {
            if (dashed && (i & 3) == 3) continue;
            double a0 = phase + Math.PI * 2.0 * i / segments, a1 = phase + Math.PI * 2.0 * (i + 1) / segments;
            Vec3 d0 = uv[0].scale(Math.cos(a0)).add(uv[1].scale(Math.sin(a0)));
            Vec3 d1 = uv[0].scale(Math.cos(a1)).add(uv[1].scale(Math.sin(a1)));
            quad(centre.add(d0.scale(inner)), centre.add(d1.scale(inner)), centre.add(d1.scale(outer)), centre.add(d0.scale(outer)));
            Vec3 m0 = centre.add(d0.scale(radius)), m1 = centre.add(d1.scale(radius));
            quad(m0.add(lift), m1.add(lift), m1.subtract(lift), m0.subtract(lift));
        }
        return this;
    }

    /** A UV sphere made of {@code lat} x {@code lon} quads. */
    public Geo sphere(Vec3 centre, double radius, int lat, int lon) {
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2.0 * j / lon, p1 = Math.PI * 2.0 * (j + 1) / lon;
                quad(point(centre, radius, t0, p0), point(centre, radius, t0, p1), point(centre, radius, t1, p1), point(centre, radius, t1, p0));
            }
        }
        return this;
    }

    private static Vec3 point(Vec3 c, double radius, double theta, double phi) {
        return c.add(radius * Math.sin(theta) * Math.cos(phi), radius * Math.cos(theta), radius * Math.sin(theta) * Math.sin(phi));
    }
}