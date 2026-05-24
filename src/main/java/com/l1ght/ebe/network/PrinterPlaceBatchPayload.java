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

import java.util.ArrayList;
import java.util.List;

public class PrinterPlaceBatchPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "printer_place_batch");
    public static final Type<PrinterPlaceBatchPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PrinterPlaceBatchPayload> STREAM_CODEC =
            StreamCodec.ofMember(PrinterPlaceBatchPayload::write, PrinterPlaceBatchPayload::decode);

    private final List<Entry> entries;
    private final BlockPos materialSourcePos;
    private final boolean requireHeldItem;
    private final int materialSourceRange;

    public record Entry(BlockPos pos, int stateId, String nbtStr) {
        public Entry {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbtStr = nbtStr == null ? "" : nbtStr;
        }
    }

    public PrinterPlaceBatchPayload(List<Entry> entries, BlockPos materialSourcePos,
                                    boolean requireHeldItem, int materialSourceRange) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.materialSourcePos = materialSourcePos == null ? null : materialSourcePos.immutable();
        this.requireHeldItem = requireHeldItem;
        this.materialSourceRange = Math.max(0, materialSourceRange);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        int size = Math.min(entries.size(), NetworkLimits.MAX_PRINTER_PLACE_BATCH);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            var entry = entries.get(i);
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.stateId());
            buf.writeUtf(NetworkLimits.bounded(entry.nbtStr(), NetworkLimits.MAX_BLOCK_NBT_CHARS),
                    NetworkLimits.MAX_BLOCK_NBT_CHARS);
        }
        buf.writeBoolean(materialSourcePos != null);
        if (materialSourcePos != null) {
            buf.writeBlockPos(materialSourcePos);
        }
        buf.writeBoolean(requireHeldItem);
        buf.writeVarInt(materialSourceRange);
    }

    public static PrinterPlaceBatchPayload decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > NetworkLimits.MAX_PRINTER_PLACE_BATCH) {
            throw new IllegalArgumentException("Invalid printer batch size: " + size);
        }
        var entries = new ArrayList<Entry>(size);
        long nbtChars = 0L;
        for (int i = 0; i < size; i++) {
            var pos = buf.readBlockPos();
            int stateId = buf.readVarInt();
            String nbt = buf.readUtf(NetworkLimits.MAX_BLOCK_NBT_CHARS);
            nbtChars += nbt.length();
            if (nbtChars > NetworkLimits.MAX_BATCH_NBT_CHARS) {
                throw new IllegalArgumentException("Printer batch NBT payload is too large");
            }
            entries.add(new Entry(pos, stateId, nbt));
        }
        BlockPos source = buf.readBoolean() ? buf.readBlockPos() : null;
        boolean requireHeldItem = buf.readBoolean();
        int range = buf.readVarInt();
        return new PrinterPlaceBatchPayload(entries, source, requireHeldItem, range);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PrinterPlaceBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            for (var entry : payload.entries) {
                PrinterPlacementService.place(player, level, entry.pos(), entry.stateId(), entry.nbtStr(),
                        payload.materialSourcePos, payload.requireHeldItem, payload.materialSourceRange);
            }
        });
    }
}
