package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class DailySettlementServicesRegistryTest {

    @Test
    void stopFailureIsPropagatedAfterTheOldRegistryEntryIsRemoved() {
        WeakKeyRegistry<Object, TestService> registry = new WeakKeyRegistry<>();
        Object server = new Object();
        TestService oldService = registry.getOrCreate(server, ignored -> new TestService());
        IllegalStateException failure = new IllegalStateException("injected stop failure");

        IllegalStateException actual = assertThrows(IllegalStateException.class, () ->
                DailySettlementServices.removeRegistered(
                        registry, server, ignored -> {
                            throw failure;
                        }));

        assertSame(failure, actual);
        assertNull(registry.get(server));
        TestService replacement = registry.getOrCreate(server, ignored -> new TestService());
        assertNotSame(oldService, replacement);
    }

    private static final class TestService {
    }
}
