package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.workgroup.print.BlockReservation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkgroupPrintReservationPayload implements CustomPacketPayload {
    private static final int MAX_RESERVATIONS = 64;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_print_reservations");
    public static final Type<WorkgroupPrintReservationPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupPrintReservationPayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupPrintReservationPayload::write, WorkgroupPrintReservationPayload::decode);

    private final List<Entry> entries;

    public WorkgroupPrintReservationPayload(List<BlockReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            this.entries = List.of();
            return;
        }
        List<Entry> mapped = new ArrayList<>(Math.min(MAX_RESERVATIONS, reservations.size()));
        for (BlockReservation reservation : reservations) {
            if (mapped.size() >= MAX_RESERVATIONS) break;
            mapped.add(new Entry(
                    reservation.getSessionId(),
                    reservation.getToken(),
                    reservation.getBlockId(),
                    reservation.getPos(),
                    reservation.getStateId(),
                    reservation.getNbt()
            ));
        }
        this.entries = List.copyOf(mapped);
    }

    private WorkgroupPrintReservationPayload(List<Entry> entries, boolean direct) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUUID(entry.sessionId());
            buf.writeUUID(entry.token());
            buf.writeVarLong(entry.blockId());
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.stateId());
            buf.writeUtf(entry.nbt(), 32767);
        }
    }

    public static WorkgroupPrintReservationPayload decode(RegistryFriendlyByteBuf buf) {
        int rawSize = buf.readVarInt();
        if (rawSize < 0 || rawSize > MAX_RESERVATIONS) {
            throw new IllegalArgumentException("Invalid workgroup print reservation count: " + rawSize);
        }
        int size = rawSize;
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(32767)));
        }
        return new WorkgroupPrintReservationPayload(entries, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(WorkgroupPrintReservationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.l1ght.ebe.client.ClientOnlyHooks.acceptWorkgroupPrintReservations(payload.entries);
            }
        });
    }

    public record Entry(UUID sessionId, UUID token, long blockId, BlockPos pos, int stateId, String nbt) {
        public Entry {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbt = nbt == null ? "" : nbt;
        }
    }
}
