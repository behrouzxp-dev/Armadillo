# Project requirements

This document describes the current implementation and operational constraints. It is not a commitment to unimplemented features. Project-wide instructions are in [AGENTS.md](../AGENTS.md); architecture and internal interfaces are described in [ARCHITECTURE.md](ARCHITECTURE.md) and [API.md](API.md).

## Product scope

- Armadillo is a Persian, right-to-left, portrait-oriented offline word-guessing game for groups. It is free and has no ads or background music.
- The application has no internet permission. Audio, font, icon assets and playable vocabulary are packaged locally. The manifest requests vibration permission and sets `allowBackup` to `false`.
- The owner-approved `applicationId` is `com.armadillo.game`, with minimum Android API 23, target SDK 36 and compile SDK 37 (Google Play has required API 36 for new uploads since 31 August 2026). The first GitHub release `v1.0.0` uses `versionCode` 1 and `versionName` `1.0.0`. GitHub release `v1.1.0` uses code 2; the source is prepared for `1.2.0` with code 3. Verify external distribution version codes before distributing there.
- Use a right-to-left layout throughout the Persian UI regardless of the device language, while preserving explicitly positioned game controls and home corner buttons.
- The app uses one static light theme. The appearance must not change with the system dark-theme setting, and no `values-night` resources are added.
- The shared page-header back arrow points left without moving the control from its existing position.
- The changed application ID is a new Android app identity: it cannot update the old installation or automatically read its private data. No cross-application data migration is implemented.

## Home screen

- Show the transparent armadillo artwork and game name without a surrounding card. Under the subtitle, show the version when available and the offline/free/ad-free tagline. Do not show a separate About section on the home screen.
- Place the supplied play artwork on the central start control, with its embedded checkerboard background removed, and show the saved-game continuation action only when a saved game is available. Preserve the start action, sound effect and accessibility label.
- Provide accessible sound settings and rules icon controls in the physical lower-left and lower-right corners respectively. Use navy circular backgrounds with white gear and question-mark icons. Keep the main content scrollable on smaller screens and at larger font scales.

## Game setup

- Display setup choices with transparent unselected backgrounds and navy outlines, and navy selected backgrounds with white text; preserve selection semantics for accessibility. Non-selection actions keep their existing appearance.
- Support 4, 6, 8 or 10 players, grouped into opposite-seat teams of two. Seat zero starts at the bottom and turns advance counterclockwise. The setup circle displays a top-down table and numbered team-coloured armchairs; tapping a chair edits its player name, and an accessible list of named seats remains available.
- Use blue, red, green, purple and dark navy for teams one through five, in that order.
- Store the player roster separately from an unfinished game; prefill names for later games. A blank player name is replaced with a generated name.
- Allow one or more categories from jobs, animals, foods, places and technology. Each category has 100 words at each of three difficulty levels, for 1,500 unique playable words.
- Allow a round duration of 120, 210 or 300 seconds and 3, 5 or 7 rounds. Category selection, difficulty and these settings are fixed when a game begins.
- Keep the older 723-word general bank as an archived asset and for validation; it is not a selectable or playable category. Migrate retired or unknown saved categories to supported categories, falling back to jobs if necessary.

## Gameplay and results

- Start a prepared round by tapping the preparation screen; do not display start or name-edit buttons there. During active play, tapping the screen outside the pause and swap controls records a correct answer and moves the turn to the next seat. Show the word unboxed in the main play area, without an on-screen tap hint. The swap and icon-only pause controls share the bottom row in a 4:1 ratio. Each new word starts a visible five-second active-time lock on swapping; a swapped word is recorded as consumed, not correct.
- Show round indicators for completed (red), active (green) and upcoming (empty) rounds, with accessible state descriptions. Show the current player and team at the top right, with a subtle team-coloured glow. Display a labelled time bar for each team, scaled to `totalRounds × (roundDuration + one explosion penalty)`, including elapsed time and penalties.
- Count time using a monotonic clock, not UI refresh counts. When the countdown reaches zero, record expiration, add a ten-second penalty to the current team and show the explosion screen. Player acknowledgement advances to the next round or results.
- Offer the manual pause control only during active play; its panel provides resume, early end and an independently scrollable chronological word history. Do not show a manual pause control in preparation or on the explosion screen. Backgrounding, system interruptions and loss of focus still suspend active time and audio, and restored manual pauses require an explicit resume.
- Support ending early through the pause/dialog flow and report the accumulated results. Rank teams by final time (real time plus penalties), then fewer explosions, more correct words and fewer swaps; full ties share a rank.
- Record word, seat, round, display time and outcome in the game history. Display the word list in the pause panel, not on the results screen; after the game ends the list is no longer accessible there. Display team results and rankings on the results screen.

## Data, audio and accessibility

- Keep game checkpoints, roster and sound settings on the device using private application preferences. Finished games clear the unfinished checkpoint; a corrupt or unsupported checkpoint is cleared without clearing the separate roster.
- Preserve the existing preference names and migration behavior for upgrades. Do not change the application ID or release signing key without explicit owner approval.
- Provide independent sound settings and mute; playback respects the app settings and device sound state. Explosion vibration is configurable.
- Use the bundled Mikhak typeface and preserve its [OFL license](../app/src/main/assets/fonts/Mikhak-OFL.txt). Keep the original icon artwork untouched; launcher assets are generated from it with adaptive-icon safe-zone margins, a monochrome layer and both normal and round resources.
- Team identity must never rely on colour alone: a team number accompanies every colour marker.
- Every interactive seat, control and toggle must be reachable with a screen reader, and text must follow the system font scale.
- The explosion acknowledgement must be an explicit button-like accessibility action with a labelled click, not only a raw tap detector; toggles must expose one switch node rather than two nested clickable nodes.
- On Android 16 (API 36), a portrait request is ignored on displays of at least 600dp smallest width; large-screen layouts must be verified on a real device or emulator before release. The temporary `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out is deliberately not used: it does not restore portrait lock and is removed when targeting API 37.

## Verification and release constraints

- Use the commands and signing precautions in [README.md](../README.md#build-and-test). Validate bundled words with `python3 tools/validate_words.py`.
- Run applicable JVM and instrumented tests, Debug and unsigned Release builds and lint for code or resource changes; validate real-device installation and flows when a device or emulator is available. The saved-game codec must keep unit-test coverage for schema, migration and invalid-value behaviour. Do not infer current results from a historical report.
- The owner must approve an official release; keep signing material outside the repository. A Debug APK signed by a different key cannot normally update a Release installation, and uninstalling can remove local app data.
