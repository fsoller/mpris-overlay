package me.soller.mprisoverlay.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.soller.mprisoverlay.MPRISOverlay;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Config {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static volatile Config config;

    // bumped when a new release changes defaults that need a migration
    public int configVersion = 2;

    public boolean renderOverlay = true;

    // keep showing the track when the player is paused
    public boolean showPaused = true;

    // do not draw the card at all when nothing is playing
    public boolean hideWhenIdle = false;

    public float hudScale = 1.0f;

    public int hudX = 10;

    public int hudY = 10;

    public int accentColor = 0xFF00FF00;

    // players whose name starts with one of these are ignored.
    // always replaced as a whole (never modified in place) because the updater thread reads it.
    public volatile List<String> blacklist = new ArrayList<>(List.of("firefox"));


    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mprisoverlay.json");
    }

    // earlier names of this mod, keep the settings of people who already had them
    private static final String[] OLD_FILE_NAMES = {"honmpris.json", "mprisdisplay.json"};

    private static void migrateOldFile(Path file) {

        if (Files.exists(file)) {
            return;
        }

        for (String name : OLD_FILE_NAMES) {

            Path old = file.resolveSibling(name);

            if (!Files.exists(old)) {
                continue;
            }

            try {

                Files.move(old, file);

            } catch (IOException e) {

                MPRISOverlay.LOGGER.warn("Could not move {} to {}", old, file, e);
            }

            return;
        }
    }

    public static synchronized void load() {

        Path file = file();

        migrateOldFile(file);

        Config loaded = null;
        boolean legacy = false;

        if (Files.exists(file)) {

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {

                JsonElement element = JsonParser.parseReader(reader);

                if (element != null && element.isJsonObject()) {

                    JsonObject object = element.getAsJsonObject();

                    // files written before 1.2 have no version and always ignored firefox
                    legacy = !object.has("configVersion");

                    loaded = GSON.fromJson(object, Config.class);
                }

            } catch (IOException | JsonParseException e) {

                MPRISOverlay.LOGGER.warn("Could not read {}, using defaults", file, e);

                backupBrokenFile(file);
            }
        }

        if (loaded == null) {
            loaded = new Config();
        }

        if (legacy) {

            List<String> migrated = new ArrayList<>(loaded.sanitizedBlacklist());

            if (!migrated.contains("firefox")) {
                migrated.add("firefox");
            }

            loaded.blacklist = migrated;
            loaded.configVersion = 2;
        }

        loaded.sanitize();

        config = loaded;

        save();
    }

    private static void backupBrokenFile(Path file) {

        try {

            Files.move(
                    file,
                    file.resolveSibling("mprisoverlay.json.broken"),
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException ignored) {
            // best effort only
        }
    }

    public static synchronized void save() {

        Config current = config;

        if (current == null) {
            return;
        }

        Path file = file();
        Path temp = file.resolveSibling("mprisoverlay.json.tmp");

        try {

            Files.createDirectories(file.getParent());

            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }

            try {

                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            } catch (AtomicMoveNotSupportedException e) {

                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {

            MPRISOverlay.LOGGER.warn("Could not save {}", file, e);
        }
    }

    public static Config get() {

        Config current = config;

        if (current == null) {

            load();
            current = config;
        }

        return current;
    }

    /** Keeps hand-edited values inside something the renderer can handle. */
    public void sanitize() {

        if (Float.isNaN(hudScale) || Float.isInfinite(hudScale)) {
            hudScale = 1.0f;
        }

        hudScale = Math.max(0.25f, Math.min(4.0f, hudScale));

        hudX = Math.max(-10_000, Math.min(10_000, hudX));
        hudY = Math.max(-10_000, Math.min(10_000, hudY));

        blacklist = sanitizedBlacklist();
    }

    private List<String> sanitizedBlacklist() {

        List<String> source = blacklist;
        List<String> clean = new ArrayList<>();

        if (source != null) {

            for (String entry : source) {

                if (entry == null) {
                    continue;
                }

                String normalized = entry.trim().toLowerCase(Locale.ROOT);

                if (!normalized.isEmpty() && !clean.contains(normalized)) {
                    clean.add(normalized);
                }
            }
        }

        return clean;
    }
}
