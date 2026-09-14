package com.stardew.craft.client.npcnative;

import java.util.List;
import java.util.Map;

/** Runtime data only: no editor, Minecraft, or GeckoLib objects. Coordinates remain in model units. */
public record NativeNpcModel(int version, String texture, List<Bone> bones, List<Quad> quads,
                             Map<String, Clip> clips, Profile profile) {
    public record Bone(String name, int parent, float[] origin, float[] rotation) {}
    public record Quad(int bone, float[][] vertices, float[] normal, boolean translucent, String sourcePart, boolean cull, Skin skin) {}
    /** Lower-bone weight for each corner; the upper weight is exactly one minus it. */
    public record Skin(int upper, int lower, float[] weights) {}
    public record Clip(double length, boolean loop, List<Track> tracks) {}
    public record Track(int bone, String channel, List<Key> keys) {}
    public record Key(double time, float[] before, float[] after, boolean step) {}
    public record Profile(double intervalMin, double intervalMax, double durationMin, double durationMax,
                          double doubleChance, float groundOffset, double walkStride,
                          com.stardew.craft.npc.attention.NpcAttentionMotion.Rig attentionRig, String blinkMode, Cloth cloth, LookLimits lookLimits, Gait gait) {
        /** Closed eyes and opaque glasses retain their appearance without an empty blink clip. */
        public boolean visibleBlink() { return !"occluded".equals(blinkMode) && !"closed".equals(blinkMode); }
        public com.stardew.craft.npc.attention.NpcAttentionMotion.Rig attentionRig() {
            return attentionRig == null ? com.stardew.craft.npc.attention.NpcAttentionMotion.SAM : attentionRig;
        }
    }
    /** Authored preview pace in blocks per second, paired with the existing walk stride. */
    public record Gait(double previewSpeed) {}
    /** The source surface remains editable; the runtime bends its lower rows around the posed legs. */
    public record Cloth(String bone, String kind, float anchorY, float hemY, float margin, String clearancePart, Float standingDepthMargin, Float contactBlend, boolean preserveLayerOffset, boolean supportLift) {}
    /** Hair constrained around the shoulder limits relative neck motion; the torso carries the remainder. */
    public record LookLimits(float yaw, float pitch) {}
}
