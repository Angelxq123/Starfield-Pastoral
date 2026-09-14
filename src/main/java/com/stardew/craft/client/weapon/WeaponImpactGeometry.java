package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.client.weapon.WeaponGlowGeometry.*;

/** Reusable contact flashes and quieter family-specific follow-through, chosen per attack. */
public final class WeaponImpactGeometry {
    private WeaponImpactGeometry() {}
    static void draw(VertexConsumer out, Matrix4f pose, Vec3 point, Vec3 right, Vec3 up, Vec3 normal,
                     WeaponTargetImpactClient.Style style, float age, float fade, boolean edge,
                     int r, int g, int b) {
        Frame f = new Frame(out, pose, point, right, up, normal, style.size, age, fade, edge, r, g, b);
        if(style.slenderCross()) {
            WeaponContactGeometry.slenderCross(out,pose,point,right,up,normal,style.size,fade,edge,r,g,b);
            if(age>=1 && (style==WeaponTargetImpactClient.Style.OBSIDIAN_RESONANCE||style==WeaponTargetImpactClient.Style.OSSIFIED_BONUS))
                for(int side:new int[]{-1,1}) f.sliver(side*(.38+age*.045),style.obsidian()?side*.2-age*age*.002:-.18-age*age*.005,.055,.014,side*.65,.018);
        }
        else if(style==WeaponTargetImpactClient.Style.CRESCENT_SLASH){
            CrescentFalchionGeometry.crescent(out,pose,point,right,up,normal,style.size,fade,edge,r,g,b);
        }
        else if(style.falchion()){
            CrescentFalchionGeometry.etchHit(out,pose,point,right,up,normal,style.size,fade,edge,r,g,b);
        }
        else if(style==WeaponTargetImpactClient.Style.RUST_STRIKE||style==WeaponTargetImpactClient.Style.WOOD_BLESS){
            RustWoodGeometry.contact(out,pose,point,right,up,normal,style==WeaponTargetImpactClient.Style.WOOD_BLESS,style.size,age,fade,edge,r,g,b);
        }
        else if(style==WeaponTargetImpactClient.Style.SPINE_STRIKE) {
            GuardSpineGeometry.contact(out,pose,point,right,up,normal,style.size,age,fade,edge,r,g,b);
        }
        else if (style == WeaponTargetImpactClient.Style.PIRATE_PLUNDER) {
            PirateSilverGeometry.plunder(out, pose, point, right, up, normal, style.size, age, fade, edge, r, g, b);
        }
        else if (style == WeaponTargetImpactClient.Style.IRON_THRUST || style == WeaponTargetImpactClient.Style.WIND_CUT) {
            IronWindGeometry.contact(out, pose, point, right, up, normal, style, age, fade, edge, r, g, b);
        }
        else if (style == WeaponTargetImpactClient.Style.BONE_FRACTURE || style == WeaponTargetImpactClient.Style.CLAYMORE_OUT) {
            BoneClaymoreGeometry.contact(out, pose, point, right, up, normal, style, age, fade, edge, r, g, b);
        }
        else if (style == WeaponTargetImpactClient.Style.DWARF_SHOCK) {
            DwarfWeaponGeometry.shockContact(out, pose, point, right, up, normal, style.size, age, fade, edge, r, g, b);
        }
        else if (style.needle() || style == WeaponTargetImpactClient.Style.BURGLAR_STRIKE) {
            NeedleBurglarGeometry.contact(out, pose, point, right, up, normal, style, age, fade, edge, r, g, b);
        }
        else if (style == WeaponTargetImpactClient.Style.SHADOW_FINISH
                || (style.insect() && !style.crossContact())) {
            ShadowInsectGeometry.contact(out, pose, point, right, up, normal, style, age, fade, edge, r, g, b);
        }
        else if(style==WeaponTargetImpactClient.Style.CRYSTAL_BURST) {
            f.cross(fade);
            for(int i=0;i<4;i++) {double angle=i*Math.PI/2+.35;f.sliver(Math.cos(angle)*(.17+age*.035),Math.sin(angle)*(.17+age*.035),.14,.032,angle,.035);}
        }
        else if(style.venom()) {
            CrystalVenomGeometry.venomContact(out,pose,point,right,up,normal,style.size,age,fade,edge,
                    style==WeaponTargetImpactClient.Style.VENOM_BURST,r,g,b);
        }
        else if (style.crossContact()) crossContact(f);
        else if (style.rainbow()) RainbowTrailGeometry.twinkles(out, pose, point, right, up, style.size, age, fade, edge);
        else if (style == WeaponTargetImpactClient.Style.DARK_BURST) {
            f.cross(fade);
        }
        else if (style.forge()) {
            f.glint(0.95, 0.3, style == WeaponTargetImpactClient.Style.FORGE_RING ? 0.5 : 0.75, 0.065, fade);
            for (int i = 0; i < (style == WeaponTargetImpactClient.Style.FORGE_RING ? 2 : 4); i++)
                f.sliver((i-1.5)*(0.12+age*0.035), 0.14+age*0.05-age*age*0.006, 0.06,0.018,i*0.8,0.025);
        }
        else if (style.obsidian()) obsidian(f, style);
        else if (style.ossified()) ossified(f, style);
        else if (style.sacred()) sacred(f, style);
        else if (style.grove()) leaves(f, style);
        else if (style.tide()) water(f, style);
        else if (style.infinity()) infinity(f, style);
        else if (style.galaxy()) stars(f, style);
        else if (style.frost()) ice(f, style);
        else if (style.dragon) claws(f, style);
        else molten(f, style);
    }
    private static void crossContact(Frame f) {
        f.cross(WeaponTargetImpactClient.opacity(f.age, 6));
    }
    private static void obsidian(Frame f, WeaponTargetImpactClient.Style style) {
        // One asymmetric fracture: branches open from the contact, then shed small sharp chips.
        double open = Math.min(f.age / 3, 1);
        f.ribbon(new Vec3[]{f.p(-0.65,-0.35,0),f.p(-0.12,0.08,0),f.p(0.18,0,0),f.p(0.6,0.52,0)},0.075);
        if (style != WeaponTargetImpactClient.Style.OBSIDIAN_CUT) {
            f.line(-0.12, 0.08, -0.22-open*0.18, 0.18+open*0.37, 0.016);
            f.line(0.18, 0, 0.3+open*0.25, -0.16-open*0.3, 0.012);
        }
        for (int i = 0; i < 3; i++) {
            double sign = i % 2 == 0 ? 1 : -1;
            f.sliver(sign*(0.2 + f.age*0.045), (i-1)*0.27-f.age*f.age*0.004,
                    0.09, 0.025, i*1.3+f.age*0.06, 0.028);
        }
    }
    private static void ossified(Frame f, WeaponTargetImpactClient.Style style) {
        // Tall split scoring closes briefly, then breaks downwards; no violet shards or radial burst.
        double close = Math.max(0, 1-f.age/2.5), fall = Math.max(0, f.age-3);
        for (int i = -1; i <= 1; i++) {
            double x = i*(0.15+0.14*close), y = -fall*fall*0.007;
            f.line(x-0.04, 0.55+y, x+0.015, 0.09+y, 0.016);
            f.line(x+0.015+fall*i*0.018, -0.02+y, x+0.06+fall*i*0.025, -0.43+y, 0.023);
        }
        if (style == WeaponTargetImpactClient.Style.OSSIFIED_BONUS)
            f.line(-0.48, -0.16, 0.48, 0.27, 0.025);
    }
    private static void molten(Frame f, WeaponTargetImpactClient.Style style) {
        // Broken molten seam, followed by falling embers. No radial starburst.
        Vec3[] seam = new Vec3[6];
        for (int i = 0; i < seam.length; i++)
            seam[i] = f.p(-0.8 + i * 0.32, (i % 2 == 0 ? -0.06 : 0.13), 0);
        f.ribbon(seam, 0.11);
        int count = style.heavy ? 7 : 4;
        for (int i = 0; i < count; i++) {
            double x = (i - (count - 1) / 2.0) * (0.16 + f.age * 0.02);
            double y = 0.18 + (i % 3) * 0.12 + f.age * 0.025 - f.age * f.age * 0.009;
            f.sliver(x, y, 0.07, 0.035, i * 0.9, 0.035);
        }
    }
    private static void claws(Frame f, WeaponTargetImpactClient.Style style) {
        // Three offset tearing cuts; the long strike opens them, the dagger keeps them tightly packed.
        double spread = style.shiv() ? 0.15 : 0.25;
        for (int i = -1; i <= 1; i++) {
            double offset = i * spread, drift = Math.min(f.age, 5) * 0.012 * i;
            f.line(-0.32 + offset + drift, 0.66 - i * 0.08, 0.32 + offset + drift, -0.6 - i * 0.08, 0.027);
            if (f.age > 1) f.sliver(0.35 + offset + f.age * 0.014, -0.6 - f.age * 0.025,
                    0.12, 0.032, -0.6, 0.055);
        }
    }
    private static void ice(Frame f, WeaponTargetImpactClient.Style style) {
        // Thick, uneven crystal splinters separate, tumble and fall after contact.
        int count = style == WeaponTargetImpactClient.Style.FROST_SPINE ? 7 : 5;
        for (int i = 0; i < count; i++) {
            double angle = i * 2.399 + 0.4, radius = 0.12 + f.age * (0.045 + (i % 2) * 0.014);
            double x = Math.cos(angle) * radius, y = Math.sin(angle) * radius - f.age * f.age * 0.003;
            f.sliver(x, y, i % 2 == 0 ? 0.34 : 0.21, 0.065, angle + f.age * 0.035, 0.11);
        }
    }
    private static void water(Frame f, WeaponTargetImpactClient.Style style) {
        // Two open crescent wakes; the reel contracts while an anchor throws water outwards.
        boolean reel = style == WeaponTargetImpactClient.Style.TIDE_REEL;
        double radius = reel ? Math.max(0.23, 0.9 - f.age * 0.07) : 0.28 + f.age * 0.06;
        for (int sign : new int[]{-1, 1}) {
            Vec3[] arc = new Vec3[15];
            for (int i = 0; i < arc.length; i++) {
                double a = -1.15 + i * 0.16;
                arc[i] = f.p(Math.cos(a) * radius * sign, Math.sin(a) * radius * 0.63, 0);
            }
            f.ribbon(arc, 0.072);
        }
        for (int i = 0; i < 3; i++) {
            double x = (i - 1) * (0.2 + f.age * 0.04), y = 0.32 + f.age * 0.045 - f.age * f.age * 0.006;
            f.sliver(x, y, 0.075, 0.04, 0, 0.04);
        }
    }
    private static void leaves(Frame f, WeaponTargetImpactClient.Style style) {
        GroveEffectGeometry.slash(f.out, f.pose, f.point, f.right, f.up, f.normal,
                f.size, f.age, f.fade, style == WeaponTargetImpactClient.Style.FOREST_RELEASE
                        || style == WeaponTargetImpactClient.Style.FOREST_CUT, f.edge);
    }
    private static void stars(Frame f, WeaponTargetImpactClient.Style style) {
        // A stable four-point star; tiny satellites orbit instead of imitating debris.
        boolean needle = style == WeaponTargetImpactClient.Style.GALAXY_STAB || style == WeaponTargetImpactClient.Style.GALAXY_LEAP;
        f.line(0, -0.75, 0, 0.75, 0.035);
        f.line(needle ? -0.22 : -0.46, 0, needle ? 0.22 : 0.46, 0, 0.024);
        for (int i = 0; i < 2; i++) {
            double angle = i * Math.PI + f.age * 0.15, radius = 0.46 + f.age * 0.018;
            f.sliver(Math.cos(angle) * radius, Math.sin(angle) * radius * 0.6, 0.09, 0.042, angle, 0.035);
        }
    }
    private static void infinity(Frame f, WeaponTargetImpactClient.Style style) {
        // Opposing curved cuts shear apart; gold and violet edges share the same finite trajectory.
        double separation = f.age * 0.025;
        for (int sign : new int[]{-1, 1}) {
            Vec3[] arc = new Vec3[17];
            for (int i = 0; i < arc.length; i++) {
                double a = -1.8 + i * 0.19;
                arc[i] = f.p(sign * (Math.cos(a) * 0.53 - 0.25 + separation), Math.sin(a) * 0.48, 0);
            }
            f.ribbon(arc, 0.082);
        }
        if (style == WeaponTargetImpactClient.Style.INFINITY_BACK) f.line(-0.2, 0.65, 0.2, -0.65, 0.03);
    }
    private static void sacred(Frame f, WeaponTargetImpactClient.Style style) {
        boolean templar = style == WeaponTargetImpactClient.Style.TEMPLAR_STRIKE
                || style == WeaponTargetImpactClient.Style.TEMPLAR_JUDGEMENT || style == WeaponTargetImpactClient.Style.TEMPLAR_SHARE
                || style == WeaponTargetImpactClient.Style.TEMPLAR_CUT;
        if (!templar) {
            if (style == WeaponTargetImpactClient.Style.HOLY_PULSE) {
                double r = 0.15 + f.age * 0.065;
                f.line(0, r, r * 0.6, 0, 0.018); f.line(r * 0.6, 0, 0, -r, 0.018);
                f.line(0, -r, -r * 0.6, 0, 0.018); f.line(-r * 0.6, 0, 0, r, 0.018);
            } else {
                f.line(0.04, 0.9, -0.04, -0.76, 0.045);
                for (int i = -1; i <= 1; i += 2) {
                    double y = 0.32 - f.age * 0.075;
                    f.line(i * 0.19, y + 0.2, i * 0.19, y - 0.1, 0.012);
                }
            }
            return;
        }
        if (style == WeaponTargetImpactClient.Style.TEMPLAR_STRIKE || style == WeaponTargetImpactClient.Style.TEMPLAR_CUT) {
            // Guard breaks into a decisive angled counter-cut.
            f.line(-0.6, -0.43, 0.55, 0.48, 0.038);
            f.line(-0.28 - f.age * 0.02, 0.48, -0.48 - f.age * 0.025, 0.19, 0.02);
            f.line(0.28 + f.age * 0.02, -0.48, 0.48 + f.age * 0.025, -0.19, 0.02);
        } else {
            double radius = Math.max(0.12, 0.53 - f.age * 0.045);
            for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
                f.line(sx * radius, sy * radius, sx * (radius - 0.15), sy * radius, 0.02);
                f.line(sx * radius, sy * radius, sx * radius, sy * (radius - 0.15), 0.02);
            }
            if (style == WeaponTargetImpactClient.Style.TEMPLAR_JUDGEMENT) {
                f.line(-0.08, 1.1, -0.08, -0.85, 0.024);
                f.line(0.08, 1.1, 0.08, -0.85, 0.024);
            }
        }
    }
    private record Frame(VertexConsumer out, Matrix4f pose, Vec3 point, Vec3 right, Vec3 up, Vec3 normal,
                         float size, float age, float fade, boolean edge, int r, int g, int b) {
        Vec3 p(double x, double y, double z) { return point.add(right.scale(x * size)).add(up.scale(y * size)).add(normal.scale(z * size)); }
        void cross(float flash) {
            WeaponContactGeometry.fourDiamonds(out, pose, point, right, up, normal, size, flash, edge, r, g, b);
        }
        void glint(double dx, double dy, double length, double width, float flash) {
            Vec3 axis = right.scale(dx).add(up.scale(dy)).normalize();
            WeaponContactGeometry.blade(out, pose, point.subtract(axis.scale(size * length)),
                    point.add(axis.scale(size * length)), normal, size * width * 1.9,
                    flash * 0.8f, edge, r, g, b);
        }
        void line(double x1, double y1, double x2, double y2, double width) {
            WeaponContactGeometry.blade(out, pose, p(x1, y1, 0), p(x2, y2, 0), normal,
                    size * width * 2.4, fade, edge, r, g, b);
        }
        void ribbon(Vec3[] path, double width) {
            WeaponContactGeometry.ribbon(out, pose, path, normal, size * width, fade, edge, r, g, b);
        }
        void sliver(double x, double y, double length, double width, double angle, double depth) {
            double sin = Math.sin(angle), cos = Math.cos(angle), scale = edge ? 1.16 : 1;
            Vec3 tip = p(x + sin * length * scale, y + cos * length * scale, 0);
            Vec3 tail = p(x - sin * length * scale, y - cos * length * scale, 0);
            Vec3 left = p(x - cos * width * scale, y + sin * width * scale, 0);
            Vec3 right = p(x + cos * width * scale, y - sin * width * scale, 0);
            Vec3 front = p(x, y, depth * scale), back = p(x, y, -depth * scale);
            Vec3[] rim = {tip, right, tail, left};
            for (int i = 0; i < 4; i++) for (Vec3 cap : new Vec3[]{front, back}) {
                vertex(out, pose, rim[i], r, g, b, Math.round(210 * fade));
                vertex(out, pose, rim[(i + 1) % 4], r, g, b, Math.round(210 * fade));
                vertex(out, pose, cap, edge ? r : 255, edge ? g : 249, edge ? b : 216, Math.round(245 * fade));
                vertex(out, pose, cap, edge ? r : 255, edge ? g : 249, edge ? b : 216, Math.round(245 * fade));
            }
        }
    }
}
