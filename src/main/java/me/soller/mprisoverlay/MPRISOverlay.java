package me.soller.mprisoverlay;

import java.io.File;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = MPRISOverlay.MODID,
    name = "MPRIS Overlay",
    version = MPRISOverlay.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    guiFactory = "me.soller.mprisoverlay.client.GuiFactory")
public class MPRISOverlay {

    public static final String MODID = "mprisoverlay";
    public static final String VERSION = "1.2.0-alpha";

    public static final Logger LOGGER = LogManager.getLogger("MPRIS Overlay");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        if (event.getSide() == Side.CLIENT) {
            Config.init(new File(event.getModConfigurationDirectory(), MODID + ".cfg"));
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        // client classes live in their own class so a dedicated server never loads them
        if (event.getSide() == Side.CLIENT) {
            ClientInit.init();
        }
    }

    // a client-only mod: servers without it must not refuse the player
    @NetworkCheckHandler
    public boolean checkNetwork(Map<String, String> mods, Side side) {
        return true;
    }
}
