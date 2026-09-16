package com.stardew.craft.blockentity;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.block.utility.HayHopperBlock;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** A physical feed dispenser. Farm ownership comes from the current claim, never a saved player UUID. */
public class HayHopperBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    public HayHopperBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.HAY_HOPPER.get(), pos, state); }
    public static void serverTick(Level level, BlockPos pos, BlockState state, HayHopperBlockEntity hopper) {
        if (level instanceof ServerLevel server && (level.getGameTime() + pos.asLong()) % 20 == 0) hopper.syncFullState(server);
    }
    public UUID storageFarm() {
        if (!(level instanceof ServerLevel server)) return null;
        var home = FarmFeed.home(server, worldPosition);
        if (home != null && PrefabDefinitions.supported(home.family())) return home.farmId();
        var farm = FarmFeed.farm(server, worldPosition); return farm == null ? null : farm.getInstanceId();
    }
    public int extractHayToPlayer(Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer actor)) return 0;
        var home = FarmFeed.home(server, worldPosition);
        if (home == null || !PrefabDefinitions.supported(home.family()) || !BuildingService.canManage(actor, home)) return 0;
        int alreadyHay = 0;
        for (var pos : BlockPos.betweenClosed(LivestockHomes.bounds(home).min(), LivestockHomes.bounds(home).maxInclusive()))
            if (LivestockService.hasHay(server, pos, false)) alreadyHay++;
        int requested = Math.max(0, Math.min(Math.max(1, LivestockWorldData.get(server.getServer()).occupancy(home.id())), home.tier() * 4 - alreadyHay));
        requested = Math.min(requested, FarmFeed.amount(server.getServer(), home.farmId()));
        if (requested == 0) return 0;
        int space = 0; int max = new ItemStack(ModItems.HAY.get()).getMaxStackSize();
        for (int i = 0; i < 36; i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) space += max;
            else if (stack.is(ModItems.HAY.get())) space += Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }
        if (space < requested) return 0;
        int removed = FarmFeed.take(server.getServer(), home.farmId(), requested);
        player.getInventory().add(new ItemStack(ModItems.HAY.get(), removed));
        syncFullState(server); return removed;
    }
    private void syncFullState(ServerLevel server) {
        var farm = storageFarm(); boolean full = farm != null && FarmFeed.amount(server.getServer(), farm) > 0;
        for (var pos : java.util.List.of(worldPosition, worldPosition.above())) {
            var state = server.getBlockState(pos);
            if (state.getBlock() instanceof HayHopperBlock && state.getValue(HayHopperBlock.FULL) != full)
                server.setBlock(pos, state.setValue(HayHopperBlock.FULL, full), 3);
        }
    }
}
