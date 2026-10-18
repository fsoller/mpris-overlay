# MPRIS Overlay

A lightweight Minecraft Fabric mod that shows the track you are listening to
in a small HUD: title, artist, album cover and playback progress.
It works with any [MPRIS](https://specifications.freedesktop.org/mpris-spec/latest/)
media player (Spotify, VLC, mpv, browsers, ...). Highly based on Spotify Overlay.

## Requirements

- Minecraft 26.3 with [Fabric Loader](https://fabricmc.net/) 0.19.5+ and Fabric API
- Java 25
- Linux (or BSD) with [`playerctl`](https://github.com/altdesktop/playerctl) installed and in your `PATH`

## Features

- Current song title, artist, album cover and a smooth progress bar
- Keeps showing the track when the player is paused (can be turned off)
- Players can be ignored with a blacklist (Firefox is ignored by default)
- Album covers load in the background, so the game never stutters on a track change

## Configuration

Open the settings from Mod Menu, or edit `config/mprisoverlay.json`:

| Option         | Default      | Description                                                        |
| -------------- | ------------ | ------------------------------------------------------------------ |
| `renderOverlay`| `true`       | Show or hide the HUD                                               |
| `showPaused`   | `true`       | Keep showing the track while the player is paused                  |
| `hideWhenIdle` | `false`      | Hide the HUD completely when nothing is playing                    |
| `hudScale`     | `1.0`        | HUD size (0.25 to 4)                                               |
| `hudX`, `hudY` | `10`, `10`   | Position of the top-left corner                                    |
| `accentColor`  | `0xFF00FF00` | Progress bar color, ARGB integer (the settings screen takes `#RRGGBB`) |
| `blacklist`    | `["firefox"]`| Players whose name starts with one of these are ignored            |

Player names are the ones printed by `playerctl -l`.

## Development

Clone the repository:

```bash
git clone https://github.com/fsoller/mpris-overlay.git
cd mpris-overlay
```

Run the game with the mod, or build the jar (it ends up in `build/libs/`):

```bash
./gradlew runClient
./gradlew build
```

## License

CC0-1.0, see [LICENSE](LICENSE).
