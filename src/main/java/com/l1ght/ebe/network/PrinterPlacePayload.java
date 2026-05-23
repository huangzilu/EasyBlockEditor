package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.Direction;
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
        buf.writeUtf(nbtStr);
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
        String nbt = buf.readUtf();
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

            var state = Block.stateById(payload.stateId);
            if (state == null || state.isAir()) return;

            var existing = level.getBlockState(payload.pos);
            if (!existing.isAir()) return;

            var block = state.getBlock();
            var requiredItem = block.asItem();
            if (requiredItem == null) return;

            boolean canPlace = player.isCreative();
            if (!canPlace) {
                CompoundTag targetNbt = parseCleanTargetNbt(payload.nbtStr);
                if (payload.requireHeldItem) {
                    canPlace = consumeFromHeldItem(player, level, requiredItem, targetNbt);
                } else if (payload.materialSourcePos != null) {
                    canPlace = payload.materialSourcePos.closerThan(player.blockPosition(), 128.0)
                            && (payload.materialSourceRange <= 0 || isWithinBlockRange(payload.pos, payload.materialSourcePos, payload.materialSourceRange))
                            && consumeFromMaterialSource(level, payload.materialSourcePos, requiredItem, targetNbt);
                } else {
                    canPlace = consumeFromInventory(player, level, requiredItem, targetNbt);
                }
            }

            if (canPlace) {
                if (level.setBlock(payload.pos, state, Block.UPDATE_ALL)) {
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
                                level.sendBlockUpdated(payload.pos, state, state, Block.UPDATE_ALL);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
    }

    private static boolean consumeFromHeldItem(ServerPlayer player, ServerLevel level, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty() && stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private static boolean isWithinBlockRange(BlockPos pos, BlockPos center, int range) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    private static boolean consumeFromInventory(ServerPlayer player, ServerLevel level, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromMaterialSource(ServerLevel level, BlockPos sourcePos, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        if (consumeFromContainer(level, sourcePos, requiredItem, targetNbt)) {
            return true;
        }

        var state = level.getBlockState(sourcePos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction neighborDirection = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                return consumeFromContainer(level, sourcePos.relative(neighborDirection), requiredItem, targetNbt);
            }
        }
        return false;
    }

    private static boolean consumeFromContainer(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
                stack.shrink(1);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    private static CompoundTag parseCleanTargetNbt(String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) {
            return null;
        }
        try {
            var targetNbt = TagParser.parseTag(nbtStr);
            targetNbt.remove("x");
            targetNbt.remove("y");
            targetNbt.remove("z");
            targetNbt.remove("id");
            return targetNbt;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean nbtMatches(ServerLevel level, ItemStack stack, CompoundTag targetNbt) {
        CompoundTag cleanedTarget = targetNbt == null ? new CompoundTag() : cleanPlacementNbt(targetNbt);
        CompoundTag stackBlockEntityTag = extractStackBlockEntityTag(level, stack);
        if (cleanedTarget.isEmpty()) {
            return stackBlockEntityTag == null || cleanPlacementNbt(stackBlockEntityTag).isEmpty();
        }
        return stackBlockEntityTag != null && cleanPlacementNbt(stackBlockEntityTag).equals(cleanedTarget);
    }

    private static CompoundTag extractStackBlockEntityTag(ServerLevel level, ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            return data.copyTag();
        }
        Tag saved = stack.saveOptional(level.registryAccess());
        if (saved instanceof CompoundTag ct && ct.contains("BlockEntityTag")) {
            return ct.getCompound("BlockEntityTag").copy();
        }
        return null;
    }

    private static CompoundTag cleanPlacementNbt(CompoundTag tag) {
        CompoundTag cleaned = tag.copy();
        cleaned.remove("x");
        cleaned.remove("y");
        cleaned.remove("z");
        cleaned.remove("id");
        return cleaned;
    }
}
