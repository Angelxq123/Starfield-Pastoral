package com.stardew.craft.client.gui;

/** Naming controls stay visible even when a long parent name makes the message scroll. */
public final class AnimalBirthLayout {
    private AnimalBirthLayout() { }
    public record Page(int x,int y,int width,int height,int contentX,int contentWidth,int top,int bottom,int labelY,int fieldY,int fieldHeight,int buttonY,int buttonHeight) { }
    public static Page fit(int width,int height,int line,int content,int buttonHeight) {
        int w=Math.min(300,width-20),cw=w-36,field=Math.max(28,line+14);
        int h=Math.min(height-20,20+content+12+line+6+field+12+buttonHeight+16);
        int x=(width-w)/2,y=(height-h)/2,by=y+h-16-buttonHeight,fy=by-12-field,ly=fy-line-6;
        return new Page(x,y,w,h,x+18,cw,y+20,ly-12,ly,fy,field,by,buttonHeight);
    }
    public static int clampScroll(int value,int content,int view) {return Math.max(0,Math.min(value,Math.max(0,content-view)));}
}
