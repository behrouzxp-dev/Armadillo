# Project plan and verification backlog

This is a maintenance checklist based on the current repository, not an approved schedule or promise of new features. Track substantial work in GitHub Issues when appropriate and obtain owner approval before releases or sensitive changes, as specified in [AGENTS.md](../AGENTS.md).

## Current baseline

- Two Gradle modules: the `:app` Android application with bundled Persian word banks and private local persistence, and `:game-core`, a dependency-free Kotlin/JVM library holding the engine, models, seat plan, checkpoint codec and roster merge, with its own JVM tests. See [REQUIREMENTS.md](REQUIREMENTS.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
- `v1.0.0` is the first official release (`versionName` `1.0.0`, `versionCode` 1, signed APK and AAB attached to the GitHub Release). GitHub releases `v1.1.0` and `v1.2.0` use `versionCode` 2 and 3; the source is configured for `1.3.0` with `versionCode` 4. External distribution codes must be verified before distributing there, and release prerequisites must be checked before publication. The application ID change creates a separate installation and does not transfer the old app's data.
- CI uses a shared validation build for word-bank validation, Debug and unsigned Release builds, tests and lint; pull requests also run Compose instrumented tests on an API 35 emulator. Security audits resolved dependencies. The signed release is manually dispatched by the owner from `main` after explicit approval; it does not require a protected environment or tag. See [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) and [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for prerequisites and limitations.
- Historical reports and previous CI runs describe the specific builds they tested; they do not establish the results of subsequent changes.

## Verification to perform when available

1. Run the current Debug and unsigned Release builds, JVM unit tests, lint and vocabulary validator after relevant changes. Report actual results and any warnings.
2. On a physical Android device, test installation, upgrade from a compatible signed build, Persian/RTL rendering, large-screen layouts, pause/resume, explosion acknowledgement, interruptions, sound and results. The emulator job covers only a home-screen smoke test; it does not replace physical-device verification.
3. For an approved release only, verify the owner's existing signing configuration, build the release, verify the artifact and signing certificate, and test an upgrade from a compatible installation. Never replace the established key without approval.
4. Review GitHub Actions deprecation warnings before changing automation; any workflow change should be tested separately.

## Release readiness decisions

Before any new official repository release, the owner must approve the release and its version, and the team must verify the Android version code and signing compatibility. Use GitHub Releases and tags only after approval and verification. Preserve historical records and do not commit credentials, keys, generated caches or temporary artifacts.

## Completed architecture work

- **Localised strings (done).** Every user-visible Persian string lives in `app/src/main/res/values/strings.xml` as the default locale; `res/xml/locales_config.xml` and `android:localeConfig` declare `fa` for the Android 13+ per-app language settings. `:game-core` holds no UI text: `SeatPlan.createPlayers` and `GameCodec.decode` take an injectable name labeller that the app supplies from resources.
- **Pure engine module (done).** `:game-core` contains the engine, models, seat plan, checkpoint codec and roster merge, and runs its tests with the plain Kotlin JVM plugin; the app depends on it with `implementation(project(":game-core"))`.
- **Compose UI (done).** The screens, the eight dialogs and the seating circle are Jetpack Compose composables on Material 3, with the brand palette and Mikhak typography mapped onto a static light colour scheme. `MainActivity` is a thin host; `GameController` owns the engine, timer, persistence, audio and dialog state. Compose 1.12 (BOM 2026.09.00) requires `compileSdk 37`; `targetSdk` stays 36.
- **Toolchain (done).** Gradle wrapper 9.8.2 (pinned with `distributionSha256Sum`), Android Gradle Plugin 9.5.0 with its built-in Kotlin compiler, and Kotlin 2.4.21 for the JVM module. The obsolete `org.jetbrains.kotlin.android` plugin is gone, `jvmTarget` moved to `kotlin.compilerOptions`, and `gradle.properties` now reserves enough memory for AGP 9's release lint. Verified only through CI; no local build was performed.
- **Instrumented test scaffolding (done).** `:app` declares `androidx.test` and Compose `ui-test-junit4`, uses `AndroidJUnitRunner`, includes a home-screen Compose test and a pure `GameSetupMath` JVM test, and `ci.yml` runs `:app:connectedDebugAndroidTest` on an API 35 emulator for pull requests and manual dispatches.

## Planned technical work

These items are not implemented yet and need their own reviewed changes:

1. Generate Gradle dependency verification metadata (`gradle/verification-metadata.xml`) on a trusted machine with `./gradlew --write-verification-metadata sha256 help` and commit the result. It is deliberately not generated in CI, where the first download would only attest to itself. This environment has no JVM, Android SDK or Gradle distribution, so the file was **not** generated here; do not assume it exists.
2. Extend the emulator instrumented tests beyond the home-screen smoke test to cover pause/resume, explosion acknowledgement and results flows, and verify installation/upgrade and large-screen layouts on a physical device.

## Documentation maintenance

Update the relevant requirements, architecture and internal interfaces when behavior or structure changes. Keep [CHANGELOG.md](../CHANGELOG.md) accurate. Avoid restating a past test result as a current result; preserve the historical test report as evidence of its own build only.
