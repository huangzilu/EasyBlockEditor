package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WorkgroupPrintSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_print_sync");
    public static final Type<WorkgroupPrintSyncPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupPrintSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupPrintSyncPayload::write, WorkgroupPrintSyncPayload::decode);

    private final boolean active;
    private final int placed;
    private final int total;
    private final String snapshotJson;

    public WorkgroupPrintSyncPayload(boolean active, int placed, int total, String snapshotJson) {
        this.active = active;
        this.placed = Math.max(0, placed);
        this.total = Math.max(0, total);
        this.snapshotJson = snapshotJson == null ? "{}" : snapshotJson;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(placed);
        buf.writeVarInt(total);
        buf.writeUtf(NetworkLimits.bounded(snapshotJson, NetworkLimits.MAX_JSON_CHARS), NetworkLimits.MAX_JSON_CHARS);
    }

    public static WorkgroupPrintSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupPrintSyncPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(NetworkLimits.MAX_JSON_CHARS));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(WorkgroupPrintSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.l1ght.ebe.client.ClientOnlyHooks.updateWorkgroupPrintState(
                        payload.active, payload.placed, payload.total, payload.snapshotJson);
            }
        });
    }
}
