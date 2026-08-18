package com.stardew.craft.communitycenter.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.data.BundleIngredient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Sends the relatively static bundle catalog separately from player progress. */
@SuppressWarnings("null")
@EventBusSubscriber(modid = StardewCraft.MODID)
public record BundleDefinitionSyncPayload(
        List<BundleDefinition> definitions,
        Map<Integer, String> areaNames,
        Map<Integer, String> areaDisplayKeys
) implements CustomPacketPayload {
    private static final Map<MinecraftServer, Map<UUID, Snapshot>> SENT_SNAPSHOTS =
            new WeakHashMap<>();

    public static final Type<BundleDefinitionSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "bundle_definition_sync")
    );

    public static final StreamCodec<ByteBuf, BundleDefinitionSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BundleDefinitionSyncPayload decode(ByteBuf buf) {
                    int definitionCount = ByteBufCodecs.VAR_INT.decode(buf);
                    List<BundleDefinition> definitions = new ArrayList<>(definitionCount);
                    for (int index = 0; index < definitionCount; index++) {
                        int bundleId = ByteBufCodecs.VAR_INT.decode(buf);
                        int areaId = ByteBufCodecs.VAR_INT.decode(buf);
                        String internalName = ByteBufCodecs.STRING_UTF8.decode(buf);
                        String displayNameKey = ByteBufCodecs.STRING_UTF8.decode(buf);
                        String reward = ByteBufCodecs.STRING_UTF8.decode(buf);
                        int color = ByteBufCodecs.VAR_INT.decode(buf);
                        int requiredCount = ByteBufCodecs.VAR_INT.decode(buf);
                        int ingredientCount = ByteBufCodecs.VAR_INT.decode(buf);
                        List<BundleIngredient> ingredients = new ArrayList<>(ingredientCount);
                        for (int ingredientIndex = 0;
                             ingredientIndex < ingredientCount;
                             ingredientIndex++) {
                            String itemId = buf.readBoolean()
                                    ? ByteBufCodecs.STRING_UTF8.decode(buf)
                                    : null;
                            String sdvId = ByteBufCodecs.STRING_UTF8.decode(buf);
                            int category = ByteBufCodecs.VAR_INT.decode(buf);
                            int stack = ByteBufCodecs.VAR_INT.decode(buf);
                            int quality = ByteBufCodecs.VAR_INT.decode(buf);
                            ingredients.add(new BundleIngredient(
                                    itemId, sdvId, category, stack, quality));
                        }
                        definitions.add(new BundleDefinition(
                                bundleId,
                                areaId,
                                internalName,
                                displayNameKey,
                                reward,
                                List.copyOf(ingredients),
                                color,
                                requiredCount));
                    }

                    int areaCount = ByteBufCodecs.VAR_INT.decode(buf);
                    Map<Integer, String> areaNames = new LinkedHashMap<>(areaCount);
                    Map<Integer, String> areaDisplayKeys = new LinkedHashMap<>(areaCount);
                    for (int index = 0; index < areaCount; index++) {
                        int areaId = ByteBufCodecs.VAR_INT.decode(buf);
                        areaNames.put(areaId, ByteBufCodecs.STRING_UTF8.decode(buf));
                        areaDisplayKeys.put(areaId, ByteBufCodecs.STRING_UTF8.decode(buf));
                    }
                    return new BundleDefinitionSyncPayload(
                            definitions, areaNames, areaDisplayKeys);
                }

                @Override
                public void encode(ByteBuf buf, BundleDefinitionSyncPayload payload) {
                    ByteBufCodecs.VAR_INT.encode(buf, payload.definitions.size());
                    for (BundleDefinition definition : payload.definitions) {
                        ByteBufCodecs.VAR_INT.encode(buf, definition.bundleId());
                        ByteBufCodecs.VAR_INT.encode(buf, definition.areaId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, definition.internalName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, definition.displayNameKey());
                        ByteBufCodecs.STRING_UTF8.encode(buf, definition.rewardString());
                        ByteBufCodecs.VAR_INT.encode(buf, definition.color());
                        ByteBufCodecs.VAR_INT.encode(buf, definition.requiredCount());
                        ByteBufCodecs.VAR_INT.encode(buf, definition.ingredients().size());
                        for (BundleIngredient ingredient : definition.ingredients()) {
                            buf.writeBoolean(ingredient.itemId() != null);
                            if (ingredient.itemId() != null) {
                                ByteBufCodecs.STRING_UTF8.encode(buf, ingredient.itemId());
                            }
                            ByteBufCodecs.STRING_UTF8.encode(buf, ingredient.sdvId());
                            ByteBufCodecs.VAR_INT.encode(buf, ingredient.category());
                            ByteBufCodecs.VAR_INT.encode(buf, ingredient.stack());
                            ByteBufCodecs.VAR_INT.encode(buf, ingredient.quality());
                        }
                    }

                    ByteBufCodecs.VAR_INT.encode(buf, payload.areaNames.size());
                    for (Map.Entry<Integer, String> area : payload.areaNames.entrySet()) {
                        ByteBufCodecs.VAR_INT.encode(buf, area.getKey());
                        ByteBufCodecs.STRING_UTF8.encode(buf, area.getValue());
                        ByteBufCodecs.STRING_UTF8.encode(
                                buf, payload.areaDisplayKeys.getOrDefault(area.getKey(), ""));
                    }
                }
            };

    public BundleDefinitionSyncPayload {
        definitions = List.copyOf(definitions);
        areaNames = Map.copyOf(areaNames);
        areaDisplayKeys = Map.copyOf(areaDisplayKeys);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BundleDefinitionSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BundleDataManager.applyFromNetwork(
                payload.definitions, payload.areaNames, payload.areaDisplayKeys));
    }

    public static boolean sendIfChanged(
            ServerPlayer player,
            Collection<BundleDefinition> resolvedDefinitions
    ) {
        Snapshot snapshot = createSnapshot(resolvedDefinitions);
        Map<UUID, Snapshot> serverSnapshots = SENT_SNAPSHOTS.computeIfAbsent(
                player.server, ignored -> new HashMap<>());
        Snapshot previous = serverSnapshots.get(player.getUUID());
        if (previous != null && previous.equals(snapshot)) {
            return false;
        }

        PacketDistributor.sendToPlayer(player, new BundleDefinitionSyncPayload(
                snapshot.definitions, snapshot.areaNames, snapshot.areaDisplayKeys));
        serverSnapshots.put(player.getUUID(), snapshot);
        return true;
    }

    private static Snapshot createSnapshot(Collection<BundleDefinition> resolvedDefinitions) {
        List<BundleDefinition> definitions = List.copyOf(resolvedDefinitions);
        Map<Integer, String> areaNames = new LinkedHashMap<>();
        Map<Integer, String> areaDisplayKeys = new LinkedHashMap<>();
        for (int areaId = 0; areaId <= 6; areaId++) {
            String name = BundleDataManager.getAreaName(areaId);
            String displayKey = BundleDataManager.getAreaDisplayNameKey(areaId);
            if (name != null) {
                areaNames.put(areaId, name);
            }
            if (displayKey != null) {
                areaDisplayKeys.put(areaId, displayKey);
            }
        }
        return new Snapshot(definitions, areaNames, areaDisplayKeys);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Map<UUID, Snapshot> snapshots = SENT_SNAPSHOTS.get(player.server);
        if (snapshots != null) {
            snapshots.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SENT_SNAPSHOTS.remove(event.getServer());
    }

    private record Snapshot(
            List<BundleDefinition> definitions,
            Map<Integer, String> areaNames,
            Map<Integer, String> areaDisplayKeys
    ) {
        private Snapshot {
            definitions = List.copyOf(definitions);
            areaNames = Map.copyOf(areaNames);
            areaDisplayKeys = Map.copyOf(areaDisplayKeys);
        }
    }
}
