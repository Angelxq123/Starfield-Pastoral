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
        List<ItemStack> passOutLostItems,
        boolean personalSettlement
) implements CustomPacketPayload {

    /** 无晕倒的便捷构造（兼容旧调用点） */
    public OvernightSettlementPayload(List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(-1, shippedItems, levelUps, -1, 0, List.of(), true);
    }

    /** 带屏障日号、无晕倒的便捷构造 */
    public OvernightSettlementPayload(
            int absoluteDay, List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(absoluteDay, shippedItems, levelUps, -1, 0, List.of(), true);
    }

    /** 原五参数构造器保留给现有日结算与调试调用。 */
    public OvernightSettlementPayload(
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems) {
        this(-1, shippedItems, levelUps, passOutType, passOutMoneyLost,
                passOutLostItems, true);
    }

    public OvernightSettlementPayload(
            int absoluteDay,
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems) {
        this(absoluteDay, shippedItems, levelUps, passOutType, passOutMoneyLost,
                passOutLostItems, true);
    }

    public static OvernightSettlementPayload barrierOnly(int absoluteDay) {
        return new OvernightSettlementPayload(
                absoluteDay, List.of(), List.of(), -1, 0, List.of(), false);
    }

    /** 是否包含晕倒数据 */
    public boolean hasPassOut() {
        return passOutType >= 0;
    }

    public static final Type<OvernightSettlementPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "overnight_settlement"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OvernightSettlementPayload> STREAM_CODEC =
            new StreamCodec<>() {
                private final StreamCodec<RegistryFriendlyByteBuf, List<ShippedItem>> shipped =
                        ShippedItem.STREAM_CODEC.apply(ByteBufCodecs.list());
                private final StreamCodec<RegistryFriendlyByteBuf, List<LevelUpData>> levels =
                        LevelUpData.STREAM_CODEC.apply(ByteBufCodecs.list());
                private final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> lostItems =
                        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list());

                @Override
                public OvernightSettlementPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new OvernightSettlementPayload(
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            shipped.decode(buffer),
                            levels.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            ByteBufCodecs.VAR_INT.decode(buffer),
                            lostItems.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OvernightSettlementPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.absoluteDay());
                    shipped.encode(buffer, payload.shippedItems());
                    levels.encode(buffer, payload.levelUps());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.passOutType());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.passOutMoneyLost());
                    lostItems.encode(buffer, payload.passOutLostItems());
                    ByteBufCodecs.BOOL.encode(buffer, payload.personalSettlement());
                }
            };

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
