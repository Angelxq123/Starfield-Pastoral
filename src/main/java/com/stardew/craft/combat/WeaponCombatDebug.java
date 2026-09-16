package com.stardew.craft.combat;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/** Bounded, opt-in trace of one weapon hit through the server damage pipeline. */
public final class WeaponCombatDebug {
    private WeaponCombatDebug() {
    }

    public static void log(
            String stage,
            Player player,
            Entity target,
            String detail,
            Object... detailArguments
    ) {
        if (!Config.isServerDebugLoggingEnabled()
                || player == null
                || player.level().isClientSide) {
            return;
        }

        Object[] arguments = new Object[5 + detailArguments.length];
        arguments[0] = stage;
        arguments[1] = player.level().getGameTime();
        arguments[2] = player.getGameProfile().getName();
        arguments[3] = describeTarget(target);
        arguments[4] = describeWeapon(player.getMainHandItem());
        System.arraycopy(
                detailArguments,
                0,
                arguments,
                5,
                detailArguments.length
        );
        StardewCraft.LOGGER.info(
                "[WEAPON_DEBUG] stage={} gameTick={} player={} target={} weapon={} "
                        + detail,
                arguments
        );
    }

    public static String health(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return "n/a";
        }
        return living.getHealth() + "/" + living.getMaxHealth();
    }

    /** Reads Youer's public field when available while remaining build-compatible with vanilla mappings. */
    public static float lastHurt(LivingEntity entity) {
        if (entity == null) {
            return Float.NaN;
        }
        try {
            Field field = LivingEntity.class.getDeclaredField("lastHurt");
            field.setAccessible(true);
            return field.getFloat(entity);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Float.NaN;
        }
    }

    /** Describes the Bukkit event that made Youer's LivingEntity#hurt return false. */
    public static String bukkitRejection(LivingEntity entity) {
        if (!Config.isServerDebugLoggingEnabled() || entity == null) {
            return "disabled";
        }
        try {
            Field eventField = LivingEntity.class.getField(
                    "bukkitEntityDamageEvent"
            );
            Object event = eventField.get(entity);
            if (event == null) {
                return "event=none";
            }

            Class<?> eventType = event.getClass();
            Object cancelled = invokeNoArgs(event, "isCancelled");
            Object cause = invokeNoArgs(event, "getCause");
            Object damage = invokeNoArgs(event, "getDamage");
            Object finalDamage = invokeNoArgs(event, "getFinalDamage");
            return "event=" + eventType.getSimpleName()
                    + ",cancelled=" + cancelled
                    + ",cause=" + cause
                    + ",damage=" + damage
                    + ",finalDamage=" + finalDamage
                    + ",listeners=" + listenerPlugins(event);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return "unavailable(" + error.getClass().getSimpleName() + ")";
        }
    }

    private static Set<String> listenerPlugins(Object event)
            throws ReflectiveOperationException {
        Object handlers = invokeNoArgs(event, "getHandlers");
        Object listeners = invokeNoArgs(handlers, "getRegisteredListeners");
        Set<String> plugins = new TreeSet<>();
        int length = Array.getLength(listeners);
        for (int index = 0; index < length; index++) {
            Object listener = Array.get(listeners, index);
            try {
                Method getPlugin = methodFromSuperclass(
                        listener,
                        "org.bukkit.plugin.RegisteredListener",
                        "getPlugin"
                );
                Object plugin = getPlugin.invoke(listener);
                Method getName = getPlugin.getReturnType().getMethod(
                        "getName"
                );
                Object name = getName.invoke(plugin);
                if (name != null) {
                    plugins.add(name.toString());
                }
            } catch (ReflectiveOperationException
                     | RuntimeException
                     | LinkageError error) {
                plugins.add("unavailable:" + error.getClass().getSimpleName());
            }
        }
        return plugins;
    }

    private static Method methodFromSuperclass(
            Object target,
            String ownerClassName,
            String methodName
    ) throws NoSuchMethodException {
        Class<?> type = target.getClass();
        while (type != null && !ownerClassName.equals(type.getName())) {
            type = type.getSuperclass();
        }
        if (type == null) {
            throw new NoSuchMethodException(ownerClassName + "#" + methodName);
        }
        return type.getMethod(methodName);
    }

    private static Object invokeNoArgs(Object target, String methodName)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static String describeTarget(Entity entity) {
        if (entity == null) {
            return "none";
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())
                + "#" + entity.getId()
                + "/" + entity.getUUID();
    }

    private static String describeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem())
                .toString();
        return WeaponCombatIdentity.resolve(stack)
                .map(identity -> registryId + "(" + identity.id() + ")")
                .orElse(registryId);
    }
}
