package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;

/** Independent frame branches from Duggy.cs. Crossing 4 or 8 advances the next branch in the same step. */
public final class DuggyLifecycle {
    private int frame;
    private double timer,interval=175;
    private boolean hidden=true;
    public boolean hidden(){return hidden;}
    public int frame(){return frame;}
    public double cursor(){return frame+timer/interval;}
    public int damage(){return !hidden&&frame>=4?8:0;}
    /** Returns true when the source removes objects and resets the Back tile to index zero. */
    public boolean step(double elapsed,boolean mayEmerge){
        if(frame<4){
            if(hidden&&!mayEmerge)return false;
            hidden=false;interval=100;animate(elapsed);
        }
        if(frame>=4&&frame<8){animate(elapsed);interval=220;}
        if(frame>=8)animate(elapsed);
        if(frame>=10){hidden=true;frame=0;return true;}
        return false;
    }
    private void animate(double elapsed){timer+=elapsed;if(timer>interval){frame++;timer=0;}}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Frame",frame);t.putDouble("Timer",timer);t.putDouble("Interval",interval);t.putBoolean("Hidden",hidden);return t;}
    public void load(CompoundTag t){frame=t.getInt("Frame");timer=t.getDouble("Timer");interval=t.getDouble("Interval");hidden=t.getBoolean("Hidden");}
}
