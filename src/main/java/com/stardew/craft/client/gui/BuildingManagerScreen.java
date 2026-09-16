package com.stardew.craft.client.gui;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.menu.IBuildingManagerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Existing container providers share the folio without changing their server protocol. */
public class BuildingManagerScreen extends FarmFolioScreen
        implements MenuAccess<AbstractContainerMenu> {
    private final AbstractContainerMenu menu;
    private final IBuildingManagerMenu manager;
    private int page;

    public BuildingManagerScreen(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(title, null);
        this.menu = menu;
        manager = (IBuildingManagerMenu) menu;
    }

    @Override
    public AbstractContainerMenu getMenu() {
        return menu;
    }

    private Component tr(String key, Object... args) {
        return Component.translatable(
                "gui.stardew_craft." + manager.buildingFamily() + "_manager." + key, args);
    }

    private record Requirement(ItemStack item, Component name, int have, int need) {}

    private List<Requirement> requirements() {
        var rows = new ArrayList<Requirement>();
        if (manager.isEnclosedRequired())
            rows.add(
                    new Requirement(
                            new ItemStack(Items.BRICKS),
                            Component.translatable("gui.stardew_craft.animal_manager.enclosure"),
                            manager.isEnclosed() ? 1 : 0,
                            1));
        if (manager.getReqInteriorBlocks() > 0)
            rows.add(
                    new Requirement(
                            new ItemStack(Items.SCAFFOLDING),
                            Component.translatable("gui.stardew_craft.animal_manager.space"),
                            manager.getCurInteriorBlocks(),
                            manager.getReqInteriorBlocks()));
        if (manager.isDoorRequired())
            rows.add(
                    new Requirement(
                            new ItemStack(Items.OAK_DOOR),
                            Component.translatable("gui.stardew_craft.animal_manager.door"),
                            manager.getCurDoorCount(),
                            manager.getReqDoorCount()));
        int[]
                have =
                        {
                            manager.getCurFeedTrough(),
                            manager.getCurAutoFeedTrough(),
                            manager.getCurHayHopper(),
                            manager.getCurIncubator()
                        },
                need =
                        {
                            manager.getReqFeedTrough(),
                            manager.getReqAutoFeedTrough(),
                            manager.getReqHayHopper(),
                            manager.getReqIncubator()
                        };
        net.minecraft.world.level.block.Block[] blocks = {
            ModBlocks.FEED_TROUGH.get(),
            ModBlocks.AUTOFEED_TROUGH.get(),
            ModBlocks.HAY_HOPPER.get(),
            ModBlocks.INCUBATOR.get()
        };
        for (int i = 0; i < need.length; i++)
            if (need[i] > 0) {
                var item = new ItemStack(blocks[i]);
                rows.add(new Requirement(item, item.getHoverName(), have[i], need[i]));
            }
        return rows;
    }

    private void send(int action) {
        if (minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override
    protected void layout() {
        int right = x + w - 172;
        button(Component.translatable("gui.back"), x + 16, y + h - 34, 94, 24, this::onClose);
        button(
                                tr("relocate"),
                                right,
                                y + 52,
                                156,
                                30,
                                "button",
                                "move",
                                () -> minecraft.setScreen(new Confirm(false)))
                        .active =
                manager.hasExistingBuilding();
        button(tr("demolish"), right, y + 92, 156, 30, () -> minecraft.setScreen(new Confirm(true)))
                        .active =
                manager.hasExistingBuilding();
        button(
                                manager.isAtMaxTier()
                                        ? tr("max")
                                        : tr(manager.hasExistingBuilding() ? "upgrade" : "build"),
                                right,
                                y + h - 74,
                                156,
                                30,
                                "button",
                                "home",
                                () -> send(IBuildingManagerMenu.ACTION_BUILD_OR_UPGRADE))
                        .active =
                !manager.isAtMaxTier() && manager.canBuildOrUpgrade();
        int count = Math.max(1, (h - 160) / 30), total = requirements().size();
        arrow(
                false,
                x + 28,
                y + h - 70,
                page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + 98,
                y + h - 70,
                (page + 1) * count < total,
                () -> {
                    page++;
                    init();
                });
    }

    private int state;

    @Override
    public void tick() {
        int current =
                java.util.Objects.hash(
                        requirements().stream()
                                .map(r -> java.util.List.of(r.have(), r.need()))
                                .toList(),
                        manager.getCurrentTier(),
                        manager.canBuildOrUpgrade(),
                        manager.getBoundAnimalCount());
        if (current != state) {
            state = current;
            init();
        }
    }

    @Override
    protected void paint(GuiGraphics g) {
        int pw = w - 204;
        paper(g, x + 16, y + 42, pw, h - 86);
        label(
                g,
                tr(
                        "stage",
                        tr(
                                manager.getCurrentTier() > 0
                                        ? "stage.t" + manager.getCurrentTier()
                                        : "stage.unformed")),
                x + 28,
                y + 56,
                pw - 24,
                INK);
        label(
                g,
                Component.translatable(
                        "livestock.stardewcraft.occupancy",
                        manager.getBoundAnimalCount(),
                        manager.getCurrentCapacity()),
                x + 28,
                y + 78,
                pw - 24,
                MUTED);
        int count = Math.max(1, (h - 160) / 30);
        var rows = requirements();
        for (int i = page * count; i < Math.min(rows.size(), (page + 1) * count); i++) {
            var row = rows.get(i);
            int yy = y + 106 + (i - page * count) * 30;
            item(g, row.item(), x + 28, yy, 1);
            label(g, row.name(), x + 52, yy + 4, pw - 116, INK);
            label(
                    g,
                    Component.literal(row.have() + " / " + row.need()),
                    x + pw - 58,
                    yy + 4,
                    62,
                    row.have() >= row.need() ? INK : RED);
        }
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.player.closeContainer();
        minecraft.setScreen(null);
    }

    private final class Confirm extends FarmFolioScreen {
        private final boolean demolish;

        Confirm(boolean demolish) {
            super(tr(demolish ? "demolish" : "relocate"), BuildingManagerScreen.this);
            this.demolish = demolish;
        }

        @Override
        protected int preferredWidth() {
            return 404;
        }

        @Override
        protected int preferredHeight() {
            return 240;
        }

        @Override
        protected void layout() {
            button(
                    Component.translatable("gui.cancel"),
                    x + 16,
                    y + h - 38,
                    118,
                    26,
                    this::onClose);
            button(
                                    Component.translatable("gui.done"),
                                    x + w - 164,
                                    y + h - 38,
                                    148,
                                    26,
                                    () -> {
                                        minecraft.setScreen(parent);
                                        send(
                                                demolish
                                                        ? IBuildingManagerMenu.ACTION_DEMOLISH
                                                        : IBuildingManagerMenu.ACTION_RELOCATE);
                                    })
                            .active =
                    !demolish || manager.getBoundAnimalCount() == 0;
        }

        @Override
        protected void paint(GuiGraphics g) {
            paper(g, x + 16, y + 36, w - 32, h - 88);
            String stem = "dialog." + (demolish ? "demolish" : "relocate") + ".";
            int yy = y + 52;
            for (String key :
                    List.of(
                            "line1",
                            "line2",
                            demolish && manager.getBoundAnimalCount() > 0
                                    ? "blocked_animals"
                                    : "line3"))
                yy =
                        paragraph(
                                        g,
                                        tr(stem + key, manager.getBoundAnimalCount()),
                                        x + 30,
                                        yy,
                                        w - 60,
                                        y + h - 62,
                                        MUTED)
                                + 8;
        }
    }
}
