package com.stardew.craft.api.v1.mining;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.content.StardewContentRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Namespaced mine-monster identities, progress routing and spawn configuration. */
public final class StardewMineMonsterProfiles {
    private static final String PROFILE_DATA_KEY =
            "stardewcraftMineMonsterProfile";
    private static final Map<ResourceLocation, Registered> PROFILES =
            new LinkedHashMap<>();
    private static volatile Catalog catalog =
            new Catalog(Map.of(), List.of());

    private StardewMineMonsterProfiles() {
    }

    public static synchronized void register(
            ResourceLocation id,
            net.minecraft.world.entity.EntityType<? extends Mob> entityType,
            Set<String> progressTags,
            StardewMineMonsterConfigurator configurator
    ) {
        register(
                id,
                entityType,
                entityType.getDescriptionId(),
                progressTags,
                configurator
        );
    }

    public static synchronized void register(
            ResourceLocation id,
            net.minecraft.world.entity.EntityType<? extends Mob> entityType,
            String translationKey,
            Set<String> progressTags,
            StardewMineMonsterConfigurator configurator
    ) {
        Objects.requireNonNull(configurator, "configurator");
        StardewMineMonsterProfile profile =
                new StardewMineMonsterProfile(
                        id, entityType, translationKey, progressTags);
        if (PROFILES.putIfAbsent(
                id, new Registered(profile, configurator)) != null) {
            throw new IllegalStateException(
                    "Mine monster profile already registered: " + id);
        }
        List<StardewMineMonsterProfile> ordered = PROFILES.values().stream()
                .map(Registered::profile)
                .sorted(java.util.Comparator.comparing(
                        value -> value.id().toString()))
                .toList();
        catalog = new Catalog(Map.copyOf(PROFILES), ordered);
        StardewContentRegistry.invalidate();
    }



    public static List<StardewMineMonsterProfile> all() {
        return catalog.profiles();
    }

    @Nullable
    public static StardewMineMonsterProfile find(ResourceLocation id) {
        Registered registered = catalog.byId().get(id);
        return registered == null ? null : registered.profile();
    }

    @Nullable


    /** Marks a newly created entity before EntityJoinLevelEvent applies it. */
    public static boolean mark(
            Mob mob,
            ResourceLocation profileId
    ) {
        Objects.requireNonNull(mob, "mob");
        if (find(profileId) == null) {
            return false;
        }
        mob.getPersistentData().putString(
                PROFILE_DATA_KEY, profileId.toString());
        return true;
    }

    public static boolean hasMarkedProfile(Mob mob) {
        return mob.getPersistentData().contains(PROFILE_DATA_KEY);
    }

    /**
     * Applies the marked profile once the entity has a server level. Returns
     * false for a missing or failed profile so the caller can reject the spawn.
     */
    public static boolean applyMarkedProfile(Mob mob, int floor) {
        String raw = mob.getPersistentData()
                .getString(PROFILE_DATA_KEY);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        Registered registered =
                id == null ? null : catalog.byId().get(id);
        if (registered == null
                || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (mob.getType() != registered.profile().entityType()) {
            StardewCraft.LOGGER.error(
                    "Mine monster profile {} expected entity {}, got {}",
                    id,
                    registered.profile().entityType(),
                    mob.getType());
            return false;
        }
        try {
            registered.profile().progressTags().forEach(mob::addTag);
            registered.configurator().configure(
                    mob,
                    new StardewMineMonsterProfileContext(level, floor));
            if (mob.getCustomName() == null) {
                mob.setCustomName(net.minecraft.network.chat.Component.translatable(
                        registered.profile().translationKey()
                ));
                mob.setCustomNameVisible(false);
            }
            return true;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Mine monster profile {} failed to configure entity",
                    id, exception);
            return false;
        }
    }


    private record Registered(
            StardewMineMonsterProfile profile,
            StardewMineMonsterConfigurator configurator
    ) {
    }

    private record Catalog(
            Map<ResourceLocation, Registered> byId,
            List<StardewMineMonsterProfile> profiles
    ) {
    }
}
