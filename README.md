# Armadillo — group word-guessing game

**Android / Kotlin · latest release v1.0.0**

Armadillo is an offline, right-to-left Persian group game. Its `applicationId` is `com.armadillo.game`. The app is free, ad-free and requires no internet permission; its sounds, font and words are bundled locally. It uses portrait orientation and has no background music. The manifest requests vibration permission and disables Android backup.

The first official release is [v1.0.0](https://github.com/behrouzxp-dev/Armadillo/releases/tag/v1.0.0) (`versionCode` 1, `versionName` 1.0.0) for the `com.armadillo.game` application ID; its signed Release APK and AAB are attached to the GitHub Release. The source is prepared for `1.1.0` (`versionCode` 2), which is not yet published; verify version codes in external distribution channels before release. The repository on GitHub is the source of truth. See [AGENTS.md](AGENTS.md) for project rules and [CHANGELOG.md](CHANGELOG.md) for changes. Historical test reports and changelogs describe earlier builds, not an official release of the current repository.

## Documentation

- [Requirements](docs/REQUIREMENTS.md) — current product, compatibility and verification requirements.
- [Architecture](docs/ARCHITECTURE.md) — components, state, timing and persistence.
- [Internal interfaces](docs/API.md) — Kotlin contracts; the app has no network API.
- [Project plan](docs/PROJECT_PLAN.md) — maintenance and verification backlog, not an approved release schedule.
- [GitHub Actions](docs/GITHUB_ACTIONS.md), [release guide](docs/RELEASE_GUIDE.md) and [secrets setup](docs/SECRETS_SETUP.md) — workflow audit, operations and signing prerequisites.

## Features

- The UI is Jetpack Compose with Material 3 components (Compose BOM 2026.09.00, Material3 1.4.0) on a Material 3 colour scheme mapped onto the existing brand palette; every screen, dialog and the seating circle is a composable. The app uses RTL layout independently of the device language, with local exceptions for controls whose physical order is intentional.
- The page canvas is taken from the icon background (`#F9CB03`), so the launcher icon and the app share one colour. Filled buttons use navy `#07223C` with white text; outlined buttons use a cream fill with a navy edge, giving the two actions distinct visual weight. The theme is static: it is always light and never follows the system theme setting.
- The home screen displays a transparent version of the armadillo artwork, a shell-inspired play button, conditional saved-game continuation, in-page About information and icon buttons for sound settings and rules in the lower corners.
- The bundled Mikhak typeface (Regular and Bold masters, SIL Open Font License 1.1) is used for every label, button and canvas text.
- Pause/resume is available during preparation, active play, manual pause, system pause and the explosion screen. An early-end action is available in the pause panel/dialog.
- The user's original icon artwork is never edited; the launcher assets are generated from it with adaptive-icon safe-zone margins (artwork at 62% of the canvas) and include a monochrome layer for Android 13+ themed icons. `python3 tools/build_icon.py` regenerates them.
- Playable categories are jobs, animals, foods, places and technology, each with 100 words at each easy, medium and hard level: 1,500 unique words. The previous 723-word general bank remains in `words.json` but is not used in category selection or the word cycle.
- Player names and order are stored separately and prefilled for subsequent games.
- Round duration options are 120, 210 or 300 seconds; round counts are 3, 5 or 7. Category selection uses a three-column grid, and sound settings are available on the home screen.
- Seats and turns rotate counterclockwise. Fixed team colors are blue, red, green, purple and dark navy, in that order. The player setup circle shows a top-down table surrounded by team-coloured, numbered armchairs; tapping one opens name editing, and the accessible seat list remains below it.
- Results record displayed words with player name, round, display time and outcome.
- Time and audio stop when the app goes into the background, the screen locks or a system interruption occurs; time outside the app is not counted.
- Within the app's own storage, migration of older saved-game formats removes the general category from active play while retaining time and scores. A legacy word may be replaced by a word from a playable category. This does not transfer data across different application IDs.

## Gameplay

1. Select 4, 6, 8 or 10 players; teams have two players each. The bottom seat starts, and turns rotate counterclockwise.
2. Enter names and select one or more categories. Selections remain fixed for that game.
3. Set difficulty, round duration and number of rounds.
4. During a round, pause to save and stop the game, current word and remaining time; resume from the same point. After a correct guess, tap the game screen to pass the phone and turn to the next player; the pause and swap controls retain their own actions. Word replacement is disabled during the first five seconds; a replaced word still counts as used.
5. When time expires, the team holding the phone receives a 10-second penalty. The explosion screen remains until a player taps it.
6. The results screen displays team times and the chronological word list.

## Project structure

- `app/src/main/java/com/armadillo/game/MainActivity.kt` — thin Compose host: lifecycle, edge-to-edge and back dispatch
- `app/src/main/java/com/armadillo/game/GameController.kt` — engine, timer, persistence, audio and dialog state
- `app/src/main/java/com/armadillo/game/ui/` — Compose screens, dialogs, shared components and the Compose seat circle
- `app/src/main/java/com/armadillo/game/ui/theme/` — Material 3 theme, brand palette, Mikhak typography and spacing tokens
- `app/src/main/java/com/armadillo/game/audio/`, `data/` — SoundPool audio and SharedPreferences storage
- `app/src/main/res/values/strings.xml` — every user-visible Persian string (the default locale)
- `app/src/main/res/xml/locales_config.xml` — the `fa` locale declared for Android 13+ per-app language settings
- `game-core/src/main/kotlin/com/armadillo/game/game/` — engine, models and seat plan
- `game-core/src/main/kotlin/com/armadillo/game/data/` — pure JSON checkpoint codec and roster merging
- `app/src/main/assets/categories.json` — five playable categories and 1,500 topic words
- `app/src/main/assets/words.json` — retained legacy general bank, inactive in gameplay
- `app/src/main/res/font/` — Mikhak Regular/Bold as Android font resources; `app/src/main/assets/fonts/` — their OFL license
- `app/src/main/res/drawable-nodpi/` — original icon artwork, generated adaptive foreground, monochrome layer, hero bitmap and transparent home logo
- `game-core/src/test/` — Kotlin/JVM unit tests for the engine, codec and roster
- `docs/` — requirements, architecture, internal interfaces and maintenance plan
- `tools/build_categories.py` — builds earlier categories while preserving newer categories
- `tools/add_armadillo_categories.py` — place and technology vocabulary
- `tools/validate_words.py` — validates the archived general bank and 1,500-word topic bank
- `tools/build_icon.py` — regenerates the launcher assets from the original artwork
- `tools/SESSION_HANDOFF.md` — historical session notes and verification results
- `.github/workflows/` — CI, reusable build, security audit, guarded release and temporary-artifact cleanup workflows

## Architecture and persistence

The repository has two Gradle modules. `:game-core` is a plain Kotlin/JVM library with no Android dependency: it holds the deterministic engine, the models, the seat plan, the checkpoint codec and the roster merge, and its tests run on the JVM without the Android plugin. `:app` is the Android application and depends on `:game-core`. `MainActivity` is a thin `ComponentActivity` that hosts the Compose tree, applies edge-to-edge insets once and forwards lifecycle and back events to `GameController`. `GameController` owns the engine, timer, word bank, audio, storage and dialog state and exposes observable state that the composables read. `GameEngine` applies game state transitions against a caller-supplied monotonic clock; `Models.kt` defines phases, players, teams and ranking. `LocalWordBank` reads bundled topic words and starts a new cycle after the selected pool is exhausted. `LocalStore` persists settings, the player roster and unfinished-game checkpoints in the private `armadillo_local_v1` SharedPreferences file. A restored unfinished game enters a system-paused state; a manual pause remains paused until the player resumes. The legacy general word bank is retained for historical compatibility, not as a playable category.

## Build requirements

- JDK 17 or newer (the recorded release build used Temurin JDK 17.0.21)
- Android SDK Platform 37 and Build Tools 36 (Compose 1.12 requires `compileSdk 37`; `targetSdk` stays 36)
- Gradle Wrapper 9.8.2, checked against `distributionSha256Sum`
- Android Gradle Plugin 9.5.0 with its built-in Kotlin compiler (Kotlin 2.4.21 for the JVM module and for the Compose compiler plugin); the obsolete `org.jetbrains.kotlin.android` plugin is no longer applied
- Android 6.0 / API 23 minimum; `minSdk=23`, `targetSdk=36` (Google Play requires API 36 for new uploads since 31 August 2026)

`local.properties` contains the builder's SDK path and is not part of the source archive.

## Build and test

Linux/macOS:

```bash
./gradlew :game-core:test :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 tools/validate_words.py
# An unsigned release build verifies R8, resource shrinking and release lint.
./gradlew :app:lintRelease :app:assembleRelease
# Release signing inputs must be loaded into the current shell from private storage first.
```

The Debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` and is signed with the local Android debug key. A Debug APK generally cannot update a Release APK signed with a different key. Uninstalling an existing app can delete its local data; back it up before taking that step.

Release builds require `RELEASE_KEYSTORE_PATH`, `RELEASE_KEY_ALIAS`, `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD`. Do not put actual passwords in source, `gradle.properties` or the command line. Keep the original keystore and its recovery information in secure private storage, outside the repository.

Windows:

```bat
gradlew.bat :game-core:test :app:testDebugUnitTest
gradlew.bat assembleDebug
gradlew.bat lintDebug
```

The signed release APK is produced at:

```text
app/build/outputs/apk/release/app-release.apk
```

## GitHub Actions builds

CI runs the shared Android validation build on pushes to `main`/`develop` and pull requests into those branches. It runs unit tests, lint and Debug/unsigned Release builds and retains the optional Debug APK for one day. A separate read-only security workflow scans resolved dependencies against OSV and reports POM license metadata. The tag-triggered signed release requires a protected tag, prior validation and explicit owner approval in the protected `production` environment. A cleanup workflow sweeps known temporary Actions artifacts every six hours; official release assets are preserved.

See [GitHub Actions](docs/GITHUB_ACTIONS.md) for the audit, required branch settings and limitations, [release guide](docs/RELEASE_GUIDE.md) for release steps, and [secrets setup](docs/SECRETS_SETUP.md) for credentials. No signed release is published merely by merging the workflow. No device/emulator testing is implied by CI.

## Theme and accessibility

- One static light theme: there is no `values-night` variant and `android:forceDarkAllowed` is `false`, so changing the system theme cannot change the app's colours.
- Buttons are platform `Button`s, so TalkBack announces them as buttons. Each seat on the players screen is also exposed as an individual button, because a canvas drawing cannot provide per-seat accessibility nodes.
- Team identity is never colour-only: team numbers accompany every team chip, label and result row.
- Body text on the canvas (`#38434F` on `#F9CB03`) and white text on navy buttons exceed the WCAG AA contrast ratio. Team numbers accompany team colours, so team identity does not depend on colour alone.
- The seating canvas now follows the system font scale, and pages preserve their scroll position when a selection re-renders them.

## License

The repository is released under the [MIT License](LICENSE). Bundled assets keep their own licences; the Mikhak typeface is SIL OFL 1.1.

## Signing and upgrades

- The application ID has changed from `com.dour.game` to `com.armadillo.game` with owner approval. Android treats them as separate applications: the new build cannot upgrade the old installation and cannot automatically access its private saved games, roster or settings. Keep the old installation and its data until the owner decides otherwise; there is no implemented cross-application data migration.
- Keep the existing release keystore and credentials securely backed up outside the repository. Never replace an established key without explicit authorization.
- The `com.armadillo.game` release is signed with its own key: the `v1.0.0` certificate is `SHA-256` `4F:40:5D:B1:95:4C:17:40:56:65:36:B8:56:8C:4B:AE:56:D5:A1:E6:18:4C:21:10:CD:32:39:AF:52:98:26:B8`. Future versions of this application ID must keep using it so Android accepts upgrades.
- The earlier `com.dour.game` builds used a different certificate, `SHA-256` `07:2A:8E:FB:08:75:88:FF:64:90:EB:E6:AF:75:C9:A8:9A:98:71:B5:F9:A2:F4:D5:1F:9D:6C:88:94:CC:EF:93`; see the historical [test report](Historical-Test-Report.md).
- Android upgrades depend on compatible signing and version codes. In particular, a Debug APK normally cannot replace an installation signed with a different Release key.
- Historical reports document past builds and limitations, not current build or device-test results. The current release status is the one stated at the top of this file; no device or emulator test accompanies `v1.0.0`.
