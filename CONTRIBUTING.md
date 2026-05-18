# Contributing to CodeXP

Thanks for your interest in CodeXP. This project welcomes bug fixes, compatibility updates, tests, documentation, and focused improvements.

## Development Setup

Requirements:

- JDK 21 for Gradle toolchains
- IntelliJ IDEA 2023.3 or later for local plugin development
- Git

Useful commands:

```bash
./gradlew check koverXmlReport buildPlugin verifyPlugin
./gradlew runIde
```

The plugin zip is created in `build/distributions/`.

## Pull Requests

Before opening a pull request:

- Keep changes focused on one problem.
- Run `./gradlew check buildPlugin` locally.
- Run `./gradlew verifyPlugin` for compatibility or build configuration changes.
- Update `CHANGELOG.md` when the change affects users, compatibility, build, or release behavior.
- Do not include private certificates, Marketplace tokens, or local signing files.

## Branch Workflow

- Use `develop` as the integration branch for normal work.
- Open feature, fix, documentation, and maintenance pull requests against `develop`.
- Use `main` as the stable release branch.
- Merge `develop` into `main` only when preparing a release.
- For urgent production fixes, branch from `main`, merge back into `main`, then merge the fix back into `develop`.

## Release Policy

CodeXP is open source, but publishing to JetBrains Marketplace is restricted to the maintainer.

Contributors should not run or request release publishing. Marketplace credentials are stored only in GitHub Secrets or protected GitHub Environments.

Release drafts are created manually from the `Release Draft` workflow after the release changes have been merged into `main`. Publishing the GitHub Release triggers the protected Marketplace deployment workflow.
