<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CodeXP Changelog

## [Unreleased]

### Added
- Add contributor documentation, issue templates, pull request template, security policy, and CODEOWNERS.
- Add focused domain tests for level calculation and challenge definitions.

### Fixed
- Fix Kotlin bytecode generation for IntelliJ `ToolWindowFactory` default methods so Plugin Verifier no longer reports internal API usages.

### Changed
- Modernize the build to IntelliJ Platform Gradle Plugin 2.x, Kotlin 2.3, Gradle 9, and Java 17 bytecode.
- Consolidate CI around build, tests, coverage, Plugin Verifier, release drafts, signing, and Marketplace publishing.
- Restrict Marketplace publishing workflow to the maintainer and a protected deployment environment.
- Remove repository-tracked local IntelliJ Gradle JDK settings.
- Expand the plugin's compatible IDE build versions from `243.*` to `261.*`.

### Removed
- Remove the obsolete UI test workflow and legacy template service test.
- Remove stale IntelliJ run configurations for removed UI test and Qodana tasks.

## [2.0.2] - 2024-12-18
### Added
- Added LICENSE file with MIT License to clarify the terms of use.

### Changed
- Expand the plugin's compatible IDE build versions from `241.*` to `243.*`.

## [2.0.1] - 2024-04-04
### Fixed
- Fix plugin compatibility issue after IntelliJ IDEA version 2023.2

## [2.0.0] - 2023-08-30

### Added
- Organize CodeXP configuration options by type for easier readability.
- Add a new notification type: CodeXP notification
- Add configuration for notification type (IntelliJ notification or CodeXP notification)

### Fixed
- Fix plugin compatibility issue after IntelliJ IDEA version 2023.2

## [1.2.1] - 2023-07-19

### Fixed
- Fix plugin initialization issue of CodeXP plugin at IDE startup

## [1.2.0] - 2023-07-18

### Added

- Add 'Enter', 'Cut', 'Copy' event statistic and challenge
- Display gained experience point from keyboard event into typed position
- Add configurations for show gained experience and gained experience display position

### Fixed

- Fix an issue where settings applied in the CodeXP configuration window were not being saved

## [1.1.0] - 2023-06-18

### Added

- Add CodeXP configuration in IDE setting
- Add checkbox for show/hide completed challenges

## [1.0.0] - 2023-06-04

### Added

- Implement core logic of CodeXP
- Implement CodeXP Dashboard
- Define events to detect and add challenges for each event
- Implement notification alert feature for leveling up or completing challenges

[Unreleased]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.2...HEAD

[2.0.2]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.1...v2.0.2

[2.0.1]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.0...v2.0.1

[2.0.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.2.1...v2.0.0

[1.2.1]: https://github.com/ByteAurora/intellij-codexp/compare/v1.2.0...v1.2.1

[1.2.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.1.0...v1.2.0

[1.1.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.0.0...v1.1.0

[1.0.0]: https://github.com/ByteAurora/intellij-codexp/commits/v1.0.0
