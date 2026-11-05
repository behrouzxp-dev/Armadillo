# Owner-approved signed releases

No release, tag or signing key is created by this documentation. The existing `v1.0.0` release and original keystore must be preserved. A new signed APK is **not** produced until all prerequisites, owner approval and the tag-triggered workflow succeed.

## Prerequisites

- Configure the `production` environment, required owner reviewer, protected `v*` tag rules and environment secrets in [SECRETS_SETUP.md](SECRETS_SETUP.md) and [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md). The environment must have exactly the repository owner as its required reviewer. If GitHub's plan does not support required reviewers for this private repository, automatic publishing remains blocked; do not remove the guard. Use a separately approved secure manual release process or change the plan/settings first.
- Merge the reviewed workflow changes to protected `main`. Required CI and security checks must pass. Test the important flows and an upgrade on an Android device; CI is not an installation test.
- Retrieve the **original** signing key from secure storage. Its APK and AAB certificate must be SHA-256 `4F:40:5D:B1:95:4C:17:40:56:65:36:B8:56:8C:4B:AE:56:D5:A1:E6:18:4C:21:10:CD:32:39:AF:52:98:26:B8`. Do not generate or substitute a key.

## Prepare a version

1. In a reviewed change on `main`, set the literal `versionName` and `versionCode` in `app/build.gradle.kts`. Keep Semantic Versioning and increase the integer code for every release; current official `v1.0.0` has code `1`. Do not change the application ID. Update the changelog.
2. The release tag must be exactly `vMAJOR.MINOR.PATCH`, with no leading zeroes, suffix or build metadata, and must equal `v` + `versionName`. The workflow checks both tag and code against **all** existing GitHub releases (including drafts), and downloads their `output-metadata.json` to compare source configuration and actual APK metadata. Missing tags/metadata, duplicate tags/releases, lower versions, mismatches or inaccessible history stop the process. Check any external stores/distribution channels manually: GitHub cannot prove their version codes are lower.
3. Only after approval of the version and code, create and push a protected tag pointing to the reviewed `main` commit, for example:

   ```bash
   git tag v1.0.1 <approved-main-commit>
   git push origin v1.0.1
   ```

   This example is not a recommendation to release `1.0.1`; never reuse an existing tag. Repository/tag rules must restrict who can create or move tags.
4. The tag triggers **Signed release**. Preflight verifies tag protection, that its commit is on `main`, that `production` requires only the owner as reviewer, version uniqueness and history. It then runs the shared builds, tests, lint and dependency audit without signing secrets. Only afterward does GitHub request the owner's environment approval; approving is an explicit decision for that specific run.
5. The approved job creates the keystore in runner temporary storage, builds the signed APK and AAB, removes the keystore, verifies both signatures against the original certificate and checks the APK application ID, version name and code. The signed transfer artifact is retained for one day. The publisher verifies SHA-256 checksums, creates a **draft**, uploads APK, AAB, metadata and `SHA256SUMS`, checks asset names and only then publishes it. An upload/check failure can leave an incomplete draft; it cannot publish it.

Existing releases, drafts and assets are never overwritten or deleted automatically. If a run fails after draft creation, the owner must inspect the draft and explicitly decide how to recover; another run refuses the duplicate. Do not delete an official release to retry. If the transfer artifact has expired, rerun the approved process only after resolving the duplicate/draft safely. Concurrent releases are serialized; GitHub can replace an older pending run in the concurrency queue, so inspect the Actions page after pushing a tag.

## Download and verify

Open **Releases → the desired version**, download `app-release.apk` and `SHA256SUMS`, and verify its hash against the APK line in `SHA256SUMS`. The checksum file uses the original build directory paths; for a downloaded APK compare the output of `sha256sum app-release.apk` to the corresponding recorded hash. For Android installation, use a compatible device and preserve existing local data; a debug-signed APK normally cannot update a release-signed installation. Release outputs also include the AAB and Gradle output metadata.

## Troubleshooting

- **Preflight fails:** verify tag rules, owner-only reviewer, tag on protected `main`, exact version, all existing release tags and metadata. The GitHub token must be able to read environments and releases. Never bypass the guard to force a publish.
- **Signing fails:** verify all four environment secrets, original keystore and alias, and the recorded certificate fingerprint without printing passwords. See the secrets guide.
- **OSV/POM or dependency audit fails:** check network access and dependency resolution, inspect findings, and fix or review licenses; a network error is not a clean scan.
- **Artifact quota exceeded:** review and delete only expendable Actions artifacts via GitHub after checking their contents; official release assets are separate. Cleanup may be delayed and GitHub billing calculations can lag.
- **Draft left behind:** inspect the run and draft; owner approval is required before modifying or deleting anything important. No automated overwrite or deletion is enabled.
