package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Status-bar widget that shows a diff icon in the IDE's bottom bar.
 *
 * - Icon is [AllIcons.Actions.Diff]       while the diff is **inactive**.
 * - Icon is [AllIcons.Diff.ApplyNotConflicts] while the diff is **active** (green tint).
 * - Clicking the icon toggles the diff for the currently focused text editor.
 *
 * The service calls [notifyUpdate] after every state change so the icon refreshes
 * without polling.
 */
class InlineDiffStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.IconPresentation {

    companion object {
        const val WIDGET_ID = "InlineDiffStatusBarWidget"
    }

    private var statusBar: StatusBar? = null

    // -------------------------------------------------------------------------
    // StatusBarWidget
    // -------------------------------------------------------------------------

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    // -------------------------------------------------------------------------
    // StatusBarWidget.IconPresentation
    // -------------------------------------------------------------------------

    override fun getIcon(): Icon {
        val active = currentEditor()?.let { InlineDiffService.getInstance(project).isActive(it) } ?: false
        return if (active) AllIcons.Diff.ApplyNotConflicts else AllIcons.Actions.Diff
    }

    override fun getTooltipText(): String {
        val active = currentEditor()?.let { InlineDiffService.getInstance(project).isActive(it) } ?: false
        return if (active) "Hide Inline Diff  [^⌥⇧D]" else "Show Inline Diff  [^⌥⇧D]"
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        val editor = currentEditor() ?: return@Consumer
        val file   = currentFile()   ?: return@Consumer
        val service = InlineDiffService.getInstance(project)
        if (service.isActive(editor)) {
            service.clearDiff(editor)
        } else {
            service.computeAndShowDiff(editor, file, project)
        }
        // Widget re-renders via the notifyUpdate() call made by the service
    }

    // -------------------------------------------------------------------------
    // Called by InlineDiffService after every state change
    // -------------------------------------------------------------------------

    fun notifyUpdate() = statusBar?.updateWidget(WIDGET_ID)

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun currentEditor() = FileEditorManager.getInstance(project).selectedTextEditor

    private fun currentFile(): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
}
