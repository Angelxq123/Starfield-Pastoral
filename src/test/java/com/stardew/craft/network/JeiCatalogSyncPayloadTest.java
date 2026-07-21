package com.stardew.craft.network;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JeiCatalogSyncPayloadTest {
    @Test
    void entriesDefensivelyCopyMutableStacksAndSeasons() {
        ItemStack item = new ItemStack(Items.APPLE, 4);
        ItemStack geode = new ItemStack(Items.AMETHYST_CLUSTER, 2);
        ItemStack output = new ItemStack(Items.DIAMOND, 3);
        Set<Integer> seasons = new HashSet<>(Set.of(0, 2));
        ItemStack tradeItem = new ItemStack(Items.EMERALD, 5);

        JeiCatalogSyncPayload.ShopEntry shop = new JeiCatalogSyncPayload.ShopEntry(
                item, "example:shop", "Pierre", 50, 1, tradeItem, 5, 2,
                seasons, 2, 40, true, 4, 1, true, List.of("unknown"), false);
        JeiCatalogSyncPayload.GeodeEntry drop = new JeiCatalogSyncPayload.GeodeEntry(geode, output);
        item.setCount(1);
        geode.setCount(1);
        output.setCount(1);
        seasons.clear();
        tradeItem.setCount(1);

        assertEquals(4, shop.item().getCount());
        assertEquals("Pierre", shop.ownerNpcId());
        assertEquals(Set.of(0, 2), shop.seasons());
        assertEquals(5, shop.tradeItem().getCount());
        assertEquals(5, shop.tradeItemCount());
        assertEquals(2, shop.purchaseStack());
        assertEquals(40, shop.minMineLevel());
        assertEquals(4, shop.dayOfWeek());
        assertEquals(2, drop.geode().getCount());
        assertEquals(3, drop.output().getCount());

        shop.item().setCount(1);
        shop.tradeItem().setCount(1);
        drop.geode().setCount(1);
        drop.output().setCount(1);
        assertEquals(4, shop.item().getCount());
        assertEquals(5, shop.tradeItem().getCount());
        assertEquals(2, drop.geode().getCount());
        assertEquals(3, drop.output().getCount());
    }

    @Test
    void constructorRejectsCatalogsAboveTheEntryLimit() {
        JeiCatalogSyncPayload.ShopEntry entry = new JeiCatalogSyncPayload.ShopEntry(
                ItemStack.EMPTY, "", 0, 0, ItemStack.EMPTY, 0, 1,
                Set.of(), 1, 0, false, -1, 0, false, false);
        List<JeiCatalogSyncPayload.ShopEntry> oversized = Collections.nCopies(100_001, entry);

        assertThrows(IllegalArgumentException.class, () -> new JeiCatalogSyncPayload(oversized, List.of()));
    }

    @Test
    void sharedCatalogComposesWithIndependentPlayerGeodes() {
        JeiCatalogSyncPayload.ShopEntry shop = shopEntry(new ItemStack(Items.APPLE, 4));
        JeiCatalogSyncPayload.FishPondEntry fishPond = fishPondEntry(
                new ItemStack(Items.COD), new ItemStack(Items.EMERALD, 2));
        JeiCatalogSyncPayload.SharedCatalog shared =
                new JeiCatalogSyncPayload.SharedCatalog(List.of(shop), List.of(fishPond));

        JeiCatalogSyncPayload first = JeiCatalogSyncPayload.fromShared(shared, List.of(
                new JeiCatalogSyncPayload.GeodeEntry(
                        new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.DIAMOND, 2))));
        JeiCatalogSyncPayload second = JeiCatalogSyncPayload.fromShared(shared, List.of(
                new JeiCatalogSyncPayload.GeodeEntry(
                        new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.GOLD_INGOT, 3))));

        assertEquals("example:shop", first.shops().getFirst().shopId());
        assertEquals("example:shop", second.shops().getFirst().shopId());
        assertEquals(4, first.shops().getFirst().item().getCount());
        assertEquals(4, second.shops().getFirst().item().getCount());
        assertEquals(2, first.fishPonds().getFirst().output().getCount());
        assertEquals(2, second.fishPonds().getFirst().output().getCount());
        assertEquals(Items.DIAMOND, first.geodes().getFirst().output().getItem());
        assertEquals(2, first.geodes().getFirst().output().getCount());
        assertEquals(Items.GOLD_INGOT, second.geodes().getFirst().output().getItem());
        assertEquals(3, second.geodes().getFirst().output().getCount());

        first.geodes().getFirst().output().setCount(99);
        assertEquals(2, first.geodes().getFirst().output().getCount());
        assertEquals(3, second.geodes().getFirst().output().getCount());
    }

    @Test
    void sharedCatalogDefensivelyCopiesListsAndEntryStacks() {
        ItemStack shopStack = new ItemStack(Items.APPLE, 4);
        ItemStack fishStack = new ItemStack(Items.COD, 2);
        ItemStack pondOutput = new ItemStack(Items.EMERALD, 3);
        List<JeiCatalogSyncPayload.ShopEntry> shops = new ArrayList<>(List.of(shopEntry(shopStack)));
        List<JeiCatalogSyncPayload.FishPondEntry> fishPonds =
                new ArrayList<>(List.of(fishPondEntry(fishStack, pondOutput)));

        JeiCatalogSyncPayload.SharedCatalog shared = new JeiCatalogSyncPayload.SharedCatalog(shops, fishPonds);
        shops.clear();
        fishPonds.clear();
        shopStack.setCount(1);
        fishStack.setCount(1);
        pondOutput.setCount(1);

        assertEquals(1, shared.shops().size());
        assertEquals(1, shared.fishPonds().size());
        assertEquals(4, shared.shops().getFirst().item().getCount());
        assertEquals(2, shared.fishPonds().getFirst().fish().getCount());
        assertEquals(3, shared.fishPonds().getFirst().output().getCount());
        assertThrows(UnsupportedOperationException.class, () -> shared.shops().clear());
        assertThrows(UnsupportedOperationException.class, () -> shared.fishPonds().clear());

        shared.shops().getFirst().item().setCount(99);
        shared.fishPonds().getFirst().fish().setCount(99);
        shared.fishPonds().getFirst().output().setCount(99);
        assertEquals(4, shared.shops().getFirst().item().getCount());
        assertEquals(2, shared.fishPonds().getFirst().fish().getCount());
        assertEquals(3, shared.fishPonds().getFirst().output().getCount());
    }

    @Test
    void sharedCatalogNormalizesNullListsAndEnforcesEntryLimits() {
        JeiCatalogSyncPayload.SharedCatalog empty = new JeiCatalogSyncPayload.SharedCatalog(null, null);
        JeiCatalogSyncPayload.ShopEntry shop = shopEntry(ItemStack.EMPTY);
        JeiCatalogSyncPayload.FishPondEntry fishPond = fishPondEntry(ItemStack.EMPTY, ItemStack.EMPTY);
        JeiCatalogSyncPayload.GeodeEntry geode =
                new JeiCatalogSyncPayload.GeodeEntry(ItemStack.EMPTY, ItemStack.EMPTY);

        assertEquals(List.of(), empty.shops());
        assertEquals(List.of(), empty.fishPonds());
        assertThrows(IllegalArgumentException.class, () -> new JeiCatalogSyncPayload.SharedCatalog(
                Collections.nCopies(100_001, shop), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new JeiCatalogSyncPayload.SharedCatalog(
                List.of(), Collections.nCopies(100_001, fishPond)));
        assertThrows(IllegalArgumentException.class, () -> JeiCatalogSyncPayload.fromShared(
                empty, Collections.nCopies(100_001, geode)));
    }

    @Test
    void sharedCompositionRejectsNullArgumentsClearly() {
        JeiCatalogSyncPayload.SharedCatalog shared =
                new JeiCatalogSyncPayload.SharedCatalog(List.of(), List.of());

        NullPointerException missingShared = assertThrows(NullPointerException.class,
                () -> JeiCatalogSyncPayload.fromShared(null, List.of()));
        NullPointerException missingGeodes = assertThrows(NullPointerException.class,
                () -> JeiCatalogSyncPayload.fromShared(shared, null));
        NullPointerException missingPlayer = assertThrows(NullPointerException.class,
                () -> JeiCatalogSyncPayload.current(null, shared));

        assertEquals("shared", missingShared.getMessage());
        assertEquals("geodes", missingGeodes.getMessage());
        assertEquals("player", missingPlayer.getMessage());
    }

    private static JeiCatalogSyncPayload.ShopEntry shopEntry(ItemStack item) {
        return new JeiCatalogSyncPayload.ShopEntry(
                item, "example:shop", "Pierre", 50, 1, ItemStack.EMPTY, 0, 1,
                Set.of(0, 1, 2, 3), 1, 0, false, -1, 0, false, List.of(), false);
    }

    private static JeiCatalogSyncPayload.FishPondEntry fishPondEntry(ItemStack fish, ItemStack output) {
        return new JeiCatalogSyncPayload.FishPondEntry(
                fish, output, 3, 0.5D, 0.1D, 0.2D, 1, 2, false);
    }
}
