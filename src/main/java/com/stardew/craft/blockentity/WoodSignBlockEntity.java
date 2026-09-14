package com.stardew.craft.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Display-only copy: never expose an item handler or drop its contents. */
@SuppressWarnings("null")
public final class WoodSignBlockEntity extends BlockEntity {
    private ItemStack displayItem = ItemStack.EMPTY;

    public WoodSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WOOD_SIGN.get(), pos, state);
    }

    public ItemStack getDisplayItem() {
        return displayItem.copy();
    }

    public void setDisplayItem(ItemStack stack) {
        displayItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayItem.isEmpty()) tag.put("DisplayItem", displayItem.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayItem = ItemStack.parseOptional(registries, tag.getCompound("DisplayItem"));
        if (!displayItem.isEmpty()) displayItem.setCount(1);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
