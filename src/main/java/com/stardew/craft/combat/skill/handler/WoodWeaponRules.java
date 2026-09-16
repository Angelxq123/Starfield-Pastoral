package com.stardew.craft.combat.skill.handler;

import net.minecraft.world.phys.Vec3;

/** Authored gameplay values for the second pair of clubs. */
public final class WoodWeaponRules {
    public static final String WHIRL="wood_club_whirl", LEAP="wood_mallet_leap";
    public static final String CLUB_SWING="wood_club_swing", MALLET_SWING="wood_mallet_swing";
    public static final int COOLDOWN_TICKS=160, HOP_TICKS=7;
    private WoodWeaponRules() {}
    public static boolean isWeapon(String weapon){return "wood_club".equals(weapon)||"wood_mallet".equals(weapon);}
    public static boolean supports(String weapon,String skill){return "wood_club".equals(weapon)?WHIRL.equals(skill):"wood_mallet".equals(weapon)&&LEAP.equals(skill);}
    public static int duration(String skill){return WHIRL.equals(skill)?18:14;}
    public static int hitTick(String skill,int phase){return WHIRL.equals(skill)?phase==0?5:12:7;}
    public static float multiplier(String skill,int phase){return WHIRL.equals(skill)?phase==0?.7f:1.3f:2;}
    /** One impulse; vanilla travel owns gravity, drag, collision and landing afterwards. */
    public static Vec3 leapVelocity(Vec3 current,Vec3 forward) {
        Vec3 horizontal=forward.multiply(1,0,1).normalize().scale(.22);
        return new Vec3(current.x+horizontal.x,Math.max(current.y,.23),current.z+horizontal.z);
    }
}
