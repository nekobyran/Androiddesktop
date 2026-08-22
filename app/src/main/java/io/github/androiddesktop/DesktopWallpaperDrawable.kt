package io.github.androiddesktop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Static desktop wallpaper with geometry/shaders cached per bounds change. */
class DesktopWallpaperDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val frame = RectF()
    private var widthPx = 1f
    private var heightPx = 1f
    private var diagonalStep = 1f
    private var backgroundShader: Shader? = null
    private var blueGlowShader: Shader? = null
    private var greenGlowShader: Shader? = null

    override fun onBoundsChange(bounds: Rect) {
        widthPx = bounds.width().toFloat().coerceAtLeast(1f)
        heightPx = bounds.height().toFloat().coerceAtLeast(1f)
        val minSide = min(widthPx, heightPx)
        diagonalStep = (minSide / 12f).coerceAtLeast(1f)
        backgroundShader = LinearGradient(
            0f,
            0f,
            widthPx,
            heightPx,
            intArrayOf(Color.rgb(8, 16, 28), Color.rgb(16, 34, 54), Color.rgb(10, 14, 24)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        blueGlowShader = RadialGradient(
            widthPx * 0.72f,
            heightPx * 0.22f,
            minSide * 0.55f,
            Color.argb(120, 89, 167, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        greenGlowShader = RadialGradient(
            widthPx * 0.18f,
            heightPx * 0.82f,
            minSide * 0.42f,
            Color.argb(95, 124, 222, 169),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        path.reset()
        path.moveTo(widthPx * 0.08f, heightPx * 0.72f)
        path.cubicTo(widthPx * 0.28f, heightPx * 0.58f, widthPx * 0.42f, heightPx * 0.92f, widthPx * 0.7f, heightPx * 0.68f)
        path.cubicTo(widthPx * 0.83f, heightPx * 0.56f, widthPx * 0.95f, heightPx * 0.62f, widthPx * 1.08f, heightPx * 0.52f)
        path.lineTo(widthPx * 1.08f, heightPx * 1.08f)
        path.lineTo(-widthPx * 0.08f, heightPx * 1.08f)
        path.close()
        frame.set(widthPx * 0.06f, heightPx * 0.08f, widthPx * 0.94f, heightPx * 0.92f)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.style = Paint.Style.FILL
        paint.shader = backgroundShader
        canvas.drawRect(b, paint)

        paint.shader = blueGlowShader
        canvas.drawRect(b, paint)

        paint.shader = greenGlowShader
        canvas.drawRect(b, paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(28, 255, 255, 255)
        var x = -widthPx
        while (x < widthPx * 2f) {
            canvas.drawLine(x, heightPx, x + widthPx * 0.55f, 0f, paint)
            x += diagonalStep
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(38, 255, 255, 255)
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(72, 157, 202, 255)
        canvas.drawRoundRect(frame, 36f, 36f, paint)
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
