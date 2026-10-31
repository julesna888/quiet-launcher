<p align="center"><img src="docs/logo.png" width="560" alt="Quiet Launcher"></p>

<p align="center">A minimal home screen for the Amazon Fire TV Stick: your apps, a greeting and the time.<br>No recommendations, no ads, no video previews.</p>

<p align="center"><a href="README.it.md">Italiano</a></p>

<p align="center">
  <img src="docs/home.png" width="49%" alt="Home row with the selected app large in the centre">
  <img src="docs/home-prime.png" width="49%" alt="Home row, Prime Video selected, background in the app's colour">
</p>
<p align="center">
  <img src="docs/grid.png" width="32%" alt="All apps grid">
  <img src="docs/search.png" width="32%" alt="Search with the on-screen keyboard">
  <img src="docs/options.png" width="32%" alt="Hide or uninstall an app">
</p>

## Read this first

**This was built for one device and tested on that device only:** a Fire TV Stick Lite (codename `sheldon`) on Fire OS 7.7.1.7 (Android 9). It has not been tried on any other Fire TV model or firmware, and it may behave differently, or not work, elsewhere. Use it at your own risk.

That Stick is heavily locked down (locked bootloader, no root, Amazon's own packages protected), so the launcher works around the system instead of replacing it:

- **You cannot set a third-party launcher as the default home.** Fire OS ignores the normal Android mechanism.
- **Pressing Home shows Amazon's home for about 5 seconds** before Quiet Launcher takes over. A small accessibility service (`HomeGuard`) notices Amazon's home appear and brings Quiet Launcher back. That delay is a known limit, not a bug that can be tuned away on this firmware.
- **Opening an app is as fast as the app itself** (about a second cold on this Stick, which has under 1 GB of RAM). The launcher does not speed apps up.
- Amazon's system apps cannot be uninstalled from here; you can only hide them from the grid.
- **In Amazon's own *Apps & Games* screen Quiet Launcher shows a generic Android tile instead of its banner.** The banner loads fine inside the app; Fire OS just doesn't use it there. Purely cosmetic, and we could not find a fix.

If you try it on another model, please open an issue with what you saw.

## What it does

- **Row home.** The selected app is large in the centre, the others small and muted; the sixth tile opens **All apps**.
- **Pick your 5 apps** on first launch, and again any time with **Play/Pause**.
- **Last used app** is selected when you come back to the home.
- **Search** your apps with the bar at the top and an on-screen keyboard.
- **Hide apps** you never use (**Menu** → *Hide*) and bring them back from the *Show hidden apps* tile. Non-system apps can also be uninstalled from the same menu.
- **On-screen key hints**: a line at the bottom always tells you which button does what on the current screen.
- **Pause the Home takeover**: press **Menu** on the *All apps* tile and choose *Pause 10 min*. For 10 minutes the Home button shows Amazon's own screen (to change a system setting, say), then Quiet Launcher takes over again by itself. *Turn on now* ends the pause early. While paused, the home screen shows "Amazon's home allowed · N min left".
- **Wraps around**: pressing right on the last tile goes back to the first.
- **Brand colour** on the highlighted app.
- **Follows the system language** (English and Italian included; add another with a `res/values-xx/strings.xml`), and the 12/24 h clock setting.
- **Light screensaver** after 5 minutes idle.
- A single custom `View` drawing on a `Canvas`: no WebView, about 220 KB, roughly 30–60 MB of RAM.

## Install

1. On the Fire TV: *Settings → My Fire TV → Developer options* → turn on **ADB debugging**, and note the IP address (*Network*).
2. Build it (below) or take the APK from the GitHub Actions artifact.
3. Run `scripts/install.sh <fire-tv-ip>`

`install.sh` installs the APK and enables `HomeGuard`. To go back to Amazon's home:

```sh
adb -s <ip>:5555 shell settings delete secure enabled_accessibility_services
```

## Build

Needs JDK 17+, the Android SDK with `platforms;android-28` and `build-tools;34.0.0`, and `zip`.

```sh
export JAVA_HOME=...   ANDROID_HOME=...
app/build.sh           # -> app/out/launcher.apk (signed with a debug key generated in app/out/)
```

## Logos

Brand logos are **not included**; without them apps show their name as text. To get them, put your own SVG/PNG files in `logos-src/` (names in `tools/make-logos.py`), run `python3 tools/make-logos.py` (needs Chrome and Pillow), then rebuild.

## Optimise the Fire TV (optional)

`scripts/optimize.sh <ip>` disables Amazon services (telemetry, previews, tutorials) and halves animation durations. It also disables forced system updates, so they will stop arriving on their own. Any package can be restored with `adb shell pm enable <package>`.

## Notes

- Keys handled: D-pad, OK, Back, Menu, Play/Pause. A long press on OK does nothing, on purpose.
- Known apps and their brand colours are in `LauncherView.java` (`KNOWN`, `PINNED`).
- MIT licence. See `NOTICE.md` for the font and trademarks. Not affiliated with or endorsed by Amazon; *Fire TV* and the other names are trademarks of their owners.
