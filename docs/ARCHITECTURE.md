# Architecture

This document describes the current Android implementation. It is not a proposed redesign. See [REQUIREMENTS.md](REQUIREMENTS.md) for behavior and [API.md](API.md) for internal contracts.

## System boundary

The repository contains two Gradle modules. `app` is the Android application module, implemented in Kotlin. `game-core` is a plain Kotlin/JVM library with no Android dependency; it holds the deterministic engine, models, seat plan, checkpoint codec and roster merge, so its unit tests run without the Android plugin. There is no backend or network service in this project. The manifest has no internet permission; data and media are local. The package and application ID are `com.armadillo.game`. Jetpack Compose with Material 3 provides the UI on an AndroidX `ComponentActivity`; platform APIs provide the lifecycle, preferences, audio and vibration.

```text
:app — MainActivity (thin Compose host) + GameController (engine, timer, state)
  ├── Compose UI ── ui/screens, ui/Dialogs, ui/components, ui/theme (Material 3)
  ├── :game-core ── GameEngine + GameState/SeatPlan (rules and transitions)
  ├── LocalWordBank : WordSource ── categories.json
  ├── LocalStore ── private SharedPreferences: armadillo_local_v1
  └── GameAudio ── SoundPool + bundled audio
```

## Components

| Location | Responsibility |
| --- | --- |
| `app/src/main/java/com/armadillo/game/MainActivity.kt` | Thin `ComponentActivity`: hosts the Compose tree, enables edge-to-edge, applies the system-bar style and routes lifecycle, focus and back events to the controller. |
| `app/src/main/java/com/armadillo/game/GameController.kt` | Validates setup, drives the engine and handler timer, persists checkpoints, plays audio, and exposes observable screen, draft, game and dialog state. |
| `app/src/main/java/com/armadillo/game/ui/` | Compose screens, the eight dialogs, shared Material 3 components, the Compose seat circle and the theme/tokens. |
| `game-core/…/game/Models.kt` | State, phase, player, category and ranking models; game constants and seat mapping. |
| `game-core/…/game/GameEngine.kt` | Transition logic, active-time accounting, words and results. Callers supply a monotonic clock and a `WordSource`. |
| `data/LocalWordBank.kt` | Loads and validates bundled topic words, deduplicates a selected pool and draws unused words. |
| `data/LocalStore.kt` | Persists sound settings, roster and game checkpoint in `armadillo_local_v1`; delegates encoding to `GameCodec` and reports discarded checkpoints. |
| `game-core/…/data/GameCodec.kt` | Pure JSON codec for the checkpoint: schema, migration and value clamping. No Android dependency, covered by JVM tests. |
| `game-core/…/data/PlayerRoster.kt` | Pure roster merge helper used by `LocalStore`. |
| `app/src/main/res/values/strings.xml` | Every user-visible Persian string; `app/src/main/res/xml/locales_config.xml` declares `fa` for the Android 13+ per-app language settings. |
| `audio/GameAudio.kt` | Loads local sounds with `SoundPool`, handles playback/settings and checks system sound state. |
| `ui/theme/` | Material 3 colour scheme and typography built from the brand palette, Mikhak font resources and spacing/radius tokens. |
| `app/src/main/assets/` and `res/` | Topic and archived word banks, licensed font, artwork, icons, sounds and styles. |
| `game-core/src/test/`, `tools/` | JVM unit tests for the engine, codec and roster, plus word-bank and icon tooling. |

The Compose root provides right-to-left layout direction for screens and dialogs regardless of the device locale. Scoped left-to-right overrides preserve the physical ordering of the home corner controls and selected in-game controls.

Setup selection cards and duration/round buttons show the page background when unselected and navy with white text when selected. Their selection state is exposed to accessibility services. Other outlined actions retain their existing fills. The home corner controls use white settings/help icons on navy circles, and the shared page-header back arrow points left.

## State and timing

