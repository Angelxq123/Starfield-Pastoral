package com.stardew.craft.client.gui;

import com.stardew.craft.menu.BarnManagerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BarnManagerScreen extends BuildingManagerScreen {
    public BarnManagerScreen(BarnManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
