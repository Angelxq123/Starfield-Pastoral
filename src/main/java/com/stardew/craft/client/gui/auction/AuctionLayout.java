package com.stardew.craft.client.gui.auction;

/** Geometry in GUI pixels, independent of a rendering context for headless layout checks. */
public final class AuctionLayout {
    private AuctionLayout() { }
    public static int seasonStart(int day) { return Math.max(0, day - 1) / 28 * 28 + 1; }
    public static boolean bookable(int date, int today, int occupiedDays) {
        int offset = date - today;
        return offset >= 1 && offset <= 14 && (occupiedDays & (1 << (offset - 1))) == 0;
    }
    public record Booking(boolean wide, int calendarWidth, int calendarY, int cellHeight,
                          int timeX, int timeWidth, int timeCellHeight, int hourColumns, int minuteColumns, int hourY, int minuteY, int noteY) { }
    public static Booking booking(int width, int line, int minimumTimeCell, boolean timeOnly) {
        boolean wide = width >= 350;
        int control = line + 14, calendarW = wide ? 196 : width, cellH = wide ? Math.max(26, line + 10) : Math.max(24, line + 6);
        int calendarY = wide ? control + line + 14 : 2 * line + 10;
        int calendarEnd = calendarY + 4 * cellH + line + 16;
        int tx = wide ? 218 : 0, tw = width - tx;
        int hourCols = Math.min(5, tw / minimumTimeCell), minuteCols = tw >= 6 * minimumTimeCell ? 6 : 3;
        int timeCellH = wide ? control : Math.max(20, line + 6);
        int hourY = wide ? 3 * line + 24 : line + 5;
        int minuteY = hourY + ((15 + hourCols - 1) / hourCols) * (timeCellH + 3) + line + (wide ? 12 : 8);
        int noteY = wide || timeOnly ? Math.max(wide ? calendarEnd : 0,
                minuteY + ((6 + minuteCols - 1) / minuteCols) * (timeCellH + 3)) + 12 : calendarEnd + 8;
        return new Booking(wide, calendarW, calendarY, cellH, tx, tw, timeCellH, hourCols, minuteCols, hourY, minuteY, noteY);
    }
    public record Viewport(int x, int y, int width, int height, int contentWidth, int bodyTop, int bodyBottom) { }
    public static Viewport viewport(int width, int height, int preferredWidth, int preferredHeight, int line, int footerHeight) {
        int w = Math.min(preferredWidth, width - 12), h = Math.min(preferredHeight, height - 12);
        int x = (width - w) / 2, y = (height - h) / 2;
        return new Viewport(x, y, w, h, w - 34, y + Math.max(38, line + 24), y + h - 12 - footerHeight);
    }
    public record Bid(boolean wide, int lotY, int priceX, int priceY, int priceWidth,
                      int valueY, int minimumY, int quickY, int fieldY, int hintY) { }
    public static Bid bid(int width, int line, int metaHeight, int lotHeight,
                          int captionHeight, int minimumHeight, int quickHeight) {
        boolean wide = width >= 350;
        int lotY = metaHeight + (wide ? 16 : 8);
        int px = wide ? 172 : 0, py = wide ? lotY : lotY + lotHeight + 4;
        int valueY = py + captionHeight + 5;
        int minimumY = wide ? valueY + 2 * line + 7 : py + captionHeight + 3;
        int quickY, fieldY, hintY;
        if (wide) {
            quickY = minimumY + minimumHeight + 8;
            fieldY = quickY + quickHeight + line + 12;
            hintY = fieldY + line + 24;
        } else {
            fieldY = minimumY + minimumHeight + 6;
            quickY = fieldY + line + 22;
            hintY = quickY + quickHeight + 10;
        }
        return new Bid(wide, lotY, px, py, width - px, valueY, minimumY, quickY, fieldY, hintY);
    }

    public record Scrollbar(int thumbY, int thumbHeight, int maximum, int top, int height) {
        public int scrollAt(double y) {
            return Math.max(0, Math.min(maximum, (int) Math.round((y - top - thumbHeight / 2.0) * maximum / Math.max(1, height - thumbHeight))));
        }
    }
    public static Scrollbar scrollbar(int top, int height, int contentHeight, int scroll) {
        int maximum = Math.max(0, contentHeight - height);
        int thumb = Math.min(height, Math.max(16, height * height / Math.max(1, contentHeight)));
        int offset = Math.max(0, Math.min(scroll, maximum));
        return new Scrollbar(top + (height - thumb) * offset / Math.max(1, maximum), thumb, maximum, top, height);
    }
}
