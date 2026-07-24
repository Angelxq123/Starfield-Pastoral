package com.stardew.craft.book;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooksellerScheduleRecoveryTest {

    @Test
    void reconnectBetweenCommitAndPublicationDefersNoticeUntilTargetDay() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        int oldBackingDay = 225;
        int targetAbsoluteDay = 226;
        AtomicInteger notices = new AtomicInteger();

        // Commit queues the target-day notice while the player is offline.
        data.queueBooksellerNotice(targetAbsoluteDay);

        // The player reconnects before backing-date publication: no early notice.
        assertFalse(BooksellerSchedule.deliverPendingNotice(
                data, oldBackingDay, notices::incrementAndGet));
        assertEquals(targetAbsoluteDay, data.getPendingBooksellerNoticeDay());

        // Publication delivers to the now-online frozen player exactly once.
        assertTrue(BooksellerSchedule.deliverPendingNotice(
                data, targetAbsoluteDay, notices::incrementAndGet));
        assertFalse(BooksellerSchedule.deliverPendingNotice(
                data, targetAbsoluteDay, notices::incrementAndGet));

        assertEquals(1, notices.get());
        assertEquals(Integer.MIN_VALUE, data.getPendingBooksellerNoticeDay());
    }

    @Test
    void targetDayNoticeIsClaimedBeforeTheExternalSend() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        int targetAbsoluteDay = 226;
        AtomicInteger attempts = new AtomicInteger();
        data.queueBooksellerNotice(targetAbsoluteDay);

        assertThrows(IllegalStateException.class, () ->
                BooksellerSchedule.deliverPendingNotice(data, targetAbsoluteDay, () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("injected send failure");
                }));
        assertFalse(BooksellerSchedule.deliverPendingNotice(
                data, targetAbsoluteDay, attempts::incrementAndGet));

        assertEquals(1, attempts.get());
        assertEquals(Integer.MIN_VALUE, data.getPendingBooksellerNoticeDay());
    }

    @Test
    void staleNoticeIsClearedWithoutBeingDisplayed() {
        PlayerStardewData data = new PlayerStardewData(UUID.randomUUID());
        AtomicInteger notices = new AtomicInteger();
        data.queueBooksellerNotice(225);

        assertFalse(BooksellerSchedule.deliverPendingNotice(
                data, 226, notices::incrementAndGet));

        assertEquals(0, notices.get());
        assertEquals(Integer.MIN_VALUE, data.getPendingBooksellerNoticeDay());
    }
}
