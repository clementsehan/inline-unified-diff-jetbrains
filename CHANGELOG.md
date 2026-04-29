<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# inline-unified-diff-jetbrains Changelog

## [Unreleased]

## [0.0.1] - 2026-04-29
### Added
- Inline unified diff rendering directly inside the editor with per-line highlights for insertions, deletions, and replacements
- Accept / Reject buttons on each diff chunk via inline gutter controls and mouse listener
- Floating summary panel with "Keep All" and "Undo All" bulk action buttons
- Status bar widget showing the current inline diff state
- Toggle action to enable/disable inline diff mode (`ToggleInlineDiffAction`)
- Auto-disables inline diff mode when all chunks have been resolved
