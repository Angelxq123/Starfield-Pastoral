package com.stardew.craft.aquarium;

import com.google.gson.JsonParser;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import com.stardew.craft.item.trinket.StardewTrinketItem;
import com.stardew.craft.item.trinket.TrinketType;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original AquariumFish movement types, independent of fish-pond breeding rules. */
public final class AquariumRules {
    public static final int SIZE = 23;
    public enum Kind { SWIM, GROUND, HAT, DECORATION, INVALID }
    private static final Map<String, String> MOVEMENT = load();
    private static final Map<net.minecraft.world.item.Item, String> MOVEMENT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<net.minecraft.world.item.Item, String> DECORATION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String[] DECORATIONS = {"152", "393", "390", "117", "166", "832", "109",
            "709", "392", "394", "167", "789", "330", "797"};
    private AquariumRules() {}

    private static Map<String, String> load() {
        try (var stream = AquariumRules.class.getResourceAsStream("/data/stardewcraft/aquarium/fish.json")) {
            if (stream == null) throw new IllegalStateException("Missing aquarium species");
            var result = new LinkedHashMap<String, String>();
            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> result.put(e.getKey(), e.getValue().getAsString().split("/", -1)[1]));
            return Map.copyOf(result);
        } catch (Exception e) { throw new IllegalStateException("Cannot load aquarium species", e); }
    }

    public static boolean frog(ItemStack stack) {
        return stack.getItem() instanceof StardewTrinketItem trinket && trinket.getTrinketType() == TrinketType.FROG_EGG;
    }
    public static String movement(ItemStack stack) {
        if (frog(stack)) return "frog";
        return MOVEMENT_CACHE.computeIfAbsent(stack.getItem(), item -> {
            for (var entry : MOVEMENT.entrySet()) if (FishPondQualifiedItemService.matches("(O)" + entry.getKey(), stack)) return entry.getValue();
            return "";
        });
    }
    public static String decoration(ItemStack stack) {
        return DECORATION_CACHE.computeIfAbsent(stack.getItem(), item -> {
            for (String id : DECORATIONS) if (FishPondQualifiedItemService.matches("(O)" + id, stack)) return id;
            return "";
        });
    }
    public static boolean hatWearer(ItemStack stack) { return FishPondQualifiedItemService.matches("(O)397", stack); }
    public static Kind kind(ItemStack stack) {
        if (stack.isEmpty()) return Kind.INVALID;
        if (stack.getItem() instanceof StardewHatItem) return Kind.HAT;
        if (!decoration(stack).isEmpty()) return Kind.DECORATION;
        return switch (movement(stack)) {
            case "" -> Kind.INVALID;
            case "crawl", "ground", "front_crawl", "static", "frog" -> Kind.GROUND;
            default -> Kind.SWIM;
        };
    }
    public static Kind slotKind(int slot) {
        return slot < 0 || slot >= SIZE ? Kind.INVALID : slot < 3 ? Kind.SWIM : slot < 6 ? Kind.GROUND
                : slot < 9 ? Kind.HAT : Kind.DECORATION;
    }
    public static boolean canPlace(Container contents, int slot, ItemStack stack) {
        Kind kind = kind(stack);
        if (kind == Kind.INVALID || kind != slotKind(slot)) return false;
        int hats = 0, wearers = 0;
        for (int i = 0; i < SIZE; i++) {
            ItemStack other = contents.getItem(i);
            if (hatWearer(other)) wearers++;
            if (i == slot) continue;
            if (other.getItem() instanceof StardewHatItem && !other.isEmpty()) hats++;
            if (kind == Kind.DECORATION && ItemStack.isSameItem(other, stack)) return false;
        }
        return kind != Kind.HAT || hats < wearers;
    }
}
