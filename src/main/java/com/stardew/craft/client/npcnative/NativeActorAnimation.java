package com.stardew.craft.client.npcnative;

/** Script aliases and playback timing on an already selected native character. */
public final class NativeActorAnimation {
    private NativeActorAnimation() {}

    public static String resolve(NativeNpcModel model, String npcId, String requested) {
        if (requested.isEmpty()) return null;
        if (model.clips().containsKey(requested)) return requested;
        String qualified = "animation." + npcId + "." + requested;
        return model.clips().containsKey(qualified) ? qualified : null;
    }

    public static void apply(NativeNpcPose pose, NativeNpcModel model, String clip,
                             double elapsed, boolean loop) {
        double length = model.clips().get(clip).length();
        double time = Math.max(0, elapsed);
        // The script owns looping, even when the authored clip has a different default.
        time = loop ? time % length : Math.min(time, Math.nextDown(length));
        pose.apply(clip, time);
    }
}
