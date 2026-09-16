package com.stardew.craft.api.v1.client;

import com.stardew.craft.client.hud.StardewAddonHudBridge;
import java.util.Optional;

/** CLIENT ONLY. Query each frame; never retain coordinates across resize, GUI scale or HUD edits. */
public final class StardewClientHud {
    private StardewClientHud() {}

    /** Empty without a local player/world; otherwise includes the main HUD's current visibility. */
    public static Optional<StardewHudSnapshot> mainHud() {
        return StardewAddonHudBridge.current();
    }
}
