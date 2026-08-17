package io.github.androiddesktop

import android.graphics.Rect

data class DesktopApp(
    val label: String,
    val packageName: String,
    val glyph: String,
    val description: String
)

data class DesktopWindow(
    val id: Int,
    val app: DesktopApp,
    val bounds: Rect,
    val displayMode: DisplayMode,
    val note: String
)

enum class DisplayMode {
    PlaceholderSurface,
    PrivilegedVirtualDisplay,
    ExternalDisplay
}

data class PrivilegedCoreState(
    val wirelessDebugging: Boolean,
    val shizukuOrShell: Boolean,
    val virtualDisplay: Boolean,
    val inputInjection: Boolean
) {
    val ready: Boolean get() = wirelessDebugging && shizukuOrShell && virtualDisplay && inputInjection
}
