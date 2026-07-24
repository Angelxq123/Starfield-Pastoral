package com.stardew.craft.network.overnight;

import java.util.Objects;

final class ClientOvernightFlow {
    private final UiGateway ui;
    private int currentAbsoluteDay = -1;
    private int lastAcknowledgedAbsoluteDay = -1;
    private int votedCount;
    private int requiredCount;
    private boolean locked;
    private OvernightSettlementPayload pendingReadyPayload;

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
        if (payload.absoluteDay() <= lastAcknowledgedAbsoluteDay) {
            return;
        }
        if (payload.absoluteDay() <= 0
                || (locked && payload.absoluteDay() < currentAbsoluteDay)) {
            return;
        }
        if (!locked || payload.absoluteDay() > currentAbsoluteDay) {
            currentAbsoluteDay = payload.absoluteDay();
            locked = payload.locked();
            pendingReadyPayload = null;
            if (!locked) {
                currentAbsoluteDay = -1;
            } else {
                ui.showWaiting(votedCount, requiredCount);
            }
            return;
        }
        if (currentAbsoluteDay != payload.absoluteDay()) {
            return;
        }
        locked = payload.locked();
        if (!locked) {
            currentAbsoluteDay = -1;
            pendingReadyPayload = null;
        } else {
            ui.showWaiting(votedCount, requiredCount);
        }
    }

    void receiveSettlement(OvernightSettlementPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.absoluteDay() < 0) {
            if (!locked) {
                ui.startLegacy(payload);
            }
            return;
        }
        if (!locked || payload.absoluteDay() != currentAbsoluteDay) {
            return;
        }
        if (pendingReadyPayload == null) {
            pendingReadyPayload = payload;
            ui.showWaiting(votedCount, requiredCount);
        }
    }

    boolean handleDismissInput() {
        if (!isReady()) {
            return true;
        }
        return startReadySequence(currentAbsoluteDay);
    }

    boolean startReadySequence(int absoluteDay) {
        if (!locked || absoluteDay != currentAbsoluteDay || !isReady()) {
            return false;
        }
        OvernightSettlementPayload payload = pendingReadyPayload;
        lastAcknowledgedAbsoluteDay = absoluteDay;
        locked = false;
        currentAbsoluteDay = -1;
        pendingReadyPayload = null;
        ui.acknowledgeAndStart(absoluteDay, payload);
        return true;
    }

    boolean isLocked() {
        return locked;
    }

    boolean isReady() {
        return locked
                && pendingReadyPayload != null
                && pendingReadyPayload.absoluteDay() == currentAbsoluteDay;
    }

    int currentAbsoluteDay() {
        return currentAbsoluteDay;
    }

    void resetConnectionState() {
        currentAbsoluteDay = -1;
        locked = false;
        pendingReadyPayload = null;
        lastAcknowledgedAbsoluteDay = -1;
        votedCount = 0;
        requiredCount = 0;
    }

    interface UiGateway {
        boolean isLocalSleeperOrWaiting();

        void showWaiting(int votedCount, int requiredCount);

        void acknowledgeAndStart(int absoluteDay, OvernightSettlementPayload payload);

        void startLegacy(OvernightSettlementPayload payload);
    }
}
