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
        appendLine("2. App 通过 Shizuku、自建 adb shell core、root 或系统签名服务获得 Binder 调用能力。")
        appendLine("3. 特权核心创建 display/session，启动目标 App 到该 display/task。")
        appendLine("4. Androiddesktop 窗口分配 SurfaceView/TextureView 插槽并绑定 session descriptor。")
        appendLine("5. 窗口移动/缩放映射为目标 display 的裁剪、缩放和输入坐标转换。")
        appendLine("6. 输入事件通过 shell input 或 Binder 注入回目标显示。")
        appendLine()
        appendLine("== 当前开源版状态 ==")
        appendLine("已实现 Material 3 桌面壳、Dock、启动台、可拖动窗口、核心 contract、命令规划。")
        appendLine("真实第三方 App 画面嵌入仍需完成 core 进程，不伪装已完成。")
    }

    fun sessionBlueprint(targetPackage: String, bounds: Rect): String {
        val request = CoreSessionRequest(targetPackage.trim().ifEmpty { "com.android.settings" }, bounds)
        val descriptor = CoreSessionDescriptor(
            sessionId = "pending-${request.targetPackage.replace('.', '-')}",
            displayId = null,
            targetPackage = request.targetPackage,
            surfaceState = SurfaceBridgeState.Missing,
            inputState = InputBridgeState.Missing,
            lifecycleState = WindowLifecycleState.Created,
            message = "Host window created; privileged display session is not attached yet."
        )
        return buildString {
            appendLine("== Session contract ==")
            appendLine("request.targetPackage=${request.targetPackage}")
            appendLine("request.bounds=${request.requestedBounds.flattenToString()}")
            appendLine("request.size=${request.requestedWidth}x${request.requestedHeight}")
            appendLine("request.densityDpi=${request.requestedDensityDpi}")
            appendLine("request.transport=${request.transport}")
            appendLine()
            appendLine("descriptor.sessionId=${descriptor.sessionId}")
            appendLine("descriptor.displayId=${descriptor.displayId ?: "<not-bound>"}")
            appendLine("descriptor.surfaceState=${descriptor.surfaceState}")
            appendLine("descriptor.inputState=${descriptor.inputState}")
            appendLine("descriptor.lifecycleState=${descriptor.lifecycleState}")
            appendLine("descriptor.message=${descriptor.message}")
            appendLine()
            append(readinessReport().toHumanReport())
        }
    }

    fun readinessReport(): CoreReadinessReport = CoreReadinessReport(
        wirelessDebugConnected = false,
        privilegedTransportReady = false,
        displaySessionReady = false,
        inputBridgeReady = false,
        targetTaskAttached = false
    )

    fun coreLaunchScript(targetPackage: String, bounds: Rect): String {
        val pkg = targetPackage.trim().ifEmpty { "com.android.settings" }
        val boundsArg = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        return buildString {
            appendLine("# Androiddesktop privileged core bootstrap draft")
            appendLine("# This script does not contain secrets. Fill in phone IP/ports from Wireless debugging UI.")
            appendLine("adb pair <phone-ip>:<pair-port> <pair-code>")
            appendLine("adb connect <phone-ip>:<adb-port>")
            appendLine("adb devices")
            appendLine()
            appendLine("# Host grants; may fail on ROMs that block shell grants.")
            appendLine("adb shell appops set $hostPackageName SYSTEM_ALERT_WINDOW allow")
            appendLine("adb shell appops set $hostPackageName GET_USAGE_STATS allow")
            appendLine("adb shell pm grant $hostPackageName android.permission.WRITE_SECURE_SETTINGS")
            appendLine()
            appendLine("# Feature gates for window/display experiments.")
            appendLine("adb shell settings put global force_resizable_activities 1")
            appendLine("adb shell settings put global enable_freeform_support 1")
            appendLine("adb shell settings put global freeform_window_management 1")
            appendLine()
            appendLine("# Minimal external validation before a real core exists.")
            appendLine("adb shell cmd package resolve-activity --brief $pkg")
            appendLine("adb shell am start --windowingMode 5 --activity-brought-to-front --bounds $boundsArg \\$(adb shell cmd package resolve-activity --brief $pkg | tail -n 1)")
            appendLine()
            appendLine("# Real next implementation target:")
            appendLine("# adb shell app_process /system/bin io.github.androiddesktop.core.PrivilegedCore --host $hostPackageName --target $pkg --bounds $boundsArg")
            appendLine("# The app_process core must return a CoreSessionDescriptor: sessionId, displayId, surfaceState, inputState.")
        }
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
            "adb shell am start$display --windowingMode 5 --activity-brought-to-front --bounds $boundsArg \\$(adb shell cmd package resolve-activity --brief $pkg | tail -n 1)",
            "# 5) 证据采集",
            "adb shell dumpsys display",
            "adb shell dumpsys activity activities | grep -iE \"displayId|windowingMode|bounds|$pkg\"",
            "adb shell dumpsys input"
        )
    }
}
