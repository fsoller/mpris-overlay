package me.soller.mprisoverlay.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MPRISOverlayModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    private static class ConfigScreen extends Screen {

        private static final int ROW_HEIGHT = 24;
        private static final int FIRST_ROW_Y = 28;
        private static final int LABEL_WIDTH = 150;
        private static final int CONTROL_WIDTH = 150;

        private final Screen parent;

        // edited with toggles, copied into the config when the screen closes
        private boolean renderOverlay;
        private boolean showPaused;
        private boolean hideWhenIdle;

        private EditBox scaleBox;
        private EditBox xBox;
        private EditBox yBox;
        private EditBox colorBox;
        private EditBox blacklistBox;

        protected ConfigScreen(Screen parent) {

            super(Component.translatable("mprisoverlay.config.title"));

            this.parent = parent;
        }

        @Override
        protected void init() {

            Config cfg = Config.get();

            renderOverlay = cfg.renderOverlay;
            showPaused = cfg.showPaused;
            hideWhenIdle = cfg.hideWhenIdle;

            int left = this.width / 2 - LABEL_WIDTH - 10;
            int right = this.width / 2 + 10;

            // title
            this.addRenderableWidget(
                    new StringWidget(
                            this.width / 2 - this.font.width(this.title) / 2,
                            8,
                            this.font.width(this.title),
                            12,
                            this.title,
                            this.font
                    )
            );

            int row = 0;

            addToggle(left, right, row++, "mprisoverlay.config.render_overlay", () -> renderOverlay, value -> renderOverlay = value);
            addToggle(left, right, row++, "mprisoverlay.config.show_paused", () -> showPaused, value -> showPaused = value);
            addToggle(left, right, row++, "mprisoverlay.config.hide_idle", () -> hideWhenIdle, value -> hideWhenIdle = value);

            scaleBox = addField(left, right, row++, "mprisoverlay.config.scale", String.valueOf(cfg.hudScale), 8);
            xBox = addField(left, right, row++, "mprisoverlay.config.pos_x", String.valueOf(cfg.hudX), 8);
            yBox = addField(left, right, row++, "mprisoverlay.config.pos_y", String.valueOf(cfg.hudY), 8);
            colorBox = addField(left, right, row++, "mprisoverlay.config.accent", formatColor(cfg.accentColor), 9);
            blacklistBox = addField(left, right, row++, "mprisoverlay.config.blacklist", String.join(", ", cfg.blacklist), 256);

            int doneY = Math.max(FIRST_ROW_Y + row * ROW_HEIGHT + 8, this.height - 28);

            this.addRenderableWidget(
                    Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                            .bounds(this.width / 2 - 100, doneY, 200, 20)
                            .build()
            );
        }

        private void addLabel(int x, int y, String key) {

            this.addRenderableWidget(
                    new StringWidget(
                            x,
                            y,
                            LABEL_WIDTH,
                            20,
                            Component.translatable(key),
                            this.font
                    )
            );
        }

        private void addToggle(
                int left,
                int right,
                int row,
                String key,
                java.util.function.BooleanSupplier getter,
                java.util.function.Consumer<Boolean> setter
        ) {

            int y = FIRST_ROW_Y + row * ROW_HEIGHT;

            addLabel(left, y, key);

            this.addRenderableWidget(
                    Button.builder(
                                    onOff(getter.getAsBoolean()),
                                    button -> {
                                        boolean value = !getter.getAsBoolean();
                                        setter.accept(value);
                                        button.setMessage(onOff(value));
                                    })
                            .bounds(right, y, CONTROL_WIDTH, 20)
                            .build()
            );
        }

        private EditBox addField(int left, int right, int row, String key, String value, int maxLength) {

            int y = FIRST_ROW_Y + row * ROW_HEIGHT;

            addLabel(left, y, key);

            EditBox box = new EditBox(
                    this.font,
                    right,
                    y,
                    CONTROL_WIDTH,
                    20,
                    Component.translatable(key)
            );

            box.setMaxLength(maxLength);
            box.setValue(value);

            this.addRenderableWidget(box);

            return box;
        }

        private static Component onOff(boolean value) {
            return Component.translatable(value ? "options.on" : "options.off");
        }

        private static String formatColor(int argb) {

            // fully opaque colors are shown as plain #RRGGBB
            if ((argb >>> 24) == 0xFF) {
                return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
            }

            return String.format(Locale.ROOT, "#%08X", argb);
        }

        // invalid text never overwrites a good value, it just keeps the old one
        private static float parseFloat(String text, float fallback) {

            try {
                return Float.parseFloat(text.trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static int parseInt(String text, int fallback) {

            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

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

        private static List<String> parseBlacklist(String text) {

            List<String> entries = new ArrayList<>();

            for (String part : text.split(",")) {

                String entry = part.trim().toLowerCase(Locale.ROOT);

                if (!entry.isEmpty() && !entries.contains(entry)) {
                    entries.add(entry);
                }
            }

            return entries;
        }

        private void apply() {

            Config cfg = Config.get();

            cfg.renderOverlay = renderOverlay;
            cfg.showPaused = showPaused;
            cfg.hideWhenIdle = hideWhenIdle;

            cfg.hudScale = parseFloat(scaleBox.getValue(), cfg.hudScale);
            cfg.hudX = parseInt(xBox.getValue(), cfg.hudX);
            cfg.hudY = parseInt(yBox.getValue(), cfg.hudY);
            cfg.accentColor = parseColor(colorBox.getValue(), cfg.accentColor);

            // the list is replaced, not edited: the updater thread reads it
            cfg.blacklist = parseBlacklist(blacklistBox.getValue());

            cfg.sanitize();

            Config.save();
        }

        @Override
        public void onClose() {

            // saves on Done and on Esc, so nothing typed here is lost
            apply();

            this.minecraft.setScreenAndShow(parent);
        }
    }
}
