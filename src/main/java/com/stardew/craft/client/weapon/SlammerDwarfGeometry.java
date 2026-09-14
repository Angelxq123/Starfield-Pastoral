package com.stardew.craft.client.weapon;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import static com.stardew.craft.combat.skill.handler.SlammerDwarfRules.*;

public final class SlammerDwarfGeometry {
    private static final Vec3 UP=new Vec3(0,1,0);
    private SlammerDwarfGeometry() {}
    public static String base(String id){for(String skill:new String[]{LIFT,RUSH,PISTON,FAULT})for(int p=0;p<count(skill);p++)if(hitId(skill,p).equals(id))return skill;return "";}
    public static void draw(VertexConsumer out,Matrix4f pose,Vec3 c,String id,int phase,float age,float yaw,boolean edge) {
        if(age<0||age>=8)return;String skill=base(id);if(skill.isEmpty())return;
        float t=age/8,fade=(1-t)*(1-t*.35f);double a=Math.toRadians(yaw);
        Vec3 forward=new Vec3(-Math.sin(a),0,Math.cos(a)),right=new Vec3(forward.z,0,-forward.x);
        int r=edge?43:FAULT.equals(skill)||PISTON.equals(skill)?248:235,g=edge?29:FAULT.equals(skill)||PISTON.equals(skill)?189:210,b=edge?24:FAULT.equals(skill)||PISTON.equals(skill)?98:177;
        if(LIFT.equals(skill)) {
            for(int layer=0;layer<2;layer++) {
                Vec3[] path=new Vec3[19];
                for(int i=0;i<path.length;i++){double u=i/18.0;path[i]=c.add(forward.scale(.6+Math.sin(u*Math.PI*.8)*1.8)).add(right.scale((u-.5)*.55+layer*.18)).add(0,.25+u*1.8,0);}
                WeaponContactGeometry.ribbon(out,pose,path,right,layer==0?.23:.10,fade*(layer==0?1:.45f),edge,r,g,b);
            }
        } else if(PISTON.equals(skill)) {
            Vec3 point=c.add(forward.scale(1.8+Math.min(t,.4)*.4)).add(0,1,0);
            for(int layer=0;layer<2;layer++) {
                Vec3 center=point.subtract(forward.scale(layer*.32));double w=(phase==0?.56:.42)*(1+t*.3);
                Vec3[] corners={center.add(right.scale(w)),center.add(UP.scale(w*.65)),center.subtract(right.scale(w)),center.subtract(UP.scale(w*.65))};
                for(int n=0;n<4;n++)WeaponContactGeometry.blade(out,pose,corners[n],corners[(n+1)%4],forward,.065,fade*(layer==0?1:.4f),edge,r,g,b);
                if(layer==0)for(Vec3 corner:corners) {
                    WeaponContactGeometry.blade(out,pose,corner.subtract(forward.scale(.65)),corner.add(forward.scale(.2)),
                            corner.subtract(center).normalize(),.045,fade*.8f,edge,r,g,b);
                }
            }
            WeaponContactGeometry.blade(out,pose,point.subtract(forward.scale(.8)),point.add(forward.scale(.24)),UP,.12,fade,edge,r,g,b);
        } else {
            boolean fault=FAULT.equals(skill),last=!fault&&phase==3;Vec3 center=c.add(0,.10,0);
            float flash=Math.max(0,1-age/3.5f);double breadth=fault?1.15:last?1.30:.9;
            // Orthogonal compressed flashes read as a pressure face, with a vertical rebound core.
            WeaponContactGeometry.blade(out,pose,center.subtract(right.scale(breadth)),center.add(right.scale(breadth)),forward,.26,flash,edge,r,g,b);
            WeaponContactGeometry.blade(out,pose,center,center.add(UP.scale(fault?1.05:last?1.35:.8)),right,.18,flash,edge,r,g,b);
            for(int ray=0;ray<(fault?4:6);ray++) {
                double angle=fault?Math.PI/4+ray*Math.PI/2:ray*2.399963;
                Vec3 radial=right.scale(Math.cos(angle)).add(forward.scale(Math.sin(angle)));
                double reach=(fault?1.55:last?2.5:1.9)*(1-Math.pow(1-t,3));
                Vec3 foot=center.add(radial.scale(reach)),peak=foot.add(UP.scale(Math.sin(t*Math.PI)*(fault?.7:.75)));
                WeaponContactGeometry.blade(out,pose,foot,peak.add(radial.scale(.2)),radial.cross(UP),fault?.10:.065,fade,edge,r,g,b);
                if(fault) {
                    Vec3 end=center.add(right.scale(Math.cos(angle+Math.PI/2)*reach)).add(forward.scale(Math.sin(angle+Math.PI/2)*reach));
                    WeaponContactGeometry.blade(out,pose,foot,end,UP,.055,fade*.55f,edge,r,g,b);
                } else {
                    Vec3[] path=new Vec3[9];for(int i=0;i<9;i++){double u=i/8.0;path[i]=center.add(radial.scale(.3+u*reach)).add(0,Math.sin(u*Math.PI)*.16,0);}
                    WeaponContactGeometry.ribbon(out,pose,path,UP,.09,fade*.65f,edge,r,g,b);
                }
            }
        }
    }
    public static void contact(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,String id,int phase,float age,boolean edge) {
        if(age<0||age>=8)return;String skill=base(id);float fade=(1-age/8)*(1-age/8);
        boolean dwarf=PISTON.equals(skill)||FAULT.equals(skill);int r=edge?43:245,g=edge?29:dwarf?203:221,b=edge?24:dwarf?132:184;
        if(PISTON.equals(skill)) {
            double d=(phase==0?.48:.32)*(1+age*.02);Vec3[] points={c.add(right.scale(d)),c.add(up.scale(d*.55)),c.subtract(right.scale(d)),c.subtract(up.scale(d*.55))};
            for(int i=0;i<4;i++)WeaponContactGeometry.blade(out,pose,points[i],points[(i+1)%4],normal,.075,fade,edge,r,g,b);
            WeaponContactGeometry.blade(out,pose,c.subtract(right.scale(.3)),c.add(right.scale(.3)),normal,.085,fade,edge,r,g,b);
        } else if(LIFT.equals(skill)) {
            WeaponContactGeometry.slenderCross(out,pose,c,right,up,normal,.7f,fade,edge,r,g,b);
            for(int sign:new int[]{-1,1})WeaponContactGeometry.blade(out,pose,c.add(right.scale(sign*.1)),c.add(right.scale(sign*.3)).add(up.scale(.65)),normal,.07,fade*.65f,edge,r,g,b);
        } else {
            WeaponContactGeometry.fourDiamonds(out,pose,c,right,up,normal,FAULT.equals(skill)||phase==3?1.15f:.8f,fade,edge,r,g,b);
        }
    }
}
