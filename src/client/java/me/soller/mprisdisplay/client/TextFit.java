package me.soller.mprisdisplay.client;

import net.minecraft.client.gui.Font;

/** Shortens text with "..." so it fits in a given pixel width. */
public final class TextFit {

    private static final String ELLIPSIS = "...";

    private TextFit() {
    }

    public static String fit(Font font, String text, int maxWidth) {

        if (font.width(text) <= maxWidth) {
            return text;
        }

        int available = maxWidth - font.width(ELLIPSIS);

        if (available <= 0) {
            return ELLIPSIS;
        }

        // binary search instead of measuring the string once per character
        int low = 0;
        int high = text.length();

        while (low < high) {

            int mid = (low + high + 1) >>> 1;

            if (font.width(text.substring(0, mid)) <= available) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        // do not cut an emoji / surrogate pair in half
        if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) {
            low--;
        }

        return text.substring(0, low).stripTrailing() + ELLIPSIS;
    }

    /**
     * Remembers the last result: the HUD asks for the same text every frame,
     * so measuring it again each time would be wasted work.
     */
    public static final class Cache {

        private Font font;
        private String text;
        private int maxWidth;
        private String result = "";

        public String get(Font font, String text, int maxWidth) {

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
