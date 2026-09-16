package com.stardew.craft.client.weapon;

import com.stardew.craft.combat.skill.SkillCooldownTime;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端本地技能冷却存储（由服务端同步）
 */
public final class WeaponSkillCooldownsClient {

    private static final Map<String, CooldownEntry> cooldowns = new HashMap<>();
    private static long clientTick;

    private WeaponSkillCooldownsClient() {}

    public static void setCooldown(String weaponId, String skillId, int totalTicks, int remainingTicks) {
        int normalizedRemaining = Math.max(0, remainingTicks);
        long endTick = SkillCooldownTime.endAt(clientTick, 0L, normalizedRemaining);
        cooldowns.put(getKey(weaponId, skillId),
                new CooldownEntry(endTick, Math.max(0, totalTicks)));
    }

    /** Advances the session clock without depending on the active dimension. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) {
            clientTick++;
        }
    }

    public static boolean isOnCooldown(String weaponId, String skillId) {
        return getRemainingTicks(weaponId, skillId) > 0;
    }

    @SuppressWarnings("null")
    public static int getRemainingTicks(String weaponId, String skillId) {
        CooldownEntry entry = cooldowns.get(getKey(weaponId, skillId));
        if (entry == null) return 0;
        
        int remaining = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0, entry.endTick - clientTick));
        return remaining;
    }

    public static int getTotalTicks(String weaponId, String skillId) {
        CooldownEntry entry = cooldowns.get(getKey(weaponId, skillId));
        return entry != null ? entry.totalTicks : 0;
    }

    public static void clear() {
        cooldowns.clear();
        clientTick = 0L;
    }

    private static String getKey(String weaponId, String skillId) {
        return weaponId + "|" + skillId;
    }

    private record CooldownEntry(long endTick, int totalTicks) {}
}
