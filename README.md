# Icon Recolor

Recolor your app icons with any two colors, or with a palette taken from your wallpaper (Material You style).

The app redraws icons in the colors you choose and pins them to the home screen as shortcuts. The original icons and system files are left untouched.

## Features

- **Two-color mode** — pick the background and glyph colors yourself, or start from a preset pair.
- **Wallpaper palette** — Pixel-style themed colors (light and dark) derived from the system wallpaper or from an image you pick.
- **Icon shapes** — use the launcher's own shape, choose from built-in shapes (circle, squircle, diamond, hexagon, octagon, flower, star, clover, square), or draw your own.
- **Shareable shapes** — every custom shape has a short `icrec-…` code you can copy and import on another device.
- **Per-app recoloring method** — Auto, Glyph or Gradient. Auto uses the app's monochrome layer when it has a usable one; Gradient keeps the details of illustrated icons such as games.
- **Update in place** — recolor already pinned shortcuts without confirming each one again.
- **Local only** — icons are rendered on the device. The app has no internet permission.

## Requirements

- Android 13 or newer (API 33).
- A Xiaomi, Redmi or POCO device running HyperOS. This is what the app is built for; other devices are not guaranteed to work.
- [Shizuku](https://shizuku.rikka.app/) running — recommended. It grants the Xiaomi "Home screen shortcuts" permission with one tap. You can also enable that permission manually in the system app settings.

## Usage

1. Choose the colors: your own pair, or a palette from the wallpaper.
2. Optionally pick an icon shape.
3. Tap **Connect** and then **Allow** in the Shizuku card to grant the shortcut permission.
4. Select an app and pin its icon. Confirm the request in the launcher dialog.
5. After changing colors or shape, use **Update pinned** to refresh the existing shortcuts.

If an icon looks wrong, open the app's method menu and switch between Auto, Glyph and Gradient.

## Building

Requires JDK 17+ and the Android SDK.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
`gradle.properties` points `aapt2` to the Termux binary for on-device builds. Remove that line when building on a regular machine or in CI.

## Notes

- Icons are added as new shortcuts; the original app icons stay as they are.
- Pinned shortcuts launch the target app through a small invisible activity, so the app starts the same way as from a regular launcher icon.
- Behavior may differ between launchers and HyperOS versions.
- The app interface is currently in Russian.

Built with Kotlin, Jetpack Compose (Material 3) and the Shizuku API.
