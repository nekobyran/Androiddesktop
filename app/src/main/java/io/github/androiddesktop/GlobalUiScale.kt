package io.github.androiddesktop

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

/**
 * App-local logical density controller.
 *
 * Android phones commonly expose a high system density so a 1920x1080 panel
 * becomes only ~700-900dp wide. Androiddesktop is a desktop shell, so it needs
 * an independent logical scale. We override only this app's Activity context;
 * the device/system density is never modified.
 */
object GlobalUiScale {
    const val MinPercent = 50
    const val MaxPercent = 125
    const val StepPercent = 5
    const val DesktopPresetPercent = 60
    const val SystemPercent = 100
        const val DefaultPercent = 65


    private const val PrefsName = "androiddesktop_ui"
    private const val ScaleKey = "global_scale_percent"

    fun wrap(base: Context): Context {
        val percent = loadOrInitializePercent(base)
        if (percent == SystemPercent) return base

        val sourceDpi = base.resources.configuration.densityDpi
            .takeIf { it > 0 }
            ?: base.resources.displayMetrics.densityDpi
        val targetDpi = (sourceDpi * (percent / 100f))
            .roundToInt()
            .coerceIn(120, 640)
                val source = base.resources.configuration
        val override = Configuration(source).apply {
            densityDpi = targetDpi
            if (source.screenWidthDp > 0) screenWidthDp = scaleLogicalDp(source.screenWidthDp, percent)
            if (source.screenHeightDp > 0) screenHeightDp = scaleLogicalDp(source.screenHeightDp, percent)
            if (source.smallestScreenWidthDp > 0) smallestScreenWidthDp = scaleLogicalDp(source.smallestScreenWidthDp, percent)
        }

        return base.createConfigurationContext(override)
    }

    fun percent(context: Context): Int = normalize(
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .getInt(ScaleKey, SystemPercent)
    )

    fun setPercent(context: Context, value: Int): Int {
        val normalized = normalize(value)
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putInt(ScaleKey, normalized)
            .apply()
        return normalized
    }

    fun effectiveDensityDpi(context: Context): Int =
        context.resources.configuration.densityDpi
            .takeIf { it > 0 }
            ?: context.resources.displayMetrics.densityDpi

    fun summary(context: Context): String {
        val config = context.resources.configuration
        return "${percent(context)}% · ${effectiveDensityDpi(context)} dpi · ${config.screenWidthDp}×${config.screenHeightDp} dp"
    }

    private fun loadOrInitializePercent(context: Context): Int {
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        if (prefs.contains(ScaleKey)) return normalize(prefs.getInt(ScaleKey, SystemPercent))

                val initial = DefaultPercent
        prefs.edit().putInt(ScaleKey, initial).apply()
        return initial

    }

        private fun scaleLogicalDp(value: Int, percent: Int): Int =
        (value * (SystemPercent / percent.toFloat())).roundToInt().coerceAtLeast(1)

    private fun normalize(value: Int): Int {
        val stepped = (value.toFloat() / StepPercent).roundToInt() * StepPercent
        return stepped.coerceIn(MinPercent, MaxPercent)
    }
}
