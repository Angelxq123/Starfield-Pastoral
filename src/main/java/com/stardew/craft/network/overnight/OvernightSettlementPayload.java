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
import java.util.Objects;

@SuppressWarnings("null")
public record OvernightSettlementPayload(
        int absoluteDay,
        List<ShippedItem> shippedItems,
        List<LevelUpData> levelUps,
        int passOutType,               // -1 = 未晕倒；>=0 = PassOutService.PassOutType.getId()
        int passOutMoneyLost,
        List<ItemStack> passOutLostItems,
        OvernightContext context,
        boolean personalSettlement
) implements CustomPacketPayload {

    public OvernightSettlementPayload {
        shippedItems = List.copyOf(Objects.requireNonNull(shippedItems, "shippedItems"));
        levelUps = List.copyOf(Objects.requireNonNull(levelUps, "levelUps"));
        passOutLostItems = List.copyOf(Objects.requireNonNull(
                passOutLostItems, "passOutLostItems"));
        context = Objects.requireNonNull(context, "context");
    }

    /** 无晕倒的便捷构造（兼容旧调用点） */
    public OvernightSettlementPayload(List<ShippedItem> shippedItems, List<LevelUpData> levelUps) {
        this(-1, shippedItems, levelUps, -1, 0, List.of(),
                OvernightContext.unknown(), true);
    }

    /** 带多人结算屏障日号、无晕倒的便捷构造。 */
    public OvernightSettlementPayload(
            int absoluteDay,
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps
    ) {
        this(absoluteDay, shippedItems, levelUps, -1, 0, List.of(),
                OvernightContext.forAbsoluteDay(absoluteDay), true);
    }

    /** 兼容尚未提供日期/天气快照的旧调用点。 */
    public OvernightSettlementPayload(
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems
    ) {
        this(-1, shippedItems, levelUps, passOutType, passOutMoneyLost,
                passOutLostItems, OvernightContext.unknown(), true);
    }

    public OvernightSettlementPayload(
            int absoluteDay,
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems
    ) {
        this(absoluteDay, shippedItems, levelUps, passOutType, passOutMoneyLost,
                passOutLostItems, OvernightContext.forAbsoluteDay(absoluteDay), true);
    }

    /** 官方 0.5.4 日期/天气快照构造器。 */
    public OvernightSettlementPayload(
            List<ShippedItem> shippedItems,
            List<LevelUpData> levelUps,
            int passOutType,
            int passOutMoneyLost,
            List<ItemStack> passOutLostItems,
            OvernightContext context
    ) {
        this(context.absoluteDay(), shippedItems, levelUps, passOutType,
                passOutMoneyLost, passOutLostItems, context, true);
    }

    public static OvernightSettlementPayload barrierOnly(int absoluteDay) {
        return new OvernightSettlementPayload(
                absoluteDay, List.of(), List.of(), -1, 0, List.of(),
                OvernightContext.forAbsoluteDay(absoluteDay), false);
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
                            OvernightContext.STREAM_CODEC.decode(buffer),
                            ByteBufCodecs.BOOL.decode(buffer));
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OvernightSettlementPayload payload
                ) {
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.absoluteDay());
                    shipped.encode(buffer, payload.shippedItems());
                    levels.encode(buffer, payload.levelUps());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.passOutType());
                    ByteBufCodecs.VAR_INT.encode(buffer, payload.passOutMoneyLost());
                    lostItems.encode(buffer, payload.passOutLostItems());
                    OvernightContext.STREAM_CODEC.encode(buffer, payload.context());
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

    /**
     * Immutable night snapshot. The client must not infer these values from the
     * live time/weather caches: those caches switch to the new day while the
     * end-of-night menus are still being shown.
     */
    public record OvernightContext(
            int previousDay,
            int previousSeason,
            int previousYear,
            int newDay,
            int newSeason,
            int newYear,
            String previousWeather
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OvernightContext> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousSeason());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.previousYear());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newDay());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newSeason());
                    ByteBufCodecs.VAR_INT.encode(buffer, value.newYear());
                    ByteBufCodecs.STRING_UTF8.encode(buffer, value.previousWeather());
                },
                buffer -> new OvernightContext(
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.VAR_INT.decode(buffer),
                        ByteBufCodecs.STRING_UTF8.decode(buffer)
                )
        );

        public OvernightContext {
            previousDay = Math.max(1, previousDay);
            previousSeason = Math.max(0, Math.min(3, previousSeason));
            previousYear = Math.max(1, previousYear);
            newDay = Math.max(1, newDay);
            newSeason = Math.max(0, Math.min(3, newSeason));
            newYear = Math.max(1, newYear);
            previousWeather = previousWeather == null || previousWeather.isBlank() ? "Sun" : previousWeather;
        }

        public static OvernightContext unknown() {
            return new OvernightContext(1, 0, 1, 2, 0, 1, "Sun");
        }

        public static OvernightContext forAbsoluteDay(int absoluteDay) {
            int target = Math.max(1, absoluteDay);
            int previous = Math.max(1, target - 1);
            DateParts oldDate = dateParts(previous);
            DateParts newDate = dateParts(target);
            return new OvernightContext(
                    oldDate.day(), oldDate.season(), oldDate.year(),
                    newDate.day(), newDate.season(), newDate.year(), "Sun");
        }

        public static OvernightContext forTargetDate(
                int newYear,
                int newSeason,
                int newDay,
                String previousWeather
        ) {
            int target = (Math.max(1, newYear) - 1) * 112
                    + Math.max(0, Math.min(3, newSeason)) * 28
                    + Math.max(1, Math.min(28, newDay));
            DateParts previous = dateParts(Math.max(1, target - 1));
            return new OvernightContext(
                    previous.day(), previous.season(), previous.year(),
                    newDay, newSeason, newYear, previousWeather);
        }

        public int absoluteDay() {
            return (newYear - 1) * 112 + newSeason * 28 + newDay;
        }

        private static DateParts dateParts(int absoluteDay) {
            int zeroBased = Math.max(1, absoluteDay) - 1;
            return new DateParts(
                    zeroBased / 112 + 1,
                    zeroBased % 112 / 28,
                    zeroBased % 28 + 1);
        }

        private record DateParts(int year, int season, int day) {
        }
    }
}
