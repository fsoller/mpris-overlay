package me.soller.mprisdisplay.client;

import me.soller.mprisdisplay.MPRISDisplay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class MPRISDisplayClient implements ClientModInitializer {

	private static final MPRISService mpris = new MPRISService();

	@Override
	public void onInitializeClient() {

		Config.load();

		mpris.start();

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				MPRISDisplay.id("player"),
				(context, tickCounter) -> HudRenderer.render(context, mpris)
		);
	}
}
