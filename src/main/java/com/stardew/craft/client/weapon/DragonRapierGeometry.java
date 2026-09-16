package com.stardew.craft.client.weapon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.combat.skill.handler.DragonRapierRules.*;
public final class DragonRapierGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private DragonRapierGeometry() {}
    public static String base(String id){if((RIPOSTE+"_counter").equals(id)||(RIPOSTE+"_guard").equals(id))return RIPOSTE;
        for(String skill:new String[]{JAW,BREATH,RIPOSTE})for(int p=0;p<count(skill);p++)if(hitId(skill,p,false).equals(id))return skill;return "";}
    public static void draw(VertexConsumer out,Matrix4f pose,Vec3 c,String id,int phase,float age,float yaw,boolean edge) {
        if(age<0||age>=8)return;String skill=base(id);if(skill.isEmpty())return;
        float t=age/8,fade=(1-t)*(1-t*.35f);double a=Math.toRadians(yaw);Vec3 forward=new Vec3(-Math.sin(a),0,Math.cos(a)),right=new Vec3(forward.z,0,-forward.x);
        if(RIPOSTE.equals(skill)) {
            if(id.endsWith("_guard")) {
                Vec3[] path=new Vec3[19];for(int i=0;i<19;i++){double angle=-1.2+i/18.0*2.4;path[i]=c.add(forward.scale(.8)).add(right.scale(Math.cos(angle)*.65)).add(0,1+Math.sin(angle)*.65,0);}
                WeaponContactGeometry.ribbon(out,pose,path,forward,.075,fade,edge,edge?24:189,edge?34:220,edge?44:242);
            } else {
                Vec3 from=c.add(forward.scale(.6)).add(0,1.05,0),to=c.add(forward.scale(3.45)).add(0,1.05,0);
                WeaponContactGeometry.blade(out,pose,from,to,UP,.065,fade,edge,edge?24:181,edge?34:221,edge?44:246);
                WeaponContactGeometry.blade(out,pose,from,to,right,.04,fade*.8f,edge,edge?24:219,edge?34:236,edge?44:248);
            }
            return;
        }
        if(JAW.equals(skill)||phase==0) {
            // Paired opposing fangs converge toward the contact, retaining volume above the ground.
            for(int sign:new int[]{-1,1}) {
                Vec3[] path=new Vec3[23];
                for(int i=0;i<23;i++){double u=i/22.0;path[i]=c.add(forward.scale(.45+u*2.35)).add(right.scale(sign*Math.sin(u*Math.PI)*(1.05-t*.35))).add(0,.45+Math.sin(u*Math.PI)*.65,0);}
                WeaponContactGeometry.ribbon(out,pose,path,UP.add(forward.scale(.4)).normalize(),.24,fade,edge,edge?58:247,edge?30:219,edge?20:163);
            }
            Vec3 point=c.add(forward.scale(2.15)).add(0,.65,0);
            WeaponContactGeometry.blade(out,pose,point.add(0,-.5,0),point.add(0,.65,0),right,.15,Math.max(0,1-age/3),edge,edge?58:255,edge?30:226,edge?20:173);
        } else {
            for(int tongue=0;tongue<5;tongue++) {
                double angle=Math.toRadians((tongue-2)*17);Vec3 direction=forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
                Vec3[] path=new Vec3[22];
                for(int i=0;i<22;i++){double u=i/21.0;path[i]=c.add(direction.scale(.25+u*5.2)).add(right.scale(Math.sin(u*Math.PI*1.5+phase)*.12*u)).add(0,.22+Math.sin(u*Math.PI)*(.65+(tongue%2)*.2),0);}
                WeaponContactGeometry.ribbon(out,pose,path,UP.add(right.scale(.25)).normalize(),tongue==2?.22:.13,fade*(tongue==2?1:.65f),edge,edge?69:247,edge?27:181,edge?16:98);
            }
        }
    }
    public static void contact(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,String id,int phase,float age,boolean edge) {
        if(age<0||age>=8)return;String skill=base(id);float fade=(1-age/8)*(1-age/8);boolean rapier=RIPOSTE.equals(skill);
        int r=edge?rapier?24:58:rapier?211:250,g=edge?rapier?34:30:rapier?237:215,b=edge?rapier?44:20:rapier?249:146;
        if(rapier)WeaponContactGeometry.slenderCross(out,pose,c,right,up,normal,id.endsWith("_counter")?.8f:.6f,fade,edge,r,g,b);
        else if(phase==0)WeaponContactGeometry.fourDiamonds(out,pose,c,right,up,normal,JAW.equals(skill)?1.15f:1f,fade,edge,r,g,b);
        else for(int sign:new int[]{-1,1}) {
            Vec3[] path=new Vec3[11];for(int i=0;i<11;i++){double u=i/10.0;path[i]=c.add(right.scale(sign*Math.sin(u*Math.PI)*.3)).add(up.scale((u-.5)*.65));}
            WeaponContactGeometry.ribbon(out,pose,path,normal,.065,fade,edge,r,g,b);
        }
    }
}
