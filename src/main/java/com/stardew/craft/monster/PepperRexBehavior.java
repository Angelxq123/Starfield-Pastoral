package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
/** DinoMonster.behaviorAtGameTick. Timers reset on crossing zero; no catch-up volleys. */
public final class PepperRexBehavior {
 public static final int IDLE=0,PREPARE=1,FIRE=2;
 public enum Event { NONE, PREPARE, FIRST_SHOT, SHOT }
 private int untilAttack=2000,nextFire,totalFire,nextDirection,nextWander,facing=2;
 private boolean attacking,firing,wander,walking;
 public void initialize(RandomSource r){nextDirection=r.nextInt(1000,3000);nextWander=r.nextInt(1000,2000);}
 public int phase(){return attacking?(firing?FIRE:PREPARE):IDLE;}public int remaining(){return totalFire;}public int facing(){return facing;}public void facing(int d){facing=d;}public boolean walking(){return walking;}public boolean wander(){return wander;}public int cooldown(){return untilAttack;}
 public Event tick(int ms,boolean near,boolean close,int targetFacing,RandomSource r){
  if(attacking)walking=false;else if(near)walking=true;else{walking=false;nextDirection-=ms;nextWander-=ms;if(nextDirection<0){nextDirection=r.nextInt(500,1000);facing=Math.floorMod(facing+r.nextInt(3)-1,4);}if(nextWander<0){nextWander=wander?r.nextInt(1000,2000):r.nextInt(1000,3000);wander=!wander;}}
  untilAttack-=ms;
  if(!attacking&&close){firing=false;if(untilAttack<0){untilAttack=0;attacking=true;nextFire=500;totalFire=3000;return Event.PREPARE;}return Event.NONE;}
  if(totalFire<=0)return Event.NONE;
  if(!firing&&targetFacing>=0)facing=targetFacing;
  totalFire-=ms;Event result=Event.NONE;
  if(nextFire>0){nextFire-=ms;if(nextFire<=0){result=firing?Event.SHOT:Event.FIRST_SHOT;firing=true;nextFire=70;}}
  if(totalFire<=0){totalFire=0;nextFire=0;attacking=false;untilAttack=r.nextInt(1000,2000);}
  return result;
 }
 public double shotAngle(){double base=switch(facing){case 0->90;case 1->0;case 3->180;default->270;};return Math.toRadians(base+Math.sin(totalFire/1000.*Math.PI)*25);}
 public CompoundTag save(){var t=new CompoundTag();t.putInt("UntilAttack",untilAttack);t.putInt("NextFire",nextFire);t.putInt("TotalFire",totalFire);t.putInt("NextDirection",nextDirection);t.putInt("NextWander",nextWander);t.putInt("Facing",facing);t.putBoolean("Attacking",attacking);t.putBoolean("Firing",firing);t.putBoolean("Wander",wander);t.putBoolean("Walking",walking);return t;}
 public void load(CompoundTag t){untilAttack=t.getInt("UntilAttack");nextFire=t.getInt("NextFire");totalFire=t.getInt("TotalFire");nextDirection=t.getInt("NextDirection");nextWander=t.getInt("NextWander");facing=t.getInt("Facing");attacking=t.getBoolean("Attacking");firing=t.getBoolean("Firing");wander=t.getBoolean("Wander");walking=t.getBoolean("Walking");}
}
