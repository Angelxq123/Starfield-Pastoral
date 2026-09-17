package com.stardew.craft.mixin;

import com.stardew.craft.client.gui.menu.StardewGameMenuScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client container corrections for SDV slot hit targets and one-slot shift moves. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenSlotHitMixin implements StardewGameMenuScreen.QuickCraftStateAccess {
    @Shadow
    private int quickCraftingType;

    @Shadow
    private int quickCraftingRemainder;

    @Shadow
    private boolean doubleclick;

    @Override
    public int stardewcraft$quickCraftingType() {
        return quickCraftingType;
    }

    @Override
    public int stardewcraft$quickCraftingRemainder() {
        return quickCraftingRemainder;
    }

    /**
     * Vanilla treats shift-double-click as a request to quick-move every matching stack in
     * the same inventory. A normal second shift-click is already sent on mouse-down, so the
     * release sweep makes one physical action appear to consume an adjacent stack as well.
     * Keep ordinary double-click collection, but make every shift-click affect one slot.
     */
    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void stardewcraft$keepShiftQuickMoveToOneSlot(double mouseX, double mouseY, int button,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (Screen.hasShiftDown()) {
            doubleclick = false;
        }
    }

    @Inject(method = "findSlot", at = @At("HEAD"), cancellable = true)
    private void stardewcraft$findVisualInventorySlot(double mouseX, double mouseY,
                                                       CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof StardewGameMenuScreen screen) {
            Slot slot = screen.findVisualInventorySlot(mouseX, mouseY);
            if (slot != null) {
                cir.setReturnValue(slot);
            }
        }
    }
}
