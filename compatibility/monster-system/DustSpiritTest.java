package com.stardew.craft.monster;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DustSpiritTest {
    @Test void sourceIntegerHopsAndReload(){
        var a=new DustSpiritMotion();var r=RandomSource.create(42);a.launch(r);int low=0;boolean landed=false;
        for(int i=0;i<100;i++){a.jumpPhysics();low=Math.min(low,a.offset());var b=new DustSpiritMotion();b.load(a.save());assertEquals(a.save(),b.save());if(a.offset()==0){landed=true;break;}}
        assertTrue(landed);assertTrue(low<-15&&low>-60);assertEquals(.5F,DustSpiritMotion.pitch(0));assertEquals(1F,DustSpiritMotion.pitch(1200));assertEquals(2F,DustSpiritMotion.pitch(2400));
    }
    @Test void sourceInertiaCapAndPersistentSlipperiness(){
        var a=new DustSpiritMotion();a.charge();var r=RandomSource.create(52);
        for(int i=0;i<1000;i++){a.accelerate(10,5,0,r);assertTrue(Math.abs(a.x())<=5&&Math.abs(a.y())<=5);}
        double x=a.x();a.repath();a.decay(false);assertEquals(x*.9,a.x(),1e-8);
        assertTrue(a.x()>0&&a.y()<0,"Charge should approach positive X/Z, source Y is inverted");
    }
    @Test void fifoPathTiesAndSourceBudget(){
        var start=new SourceTilePath.Tile(0,0);var path=SourceTilePath.find(start,start,350,t->Math.abs(t.x())+Math.abs(t.z())==2,t->true);
        assertEquals(List.of(start,new SourceTilePath.Tile(-1,0),new SourceTilePath.Tile(-2,0)),path);
        assertTrue(SourceTilePath.find(start,start,1,t->Math.abs(t.x())==2,t->true).isEmpty());
        assertTrue(SourceTilePath.find(start,new SourceTilePath.Tile(1,0),350,t->t.x()==1,t->false).isEmpty());
    }
}
