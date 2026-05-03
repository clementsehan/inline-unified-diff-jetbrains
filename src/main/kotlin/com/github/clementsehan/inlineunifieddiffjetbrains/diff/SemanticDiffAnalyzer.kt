package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil

enum class SafeChangeType { WHITESPACE, COMMENT, MIXED_SAFE, UNSAFE }

/**
 * Project-level service that classifies diff chunks as semantically safe or unsafe
 * using PSI analysis.
 *
 * A chunk is "safe" when the only differences between the HEAD version and the working
 * copy are whitespace or comments — the meaningful token sequence is identical on both
 * sides. Safe chunks are rendered with a muted pastel style and a descriptive label
 * instead of the glaring red/green diff backgrounds.
 *
 * Call [annotateChunks] from a background thread after diff computation. It mutates
 * [DiffChunk.safeChangeType] in-place for every chunk in the list.
 */
@Service(Service.Level.PROJECT)
class SemanticDiffAnalyzer(private val project: Project) {

    companion object {
        fun getInstance(project: Project): SemanticDiffAnalyzer = project.service()
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Annotates each [DiffChunk] in [chunks] by setting its [DiffChunk.safeChangeType].
     *
     * Must be called from a **background thread**. Internally acquires a [ReadAction]
     * for all PSI operations.
     *
     * @param chunks       Chunks produced by diff computation (mutated in-place).
     * @param baseContent  Full text of the file at HEAD, used to build the old [PsiFile].
     * @param document     Live editor document (source for the new [PsiFile]).
     * @param virtualFile  Used to determine the language for PSI parsing.
     */
    fun annotateChunks(
        chunks: List<DiffChunk>,
        baseContent: String,
        document: Document,
        virtualFile: VirtualFile,
    ) {
        ReadAction.run<Throwable> {
            try {
                val language = (virtualFile.fileType as? LanguageFileType)?.language ?: return@run

                val oldPsiFile = createPsiFile(baseContent, language, virtualFile)
                val newPsiFile = createPsiFile(document.text, language, virtualFile)

                for (chunk in chunks) {
                    chunk.safeChangeType = try {
                        val oldRange = baseContentRange(baseContent, chunk.baseStart, chunk.baseEnd)
                        val newRange = documentRange(document, chunk.currentStart, chunk.currentEnd)
                        analyzeChangeSafety(oldPsiFile, oldRange, newPsiFile, newRange)
                    } catch (e: Exception) {
                        SafeChangeType.UNSAFE
                    }
                }
            } catch (e: Exception) {
                thisLogger().warn("annotateChunks failed", e)
            }
        }
    }

    /**
     * Classifies the change between [oldRange] in [oldFile] and [newRange] in [newFile].
     *
     * Algorithm:
     * 1. Extract all PSI leaf nodes for each range.
     * 2. Strip [PsiWhiteSpace] and [PsiComment] from both sides.
     * 3. If the remaining meaningful token texts differ → [SafeChangeType.UNSAFE].
     * 4. Otherwise inspect what *was* stripped to determine the specific safe category.
     */
    fun analyzeChangeSafety(
        oldFile: PsiFile, oldRange: TextRange,
        newFile: PsiFile, newRange: TextRange,
    ): SafeChangeType {
        val oldLeaves = leavesInRange(oldFile, oldRange)
        val newLeaves = leavesInRange(newFile, newRange)

        // If PSI traversal found no tokens for a non-empty range, the language doesn't
        // produce a traversable token tree (e.g. TextMate fallback grammars). Fall back
        // to a text-based analysis so comment/whitespace-only changes are still detected.
        if ((oldLeaves.isEmpty() && !oldRange.isEmpty) || (newLeaves.isEmpty() && !newRange.isEmpty)) {
            val oldText = if (oldRange.isEmpty) "" else oldFile.text.substring(oldRange.startOffset, oldRange.endOffset)
            val newText = if (newRange.isEmpty) "" else newFile.text.substring(newRange.startOffset, newRange.endOffset)
            return analyzeChangeSafetyTextBased(oldText, newText)
        }

        val oldMeaningful = oldLeaves.filterMeaningful().map { it.text }
        val newMeaningful = newLeaves.filterMeaningful().map { it.text }

        if (oldMeaningful != newMeaningful) return SafeChangeType.UNSAFE

        val commentsChanged =
            oldLeaves.filterIsInstance<PsiComment>().map { it.text } !=
            newLeaves.filterIsInstance<PsiComment>().map { it.text }

        val whitespaceChanged =
            oldLeaves.filterIsInstance<PsiWhiteSpace>().map { it.text } !=
            newLeaves.filterIsInstance<PsiWhiteSpace>().map { it.text }

        return when {
            commentsChanged && whitespaceChanged -> SafeChangeType.MIXED_SAFE
            commentsChanged                      -> SafeChangeType.COMMENT
            else                                 -> SafeChangeType.WHITESPACE
        }
    }

    // -------------------------------------------------------------------------
    // PSI file construction
    // -------------------------------------------------------------------------

    /**
     * Creates a lightweight in-memory [PsiFile] from [text].
     *
     * [eventSystemEnabled]=false prevents PSI events from firing during construction,
     * making this safe to call on a background thread. [markAsCopy]=true signals that
     * this file is not a source-of-truth and should not be tracked by the IDE.
     */
    private fun createPsiFile(
        text: String,
        language: Language,
        virtualFile: VirtualFile,
    ): PsiFile {
        val ext = virtualFile.extension ?: "txt"
        return PsiFileFactory.getInstance(project).createFileFromText(
            "diff_scratch.$ext",
            language,
            text,
            /*eventSystemEnabled=*/ false,
            /*markAsCopy=*/ true,
        )
    }

    // -------------------------------------------------------------------------
    // Leaf extraction
    // -------------------------------------------------------------------------

    /**
     * Returns all leaf (childless) [PsiElement]s whose [PsiElement.getTextRange]
     * intersects [range], using [SyntaxTraverser] for an efficient depth-first walk.
     */
    private fun leavesInRange(file: PsiFile, range: TextRange): List<PsiElement> {
        // TextRange.intersects uses strict inequality, so an empty range TextRange(x,x)
        // still matches any leaf whose range spans x. Guard here to avoid spurious
        // token matches for ADDED/DELETED chunks whose opposite side has no lines.
        if (range.isEmpty) return emptyList()

        // Use strict containment (start >= range.start AND end <= range.end) rather than
        // intersects(). intersects() uses strict inequality on one side only, so a token
        // whose startOffset equals range.endOffset is excluded, but a token whose
        // startOffset is one before range.endOffset (like `describe` immediately after
        // the closing `*/`) still passes. Containment requires the entire token to live
        // inside the range, so boundary tokens on the next changed line are never included.
        return SyntaxTraverser.psiTraverser(file)
            .filter { element: PsiElement ->
                element.firstChild == null &&
                element.textRange.startOffset >= range.startOffset &&
                element.textRange.endOffset   <= range.endOffset
            }
            .toList()
    }

    private fun List<PsiElement>.filterMeaningful(): List<PsiElement> =
        filter { element ->
            element !is PsiWhiteSpace &&
            element !is PsiComment &&
            // Block comments (e.g. JSDoc /** ... */) are non-leaf PsiComment nodes whose
            // children are raw doc tokens (DOC_COMMENT_DATA, DOC_TAG_NAME, etc.) — not
            // PsiComment instances themselves. Without this check those inner tokens pass
            // the two guards above and are treated as meaningful code, causing UNSAFE.
            PsiTreeUtil.getParentOfType(element, PsiComment::class.java) == null
        }

    // -------------------------------------------------------------------------
    // Text-based fallback (for languages without a traversable PSI token tree)
    // -------------------------------------------------------------------------

    /**
     * Classifies the change between [oldText] and [newText] using line-level text analysis.
     * Used when the language (e.g. TextMate) doesn't produce PSI leaf nodes.
     *
     * A line is considered a "comment line" if its trimmed content starts with a common
     * single-line comment marker (`//`, `#`, `/*`, `*/`, ` * `). Inline comments at the
     * end of a code line are not detected here; that edge case is handled by PSI in
     * languages that have a full parser.
     */
    private fun analyzeChangeSafetyTextBased(oldText: String, newText: String): SafeChangeType {
        if (codeLines(oldText) != codeLines(newText)) return SafeChangeType.UNSAFE

        val commentsChanged = commentLines(oldText) != commentLines(newText)

        // "whitespace changed" = non-comment content (blank lines, indentation) changed
        val nonCommentOld = oldText.lines().filter { !isCommentLine(it.trim()) }.joinToString("\n")
        val nonCommentNew = newText.lines().filter { !isCommentLine(it.trim()) }.joinToString("\n")
        val whitespaceChanged = nonCommentOld != nonCommentNew

        return when {
            commentsChanged && whitespaceChanged -> SafeChangeType.MIXED_SAFE
            commentsChanged                      -> SafeChangeType.COMMENT
            else                                 -> SafeChangeType.WHITESPACE
        }
    }

    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !isCommentLine(it) }

    private fun commentLines(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && isCommentLine(it) }

    private fun isCommentLine(trimmed: String): Boolean =
        trimmed.startsWith("//") ||
        trimmed.startsWith("#")  ||
        trimmed.startsWith("/*") ||
        trimmed.startsWith("*/") ||
        trimmed.startsWith("* ") ||
        trimmed == "*"

    // -------------------------------------------------------------------------
    // Range helpers
    // -------------------------------------------------------------------------

    /**
     * Converts 0-based [startLine]..[endLine) line indices in [baseContent] to a
     * character [TextRange] suitable for querying the in-memory [PsiFile].
     */
    private fun baseContentRange(baseContent: String, startLine: Int, endLine: Int): TextRange {
        val start = lineStartOffset(baseContent, startLine)
        val end   = lineStartOffset(baseContent, endLine).coerceAtMost(baseContent.length)
        return TextRange(start.coerceAtMost(baseContent.length), end.coerceAtLeast(start))
    }

    private fun lineStartOffset(text: String, lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        var line = 0
        for (i in text.indices) {
            if (text[i] == '\n') {
                if (++line == lineIndex) return i + 1
            }
        }
        return text.length
    }

    /**
     * Converts 0-based [startLine]..[endLine) line indices in [document] to a
     * character [TextRange] for querying the PSI tree backed by that document.
     */
    private fun documentRange(document: Document, startLine: Int, endLine: Int): TextRange {
        val start = if (startLine < document.lineCount)
            document.getLineStartOffset(startLine) else document.textLength
        val end = when {
            endLine <= 0                    -> start
            endLine <= document.lineCount   -> document.getLineStartOffset(endLine)
            else                            -> document.textLength
        }
        return TextRange(start, end.coerceAtLeast(start))
    }
}
