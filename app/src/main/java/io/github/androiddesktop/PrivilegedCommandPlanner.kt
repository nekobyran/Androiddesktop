package io.github.androiddesktop

import android.graphics.Rect

class PrivilegedCommandPlanner(private val packageName: String) {
    fun planForTarget(targetPackage: String, bounds: Rect, displayId: Int? = null): List<String> {
        val target = targetPackage.trim().ifEmpty { "com.android.settings" }
        val display = displayId?.let { " --display $it" }.orEmpty()
        val boundsArg = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        return listOf(
            "adb devices",
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
            "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "adb shell appops set $packageName GET_USAGE_STATS allow",
            "adb shell settings put global force_resizable_activities 1",
            "adb shell settings put global enable_freeform_support 1",
            "adb shell settings put global freeform_window_management 1",
            "adb shell cmd package resolve-activity --brief $target",
            "adb shell monkey -p $target 1",
            "adb shell am start$display --windowingMode 5 --activity-brought-to-front --bounds $boundsArg \$(adb shell cmd package resolve-activity --brief $target | tail -n 1)",
            "adb shell dumpsys display",
            "adb shell dumpsys activity activities | grep -iE \"windowingMode|bounds|displayId|$target\"",
            "adb shell dumpsys window displays"
        )
    }

    fun explain(): String = buildString {
        appendLine("同类型应用原理规划:")
        appendLine("1. 普通通道: 用公开 ActivityOptions.launchBounds/launchDisplayId 请求窗口边界与显示器。")
        appendLine("2. ADB 通道: 用 pm/appops/settings 打开 freeform 与必要权限，再用 am start --windowingMode/--bounds 验证窗口化。")
        appendLine("3. Shizuku/特权服务通道: 原 APK 声明了 Shizuku API 和 priv/display 服务痕迹；开源版保留命令规划和接口边界，不内置私有协议。")
        appendLine("4. 显示通道: 通过 DisplayManager 枚举外接/虚拟显示，结合 launchDisplayId 或 ADB --display 做画面测试。")
    }
}
