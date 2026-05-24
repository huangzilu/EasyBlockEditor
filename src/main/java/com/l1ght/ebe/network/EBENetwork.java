package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class EBENetwork {

    public static void register(IEventBus modBus) {
        modBus.addListener(EBENetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EBEMod.MOD_ID);

        registrar.playToServer(
                PlaceBlocksPayload.TYPE,
                PlaceBlocksPayload.STREAM_CODEC,
                PlaceBlocksPayload::handleServer
        );

        registrar.playToServer(
                PrinterPlacePayload.TYPE,
                PrinterPlacePayload.STREAM_CODEC,
                PrinterPlacePayload::handleServer
        );

        registrar.playToServer(
                PrinterPlaceBatchPayload.TYPE,
                PrinterPlaceBatchPayload.STREAM_CODEC,
                PrinterPlaceBatchPayload::handleServer
        );

        registrar.playToClient(
                PlaceProgressPayload.TYPE,
                PlaceProgressPayload.STREAM_CODEC,
                PlaceProgressPayload::handleClient
        );

        registrar.playToClient(
                WorkgroupSyncPayload.TYPE,
                WorkgroupSyncPayload.STREAM_CODEC,
                WorkgroupSyncPayload::handleClient
        );

        registrar.playToServer(
                WorkgroupProjectionPayload.TYPE,
                WorkgroupProjectionPayload.STREAM_CODEC,
                WorkgroupProjectionPayload::handleServer
        );

        registrar.playToServer(
                WorkgroupActionPayload.TYPE,
                WorkgroupActionPayload.STREAM_CODEC,
                WorkgroupActionPayload::handleServer
        );

        registrar.playToServer(
                AdminActionPayload.TYPE,
                AdminActionPayload.STREAM_CODEC,
                AdminActionPayload::handleServer
        );

        registrar.playToClient(
                AdminSyncPayload.TYPE,
                AdminSyncPayload.STREAM_CODEC,
                AdminSyncPayload::handleClient
        );

        registrar.playToServer(
                WorkgroupPrintUploadPayload.TYPE,
                WorkgroupPrintUploadPayload.STREAM_CODEC,
                WorkgroupPrintUploadPayload::handleServer
        );

        registrar.playToServer(
                WorkgroupPrintReservePayload.TYPE,
                WorkgroupPrintReservePayload.STREAM_CODEC,
                WorkgroupPrintReservePayload::handleServer
        );

        registrar.playToClient(
                WorkgroupPrintReservationPayload.TYPE,
                WorkgroupPrintReservationPayload.STREAM_CODEC,
                WorkgroupPrintReservationPayload::handleClient
        );

        registrar.playToServer(
                WorkgroupPrinterPlacePayload.TYPE,
                WorkgroupPrinterPlacePayload.STREAM_CODEC,
                WorkgroupPrinterPlacePayload::handleServer
        );

        registrar.playToClient(
                WorkgroupPrintSyncPayload.TYPE,
                WorkgroupPrintSyncPayload.STREAM_CODEC,
                WorkgroupPrintSyncPayload::handleClient
        );
    }
}
