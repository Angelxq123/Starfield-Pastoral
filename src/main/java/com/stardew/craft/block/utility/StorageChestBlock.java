package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.StorageChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StorageChestBlock extends WoodenChestBlock {
    private final ChestVariant variant;
    private final VoxelShape shape;
    public StorageChestBlock(Properties properties, ChestVariant variant) {
        super(properties);
        this.variant = variant;
        this.shape = variant == ChestVariant.JUNIMO ? box(1, 0, 1, 15, 21, 15) : box(0, 0, 0, 16, variant == ChestVariant.BIG_WOOD ? 21.5 : 20.5, 16);
    }
    public ChestVariant variant() { return variant; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageChestBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape; }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest) {
            chest.bindOwner(player);
        }
    }
}
