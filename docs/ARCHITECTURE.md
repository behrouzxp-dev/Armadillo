# Architecture

This document describes the current Android implementation. It is not a proposed redesign. See [REQUIREMENTS.md](REQUIREMENTS.md) for behavior and [API.md](API.md) for internal contracts.

## System boundary

The repository contains one Gradle Android application module, `app`, implemented in Kotlin. There is no backend or network service in this project. The manifest has no internet permission; data and media are local. The package and application ID are `com.armadillo.game`. Android platform APIs provide the UI, lifecycle, preferences, audio and vibration.

```text
MainActivity (screens, lifecycle, handler and UI events)
  ├── GameEngine + GameState/SeatPlan (rules and transitions)
  ├── LocalWordBank : WordSource ── categories.json
  ├── LocalStore ── private SharedPreferences: armadillo_local_v1
  ├── GameAudio ── SoundPool + bundled audio
  └── DesignSystem + SeatingCircleView ── resources and Mikhak typeface
```

## Components

| Location | Responsibility |
| --- | --- |
| `app/src/main/java/com/armadillo/game/MainActivity.kt` | Builds screens, validates setup, handles interaction, observes lifecycle/focus and schedules timer updates. |
| `game/Models.kt` | State, phase, player, category and ranking models; game constants and seat mapping. |
| `game/GameEngine.kt` | Transition logic, active-time accounting, words and results. Callers supply a monotonic clock and a `WordSource`. |
| `data/LocalWordBank.kt` | Loads and validates bundled topic words, deduplicates a selected pool and draws unused words. |
| `data/LocalStore.kt` | Persists sound settings, roster and game checkpoint in `armadillo_local_v1`; delegates encoding to `GameCodec` and reports discarded checkpoints. |
| `data/GameCodec.kt` | Pure JSON codec for the checkpoint: schema, migration and value clamping. No Android dependency, covered by JVM tests. |
| `audio/GameAudio.kt` | Loads local sounds with `SoundPool`, handles playback/settings and checks system sound state. |
| `ui/DesignSystem.kt`, `ui/SeatingCircleView.kt` | Shared colors, typography, spacing and seat rendering/interaction. |
| `app/src/main/assets/` and `res/` | Topic and archived word banks, licensed font, artwork, icons, sounds and styles. |
| `app/src/test/`, `tools/` | JVM unit tests and word-bank generation/validation tools. |

## State and timing

`GameState` has `PREPARATION`, `PLAYING`, `MANUAL_PAUSED`, `SYSTEM_PAUSED`, `EXPLODING` and `RESULTS` phases. `GameEngine` measures elapsed time in nanoseconds from its supplied monotonic clock; the activity passes Android's `SystemClock.elapsedRealtimeNanos()`. Its handler calls `tick()` during play, updates the UI and requests periodic checkpoints. UI refresh intervals do not define elapsed game time.

Live screens refresh three times per second (`LIVE_UPDATE_INTERVAL_MS`); the monotonic clock still defines elapsed time, and unchanged team times, player cards and word views are skipped to avoid needless allocation. Starting a round draws a word and enters `PLAYING`. Correct answers advance the seat; swaps are blocked until seven seconds of active play have elapsed since the latest word. Expiry records the word outcome, applies the current team's penalty and enters `EXPLODING`; acknowledging the explosion advances to preparation or results. Manual and system pauses are distinct. System resumption restores the prior phase, including manual-pause intent or a pending explosion. The activity stops loops and audio and saves when it loses foreground/focus.

## Data and migration

`LocalStore` keeps the roster independent of the unfinished-game checkpoint and caches the roster in memory. `hasSavedGame()` only checks key existence, so rendering the home screen never parses or rewrites the checkpoint. The checkpoint is JSON produced by `GameCodec` (schema 3). Saving a results state removes the unfinished checkpoint; loading an invalid checkpoint clears it without clearing the separate roster. Saved categories are filtered to playable categories and may cause the current word to be refreshed. The legacy `words.json` remains an archive/validation input, not the live word source.

`LocalWordBank` is constructed on a background thread during startup because it validates the whole bank; game start is blocked with a message until it is ready. It loads `categories.json`, checks five categories, three difficulty tiers and 100 unique entries per tier (1,500 unique normalized entries overall). Once the selected word pool is exhausted, it clears the used set and starts a fresh cycle. Word history is distinct from the used-word set.

Android backup is disabled in the manifest; no in-app backup/export is documented here. The owner-approved application ID change makes this a separate installation from the earlier app. Its private preferences, including games and roster, cannot automatically be read by the new installation; cross-application data migration is not implemented. The new preference file is `armadillo_local_v1`. Do not remove the earlier installation or its data without owner approval. Further changes to preference names, save migration, application ID, version code or signing can affect upgrade compatibility and need appropriate review and testing.

## Build and verification

The Gradle wrapper, Android SDK and JDK requirements and commands are in [README.md](../README.md#build-and-test). The [Android build workflow](../.github/workflows/debug-apk.yml) runs on pull requests and validates the word banks, builds and verifies the Debug APK, uploads a short-lived artifact and runs JVM unit tests plus lint; a second job builds an unsigned Release APK with R8 and resource shrinking and runs release lint. No device/emulator testing is implied by a successful workflow.

## Platform behaviour

The app targets API 36, where two platform changes are mandatory. Back navigation is handled through `OnBackInvokedCallback` (`onBackPressed()` is no longer called on Android 13+ when predictive back is enabled) and the window is edge-to-edge, with system-bar insets applied once in `showPage`. `android:forceDarkAllowed` is `false` and there is no night resource set, so the light theme is static. Team identity is always paired with a team number because the fixed team colours cannot reach 3:1 contrast on the yellow canvas.

Historical release documents and [the earlier test report](../Historical-Test-Report.md) are retained for provenance, not as evidence of a current build. The repository contains no configured user-facing app version name; `versionCode` is 1 because the `com.armadillo.game` application ID has never been published. No secret keys belong in source control.
