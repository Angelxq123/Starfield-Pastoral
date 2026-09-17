package com.stardew.craft.api.v1.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** One immutable Robin construction or upgrade order visible to the local player. */
public record StardewConstructionOrderSnapshot(
        UUID buildingId,
        ResourceLocation buildingFamilyId,
        String customName,
        WorkType workType,
        int targetTier,
        int remainingWorkDays
) {
    public StardewConstructionOrderSnapshot {
        buildingId = Objects.requireNonNull(buildingId, "buildingId");
        buildingFamilyId = Objects.requireNonNull(buildingFamilyId, "buildingFamilyId");
        customName = Objects.requireNonNull(customName, "customName");
        workType = Objects.requireNonNull(workType, "workType");
        if (customName.length() > 32) {
            throw new IllegalArgumentException("customName exceeds 32 characters");
        }
        if (targetTier < 1) {
            throw new IllegalArgumentException("targetTier must be positive");
        }
        if (remainingWorkDays < 0) {
            throw new IllegalArgumentException("remainingWorkDays must not be negative");
        }
    }

    /** Zero means the server is waiting to project the completed building state. */
    public boolean readyForCompletion() {
        return remainingWorkDays == 0;
    }

    /** Uses the custom name when present, otherwise resolves the family title in the client language. */
    public Component displayName() {
        return customName.isBlank()
                ? com.stardew.craft.api.v1.building.StardewBuildingFamilies.title(
                        buildingFamilyId, targetTier)
                : Component.literal(customName);
    }

    public enum WorkType {
        CONSTRUCTION,
        UPGRADE
    }
}
