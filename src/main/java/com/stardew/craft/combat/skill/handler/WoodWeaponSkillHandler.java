package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.HeavyHammerCombatEvents;
import com.stardew.craft.combat.network.WoodWeaponFxPayload;
import com.stardew.craft.combat.skill.*;
import com.stardew.craft.combat.skill.runtime.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import static com.stardew.craft.combat.skill.handler.WoodWeaponRules.*;

/** The whirl and velocity-driven hop each own one release snapshot. */
public final class WoodWeaponSkillHandler implements PostServerRuntimeWeaponSkillHandler {
    private final String skill;
    public WoodWeaponSkillHandler(String skill){this.skill=skill;}
    @Override public SkillValidation validate(SkillExecutionContext c) {
        if(c.hand()!=InteractionHand.MAIN_HAND || !supports(c.weaponId().getPath(),skill)
                || !c.player().isAlive() || c.player().isSpectator() || c.player().isPassenger()
                || WeaponSkillAnimationLock.isLocked(c.player(),c.nowTick())
                || YetiFreezeTracker.isMovementLocked(c.player(),c.nowTick())
                || LEAP.equals(skill)&&WeaponSkillMovementControl.isLocked(c.player(),c.nowTick())
                || WeaponSkillRuntime.hasActive(c.player().getUUID(),c.skillId()))
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        return WeaponSkillCooldowns.isOnCooldown(c.player(),c.weaponId().getPath(),skill,c.nowTick())
                ?SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN):SkillValidation.accept();
    }
    @Override public void begin(SkillExecutionContext c,SkillInstance i) {
        WeaponSkillRuntime.commitCooldown(c,i,COOLDOWN_TICKS);
        State state=new State(c,skill);i.initializeExecutionState(state);
        i.registerCommittedEffect(()->{
            state.start(c.player());
            WeaponSkillAnimationLock.setLock(c.player(),c.nowTick(),duration(skill));
            WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(),c.weaponId().getPath(),skill,duration(skill));
        });
    }
    @Override public boolean completesImmediately(){return false;}
    @Override public SkillTickResult tick(SkillExecutionContext c,SkillInstance i){return i.requireExecutionState(State.class).valid(c.player())?SkillTickResult.CONTINUE:SkillTickResult.CANCEL;}
    @Override public SkillTickResult postServerTick(SkillExecutionContext c,SkillInstance i){return i.requireExecutionState(State.class).advance(c);}
    @Override public void finish(SkillExecutionContext c,SkillInstance i,SkillInstance.EndReason reason) {
        i.executionState(State.class).ifPresent(s->{s.cancel();if(c.nowTick()<s.started+duration(skill))WeaponSkillAnimationLock.clear(c.player());});
    }

    static final class State implements SkillInstance.ExecutionState {
        final long started;
        final String skill;
        final ItemStack held;
        final int slot;
        final Vec3 forward;
        int phase;
        boolean canceled;
        long lastTick=Long.MIN_VALUE;
        State(SkillExecutionContext c,String skill) {
            started=c.nowTick();this.skill=skill;held=c.player().getMainHandItem();slot=c.player().getInventory().selected;
            forward=HeavyHammerExecutionState.forward(c.player());
        }
        void start(ServerPlayer p){
            if(!LEAP.equals(skill)||!p.onGround())return;
            WeaponSkillMovementArbiter.revokeCurrent(p);
            p.setDeltaMovement(leapVelocity(p.getDeltaMovement(),forward));
            p.hasImpulse=true;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
        }
        boolean valid(ServerPlayer p){return !canceled&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&p.getInventory().selected==slot&&p.getMainHandItem()==held;}
        void cancel(){canceled=true;}
        SkillTickResult advance(SkillExecutionContext c) {
            if(!valid(c.player()) || YetiFreezeTracker.isMovementLocked(c.player(),c.nowTick()))return SkillTickResult.CANCEL;
            if(lastTick==c.nowTick())return SkillTickResult.CONTINUE;lastTick=c.nowTick();
            long age=c.nowTick()-started;
            int count=WHIRL.equals(skill)?2:1;
            if(phase<count&&age>=hitTick(skill,phase)&&(!LEAP.equals(skill)||c.player().onGround())) {
                int stage=phase++; // Claim the phase before damage callbacks can re-enter.
                Vec3 center=WHIRL.equals(skill)?c.player().position():HeavyHammerExecutionState.ground(c.player(),c.player().position().add(forward.scale(.75)));
                if(center!=null) {
                    String hitId=stage==1?skill+"_finish":skill;
                    for(LivingEntity target:HeavyHammerExecutionState.targets(c,center,2.5)) {
                        if(!valid(c.player()))return SkillTickResult.CANCEL;
                        WeaponSkillDamage.apply(c.player(),target,SkillContext.builder().skillId(hitId).tier(SkillContext.SkillTier.MINOR)
                                .damageMultiplier(multiplier(skill,stage)).defaultKnockback(false).build(),c.weaponSnapshot(),c.nowTick()+2,
                                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,WeaponSkillDamage.HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE);
                    }
                    WoodWeaponFxPayload.send(c.player(),hitId,center,stage==1?1:LEAP.equals(skill)?2:0,-1);
                }
            }
            return age>=duration(skill)?SkillTickResult.COMPLETE:SkillTickResult.CONTINUE;
        }
    }

    public static void appliedHit(ResolvedWeaponHit hit) {
        if(!hit.dealtPositiveDamage()||!(hit.attacker() instanceof ServerPlayer p)||!isWeapon(hit.weaponIdentity().logicId()))return;
        String id=hit.authoredSkillContext().getSkillId();
        if(!(WHIRL.equals(id)||(WHIRL+"_finish").equals(id)||LEAP.equals(id)))return;
        LivingEntity target=hit.target();long now=hit.gameTick();boolean leap=LEAP.equals(id),heavy=id.endsWith("_finish");
        if(target.isAlive()&&target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)<.8&&!WeaponSkillMovementControl.isLocked(target,now)) {
            int ticks=YetiFreezeTracker.applyWithEquipmentProtection(target,now,leap?5:heavy?3:2,YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER);
            if(ticks>0) {
                if(leap&&target.onGround())HeavyHammerCombatEvents.queueLift(target,now+ticks,.21);
                else if(heavy)HeavyHammerCombatEvents.queuePush(target,p.position(),.3f,now+ticks);
            }
        }
        WoodWeaponFxPayload.send(p,id,target.getBoundingBox().getCenter(),leap?5:heavy?4:3,target.getId());
    }
}
