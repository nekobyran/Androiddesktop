package io.github.androiddesktop

import android.content.Context
import kotlin.math.min

/**
 * Shared adaptive-density contract for the desktop shell.
 *
 * A landscape phone has plenty of width but very little vertical budget, so the
 * compact breakpoint is driven primarily by screen height rather than width.
 * The same view tree and callbacks are kept; only presentation density changes.
 */
data class AdaptiveDesktopMetrics(
    val compactPhone: Boolean,
    val outerHorizontalDp: Int,
    val outerVerticalDp: Int,
    val topTitleSp: Float,
    val dockButtonWidthDp: Int,
    val dockAppWidthDp: Int,
    val dockHeightDp: Int,
    val dockIconDp: Int,
    val dockLabelSp: Float,
    val launcherWidthDp: Int,
    val launcherPaddingDp: Int,
    val launcherTileWidthDp: Int,
    val launcherTileHeightDp: Int,
    val launcherIconDp: Int,
    val consoleWidthDp: Int,
    val consoleHeightDp: Int
) {
    companion object {
        fun from(context: Context): AdaptiveDesktopMetrics {
            val configuration = context.resources.configuration
            val widthDp = configuration.screenWidthDp.coerceAtLeast(320)
            val heightDp = configuration.screenHeightDp.coerceAtLeast(240)
            val compact = heightDp < 600
            return if (compact) {
                AdaptiveDesktopMetrics(
                    compactPhone = true,
                    outerHorizontalDp = 6,
                    outerVerticalDp = 3,
                    topTitleSp = 16f,
                    dockButtonWidthDp = 56,
                    dockAppWidthDp = 54,
                    dockHeightDp = 44,
                    dockIconDp = 22,
                    dockLabelSp = 7f,
                    launcherWidthDp = min(widthDp - 16, 420).coerceAtLeast(340),
                    launcherPaddingDp = 12,
                    launcherTileWidthDp = 92,
                    launcherTileHeightDp = 68,
                    launcherIconDp = 30,
                    consoleWidthDp = min(widthDp - 16, 360).coerceAtLeast(300),
                    consoleHeightDp = min(heightDp - 96, 150).coerceAtLeast(112)
                )
            } else {
                AdaptiveDesktopMetrics(
                    compactPhone = false,
                    outerHorizontalDp = 12,
                    outerVerticalDp = 6,
                    topTitleSp = 20f,
                    dockButtonWidthDp = 68,
                    dockAppWidthDp = 62,
                    dockHeightDp = 48,
                    dockIconDp = 24,
                    dockLabelSp = 8f,
                    launcherWidthDp = 470,
                    launcherPaddingDp = 18,
                    launcherTileWidthDp = 104,
                    launcherTileHeightDp = 88,
                    launcherIconDp = 38,
                    consoleWidthDp = 430,
                    consoleHeightDp = 190
                )
            }
        }
    }
}
