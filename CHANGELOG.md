<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# inline-unified-diff-jetbrains Changelog

## [Unreleased]
### Added
- Dead code removal detection: deleted chunks containing functions with no usages in the project index are classified as safe and labelled "✓ Safe: Dead code removed"
- Dead code detection supports Java, Kotlin, and JavaScript/TypeScript; JS/TS support activates automatically in IDEs with the JavaScript plugin (IntelliJ IDEA Ultimate, WebStorm, etc.)
- Multi-function chunks are handled: a chunk removing several unused functions at once is marked safe only when every removed function has zero usages

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
