package com.stardew.craft.time.settlement;

import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class DailySettlementLoginRecovery {
    private final Operations operations;

    DailySettlementLoginRecovery(Operations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    public static void onPlayerLogin(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        new DailySettlementLoginRecovery(new Operations() {
            @Override
            public void specialOrderItems() {
                com.stardew.craft.specialorder.SpecialOrderManager
                        .resumeTemporaryItemCleanup(player);
            }

            @Override
            public void mailDelivery() {
                com.stardew.craft.mail.MailService.flushOnLogin(player);
            }

            @Override
            public void dateMail() {
                com.stardew.craft.time.StardewTimeManager.get()
                        .syncDateTriggeredMailOnLogin(player);
            }

            @Override
            public void bookseller() {
                com.stardew.craft.book.BooksellerSchedule.onPlayerLogin(player);
            }
        }).recover();
    }

    void recover() {
        operations.specialOrderItems();
        operations.mailDelivery();
        operations.dateMail();
        operations.bookseller();
    }

    interface Operations {
        void specialOrderItems();

        void mailDelivery();

        void dateMail();

        void bookseller();
    }
}
