package com.stardew.craft.client.gui;

/** Compact tally slip. Numeric hierarchy uses integer magnification and wraps before clipping. */
public final class SiloLayout {
    private SiloLayout() { }
    public record Page(int x,int y,int width,int height,int contentX,int contentWidth,int top,int bottom,int footerY) { }
    public static Page fit(int width,int height,int line,int footer,int content) {
        int w=Math.min(280,width-20),cw=w-32;
        int h=Math.min(height-20,Math.max(154,46+content+footer+24));
        int x=(width-w)/2,y=(height-h)/2;
        return new Page(x,y,w,h,x+16,cw,y+46,y+h-footer-20,y+h-footer-10);
    }
    public record Counter(int scale,boolean stacked,int capacityX,int height) { }
    public static Counter counter(int width,int currentWidth,int capacityWidth,int line) {
        int scale=2*currentWidth+6+capacityWidth<=width?2:1;
        boolean stacked=currentWidth*scale+6+capacityWidth>width;
        return new Counter(scale,stacked,stacked?0:currentWidth*scale+6,line*scale+(stacked?line+4:0));
    }
    public static int fillWidth(int amount,int capacity,int width) {
        return capacity<=0?0:(int)((long)Math.max(0,Math.min(amount,capacity))*width/capacity);
    }
    public static int clampScroll(int value,int content,int view) { return Math.max(0,Math.min(value,Math.max(0,content-view))); }
}
