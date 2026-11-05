# Armadillo changelog

This project follows semantic versioning (SemVer). The `1.0.0` entry below is the first official release of this repository and uses the `com.armadillo.game` application ID. The entries after it describe earlier builds of the previous `com.dour.game` identity and are kept as historical records only.

## [Unreleased]

- Consolidate Android CI into a reusable validation build, add OSV/POM dependency auditing and one-day temporary artifact retention with six-hour cleanup, and guard tag-triggered signed releases with protected tags, owner approval, version history and certificate verification.
- Centre the unboxed word and record a correct guess by tapping the play area; retain separate pause and full-width swap controls with a clear disabled state. Shorten the active-time swap lock to five seconds and migrate schema 3 saved cooldowns.
- Change the fifth team colour from gold to dark navy and replace the setup circle’s name labels with a top-down table and numbered, team-coloured armchairs; keep seat tapping and the accessible name-editing list.
- Replace the shared page-header back button with an accessible right-pointing arrow icon for RTL navigation.
- Apply RTL layout to all Compose screens and dialogs independently of the device language, retaining intentional local ordering of game and home controls.
- Redesign the home screen with transparent armadillo artwork, a shell-inspired play button, conditional saved-game continuation, About information on the page, and sound and rules icon buttons in the bottom corners.
- Generate the transparent home logo from the original artwork without changing the launcher assets.

## [1.0.0] — 2026-11-05

