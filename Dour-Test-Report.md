# Build and verification report — Armadillo 2.2.0

This is the historical report for the 2026-10-29 release build, preserved as originally recorded. Its results do not verify later changes or other versions. For current build instructions and the separate later Debug CI run, see [README.md](README.md#build-and-test).

**Date:** 2026-10-29 (Asia/Tehran)  
**Project:** `/home/user/Dour/`  
**Base:** verified latest Drive source archive from `Releases/v2.1.0` (not an older release)  
**Application ID:** `com.dour.game`  
**Version:** `2.2.0` · `versionCode=6`  
**Release APK:** `Armadillo-v2.2.0-release.apk` (1,467,678 bytes)

## Build environment

- Eclipse Temurin / OpenJDK 17.0.21
- Android SDK Platform 35; Build Tools 35.0.0
- Gradle Wrapper 8.11.1; Android Gradle Plugin 8.9.2; Kotlin plugin 2.0.21
- Gradle memory: `-Xmx512m`, `MaxMetaspaceSize=384m`, one worker
- The existing `armadillo-release` keystore was restored from its owner-only Drive signing folder and used. Its public certificate fingerprint matches the prior v2.1.0/v2.0.0 release certificate. No replacement key was created; private signing files are excluded from source and report.

## Results

| Check | Result |
|---|---|
| `:app:testDebugUnitTest` | **Passed:** 21 tests, 0 failures, 0 errors, 0 skipped |
| Pause/resume regression tests | **Passed:** repeated manual pauses preserve current player, word, used-word set, history, accrued team time and remaining countdown; manual pause restores as paused after system interruption; pausing during preparation does not start the timer or lose setup |
| `python3 tools/validate_words.py` | **Passed:** 723 unique archived general words preserved; 1,500 unique playable topic words validated |
| `:app:assembleDebug` | **Passed** |
| `:app:assembleRelease` | **Passed:** signed release APK produced with the existing key |
| `:app:lintDebug` / release lint checks | **Passed:** 0 errors, 29 warnings |
| `aapt dump badging` | Confirmed `com.dour.game`, versionCode 6, versionName 2.2.0, min SDK 23 and target SDK 35 |
| `aapt dump resources` | Confirmed canvas resolves to `#F2C50A`; button-background token resolves to `#07223C`; enabled text is `#FFFFFF`; disabled text is `#BEC8D6` |
| Static pause/end-control review | Confirmed the active game page no longer creates the “پایان” button; the separate early-end action and automatic round completion logic remain. Pause/resume controls route through the current game phase and save checkpoints durably. |
| `apksigner verify --print-certs` | **Passed:** APK signature valid; certificate SHA-256 matches the prior release key |
| `zipalign -c -v 4` | **Passed:** APK alignment verified |
| Connected device/emulator | **Not performed:** `adb devices -l` listed no connected device; no emulator interaction or visual runtime check was performed. |

The build environment emitted a non-fatal Android SDK XML-version compatibility warning (tooling encountered SDK XML version 4 while one processor supports up to version 3); all build, signing, alignment, lint and unit-test tasks still completed successfully.

## Contrast check

WCAG relative-luminance ratios from the requested token values: white on `#07223C` **16.12:1**; disabled text `#BEC8D6` on `#07223C` **9.53:1**; navy `#07223C` on the page background `#F2C50A` **9.80:1**. These are static token checks, not a visual device test.

## Release integrity

- APK SHA-256: `843b72db80ba440979eb32ace7f4a9deec2ffe839aa0c22eb4eed86c27c5be4f`
- APK MD5: `64eda550f5c18415a74373afc4704cea`
- Signing-certificate SHA-256: `07:2A:8E:FB:08:75:88:FF:64:90:EB:E6:AF:75:C9:A8:9A:98:71:B5:F9:A2:F4:D5:1F:9D:6C:88:94:CC:EF:93`

## Limitations

- No physical-device or emulator install, interaction, or screenshot test was performed. The pause UI and color rendering were verified by source/resource inspection and JVM tests only.
- Lint reported 29 warnings and no errors. The non-fatal SDK XML tooling warning is noted above.
- No claim is made here about runtime behavior beyond the automated JVM tests and build/resource/signature checks listed above.
