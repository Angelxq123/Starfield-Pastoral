package com.stardew.craft.interior.door;

public interface DoorMovementQueue {
    boolean stardewcraft$awaitingDoorCorrection();
    void stardewcraft$flushDoorMoves();
    void stardewcraft$discardDoorMoves();
}
