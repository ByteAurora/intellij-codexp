# Release Process

CodeXP uses `develop` for day-to-day work and `main` for stable, release-ready code.

## Branch Model

- `main` is the stable branch that represents published or release-ready code.
- `develop` is the integration branch for normal work.
- Feature, fix, documentation, maintenance, and dependency branches should target `develop`.
- `main` should receive changes through pull requests from `develop` when those changes are ready to be part of the stable branch.
- Hotfixes may branch from `main`, merge back into `main`, and then be reconciled with `develop`.

## Branch Naming

Use short, descriptive branch names with one of these prefixes:

- `feature/` for user-facing features.
- `fix/` for bug fixes.
- `docs/` for documentation-only changes.
- `chore/` for maintenance, build, dependency, or repository hygiene changes.
- `release/` for release preparation branches.
- `hotfix/` for urgent fixes based on `main`.

Prefer lowercase kebab-case after the prefix:

```text
feature/custom-xp-rules
fix/idea-compatibility
docs/release-process
chore/update-github-actions
release/2.2.0
hotfix/2.1.1
```

## Pull Request Flow

Normal development:

```text
feature/* -> develop
fix/* -> develop
docs/* -> develop
chore/* -> develop
dependabot/* -> develop
```

Stable branch sync:

```text
develop -> main
```

Use `develop -> main` for release preparation or stable maintenance syncs. A stable maintenance sync should not imply Marketplace publishing.

## Pull Request Format

Use concise titles that describe the purpose of the change:

```text
feat: Add custom XP rules
fix: Restore compatibility with newer IDE builds
docs: Update release process
chore: Update GitHub Actions dependencies
Release CodeXP v2.2.0
```

For normal work, keep the body short:

```md
Briefly explain why this change is needed.

## Changes
- Add the main change.
- Note compatibility or maintenance impact when relevant.

Please check the build workflows pass before merging.
```

For `develop -> main` release PRs, use the existing release style:

```md
## Description
This PR merges the `develop` branch into `main` for the release of version X.Y.Z

<br>

## Features
- User-facing feature or compatibility change.

<br>

## Fixed
- User-facing bug fix.

<br>

Please check the build workflows pass before merging.
```

For stable maintenance syncs that are not plugin releases, state that clearly in the PR body.

## CI Expectations

The required CI job is `Build, test, and verify`.

It can take several minutes because it builds the plugin and runs IntelliJ Plugin Verifier across multiple IDE versions.

When waiting on a pull request:

- Wait for `Build, test, and verify` to complete before merging.
- If a branch is out of date with `main`, update the branch and wait for CI again.
- Cancelled runs can be normal when newer pushes supersede older runs on the same branch.
- A stable maintenance sync to `main` should not create a release draft or publish the plugin.

## Release Flow

Normal merges do not publish the plugin.

1. Merge release-ready changes into `main`.
2. Run and verify CI on `main`.
3. A maintainer prepares the release notes and GitHub Release.
4. Publishing is performed only by a maintainer.
5. Confirm the version is listed on JetBrains Marketplace.

Release draft creation must be an explicit maintainer action.

## Version And Compatibility Checks

Before publishing a release, verify:

- `pluginVersion` has the intended version.
- `pluginSinceBuild` and `pluginUntilBuild` match the intended IDE compatibility range.
- `./gradlew check koverXmlReport buildPlugin verifyPlugin` passes.

## Release Notes

Release notes are for plugin users. They should explain what changed from the user's perspective.

Include:

- IDE compatibility changes.
- User-visible features.
- Bug fixes.
- Stability, performance, installation, or behavior changes that affect users.
- Migration or update notes that affect users.

Exclude or minimize:

- CI, branch protection, or repository policy changes.
- Dependency maintenance that does not affect users.
- Contributor-only docs, templates, CODEOWNERS, and internal tests.
- Build tool updates unless they change compatibility, installation, or runtime behavior.

Example user-facing release note:

```md
### Changed
- Added support for IntelliJ IDEA 2025.2 and newer IDE builds up to 2026.1.

### Fixed
- Fixed compatibility issues that could prevent the plugin from loading on newer IntelliJ IDE versions.
```

GitHub Release drafts should use:

- Title: `vX.Y.Z`
- Tag: `vX.Y.Z`
- Target: the intended `main` commit
- Notes: user-facing release notes only
