package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

@SuppressWarnings("null")
public record OvernightSettlementPayload(
        int absoluteDay,
        List<ShippedItem> shippedItems,
        List<LevelUpData> levelUps,
        int passOutType,               // -1 = 未晕倒；>=0 = PassOutService.PassOutType.getId()
        int passOutMoneyLost,
        List<ItemStack> passOutLostItems
) implements CustomPacketPayload {

    /** 无晕倒的便捷构造（兼容旧调用点） */
    public OvernightSettlementPayload(List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(-1, shippedItems, levelUps, -1, 0, List.of());
    }

    /** 带屏障日号、无晕倒的便捷构造 */
    public OvernightSettlementPayload(
            int absoluteDay, List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(absoluteDay, shippedItems, levelUps, -1, 0, List.of());
    }

    /** 原五参数构造器保留给现有日结算与调试调用。 */
    public OvernightSettlementPayload(
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems) {
        this(-1, shippedItems, levelUps, passOutType, passOutMoneyLost, passOutLostItems);
    }

    /** 是否包含晕倒数据 */
    public boolean hasPassOut() {
        return passOutType >= 0;
    }

    public static final Type<OvernightSettlementPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_settlement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightSettlementPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OvernightSettlementPayload::absoluteDay,
            ShippedItem.STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::shippedItems,
            LevelUpData.STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::levelUps,
            ByteBufCodecs.VAR_INT, OvernightSettlementPayload::passOutType,
            ByteBufCodecs.VAR_INT, OvernightSettlementPayload::passOutMoneyLost,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), OvernightSettlementPayload::passOutLostItems,
            OvernightSettlementPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OvernightSettlementPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OvernightSettlementPayload payload) {
        ClientOvernightHandler.receiveSettlement(payload);
    }

    public record LevelUpData(int skillIndex, int newLevel) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LevelUpData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUpData::skillIndex,
                ByteBufCodecs.VAR_INT, LevelUpData::newLevel,
                LevelUpData::new
        );
    }

    public record ShippedItem(ItemStack stack, int category, int pricePerItem) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ShippedItem> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, ShippedItem::stack,
                ByteBufCodecs.VAR_INT, ShippedItem::category,
                ByteBufCodecs.VAR_INT, ShippedItem::pricePerItem,
                ShippedItem::new
        );
    }
}
