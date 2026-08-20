package com.stardew.craft.network.overnight;

import java.util.Objects;

final class ClientOvernightUiGateway implements ClientOvernightFlow.UiGateway {
    private final ClientAccess client;

    ClientOvernightUiGateway(ClientAccess client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean isLocalSleeperOrWaiting() {
        return client.isPlayerSleeping() || client.isInBedOrWaitingScreen();
    }

    @Override
    public void showWaiting(int votedCount, int requiredCount) {
        client.showWaiting(votedCount, requiredCount);
    }

    @Override
    public void showPrelude(int absoluteDay, int votedCount, int requiredCount) {
        client.showPrelude(absoluteDay, votedCount, requiredCount);
    }

    @Override
    public void showReady(int votedCount, int requiredCount) {
        client.showReady(votedCount, requiredCount);
    }

    @Override
    public void startSettlement(
            int absoluteDay, OvernightSettlementPayload payload) {
        client.startSettlement(absoluteDay, payload);
    }

    @Override
    public void acknowledgeSettlement(int absoluteDay) {
        client.acknowledgeSettlement(absoluteDay);
    }

    @Override
    public void startLegacy(OvernightSettlementPayload payload) {
        client.startLegacy(payload);
    }

    @Override
    public void requestCancelWaiting() {
        client.requestCancelWaiting();
    }

    interface ClientAccess {
        boolean isPlayerSleeping();

        boolean isInBedOrWaitingScreen();

        void showWaiting(int votedCount, int requiredCount);

        void showPrelude(int absoluteDay, int votedCount, int requiredCount);

        void showReady(int votedCount, int requiredCount);

        void startSettlement(int absoluteDay, OvernightSettlementPayload payload);

        void acknowledgeSettlement(int absoluteDay);

        void startLegacy(OvernightSettlementPayload payload);

        void requestCancelWaiting();
    }
}
