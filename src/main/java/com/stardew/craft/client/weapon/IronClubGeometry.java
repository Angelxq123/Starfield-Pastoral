package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A dense downward pressure stroke versus a broad, tilted crescent. No floor rings. */
public final class IronClubGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private IronClubGeometry() {}
    public static void draw(VertexConsumer out,Matrix4f pose,Vec3 c,int phase,float age,float yaw,boolean edge) {
        if(age<0||age>=7)return;float t=age/7,fade=(1-t)*(1-t*.25f);
        double angle=Math.toRadians(yaw);Vec3 forward=new Vec3(-Math.sin(angle),0,Math.cos(angle)),right=new Vec3(forward.z,0,-forward.x);
        if(phase==0) {
            Vec3[] path=new Vec3[15];
            for(int i=0;i<path.length;i++){double u=i/14.0;path[i]=c.add(forward.scale(.7+u*1.3)).add(right.scale(.5-u*.8)).add(0,1.85-u*1.25,0);}
            WeaponContactGeometry.ribbon(out,pose,path,forward,.22,fade,edge,edge?27:183,edge?30:199,edge?37:218);
            return;
        }
        for(int layer=0;layer<2;layer++) {
            Vec3[] path=new Vec3[33];double radius=layer==0?3.5:2.95;
            for(int i=0;i<path.length;i++) {
                double u=i/32.0,a=Math.toRadians(-110+220*u);
                path[i]=c.add(forward.scale(Math.cos(a)*radius)).add(right.scale(Math.sin(a)*radius))
                        .add(0,.95+Math.sin(a)*.28-layer*.12,0);
            }
            WeaponContactGeometry.ribbon(out,pose,path,UP.add(forward.scale(.25)).normalize(),layer==0?.19:.10,
                    fade*(layer==0?1:.42f),edge,edge?46:layer==0?237:176,edge?33:layer==0?218:156,edge?25:layer==0?183:127);
        }
    }
    public static void contact(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,boolean press,float age,boolean edge) {
        if(age<0||age>=7)return;float t=age/7,fade=(1-t)*(1-t);
        int r=edge?30:press?207:247,g=edge?31:press?220:224,b=edge?37:press?235:183;
        if(press) {
            Vec3 axis=right.add(up.scale(-.22)).normalize();
            WeaponContactGeometry.blade(out,pose,c.subtract(axis.scale(.68)),c.add(axis.scale(.68)),normal,.20,fade,edge,r,g,b);
            for(int i=0;i<3;i++) {
                Vec3 p=c.add(right.scale((i-1)*(.17+t*.42))).subtract(up.scale(.10+t*.35));
                WeaponContactGeometry.blade(out,pose,p,p.add(right.scale((i-1)*.13)).subtract(up.scale(.16)),normal,.045,fade*.65f,edge,r,g,b);
            }
        } else {
            WeaponContactGeometry.fourDiamonds(out,pose,c,right,up,normal,1.15f,fade,edge,r,g,b);
            for(int sign:new int[]{-1,1}) {
                Vec3[] path=new Vec3[8];
                for(int i=0;i<8;i++){double u=i/7.0;path[i]=c.add(right.scale(sign*(.2+t*.6+u*.28))).add(up.scale(.13+Math.sin(u*Math.PI)*.11-u*.19));}
                WeaponContactGeometry.ribbon(out,pose,path,normal,.024,fade*.6f,edge,r,g,b);
            }
        }
    }
}
