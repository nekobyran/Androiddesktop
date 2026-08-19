package io.github.androiddesktop

import android.content.Context
import android.content.pm.PackageManager

object VrRuntimeDetector {
    private val vrOrXrFeatures = listOf(
        PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE,
        "android.hardware.vr.high_performance",
        "android.software.xr.immersive",
        "android.software.xr.spatial",
        "android.software.vr.mode",
        "com.oculus.software.vr",
        "com.pico.software.vr"
    )

    fun shouldUseVr(context: Context): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) return true
        return vrOrXrFeatures.any { context.packageManager.hasSystemFeature(it) }
    }

    fun report(context: Context): String = buildString {
        appendLine("== VR runtime gate ==")
        appendLine("VR mode only opens on VR/XR-capable devices.")
        vrOrXrFeatures.forEach { feature ->
            appendLine("$feature=${context.packageManager.hasSystemFeature(feature)}")
        }
    }
}
