package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D

/**
 * Renders deleted HEAD lines as a red "ghost" block element between editor lines,
 * with "✓ Keep" and "↩ Undo" pill buttons at the bottom.
 *
 * When [deletedLines] is empty (ADDED chunks) only the button row is rendered,
 * with no background — the pills float directly below the green-highlighted lines.
 *
 * Button bounds are stored *relative to the inlay's top-left corner* after every
 * [paint] call so that [InlineDiffMouseListener] can map clicks to actions.
 *
 * @param deletedLines  Lines from HEAD that were removed; empty for ADDED chunks.
 * @param onKeep        Called when the user clicks "✓ Keep".
 * @param onUndo        Called when the user clicks "↩ Undo".
 */
class InlineDiffRenderer(
    private val deletedLines: List<String>,
    private val onKeep: () -> Unit,
    private val onUndo: () -> Unit,
) : EditorCustomElementRenderer {

    companion object {
        private val BG_COLOR     = JBColor(Color(255, 80,  80,  45), Color(180, 60,  60,  80))
        private val STRIPE_COLOR = JBColor(Color(200, 40,  40, 220), Color(220, 60,  60, 220))
        private val KEEP_BTN_BG  = JBColor(Color( 40, 160,  60, 210), Color( 30, 130,  50, 210))
        private val UNDO_BTN_BG  = JBColor(Color(180,  40,  40, 210), Color(160,  50,  50, 210))
        private val BUTTON_FG    = JBColor(Color.WHITE, Color(255, 220, 220))

        private const val STRIPE_W        = 4
        private const val TEXT_LEFT_PAD   = 10
        private const val LINE_GAP        = 2   // extra px between ghost lines
        private const val BLOCK_V_PAD     = 3   // top/bottom padding for the whole block
        private const val BUTTON_ROW_GAP  = 4   // gap between last deleted line and button row
        private const val BTN_SPACING     = 6   // horizontal gap between Keep and Undo
        private const val BTN_LEFT_MARGIN = 8   // left margin for button row (after stripe)
        private const val BTN_H_PADDING   = 10
        private const val BTN_V_PADDING   = 3
        private const val BTN_ARC         = 6
    }

    var keepButtonBounds: Rectangle? = null
        private set
    var undoButtonBounds: Rectangle? = null
        private set

    fun triggerKeep() = onKeep()
    fun triggerUndo() = onUndo()

    // -------------------------------------------------------------------------
    // EditorCustomElementRenderer
    // -------------------------------------------------------------------------

    override fun calcWidthInPixels(inlay: Inlay<*>): Int =
        inlay.editor.component.width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val lh = inlay.editor.lineHeight
        // linesH is the height occupied by the deleted-line rows (0 for button-only mode)
        val linesH = if (deletedLines.isEmpty()) 0
                     else deletedLines.size * (lh + LINE_GAP) - LINE_GAP + BUTTON_ROW_GAP
        return BLOCK_V_PAD * 2 + linesH + lh  // lh reserved for the button row
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val editor    = inlay.editor
            val lh        = editor.lineHeight
            val scheme    = editor.colorsScheme
            val font      = scheme.getFont(EditorFontType.PLAIN)
            val textColor = scheme.defaultForeground
            val metrics   = g2.getFontMetrics(font)
            val totalH    = calcHeightInPixels(inlay)

            if (deletedLines.isNotEmpty()) {
                // Full-width background and left stripe for the deleted-content area
                g2.color = BG_COLOR
                g2.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, totalH)

                g2.color = STRIPE_COLOR
                g2.fillRect(targetRegion.x, targetRegion.y, STRIPE_W, totalH)

                // Each deleted line prefixed with "−"
                g2.font = font
                deletedLines.forEachIndexed { i, line ->
                    val lineY    = targetRegion.y + BLOCK_V_PAD + i * (lh + LINE_GAP)
                    val baseline = lineY + metrics.ascent
                    g2.color = STRIPE_COLOR
                    g2.drawString("−", targetRegion.x + 1, baseline)
                    g2.color = textColor
                    g2.drawString(line, targetRegion.x + STRIPE_W + TEXT_LEFT_PAD, baseline)
                }
            }

            // ── Button row ────────────────────────────────────────────────────
            val linesAreaH = if (deletedLines.isEmpty()) 0
                             else deletedLines.size * (lh + LINE_GAP) - LINE_GAP + BUTTON_ROW_GAP
            val btnRowY = targetRegion.y + BLOCK_V_PAD + linesAreaH

            val btnFont    = font.deriveFont(Font.BOLD)
            val btnMetrics = g2.getFontMetrics(btnFont)
            val btnH       = btnMetrics.height + BTN_V_PADDING * 2

            val keepText = "✓ Keep"
            val undoText = "↩ Undo"
            val keepW    = btnMetrics.stringWidth(keepText) + BTN_H_PADDING * 2
            val undoW    = btnMetrics.stringWidth(undoText) + BTN_H_PADDING * 2

            val btnX0 = targetRegion.x + STRIPE_W + BTN_LEFT_MARGIN
            val btnY  = btnRowY + (lh - btnH) / 2
            val keepX = btnX0
            val undoX = keepX + keepW + BTN_SPACING

            g2.color = KEEP_BTN_BG
            g2.fill(RoundRectangle2D.Float(keepX.toFloat(), btnY.toFloat(), keepW.toFloat(), btnH.toFloat(), BTN_ARC.toFloat(), BTN_ARC.toFloat()))
            g2.color = BUTTON_FG
            g2.font  = btnFont
            g2.drawString(keepText, keepX + BTN_H_PADDING, btnY + BTN_V_PADDING + btnMetrics.ascent)

            g2.color = UNDO_BTN_BG
            g2.fill(RoundRectangle2D.Float(undoX.toFloat(), btnY.toFloat(), undoW.toFloat(), btnH.toFloat(), BTN_ARC.toFloat(), BTN_ARC.toFloat()))
            g2.color = BUTTON_FG
            g2.drawString(undoText, undoX + BTN_H_PADDING, btnY + BTN_V_PADDING + btnMetrics.ascent)

            // Store bounds relative to targetRegion so InlineDiffMouseListener can hit-test
            keepButtonBounds = Rectangle(keepX - targetRegion.x, btnY - targetRegion.y, keepW, btnH)
            undoButtonBounds = Rectangle(undoX - targetRegion.x, btnY - targetRegion.y, undoW, btnH)
        } finally {
            g2.dispose()
        }
    }
}
