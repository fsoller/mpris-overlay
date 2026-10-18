package me.soller.mprisoverlay.client;

import me.soller.mprisoverlay.MPRISOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class HudRenderer {

    // hud size
    private static final int HUD_WIDTH = 250;
    private static final int HUD_HEIGHT = 64;
    private static final int PADDING = 6;

    private static final int COVER_SIZE = HUD_HEIGHT - PADDING * 2;

    private static final int TEXT_X =
            COVER_SIZE + PADDING * 2;

    private static final int BAR_Y = 42;

    static final int BAR_WIDTH =
            HUD_WIDTH - COVER_SIZE - PADDING * 3;

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

    private static final AlbumCoverManager coverManager =
            new AlbumCoverManager();

    private static final TextFit.Cache titleCache = new TextFit.Cache();
    private static final TextFit.Cache artistCache = new TextFit.Cache();

    // an overlay should never be able to take the game down with it
    private static boolean failed;

    // 1. BACKGROUND
    private static void drawBackground(GuiGraphicsExtractor context) {
        context.fill(
                0,
                0,
                HUD_WIDTH,
                HUD_HEIGHT,
                BACKGROUND_COLOR
        );
    }

    // 2. ALBUM COVER
    private static void drawAlbumCover(GuiGraphicsExtractor context, Identifier cover) {

        if (cover == null) {

            // no cover (yet): keep the layout instead of leaving a hole
            context.fill(
                    PADDING,
                    PADDING,
                    PADDING + COVER_SIZE,
                    PADDING + COVER_SIZE,
                    COVER_FALLBACK_COLOR
            );

            return;
        }

        context.blit(
                RenderPipelines.GUI_TEXTURED,
                cover,
                PADDING,
                PADDING,
                0,
                0,
                COVER_SIZE,
                COVER_SIZE,
                COVER_SIZE,
                COVER_SIZE
        );
    }

    // 3. TRACK TITLE AND ARTIST
    private static void drawSongInfo(
            GuiGraphicsExtractor context,
            Font font,
            String title,
            String artist,
            int titleColor
    ) {

        // increases the font size a little bit
        context.pose().pushMatrix();
        context.pose().scale(TITLE_SCALE, TITLE_SCALE);

        int scaledWidth = (int) (BAR_WIDTH / TITLE_SCALE);

        context.text(
                font,
                titleCache.get(font, title, scaledWidth),
                (int) (TEXT_X / TITLE_SCALE),
                (int) (10 / TITLE_SCALE),
                titleColor,
                false
        );

        // back to normal size
        context.pose().popMatrix();

        if (!artist.isEmpty()) {

            String line = Component.translatable("mprisoverlay.hud.by", artist).getString();

            context.text(
                    font,
                    artistCache.get(font, line, BAR_WIDTH),
                    TEXT_X,
                    24,
                    SECONDARY_TEXT_COLOR,
                    false
            );
        }
    }

    // 4. PROGRESS BAR
    private static void drawProgressBar(
            GuiGraphicsExtractor context,
            Song song,
            int accentColor
    ) {

        context.fill(
                TEXT_X,
                BAR_Y,
                TEXT_X + BAR_WIDTH,
                BAR_Y + BAR_HEIGHT,
                BAR_BACKGROUND
        );

        int progressWidth =
                (int) (BAR_WIDTH * song.getProgress());

        // a paused track gets a dimmer bar
        int color = song.isPlaying()
                ? accentColor
                : (accentColor & 0x00FFFFFF) | 0x80000000;

        context.fill(
                TEXT_X,
                BAR_Y,
                TEXT_X + progressWidth,
                BAR_Y + BAR_HEIGHT,
                color
        );
    }

    // 5. TIME
    private static void drawTime(
            GuiGraphicsExtractor context,
            Font font,
            Song song
    ) {

        context.text(
                font,
                song.getFormattedPosition(),
                TEXT_X,
                50,
                SECONDARY_TEXT_COLOR,
                false
        );

        String duration = song.getFormattedDuration();

        int durationX = TEXT_X + BAR_WIDTH - font.width(duration);

        context.text(
                font,
                duration,
                durationX,
                50,
                SECONDARY_TEXT_COLOR,
                false
        );
    }

    public static void render(
            GuiGraphicsExtractor context,
            MPRISService service
    ) {

        if (failed) {
            return;
        }

        try {

            draw(context, service);

        } catch (RuntimeException e) {

            failed = true;
            MPRISOverlay.LOGGER.error("The overlay failed to render and was disabled until the game restarts", e);
        }
    }

    private static void draw(
            GuiGraphicsExtractor context,
            MPRISService service
    ) {

        Config config = Config.get();

        if (!config.renderOverlay) {
            return;
        }

        Song song = service.getSong();
        MPRISService.Status status = service.getStatus();

        // when there is a problem (no playerctl, wrong OS) always say so
        if (song == null && config.hideWhenIdle && status == MPRISService.Status.OK) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        Identifier cover = coverManager.update(
                song == null
                        ? AlbumCoverManager.PLACEHOLDER
                        : song.getCoverUrl()
        );

        context.pose().pushMatrix();

        try {

            context.pose().translate(
                    config.hudX,
                    config.hudY
            );

            context.pose().scale(
                    config.hudScale,
                    config.hudScale
            );

            drawBackground(context);
            drawAlbumCover(context, cover);

            if (song == null) {

                drawSongInfo(context, font, idleMessage(status), "", TEXT_COLOR);

            } else {

                drawSongInfo(
                        context,
                        font,
                        song.getTitle(),
                        song.getArtist(),
                        song.isPlaying() ? TEXT_COLOR : PAUSED_TEXT_COLOR
                );

                // live streams have no length, there is nothing to show a bar for
                if (song.hasDuration()) {
                    drawProgressBar(context, song, config.accentColor);
                    drawTime(context, font, song);
                }
            }

        } finally {

            context.pose().popMatrix();
        }
    }

    private static String idleMessage(MPRISService.Status status) {

        String key = switch (status) {
            case PLAYERCTL_MISSING -> "mprisoverlay.hud.no_playerctl";
            case UNSUPPORTED_OS -> "mprisoverlay.hud.unsupported_os";
            case OK -> "mprisoverlay.hud.idle";
        };

        return Component.translatable(key).getString();
    }
}