`GameState` has `PREPARATION`, `PLAYING`, `MANUAL_PAUSED`, `SYSTEM_PAUSED`, `EXPLODING` and `RESULTS` phases. `GameController.syncToPhase()` maps the engine phase to a Compose screen (`GAME`, `EXPLOSION`, `SYSTEM_PAUSED`, `RESULTS`). `GameEngine` measures elapsed time in nanoseconds from its supplied monotonic clock; the activity passes Android's `SystemClock.elapsedRealtimeNanos()`. Its handler calls `tick()` during play, updates the UI and requests periodic checkpoints. UI refresh intervals do not define elapsed game time.

During play the controller ticks four times per second (`LIVE_UPDATE_INTERVAL_MS`) and publishes an immutable `GameSnapshot`; Compose recomposes only the composables that read the changed values. The monotonic clock still defines elapsed time, never the tick count or recomposition. Starting a round draws a word and enters `PLAYING`. Correct answers advance the seat; swaps are blocked until five seconds of active play have elapsed since the latest word. Expiry records the word outcome, applies the current team's penalty and enters `EXPLODING`; acknowledging the explosion advances to preparation or results. Manual and system pauses are distinct. System resumption restores the prior phase, including manual-pause intent or a pending explosion. The activity stops loops and audio and saves when it loses foreground/focus.

## Data and migration

`LocalStore` keeps the roster independent of the unfinished-game checkpoint and caches the roster in memory. `hasSavedGame()` only checks key existence, so rendering the home screen never parses or rewrites the checkpoint. The checkpoint is JSON produced by `GameCodec` (schema 4); schema 3 swap cooldowns are migrated from seven to five seconds while preserving elapsed active time. Saving a results state removes the unfinished checkpoint; loading an invalid checkpoint clears it without clearing the separate roster. Saved categories are filtered to playable categories and may cause the current word to be refreshed. The legacy `words.json` remains an archive/validation input, not the live word source.

`LocalWordBank` is constructed on a background thread during startup because it validates the whole bank; game start is blocked with a message until it is ready. It loads `categories.json`, checks five categories, three difficulty tiers and 100 unique entries per tier (1,500 unique normalized entries overall). Once the selected word pool is exhausted, it clears the used set and starts a fresh cycle. Word history is distinct from the used-word set.

Android backup is disabled in the manifest; no in-app backup/export is documented here. The owner-approved application ID change makes this a separate installation from the earlier app. Its private preferences, including games and roster, cannot automatically be read by the new installation; cross-application data migration is not implemented. The new preference file is `armadillo_local_v1`. Do not remove the earlier installation or its data without owner approval. Further changes to preference names, save migration, application ID, version code or signing can affect upgrade compatibility and need appropriate review and testing.

## Build and verification

The Gradle wrapper, Android SDK and JDK requirements and commands are in [README.md](../README.md#build-and-test). CI calls a shared validation workflow for word validation, JVM tests, Debug and unsigned Release builds and both lint variants. A separate dependency audit runs for PRs and protected-branch pushes; the manually dispatched signed release also invokes it before the signing job. See [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) for the workflow audit, runtime requirements and limitations. No device/emulator testing is implied by a successful workflow. The wrapper is pinned to Gradle 9.8.2 and verified against `distributionSha256Sum`.

## Platform behaviour

The app targets API 36, where two platform changes are mandatory. Back navigation goes through a single `OnBackPressedDispatcher` callback registered in `MainActivity`; `ComponentActivity` forwards predictive back on API 33+ and the legacy back button on API 23–32 to it, and a Compose dialog window handles its own back press first. The window is edge-to-edge via `enableEdgeToEdge()` with the light system-bar style, and system-bar insets are applied once by `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` on the Compose root. `android:forceDarkAllowed` is `false` and there is no night resource set, so the light theme is static. Team identity is paired with a team number, including in the accessible seat list, so it does not depend on colour alone. The setup circle draws top-down table and armchair assets and tints the armchairs by team; the seat list remains available for name editing and accessibility.

Historical release documents and [the earlier test report](../Historical-Test-Report.md) are retained for provenance, not as evidence of a current build. The configured app version is `1.1.0` with `versionCode` 2. The first `com.armadillo.game` release was `1.0.0` (code 1); signed releases require an explicit owner request and manual dispatch from `main`; the workflow creates a new release tag. No secret keys belong in source control.
