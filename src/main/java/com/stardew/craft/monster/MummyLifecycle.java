package com.stardew.craft.monster;
/** Source callbacks: frame19 START after300ms, ten-second wait, reversed four100ms frames. */
public final class MummyLifecycle {
 public static final int WALK=0,CRUMBLE=1,DOWNED=2,REVIVE=3;
 private int phase=WALK,elapsed,remaining;
 public int phase(){return phase;}public int elapsed(){return elapsed;}public int remaining(){return remaining;}
 public boolean collapsed(){return phase==CRUMBLE||phase==DOWNED;}
 public boolean shaking(){return phase==DOWNED&&remaining<2000;}
 public void crumble(){phase=CRUMBLE;elapsed=0;remaining=10000;}
 public boolean tick(int milliseconds){int old=phase;elapsed+=milliseconds;
  if(phase==CRUMBLE&&elapsed>=300){phase=DOWNED;elapsed=0;}
  else if(phase==DOWNED){remaining=Math.max(0,remaining-milliseconds);if(remaining==0){phase=REVIVE;elapsed=0;}}
  else if(phase==REVIVE&&elapsed>=400){phase=WALK;elapsed=0;}
  return old!=phase;
 }
 public static net.minecraft.world.phys.AABB collisionBox(double x,double y,double z,float yaw,float scale,int phase){
  double a=phase==WALK?.51:phase==DOWNED?.54:.66,b=phase==WALK?.45:phase==DOWNED?.42:1.14,angle=Math.toRadians(180-yaw),c=Math.abs(Math.cos(angle)),s=Math.abs(Math.sin(angle));
  double dx=(a*c+b*s)*scale,dz=(a*s+b*c)*scale;return new net.minecraft.world.phys.AABB(x-dx,y,z-dz,x+dx,y+(phase==DOWNED?.46:1.84)*scale,z+dz);
 }
 public void load(int phase,int elapsed,int remaining){this.phase=phase;this.elapsed=elapsed;this.remaining=remaining;}
}
