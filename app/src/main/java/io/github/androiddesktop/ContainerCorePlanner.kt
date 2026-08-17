package io.github.androiddesktop

import android.graphics.Rect

class ContainerCorePlanner(private val hostPackageName: String) {
    fun principle(): String = buildString {
        appendLine("== 正确软件模型 ==")
        appendLine("目标不是把系统 App 拉到 Android 系统自由窗，而是在 Androiddesktop 内部构建一个类 Windows 桌面容器。")
        appendLine("每个窗口应对应一个被托管 App 的显示会话: Surface/VirtualDisplay + ActivityTask + 输入转发。")
        appendLine()
        appendLine("== 推测的特权核心链路 ==")
        appendLine("1. 手机开启无线调试，用户完成 adb pair/connect。")
        appendLine("2. App 通过 Shizuku 或自建 adb shell core 获得 shell/root 等级 Binder 调用能力。")
        appendLine("3. 特权核心调用 DisplayManager/ActivityTaskManager 隐藏或 shell 能力，创建可承载任务的显示会话。")
        appendLine("4. 目标 App 被启动到指定 display/task/windowing mode。")
        appendLine("5. Androiddesktop 用 SurfaceView/TextureView 显示该会话画面，并把触摸、键盘、鼠标事件映射回目标显示。")
        appendLine("6. Dock、启动台、窗口移动/缩放只是桌面壳；真正显示其他 App 内容依赖第 2-5 步。")
        appendLine()
        appendLine("== 当前开源版状态 ==")
        appendLine("本版本已实现 Material 3 风格桌面壳、Dock、启动台和窗口占位容器。")
        appendLine("真实第三方 App 画面嵌入仍是特权核心接口边界，不伪装已完成。")
    }

    fun wirelessDebugCommands(targetPackage: String, bounds: Rect, displayId: Int? = null): List<String> {
        val pkg = targetPackage.trim().ifEmpty { "com.android.settings" }
        val display = displayId?.let { " --display $it" }.orEmpty()
        val boundsArg = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        return listOf(
            "# 1) 手机设置 > 开发者选项 > 无线调试，先读取配对地址、连接地址和配对码",
            "adb pair <phone-ip>:<pair-port> <pair-code>",
            "adb connect <phone-ip>:<adb-port>",
            "adb devices",
            "# 2) 给宿主 App shell 级调试权限。普通安装不会自动获得这些权限。",
            "adb shell appops set $hostPackageName SYSTEM_ALERT_WINDOW allow",
            "adb shell appops set $hostPackageName GET_USAGE_STATS allow",
            "adb shell pm grant $hostPackageName android.permission.WRITE_SECURE_SETTINGS",
            "# 3) 打开多显示/自由窗相关全局开关，具体 ROM 可能忽略。",
            "adb shell settings put global force_resizable_activities 1",
            "adb shell settings put global enable_freeform_support 1",
            "adb shell settings put global freeform_window_management 1",
            "# 4) 原理验证: 把目标 App 启动到指定 display/windowing/bounds。完整容器需要 core 捕获/承载该 display 的 Surface。",
            "adb shell am start$display --windowingMode 5 --activity-brought-to-front --bounds $boundsArg \$(adb shell cmd package resolve-activity --brief $pkg | tail -n 1)",
            "# 5) 证据采集",
            "adb shell dumpsys display",
            "adb shell dumpsys activity activities | grep -iE \"displayId|windowingMode|bounds|$pkg\"",
            "adb shell dumpsys input"
        )
    }
}
