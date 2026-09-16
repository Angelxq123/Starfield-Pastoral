package com.stardew.craft.client.gui;

import com.stardew.craft.api.v1.farm.StardewFarmLayoutPreview;
import com.stardew.craft.api.v1.farm.StardewFarmSelectionOptions;
import com.stardew.craft.network.payload.FarmSelectionSubmitPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draft lifetime is independent of widgets, scrolling, and window resizing. */
final class FarmSetupDraft {
    final List<StardewFarmLayoutPreview> layouts;
    final Map<ResourceLocation, Map<ResourceLocation, String>> configurations = new HashMap<>();
    final Map<ResourceLocation, Boolean> options = new HashMap<>();
    String farmName = "", preferredName = "", favoriteThing = "";
    String petVariant = "stardewcraft:cat0", petName = "";
    int selected;
    boolean male = true, forceCancelPending;

    FarmSetupDraft(List<StardewFarmLayoutPreview> layouts, List<StardewFarmSelectionOptions.Option> options) {
        this.layouts = List.copyOf(layouts);
        for (var layout : layouts) {
            var values = new HashMap<ResourceLocation, String>();
            layout.configurationFields().forEach(field -> values.put(field.id(), field.defaultValue()));
            configurations.put(layout.id(), values);
        }
        for (int i = 0; i < layouts.size(); i++) if (layouts.get(i).selectable()) { selected = i; break; }
        options.forEach(option -> this.options.put(option.id(), option.defaultSelected()));
    }

    StardewFarmLayoutPreview layout() { return layouts.isEmpty() ? null : layouts.get(selected); }

    /** Catalog growth affects page count, not the height of the entire creation form. */
    FarmPage page(int requested, boolean includeLocked) {
        var indices = java.util.stream.IntStream.range(0, layouts.size())
                .filter(i -> includeLocked || layouts.get(i).selectable()).boxed().toList();
        int pages = Math.max(1, (indices.size() + 3) / 4);
        int page = Math.max(0, Math.min(requested, pages - 1));
        int start = page * 4;
        return new FarmPage(List.copyOf(indices.subList(start, Math.min(start + 4, indices.size()))), page, pages);
    }

    record FarmPage(List<Integer> indices, int number, int pages) { }

    String missingField(boolean profileOnly) {
        if (!profileOnly && (layout() == null || !layout().selectable())) return "farm";
        if (!profileOnly && farmName.isBlank()) return "farm_name";
        if (!profileOnly && !petVariant.isEmpty() && petName.isBlank()) return "pet_name";
        if (preferredName.isBlank()) return "name";
        if (favoriteThing.isBlank()) return "favorite";
        return "";
    }

    FarmSelectionSubmitPayload payload() {
        if (!missingField(false).isEmpty()) throw new IllegalStateException("Incomplete farm setup");
        return new FarmSelectionSubmitPayload(layout().id().toString(), farmName.trim(), forceCancelPending,
                preferredName.trim(), favoriteThing.trim(), male, Map.copyOf(configurations.get(layout().id())), petVariant, petName.trim());
    }
}
