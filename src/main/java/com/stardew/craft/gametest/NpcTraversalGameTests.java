package com.stardew.craft.gametest;

import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.NpcCentralMovementService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("stardewcraft_npc_runtime")
@PrefixGameTestTemplate(false)
public final class NpcTraversalGameTests {
    private static final class Npc extends StardewNpcEntity {
        int jumps;
        Npc(GameTestHelper h) {
            super(ModEntities.STARDEW_NPC.get(), h.getLevel()); setNpcId("sam");
            jumpControl = new JumpControl(this) { @Override public void jump() { jumps++; super.jump(); } };
        }
    }
    private static BlockPos floor(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(2,2,2));
        for(int x=-1;x<=10;x++) for(int z=-1;z<=7;z++) {
            h.getLevel().setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<5;y++) h.getLevel().setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        return base;
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void closedDoorContainingLandingOpensWithoutExistingPath(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);var level=h.getLevel();var door=base.offset(2,0,2);
        var state=Blocks.OAK_DOOR.defaultBlockState();
        level.setBlock(door,state,2);level.setBlock(door.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        var box=state.getCollisionShape(level,door).bounds().move(door);
        npc.setPos((box.minX+box.maxX)/2,door.getY(),(box.minZ+box.maxZ)/2);npc.setOnGround(true);
        h.assertTrue(npc.getNavigation().getPath()==null,"Fixture should have no path yet");
        var plan=plan(npc,new String[]{"walk"},Vec3.atBottomCenterOf(base.offset(6,0,2)));
        var method=NpcCentralMovementService.class.getDeclaredMethod("tryOpenPathDoors",net.minecraft.server.level.ServerLevel.class,
                StardewNpcEntity.class,plan.getClass());method.setAccessible(true);method.invoke(null,level,npc,plan);
        h.assertTrue(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                "A door containing the landing prevents the first path from being created");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=600)
    public static void repeatedWalkWarpWalkFinishesAtWorkpointAndReturns(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var level=h.getLevel();var npc=new Npc(h);npc.setNpcId("caroline");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        var home=Vec3.atBottomCenterOf(base.offset(0,0,1));
        var entrance=Vec3.atBottomCenterOf(base.offset(3,0,1));
        var landing=Vec3.atBottomCenterOf(base.offset(6,0,5));
        var work=Vec3.atBottomCenterOf(base.offset(9,0,5));
        npc.setPos(home);npc.setOnGround(true);
        Object[] active={plan(npc,new String[]{"walk","warp","walk"},entrance,landing,work)};
        int[] completed={0};
        var stepIndex=active[0].getClass().getDeclaredField("currentStepIndex");stepIndex.setAccessible(true);
        h.onEachTick(()->{
            try {
                level.tickNonPassenger(npc);execute(h,npc,active[0]);
                if(stepIndex.getInt(active[0])>=3) {
                    var expected=completed[0]%2==0?work:home;
                    h.assertTrue(npc.position().distanceToSqr(expected)<.3,"Portal entry was mistaken for the final workpoint");
                    completed[0]++;
                    if(completed[0]==4) {
                        com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                        h.succeed();return;
                    }
                    active[0]=completed[0]%2==0
                            ?plan(npc,new String[]{"walk","warp","walk"},entrance,landing,work)
                            :plan(npc,new String[]{"walk","warp","walk"},landing,entrance,home);
                }
                if(npc.tickCount>=590) {
                    com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                    h.fail("Repeated portal trip stalled at leg "+completed[0]+", step "+stepIndex.getInt(active[0]));
                }
            } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=400)
    public static void blockedLiveRouteEscapesAroundNewWall(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var level=h.getLevel();var npc=new Npc(h);npc.setNpcId("gus");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(0,0,3)));npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(base.offset(8,0,3));
        var plan=plan(npc,new String[]{"walk"},target);
        boolean[] blocked={false},detoured={false};
        h.onEachTick(() -> {
            level.tickNonPassenger(npc);
            if(!blocked[0] && npc.getX()>base.getX()+1.5) {
                // Change the physical route after navigation has chosen its path.
                for(int z=-1;z<=4;z++) for(int y=0;y<3;y++)
                    level.setBlock(base.offset(4,y,z),Blocks.STONE.defaultBlockState(),2);
                blocked[0]=true;
            }
            try {execute(h,npc,plan);} catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
            detoured[0] |= npc.getZ()>base.getZ()+5;
            h.assertTrue(npc.jumps==0 && Math.abs(npc.getY()-base.getY())<.1,"Recovery jumped or teleported onto the wall");
            if(npc.position().distanceToSqr(target)<.25) {
                h.assertTrue(blocked[0] && detoured[0],"NPC did not use the available exit around the obstruction");
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                h.succeed();
            }
            if(npc.tickCount>=390) h.fail("Live blocked route never recovered: "+npc.position());
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=240)
    public static void recoveryCostExpiresAndDoesNotBlockOnlyExit(GameTestHelper h) {
        var base=floor(h);var npc=new Npc(h);var level=h.getLevel();
        npc.setPos(Vec3.atBottomCenterOf(base.offset(1,0,3)));npc.setOnGround(true);
        var target=base.offset(8,0,3);
        var nav=(com.stardew.craft.entity.npc.NpcPathNavigation)npc.getNavigation();
        var original=nav.createPath(target,0);nav.moveTo(original,1);
        int index=1;
        while(index<original.getNodeCount()-1 && original.getNode(index).x<base.getX()+3) index++;
        original.setNextNodeIndex(index);var failed=original.getNextNodePos();nav.prepareRecovery();
        var other=new Npc(h);other.setPos(npc.position());other.setOnGround(true);
        var otherPath=other.getNavigation().createPath(target,0);
        h.assertTrue(java.util.stream.IntStream.range(0,otherPath.getNodeCount()).anyMatch(i->otherPath.getNodePos(i).equals(failed)),
                "One NPC's failure affected another NPC's route");
        for(int x=-1;x<=10;x++) for(int z:new int[]{2,4}) for(int y=0;y<3;y++)
            level.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        var onlyExit=nav.createPath(target,0);
        h.assertTrue(onlyExit!=null && onlyExit.canReach(),"Finite failure cost blocked the only exit");
        for(int x=-1;x<=10;x++) for(int z:new int[]{2,4}) for(int y=0;y<3;y++)
            level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        h.runAfterDelay(210,()->{
            nav.stop();var expired=nav.createPath(target,0);
            h.assertTrue(expired!=null && java.util.stream.IntStream.range(0,expired.getNodeCount()).anyMatch(i->expired.getNodePos(i).equals(failed)),
                    "Temporary failure cost never expired");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=200)
    public static void narrowRightAngleKeepsBodyClearOfInnerCorner(GameTestHelper h) {
        var base = floor(h);
        var npc = new Npc(h);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        for (int x=0;x<=6;x++) for (int z=0;z<=6;z++) {
            boolean corridor = z==2 && x<=3 || x==3 && z>=2;
            if (!corridor) for (int y=0;y<3;y++)
                h.getLevel().setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        }
        npc.setPos(Vec3.atBottomCenterOf(base.offset(1,0,2)));
        npc.setOnGround(true);
        var nodes = new java.util.ArrayList<net.minecraft.world.level.pathfinder.Node>();
        for (int x=2;x<=3;x++) nodes.add(new net.minecraft.world.level.pathfinder.Node(base.getX()+x,base.getY(),base.getZ()+2));
        for (int z=3;z<=5;z++) nodes.add(new net.minecraft.world.level.pathfinder.Node(base.getX()+3,base.getY(),base.getZ()+z));
        var target = Vec3.atBottomCenterOf(base.offset(3,0,5));
        npc.getNavigation().moveTo(new net.minecraft.world.level.pathfinder.Path(nodes,BlockPos.containing(target),true),1);
        h.onEachTick(() -> {
            h.getLevel().tickNonPassenger(npc);
            h.assertTrue(npc.jumps==0 && Math.abs(npc.getY()-base.getY())<.05,"Corner route climbed or jumped");
            if(npc.position().distanceToSqr(target)<.25) h.succeed();
            if(npc.tickCount>=190) h.fail("NPC cut the inner corner and stayed trapped: "+npc.position());
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities")
    public static void stuckRecoveryReplacesCachedPathAndPenalizesFailedNode(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(1,0,3)));npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(base.offset(8,0,3));
        var nav=npc.getNavigation();
        h.assertTrue(nav.moveTo(target.x,target.y,target.z,1),"Initial path missing");
        var original=nav.getPath();
        int failedIndex=1;
        while(failedIndex<original.getNodeCount()-1 && original.getNode(failedIndex).x<base.getX()+3) failedIndex++;
        original.setNextNodeIndex(failedIndex);
        var failed=original.getNextNodePos();
        var plan=plan(npc,new String[]{"walk"},target);
        var checks=plan.getClass().getDeclaredField("stuckCheckCount");checks.setAccessible(true);checks.setInt(plan,4);
        // Recovery shares the per-tick budget with every other test actor too.
        h.succeedWhen(() -> {
            try {execute(h,npc,plan);} catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
            var replacement=nav.getPath();
            h.assertTrue(replacement!=null && replacement!=original,"Stuck recovery reused the cached failed path");
            for(int i=0;i<replacement.getNodeCount();i++) h.assertTrue(!replacement.getNodePos(i).equals(failed),
                    "Replan did not avoid the failed node despite an open alternative");
            com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(h.getLevel(),npc.getNpcId());
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=130)
    public static void localCirclingDoesNotCountAsRouteProgress(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);
        var centre=Vec3.atBottomCenterOf(base.offset(2,0,3));
        npc.setPos(centre);npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(base.offset(9,0,3));
        var plan=plan(npc,new String[]{"walk"},target);
        var reason=plan.getClass().getDeclaredField("debugRepathReason");reason.setAccessible(true);
        int[] ticks={0};
        h.onEachTick(() -> {
            // Displacement in each old 20-tick sample exceeds its 0.2-block threshold,
            // but the actor never leaves this small circle.
            int t=++ticks[0];npc.setPos(centre.add(.45*Math.cos(t*.12),0,.45*Math.sin(t*.12)));
            try {
                execute(h,npc,plan);
                if("local_loop_repath".equals(reason.get(plan))) {
                    com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(h.getLevel(),npc.getNpcId());
                    h.succeed();
                }
            } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
            if(t>=120) h.fail("Circling indefinitely was counted as progress");
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void loadedNpcQueryPreservesSubclassAndRemoval(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var npc = new Npc(h);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(floor(h)));
        var cacheField = com.stardew.craft.npc.runtime.NpcSpawnManager.class.getDeclaredField("ENTITY_SCAN_CACHES");
        cacheField.setAccessible(true);
        var caches = (java.util.Map<?, ?>) cacheField.get(null);
        var query = com.stardew.craft.npc.runtime.NpcSpawnManager.class.getDeclaredMethod("getCachedAllNpcs", net.minecraft.server.level.ServerLevel.class);
        query.setAccessible(true);
        try {
            h.assertTrue(level.addFreshEntity(npc), "Loaded NPC fixture was rejected");
            // Compare the old spatial query once; timings are diagnostic, not a flaky CI limit.
            long started = System.nanoTime();
            var spatial = level.getEntitiesOfClass(StardewNpcEntity.class,
                    new net.minecraft.world.phys.AABB(-3.0E7,-2048,-3.0E7,3.0E7,4096,3.0E7));
            long spatialNanos = System.nanoTime()-started;
            caches.remove(level);
            started = System.nanoTime();
            var loaded = (List<?>) query.invoke(null, level);
            long loadedNanos = System.nanoTime()-started;
            h.assertTrue(loaded.contains(npc), "Loaded scan lost an NPC subclass");
            h.assertTrue(new java.util.HashSet<>(loaded).equals(new java.util.HashSet<>(spatial)),
                    "Loaded scan changed accessible NPC membership");
            npc.discard();
            caches.remove(level);
            h.assertTrue(!((List<?>) query.invoke(null, level)).contains(npc), "Removed NPC retained on scan refresh");
            com.mojang.logging.LogUtils.getLogger().info("[NPC_SCAN_REGRESSION] spatialMs={} loadedMs={} count={}",
                    spatialNanos/1_000_000.0, loadedNanos/1_000_000.0, loaded.size());
        } finally {
            npc.discard();
            caches.remove(level);
        }
        h.succeed();
    }
    // Exercise the real route executor without changing production visibility for tests.
    private static Object plan(Npc npc, String[] modes, Vec3... targets) throws ReflectiveOperationException {
        var step=Class.forName("com.stardew.craft.npc.runtime.NpcRoutePlanner$NpcRouteStep");
        var steps=new java.util.ArrayList<>();
        for(int i=0;i<targets.length;i++) { var make=step.getDeclaredMethod(modes[i],String.class,Vec3.class);make.setAccessible(true);steps.add(make.invoke(null,"regression_"+i,targets[i])); }
        var type=Class.forName("com.stardew.craft.npc.runtime.NpcCentralMovementService$NpcRoutePlan");
        var constructor=type.getDeclaredConstructor(String.class,java.util.UUID.class,List.class,long.class);constructor.setAccessible(true);
        return constructor.newInstance("portal_regression",npc.getUUID(),steps,npc.level().getGameTime());
    }
    private static void execute(GameTestHelper h,Npc npc,Object plan) throws ReflectiveOperationException {
        var method=NpcCentralMovementService.class.getDeclaredMethod("executePlanTick",net.minecraft.server.level.ServerLevel.class,StardewNpcEntity.class,plan.getClass());method.setAccessible(true);method.invoke(null,h.getLevel(),npc,plan);
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void elevatedEntranceMarkerStillTransitions(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);var from=Vec3.atBottomCenterOf(base);var to=Vec3.atBottomCenterOf(base.offset(6,0,0));npc.setPos(from);npc.setOnGround(true);
        var plan=plan(npc,new String[]{"walk","warp"},from.add(0,1.5,0),to);
        npc.getMoveControl().setWantedPosition(from.x + 1, from.y, from.z, 1);
        npc.setDeltaMovement(.1, -.2, .1);
        execute(h,npc,plan);execute(h,npc,plan);
        npc.getMoveControl().tick();
        h.assertTrue(npc.getSpeed() == 0 && npc.getDeltaMovement().lengthSqr() == 0,
                "Portal retains movement toward the previous room");
        h.assertTrue(npc.position().distanceToSqr(to)<.01,"Elevated doorway marker prevents WARP progression");
        var reverse = plan(npc,new String[]{"walk","warp"},to.add(0,1.5,0),from);
        execute(h,npc,reverse);execute(h,npc,reverse);
        h.assertTrue(npc.position().distanceToSqr(from)<.01,"Return portal did not transition");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=160)
    public static void fishShopDoorwayFinishesApproachBeforeWarp(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);
        var level=h.getLevel();
        // Saved fish shop: two trigger cells in front of double doors, with a fence at the right.
        var doorway=base.offset(3,0,2);
        for(int x=-1;x<=0;x++) {
            var door=doorway.offset(x,0,-1);
            var state=Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoorBlock.FACING,net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HINGE,x==0
                            ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                            : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
            level.setBlock(door,state,2);
            level.setBlock(door.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
            for(int y=0;y<2;y++) level.setBlock(doorway.offset(x,y,0),com.stardew.craft.block.ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(),2);
            level.setBlock(doorway.offset(x,2,0),Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP),2);
        }
        for(int y=0;y<2;y++) {
            level.setBlock(doorway.offset(1,y,-1),Blocks.STRIPPED_OAK_WOOD.defaultBlockState(),2);
            level.setBlock(doorway.offset(1,y,0),Blocks.OAK_FENCE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FenceBlock.NORTH,true),2);
        }
        var npc=new Npc(h);npc.setNpcId("willy");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(doorway.getX()+.88,doorway.getY(),doorway.getZ()+1.56);npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(doorway);
        var landing=Vec3.atBottomCenterOf(base.offset(8,0,5));
        var plan=plan(npc,new String[]{"walk","warp"},target,landing);
        // A restored actor can have a transient false ground flag before its next physics tick.
        npc.setOnGround(false);
        // This is already within the entrance approach range in the shipped 0.5.6 runtime.
        // Navigation may finish here; do not require another path to the trigger's centre.
        execute(h,npc,plan);
        var index=plan.getClass().getDeclaredField("currentStepIndex");index.setAccessible(true);
        h.assertTrue(index.getInt(plan)==1,"Reached entrance does not hand off to WARP at Willy's saved position");
        h.assertTrue(!level.getBlockState(doorway.north()).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                "Portal approach opens a nearby door that the walking route never crosses");
        h.onEachTick(() -> {
            level.tickNonPassenger(npc);
            try {execute(h,npc,plan);} catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
            if(npc.position().distanceToSqr(landing)<.01) {
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                h.succeed();
            }
            if(npc.tickCount>=140) h.fail("Fish shop stalled: pos="+npc.position()+" ground="+npc.onGround()
                    +" nav="+npc.getNavigation().isDone()+" clear="+level.noCollision(npc,npc.getBoundingBox().expandTowards(target.subtract(npc.position()))));
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void authoredEntranceDoesNotRequireWalkingThroughItsMarker(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var level=h.getLevel();var npc=new Npc(h);
        var from=Vec3.atBottomCenterOf(base.offset(3,0,3));
        npc.setPos(from);npc.setOnGround(true);
        var landing=Vec3.atBottomCenterOf(base.offset(8,0,5));
        var index=Class.forName("com.stardew.craft.npc.runtime.NpcCentralMovementService$NpcRoutePlan")
                .getDeclaredField("currentStepIndex");index.setAccessible(true);
        for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var offset=new Vec3(direction.getStepX(),0,direction.getStepZ());
            var target=from.add(offset.scale(1.75));
            var near=plan(npc,new String[]{"walk","warp"},target,landing);
            execute(h,npc,near);
            h.assertTrue(index.getInt(near)==1,"Clear entrance approach failed toward "+direction);
            var obstacle=BlockPos.containing(from.add(offset));
            level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);
            level.setBlock(obstacle.above(),Blocks.STONE.defaultBlockState(),2);
            var blocked=plan(npc,new String[]{"walk","warp"},target,landing);
            execute(h,npc,blocked);
            h.assertTrue(index.getInt(blocked)==1,"Authored portal incorrectly requires walking through its marker toward "+direction);
            level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            level.setBlock(obstacle.above(),Blocks.AIR.defaultBlockState(),2);
            var exact=plan(npc,new String[]{"walk"},target);
            execute(h,npc,exact);
            h.assertTrue(index.getInt(exact)==0,"Work destination incorrectly uses entrance approach range");
        }
        com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void warpUsesExactAuthoredPositionBesidePartialBlocks(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);npc.setPos(Vec3.atBottomCenterOf(base));var dest=base.offset(6,0,0);
        h.getLevel().setBlockAndUpdate(dest,Blocks.STONE_SLAB.defaultBlockState());
        var plan=plan(npc,new String[]{"warp"},Vec3.atBottomCenterOf(dest));execute(h,npc,plan);
        h.assertTrue(npc.position().distanceToSqr(Vec3.atBottomCenterOf(dest))<.01,"WARP silently changed the authored landing height");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void warpDoesNotWaitForFloorSearch(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var npc=new Npc(h);var from=Vec3.atBottomCenterOf(base);npc.setPos(from);var dest=base.offset(6,0,0);
        for(int y=-1;y>=-4;y--) h.getLevel().setBlockAndUpdate(dest.offset(0,y,0),Blocks.AIR.defaultBlockState());
        execute(h,npc,plan(npc,new String[]{"warp"},Vec3.atBottomCenterOf(dest)));
        h.assertTrue(npc.position().distanceToSqr(Vec3.atBottomCenterOf(dest))<.01,"Authored WARP waits forever for a replacement floor");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities",timeoutTicks=400)
    public static void walkingPastDoorDoesNotOpenIt(GameTestHelper h) throws ReflectiveOperationException {
        var base=floor(h);var level=h.getLevel();var npc=new Npc(h);npc.setNpcId("pierre");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        var doorPos=base.offset(4,0,4);
        var state=Blocks.OAK_DOOR.defaultBlockState();
        level.setBlock(doorPos,state,2);
        level.setBlock(doorPos.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(0,0,3)));npc.setOnGround(true);
        var target=Vec3.atBottomCenterOf(base.offset(8,0,3));
        var plan=plan(npc,new String[]{"walk"},target);
        h.onEachTick(()->{
            level.tickNonPassenger(npc);
            try {execute(h,npc,plan);} catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
            h.assertTrue(!level.getBlockState(doorPos).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                    "NPC opened a door beside its walking route");
            if(npc.tickCount>=360) h.fail("Side-door route did not finish: "+npc.position()+" target="+target);
            if(npc.position().distanceToSqr(target)<.25) {
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                h.succeed();
            }
        });
    }
    @GameTest(templateNamespace="stardewcraft_npc_runtime",template="ring_utilities")
    public static void partialBlockEdgeDoesNotRequestJump(GameTestHelper h) {
        var base=floor(h);var npc=new Npc(h);
        // An open trapdoor occupies the edge, while the centre footprint remains clear.
        h.getLevel().setBlockAndUpdate(base,Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN,true));
        npc.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);npc.setOnGround(true);
        for(int i=0;i<15;i++){npc.getMoveControl().setWantedPosition(npc.getX()+1,npc.getY(),npc.getZ(),1);npc.getMoveControl().tick();}
        h.assertTrue(npc.jumps==0,"Flat movement beside a partial block repeatedly triggers vanilla jumps");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=220)
    public static void walksAroundFullBlockWithoutJumping(GameTestHelper h) {
        var base = floor(h);
        var npc = new Npc(h);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(0,0,3)));
        npc.setOnGround(true);
        h.getLevel().setBlockAndUpdate(base.offset(3,0,3), Blocks.STONE.defaultBlockState());
        var target = Vec3.atBottomCenterOf(base.offset(7,0,3));
        npc.setNpcId("abigail");
        // Explicit low-step profile still routes around a full-block obstacle.
        npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT).setBaseValue(.6);
        boolean[] detoured = {false};
        h.onEachTick(() -> {
            h.getLevel().tickNonPassenger(npc);
            boolean done = NpcCentralMovementService.tickAuthoredWalkTarget(h.getLevel(), npc, "obstacle_regression", "obstacle", target);
            if (npc.tickCount >= 210) h.fail("Obstacle route stalled: " + npc.position() + " target=" + target + " path=" + npc.getNavigation().getPath());
            detoured[0] |= Math.abs(npc.getZ() - target.z) > .6;
            h.assertTrue(npc.jumps == 0, "Obstacle avoidance requested a jump");
            h.assertTrue(Math.abs(npc.getY() - target.y) < .1, "NPC climbed onto full block");
            if (done) {
                h.assertTrue(detoured[0], "NPC did not walk around the obstacle");
                NpcCentralMovementService.resetAuthoredMovementPlan(npc.getNpcId(), "obstacle_regression");
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(h.getLevel(), npc.getNpcId());
                com.stardew.craft.npc.runtime.NpcExecutionCoordinator.cancel(npc);
                h.succeed();
            }
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=220)
    public static void levelPathDoesNotStepOntoClippedCorner(GameTestHelper h) {
        var base = floor(h);
        var npc = new Npc(h);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(base.getX()+2.65, base.getY(), base.getZ()+2.78);
        npc.setOnGround(true);
        h.getLevel().setBlockAndUpdate(base.offset(3,0,3), Blocks.STONE.defaultBlockState());
        // All waypoints are on the floor and avoid the corner. The starting body is
        // slightly off the centreline, as happens when turning or being pushed.
        var nodes = new java.util.ArrayList<net.minecraft.world.level.pathfinder.Node>();
        for (int x=3;x<=7;x++) nodes.add(new net.minecraft.world.level.pathfinder.Node(
                base.getX()+x, base.getY(), base.getZ()+2));
        var target = Vec3.atBottomCenterOf(base.offset(7,0,2));
        var path = new net.minecraft.world.level.pathfinder.Path(nodes, BlockPos.containing(target), true);
        h.assertTrue(npc.maxUpStep() >= 1, "Corner fixture must use the normal full-block step capability");
        npc.getNavigation().moveTo(path, 1);
        h.onEachTick(() -> {
            h.getLevel().tickNonPassenger(npc);
            h.assertTrue(Math.abs(npc.getY()-base.getY())<.05,
                    "Level path collision lifted NPC onto the side corner: " + npc.position());
            h.assertTrue(npc.jumps==0, "Corner traversal requested a jump");
            if (npc.position().distanceToSqr(target)<.25) h.succeed();
            if (npc.tickCount>=210) h.fail("Corner avoidance stalled: " + npc.position());
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=260)
    public static void walksUpTerrainTerracesWithoutJumping(GameTestHelper h) {
        var base = floor(h);
        var level = h.getLevel();
        // Town terrain uses full-block terraces, not StairBlock. No level detour exists.
        for (int x = -1; x <= 10; x++) for (int z = -1; z <= 7; z++) {
            int height = x >= 6 ? 2 : x >= 3 ? 1 : 0;
            for (int y = 0; y < height; y++)
                level.setBlockAndUpdate(base.offset(x,y,z), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        var npc = new Npc(h);
        npc.setNpcId("robin");
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(1,0,3)));
        npc.setOnGround(true);
        var target = Vec3.atBottomCenterOf(base.offset(8,2,3));
        var stepHeight = npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        double configuredStep = stepHeight.getBaseValue();
        stepHeight.setBaseValue(.6);
        var oldPath = npc.getNavigation().createPath(BlockPos.containing(target),0);
        h.assertTrue(oldPath==null || !oldPath.canReach(), "Fixture does not reproduce the old terrain blockage");
        stepHeight.setBaseValue(configuredStep);
        h.onEachTick(() -> {
            var before = npc.position();
            level.tickNonPassenger(npc);
            h.assertTrue(npc.jumps == 0, "Terrain stepping requested a jump");
            h.assertTrue(npc.position().subtract(before).horizontalDistanceSqr() < .25, "Terrain route teleported horizontally");
            boolean done = NpcCentralMovementService.tickAuthoredWalkTarget(level,npc,"terrain_regression","uphill",target);
            if (npc.tickCount >= 250) h.fail("Full-block terrain stalls work route: " + npc.position() + " target=" + target);
            if (done) {
                h.assertTrue(Math.abs(npc.getY()-target.y)<.1, "Arrived on the wrong terrace");
                NpcCentralMovementService.resetAuthoredMovementPlan(npc.getNpcId(),"terrain_regression");
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,npc.getNpcId());
                com.stardew.craft.npc.runtime.NpcExecutionCoordinator.cancel(npc);
                h.succeed();
            }
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=480)
    public static void fenceGateOpensAndClosesOnEastWestRoute(GameTestHelper h) {
        walkThroughFenceGate(h, net.minecraft.core.Direction.EAST, "clint");
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=480)
    public static void fenceGateOpensAndClosesOnNorthSouthRoute(GameTestHelper h) {
        walkThroughFenceGate(h, net.minecraft.core.Direction.SOUTH, "jodi");
    }

    private static void walkThroughFenceGate(GameTestHelper h, net.minecraft.core.Direction direction, String id) {
        var level=h.getLevel();var base=floor(h).offset(5,0,3);
        var side=direction.getClockWise();
        for(int i=-4;i<=4;i++) for(int sign:new int[]{-1,1}) for(int y=0;y<3;y++)
            level.setBlockAndUpdate(base.relative(direction,i).relative(side,sign).above(y),Blocks.STONE.defaultBlockState());
        var gate=Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING,direction);
        level.setBlockAndUpdate(base,gate);
        var npc=new Npc(h);npc.setNpcId(id);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        var start=Vec3.atBottomCenterOf(base.relative(direction,-3));
        var end=Vec3.atBottomCenterOf(base.relative(direction,3));
        npc.setPos(start);npc.setOnGround(true);
        var path=npc.getNavigation().createPath(BlockPos.containing(end),0);
        boolean throughGate=false;
        if(path!=null) for(int i=0;i<path.getNodeCount();i++) throughGate|=path.getNodePos(i).equals(base);
        h.assertTrue(path!=null && path.canReach() && throughGate,"Closed gate is excluded from the walking route");
        int[] phase={0},opens={0};boolean[] wasOpen={false};
        String owner="gate_regression_"+id;
        h.onEachTick(()->{
            var before=npc.position();level.tickNonPassenger(npc);
            boolean done=NpcCentralMovementService.tickAuthoredWalkTarget(level,npc,owner,
                    phase[0]==0?"outbound":"return",phase[0]==0?end:start);
            boolean open=level.getBlockState(base).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN);
            if(open&&!wasOpen[0]) opens[0]++;
            if(!open&&wasOpen[0]) {
                double progress=(npc.getX()-base.getX()-.5)*direction.getStepX()
                        +(npc.getZ()-base.getZ()-.5)*direction.getStepZ();
                h.assertTrue(phase[0]==0?progress>.6:progress<-.6,"Gate closed before the NPC passed");
            }
            wasOpen[0]=open;
            h.assertTrue(Math.abs(npc.getY()-base.getY())<.1 && npc.jumps==0,"NPC climbed or jumped over the gate");
            h.assertTrue(npc.position().subtract(before).horizontalDistanceSqr()<.25,"Gate traversal teleported NPC");
            if(done) {
                NpcCentralMovementService.resetAuthoredMovementPlan(id,owner);
                h.assertTrue(!level.getBlockState(base).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),"Gate was left open after route completion");
                if(phase[0]++==1) {
                    h.assertTrue(opens[0]==2,"Gate repeatedly toggled or failed to open on return");
                    com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level,id);
                    com.stardew.craft.npc.runtime.NpcExecutionCoordinator.cancel(npc);
                    h.succeed();
                }
            }
            if(npc.tickCount>=470) h.fail("Gate route stalled at "+npc.position());
        });
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities")
    public static void fenceGateCleanupPreservesExistingOpenAndRedstoneControl(GameTestHelper h) throws ReflectiveOperationException {
        var pos=floor(h);var level=h.getLevel();var npc=new Npc(h);
        npc.setPos(Vec3.atBottomCenterOf(pos.offset(4,0,0)));
        var plan=plan(npc,new String[]{"walk"},npc.position());
        var open=NpcCentralMovementService.class.getDeclaredMethod("tryOpenDoorAt",net.minecraft.server.level.ServerLevel.class,
                StardewNpcEntity.class,plan.getClass(),BlockPos.class);open.setAccessible(true);
        var close=NpcCentralMovementService.class.getDeclaredMethod("closeOpenedDoors",net.minecraft.server.level.ServerLevel.class,
                StardewNpcEntity.class,plan.getClass(),boolean.class);close.setAccessible(true);
        var gate=Blocks.OAK_FENCE_GATE.defaultBlockState();
        level.setBlockAndUpdate(pos,gate.setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN,true));
        open.invoke(null,level,npc,plan,pos);close.invoke(null,level,npc,plan,true);
        h.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),"NPC closed a gate it did not open");
        level.setBlockAndUpdate(pos,gate);
        open.invoke(null,level,npc,plan,pos);
        level.setBlock(pos,level.getBlockState(pos).setValue(net.minecraft.world.level.block.FenceGateBlock.POWERED,true),2);
        close.invoke(null,level,npc,plan,true);
        h.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN),"NPC cleanup overrode a powered gate");
        h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_npc_runtime", template="ring_utilities", timeoutTicks=220)
    public static void climbsStairsWithoutJumping(GameTestHelper h) {
        var base = floor(h);
        var level = h.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y < 4; y++) {
                level.setBlockAndUpdate(base.offset(x,y,2), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x,y,4), Blocks.STONE.defaultBlockState());
            }
            if (x >= 4) level.setBlockAndUpdate(base.offset(x,0,3), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(base.offset(3,0,3), Blocks.STONE_STAIRS.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST));
        var npc = new Npc(h);
        npc.addTag(com.stardew.craft.auction.AuctionService.AUCTION_HOST_TAG);
        npc.setPos(Vec3.atBottomCenterOf(base.offset(1,0,3)));
        npc.setOnGround(true);
        var target = Vec3.atBottomCenterOf(base.offset(7,1,3));
        // Concurrent travel tests must use distinct logical NPC identities.
        npc.setNpcId("haley");
        h.onEachTick(() -> {
            level.tickNonPassenger(npc);
            h.assertTrue(npc.jumps == 0, "Stairs requested a jump instead of collision stepping");
            boolean done = NpcCentralMovementService.tickAuthoredWalkTarget(level, npc, "stairs_regression", "stairs", target);
            if (npc.tickCount >= 210) h.fail("Stair route stalled: " + npc.position() + " target=" + target + " path=" + npc.getNavigation().getPath());
            if (done) {
                NpcCentralMovementService.resetAuthoredMovementPlan(npc.getNpcId(), "stairs_regression");
                com.stardew.craft.npc.runtime.NpcChunkForceManager.releaseNpcForcedChunks(level, npc.getNpcId());
                com.stardew.craft.npc.runtime.NpcExecutionCoordinator.cancel(npc);
                h.succeed();
            }
        });
    }
}
