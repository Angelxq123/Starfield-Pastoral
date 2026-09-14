package com.stardew.craft.monster;
import com.google.gson.Gson;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.entity.projectile.ShamanCurseEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
class ShamanCurseTest {
    @Test void wavyPhaseUsesTheSourcesMillisecondComponent(){
        var velocity=new Vec3(45./64,0,0);var zero=ShamanCurseEntity.sourceStep(velocity,0,0);assertEquals(15./64,zero.x,1e-12);assertEquals(8./64,zero.z,1e-12);
        for(int sub=0;sub<3;sub++)assertEquals(ShamanCurseEntity.sourceStep(velocity,0,sub),ShamanCurseEntity.sourceStep(velocity,20,sub));
        var step=ShamanCurseEntity.sourceStep(velocity,0,1);assertEquals(15./64+Math.sin(16*Math.PI/128)/8,step.x,1e-12);assertEquals(Math.cos(16*Math.PI/128)/8,step.z,1e-12);
    }
    @Test void spatialWaveFollowsElevationAndPreservesSourceWaveMagnitude(){
        for(var velocity:java.util.List.of(new Vec3(0,45./64,0),new Vec3(0,-45./64,0),new Vec3(.3,.4,.5))){
            for(int sub=0;sub<3;sub++){
                var wave=ShamanCurseEntity.spatialStep(velocity,7,sub).subtract(velocity.scale(1./3));
                assertEquals(.125,wave.length(),1e-10);
                assertEquals(ShamanCurseEntity.spatialStep(velocity,7,sub),ShamanCurseEntity.spatialStep(velocity,27,sub));
            }
        }
    }
    @Test void coreAndReverseHullHaveEveryPhysicalEndcap(){
        var m=new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/stardewcraft/monster_native/shaman_curse.json"))),NativeNpcModel.class);
        for(var name:java.util.List.of("core","outline")){
            var qs=m.quads().stream().filter(q->q.sourcePart().equals(name)).toList();assertEquals(6,qs.size());
            for(var q:qs){double dot=0;for(int i=0;i<3;i++){double c=0;for(var v:q.vertices())c+=v[i]/4;dot+=c*q.normal()[i];}assertTrue(name.equals("core")?dot>0:dot<0,"Incorrect signed physical face for "+name);}
        }
    }
}
