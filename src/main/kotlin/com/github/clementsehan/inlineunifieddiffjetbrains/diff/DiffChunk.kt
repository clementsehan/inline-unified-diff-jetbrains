package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.RangeHighlighter

enum class DiffType { ADDED, DELETED, MODIFIED }

/**
 * One contiguous diff region between the HEAD content and the current [Document].
 *
 * Line indices are 0-based throughout, matching [Document.getLineStartOffset].
 *
 * @param type         ADDED   → only current lines exist (green highlight + button bar below)
 *                     DELETED → only base lines exist (red ghost block with buttons)
 *                     MODIFIED → both sides differ (red ghost above + green highlight below)
 * @param currentStart First affected line in the current document (inclusive).
 *                     For DELETED this is the insertion point for the ghost block.
 * @param currentEnd   One-past-last affected line (exclusive). Equals [currentStart] when DELETED.
 * @param baseStart    First affected line in the HEAD content (inclusive, 0-based).
 * @param baseEnd      One-past-last affected line in HEAD (exclusive, 0-based).
 * @param baseLines    Original lines from HEAD; used for the red ghost renderer and Undo rewrites.
 */
data class DiffChunk(
    val type: DiffType,
    val currentStart: Int,
    val currentEnd: Int,
    val baseStart: Int,
    val baseEnd: Int,
    val baseLines: List<String>,
) {
    /** Set by [SemanticDiffAnalyzer.annotateChunks] after diff computation. */
    var safeChangeType: SafeChangeType = SafeChangeType.UNSAFE

    /** Green [RangeHighlighter]s applied to added/modified current lines. */
    val addedHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    /** [Inlay]s that render the ghost block (deleted lines + Keep/Undo buttons). */
    val deletedInlays: MutableList<Inlay<*>> = mutableListOf()
}
