package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.SkillContext;

public final class DragonRapierRules {
    public static final String JAW="dragontooth_club_jaw", BREATH="dragontooth_club_breath", RIPOSTE="rapier_riposte";
    public static final String CLUB_SWING="dragontooth_club_swing", RAPIER_SWING="rapier_swing";
    private DragonRapierRules() {}
    public static boolean isWeapon(String id){return "dragontooth_club".equals(id)||"rapier".equals(id);}
    public static boolean supports(String weapon,String skill){return "dragontooth_club".equals(weapon)?JAW.equals(skill)||BREATH.equals(skill):"rapier".equals(weapon)&&RIPOSTE.equals(skill);}
    public static int count(String id){return BREATH.equals(id)?5:1;}
    public static int hitTick(String id,int phase){return BREATH.equals(id)?8+phase*4:RIPOSTE.equals(id)?6:7;}
    public static int duration(String id){return BREATH.equals(id)?28:RIPOSTE.equals(id)?12:16;}
    public static int cooldown(String id){return BREATH.equals(id)?24:RIPOSTE.equals(id)?6:8;}
    public static double radius(String id,int phase){return RIPOSTE.equals(id)?3.5:BREATH.equals(id)&&phase>0?5.5:3.25;}
    public static boolean inArc(String id,int phase,double forward,double side){return RIPOSTE.equals(id)?forward>=0&&forward*forward+side*side<=3.5*3.5&&Math.abs(side)<=.55:
            HeavyHammerRules.inArc(forward,side,radius(id,phase),BREATH.equals(id)&&phase>0?40:80);}
    public static String hitId(String id,int phase,boolean counter){return RIPOSTE.equals(id)&&counter?id+"_counter":phase==0?id:id+"_"+phase;}
    public static float multiplier(String id,int phase,boolean counter){return JAW.equals(id)?2.8f:BREATH.equals(id)?phase==0?2.4f:.8f:counter?2.4f:1.8f;}
    public static SkillContext damageContext(String id,int phase,boolean counter){return SkillContext.builder().skillId(hitId(id,phase,counter))
            .tier(BREATH.equals(id)?SkillContext.SkillTier.MAJOR:SkillContext.SkillTier.MINOR)
            .damageMultiplier(multiplier(id,phase,counter)).defaultKnockback(false).build();}
    /** A short, one-use guard. It never delays or gates the automatic thrust. */
    public static final class Guard {
        private boolean consumed;
        public boolean consume(long age,double forward,double side,boolean eligible) {
            if(consumed||!eligible||age<0||age>=4||!HeavyHammerRules.inArc(forward,side,3.5,70))return false;
            consumed=true;return true;
        }
        public boolean counter(){return consumed;}
    }
}
