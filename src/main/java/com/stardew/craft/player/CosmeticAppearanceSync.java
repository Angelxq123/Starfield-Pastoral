package com.stardew.craft.player;

import com.stardew.craft.network.payload.CosmeticAppearanceSyncPayload;
import com.stardew.craft.network.payload.CosmeticAppearanceBatchSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CosmeticAppearanceSync {
    private CosmeticAppearanceSync() {
    }

    public static void sendToPlayer(ServerPlayer recipient, ServerPlayer subject, PlayerStardewData data) {
        PacketDistributor.sendToPlayer(recipient, payload(subject, data));
    }

    public static void broadcast(ServerPlayer subject, PlayerStardewData data) {
        PacketDistributor.sendToAllPlayers(payload(subject, data));
    }

    public static void syncAllTo(ServerPlayer recipient) {
        java.util.List<CosmeticAppearanceBatchSyncPayload.Appearance> appearances =
                new java.util.ArrayList<>();
        for (ServerPlayer subject : recipient.server.getPlayerList().getPlayers()) {
            if (subject == recipient) {
                continue;
            }
            PlayerStardewData data = PlayerDataManager.getPlayerData(subject);
            appearances.add(new CosmeticAppearanceBatchSyncPayload.Appearance(
                    subject.getUUID(),
                    data.getEquippedHat(),
                    data.getEquippedShirt(),
                    data.getEquippedPants()));
        }
        if (!appearances.isEmpty()) {
            PacketDistributor.sendToPlayer(recipient,
                    new CosmeticAppearanceBatchSyncPayload(appearances));
        }
    }

    private static CosmeticAppearanceSyncPayload payload(ServerPlayer subject, PlayerStardewData data) {
        return new CosmeticAppearanceSyncPayload(
                subject.getUUID(),
                data.getEquippedHat(),
                data.getEquippedShirt(),
                data.getEquippedPants()
        );
    }
}
