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
    private val onKeepAll:      () -> Unit,
    private val onUndoAll:      () -> Unit,
    private val onNavigatePrev: () -> Unit,
    private val onNavigateNext: () -> Unit,
    private val onKeepSafe:     () -> Unit,
    private val onRefresh:      () -> Unit,
) : JPanel() {

    companion object {
        private val PANEL_BG    = JBColor(Color(30,  30,  30,  215), Color(55,  55,  55,  215))
        private val TEXT_COLOR  = JBColor(Color(220, 220, 220, 255),  Color(200, 200, 200, 255))
        private val KEEP_BG     = JBColor(Color(40,  160,  60, 230),  Color(30,  130,  50,  230))
        private val UNDO_BG     = JBColor(Color(180,  40,  40, 230),  Color(160,  50,   50, 230))
        private val NAV_BG      = JBColor(Color(70,   70,  85, 220),  Color(90,   90, 105, 220))
        private val SAFE_BG     = JBColor(Color(30,  120, 185, 230),  Color(25,  100, 160, 230))
        private val BTN_FG      = JBColor(Color.WHITE, Color.WHITE)

        private const val PANEL_H_PAD = 14
        private const val PANEL_V_PAD = 7
        private const val BTN_H_PAD   = 10
        private const val BTN_V_PAD   = 4
        private const val PANEL_ARC   = 18f
        private const val BTN_ARC     = 8f
        private const val GAP         = 8
        private const val FONT_SIZE   = 12f
    }

    private var count   = 0
    private var hasSafe = false
    private var keepBounds: Rectangle? = null
    private var undoBounds: Rectangle? = null
    private var prevBounds:    Rectangle? = null
    private var nextBounds:    Rectangle? = null
    private var safeBounds:    Rectangle? = null
    private var refreshBounds: Rectangle? = null
    // 0=none 1=keep 2=undo 3=prev 4=next 5=safe 6=refresh
    private var hovered = 0

    init {
        isOpaque = false

        val ma = object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val h = when {
                    keepBounds?.contains(e.point)    == true -> 1
                    undoBounds?.contains(e.point)    == true -> 2
                    prevBounds?.contains(e.point)    == true -> 3
                    nextBounds?.contains(e.point)    == true -> 4
                    safeBounds?.contains(e.point)    == true -> 5
                    refreshBounds?.contains(e.point) == true -> 6
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
                    keepBounds?.contains(e.point)    == true -> onKeepAll()
                    undoBounds?.contains(e.point)    == true -> onUndoAll()
                    prevBounds?.contains(e.point)    == true -> onNavigatePrev()
                    nextBounds?.contains(e.point)    == true -> onNavigateNext()
                    safeBounds?.contains(e.point)    == true -> onKeepSafe()
                    refreshBounds?.contains(e.point) == true -> onRefresh()
                }
            }
        }
        addMouseListener(ma)
        addMouseMotionListener(ma)
    }

    fun updateCount(total: Int, safeCount: Int) {
        val newHasSafe = safeCount > 0
        if (count != total || hasSafe != newHasSafe) {
            count   = total
            hasSafe = newHasSafe
            repaint()
        }
    }

    override fun getPreferredSize(): Dimension {
        val fm    = getFontMetrics(baseFont())
        val btnFm = getFontMetrics(boldFont())
        val textW = fm.stringWidth("$count remaining")
        val btnH  = btnFm.height + BTN_V_PAD * 2
        val prevW    = btnFm.stringWidth("▲") + BTN_H_PAD * 2
        val nextW    = btnFm.stringWidth("▼") + BTN_H_PAD * 2
        val safeW    = if (hasSafe) btnFm.stringWidth("✓ Accept safe") + BTN_H_PAD * 2 + GAP else 0
        val keepW    = btnFm.stringWidth("✓ Keep all") + BTN_H_PAD * 2
        val undoW    = btnFm.stringWidth("↩ Undo all") + BTN_H_PAD * 2
        val refreshW = btnFm.stringWidth("↻ Refresh") + BTN_H_PAD * 2
        val w = PANEL_H_PAD * 2 + textW + GAP + prevW + GAP + nextW + GAP + safeW + keepW + GAP + undoW + GAP + refreshW
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

            var curX = PANEL_H_PAD + labelW + GAP

            // ▲ prev chunk
            val prevText = "▲"
            val prevW    = btnFm.stringWidth(prevText) + BTN_H_PAD * 2
            val prevY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 3) 1.0f else 0.88f)
            g2.color = NAV_BG
            g2.fill(RoundRectangle2D.Float(curX.toFloat(), prevY.toFloat(), prevW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(prevText, curX + BTN_H_PAD, prevY + BTN_V_PAD + btnFm.ascent)
            prevBounds = Rectangle(curX, prevY, prevW, btnH)
            curX += prevW + GAP

            // ▼ next chunk
            val nextText = "▼"
            val nextW    = btnFm.stringWidth(nextText) + BTN_H_PAD * 2
            val nextY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 4) 1.0f else 0.88f)
            g2.color = NAV_BG
            g2.fill(RoundRectangle2D.Float(curX.toFloat(), nextY.toFloat(), nextW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(nextText, curX + BTN_H_PAD, nextY + BTN_V_PAD + btnFm.ascent)
            nextBounds = Rectangle(curX, nextY, nextW, btnH)
            curX += nextW + GAP

            // ✓ Accept safe (only when safe chunks exist)
            if (hasSafe) {
                val safeText = "✓ Accept safe"
                val safeW    = btnFm.stringWidth(safeText) + BTN_H_PAD * 2
                val safeY    = midY - btnH / 2
                g2.composite = alpha(if (hovered == 5) 1.0f else 0.88f)
                g2.color = SAFE_BG
                g2.fill(RoundRectangle2D.Float(curX.toFloat(), safeY.toFloat(), safeW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
                g2.composite = AlphaComposite.SrcOver
                g2.color = BTN_FG; g2.font = boldFont
                g2.drawString(safeText, curX + BTN_H_PAD, safeY + BTN_V_PAD + btnFm.ascent)
                safeBounds = Rectangle(curX, safeY, safeW, btnH)
                curX += safeW + GAP
            } else {
                safeBounds = null
            }

            // ✓ Keep all
            val keepText = "✓ Keep all"
            val keepW    = btnFm.stringWidth(keepText) + BTN_H_PAD * 2
            val keepY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 1) 1.0f else 0.88f)
            g2.color = KEEP_BG
            g2.fill(RoundRectangle2D.Float(curX.toFloat(), keepY.toFloat(), keepW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(keepText, curX + BTN_H_PAD, keepY + BTN_V_PAD + btnFm.ascent)
            keepBounds = Rectangle(curX, keepY, keepW, btnH)
            curX += keepW + GAP

            // ↩ Undo all
            val undoText = "↩ Undo all"
            val undoW    = btnFm.stringWidth(undoText) + BTN_H_PAD * 2
            val undoY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 2) 1.0f else 0.88f)
            g2.color = UNDO_BG
            g2.fill(RoundRectangle2D.Float(curX.toFloat(), undoY.toFloat(), undoW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(undoText, curX + BTN_H_PAD, undoY + BTN_V_PAD + btnFm.ascent)
            undoBounds = Rectangle(curX, undoY, undoW, btnH)
            curX += undoW + GAP

            // ↻ Refresh
            val refreshText = "↻ Refresh"
            val refreshW    = btnFm.stringWidth(refreshText) + BTN_H_PAD * 2
            val refreshY    = midY - btnH / 2
            g2.composite = alpha(if (hovered == 6) 1.0f else 0.88f)
            g2.color = NAV_BG
            g2.fill(RoundRectangle2D.Float(curX.toFloat(), refreshY.toFloat(), refreshW.toFloat(), btnH.toFloat(), BTN_ARC, BTN_ARC))
            g2.composite = AlphaComposite.SrcOver
            g2.color = BTN_FG; g2.font = boldFont
            g2.drawString(refreshText, curX + BTN_H_PAD, refreshY + BTN_V_PAD + btnFm.ascent)
            refreshBounds = Rectangle(curX, refreshY, refreshW, btnH)
        } finally {
            g2.dispose()
        }
    }

    private fun baseFont() = font.deriveFont(Font.PLAIN, FONT_SIZE)
    private fun boldFont() = font.deriveFont(Font.BOLD,  FONT_SIZE)
    private fun alpha(a: Float): AlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a)
}
