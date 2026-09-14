package com.stardew.craft.client.gui;

import com.stardew.craft.network.payload.FarmListSyncPayload.FarmEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Selection identity survives scrolling and widget reconstruction. */
final class FarmBrowserModel {
    final List<FarmEntry> farms;
    final boolean joining;
    String query = "";
    UUID selected;
    int scrollOffset;

    FarmBrowserModel(List<FarmEntry> farms, boolean joining) {
        this.joining = joining;
        this.farms = farms.stream().sorted(Comparator
                .comparingInt((FarmEntry f) -> joining ? 0 : f.isMember() ? 0 : f.permission() == 2 ? 1 : f.permission() == 1 ? 2 : 3)
                .thenComparing(FarmEntry::farmName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(f -> f.ownerUUID().toString())).toList();
        if (!joining) this.farms.stream().filter(FarmEntry::isMember).findFirst().ifPresent(f -> selected = f.ownerUUID());
    }

    List<FarmEntry> filtered() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return farms.stream().filter(f -> f.farmName().toLowerCase(Locale.ROOT).contains(needle)
                || f.ownerName().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    void search(String value) {
        query = value;
        scrollOffset = 0;
        if (filtered().stream().noneMatch(f -> f.ownerUUID().equals(selected))) selected = null;
    }

    FarmEntry selection() { return farms.stream().filter(f -> f.ownerUUID().equals(selected)).findFirst().orElse(null); }
    boolean allowed(FarmEntry farm) { return farm != null && (joining ? !farm.isMember() : farm.isMember() || farm.permission() >= 1); }
    boolean canSubmit(boolean pending) { return allowed(selection()) && !(joining && pending); }
    int maxScroll(int size) { return Math.max(0, filtered().size() - size); }
    List<FarmEntry> entries(int size) {
        var matching = filtered();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, matching.size() - size)));
        return matching.subList(scrollOffset, Math.min(scrollOffset + size, matching.size()));
    }
}
