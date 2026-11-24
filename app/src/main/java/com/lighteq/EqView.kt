package com.lighteq

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/** Кастомный вид: вертикальные ползунки полос + плавная кривая АЧХ. */
class EqView(ctx: Context) : View(ctx) {
    var minL = -1500
    var maxL = 1500
    var levels = IntArray(0)
    var labels: List<String> = emptyList()
    var onChange: ((Int, Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val aa = Paint.ANTI_ALIAS_FLAG
    private val track = Paint(aa).apply { color = 0xFF2A2D3A.toInt(); strokeWidth = 4 * d; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(aa).apply { color = 0xFF7C5CFF.toInt(); strokeWidth = 4 * d; strokeCap = Paint.Cap.ROUND }
    private val zero = Paint(aa).apply { color = 0xFF3A3E50.toInt(); strokeWidth = 1 * d }
    private val ring = Paint(aa).apply { color = 0xFF7C5CFF.toInt() }
    private val dot = Paint(aa).apply { color = 0xFFFFFFFF.toInt() }
    private val curve = Paint(aa).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(aa).apply { color = 0xFF8D90A3.toInt(); textSize = 11 * d; textAlign = Paint.Align.CENTER }
    private val padH = 8 * d
    private val top get() = 26 * d
    private val bottom get() = height - 28 * d
    private var active = -1

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        curve.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, 0xFF7C5CFF.toInt(), 0xFF00D1B2.toInt(), Shader.TileMode.CLAMP)
    }

    private fun colX(i: Int): Float = padH + (width - 2 * padH) / levels.size * (i + 0.5f)
    private fun yFor(l: Int): Float = top + (maxL - l).toFloat() / (maxL - minL) * (bottom - top)

    override fun onDraw(c: Canvas) {
        if (levels.isEmpty()) return
        val y0 = yFor(0)
        c.drawLine(padH, y0, width - padH, y0, zero)
        val path = Path()
        for (i in levels.indices) {
            val x = colX(i)
            val y = yFor(levels[i])
            c.drawLine(x, top, x, bottom, track)
            c.drawLine(x, y0, x, y, fill)
            if (i == 0) path.moveTo(x, y) else {
                val px = colX(i - 1)
                val cx = (px + x) / 2
                path.cubicTo(cx, yFor(levels[i - 1]), cx, y, x, y)
            }
        }
        c.drawPath(path, curve)
        for (i in levels.indices) {
            val x = colX(i)
            val y = yFor(levels[i])
            c.drawCircle(x, y, 12 * d, ring)
            c.drawCircle(x, y, 8 * d, dot)
            val db = levels[i] / 100f
            c.drawText(if (db > 0) "+" + db.roundToInt() else db.roundToInt().toString(), x, top - 10 * d, txt)
            c.drawText(labels.getOrElse(i) { "" }, x, height - 8 * d, txt)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (levels.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                var best = 0
                for (i in levels.indices) if (abs(colX(i) - e.x) < abs(colX(best) - e.x)) best = i
                active = best
                update(e.y)
            }
            MotionEvent.ACTION_MOVE -> if (active >= 0) update(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> active = -1
        }
        return true
    }

    private fun update(y: Float) {
        val f = ((y - top) / (bottom - top)).coerceIn(0f, 1f)
        val raw = maxL - f * (maxL - minL)
        val l = ((raw / 50f).roundToInt() * 50).coerceIn(minL, maxL)
        if (l != levels[active]) {
            levels[active] = l
            invalidate()
            onChange?.invoke(active, l)
        }
    }
}
