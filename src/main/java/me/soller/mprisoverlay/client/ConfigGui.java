package me.soller.mprisoverlay.client;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import me.soller.mprisoverlay.Config;
import me.soller.mprisoverlay.MPRISOverlay;

/** The settings screen: Mods -> MPRIS Overlay -> Config. */
public class ConfigGui extends GuiConfig {

    public ConfigGui(GuiScreen parent) {

        super(
            parent,
            elements(),
            MPRISOverlay.MODID,
            false,
            false,
            StatCollector.translateToLocal(MPRISOverlay.MODID + ".config.title"));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> elements() {

        return new ConfigElement(
            Config.getConfiguration()
                .getCategory(Configuration.CATEGORY_GENERAL)).getChildElements();
    }
}
