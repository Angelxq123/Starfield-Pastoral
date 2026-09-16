package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → Client: show Marnie's question dialog (Supplies / Purchase Animals / Leave).
 */
@SuppressWarnings("null")
public record OpenMarnieMenuPayload() implements CustomPacketPayload {

    public static final Type<OpenMarnieMenuPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_marnie_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenMarnieMenuPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {},
        buf -> new OpenMarnieMenuPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMarnieMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenMarnieMenuPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.npc.marnie.menu.question"),
                List.of(
                    Component.translatable("stardewcraft.npc.marnie.menu.supplies"),
                    Component.translatable("stardewcraft.npc.marnie.menu.purchase"),
                    Component.translatable("pet.stardewcraft.adopt"),
                    Component.translatable("stardewcraft.npc.marnie.menu.leave")
                ),
                index -> {
                    if (index < 3) {
                        PacketDistributor.sendToServer(new MarnieMenuChoicePayload(index == 2 ? 3 : index));
                    }
                },
                -1
            )
        ));
    }
}
