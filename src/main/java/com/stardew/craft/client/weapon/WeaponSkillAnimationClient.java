package com.stardew.craft.client.weapon;

import com.stardew.craft.client.weapon.presentation.SkillPresentationClient;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class WeaponSkillAnimationClient {

    private static long startTick = -1;
    private static int durationTicks = 0;
    private static String weaponId;
    private static String skillId;
    private static Vec3 windSpireOrigin;
    private static long windSpireTick = -9999;
    private static final Map<Integer, WorldAction> WORLD_ACTIONS = new HashMap<>();
    private static ClientLevel actionLevel;

    private WeaponSkillAnimationClient() {}

    public static void start(int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        start(null, null, durationTicks);
    }

    @SuppressWarnings("null")
    public static void start(String weaponId, String skillId, int durationTicks) {
        startLocalAnimation(weaponId, skillId, durationTicks);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || skillId == null) {
            return;
        }
        SkillEffectsClient.playSkillEffects(skillId, mc.player);
    }

    public static void start(WeaponSkillAnimPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (actionLevel != mc.level) {
            WORLD_ACTIONS.clear();
            actionLevel = mc.level;
        }
        // An optional frenzy opening must not replace an already executing needle stroke.
        if (MeleeWeaponVisuals.NEEDLE_FRENZY.equals(payload.skillId())
                && getWorldActionProgress(payload.casterEntityId(), 0) >= 0) {
            var current = getWorldAction(payload.casterEntityId());
            if (current != null && "iridium_needle".equals(payload.weaponId())
                    && payload.weaponId().equals(current.weaponId())
                    && NeedleBurglarVisuals.keepsCurrentStrike(current.skillId(), payload.skillId())) {
                NeedleBurglarVisuals.start(payload);
                return;
            }
        }
        long playbackStartTick = mc.level.getGameTime();
        WORLD_ACTIONS.put(
                payload.casterEntityId(),
                new WorldAction(payload, playbackStartTick)
        );

        Entity entity = mc.level.getEntity(payload.casterEntityId());
        Player caster = entity instanceof Player player ? player : null;
        boolean localCaster = mc.player != null && mc.player.getId() == payload.casterEntityId();
        if (localCaster) {
            startLocalAnimation(
                    payload.weaponId(),
                    payload.skillId(),
                    payload.actionDurationTicks(),
                    playbackStartTick
            );
        }

        boolean migrated;
        if ("lava_katana".equals(payload.weaponId()) && LavaKatanaVisuals.BRAND.equals(payload.skillId())) {
            LavaKatanaVisuals.startBrand(payload);
            migrated = true;
        } else if ("lava_katana".equals(payload.weaponId()) && LavaKatanaVisuals.REVERB.equals(payload.skillId())) {
            migrated = true; // Sustained ignition is driven by its authoritative state payload.
        } else if (MeleeWeaponVisuals.startSkill(payload)) {
            migrated = true;
        } else {
            migrated = SkillPresentationClient.start(payload, playbackStartTick);
        }
        if (!migrated && caster != null) {
            SkillEffectsClient.playSkillEffects(payload.skillId(), caster);
        }
    }

    @SuppressWarnings("null")
    private static void startLocalAnimation(String weaponId, String skillId, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        startLocalAnimation(weaponId, skillId, durationTicks, mc.level.getGameTime());
    }

    private static void startLocalAnimation(
            String weaponId,
            String skillId,
            int durationTicks,
            long actionStartTick
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        WeaponSkillAnimationClient.startTick = actionStartTick;
        WeaponSkillAnimationClient.durationTicks = Math.max(1, durationTicks);
        WeaponSkillAnimationClient.weaponId = weaponId;
        WeaponSkillAnimationClient.skillId = skillId;

        if ("wind_spire_thrust".equals(skillId) && mc.level != null) {
            var player = mc.player;
            if (player == null) {
                return;
            }
            long now = mc.level.getGameTime();
            if (now - windSpireTick > 2) {
                windSpireOrigin = player.position();
                windSpireTick = now;
            }
        }
    }

    public static Vec3 getWindSpireOrigin() {
        return windSpireOrigin;
    }

    public static long getWindSpireTick() {
        return windSpireTick;
    }

    @SuppressWarnings("null")
    public static float getProgress(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || startTick < 0 || durationTicks <= 0) {
            return -1.0f;
        }
        float age = (mc.level.getGameTime() - startTick) + partialTick;
        float t = age / durationTicks;
        if (t >= 1.0f) {
            startTick = -1;
            durationTicks = 0;
            weaponId = null;
            skillId = null;
            return -1.0f;
        }
        return t;
    }

    public static boolean isActive() {
        return startTick >= 0 && durationTicks > 0;
    }

    public static void stopMatching(int actor,String expectedSkill) {
        var action=WORLD_ACTIONS.get(actor);
        if(action!=null&&expectedSkill.equals(action.payload.skillId()))WORLD_ACTIONS.remove(actor);
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.getId()==actor&&expectedSkill.equals(skillId))stop();
    }

    public static void stop() {
        startTick = -1;
        durationTicks = 0;
        weaponId = null;
        skillId = null;
    }

    public static String getWeaponId() {
        return weaponId;
    }

    public static String getSkillId() {
        return skillId;
    }

    public static float getWorldActionProgress(int entityId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || actionLevel != minecraft.level) {
            WORLD_ACTIONS.clear();
            actionLevel = minecraft.level;
            return -1.0f;
        }
        WorldAction worldAction = WORLD_ACTIONS.get(entityId);
        if (worldAction == null) {
            return -1.0f;
        }
        float progress = calculatePlaybackProgress(
                minecraft.level.getGameTime(),
                worldAction.playbackStartTick,
                partialTick,
                worldAction.payload.actionDurationTicks()
        );
        if (progress >= 1.0f) {
            WORLD_ACTIONS.remove(entityId);
            return -1.0f;
        }
        return Math.max(0.0f, progress);
    }

    public static WeaponSkillAnimPayload getWorldAction(int entityId) {
        WorldAction action = WORLD_ACTIONS.get(entityId);
        return action == null ? null : action.payload;
    }

    public static long getWorldActionPlaybackStartTick(int entityId) {
        WorldAction action = WORLD_ACTIONS.get(entityId);
        return action == null ? -1L : action.playbackStartTick;
    }

    static float calculatePlaybackProgress(
            long currentTick,
            long playbackStartTick,
            float partialTick,
            int durationTicks
    ) {
        float age = (currentTick - playbackStartTick) + partialTick;
        return age / Math.max(1, durationTicks);
    }

    private record WorldAction(
            WeaponSkillAnimPayload payload,
            long playbackStartTick
    ) {}
}
