package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PirateSilverGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private PirateSilverGeometry(){}
    static void plunder(VertexConsumer out,Matrix4f pose,Vec3 center,Vec3 right,Vec3 up,Vec3 normal,float size,float age,float fade,boolean edge,int r,int g,int b){
        if(fade<=0)return;
        Vec3[] arc=new Vec3[13];
        for(int i=0;i<arc.length;i++){double t=i/12.0;
            arc[i]=center.add(right.scale((t*1.15-.6)*size)).add(up.scale((.22*Math.sin(t*Math.PI)-t*.32)*size));}
        WeaponContactGeometry.ribbon(out,pose,arc,normal,.085*size,fade,edge,r,g,b);
        for(int i=0;i<2;i++){
            Vec3 p=center.add(right.scale((.18+age*.03+i*.13)*size)).add(up.scale((-.18-i*.09-age*age*.005)*size));
            Vec3 axis=right.add(up.scale(-.5)).normalize().scale(.085*size);
            WeaponContactGeometry.blade(out,pose,p.subtract(axis),p.add(axis),normal,.023*size,fade*.65f,edge,r,g,b);
        }
    }
    static Vec3[][] anchorPaths(Vec3 center){
        Vec3[][] result=new Vec3[4][];
        for(int i=0;i<4;i++){double x=(i<2?1:-1),z=(i%2==0?1:-1);
            result[i]=new Vec3[]{center.add(x*.13,0,z*.29),center.add(x*.29,0,z*.29),center.add(x*.29,0,z*.13)};}
        return result;
    }
    static void anchorStroke(VertexConsumer out,Matrix4f pose,Vec3[] path,float fade){
        if(fade>0)WeaponContactGeometry.ribbon(out,pose,path,UP,.025,fade*.65f,false,194,218,244);
    }
    static boolean validSegment(Vec3 from,Vec3 to){double d=from.distanceToSqr(to);return Double.isFinite(d)&&d>=.0016&&d<=6.25;}
    static void wake(VertexConsumer out,Matrix4f pose,Vec3 from,Vec3 to,float fade){
        if(fade<=0||!validSegment(from,to))return;
        Vec3 forward=to.subtract(from).normalize(),side=forward.cross(UP).normalize();if(side.lengthSqr()<1e-6)return;
        for(int sign:new int[]{-1,1}){
            Vec3[] curve=new Vec3[11];for(int i=0;i<curve.length;i++){
                double t=i/10.0;curve[i]=from.lerp(to,t).add(side.scale(sign*(.12+Math.sin(t*Math.PI)*.08))).add(0,Math.sin(t*Math.PI)*.04,0);}
            WeaponContactGeometry.ribbon(out,pose,curve,side.cross(forward).normalize(),sign>0?.035:.018,fade*.65f,false,184,207,238);
        }
    }
    public static void furyBlade(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,float fade){
        if(fade<=0)return;Vec3 along=tip.subtract(base).normalize();
        for(int face:new int[]{-1,1}){
            Vec3 offset=new Vec3(0,0,face*.037);
            WeaponContactGeometry.blade(out,pose,base.lerp(tip,.3).add(offset),base.lerp(tip,.85).add(offset),new Vec3(0,0,1),.012,fade*.6f,false,211,119,71);
            Vec3 p=base.lerp(tip,.38).add(offset);
            WeaponContactGeometry.blade(out,pose,p.subtract(along.scale(.026)),p.add(along.scale(.026)),new Vec3(0,0,1),.016,fade*.65f,false,237,194,111);
        }
    }
}
