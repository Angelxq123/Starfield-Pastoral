package com.stardew.craft.floor;

import com.stardew.craft.StardewCraft;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SurfaceFloorEvents {
    private SurfaceFloorEvents() {}

    @SubscribeEvent public static void sent(ChunkWatchEvent.Sent event) {
        SurfaceFloorData.get(event.getLevel()).sendChunk(event.getLevel(), event.getPos(), event.getPlayer());
    }
    @SubscribeEvent public static void unwatch(ChunkWatchEvent.UnWatch event) {
        PacketDistributor.sendToPlayer(event.getPlayer(), new SurfaceFloorPacket(event.getLevel().dimension().location(),
                event.getPos().toLong(), true, List.of()));
    }

    /** Deliberate removal gesture: never mines the support or strips the underlying log. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void remove(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown() || event.getFace() != Direction.UP) return;
        var stack = event.getItemStack();
        if (!stack.canPerformAction(ItemAbilities.AXE_DIG) && !stack.canPerformAction(ItemAbilities.PICKAXE_DIG)) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!SurfaceFloorItem.mayEdit(player, event.getPos())) return;
            if (SurfaceFloorData.get(player.serverLevel()).remove(player.serverLevel(), event.getPos(), !player.isCreative())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } else if (com.stardew.craft.client.floor.ClientSurfaceFloors.at(event.getPos()) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
