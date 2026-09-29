# QitaUI

A PlayStation Vita-style home screen (launcher) for Android, built with Kotlin and Jetpack Compose.

- Blue gradient background with drifting particles
- Swipeable pages of 10 round app bubbles (staggered rows), page dots down the left edge
- Tap a bubble to open its full-screen LiveArea page: icon, title, **Start** button and swipeable cards
- Drag the folded top-right corner diagonally to peel the page away: past a threshold it closes the page and the app's background processes. Dragging down from the top edge also dismisses the page.
- Clock and battery in the top status strip
- Landscape, immersive; registers as a HOME app so it can be set as your default launcher

## Build

Open in Android Studio (Ladybug or newer) and run the `app` configuration, or with Gradle and the Android SDK installed:

    gradle :app:assembleDebug

Then choose QitaUI as the default home app in Android settings to use it as your launcher.

This is an original UI inspired by the Vita's look; it uses no Sony assets.
