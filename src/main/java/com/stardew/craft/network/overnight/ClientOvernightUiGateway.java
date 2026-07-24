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
    public void acknowledgeAndStart(
            int absoluteDay, OvernightSettlementPayload payload) {
        client.acknowledgeAndStart(absoluteDay, payload);
    }

    @Override
    public void startLegacy(OvernightSettlementPayload payload) {
        client.startLegacy(payload);
    }

    interface ClientAccess {
        boolean isPlayerSleeping();

        boolean isInBedOrWaitingScreen();

        void showWaiting(int votedCount, int requiredCount);

        void acknowledgeAndStart(int absoluteDay, OvernightSettlementPayload payload);

        void startLegacy(OvernightSettlementPayload payload);
    }
}
