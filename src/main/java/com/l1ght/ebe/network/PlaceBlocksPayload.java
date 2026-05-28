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

    public record Entry(BlockPos pos, int stateId, String nbt) {
        public Entry(BlockPos pos, int stateId) {
            this(pos, stateId, "");
        }

        public Entry {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbt = nbt == null ? "" : nbt;
        }
    }

    public PlaceBlocksPayload(List<Entry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (var e : entries) {
            buf.writeBlockPos(e.pos());
            buf.writeVarInt(e.stateId());
            buf.writeUtf(NetworkLimits.bounded(e.nbt(), NetworkLimits.MAX_BLOCK_NBT_CHARS), NetworkLimits.MAX_BLOCK_NBT_CHARS);
        }
    }

    public static PlaceBlocksPayload decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > NetworkLimits.MAX_PLACE_BLOCKS) {
            throw new IllegalArgumentException("Invalid place-all entry count: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        long nbtChars = 0L;
        for (int i = 0; i < size; i++) {
            var pos = buf.readBlockPos();
            var stateId = buf.readVarInt();
            var nbt = buf.readUtf(NetworkLimits.MAX_BLOCK_NBT_CHARS);
            nbtChars += nbt.length();
            if (nbtChars > NetworkLimits.MAX_BATCH_NBT_CHARS) {
                throw new IllegalArgumentException("Place-all NBT payload is too large");
            }
            entries.add(new Entry(pos, stateId, nbt));
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
            if (payload.entries.isEmpty()) return;
            if (payload.entries.size() > NetworkLimits.MAX_PLACE_BLOCKS) {
                EBEMod.LOGGER.warn("Rejected oversized place-all payload from {}: {} entries",
                        serverPlayer.getGameProfile().getName(), payload.entries.size());
                return;
            }
            if (payload.entries.size() > NetworkLimits.MAX_PLACE_BLOCKS_PER_PACKET) {
                EBEMod.LOGGER.warn("Received legacy oversized place-all payload from {}: {} entries; accepting with server tick queue",
                        serverPlayer.getGameProfile().getName(), payload.entries.size());
            }
            PlaceAllQueue.enqueue(level, serverPlayer, payload.entries);
        });
    }
}
