package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import git4idea.repo.GitRepositoryManager
import java.awt.Color

/**
 * Project-level service that owns all inline diff state.
 *
 * Lifecycle per editor:
 *   1. [computeAndShowDiff] fetches HEAD content on a background thread, computes
 *      [LineFragment]s via [ComparisonManager], then switches to the EDT to apply markup.
 *   2. [keepChunk]  → strips markup for that chunk, leaving document text unchanged.
 *   3. [undoChunk]  → rewrites the document back to HEAD for that chunk, then strips markup.
 *   4. [clearDiff]  → removes every highlighter/inlay for the editor.
 */
@Service(Service.Level.PROJECT)
class InlineDiffService(private val project: Project) : Disposable {

    companion object {
        private val ADDED_BG    = JBColor(Color(0,   200, 80,  40), Color(0,   160, 70,  60))
        private val MODIFIED_BG = JBColor(Color(50,  200, 100, 40), Color(50,  160, 90,  60))

        fun getInstance(project: Project): InlineDiffService = project.service()
    }

    // -------------------------------------------------------------------------
    // Per-editor state
    // -------------------------------------------------------------------------

    /**
     * Tracks markup and the mouse listener for one editor.
     * Implements [Disposable] so [Disposer.register] ties its lifetime to the service.
     */
    private inner class EditorDiffState(val editor: Editor) : Disposable {
        val chunks = mutableListOf<DiffChunk>()
        private val mouseListener = InlineDiffMouseListener { chunks }

        init {
            // addEditorMouseListener(listener, parentDisposable) auto-unregisters on dispose
            editor.addEditorMouseListener(mouseListener, this)
        }

        override fun dispose() = clearAllMarkup()

        fun clearAllMarkup() {
            chunks.forEach { clearChunkMarkup(editor, it) }
            chunks.clear()
        }
    }

    /** Map is only ever accessed from the EDT. */
    private val editorStates = mutableMapOf<Editor, EditorDiffState>()

    fun isActive(editor: Editor): Boolean = editor in editorStates

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Asynchronously computes the diff between HEAD and the current [Document], then
     * renders green highlights (additions) and red ghost inlays (deletions) on the EDT.
     */
    fun computeAndShowDiff(editor: Editor, virtualFile: VirtualFile, project: Project) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Computing Inline Diff…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching HEAD content…"
                val baseContent = fetchHeadContent(virtualFile) ?: run {
                    thisLogger().warn("Could not fetch HEAD content for ${virtualFile.name}")
                    return
                }

                indicator.text = "Comparing with working copy…"
                val currentContent = ReadAction.compute<String, Throwable> {
                    editor.document.text
                }

                // ComparisonManager must be called inside a read action
                val fragments = ReadAction.compute<List<LineFragment>, Throwable> {
                    ComparisonManager.getInstance().compareLines(
                        baseContent, currentContent,
                        ComparisonPolicy.DEFAULT, indicator
                    )
                }

                val chunks = buildChunks(fragments, baseContent)

