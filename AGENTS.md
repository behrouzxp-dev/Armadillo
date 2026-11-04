# AGENTS.md

Permanent Project Development Instructions

This file defines the permanent development, security, documentation, GitHub, testing, release, and workspace management rules for this project.

All agents working on this repository must read and follow these instructions before performing any task.

1. Core Principles

- Treat this file as the permanent project-wide instruction source.
- Read and understand the existing project structure before making changes.
- Preserve existing functionality unless a change is explicitly requested.
- Avoid unnecessary complexity, dependencies, and architectural changes.
- Never make assumptions about important requirements.
- Ask the project owner when an important decision is unclear.
- Prefer reliable, maintainable, testable, and well-documented solutions.
- Never claim that an operation succeeded without verification.

2. Real-Time Progress Reporting

All user-visible progress reports must be written in Persian.

Requirements:

- Use right-to-left (RTL) formatting.
- Provide concise reports at meaningful development milestones.
- Report completed operations, current activities, discovered problems, and decisions requiring approval.
- Clearly distinguish completed work from ongoing work.
- Never fabricate progress, test results, build results, or deployment status.
- Do not expose passwords, private keys, tokens, or other sensitive information in progress reports.

The final report must also be written in Persian and formatted RTL.

3. Documentation Language Policy

All source-controlled technical documentation must be written in English.

This includes:

- `README.md`
- `AGENTS.md`
- `ARCHITECTURE.md`
- `REQUIREMENTS.md`
- `CHANGELOG.md`
- `RELEASES.md`
- `API.md`
- Technical reports
- Code comments
- Commit messages
- Branch names
- GitHub Issues
- Pull Requests
- Release notes

Application interface language is independent of documentation language and must follow the project requirements.

All user-facing progress and final reports must remain in Persian.

4. Project Structure and Documentation

Maintain a clean, logical, and maintainable repository structure.

Recommended documentation structure:

```text
project/
├── AGENTS.md
├── README.md
├── CHANGELOG.md
├── RELEASES.md
├── docs/
│   ├── REQUIREMENTS.md
│   ├── ARCHITECTURE.md
│   ├── PROJECT_PLAN.md
│   └── API.md
├── src/
└── ...

```

Adapt the structure to the selected framework and project requirements.

Rules:

- Do not create unnecessary documentation files.
- Keep documentation synchronized with actual implementation.
- Update relevant documentation whenever architecture, requirements, or behavior changes.
- Never document unimplemented features as completed.
- Do not duplicate the same information across multiple documents without a clear reason.

5. GitHub as the Primary Source of Truth

GitHub is the primary and authoritative platform for:

- Source code
- Project documentation
- Version history
- Branches and commits
- Issues and Pull Requests
- Tags
- Releases
- Build artifacts

Do not use Google Drive or other cloud storage services for project source code, documentation, development files, or release management.

The repository must remain the authoritative source of project history.

Rules:

- Keep the repository organized.
- Never upload unnecessary build caches or temporary files.
- Never commit credentials or signing keys.
- Keep release artifacts separate from the source-code history whenever possible.
- Verify repository changes after pushing.

6. Git and Branch Management

Use the following branch strategy:

- `main`: stable and approved code.
- `feature/*`: new functionality.
- `fix/*`: bug fixes.
- `refactor/*`: internal code improvements.
- `docs/*`: documentation-only changes.

Rules:

- Keep commits small and logically separated.
- Use meaningful English commit messages.
- Follow Conventional Commits where practical.

Examples:

```text
feat: add user profile screen
fix: resolve application startup crash
refactor: simplify database repository
docs: update installation instructions

```

- Do not commit unfinished or untested work to `main`.
- Do not rewrite shared Git history without explicit approval.
- Do not force-push to protected or shared branches.
- Do not delete important branches without approval.

For small projects, temporary feature branches are sufficient. Do not introduce unnecessary branch complexity.

7. Pull Request and Merge Policy

Use Pull Requests for significant changes.

Every Pull Request should contain:

- A clear English title.
- A description of the changes.
- Relevant Issue references when applicable.
- Testing information.
- Potential risks or breaking changes.

Rules:

- Review changes before merging.
- Resolve important conflicts carefully.
- Never silently discard existing functionality.
- Obtain project owner approval before merging significant architectural or behavior-changing changes into `main`.
- Do not merge unverified changes.

8. Issue and Task Management

Use GitHub Issues to track substantial tasks and bugs when appropriate.

Issue descriptions should include:

- Problem or objective
- Expected behavior
- Actual behavior when reporting a bug
- Steps to reproduce when applicable
- Acceptance criteria

Avoid creating unnecessary Issues for trivial internal operations.

Keep task status accurate.

9. Version Management

Use Semantic Versioning:

```text
MAJOR.MINOR.PATCH

```

Examples:

- `1.0.0`: first stable release.
- `1.1.0`: backward-compatible feature addition.
- `1.1.1`: backward-compatible bug fix.
- `2.0.0`: incompatible changes.

Rules:

- Keep version information consistent across relevant project files.
- Maintain `CHANGELOG.md`.
- Use Git tags for releases.
- Never reuse an existing release tag for a different version.
- Never delete historical releases without explicit approval.

10. Build and Automated Testing

Before considering a task complete:

- Run relevant automated tests.
- Check for compilation errors.
- Run static analysis or lint checks when available.
- Verify important application flows.
- Check for regressions.
- Confirm the final build configuration.

For Android projects:

- Verify Debug builds.
- Verify Release builds when release configuration is available.
- Check application ID and version information.
- Verify signing configuration without exposing secrets.
- Test installation and startup where possible.

