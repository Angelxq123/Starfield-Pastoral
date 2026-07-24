package com.stardew.craft.network.overnight;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOvernightFlowTest {

    @Test
    void voteProgressOpensWaitingOnlyForTheLocalSleeper() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);

        flow.receiveVoteProgress(1, 2);
        assertEquals(0, gateway.waitingOpens);

        gateway.localSleeperOrWaiting = true;
        flow.receiveVoteProgress(1, 2);

        assertEquals(1, gateway.waitingOpens);
        assertEquals(1, gateway.votedCount);
        assertEquals(2, gateway.requiredCount);
    }

    @Test
    void barrierAlwaysRestoresWaitingScreenIncludingAfterReconnect() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        assertTrue(flow.isLocked());
        assertEquals(1, gateway.waitingOpens);

        flow.resetConnectionState();
        gateway.localSleeperOrWaiting = false;
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));

        assertTrue(flow.isLocked());
        assertEquals(2, gateway.waitingOpens);
    }

    @Test
    void readyClickAcknowledgesOnceAndStartsTheCompleteSettlementChain() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);
        OvernightSettlementPayload ready = new OvernightSettlementPayload(
                226,
                List.of(),
                List.of(
                        new OvernightSettlementPayload.LevelUpData(0, 5),
                        new OvernightSettlementPayload.LevelUpData(1, 10)),
                0,
                125,
                List.of());

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        assertTrue(flow.handleDismissInput());
        assertEquals(0, gateway.acknowledgedDays.size());

        flow.receiveSettlement(ready);
        assertTrue(flow.isReady());
        assertTrue(flow.handleDismissInput());
        assertFalse(flow.isLocked());
        assertEquals(List.of(226), gateway.acknowledgedDays);
        assertEquals(List.of(
                        ClientOvernightHandler.SettlementStage.PASS_OUT_OVERLAY,
                        ClientOvernightHandler.SettlementStage.PASS_OUT_SUMMARY,
                        ClientOvernightHandler.SettlementStage.LEVEL_UP,
                        ClientOvernightHandler.SettlementStage.LEVEL_UP,
                        ClientOvernightHandler.SettlementStage.SHIPPING),
                gateway.startedStages);

        assertTrue(flow.handleDismissInput());
        assertEquals(List.of(226), gateway.acknowledgedDays);
    }

    @Test
    void acknowledgedDayRejectsLatePacketsButReconnectAcceptsANewerDay() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);
        OvernightSettlementPayload day226 = readyPayload(226);

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        flow.receiveSettlement(day226);
        flow.handleDismissInput();
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        flow.receiveSettlement(day226);

        assertFalse(flow.isLocked());
        assertEquals(List.of(226), gateway.acknowledgedDays);

        flow.receiveBarrierState(new OvernightBarrierPayload(227, true));
        assertTrue(flow.isLocked());
        assertEquals(227, flow.currentAbsoluteDay());
    }

    @Test
    void connectionResetClearsReadyAndAllowsRecoveredSameDayBarrier() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        flow.receiveSettlement(readyPayload(226));

        flow.resetConnectionState();

        assertFalse(flow.isLocked());
        assertFalse(flow.isReady());
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        assertTrue(flow.isLocked());
    }

    private static OvernightSettlementPayload readyPayload(int absoluteDay) {
        return new OvernightSettlementPayload(
                absoluteDay, List.of(), List.of(), -1, 0, List.of());
    }

    private static final class RecordingGateway implements ClientOvernightFlow.UiGateway {
        private boolean localSleeperOrWaiting;
        private int waitingOpens;
        private int votedCount;
        private int requiredCount;
        private final List<Integer> acknowledgedDays = new ArrayList<>();
        private List<ClientOvernightHandler.SettlementStage> startedStages = List.of();

        @Override
        public boolean isLocalSleeperOrWaiting() {
            return localSleeperOrWaiting;
        }

        @Override
        public void showWaiting(int votedCount, int requiredCount) {
            waitingOpens++;
            this.votedCount = votedCount;
            this.requiredCount = requiredCount;
        }

        @Override
        public void acknowledgeAndStart(
                int absoluteDay, OvernightSettlementPayload payload) {
            acknowledgedDays.add(absoluteDay);
            startedStages = ClientOvernightHandler.settlementStages(payload);
        }

        @Override
        public void startLegacy(OvernightSettlementPayload payload) {
            startedStages = ClientOvernightHandler.settlementStages(payload);
        }
    }
}
