package com.l1ght.ebe;

import com.l1ght.ebe.command.EBECommands;
import com.l1ght.ebe.clientbridge.EBEClientBridge;
import com.l1ght.ebe.config.EBEServerConfig;
import com.l1ght.ebe.item.ArchitectToolboxItem;
import com.l1ght.ebe.item.RemoteItem;
import com.l1ght.ebe.network.EBENetwork;
import com.l1ght.ebe.network.AdminActionPayload;
import com.l1ght.ebe.network.WorkgroupNetworkSync;
import com.l1ght.ebe.network.WorkgroupSyncPayload;
import com.l1ght.ebe.server.ServerSettingsManager;
import com.l1ght.ebe.server.library.ServerFileLibraryManager;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.placement.PlaceAllQueue;
import com.l1ght.ebe.server.placement.PrinterPlacementQueue;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(EBEMod.MOD_ID)
public class EBEMod {
    public static final String MOD_ID = "ebe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredItem<ArchitectToolboxItem> ARCHITECT_TOOLBOX =
            ITEMS.register("architect_toolbox", ArchitectToolboxItem::new);

    public static final DeferredItem<RemoteItem> REMOTE =
            ITEMS.register("remote", RemoteItem::new);

    public static final Supplier<CreativeModeTab> EBE_TAB = CREATIVE_MODE_TABS.register("ebe_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ebe"))
                    .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
                    .icon(() -> new ItemStack(ARCHITECT_TOOLBOX.get()))
                    .displayItems((params, output) -> {
                        output.accept(ARCHITECT_TOOLBOX.get());
                        output.accept(REMOTE.get());
                    })
                    .build());

    public EBEMod(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        EBEClientBridge.registerClientConfig(modContainer);
        EBEServerConfig.register(modContainer);

        EBENetwork.register(modEventBus);

        modEventBus.addListener(this::onLoadComplete);

        NeoForge.EVENT_BUS.register(this);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(EBEClientBridge::ensureSchematicDir);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PermissionManager.load();
        ServerSettingsManager.load();
        ServerFileLibraryManager.load();
        WorkgroupManager.load();
        LOGGER.info("EasyBlockEditor server starting");
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        boolean permissionsChanged = PermissionManager.pollHotReload();
        boolean settingsChanged = ServerSettingsManager.pollHotReload();
        boolean libraryChanged = ServerFileLibraryManager.pollHotReload();
        boolean workgroupsChanged = WorkgroupManager.pollHotReload();
        boolean projectionExpired = WorkgroupManager.expireProjections(ServerSettingsManager.get().projectionTimeoutSeconds);
        boolean printTickChanged = WorkgroupPrintSessionManager.tick(event.getServer().overworld().getGameTime());
        PlaceAllQueue.tick();
        PrinterPlacementQueue.tick();
        var printChangedGroups = WorkgroupPrintSessionManager.consumeChangedGroupsForBroadcast();

        if (permissionsChanged || settingsChanged || libraryChanged || workgroupsChanged || projectionExpired || printTickChanged || !printChangedGroups.isEmpty()) {
            for (var player : event.getServer().getPlayerList().getPlayers()) {
                if (player.hasPermissions(2)) {
                    AdminActionPayload.sendAdminSync(player);
                }
                if (workgroupsChanged || projectionExpired || printTickChanged) {
                    PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
                }
            }
            for (var groupId : printChangedGroups) {
                WorkgroupNetworkSync.syncGroup(event.getServer(), groupId);
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EBECommands.register(event.getDispatcher());
    }
}
