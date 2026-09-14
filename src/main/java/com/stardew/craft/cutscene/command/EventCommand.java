package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;

/**
 * A single step in an event script.
 * Commands are ticked every client tick until complete.
 */
public interface EventCommand {

    /**
     * Called once when the command first becomes active.
     */
    default void start(EventPlayer player) {}

    /**
     * Called every client tick while this command is active.
     */
    void tick(EventPlayer player);

    /**
     * @return true when this command is finished and the player should advance.
     */
    boolean isComplete();

    /**
     * Called when the player skips the event.
     * State-changing commands should apply their effects here.
     * Visual/audio commands can no-op.
     */
    default void onSkip(EventPlayer player) {}

    /**
     * @return true if this command modifies game state (friendship, items, etc.)
     *         and should be executed even when skipping.
     */
    default boolean isStateCommand() { return false; }
}
