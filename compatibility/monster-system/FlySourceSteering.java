package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Fly.cs updateAnimation. Velocities remain in source pixels; positive source Y velocity moves north. */
public final class FlySourceSteering {
    public double xVelocity,yVelocity,rotation,spawnRemaining=1000,hitRemaining;
    public boolean turningRight;
    public int slipperiness=24;
    public void animate(double elapsed,double dx,double dy,boolean active,RandomSource random) {
        if(hitRemaining>=0)hitRemaining-=elapsed;
        if(spawnRemaining>=0){spawnRemaining-=elapsed;return;}
        if(!active)return;
        double xSlope=-dx,ySlope=dy,t=Math.max(1,Math.abs(xSlope)+Math.abs(ySlope));
        if(t<64){xVelocity=Math.clamp(xVelocity*1.1,-7,7);yVelocity=Math.clamp(yVelocity*1.1,-7,7);}
        xSlope/=t;ySlope/=t;
        if(hitRemaining<=0) {
            double target=Math.atan2(-ySlope,xSlope)-Math.PI/2;
            double delta=Math.abs(target)-Math.abs(rotation);
            if(delta>Math.PI*7/8&&random.nextBoolean())turningRight=true;
            else if(delta<Math.PI/8)turningRight=false;
            rotation+=(turningRight?-1:1)*Math.signum(target-rotation)*Math.PI/64;
            rotation%=Math.PI*2;hitRemaining=5+random.nextInt(-1,2);
        }
        double accel=Math.min(7,Math.max(2,7-t/64/2));
        xSlope=Math.cos(rotation+Math.PI/2);ySlope=-Math.sin(rotation+Math.PI/2);
        xVelocity+=-xSlope*accel/6+random.nextInt(-10,10)/100.;
        yVelocity+=-ySlope*accel/6+random.nextInt(-10,10)/100.;
        if(Math.abs(xVelocity)>Math.abs(-xSlope*7))xVelocity-=-xSlope*accel/6;
        if(Math.abs(yVelocity)>Math.abs(-ySlope*7))yVelocity-=-ySlope*accel/6;
    }
    public float minecraftYaw(){return (float)Math.toDegrees(rotation)-180;}
    public void decay() {
        xVelocity*=1-1./slipperiness;yVelocity*=1-1./slipperiness;
        if(Math.abs(xVelocity)<=.05)xVelocity=0;if(Math.abs(yVelocity)<=.05)yVelocity=0;
    }
    public CompoundTag save() {
        var t=new CompoundTag();t.putDouble("X",xVelocity);t.putDouble("Y",yVelocity);t.putDouble("Rotation",rotation);
        t.putDouble("Spawn",spawnRemaining);t.putDouble("Hit",hitRemaining);t.putBoolean("Right",turningRight);t.putInt("Slip",slipperiness);return t;
    }
    public void load(CompoundTag t) {
        xVelocity=t.getDouble("X");yVelocity=t.getDouble("Y");rotation=t.getDouble("Rotation");spawnRemaining=t.getDouble("Spawn");
        hitRemaining=t.getDouble("Hit");turningRight=t.getBoolean("Right");slipperiness=t.getInt("Slip");
    }
}
