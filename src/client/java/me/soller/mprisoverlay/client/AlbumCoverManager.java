package me.soller.mprisoverlay.client;

import com.mojang.blaze3d.platform.NativeImage;
import me.soller.mprisoverlay.MPRISOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loads album covers without ever blocking the render thread.
 *
 * {@link #update(String)} is meant to be called every frame from the render
 * thread. It only does work when the requested cover changes: the image is then
 * downloaded and decoded in the background and uploaded to the GPU on the
 * render thread once it is ready.
 */
public class AlbumCoverManager {

    /** Cover key for the bundled placeholder image. */
    public static final String PLACEHOLDER = "builtin:textures/boymoder.png";

    private static final String BUILTIN_PREFIX = "builtin:";

    // covers are drawn at ~52px, anything bigger is wasted memory
    private static final int MAX_TEXTURE_SIZE = 256;
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mprisoverlay-cover-loader");
        thread.setDaemon(true);
        return thread;
    });

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // incremented on every new request, so results of outdated requests can be dropped
    private final AtomicLong generation = new AtomicLong();

    // everything below is only touched from the render thread
    private String requestedKey;
    private Identifier currentTexture;
    private int textureCounter;

    /**
     * @param key cover URL (http, https, file) or {@link #PLACEHOLDER}; null or blank for no cover
     * @return the texture to draw, or null while there is none (loading, no cover or failed)
     */
    public Identifier update(String key) {

        String normalized = key == null ? "" : key;

        if (!normalized.equals(requestedKey)) {

            requestedKey = normalized;

            long id = generation.incrementAndGet();

            // never show the previous track's cover while the new one loads
            releaseCurrent();

            if (!normalized.isEmpty()) {
                executor.execute(() -> load(normalized, id));
            }
        }

        return currentTexture;
    }

    private void releaseCurrent() {

        if (currentTexture != null) {

            Minecraft.getInstance().getTextureManager().release(currentTexture);
            currentTexture = null;
        }
    }

    // ---- background thread -------------------------------------------------

    private void load(String key, long requestId) {

        NativeImage nativeImage;

        try {

            byte[] data = readBytes(key);

            if (data == null || requestId != generation.get()) {
                return;
            }

            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));

            if (decoded == null) {
                // unsupported format (webp for example)
                MPRISOverlay.LOGGER.debug("Unsupported cover image: {}", key);
                return;
            }

            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(toSquareThumbnail(decoded), "png", png);

            nativeImage = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return;

        } catch (Exception e) {

            MPRISOverlay.LOGGER.debug("Could not load cover {}", key, e);
            return;
        }

        NativeImage image = nativeImage;

        // textures must be created on the render thread
        Minecraft.getInstance().execute(() -> {

            if (requestId != generation.get()) {
                image.close();
                return;
            }

            install(image);
        });
    }

    private byte[] readBytes(String key) throws Exception {

        if (key.startsWith(BUILTIN_PREFIX)) {

            String path = "/assets/" + MPRISOverlay.MOD_ID + "/" + key.substring(BUILTIN_PREFIX.length());

            try (InputStream in = AlbumCoverManager.class.getResourceAsStream(path)) {
                return in == null ? null : in.readAllBytes();
            }
        }

        if (key.startsWith("file:")) {

            Path path = Path.of(URI.create(key));

            if (!Files.isRegularFile(path) || Files.size(path) > MAX_DOWNLOAD_BYTES) {
                return null;
            }

            return Files.readAllBytes(path);
        }

        if (key.startsWith("http://") || key.startsWith("https://")) {

            HttpRequest request = HttpRequest.newBuilder(URI.create(key))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream in = response.body()) {

                if (response.statusCode() != 200) {
                    return null;
                }

                byte[] data = in.readNBytes(MAX_DOWNLOAD_BYTES + 1);

                return data.length > MAX_DOWNLOAD_BYTES ? null : data;
            }
        }

        return null;
    }

    /**
     * Center-crops to a square (video thumbnails are 16:9) and scales down
     * with proper interpolation, which looks much better than letting the GPU
     * shrink a 640px image to 52px.
     */
    private static BufferedImage toSquareThumbnail(BufferedImage source) {

        int width = source.getWidth();
        int height = source.getHeight();

        int side = Math.min(width, height);
        int x = (width - side) / 2;
        int y = (height - side) / 2;

        int target = Math.min(side, MAX_TEXTURE_SIZE);

        BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = result.createGraphics();

        try {

            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            graphics.drawImage(source, 0, 0, target, target, x, y, x + side, y + side, null);

        } finally {

            graphics.dispose();
        }

        return result;
    }

    // ---- render thread -----------------------------------------------------

    private void install(NativeImage image) {

        TextureManager textures = Minecraft.getInstance().getTextureManager();

        // a new id for every cover: reusing one id makes the manager close the
        // texture that may still be referenced by the frame being built
        int number = ++textureCounter;

        Identifier id = MPRISOverlay.id("cover/" + number);

        textures.register(id, new DynamicTexture(() -> "mprisoverlay_cover_" + number, image));

        Identifier previous = currentTexture;
        currentTexture = id;

        if (previous != null) {
            textures.release(previous);
        }
    }
}
