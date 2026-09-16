package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Facility counts within the fixed management range. No roof, enclosure or minimum-air requirement. */
public final class BuildingResidence {
    private BuildingResidence() {}
    public record Assessment(boolean loaded, int troughs, int automaticTroughs,
                             int hoppers, int incubators, int eligibleTier) {}

    public static Assessment scan(ServerLevel level, BuildingBounds bounds, net.minecraft.resources.ResourceLocation family) {
        if (!level.hasChunksAt(bounds.min(), bounds.maxInclusive())) return new Assessment(false, 0, 0, 0, 0, 0);
        int troughs = 0, automatic = 0, hoppers = 0, incubators = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.min().getX(); x < bounds.maxExclusive().getX(); x++) {
            for (int z = bounds.min().getZ(); z < bounds.maxExclusive().getZ(); z++) {
                for (int y = bounds.min().getY(); y < bounds.maxExclusive().getY(); y++) {
                    cursor.set(x, y, z);
                    var facility=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,cursor).access();
                    troughs+=Math.max(0,facility.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.TROUGH));
                    automatic+=Math.max(0,facility.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.AUTOMATIC_TROUGH));
                    hoppers+=Math.max(0,facility.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.HOPPER));
                    incubators+=Math.max(0,facility.units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.INCUBATOR));
                }
            }
        }
        int eligible = 0;
        for (var tier : PrefabDefinitions.get(family).tiers()) {
            var needs = PrefabDefinitions.facilities(family,tier.level());
            // Automatic troughs also provide usable feeding places for earlier tiers.
            if (troughs + automatic >= needs.troughs() && automatic >= needs.automaticTroughs()
                    && hoppers >= needs.hoppers() && incubators >= needs.incubators()) eligible = tier.level();
        }
        return new Assessment(true, troughs, automatic, hoppers, incubators, eligible);
    }

    public static Assessment refresh(ServerLevel level, BuildingRecord record) {
        Assessment assessment = scan(level, record.claim(), record.family());
        if (assessment.loaded()) {
            var residence = assessment.eligibleTier() >= record.tier() ? BuildingRecord.Residence.VALID : BuildingRecord.Residence.INVALID;
            if (record.residence() != residence && record.phase() == BuildingRecord.Phase.READY) {
                BuildingWorldData.get(level.getServer()).assessResidence(record.id(), record.revision(), assessment.eligibleTier());
            }
        }
        return assessment;
    }
}
