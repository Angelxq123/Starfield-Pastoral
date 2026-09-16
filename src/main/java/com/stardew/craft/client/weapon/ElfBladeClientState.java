package com.stardew.craft.client.weapon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ElfBladeClientState {

    private static net.minecraft.world.level.Level activeLevel;
    private static boolean active = false;
    private static long endTick = 0L;
    private static int totalTicks = 0;

    private ElfBladeClientState() {}

    public static void start(long nowTick, int durationTicks) {
        activeLevel = Minecraft.getInstance().level;
        active = true;
        totalTicks = Math.max(1, durationTicks);
        endTick = nowTick + totalTicks;
    }

    public static void clear() {
        active = false;
        endTick = 0L;
        totalTicks = 0;
    }

    public static boolean isActive(Player player) {
        if (!active || player == null || player.level() != activeLevel) {
            return false;
        }
        long nowTick = player.level().getGameTime();
        if (nowTick > endTick) {
            clear();
            return false;
        }
        return true;
    }

    public static int getRemainingTicks(Player player) {
        if (!isActive(player)) {
            return 0;
        }
        long nowTick = player.level().getGameTime();
        return (int) Math.max(0, endTick - nowTick);
    }

    public static int getTotalTicks() {
        return Math.max(1, totalTicks);
    }

    public static void clearIfNoPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level != activeLevel) {
            clear();
        }
    }
}
