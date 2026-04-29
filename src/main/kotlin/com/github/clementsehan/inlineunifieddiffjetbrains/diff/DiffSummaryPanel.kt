package com.github.clementsehan.inlineunifieddiffjetbrains.diff

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel

/**
 * Floating overlay shown at the bottom of the editor while the diff is active.
 * Displays how many chunks remain and provides "✓ Keep all" / "↩ Undo all" buttons.
 *
 * Painted entirely via [paintComponent] (no child components) so there is no
 * layout overhead and the transparency/antialiasing is under our full control.
 */
class DiffSummaryPanel(
    private val onKeepAll: () -> Unit,
    private val onUndoAll: () -> Unit,
) : JPanel() {

    companion object {
        private val PANEL_BG    = JBColor(Color(30,  30,  30,  215), Color(55,  55,  55,  215))
        private val TEXT_COLOR  = JBColor(Color(220, 220, 220, 255),  Color(200, 200, 200, 255))
        private val KEEP_BG     = JBColor(Color(40,  160,  60, 230),  Color(30,  130,  50,  230))
        private val UNDO_BG     = JBColor(Color(180,  40,  40, 230),  Color(160,  50,   50, 230))
        private val BTN_FG      = Color.WHITE

        private const val PANEL_H_PAD = 14
        private const val PANEL_V_PAD = 7
        private const val BTN_H_PAD   = 10
        private const val BTN_V_PAD   = 4
        private const val PANEL_ARC   = 18f
        private const val BTN_ARC     = 8f
        private const val GAP         = 8
        private const val FONT_SIZE   = 12f
    }

    private var count = 0
    private var keepBounds: Rectangle? = null
    private var undoBounds: Rectangle? = null
    private var hovered = 0  // 0=none 1=keep 2=undo

    init {
        isOpaque = false

        val ma = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val h = when {
                    keepBounds?.contains(e.point) == true -> 1
                    undoBounds?.contains(e.point) == true -> 2
                    else -> 0
                }
                if (h != hovered) {
                    hovered = h
                    cursor = if (h != 0) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                             else Cursor.getDefaultCursor()
                    repaint()
                }
            }
            override fun mouseExited(e: MouseEvent) {
                if (hovered != 0) { hovered = 0; cursor = Cursor.getDefaultCursor(); repaint() }
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                when {
                    keepBounds?.contains(e.point) == true -> onKeepAll()
                    undoBounds?.contains(e.point) == true -> onUndoAll()
                }
            }
        }
        addMouseListener(ma)
        addMouseMotionListener(ma)
    }

    fun updateCount(n: Int) {
        if (count != n) { count = n; repaint() }
    }

    override fun getPreferredSize(): Dimension {
        val fm    = getFontMetrics(baseFont())
        val btnFm = getFontMetrics(boldFont())
        val textW = fm.stringWidth("$count remaining")
        val btnH  = btnFm.height + BTN_V_PAD * 2
        val keepW = btnFm.stringWidth("✓ Keep all") + BTN_H_PAD * 2
        val undoW = btnFm.stringWidth("↩ Undo all") + BTN_H_PAD * 2
        val w = PANEL_H_PAD * 2 + textW + GAP + keepW + GAP + undoW
        val h = PANEL_V_PAD * 2 + maxOf(fm.height, btnH)
        return Dimension(w, h)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val w = width; val h = height

            g2.color = PANEL_BG
            g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), PANEL_ARC, PANEL_ARC))

            val plainFont = baseFont(); val boldFont = boldFont()
            val fm        = g2.getFontMetrics(plainFont)
            val btnFm     = g2.getFontMetrics(boldFont)
            val btnH      = btnFm.height + BTN_V_PAD * 2
            val midY      = h / 2

            // "N remaining" label
            val label  = "$count remaining"
            val labelW = fm.stringWidth(label)
            g2.font  = plainFont
            g2.color = TEXT_COLOR
            g2.drawString(label, PANEL_H_PAD, midY + fm.ascent / 2 - 1)

            // ✓ Keep all
            val keepText = "✓ Keep all"
            val keepW    = btnFm.stringWidth(keepText) + BTN_H_PAD * 2
            val keepX    = PANEL_H_PAD + labelW + GAP
            val keepY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 1) 1.0f else 0.88f)
            g2.color = KEEP_BG
            g2.fill(RoundRectangle2D.Float(keepX.toFloat(), keepY.toFloat(), keepW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(keepText, keepX + BTN_H_PAD, keepY + BTN_V_PAD + btnFm.ascent)
            keepBounds = Rectangle(keepX, keepY, keepW, btnH)

            // ↩ Undo all
            val undoText = "↩ Undo all"
            val undoW    = btnFm.stringWidth(undoText) + BTN_H_PAD * 2
            val undoX    = keepX + keepW + GAP
            val undoY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 2) 1.0f else 0.88f)
            g2.color = UNDO_BG
            g2.fill(RoundRectangle2D.Float(undoX.toFloat(), undoY.toFloat(), undoW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(undoText, undoX + BTN_H_PAD, undoY + BTN_V_PAD + btnFm.ascent)
            undoBounds = Rectangle(undoX, undoY, undoW, btnH)
        } finally {
            g2.dispose()
        }
    }

    private fun baseFont() = font.deriveFont(Font.PLAIN, FONT_SIZE)
    private fun boldFont() = font.deriveFont(Font.BOLD,  FONT_SIZE)
    private fun alpha(a: Float): AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a)
}
