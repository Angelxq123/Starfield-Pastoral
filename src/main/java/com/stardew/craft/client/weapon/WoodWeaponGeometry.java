package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Wood club wind stays at waist height; mallet impact compresses, then throws up short splinters. */
public final class WoodWeaponGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private WoodWeaponGeometry() {}
    public static void draw(VertexConsumer out,Matrix4f pose,Vec3 center,int phase,float age,float yaw,boolean edge) {
        if(age<0||age>=8)return;float t=age/8,fade=(1-t)*(1-t*.35f);
        int r=edge?42:phase==2?245:221,g=edge?29:phase==2?214:221,b=edge?20:phase==2?157:206;
        double facing=Math.toRadians(yaw+90);
        if(phase<2) {
            double radius=1.3+(phase==1?1.2:.9)*(1-Math.pow(1-t,3));
            for(int arc=0;arc<3;arc++) {
                Vec3[] path=new Vec3[17];
                for(int i=0;i<path.length;i++) {
                    double u=i/16.0,a=facing+arc*Math.PI*2/3+t*2.1+u*Math.PI*.57;
                    path[i]=center.add(Math.cos(a)*radius,.7+Math.sin(u*Math.PI)*(phase==1?.2:.1),Math.sin(a)*radius);
                }
                WeaponContactGeometry.ribbon(out,pose,path,UP,phase==1?.19:.11,fade,edge,r,g,b);
            }
            return;
        }
        Vec3 c=center.add(0,.06,0);float flash=Math.max(0,1-age/3);
        WeaponContactGeometry.blade(out,pose,c.add(-.95,0,0),c.add(.95,0,0),UP,.25,flash,edge,r,g,b);
        WeaponContactGeometry.blade(out,pose,c,c.add(0,.85,0),new Vec3(1,0,0),.17,flash,edge,r,g,b);
        for(int ray=0;ray<6;ray++) {
            double a=facing+ray*2.399963,spread=.15+t*(1.1+(ray%2)*.5);
            Vec3 radial=new Vec3(Math.cos(a),0,Math.sin(a));Vec3 p=c.add(radial.scale(spread));
            Vec3 normal=radial.cross(UP);double lift=Math.sin(t*Math.PI)*(.4+(ray%3)*.15);
            WeaponContactGeometry.blade(out,pose,p.add(0,lift,0),p.add(radial.scale(.16)).add(0,lift+.23,0),normal,.045,fade,edge,r,g,b);
            WeaponContactGeometry.blade(out,pose,c.add(radial.scale(.18)),c.add(radial.scale(.4+t*2)),UP,.075,fade*.65f,edge,edge?42:186,edge?29:147,edge?20:92);
        }
    }
}
