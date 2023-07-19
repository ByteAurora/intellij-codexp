# <img src="https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/codexp.png" alt="CodeXP" width="32" height="32"/>  CodeXP

![Build](https://github.com/ILoveGameCoding/intellij-codexp/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21896-codexp.svg)](https://plugins.jetbrains.com/plugin/21896-codexp)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21896-codexp.svg)](https://plugins.jetbrains.com/plugin/21896-codexp)

<!-- Plugin description -->
The CodeXP plugin is designed to gamify your coding experience in IntelliJ IDEs.

<br>

## Features

- Awards experience points (xp) for various actions such as:
  - Typing
  - Cut, Copy, Paste
  - Backspace, Tab, Enter
  - Save
  - Build, Run, Debug
  - Other actions
- Provides periodic challenges to keep the coding experience engaging.
- Displays your level based on the accumulated xp, providing a fun and engaging way to track your coding activities.
<!-- Plugin description end -->

<br>

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "CodeXP"</kbd> >
  <kbd>Install Plugin</kbd>

  <br>

- Manually:

  Download the [latest release](https://github.com/ILoveGameCoding/intellij-codexp/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

<br>

## Gained XP Effect on Editor

Displays the XP gained from tasks related to the keyboard and editor (typing, copying, pasting, cutting, etc.) at the
caret position.

![Effect](https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/gained_xp_effect.gif)

<br>

## Dashboard in Light/Dark Theme

<p float="left">
  <img src="https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/dashboard_light_1.png" width="400" />
  <img src="https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/dashboard_light_2.png" width="400" />
</p>
<p float="left">
  <img src="https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/dashboard_dark_1.png" width="400" />
  <img src="https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/dashboard_dark_2.png" width="400" />
</p>

<br>

## Completed Challenges

Completed challenges are added under the "Completed Challenges" section. Also, more difficult challenges are automatically added after you complete a challenge.

![Completed Challenges](https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/completed_challenges.png)

<br>

## Notification
Whenever a challenge is completed or a level up occurs, a notification is provided to inform you of the details.

![Notification](https://github.com/ILoveGameCoding/intellij-codexp/blob/main/images/notification.jpg)

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation