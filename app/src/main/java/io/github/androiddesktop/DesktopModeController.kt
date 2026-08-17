package io.github.androiddesktop

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.InputDevice

class DesktopModeController(private val context: Context) {
    fun collectSnapshot(activity: Activity? = null): DesktopSnapshot {
        val pm = context.packageManager
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays.toList()
        val inputSummary = collectInputSummary()
        return DesktopSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            isHuaweiFamily = isHuaweiFamily(),
            isActivityInMultiWindow = activity?.isInMultiWindowMode ?: false,
            hasFreeformFeature = pm.hasSystemFeature("android.software.freeform_window_management"),
            hasPcFeature = pm.hasSystemFeature("android.hardware.type.pc"),
            displayCount = displays.size,
            displayDescriptions = displays.map { displayDescription(it) },
            hasMouse = inputSummary.hasMouse,
            hasKeyboard = inputSummary.hasKeyboard,
            hasTouchpad = inputSummary.hasTouchpad,
            inputDescriptions = inputSummary.descriptions,
            forceResizableActivities = readGlobalSetting("force_resizable_activities"),
            enableFreeformSupport = readGlobalSetting("enable_freeform_support"),
            freeformWindowManagement = readGlobalSetting("freeform_window_management")
        )
    }

    fun buildHumanReport(activity: Activity? = null): String {
        val s = collectSnapshot(activity)
        return buildString {
            appendLine("设备: ${s.manufacturer} ${s.brand} ${s.model} / API ${s.sdk}")
            appendLine("Huawei/Honor 系: ${yesNo(s.isHuaweiFamily)}")
            appendLine("当前 Activity 多窗口中: ${yesNo(s.isActivityInMultiWindow)}")
            appendLine("系统 freeform 特性: ${yesNo(s.hasFreeformFeature)}")
            appendLine("PC/桌面设备特性: ${yesNo(s.hasPcFeature)}")
            appendLine("显示器数量: ${s.displayCount}")
            s.displayDescriptions.forEach { appendLine("  - $it") }
            appendLine("鼠标: ${yesNo(s.hasMouse)} / 键盘: ${yesNo(s.hasKeyboard)} / 触控板: ${yesNo(s.hasTouchpad)}")
            s.inputDescriptions.forEach { appendLine("  - $it") }
            appendLine("global.force_resizable_activities: ${s.forceResizableActivities ?: "<unreadable>"}")
            appendLine("global.enable_freeform_support: ${s.enableFreeformSupport ?: "<unreadable>"}")
            appendLine("global.freeform_window_management: ${s.freeformWindowManagement ?: "<unreadable>"}")
        }
    }

    private fun isHuaweiFamily(): Boolean {
        val text = listOf(Build.MANUFACTURER, Build.BRAND, Build.HARDWARE, Build.PRODUCT)
            .joinToString(" ")
            .lowercase()
        return text.contains("huawei") || text.contains("honor") || text.contains("kirin") || text.contains("hisilicon")
    }

    private fun readGlobalSetting(name: String): String? = runCatching {
        Settings.Global.getString(context.contentResolver, name)
    }.getOrNull()

    private fun displayDescription(display: Display): String {
        val mode = if (Build.VERSION.SDK_INT >= 23) display.mode else null
        val size = if (mode != null) "${mode.physicalWidth}x${mode.physicalHeight}@${mode.refreshRate.toInt()}Hz" else "mode=<api<23>"
        val state = if (Build.VERSION.SDK_INT >= 20) display.state else -1
        return "id=${display.displayId}, name=${display.name}, $size, flags=${display.flags}, state=$state"
    }

    private fun collectInputSummary(): InputSummary {
        var hasMouse = false
        var hasKeyboard = false
        var hasTouchpad = false
        val descriptions = mutableListOf<String>()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            val isMouse = sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
            val isKeyboard = device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
            val isTouchpad = sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
            hasMouse = hasMouse || isMouse
            hasKeyboard = hasKeyboard || isKeyboard
            hasTouchpad = hasTouchpad || isTouchpad
            if (isMouse || isKeyboard || isTouchpad) {
                descriptions += "id=$id name=${device.name} mouse=$isMouse keyboard=$isKeyboard touchpad=$isTouchpad sources=0x${sources.toString(16)}"
            }
        }
        return InputSummary(hasMouse, hasKeyboard, hasTouchpad, descriptions)
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}

data class DesktopSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdk: Int,
    val isHuaweiFamily: Boolean,
    val isActivityInMultiWindow: Boolean,
    val hasFreeformFeature: Boolean,
    val hasPcFeature: Boolean,
    val displayCount: Int,
    val displayDescriptions: List<String>,
    val hasMouse: Boolean,
    val hasKeyboard: Boolean,
    val hasTouchpad: Boolean,
    val inputDescriptions: List<String>,
    val forceResizableActivities: String?,
    val enableFreeformSupport: String?,
    val freeformWindowManagement: String?
)

private data class InputSummary(
    val hasMouse: Boolean,
    val hasKeyboard: Boolean,
    val hasTouchpad: Boolean,
    val descriptions: List<String>
)
