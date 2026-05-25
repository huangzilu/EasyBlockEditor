package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.placement.EntityPlacementQueue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class PlaceEntitiesPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "place_entities");
    public static final Type<PlaceEntitiesPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceEntitiesPayload> STREAM_CODEC =
            StreamCodec.ofMember(PlaceEntitiesPayload::write, PlaceEntitiesPayload::decode);

    private final Purpose purpose;
    private final List<String> entities;

    public enum Purpose {
        PLACE_ALL,
        PRINTER;

        static Purpose byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : PRINTER;
        }
    }

    public PlaceEntitiesPayload(Purpose purpose, List<String> entities) {
        this.purpose = purpose == null ? Purpose.PRINTER : purpose;
        this.entities = entities == null ? List.of() : List.copyOf(entities);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(purpose.ordinal());
        buf.writeVarInt(entities.size());
        for (String entity : entities) {
            buf.writeUtf(NetworkLimits.bounded(entity, NetworkLimits.MAX_ENTITY_NBT_CHARS),
                    NetworkLimits.MAX_ENTITY_NBT_CHARS);
        }
    }

    public static PlaceEntitiesPayload decode(RegistryFriendlyByteBuf buf) {
        Purpose purpose = Purpose.byOrdinal(buf.readVarInt());
        int size = buf.readVarInt();
        if (size < 0 || size > NetworkLimits.MAX_PLACE_ENTITIES_PER_PACKET) {
            throw new IllegalArgumentException("Invalid entity placement count: " + size);
        }
        var entities = new ArrayList<String>(size);
        long nbtChars = 0L;
        for (int i = 0; i < size; i++) {
            String nbt = buf.readUtf(NetworkLimits.MAX_ENTITY_NBT_CHARS);
            nbtChars += nbt.length();
            if (nbtChars > NetworkLimits.MAX_BATCH_NBT_CHARS) {
                throw new IllegalArgumentException("Entity placement payload is too large");
            }
            entities.add(nbt);
        }
        return new PlaceEntitiesPayload(purpose, entities);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PlaceEntitiesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!canUse(player, payload.purpose)) return;
            EntityPlacementQueue.enqueue(level, player, payload.purpose, payload.entities);
        });
    }

    private static boolean canUse(ServerPlayer player, Purpose purpose) {
        if (purpose == Purpose.PLACE_ALL) {
            return PermissionManager.canUse(player, PermissionFeature.PLACE_ALL);
        }
        return PermissionManager.canUse(player, PermissionFeature.PRINTER);
    }
}
