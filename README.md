# MPRIS Overlay — Minecraft 1.7.10 (Forge) port

Same mod as the Fabric 26.x version: shows the track playing in any MPRIS
player (via `playerctl`) as a small HUD. This folder only contains the
**source code and resources**, to be dropped into a 1.7.10 Forge build setup.

## Using it with the GTNH ExampleMod template (recommended)

1. Create a new project from https://github.com/GTNewHorizons/ExampleMod1.7.10
   ("Use this template").
2. In `gradle.properties` set:

   ```properties
   modName = MPRIS Overlay
   modId = mprisoverlay
   modGroup = me.soller.mprisoverlay
   ```

3. Delete the template's example package under `src/main/java` and its
   `mcmod.info`, then copy the `src/` folder of this port over the project.
4. `./gradlew build` (jar in `build/libs/`) or `./gradlew runClient`.

Any other 1.7.10 Forge setup works too: only `src/main/java` and
`src/main/resources` are needed. The code is plain Java 8.

## Requirements

- Linux/BSD with [`playerctl`](https://github.com/altdesktop/playerctl) in your `PATH`
- Minecraft 1.7.10 with Forge

## Configuration

`config/mprisoverlay.cfg` is created on first launch. Edit it and restart the game
(there is no in-game settings screen in this version, Mod Menu does not exist for 1.7.10).

| Option          | Default    | Description                                             |
| --------------- | ---------- | ------------------------------------------------------- |
| `renderOverlay` | `true`     | Show or hide the HUD                                    |
| `showPaused`    | `true`     | Keep showing the track while the player is paused       |
| `hideWhenIdle`  | `false`    | Hide the HUD completely when nothing is playing         |
| `hudScale`      | `1.0`      | HUD size (0.25 to 4)                                    |
| `hudX`, `hudY`  | `10`, `10` | Position of the top-left corner, in GUI pixels          |
| `accentColor`   | `#00FF00`  | Progress bar color, `#RRGGBB` or `#AARRGGBB`            |
| `blacklist`     | `firefox`  | Players whose name starts with one of these are ignored |

## Differences from the 26.x version

- No Mod Menu settings screen: configuration is the `.cfg` file only
- Framework differences only (Forge events and OpenGL 1.x instead of Fabric/GuiGraphics)

## License

CC0-1.0
