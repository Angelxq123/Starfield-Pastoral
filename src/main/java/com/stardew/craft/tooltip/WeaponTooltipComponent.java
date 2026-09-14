package com.stardew.craft.tooltip;

import com.stardew.craft.item.weapon.WeaponData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Visual replacement for the built-in weapon text, with other tooltip text retained. */
public record WeaponTooltipComponent(ItemStack stack, WeaponData data, Component title,
                                     List<Component> extras, boolean expanded,
                                     int screenWidth, int screenHeight) implements TooltipComponent {
    public WeaponTooltipComponent {
        stack = stack.copy();
        title = title.copy();
        extras = List.copyOf(extras);
    }
}
