# Armadillo 2.2.0 — durable session handoff

Original handoff date: 2026-10-29 (Asia/Tehran)

This is a historical record of the 2.2.0 release, not a current deployment checklist. Paths, storage locations and verification results below describe that earlier environment and may no longer be available. Current project work must follow [AGENTS.md](../AGENTS.md): GitHub is the authoritative source for project files and releases, and signing keys must be securely preserved outside the repository. For current build instructions, including the Debug workflow, see [README.md](../README.md).

## Release state at handoff

- The release source was based on the then-latest verified Drive release, v2.1.0; older versions were comparison/history only.
- Project at the time: the historical `Dour` project; `applicationId=com.dour.game`; `versionName=2.2.0`; `versionCode=6`.
- Requested design tokens are exact: page canvas `#F2C50A`; all app/dialog button fills `#07223C`. Text uses accessible white/disabled-gray states; Lalezar and team identity colors remain. Original icon artwork was not changed.
- The active game page no longer has a “پایان” button. Early end remains separate in the pause overlay/dialog; automatic completion remains.
- The pause/resume control is reachable in preparation, playing, manual-pause, system-pause and explosion states. Repeated pause/resume preserves player, current word, word history, used words and remaining time. System interruption restores a manual pause as paused until the user explicitly continues.
- Offline/free/ad-free use, Persian RTL, portrait orientation, no background music, game rules/timing, durable local saves, settings and stopping time/audio in the background remain in scope.

## Verification

- `:app:testDebugUnitTest`: 21 passed; 0 failures/errors/skips.
- `python3 tools/validate_words.py`: passed; 723 unique archived general words and 1,500 unique topic words.
- `:app:assembleDebug`, `:app:assembleRelease`: passed. `:app:lintDebug` and release lint checks passed with 0 errors and 29 warnings.
- APK metadata: package `com.dour.game`, version 2.2.0/code 6, min SDK 23, target SDK 35.
- Release signature matches the existing v2.1.0/v2.0.0 certificate (`07:2A:8E:FB:08:75:88:FF:64:90:EB:E6:AF:75:C9:A8:9A:98:71:B5:F9:A2:F4:D5:1F:9D:6C:88:94:CC:EF:93`); the prior key was reused. Never include private signing material in source, APK, reports, logs or chat.
- APK SHA-256: `843b72db80ba440979eb32ace7f4a9deec2ffe839aa0c22eb4eed86c27c5be4f`.
- APK signature and `zipalign` checks passed; resource table contains the requested color values. No device/emulator install, interaction or visual runtime test was performed; `adb devices -l` returned no connected devices.
- Toolchain: Temurin/OpenJDK 17.0.21, SDK Platform 35, Build Tools 35.0.0, Gradle 8.11.1, AGP 8.9.2 and Kotlin 2.0.21. One non-fatal SDK XML-version compatibility warning appeared; all builds/tests passed.

## Drive and backups

- The existing releases parent contained `Releases/v2.2.0`, recorded as owner-only, and the previous source base under `Releases/v2.1.0`. Storage identifiers have been omitted to reduce unnecessary disclosure.
- The v2.1.0 source ZIP had Drive/local MD5 `34ceb8952f3af8143b325e839e9eddf9`. A pre-edit source backup was recorded with SHA-256 `32892b50d59d0c60541f990e074a025ead4ce3c69f68fa1cd0af7e781c290c61`; its local path has been omitted.
- Existing owner-only signing folder was used. Keep that key unchanged for future releases; private files were temporarily staged outside the project in protected storage for signing and must not enter archives.
- The original handoff instructed that prior release folders be preserved and the v2.2.0 deliverables verified in Drive. This is historical context only; use GitHub for future source and release management as required by `AGENTS.md`. Preserve existing backups and signing material.

## Local deliverables at handoff

- The Release APK was produced at `app/build/outputs/apk/release/app-release.apk` in the historical build environment; its presence in a later checkout is not implied.
- Source documentation at handoff: `README.md`, `Dour-README.md`, `CHANGELOG.md`, `Dour-Test-Report.md`.
- Build-local `local.properties`, `.gradle/`, `.kotlin/` and `app/build/` are not source-release content. Exclude these, signing material, SDK/toolchain and caches from source archives.
