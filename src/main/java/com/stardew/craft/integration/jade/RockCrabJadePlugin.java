package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.monster.RockCrabEntity;
import snownee.jade.api.*;

/** Do not reveal a concealed crab through Jade's entity name and health header. */
@WailaPlugin(StardewCraft.MODID)
public final class RockCrabJadePlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback(Integer.MAX_VALUE,(hit,accessor,original)->
                accessor instanceof EntityAccessor entity && entity.getEntity() instanceof RockCrabEntity crab && crab.disguised()
                        ? null : accessor);
    }
}
