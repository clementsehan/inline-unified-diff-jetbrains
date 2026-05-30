package com.github.clementsehan.inlineunifieddiffjetbrains.actions

import com.github.clementsehan.inlineunifieddiffjetbrains.diff.InlineDiffService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

class NavigateNextDiffChunkAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor  = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabledAndVisible =
            editor != null && project != null && InlineDiffService.getInstance(project).isActive(editor)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project                         ?: return
        InlineDiffService.getInstance(project).navigateNextChunk(editor)
    }
}

class NavigatePrevDiffChunkAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor  = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        e.presentation.isEnabledAndVisible =
            editor != null && project != null && InlineDiffService.getInstance(project).isActive(editor)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor  = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project                         ?: return
        InlineDiffService.getInstance(project).navigatePreviousChunk(editor)
    }
}
