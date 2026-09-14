package com.stardew.craft.gametest;

import com.stardew.craft.menu.WoodenChestMenu;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_chest_controls")
@PrefixGameTestTemplate(false)
public final class ChestControlGameTests {
    @GameTest(templateNamespace = "stardewcraft_chest_controls", template = "ring_utilities")
    public static void rewardCapabilitiesReachClientAndRejectActions(GameTestHelper helper) {
        var inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();
        for (boolean reward : new boolean[]{false, true}) {
            for (boolean colorable : new boolean[]{false, true}) {
                var container = new SimpleContainer(27);
                container.setItem(3, new ItemStack(Items.DIAMOND, 2));
                var changes = new AtomicInteger();
                var server = new WoodenChestMenu(1, inventory, container,
                        colorable ? ignored -> changes.incrementAndGet() : null, -1, reward);
                var client = new WoodenChestMenu(1, inventory);
                helper.assertTrue(!client.canChangeColor() && !client.canOrganize(),
                        "Client enabled actions before receiving server capabilities");
                server.setSynchronizer(new ContainerSynchronizer() {
                    @Override
                    public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                                ItemStack carried, int[] data) {
                        for (int index = 0; index < data.length; index++) client.setData(index, data[index]);
                    }
                    @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {}
                    @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {}
                    @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
                        client.setData(id, value);
                    }
                });
                helper.assertTrue(client.canChangeColor() == (!reward && colorable), "Incorrect client color capability");
                helper.assertTrue(client.canOrganize() == !reward, "Incorrect client organize capability");
                helper.assertTrue(client.slots.getFirst().mayPlace(new ItemStack(Items.DIRT)) == !reward,
                        "Client slot retained the constructor's stale reward flag");
                server.setColorSelectionFromClient(5);
                helper.assertTrue(changes.get() == (!reward && colorable ? 1 : 0), "Color handler ignored reward restriction");
                server.organizeContainer();
                if (reward) {
                    helper.assertTrue(container.getItem(3).is(Items.DIAMOND) && container.getItem(3).getCount() == 2
                            && container.getItem(0).isEmpty(), "Reward inventory was reorganized");
                } else {
                    helper.assertTrue(container.getItem(0).is(Items.DIAMOND) && container.getItem(3).isEmpty(),
                            "Ordinary storage lost its organize action");
                }
            }
        }
        helper.succeed();
    }
}
