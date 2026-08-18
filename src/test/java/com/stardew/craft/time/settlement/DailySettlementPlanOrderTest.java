package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementPlanOrderTest {
    @Test
    void planUsesTheExactPhaseAndWorkUnitOrder() {
        List<PlannedItem> actual = new ArrayList<>();
        AtomicReference<DailySettlementCoordinator> coordinatorRef = new AtomicReference<>();
        DailySettlementPlanFactory factory = new DailySettlementPlanFactory((name, context) ->
                DailySettlementWorkUnits.atomic(name, () -> actual.add(new PlannedItem(
                        coordinatorRef.get().phase(), name)), () -> {}));
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L),
                () -> 1_000_000L,
                () -> 1,
                factory,
                DailySettlementCoordinator.LifecycleListener.NOOP);
        coordinatorRef.set(coordinator);

        coordinator.start(context());
        for (int guard = 0; coordinator.isActive() && guard < 100; guard++) {
            coordinator.tick();
        }

        assertFalse(coordinator.isActive(), "ready publication must return the coordinator to idle");
        assertEquals(List.of(
                item(DailySettlementPhase.PREPARE, "shipping_bin_flush"),
                item(DailySettlementPhase.PREPARE, "non_participant_cleanup"),
                item(DailySettlementPhase.PREPARE, "daily_process_scope"),

                item(DailySettlementPhase.WORLD_BATCHES, "festival_season_prep"),
                item(DailySettlementPhase.WORLD_BATCHES, "npc_friendship_daily"),
                item(DailySettlementPhase.WORLD_BATCHES, "npc_dialogue_events"),
                item(DailySettlementPhase.WORLD_BATCHES, "npc_dialogue_topics"),
                item(DailySettlementPhase.WORLD_BATCHES, "weather_npc_reset"),
                item(DailySettlementPhase.WORLD_BATCHES, "crops"),
                item(DailySettlementPhase.WORLD_BATCHES, "trees"),
                item(DailySettlementPhase.WORLD_BATCHES, "fruit_trees"),
                item(DailySettlementPhase.WORLD_BATCHES, "tea_bushes"),
                item(DailySettlementPhase.WORLD_BATCHES, "wild_tree_seeds"),
                item(DailySettlementPhase.WORLD_BATCHES, "farm_debris"),
                item(DailySettlementPhase.WORLD_BATCHES, "sprinklers"),
                item(DailySettlementPhase.WORLD_BATCHES, "pasture_grass"),
                item(DailySettlementPhase.WORLD_BATCHES, "animals"),
                item(DailySettlementPhase.WORLD_BATCHES, "fish_ponds"),
                item(DailySettlementPhase.WORLD_BATCHES, "public_forage"),
                item(DailySettlementPhase.WORLD_BATCHES, "forest_farm_forage"),
                item(DailySettlementPhase.WORLD_BATCHES, "artifact_spots"),
                item(DailySettlementPhase.WORLD_BATCHES, "quarry"),
                item(DailySettlementPhase.WORLD_BATCHES, "coal_forest"),
                item(DailySettlementPhase.WORLD_BATCHES, "secret_woods_entrance"),
                item(DailySettlementPhase.WORLD_BATCHES, "farm_caves"),
                item(DailySettlementPhase.WORLD_BATCHES, "addon_farm_tasks"),

                item(DailySettlementPhase.PLAYER_BATCHES, "player_daily_settlement"),

                item(DailySettlementPhase.COMMIT, "weather_forecast"),
                item(DailySettlementPhase.COMMIT, "farm_cursor"),
                item(DailySettlementPhase.COMMIT, "special_orders"),
                item(DailySettlementPhase.COMMIT, "lost_and_found"),
                item(DailySettlementPhase.COMMIT, "bookseller"),
                item(DailySettlementPhase.COMMIT, "shop_stock"),
                item(DailySettlementPhase.COMMIT, "mail"),
                item(DailySettlementPhase.COMMIT, "dirty_mark"),
                item(DailySettlementPhase.COMMIT, "daily_process_cleanup"),
                item(DailySettlementPhase.COMMIT, "date_publication")
        ), actual);
    }

    @Test
    void publicationIsTheLastCommitItemAndPlayerWorkFollowsAllWorldWork() {
        List<String> names = new ArrayList<>();
        DailySettlementPlanFactory factory = new DailySettlementPlanFactory((name, context) ->
                DailySettlementWorkUnits.atomic(name, () -> names.add(name), () -> {}));
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 100,
                factory, DailySettlementCoordinator.LifecycleListener.NOOP);

        coordinator.start(context());
        while (coordinator.isActive()) {
            coordinator.tick();
        }

        assertEquals("date_publication", names.getLast());
        assertEquals(1, names.stream().filter("date_publication"::equals).count());
        assertEquals(1, names.stream().filter("shipping_bin_flush"::equals).count());
        assertEquals(1, names.stream().filter("player_daily_settlement"::equals).count());
        assertEquals(0, names.subList(names.indexOf("date_publication") + 1, names.size()).size());
        assertEquals(26, names.indexOf("player_daily_settlement"));
        assertEquals(28, names.indexOf("farm_cursor"));
        assertEquals(0, names.indexOf("shipping_bin_flush"));
    }

    @Test
    void shippingFlushPrecedesEveryPlayerSettlementItem() {
        List<String> names = runPlan();

        assertEquals(0, names.indexOf("shipping_bin_flush"));
        assertTrue(names.indexOf("shipping_bin_flush")
                < names.indexOf("player_daily_settlement"));
    }

    @Test
    void noCommitWorkCanFollowDatePublication() {
        List<String> names = runPlan();

        assertEquals("date_publication", names.getLast());
        assertEquals(1, names.stream().filter("date_publication"::equals).count());
    }

    private static List<String> runPlan() {
        List<String> names = new ArrayList<>();
        DailySettlementPlanFactory factory = new DailySettlementPlanFactory((name, context) ->
                DailySettlementWorkUnits.atomic(name, () -> names.add(name), () -> {}));
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 100,
                factory, DailySettlementCoordinator.LifecycleListener.NOOP);
        coordinator.start(context());
        while (coordinator.isActive()) {
            coordinator.tick();
        }
        return names;
    }

    private static PlannedItem item(DailySettlementPhase phase, String name) {
        return new PlannedItem(phase, name);
    }

    private static DailySettlementContext context() {
        return new DailySettlementContext(226, 3, 0, 2, 1560, false, List.of(), Set.of());
    }

    private record PlannedItem(DailySettlementPhase phase, String name) {
    }
}
