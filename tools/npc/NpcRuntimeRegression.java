package com.stardew.craft.npc.runtime;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.stardew.craft.npc.data.*;
import java.nio.file.*;
import java.util.*;

/** Deterministic regression tests against production code and shipped, repository-owned data. */
public final class NpcRuntimeRegression {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        Path resources=Path.of(args[0]);
        verifyUnimplementedSocialNpcs(resources);
        var compiled=NpcScheduleCompiler.compile(json("""
                {"day":{"1100":"3 4 2 next","600":"Town @first 0 idle"}}
                """));
        check(compiled.get("day").getFirst().time()==600,"Chronological compilation");
        check(compiled.get("day").getLast().location().equals("Town"),"Inherited location must use chronological order");
        rejects(()->NpcScheduleCompiler.compile(json("{\"a\":{\"_goto\":\"B\"},\"b\":{\"_goto\":\"A\"}}")),"Case-insensitive GOTO cycle");
        rejects(()->NpcScheduleCompiler.compile(json("{\"a\":{\"_goto\":\"missing\"}}")),"Dangling GOTO");
        rejects(()->NpcScheduleCompiler.compile(json("{\"day\":{\"1260\":\"Town @first 0\"}}")),"Invalid minutes");
        check(NpcScheduleCompiler.compile(json("{\"a\":{\"600\":{\"location\":\"Town\",\"point\":\"x\",\"behavior\":\"addon:read\"}}}"))
                .get("a").getFirst().behavior().equals("addon:read"),"Structured namespaced behavior");
        check(new NpcScheduleCursor().select(List.of(800,1200),600,false)==-1,"Future departure is not a morning target");
        rejects(()->NpcScheduleCompiler.compile(json("{\"_point_replacements\":[{\"from\":\"chair\"}],\"spring\":{\"600\":\"Town @chair 2\"}}")),"Incomplete point replacement");
        var cursor=new NpcScheduleCursor(); var times=List.of(600,900,1200,1800);
        check(cursor.select(times,600,false)==0,"First checkpoint");
        check(cursor.select(times,1300,false)==0,"Due tasks cannot steal an in-flight route");
        check(cursor.select(times,1300,true)==1,"First queued destination");
        check(cursor.select(times,1300,false)==1,"Second route remains in flight");
        check(cursor.select(times,1300,true)==2,"Second queued destination");
        check(new NpcScheduleCursor().select(times,1300,false)==2,"Initial late observation catches up");
        check(cursor.select(times,600,false)==0,"Clock reversal resets the cursor");
        var lease=new NpcControlState(); var entity=UUID.randomUUID();
        long ordinary=lease.claim("schedule",0,10,2,entity);
        long activity=lease.claim("activity",20,10,2,entity);
        check(!lease.owns("schedule",ordinary,10),"Preemption revokes old generation");
        check(lease.claim("schedule",0,11,2,entity)<0,"Low priority cannot steal");
        check(lease.claim("competitor",20,11,2,entity)<0,"Equal priority cannot steal");
        long renewed=lease.claim("activity",20,12,2,entity);
        check(renewed!=activity && !lease.owns("activity",activity,12),"Expired work cannot be resurrected by renewal");
        check(lease.claim("activity",20,13,2,UUID.randomUUID())!=renewed,"Entity replacement revokes old generation");
        var budget=new NpcNavigationBudget();
        check(budget.acquire("a",0,1),"First budget grant");
        check(!budget.acquire("b",0,1),"Per-tick cap");
        check(!budget.acquire("a",1,1),"Existing waiter goes before recurring actor");
        check(budget.acquire("b",1,1),"FIFO grant");
        check(budget.acquire("c",100,1),"Expired waiters do not block the queue");
        var abandoned=new NpcNavigationBudget();
        abandoned.acquire("first",0,1); abandoned.acquire("gone",0,1);
        abandoned.acquire("live",1,4);
        check(abandoned.acquire("live",2,4),"An abandoned head cannot waste forty ticks of available searches");
        var policy=NpcNavigationPolicy.decode(null);
        check(!policy.arrived(0,1,0,.5),"Wrong floor is not arrival");
        check(!policy.arrived(.8,0,0,.5),"Portal approach cannot skip two blocks");
        check(policy.arrived(.1,.1,.1,.5),"Valid 3D arrival");
        check(policy.retryDelay(12)<=policy.maxRetryTicks(),"Bounded exponential backoff");
        rejects(()->NpcNavigationPolicy.decode(json("{\"navigation\":{\"searches_per_tick\":1.5}}")),"Fractional search budget");
        var state=new NpcRuntimeState("addon:actor");
        state.setNamedPointId("addon:chair"); state.setRouteBehaviorToken("addon:read");
        state.rememberPosition(new NpcRuntimeState.ActualPosition("minecraft:overworld",1,65,3,40,9));
        var restored=NpcRuntimeState.fromNbt(state.toNbt());
        check(restored.actualPosition().equals(state.actualPosition()),"Actual position save round trip");
        check(restored.namedPointId().equals("addon:chair"),"Namespaced desired state survives save");
        check(NpcRuntimeState.fromNbt(new net.minecraft.nbt.CompoundTag()).actualPosition()==null,"Legacy saves have no fabricated actual position");
        check(NpcQuestionAuthority.parse("$q 1 ask#Hi#$r 1 20 yes#Yes#$r 1 -5 no#No").size()==2,"Distinct choices may share a SDV answer ID");
        Map<String,JsonObject> events=new LinkedHashMap<>();
        try(var files=Files.list(resources.resolve("events"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                var data=json(Files.readString(file));
                events.put(data.has("event_id")?data.get("event_id").getAsString():file.getFileName().toString().replace(".json",""),data);
            }
        }
        var motion = NpcMotionProfile.compile(events);
        check(motion.get("robin").stepHeight()==1, "Robin can step over town terrain without jumps");
        check(motion.get("george").stepHeight()==.6, "Wheelchair retains separate step limits");
        NpcDataRegistry.replaceEvents(events);
        NpcRoutePoints.compile(events);
        NpcRouteDataValidation.validate(events);
        routeAllocationSample();
        rejects(()->NpcRouteDataValidation.validate(Map.of("location_graph",json("{\"edges\":[{\"from\":{},\"to\":\"town\"}]}"))),"Malformed graph rejected before publication");
        rejects(()->NpcRouteDataValidation.validate(Map.of("npc_route_profiles",json("{\"profiles\":{\"sam\":{\"town\":[{\"point\":\"a\",\"mode\":\"fly\"}]}}}"))),"Unknown route mode rejected before publication");
        check(NpcRoutePoints.compile(Map.of("example:npc_route_points",json("{\"points\":{\"bench\":{\"furniture\":\"example:bench\"}}}"))).size()==1,"Addon support kinds remain extensible");
        var mutableCopy=NpcDataRegistry.events().get("npc_runtime");mutableCopy.addProperty("audit_mutation",true);
        check(!NpcDataRegistry.events().get("npc_runtime").has("audit_mutation"),"Snapshot getters cannot mutate published definitions");
        rejects(()->NpcRoutePoints.compile(Map.of("npc_route_points",json("{\"points\":{\"bad\":{\"x\":0,\"y\":64,\"z\":0,\"origin_offset\":[0,0]}}}"))),"Bad support offset rejected before runtime");
        rejects(()->NpcRoutePoints.compile(Map.of("npc_route_points",json("{\"points\":{\"bad\":{\"x\":\"NaN\",\"y\":64,\"z\":0}}}"))),"Non-finite point rejected before publication");
        var namespaced=new LinkedHashMap<>(events);
        namespaced.put("example:npc_route_points",json("{\"points\":{\"chair\":{\"x\":0,\"y\":64,\"z\":0,\"furniture\":\"chair\"}}}"));
        NpcDataRegistry.replaceEvents(namespaced);
        check(NpcSupportTarget.point("example:chair")!=null && NpcSupportTarget.point("chair")==null,"Support lookup preserves the declaring namespace");
        NpcDataRegistry.replaceEvents(events);
        check(NpcTravelStatus.decode(null).action().equals("wait"),"Blocked tasks do not silently teleport or skip by default");
        rejects(()->NpcTravelStatus.decode(json("{\"navigation\":{\"blocked_task_policy\":\"teleport\"}}")),"Unknown recovery policy rejected");
        NpcTravelStatus.observe("repair:timer","task","path_unavailable",0);
        NpcTravelStatus.observe("repair:timer","task","no_progress",100);
        check(NpcTravelStatus.get("repair:timer").since()==0,"Changed blocked reason does not restart the recovery timeout");
        NpcTravelStatus.clear("repair:timer");
        var recovery=new NpcScheduleCursor();recovery.select(times,600,false);recovery.skipToDue(times,1300);
        check(recovery.select(times,1300,false)==2,"Explicit recovery advances only to an already due task");
        long serverRevision=NpcDataRegistry.revision();
        NpcDataRegistry.applyEventsFromJson("{\"client_only\":{\"value\":1}}");
        check(NpcDataRegistry.revision()==serverRevision && !NpcDataRegistry.events().containsKey("client_only"),"Client sync cannot replace integrated-server definitions");
        check(NpcDataRegistry.clientEvents().containsKey("client_only"),"Client receives its own event snapshot");
        NpcDataRegistry.clearClientEvents();
        check(NpcDataRegistry.clientEvents().isEmpty(),"Client disconnect clears the previous server's data");
        check(NpcActivityCatalog.compile(events).size()>=7,"All shipped activities compile");
        NpcMotionProfile.compile(events);
        int documents=0,nodes=0;
        try(var files=Files.list(resources.resolve("schedules"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                try {
                    var source=json(Files.readString(file));
                    var days=NpcScheduleCompiler.compile(source);
                    for(var day:days.entrySet()) {
                        var entries=source.getAsJsonObject(day.getKey()).entrySet().stream()
                                .filter(e->!e.getKey().startsWith("_")).toList();
                        check(day.getValue().size()==entries.size(),file.getFileName()+"/"+day.getKey()+": every authored stop compiled");
                        verifyDay(day.getValue(),file.getFileName()+"/"+day.getKey());
                        nodes+=day.getValue().size();
                    }
                }
                catch(RuntimeException error) { throw new AssertionError(file+": "+error.getMessage(),error); }
                documents++;
            }
        }
        check(documents>=34 && nodes>0,"Formal schedule catalog is present");
        var sam=json(Files.readString(resources.resolve("schedules/sam.json")));
        for (String season:List.of("spring","summer","fall","winter")) for(int day=1;day<=28;day++)
            for(String weather:List.of("sun","rain","storm","greenrain")) for(int hearts:List.of(0,6)) {
                var context=new NpcScheduleRules.Context(season,day,1,weather,123,day,id->hearts,id->false);
                check(NpcScheduleRules.select(sam,context).equals(SamSchedulePolicy.select(season,day,1,weather,hearts,hearts,123,day)),
                        "Legacy content selection preserved "+season+"/"+day+"/"+weather);
            }
        System.out.println("NPC runtime regression: "+assertions+" assertions; "+documents+" schedules; "+nodes+" nodes.");
    }
    private static void verifyUnimplementedSocialNpcs(Path resources) throws Exception {
        Map<String,NpcCapabilityProfile> profiles=new LinkedHashMap<>();
        for(var entry:json(Files.readString(resources.resolve("capabilities/base_profiles.json"))).getAsJsonArray("npcs")) {
            var npc=entry.getAsJsonObject();
            String id=npc.get("id").getAsString();
            profiles.put(id,new NpcCapabilityProfile(id,npc.get("implemented").getAsBoolean(),
                    npc.get("pathing_enabled").getAsBoolean(),npc.get("animation_profile").getAsString(),
                    npc.get("age").getAsInt(),npc.get("manners").getAsInt(),npc.get("social_anxiety").getAsInt(),
                    npc.get("optimism").getAsInt(),npc.get("gender").getAsInt(),npc.get("datable").getAsBoolean()));
        }
        for(String id:List.of("kent","leo")) {
            var profile=profiles.get(id);
            check(profile!=null && !profile.implemented() && !profile.pathingEnabled(),id+": unfinished NPC is disabled");
            check(!profile.canRunPathing(),id+": unfinished NPC cannot enter the movement roster");
            check(!NpcSocialRules.canSocialize(id) && !NpcSocialRules.canSocialize(id,null),id+": unfinished NPC cannot socialize");
            check(!NpcSocialRules.shouldCreateFriendshipForSocialPage(id),id+": overview cannot create a friendship record");
            check(!NpcSocialRules.shouldShowOnSocialPage(id,profile,null,null),id+": absent from a new player's social page");
            var existing=new NpcFriendshipDataManager.FriendshipState();
            existing.addPoints(1500,3500);
            check(!NpcSocialRules.shouldShowOnSocialPage(id,profile,existing,null),id+": existing friendship cannot restore the social row");
            check(!NpcSocialRules.isIntroductionsNpc(id,profile),id+": excluded from introductions");
        }
        for(String id:List.of("lewis","robin","abigail")) {
            check(NpcSocialRules.canSocialize(id),id+": implemented social NPC remains available");
            check(NpcSocialRules.shouldShowOnSocialPage(id,profiles.get(id),null,null),id+": existing social visibility is preserved");
        }
        check(NpcSocialRules.shouldCreateFriendshipForSocialPage("lewis")
                && NpcSocialRules.shouldCreateFriendshipForSocialPage("robin"),"Existing introductory friendships are preserved");
    }
    private static void verifyDay(List<NpcScheduleCompiler.Node> nodes,String label) {
        if(nodes.isEmpty()) return;
        var times=nodes.stream().map(NpcScheduleCompiler.Node::time).toList();
        var queue=new NpcScheduleCursor();
        check(queue.select(times,times.getFirst()-1,false)==-1,label+": no future departure");
        check(queue.select(times,times.getFirst(),false)==0,label+": first departure");
        // Even when a whole day's travel is late, ordinary ticking cannot silently skip stops.
        for(int i=0;i<nodes.size();i++) {
            var node=nodes.get(i);
            check(node.index()==i && (i==0 || times.get(i)>times.get(i-1)),label+": chronological unique checkpoints");
            if(!node.point().isBlank()) check(NpcRoutePoints.get(node.point())!=null,label+": unresolved @"+node.point());
            check(queue.select(times,2959,false)==i,label+": in-flight destination retained "+i);
            check(queue.select(times,2959,true)==Math.min(i+1,nodes.size()-1),label+": only one due stop advanced "+i);
        }
        // Check departure boundaries and late recovery independently of the live queue.
        for(int i=0;i<times.size();i++) {
            int clock=times.get(i);
            check(new NpcScheduleCursor().select(times,clock,false)==i,label+": fresh recovery at "+clock);
            check(new NpcScheduleCursor().select(times,clock-1,false)==i-1,label+": recovery before "+clock);
        }
        check(queue.select(times,times.getFirst()-1,false)==-1,label+": backward clock clears yesterday's stop");
        check(queue.select(times,times.getFirst(),false)==0,label+": morning restarts at first stop");
    }
    private static void routeAllocationSample() throws Exception {
        var method=NpcRoutePlanner.class.getDeclaredMethod("resolveProfileRoute",net.minecraft.server.level.ServerLevel.class,String.class,NpcRuntimeState.class);
        method.setAccessible(true);
        var state=new NpcRuntimeState("bench:no_profile");
        for(int i=0;i<300;i++)method.invoke(null,null,"bench:no_profile",state);
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId(),bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
        for(int i=0;i<2000;i++)method.invoke(null,null,"bench:no_profile",state);
        long allocated=bean.getThreadAllocatedBytes(thread)-bytes;
        System.out.printf(java.util.Locale.ROOT,"NPC profile lookup sample: %.1f bytes/call, %.3f ms/call (2000 warm lookups)%n",allocated/2000.0,(System.nanoTime()-start)/2_000_000_000.0);
        check(allocated/2000<4096,"Warm profile lookup must not copy the whole catalog per actor/tick");
        var oldEvents=NpcDataRegistry.events();
        try {
            var replacement=new LinkedHashMap<>(oldEvents);
            replacement.put("npc_route_profiles",json("{\"profiles\":{\"bench:no_profile\":{\"town\":[{\"point\":\"bench:point\"}]}}}"));
            replacement.put("bench:npc_route_points",json("{\"points\":{\"point\":{\"x\":7,\"y\":64,\"z\":9}}}"));
            NpcDataRegistry.replaceEvents(replacement);
            var route=(NpcRoutePlanner.NpcRouteContext)method.invoke(null,null,"bench:no_profile",state);
            check(route!=null && route.ready() && route.destinationSteps.getFirst().target.x==7.5,"Profile cache observes resource reload immediately");
        } finally {NpcDataRegistry.replaceEvents(oldEvents);}

    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
    private static void rejects(Runnable test,String message) {
        try { test.run(); } catch(IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(message+" was accepted");
    }
}
