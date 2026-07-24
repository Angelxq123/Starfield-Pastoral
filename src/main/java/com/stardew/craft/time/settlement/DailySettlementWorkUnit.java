package com.stardew.craft.time.settlement;

public interface DailySettlementWorkUnit extends AutoCloseable {
    String name();

    String currentItemIdentity();

    boolean isComplete();

    void runNext() throws Exception;

    void skipFailedItem();

    default String subsystemName() {
        return name();
    }

    default String closeFailureSubsystemName() {
        return subsystemName();
    }

    default boolean isAtomic() {
        return false;
    }

    default int maxRetries() {
        return 0;
    }

    @Override
    default void close() {
    }
}
