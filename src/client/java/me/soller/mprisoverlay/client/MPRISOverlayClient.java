package me.soller.mprisoverlay.client;

import me.soller.mprisoverlay.MPRISOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class MPRISOverlayClient implements ClientModInitializer {

	private static final MPRISService mpris = new MPRISService();

	@Override
	public void onInitializeClient() {

		Config.load();

		mpris.start();

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				MPRISOverlay.id("player"),
				(context, tickCounter) -> HudRenderer.render(context, mpris)
		);
	}
}
