package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetedWorkRunnerTest {

    @Test
    void fourMillisecondBudgetProcessesOnlyTwoItemsWithTwoMillisecondClockSteps() throws Exception {
        List<String> processed = new ArrayList<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "crops", List.of("a", "b", "c"), value -> value, processed::add, () -> {});

        BudgetedWorkRunner.TickResult result = new BudgetedWorkRunner(new FakeClock(2_000_000L))
                .run(unit, 4_000_000L, 10);

        assertEquals(List.of("a", "b"), processed);
        assertEquals(2, result.processedItems());
        assertEquals(4_000_000L, result.elapsedNanos());
        assertEquals(0L, result.overshootNanos());
        assertFalse(result.complete());
    }

    @Test
    void startsOneItemEvenWhenTheBudgetIsAlreadyExceeded() throws Exception {
        List<String> processed = new ArrayList<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "crops", List.of("a", "b"), value -> value, processed::add, () -> {});

        BudgetedWorkRunner.TickResult result = new BudgetedWorkRunner(new FakeClock(2_000_000L))
                .run(unit, 1L, 10);

        assertEquals(List.of("a"), processed);
        assertEquals(1, result.processedItems());
        assertEquals(2_000_000L, result.elapsedNanos());
        assertEquals(1_999_999L, result.overshootNanos());
        assertFalse(result.complete());
    }

    @Test
    void itemLimitStopsWorkBeforeTheTimeBudget() throws Exception {
        List<String> processed = new ArrayList<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "crops", List.of("a", "b", "c"), value -> value, processed::add, () -> {});

        BudgetedWorkRunner.TickResult result = new BudgetedWorkRunner(new FakeClock(1L))
                .run(unit, 1_000_000L, 2);

        assertEquals(List.of("a", "b"), processed);
        assertEquals(2, result.processedItems());
        assertFalse(result.complete());
    }

    @Test
    void cursorResumesExactlyWithoutRepeatingOrOmittingItems() throws Exception {
        List<Integer> processed = new ArrayList<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "crops", List.of(1, 2, 3, 4, 5), Object::toString, processed::add, () -> {});
        BudgetedWorkRunner runner = new BudgetedWorkRunner(new FakeClock(1L));

        assertFalse(runner.run(unit, 1_000_000L, 2).complete());
        assertFalse(runner.run(unit, 1_000_000L, 2).complete());
        BudgetedWorkRunner.TickResult finalTick = runner.run(unit, 1_000_000L, 2);

        assertEquals(List.of(1, 2, 3, 4, 5), processed);
        assertEquals(1, finalTick.processedItems());
        assertTrue(finalTick.complete());
        assertTrue(unit.isComplete());
    }

    @Test
    void emptyCursorStartsCompleteAndRunsNoItems() throws Exception {
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "empty", List.<String>of(), value -> value, value -> {}, () -> {});

        BudgetedWorkRunner.TickResult result = new BudgetedWorkRunner(new FakeClock(1L))
                .run(unit, 10L, 1);

        assertEquals(0, result.processedItems());
        assertEquals(0L, result.elapsedNanos());
        assertTrue(result.complete());
    }

    @Test
    void rejectsInvalidRunnerArguments() {
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.atomic("one", () -> {}, () -> {});
        BudgetedWorkRunner runner = new BudgetedWorkRunner(new FakeClock(1L));

        assertThrows(NullPointerException.class, () -> new BudgetedWorkRunner(null));
        assertThrows(NullPointerException.class, () -> runner.run(null, 1L, 1));
        assertThrows(IllegalArgumentException.class, () -> runner.run(unit, 0L, 1));
        assertThrows(IllegalArgumentException.class, () -> runner.run(unit, -1L, 1));
        assertThrows(IllegalArgumentException.class, () -> runner.run(unit, 1L, 0));
        assertThrows(IllegalArgumentException.class, () -> runner.run(unit, 1L, -1));
    }

    @Test
    void elapsedTimeNeverBecomesNegativeWhenClockMovesBackwards() throws Exception {
        BudgetedWorkRunner.NanoClock clock = new BudgetedWorkRunner.NanoClock() {
            private int reads;

            @Override
            public long nanoTime() {
                return reads++ == 0 ? 10L : 5L;
            }
        };
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.atomic("one", () -> {}, () -> {});

        BudgetedWorkRunner.TickResult result = new BudgetedWorkRunner(clock).run(unit, 1L, 1);

        assertEquals(0L, result.elapsedNanos());
        assertEquals(0L, result.overshootNanos());
        assertTrue(result.complete());
    }

    @Test
    void cursorSnapshotsEntriesAndIdentitiesAtCreation() throws Exception {
        MutableEntry original = new MutableEntry("before");
        List<MutableEntry> entries = new ArrayList<>();
        entries.add(original);
        List<String> processed = new ArrayList<>();

        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "snapshot", entries, MutableEntry::identity,
                entry -> processed.add(entry.identity()), () -> {});
        original.identity = "after";
        entries.add(new MutableEntry("late"));

        assertEquals("before", unit.currentItemIdentity());
        DailySettlementWorkUnits.drain(unit);
        assertEquals(List.of("after"), processed);
    }

    @Test
    void failedCursorItemRemainsCurrentUntilItIsSkipped() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> processed = new ArrayList<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "cursor", List.of("bad", "good"), value -> value, value -> {
                    attempts.incrementAndGet();
                    if (value.equals("bad")) {
                        throw new Exception("failure");
                    }
                    processed.add(value);
                }, () -> {});

        Exception failure = assertThrows(Exception.class, unit::runNext);

        assertEquals("failure", failure.getMessage());
        assertEquals("bad", unit.currentItemIdentity());
        assertFalse(unit.isComplete());
        unit.skipFailedItem();
        assertEquals("good", unit.currentItemIdentity());
        unit.runNext();
        assertEquals(2, attempts.get());
        assertEquals(List.of("good"), processed);
        assertTrue(unit.isComplete());
    }

    @Test
    void runnerPropagatesFailuresWithoutAdvancingOrCountingTheItem() {
        Exception expected = new Exception("stop");
        AtomicInteger attempts = new AtomicInteger();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "cursor", List.of("a"), value -> value, value -> {
                    attempts.incrementAndGet();
                    throw expected;
                }, () -> {});

        Exception actual = assertThrows(Exception.class,
                () -> new BudgetedWorkRunner(new FakeClock(1L)).run(unit, 10L, 1));

        assertSame(expected, actual);
        assertEquals(1, attempts.get());
        assertEquals("a", unit.currentItemIdentity());
        assertFalse(unit.isComplete());
    }

    @Test
    void atomicCompletesAfterSuccessOrSkip() throws Exception {
        DailySettlementWorkUnit successful = DailySettlementWorkUnits.atomic(
                "success", () -> {}, () -> {});
        DailySettlementWorkUnit skipped = DailySettlementWorkUnits.atomic(
                "skipped", () -> { throw new Exception("failure"); }, () -> {});

        assertEquals("success", successful.name());
        assertEquals("success", successful.currentItemIdentity());
        assertEquals(2, successful.maxRetries());
        successful.runNext();
        assertTrue(successful.isComplete());

        assertThrows(Exception.class, skipped::runNext);
        assertFalse(skipped.isComplete());
        skipped.skipFailedItem();
        assertTrue(skipped.isComplete());
    }

    @Test
    void closeCallbacksRunAtMostOnce() {
        AtomicInteger cursorCloses = new AtomicInteger();
        AtomicInteger atomicCloses = new AtomicInteger();
        DailySettlementWorkUnit cursor = DailySettlementWorkUnits.cursor(
                "cursor", List.of("a"), value -> value, value -> {}, cursorCloses::incrementAndGet);
        DailySettlementWorkUnit atomic = DailySettlementWorkUnits.atomic(
                "atomic", () -> {}, atomicCloses::incrementAndGet);

        cursor.close();
        cursor.close();
        atomic.close();
        atomic.close();

        assertEquals(1, cursorCloses.get());
        assertEquals(1, atomicCloses.get());
    }

    @Test
    void drainSkipsCursorItemAfterItsFirstFailure() throws Exception {
        AtomicInteger failedAttempts = new AtomicInteger();
        List<String> processed = new ArrayList<>();
        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "cursor", List.of("bad", "good"), value -> value, value -> {
                    if (value.equals("bad")) {
                        failedAttempts.incrementAndGet();
                        throw new Exception("failure");
                    }
                    processed.add(value);
                }, closes::incrementAndGet);

        DailySettlementWorkUnits.drain(unit);

        assertEquals(0, unit.maxRetries());
        assertEquals(1, failedAttempts.get());
        assertEquals(List.of("good"), processed);
        assertTrue(unit.isComplete());
        assertEquals(1, closes.get());
        unit.close();
        assertEquals(1, closes.get());
    }

    @Test
    void drainRetriesAtomicTwiceAndSkipsAfterThirdFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.atomic("atomic", () -> {
            attempts.incrementAndGet();
            throw new Exception("failure");
        }, closes::incrementAndGet);

        DailySettlementWorkUnits.drain(unit);

        assertEquals(3, attempts.get());
        assertTrue(unit.isComplete());
        assertEquals(1, closes.get());
    }

    @Test
    void drainResetsFailuresAfterEachSuccessfulItem() {
        PerItemRetryWorkUnit unit = new PerItemRetryWorkUnit();

        DailySettlementWorkUnits.drain(unit);

        assertEquals(List.of("A", "B"), unit.processedItems);
        assertTrue(unit.skippedItems.isEmpty());
        assertEquals(List.of(3, 3), List.of(unit.attempts[0], unit.attempts[1]));
        assertTrue(unit.isComplete());
    }

    @Test
    void factoriesRejectNullArgumentsAndNullEntries() {
        List<String> entries = List.of("a");

        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor(null, entries, value -> value, value -> {}, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.<String>cursor(
                        "cursor", null, value -> value, value -> {}, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor("cursor", entries, null, value -> {}, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor("cursor", entries, value -> value, null, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor("cursor", entries, value -> value, value -> {}, null));
        List<String> entriesWithNull = new ArrayList<>();
        entriesWithNull.add(null);
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor(
                        "cursor", entriesWithNull, value -> value, value -> {}, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.cursor(
                        "cursor", entries, value -> null, value -> {}, () -> {}));

        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.atomic(null, () -> {}, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.atomic("atomic", null, () -> {}));
        assertThrows(NullPointerException.class,
                () -> DailySettlementWorkUnits.atomic("atomic", () -> {}, null));
        assertThrows(NullPointerException.class, () -> DailySettlementWorkUnits.drain(null));
    }

    @Test
    void interfaceDefaultsToNoRetriesAndNoOpClose() {
        AtomicReference<String> current = new AtomicReference<>("item");
        DailySettlementWorkUnit unit = new DailySettlementWorkUnit() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public String currentItemIdentity() {
                return current.get();
            }

            @Override
            public boolean isComplete() {
                return false;
            }

            @Override
            public void runNext() {
            }

            @Override
            public void skipFailedItem() {
                current.set(null);
            }
        };

        assertEquals(0, unit.maxRetries());
        unit.close();
        assertEquals("item", current.get());
    }

    private static final class FakeClock implements BudgetedWorkRunner.NanoClock {
        private final long stepNanos;
        private long now;

        private FakeClock(long stepNanos) {
            this.stepNanos = stepNanos;
        }

        @Override
        public long nanoTime() {
            now += stepNanos;
            return now;
        }
    }

    private static final class MutableEntry {
        private String identity;

        private MutableEntry(String identity) {
            this.identity = identity;
        }

        private String identity() {
            return identity;
        }
    }

    private static final class PerItemRetryWorkUnit implements DailySettlementWorkUnit {
        private final List<String> entries = List.of("A", "B");
        private final int[] attempts = new int[entries.size()];
        private final List<String> processedItems = new ArrayList<>();
        private final List<String> skippedItems = new ArrayList<>();
        private int cursor;

        @Override
        public String name() {
            return "per-item-retries";
        }

        @Override
        public String currentItemIdentity() {
            return entries.get(cursor);
        }

        @Override
        public boolean isComplete() {
            return cursor >= entries.size();
        }

        @Override
        public void runNext() throws Exception {
            if (++attempts[cursor] <= 2) {
                throw new Exception("retry " + entries.get(cursor));
            }
            processedItems.add(entries.get(cursor));
            cursor++;
        }

        @Override
        public void skipFailedItem() {
            skippedItems.add(entries.get(cursor));
            cursor++;
        }

        @Override
        public int maxRetries() {
            return 2;
        }
    }
}
