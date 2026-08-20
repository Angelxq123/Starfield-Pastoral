package com.stardew.craft.network.overnight;

import java.util.Objects;

final class ClientOvernightFlow {
    private final UiGateway ui;
    private int lockedAbsoluteDay = -1;
    private int lastAcknowledgedAbsoluteDay = -1;
    private int votedCount;
    private int requiredCount;
    private OvernightSettlementPayload pendingReadyPayload;
    private boolean worldReady;
    private boolean settlementStarted;
    private boolean settlementSequenceFinished;

    ClientOvernightFlow(UiGateway ui) {
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    void receiveVoteProgress(int votedCount, int requiredCount) {
        this.votedCount = votedCount;
        this.requiredCount = requiredCount;
        if (ui.isLocalSleeperOrWaiting()) {
            ui.showWaiting(votedCount, requiredCount);
        }
    }

    void receiveBarrierState(OvernightBarrierPayload payload) {
        Objects.requireNonNull(payload, "payload");
        int absoluteDay = payload.absoluteDay();
        if (absoluteDay <= lastAcknowledgedAbsoluteDay
                || absoluteDay <= 0
                || (isLocked() && absoluteDay < lockedAbsoluteDay)) {
            return;
        }
        if (!payload.locked()) {
            lockedAbsoluteDay = -1;
            pendingReadyPayload = null;
            worldReady = false;
            settlementStarted = false;
            return;
        }
        if (absoluteDay > lockedAbsoluteDay) {
            pendingReadyPayload = null;
            worldReady = false;
            settlementStarted = false;
        }
        lockedAbsoluteDay = absoluteDay;
        ui.showPrelude(absoluteDay, votedCount, requiredCount);
    }

    void receiveSettlement(OvernightSettlementPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.absoluteDay() < 0) {
            if (!isLocked()) {
                ui.startLegacy(payload);
            }
            return;
        }
        if (payload.absoluteDay() <= lastAcknowledgedAbsoluteDay
                || (isLocked() && payload.absoluteDay() < lockedAbsoluteDay)) {
            return;
        }
        if (payload.absoluteDay() > lockedAbsoluteDay) {
            lockedAbsoluteDay = payload.absoluteDay();
            pendingReadyPayload = null;
            // A dated settlement without a barrier is a legacy/reconnect packet.
            // There is no separate world-ready gate in that protocol.
            worldReady = true;
            settlementStarted = false;
        }
        if (pendingReadyPayload == null) {
            pendingReadyPayload = payload;
            ui.showReady(votedCount, requiredCount);
            startReadySequence(lockedAbsoluteDay);
        }
    }

    void receiveWorldReady(OvernightWorldReadyPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!isLocked() || payload.absoluteDay() != lockedAbsoluteDay
                || payload.absoluteDay() <= lastAcknowledgedAbsoluteDay) {
            return;
        }
        worldReady = true;
        if (settlementStarted && settlementSequenceFinished) {
            finishSettlementSequence();
        }
    }

    boolean handleDismissInput() {
        if (!isReady() || !worldReady) {
            return true;
        }
        if (settlementStarted) {
            return true;
        }
        return startReadySequence(lockedAbsoluteDay);
    }

    boolean canCancelWaiting() {
        return !isLocked();
    }

    boolean requestCancelWaiting() {
        if (!canCancelWaiting()) {
            return false;
        }
        ui.requestCancelWaiting();
        return true;
    }

    void receiveCancellationAccepted() {
        if (isLocked()) {
            return;
        }
        votedCount = 0;
        requiredCount = 0;
        pendingReadyPayload = null;
        lockedAbsoluteDay = -1;
        worldReady = false;
        settlementStarted = false;
    }

    boolean startReadySequence(int absoluteDay) {
        if (absoluteDay != lockedAbsoluteDay || !isReady()
                || settlementStarted) {
            return false;
        }
        OvernightSettlementPayload payload = pendingReadyPayload;
        settlementStarted = true;
        ui.startSettlement(absoluteDay, payload);
        return true;
    }

    boolean finishSettlementSequence() {
        if (!settlementStarted || !settlementSequenceFinished
                || !worldReady || !isReady()) {
            return false;
        }
        int absoluteDay = lockedAbsoluteDay;
        ui.acknowledgeSettlement(absoluteDay);
        lastAcknowledgedAbsoluteDay = absoluteDay;
        lockedAbsoluteDay = -1;
        pendingReadyPayload = null;
        worldReady = false;
        settlementStarted = false;
        settlementSequenceFinished = false;
        return true;
    }

    void markSettlementSequenceFinished() {
        settlementSequenceFinished = true;
    }

    boolean isLocked() {
        return lockedAbsoluteDay > 0;
    }

    boolean isReady() {
        return isLocked()
                && pendingReadyPayload != null
                && pendingReadyPayload.absoluteDay() == lockedAbsoluteDay;
    }

    boolean isWorldReady() {
        return worldReady;
    }

    int currentAbsoluteDay() {
        return lockedAbsoluteDay;
    }

    void resetConnectionState() {
        lockedAbsoluteDay = -1;
        pendingReadyPayload = null;
        lastAcknowledgedAbsoluteDay = -1;
        votedCount = 0;
        requiredCount = 0;
        worldReady = false;
        settlementStarted = false;
        settlementSequenceFinished = false;
    }

    interface UiGateway {
        boolean isLocalSleeperOrWaiting();

        void showWaiting(int votedCount, int requiredCount);

        void showPrelude(int absoluteDay, int votedCount, int requiredCount);

        void showReady(int votedCount, int requiredCount);

        void startSettlement(int absoluteDay, OvernightSettlementPayload payload);

        void acknowledgeSettlement(int absoluteDay);

        void startLegacy(OvernightSettlementPayload payload);

        void requestCancelWaiting();
    }
}
