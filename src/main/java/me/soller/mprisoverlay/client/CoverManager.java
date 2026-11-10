package me.soller.mprisoverlay.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import me.soller.mprisoverlay.MPRISOverlay;

/**
 * Loads album covers without ever blocking the render thread.
 *
 * {@link #update(String)} is meant to be called every frame from the render
 * thread. It only does work when the requested cover changes: the image is then
 * downloaded and decoded in the background and uploaded to the GPU on the
 * render thread once it is ready.
 */
public class CoverManager {

    /** Cover key for the bundled placeholder image. */
    public static final String PLACEHOLDER = "builtin:placeholder";

    private static final ResourceLocation PLACEHOLDER_TEXTURE = new ResourceLocation(
        MPRISOverlay.MODID,
        "textures/boymoder.png");

    // covers are drawn at ~52px, anything bigger is wasted memory
    private static final int MAX_TEXTURE_SIZE = 256;
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "mprisoverlay-cover-loader");
            thread.setDaemon(true);
            return thread;
        }
    });

    // incremented on every new request, so results of outdated requests can be dropped
    private final AtomicLong generation = new AtomicLong();

    // work that must run on the render thread (creating textures)
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<Runnable>();

    // everything below is only touched from the render thread
    private String requestedKey;
    private ResourceLocation dynamicTexture;

    /**
     * @param key cover URL (http, https, file) or {@link #PLACEHOLDER}; null or blank for no cover
     * @return the texture to draw, or null while there is none (loading, no cover or failed)
     */
    public ResourceLocation update(String key) {

        Runnable task;

        while ((task = pending.poll()) != null) {
            task.run();
        }

        final String normalized = key == null ? "" : key;

        if (!normalized.equals(requestedKey)) {

            requestedKey = normalized;

            final long id = generation.incrementAndGet();

            // never show the previous track's cover while the new one loads
            releaseDynamic();

            if (!normalized.isEmpty() && !PLACEHOLDER.equals(normalized)) {

                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        load(normalized, id);
                    }
                });
            }
        }

        if (PLACEHOLDER.equals(requestedKey)) {
            return PLACEHOLDER_TEXTURE;
        }

        return dynamicTexture;
    }

    private void releaseDynamic() {

        if (dynamicTexture != null) {

            Minecraft.getMinecraft()
                .getTextureManager()
                .deleteTexture(dynamicTexture);
            dynamicTexture = null;
        }
    }

    // ---- background thread -------------------------------------------------

    private void load(String key, final long requestId) {

        final BufferedImage thumbnail;

        try {

            byte[] data = readBytes(key);

            if (data == null || requestId != generation.get()) {
                return;
            }

            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));

            if (decoded == null) {
                // unsupported format (webp for example)
                return;
            }

            thumbnail = toSquareThumbnail(decoded);

        } catch (Exception e) {

            MPRISOverlay.LOGGER.debug("Could not load cover {}", key, e);
            return;
        }

        // textures must be created on the render thread
        pending.add(new Runnable() {

            @Override
            public void run() {

                if (requestId == generation.get()) {
                    install(thumbnail);
                }
            }
        });
    }

    private byte[] readBytes(String key) throws Exception {

        if (key.startsWith("file:")) {

            File file = new File(new URI(key));

            if (!file.isFile() || file.length() > MAX_DOWNLOAD_BYTES) {
                return null;
            }

            return Files.readAllBytes(file.toPath());
        }

        if (key.startsWith("http://") || key.startsWith("https://")) {

            HttpURLConnection connection = (HttpURLConnection) new URL(key).openConnection();

            try {

                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "MPRISOverlay");

                if (connection.getResponseCode() != 200) {
                    return null;
                }

                InputStream in = connection.getInputStream();

                try {
                    return readLimited(in, MAX_DOWNLOAD_BYTES);
                } finally {
                    in.close();
                }

            } finally {

                connection.disconnect();
            }
        }

        return null;
    }

    // returns null if the stream is bigger than the limit
    private static byte[] readLimited(InputStream in, int limit) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;

        while ((read = in.read(buffer)) != -1) {

            out.write(buffer, 0, read);

            if (out.size() > limit) {
                return null;
            }
        }

        return out.toByteArray();
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

    private void install(BufferedImage image) {

        Minecraft minecraft = Minecraft.getMinecraft();

        // creating a DynamicTexture uploads it to the GPU, so this must be the render thread
        DynamicTexture texture = new DynamicTexture(image);

        ResourceLocation previous = dynamicTexture;

        dynamicTexture = minecraft.getTextureManager()
            .getDynamicTextureLocation("mprisoverlay_cover", texture);

        if (previous != null) {
            minecraft.getTextureManager()
                .deleteTexture(previous);
        }
    }
}
