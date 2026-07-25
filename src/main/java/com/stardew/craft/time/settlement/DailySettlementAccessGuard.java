package com.stardew.craft.time.settlement;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DailySettlementAccessGuard {
    private static final double POSITION_EPSILON_SQUARED = 1.0E-6D;

    private final DailySettlementBarrier barrier;
    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private final Set<UUID> restoring = new HashSet<>();

    public DailySettlementAccessGuard(DailySettlementBarrier barrier) {
        this.barrier = Objects.requireNonNull(barrier, "barrier");
    }

    public static <T extends CustomPacketPayload> IPayloadHandler<T> gated(
            IPayloadHandler<T> handler) {
        return gated(handler, context -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return false;
            }
            DailySettlementServices.Services services =
                    DailySettlementServices.find(player.server);
            return !com.stardew.craft.farm.OfflineFarmCatchUpService
                    .isPlayerLocked(player)
                    && services != null
                    && services.accessGuard().isGameplayAllowed(player.getUUID());
        });
    }

    static <T extends CustomPacketPayload> IPayloadHandler<T> gated(
            IPayloadHandler<T> handler,
            Predicate<IPayloadContext> accessAllowed) {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(accessAllowed, "accessAllowed");
        return (payload, context) -> context.enqueueWork(() -> {
            if (!accessAllowed.test(context)) {
                return;
            }
            handler.handle(payload, context);
        });
    }

    public boolean isGameplayAllowed(UUID playerId) {
        return !barrier.isLocked(Objects.requireNonNull(playerId, "playerId"));
    }

    public void captureAnchor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        closeContainerIfLocked(player);
        captureAnchor(
                player.getUUID(),
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot());
    }

    void captureAnchor(
            UUID playerId,
            ResourceKey<Level> dimension,
            Vec3 position,
            float yaw,
            float pitch) {
        Objects.requireNonNull(playerId, "playerId");
        if (!barrier.isLocked(playerId)) {
            return;
        }
        anchors.put(playerId, new Anchor(dimension, position, yaw, pitch));
    }

    public void reconnectAnchor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        closeContainerIfLocked(player);
        reconnectAnchor(
                player.getUUID(),
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot());
    }

    void reconnectAnchor(
            UUID playerId,
            ResourceKey<Level> dimension,
            Vec3 position,
            float yaw,
            float pitch) {
        captureAnchor(playerId, dimension, position, yaw, pitch);
    }

    Optional<Anchor> anchor(UUID playerId) {
        return Optional.ofNullable(anchors.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public void onPlayerTick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!barrier.isLocked(playerId)) {
            anchors.remove(playerId);
            return;
        }
        Anchor anchor = anchors.get(playerId);
        if (anchor == null) {
            captureAnchor(player);
            anchor = anchors.get(playerId);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        if (anchor == null) {
            return;
        }
        boolean displaced = player.level().dimension() != anchor.dimension()
                || player.position().distanceToSqr(anchor.position())
                > POSITION_EPSILON_SQUARED;
        if (displaced) {
            restore(player, anchor);
            return;
        }
        player.setYRot(anchor.yaw());
        player.setXRot(anchor.pitch());
    }

    public boolean rejectTeleport(ServerPlayer player) {
        return rejectTeleport(player.getUUID());
    }

    boolean rejectTeleport(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return barrier.isLocked(playerId) && !restoring.contains(playerId);
    }

    public boolean acknowledge(UUID playerId, int absoluteDay) {
        Objects.requireNonNull(playerId, "playerId");
        if (!barrier.acknowledge(playerId, absoluteDay)) {
            return false;
        }
        anchors.remove(playerId);
        restoring.remove(playerId);
        return true;
    }

    public void onLogout(UUID playerId) {
        anchors.remove(Objects.requireNonNull(playerId, "playerId"));
        restoring.remove(playerId);
    }

    void clearAnchors(Collection<UUID> playerIds) {
        for (UUID playerId : Objects.requireNonNull(playerIds, "playerIds")) {
            anchors.remove(Objects.requireNonNull(playerId, "playerId"));
            restoring.remove(playerId);
        }
    }

    public void closeContainerIfLocked(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (barrier.isLocked(player.getUUID())
                && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    public void clear() {
        anchors.clear();
        restoring.clear();
    }

    private void restore(ServerPlayer player, Anchor anchor) {
        ServerLevel target = player.server.getLevel(anchor.dimension());
        if (target == null) {
            return;
        }
        UUID playerId = player.getUUID();
        restoring.add(playerId);
        try {
            Vec3 position = anchor.position();
            player.teleportTo(
                    target,
                    position.x,
                    position.y,
                    position.z,
                    anchor.yaw(),
                    anchor.pitch());
        } finally {
            restoring.remove(playerId);
        }
    }

    public record Anchor(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yaw,
            float pitch) {
        public Anchor {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
        }
    }
}
