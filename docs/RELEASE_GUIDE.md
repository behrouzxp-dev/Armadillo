# Owner-approved signed releases

The existing `v1.0.0` release and original keystore must be preserved. No release is made by merging the workflow or pushing a tag. A new signed APK is **not** produced until an owner explicitly requests a release and the manually dispatched workflow succeeds.

## Prerequisites

- Store the four original signing credentials in GitHub Actions repository secrets as described in [SECRETS_SETUP.md](SECRETS_SETUP.md). This workflow does not require a `production` environment or tag protection. Repository secrets are accessible to workflows as permitted by GitHub; restrict write access and review workflow changes carefully.
- Merge the reviewed workflow and version changes to `main`. Required CI and security checks must pass. Test important flows and an upgrade on an Android device when possible; CI is not an installation test.
- Keep the **original** signing key in secure storage. Its APK and AAB certificate must be SHA-256 `4F:40:5D:B1:95:4C:17:40:56:65:36:B8:56:8C:4B:AE:56:D5:A1:E6:18:4C:21:10:CD:32:39:AF:52:98:26:B8`. Do not generate or substitute a key.

## Prepare and publish a version

1. In a reviewed change on `main`, set the literal `versionName` and `versionCode` in `app/build.gradle.kts`. Use Semantic Versioning and increase the integer code for every release; the official `v1.0.0` uses code `1`. Do not change the application ID. Update the changelog.
2. The requested tag must be exactly `vMAJOR.MINOR.PATCH`, with no leading zeroes, suffix or build metadata, and must equal `v` + `versionName`. The preflight rejects existing tags and releases, and checks both tag and code against **all** existing GitHub releases (including drafts). It downloads their `output-metadata.json` to compare source configuration and actual APK metadata. Missing historical tags/metadata, mismatches or inaccessible history stop the process. Check external stores/distribution channels manually: GitHub cannot prove their version codes are lower.
3. Only when the owner explicitly approves this specific release, the owner or their authorized agent manually starts **Signed release** on `main` with the tag as its `tag` input, for example `gh workflow run release.yml --ref main -f tag=v1.1.0`. GitHub must identify both the original actor and rerun actor as the repository owner; dispatching from another branch or as another user is rejected. A push of a tag does not start a release.
4. Preflight checks the owner, the checked-out current `main` commit, version and history. The shared build, tests, lint and dependency audit run before signing. No separate environment approval is requested: the owner's explicit manual dispatch is the approval. This guard is in the workflow itself and does not protect repository secrets from an untrusted user who can modify or run other workflows.
5. The signing job creates the keystore in runner temporary storage, builds the signed APK and AAB, removes the keystore, verifies both signatures against the original certificate and checks APK application ID and version. A one-day signed transfer artifact passes the files to the publisher. The publisher checks SHA-256 hashes, creates a new tag targeting the approved commit, creates a draft release, verifies the tag and asset names, and only then publishes. It checks the tag again before publication; without server-side tag protection the checks cannot eliminate all concurrent tag-mutation races.

Existing releases, drafts and tags are never intentionally overwritten or deleted. A failure after draft creation can leave a draft and/or tag; inspect and recover only with owner authorization. The preflight rejects an existing tag even if it has no release, so retries require explicit recovery; do not delete an official release or tag to retry. If the transfer artifact has expired, rerun only after resolving any draft/tag safely. Concurrent releases are serialized; GitHub can replace an older pending run in the concurrency queue, so inspect the Actions page after dispatch.

## Download and verify

Open **Releases → the desired version**, download `app-release.apk` and `SHA256SUMS`, and compare the output of `sha256sum app-release.apk` to the APK line in `SHA256SUMS`. The checksum file records the original build directory paths. For Android installation, use a compatible device and preserve existing local data; a debug-signed APK normally cannot update a release-signed installation. Release outputs also include the AAB and Gradle output metadata.

## Troubleshooting

- **Preflight fails:** verify that the owner manually dispatched `main`, the requested tag is new and matches the source version, and historical tags and metadata are accessible. If `main` moved before preflight, review the new commit before dispatching again.
- **Signing fails:** verify the four repository secrets, original keystore and alias, and the recorded certificate fingerprint without printing passwords. See the secrets guide.
- **OSV/POM or dependency audit fails:** check network access and dependency resolution, inspect findings, and fix or review licenses; a network error is not a clean scan.
- **Artifact quota exceeded:** review and delete only expendable Actions artifacts after checking their contents; official release assets are separate. Cleanup may be delayed and GitHub billing calculations can lag.
- **Draft or tag left behind:** inspect the run, draft and tag; owner approval is required before modifying or deleting anything important. No automated overwrite or deletion is enabled.
