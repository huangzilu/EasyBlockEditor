package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
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

        registrar.playToClient(
                PlaceProgressPayload.TYPE,
                PlaceProgressPayload.STREAM_CODEC,
                PlaceProgressPayload::handleClient
        );
    }
}
