package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class AdminSyncPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "admin_sync");
    public static final Type<AdminSyncPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminSyncPayload> STREAM_CODEC = StreamCodec.ofMember(AdminSyncPayload::write, AdminSyncPayload::decode);

    private final String json;

    public AdminSyncPayload(String json) {
        this.json = json == null ? "{}" : json;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(json, 32767);
    }

    public static AdminSyncPayload decode(RegistryFriendlyByteBuf buf) {
        return new AdminSyncPayload(buf.readUtf(32767));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(AdminSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.l1ght.ebe.client.ClientOnlyHooks.updateAdminData(payload.json);
            }
        });
    }
}
