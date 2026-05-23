package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.l1ght.ebe.server.workgroup.print.BlockReservation;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public class WorkgroupPrintReservePayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_print_reserve");
    public static final Type<WorkgroupPrintReservePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupPrintReservePayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupPrintReservePayload::write, WorkgroupPrintReservePayload::decode);

    private final int maxReservations;
    private final BlockPos center;
    private final int range;

    public WorkgroupPrintReservePayload(int maxReservations, BlockPos center, int range) {
        this.maxReservations = Math.max(1, maxReservations);
        this.center = center == null ? BlockPos.ZERO : center.immutable();
        this.range = Math.max(0, range);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(maxReservations);
        buf.writeBlockPos(center);
        buf.writeVarInt(range);
    }

    public static WorkgroupPrintReservePayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupPrintReservePayload(buf.readVarInt(), buf.readBlockPos(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupPrintReservePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (!PermissionManager.canUse(player, PermissionFeature.PRINTER)
                    || !PermissionManager.canUse(player, PermissionFeature.COLLABORATE)) {
                return;
            }
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group == null) return;

            String dimension = level.dimension().location().toString();
            List<BlockReservation> reservations = WorkgroupPrintSessionManager.reserveForPlayer(
                    group.id,
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    dimension,
                    level.getGameTime(),
                    payload.maxReservations,
                    payload.center,
                    payload.range
            );
            PacketDistributor.sendToPlayer(player, new WorkgroupPrintReservationPayload(reservations));
            if (!reservations.isEmpty()) {
                WorkgroupNetworkSync.syncGroup(player.server, group.id);
            }
        });
    }
}
