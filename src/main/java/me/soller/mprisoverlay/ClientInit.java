package me.soller.mprisoverlay;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import me.soller.mprisoverlay.client.ConfigEventHandler;
import me.soller.mprisoverlay.client.HudRenderer;
import me.soller.mprisoverlay.client.MPRISService;

final class ClientInit {

    private ClientInit() {}

    static void init() {

        MPRISService service = new MPRISService();
        service.start();

        MinecraftForge.EVENT_BUS.register(new HudRenderer(service));

        // ConfigChangedEvent is posted on the FML bus, not the Forge one
        FMLCommonHandler.instance()
            .bus()
            .register(new ConfigEventHandler());
    }
}
