package me.soller.mprisoverlay.client;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.soller.mprisoverlay.Config;
import me.soller.mprisoverlay.MPRISOverlay;

/** Applies (and saves) the settings when the config screen is closed. */
public class ConfigEventHandler {

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {

        if (MPRISOverlay.MODID.equals(event.modID)) {
            Config.sync();
        }
    }
}
