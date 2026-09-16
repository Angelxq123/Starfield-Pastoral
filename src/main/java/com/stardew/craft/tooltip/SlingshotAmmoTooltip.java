package com.stardew.craft.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record SlingshotAmmoTooltip(ItemStack ammunition) implements TooltipComponent {
    public SlingshotAmmoTooltip { ammunition = ammunition.copy(); }
}
