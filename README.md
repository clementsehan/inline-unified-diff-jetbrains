# Inline Unified Diff

![Build](https://github.com/clementsehan/inline-unified-diff-jetbrains/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/31530.svg)](https://plugins.jetbrains.com/plugin/31530)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31530.svg)](https://plugins.jetbrains.com/plugin/31530)

<!-- Plugin description -->
**You want to review your uncommitted changes before committing — but that means opening a diff panel, hunting for the file, scrolling to the right chunk, and then scrolling back to where you were in the editor.** That context switch breaks flow, and it adds up.

**Inline Unified Diff** eliminates the round-trip. It overlays your uncommitted Git changes directly on your code — right where they happened — without leaving the editor.

When you toggle the diff on, every changed region is annotated inline:

- **Deleted lines** appear as a red ghost block at the exact location where they were removed, showing the original text from HEAD.
- **Modified lines** show the old version as a red ghost block immediately above the new (green-highlighted) lines.
- **Added lines** are highlighted in green.

Each chunk shows a **✓ Keep** and an **↩ Undo** button:

- **Keep** dismisses the diff overlay for that chunk, leaving your current text as-is.
- **Undo** reverts the chunk back to the HEAD version (wrapped in a normal undoable write command).

**Smart safety labels** classify low-risk changes — whitespace adjustments, comment edits, dead code removal — so genuinely risky edits stand out at a glance.

**How to activate**

- Press <kbd>Ctrl+Alt+Shift+D</kbd> to toggle the diff on/off for the current file.
- Or click the status-bar icon at the bottom of the IDE window.
- Or right-click in the editor and choose **Toggle Inline Diff View**.

---

**Works with all JetBrains IDEs:** IntelliJ IDEA, PyCharm, WebStorm, GoLand, Rider, CLion, DataGrip, Android Studio, and more.

---

If you find this plugin useful, consider [buying me a coffee ☕](https://ko-fi.com/clemsehan) — it helps keep the project alive!

---

**Keywords:** git, diff, code review, vcs, uncommitted changes, inline diff, unified diff, git diff, highlight changes, editor overlay, gutter diff
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
