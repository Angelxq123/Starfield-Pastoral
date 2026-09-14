package com.stardew.craft.client.weapon;

import com.stardew.craft.combat.network.LavaKatanaReverbPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Separate clocks for each caster; a late end packet cannot end a newer cast. */
public final class LavaKatanaReverbClientState {
    private static final Map<Integer, Window> WINDOWS = new HashMap<>();
    private static ClientLevel activeLevel;

    private LavaKatanaReverbClientState() {}

    record Window(long castTick, long playbackTick, int duration, boolean active) {
        boolean accepts(long incomingTick, boolean starting) {
            return incomingTick > castTick || incomingTick == castTick && active && !starting;
        }
        float progress(double now) {
            return Mth.clamp((float) ((now - playbackTick) / Math.max(1, duration)), 0, 1);
        }
        boolean isActive(double now) {
            return active && now <= playbackTick + duration;
        }
    }

    public static boolean apply(LavaKatanaReverbPayload payload) {
        clearIfNoPlayer();
        if (activeLevel == null) return false;
        Window old = WINDOWS.get(payload.casterId());
        if (old != null && !old.accepts(payload.castTick(), payload.active())) return false;
        WINDOWS.put(payload.casterId(), new Window(payload.castTick(), activeLevel.getGameTime(),
                Math.max(1, payload.durationTicks()), payload.active()));
        return true;
    }

    public static Set<Integer> activeCasterIds() {
        clearIfNoPlayer();
        if (activeLevel == null) return Set.of();
        long now = activeLevel.getGameTime();
        return WINDOWS.entrySet().stream().filter(entry -> entry.getValue().isActive(now))
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
    }

    public static float progress(int casterId, float partialTick) {
        Window window = WINDOWS.get(casterId);
        return window == null || activeLevel == null ? 1 : window.progress(activeLevel.getGameTime() + partialTick);
    }

    public static boolean isActive(Player player) {
        clearIfNoPlayer();
        Window window = player == null ? null : WINDOWS.get(player.getId());
        return window != null && player.level() == activeLevel && player.isAlive()
                && window.isActive(activeLevel.getGameTime());
    }

    public static int getRemainingTicks(Player player) {
        return isActive(player) ? (int) Math.max(0,
                WINDOWS.get(player.getId()).playbackTick + WINDOWS.get(player.getId()).duration
                        - activeLevel.getGameTime()) : 0;
    }

    public static int getTotalTicks() {
        Player player = Minecraft.getInstance().player;
        Window window = player == null ? null : WINDOWS.get(player.getId());
        return window == null ? 1 : window.duration;
    }

    public static void clearIfNoPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (activeLevel != mc.level || mc.player == null) {
            WINDOWS.clear();
            activeLevel = mc.level;
        } else if (activeLevel != null) {
            WINDOWS.keySet().removeIf(id -> !(activeLevel.getEntity(id) instanceof Player player) || !player.isAlive());
        }
    }
}
