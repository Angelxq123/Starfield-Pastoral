package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.FarmListSyncPayload;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/** Joining is an application, distinct from permission to visit an existing farm. */
public class FarmJoinSelectScreen extends FarmBrowserScreen {
    public FarmJoinSelectScreen(List<FarmListSyncPayload.FarmEntry> farms) { this(farms, null); }
    public FarmJoinSelectScreen(List<FarmListSyncPayload.FarmEntry> farms, Screen returnScreen) {
        super(farms, true, "", returnScreen);
    }
}
