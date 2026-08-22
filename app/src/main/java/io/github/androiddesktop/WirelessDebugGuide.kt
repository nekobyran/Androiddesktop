package io.github.androiddesktop

import android.graphics.Rect

/** Text diagnostics for the built-in wireless ADB workflow. */
object WirelessDebugGuide {
    fun guide(hostPackage: String, targetPackage: String, bounds: Rect): String = buildString {
        appendLine("== Androiddesktop 内置无线调试配对 ==")
        appendLine("目标：在当前 Android 设备内完成 TLS 配对、ADB 连接、shell core 启动与真实 VirtualDisplay 会话。")
        appendLine()
        appendLine("阶段 1 · 系统安全确认")
        appendLine("1. 开启开发者选项。")
        appendLine("2. 开启“无线调试”。该系统开关必须由用户亲自确认，Androiddesktop 不绕过系统授权。")
        appendLine("3. 点“使用配对码配对设备”，保持显示六位配对码的系统弹窗打开。")
        appendLine()
        appendLine("阶段 2 · Androiddesktop 内配对")
        appendLine("1. 应用通过 mDNS 搜索 _adb-tls-pairing._tcp 服务并读取临时配对端口。")
        appendLine("2. 用户把系统显示的六位配对码输入 Androiddesktop。")
        appendLine("3. 应用通过本机 TLS pairing 协议完成 pair，再自动发现并连接 _adb-tls-connect._tcp。")
        appendLine("4. ADB 私钥与证书只保存在应用内部目录；配对码不会持久化。")
        appendLine()
        appendLine("阶段 3 · 启动认证 shell core")
        appendLine("1. 已连接的本机 ADB shell 为 $hostPackage 设置所需调试权限与自由窗实验开关。")
        appendLine("2. shell 使用宿主 APK 的 CLASSPATH 启动 io.github.androiddesktop.PrivilegedShellCore。")
        appendLine("3. core 只监听 127.0.0.1:${PrivilegedCoreClient.PORT}，每条命令都要求本机随机 token。")
        appendLine("4. 看到 CORE_READY 后首次引导才视为完成。")
        appendLine()
        appendLine("阶段 4 · 真实应用窗口")
        appendLine("1. Launchpad 通过 PackageManager 实时枚举 ACTION_MAIN + CATEGORY_LAUNCHER Activity。")
        appendLine("2. 点击应用创建 SurfaceView + VirtualDisplay，并让 shell core 把目标 Activity 启动到该 display。")
        appendLine("3. Dock 不预置应用，只显示当前已创建窗口对应的真实应用。")
        appendLine()
        appendLine("诊断目标：")
        appendLine("host=$hostPackage")
        appendLine("target=$targetPackage")
        appendLine("bounds=${bounds.flattenToString()}")
        appendLine()
        appendLine("外部 adb 命令仅用于诊断，不是内置配对必需步骤：")
        appendLine("adb shell dumpsys display")
        appendLine("adb shell dumpsys activity activities | grep -iE \"$targetPackage|$hostPackage|display\"")
    }
}
