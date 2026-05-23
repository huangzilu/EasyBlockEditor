package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.placement.PlaceAllQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class PlaceBlocksPayload implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "place_blocks");
    public static final Type<PlaceBlocksPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceBlocksPayload> STREAM_CODEC = StreamCodec.ofMember(PlaceBlocksPayload::write, PlaceBlocksPayload::decode);

    private final List<Entry> entries;

    public record Entry(BlockPos pos, int stateId) {}

    public PlaceBlocksPayload(List<Entry> entries) {
        this.entries = entries;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (var e : entries) {
            buf.writeBlockPos(e.pos());
            buf.writeVarInt(e.stateId());
        }
    }

    public static PlaceBlocksPayload decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readBlockPos(), buf.readVarInt()));
        }
        return new PlaceBlocksPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PlaceBlocksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level() instanceof ServerLevel level)) return;
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (!PermissionManager.canUse(serverPlayer, PermissionFeature.PLACE_ALL)) return;
            if (!PlaceAllQueue.withinMaxEditSize(payload.entries)) return;
            PlaceAllQueue.enqueue(level, serverPlayer, payload.entries);
        });
    }
}
