package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;

/** Grub.cs and AnimatedSprite.Animate: source milliseconds and carried sprite frame/timer. */
public final class GrubLifecycle {
    public static final int MOBILE=0, RETREAT=1, FORM=2, PUPA=3;
    private double remaining=2000, spriteTimer;
    private int frame,phase;
    public int phase(){return phase;}
    public double remaining(){return remaining;}
    public int frame(){return frame;}
    public boolean ready(){return phase==PUPA&&remaining<=0;}
    public void step(double elapsed,float health,int sourceMaxHealth,boolean moving,int facing) {
        if(phase==PUPA){remaining-=elapsed;return;}
        if(health<=sourceMaxHealth/2-2) {
            remaining-=elapsed;
            if(remaining<=0) {
                phase=FORM;animate(elapsed,16,125);
                if(frame==19){phase=PUPA;remaining=4500;}
                return;
            }
            phase=RETREAT;
        } else phase=MOBILE;
        // Directional animation is advanced by MovePosition, including the retreat.
        if(moving)animate(elapsed,facing==0?8:facing==2?0:facing*4,195);
    }
    private void animate(double elapsed,int start,double interval) {
        if(frame<start||frame>=start+4)frame=start+Math.floorMod(frame,4);
        spriteTimer+=elapsed;
        if(spriteTimer>interval){frame++;spriteTimer=0;if(frame>=start+4)frame=start;}
    }
    public CompoundTag save() {
        var t=new CompoundTag();t.putDouble("Remaining",remaining);t.putDouble("SpriteTimer",spriteTimer);
        t.putInt("Frame",frame);t.putInt("Phase",phase);return t;
    }
    public void load(CompoundTag t) {
        remaining=t.getDouble("Remaining");spriteTimer=t.getDouble("SpriteTimer");frame=t.getInt("Frame");phase=t.getInt("Phase");
    }
}
