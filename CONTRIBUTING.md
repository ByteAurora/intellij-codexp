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

## Release Policy

CodeXP is open source, but publishing to JetBrains Marketplace is restricted to the maintainer.

Contributors should not run or request release publishing. Marketplace credentials are stored only in GitHub Secrets or protected GitHub Environments.

