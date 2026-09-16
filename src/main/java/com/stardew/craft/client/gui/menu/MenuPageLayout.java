package com.stardew.craft.client.gui.menu;

/** Geometry is shared by rendering, hit testing and offline layout proofs. */
public final class MenuPageLayout {
    private MenuPageLayout() { }
    public record Farm(int x,int y,int width,int bottom,int titleY,int defaultY,int defaultHeight,
                       int listTitleY,int listY,int rowHeight,int visibleRows) {
        public int listBottom() { return listY+visibleRows*rowHeight; }
    }
    public static Farm farm(int x,int y,int w,int h,int line) {
        int defaultY=y+line+10,defaultH=line*2+8,listTitle=defaultY+defaultH+6,listY=listTitle+line+4;
        int rowH=line*2+10,visible=Math.max(0,Math.min(5,(y+h-listY)/rowH));
        return new Farm(x,y,w,y+h,y,defaultY,defaultH,listTitle,listY,rowH,visible);
    }
    public record Leaderboard(int contentX,int contentY,int contentW,int titleY,int metricY,int headerY,int listY,
                              int listBottom,int selfY,int rowH,int visibleRows,int refreshX,int refreshY,int refreshW,int refreshH,
                              int metricX,int metricW,int metricH,int metricVisible,int periodY,boolean sidebar) {
        public boolean stackedRows() { return contentW < 190; }
    }
    public static Leaderboard leaderboard(int mx,int my,int mw,int mh,int line,int refreshLabelWidth) {
        // These are design-canvas units. The real menu is 216x191 at REFERENCE_SCALE=4.
        int margin=8,gap=6,metricW=Math.max(68,Math.min(112,Math.round(mw*.34f)));
        int x=mx+margin+metricW+gap,w=mw-margin*2-metricW-gap,y=my+margin;
        int controlH=Math.max(16,line+4),titleH=Math.max(controlH,(int)Math.ceil(line*leaderboardTitleScale(mh))+2);
        int metricY=y+titleH+4,periodY=metricY,headerY=periodY+controlH+2;
        boolean stacked=w<190;
        int listY=headerY+(stacked?0:line+6);
        int footerY=my+mh-margin-controlH,rowH=stacked?line*2+4:Math.max(24,line+10);
        int selfY=footerY-rowH-3,listBottom=selfY-3;
        int visible=Math.max(1,Math.min(5,(listBottom-listY)/rowH));
        rowH=Math.max(rowH,Math.min(36,(footerY-listY-6)/(visible+1)));
        selfY=footerY-rowH-3;
        listBottom=selfY-3;
        int refreshW=Math.min(w/2,Math.max(36,refreshLabelWidth+8));
        int metricH=Math.max(20,line*2+2);
        int metricVisible=Math.max(1,(my+mh-margin-line-5-metricY)/metricH);
        return new Leaderboard(x,y,w,y,metricY,headerY,listY,listBottom,selfY,rowH,visible,
                x+w-refreshW,footerY,refreshW,controlH,mx+margin,metricW,metricH,metricVisible,periodY,true);
    }
    public static float leaderboardTitleScale(int menuHeight) { return menuHeight<250?1.25f:menuHeight<350?1.5f:2f; }
    public record MetricButton(int x,int y,int width,int height) {
        public boolean contains(double mx,double my) { return mx>=x&&mx<x+width&&my>=y&&my<y+height; }
    }
    public static MetricButton metricButton(Leaderboard l,int visibleIndex) {
        return new MetricButton(l.metricX(),l.metricY()+visibleIndex*l.metricH(),l.metricW()-8,l.metricH()-2);
    }
    public static int revealMetric(int scroll,int selected,int total,int visible) {
        int max=Math.max(0,total-visible);
        int result=Math.max(0,Math.min(max,scroll));
        if(selected<result)result=selected;
        else if(selected>=result+visible)result=selected-visible+1;
        return Math.max(0,Math.min(max,result));
    }
    public record MetricScrollbar(int x,int y,int width,int height,int thumbY,int thumbHeight,int maxScroll) {
        public int scrollAt(double mouseY,int grabOffset) {
            return Math.max(0,Math.min(maxScroll,(int)Math.round((mouseY-y-grabOffset)*maxScroll/Math.max(1,height-thumbHeight))));
        }
    }
    public static MetricScrollbar metricScrollbar(Leaderboard l,int total,int scroll) {
        int height=l.metricVisible()*l.metricH()-2,maxScroll=Math.max(0,total-l.metricVisible());
        int thumb=Math.min(height,Math.max(18,height*l.metricVisible()/Math.max(1,total)));
        int y=l.metricY()+(height-thumb)*Math.max(0,Math.min(maxScroll,scroll))/Math.max(1,maxScroll);
        return new MetricScrollbar(l.metricX()+l.metricW()-6,l.metricY(),6,height,y,thumb,maxScroll);
    }
    public record Tooltip(int x,int y,int width,int height) { }
    public static Tooltip tooltip(int mx,int my,int sw,int sh,int textWidth,int lineCount,int lineHeight) {
        int w=Math.min(sw-8,textWidth+16),h=Math.min(sh-8,lineCount*(lineHeight+3)+13);
        int x=mx+12+w<=sw-4?mx+12:mx-w-12;
        return new Tooltip(Math.max(4,Math.min(x,sw-w-4)),Math.max(4,Math.min(my+14,sh-h-4)),w,h);
    }
    public static int optionRowHeight(int height) { return Math.max(32,Math.min(92,(height-64)/3)); }
}
