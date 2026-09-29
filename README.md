# QitaUI

A PlayStation Vita-style home screen (launcher) for Android, built with Kotlin and Jetpack Compose.

- Blue gradient background with drifting particles
- Home shows only the apps you add, as swipeable pages of bubbles (staggered rows), page dots down the left edge
- Desktop (🖥 in the top strip): a Linux-style file-manager window listing every app on the device, with folders (games, media, social, system...), a filter box and a shell-style prompt; tap an app to launch it, "+ home" to add it to the home screen, plus shortcuts to Android and Wi-Fi settings
- Tap a bubble to open its full-screen LiveArea page: icon, title, **Start** button and swipeable cards
- Drag the folded top-right corner diagonally to peel the page away: past a threshold it closes the page and the app's background processes. Dragging down from the top edge also dismisses the page.
- Status strip: clock, Wi-Fi, battery (with charging bolt) and a settings gear
- Settings: themes, custom wallpaper, particles, bubble size, 24h clock, sort order
- Long-press a bubble and drag to rearrange (drop on another bubble to take its place, hold at a screen edge to change page); long-press without moving for App info / Uninstall. Pressing Home returns to the first page
- Landscape, immersive; registers as a HOME app so it can be set as your default launcher

## Build

Open in Android Studio (Ladybug or newer) and run the `app` configuration, or with Gradle and the Android SDK installed:

    gradle :app:assembleDebug

Then choose QitaUI as the default home app in Android settings to use it as your launcher.

This is an original UI inspired by the Vita's look; it uses no Sony assets.
