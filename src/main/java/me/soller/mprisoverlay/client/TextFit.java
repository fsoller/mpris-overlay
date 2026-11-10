package me.soller.mprisoverlay.client;

import net.minecraft.client.gui.FontRenderer;

/** Shortens text with "..." so it fits in a given pixel width. */
public final class TextFit {

    private static final String ELLIPSIS = "...";

    private TextFit() {}

    public static String fit(FontRenderer font, String text, int maxWidth) {

        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        int available = maxWidth - font.getStringWidth(ELLIPSIS);

        if (available <= 0) {
            return ELLIPSIS;
        }

        // binary search instead of measuring the string once per character
        int low = 0;
        int high = text.length();

        while (low < high) {

            int mid = (low + high + 1) >>> 1;

            if (font.getStringWidth(text.substring(0, mid)) <= available) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        // do not cut an emoji / surrogate pair in half
        if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) {
            low--;
        }

        return rtrim(text.substring(0, low)) + ELLIPSIS;
    }

    // String.stripTrailing needs Java 11
    private static String rtrim(String text) {

        int end = text.length();

        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }

        return text.substring(0, end);
    }

    /**
     * Remembers the last result: the HUD asks for the same text every frame,
     * so measuring it again each time would be wasted work.
     */
    public static final class Cache {

        private FontRenderer font;
        private String text;
        private int maxWidth;
        private String result = "";

        public String get(FontRenderer font, String text, int maxWidth) {

            if (font != this.font || maxWidth != this.maxWidth || !text.equals(this.text)) {

                this.font = font;
                this.text = text;
                this.maxWidth = maxWidth;
                this.result = fit(font, text, maxWidth);
            }

            return result;
        }
    }
}
