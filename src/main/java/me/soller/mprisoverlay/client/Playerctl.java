package me.soller.mprisoverlay.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.soller.mprisoverlay.MPRISOverlay;

/**
 * Thin wrapper around the playerctl command line tool.
 * Never blocks for more than a few seconds and never throws.
 */
final class Playerctl {

    private static final long TIMEOUT_SECONDS = 3;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    // true while the playerctl binary cannot be started (not installed / not in PATH)
    private static volatile boolean missing;

    private Playerctl() {}

    static boolean isMissing() {
        return missing;
    }

    /**
     * Runs a command and returns its non-empty stdout lines.
     * Returns an empty list if the command fails, times out or cannot be started.
     */
    static List<String> run(String... command) {

        Process process;

        try {

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
            process = builder.start();

        } catch (IOException e) {

            if (!missing) {
                MPRISOverlay.LOGGER.warn("Could not start '{}': {}", command[0], e.getMessage());
            }

            missing = true;

            return Collections.emptyList();
        }

        missing = false;

        try {

            // we never write to it
            process.getOutputStream()
                .close();

            // playerctl output is tiny, so it is safe to wait for exit before reading
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Collections.emptyList();
            }

            String output = new String(readAll(process.getInputStream()), UTF_8);

            List<String> lines = new ArrayList<String>();

            for (String line : output.split("\\r?\\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }

            return lines;

        } catch (IOException e) {

            process.destroyForcibly();
            return Collections.emptyList();

        } catch (InterruptedException e) {

            process.destroyForcibly();
            Thread.currentThread()
                .interrupt();
            return Collections.emptyList();
        }
    }

    // InputStream.readAllBytes needs Java 9
    private static byte[] readAll(InputStream in) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        return out.toByteArray();
    }
}
