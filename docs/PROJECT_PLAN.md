# Project plan and verification backlog

This is a maintenance checklist based on the current repository, not an approved schedule or promise of new features. Track substantial work in GitHub Issues when appropriate and obtain owner approval before releases or sensitive changes, as specified in [AGENTS.md](../AGENTS.md).

## Current baseline

- Two Gradle modules: the `:app` Android application with bundled Persian word banks and private local persistence, and `:game-core`, a dependency-free Kotlin/JVM library holding the engine, models, seat plan, checkpoint codec and roster merge, with its own JVM tests. See [REQUIREMENTS.md](REQUIREMENTS.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
- The repository is not designated as an official release. `versionName` is `1.0.0` and `versionCode` is 1 because this application ID has never been published; both must be raised for the first store upload. The application ID change creates a separate installation and does not transfer the old app's data.
- The Android build workflow runs on every pull request: word-bank validation, Debug build and APK identity check, artifact upload, unit tests, lint, plus an unsigned Release build with R8 and release lint. See [README.md](../README.md#github-actions-builds).
- Historical reports and previous CI runs describe the specific builds they tested; they do not establish the results of subsequent changes.

## Verification to perform when available

1. Run the current Debug and unsigned Release builds, JVM unit tests, lint and vocabulary validator after relevant changes. Report actual results and any warnings.
2. On a physical Android device or emulator, test installation and startup, Persian/RTL rendering, setup, pause/resume, interruptions, sound and results. Device testing has not been established merely by the historical JVM tests.
3. For an approved release only, verify the owner's existing signing configuration, build the release, verify the artifact and signing certificate, and test an upgrade from a compatible installation. Never replace the established key without approval.
4. Review GitHub Actions deprecation warnings before changing automation; any workflow change should be tested separately.

## Release readiness decisions

Before any first official repository release, the owner must approve the release and its version, and the team must verify the Android version code and signing compatibility. Use GitHub Releases and tags only after approval and verification. Preserve historical records and do not commit credentials, keys, generated caches or temporary artifacts.

## Completed architecture work

- **Localised strings (done).** Every user-visible Persian string lives in `app/src/main/res/values/strings.xml` as the default locale; `res/xml/locales_config.xml` and `android:localeConfig` declare `fa` for the Android 13+ per-app language settings. `:game-core` holds no UI text: `SeatPlan.createPlayers` and `GameCodec.decode` take an injectable name labeller that the app supplies from resources.
- **Pure engine module (done).** `:game-core` contains the engine, models, seat plan, checkpoint codec and roster merge, and runs its tests with the plain Kotlin JVM plugin; the app depends on it with `implementation(project(":game-core"))`.
- **Compose UI (done).** The screens, the eight dialogs and the seating circle are Jetpack Compose composables on Material 3, with the brand palette and Mikhak typography mapped onto a static light colour scheme. `MainActivity` is a thin host; `GameController` owns the engine, timer, persistence, audio and dialog state. Compose 1.12 (BOM 2026.09.00) requires `compileSdk 37`; `targetSdk` stays 36.
- **Toolchain (done).** Gradle wrapper 9.8.2 (pinned with `distributionSha256Sum`), Android Gradle Plugin 9.5.0 with its built-in Kotlin compiler, and Kotlin 2.4.21 for the JVM module. The obsolete `org.jetbrains.kotlin.android` plugin is gone, `jvmTarget` moved to `kotlin.compilerOptions`, and `gradle.properties` now reserves enough memory for AGP 9's release lint. Verified only through CI; no local build was performed.

## Planned technical work

These items are not implemented yet and need their own reviewed changes:

1. Add Compose UI tests (`androidx.compose.ui:ui-test-junit4`) and instrumented tests and an emulator job so pause, resume, explosion and results flows are verified on a real Android runtime.
2. Generate Gradle dependency verification metadata (`gradle/verification-metadata.xml`) on a trusted machine with `./gradlew --write-verification-metadata sha256 help` and commit the result. It is deliberately not generated in CI, where the first download would only attest to itself.

## Documentation maintenance

Update the relevant requirements, architecture and internal interfaces when behavior or structure changes. Keep [CHANGELOG.md](../CHANGELOG.md) accurate. Avoid restating a past test result as a current result; preserve the historical test report as evidence of its own build only.
