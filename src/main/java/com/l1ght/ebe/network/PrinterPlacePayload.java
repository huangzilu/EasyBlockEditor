package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.placement.PrinterPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PrinterPlacePayload implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "printer_place");
    public static final Type<PrinterPlacePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PrinterPlacePayload> STREAM_CODEC = StreamCodec.ofMember(PrinterPlacePayload::write, PrinterPlacePayload::decode);

    private final BlockPos pos;
    private final int stateId;
    private final String nbtStr;
    private final BlockPos materialSourcePos;
    private final boolean requireHeldItem;
    private final int materialSourceRange;

    public PrinterPlacePayload(BlockPos pos, int stateId, String nbtStr) {
        this(pos, stateId, nbtStr, null, false, 0);
    }

    public PrinterPlacePayload(BlockPos pos, int stateId, String nbtStr, BlockPos materialSourcePos) {
        this(pos, stateId, nbtStr, materialSourcePos, false, 0);
    }

    public PrinterPlacePayload(BlockPos pos, int stateId, String nbtStr, BlockPos materialSourcePos,
                               boolean requireHeldItem, int materialSourceRange) {
        this.pos = pos;
        this.stateId = stateId;
        this.nbtStr = nbtStr != null ? nbtStr : "";
        this.materialSourcePos = materialSourcePos == null ? null : materialSourcePos.immutable();
        this.requireHeldItem = requireHeldItem;
        this.materialSourceRange = Math.max(0, materialSourceRange);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(stateId);
        buf.writeUtf(NetworkLimits.bounded(nbtStr, NetworkLimits.MAX_BLOCK_NBT_CHARS), NetworkLimits.MAX_BLOCK_NBT_CHARS);
        buf.writeBoolean(materialSourcePos != null);
        if (materialSourcePos != null) {
            buf.writeBlockPos(materialSourcePos);
        }
        buf.writeBoolean(requireHeldItem);
        buf.writeVarInt(materialSourceRange);
    }

    public static PrinterPlacePayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int stateId = buf.readVarInt();
        String nbt = buf.readUtf(NetworkLimits.MAX_BLOCK_NBT_CHARS);
        BlockPos source = buf.readBoolean() ? buf.readBlockPos() : null;
        boolean requireHeldItem = buf.readBoolean();
        int materialSourceRange = buf.readVarInt();
        return new PrinterPlacePayload(pos, stateId, nbt, source, requireHeldItem, materialSourceRange);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(PrinterPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level() instanceof ServerLevel level)) return;
            if (!(context.player() instanceof ServerPlayer player)) return;
            PrinterPlacementService.place(player, level, payload.pos, payload.stateId, payload.nbtStr,
                    payload.materialSourcePos, payload.requireHeldItem, payload.materialSourceRange);
        });
    }
}
