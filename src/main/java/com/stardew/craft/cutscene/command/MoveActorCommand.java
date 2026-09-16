package com.stardew.craft.cutscene.command;

/**
 * Ordinary walking to an authored point, with the same grounded route handling as navigate_actor.
 * Legacy ticks remain accepted for Java callers but never set a walking deadline.
 * JSON: {"cmd":"move_actor", "actor":"linus", "x":3, "y":0, "z":0, "relative":true}
 */
public class MoveActorCommand extends NavigateActorCommand {
    public MoveActorCommand(String actorTag, double x, double y, double z, int ticks, boolean relative) {
        this(actorTag, x, y, z, ticks, relative, null);
    }

    public MoveActorCommand(String actorTag, double x, double y, double z, int ticks,
                            boolean relative, String anchor) {
        super(actorTag, x, y, z, relative, 0, anchor);
        this.authoredTicks = ticks;
    }
}
