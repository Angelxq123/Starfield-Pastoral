package com.stardew.craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.utility.LuckyPurpleShortsBlock;
import com.stardew.craft.blockentity.LuckyPurpleShortsBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

@SuppressWarnings("deprecation")
public class LuckyPurpleShortsBlockEntityRenderer implements BlockEntityRenderer<LuckyPurpleShortsBlockEntity> {
    public LuckyPurpleShortsBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
        public void render(@Nonnull LuckyPurpleShortsBlockEntity blockEntity, float partialTick, @Nonnull PoseStack poseStack,
                                           @Nonnull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        float age = gameTime + partialTick;
        float bob = Mth.sin(age * 0.12F) * 0.10F;
        float rotation = age * 3.0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.20D + bob, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.scale(0.9F, 0.9F, 0.9F);
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        BlockState renderState = ModBlocks.LUCKY_PURPLE_SHORTS.get().defaultBlockState()
                .setValue(LuckyPurpleShortsBlock.FACING, Direction.NORTH);
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(renderState);
        ModelBlockRenderer renderer = minecraft.getBlockRenderer().getModelRenderer();
        RenderType renderType = ItemBlockRenderTypes.getRenderType(renderState, false);
        SpatialBlockModelRenderer.render(renderer,
            level,
            model,
                renderState,
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