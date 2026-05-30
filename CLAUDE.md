# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Inline Unified Diff** is a JetBrains IDE plugin that displays uncommitted Git changes directly in the editor in unified diff format. Users can toggle the view on/off and accept or undo individual diff chunks without leaving the editor.

- **Minimum IDE**: JetBrains IDEs 2024.1+
- **Marketplace**: https://plugins.jetbrains.com/plugin/31530

## Build & Development Commands

```bash
./gradlew buildPlugin          # Build distributable .zip in build/distributions/
./gradlew check                # Run tests
./gradlew verifyPlugin         # Verify plugin structure and compatibility against 2024.3.5
./gradlew patchChangelog       # Bump version and move [Unreleased] to versioned section
./gradlew getChangelog --unreleased --no-header  # Get release notes
```

## Architecture Overview

### Core Service: InlineDiffService
`src/main/kotlin/.../diff/InlineDiffService.kt` — Project-level `@Service` that owns all diff state per editor.

Key methods:
- `computeAndShowDiff(editor, virtualFile, project)` — Entry point: fetches HEAD, computes diffs, renders on EDT
- `keepChunk(editor, chunk)` / `undoChunk(editor, chunk)` — Per-chunk accept/revert
- `keepAll(editor)` / `undoAll(editor)` — Bulk actions
- `clearDiff(editor)` — Remove all markup for an editor

Per-editor state is stored as `EditorDiffState` (child disposable), which holds chunks, markup, mouse listener, and floating summary panel. State auto-unregisters on editor disposal.

### Diff Computation Pipeline

1. **Toggle** (`ToggleInlineDiffAction`) — Ctrl+Alt+Shift+D or status-bar click
2. **Background**: Fetches HEAD content via `git show HEAD:<path>` (ProcessBuilder + `GitRepositoryManager`), then computes diffs via `ComparisonManager.compareLines()`
3. **Semantic Analysis**: `SemanticDiffAnalyzer.annotateChunks()` classifies each chunk using PSI (see below)
4. **EDT Rendering**: `applyDiff()` adds green `RangeHighlighter`s for added/modified lines, block `Inlay`s for deleted lines (ghost block + Keep/Undo buttons), floating summary panel, and auto-scrolls to first chunk

### Semantic Change Classification (SafeChangeType)

`SafeChangeType` values: `WHITESPACE`, `COMMENT`, `MIXED_SAFE`, `DEAD_CODE_REMOVAL`, `UNSAFE`

`SemanticDiffAnalyzer`:
- Extracts PSI leaf nodes from both sides, strips whitespace/comments, compares tokens
- Falls back to text-based comparison for TextMate grammars (no PSI available)
- **Dead Code Detection**: If deleted chunk is a single function with zero call-sites (via `PsiSearchHelper.processAllFilesWithWord()`), marks as `DEAD_CODE_REMOVAL`. Supports Java (`PsiMethod`), Kotlin (`KtNamedFunction`), JavaScript (`JSFunction`)
- Each language guarded with `Class.forName()` check to avoid `ClassNotFoundError` in IDEs without that plugin (e.g., PhpStorm has no Java plugin)

### Document Rewriting

`DiffType`: `ADDED` (Undo = delete), `DELETED` (Undo = insert), `MODIFIED` (Undo = replace)

All rewrites wrapped in `WriteCommandAction` to be undoable via Ctrl+Z and PSI-aware.

### Key Data Models

```
DiffChunk
  ├─ type: DiffType (ADDED, DELETED, MODIFIED)
  ├─ currentStart/End: Int (editor lines, 0-based, exclusive)
  ├─ baseStart/End: Int (HEAD lines, 0-based, exclusive)
  ├─ baseLines: List<String>
  ├─ safeChangeType: SafeChangeType
  ├─ addedHighlighters: List<RangeHighlighter>
  └─ deletedInlays: List<Inlay<*>>
```

### Rendering

- **`InlineDiffRenderer`**: Renders deleted lines as semi-transparent red ghost block with striped border. Paints Keep (✓) and Undo (↩) pill buttons; stores button bounds as `Rectangle` for hit-testing
- **`SafeHintRenderer`**: Renders the blue safe-change label above added/modified lines
- **`DiffSummaryPanel`**: Floating overlay at bottom-center of editor; fully custom `paintComponent()` (no child Swing components)
- **`InlineDiffMouseListener`**: Hit-tests clicks against button bounds (inlay origin + relative offset)
- **`InlineDiffStatusBarWidget`**: Shows Diff icon (inactive) or ApplyNotConflicts green icon (active)

### Plugin Registration

`src/main/resources/META-INF/plugin.xml`:
- Required deps: `com.intellij.modules.platform`, `Git4Idea`
- Optional dep: `org.jetbrains.kotlin` → loads `withKotlin.xml` for K2 mode compatibility

### CI/CD

Two relevant workflows:
- **`build.yml`**: Runs on push to `main` and PRs — builds, tests, verifies, creates a draft release on `main`
- **`release.yml`**: Manual trigger — signs and publishes to Marketplace using `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`

## Important Notes

1. **Git Integration**: HEAD content is fetched via `ProcessBuilder` running `git show HEAD:<path>`. There is a TODO to migrate to `GitContentRevision` API for better proxy/auth support.

2. **LineFragment Semantics**: `ComparisonManager.compareLines()` returns fragments where `startLine1/endLine1` = range in base (HEAD), `startLine2/endLine2` = range in current content (0-based, end exclusive). Chunk `DiffType` is determined by whether each side's range is empty.

3. **No Tests Currently**: The test framework is imported in `build.gradle.kts` but no test files exist yet.
