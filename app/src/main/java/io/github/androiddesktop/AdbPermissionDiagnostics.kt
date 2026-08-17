package io.github.androiddesktop

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

class AdbPermissionDiagnostics(private val context: Context) {
    private val packageName: String = context.packageName

    fun collect(): PermissionReport {
        return PermissionReport(
            packageName = packageName,
            canDrawOverlays = canDrawOverlays(),
            hasUsageStatsAccess = hasUsageStatsAccess(),
            hasWriteSecureSettings = hasWriteSecureSettings(),
            adbCommands = adbCommands()
        )
    }

    fun buildHumanReport(): String {
        val report = collect()
        return buildString {
            appendLine("包名: ${report.packageName}")
            appendLine("SYSTEM_ALERT_WINDOW: ${status(report.canDrawOverlays)}")
            appendLine("PACKAGE_USAGE_STATS: ${status(report.hasUsageStatsAccess)}")
            appendLine("WRITE_SECURE_SETTINGS: ${status(report.hasWriteSecureSettings)}")
            appendLine()
            appendLine("建议 ADB 命令:")
            report.adbCommands.forEach { appendLine(it) }
        }
    }

    fun overlaySettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
    )

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(context) else true
    }

    private fun hasWriteSecureSettings(): Boolean {
        return context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasUsageStatsAccess(): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000L, now)
        }.getOrNull()
        if (!stats.isNullOrEmpty()) return true

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun adbCommands(): List<String> = listOf(
        "adb devices",
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
        "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
        "adb shell appops set $packageName GET_USAGE_STATS allow",
        "adb shell settings put global force_resizable_activities 1",
        "adb shell settings put global enable_freeform_support 1",
        "adb shell settings put global freeform_window_management 1",
        "adb shell am force-stop $packageName",
        "adb shell monkey -p $packageName 1"
    )

    private fun status(value: Boolean): String = if (value) "granted/active" else "missing"
}

data class PermissionReport(
    val packageName: String,
    val canDrawOverlays: Boolean,
    val hasUsageStatsAccess: Boolean,
    val hasWriteSecureSettings: Boolean,
    val adbCommands: List<String>
)
