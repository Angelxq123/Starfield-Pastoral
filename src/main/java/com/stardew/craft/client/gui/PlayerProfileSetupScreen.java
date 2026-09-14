package com.stardew.craft.client.gui;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Existing-save repair uses the same form and art as new-farm setup. */
public final class PlayerProfileSetupScreen extends FarmSelectionScreen {
    public PlayerProfileSetupScreen() { super(true); }

    private PlayerProfileSetupScreen(String farmTypeId, String farmName, boolean forceCancelPending,
                                     Map<ResourceLocation, String> configuration) {
        super(false);
        String id = farmTypeId.contains(":") ? farmTypeId : "stardewcraft:" + farmTypeId;
        for (int i = 0; i < draft.layouts.size(); i++) {
            if (draft.layouts.get(i).id().toString().equals(id)) { draft.selected = i; break; }
        }
        draft.farmName = farmName;
        draft.forceCancelPending = forceCancelPending;
        if (draft.layout() != null) draft.configurations.get(draft.layout().id()).putAll(configuration);
    }

    public static PlayerProfileSetupScreen forNewFarm(String farmTypeId, String farmName, boolean forceCancelPending) {
        return forNewFarm(farmTypeId, farmName, forceCancelPending, Map.of());
    }
    public static PlayerProfileSetupScreen forNewFarm(String farmTypeId, String farmName, boolean forceCancelPending,
                                                     Map<ResourceLocation, String> layoutConfiguration) {
        return new PlayerProfileSetupScreen(farmTypeId, farmName, forceCancelPending, layoutConfiguration);
    }
}
