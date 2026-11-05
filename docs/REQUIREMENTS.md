# Project requirements

This document describes the current implementation and operational constraints. It is not a commitment to unimplemented features. Project-wide instructions are in [AGENTS.md](../AGENTS.md); architecture and internal interfaces are described in [ARCHITECTURE.md](ARCHITECTURE.md) and [API.md](API.md).

## Product scope

- Armadillo is a Persian, right-to-left, portrait-oriented offline word-guessing game for groups. It is free and has no ads or background music.
- The application has no internet permission. Audio, font, icon assets and playable vocabulary are packaged locally. The manifest requests vibration permission and sets `allowBackup` to `false`.
- The owner-approved `applicationId` is `com.armadillo.game`, with minimum Android API 23 and target/compile SDK 36 (Google Play has required API 36 for new uploads since 31 August 2026). This application ID has never been published to a store, so the first release `v1.0.0` uses `versionCode` 1 and `versionName` `1.0.0`; both must be raised for the next release.
- The app uses one static light theme. The appearance must not change with the system dark-theme setting, and no `values-night` resources are added.
- The changed application ID is a new Android app identity: it cannot update the old installation or automatically read its private data. No cross-application data migration is implemented.

## Game setup

- Support 4, 6, 8 or 10 players, grouped into opposite-seat teams of two. Seat zero starts at the bottom and turns advance counterclockwise.
- Store the player roster separately from an unfinished game; prefill names for later games. A blank player name is replaced with a generated name.
- Allow one or more categories from jobs, animals, foods, places and technology. Each category has 100 words at each of three difficulty levels, for 1,500 unique playable words.
- Allow a round duration of 120, 210 or 300 seconds and 3, 5 or 7 rounds. Category selection, difficulty and these settings are fixed when a game begins.
- Keep the older 723-word general bank as an archived asset and for validation; it is not a selectable or playable category. Migrate retired or unknown saved categories to supported categories, falling back to jobs if necessary.

## Gameplay and results

- A correct answer records the result and moves the turn to the next seat. Each new word starts a seven-second active-time lock on swapping; a swapped word is recorded as consumed, not correct.
- Count time using a monotonic clock, not UI refresh counts. When the countdown reaches zero, record expiration, add a ten-second penalty to the current team and show the explosion screen. Player acknowledgement advances to the next round or results.
- Permit pause and resume without resetting the player, word, history or remaining active time. Backgrounding, system interruptions and loss of focus suspend active time and audio; restored manual pauses require an explicit resume.
- Support ending early through the pause/dialog flow and report the accumulated results. Rank teams by final time (real time plus penalties), then fewer explosions, more correct words and fewer swaps; full ties share a rank.
- Record word, seat, round, display time and outcome in the game history. Display team results and the recorded word list.

## Data, audio and accessibility

- Keep game checkpoints, roster and sound settings on the device using private application preferences. Finished games clear the unfinished checkpoint; a corrupt or unsupported checkpoint is cleared without clearing the separate roster.
- Preserve the existing preference names and migration behavior for upgrades. Do not change the application ID or release signing key without explicit owner approval.
- Provide independent sound settings and mute; playback respects the app settings and device sound state. Explosion vibration is configurable.
- Use the bundled Mikhak typeface and preserve its [OFL license](../app/src/main/assets/fonts/Mikhak-OFL.txt). Keep the original icon artwork untouched; launcher assets are generated from it with adaptive-icon safe-zone margins, a monochrome layer and both normal and round resources.
- Team identity must never rely on colour alone: a team number accompanies every colour marker.
- Every interactive seat, control and toggle must be reachable with a screen reader, and text must follow the system font scale.

## Verification and release constraints

- Use the commands and signing precautions in [README.md](../README.md#build-and-test). Validate bundled words with `python3 tools/validate_words.py`.
- Run applicable unit tests, Debug and unsigned Release builds and lint for code or resource changes; validate real-device installation and flows when a device or emulator is available. The saved-game codec must keep unit-test coverage for schema, migration and invalid-value behaviour. Do not infer current results from a historical report.
- The owner must approve an official release; keep signing material outside the repository. A Debug APK signed by a different key cannot normally update a Release installation, and uninstalling can remove local app data.
