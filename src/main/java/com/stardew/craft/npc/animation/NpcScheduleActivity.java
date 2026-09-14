package com.stardew.craft.npc.animation;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Server-owned action transaction: settle, enter, hold, exit, then release locomotion. */
public final class NpcScheduleActivity {
    private final StardewNpcEntity npc;
    private int settled, alignmentTicks;
    private long retryAt;
    private String candidate = "";
    private Runnable afterExit;

    public NpcScheduleActivity(StardewNpcEntity npc) { this.npc = npc; }
    public boolean isSettling() { return !candidate.isEmpty(); }
    public boolean hasPendingInteraction() { return afterExit != null; }

    public void interrupt(Runnable callback) {
        var previous=afterExit;
        afterExit=previous==null?callback:()->{previous.run();callback.run();};
    }

    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (com.stardew.craft.festival.FestivalNpcController.controlsNpc(npc.getNpcId())) {
            cancel(); return;
        }
        var state = NpcRuntimeDataManager.get(level).states().get(npc.getNpcId());
        var wanted = state == null || state.pathingSuppressed() ? null : com.stardew.craft.npc.data.NpcActivityCatalog.find(npc.getNpcId(),state.routeBehaviorToken());
        String point = state == null ? "" : state.namedPointId();
        var event = npc.getScheduleActivityEvent();
        if (!event.isEmpty()) {
            if (NpcExecutionCoordinator.claim(npc,NpcExecutionCoordinator.ACTIVITY,20,2)<0) { cancel(); return; }
            var current = com.stardew.craft.npc.data.NpcActivityCatalog.find(npc.getNpcId(),event.getString("action"));
            int enterTicks=event.contains("enterTicks") ? Math.max(1,event.getInt("enterTicks")) : current==null?1:current.enterTicks();
            int exitTicks=event.contains("exitTicks") ? Math.max(1,event.getInt("exitTicks")) : current==null?1:current.exitTicks();
            boolean definitionChanged=current==null || event.contains("definition") && (
                    !event.getString("definition").equals(current.id()) || !event.getString("playClip").equals(current.playClip())
                    || !event.getString("enterClip").equals(current.enterClip()) || !event.getString("exitClip").equals(current.exitClip())
                    || !event.getString("support").equals(current.support()) || !event.getString("transition").equals(current.transition())
                    || enterTicks!=current.enterTicks() || exitTicks!=current.exitTicks());
            Vec3 origin = new Vec3(event.getDouble("x"),event.getDouble("y"),event.getDouble("z"));
            if (npc.position().distanceToSqr(origin) > 9) { finish(); return; }
            boolean changed = definitionChanged || !java.util.Objects.equals(wanted,current) || !point.equals(event.getString("point")) || afterExit != null
                    || !npc.isAlive() || npc.isInWaterOrBubble() || npc.isPassenger()
                    || (!event.getString("support").isEmpty() && !supportUnchanged(level,event))
                    || !NpcWorkstationTarget.unchanged(level,event) || !NpcActivityTarget.unchanged(level,event);
            npc.getNavigation().stop();
            npc.setDeltaMovement(0,npc.getDeltaMovement().y,0);
            npc.setWalking(false);
            float lockedYaw = event.getFloat("yaw");
            npc.setYRot(lockedYaw); npc.setYBodyRot(lockedYaw); npc.setYHeadRot(lockedYaw);
            if (changed && !event.contains("exit") && now >= event.getLong("start") + enterTicks) {
                event = event.copy(); event.putLong("exit",now); npc.setScheduleActivityEvent(event);
            }
            if (event.contains("exit") && now >= event.getLong("exit") + exitTicks) finish();
            return;
        }
        if (afterExit != null) { var callback = afterExit; afterExit = null; callback.run(); return; }
        boolean available = now >= retryAt && wanted != null && npc.onGround() && npc.getNavigation().isDone()
                && !npc.isFacingOverrideActive() && !npc.isAttentionActive()
                && !NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()) && !npc.isPassenger() && !npc.isInWaterOrBubble();
        var target = available ? NpcScheduleRuntimeService.resolveWorldTarget(level,state,null) : null;
        var furniture = NpcSupportTarget.point(point);
        var support = wanted != null && wanted.supported() && furniture!=null
                && furniture.has("furniture") && wanted.support().equals(furniture.get("furniture").getAsString())
                ? NpcSupportTarget.resolve(level,point) : null;
        var workstation = NpcWorkstationTarget.resolve(level, point);
        var sceneTarget=NpcActivityTarget.resolve(level,point);
        available &= !NpcActivityTarget.required(point) || sceneTarget!=null && sceneTarget.surface().distanceToSqr(npc.position())<9;
        available &= !NpcWorkstationTarget.required(point) || workstation != null
                && workstation.free(level) && npc.position().distanceToSqr(Vec3.atCenterOf(workstation.block())) < 16;
        available &= target != null && NpcCentralMovementService.canAlignActivity(npc,target.position())
                && (!wanted.supported() || support != null && free(level,support,point));
        String identity = available ? wanted.asset()+":"+point : "";
        if (!identity.equals(candidate)) { candidate = identity; settled = 0; alignmentTicks = 0; }
        if (!available) {
            settled = 0;
            if (wanted != null && wanted.supported() && now >= retryAt && npc.getNavigation().isDone() && !npc.isFacingOverrideActive()
                    && !NpcInteractionService.isDialogueMovementLocked(npc.getNpcId())) {
                NpcCentralMovementService.resetMovementPlan(npc.getNpcId()); retryAt = now+100;
            }
            return;
        }
        if (NpcExecutionCoordinator.claim(npc,NpcExecutionCoordinator.ACTIVITY,20,2)<0) {
            candidate=""; settled=0; return;
        }
        if (++alignmentTicks > 80) {
            candidate = ""; settled = 0; retryAt = now+100;
            NpcCentralMovementService.resetMovementPlan(npc.getNpcId()); return;
        }
        // Finish the final few centimetres and facing before handing the whole pose to the clip.
        Vec3 delta = target.position().subtract(npc.position());
        float targetYaw = support != null ? support.approachYaw() : switch(state.facing()) {
            case 0 -> 180; case 1 -> 270; case 3 -> 90; default -> 0;
        };
        float yaw = Mth.rotLerp(.18F,npc.getYRot(),targetYaw);
        npc.setYRot(yaw); npc.setYBodyRot(yaw); npc.setYHeadRot(yaw);
        if (delta.lengthSqr() > .0001) {
            NpcCentralMovementService.alignActivity(level,npc,target.position());
            settled = 0; return;
        }
        if (Math.abs(Mth.wrapDegrees(targetYaw-yaw)) > 1) { settled = 0; return; }
        if (++settled < 8) return;
        event = new CompoundTag();
        event.putString("action",wanted.asset());
        event.putString("definition",wanted.id());
        event.putString("transition",wanted.transition());
        event.putString("enterClip",wanted.enterClip()); event.putString("playClip",wanted.playClip()); event.putString("exitClip",wanted.exitClip());
        event.putInt("enterTicks",wanted.enterTicks()); event.putInt("exitTicks",wanted.exitTicks());
        event.putString("support",wanted.support()); event.putString("point",point); event.putLong("start",now);
        event.putFloat("yaw",support != null ? support.yaw() : targetYaw);
        event.putFloat("endYaw",targetYaw);
        Vec3 origin = support != null ? support.origin() : npc.position();
        event.putDouble("x",origin.x); event.putDouble("y",origin.y); event.putDouble("z",origin.z);
        if (support != null) event.putLong("block",support.block().asLong());
        if (workstation != null) workstation.write(event);
        if (sceneTarget != null) sceneTarget.write(event);
        npc.setScheduleActivityEvent(event);
    }

    private boolean supportUnchanged(ServerLevel level, CompoundTag event) {
        var support = NpcSupportTarget.resolve(level,event.getString("point"));
        return support != null && support.block().asLong() == event.getLong("block")
                && support.yaw() == event.getFloat("yaw")
                && support.origin().distanceToSqr(new Vec3(event.getDouble("x"),event.getDouble("y"),event.getDouble("z")))<1e-8;
    }

    private boolean free(ServerLevel level, NpcSupportTarget target,String point) {
        var area = NpcSupportTarget.occupiedArea(target,point);
        return level.getEntities(npc,area,e -> e instanceof net.minecraft.world.entity.player.Player
                || e instanceof com.stardew.craft.entity.seat.SofaSeatEntity && e.isVehicle()
                || e instanceof StardewNpcEntity other && other.isPlayingNativeActivity()).isEmpty();
    }

    /** Forced relocation/removal cancels pending callbacks instead of executing stale interactions. */
    public void cancel() {
        afterExit=null; settled=0; candidate="";
        npc.setScheduleActivityEvent(new CompoundTag());
        NpcExecutionCoordinator.release(npc,NpcExecutionCoordinator.ACTIVITY);
    }

    private void finish() {
        float yaw = npc.getScheduleActivityEvent().getFloat("endYaw");
        npc.setYRot(yaw); npc.setYBodyRot(yaw); npc.setYHeadRot(yaw);
        npc.yRotO = yaw; npc.yBodyRotO = yaw; npc.yHeadRotO = yaw;
        npc.setScheduleActivityEvent(new CompoundTag());
        NpcExecutionCoordinator.release(npc,NpcExecutionCoordinator.ACTIVITY);
        settled = 0; candidate = "";
        if (afterExit != null) { var callback = afterExit; afterExit = null; callback.run(); }
    }
}
