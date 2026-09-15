package com.stardew.craft.npc.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.client.npcnative.NativeWalkClock;
import com.stardew.craft.client.npcnative.NativeWheelchairClock;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NpcTravelPaceTest {
    private static final double SCALE=NpcMotionProfile.TRAVEL_SPEED_MULTIPLIER;

    @Test void shippedProfilesKeepTheirRelativePaceAndMissingSpeedGetsTheSameBoost() throws Exception {
        var source=resource("data/stardewcraft/npc/events/npc_runtime.json");
        var profiles=NpcMotionProfile.compile(Map.of("npc_runtime",source));
        assertEquals(source.getAsJsonObject("actors").size(),profiles.size());
        for(var entry:profiles.entrySet()) {
            var authored=source.getAsJsonObject("actors").getAsJsonObject(entry.getKey());
            double original=authored.has("speed")?authored.get("speed").getAsDouble():.2;
            assertEquals(original,entry.getValue().speed(),1e-10,entry.getKey());
            assertEquals(original*1.5,entry.getValue().travelSpeed(),1e-10,entry.getKey());
        }
        assertTrue(profiles.get("dwarf").travelSpeed()<profiles.get("sam").travelSpeed());
        assertTrue(profiles.get("jas").travelSpeed()<profiles.get("vincent").travelSpeed());
    }

    @Test void allShippedWalkingCharactersTravelFartherWithoutChangingCadenceAtDifferentFrameRates() throws Exception {
        var actors=resource("data/stardewcraft/npc/events/npc_runtime.json").getAsJsonObject("actors");
        int checked=0;
        for(String actor:actors.keySet()) {
            if(actor.equals("george"))continue;
            double stride=resource("assets/stardewcraft/npc_native/"+actor+".json")
                    .getAsJsonObject("profile").get("walkStride").getAsDouble();
            for(int fps:new int[]{15,30,60,144}) {
                var original=new NativeWalkClock(stride);
                var faster=new NativeWalkClock(stride*SCALE);
                double distance=0;
                for(int frame=0;frame<=fps*4;frame++) {
                    double time=frame/(double)fps;
                    // Start, cruise, a slow approach, stop, and restart.
                    if(frame>0 && time>.25 && time<2.5)distance+=(time>2?.15:.75)/fps;
                    if(time>3)distance+=.75/fps;
                    var before=original.sample(time,distance,0,true);
                    var after=faster.sample(time,distance*SCALE,0,true);
                    assertEquals(before.phase(),after.phase(),1e-9,actor+" fps="+fps);
                    assertEquals(before.weight(),after.weight(),1e-9,actor+" blend");
                }
            }
            checked++;
        }
        assertTrue(checked>=30,"Exercise the shipped cast, not just a single generic stride");
    }

    @Test void stoppedAirborneRepeatedPassAndTeleportStillDoNotAdvanceTheWalk() {
        var clock=new NativeWalkClock(.5*SCALE);
        clock.sample(0,0,0,true);
        var walking=clock.sample(.05,.05*SCALE,0,true);
        assertEquals(walking,clock.sample(.05,.05*SCALE,0,true));
        var stopped=walking;
        for(int i=2;i<=10;i++)stopped=clock.sample(i*.05,.05*SCALE,0,true);
        assertEquals(walking.phase(),stopped.phase());
        assertEquals(0,stopped.weight());
        var airborne=clock.sample(.55,.1*SCALE,0,false);
        assertEquals(stopped.phase(),airborne.phase());
        assertEquals(0,airborne.weight());
        var warped=clock.sample(.6,20,0,true);
        assertEquals(.3,warped.phase());
        assertEquals(0,warped.weight());
    }

    @Test void wheelchairPushCadenceStaysTheSameWhileWheelsRollTheFullDistance() {
        for(int direction:new int[]{-1,1}) {
            var original=new NativeWheelchairClock();
            var faster=new NativeWheelchairClock(SCALE);
            for(int frame=0;frame<=60;frame++) {
                double time=frame/60.,z=direction*.5*time;
                var before=original.sample(time,0,z,0,true);
                var after=faster.sample(time,0,z*SCALE,0,true);
                assertEquals(before.right().phase(),after.right().phase(),1e-9);
                assertEquals(before.left().phase(),after.left().phase(),1e-9);
                assertEquals(before.weight(),after.weight(),1e-9);
                assertEquals(before.right().angle()*SCALE,after.right().angle(),1e-9);
                assertEquals(before.left().angle()*SCALE,after.left().angle(),1e-9);
                assertEquals(before.casterSpinRight()*SCALE,after.casterSpinRight(),1e-9);
                assertEquals(before.casterSpinLeft()*SCALE,after.casterSpinLeft(),1e-9);
            }
        }
    }

    @Test void wheelchairStationaryTurnsKeepTheirOriginalMotionAndSettleAfterStopping() {
        for(int direction:new int[]{-1,1}) {
            var original=new NativeWheelchairClock();
            var faster=new NativeWheelchairClock(SCALE);
            NativeWheelchairClock.Sample sample=null;
            for(int frame=0;frame<=180;frame++) {
                double time=frame/60.,yaw=direction*Math.min(90,90*time);
                var before=original.sample(time,0,0,yaw,true);
                sample=faster.sample(time,0,0,yaw,true);
                assertEquals(before,sample);
            }
            assertEquals(0,sample.weight());
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try(var input=NpcTravelPaceTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input,path);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
