# Security policy

Armadillo is an offline Android game with no network permission, no backend and no user accounts. Saved games, the player roster and sound settings stay in private application storage on the device.

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub's **Security → Report a vulnerability** on this repository, or open a private security advisory. Do **not** include secrets, keystores, passwords or exploit details in a public issue, pull request, discussion or release note. Include the affected commit/tag, reproduction steps and impact. Reports are triaged by the repository owner.

## Scope

In scope: the Android app, bundled assets, Gradle build files, GitHub Actions workflows and release/signing automation. Out of scope: third-party services, the historical `com.dour.game` builds and issues that require a modified device or a malicious app already installed with root access.

## Signing and release automation

- Release signing uses four repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); private keys and passwords must never be committed. See [docs/SECRETS_SETUP.md](docs/SECRETS_SETUP.md).
- Signed releases are manually dispatched from `main` by the repository owner. The preflight checks the actor, version, history and certificate; see [docs/RELEASE_GUIDE.md](docs/RELEASE_GUIDE.md).
- The obsolete `Signing probe` workflow has been disabled. Secret values and Actions permissions could not be enumerated with the available integration token (HTTP 403); an owner should review and rotate signing secrets and configure branch protection/rulesets.
- Pull requests from forks receive a read-only token and no signing secrets. Workflow, Gradle, wrapper and script changes are security-sensitive and require review.

## Dependencies

`security.yml` resolves dependencies and queries OSV, reporting POM licence metadata for review. Dependabot proposes monthly Gradle and Actions updates. Dependency verification metadata (`gradle/verification-metadata.xml`) is not yet committed; generate it on a trusted machine before relying on it.
