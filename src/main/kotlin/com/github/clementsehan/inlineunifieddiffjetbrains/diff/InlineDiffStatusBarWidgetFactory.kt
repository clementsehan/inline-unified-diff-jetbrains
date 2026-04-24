package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Registers [InlineDiffStatusBarWidget] as a standard IDE status-bar widget.
 * Works in all JetBrains IDEs (IntelliJ IDEA, PhpStorm, WebStorm, etc.).
 *
 * The widget is enabled by default and can be toggled off per-IDE via
 * View → Appearance → Status Bar Widgets → "Inline Diff".
 */
class InlineDiffStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = InlineDiffStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Inline Diff"

    override fun isAvailable(project: Project): Boolean = true

    override fun isEnabledByDefault(): Boolean = true

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        InlineDiffStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}
