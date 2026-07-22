package com.stardew.craft.warp;

import com.stardew.craft.interior.CrossDimensionTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single entry point for mod-originated player teleports.
 * <p>
 * Cross-dimension calls automatically mark the player as
 * {@link CrossDimensionTeleporter#markSkipAutoTeleport(java.util.UUID) SKIP_AUTO_TELEPORT}
 * so that the safety-net redirect in {@code DimensionEventHandler} does not
 * overwrite the target coordinates. Mod code must always go through this
 * helper instead of calling {@link ServerPlayer#teleportTo(ServerLevel, double, double, double, float, float)}
 * directly — raw calls will be treated as vanilla sources and redirected to
 * the farm spawn / current mine floor entrance.
 */
public final class ModTeleport {

    private ModTeleport() {}

    public static void to(ServerPlayer player, ServerLevel target,
                          double x, double y, double z, float yaw, float pitch) {
        if (player.level() != target) {
            CrossDimensionTeleporter.markSkipAutoTeleport(player.getUUID());
        }
        player.teleportTo(target, x, y, z, yaw, pitch);
        com.stardew.craft.event.PlayerLocationStateGuardEvents.reconcileLocationState(player, true);
    }

    public static void to(ServerPlayer player, ServerLevel target, BlockPos pos,
                          float yaw, float pitch) {
        to(player, target, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
    }

    public static void to(ServerPlayer player, ServerLevel target, BlockPos pos) {
        to(player, target, pos, player.getYRot(), player.getXRot());
    }
}
