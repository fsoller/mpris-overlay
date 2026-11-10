package me.soller.mprisoverlay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.soller.mprisoverlay.Config;
import me.soller.mprisoverlay.MPRISOverlay;

public class HudRenderer {

    // hud size
    private static final int HUD_WIDTH = 250;
    private static final int HUD_HEIGHT = 64;
    private static final int PADDING = 6;

    private static final int COVER_SIZE = HUD_HEIGHT - PADDING * 2;

    private static final int TEXT_X = COVER_SIZE + PADDING * 2;

    private static final int BAR_Y = 42;
    private static final int BAR_WIDTH = HUD_WIDTH - COVER_SIZE - PADDING * 3;
    private static final int BAR_HEIGHT = 2;

    // hud colors
    private static final int BACKGROUND_COLOR = 0x80000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PAUSED_TEXT_COLOR = 0xFFBBBBBB;
    private static final int SECONDARY_TEXT_COLOR = 0x99FFFFFF;
    private static final int BAR_BACKGROUND = 0x55FFFFFF;
    private static final int COVER_FALLBACK_COLOR = 0x30FFFFFF;

    // track title
    private static final float TITLE_SCALE = 1.4f;

    private final MPRISService service;
    private final CoverManager covers = new CoverManager();

    private final TextFit.Cache titleCache = new TextFit.Cache();
    private final TextFit.Cache artistCache = new TextFit.Cache();

    // an overlay should never be able to take the game down with it
    private boolean failed;

    public HudRenderer(MPRISService service) {
        this.service = service;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {

        // ALL is posted once, after everything vanilla has been drawn
        if (failed || event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        try {

            draw();

        } catch (RuntimeException e) {

            failed = true;
            MPRISOverlay.LOGGER.error("The overlay failed to render and was disabled until the game restarts", e);
        }
    }

    private void draw() {

        if (!Config.renderOverlay) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();

        // F1
        if (minecraft.gameSettings.hideGUI) {
            return;
        }

        Song song = service.getSong();
        MPRISService.Status status = service.getStatus();

        // when there is a problem (no playerctl, wrong OS) always say so
        if (song == null && Config.hideWhenIdle && status == MPRISService.Status.OK) {
            return;
        }

        FontRenderer font = minecraft.fontRenderer;

        ResourceLocation cover = covers.update(song == null ? CoverManager.PLACEHOLDER : song.getCoverUrl());

        GL11.glPushMatrix();

        try {

            GL11.glTranslatef(Config.hudX, Config.hudY, 0f);
            GL11.glScalef(Config.hudScale, Config.hudScale, 1f);

            // 1. background
            Gui.drawRect(0, 0, HUD_WIDTH, HUD_HEIGHT, BACKGROUND_COLOR);

            // 2. album cover
            drawAlbumCover(minecraft, cover);

            if (song == null) {

                drawSongInfo(font, idleMessage(status), "", TEXT_COLOR);

            } else {

                drawSongInfo(
                    font,
                    song.getTitle(),
                    song.getArtist(),
                    song.isPlaying() ? TEXT_COLOR : PAUSED_TEXT_COLOR);

                // live streams have no length, there is nothing to show a bar for
                if (song.hasDuration()) {
                    drawProgressBar(song);
                    drawTime(font, song);
                }
            }

        } finally {

            GL11.glPopMatrix();

            // leave the state the way the vanilla hud expects it
            GL11.glColor4f(1f, 1f, 1f, 1f);
            enableBlend();
        }
    }

    // Gui.drawRect turns blending off when it is done, text and textures need it on
    private static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void drawAlbumCover(Minecraft minecraft, ResourceLocation cover) {

        if (cover == null) {

            // no cover (yet): keep the layout instead of leaving a hole
            Gui.drawRect(PADDING, PADDING, PADDING + COVER_SIZE, PADDING + COVER_SIZE, COVER_FALLBACK_COLOR);
            return;
        }

        enableBlend();
        GL11.glColor4f(1f, 1f, 1f, 1f);

        minecraft.getTextureManager()
            .bindTexture(cover);

        // smooth scaling, the default for gui textures is nearest neighbour
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(PADDING, PADDING + COVER_SIZE, 0, 0, 1);
        tessellator.addVertexWithUV(PADDING + COVER_SIZE, PADDING + COVER_SIZE, 0, 1, 1);
        tessellator.addVertexWithUV(PADDING + COVER_SIZE, PADDING, 0, 1, 0);
        tessellator.addVertexWithUV(PADDING, PADDING, 0, 0, 0);
        tessellator.draw();
    }

    private void drawSongInfo(FontRenderer font, String title, String artist, int titleColor) {

        enableBlend();

        // increases the font size a little bit
        GL11.glPushMatrix();
        GL11.glScalef(TITLE_SCALE, TITLE_SCALE, 1f);

        int scaledWidth = (int) (BAR_WIDTH / TITLE_SCALE);

        font.drawString(
            titleCache.get(font, title, scaledWidth),
            (int) (TEXT_X / TITLE_SCALE),
            (int) (10 / TITLE_SCALE),
            titleColor);

        // back to normal size
        GL11.glPopMatrix();

        if (!artist.isEmpty()) {

            String line = StatCollector.translateToLocalFormatted("mprisoverlay.hud.by", artist);

            font.drawString(artistCache.get(font, line, BAR_WIDTH), TEXT_X, 24, SECONDARY_TEXT_COLOR);
        }
    }

    private static void drawProgressBar(Song song) {

        Gui.drawRect(TEXT_X, BAR_Y, TEXT_X + BAR_WIDTH, BAR_Y + BAR_HEIGHT, BAR_BACKGROUND);

        int progressWidth = (int) (BAR_WIDTH * song.getProgress());

        // a paused track gets a dimmer bar
        int color = song.isPlaying() ? Config.accentColor : (Config.accentColor & 0x00FFFFFF) | 0x80000000;

        Gui.drawRect(TEXT_X, BAR_Y, TEXT_X + progressWidth, BAR_Y + BAR_HEIGHT, color);
    }

    private static void drawTime(FontRenderer font, Song song) {

        enableBlend();

        font.drawString(song.getFormattedPosition(), TEXT_X, 50, SECONDARY_TEXT_COLOR);

        String duration = song.getFormattedDuration();

        font.drawString(duration, TEXT_X + BAR_WIDTH - font.getStringWidth(duration), 50, SECONDARY_TEXT_COLOR);
    }

    private static String idleMessage(MPRISService.Status status) {

        String key;

        switch (status) {
            case PLAYERCTL_MISSING:
                key = "mprisoverlay.hud.no_playerctl";
                break;
            case UNSUPPORTED_OS:
                key = "mprisoverlay.hud.unsupported_os";
                break;
            default:
                key = "mprisoverlay.hud.idle";
                break;
        }

        return StatCollector.translateToLocal(key);
    }
}
