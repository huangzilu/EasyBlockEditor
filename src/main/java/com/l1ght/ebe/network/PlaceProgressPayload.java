package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PlaceProgressPayload implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "place_progress");
    public static final Type<PlaceProgressPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceProgressPayload> STREAM_CODEC = StreamCodec.ofMember(PlaceProgressPayload::write, PlaceProgressPayload::decode);

    private final int placed;
    private final int total;

    public PlaceProgressPayload(int placed, int total) {
        this.placed = placed;
        this.total = total;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(placed);
        buf.writeVarInt(total);
    }

    public static PlaceProgressPayload decode(RegistryFriendlyByteBuf buf) {
        return new PlaceProgressPayload(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getPlaced() { return placed; }
    public int getTotal() { return total; }

    public static void handleClient(PlaceProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.l1ght.ebe.client.ClientOnlyHooks.setProjectionProgress(payload.placed, payload.total);
            }
        });
    }
}
