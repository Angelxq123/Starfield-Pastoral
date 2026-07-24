package com.stardew.craft.time.settlement;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.overnight.OvernightBarrierPayload;
import net.minecraft.server.level.ServerPlayer;
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
                && accessGuard(player).map(guard -> guard.rejectTeleport(player)).orElse(false)) {
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
        resumePlayerSettlement(
                services.players(), services.barrier(), player.getUUID(), coordinatorOwns);
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

    private static boolean isLocked(ServerPlayer player) {
        return accessGuard(player)
                .map(guard -> !guard.isGameplayAllowed(player.getUUID()))
                .orElse(false);
    }

    private static void rejectInteraction(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isLocked(player)) {
            ((net.neoforged.bus.api.ICancellableEvent) event).setCanceled(true);
        }
    }

    private static Optional<DailySettlementAccessGuard> accessGuard(ServerPlayer player) {
        return Optional.of(DailySettlementServices.getForPlayer(player).accessGuard());
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
