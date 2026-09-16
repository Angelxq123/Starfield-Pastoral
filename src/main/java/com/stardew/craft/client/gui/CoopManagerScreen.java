package com.stardew.craft.client.gui;

import com.stardew.craft.menu.CoopManagerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CoopManagerScreen extends BuildingManagerScreen {
    public CoopManagerScreen(CoopManagerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
