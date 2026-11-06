# GitHub Actions: audit and operations

This is the source of truth for workflow responsibilities and branch policies. See [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for release operations and [SECRETS_SETUP.md](SECRETS_SETUP.md) for signing credentials. These workflows must be merged to the relevant default branch before GitHub can run scheduled cleanup or the intended release automation.

## Audit of the starting repository

- Android application `:app` and JVM library `:game-core`; Gradle wrapper 9.8.2 with a distribution SHA-256, AGP 9.5.0, Kotlin 2.4.21, JDK 17, Android compile SDK 37, target SDK 36, min SDK 23. Debug and Release are the only configured build types; there are no product flavors or custom APK output names. Debug is signed by the Android debug key; Release uses the existing environment-based signing configuration.
- Four `:game-core` JVM test files cover the engine, codec, progress and roster. `app/src/test/` holds pure app-layer JVM tests and `app/src/androidTest/` holds Compose instrumented tests; `app/build.gradle.kts` declares `AndroidJUnitRunner` and `ui-test-junit4`. Android lint is configured by AGP; no separate Detekt or ktlint configuration was found. `tools/validate_words.py` validates the bundled word banks.
- The old `debug-apk.yml` ran a debug build, word validation, tests and debug lint plus an unsigned Release build and lint, on PRs to `main`, selected pushes and manual runs. It retained the debug APK for seven days and failure logs for three. It is replaced by CI plus the shared validation build, not duplicated.
- The existing `release.yml` was manually dispatched with a tag; it checked the tag against the configured version, built a signed APK and AAB, verified the APK signature and uploaded to an existing release. Its `--clobber` could replace existing assets; it did not enforce the original signing certificate, increasing version codes or release approval via an environment. The workflow has been adapted rather than duplicated.
- Dependabot already proposes monthly Gradle and Actions updates; it remains in place. No existing composite or reusable workflow was found. The GitHub workflow list also showed an old `signing-probe.yml` entry; the obsolete `Signing probe` workflow (ID `408122212`) has now been disabled (`disabled_manually`) and its historical `workflow_dispatch`/push file is not present on `main`. An administrator should still review and rotate the four signing secrets referenced by those historical runs.
- No security scan, dependency-license report or scheduled artifact cleanup existed. The repository is public and user-owned: GitHub's Dependency Review action requires Advanced Security on an organization-owned private repository. The OSV/POM audit below does not depend on that entitlement. Existing dependency verification metadata is absent; generate and review it on a trusted machine as a separate improvement.
- At the original audit time the Actions artifact API returned zero retained artifacts; a later audit found 21 temporary artifacts (~252 MiB). Temporary Debug uploads are now restricted to pull requests and signed transfer artifacts are swept after one hour. A recent run had hit artifact storage quota, so GitHub billing/retention usage should still be checked by an administrator. The integration could not list Actions secrets or read branch protection (HTTP 403); their configuration was not verified. Attempts to enable branch protection or a ruleset also returned HTTP 403 (`Resource not accessible by integration`), so these settings require owner action with a token that has administration rights. `v1.0.0` is an existing official release with APK, AAB, metadata and checksums and must not be altered.

## Workflows

| File | Triggers / jobs | Permissions and output |
| --- | --- | --- |
| `ci.yml` | Push to `main`, PR into `main`, manual dispatch. Calls `build.yml` once; PRs and manual runs also start an API 35 emulator job for `:app:connectedDebugAndroidTest`. Feature-branch pushes are checked through their PR, not duplicated as push runs. | `contents: read`; checks include `validate / Build, test and lint` and `Compose UI tests on emulator`. |
| `build.yml` | Reusable (`workflow_call`) or manual. Validates words and workflow helpers, runs `:game-core:test`, `:app:testDebugUnitTest`, Debug and unsigned Release builds and both lints; verifies the debug APK identity. | `contents: read`; debug APK artifact `armadillo-ci-debug-apk`, uploaded on pull requests only and retained one day. Upload quota failure is a warning; it does not falsify build results. |
| `security.yml` | PRs into and pushes to `main`, or reusable call by Release. Resolves debug/release runtime and game-core test runtime dependencies, queries OSV, and reports POM license metadata. | `contents: read`; no signing secrets, cache, artifact or privileged PR event. Known OSV findings or scan errors fail the job. |
| `release.yml` | Manual owner dispatch from `main` with a new strict SemVer tag. Preflight → shared validation and security → signing → draft/tag creation and publishing. A tag push alone does not publish. | Read-only preflight/sign; `contents: write` only for publication. See the release guide. |
| `cleanup.yml` | `0 */6 * * *` (UTC) and manual dispatch. Deletes only known temporary Actions artifact names **older than 24 hours** (one hour for the signed transfer artifact). | `actions: write`, `contents: read`; never calls Releases, tags, source, or cache deletion endpoints. |

GitHub schedules are best effort and can run late or be skipped, especially on inactive repositories. One-day `retention-days` is the primary expiration mechanism for Debug uploads, with a one-hour sweep for the signed transfer artifact; the six-hour job is a supplementary sweep, not a six-hour artifact lifetime. The cleanup allowlist also covers the old debug APK and its two failure-log names. A stale approved-release transfer artifact is not a GitHub Release asset. All official release assets are outside the Actions artifact API and are preserved indefinitely.

The security audit covers selected resolved Maven runtime and test configurations, including transitive modules. OSV findings are limited to that database and Maven coordinates; build plugins, Gradle distribution, unselected configurations, native dependencies, bundled assets and unknown advisories are not comprehensively scanned. POM license names/URLs, including simple inherited parent POMs, are reported for manual review: missing metadata is marked `UNKNOWN`, and no legal approval or license-policy gate is implied. OSV, Google Maven and Maven Central must be reachable from the runner; errors fail rather than produce a false green result. Dependabot is still useful for automated update proposals and GitHub alerts when enabled.

Pinned Actions: `actions/checkout` v7, `actions/setup-java` v6, `actions/upload-artifact`/`download-artifact` v7 and `reactivecircus/android-emulator-runner` v2 (commit `a421e43855164a8197daf9d8d40fe71c6996bb0d`) at full commit SHAs. The wrapper uses its existing distribution checksum. There is no shared Actions cache or hidden artifact upload; GitHub-hosted runners are ephemeral. Actions and dependency pins require periodic review.

## Branches and required checks

`main` remains the stable branch and is the only CI branch; the `develop` triggers were removed because that branch does not exist. Use `feature/*`, `fix/*` and `hotfix/*` branches for work. Never automatically merge PRs. The current Arena session branch is an exception to the naming convention and is not renamed.

An administrator must configure repository rulesets or branch protection for `main`:

1. Require pull requests and at least one owner/maintainer approval; restrict bypass and direct pushes to trusted administrators, and do not enable automatic merging.
2. Require status checks to pass and require branches to be up to date. After the new workflows have run at least once, select the exact contexts shown by GitHub for **CI / validate / Build, test and lint** and **Security / Dependency and license audit** (GitHub's displayed context can vary for reusable jobs). Do not mark the artifact upload warning as a successful APK download.
3. Require conversation resolution as appropriate; prevent force-push and deletion. Treat changes to workflows, Gradle wrapper, build files, scripts and the signing environment as security-sensitive review items.
4. The owner requested that signed releases not require a protected environment or tag ruleset. Releases require an explicit owner request and manual dispatch from `main`; the preflight checks the owner and original/rerun actors. A user with workflow write access can modify or create a different workflow and potentially access repository secrets, so restrict write access and review changes. Tag checks cannot eliminate races without server-side tag protection. See [SECRETS_SETUP.md](SECRETS_SETUP.md).

The available integration token cannot configure branch settings: `PUT repos/.../branches/main/protection` and `POST repos/.../rulesets` both returned HTTP 403. An owner must apply the rules in **Settings → Branches / Rules**, or use a token with administration rights:

```bash
gh api --method PUT repos/behrouzxp-dev/Armadillo/branches/main/protection --input - <<'JSON'
{"required_status_checks":{"strict":true,"contexts":["validate / Build, test and lint","Dependency and license audit"]},"enforce_admins":true,"required_pull_request_reviews":{"required_approving_review_count":1},"restrictions":null,"allow_force_pushes":false,"allow_deletions":false}
JSON
```

Confirm the exact displayed status-check contexts after the workflows have run once, and add the emulator check when it exists. Do not assume branch protection is active.

PRs from forks use `pull_request`, not `pull_request_target`, and receive no signing secrets or write token. The manual release workflow must be merged to `main` before it can be dispatched.
