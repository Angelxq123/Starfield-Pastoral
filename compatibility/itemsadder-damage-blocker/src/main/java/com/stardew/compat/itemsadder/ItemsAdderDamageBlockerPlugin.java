package com.stardew.compat.itemsadder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Traces and neutralizes only ItemsAdder's cancellation of player damage.
 * The first real cancellation identifies the active Youer handler table;
 * subsequent calls are guarded at the individual listener level.
 */
public final class ItemsAdderDamageBlockerPlugin extends JavaPlugin implements Listener {
    private static final String ITEMSADDER = "ItemsAdder";

    private final Set<RegisteredListener> guardedListeners = Collections.newSetFromMap(
            new IdentityHashMap<>()
    );
    private final Set<RegisteredListener> guardedCloseListeners = Collections.newSetFromMap(
            new IdentityHashMap<>()
    );
    private final Set<String> reportedInvalidCloseLocations = new HashSet<>();
    private boolean guardInstallScheduled;
    private boolean guardsInstalled;
    private BukkitTask closeGuardScanTask;
    private int closeGuardScanAttempts;
    private boolean closeGuardsInstalled;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Runtime ItemsAdder damage interception is enabled.");
        startCloseGuardScan();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (ITEMSADDER.equalsIgnoreCase(event.getPlugin().getName())) {
            closeGuardsInstalled = false;
            guardedCloseListeners.clear();
            startCloseGuardScan();
        }
    }

    /**
     * First-run probe. It records the active ItemsAdder registrations, restores
     * the current hit, then replaces each registration with a tracing wrapper.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() || !(event.getDamager() instanceof Player player)) {
            return;
        }

        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin(ITEMSADDER);
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            return;
        }

        List<String> listenerNames = itemsAdderListeners(event.getHandlers());
        if (listenerNames.isEmpty()) {
            return;
        }

        getLogger().warning("ItemsAdder blocked EntityDamageByEntityEvent: "
                + "player=" + player.getName()
                + ", target=" + describe(event.getEntity())
                + ", cause=" + event.getCause()
                + ", damage=" + event.getDamage()
                + ", finalDamage=" + event.getFinalDamage()
                + ", weapon=" + describeItem(player.getInventory().getItemInMainHand())
                + ", listeners=" + listenerNames);

        event.setCancelled(false);
        if (!guardsInstalled && !guardInstallScheduled) {
            guardInstallScheduled = true;
            Bukkit.getScheduler().runTask(this, this::installItemsAdderGuards);
        }
        getLogger().info("ItemsAdder cancellation overridden for this hit; tracing handlers next tick.");
    }

    private void installItemsAdderGuards() {
        guardInstallScheduled = false;
        HandlerList handlers = EntityDamageByEntityEvent.getHandlerList();
        int installed = 0;
        for (RegisteredListener registered : handlers.getRegisteredListeners()) {
            Plugin owner = registered.getPlugin();
            if (owner == null
                    || !ITEMSADDER.equalsIgnoreCase(owner.getName())
                    || guardedListeners.contains(registered)) {
                continue;
            }

            EventExecutor originalExecutor = registered.getExecutor();
            if (originalExecutor == null) {
                getLogger().warning("Could not trace ItemsAdder listener "
                        + listenerDescription(registered, null) + "; executor is inaccessible.");
                continue;
            }

            String description = listenerDescription(registered, originalExecutor);
            RegisteredListener guarded = new RegisteredListener(
                    registered.getListener(),
                    (listener, event) -> {
                        boolean canceledBefore = event instanceof Cancellable cancellable
                                && cancellable.isCancelled();
                        originalExecutor.execute(listener, event);
                        if (event instanceof EntityDamageByEntityEvent damageEvent
                                && !canceledBefore
                                && damageEvent.isCancelled()) {
                            Player player = damageEvent.getDamager() instanceof Player p ? p : null;
                            getLogger().warning("ItemsAdder handler canceled EntityDamageByEntityEvent: "
                                    + "handler=" + description
                                    + ", player=" + (player == null ? "unknown" : player.getName())
                                    + ", target=" + describe(damageEvent.getEntity())
                                    + ", cause=" + damageEvent.getCause()
                                    + ", damage=" + damageEvent.getDamage()
                                    + ", finalDamage=" + damageEvent.getFinalDamage());
                            damageEvent.setCancelled(false);
                            getLogger().info("ItemsAdder handler cancellation overridden; hit restored.");
                        }
                    },
                    registered.getPriority(),
                    owner,
                    registered.isIgnoringCancelled()
            );
            handlers.unregister(registered);
            handlers.register(guarded);
            guardedListeners.add(guarded);
            installed++;
        }

        if (installed > 0) {
            guardsInstalled = true;
            handlers.unregister((Listener) this);
            getLogger().info("Installed direct ItemsAdder damage handler tracing: "
                    + installed + " listener(s); damage probe unregistered.");
        }
    }

    private void startCloseGuardScan() {
        if (closeGuardScanTask != null && !closeGuardScanTask.isCancelled()) {
            return;
        }
        closeGuardScanAttempts = 0;
        closeGuardScanTask = Bukkit.getScheduler().runTaskTimer(
                this,
                this::installInvalidTileStateCloseGuards,
                1L,
                20L
        );
    }

    /**
     * ItemsAdder catches the TileState exception internally, so the only
     * reliable fix is to skip its close serializer before it is invoked when
     * Youer exposes a non-tile block state for a chest location.
     */
    private void installInvalidTileStateCloseGuards() {
        if (closeGuardsInstalled) {
            cancelCloseGuardScan();
            return;
        }
        closeGuardScanAttempts++;
        Plugin itemsAdder = Bukkit.getPluginManager().getPlugin(ITEMSADDER);
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            if (closeGuardScanAttempts >= 100) {
                cancelCloseGuardScan();
            }
            return;
        }

        HandlerList handlers = InventoryCloseEvent.getHandlerList();
        int installed = 0;
        for (RegisteredListener registered : handlers.getRegisteredListeners()) {
            Plugin owner = registered.getPlugin();
            if (owner == null
                    || !ITEMSADDER.equalsIgnoreCase(owner.getName())
                    || guardedCloseListeners.contains(registered)
                    || !isInventoryCloseHandler(registered)) {
                continue;
            }

            EventExecutor originalExecutor = registered.getExecutor();
            if (originalExecutor == null) {
                continue;
            }
            String description = listenerDescription(registered, originalExecutor);
            RegisteredListener guarded = new RegisteredListener(
                    registered.getListener(),
                    (listener, event) -> {
                        if (event instanceof InventoryCloseEvent close
                                && shouldSkipInvalidTileStateSerialization(close, description)) {
                            return;
                        }
                        originalExecutor.execute(listener, event);
                    },
                    registered.getPriority(),
                    owner,
                    registered.isIgnoringCancelled()
            );
            handlers.unregister(registered);
            handlers.register(guarded);
            guardedCloseListeners.add(guarded);
            installed++;
        }

        if (installed > 0) {
            closeGuardsInstalled = true;
            cancelCloseGuardScan();
            getLogger().info("Installed ItemsAdder inventory-close TileState guard: "
                    + installed + " listener(s).");
        } else if (closeGuardScanAttempts >= 100) {
            cancelCloseGuardScan();
        }
    }

    private static boolean isInventoryCloseHandler(RegisteredListener registered) {
        EventExecutor executor = registered.getExecutor();
        Method method = executor == null ? null : boundMethod(executor);
        if (method != null) {
            return method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == InventoryCloseEvent.class;
        }
        return "itemsadder.m.k".equals(registered.getListener().getClass().getName());
    }

    private boolean shouldSkipInvalidTileStateSerialization(
            InventoryCloseEvent event,
            String handlerDescription
    ) {
        Inventory inventory = event.getInventory();
        if (inventory == null
                || inventory.getType() != InventoryType.CHEST
                || inventory.getHolder() instanceof LivingEntity
                || inventory.getLocation() == null) {
            return false;
        }

        BlockState state;
        try {
            state = inventory.getLocation().getBlock().getState();
        } catch (RuntimeException error) {
            state = null;
        }
        if (state instanceof TileState) {
            return false;
        }

        String location = inventory.getLocation().toVector().toString();
        if (reportedInvalidCloseLocations.add(handlerDescription + "@" + location)) {
            getLogger().warning("Skipped ItemsAdder inventory-close serialization: "
                    + "handler=" + handlerDescription
                    + ", player=" + event.getPlayer().getName()
                    + ", inventory=" + inventory.getType()
                    + ", location=" + location
                    + ", reason=TileState is null");
        }
        return true;
    }

    private void cancelCloseGuardScan() {
        if (closeGuardScanTask != null) {
            closeGuardScanTask.cancel();
            closeGuardScanTask = null;
        }
    }

    private static Method boundMethod(EventExecutor executor) {
        for (Class<?> type = executor.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField("method");
                if (!Method.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(executor);
                if (value instanceof Method method) {
                    return method;
                }
            } catch (NoSuchFieldException ignored) {
                // Continue through the executor superclass chain.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                break;
            }
        }

        // Paper's generated executors are cached by their exact handler method.
        for (var entry : EventExecutor.eventExecutorMap.entrySet()) {
            if (entry.getValue().isAssignableFrom(executor.getClass())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static List<String> itemsAdderListeners(HandlerList handlers) {
        List<String> names = new ArrayList<>();
        for (RegisteredListener registered : handlers.getRegisteredListeners()) {
            Plugin owner = registered.getPlugin();
            if (owner != null && ITEMSADDER.equalsIgnoreCase(owner.getName())) {
                names.add(listenerDescription(registered, registered.getExecutor()));
            }
        }
        return names;
    }

    private static String listenerDescription(
            RegisteredListener registered,
            EventExecutor executor
    ) {
        return registered.getListener().getClass().getName()
                + "#" + methodDescription(executor, registered.getListener())
                + "@" + registered.getPriority();
    }

    private static String methodDescription(EventExecutor executor, Listener listener) {
        Method method = executor == null ? null : boundMethod(executor);
        if (method != null) {
            return method.getName() + "(" + method.getParameterTypes()[0].getSimpleName() + ")";
        }
        return eventHandlerMethods(listener);
    }

    private static String eventHandlerMethods(Listener listener) {
        List<String> methods = new ArrayList<>();
        for (Class<?> type = listener.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(EventHandler.class)
                        || method.getParameterCount() != 1
                        || !method.getParameterTypes()[0]
                        .isAssignableFrom(EntityDamageByEntityEvent.class)) {
                    continue;
                }
                methods.add(method.getName() + "("
                        + method.getParameterTypes()[0].getSimpleName() + ")");
            }
        }
        return methods.isEmpty() ? "<method-unavailable>" : String.join("|", methods);
    }

    private static String describe(Entity entity) {
        return entity.getType().getKey() + "#" + entity.getEntityId()
                + "/" + entity.getUniqueId();
    }

    private static String describeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType().getKey() + "x" + item.getAmount();
    }
}
