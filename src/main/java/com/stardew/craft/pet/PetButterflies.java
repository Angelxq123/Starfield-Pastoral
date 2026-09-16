package com.stardew.craft.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class PetButterflies {
    private PetButterflies() {}
    static void spawn(ServerLevel level, Vec3 position) {
        for (int i = 0; i < 8; i++) level.addFreshEntity(com.stardew.craft.entity.mastery.PrismaticButterflyEntity.createBlessingVisual(
                level, position.x + (level.random.nextDouble() - .5) * .8,
                position.y + .25 + level.random.nextDouble() * .6, position.z + (level.random.nextDouble() - .5) * .8));
    }
}
