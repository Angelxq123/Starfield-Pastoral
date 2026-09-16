package com.stardew.craft.pet;

import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PetGifts {
    private PetGifts() {}
    public static void give(ServerPlayer player, PetEntity entity, PetRecord pet) {
        int day = com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay();
        var gate = StardewDeterministicRandom.create(day, player.serverLevel().getSeed() / 2, pet.timesPet, 71928, pet.id.hashCode());
        if (gate.nextDouble() >= pet.variant.species().giftChance()) return;
        var entries = pet.variant.species().gifts().stream().filter(g -> pet.friendship >= g.friendship()).toList();
        double remaining = entity.getRandom().nextDouble() * entries.stream().mapToDouble(com.stardew.craft.api.v1.pet.StardewPetSpeciesDefinition.Gift::weight).sum();
        for (var gift : entries) {
            remaining -= gift.weight(); if (remaining > 0) continue;
            ItemStack result;
            if (gift.query().startsWith("LOCATION_FISH ")) result = com.stardew.craft.fishing.data.FishingDataManager.get().selectPetGift(player, player.serverLevel(), gift.query().split(" ")[1], entity.getRandom());
            else if (gift.query().startsWith("RANDOM_ITEMS ")) {
                String[] parts = gift.query().split(" "); int start = Integer.parseInt(parts[2]), end = Integer.parseInt(parts[3]);
                result = FishPondQualifiedItemService.createItemStack(parts[1] + (start + entity.getRandom().nextInt(end - start + 1)), gift.count());
            } else result = FishPondQualifiedItemService.createItemStack(gift.query(), gift.count());
            if (!result.isEmpty()) entity.spawnAtLocation(result);
            return;
        }
    }
}
