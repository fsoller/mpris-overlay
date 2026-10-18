package me.soller.mprisdisplay.client;

import java.util.Locale;

/**
 * Immutable snapshot of what a media player reported at one point in time.
 * While the track is playing, the position is extrapolated from the moment
 * the snapshot was taken, so the progress bar moves smoothly between polls.
 */
public final class Song {

    private final String title;
    private final String artist;
    private final String coverUrl;
    private final long positionMicros;
    private final long durationMicros;
    private final boolean playing;
    private final long capturedAtNanos;

    public Song(
            String title,
            String artist,
            String coverUrl,
            long positionMicros,
            long durationMicros,
            boolean playing
    ) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.coverUrl = (coverUrl == null || coverUrl.isBlank()) ? null : coverUrl;
        this.positionMicros = Math.max(0L, positionMicros);
        this.durationMicros = Math.max(0L, durationMicros);
        this.playing = playing;
        this.capturedAtNanos = System.nanoTime();
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    /** Cover art URL as reported by the player, or null if there is none. */
    public String getCoverUrl() {
        return coverUrl;
    }

    public boolean isPlaying() {
        return playing;
    }

    /** Live streams and some players do not report a length. */
    public boolean hasDuration() {
        return durationMicros > 0;
    }

    public long getCurrentPositionMicros() {
        long position = positionMicros;

        if (playing) {
            position += (System.nanoTime() - capturedAtNanos) / 1_000L;
        }

        if (durationMicros > 0) {
            position = Math.min(position, durationMicros);
        }

        return position;
    }

    public float getProgress() {
        if (durationMicros <= 0) {
            return 0f;
        }

        float ratio = (float) getCurrentPositionMicros() / (float) durationMicros;

        return Math.max(0f, Math.min(1f, ratio));
    }

    public String getFormattedPosition() {
        return formatTime(getCurrentPositionMicros() / 1_000_000L);
    }

    public String getFormattedDuration() {
        return formatTime(durationMicros / 1_000_000L);
    }

    private static String formatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }

        return String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }
}
