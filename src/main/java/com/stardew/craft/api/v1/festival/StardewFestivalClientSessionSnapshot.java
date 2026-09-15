package com.stardew.craft.api.v1.festival;

import com.stardew.craft.festival.FestivalRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/**
 * Bounded, privacy-safe festival session view synchronized to one client.
 *
 * <p>Participant UUIDs and addon persistent data intentionally remain
 * server-side.
 */
public record StardewFestivalClientSessionSnapshot(
        ResourceLocation festivalId,
        String runtimeId,
        int year,
        int season,
        int day,
        StardewFestivalSessionSnapshot.Phase phase,
        StardewFestivalSessionSnapshot.MapPhase mapPhase,
        int participantCount,
        boolean localPlayerParticipating
) {
    public StardewFestivalClientSessionSnapshot {
        festivalId = Objects.requireNonNull(festivalId, "festivalId");
        runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        phase = Objects.requireNonNull(phase, "phase");
        mapPhase = Objects.requireNonNull(mapPhase, "mapPhase");
        if (participantCount < 0) {
            throw new IllegalArgumentException(
                    "participantCount must be non-negative");
        }
    }

    /**
     * Client display name resolved from the synchronized festival definitions.
     * Built-in names use the current client language; addon names use their
     * definition's {@code display_name}. Missing definitions fall back to the
     * namespaced ID. Call again after a definition reload to obtain updated names.
     *
     * <p>Render the component directly, or use {@link Component#getString()} for
     * plain text. This does not change the snapshot constructor or wire format.
     */
    public Component displayName() {
        var definition = FestivalRegistry.get(festivalId).orElse(null);
        String fallback = definition == null ? festivalId.toString() : definition.sourceName();
        if (festivalId.getNamespace().equals("stardewcraft")) {
            String id = definition == null ? runtimeId : definition.id();
            return Component.translatableWithFallback(
                    "stardewcraft.festival.calendar." + id.toLowerCase(Locale.ROOT), fallback);
        }
        return Component.literal(fallback);
    }
}
