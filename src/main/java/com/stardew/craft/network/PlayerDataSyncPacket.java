package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerStardewData;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * 玩家数据同步包（服务端 -> 客户端）
 * 使用NBT传输完整数据
 */
public record PlayerDataSyncPacket(CompoundTag data) implements CustomPacketPayload {
    private static final Set<String> CLIENT_FIELDS = Set.of(
            "Health", "MaxHealth", "Energy", "MaxEnergy", "Exhausted", "Money",
            "MaxMineFloorReached", "TicketPrizesClaimed", "SpecialOrderPrizeTickets",
            "FairStarTokens", "ClubCoins", "FirstJoinDay", "WinterStarRecipient",
            "Gender", "PreferredName", "FavoriteThing", "LostBooksFound", "LostBookInteractions",
            "TempFishingLevelBonus", "TempLuckBonus", "TempMaxEnergyBonus",
            "TempFarmingLevelBonus", "TempForagingLevelBonus", "TempMiningLevelBonus",
            "Experience", "SkillLevels", "MasteryExp", "MasteryLevelsSpent",
            "ClaimedMasteryRewards", "GotMasteryHint", "VisitedMasteryCave",
            "UnlockedTrinketSlots", "Professions", "UnlockedRecipes", "MailFlags",
            "SpecialItems", "SecretNotesSeen", "RevealedGiftTastes", "Stats",
            "HasFarm", "FarmName", "FarmOwnerUUID", "RecipeCraftCounts",
            "FishCatchCounts", "ShippedBasic", "ItemsShipped");
    
    @SuppressWarnings("null")
    public static final Type<PlayerDataSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "player_data_sync")
    );
    
    @SuppressWarnings("null")
    public static final StreamCodec<ByteBuf, PlayerDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG,
        PlayerDataSyncPacket::data,
        PlayerDataSyncPacket::new
    );
    
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * 从玩家数据创建数据包
     */
    public static PlayerDataSyncPacket fromPlayerData(PlayerStardewData playerData) {
        CompoundTag nbt = clientView(playerData.toNBT());
        return new PlayerDataSyncPacket(nbt);
    }

    static CompoundTag clientView(CompoundTag persisted) {
        CompoundTag client = new CompoundTag();
        for (String key : CLIENT_FIELDS) {
            Tag value = persisted.get(key);
            if (value != null) {
                client.put(key, value.copy());
            }
        }
        return client;
    }
    
    /**
     * 客户端接收处理
     */
    public static void handle(PlayerDataSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 更新客户端缓存
            com.stardew.craft.client.ClientPlayerDataCache.updateFromNBT(packet.data());
        });
    }
}
