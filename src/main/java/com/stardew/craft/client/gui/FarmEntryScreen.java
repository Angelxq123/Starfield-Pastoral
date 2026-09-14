package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.FarmListSyncPayload;
import java.util.List;

/** Existing-farm travel uses the shared directory, preserving the entrance tag. */
public class FarmEntryScreen extends FarmBrowserScreen {
    public FarmEntryScreen(List<FarmListSyncPayload.FarmEntry> farms, String entryTag) {
        super(farms, false, entryTag, null);
    }
}
