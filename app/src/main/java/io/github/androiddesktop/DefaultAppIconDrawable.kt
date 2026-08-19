package io.github.androiddesktop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.absoluteValue
import kotlin.math.min

class DefaultAppIconDrawable(
    private val label: String,
    private val glyph: String,
    private val packageName: String
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val width = b.width().toFloat().coerceAtLeast(1f)
        val height = b.height().toFloat().coerceAtLeast(1f)
        val size = min(width, height)
        val hue = (packageName.ifEmpty { label }.hashCode().absoluteValue % 360).toFloat()
        val colorA = Color.HSVToColor(floatArrayOf(hue, 0.46f, 0.92f))
        val colorB = Color.HSVToColor(floatArrayOf((hue + 42f) % 360f, 0.72f, 0.54f))
        val rect = RectF(b.left + width * 0.08f, b.top + height * 0.08f, b.right - width * 0.08f, b.bottom - height * 0.08f)
        val radius = size * 0.24f

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colorA, colorB, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        paint.color = Color.argb(72, 255, 255, 255)
        canvas.drawCircle(rect.right - size * 0.22f, rect.top + size * 0.24f, size * 0.2f, paint)
        paint.color = Color.argb(42, 0, 0, 0)
        canvas.drawRoundRect(RectF(rect.left, rect.centerY(), rect.right, rect.bottom), radius, radius, paint)

        val mark = glyph.ifEmpty { label.take(1).uppercase() }
        textPaint.textSize = size * if (mark.length > 1) 0.32f else 0.42f
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(mark.take(2), rect.centerX(), baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
