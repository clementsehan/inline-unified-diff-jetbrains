<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# inline-unified-diff-jetbrains Changelog

## [Unreleased]

## [0.0.2] - 2026-05-03

### Added

- Semantic noise reduction: diff chunks that only change whitespace, comments, or formatting are classified as safe and rendered with a muted label (e.g. "✓ Safe: Formatting only", "✓ Safe: Comments modified") so genuinely risky changes stand out
- Safe classification works for all JetBrains-supported languages via PSI analysis, with an automatic text-based fallback for languages backed by TextMate grammars
- Safe-deleted chunks (e.g. comment-only removals) display the safe label inline inside the ghost block, to the right of the Keep / Undo buttons

## [0.0.1] - 2026-04-29

### Added

- Inline unified diff rendering directly inside the editor with per-line highlights for insertions, deletions, and replacements
- Accept / Reject buttons on each diff chunk via inline gutter controls and mouse listener
- Floating summary panel with "Keep All" and "Undo All" bulk action buttons
- Status bar widget showing the current inline diff state
- Toggle action to enable/disable inline diff mode (`ToggleInlineDiffAction`)
- Auto-disables inline diff mode when all chunks have been resolved

[Unreleased]: https://github.com/clementsehan/inline-unified-diff-jetbrains/compare/0.0.2...HEAD
[0.0.2]: https://github.com/clementsehan/inline-unified-diff-jetbrains/compare/0.0.1...0.0.2
[0.0.1]: https://github.com/clementsehan/inline-unified-diff-jetbrains/commits/0.0.1
