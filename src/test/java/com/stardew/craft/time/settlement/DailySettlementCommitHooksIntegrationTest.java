package com.stardew.craft.time.settlement;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementCommitHooksIntegrationTest {

    @Test
    void frozenCommitHooksRecoverAcrossPublicationWithoutAdmittingLateLogin() {
        UUID frozenMain = UUID.randomUUID();
        UUID frozenValley = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        Map<UUID, PlayerStardewData> data = new HashMap<>();
        data.put(frozenMain, playerWithTomorrowFlag(frozenMain));
        data.put(frozenValley, playerWithTomorrowFlag(frozenValley));
        data.put(lateLogin, playerWithTomorrowFlag(lateLogin));
        Set<UUID> online = new HashSet<>();
        List<String> mailEffects = new ArrayList<>();
        List<String> removedItems = new ArrayList<>();
        AtomicInteger booksellerSends = new AtomicInteger();
        AtomicInteger scheduledDateMail = new AtomicInteger();
        AtomicInteger backingDay = new AtomicInteger(225);
        int targetDay = 226;
        DailySettlementContext context = new DailySettlementContext(
                targetDay, 3, 0, 2, 1560, false,
                List.of(), Set.of(), List.of(),
                List.of(frozenMain, frozenValley), List.of(frozenValley));

        DailySettlementCommitHooks hooks = new DailySettlementCommitHooks(
                new DailySettlementCommitHooks.Operations() {
                    @Override
                    public void specialOrders(List<UUID> playerIds) {
                        for (UUID playerId : playerIds) {
                            data.get(playerId).queuePendingSpecialOrderItemCleanup(
                                    "stardewcraft:ectoplasm");
                        }
                    }

                    @Override
                    public void bookseller(
                            List<UUID> playerIds, DailySettlementContext target) {
                        for (UUID playerId : playerIds) {
                            data.get(playerId).queueBooksellerNotice(target.absoluteDay());
                        }
                        online.add(frozenValley);
                        loginRecovery(
                                data.get(frozenValley), backingDay, booksellerSends,
                                mailEffects, removedItems, scheduledDateMail).recover();
                    }

                    @Override
                    public void mail(List<UUID> playerIds, int absoluteDay) {
                        for (UUID playerId : playerIds) {
                            PlayerStardewData current = data.get(playerId);
                            current.deliverTomorrowMail(absoluteDay);
                            current.queuePendingDateTriggeredMailDay(absoluteDay);
                            if (online.contains(playerId)) {
                                recoverMail(current, mailEffects);
                                recoverDateMail(current, scheduledDateMail);
                            }
                        }
                    }

                    @Override
                    public void readyBookseller(List<UUID> playerIds, int absoluteDay) {
                        assertEquals(targetDay, backingDay.get());
                        for (UUID playerId : playerIds) {
                            if (online.contains(playerId)) {
                                deliverBookseller(data.get(playerId), absoluteDay,
                                        booksellerSends);
                            }
                        }
                    }
                });
        DailySettlementPlanFactory plan = new DailySettlementPlanFactory((name, target) -> {
            if (hooks.supports(name)) {
                return hooks.create(name, target);
            }
            if ("date_publication".equals(name)) {
                return DailySettlementWorkUnits.atomic(name,
                        () -> backingDay.set(target.absoluteDay()), () -> {});
            }
            return DailySettlementWorkUnits.atomic(name, () -> {}, () -> {});
        });
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 64,
                plan, new DailySettlementCoordinator.LifecycleListener() {
                    @Override
                    public void phaseChanged(
                            DailySettlementContext target, DailySettlementPhase phase) {
                    }

                    @Override
                    public void itemFailure(
                            DailySettlementContext target, String unitName,
                            String itemIdentity, int attempt, boolean permanent) {
                    }

                    @Override
                    public void ready(DailySettlementContext target) {
                        hooks.ready(target);
                    }
                });

        coordinator.start(context);
        coordinator.drain();

        online.add(frozenMain);
        loginRecovery(
                data.get(frozenMain), backingDay, booksellerSends,
                mailEffects, removedItems, scheduledDateMail).recover();

        assertEquals(targetDay, backingDay.get());
        assertEquals(1, booksellerSends.get());
        assertEquals(List.of("ccPantry", "ccPantry"), mailEffects);
        assertEquals(List.of("stardewcraft:ectoplasm", "stardewcraft:ectoplasm"),
                removedItems);
        assertEquals(2, scheduledDateMail.get());
        assertTrue(data.get(frozenMain).getPendingMailDeliveryEffects().isEmpty());
        assertTrue(data.get(frozenValley).getPendingMailDeliveryEffects().isEmpty());
        assertTrue(data.get(frozenMain).getPendingSpecialOrderItemCleanups().isEmpty());
        assertTrue(data.get(frozenValley).getPendingSpecialOrderItemCleanups().isEmpty());
        assertFalse(data.get(lateLogin).hasMailFlag("ccPantry"));
        assertTrue(data.get(lateLogin).getPendingDateTriggeredMailDays().isEmpty());
        assertTrue(data.get(lateLogin).getPendingSpecialOrderItemCleanups().isEmpty());
        assertEquals(Integer.MIN_VALUE,
                data.get(lateLogin).getPendingBooksellerNoticeDay());
    }

    private static PlayerStardewData playerWithTomorrowFlag(UUID playerId) {
        PlayerStardewData data = new PlayerStardewData(playerId);
        data.addMailFlagForTomorrow("ccPantry", 225);
        return data;
    }

    private static void recoverMail(PlayerStardewData data, List<String> effects) {
        for (String mailId : data.getPendingMailDeliveryEffects()) {
            if (data.claimPendingMailDeliveryEffect(mailId)) {
                effects.add(mailId);
            }
        }
    }

    private static void recoverSpecialOrder(
            PlayerStardewData data, List<String> removedItems) {
        for (String itemId : data.getPendingSpecialOrderItemCleanups()) {
            removedItems.add(itemId);
            data.completePendingSpecialOrderItemCleanup(itemId);
        }
    }

    private static void recoverDateMail(
            PlayerStardewData data, AtomicInteger scheduledDateMail) {
        for (int absoluteDay : data.getPendingDateTriggeredMailDays()) {
            scheduledDateMail.incrementAndGet();
            data.completePendingDateTriggeredMailDay(absoluteDay);
        }
    }

    private static DailySettlementLoginRecovery loginRecovery(
            PlayerStardewData data,
            AtomicInteger backingDay,
            AtomicInteger booksellerSends,
            List<String> mailEffects,
            List<String> removedItems,
            AtomicInteger scheduledDateMail) {
        return new DailySettlementLoginRecovery(
                new DailySettlementLoginRecovery.Operations() {
                    @Override
                    public void specialOrderItems() {
                        recoverSpecialOrder(data, removedItems);
                    }

                    @Override
                    public void mailDelivery() {
                        recoverMail(data, mailEffects);
                    }

                    @Override
                    public void dateMail() {
                        recoverDateMail(data, scheduledDateMail);
                    }

                    @Override
                    public void bookseller() {
                        deliverBookseller(data, backingDay.get(), booksellerSends);
                    }
                });
    }

    private static void deliverBookseller(
            PlayerStardewData data, int absoluteDay, AtomicInteger sends) {
        if (data.hasPendingBooksellerNotice(absoluteDay)
                && data.acknowledgeBooksellerNotice(absoluteDay)) {
            sends.incrementAndGet();
        }
    }
}
