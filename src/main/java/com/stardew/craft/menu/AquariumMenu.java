package com.stardew.craft.menu;

import com.stardew.craft.aquarium.AquariumRules;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AquariumMenu extends AbstractContainerMenu {
    private final Container contents;
    public AquariumMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(AquariumRules.SIZE)); }
    public AquariumMenu(int id, Inventory inventory, Container contents) {
        super(ModMenuTypes.AQUARIUM.get(), id);
        this.contents = contents;
        checkContainerSize(contents, AquariumRules.SIZE);
        for (int i = 0; i < AquariumRules.SIZE; i++) {
            final int index = i;
            int x = i < 9 ? 12 + i * 18 + (i / 3) * 4 : 34 + ((i - 9) % 7) * 18;
            int y = i < 9 ? 35 : 77 + ((i - 9) / 7) * 18;
            addSlot(new Slot(contents, i, x, y) {
                @Override public boolean mayPlace(ItemStack stack) { return AquariumRules.canPlace(contents, index, stack); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public void onTake(Player player, ItemStack stack) {
                    super.onTake(player, stack);
                    if (!player.level().isClientSide) player.level().playSound(null, player.blockPosition(),
                            ModSounds.PULL_ITEM_FROM_WATER.get(), SoundSource.BLOCKS, .4f, 1);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 16 + col * 18, 145 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 16 + col * 18, 203));
    }
    @Override public boolean stillValid(Player player) { return contents.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (!(index < AquariumRules.SIZE ? moveItemStackTo(stack, AquariumRules.SIZE, slots.size(), true)
                : moveItemStackTo(stack, 0, AquariumRules.SIZE, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
