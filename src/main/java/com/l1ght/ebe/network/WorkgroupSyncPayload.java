package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WorkgroupSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_sync");
    public static final Type<WorkgroupSyncPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupSyncPayload> STREAM_CODEC = StreamCodec.ofMember(WorkgroupSyncPayload::write, WorkgroupSyncPayload::decode);

    private final String json;

    public WorkgroupSyncPayload(String json) {
        this.json = json == null ? "[]" : json;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(NetworkLimits.bounded(json, NetworkLimits.MAX_JSON_CHARS), NetworkLimits.MAX_JSON_CHARS);
    }

    public static WorkgroupSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupSyncPayload(buf.readUtf(NetworkLimits.MAX_JSON_CHARS));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(WorkgroupSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.l1ght.ebe.client.ClientOnlyHooks.updateWorkgroups(payload.json);
            }
        });
    }
}
