package io.github.androiddesktop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var desktopModeController: DesktopModeController
    private lateinit var adbPermissionDiagnostics: AdbPermissionDiagnostics
    private lateinit var multiWindowLauncher: MultiWindowLauncher
    private lateinit var huaweiVisualProbe: HuaweiVisualProbe
    private lateinit var privilegedCommandPlanner: PrivilegedCommandPlanner
    private lateinit var output: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var packageInput: EditText
    private lateinit var preview: WindowPreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktopModeController = DesktopModeController(this)
        adbPermissionDiagnostics = AdbPermissionDiagnostics(this)
        multiWindowLauncher = MultiWindowLauncher(this)
        huaweiVisualProbe = HuaweiVisualProbe(this)
        privilegedCommandPlanner = PrivilegedCommandPlanner(packageName)
        val root = buildUi()
        setContentView(root)
        UiMotion.runEntrance(root)
        refreshReport(animated = false)
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Androiddesktop"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        })
        root.addView(TextView(this).apply {
            text = "Clean-room Android 桌面模式 / 多窗口诊断与启动器"
            textSize = 14f
        })

        preview = WindowPreviewView(this)
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(156)
        ).apply {
            topMargin = dp(12)
            bottomMargin = dp(8)
        })

        packageInput = EditText(this).apply {
            hint = "目标包名，例如 com.android.settings"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setText("com.android.settings")
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    launchTarget()
                    true
                } else {
                    false
                }
            }
        }
        root.addView(packageInput)

        root.addView(buttonRow(
            button("刷新状态") { refreshReport() },
            button("启动多窗口") { launchTarget() }
        ))
        root.addView(buttonRow(
            button("复制 ADB 命令") { copyAdbCommands() },
            button("复制窗口化命令") { copyWindowingCommands() }
        ))
        root.addView(buttonRow(
            button("Huawei 画面测试") { showHuaweiProbe() },
            button("同类原理") { showPrinciplePlan() }
        ))
        root.addView(buttonRow(
            button("悬浮窗设置") { openSafely(adbPermissionDiagnostics.overlaySettingsIntent()) },
            button("使用情况权限") { openSafely(adbPermissionDiagnostics.usageAccessIntent()) }
        ))
        root.addView(buttonRow(
            button("应用详情") { openAppDetails() },
            button("系统设置测试") { packageInput.setText("com.android.settings"); launchTarget() }
        ))

        output = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            alpha = 1f
        }
        outputScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(output)
        }
        root.addView(outputScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply { topMargin = dp(8) })
        return root
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        minHeight = dp(44)
        UiMotion.attachPressFeedback(this)
        setOnClickListener {
            UiMotion.pulse(this)
            onClick()
        }
    }

    private fun buttonRow(vararg buttons: Button): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { button ->
            addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun refreshReport(animated: Boolean = true) {
        val snapshot = desktopModeController.collectSnapshot(this)
        preview.update(snapshot)
        preview.playRefresh()
        val desktop = desktopModeController.buildHumanReport(this)
        val permissions = adbPermissionDiagnostics.buildHumanReport()
        setReport(buildString {
            appendLine("== Desktop Snapshot ==")
            appendLine(desktop)
            appendLine("== ADB / Permission Diagnostics ==")
            appendLine(permissions)
            appendLine("== 动画状态 ==")
            appendLine("1. 启动入场：标题、预览和控制区整体淡入上滑。")
            appendLine("2. 按钮交互：按压缩放 + 点击脉冲反馈。")
            appendLine("3. 状态刷新：报告区域淡出/淡入切换，窗口预览同步 refresh pulse。")
            appendLine("4. 多窗口启动：预览区触发 launch pulse，模拟目标窗口弹出。")
            appendLine("== 原理摘要 ==")
            appendLine("1. 检测外接显示、输入设备、freeform 特性与全局 settings。")
            appendLine("2. 普通通道通过 ActivityOptions.launchBounds 请求系统以给定窗口边界启动目标 Activity。")
            appendLine("3. ADB/特权通道通过 settings + am start --windowingMode/--bounds 验证真正窗口化。")
            appendLine("4. 同类桌面应用通常还需要前台服务、显示会话、悬浮层/任务栏和可选 Shizuku/ADB 授权服务。")
            appendLine("5. 普通三方应用无法强制 ROM 进入真正桌面模式；需要系统支持、ADB settings 或厂商桌面服务配合。")
        }, animated)
    }

    private fun launchTarget() {
        val bounds = Rect(dp(80), dp(80), dp(920), dp(720))
        preview.playLaunch()
        val result = multiWindowLauncher.launchPackage(packageInput.text.toString(), bounds)
        setReport(output.text.toString() + "\n== Launch Result ==\n${result.message}\n", animated = true)
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
    }

    private fun copyAdbCommands() {
        val commands = adbPermissionDiagnostics.collect().adbCommands.joinToString("\n")
        copyText("Androiddesktop ADB commands", commands, "ADB 命令已复制")
    }

    private fun copyWindowingCommands() {
        val bounds = Rect(dp(80), dp(80), dp(920), dp(720))
        val commands = privilegedCommandPlanner.planForTarget(packageInput.text.toString(), bounds).joinToString("\n")
        copyText("Androiddesktop windowing commands", commands, "窗口化 ADB 命令已复制")
    }

    private fun copyText(label: String, content: String, toast: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
        preview.playRefresh()
        setReport(content, animated = true)
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
    }

    private fun showHuaweiProbe() {
        val snapshot = desktopModeController.collectSnapshot(this)
        preview.update(snapshot)
        preview.playRefresh()
        setReport(huaweiVisualProbe.buildProbePlan(snapshot), animated = true)
    }

    private fun showPrinciplePlan() {
        preview.playRefresh()
        setReport(privilegedCommandPlanner.explain(), animated = true)
    }

    private fun openAppDetails() {
        openSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openSafely(intent: Intent) {
        UiMotion.flash(preview)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, it.message ?: "无法打开设置页", Toast.LENGTH_LONG).show() }
    }

    private fun setReport(text: String, animated: Boolean) {
        UiMotion.replaceText(output, outputScroll, text, animated)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
