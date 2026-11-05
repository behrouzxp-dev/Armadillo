# Signing secrets and protected environment

The established `com.armadillo.game` signing key must remain outside Git. Keep an encrypted backup of the **original** keystore and its recovery information in owner-controlled secure storage. Never paste credentials, keystore data or tokens into issues, logs, commands recorded in shell history, or chat. Do not create a replacement key without explicit owner authorization.

## GitHub configuration

1. An administrator opens **Settings → Environments → New environment**, names it `production`, adds the repository owner as the **only required reviewer**, disables administrator bypass of protection rules, and saves. If the owner both creates tags and approves releases, do not enable the option that prevents the triggering user from reviewing; alternatively use a separate trusted tag creator. The release preflight checks this setting through the GitHub API before a job can access the environment. Verify your private-repository plan supports required reviewers; do not rely on an automatically created unprotected environment.
2. In **production → Environment secrets**, add these exact secret names:

   | Secret | Value |
   | --- | --- |
   | `KEYSTORE_BASE64` | Base64 encoding of the original keystore bytes, with no line wrapping. |
   | `KEYSTORE_PASSWORD` | Original keystore password. |
   | `KEY_ALIAS` | Alias of the original signing key. |
   | `KEY_PASSWORD` | Original key password. |

   For example, on a secure local machine generate the base64 value from the owner-held file with `base64 -w 0 <path-to-original-keystore>` and transfer it privately through the GitHub UI; never commit the output. macOS `base64` options differ. Avoid saving it in shell history or unencrypted files.
3. Restrict creation, update and deletion of `v*` tags to trusted owner-controlled actors. Configure protected branches and required checks as described in [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md).
4. If older repository-level signing secrets already exist, safely copy the original values into the environment first and verify the protected setup with the owner. Then an administrator should remove the repository-level copies so an untrusted or historical workflow cannot access them. Do not delete the only recoverable copy of any credential. Secret values are not readable back from GitHub; no secret contents were inspected by this audit.

Only the signing step receives these four values. The keystore is materialized under `RUNNER_TEMP` with restrictive permissions and removed with a shell trap. CI, security scans and PRs do not receive signing secrets. A malicious tag workflow could otherwise request repository secrets: environment scoping, owner-only approval, protected tags, restricted bypass and review of workflow changes are all necessary together.

The GitHub-provided `GITHUB_TOKEN` is scoped per job; no personal access token is required. Publication uses `contents: write`; cleanup uses `actions: write`; other jobs use read-only contents. The signed APK and AAB certificate fingerprints are public and compared to the existing released certificate; passwords and keystore contents are never printed. See [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for the per-release approval and verification process.
