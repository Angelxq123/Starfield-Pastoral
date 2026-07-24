package com.stardew.craft.time.settlement;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class DailySettlementCommitHooks {
    private final Operations operations;

    DailySettlementCommitHooks(Operations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    static DailySettlementCommitHooks production(MinecraftServer server) {
        WeakReference<MinecraftServer> reference =
                new WeakReference<>(Objects.requireNonNull(server, "server"));
        return new DailySettlementCommitHooks(new Operations() {
            @Override
            public void specialOrders(List<UUID> playerIds) {
                com.stardew.craft.specialorder.SpecialOrderManager.onNewDayForPlayers(
                        level(), playerIds);
            }

            @Override
            public void bookseller(
                    List<UUID> playerIds, DailySettlementContext context) {
                com.stardew.craft.book.BooksellerSchedule.onNewDayForPlayers(
                        level(), playerIds, context.year(), context.season(),
                        context.day(), context.absoluteDay());
                com.stardew.craft.shop.BooksellerEvents.forceCheckNow(level());
            }

            @Override
            public void mail(List<UUID> playerIds, int absoluteDay) {
                com.stardew.craft.mail.MailService.deliverTomorrowMailForPlayers(
                        server(), playerIds, absoluteDay);
                com.stardew.craft.time.StardewTimeManager.get()
                        .scheduleDateTriggeredMailForPlayers(
                                server(), playerIds, absoluteDay);
            }

            @Override
            public void readyBookseller(List<UUID> playerIds, int absoluteDay) {
                com.stardew.craft.book.BooksellerSchedule.deliverPendingNoticesForPlayers(
                        server(), playerIds, absoluteDay);
            }

            private MinecraftServer server() {
                MinecraftServer current = reference.get();
                if (current == null) {
                    throw new IllegalStateException(
                            "Daily settlement server is no longer available");
                }
                return current;
            }

            private ServerLevel level() {
                ServerLevel level = server().getLevel(
                        com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
                if (level == null) {
                    throw new IllegalStateException("Stardew Valley level is not loaded");
                }
                return level;
            }
        });
    }

    boolean supports(String name) {
        return "special_orders".equals(name)
                || "bookseller".equals(name)
                || "mail".equals(name);
    }

    DailySettlementWorkUnit create(String name, DailySettlementContext context) {
        Objects.requireNonNull(context, "context");
        DailySettlementWorkUnits.ThrowingRunnable action = switch (name) {
            case "special_orders" -> () -> operations.specialOrders(
                    context.allOnlinePlayerIds());
            case "bookseller" -> () -> operations.bookseller(
                    context.valleyOnlinePlayerIds(), context);
            case "mail" -> () -> operations.mail(
                    context.allOnlinePlayerIds(), context.absoluteDay());
            default -> throw new IllegalArgumentException(
                    "Unsupported commit hook: " + name);
        };
        return DailySettlementWorkUnits.atomic(name, action, () -> {});
    }

    void ready(DailySettlementContext context) {
        operations.readyBookseller(
                context.valleyOnlinePlayerIds(), context.absoluteDay());
    }

    interface Operations {
        void specialOrders(List<UUID> playerIds);

        void bookseller(List<UUID> playerIds, DailySettlementContext context);

        void mail(List<UUID> playerIds, int absoluteDay);

        void readyBookseller(List<UUID> playerIds, int absoluteDay);
    }
}
