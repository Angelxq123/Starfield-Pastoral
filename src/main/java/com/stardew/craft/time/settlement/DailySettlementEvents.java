package com.stardew.craft.time.settlement;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.overnight.OvernightBarrierPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class DailySettlementEvents {
    private DailySettlementEvents() {
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(event.getServer());
        if (services != null) {
            services.coordinator().tick();
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DailySettlementServices.remove(event.getServer());
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            liveAccessGuard(player).ifPresent(guard -> guard.onPlayerTick(player));
        }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && rejectTeleport(player)) {
            event.setCanceled(true);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        rejectInteraction(event);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        rejectInteraction(event);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        rejectInteraction(event);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        rejectInteraction(event);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        rejectInteraction(event);
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItem(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) {
            event.setCanceled(true);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && isLocked(player)) {
            event.cancelWithResult(ItemInteractionResult.FAIL);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && isLocked(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services == null) {
            services = DailySettlementServices.get(player.server);
            DailySettlementBarrier.ReadyResult recovered =
                    services.players().onLogin(
                            player.getUUID(), services.barrier()).orElse(null);
            int absoluteDay = services.barrier().lockedDay(player.getUUID());
            if (absoluteDay > 0) {
                services.accessGuard().reconnectAnchor(player);
            }
            if (recovered != null) {
                PacketDistributor.sendToPlayer(
                        player, new OvernightBarrierPayload(absoluteDay, true));
                PacketDistributor.sendToPlayer(player, recovered.payload());
                com.stardew.craft.cutscene.server.WakeUpEventScheduler
                        .enqueueAtNightSettlement(player);
            }
            return;
        }
        boolean coordinatorOwns = services.coordinator().context()
                .map(context -> context.playerIds().contains(player.getUUID()))
                .orElse(false);
        Optional<DailySettlementBarrier.ReadyResult> recovered = resumePlayerSettlement(
                services.players(), services.barrier(), player.getUUID(), coordinatorOwns);
        if (recovered.isEmpty() && services.players().participates(player)) {
            DailySettlementServices.Services activeServices = services;
            activeServices.coordinator().context().ifPresent(active ->
                    lockLateJoinForActiveDay(
                            active, activeServices.barrier(), player.getUUID()));
        }
        services.accessGuard().reconnectAnchor(player);
        int absoluteDay = services.barrier().lockedDay(player.getUUID());
        if (absoluteDay <= 0) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player, new OvernightBarrierPayload(absoluteDay, true));
        DailySettlementBarrier.ReadyResult ready =
                services.barrier().readyResult(player.getUUID(), absoluteDay);
        if (ready != null) {
            PacketDistributor.sendToPlayer(player, ready.payload());
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services != null) {
            services.accessGuard().onLogout(player.getUUID());
            services.players().onLogout(player.getUUID());
        }
    }

    public static void onPlayerEnteredSettlementDimension(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services == null || !services.players().participates(player)) {
            return;
        }
        services.coordinator().context().ifPresent(active -> {
            if (!lockLateJoinForActiveDay(
                    active, services.barrier(), player.getUUID())) {
                return;
            }
            services.accessGuard().reconnectAnchor(player);
            PacketDistributor.sendToPlayer(
                    player, new OvernightBarrierPayload(active.absoluteDay(), true));
        });
    }

    public static void onReadyAcknowledged(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        if (services == null || !services.players().participates(player)) {
            return;
        }
        services.coordinator().context().ifPresent(active -> {
            if (!lockLateJoinForActiveDay(
                    active, services.barrier(), player.getUUID())) {
                return;
            }
            services.accessGuard().reconnectAnchor(player);
            PacketDistributor.sendToPlayer(
                    player, new OvernightBarrierPayload(active.absoluteDay(), true));
        });
        ServerLevel stardewLevel = player.server.getLevel(
                com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
        if (stardewLevel != null) {
            com.stardew.craft.farm.OfflineFarmCatchUp.catchUp(
                    stardewLevel, player.getUUID());
        }
    }

    static boolean lockLateJoinForActiveDay(
            DailySettlementContext context,
            DailySettlementBarrier barrier,
            UUID playerId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(barrier, "barrier");
        Objects.requireNonNull(playerId, "playerId");
        if (context.playerIds().contains(playerId) || barrier.isLocked(playerId)) {
            return false;
        }
        barrier.lockAll(context.absoluteDay(), List.of(playerId));
        return true;
    }

    private static boolean isLocked(ServerPlayer player) {
        if (com.stardew.craft.farm.OfflineFarmCatchUpService.isPlayerLocked(player)) {
            return true;
        }
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        return services == null
                || !services.accessGuard().isGameplayAllowed(player.getUUID());
    }

    private static boolean rejectTeleport(ServerPlayer player) {
        if (com.stardew.craft.farm.OfflineFarmCatchUpService.isPlayerLocked(player)) {
            return true;
        }
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        return services == null
                || services.accessGuard().rejectTeleport(player);
    }

    private static void rejectInteraction(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) {
            ((net.neoforged.bus.api.ICancellableEvent) event).setCanceled(true);
        }
    }

    private static Optional<DailySettlementAccessGuard> liveAccessGuard(ServerPlayer player) {
        DailySettlementServices.Services services =
                DailySettlementServices.find(player.server);
        return services == null
                ? Optional.empty()
                : Optional.of(services.accessGuard());
    }

    static Optional<DailySettlementBarrier.ReadyResult> resumePlayerSettlement(
            PlayerDailySettlementService players,
            DailySettlementBarrier barrier,
            UUID playerId,
            boolean coordinatorOwns) {
        if (coordinatorOwns) {
            return Optional.empty();
        }
        return players.onLogin(playerId, barrier);
    }
}