- Publish the first official release, tag `v1.0.0` (`versionCode` 1, `versionName` 1.0.0) for the `com.armadillo.game` identity, with a signed Release APK, an AAB and a `SHA256SUMS` file attached to the GitHub Release.
- Add the manual **Signed release** workflow: it materialises the release keystore from the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` secrets, checks that the requested tag matches `versionName`, runs the word-bank validator and the JVM tests, builds the signed APK and AAB, verifies the signer with `apksigner`, writes checksums, uploads the artifacts to the existing Release and always deletes the keystore afterwards. The workflow never creates tags or releases and does not run automatically.
- Record the signing certificate of `v1.0.0` (`SHA-256` `4F:40:5D:B1:95:4C:17:40:56:65:36:B8:56:8C:4B:AE:56:D5:A1:E6:18:4C:21:10:CD:32:39:AF:52:98:26:B8`) as the key future upgrades of this application ID must keep using.
- Move the entire UI to Jetpack Compose with Material 3 (Compose BOM 2026.09.00, Material3 1.4.0): all twelve screens, the eight dialogs and the seating circle are composables, `MainActivity` is a thin host and `GameController` owns the engine, timer, audio, storage and dialog state. The brand palette, Mikhak typography and static light theme are mapped onto a Material 3 colour scheme. `compileSdk` rises to 37 (Compose 1.12 requirement) while `targetSdk` stays 36, Mikhak moves from `assets/fonts` to `res/font`, back navigation uses one `OnBackPressedDispatcher` callback, and insets are applied once with `WindowInsets.safeDrawing`.
- Move every user-visible Persian string into `res/values/strings.xml` (Persian as the default locale), declare `android:localeConfig` with `res/xml/locales_config.xml`, and take the manifest label from `@string/app_name`. The engine no longer hard-codes player names: `SeatPlan.createPlayers` and `GameCodec.decode` accept an injected labeller that the app fills from resources.
- Split the deterministic engine, models, seat plan, checkpoint codec and roster merge into the pure Kotlin/JVM module `:game-core` (no Android dependency) and run its tests with `:game-core:test`; the app depends on it via `implementation(project(":game-core"))`.
- Move the toolchain to Gradle 9.8.2 (wrapper pinned with `distributionSha256Sum`), Android Gradle Plugin 9.5.0 and its built-in Kotlin compiler, and Kotlin 2.4.21 for the JVM module; drop the obsolete `org.jetbrains.kotlin.android` plugin, move `jvmTarget` to `kotlin.compilerOptions`, and raise the Gradle daemon memory after AGP 9 release lint ran out of metaspace.
- Annotate the pre-33 `onBackPressed()` override with the reason it stays next to the API 33+ `OnBackInvokedCallback`, so AGP 9's `GestureBackNavigation` check no longer fails the build.
- Replace the Lalezar typeface with the bundled Mikhak font (Regular and Bold masters, SIL OFL 1.1).
- Take the page canvas from the icon background (`#F9CB03`) and rebuild the palette around it; filled buttons stay navy while outlined buttons get a cream fill with a navy edge, so the two hierarchies are visually distinct again.
- Force one static light theme: no night resources and `forceDarkAllowed=false`, so the system theme setting cannot change the app.
- Regenerate the launcher assets with adaptive-icon safe-zone margins (artwork at 62% of the canvas), a monochrome layer for Android 13+ and a small hero bitmap.
- Raise `compileSdk`/`targetSdk` to 36, set `versionName` to 1.0.0, enable R8 minification and resource shrinking, and add the MIT licence.
- Migrate back navigation to `OnBackInvokedCallback` and adopt edge-to-edge insets, as API 36 requires.
- Extract `GameCodec` as a pure, unit-tested checkpoint codec; stop rewriting preferences from the home screen, report discarded checkpoints, cache the roster and prune finished audio streams.
- Load the word bank off the main thread, refresh live screens three times a second, skip unchanged views and preserve scroll position on in-place re-renders.
- Make seats individually reachable for screen readers, use platform buttons, add team numbers beside colour chips, follow the system font scale on the seating canvas and let the whole settings row toggle.
- Keep setup progress across process death and cap player-name length at 24 characters.
- Run unit tests, lint and the word-bank validator on pull requests, and add an unsigned Release build job; publish unit-test and lint failures as check annotations.
- Reset the Android `versionCode` to 1 because the `com.armadillo.game` application ID has never been published; planned historical builds used the earlier `com.dour.game` identity with `versionCode` 6.
- Upgrade the Debug workflow to `actions/checkout@v7`, `actions/setup-java@v6` and `actions/upload-artifact@v7`, replacing action versions that GitHub marked as deprecated.
- Scope the Debug workflow's automatic push trigger to workflow-file changes on `main`, because its previous trigger referenced a removed session branch, and update the documentation that described that branch.
- Rename the Gradle project, Kotlin packages, application ID and local preference file for Armadillo. The owner-approved application ID change creates a separate Android installation; earlier app data is not automatically transferred. Historical records retain their original identifiers.
- Consolidate the historical version-specific changelogs into this file.
- Add current requirements, architecture, internal interface and maintenance-plan documentation under `docs/`.
- Remove an unreferenced legacy drawable and the redundant `Dour-README.md` pointer; retain the main README and historical project records.
- Remove the configured user-facing version name while retaining the existing Android version code and application ID. The About screen shows a version only when one is configured for a future build.
- Document the repository's Debug APK workflow, artifact retention, trigger limitations, signing and upgrade caveats.
- Clarify historical test-report scope and correct documentation links without changing the application version or behavior.

## [2.2.0] — 2026-10-29

- Based this historical build on the then-latest verified v2.1.0 Drive source, increased `versionCode` to 6 and `versionName` to 2.2.0, and retained `applicationId=com.dour.game`.
- Set the central page background token to exactly `#F2C50A` and the background token for all buttons, including dialogs, to exactly `#07223C`. Retained white/disabled-gray high-contrast text, ripple feedback and selection borders.
- Removed the end button from the active game screen; early ending remains available from the pause panel/dialog and rounds still finish automatically.
- Made pause/resume available during preparation, active play, manual pause, system pause and the explosion screen. Repeated pauses and background restoration retain the player, word, history, remaining time and timer behavior; after restoring a manual pause, the user must explicitly continue.
- Retained Lalezar, team colors, original icon artwork, offline/free/ad-free behavior, Persian RTL and portrait orientation, game rules and timing, durable local saves, sound settings, and background time/audio pausing.
- The historical report records 21 passing unit tests, passing vocabulary validation and Debug/signed Release builds, and lint with 0 errors and 29 warnings; release signature, APK metadata and alignment checks passed, and the certificate fingerprint matched the previous key. No device/emulator installation or interaction was performed. These results refer to that historical build, not later checkouts. See [Historical-Test-Report.md](Historical-Test-Report.md) for details.

