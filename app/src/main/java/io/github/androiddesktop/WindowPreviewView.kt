package io.github.androiddesktop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WindowPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val displayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 240, 248) }
    private val displayStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(66, 96, 150)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(51, 103, 214) }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 150, 230) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 43, 66)
        textSize = dp(11f)
    }
    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(116, 127, 148)
        textSize = dp(9.5f)
    }
    private val windowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 64, 145)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }

    private var displayCount = 1
    private var hasMouse = false
    private var hasKeyboard = false
    private var huaweiFamily = false
    private var freeform = false
        private var launchPulse = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        UiMotion.runEntrance(this)
    }

    fun update(snapshot: DesktopSnapshot) {
        displayCount = snapshot.displayCount.coerceAtLeast(1)
        hasMouse = snapshot.hasMouse
        hasKeyboard = snapshot.hasKeyboard
        huaweiFamily = snapshot.isHuaweiFamily
        freeform = snapshot.hasFreeformFeature || snapshot.enableFreeformSupport == "1" || snapshot.freeformWindowManagement == "1"
        invalidate()
    }

        fun playRefresh() {
        UiMotion.animateFloat(this, 0f, 1f, MotionTokens.StateMs) {
            launchPulse = it * 0.35f
            invalidate()
        }
    }

    fun playLaunch() {
        UiMotion.animateFloat(this, 0f, 1f, MotionTokens.ContentSwitchMs) {
            launchPulse = sin(it * Math.PI).toFloat().coerceAtLeast(0f)
            invalidate()
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = dp(12f)
        val display = RectF(pad, pad, w - pad, h - pad)
        val radius = dp(18f)
        canvas.drawRoundRect(display, radius, radius, displayPaint)
        canvas.drawRoundRect(display, radius, radius, displayStrokePaint)

        val glowInset = dp(7f) - launchPulse * dp(4f)
        val glow = RectF(display).apply { inset(glowInset, glowInset) }
        accentPaint.alpha = (36 + launchPulse * 70).toInt().coerceIn(36, 120)
        canvas.drawRoundRect(glow, radius, radius, accentPaint)
        accentPaint.alpha = 255

        val title = if (huaweiFamily) "Huawei desktop probe" else "Android desktop probe"
        canvas.drawText(title, display.left + dp(16f), display.top + dp(25f), textPaint)
        canvas.drawText("displays=$displayCount  mouse=$hasMouse  keyboard=$hasKeyboard", display.left + dp(16f), display.top + dp(42f), mutedPaint)

                val baseLeft = display.left + dp(20f)
        val baseTop = display.top + dp(58f)
        // Static preview geometry avoids an always-on repaint loop on the desktop.
        val floatOffset = 0f
        drawWindow(canvas, RectF(baseLeft, baseTop + floatOffset, baseLeft + w * 0.46f, baseTop + h * 0.34f + floatOffset), "Control", accentPaint)
        drawWindow(canvas, RectF(display.right - w * 0.47f, baseTop + dp(12f) - floatOffset, display.right - dp(22f), baseTop + h * 0.40f - floatOffset), "Target app", secondaryPaint)


        val chipTop = display.bottom - dp(30f)
        val chipText = if (freeform) "freeform ready" else "freeform request mode"
        drawChip(canvas, display.left + dp(16f), chipTop, chipText)
        drawChip(canvas, display.left + dp(142f), chipTop, "launch bounds")
    }

    private fun drawWindow(canvas: Canvas, rect: RectF, label: String, paint: Paint) {
        val radius = dp(12f)
        paint.alpha = 235
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.alpha = 255
        canvas.drawRoundRect(rect, radius, radius, windowStrokePaint)
        val header = RectF(rect.left, rect.top, rect.right, rect.top + dp(20f))
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(48, 255, 255, 255) }
        canvas.drawRoundRect(header, radius, radius, headerPaint)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(10f)
                        isFakeBoldText = true
        }
        canvas.drawText(label, rect.left + dp(12f), rect.top + dp(14f), labelPaint)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 255, 255, 255)
            strokeWidth = dp(2f)
        }
        repeat(3) { index ->
            val y = rect.top + dp(34f + index * 13f)
            canvas.drawLine(rect.left + dp(14f), y, rect.right - dp(18f + index * 12f), y, linePaint)
        }
    }

    private fun drawChip(canvas: Canvas, left: Float, top: Float, label: String) {
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 51, 103, 214) }
        val rect = RectF(left, top, left + dp(112f), top + dp(20f))
        canvas.drawRoundRect(rect, dp(10f), dp(10f), chipPaint)
        canvas.drawText(label, left + dp(10f), top + dp(14f), mutedPaint)
    }

    
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
