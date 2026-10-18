package me.soller.mprisoverlay.client;

import me.soller.mprisoverlay.MPRISOverlay;

import java.util.List;
import java.util.Locale;

/**
 * Polls playerctl on a background thread and keeps the most relevant
 * song available for the HUD.
 */
public final class MPRISService {

    public enum Status {
        OK,
        PLAYERCTL_MISSING,
        UNSUPPORTED_OS
    }

    private static final String SEPARATOR = "\u001f";

    // one playerctl call returns every player, one line per player
    private static final String FORMAT = String.join(
            SEPARATOR,
            "{{playerName}}",
            "{{status}}",
            "{{mpris:artUrl}}",
            "{{title}}",
            "{{artist}}",
            "{{mpris:length}}",
            "{{position}}"
    );

    private static final int FIELD_COUNT = 7;

    private static final long POLL_INTERVAL_MS = 500;
    private static final long MISSING_RETRY_MS = 10_000;

    // the song that should be displayed, null if nothing is playing
    private volatile Song currentSong;
    private volatile Status status = Status.OK;

    private Thread thread;
    private String lastError;

    public synchronized void start() {

        if (thread != null) {
            return;
        }

        if (!isSupportedOs()) {
            status = Status.UNSUPPORTED_OS;
            MPRISOverlay.LOGGER.info("MPRIS is only available on Linux/BSD, the overlay will stay idle");
            return;
        }

        thread = new Thread(this::loop, "mprisoverlay-updater");

        // must be a daemon, otherwise the JVM cannot exit and the shutdown watchdog crashes the client
        thread.setDaemon(true);

        thread.start();
    }

    public synchronized void stop() {

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private static boolean isSupportedOs() {

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        return os.contains("linux") || os.contains("bsd");
    }

    private void loop() {

        while (!Thread.currentThread().isInterrupted()) {

            try {

                poll();
                lastError = null;

            } catch (RuntimeException e) {

                // log each distinct problem only once, this loop runs forever
                String message = e.toString();

                if (!message.equals(lastError)) {
                    MPRISOverlay.LOGGER.warn("Failed to read player information", e);
                    lastError = message;
                }

                currentSong = null;
            }

            try {

                Thread.sleep(Playerctl.isMissing() ? MISSING_RETRY_MS : POLL_INTERVAL_MS);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            }
        }
    }

    private void poll() {

        List<String> lines = Playerctl.run(
                "playerctl",
                "--all-players",
                "metadata",
                "--format",
                FORMAT
        );

        if (Playerctl.isMissing()) {
            status = Status.PLAYERCTL_MISSING;
            currentSong = null;
            return;
        }

        status = Status.OK;

        Config config = Config.get();
        List<String> blacklist = config.blacklist;
        boolean showPaused = config.showPaused;

        Song paused = null;
        Song playing = null;

        for (String line : lines) {

            String[] fields = line.split(SEPARATOR, -1);

            if (fields.length < FIELD_COUNT) {
                continue;
            }

            String player = fields[0].trim();

            if (isBlacklisted(player, blacklist)) {
                continue;
            }

            String title = fields[3].trim();

            if (title.isEmpty()) {
                continue;
            }

            String state = fields[1].trim();
            boolean isPlaying = "Playing".equalsIgnoreCase(state);
            boolean isPaused = "Paused".equalsIgnoreCase(state);

            if (!isPlaying && !isPaused) {
                continue;
            }

            Song song = new Song(
                    title,
                    fields[4].trim(),
                    fields[2].trim(),
                    parseLong(fields[6]),
                    parseLong(fields[5]),
                    isPlaying
            );

            if (isPlaying) {
                playing = song;
                break;
            }

            if (paused == null) {
                paused = song;
            }
        }

        if (playing != null) {
            currentSong = playing;
        } else {
            currentSong = showPaused ? paused : null;
        }
    }

    private static boolean isBlacklisted(String player, List<String> blacklist) {

        String name = player.toLowerCase(Locale.ROOT);

        for (String entry : blacklist) {

            if (entry != null && !entry.isBlank() && name.startsWith(entry.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    // players sometimes report nothing, or garbage, for length and position
    private static long parseLong(String value) {

        try {

            return Long.parseLong(value.trim());

        } catch (NumberFormatException e) {

            return 0L;
        }
    }

    /** The song that should be displayed, or null if nothing is playing. */
    public Song getSong() {
        return currentSong;
    }

    public Status getStatus() {
        return status;
    }
}
