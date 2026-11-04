# Armadillo — group word-guessing game

**Android / Kotlin · Unreleased**

Armadillo is an offline, right-to-left Persian group game. Its `applicationId` is `com.armadillo.game`. The app is free, ad-free and requires no internet permission; its sounds, font and words are bundled locally. It uses portrait orientation and has no background music. The manifest requests vibration permission and disables Android backup.

This repository does not designate a current official release or a user-facing application version. `versionCode` remains 6; choose a `versionName` and an appropriate `versionCode` when preparing a future release. The repository on GitHub is the source of truth. See [AGENTS.md](AGENTS.md) for project rules and [CHANGELOG.md](CHANGELOG.md) for changes. Historical test reports and changelogs describe earlier builds, not an official release of the current repository.

## Documentation

- [Requirements](docs/REQUIREMENTS.md) — current product, compatibility and verification requirements.
- [Architecture](docs/ARCHITECTURE.md) — components, state, timing and persistence.
- [Internal interfaces](docs/API.md) — Kotlin contracts; the app has no network API.
- [Project plan](docs/PROJECT_PLAN.md) — maintenance and verification backlog, not an approved release schedule.

## Features

- The central page background token is `#F2C50A`; button backgrounds use `#07223C`, with touch feedback and selection borders.
- Pause/resume is available during preparation, active play, manual pause, system pause and the explosion screen. An early-end action is available in the pause panel/dialog.
- The user's original icon artwork is retained; a scaled version with safe padding is used for the adaptive icon. The local Persian Lalezar font is bundled with its SIL Open Font License 1.1 (`app/src/main/assets/fonts/`).
- Playable categories are jobs, animals, foods, places and technology, each with 100 words at each easy, medium and hard level: 1,500 unique words. The previous 723-word general bank remains in `words.json` but is not used in category selection or the word cycle.
- Player names and order are stored separately and prefilled for subsequent games.
- Round duration options are 120, 210 or 300 seconds; round counts are 3, 5 or 7. Category selection uses a three-column grid, and sound settings are available on the home screen.
- Seats and turns rotate counterclockwise. Fixed team colors are blue, red, green, purple and yellow, in that order.
- Results record displayed words with player name, round, display time and outcome.
- Time and audio stop when the app goes into the background, the screen locks or a system interruption occurs; time outside the app is not counted.
- Within the app's own storage, migration of older saved-game formats removes the general category from active play while retaining time and scores. A legacy word may be replaced by a word from a playable category. This does not transfer data across different application IDs.

## Gameplay

1. Select 4, 6, 8 or 10 players; teams have two players each. The bottom seat starts, and turns rotate counterclockwise.
2. Enter names and select one or more categories. Selections remain fixed for that game.
3. Set difficulty, round duration and number of rounds.
4. During a round, pause to save and stop the game, current word and remaining time; resume from the same point. After a correct guess, use the guessed action to pass the phone and turn to the next player. Word replacement is disabled during the first seven seconds; a replaced word still counts as used.
5. When time expires, the team holding the phone receives a 10-second penalty. The explosion screen remains until a player taps it.
6. The results screen displays team times and the chronological word list.

## Project structure

- `app/src/main/java/com/armadillo/game/` — UI, models, engine, audio and local storage
- `app/src/main/java/com/armadillo/game/ui/DesignSystem.kt` — shared palette, typography and UI tokens
- `app/src/main/assets/categories.json` — five playable categories and 1,500 topic words
- `app/src/main/assets/words.json` — retained legacy general bank, inactive in gameplay
- `app/src/main/assets/fonts/` — Lalezar font and OFL license
- `app/src/main/res/drawable-nodpi/armadillo_icon_art.jpg` — original icon artwork copy
- `app/src/test/` — Kotlin/JVM unit tests
- `docs/` — requirements, architecture, internal interfaces and maintenance plan
- `tools/build_categories.py` — builds earlier categories while preserving newer categories
- `tools/add_armadillo_categories.py` — place and technology vocabulary
- `tools/validate_words.py` — validates the archived general bank and 1,500-word topic bank
- `tools/SESSION_HANDOFF.md` — historical session notes and verification results
- `.github/workflows/debug-apk.yml` — manual Debug build, artifact upload, unit tests and lint

