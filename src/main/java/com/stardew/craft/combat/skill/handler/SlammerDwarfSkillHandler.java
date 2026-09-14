package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.HeavyHammerCombatEvents;
import com.stardew.craft.combat.network.SlammerDwarfFxPayload;
import com.stardew.craft.combat.skill.*;
import com.stardew.craft.combat.skill.runtime.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import static com.stardew.craft.combat.skill.handler.SlammerDwarfRules.*;

/** Four deliberate releases; movement remains player-controlled, automatic waves retain the release direction. */
public final class SlammerDwarfSkillHandler implements PostServerRuntimeWeaponSkillHandler {
    private final String skill;
    public SlammerDwarfSkillHandler(String skill){this.skill=skill;}
    @Override public SkillValidation validate(SkillExecutionContext c) {
        if(c.hand()!=InteractionHand.MAIN_HAND||!supports(c.weaponId().getPath(),skill)||!c.player().isAlive()
                ||c.player().isSpectator()||c.player().isPassenger()||WeaponSkillAnimationLock.isLocked(c.player(),c.nowTick())
                ||YetiFreezeTracker.isMovementLocked(c.player(),c.nowTick())||WeaponSkillRuntime.hasActive(c.player().getUUID(),c.skillId()))
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        return WeaponSkillCooldowns.isOnCooldown(c.player(),c.weaponId().getPath(),skill,c.nowTick())
                ?SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN):SkillValidation.accept();
    }
    @Override public void begin(SkillExecutionContext c,SkillInstance i) {
        WeaponSkillRuntime.commitCooldown(c,i,cooldown(skill)*20);i.initializeExecutionState(new State(c,skill));
        i.registerCommittedEffect(()->{
            WeaponSkillAnimationLock.setLock(c.player(),c.nowTick(),animationTicks(skill));
            WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(),c.weaponId().getPath(),skill,animationTicks(skill));
        });
    }
    @Override public boolean completesImmediately(){return false;}
    @Override public SkillTickResult tick(SkillExecutionContext c,SkillInstance i){return i.requireExecutionState(State.class).valid(c)?SkillTickResult.CONTINUE:SkillTickResult.CANCEL;}
    @Override public SkillTickResult postServerTick(SkillExecutionContext c,SkillInstance i) {
        State s=i.requireExecutionState(State.class);if(!s.valid(c))return SkillTickResult.CANCEL;
        long age=c.nowTick()-s.started;int phase=s.sequence.advance(age);
        if(phase>=0) {
            Vec3 center;
            if(FAULT.equals(skill)) {
                center=s.advanceGround(c.player(),distance(phase));
                if(center==null)return SkillTickResult.COMPLETE;
            } else if(RUSH.equals(skill))center=HeavyHammerExecutionState.ground(c.player(),c.player().position().add(s.forward.scale(.65)));
            else center=c.player().position();
            if(center!=null) {
                for(var target:HeavyHammerExecutionState.targets(c,center,radius(skill))) {
                    Vec3 offset=target.position().subtract(center);
                    if(inArc(skill,offset.dot(s.forward),offset.dot(s.side))&&s.sequence.claim(target.getUUID()))
                        WeaponSkillDamage.apply(c.player(),target,damageContext(skill,phase),c.weaponSnapshot(),c.nowTick()+2,
                                WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,WeaponSkillDamage.HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE);
                }
                SlammerDwarfFxPayload.send(c.player(),hitId(skill,phase),center,phase,-1,s.yaw);
            }
        }
        return age>=duration(skill)?SkillTickResult.COMPLETE:SkillTickResult.CONTINUE;
    }
    @Override public void finish(SkillExecutionContext c,SkillInstance i,SkillInstance.EndReason reason) {
        i.executionState(State.class).ifPresent(s->{s.canceled=true;if(c.nowTick()<s.started+animationTicks(skill))WeaponSkillAnimationLock.clear(c.player());});
    }
    private static final class State implements SkillInstance.ExecutionState {
        final long started;final ItemStack held;final int slot;final Vec3 origin,forward,side;final float yaw;
        final Sequence sequence;boolean canceled;double travelled;Vec3 ground;
        State(SkillExecutionContext c,String skill){started=c.nowTick();held=c.player().getMainHandItem();slot=c.player().getInventory().selected;
            origin=c.player().position();forward=HeavyHammerExecutionState.forward(c.player());side=new Vec3(-forward.z,0,forward.x);
            yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));sequence=new Sequence(skill);}
        boolean valid(SkillExecutionContext c){var p=c.player();return !canceled&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()
                &&p.getMainHandItem()==held&&p.getInventory().selected==slot&&!YetiFreezeTracker.isMovementLocked(p,c.nowTick());}
        Vec3 advanceGround(ServerPlayer p,double distance) {
            if(ground==null){ground=HeavyHammerExecutionState.ground(p,origin);if(ground==null)return null;}
            while(travelled<distance) {
                double next=Math.min(distance,travelled+.25);
                Vec3 probe=origin.add(forward.scale(next));probe=new Vec3(probe.x,ground.y,probe.z);
                Vec3 contact=HeavyHammerExecutionState.ground(p,probe);
                if(contact==null||p.level().clip(new ClipContext(ground.add(0,.2,0),contact.add(0,.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)return null;
                travelled=next;ground=contact;
            }
            return ground;
        }
    }
    public static void appliedHit(ResolvedWeaponHit hit) {
        if(!hit.dealtPositiveDamage()||!(hit.attacker() instanceof ServerPlayer p)||!isWeapon(hit.weaponIdentity().logicId()))return;
        String actual=hit.authoredSkillContext().getSkillId(),base=null;int phase=-1;
        for(String id:new String[]{LIFT,RUSH,PISTON,FAULT})for(int n=0;n<count(id);n++)if(hitId(id,n).equals(actual)){base=id;phase=n;}
        if(base==null||!supports(hit.weaponIdentity().logicId(),base))return;
        var target=hit.target();long now=hit.gameTick();int stagger=LIFT.equals(base)?4:RUSH.equals(base)?phase==3?5:2:FAULT.equals(base)?5:2;
        if(target.isAlive()&&target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)<.8
                &&!YetiFreezeTracker.isMovementLocked(target,now)&&!WeaponSkillMovementControl.isLocked(target,now)) {
            int ticks=YetiFreezeTracker.applyWithEquipmentProtection(target,now,stagger,YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER);
            if(ticks>0) {
                if(LIFT.equals(base)&&target.onGround())HeavyHammerCombatEvents.queueLift(target,now+ticks,.24);
                else if(FAULT.equals(base)||RUSH.equals(base)&&phase==3)HeavyHammerCombatEvents.queuePush(target,p.position(),.4f,now+ticks);
            }
        }
        SlammerDwarfFxPayload.send(p,actual,target.getBoundingBox().getCenter(),phase,target.getId(),p.getYRot());
    }
}
