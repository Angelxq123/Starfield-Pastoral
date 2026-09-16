package com.stardew.craft.npc.runtime;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.server.level.ServerLevel;

/** Save the actor's safe physical location independently from the schedule's desired destination. */
public final class NpcActorPersistence {
    private NpcActorPersistence() {}
    public static void capture(StardewNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel level) || npc.isRemoved() || !npc.isAlive()
                || npc.getTags().contains(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG)
                || !npc.onGround() || npc.isInWaterOrBubble() || npc.isPassenger()
                || com.stardew.craft.festival.FestivalNpcController.controlsNpc(npc.getNpcId())) return;
        var data=NpcRuntimeDataManager.get(level);
        if (data.getOrCreate(npc.getNpcId()).rememberPosition(new NpcRuntimeState.ActualPosition(
                level.dimension().location().toString(),npc.getX(),npc.getY(),npc.getZ(),npc.getYRot(),
                StardewTimeManager.get().getAbsoluteDay()))) data.setDirty();
    }
}