                ApplicationManager.getApplication().invokeLater {
                    if (!editor.isDisposed) {
                        applyDiff(editor, chunks)
                        notifyStatusBar()
                    }
                }
            }
        })
    }

    /** Removes all markup for [editor] without touching the document. */
    fun clearDiff(editor: Editor) {
        val state = editorStates.remove(editor) ?: return
        Disposer.dispose(state)
        notifyStatusBar()
    }

    /**
     * **Keep** – accepts the current text for [chunk] and removes the diff overlay.
     * The document is NOT modified; existing code stays as-is.
     */
    fun keepChunk(editor: Editor, chunk: DiffChunk) {
        clearChunkMarkup(editor, chunk)
        editorStates[editor]?.chunks?.remove(chunk)
        autoToggleOffIfEmpty(editor)
    }

    /**
     * **Undo** – reverts the [chunk]'s lines back to the HEAD version, then removes markup.
     *
     * The rewrite is wrapped in a [WriteCommandAction] so:
     *   • it is undoable via Ctrl+Z
     *   • PSI and VCS change tracking record the modification correctly
     *   • the platform can coalesce it with neighbouring document writes
     */
    fun undoChunk(editor: Editor, chunk: DiffChunk) {
        WriteCommandAction.runWriteCommandAction(project, "Undo Diff Chunk", null, {
            val doc      = editor.document
            val baseText = chunk.baseLines.joinToString("\n")

            // Reconstruct the affected offset range in the CURRENT document.
            // Because undoChunk is always called from the EDT, line counts are stable.
            when (chunk.type) {
                DiffType.ADDED -> {
                    // Added lines must be deleted entirely
                    val start = doc.getLineStartOffset(chunk.currentStart)
                    val end   = lineEndForDelete(doc, chunk.currentEnd)
                    doc.deleteString(start, end)
                }
                DiffType.DELETED -> {
                    // Deleted lines must be re-inserted at the gap point
                    val insertAt = if (chunk.currentStart < doc.lineCount)
                        doc.getLineStartOffset(chunk.currentStart)
                    else
                        doc.textLength
                    doc.insertString(insertAt, baseText + "\n")
                }
                DiffType.MODIFIED -> {
                    // Modified lines are replaced wholesale with the base text
                    val start = doc.getLineStartOffset(chunk.currentStart)
                    val end   = lineEndForDelete(doc, chunk.currentEnd)
                    doc.replaceString(start, end, baseText + "\n")
                }
            }
        })

        clearChunkMarkup(editor, chunk)
        editorStates[editor]?.chunks?.remove(chunk)
        autoToggleOffIfEmpty(editor)
    }

    private fun autoToggleOffIfEmpty(editor: Editor) {
        if (editorStates[editor]?.chunks?.isEmpty() == true) clearDiff(editor)
    }

    override fun dispose() {
        editorStates.values.toList().forEach { Disposer.dispose(it) }
        editorStates.clear()
    }

    // -------------------------------------------------------------------------
    // Markup application
    // -------------------------------------------------------------------------

    private fun applyDiff(editor: Editor, chunks: List<DiffChunk>) {
        val state = EditorDiffState(editor)
        editorStates[editor] = state
        Disposer.register(this, state)
        chunks.forEach { chunk ->
            applyChunkMarkup(editor, chunk)
            state.chunks.add(chunk)
        }
    }

    private fun applyChunkMarkup(editor: Editor, chunk: DiffChunk) {
        val doc         = editor.document
        val markupModel = editor.markupModel

        // --- Green background for ADDED / MODIFIED current-doc lines ---------------
        if (chunk.type != DiffType.DELETED && chunk.currentStart < chunk.currentEnd) {
            val bg    = if (chunk.type == DiffType.ADDED) ADDED_BG else MODIFIED_BG
            val attrs = TextAttributes().apply { backgroundColor = bg }

            val startOff = doc.getLineStartOffset(chunk.currentStart)
            val endOff   = if (chunk.currentEnd < doc.lineCount)
                doc.getLineStartOffset(chunk.currentEnd) else doc.textLength

            val hl = markupModel.addRangeHighlighter(
                startOff, endOff,
                HighlighterLayer.SELECTION - 1,
                attrs,
                HighlighterTargetArea.LINES_IN_RANGE,
            )
            chunk.addedHighlighters.add(hl)
        }

        // --- Ghost block for DELETED / MODIFIED: deleted lines + Keep/Undo buttons -
        if (chunk.type != DiffType.ADDED) {
            // Anchor to the end of the line *before* the gap so the offset is
            // unambiguously inside that line.  showAbove=false then places the
            // block below it, i.e. right at the deletion boundary.
            val (insertOffset, showAbove) = when {
                chunk.currentStart > 0 ->
                    doc.getLineEndOffset(chunk.currentStart - 1) to false
                chunk.currentStart < doc.lineCount ->
                    doc.getLineStartOffset(0) to true
                else ->
                    doc.textLength to false
            }

            val renderer = InlineDiffRenderer(
                deletedLines = chunk.baseLines,
                onKeep = { keepChunk(editor, chunk) },
                onUndo = { undoChunk(editor, chunk) },
            )

            editor.inlayModel
                .addBlockElement(insertOffset, showAbove, /*showWhenFolded=*/false, /*priority=*/100, renderer)
                ?.let { chunk.deletedInlays.add(it) }
        }

        // --- Button bar below added lines (ADDED chunks have no ghost block) ------
        if (chunk.type == DiffType.ADDED) {
            val insertOffset = doc.getLineEndOffset(chunk.currentEnd - 1)

            val renderer = InlineDiffRenderer(
                deletedLines = emptyList(),
                onKeep = { keepChunk(editor, chunk) },
                onUndo = { undoChunk(editor, chunk) },
            )

            editor.inlayModel
                .addBlockElement(insertOffset, /*showAbove=*/false, /*showWhenFolded=*/false, /*priority=*/100, renderer)
                ?.let { chunk.deletedInlays.add(it) }
        }
    }

    private fun clearChunkMarkup(editor: Editor, chunk: DiffChunk) {
        chunk.addedHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        chunk.deletedInlays.forEach     { it.dispose() }
        chunk.addedHighlighters.clear()
        chunk.deletedInlays.clear()
    }

    // -------------------------------------------------------------------------
    // Diff computation helpers
    // -------------------------------------------------------------------------

    /**
     * Maps [LineFragment]s from [ComparisonManager.compareLines] into [DiffChunk]s.
     *
     * [LineFragment] fields:
     *   startLine1/endLine1 → range in baseContent (0-based, endLine exclusive)
     *   startLine2/endLine2 → range in currentContent
     */
    private fun buildChunks(
        fragments: List<LineFragment>,
        baseContent: String,
    ): List<DiffChunk> {
        val baseLines = baseContent.lines()

        return fragments.map { frag ->
            val type = when {
                frag.startLine1 == frag.endLine1 -> DiffType.ADDED
                frag.startLine2 == frag.endLine2 -> DiffType.DELETED
                else                             -> DiffType.MODIFIED
            }
            DiffChunk(
                type         = type,
                currentStart = frag.startLine2,
                currentEnd   = frag.endLine2,
                baseLines    = baseLines.subList(
                    frag.startLine1.coerceIn(0, baseLines.size),
                    frag.endLine1.coerceIn(0, baseLines.size),
                ),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Git content retrieval
    // -------------------------------------------------------------------------

    /**
     * Returns the content of [virtualFile] from the HEAD commit, or null on failure.
     *
     * Uses `git show HEAD:<relative-path>` via [ProcessBuilder].  Runs on a background
     * thread (called from [Task.Backgroundable.run]); never call from the EDT.
     *
     * TODO: Replace with [git4idea.GitUtil] / [git4idea.history.GitContentRevision] for a
     *       fully platform-integrated approach that respects proxy/auth settings.
     */
    private fun fetchHeadContent(virtualFile: VirtualFile): String? = try {
        val repo = GitRepositoryManager.getInstance(project)
            .getRepositoryForFile(virtualFile) ?: return null

        val repoRoot     = repo.root
        val relativePath = virtualFile.path.removePrefix(repoRoot.path).trimStart('/')

        val process = ProcessBuilder("git", "show", "HEAD:$relativePath")
            .directory(java.io.File(repoRoot.path))
            .redirectErrorStream(false)
            .start()

        val content  = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode == 0) content else null
    } catch (ex: Exception) {
        thisLogger().warn("Failed to fetch HEAD content for ${virtualFile.name}", ex)
        null
    }

    // -------------------------------------------------------------------------
    // Offset arithmetic
    // -------------------------------------------------------------------------

    /**
     * Returns the start-offset of [lineIndex] in [doc], or [doc.textLength] if
     * [lineIndex] is past the end.  Used when slicing ranges for delete/replace writes.
     */
    /** Pokes the status-bar widget so its icon re-renders immediately after a state change. */
    private fun notifyStatusBar() {
        val statusBar = WindowManager.getInstance().getIdeFrame(project)?.statusBar ?: return
        (statusBar.getWidget(InlineDiffStatusBarWidget.WIDGET_ID) as? InlineDiffStatusBarWidget)
            ?.notifyUpdate()
    }

    private fun lineEndForDelete(
        doc: com.intellij.openapi.editor.Document,
        lineIndex: Int,
    ): Int = if (lineIndex < doc.lineCount) doc.getLineStartOffset(lineIndex) else doc.textLength
}
