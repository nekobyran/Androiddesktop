package io.github.androiddesktop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * In-app, executable wireless-debugging onboarding. The guide does not merely
 * show a shell script: it exposes device-side steps, opens the relevant system
 * settings, checks observable state, authenticates the privileged core, and
 * keeps the exact ADB commands available for the desktop side.
 */
class WirelessDebugGuidePanel(
    private val host: Context,
    private val hostPackage: String,
    private val targetPackageProvider: () -> String,
    private val sessionSummaryProvider: () -> String,
    private val onClose: () -> Unit,
    private val onMessage: (String) -> Unit
) : LinearLayout(host) {

        private val statusViews = linkedMapOf<String, TextView>()
    private val summaryView = TextView(host)
    private val commandView = TextView(host)
    private val compactPhone = resources.configuration.screenHeightDp < 600


    init {
        orientation = VERTICAL
        visibility = View.GONE
        alpha = 0f
        background = Material3Tokens.surface(
            Material3Tokens.SurfaceContainerHigh,
            dp(28),
            Color.argb(92, 255, 255, 255),
            1
        )
                setPadding(
            dp(if (compactPhone) 12 else 18),
            dp(if (compactPhone) 10 else 16),
            dp(if (compactPhone) 12 else 18),
            dp(if (compactPhone) 10 else 16)
        )
        elevation = dp(16).toFloat()
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(if (compactPhone) 12 else 36)
            rightMargin = dp(if (compactPhone) 12 else 36)
            topMargin = dp(if (compactPhone) 8 else 28)
            bottomMargin = dp(if (compactPhone) 8 else 28)
        }


        val header = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
                header.addView(label("无线调试与特权核心", if (compactPhone) 18f else 22f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        header.addView(actionButton("重新检测") { refreshStatus() })
        header.addView(actionButton("复制全部") { copyAllGuide() }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        header.addView(actionButton("关闭") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        addView(header)

                summaryView.apply {
            textSize = if (compactPhone) 10.5f else 12f
            setTextColor(Material3Tokens.OnSurfaceVariant)
            setPadding(0, dp(if (compactPhone) 4 else 6), 0, dp(if (compactPhone) 6 else 10))
        }

        addView(summaryView)

        val contentRow = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
        }
        addView(contentRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

                val steps = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(0, 0, dp(if (compactPhone) 8 else 12), 0)
        }

        val stepsScroll = ScrollView(host).apply {
            isFillViewport = true
            addView(steps)
        }
        contentRow.addView(stepsScroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 0.45f))

        steps.addView(stepCard(
            key = "developer",
            number = "1",
            title = "开启开发者选项",
            description = "设置 → 关于手机 → 连续点击版本号，然后回到系统设置进入开发者选项。",
            action = "打开开发者选项"
        ) { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })

        steps.addView(stepCard(
            key = "wireless",
            number = "2",
            title = "开启无线调试",
            description = "在开发者选项中开启无线调试，手机与电脑保持在同一局域网。",
            action = "打开无线调试"
        ) { openSettings("android.settings.WIRELESS_DEBUGGING_SETTINGS") })

        steps.addView(stepCard(
            key = "pair",
            number = "3",
            title = "电脑执行 adb pair / connect",
            description = "手机点“使用配对码配对设备”，电脑执行 adb pair IP:配对端口，再执行 adb connect IP:连接端口。此步骤必须在电脑端完成。",
            action = "复制配对模板"
        ) {
            copyText("Androiddesktop ADB pairing", "adb pair <手机IP:配对端口>\nadb connect <手机IP:连接端口>\nadb devices -l")
        })

        steps.addView(stepCard(
            key = "core",
            number = "4",
            title = "启动认证 shell core",
            description = "Androiddesktop 首次启动会生成 256-bit 本机 token。复制核心脚本到电脑执行，看到 CORE_READY 才算完成。",
            action = "复制核心脚本"
        ) { copyCoreScript() })

        steps.addView(stepCard(
            key = "session",
            number = "5",
            title = "创建窗口并验证",
            description = "回到桌面，从启动台打开番茄小说/华为阅读等真实应用。只有 VirtualDisplay 有真实 Activity task 且截图出现 App 内容才算通过。",
            action = "复制验证命令"
        ) { copyVerificationScript() })

                val commandContainer = LinearLayout(host).apply {
            orientation = VERTICAL
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(20), Material3Tokens.Outline, 1)
            setPadding(
                dp(if (compactPhone) 10 else 14),
                dp(if (compactPhone) 8 else 12),
                dp(if (compactPhone) 10 else 14),
                dp(if (compactPhone) 8 else 12)
            )
        }
        commandContainer.addView(label("当前完整命令", if (compactPhone) 12f else 14f, bold = true))
        commandView.apply {
            textSize = if (compactPhone) 9.5f else 11f

            setTextColor(Material3Tokens.OnSurface)
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
        }
        val commandScroll = ScrollView(host).apply { addView(commandView) }
        commandContainer.addView(commandScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        contentRow.addView(commandContainer, LayoutParams(0, LayoutParams.MATCH_PARENT, 0.55f))

        refreshStatus()
    }

    fun refreshStatus() {
        val development = readGlobalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        val wireless = readGlobalInt("adb_wifi_enabled") == 1
        val tokenPresent = runCatching { CoreAuthTokenStore(host).tokenFile().isFile }.getOrDefault(false)

        setStatus("developer", if (development) "已开启" else "待开启", development)
        setStatus("wireless", if (wireless) "已开启" else "未检测到 / 请人工确认", wireless)
        setStatus("pair", "需电脑端确认", null)
        setStatus("core", if (tokenPresent) "token 已就绪 · 检测 core…" else "等待首次启动生成 token", tokenPresent.takeIf { !it })
        setStatus("session", sessionSummaryProvider(), null)
        summaryView.text = "设备侧状态会自动检测；配对端口/连接端口由 Android 无线调试页面动态生成。${if (AppMotion.reducedMotion(host)) " 已启用 reduced-motion。" else " 动画遵循 120/200/220/240ms motion token。"}"
        commandView.text = fullGuide()

        CoreIoDispatcher.execute {
            val ping = runCatching { PrivilegedCoreClient(host).ping() }.getOrNull()
            post {
                if (ping?.success == true) {
                    setStatus("core", "CORE_READY · 已认证", true)
                } else {
                    setStatus("core", if (tokenPresent) "token 已就绪 · core 未连接" else "core 未连接", false)
                }
            }
        }
    }

    private fun stepCard(
        key: String,
        number: String,
        title: String,
        description: String,
        action: String,
        onAction: () -> Unit
    ): View {
                val card = LinearLayout(host).apply {
            orientation = VERTICAL
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(18), Color.argb(40, 255, 255, 255), 1)
            setPadding(
                dp(if (compactPhone) 10 else 12),
                dp(if (compactPhone) 7 else 10),
                dp(if (compactPhone) 10 else 12),
                dp(if (compactPhone) 7 else 10)
            )
        }
        val top = LinearLayout(host).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(number, if (compactPhone) 11f else 13f, bold = true).apply {
            gravity = Gravity.CENTER
            background = Material3Tokens.surface(Material3Tokens.PrimaryContainer, dp(14))
        }, LayoutParams(dp(if (compactPhone) 26 else 30), dp(if (compactPhone) 26 else 30)))
        top.addView(label(title, if (compactPhone) 12f else 14f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(if (compactPhone) 7 else 10) })
        val status = label("检测中", if (compactPhone) 9f else 10f, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(if (compactPhone) 6 else 8), dp(if (compactPhone) 3 else 4), dp(if (compactPhone) 6 else 8), dp(if (compactPhone) 3 else 4))
        }

        statusViews[key] = status
        top.addView(status)
                card.addView(top)
        card.addView(label(description, if (compactPhone) 9.5f else 11f, bold = false).apply {
            setPadding(0, dp(if (compactPhone) 4 else 6), 0, dp(if (compactPhone) 5 else 7))
        })
        card.addView(actionButton(action, onAction), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        return card.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(if (compactPhone) 6 else 8) }
        }

    }

    private fun setStatus(key: String, value: String, success: Boolean?) {
        statusViews[key]?.apply {
            text = value
            val base = when (success) {
                true -> 0xFF1B5E20.toInt()
                false -> 0xFF6B3B16.toInt()
                null -> Material3Tokens.SecondaryContainer
            }
            background = Material3Tokens.surface(base, dp(13))
            setTextColor(Material3Tokens.OnSurface)
        }
    }

            private fun actionButton(textValue: String, onClick: () -> Unit): Button = Button(host).apply {
        text = textValue
        textSize = if (compactPhone) 10f else 11f
        isAllCaps = false
        contentDescription = when (textValue) {
            "关闭" -> "androiddesktop-guide-close"
            "重新检测" -> "androiddesktop-guide-refresh"
            "复制全部" -> "androiddesktop-guide-copy-all"
            else -> "androiddesktop-guide-action"
        }
        setTextColor(Material3Tokens.OnSurface)

        background = Material3Tokens.ripple(Material3Tokens.PrimaryContainer, dp(16))
        AppMotion.installPressFeedback(this)
        setOnClickListener { onClick() }
    }

    private fun label(value: String, sizeSp: Float, bold: Boolean): TextView = TextView(host).apply {
        text = value
        textSize = sizeSp
        setTextColor(if (bold) Material3Tokens.OnSurface else Material3Tokens.OnSurfaceVariant)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun openSettings(action: String) {
        val primary = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launched = runCatching { host.startActivity(primary); true }.getOrDefault(false)
        if (!launched) {
            runCatching { host.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        onMessage("已请求打开系统设置：$action。返回 Androiddesktop 后点击“重新检测”。")
    }

    private fun copyCoreScript() {
        val text = fullGuide().lineSequence()
            .dropWhile { !it.contains("阶段 3") }
            .takeWhile { !it.contains("阶段 4") }
            .joinToString("\n")
        copyText("Androiddesktop privileged core", text)
    }

    private fun copyVerificationScript() {
        val target = targetPackageProvider().ifBlank { "com.dragon.read" }
        val text = buildString {
            appendLine("adb shell dumpsys display | grep -iE \"Androiddesktop|mDisplayId|$target\"")
            appendLine("adb shell dumpsys activity activities | grep -iE \"Display: mDisplayId|$target|$hostPackage\"")
            appendLine("adb shell screencap -d <displayId> -p /sdcard/androiddesktop-target.png")
            appendLine("adb pull /sdcard/androiddesktop-target.png")
        }
        copyText("Androiddesktop verification", text)
    }

    private fun copyAllGuide() = copyText("Androiddesktop wireless debugging guide", fullGuide())

    private fun copyText(label: String, value: String) {
        val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(host, "已复制：$label", Toast.LENGTH_SHORT).show()
    }

    private fun fullGuide(): String {
        val target = targetPackageProvider().ifBlank { "com.dragon.read" }
        return WirelessDebugGuide.guide(hostPackage, target, Rect(dp(80), dp(80), dp(920), dp(720)))
    }

    private fun readGlobalInt(name: String): Int? = runCatching {
        Settings.Global.getInt(host.contentResolver, name)
    }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
