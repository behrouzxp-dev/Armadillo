# Signing secrets

The established `com.armadillo.game` signing key must remain outside Git. Keep an encrypted backup of the **original** keystore and its recovery information in owner-controlled secure storage. Never paste credentials, keystore data or tokens into issues, logs, commands recorded in shell history, or chat. Do not create a replacement key without explicit owner authorization.

## GitHub configuration

In **Settings → Secrets and variables → Actions → Repository secrets**, configure these exact secret names using the original signing credentials:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 encoding of the original keystore bytes, with no line wrapping. |
| `KEYSTORE_PASSWORD` | Original keystore password. |
| `KEY_ALIAS` | Alias of the original signing key. |
| `KEY_PASSWORD` | Original key password. |

For example, on a secure local machine generate the base64 value with `base64 -w 0 <path-to-original-keystore>` and transfer it privately through the GitHub UI; never commit the output. macOS `base64` options differ. Avoid saving it in shell history or unencrypted files. Secret values are not readable back from GitHub; never remove the only recoverable copy.

Only the signing step of the current release workflow explicitly receives these four values. The keystore is materialized under `RUNNER_TEMP` with restrictive permissions and removed with a shell trap. CI, security scans and PRs do not explicitly receive signing secrets. The manual release preflight requires the owner to start or rerun the workflow from `main`; it does not require a protected environment or protected tags.

**Security tradeoff:** repository-level secrets are not isolated by an environment approval. Anyone able to introduce or run a workflow with access to those secrets may be able to expose them, regardless of this workflow's owner check. Restrict repository write and workflow permissions, review workflow changes and preserve the original key; an unprotected tag may also be moved by authorized writers. The workflow detects some tag changes, but these checks do not replace server-side protection.

The GitHub-provided `GITHUB_TOKEN` is scoped per job; no personal access token is required. Publication uses `contents: write`; cleanup uses `actions: write`; other jobs use read-only contents. The signed APK and AAB certificate fingerprints are public and compared to the existing released certificate; passwords and keystore contents are never printed. See [RELEASE_GUIDE.md](RELEASE_GUIDE.md) for the per-release approval and verification process.
