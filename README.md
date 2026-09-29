# QitaUI

A PlayStation Vita-style home screen (launcher) for Android, built with Kotlin and Jetpack Compose.

- Blue gradient background with drifting particles
- Home shows only the apps you add, as swipeable pages of bubbles (staggered rows), page dots down the left edge
- Desktop (🖥 in the top strip): an Ubuntu/GNOME-style desktop over every app on the device — aubergine wallpaper, black top bar with Activities and a clock, a left dock with your home apps and a Show Applications grid, and a dark "Applications" window with folders, search and a terminal prompt. Tap an app to launch it, "+ Add to home" to put it on the home screen (and dock), plus shortcuts to Android and Wi-Fi settings
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

## Controller

Built for handhelds with a gamepad. The item under the gamepad highlight gets a thick pulsing yellow ring (bubbles also grow and light up their name), and a bar at the bottom shows the buttons that work on the current screen. Button symbols can be PlayStation (default) or A/B/X/Y in Settings.

| Button | Action |
| --- | --- |
| A | Select / open (click in cursor mode; hold to drag or long-press) |
| B | Back (cancels move mode) |
| X | Options menu for the highlighted app |
| Y | Home: pick the highlighted bubble up to move it. Desktop: add/remove it from home |
| Start | Toggle launcher settings |
| Select | Toggle cursor mode |
| L1 / R1 | Previous / next page (desktop: previous / next folder) |
| L2 / R2 | Toggle desktop / search |
| L3 / R3 | Recenter cursor / toggle precision (slow) cursor |
| D-pad, left stick | Move the highlight (or the pointer in cursor mode); in move mode carries the bubble |
| Right stick | Left/right changes page; scrolls in cursor mode |

Cursor mode (Settings > Controller, or Select) shows an on-screen pointer that works on every screen, including drag-to-rearrange and the corner peel. Triggers and D-pad are read both as buttons and as analog axes, so most pads work.

## Also on the home screen

Swipe up for the desktop, swipe down for search. The desktop has a draggable, minimisable window, desktop icons, a quick-settings drop-down (Wi-Fi, Bluetooth, display, sound), a Frequently Used folder and sorting.
