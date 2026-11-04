# Project plan and verification backlog

This is a maintenance checklist based on the current repository, not an approved schedule or promise of new features. Track substantial work in GitHub Issues when appropriate and obtain owner approval before releases or sensitive changes, as specified in [AGENTS.md](../AGENTS.md).

## Current baseline

- One offline Android application module with bundled Persian word banks and private local persistence. See [REQUIREMENTS.md](REQUIREMENTS.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
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

## Planned technical work

These items are not implemented yet and need their own reviewed changes:

1. Move the UI from hand-built platform views in `MainActivity` to Material 3 or Jetpack Compose and extract the view helpers into a reusable kit.
2. Extract every user-visible Persian string into `strings.xml` with a `localeConfig`, even while the app stays Persian-only.
3. Split the deterministic game engine and codec into a pure Kotlin module (`:game-core`) so tests run without the Android plugin.
4. Raise the toolchain (AGP, Gradle wrapper, Kotlin) and add a wrapper `distributionSha256Sum` plus dependency verification metadata.
5. Add instrumented/UI tests and an emulator job so pause, resume, explosion and results flows are verified on a real Android runtime.

## Documentation maintenance

Update the relevant requirements, architecture and internal interfaces when behavior or structure changes. Keep [CHANGELOG.md](../CHANGELOG.md) accurate. Avoid restating a past test result as a current result; preserve the historical test report as evidence of its own build only.
