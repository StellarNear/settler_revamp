# Tiwanaku
# to launch :
C:\Users\ho.jchatron\Documents\local_bin\jdk-17.0.20.1\bin\java.exe -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :teavm:run

**Explore ancient lands and uncover hidden resources through pure logic!**

Tiwanaku is a free, offline logic puzzle game inspired by the mystery of the Andes. It combines the elegance of Sudoku with a fresh twist — pixel-art biomes waiting to be filled, number by number.

---

## A Unique Logic Puzzle

Each level is a map of biomes — forests, deserts, mountains, oceans — waiting for you to fill with numbers following two simple rules.

### The Rules (Easy to Learn, Hard to Master)

- **Every number in a biome is unique.** A biome with 5 tiles holds the numbers 1 through 5.
- **No two neighboring tiles may share the same value.**

That's it. No tutorial marathon, no overwhelming menus. Tap a tile to set a number, and let your brain do the rest.

---

## Endless Puzzle Generation

Every level is generated on the fly — you will never run out of content. Each puzzle has a guaranteed unique solution, verified by the built-in algorithm. Thousands of hours of gameplay waiting for you.

## Fit Every Skill Level

- **7 board sizes:** from a relaxing 3x3 grid to UBER (15x10) and TITAN (20x12)
- **3 difficulty settings:** Easy, Medium, and Hard
- **Maximum Region Size:** Default (5), 6, 7, 8, or 9, selected with the arrows below Difficulty. This is an upper limit; a board can contain smaller regions too.

During generation, the black status text reports completed clue checks and the number of clues remaining. These are actual counts, not an estimated percentage. The display updates between checks; a difficult individual check can take longer. Changing the maximum region size creates a new puzzle when you press Play.

## Features

- **Help mode:** reveal possible values for any tile when you're stuck
- **Error detection:** conflicts are highlighted instantly so you can correct course
- **Checkpoint system:** fix your progress and return if you want to try a different approach
- **Charming pixel-art biomes:** desert dunes, lush forests, snowy peaks, ocean shores
- **Relaxing resolution timer:** starts with your first click, stays in the side panel, and shows your total time when the puzzle is solved
- **Clean, calming interface** — no pop-ups, no pressure
- **Available in English and German**
- **Runs entirely offline** — play anywhere, anytime

## Completely Free

No ads. No in-app purchases. No data collection. No paywalls. Ever.

Just a pure puzzle game made with love by a solo developer who believes games should respect your time and attention.

## Train Your Brain

Whether you're a Sudoku veteran looking for something new, a puzzle newcomer wanting a gentle challenge, or simply looking for a mindful way to wind down — Tiwanaku is for you. Perfect for commutes, waiting rooms, or that quiet moment before bed.

Download now and start exploring. Your adventure begins with a single number.

---

## Technical Overview

Tiwanaku is a cross-platform game written in **Java** on top of the [libGDX](https://libgdx.com/) framework, with launchers for Desktop, Android, and the Web.

### Stack

| Area        | Technology                                               |
|-------------|----------------------------------------------------------|
| Language    | Java 11                                                  |
| Framework   | libGDX 1.14.0 (+ gdx-freetype for font rendering)        |
| Desktop     | LWJGL3 backend                                           |
| Android     | libGDX Android backend (minSdk 28, targetSdk 35)         |
| Web (HTML5) | [gdx-teavm](https://github.com/xpenatan/gdx-teavm) 1.5.3 |
| Build       | Gradle 8.12.0                                            |
| i18n        | Java `ResourceBundle` (`assets/i18n/`) — English, German |
| Rendering   | 1400 × 800 internal resolution, VSync, 60 FPS target     |

### Project Structure

The project is a standard multi-module libGDX setup:

```
settler/
├── core/      # Shared game code (rules, generator, UI, rendering)
├── desktop/   # LWJGL3 launcher (Windows / macOS / Linux)
├── android/   # Android launcher + manifest + resources
├── teavm/     # TeaVM launcher for the HTML5 / web build
├── assets/    # Fonts, images, i18n property files (shared by all platforms)
└── docs/      # Published web build (served as static site)
```

The core module contains the gameplay:

- `com.apogames.settler.game.tiwanaku` — game state, level sizes, main game loop
- `com.apogames.settler.level.algorithmX` — puzzle generation / uniqueness solver
- `com.apogames.settler.game.menu` — menus and navigation
- `com.apogames.settler.asset` / `common` / `entity` — assets, helpers, entities
- `com.apogames.settler.backend.io` — save / checkpoint persistence

App identifiers:

- Android application ID: `com.apogames.tiwanaku`
- Java package namespace: `com.apogames.settler`

### Building and Running

Requires a JDK 11+ and, for the Android build, an Android SDK (set via `local.properties` or `ANDROID_HOME`).

**Desktop**
```bash
./gradlew desktop:run
```

**Android (install & launch on a connected device)**
```bash
./gradlew android:installDebug android:run
```

**Web (TeaVM)**
Build the static site into `teavm/build/dist`:
```bash
./gradlew teavm:build
```
The `TeaVMBuilder` main class can also start a local Jetty server (`--serve`) for development. The currently published web build is in `docs/` and can be served directly.

### Signing the Android Release

The Android release build is signed via Gradle properties. Provide them in `~/.gradle/gradle.properties` or on the command line:

- `SETTLER_KEYSTORE_PATH`
- `SETTLER_KEYSTORE_PASSWORD`
- `SETTLER_KEY_ALIAS` (defaults to `settler`)
- `SETTLER_KEY_PASSWORD`

Then run:
```bash
./gradlew android:assembleRelease
```

---

## License / Author

Made by Dirk Aporius. Source code © 2005–present Dirk Aporius, distributed under a BSD-style license (see the header of `Constants.java` for the full notice).
