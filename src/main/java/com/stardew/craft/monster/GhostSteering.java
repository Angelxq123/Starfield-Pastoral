package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
/** Ghost.cs: constant t=400, no Fly spawn timer, no invented post-hit turn lock. */
public final class GhostSteering {
    private double x,y,rotation;
    private boolean right;
    public double x(){return x;}
    public double y(){return y;}
    public void animate(double dx,double dz,RandomSource r){
        double target=Math.atan2(-dz,-dx)-Math.PI/2,delta=Math.abs(target)-Math.abs(rotation);
        if(delta>Math.PI*7/8&&r.nextBoolean())right=true;else if(delta<Math.PI/8)right=false;
        rotation+=(right?-1:1)*Math.signum(target-rotation)*Math.PI/64;rotation%=Math.PI*2;
        double ax=-Math.cos(rotation+Math.PI/2),ay=Math.sin(rotation+Math.PI/2),accel=1.875/6;
        x+=ax*accel+r.nextInt(-10,10)/100.;y+=ay*accel+r.nextInt(-10,10)/100.;
        if(Math.abs(x)>Math.abs(ax*5))x-=ax*accel;if(Math.abs(y)>Math.abs(ay*5))y-=ay*accel;
    }
    public void decay(){x*=.875;y*=.875;if(Math.abs(x)<=.05)x=0;if(Math.abs(y)<=.05)y=0;}
    public void knockback(double vx,double vy){if(Math.abs(vx)>Math.abs(x))x=vx;if(Math.abs(vy)>Math.abs(y))y=vy;}
    public CompoundTag save(){var t=new CompoundTag();t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Rotation",rotation);t.putBoolean("Right",right);return t;}
    public void load(CompoundTag t){x=t.getDouble("X");y=t.getDouble("Y");rotation=t.getDouble("Rotation");right=t.getBoolean("Right");}
}
