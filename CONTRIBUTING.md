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

## Kotlin Documentation

Use KDoc for Kotlin documentation comments. KDoc comments use `/** ... */` and should be attached directly to declarations such as classes, interfaces, objects, functions, properties, and enum entries.

Write KDoc when it explains a public or internal contract, a non-obvious domain rule, lifecycle behavior, persistence compatibility, threading expectations, or an extension point. Avoid comments that only repeat the declaration name or restate obvious implementation details.

Recommended format:

```kotlin
/**
 * Records an IDE event and updates user progress.
 *
 * The returned result describes every UI notification that should be emitted
 * after the state mutation is complete.
 *
 * @param state Persistent CodeXP state to mutate.
 * @param event IDE event to record.
 * @return Progress changes produced by the event.
 */
fun recordEvent(state: CodeXPState, event: Event): CodeXPProgressResult
```

KDoc guidelines:

- Start with a short summary sentence.
- Add a blank line before longer details.
- Use `@param` for parameters whose meaning is not obvious from the name.
- Use `@return` when the returned value carries meaningful behavior or state.
- Use `@property` for primary-constructor properties when documenting data classes.
- Use KDoc links like `[CodeXPState]`, `[CodeXPService]`, or `[Event.TYPING]` when referencing code symbols.
- Prefer documenting why a rule exists, what callers can rely on, and what must remain compatible.
- Keep implementation comments rare; when a block needs explanation, prefer extracting a named function and documenting that function with KDoc.
- Do not use KDoc to preserve stale history. Update or remove comments when behavior changes.

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