Never report a successful build unless the build actually completed successfully.

If a test cannot be performed, clearly explain why.

11. GitHub Actions and Release Automation

Use GitHub Actions when automation provides meaningful value.

Possible workflows include:

- Automated tests.
- Static analysis.
- Debug builds.
- Release builds.
- Artifact uploads.
- Release preparation.

Rules:

- Use GitHub Secrets for required credentials.
- Never hardcode sensitive values in workflow files.
- Apply least-privilege permissions.
- Avoid unnecessary workflow executions.
- Keep workflow files understandable and maintainable.
- Verify workflow results before reporting success.

Do not publish a stable release automatically without project owner approval.

12. GitHub Releases and Build Artifacts

Use GitHub Releases for approved stable application releases.

For Android projects, release artifacts may include:

- APK
- AAB
- Relevant checksums
- Release notes

Rules:

- Use version tags such as `v1.0.0`.
- Include accurate release notes.
- Upload only verified build artifacts.
- Preserve historical releases.
- Never upload signing keys, passwords, or private credentials.
- Do not publish test builds as stable releases unless explicitly requested.

13. Security and Sensitive Information

Security is mandatory throughout the project lifecycle.

Never commit:

- Keystore files
- Private keys
- Passwords
- API secrets
- Access tokens
- Personal credentials
- Sensitive configuration files

Use GitHub Secrets for credentials required by GitHub Actions.

Android signing requirements:

- Keep release signing keys outside the repository.
- Maintain a secure, encrypted backup of the original keystore.
- Never rely on a temporary agent workspace as the only location of a signing key.
- Preserve the original keystore and its required credentials.
- Do not generate a replacement signing key for an existing published application without explicit authorization.
- Do not expose signing passwords in logs or documentation.
- Verify signing configuration before producing a stable release.

If sensitive information is accidentally committed:

- Stop further exposure.
- Notify the project owner.
- Follow an appropriate credential rotation and repository remediation process.
- Do not assume deleting the file from the latest commit removes it from Git history.

14. Code Quality and Application Stability

All implementation work must prioritize:

- Readability
- Maintainability
- Reliability
- Performance
- Security
- Accessibility where applicable
- Consistent architecture

Rules:

- Follow the conventions of the selected language and framework.
- Avoid unnecessary code duplication.
- Use clear names for classes, methods, and variables.
- Handle errors explicitly.
- Validate user inputs.
- Avoid introducing breaking changes without authorization.
- Remove obsolete code only after verifying it is no longer required.
- Do not introduce dependencies without a clear technical reason.

15. Change and Update Management

When implementing a requested update:

- Work from the latest approved project state.
- Inspect the current implementation before modifying it.
- Do not rebuild the project from an outdated version.
- Identify the affected components and dependencies.
- Preserve unrelated functionality.
- Update tests and documentation where necessary.
- Verify the changes before committing.

For bug fixes:

- Identify the root cause.
- Avoid superficial workarounds when a proper fix is possible.
- Add regression tests when appropriate.
- Verify that the issue is actually resolved.

16. Data Preservation and Workspace Cleanup

Workspace cleanup is required at the end of each completed task.

Before deleting any file:

1. Determine whether the file may be required in future development.
2. Check whether it exists in GitHub or another authorized project source.
3. Determine whether it can be reliably regenerated or downloaded.
4. Preserve important files that cannot be recovered.
5. Never delete the only copy of essential project data.

Important files include:

- Source code
- Project documentation
- Configuration files
- Database migration files
- Signing keys
- Required certificates
- Uncommitted work
- Essential test resources

After verification:

- Remove unnecessary temporary files.
- Remove safe-to-delete build caches.
- Remove redundant generated artifacts.
- Keep the workspace organized and ready for the next task.

Never delete essential files merely to reduce workspace size.

17. Actions Requiring Project Owner Approval

Obtain explicit approval before:

- Publishing a stable release.
- Deleting a repository.
- Rewriting shared Git history.
- Replacing an existing signing key.
- Changing the application ID of an established application.
- Performing destructive database migrations.
- Removing important application functionality.
- Introducing major architectural changes.
- Deleting important project data.
- Making irreversible changes to production services.

Do not interpret silence as approval.

18. Final Delivery Checklist

Before completing a task, verify:

- Requested functionality is implemented.
- Existing functionality has not been unintentionally broken.
- Relevant tests have been executed.
- Build results have been verified when applicable.
- Documentation has been updated.
- Git changes are organized.
- No secrets or sensitive files were committed.
- Important files have been preserved.
- Temporary files have been safely cleaned up.
- The final report accurately describes the work.

The final report must be written in Persian and include:

1. Completed work.
2. Modified or created files.
3. GitHub changes.
4. Tests performed and their actual results.
5. Build status.
6. Known issues and limitations.
7. Remaining tasks.
8. Any required project owner decisions.

19. Project-Specific Instructions

Project-specific requirements may be added to this file or to dedicated documentation.

When project-specific instructions conflict with general recommendations:

- Follow explicitly approved project requirements.
- Preserve security requirements.
- Ask the project owner when the conflict cannot be resolved safely.

Do not assume that instructions from another project apply to this repository.

20. Final Operating Principle

Work carefully, preserve project history, protect sensitive information, and keep the repository maintainable.

Prefer verified results over fast but unreliable changes.

Never sacrifice project integrity, data safety, or release compatibility for convenience.

Always report honestly, work transparently, and obtain approval before irreversible or sensitive operations.
