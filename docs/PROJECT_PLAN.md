# Project plan and verification backlog

This is a maintenance checklist based on the current repository, not an approved schedule or promise of new features. Track substantial work in GitHub Issues when appropriate and obtain owner approval before releases or sensitive changes, as specified in [AGENTS.md](../AGENTS.md).

## Current baseline

- One offline Android application module with bundled Persian word banks and private local persistence. See [REQUIREMENTS.md](REQUIREMENTS.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
- The repository is not designated as an official release. No user-facing app version name is currently configured, and the Android `versionCode` is 1 because this application ID has never been published. The application ID change creates a separate installation and does not transfer the old app's data.
- A manual GitHub Actions workflow builds a Debug APK and runs unit tests and lint. Its push trigger is restricted to workflow-file changes on `main`, so it is not general CI. See [README.md](../README.md#github-actions-debug-build).
- Historical reports and previous CI runs describe the specific builds they tested; they do not establish the results of subsequent changes.

## Verification to perform when available

1. Run the current Debug build, JVM unit tests, lint and vocabulary validator after relevant changes. Report actual results and any warnings.
2. On a physical Android device or emulator, test installation and startup, Persian/RTL rendering, setup, pause/resume, interruptions, sound and results. Device testing has not been established merely by the historical JVM tests.
3. For an approved release only, verify the owner's existing signing configuration, build the release, verify the artifact and signing certificate, and test an upgrade from a compatible installation. Never replace the established key without approval.
4. Review GitHub Actions deprecation warnings and the narrowly scoped push trigger before changing automation. Any workflow change should be tested separately; this item is a suggestion, not an implemented fix.

## Release readiness decisions

Before any first official repository release, the owner must approve the release and its version, and the team must verify the Android version code and signing compatibility. Use GitHub Releases and tags only after approval and verification. Preserve historical records and do not commit credentials, keys, generated caches or temporary artifacts.

## Documentation maintenance

Update the relevant requirements, architecture and internal interfaces when behavior or structure changes. Keep [CHANGELOG.md](../CHANGELOG.md) accurate. Avoid restating a past test result as a current result; preserve the historical test report as evidence of its own build only.
