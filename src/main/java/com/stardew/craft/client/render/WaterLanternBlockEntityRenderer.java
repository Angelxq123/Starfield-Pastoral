package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.blockentity.WaterLanternBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class WaterLanternBlockEntityRenderer implements BlockEntityRenderer<WaterLanternBlockEntity> {
    private static final float WATERLINE_OFFSET = -0.16F;
    private static final float BOB_AMPLITUDE = 0.08F;

    public WaterLanternBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    @SuppressWarnings({ "null", "deprecation" })
    public void render(@Nonnull WaterLanternBlockEntity blockEntity, float partialTick, @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        poseStack.pushPose();
        float time = level.getGameTime() + partialTick;
        long seed = blockEntity.getBlockPos().asLong();
        float phase = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / 4096.0F;
        float bob = WATERLINE_OFFSET + (float) Math.sin((time + phase) * 0.07F) * BOB_AMPLITUDE;
        poseStack.translate(0.0F, bob, 0.0F);

        BlockState state = blockEntity.getBlockState();
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        SpatialBlockModelRenderer.render(renderer,
                level,
                model,
                state,
                blockEntity.getBlockPos(),
                poseStack,
                buffer.getBuffer(renderType),
                true,
                RandomSource.create(0L),
                0L,
                packedOverlay
        );
        poseStack.popPose();
    }
}