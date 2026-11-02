<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CodeXP Changelog

## [Unreleased]

### Added
- Show each challenge's own sequence number in Challenges and History, including saved progress from earlier versions.
- Reset all CodeXP progress and preferences to their initial defaults while keeping your nickname.
- Keep recently updated challenges at the top with gentle card movement and restrained border highlights; preserve your reading position in long lists.
- Track real level progress, all challenges, completion history and activity in the new crest dashboard, with immediate values and subtle ambient crest decoration that respects Reduce motion.
- Celebrate XP gains, completed challenges, and level ups with gold achievement effects and subtle code details. Choose Subtle or Celebratory style, with a Reduce motion option.

### Fixed
- Match the Activity list, empty space and scroll insets to the dashboard background in light and dark themes.
- Show challenge rewards once and keep small, translucent XP labels close to the caret. Menu and toolbar actions now show XP in their project window even without an open editor.

### Changed
- Replace the completed-challenge visibility checkbox with the always-available History tab.
- Preserve existing 2.1.1 progress and notification preferences when upgrading; new effect options use their defaults and recent-event ordering begins with new activity.

## [2.1.1] - 2026-10-12

### Added
- Support IntelliJ IDEA 2026.2.x, including 2026.2.1, while retaining compatibility with IDE builds from 2023.3 onward.

### Fixed
- Restore saved values when resetting CodeXP settings, and avoid incorrectly reporting unsaved changes when the settings page is not open.
- Keep XP effects and CodeXP notifications associated with the project window where the action occurred, and clean them up when the project closes.

### Changed
- Refresh the CodeXP settings layout with standard IDE controls. Notification descriptions and XP position controls now update immediately when their related options change.

## [2.1.0] - 2026-05-18

### Changed
- Extend IDE compatibility through IntelliJ Platform 2026.1 (`261.*`), while retaining support for 2023.3 and later.

## [2.0.3] - 2025-02-03

### Changed
- Extend IDE compatibility through IntelliJ Platform 2025.1 (`251.*`).

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

[Unreleased]: https://github.com/ByteAurora/intellij-codexp/compare/v2.1.1...HEAD

[2.1.1]: https://github.com/ByteAurora/intellij-codexp/compare/v2.1.0...v2.1.1

[2.1.0]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.3...v2.1.0

[2.0.3]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.2...v2.0.3

[2.0.2]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.1...v2.0.2

[2.0.1]: https://github.com/ByteAurora/intellij-codexp/compare/v2.0.0...v2.0.1

[2.0.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.2.1...v2.0.0

[1.2.1]: https://github.com/ByteAurora/intellij-codexp/compare/v1.2.0...v1.2.1

[1.2.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.1.0...v1.2.0

[1.1.0]: https://github.com/ByteAurora/intellij-codexp/compare/v1.0.0...v1.1.0

[1.0.0]: https://github.com/ByteAurora/intellij-codexp/commits/v1.0.0
