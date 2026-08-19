package io.github.androiddesktop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

class DesktopWallpaperDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val width = b.width().toFloat().coerceAtLeast(1f)
        val height = b.height().toFloat().coerceAtLeast(1f)

        paint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            intArrayOf(Color.rgb(8, 16, 28), Color.rgb(16, 34, 54), Color.rgb(10, 14, 24)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, paint)

        paint.shader = RadialGradient(
            width * 0.72f,
            height * 0.22f,
            min(width, height) * 0.55f,
            Color.argb(120, 89, 167, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, paint)

        paint.shader = RadialGradient(
            width * 0.18f,
            height * 0.82f,
            min(width, height) * 0.42f,
            Color.argb(95, 124, 222, 169),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(b, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(28, 255, 255, 255)
        val step = min(width, height) / 12f
        var x = -width
        while (x < width * 2f) {
            canvas.drawLine(x, height, x + width * 0.55f, 0f, paint)
            x += step
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(38, 255, 255, 255)
        path.reset()
        path.moveTo(width * 0.08f, height * 0.72f)
        path.cubicTo(width * 0.28f, height * 0.58f, width * 0.42f, height * 0.92f, width * 0.7f, height * 0.68f)
        path.cubicTo(width * 0.83f, height * 0.56f, width * 0.95f, height * 0.62f, width * 1.08f, height * 0.52f)
        path.lineTo(width * 1.08f, height * 1.08f)
        path.lineTo(-width * 0.08f, height * 1.08f)
        path.close()
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(72, 157, 202, 255)
        canvas.drawRoundRect(RectF(width * 0.06f, height * 0.08f, width * 0.94f, height * 0.92f), 36f, 36f, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
