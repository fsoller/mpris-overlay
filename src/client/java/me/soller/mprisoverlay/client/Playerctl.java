package me.soller.mprisoverlay.client;

import me.soller.mprisoverlay.MPRISOverlay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the playerctl command line tool.
 * Never blocks for more than a few seconds and never throws.
 */
final class Playerctl {

    private static final long TIMEOUT_SECONDS = 3;

    // true while the playerctl binary cannot be started (not installed / not in PATH)
    private static volatile boolean missing;

    private Playerctl() {
    }

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
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();

        } catch (IOException e) {

            if (!missing) {
                MPRISOverlay.LOGGER.warn("Could not start '{}': {}", command[0], e.getMessage());
            }

            missing = true;

            return List.of();
        }

        missing = false;

        try {

            // we never write to it
            process.getOutputStream().close();

            // playerctl output is tiny, so it is safe to wait for exit before reading
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return List.of();
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );

            List<String> lines = new ArrayList<>();

            for (String line : output.split("\\R")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }

            return lines;

        } catch (IOException e) {

            process.destroyForcibly();
            return List.of();

        } catch (InterruptedException e) {

            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return List.of();
        }
    }
}
