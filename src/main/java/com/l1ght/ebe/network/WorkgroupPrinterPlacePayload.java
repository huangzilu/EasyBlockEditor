package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.placement.PrinterPlacementService;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.l1ght.ebe.server.workgroup.print.PrintBlockStatus;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class WorkgroupPrinterPlacePayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_printer_place");
    public static final Type<WorkgroupPrinterPlacePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupPrinterPlacePayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupPrinterPlacePayload::write, WorkgroupPrinterPlacePayload::decode);

    private final UUID sessionId;
    private final UUID token;
    private final BlockPos materialSourcePos;
    private final boolean requireHeldItem;
    private final int materialSourceRange;

    public WorkgroupPrinterPlacePayload(UUID sessionId, UUID token, BlockPos materialSourcePos,
                                        boolean requireHeldItem, int materialSourceRange) {
        this.sessionId = sessionId;
        this.token = token;
        this.materialSourcePos = materialSourcePos == null ? null : materialSourcePos.immutable();
        this.requireHeldItem = requireHeldItem;
        this.materialSourceRange = Math.max(0, materialSourceRange);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeUUID(token);
        buf.writeBoolean(materialSourcePos != null);
        if (materialSourcePos != null) {
            buf.writeBlockPos(materialSourcePos);
        }
        buf.writeBoolean(requireHeldItem);
        buf.writeVarInt(materialSourceRange);
    }

    public static WorkgroupPrinterPlacePayload decode(RegistryFriendlyByteBuf buf) {
        UUID sessionId = buf.readUUID();
        UUID token = buf.readUUID();
        BlockPos source = buf.readBoolean() ? buf.readBlockPos() : null;
        boolean requireHeldItem = buf.readBoolean();
        int range = buf.readVarInt();
        return new WorkgroupPrinterPlacePayload(sessionId, token, source, requireHeldItem, range);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupPrinterPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group == null) return;

            String dimension = level.dimension().location().toString();
            var reservation = WorkgroupPrintSessionManager.verifyReservation(
                    group.id, payload.sessionId, payload.token, player.getUUID(), dimension);
            if (reservation == null) return;

            PrinterPlacementService.Result placementResult = PrinterPlacementService.place(
                    player,
                    level,
                    reservation.getPos(),
                    reservation.getStateId(),
                    reservation.getNbt(),
                    payload.materialSourcePos,
                    payload.requireHeldItem,
                    payload.materialSourceRange
            );

            PrintBlockStatus status = switch (placementResult) {
                case PLACED -> PrintBlockStatus.PLACED;
                case BLOCKED -> PrintBlockStatus.FAILED_BLOCKED;
                case MISSING_MATERIAL -> PrintBlockStatus.FAILED_MISSING_MATERIAL;
                default -> null;
            };
            if (status != null) {
                WorkgroupPrintSessionManager.completeReservation(group.id, payload.sessionId, payload.token, player.getUUID(), status);
                WorkgroupNetworkSync.syncGroup(player.server, group.id);
            }
        });
    }
}
