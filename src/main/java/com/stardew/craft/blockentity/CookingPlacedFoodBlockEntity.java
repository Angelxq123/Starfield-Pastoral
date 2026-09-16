package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CookingPlacedFoodBlockEntity extends BlockEntity {
    private ItemStack storedFood = ItemStack.EMPTY;

    public CookingPlacedFoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLACED_COOKING_FOOD.get(), pos, state);
    }

    public ItemStack getStoredFood() {
        return storedFood.copy();
    }

    public void setStoredFood(ItemStack stack) {
        storedFood = stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (!storedFood.isEmpty()) {
            tag.put("StoredFood", storedFood.save(provider));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        storedFood = tag.contains("StoredFood", CompoundTag.TAG_COMPOUND)
                ? ItemStack.parseOptional(provider, tag.getCompound("StoredFood")) : ItemStack.EMPTY;
        if (!storedFood.isEmpty()) storedFood.setCount(1);
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
