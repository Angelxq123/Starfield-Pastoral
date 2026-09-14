package com.stardew.craft.client.gui;

/** Native viewport layout; the observation window moves above notes on narrow screens. */
public final class FishPondLayout {
    private FishPondLayout() { }
    public record Page(int x, int y, int width, int height, int contentX, int contentWidth,
                       int top, int bottom, int footerY, boolean columns, int pondWidth, int noteX, int noteWidth) { }
    public static Page fit(int width, int height, int line, int footerHeight, int preferredHeight) {
        int w = Math.min(420,width-12), h=Math.min(preferredHeight,height-12), x=(width-w)/2,y=(height-h)/2,cw=w-32;
        boolean columns=cw>=350;
        return new Page(x,y,w,h,x+16,cw,y+line+36,y+h-footerHeight-22,y+h-footerHeight-12,
                columns,columns?152:100,columns?172:0,columns?cw-172:cw);
    }
    public static int clampScroll(int value,int contentHeight,int viewHeight) { return Math.max(0,Math.min(value,Math.max(0,contentHeight-viewHeight))); }
    public record Row(int labelWidth, boolean stacked, int countY, int height) { }
    public static int labelWidth(int width,int countWidth) { return width-24-countWidth-22>=72?width-24-countWidth-22:width-24; }
    public static Row row(int width,int countWidth,int labelHeight,int line) {
        boolean stacked=width-24-countWidth-22<72;
        int cy=stacked?labelHeight+4:2;
        return new Row(labelWidth(width,countWidth),stacked,cy,Math.max(24,Math.max(labelHeight,cy+line+3)+8));
    }
}
