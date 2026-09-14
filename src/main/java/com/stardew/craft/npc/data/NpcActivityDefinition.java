package com.stardew.craft.npc.data;

import java.util.Set;

/** Server-side activity contract; clips and models are resource identities, never renderer objects. */
public record NpcActivityDefinition(String id, Set<String> actors, Set<String> aliases, String asset,
                                    String enterClip, String playClip, String exitClip,
                                    int enterTicks, int exitTicks, String support, String transition) {
    public NpcActivityDefinition(String id,Set<String> actors,Set<String> aliases,String asset,
                                String enterClip,String playClip,String exitClip,int enterTicks,int exitTicks,String support) {
        this(id,actors,aliases,asset,enterClip,playClip,exitClip,enterTicks,exitTicks,support,support.isEmpty()?"blend":"clips");
    }
    public NpcActivityDefinition {
        actors=Set.copyOf(actors); aliases=Set.copyOf(aliases);
        if (net.minecraft.resources.ResourceLocation.tryParse(id)==null || net.minecraft.resources.ResourceLocation.tryParse(asset)==null)
            throw new IllegalArgumentException("Invalid activity resource identity");
        if (id.isBlank() || actors.isEmpty() || asset.isBlank() || playClip.isBlank()
                || enterTicks<1 || exitTicks<1 || enterTicks>1200 || exitTicks>1200
                || !Set.of("", "chair", "bed").contains(support))
            throw new IllegalArgumentException("Invalid NPC activity " + id);
        if (!Set.of("blend","clips").contains(transition)) throw new IllegalArgumentException("Invalid activity transition");
        if ((!support.isEmpty() || transition.equals("clips")) && (enterClip.isBlank() || exitClip.isBlank()))
            throw new IllegalArgumentException("Supported activity requires enter and exit clips: " + id);
    }
    public boolean supported() { return !support.isEmpty(); }
}
