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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

enum class SafeChangeType { WHITESPACE, COMMENT, MIXED_SAFE, DEAD_CODE_REMOVAL, UNSAFE }

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

        // PsiMethod / PsiReferenceExpression live in the Java plugin (com.intellij.modules.java),
        // which is absent in non-Java IDEs such as PhpStorm. Guard every reference so those
        // bytecode instructions are never executed when the Java plugin is not on the classpath.
        //
        // Class.forName without an explicit loader uses Thread.currentThread().contextClassLoader,
        // which in IntelliJ's plugin system is the platform or bootstrap loader — not the plugin's
        // own PluginClassLoader. Using SemanticDiffAnalyzer::class.java.classLoader ensures we
        // look up classes through the same loader that owns this class, which has the correct
        // classpath including optional bundled plugins.
        val javaPsiAvailable: Boolean by lazy {
            try { Class.forName("com.intellij.psi.PsiMethod", false, SemanticDiffAnalyzer::class.java.classLoader); true }
            catch (_: ClassNotFoundException) { false }
            catch (_: NoClassDefFoundError)   { false }
        }

        // KtNamedFunction / KtNameReferenceExpression require the Kotlin plugin (optional dep).
        val kotlinPsiAvailable: Boolean by lazy {
            try { Class.forName("org.jetbrains.kotlin.psi.KtNamedFunction", false, SemanticDiffAnalyzer::class.java.classLoader); true }
            catch (_: ClassNotFoundException) { false }
            catch (_: NoClassDefFoundError)   { false }
        }

        // JSFunction / JSReferenceExpression cover JavaScript and TypeScript class methods,
        // getters, setters, and function declarations.
        //
        // We resolve the JavaScript plugin's classloader dynamically via PluginManagerCore
        // rather than declaring an optional <depends> in plugin.xml. This avoids a DevKit
        // "Cannot resolve plugin" IDE inspection error when developing against Community
        // edition (which has no JavaScript plugin). The isInstance checks work correctly
        // because PSI elements created by the JavaScript plugin are instances of classes
        // loaded by that same classloader.
        private val jsPluginClassLoader: ClassLoader? by lazy {
            try {
                com.intellij.ide.plugins.PluginManagerCore
                    .getPlugin(com.intellij.openapi.extensions.PluginId.getId("JavaScript"))
                    ?.pluginClassLoader
            } catch (_: Throwable) { null }
        }

        val jsPsiAvailable: Boolean by lazy {
            val cl = jsPluginClassLoader ?: return@lazy false
            try { Class.forName("com.intellij.lang.javascript.psi.JSFunction", false, cl); true }
            catch (_: ClassNotFoundException) { false }
            catch (_: NoClassDefFoundError)   { false }
        }

        // Class references cached once for isInstance checks (only accessed when jsPsiAvailable).
        private val jsFunctionClass: Class<*>? by lazy {
            val cl = jsPluginClassLoader ?: return@lazy null
            try { Class.forName("com.intellij.lang.javascript.psi.JSFunction", false, cl) }
            catch (_: Throwable) { null }
        }
        private val jsReferenceExpressionClass: Class<*>? by lazy {
            val cl = jsPluginClassLoader ?: return@lazy null
            try { Class.forName("com.intellij.lang.javascript.psi.JSReferenceExpression", false, cl) }
            catch (_: Throwable) { null }
        }
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
                val fileType = virtualFile.fileType
                val language = (fileType as? LanguageFileType)?.language
                thisLogger().warn("[INLINE-DIFF] annotateChunks — file=${virtualFile.name}  fileType=${fileType::class.java.simpleName}  isLanguageFileType=${fileType is LanguageFileType}  language=${language?.id}")
                if (language == null) {
                    thisLogger().warn("[INLINE-DIFF] annotateChunks — EARLY EXIT: not a LanguageFileType (TextMate or unknown). Chunks will stay UNSAFE.")
                    return@run
                }

                val oldPsiFile = createPsiFile(baseContent, language, virtualFile)
                val newPsiFile = createPsiFile(document.text, language, virtualFile)

                for (chunk in chunks) {
                    thisLogger().warn("[INLINE-DIFF] chunk type=${chunk.type} base=${chunk.baseStart}..${chunk.baseEnd} current=${chunk.currentStart}..${chunk.currentEnd}")
                    chunk.safeChangeType = try {
                        val oldRange = baseContentRange(baseContent, chunk.baseStart, chunk.baseEnd)
                        val newRange = documentRange(document, chunk.currentStart, chunk.currentEnd)
                        thisLogger().warn("[INLINE-DIFF] chunk ranges → oldRange=$oldRange (empty=${oldRange.isEmpty})  newRange=$newRange (empty=${newRange.isEmpty})")
                        val result = analyzeChangeSafety(oldPsiFile, oldRange, newPsiFile, newRange)
                        thisLogger().warn("[INLINE-DIFF] chunk result → $result")
                        result
                    } catch (e: Exception) {
                        thisLogger().warn("[INLINE-DIFF] chunk analysis threw exception → UNSAFE", e)
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
            thisLogger().warn("[INLINE-DIFF] analyzeChangeSafety — TEXT-BASED FALLBACK (empty leaves): oldLeaves=${oldLeaves.size}  newLeaves=${newLeaves.size}  oldRangeEmpty=${oldRange.isEmpty}  newRangeEmpty=${newRange.isEmpty}  language=${oldFile.language.id}")
            val oldText = if (oldRange.isEmpty) "" else oldFile.text.substring(oldRange.startOffset, oldRange.endOffset)
            val newText = if (newRange.isEmpty) "" else newFile.text.substring(newRange.startOffset, newRange.endOffset)
            return analyzeChangeSafetyTextBased(oldText, newText)
        }

        val oldMeaningful = oldLeaves.filterMeaningful().map { it.text }
        val newMeaningful = newLeaves.filterMeaningful().map { it.text }

        thisLogger().warn("[INLINE-DIFF-ANALYZE] oldMeaningful=${oldMeaningful.size} tokens  newMeaningful=${newMeaningful.size} tokens  newRangeEmpty=${newRange.isEmpty}")

        if (oldMeaningful != newMeaningful) {
            thisLogger().warn("[INLINE-DIFF-ANALYZE] meaningful tokens differ → newRangeEmpty=${newRange.isEmpty}  oldMeaningfulNotEmpty=${oldMeaningful.isNotEmpty()}")
            // Pure deletion: the new side is empty. Check whether the removed block is a
            // function/method with zero usages in the project index (dead code removal).
            if (newRange.isEmpty && oldMeaningful.isNotEmpty()) {
                val isDead = checkDeadCodeRemoval(oldFile, oldRange)
                thisLogger().warn("[INLINE-DIFF-ANALYZE] checkDeadCodeRemoval → $isDead")
                if (isDead) return SafeChangeType.DEAD_CODE_REMOVAL
            } else {
                thisLogger().warn("[INLINE-DIFF-ANALYZE] dead code check skipped (newRange not empty or old side has no tokens)")
            }
            thisLogger().warn("[INLINE-DIFF-ANALYZE] → UNSAFE")
            return SafeChangeType.UNSAFE
        }

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
    // Dead-code detection
    // -------------------------------------------------------------------------

    /**
     * Returns true if [oldRange] in [oldFile] covers exactly one top-level
     * function/method declaration AND that function has zero call-sites in the
     * project index.
     *
     * The [oldFile] is an in-memory dummy that is not registered in the index,
     * so [com.intellij.psi.search.searches.ReferencesSearch] cannot be used
     * directly on its elements. Instead:
     *   1. We extract the function name from the dummy PSI.
     *   2. We query [PsiSearchHelper.processAllFilesWithWord] which hits the
     *      real word index and visits only files that actually contain the name.
     *   3. For each such file we walk its PSI and check whether any leaf is a
     *      genuine call-site reference (not a declaration or import).
     * This avoids the index-registration requirement while still using the
     * project's live index for the search.
     */
    private fun checkDeadCodeRemoval(oldFile: PsiFile, oldRange: TextRange): Boolean {
        val log = thisLogger()
        log.warn("[INLINE-DIFF-DEAD] checkDeadCodeRemoval — javaPsiAvailable=$javaPsiAvailable  kotlinPsiAvailable=$kotlinPsiAvailable  jsPsiAvailable=$jsPsiAvailable")
        log.warn("[INLINE-DIFF-DEAD] range=${oldRange}  rangeText=${oldFile.text.substring(oldRange.startOffset.coerceAtMost(oldFile.textLength), oldRange.endOffset.coerceAtMost(oldFile.textLength)).take(120).replace('\n', '↵')}")

        val functions = topLevelFunctionsInRange(oldFile, oldRange)
        if (functions.isEmpty()) {
            log.warn("[INLINE-DIFF-DEAD] topLevelFunctionsInRange → empty  (no outermost functions found; returning UNSAFE)")
            return false
        }
        log.warn("[INLINE-DIFF-DEAD] topLevelFunctionsInRange → ${functions.map { "${it::class.java.simpleName}(${it.name})" }}")

        val scope  = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)

        for (function in functions) {
            val name = function.name
            if (name == null) {
                log.warn("[INLINE-DIFF-DEAD] function.name is null → returning UNSAFE")
                return false
            }

            var hasUsage = false
            helper.processAllFilesWithWord(name, scope, Processor { file ->
                log.warn("[INLINE-DIFF-DEAD] visiting file: ${file.name}")
                val found = SyntaxTraverser.psiTraverser(file)
                    .filter { leaf -> leaf.firstChild == null && leaf.text == name && isCallSiteLeaf(leaf) }
                    .any()
                log.warn("[INLINE-DIFF-DEAD]   → callSiteFound=$found")
                if (found) hasUsage = true
                !hasUsage
            }, true)

            log.warn("[INLINE-DIFF-DEAD] \"$name\" scan complete — hasUsage=$hasUsage")
            if (hasUsage) {
                log.warn("[INLINE-DIFF-DEAD] → UNSAFE (\"$name\" has usages)")
                return false
            }
        }

        log.warn("[INLINE-DIFF-DEAD] all ${functions.size} functions are unused → DEAD_CODE_REMOVAL")
        return true
    }

    /**
     * Returns all outermost [PsiMethod], [KtNamedFunction], or [JSFunction] elements
     * whose [PsiElement.textRange] is fully contained in [range].
     *
     * "Outermost" means no other function in the result set contains it —
     * this correctly handles multiple sibling functions while ignoring nested lambdas.
     */
    private fun topLevelFunctionsInRange(file: PsiFile, range: TextRange): List<PsiNamedElement> {
        val all = SyntaxTraverser.psiTraverser(file)
            .filter { (javaPsiAvailable && isJavaPsiMethod(it)) || (kotlinPsiAvailable && isKtNamedFunction(it)) || (jsPsiAvailable && isJsFunction(it)) }
            .toList()
        thisLogger().warn("[INLINE-DIFF-DEAD] topLevelFunctionInRange — all functions in file: ${all.map { "${it::class.java.simpleName}(${(it as? PsiNamedElement)?.name}, range=${it.textRange})" }}")
        val functions = all.filter { range.contains(it.textRange) }
        thisLogger().warn("[INLINE-DIFF-DEAD] topLevelFunctionInRange — functions in range $range: ${functions.map { "${it::class.java.simpleName}(${(it as? PsiNamedElement)?.name})" }}")

        val outermost = functions.filter { fn ->
            functions.none { other -> other !== fn && other.textRange.contains(fn.textRange) }
        }
        thisLogger().warn("[INLINE-DIFF-DEAD] topLevelFunctionInRange — outermost: ${outermost.map { "${it::class.java.simpleName}(${(it as? PsiNamedElement)?.name})" }}")

        return outermost.filterIsInstance<PsiNamedElement>()
    }

    /**
     * Returns true when [leaf] is a reference to a named symbol (a call site
     * or qualified access), not a declaration identifier.
     *
     * Java:   `PsiIdentifier` → parent `PsiReferenceExpression` (e.g. `foo()`)
     * Kotlin: `LeafPsiElement` → parent `KtNameReferenceExpression` (e.g. `foo()`,
     *         `::foo`, `obj.foo()`) — all count as usages.
     *
     * Declaration names (parent is `PsiMethod` or `KtNamedFunction`) are excluded
     * by this check implicitly, since their parent type is neither of the above.
     */
    private fun isCallSiteLeaf(leaf: PsiElement): Boolean {
        val parent = leaf.parent ?: return false
        return (javaPsiAvailable    && isJavaPsiReferenceExpression(parent)) ||
               (kotlinPsiAvailable && isKtNameReferenceExpression(parent)) ||
               (jsPsiAvailable     && isJsReferenceExpression(parent))
    }

    // Each helper is isolated so its `is X` bytecode instruction is only ever executed
    // after the corresponding availability guard confirms the class is on the classpath.
    private fun isJavaPsiMethod(e: PsiElement): Boolean              = e is PsiMethod
    private fun isJavaPsiReferenceExpression(e: PsiElement): Boolean = e is PsiReferenceExpression
    private fun isKtNamedFunction(e: PsiElement): Boolean            = e is KtNamedFunction
    private fun isKtNameReferenceExpression(e: PsiElement): Boolean  = e is KtNameReferenceExpression
    // JS/TS: use Class.isInstance since there are no compile-time imports for JSFunction.
    private fun isJsFunction(e: PsiElement): Boolean             = jsFunctionClass?.isInstance(e) ?: false
    private fun isJsReferenceExpression(e: PsiElement): Boolean  = jsReferenceExpressionClass?.isInstance(e) ?: false

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
