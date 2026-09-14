package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;

/** Authored values for the lead rod's press and the kudgel's critical sweep. */
public final class IronClubRules {
    public static final String PRESS="lead_rod_press", SWEEP="kudgel_sweep";
    public static final String LEAD_SWING="lead_rod_swing", KUDGEL_SWING="kudgel_swing";
    private IronClubRules() {}
    public static boolean isWeapon(String id){return "lead_rod".equals(id)||"kudgel".equals(id);}
    public static boolean supports(String weapon,String skill){return "lead_rod".equals(weapon)?PRESS.equals(skill):"kudgel".equals(weapon)&&SWEEP.equals(skill);}
    public static int duration(String skill){return PRESS.equals(skill)?20:22;}
    public static int hitTick(String skill){return PRESS.equals(skill)?9:11;}
    public static int cooldown(String skill){return PRESS.equals(skill)?10:12;}
    public static double radius(String skill){return PRESS.equals(skill)?2.5:3.75;}
    public static boolean inArc(String skill,double forward,double side){return HeavyHammerRules.inArc(forward,side,radius(skill),PRESS.equals(skill)?70:110);}
    public static SkillContext damageContext(String skill) {
        return SkillContext.builder().skillId(skill).tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(PRESS.equals(skill)?2.4f:1f).guaranteedCrit(SWEEP.equals(skill))
                .defaultKnockback(false).build();
    }
}
