# Armadillo 2.2.0 — Change log

This is a historical release record. For current project instructions and version history, see [AGENTS.md](AGENTS.md) and [CHANGELOG.md](CHANGELOG.md). The verification results below refer to the original 2026-10-29 build, not later checkouts.

- **Base:** latest verified Drive release source `v2.1.0`; `applicationId=com.dour.game` retained; `versionCode=6`, `versionName=2.2.0`.
- **Colors:** centralized page-canvas token is exactly `#F2C50A`; centralized fill for every app and dialog button is exactly `#07223C`. Button labels/icons use readable white and disabled-gray states; ripple and selection outlines remain coherent.
- **Active game page:** removed the “پایان” button from the live game screen. The early-end action remains separately available from the pause overlay/dialog, and automatic round completion is unchanged.
- **Pause/resume:** control remains usable across preparation, active play, manual pause, system pause and the explosion interlude. Repeated pause/resume and system interruption preserve current player, word, history and remaining time; a restored manual pause waits for an explicit continue action.
- **Preserved:** Lalezar, team identity colors, original icon artwork, `com.dour.game`, offline/free/ad-free behavior, Persian RTL and portrait orientation, game rules/timing, durable local saves, sound settings and stopping time/audio in the background.
- **Verification:** 21 JVM unit tests passed; vocabulary validation passed; Debug and signed Release builds passed; lint reported 0 errors and 29 warnings; release signature, APK metadata and alignment checks passed. No physical-device or emulator interaction test was performed. See `Dour-Test-Report.md` for exact results and limitations.
