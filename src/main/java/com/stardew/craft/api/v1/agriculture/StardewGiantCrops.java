package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.api.v1.internal.giant.GiantCropRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Objects;

/** Experimental giant-crop registration. Register during mod construction; callbacks are server-only. */
public final class StardewGiantCrops {
    private StardewGiantCrops() {}

    public static void register(Definition definition) { GiantCropRegistry.register(definition); }
    public static List<Definition> definitions() { return GiantCropRegistry.definitions(); }

    /** Dimensions include the root layer, extend east/south/up from the triggering northwest root.
     * Produce IDs match crop metadata, so different crop types yielding the same item can combine.
     * Only the trigger must be mature. Other roots must be intact crops with matching produce.
     * RequiresWater applies only to the trigger. A definition may occupy at most 32768 cells.
     */
    public record Definition(ResourceLocation id, ResourceLocation produce, int width, int depth,
                             int height, double chance, boolean requiresWater, Handler handler) {
        public Definition {
            Objects.requireNonNull(id); Objects.requireNonNull(produce); Objects.requireNonNull(handler);
            if (width < 1 || depth < 1 || height < 1 || width > 32768 || depth > 32768 || height > 32768
                    || (long) width * depth * height > 32768 || !Double.isFinite(chance) || chance < 0 || chance > 1)
                throw new IllegalArgumentException("Invalid giant crop dimensions or chance: " + id);
        }
    }

    public record Context(ServerLevel level, BlockPos anchor, Definition definition,
                          int absoluteDay, boolean watered, boolean offlineCatchUp) {
        public Context { anchor = anchor.immutable(); }
    }

    /** Read-only callbacks. Do not mutate the world, load chunks, emit drops, or alter crop persistence.
     * The runtime validates the entire footprint before committing the returned block plan.
     * Addon crop blocks must clean their own persistence on replacement, as on ordinary removal.
     */
    public interface Handler {
        /** Farm bounds are always enforced in a farm. Override to opt other locations in explicitly. */
        default boolean allowsOutsideFarm(Context context) { return false; }
        default boolean condition(Context context) { return true; }
        /** Coordinates are relative to the northwest root. Null/empty rejects growth.
         * Every root-layer cell must have a non-air replacement. Unspecified source crop parts
         * inside the volume are removed without harvest drops. Plans cannot replace block entities.
         * Custom giant blocks can create their own block entities via normal onPlace initialization.
         */
        List<Cell> plan(Context context, List<StardewCropState> crops);
    }

    public record Cell(BlockPos offset, BlockState state) {
        public Cell { offset = Objects.requireNonNull(offset).immutable(); Objects.requireNonNull(state); }
    }
}
