package io.github.androiddesktop

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

class HuaweiVisualProbe(private val context: Context) {
    fun buildProbePlan(snapshot: DesktopSnapshot): String {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val externalDisplays = displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        return buildString {
            appendLine("Huawei / Android 多窗口画面测试")
            appendLine("设备族判断: ${if (snapshot.isHuaweiFamily) "Huawei/Honor-like" else "generic Android"}")
            appendLine("外接/虚拟显示数量: ${externalDisplays.size}")
            appendLine("鼠标/键盘输入: mouse=${snapshot.hasMouse}, keyboard=${snapshot.hasKeyboard}")
            appendLine()
            appendLine("建议测试步骤:")
            appendLine("1. 手机开启开发者选项与 USB debugging，授权当前电脑 adb fingerprint。")
            appendLine("2. 连接 HDMI/Type-C 扩展坞、无线投屏或华为桌面模式入口，确认外屏画面出现。")
            appendLine("3. 执行本应用给出的 adb grant/appops/settings 命令，然后重启本应用。")
            appendLine("4. 在目标包名输入框中填写要多窗口化的应用包名，点击启动测试。")
            appendLine("5. 观察目标应用是否以可调整窗口、分屏相邻窗口或外屏窗口形式出现。")
            appendLine("6. 如果只全屏启动，说明 ROM 未开放 freeform/windowing mode 给普通三方应用，需要系统权限或厂商桌面服务配合。")
            appendLine()
            appendLine("建议采集证据:")
            appendLine("adb shell dumpsys display")
            appendLine("adb shell dumpsys activity activities")
            appendLine("adb shell dumpsys window displays")
            appendLine("adb shell cmd activity get-config")
            appendLine("adb shell settings get global force_resizable_activities")
            appendLine("adb shell settings get global enable_freeform_support")
            appendLine()
            appendLine("当前 ROM 信息:")
            appendLine("manufacturer=${Build.MANUFACTURER}, brand=${Build.BRAND}, model=${Build.MODEL}, product=${Build.PRODUCT}, hardware=${Build.HARDWARE}")
        }
    }
}
