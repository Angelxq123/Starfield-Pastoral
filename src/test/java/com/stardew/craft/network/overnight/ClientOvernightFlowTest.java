package com.stardew.craft.network.overnight;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientOvernightFlowTest {

    @Test
    void productionGatewayOpensVoteOverlayForSleepingPlayerAfterScreenCloses() {
        RecordingClientAccess access = new RecordingClientAccess();
        ClientOvernightFlow flow = new ClientOvernightFlow(
                new ClientOvernightUiGateway(access));

        flow.receiveVoteProgress(1, 2);
        assertEquals(0, access.waitingOpens);

        access.playerSleeping = true;
        flow.receiveVoteProgress(1, 2);
        assertEquals(1, access.waitingOpens);

        access.playerSleeping = false;
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        assertEquals(1, access.waitingOpens);
        assertEquals(1, access.preludeOpens);
    }

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
    void barrierAlwaysRestoresNightPreludeIncludingAfterReconnect() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        assertTrue(flow.isLocked());
        assertEquals(1, gateway.preludeOpens);

        flow.resetConnectionState();
        gateway.localSleeperOrWaiting = false;
        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));

        assertTrue(flow.isLocked());
        assertEquals(2, gateway.preludeOpens);
    }

    @Test
    void readyPayloadAutomaticallyAcknowledgesAndStartsTheCompleteSettlementChain() {
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
        assertTrue(flow.isLocked());
        assertEquals(1, gateway.readyShows);
        assertEquals(List.of(), gateway.acknowledgedDays);
        assertEquals(List.of(
                        ClientOvernightHandler.SettlementStage.LEVEL_UP,
                        ClientOvernightHandler.SettlementStage.LEVEL_UP,
                        ClientOvernightHandler.SettlementStage.SHIPPING),
                gateway.startedStages);

        assertTrue(flow.handleDismissInput());
        flow.receiveWorldReady(new OvernightWorldReadyPayload(226));
        flow.markSettlementSequenceFinished();
        assertTrue(flow.finishSettlementSequence());
        assertEquals(List.of(226), gateway.acknowledgedDays);
    }

    @Test
    void datedSettlementRecoversReadyStateWhenTheBarrierPacketWasLost() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);

        flow.receiveSettlement(readyPayload(226));

        assertTrue(flow.isLocked());
        assertTrue(flow.isReady());
        assertEquals(226, flow.currentAbsoluteDay());
        assertEquals(1, gateway.readyShows);
        assertEquals(List.of(), gateway.acknowledgedDays);
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

        flow.receiveWorldReady(new OvernightWorldReadyPayload(226));
        flow.markSettlementSequenceFinished();
        assertTrue(flow.finishSettlementSequence());
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

    @Test
    void barrierOnlyReadyEndsWaitingWithoutOpeningSettlementScreens() {
        OvernightSettlementPayload barrierOnly = OvernightSettlementPayload.barrierOnly(226);

        assertFalse(barrierOnly.personalSettlement());
        assertTrue(ClientOvernightHandler.settlementStages(barrierOnly).isEmpty());
    }

    @Test
    void emptyShipmentStillUsesTheNightSettlementAnimation() {
        assertEquals(
                List.of(ClientOvernightHandler.SettlementStage.SHIPPING),
                ClientOvernightHandler.settlementStages(readyPayload(226)));
    }

    @Test
    void voteCanBeCancelledUntilTheSettlementBarrierLocks() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);

        assertTrue(flow.canCancelWaiting());
        assertTrue(flow.requestCancelWaiting());
        assertEquals(1, gateway.cancelRequests);

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));

        assertFalse(flow.canCancelWaiting());
        assertFalse(flow.requestCancelWaiting());
        assertEquals(1, gateway.cancelRequests);
    }

    @Test
    void playerPayloadWaitsForWorldReadyBeforeStartingOrAcknowledging() {
        RecordingGateway gateway = new RecordingGateway();
        ClientOvernightFlow flow = new ClientOvernightFlow(gateway);
        OvernightSettlementPayload payload = readyPayload(226);

        flow.receiveBarrierState(new OvernightBarrierPayload(226, true));
        flow.receiveSettlement(payload);

        assertTrue(flow.isLocked());
        assertFalse(flow.isWorldReady());
        assertEquals(1, gateway.startedSettlements);
        assertTrue(flow.handleDismissInput());
        assertEquals(0, gateway.acknowledgedDays.size());

        flow.receiveWorldReady(new OvernightWorldReadyPayload(226));

        assertTrue(flow.isWorldReady());
        assertEquals(1, gateway.startedSettlements);
        assertEquals(0, gateway.acknowledgedDays.size());
        flow.markSettlementSequenceFinished();
        assertTrue(flow.finishSettlementSequence());
        assertEquals(List.of(226), gateway.acknowledgedDays);
        assertFalse(flow.isLocked());
    }

    private static OvernightSettlementPayload readyPayload(int absoluteDay) {
        return new OvernightSettlementPayload(
                absoluteDay, List.of(), List.of(), -1, 0, List.of());
    }

    private static final class RecordingGateway implements ClientOvernightFlow.UiGateway {
        private boolean localSleeperOrWaiting;
        private int waitingOpens;
        private int preludeOpens;
        private int votedCount;
        private int requiredCount;
        private int readyShows;
        private int cancelRequests;
        private int startedSettlements;
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
        public void showPrelude(int absoluteDay, int votedCount, int requiredCount) {
            preludeOpens++;
            this.votedCount = votedCount;
            this.requiredCount = requiredCount;
        }

        public void showReady(int votedCount, int requiredCount) {
            readyShows++;
            this.votedCount = votedCount;
            this.requiredCount = requiredCount;
        }

        @Override
        public void startSettlement(
                int absoluteDay, OvernightSettlementPayload payload) {
            startedSettlements++;
            startedStages = ClientOvernightHandler.settlementStages(payload);
        }

        @Override
        public void acknowledgeSettlement(int absoluteDay) {
            acknowledgedDays.add(absoluteDay);
        }

        @Override
        public void startLegacy(OvernightSettlementPayload payload) {
            startedStages = ClientOvernightHandler.settlementStages(payload);
        }

        @Override
        public void requestCancelWaiting() {
            cancelRequests++;
        }
    }

    private static final class RecordingClientAccess
            implements ClientOvernightUiGateway.ClientAccess {
        private boolean playerSleeping;
        private int waitingOpens;
        private int preludeOpens;

        @Override
        public boolean isPlayerSleeping() {
            return playerSleeping;
        }

        @Override
        public boolean isInBedOrWaitingScreen() {
            return false;
        }

        @Override
        public void showWaiting(int votedCount, int requiredCount) {
            waitingOpens++;
        }

        @Override
        public void showPrelude(int absoluteDay, int votedCount, int requiredCount) {
            preludeOpens++;
        }

        public void showReady(int votedCount, int requiredCount) {
        }

        @Override
        public void startSettlement(
                int absoluteDay, OvernightSettlementPayload payload) {
        }

        @Override
        public void acknowledgeSettlement(int absoluteDay) {
        }

        @Override
        public void startLegacy(OvernightSettlementPayload payload) {
        }

        @Override
        public void requestCancelWaiting() {
        }
    }
}
