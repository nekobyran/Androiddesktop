package io.github.androiddesktop

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class VrSpatialPreviewView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var workspaceState = NiriWorkspaceState(emptyList(), -1, 0)
    private var tick = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            tick = it.animatedFraction
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    fun setWorkspace(state: NiriWorkspaceState) {
        workspaceState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = Material3Tokens.Background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 34f, 34f, paint)
        drawEye(canvas, RectF(18f, 18f, w / 2f - 8f, h - 18f), -1f)
        drawEye(canvas, RectF(w / 2f + 8f, 18f, w - 18f, h - 18f), 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(80, 255, 255, 255)
        canvas.drawLine(w / 2f, 24f, w / 2f, h - 24f, paint)
    }

    private fun drawEye(canvas: Canvas, area: RectF, stereoOffset: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Material3Tokens.Surface
        canvas.drawRoundRect(area, 28f, 28f, paint)

        val horizonY = area.top + area.height() * 0.58f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(58, 157, 202, 255)
        var line = 0
        while (line < 8) {
            val y = horizonY + line * area.height() * 0.065f
            canvas.drawLine(area.left + 18f, y, area.right - 18f, y, paint)
            line++
        }
        var col = 0
        while (col < 8) {
            val x = area.left + area.width() * (0.12f + col * 0.11f)
            canvas.drawLine(x, horizonY, area.centerX() + (x - area.centerX()) * 1.6f, area.bottom - 18f, paint)
            col++
        }

        val floatY = sin(tick * Math.PI * 2.0).toFloat() * 10f
        val visible = workspaceState.columns.takeLast(4)
        visible.forEachIndexed { index, column ->
            val depth = index.toFloat() / 4f
            val panelW = area.width() * (0.34f - depth * 0.035f)
            val panelH = area.height() * (0.42f - depth * 0.025f)
            val centerX = area.centerX() + (index - visible.size / 2f + 0.5f) * area.width() * 0.19f + stereoOffset * (8f + depth * 8f)
            val centerY = area.top + area.height() * (0.42f + depth * 0.05f) + floatY * (1f - depth * 0.35f)
            val rect = RectF(centerX - panelW / 2f, centerY - panelH / 2f, centerX + panelW / 2f, centerY + panelH / 2f)
            paint.style = Paint.Style.FILL
            paint.color = if (column.focused) Material3Tokens.PrimaryContainer else Material3Tokens.SurfaceContainerHigh
            paint.setShadowLayer(if (column.focused) 16f else 6f, 0f, 6f, Color.argb(90, 0, 0, 0))
            canvas.drawRoundRect(rect, 20f, 20f, paint)
            paint.clearShadowLayer()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (column.focused) 4f else 2f
            paint.color = if (column.focused) Material3Tokens.Primary else Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(rect, 20f, 20f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Material3Tokens.OnSurface
            paint.textSize = 24f
            paint.isFakeBoldText = true
            canvas.drawText(column.app.glyph, rect.left + 18f, rect.top + 36f, paint)
            paint.textSize = 13f
            paint.isFakeBoldText = false
            canvas.drawText(column.app.label, rect.left + 52f, rect.top + 34f, paint)
            paint.textSize = 10f
            paint.color = Material3Tokens.OnSurfaceVariant
            canvas.drawText("virtual display panel", rect.left + 18f, rect.bottom - 20f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Material3Tokens.Warning
        val cx = area.centerX()
        val cy = area.centerY()
        canvas.drawCircle(cx, cy, 16f, paint)
        canvas.drawLine(cx - 24f, cy, cx - 8f, cy, paint)
        canvas.drawLine(cx + 8f, cy, cx + 24f, cy, paint)
        canvas.drawLine(cx, cy - 24f, cx, cy - 8f, paint)
        canvas.drawLine(cx, cy + 8f, cx, cy + 24f, paint)
    }
}
