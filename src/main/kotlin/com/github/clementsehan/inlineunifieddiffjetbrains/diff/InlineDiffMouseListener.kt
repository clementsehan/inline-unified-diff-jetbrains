package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import java.awt.Rectangle

/**
 * Listens for single left-clicks in the editor and delegates to whichever
 * [InlineDiffRenderer] button the click lands on.
 *
 * One instance is registered per editor (via [InlineDiffService.EditorDiffState]).
 * It is automatically unregistered when the diff state is disposed.
 *
 * Hit-testing strategy:
 *   [InlineDiffRenderer.keepButtonBounds] / [InlineDiffRenderer.undoButtonBounds]
 *   are stored *relative to the inlay origin*.
 *   [Inlay.getBounds] returns the inlay's rectangle in editor-component coordinates.
 *   Adding the two gives the absolute button rect, compared to the mouse-event point.
 */
class InlineDiffMouseListener(
    private val chunksProvider: () -> List<DiffChunk>,
) : EditorMouseListener {

    override fun mouseClicked(event: EditorMouseEvent) {
        if (event.mouseEvent.clickCount != 1) return
        val clickPoint = event.mouseEvent.point

        for (chunk in chunksProvider()) {
            for (inlay in chunk.deletedInlays) {
                val renderer    = inlay.renderer as? InlineDiffRenderer ?: continue
                val inlayBounds = inlay.bounds ?: continue

                val keepBtn = renderer.keepButtonBounds
                if (keepBtn != null) {
                    val abs = Rectangle(
                        inlayBounds.x + keepBtn.x, inlayBounds.y + keepBtn.y,
                        keepBtn.width, keepBtn.height,
                    )
                    if (abs.contains(clickPoint)) {
                        event.consume()
                        renderer.triggerKeep()
                        return
                    }
                }

                val undoBtn = renderer.undoButtonBounds
                if (undoBtn != null) {
                    val abs = Rectangle(
                        inlayBounds.x + undoBtn.x, inlayBounds.y + undoBtn.y,
                        undoBtn.width, undoBtn.height,
                    )
                    if (abs.contains(clickPoint)) {
                        event.consume()
                        renderer.triggerUndo()
                        return
                    }
                }
            }
        }
    }
}
