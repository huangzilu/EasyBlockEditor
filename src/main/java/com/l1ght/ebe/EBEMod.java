package com.l1ght.ebe;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(EBEMod.MOD_ID)
public class EBEMod {
    public static final String MOD_ID = "ebe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EBEMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("EasyBlockEditor server starting");
    }
}