## Architecture and persistence

`MainActivity` owns the Android UI and lifecycle and connects the game engine, local word bank, audio and storage. `GameEngine` applies game state transitions against a caller-supplied monotonic clock; `Models.kt` defines phases, players, teams and ranking. `LocalWordBank` reads bundled topic words and starts a new cycle after the selected pool is exhausted. `LocalStore` persists settings, the player roster and unfinished-game checkpoints in the private `armadillo_local_v1` SharedPreferences file. A restored unfinished game enters a system-paused state; a manual pause remains paused until the player resumes. The legacy general word bank is retained for historical compatibility, not as a playable category.

## Build requirements

- JDK 17 or newer (the recorded release build used Temurin JDK 17.0.21)
- Android SDK Platform 35 and Build Tools 35
- Gradle Wrapper 8.11.1
- Android 6.0 / API 23 minimum; `minSdk=23`, `targetSdk=35`

`local.properties` contains the builder's SDK path and is not part of the source archive.

## Build and test

Linux/macOS:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 tools/validate_words.py
# Release signing inputs must be loaded into the current shell from private storage first.
./gradlew :app:lintRelease :app:assembleRelease
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` and is signed with the local Android debug key. A Debug APK generally cannot update a Release APK signed with a different key. Uninstalling an existing app can delete its local data; back it up before taking that step.

Release builds require `RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`. Do not put actual passwords in source, `gradle.properties` or the command line. Keep the original keystore and its recovery information in secure private storage, outside the repository.

Windows:

```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
gradlew.bat lintDebug
```

The signed release APK is produced at:

```text
app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions Debug build

The [Debug APK workflow](.github/workflows/debug-apk.yml) can be started manually from the GitHub Actions page with `workflow_dispatch` and an available branch. It assembles the Debug APK, uploads `armadillo-debug-apk` as a ZIP artifact retained for seven days, then runs `:app:testDebugUnitTest` and `:app:lintDebug`. Download the artifact from the run page while it is available; repository access is required. The upload occurs before tests and lint, so check the final run result as well as the artifact. It does not publish a release or create a tag.

The workflow's automatic push trigger runs only for changes to the workflow file on `main`; it is not a general continuous-integration trigger for other changes or branches. The historical [run 41904815606](https://github.com/behrouzxp-dev/Armadillo/actions/runs/41904815606) passed the Debug build, artifact upload, unit-test and lint steps. Its warnings noted deprecated action runtimes and `setup-java` v4; it did not test installation or startup. A past successful run does not establish that a future checkout builds successfully.

## Signing and upgrades

- The application ID has changed from `com.dour.game` to `com.armadillo.game` with owner approval. Android treats them as separate applications: the new build cannot upgrade the old installation and cannot automatically access its private saved games, roster or settings. Keep the old installation and its data until the owner decides otherwise; there is no implemented cross-application data migration.
- Keep the existing release keystore and credentials securely backed up outside the repository. Never replace an established key without explicit authorization.
- The public certificate fingerprint recorded for earlier signed builds is `07:2A:8E:FB:08:75:88:FF:64:90:EB:E6:AF:75:C9:A8:9A:98:71:B5:F9:A2:F4:D5:1F:9D:6C:88:94:CC:EF:93`; see the historical [test report](Historical-Test-Report.md). This record does not verify a future build or the current availability of the key.
- Android upgrades depend on compatible signing and version codes. In particular, a Debug APK normally cannot replace an installation signed with a different Release key.
- Historical reports document past builds and limitations, not current build or device-test results. No official release from this repository has been designated by this documentation.
