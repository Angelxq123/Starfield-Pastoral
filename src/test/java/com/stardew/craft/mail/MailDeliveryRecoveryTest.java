package com.stardew.craft.mail;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailDeliveryRecoveryTest {

    @Test
    void onlinePureFlagFlushSynchronizesExactlyOnceThroughDeliveryPath() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        int targetAbsoluteDay = 226;
        data.addMailFlagForTomorrow("ccPantry", targetAbsoluteDay - 1);
        AtomicInteger persistentWrites = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        List<String> effects = new ArrayList<>();

        MailService.deliverTomorrowMailForPlayer(
                data,
                targetAbsoluteDay,
                new MailService.MailDeliveryCallbacks() {
                    @Override
                    public boolean online() {
                        return true;
                    }

                    @Override
                    public void markPersistent() {
                        persistentWrites.incrementAndGet();
                    }

                    @Override
                    public void dispatch(String mailId) {
                        effects.add(mailId);
                    }

                    @Override
                    public void sync(PlayerStardewData current) {
                        assertTrue(current.hasMailFlag("ccPantry"));
                        syncs.incrementAndGet();
                    }
                });

        assertTrue(data.hasMailFlag("ccPantry"));
        assertEquals(List.of("ccPantry"), effects);
        assertEquals(1, persistentWrites.get());
        assertEquals(1, syncs.get());
    }

    @Test
    void flushedDuplicateFlagStillSynchronizesTomorrowQueueRemoval() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        int targetAbsoluteDay = 226;
        data.addMailFlag("ccPantry");
        data.addMailFlagForTomorrow("ccPantry", targetAbsoluteDay - 1);
        AtomicInteger persistentWrites = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();

        MailService.deliverTomorrowMailForPlayer(
                data,
                targetAbsoluteDay,
                new MailService.MailDeliveryCallbacks() {
                    @Override
                    public boolean online() {
                        return true;
                    }

                    @Override
                    public void markPersistent() {
                        persistentWrites.incrementAndGet();
                    }

                    @Override
                    public void dispatch(String mailId) {
                    }

                    @Override
                    public void sync(PlayerStardewData current) {
                        syncs.incrementAndGet();
                    }
                });

        assertTrue(data.hasMailFlag("ccPantry"));
        assertFalse(data.hasMailFlagForTomorrow("ccPantry"));
        assertEquals(1, persistentWrites.get());
        assertEquals(1, syncs.get());
    }

    @Test
    void onlineDispatchExceptionIsClaimedAndStillSynchronizesOnce() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        int targetAbsoluteDay = 226;
        data.addMailFlagForTomorrow("JojaMember", targetAbsoluteDay - 1);
        AtomicInteger dispatches = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        MailService.MailDeliveryCallbacks failing = callbacks(
                dispatches, syncs, true);

        assertThrows(IllegalStateException.class, () ->
                MailService.deliverTomorrowMailForPlayer(
                        data, targetAbsoluteDay, failing));
        MailService.deliverTomorrowMailForPlayer(
                data, targetAbsoluteDay, callbacks(dispatches, syncs, false));

        assertTrue(data.hasMailFlag("JojaMember"));
        assertTrue(data.getPendingMailDeliveryEffects().isEmpty());
        assertEquals(1, dispatches.get());
        assertEquals(1, syncs.get());
    }

    @Test
    void offlineTomorrowMailFlushRetainsDeliveryEffectsUntilLogin() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        int targetAbsoluteDay = 226;
        data.addMailForTomorrow("spring_2_1");
        data.addMailFlagForTomorrow("ccPantry", targetAbsoluteDay - 1);

        // Commit flushes persistent queues while the frozen player is offline.
        MailService.flushTomorrowMailbox(data, targetAbsoluteDay);
        PlayerStardewData restored = PlayerStardewData.fromNBT(data.toNBT(), playerId);

        assertTrue(restored.getMailbox().contains("spring_2_1"));
        assertTrue(restored.hasMailFlag("ccPantry"));
        assertEquals(List.of("spring_2_1", "ccPantry"),
                restored.getPendingMailDeliveryEffects());

        List<String> effects = new ArrayList<>();
        MailService.resumePendingDeliveryEffects(restored, effects::add);
        MailService.resumePendingDeliveryEffects(restored, effects::add);

        assertEquals(List.of("spring_2_1", "ccPantry"), effects);
        assertEquals(List.of(), restored.getPendingMailDeliveryEffects());
    }

    private static MailService.MailDeliveryCallbacks callbacks(
            AtomicInteger dispatches, AtomicInteger syncs, boolean failDispatch) {
        return new MailService.MailDeliveryCallbacks() {
            @Override
            public boolean online() {
                return true;
            }

            @Override
            public void markPersistent() {
            }

            @Override
            public void dispatch(String mailId) {
                dispatches.incrementAndGet();
                if (failDispatch) {
                    throw new IllegalStateException("injected mail dispatch failure");
                }
            }

            @Override
            public void sync(PlayerStardewData current) {
                syncs.incrementAndGet();
            }
        };
    }
}
