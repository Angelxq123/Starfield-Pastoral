package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
/** Serpent.updateAnimation + Monster.MovePosition. Source pixels, inverted source Y, 60 Hz. */
public final class SerpentSourceSteering {
 private double x,y,rotation;private boolean right;private int hitMilliseconds;private int slipperiness=24;
 public double x(){return x;}public double z(){return -y;}public double rotation(){return rotation;}public int hitMilliseconds(){return hitMilliseconds;}public int slipperiness(){return slipperiness;}
 public void initialize(RandomSource r){slipperiness=24+r.nextInt(10);}
 public void elapsed(int ms){if(hitMilliseconds>=0)hitMilliseconds-=ms;}
 public void animate(double dx,double dz,RandomSource r){
  double t=Math.max(1,Math.abs(dx)+Math.abs(dz));
  if(t<64){x=Math.clamp(x*1.1,-7,7);y=Math.clamp(y*1.1,-7,7);}
  if(hitMilliseconds<=0){double target=Math.atan2(-dz,-dx)-Math.PI/2,delta=Math.abs(target)-Math.abs(rotation);if(delta>Math.PI*7/8&&r.nextBoolean())right=true;else if(delta<Math.PI/8)right=false;rotation+=(right?-1:1)*Math.signum(target-rotation)*Math.PI/64;rotation%=Math.PI*2;hitMilliseconds=5+r.nextInt(-1,2);}
  double a=Math.min(7,Math.max(2,7-t/128)),hx=-Math.cos(rotation+Math.PI/2),hy=Math.sin(rotation+Math.PI/2);
  x+=hx*a/6+r.nextInt(-10,10)/100.;y+=hy*a/6+r.nextInt(-10,10)/100.;
  if(Math.abs(x)>Math.abs(hx*7))x-=hx*a/6;if(Math.abs(y)>Math.abs(hy*7))y-=hy*a/6;
 }
 public void decay(){x=decay(x);y=decay(y);}private double decay(double v){v-=v/slipperiness;return Math.abs(v)<=.05?0:v;}
 public void hit(){hitMilliseconds=500;}
 public void knockback(double vx,double vz){if(Math.abs(vx)>Math.abs(x))x=vx;if(Math.abs(vz)>Math.abs(y))y=-vz;}
 public CompoundTag save(){var t=new CompoundTag();t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Rotation",rotation);t.putBoolean("Right",right);t.putInt("HitMillis",hitMilliseconds);t.putInt("Slipperiness",slipperiness);return t;}
 public void load(CompoundTag t){x=t.getDouble("X");y=t.getDouble("Y");rotation=t.getDouble("Rotation");right=t.getBoolean("Right");hitMilliseconds=t.getInt("HitMillis");slipperiness=t.getInt("Slipperiness");}
}
