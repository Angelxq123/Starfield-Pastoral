package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Both packets come from applied-positive-damage hooks; sharing is quieter than the final verdict. */
public final class TemplarJudgementImpactClient {
    private TemplarJudgementImpactClient() {}
    public static void playImpact(int entityId, boolean settlement) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof LivingEntity target)) return;
        WeaponTargetImpactClient.show(-1, entityId, mc.level.getGameTime(), target.getBoundingBox().getCenter(),
                settlement ? WeaponTargetImpactClient.Style.TEMPLAR_JUDGEMENT : WeaponTargetImpactClient.Style.TEMPLAR_SHARE);
    }
}
