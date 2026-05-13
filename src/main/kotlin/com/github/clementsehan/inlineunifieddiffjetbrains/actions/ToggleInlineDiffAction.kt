package com.github.clementsehan.inlineunifieddiffjetbrains.actions

import com.github.clementsehan.inlineunifieddiffjetbrains.diff.InlineDiffService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * Toggle button for the inline diff view. Extends [ToggleAction] so that:
 *  - the toolbar button renders in a "pressed/highlighted" state while the diff is active
 *  - [isSelected] drives the on/off logic instead of manual state tracking in [actionPerformed]
 *
 * Registered in plugin.xml:
 *  - Keyboard shortcut: Ctrl+Alt+Shift+D (all platforms) — use Find Action (⌘⇧A) if the
 *    shortcut conflicts, or assign a custom one via Settings → Keymap → "Toggle Inline Diff".
 *  - Editor toolbar: EditorToolbarActionGroup (icon strip, top-right of each editor panel).
 *  - Right-click context menu: EditorPopupMenu.
 */
class ToggleInlineDiffAction : ToggleAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor  = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        e.presentation.isEnabledAndVisible = editor != null && project != null
        e.presentation.icon = AllIcons.Actions.Diff

        if (editor != null && project != null) {
            val active = InlineDiffService.getInstance(project).isActive(editor)
            e.presentation.text = if (active) "Hide Inline Diff" else "Show Inline Diff"
        }

        // super.update calls isSelected() and sets the selected state on the presentation
        super.update(e)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val editor  = e.getData(CommonDataKeys.EDITOR)  ?: return false
        val project = e.project                          ?: return false
        return InlineDiffService.getInstance(project).isActive(editor)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val editor      = e.getData(CommonDataKeys.EDITOR)      ?: return
        val project     = e.project                              ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val service = InlineDiffService.getInstance(project)
        if (state) {
            service.computeAndShowDiff(editor, virtualFile, project)
        } else {
            service.clearDiff(editor)
        }
    }
}
