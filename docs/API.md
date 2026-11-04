# Internal interfaces

Armadillo is an offline Android application. It exposes **no HTTP, REST or other network API**. This file summarizes implementation-level Kotlin interfaces for maintainers; they are not a separately versioned public SDK or a guarantee of binary compatibility. The source files remain authoritative.

## Game engine

Location: `app/src/main/java/com/dour/game/game/GameEngine.kt`.

Construct `GameEngine(state, words, clockNanos, wallClockMillis)` with a `GameState`, a `WordSource`, a monotonic nanosecond clock and an optional wall clock for history timestamps. Callers manage persistence and UI; the engine does not save state or play audio.

| Operation | Current behavior |
| --- | --- |
| `startRound(): Boolean` | Starts only in `PREPARATION`; selects a word, sets the round clock and enters `PLAYING`. |
| `tick(): EngineEvent` / `checkpoint(): EngineEvent` | Accrues elapsed active time; expiration returns `EXPLOSION_STARTED`. `checkpoint()` delegates to `tick()` and does not itself persist. |
| `markCorrect(): ActionResult` | During play, accrues time, records the correct word and advances to the next player. |
| `swapWord(): ActionResult` / `canSwap(): Boolean` | Swap only during play after the seven-second active-time lock; record the old word as swapped. |
| `pauseManually()` / `resumeManually()` | Pause or resume active play; return whether the action was accepted. |
| `pauseForSystem()` / `resumeFromSystemPause()` | Suspend and restore active/preparation/manual-pause/explosion phases without counting time outside play. |
| `finishExplosion(): EngineEvent` | After acknowledgement, prepare the next round or finish the game. |
| `finishEarly(): EngineEvent` | End early, recording the current word if present and entering results. |

`ActionResult` contains `accepted` and an `EngineEvent`; callers must handle an explosion event even when an action is rejected. `EngineEvent` values are `NONE`, `EXPLOSION_STARTED` and `GAME_FINISHED`.

## Word source and models

`WordSource.nextWord(categories, difficulty, used, legacyGeneralDifficulty)` supplies a word; `count(...)` reports the selected pool size. The bundled `LocalWordBank` accepts non-empty supported categories and difficulty tiers 1–3. It draws unused normalized words and clears the used set when the pool is exhausted. The legacy difficulty parameter remains for compatibility but the current playable source does not use the general bank.

`GameState` contains players, selected categories, difficulty, phase, countdown, word history and team statistics. `GameConstants` defines supported setup options, swap lock and penalty. `SeatPlan` creates players and maps seats to teams. `rankTeams` orders by final time, explosions, correct answers and swaps. See `Models.kt` and [ARCHITECTURE.md](ARCHITECTURE.md#state-and-timing) for context.

## Local storage and sound

`LocalStore` provides `saveGame`, `loadGame`, `clearGame`, roster operations and sound-setting operations. Persistence is private SharedPreferences; methods returning nullable state can indicate no usable save. A failed game decode clears the unfinished checkpoint but retains the separate roster. `saveGame` with `durable=true` uses a synchronous preference commit; sound-setting saves use asynchronous apply.

`GameAudio` loads bundled sounds, applies `SoundSettings`, plays, stops and previews effects and releases `SoundPool`. Playback also depends on the device's sound state. These APIs are application internals and require an Android context.

## Build and tooling interfaces

Use `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` and `python3 tools/validate_words.py` where their prerequisites are installed. Release tasks require the four signing environment variables documented in [README.md](../README.md#build-and-test); do not place their values in files, logs or command lines. Word-bank generators modify the bundled JSON and should not be run casually; validate and review any resulting diff.
