# CodeXP

![Build](https://github.com/ILoveGameCoding/intellij-codexp/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/21896-codexp.svg)](https://plugins.jetbrains.com/plugin/21896-codexp)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/21896-codexp.svg)](https://plugins.jetbrains.com/plugin/21896-codexp)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [x] Get familiar with the [template documentation][template].
- [x] Adjust the [pluginGroup](./gradle.properties), [plugin ID](./src/main/resources/META-INF/plugin.xml) and [sources package](./src/main/kotlin).
- [x] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [x] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `PLUGIN_ID` in the above README badges.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.

<!-- Plugin description -->
The CodeXP plugin is designed to gamify your coding experience in IntelliJ IDEs. It provides the following features:

- Awards experience points (xp) for various actions such as:
  - Typing
  - Building
  - Running
  - Creating functions
  - Searching
  - Replacing
  - Commit/Push/Pull
- Displays effects corresponding to the actions performed.
- Provides periodic challenges to keep the coding experience engaging.
- Displays your level based on the accumulated xp, providing a fun and engaging way to track your coding activities.
<!-- Plugin description end -->

## Installation

- Using IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "CodeXP"</kbd> >
  <kbd>Install Plugin</kbd>

  <br>

- Manually:

  Download the [latest release](https://github.com/ILoveGameCoding/intellij-codexp/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation