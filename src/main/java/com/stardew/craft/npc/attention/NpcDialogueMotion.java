package com.stardew.craft.npc.attention;

/** Reuses the approved glance's outbound/return steps, with a session-length hold. */
public final class NpcDialogueMotion {
    private NpcDialogueMotion() {}

    public static double readyTime(double yaw) { return .50 + NpcAttentionMotion.turnTime(yaw); }

    public static double readyTime(double yaw, double resumeFrom) {
        return resumeFrom < 0 ? readyTime(yaw) : .15 + Math.max(0,resumeFrom-NpcAttentionMotion.holdEnd(yaw));
    }

    /** Negative releaseAge means the conversation is still open. Times are seconds since start. */
    public static double motionTime(double age, double yaw, double releaseAge) {
        return motionTime(age,yaw,releaseAge,-1);
    }

    public static double motionTime(double age, double yaw, double releaseAge, double resumeFrom) {
        double ready = readyTime(yaw,resumeFrom);
        if (releaseAge < 0 || age < Math.max(ready, releaseAge)) {
            if (resumeFrom>=0) return mix(resumeFrom,NpcAttentionMotion.holdEnd(yaw),NpcAttentionMotion.smooth(age/ready));
            return Math.min(age, ready);
        }
        // Both hold poses are identical: skip only the quiet hold, never part of a step.
        return NpcAttentionMotion.holdEnd(yaw) + age - Math.max(ready, releaseAge);
    }

    public static boolean finished(double age, double yaw, double releaseAge) {
        return releaseAge >= 0 && motionTime(age, yaw, releaseAge) >= NpcAttentionMotion.duration(yaw);
    }

    /** A short takeover blends from the exact previous pose, instead of snapping to neutral. */
    public static NpcAttentionMotion.Sample blend(NpcAttentionMotion.Sample a, NpcAttentionMotion.Sample b, double t) {
        if (t<=0) return a;
        if (t>=1) return b;
        return new NpcAttentionMotion.Sample(mix(a.bodyYaw(), b.bodyYaw(), t),
                mix(a.rootX(), b.rootX(), t), mix(a.rootZ(), b.rootZ(), t),
                mix(a.shoulderYaw(), b.shoulderYaw(), t), mix(a.headYaw(), b.headYaw(), t),
                mix(a.headPitch(), b.headPitch(), t), mix(a.lean(), b.lean(), t),
                mix(a.armSwing(), b.armSwing(), t), t < .5 ? a.blink() : b.blink(),
                blend(a.right(), b.right(), t), blend(a.left(), b.left(), t));
    }

    private static NpcAttentionMotion.Foot blend(NpcAttentionMotion.Foot a, NpcAttentionMotion.Foot b, double t) {
        return new NpcAttentionMotion.Foot(mix(a.angle(), b.angle(), t), mix(a.heel(), b.heel(), t),
                mix(a.lift(), b.lift(), t), mix(a.toeX(), b.toeX(), t), mix(a.toeZ(), b.toeZ(), t));
    }

    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
