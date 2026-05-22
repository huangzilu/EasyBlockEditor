package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PrinterPlacePayload implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "printer_place");
    public static final Type<PrinterPlacePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PrinterPlacePayload> STREAM_CODEC = StreamCodec.ofMember(PrinterPlacePayload::write, PrinterPlacePayload::decode);

    private final BlockPos pos;
    private final int stateId;
    private final String nbtStr;

    public PrinterPlacePayload(BlockPos pos, int stateId, String nbtStr) {
        this.pos = pos;
        this.stateId = stateId;
        this.nbtStr = nbtStr != null ? nbtStr : "";
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(stateId);
        buf.writeUtf(nbtStr);
    }

    public static PrinterPlacePayload decode(RegistryFriendlyByteBuf buf) {
        return new PrinterPlacePayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServer(PrinterPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level() instanceof ServerLevel level)) return;
            if (!(context.player() instanceof ServerPlayer player)) return;

            var state = Block.stateById(payload.stateId);
            if (state == null || state.isAir()) return;

            var existing = level.getBlockState(payload.pos);
            if (!existing.isAir()) return;

            var block = state.getBlock();
            var requiredItem = block.asItem();
            if (requiredItem == null) return;

            boolean found = false;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(requiredItem)) {
                    if (!payload.nbtStr.isEmpty()) {
                        var stackTag = stack.saveOptional(level.registryAccess());
                        if (!(stackTag instanceof CompoundTag ct) || ct.isEmpty()) continue;
                        try {
                            var targetNbt = TagParser.parseTag(payload.nbtStr);
                            var cleanedTarget = targetNbt.copy();
                            cleanedTarget.remove("x"); cleanedTarget.remove("y"); cleanedTarget.remove("z"); cleanedTarget.remove("id");
                            if (cleanedTarget.isEmpty()) { found = true; stack.shrink(1); break; }
                            if (ct.contains("BlockEntityTag")) {
                                var beTag = ct.getCompound("BlockEntityTag");
                                if (beTag.equals(cleanedTarget)) { found = true; stack.shrink(1); break; }
                            }
                        } catch (Exception e) { continue; }
                    } else {
                        found = true;
                        stack.shrink(1);
                        break;
                    }
                }
            }

            if (found) {
                level.setBlockAndUpdate(payload.pos, state);
                if (!payload.nbtStr.isEmpty()) {
                    try {
                        var beNbt = TagParser.parseTag(payload.nbtStr);
                        beNbt.remove("x"); beNbt.remove("y"); beNbt.remove("z"); beNbt.remove("id");
                        var be = level.getBlockEntity(payload.pos);
                        if (be != null) {
                            beNbt.putInt("x", payload.pos.getX());
                            beNbt.putInt("y", payload.pos.getY());
                            beNbt.putInt("z", payload.pos.getZ());
                            be.loadWithComponents(beNbt, level.registryAccess());
                            be.setChanged();
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}
