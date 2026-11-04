# Armadillo — group word-guessing game

**Version 2.2.0 · versionCode 6 · Android / Kotlin**

Armadillo is an offline, right-to-left Persian group game. Its `applicationId` remains `com.dour.game` to preserve the application identity. The app is free, ad-free and requires no internet permission; its sounds, font and words are bundled locally. It uses portrait orientation and has no background music.

## Version 2.2.0

- This version was based on the verified v2.1.0 source. `applicationId=com.dour.game` was retained, with `versionCode=6` and `versionName=2.2.0`.
- The central page background token is exactly `#F2C50A`; the background token for all buttons, including dialog buttons, is exactly `#07223C`. Active button text is white and disabled text is high-contrast gray; touch feedback and selection borders are retained.
- The end button was removed from the active game screen. Early ending remains a separate action in the pause panel/dialog, and automatic round completion is unchanged.
- Pause/resume is available during preparation, active play, manual pause, system pause and the explosion screen. Repeated pauses, interruptions and background restoration retain the player, word, history, remaining time and timer behavior.
- Lalezar, the five team identity colors, original icon artwork and other v2.1.0 features remain unchanged. The original icon artwork was not edited.
- The original release report records unit tests, Debug/Release APK builds, lint, vocabulary validation, signing and APK alignment checks. See `Dour-Test-Report.md`; no device or emulator test was performed.

## Version 2.0.0

- This was a new release of the same feature set as 1.2.0, without changes to game functionality.
- `applicationId=com.dour.game` was retained; `versionCode=4` and `versionName=2.0.0`.
- The 2.0.0 APK was a **Release** signed with the established Armadillo key.
- Gradle reads signing inputs from environment variables rather than storing passwords or keys in source. Key files remain outside source archives.

## Features added in 1.2.0

- The application and icon were renamed Armadillo. The user's original artwork was retained without artistic changes; only a scaled version with safe padding was created for the adaptive icon.
- The local Persian Lalezar font is bundled with its SIL Open Font License 1.1 (`app/src/main/assets/fonts/`).
- Playable categories are jobs, animals, foods, places and technology, each with 100 words at each easy, medium and hard level: 1,500 unique words. The previous 723-word general bank remains in `words.json` but is not used in category selection or the word cycle.
- Player names and order are stored separately and prefilled for subsequent games.
- Round duration options are 120, 210 or 300 seconds; round counts are 3, 5 or 7.
- Category selection uses a three-column grid, and sound settings were moved to the home screen. The pause button was removed in 1.2.0, restored in 2.1.0 and made available across live game states in 2.2.0, without changing v2 engine rules.
- Seats and turns rotate counterclockwise. Fixed team colors are blue, red, green, purple and yellow, in that order.
- Results record displayed words with player name, round, display time and outcome.
- Time and audio stop when the app goes into the background, the screen locks or a system interruption occurs; time outside the app is not counted.
- Migration of old saves removes the general category from active play while retaining time and scores. Since an old word may come from the general bank, a new word from a playable category is selected on continuation.

## Gameplay

1. Select 4, 6, 8 or 10 players; teams have two players each. The bottom seat starts, and turns rotate counterclockwise.
2. Enter names and select one or more categories. Selections remain fixed for that game.
3. Set difficulty, round duration and number of rounds.
4. During a round, pause to save and stop the game, current word and remaining time; resume from the same point. After a correct guess, use the guessed action to pass the phone and turn to the next player. Word replacement is disabled during the first seven seconds; a replaced word still counts as used.
5. When time expires, the team holding the phone receives a 10-second penalty. The explosion screen remains until a player taps it.
6. The results screen displays team times and the chronological word list.

## Project structure

- `app/src/main/java/com/dour/game/` — UI, models, engine, audio and local storage
- `app/src/main/java/com/dour/game/ui/DesignSystem.kt` — shared palette, typography and UI tokens
- `app/src/main/assets/categories.json` — five playable categories and 1,500 topic words
- `app/src/main/assets/words.json` — retained legacy general bank, inactive in gameplay
- `app/src/main/assets/fonts/` — Lalezar font and OFL license
- `app/src/main/res/drawable-nodpi/armadillo_icon_art.jpg` — original icon artwork copy
- `app/src/test/` — Kotlin/JVM unit tests
- `tools/build_categories.py` — builds earlier categories while preserving newer categories
- `tools/add_armadillo_categories.py` — place and technology vocabulary
- `tools/validate_words.py` — validates the archived general bank and 1,500-word topic bank
- `tools/SESSION_HANDOFF.md` — historical session notes and verification results

## Build requirements

- JDK 17 or newer (the recorded release build used Temurin JDK 17.0.21)
- Android SDK Platform 35 and Build Tools 35
- Gradle Wrapper 8.11.1
- Android 6.0 / API 23 minimum; `minSdk=23`, `targetSdk=35`

`local.properties` contains the builder's SDK path and is not part of the source archive.

## Build and test

Linux/macOS:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintRelease
python3 tools/validate_words.py
# Release signing inputs must be loaded into the current shell from private storage first.
./gradlew :app:assembleRelease
```

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

## Signing and upgrades

- The recorded 2.2.0 APK was a **signed Release**. Its key alias was `armadillo-release`, and its certificate SHA-256 fingerprint was `07:2A:8E:FB:08:75:88:FF:64:90:EB:E6:AF:75:C9:A8:9A:98:71:B5:F9:A2:F4:D5:1F:9D:6C:88:94:CC:EF:93`; the same certificate was used for releases 2.0.0 and 2.1.0.
- The encrypted keystore and password recovery information were historically kept in an owner-only private signing folder. The same keystore was reused and must not be included in source or public archives.
- A Release APK signed with the same certificate can upgrade earlier Release installations; a prior Debug installation cannot be upgraded in place with the Release APK.
- Only the 2.2.0 checks listed in `Dour-Test-Report.md` were performed for that release; no device or emulator installation or execution was performed in that environment.

## History

- Versions 1.0.0 and 1.1.0 were retained in their own release folders and were not rewritten.
- The recorded 1.2.0 build/test status is in `Dour-Test-Report.md`.
- Known limitation: 2.2.0 was not installed or run on a physical device or emulator in the recorded release environment; see `Dour-Test-Report.md` for its verification status.
