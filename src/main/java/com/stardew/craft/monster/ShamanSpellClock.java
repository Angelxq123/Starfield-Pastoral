package com.stardew.craft.monster;
import net.minecraft.nbt.CompoundTag;
/** ShadowShaman.behaviorAtGameTick branch order and integer-millisecond timers at a 60Hz source step. */
public final class ShamanSpellClock {
    public enum Action { NONE, SPOTTED, FLEE, FIND_PATH, START_CAST, RELEASE, FORGET, WANDER }
    private boolean spotted,casting,walking=true;private int cooldown=1500;
    public boolean spotted(){return spotted;}public boolean casting(){return casting;}public boolean walking(){return walking;}public int cooldown(){return cooldown;}
    public Action step(boolean detectable,boolean inRange,boolean lowHealth,boolean hasPath,boolean sight,double castRoll){
        if(!spotted&&detectable){spotted=true;return Action.SPOTTED;}
        if(casting){walking=false;cooldown-=16;if(cooldown>0)return Action.NONE;casting=false;cooldown=1500;walking=true;return Action.RELEASE;}
        if(spotted){
            if(!inRange){walking=false;spotted=false;return Action.FORGET;}
            Action result=Action.NONE;
            if(lowHealth){walking=false;result=Action.FLEE;}
            else if(!hasPath&&!sight)result=Action.FIND_PATH;
            else if(cooldown<=0&&castRoll<.02){casting=true;walking=false;cooldown=500;result=Action.START_CAST;}
            cooldown-=16;return result;
        }
        return Action.WANDER;
    }
    public void pathFailed(){spotted=false;}
    public void delayFromHit(){if(casting)cooldown+=200;}
    public CompoundTag save(){var t=new CompoundTag();t.putBoolean("Spotted",spotted);t.putBoolean("Casting",casting);t.putBoolean("Walking",walking);t.putInt("Cooldown",cooldown);return t;}
    public void load(CompoundTag t){spotted=t.getBoolean("Spotted");casting=t.getBoolean("Casting");walking=!t.contains("Walking")||t.getBoolean("Walking");cooldown=t.contains("Cooldown")?t.getInt("Cooldown"):1500;}
}
