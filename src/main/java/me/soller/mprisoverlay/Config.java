package me.soller.mprisoverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Settings, stored in config/mprisoverlay.cfg and editable in game
 * (Mods -> MPRIS Overlay -> Config).
 *
 * The fields below are what the rest of the mod reads. {@link #sync()} refreshes
 * them from the file; it runs at startup and every time the config screen is closed.
 */
public final class Config {

    private static final String CATEGORY = Configuration.CATEGORY_GENERAL;
    private static final String LANG_PREFIX = MPRISOverlay.MODID + ".config.";

    private static Configuration configuration;

    public static volatile boolean renderOverlay = true;

    // keep showing the track when the player is paused
    public static volatile boolean showPaused = true;

    // do not draw the card at all when nothing is playing
    public static volatile boolean hideWhenIdle = false;

    public static volatile float hudScale = 1.0f;
    public static volatile int hudX = 10;
    public static volatile int hudY = 10;

    // ARGB
    public static volatile int accentColor = 0xFF00FF00;

    // players whose name starts with one of these are ignored.
    // always replaced as a whole, never modified in place
    public static volatile List<String> blacklist = new ArrayList<String>();

    private Config() {}

    public static void init(File file) {

        configuration = new Configuration(file);
        configuration.load();

        sync();
    }

    /** The config screen edits this object directly. */
    public static Configuration getConfiguration() {
        return configuration;
    }

    public static synchronized void sync() {

        if (configuration == null) {
            return;
        }

        renderOverlay = configuration.getBoolean("renderOverlay", CATEGORY, true, "Show or hide the HUD");
        label("renderOverlay");

        showPaused = configuration
            .getBoolean("showPaused", CATEGORY, true, "Keep showing the track while the player is paused");
        label("showPaused");

        hideWhenIdle = configuration
            .getBoolean("hideWhenIdle", CATEGORY, false, "Hide the HUD completely when nothing is playing");
        label("hideWhenIdle");

        hudScale = configuration.getFloat("hudScale", CATEGORY, 1.0f, 0.25f, 4.0f, "HUD size");
        label("hudScale");

        hudX = configuration
            .getInt("hudX", CATEGORY, 10, -10000, 10000, "Position of the top-left corner (GUI pixels)");
        label("hudX");

        hudY = configuration
            .getInt("hudY", CATEGORY, 10, -10000, 10000, "Position of the top-left corner (GUI pixels)");
        label("hudY");

        accentColor = parseColor(
            configuration.getString("accentColor", CATEGORY, "#00FF00", "Progress bar color, #RRGGBB or #AARRGGBB"),
            0xFF00FF00);
        label("accentColor");

        blacklist = normalize(
            configuration.getStringList(
                "blacklist",
                CATEGORY,
                new String[] { "firefox" },
                "Players whose name starts with one of these are ignored. Names are the ones printed by 'playerctl -l'"));
        label("blacklist");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    // gives each option a translated name and tooltip in the config screen
    private static void label(String name) {

        Property property = configuration.getCategory(CATEGORY)
            .get(name);

        if (property != null) {
            property.setLanguageKey(LANG_PREFIX + name);
        }
    }

    private static List<String> normalize(String[] names) {

        List<String> clean = new ArrayList<String>();

        for (String name : names) {

            String entry = name.trim()
                .toLowerCase(Locale.ROOT);

            if (!entry.isEmpty() && !clean.contains(entry)) {
                clean.add(entry);
            }
        }

        return clean;
    }

    // invalid text keeps the default instead of breaking the HUD
    private static int parseColor(String text, int fallback) {

        String hex = text.trim();

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        try {

            long value = Long.parseLong(hex, 16);

            // "00ff00" means opaque green, not a fully transparent bar
            if (hex.length() <= 6) {
                value |= 0xFF000000L;
            }

            return (int) value;

        } catch (NumberFormatException e) {

            return fallback;
        }
    }
}
