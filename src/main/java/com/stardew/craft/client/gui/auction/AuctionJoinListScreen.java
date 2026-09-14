package com.stardew.craft.client.gui.auction;

import com.stardew.craft.network.payload.OpenAuctionJoinListPayload.AuctionSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Comparator;
import java.util.List;

/** Chronological programme: each dated entry opens its own consignment form. */
@SuppressWarnings("null")
public class AuctionJoinListScreen extends AuctionScreen {
    private final List<AuctionSummary> auctions;
    public AuctionJoinListScreen(List<AuctionSummary> auctions) {
        super("stardewcraft.auction.join_list.title");
        this.auctions = auctions.stream().sorted(Comparator.comparingInt(AuctionSummary::scheduledDay)
                .thenComparingInt(AuctionSummary::startMinute)).toList();
    }
    @Override protected int preferredWidth() { return 416; }
    @Override protected int preferredHeight() {
        if (!auctions.isEmpty()) return super.preferredHeight();
        int w = Math.min(preferredWidth(), width - 12) - 34;
        return Math.max(38, line + 24) + 112 + controlH
                + wrappedHeight(tr("join_list.empty"), w) + wrappedHeight(tr("join_list.empty_hint"), w);
    }
    @Override protected void layout() {
        int y = wrappedHeight(tr("join_list.subtitle"), contentW) + 14;
        for (AuctionSummary a : auctions) {
            int rowH = Math.max(60 + line, wrappedHeight(Component.literal(a.name()), contentW - 88) + 2 * (line + 5) + 16);
            actionArea(0, y, contentW, rowH, narration(a),
                    () -> minecraft.setScreen(new AuctionConsignScreen(a, this)), (g, b) -> {
                int x = b.getX(), yy = b.getY();
                if (b.isHoveredOrFocused()) g.fill(x + 60, yy + 1, x + contentW, yy + rowH - 8, 0x33D2AE76);
                AuctionUi.sprite(g, "date_leaf", x + 6, yy + 5, 40, 48);
                String day = String.valueOf(Math.max(0, a.scheduledDay() - 1) % 28 + 1);
                number(g, day, x + 26 - font.width(day), yy + 17, 2, AuctionUi.INK);
                String season = season(a.scheduledDay()).getString();
                String fitted = font.plainSubstrByWidth(season, 56);
                g.drawString(font, fitted, x + 26 - font.width(fitted) / 2, yy + 56, AuctionUi.MUTED, false);
                if (b.isHovered()) tooltip(narration(a));
                int tx = x + 66, tw = contentW - 88;
                String time = time(a.startMinute());
                g.drawString(font, time, tx, yy + 4, AuctionUi.GOLD, false);
                Component count = tr("join_list.lots", a.lotCount());
                String countText = font.plainSubstrByWidth(count.getString(), Math.max(1, tw - font.width(time) - 12));
                g.drawString(font, countText, x + contentW - 14 - font.width(countText), yy + 4, AuctionUi.MUTED, false);
                int end = paragraph(g, Component.literal(a.name()), tx, yy + line + 13, tw, AuctionUi.INK);
                String host = tr("join_list.detail_host", a.creatorName()).getString();
                g.drawString(font, font.plainSubstrByWidth(host, tw), tx, end + 4, AuctionUi.MUTED, false);
                AuctionUi.rule(g, x + 66, yy + rowH - 3, contentW - 66);
                // A small forward chevron makes the whole entry's destination explicit.
                for (int i = 0; i < 4; i++) {
                    g.fill(x + contentW - 7 + i, yy + rowH / 2 - 4 + i, x + contentW - 5 + i, yy + rowH / 2 - 3 + i, AuctionUi.GOLD);
                    g.fill(x + contentW - 7 + i, yy + rowH / 2 + 3 - i, x + contentW - 5 + i, yy + rowH / 2 + 4 - i, AuctionUi.GOLD);
                }
            });
            y += rowH + 6;
        }
        contentHeight = auctions.isEmpty() ? wrappedHeight(tr("join_list.empty"), contentW)
                + wrappedHeight(tr("join_list.empty_hint"), contentW) + 92 : y;
        footer(tr("picker.cancel"), false, false, this::onClose);
    }
    private Component narration(AuctionSummary a) {
        return Component.literal(a.name()).append("\n").append(tr("join_list.meta", a.creatorName(),
                tr("join_list.day_time", season(a.scheduledDay()), Math.max(0, a.scheduledDay() - 1) % 28 + 1, time(a.startMinute())), a.lotCount()))
                .append("\n").append(tr("join_list.join"));
    }
    static String time(int minute) { return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60); }
    static Component season(int day) {
        return Component.translatable("stardewcraft.season." + switch ((Math.max(0, day - 1) / 28) % 4) {
            case 1 -> "summer"; case 2 -> "fall"; case 3 -> "winter"; default -> "spring";
        });
    }
    @Override protected void drawBody(GuiGraphics g, float partialTick) {
        if (auctions.isEmpty()) AuctionUi.illustration(g, "register", (contentW - 64) / 2, 2);
        int y = paragraph(g, tr(auctions.isEmpty() ? "join_list.empty" : "join_list.subtitle"), 0, auctions.isEmpty() ? 80 : 0, contentW, AuctionUi.MUTED);
        if (auctions.isEmpty()) paragraph(g, tr("join_list.empty_hint"), 0, y + 10, contentW, AuctionUi.BODY);
    }
}
