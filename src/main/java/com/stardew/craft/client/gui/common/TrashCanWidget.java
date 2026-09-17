package com.stardew.craft.client.gui.common;

import com.mojang.math.Axis;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.inventory.InventoryTrashPolicy;
import com.stardew.craft.inventory.TrashCanService;
import com.stardew.craft.inventory.TrashCanTier;
import com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shared rendering and reclaim preview for every carried-item trash-can control. */
@OnlyIn(Dist.CLIENT)
public final class TrashCanWidget {
    private TrashCanWidget() {
    }

    public static void render(GuiGraphics graphics, int bodyX, int bodyY, float bodyScale,
                              int lidPivotX, int lidPivotY, float lidScale,
                              int lidX, int lidY, float lidRotation) {
        int level = ClientPlayerDataCache.getTrashCanLevel();
        CommonGuiTextures.drawGameMenuTrashBody(graphics, bodyX, bodyY, bodyScale, level);
        graphics.pose().pushPose();
        graphics.pose().translate(lidPivotX, lidPivotY, 0);
        graphics.pose().mulPose(Axis.ZP.rotation(lidRotation));
        graphics.pose().scale(lidScale, lidScale, 1.0f);
        CommonGuiTextures.drawGameMenuTrashLidAtCurrentPose(graphics, lidX, lidY, level);
        graphics.pose().popPose();
    }

    public static int previewRefund(ItemStack stack) {
        if (!TrashCanService.isReclaimEligible(stack)) {
            return 0;
        }
        SellQuote quote = ProfessionSellPriceService.quoteItemForProfessionNames(
                Set.copyOf(ClientPlayerDataCache.getProfessions()),
                ClientPlayerDataCache.getMailFlags(), ClientPlayerDataCache.getSpecialItems(),
                ClientPlayerDataCache.getStat("Book_Artifact") > 0,
                stack, SellSource.TRASH_CAN);
        TrashCanTier tier = TrashCanTier.forLevel(ClientPlayerDataCache.getTrashCanLevel());
        return TrashCanService.calculateRefund(quote.finalUnitPrice(), stack.getCount(), tier.reclaimPercent());
    }

    public static List<Component> tooltip(ItemStack carried) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("stardewcraft.game_menu.crafting.trash_can").withStyle(ChatFormatting.WHITE));
        if (carried == null || carried.isEmpty()) {
            return lines;
        }
        lines.add(carried.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        int refund = previewRefund(carried);
        if (refund > 0) {
            lines.add(Component.translatable("stardewcraft.inventory.trash_can.reclaim", refund)
                    .withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    /** Reusable small control for container-style screens. */
    public static final class Controller {
        private float lidRotation;
        private long lastFrame;
        private boolean wasHovered;

        public int xBeside(int panelX, int panelWidth, int screenWidth) {
            int right = panelX + panelWidth + 4;
            if (right + 18 <= screenWidth) {
                return right;
            }
            int left = panelX - 22;
            if (left >= 0) {
                return left;
            }
            return Math.max(0, Math.min(screenWidth - 18, panelX + panelWidth - 22));
        }

        public boolean contains(int x, int y, double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 34;
        }

        public void render(GuiGraphics graphics, int x, int y, double mouseX, double mouseY) {
            boolean hovered = contains(x, y, mouseX, mouseY);
            Minecraft client = Minecraft.getInstance();
            if (hovered && !wasHovered) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TRASHCANLID.get(), 1.0f));
            }
            wasHovered = hovered;
            long now = net.minecraft.Util.getMillis();
            float delta = lastFrame == 0L ? 1.0f : Math.min(1.0f, (now - lastFrame) / 90.0f);
            lastFrame = now;
            lidRotation += ((hovered ? -0.45f : 0.0f) - lidRotation) * delta;
            TrashCanWidget.render(graphics, x, y + 8, 1.0f, x + 2, y + 8, 1.0f,
                    -2, -8, lidRotation);
        }

        public boolean click(AbstractContainerMenu menu, int x, int y,
                             double mouseX, double mouseY, int button) {
            if (button != 0 || !contains(x, y, mouseX, mouseY)) {
                return false;
            }
            trashCarried(menu);
            return true;
        }

        public boolean deleteKey(AbstractContainerMenu menu, int keyCode) {
            if (keyCode != com.mojang.blaze3d.platform.InputConstants.KEY_DELETE || menu.getCarried().isEmpty()) {
                return false;
            }
            trashCarried(menu);
            return true;
        }

        public boolean renderTooltip(GuiGraphics graphics, Font font, AbstractContainerMenu menu, int x, int y,
                                     int mouseX, int mouseY) {
            if (!contains(x, y, mouseX, mouseY)) {
                return false;
            }
            graphics.renderTooltip(font, TrashCanWidget.tooltip(menu.getCarried()),
                    java.util.Optional.empty(), mouseX, mouseY);
            return true;
        }

        private void trashCarried(AbstractContainerMenu menu) {
            Minecraft client = Minecraft.getInstance();
            ItemStack carried = menu.getCarried();
            if (InventoryTrashPolicy.canTrash(carried)) {
                PacketDistributor.sendToServer(new CraftingMenuInventoryActionPayload(
                        CraftingMenuInventoryActionPayload.ACTION_TRASH_CARRIED, -1, false));
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.TRASHCAN.get(), 1.0f));
            } else if (!carried.isEmpty()) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.CANCEL.get(), 1.0f));
            }
        }
    }
}
