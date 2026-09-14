package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Authored light volumes: galaxy spreads outward; infinity compresses before releasing. */
public final class HeavyHammerBurstGeometry {
    public enum Shape { SWEEP, STAR, STAR_ECHO, STAR_FINAL, PULL, PRESS, ECHO, POUND, AWAKEN }
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private HeavyHammerBurstGeometry() {}

    public static int lifetime(Shape shape) {
        return switch (shape) { case PULL -> 8; case POUND, ECHO -> 7; case STAR_FINAL, AWAKEN -> 14; default -> 11; };
    }

    public static void draw(VertexConsumer out, Matrix4f pose, Vec3 center, Shape shape,
                            float age, float radius, float yaw, long seed, boolean edge) {
        int life = lifetime(shape);
        if (age < 0 || age >= life) return;
        float t = age / life, fade = (1-t)*(.8f+.2f*(1-t));
        boolean gold = shape.ordinal() >= Shape.PULL.ordinal();
        int r = edge ? 34 : gold ? 255 : 135, g = edge ? 20 : gold ? 205 : 151, b = edge ? 49 : gold ? 114 : 255;
        double facing = Math.toRadians(yaw+90), expansion = 1-Math.pow(1-t,3);
        Vec3 c = center.add(0,.09,0);
        if (shape == Shape.PULL) {
            // Four bowed filaments pull down and inward, without a permanent black-hole sphere.
            for (int arm=0; arm<4; arm++) {
                double a=facing+arm*Math.PI/2;
                Vec3[] path=new Vec3[13];
                for(int i=0;i<path.length;i++) {
                    double u=i/12.0, q=Math.max(.08,1-t)*(1-u), angle=a+.65*u;
                    path[i]=c.add(Math.cos(angle)*radius*q,.08+.65*q*q,Math.sin(angle)*radius*q);
                }
                WeaponContactGeometry.ribbon(out,pose,path,UP,.075,fade,edge,r,g,b);
            }
            core(out,pose,c,.3f+.22f*t,.55f*(float)Math.sin(Math.PI*t),edge,r,g,b);
            return;
        }
        boolean heavy=shape==Shape.STAR_FINAL, repeated=shape==Shape.POUND;
        boolean echo=shape==Shape.ECHO || shape==Shape.STAR_ECHO;
        if(shape==Shape.AWAKEN) {
            for(int ring=0;ring<2;ring++) {
                double a=facing+ring*Math.PI/2;
                Vec3 right=new Vec3(Math.cos(a),0,Math.sin(a));
                arc(out,pose,c.add(0,.65+expansion*.35,0),right,UP,.35+expansion*.7,0,Math.PI*1.7,.07,fade,edge,r,g,b);
            }
            return;
        }
        if(shape==Shape.SWEEP) {
            // A broad crescent with raised shoulders: visible when looking forward, not only downward.
            for(int layer=0;layer<2;layer++) {
                Vec3[] curve=new Vec3[21];double reach=radius*(.24+.76*expansion)-layer*.23;
                for(int i=0;i<curve.length;i++) {
                    double u=i/20.0,a=facing+Math.toRadians(-80+160*u);
                    curve[i]=c.add(Math.cos(a)*reach,.12+Math.sin(Math.PI*u)*.38*(1-t),Math.sin(a)*reach);
                }
                WeaponContactGeometry.ribbon(out,pose,curve,UP,layer==0?.24:.075,fade*(layer==0?.85f:1),edge,r,g,b);
            }
            return;
        }
        float flash=Math.max(0,1-age/(heavy?4:3));
        core(out,pose,c,heavy?1.05f:repeated?.48f:echo?.4f:.73f,flash,edge,r,g,b);
        // Galaxy breaks into unequal rising facets. Infinity releases flattened pressure shells.
        if(gold) {
            double spread=(repeated?1.1:echo?1:1.9)*(.3+.7*expansion);
            Vec3 right=new Vec3(Math.cos(facing),0,Math.sin(facing));
            Vec3 across=right.cross(UP).scale(.9).add(0,.16*(1-t),0);
            for(int layer=0;layer<2;layer++)for(int side=0;side<2;side++)
                arc(out,pose,c.add(0,.08+layer*.08,0),right,across,spread-layer*.16,
                        side*Math.PI+.1,side*Math.PI+Math.PI-.12,layer==0?.15:.055,
                        fade*(echo?.65f:.9f),edge,r,g,b);
        } else {
            int rays=heavy?7:5;
            double spread=(heavy?2.4:echo?1.15:1.75)*(.15+.85*expansion);
            for(int i=0;i<rays;i++) {
                double a=facing+i*2.3999632297+((seed&3)*.13);
                Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a)),normal=radial.cross(UP);
                double reach=spread*(.72+(i%3)*.19),height=(.15+(i%3)*.23)*(1-t);
                Vec3 from=c.add(radial.scale(.1+expansion*.2));
                Vec3 to=c.add(radial.scale(reach)).add(0,height,0);
                WeaponContactGeometry.blade(out,pose,from,to,normal,
                        (heavy?.16:.11)*(i%2==0?1:.7),fade*.9f,edge,r,g,b);
            }
        }
        // Few distinct tapered sparks, in ballistic arcs. Echoes stay compact and do not repeat the main blast.
        int sparks=echo?3:repeated?4:heavy?9:6;
        for(int i=0;i<sparks;i++) {
            double a=facing+i*2.3999632297+(seed&7)*.19;
            double speed=(.5+(i%3)*.17)*(heavy?1.5:1), d=.22+speed*expansion;
            double height=.14+Math.sin(Math.PI*t)*((i%2==0?.72:.38)*(heavy?1.6:gold?.65:1));
            Vec3 p=c.add(Math.cos(a)*d,height,Math.sin(a)*d);
            Vec3 axis=new Vec3(Math.cos(a)*.45,.8-t,Math.sin(a)*.45).normalize();
            Vec3 normal=new Vec3(-Math.sin(a),0,Math.cos(a));
            WeaponContactGeometry.blade(out,pose,p.subtract(axis.scale(.1)),p.add(axis.scale(heavy?.28:.17)),normal,
                    heavy?.062:.04,fade*.8f,edge,r,g,b);
        }
    }

    private static void core(VertexConsumer out,Matrix4f pose,Vec3 c,float size,float fade,boolean edge,int r,int g,int b) {
        if(fade<=0)return;
        WeaponContactGeometry.blade(out,pose,c.add(-size,.01,0),c.add(size,.01,0),UP,size*.25,fade,edge,r,g,b);
        WeaponContactGeometry.blade(out,pose,c.add(0,.01,-size*.7),c.add(0,.01,size*.7),UP,size*.2,fade,edge,r,g,b);
        // Two perpendicular short vertical strokes give the contact a visible volume from low camera angles.
        for(Vec3 normal:new Vec3[]{new Vec3(1,0,0),new Vec3(0,0,1)})
            WeaponContactGeometry.blade(out,pose,c,c.add(0,size*1.4,0),normal,size*.15,fade*.8f,edge,r,g,b);
    }

    /** Small broken facets cling to the actual rendered hammer head in either camera mode. */
    public static void hammerHead(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,
                                  boolean gold,float power,double time) {
        if(power<=0)return;
        Vec3 axis=tip.subtract(base).normalize(),side=new Vec3(-axis.y,axis.x,0).normalize();
        Vec3 head=tip.lerp(base,.14),normal=new Vec3(0,0,1);
        float breath=(float)(.82+.18*Math.sin(time*.24));
        int r=gold?255:145,g=gold?214:171,b=gold?138:255;
        for(int face:new int[]{-1,1})for(int i=0;i<4;i++) {
            double a=i*Math.PI/2+Math.PI/4;
            Vec3 radial=axis.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
            Vec3 tangent=axis.scale(-Math.sin(a)).add(side.scale(Math.cos(a)));
            Vec3 c=head.add(radial.scale(.075)).add(0,0,face*.045);
            WeaponContactGeometry.blade(out,pose,c.subtract(tangent.scale(.055)),c.add(tangent.scale(.055)),normal,
                    .014,power*breath,false,r,g,b);
        }
    }

    private static void arc(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,double radius,
                            double start,double end,double width,float fade,boolean edge,int r,int g,int b) {
        Vec3[] path=new Vec3[21];
        for(int i=0;i<path.length;i++) {
            double a=start+(end-start)*i/(path.length-1);
            path[i]=c.add(right.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius*.55));
        }
        WeaponContactGeometry.ribbon(out,pose,path,right.cross(up),width,fade,edge,r,g,b);
    }
}
