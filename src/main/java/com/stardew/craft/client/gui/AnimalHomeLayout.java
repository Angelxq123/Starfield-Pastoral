package com.stardew.craft.client.gui;

/** Five destinations at most; rows keep native type and reduce their count on small screens. */
public final class AnimalHomeLayout {
    private AnimalHomeLayout() { }
    public record Page(int x,int y,int width,int height,int contentX,int contentWidth,int listY,int rows,int rowHeight,int footerY) { }
    public static Page fit(int width,int height,int header,int footer,int row,int count) {
        int w=Math.min(360,width-20),cw=w-36;
        int rows=Math.max(1,Math.min(Math.min(5,Math.max(1,count)),(height-20-header-footer-34)/row));
        int h=header+rows*row+footer+34,x=(width-w)/2,y=(height-h)/2;
        return new Page(x,y,w,h,x+18,cw,y+header,rows,row,y+h-footer-14);
    }
    public static int rowHeight(int line,boolean stacked){return Math.max(42,line*(stacked?3:2)+(stacked?22:18));}
    public static int clampScroll(int value,int count,int rows){return Math.max(0,Math.min(value,Math.max(0,count-rows)));}
    public static int keepVisible(int scroll,int selected,int count,int rows) {
        if(selected>=0){if(selected<scroll)scroll=selected;else if(selected>=scroll+rows)scroll=selected-rows+1;}
        return clampScroll(scroll,count,rows);
    }
}
