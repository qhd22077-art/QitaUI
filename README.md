# QitaUI

A PlayStation Vita-style home screen (launcher) for Android, built with Kotlin and Jetpack Compose.

- Animated blue wave background
- Swipeable pages of glossy circular app bubbles (4x2 per page) with page dots
- Tap a bubble for a simplified LiveArea card with a **Start** button
- Clock and battery in the top status strip
- Landscape, immersive; registers as a HOME app so it can be set as your default launcher

## Build

Open in Android Studio (Ladybug or newer) and run the `app` configuration, or with Gradle and the Android SDK installed:

    gradle :app:assembleDebug

Then choose QitaUI as the default home app in Android settings to use it as your launcher.

This is an original UI inspired by the Vita's look; it uses no Sony assets.
