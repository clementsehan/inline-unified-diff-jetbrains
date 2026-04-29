# Inline Unified Diff

![Build](https://github.com/clementsehan/inline-unified-diff-jetbrains/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
**Inline Unified Diff** shows your uncommitted Git changes directly inside the editor — the same way a unified diff looks in a terminal — without switching to a separate diff tool.

When you toggle the diff on, every changed region is annotated inline:

- **Deleted lines** appear as a red ghost block at the exact location where they were removed, showing the original text from HEAD.
- **Modified lines** show the old version as a red ghost block immediately above the new (green-highlighted) lines.
- **Added lines** are highlighted in green.

Each annotation includes **✓ Keep** and **↩ Undo** buttons at the bottom of the block:

- **Keep** dismisses the diff overlay for that chunk, leaving your current text as-is.
- **Undo** reverts the chunk back to the HEAD version (wrapped in a normal undoable write command).

**How to activate**

- Press <kbd>Ctrl+Alt+Shift+D</kbd> to toggle the diff on/off for the current file.
- Or click the status-bar icon at the bottom of the IDE window.
- Or right-click in the editor and choose **Toggle Inline Diff View**.

---

**Works with all JetBrains IDEs:** IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, CLion, DataGrip, Android Studio, and more.

---

If you find this plugin useful, consider [buying me a coffee ☕](https://ko-fi.com/clemsehan) — it helps keep the project alive!

---

**Keywords:** diff, git diff, inline diff, unified diff, git, VCS, changes, code review, highlight changes, editor overlay
<!-- Plugin description end -->

## Compatibility

| IDE | Minimum version |
|-----|----------------|
| IntelliJ IDEA Community & Ultimate | 2024.1 |
| PyCharm Community & Professional | 2024.1 |
| WebStorm | 2024.1 |
| GoLand | 2024.1 |
| Rider | 2024.1 |
| CLion | 2024.1 |
| RubyMine | 2024.1 |
| PhpStorm | 2024.1 |
| DataGrip | 2024.1 |
| Android Studio | Koala (2024.1) |

Requires the bundled **Git** plugin to be enabled.

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Inline Unified Diff"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/clementsehan/inline-unified-diff-jetbrains/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Support

If you find this plugin useful, consider [buying me a coffee ☕](https://ko-fi.com/clemsehan) on Ko-fi — it helps keep the project alive!

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
