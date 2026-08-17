package io.github.androiddesktop

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.TextView

object Material3Tokens {
    const val Background = 0xFF101418.toInt()
    const val Surface = 0xFF191C20.toInt()
    const val SurfaceContainer = 0xFF1F2329.toInt()
    const val SurfaceContainerHigh = 0xFF2A2F36.toInt()
    const val Primary = 0xFF9DCAFF.toInt()
    const val PrimaryContainer = 0xFF174A75.toInt()
    const val SecondaryContainer = 0xFF39485A.toInt()
    const val TertiaryContainer = 0xFF553F5D.toInt()
    const val Outline = 0xFF8A929C.toInt()
    const val OnSurface = 0xFFE2E2E9.toInt()
    const val OnSurfaceVariant = 0xFFC4C7CF.toInt()
    const val Warning = 0xFFFFDAD6.toInt()

    fun surface(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp.toFloat()
            if (strokeColor != null) setStroke(strokeDp, strokeColor)
        }

    fun ripple(baseColor: Int, radiusDp: Int): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(Color.argb(48, Color.red(Primary), Color.green(Primary), Color.blue(Primary))),
        surface(baseColor, radiusDp),
        null
    )

    fun TextView.titleStyle(sizeSp: Float = 22f) {
        setTextColor(OnSurface)
        textSize = sizeSp
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
    }

    fun TextView.bodyStyle(sizeSp: Float = 13f) {
        setTextColor(OnSurfaceVariant)
        textSize = sizeSp
        includeFontPadding = true
    }

    fun View.card(radiusDp: Int = 24, color: Int = SurfaceContainer) {
        background = surface(color, radiusDp, Color.argb(48, 255, 255, 255), 1)
        elevation = 6f
    }
}