## [2.1.0] — 2026-10-29

- Continued from verified v2.0.0 source only; v1.1.0 was a reference for the pause control's placement and behavior.
- Restored the pause button after team timers, with timer/audio pause, durable saving and a panel for resume, ending and audio settings. Resume keeps the player, word and remaining time; the v2 early-end feature remains.
- Unified screens and the seating view with central color, spacing, radius, Lalezar font, text size and interaction tokens.
- Sampled yellow and navy from the original 1024×1024 icon (`#F9CB03` and `#07223D`); the icon file and artwork were unchanged. Preserved five team colors and used a navy border for the yellow indicator on the yellow background.
- Preserved v2 features, home-screen audio settings, `com.dour.game`, offline/free/ad-free behavior, Persian RTL, portrait orientation, the monotonic timer, saved games and background time/audio pausing.
- Increased `versionCode` to 5 and `versionName` to 2.1.0.
- The available `Historical-Test-Report.md` describes 2.2.0, not a separate 2.1.0 test run; do not treat its results as 2.1.0 verification.

## [2.0.0] — 2026-10-29

- Prepared a new Release from 1.2.0 while preserving game behavior and `applicationId=com.dour.game`; increased `versionCode` to 4 and `versionName` to 2.0.0.
- Added environment-variable-based Gradle signing configuration; no passwords or key files were included in the source or source archive.
- Built a Release APK and verified its RSA 2048 signature. The established key and recovery file were historically kept in an owner-only private signing folder at the owner's request.
- Recorded 17 passing unit tests and release lint with 0 errors and 29 warnings; no device/emulator installation test was performed.

## [1.2.0] — 2026-10-29

- Renamed the application Armadillo, retaining `applicationId=com.dour.game` and the existing Kotlin/Android codebase.
- Used the user's supplied icon artwork; retained the original unchanged and created only the required scaling and padding for the adaptive icon layer.
- Added the local Lalezar font with the SIL Open Font License 1.1; the app remained Persian/RTL, offline, free and ad-free.
- Added places and technology categories. The playable bank has five categories with 100 words at each easy/medium/hard level (1,500 unique words). The legacy general bank remains archived but is not used for new category selection or word cycles.
- Saved player names and order separately and prefilled them; migrated old saved state more safely to playable categories while retaining time, scores and consumed words.
- Changed round duration options to 120, 210 and 300 seconds, and round counts to 3, 5 and 7.
- Changed category selection to a three-column layout and moved sound settings to the home screen.
- Removed the pause control from the game screen; time and audio still paused during backgrounding, screen lock and system interruption.
- Set seats and turns to rotate counterclockwise, team colors to blue/red/green/purple/yellow, removed name frames in seat selection and added fixed confirmation buttons at the bottom of steps.
- Recorded displayed words with player name, display time, round and outcome.
- Increased `versionCode` to 3 and `versionName` to 1.2.0.

## [1.1.0] — 2026-10-26

- Increased the player limit to 10; even counts of 4, 6, 8 and 10 form 2 to 5 two-player teams respectively.
- Added multi-category word selection, fixed for the duration of each game.
- Added 900 unique category words for jobs, animals and foods: exactly 100 words at each easy, medium and hard level.
- Retained the 723-word general bank from 1.0.0 and added mapping for previously saved data, provided an in-place upgrade uses a compatible signing key.
- Completely removed word-explanation-specific modes and settings.
- Limited sound settings access to the pause panel and hid the countdown from the game view while retaining the internal timer.
- Simplified game cards to the player name and turn-transfer arrow.
- Kept the explosion animation visible until a player taps and performed the state transition only once.
- Added each team's player names, team color and actual game scores to results.
- Increased `versionCode` to 2 and `versionName` to 1.1.0.

## [1.0.0] — base version (historical `com.dour.game` build)

- Initial offline Persian version with timer, teams, pause/resume, audio, a 723-word general bank and team results.
