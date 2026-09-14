package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SlammerDwarfRules {
    public static final String LIFT="slammer_upheaval", RUSH="slammer_rampage";
    public static final String PISTON="dwarf_hammer_rebound", FAULT="dwarf_hammer_faultline";
    public static final String SLAMMER_SWING="the_slammer_swing", DWARF_SWING="dwarf_hammer_swing";
    private SlammerDwarfRules() {}
    public static boolean isWeapon(String id){return "the_slammer".equals(id)||"dwarf_hammer".equals(id);}
    public static boolean supports(String weapon,String id){return "the_slammer".equals(weapon)?LIFT.equals(id)||RUSH.equals(id):"dwarf_hammer".equals(weapon)&&(PISTON.equals(id)||FAULT.equals(id));}
    public static int count(String id){return RUSH.equals(id)?4:PISTON.equals(id)?2:FAULT.equals(id)?3:1;}
    public static int hitTick(String id,int phase){return RUSH.equals(id)?new int[]{6,12,18,26}[phase]:PISTON.equals(id)?5+phase*4:FAULT.equals(id)?8+phase*6:6;}
    public static int duration(String id){return RUSH.equals(id)?32:FAULT.equals(id)?24:14;}
    public static int animationTicks(String id){return FAULT.equals(id)?12:duration(id);}
    public static int cooldown(String id){return RUSH.equals(id)?20:FAULT.equals(id)?22:8;}
    public static double radius(String id){return FAULT.equals(id)?2:3;}
    public static double distance(int phase){return 1+phase*2;}
    public static float multiplier(String id,int phase){return RUSH.equals(id)?phase==3?2f:1.1f:PISTON.equals(id)?phase==0?2.2f:.8f:FAULT.equals(id)?4:2;}
    public static String hitId(String id,int phase){return phase==0?id:id+"_"+phase;}
    public static SkillContext damageContext(String id,int phase){return SkillContext.builder().skillId(hitId(id,phase))
            .tier(RUSH.equals(id)||FAULT.equals(id)?SkillContext.SkillTier.MAJOR:SkillContext.SkillTier.MINOR)
            .damageMultiplier(multiplier(id,phase)).defaultKnockback(false).build();}
    public static boolean inArc(String id,double forward,double side){return RUSH.equals(id)||FAULT.equals(id)
            ||HeavyHammerRules.inArc(forward,side,radius(id),PISTON.equals(id)?60:80);}
    /** A phase is claimed before callbacks. Faultline hits each target at most once over the whole cast. */
    public static final class Sequence {
        private final String skill;
        private int next;
        private long last=Long.MIN_VALUE;
        private final Set<UUID> struck=new HashSet<>();
        public Sequence(String skill){this.skill=skill;}
        public int advance(long age){if(age==last)return -1;last=age;if(next>=count(skill)||age<hitTick(skill,next))return -1;return next++;}
        public boolean claim(UUID target){return !FAULT.equals(skill)||struck.add(target);}
    }
}
