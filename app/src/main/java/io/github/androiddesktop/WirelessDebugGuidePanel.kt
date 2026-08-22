package io.github.androiddesktop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * First-class, device-local wireless ADB onboarding.
 *
 * The Android security boundary is preserved: the user explicitly enables Wireless debugging and
 * opens "Pair device with pairing code" in system Settings. Androiddesktop then discovers the
 * local TLS pairing service through mDNS, accepts the six-digit code inside the app, pairs with the
 * device adbd, reconnects locally and starts the existing shell-UID core without requiring a PC.
 */
class WirelessDebugGuidePanel(
    private val host: Context,
    private val hostPackage: String,
    private val targetPackageProvider: () -> String,
    private val sessionSummaryProvider: () -> String,
    private val onClose: () -> Unit,
    private val onSetupComplete: () -> Unit,
    private val onMessage: (String) -> Unit
) : LinearLayout(host) {

    private val bridge = AndroidAdbBridge.get(host)
    private val statusViews = linkedMapOf<String, TextView>()
    private val summaryView = TextView(host)
    private val pairingServiceView = TextView(host)
    private val pairingCodeInput = EditText(host)
    private val pairingPortInput = EditText(host)
    private val pairButton: Button
    private val discoverButton: Button
    private val bootstrapButton: Button
    private val closeButton: Button
    private val compactPhone = resources.configuration.screenHeightDp < 600

    @Volatile
    private var discoveredEndpoint: AndroidAdbBridge.PairingEndpoint? = null
    private var requiredSetup = false
    private var setupComplete = false

    init {
        orientation = VERTICAL
        visibility = View.GONE
        alpha = 0f
        background = Material3Tokens.surface(
            Color.argb(250, 16, 20, 28),
            dp(28),
            Color.argb(70, 255, 255, 255),
            1
        )
        setPadding(
            dp(if (compactPhone) 18 else 30),
            dp(if (compactPhone) 14 else 24),
            dp(if (compactPhone) 18 else 30),
            dp(if (compactPhone) 14 else 24)
        )
        elevation = dp(24).toFloat()
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(if (compactPhone) 18 else 54)
            rightMargin = dp(if (compactPhone) 18 else 54)
            topMargin = dp(if (compactPhone) 10 else 30)
            bottomMargin = dp(if (compactPhone) 10 else 30)
        }

        val header = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label("无线调试配对", if (compactPhone) 19f else 24f, bold = true),
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(actionButton("重新检测") { refreshStatus() })
        closeButton = actionButton("关闭") { if (!requiredSetup || setupComplete) onClose() }
        header.addView(closeButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        })
        addView(header)

        val intro = LinearLayout(host).apply {
            orientation = VERTICAL
            background = Material3Tokens.surface(
                Color.argb(235, 65, 73, 110),
                dp(22),
                Color.argb(54, 255, 255, 255),
                1
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        intro.addView(label("Androiddesktop 会在本机完成 ADB TLS 配对", if (compactPhone) 12f else 14f, bold = true))
        intro.addView(label(
            "你仍需亲自开启系统“无线调试”并点“使用配对码配对设备”。之后保持系统配对弹窗打开，把六位代码输入这里即可；不需要电脑执行 adb pair。",
            if (compactPhone) 10f else 12f,
            bold = false
        ).apply { setPadding(0, dp(4), 0, 0) })
        addView(intro, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        summaryView.apply {
            textSize = if (compactPhone) 10.5f else 12f
            setTextColor(Material3Tokens.OnSurfaceVariant)
            setPadding(0, dp(8), 0, dp(8))
        }
        addView(summaryView)

        val contentScroll = ScrollView(host).apply { isFillViewport = false }
        val steps = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        contentScroll.addView(steps)
        addView(contentScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        steps.addView(stepCard(
            key = "developer",
            number = "1",
            title = "开启开发者选项",
            description = "如果开发者选项尚未开启，先在系统“关于手机”连续点击版本号，然后进入开发者选项。",
            action = "打开开发者选项"
        ) { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) })

        steps.addView(stepCard(
            key = "wireless",
            number = "2",
            title = "开启无线调试",
            description = "打开系统“无线调试”。这是 Android 的安全开关，普通应用不能静默替你开启。",
            action = "打开无线调试"
        ) { openSettings("android.settings.WIRELESS_DEBUGGING_SETTINGS") })

        val pairingCard = LinearLayout(host).apply {
            orientation = VERTICAL
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(20), Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        val pairingHeader = LinearLayout(host).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pairingHeader.addView(numberBadge("3"))
        pairingHeader.addView(label("在 Androiddesktop 内配对", if (compactPhone) 12f else 14f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
        val pairingStatus = statusLabel("等待配对码")
        statusViews["pair"] = pairingStatus
        pairingHeader.addView(pairingStatus)
        pairingCard.addView(pairingHeader)
        pairingCard.addView(label(
            "在无线调试页面点“使用配对码配对设备”，保持弹窗停留。Androiddesktop 会通过 mDNS 自动找到配对端口；若 ROM 屏蔽发现，也可以手动填配对端口。",
            if (compactPhone) 9.5f else 11f,
            bold = false
        ).apply { setPadding(0, dp(6), 0, dp(7)) })

        pairingServiceView.apply {
            textSize = if (compactPhone) 10f else 11.5f
            setTextColor(Material3Tokens.Primary)
            text = "配对服务：尚未搜索"
            setPadding(0, 0, 0, dp(5))
        }
        pairingCard.addView(pairingServiceView)

        val inputRow = LinearLayout(host).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pairingCodeInput.apply {
            hint = "6 位配对码"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(Material3Tokens.OnSurface)
            setHintTextColor(Material3Tokens.OnSurfaceVariant)
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(16), Material3Tokens.Outline, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            contentDescription = "androiddesktop-pairing-code"
        }
        inputRow.addView(pairingCodeInput, LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.58f))
        pairingPortInput.apply {
            hint = "端口(可选)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(Material3Tokens.OnSurface)
            setHintTextColor(Material3Tokens.OnSurfaceVariant)
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(16), Material3Tokens.Outline, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            contentDescription = "androiddesktop-pairing-port"
        }
        inputRow.addView(pairingPortInput, LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.42f).apply { leftMargin = dp(8) })
        pairingCard.addView(inputRow)

        val pairActions = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        discoverButton = actionButton("搜索配对服务") { discoverPairingService() }
        pairButton = actionButton("开始配对") { pairAndBootstrap() }
        pairActions.addView(discoverButton)
        pairActions.addView(pairButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        pairingCard.addView(pairActions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) })
        steps.addView(pairingCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val coreCard = LinearLayout(host).apply {
            orientation = VERTICAL
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(20), Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        val coreHeader = LinearLayout(host).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        coreHeader.addView(numberBadge("4"))
        coreHeader.addView(label("启动 shell 核心", if (compactPhone) 12f else 14f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
        val coreStatus = statusLabel("等待 ADB")
        statusViews["core"] = coreStatus
        coreHeader.addView(coreStatus)
        coreCard.addView(coreHeader)
        coreCard.addView(label(
            "配对成功后，Androiddesktop 会通过本机 ADB 给宿主必要的调试权限并启动认证 shell core。token 仅保存在本机，不显示、不复制。",
            if (compactPhone) 9.5f else 11f,
            bold = false
        ).apply { setPadding(0, dp(6), 0, dp(7)) })
        bootstrapButton = actionButton("连接并启动核心") { bootstrapCore() }
        coreCard.addView(bootstrapButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        steps.addView(coreCard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        steps.addView(stepCard(
            key = "session",
            number = "5",
            title = "进入桌面并打开真实应用",
            description = "核心就绪后，启动台读取系统真实可启动应用；点击应用创建 VirtualDisplay 会话，并把该应用启动到对应显示。",
            action = "复制诊断命令"
        ) { copyVerificationScript() })

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            pairingCodeInput.isEnabled = false
            pairingPortInput.isEnabled = false
            pairButton.isEnabled = false
            discoverButton.isEnabled = false
        }

        refreshStatus()
    }

    fun setRequiredSetup(required: Boolean) {
        requiredSetup = required && !setupComplete
        updateCloseState()
    }

    fun refreshStatus() {
        val development = readGlobalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        val wireless = readGlobalInt("adb_wifi_enabled") == 1
        setStatus("developer", if (development) "已开启" else "待开启", development)
        setStatus("wireless", if (wireless) "已开启" else "未检测到", wireless)
        setStatus("session", sessionSummaryProvider(), null)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            setStatus("pair", "需要 Android 11+", false)
            setStatus("core", "当前系统不支持内置 TLS 配对", false)
            summaryView.text = "当前 Android ${Build.VERSION.RELEASE}。内置无线 ADB 配对要求 Android 11 或更高版本。"
            return
        }

        summaryView.text = if (requiredSetup) {
            "首次启动需要先完成无线调试配对与核心启动；完成后才进入桌面。"
        } else {
            "可在这里重新配对、恢复无线 ADB 连接或重启 shell core。"
        }

        CoreIoDispatcher.execute {
            val connected = bridge.connect(2_500L)
            val ping = runCatching { PrivilegedCoreClient(host).ping() }.getOrNull()
            post {
                if (ping?.success == true) {
                    setStatus("pair", "已连接", true)
                    setStatus("core", "CORE_READY · 已认证", true)
                    completeSetupIfNeeded()
                } else {
                    setStatus("pair", if (connected) "ADB 已连接" else "等待配对", connected.takeIf { it })
                    setStatus("core", if (connected) "ADB 已连接 · core 未运行" else "等待 ADB", false)
                }
            }
        }
    }

    private fun discoverPairingService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        discoverButton.isEnabled = false
        pairingServiceView.text = "配对服务：正在搜索… 请保持系统配对弹窗打开"
        setStatus("pair", "搜索中", null)
        CoreIoDispatcher.execute {
            val endpoint = bridge.discoverPairingEndpoint(15_000L)
            post {
                discoverButton.isEnabled = true
                discoveredEndpoint = endpoint
                if (endpoint != null) {
                    pairingServiceView.text = "配对服务：${endpoint.host}:${endpoint.port}"
                    pairingPortInput.setText(endpoint.port.toString())
                    setStatus("pair", "已发现", true)
                } else {
                    pairingServiceView.text = "配对服务：未发现。请重新打开“使用配对码配对设备”，或手动填写配对端口。"
                    setStatus("pair", "未发现", false)
                }
            }
        }
    }

    private fun pairAndBootstrap() {
        val code = pairingCodeInput.text.toString().trim()
        val portText = pairingPortInput.text.toString().trim()
        val port = portText.takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (!Regex("\\d{6}").matches(code)) {
            pairingCodeInput.error = "请输入 6 位配对码"
            return
        }
        if (portText.isNotEmpty() && (port == null || port !in 1..65535)) {
            pairingPortInput.error = "端口无效"
            return
        }
        pairButton.isEnabled = false
        discoverButton.isEnabled = false
        bootstrapButton.isEnabled = false
        setStatus("pair", "正在配对", null)
        onMessage("Androiddesktop 正在使用本机无线调试 TLS 配对服务完成配对。")
        CoreIoDispatcher.execute {
            val result = bridge.pair(code, port)
            if (!result.paired) {
                post {
                    pairButton.isEnabled = true
                    discoverButton.isEnabled = true
                    bootstrapButton.isEnabled = true
                    setStatus("pair", "配对失败", false)
                    pairingServiceView.text = "配对服务：${result.message}"
                    onMessage(result.message)
                }
                return@execute
            }
            val endpoint = result.endpoint
            discoveredEndpoint = endpoint
            val token = runCatching { CoreAuthTokenStore(host).ensureToken() }.getOrNull()
            val bootstrap = if (token != null) bridge.bootstrapCore(hostPackage, token) else null
            post {
                pairingCodeInput.text?.clear()
                pairButton.isEnabled = true
                discoverButton.isEnabled = true
                bootstrapButton.isEnabled = true
                setStatus("pair", if (result.connected || bootstrap?.connected == true) "配对并连接成功" else "配对成功", true)
                pairingServiceView.text = buildString {
                    append("配对服务：")
                    append(endpoint?.let { "${it.host}:${it.port}" } ?: "已配对")
                    append(" · ")
                    append(result.message)
                }
                if (bootstrap?.coreStarted == true) {
                    setStatus("core", "CORE_READY · 已认证", true)
                    onMessage(bootstrap.message)
                    completeSetupIfNeeded()
                } else {
                    setStatus("core", if (bootstrap?.connected == true) "ADB 已连接 · core 启动失败" else "等待连接", false)
                    onMessage(bootstrap?.message ?: result.message)
                }
            }
        }
    }

    private fun bootstrapCore() {
        bootstrapButton.isEnabled = false
        setStatus("core", "连接并启动中", null)
        CoreIoDispatcher.execute {
            val token = runCatching { CoreAuthTokenStore(host).ensureToken() }.getOrNull()
            val result = if (token != null) bridge.bootstrapCore(hostPackage, token) else null
            post {
                bootstrapButton.isEnabled = true
                if (result?.coreStarted == true) {
                    setStatus("pair", "ADB 已连接", true)
                    setStatus("core", "CORE_READY · 已认证", true)
                    onMessage(result.message)
                    completeSetupIfNeeded()
                } else {
                    setStatus("core", "启动失败", false)
                    onMessage(result?.message ?: "无法创建本机 core token")
                }
            }
        }
    }

    private fun completeSetupIfNeeded() {
        if (setupComplete) return
        setupComplete = true
        requiredSetup = false
        updateCloseState()
        onSetupComplete()
        Toast.makeText(host, "无线调试已就绪，可以进入桌面", Toast.LENGTH_SHORT).show()
        postDelayed({ onClose() }, 450L)
    }

    private fun updateCloseState() {
        closeButton.isEnabled = !requiredSetup || setupComplete
        closeButton.alpha = if (closeButton.isEnabled) 1f else 0.42f
        closeButton.text = if (requiredSetup && !setupComplete) "完成配对后进入桌面" else "关闭"
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
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(20), Color.argb(40, 255, 255, 255), 1)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        val top = LinearLayout(host).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(numberBadge(number))
        top.addView(label(title, if (compactPhone) 12f else 14f, bold = true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(10) })
        val status = statusLabel("检测中")
        statusViews[key] = status
        top.addView(status)
        card.addView(top)
        card.addView(label(description, if (compactPhone) 9.5f else 11f, bold = false).apply {
            setPadding(0, dp(6), 0, dp(7))
        })
        card.addView(actionButton(action, onAction), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        return card.apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
    }

    private fun numberBadge(number: String): TextView = label(number, if (compactPhone) 11f else 13f, bold = true).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.surface(Material3Tokens.PrimaryContainer, dp(14))
        layoutParams = LayoutParams(dp(if (compactPhone) 28 else 32), dp(if (compactPhone) 28 else 32))
    }

    private fun statusLabel(value: String): TextView = label(value, if (compactPhone) 9f else 10f, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = Material3Tokens.surface(Material3Tokens.SecondaryContainer, dp(13))
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
            "关闭", "完成配对后进入桌面" -> "androiddesktop-guide-close"
            "重新检测" -> "androiddesktop-guide-refresh"
            "搜索配对服务" -> "androiddesktop-guide-discover"
            "开始配对" -> "androiddesktop-guide-pair"
            "连接并启动核心" -> "androiddesktop-guide-bootstrap"
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
        onMessage("已打开系统设置。启用对应开关后返回 Androiddesktop；配对时请保持系统“使用配对码配对设备”弹窗打开。")
    }

    private fun copyVerificationScript() {
        val target = targetPackageProvider().ifBlank { "com.android.settings" }
        val text = buildString {
            appendLine("# 仅用于外部诊断，不是内置配对的必需步骤")
            appendLine("adb shell dumpsys display | grep -iE \"Androiddesktop|mDisplayId|$target\"")
            appendLine("adb shell dumpsys activity activities | grep -iE \"Display: mDisplayId|$target|$hostPackage\"")
        }
        val clipboard = host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Androiddesktop verification", text))
        Toast.makeText(host, "诊断命令已复制", Toast.LENGTH_SHORT).show()
    }

    private fun readGlobalInt(name: String): Int? = runCatching {
        Settings.Global.getInt(host.contentResolver, name)
    }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
