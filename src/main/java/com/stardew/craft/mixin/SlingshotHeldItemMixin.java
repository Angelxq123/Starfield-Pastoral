package com.stardew.craft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.client.slingshot.SlingshotRenderer;
import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both camera paths keep vanilla bow/player transforms; only the item mesh is animated. */
@Mixin(ItemInHandRenderer.class)
public class SlingshotHeldItemMixin {
    @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
    private void slingshot$itemOnly(LivingEntity entity,ItemStack stack,ItemDisplayContext context,boolean left,
                                    PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(stack.getItem() instanceof SlingshotItem){SlingshotRenderer.renderHeld(entity,stack,context,left,pose,buffers,light);ci.cancel();}
    }
}
