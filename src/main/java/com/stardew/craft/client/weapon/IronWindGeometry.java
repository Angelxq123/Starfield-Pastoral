package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class IronWindGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private IronWindGeometry() {}
    static void contact(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,
                        WeaponTargetImpactClient.Style style,float age,float fade,boolean edge,int r,int g,int b) {
        if(fade<=0) return;
        double size=style.size;
        if(style==WeaponTargetImpactClient.Style.IRON_THRUST) {
            Vec3 axis=right.add(up.scale(.48)).normalize();
            WeaponContactGeometry.blade(out,pose,center.subtract(axis.scale(.65*size)),center.add(axis.scale(.65*size)),normal,.075*size,fade,edge,r,g,b);
            for(int sign:new int[]{-1,1}) {
                Vec3 p=center.add(up.scale(sign*(.10+age*.012)*size));
                WeaponContactGeometry.blade(out,pose,p.subtract(axis.scale(.11*size)),p.add(axis.scale(.11*size)),normal,.027*size,fade*.65f,edge,r,g,b);
            }
        } else {
            for(int sign:new int[]{-1,1}) {
                Vec3[] arc=new Vec3[11];
                for(int i=0;i<arc.length;i++) {
                    double t=i/10.0,x=(t-.5)*.95,y=x*.6+sign*(.04+Math.sin(t*Math.PI)*.10);
                    arc[i]=center.add(right.scale(x*size)).add(up.scale(y*size));
                }
                WeaponContactGeometry.ribbon(out,pose,arc,normal,(sign>0?.045:.026)*size,fade,edge,r,g,b);
            }
        }
    }
    /** Compact endpoint glints, never a line connecting two teleport destinations. */
    static void blink(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 direction,float fade,boolean wind) {
        if(fade<=0) return;
        Vec3 forward=new Vec3(direction.x,0,direction.z).normalize();
        if(forward.lengthSqr()<1e-6) forward=new Vec3(0,0,1);
        Vec3 side=forward.cross(UP).normalize();
        if(wind) {
            wake(out,pose,center.subtract(forward.scale(.45)),center.add(forward.scale(.45)),fade*.65f);
        } else for(int i=0;i<3;i++) {
            Vec3 p=center.add(side.scale((i-1)*.13)).add(forward.scale((i-1)*.12));
            Vec3 axis=UP.add(forward.scale(.45)).normalize().scale(i==1?.36:.23);
            WeaponContactGeometry.blade(out,pose,p.subtract(axis),p.add(axis),forward,.023,fade*(i==1?.8f:.4f),false,173,191,215);
        }
    }
    static boolean validSegment(Vec3 from,Vec3 to) {
        double d=from.distanceToSqr(to);
        return Double.isFinite(d) && d>=.0016 && d<=6.25;
    }
    /** Recorded movement only. The two ribbons have unequal bows and pointed tails. */
    static void wake(VertexConsumer out,Matrix4f pose,Vec3 from,Vec3 to,float fade) {
        if(fade<=0 || !validSegment(from,to)) return;
        Vec3 forward=to.subtract(from).normalize(), side=forward.cross(UP).normalize();
        if(side.lengthSqr()<1e-6) return;
        Vec3 normal=side.cross(forward).normalize();
        for(int sign:new int[]{-1,1}) {
            Vec3[] arc=new Vec3[11];
            for(int i=0;i<arc.length;i++) {
                double t=i/10.0,bow=Math.sin(t*Math.PI);
                arc[i]=from.lerp(to,t).add(side.scale(sign*(.15+bow*(sign>0?.13:.07)))).add(normal.scale(bow*.045));
            }
            WeaponContactGeometry.ribbon(out,pose,arc,normal,sign>0?.04:.025,fade*.65f,false,145,222,212);
        }
    }
    public static void galeBlade(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,float visibility) {
        if(visibility<=0) return;
        Vec3 along=tip.subtract(base).normalize(),side=new Vec3(-along.y,along.x,0).normalize();
        for(int face:new int[]{-1,1}) for(int sign:new int[]{-1,1}) {
            Vec3[] arc=new Vec3[9];
            for(int i=0;i<arc.length;i++) {
                double t=i/8.0;
                arc[i]=base.lerp(tip,.43+t*.45).add(side.scale(sign*(.016+Math.sin(t*Math.PI)*.02))).add(0,0,face*.037);
            }
            WeaponContactGeometry.ribbon(out,pose,arc,new Vec3(0,0,1),.006,visibility*.65f,false,156,228,215);
        }
    }
}
