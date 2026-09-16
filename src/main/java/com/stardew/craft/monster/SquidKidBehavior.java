package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
/** Ordinary SquidKid behavior only; its four-shot dangerous branch remains disabled. */
public final class SquidKidBehavior {
    public enum Action { NONE, FIRE, NUDGE }
    private int cooldown,slipperiness=2,expression;private double x,z;
    public int cooldown(){return cooldown;}public int expression(){return expression;}public double x(){return x;}public double z(){return z;}
    public void hit(){expression=3;}
    public Action step(boolean near,double dx,double dz,RandomSource r){
        cooldown=Math.max(0,cooldown-16);
        if(near&&cooldown==0&&r.nextDouble()<.01){expression=3;cooldown=r.nextInt(1200,3500);return Action.FIRE;}
        if(cooldown!=0&&r.nextDouble()<.02){if(near){slipperiness=8;double length=Math.hypot(dx,dz);if(length>0)knockback((int)(dx/length*8),(int)(dz/length*8));}return Action.NUDGE;}
        return Action.NONE;
    }
    public void animate(RandomSource r){if(expression!=0&&r.nextDouble()<.1)expression=0;if(r.nextDouble()<.01)expression++;expression%=4;}
    public void knockback(double vx,double vz){if(Math.abs(vx)>Math.abs(x))x=vx;if(Math.abs(vz)>Math.abs(z))z=vz;}
    public void decay(boolean collided){double divisor=slipperiness*(collided?4:1);x-=x/divisor;z-=z/divisor;if(Math.abs(x)<=.05)x=0;if(Math.abs(z)<=(collided?.051:.05))z=0;}
    public void reflect(boolean horizontal,boolean vertical){if(horizontal)x=-x;if(vertical)z=-z;}
    public static double lift(long tick,int substep){int millis=(int)(Math.floorMod(tick,20)*50+substep*1000./60);int offset=(int)(Math.sin(millis/2000.*Math.PI*2)*15);return (43-offset)/64.;}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Cooldown",cooldown);t.putInt("Slipperiness",slipperiness);t.putInt("Expression",expression);t.putDouble("X",x);t.putDouble("Z",z);return t;}
    public void load(CompoundTag t){cooldown=t.getInt("Cooldown");slipperiness=t.contains("Slipperiness")?t.getInt("Slipperiness"):2;expression=t.getInt("Expression");x=t.getDouble("X");z=t.getDouble("Z");}
}
