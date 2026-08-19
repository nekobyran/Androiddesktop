package io.github.androiddesktop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var desktopLayer: FrameLayout
    private lateinit var workspaceScroll: HorizontalScrollView
    private lateinit var columnStrip: LinearLayout
    private lateinit var console: TextView
        private lateinit var packageInput: EditText
    private lateinit var launcherPanel: View
    private lateinit var wirelessGuidePanel: View
    private lateinit var wirelessGuideText: TextView
    private lateinit var corePlanner: ContainerCorePlanner
    private lateinit var niriManager: NiriStyleWindowManager
    private lateinit var multiWindowLauncher: MultiWindowLauncher
    private var windowSeq = 1
    private val columnViews = linkedMapOf<Int, View>()
    private val embeddedSessions = linkedMapOf<Int, EmbeddedAppSurfaceView>()

        private val apps by lazy {
        listOf(
            DesktopApp("设置", resolveLauncherPackage("com.android.settings"), "⚙", "系统设置，常用于验证窗口承载"),
            DesktopApp("文件", resolveLauncherPackage("com.huawei.filemanager", "com.google.android.documentsui", "com.android.documentsui"), "▣", "优先使用设备真实文件管理器"),
            DesktopApp("浏览器", resolveLauncherPackage("com.huawei.browser", "com.android.chrome"), "◎", "优先使用设备默认浏览器"),
            DesktopApp("图库", resolveLauncherPackage("com.huawei.photos", "com.android.gallery3d", "com.google.android.apps.photos"), "◧", "优先使用设备图库"),
            DesktopApp("计算器", resolveLauncherPackage("com.huawei.calculator", "com.android.calculator2", "com.google.android.calculator"), "＋", "轻量多窗口验证目标"),
            DesktopApp("自定义", "", "+", "使用下方输入的包名")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (VrRuntimeDetector.shouldUseVr(this)) {
            startActivity(Intent(this, VrDesktopActivity::class.java))
            finish()
            return
        }
        corePlanner = ContainerCorePlanner(packageName)
        niriManager = NiriStyleWindowManager(dp(18), dp(340), dp(330))
        multiWindowLauncher = MultiWindowLauncher(this)
        setContentView(buildDesktopShell())
        desktopLayer.post {
            addWindow(apps.first(), DisplayMode.PrivilegedVirtualDisplay, "VirtualDisplay + SurfaceView 会话；优先使用无线 ADB shell core 启动并转发输入。")
            updateConsole(buildNiriIntro())
        }
    }

    private fun buildDesktopShell(): View {
        val root = FrameLayout(this).apply { background = DesktopWallpaperDrawable() }
                val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        root.addView(main, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        main.addView(buildTopBar())
        desktopLayer = FrameLayout(this).apply {
            background = Material3Tokens.surface(Color.argb(108, 16, 20, 28), dp(28), Color.argb(68, 255, 255, 255), 1)
            clipToPadding = false
                        setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        main.addView(desktopLayer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                        topMargin = dp(5)
            bottomMargin = dp(5)
        })
        desktopLayer.addView(buildScrollableWorkspace(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        main.addView(buildDock())
        launcherPanel = buildLauncherPanel()
        root.addView(launcherPanel)
        wirelessGuidePanel = buildWirelessGuidePanel()
        root.addView(wirelessGuidePanel)
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                titleBlock.addView(text("Androiddesktop", 20f, bold = true, color = Material3Tokens.OnSurface))
        titleBlock.addView(text("niri-like tiling · privileged display core", 9f, color = Material3Tokens.OnSurfaceVariant))
        bar.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(chip("Scrollable columns"))
        bar.addView(chip("No resize on open"))
        bar.addView(chip("Normal mode"))
        return bar
    }

    private fun buildScrollableWorkspace(): View {
                workspaceScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
                        setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        columnStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            setPadding(dp(2), dp(2), dp(460), dp(2))
        }
        workspaceScroll.addView(columnStrip, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return workspaceScroll
    }

    private fun buildDock(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
                        background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(24), Color.argb(64, 255, 255, 255), 1)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        row.addView(dockButton("启动台", "◉") { toggleLauncher() })
        row.addView(dockButton("核心", "◆") { updateConsole(corePlanner.principle()) })
        row.addView(dockButton("会话", "▤") { showCoreSessionContract() })
        row.addView(dockButton("无线导引", "⇄") { showWirelessDebugGuide() })
        row.addView(dockButton("左移", "‹") { focusPreviousColumn() })
        row.addView(dockButton("右移", "›") { focusNextColumn() })
        row.addView(dockButton("脚本", "⌁") { copyCoreCommands() })
        apps.take(5).forEach { app ->
            row.addView(appDockButton(app) { addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "追加到 niri-like 横向滚动列。") })
        }
        return scroll
    }

    private fun buildLauncherPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainerHigh, dp(28), Color.argb(70, 255, 255, 255), 1)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = 18f
        }
        panel.layoutParams = FrameLayout.LayoutParams(dp(370), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(20)
            bottomMargin = dp(92)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("启动台", 20f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(smallButton("关闭") { toggleLauncher(false) })
        panel.addView(header)
        packageInput = EditText(this).apply {
            hint = "输入目标包名，例如 com.android.settings"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setText("com.android.settings")
            setTextColor(Material3Tokens.OnSurface)
            setHintTextColor(Material3Tokens.OnSurfaceVariant)
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(18), Material3Tokens.Outline, 1)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    addCustomWindow()
                    true
                } else false
            }
        }
        panel.addView(packageInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
            bottomMargin = dp(12)
        })
        val grid = GridLayout(this).apply { columnCount = 3; rowCount = 2 }
        apps.forEach { app -> grid.addView(launcherTile(app), ViewGroup.LayoutParams(dp(106), dp(98))) }
        panel.addView(grid)
                panel.addView(smallButton("复制无线调试/核心脚本") { copyCoreCommands() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return panel
    }

    private fun buildWirelessGuidePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainerHigh, dp(28), Color.argb(92, 255, 255, 255), 1)
            setPadding(dp(22), dp(20), dp(22), dp(20))
            elevation = 24f
        }
        panel.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER).apply {
            leftMargin = dp(44)
            rightMargin = dp(44)
            topMargin = dp(34)
            bottomMargin = dp(34)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("无线调试与特权核心", 22f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(smallButton("复制全部") {
            val guide = wirelessGuideContent()
            copyText("Androiddesktop wireless debugging guide", guide)
            Toast.makeText(this, "无线调试导引已复制", Toast.LENGTH_SHORT).show()
        })
        header.addView(smallButton("关闭") { toggleWirelessGuide(false) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        panel.addView(header)
        panel.addView(text("按顺序完成：开发者选项 → 无线 ADB 配对 → 启动 shell core → VirtualDisplay 会话 → dumpsys/截图验证。", 12f, color = Material3Tokens.OnSurfaceVariant).apply { setPadding(0, dp(8), 0, dp(10)) })
        wirelessGuideText = text(wirelessGuideContent(), 12f, color = Material3Tokens.OnSurface).apply {
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.18f)
        }
        val scroll = ScrollView(this).apply {
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(20), Material3Tokens.Outline, 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(wirelessGuideText)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return panel
    }

    private fun wirelessGuideContent(): String {
        val targetPackage = if (::packageInput.isInitialized) packageInput.text.toString().trim().ifEmpty { "com.android.settings" } else "com.android.settings"
        return WirelessDebugGuide.guide(packageName, targetPackage, Rect(dp(80), dp(80), dp(920), dp(720)))
    }

    private fun addCustomWindow() {
        val pkg = packageInput.text.toString().trim().ifEmpty { "com.android.settings" }
        addWindow(DesktopApp("自定义", pkg, "+", "用户输入目标包"), DisplayMode.PrivilegedVirtualDisplay, "等待特权核心把 $pkg 启动到容器显示会话。")
        toggleLauncher(false)
    }

    private fun addWindow(app: DesktopApp, mode: DisplayMode, note: String) {
        val id = windowSeq++
        val state = niriManager.addWindow(id, app)
        val bounds = state.focusedColumn?.bounds ?: Rect(0, 0, dp(340), dp(330))
        val window = DesktopWindow(id, app, bounds, mode, note)
        val view = buildColumnView(window)
        columnViews[id] = view
        columnStrip.addView(view)
        NiriWindowMotion.enterColumn(view)
        applyWorkspaceState(state)
        updateConsole(corePlanner.sessionBlueprint(app.packageName.ifEmpty { packageInput.text.toString() }, bounds) + "\n" + niriManager.describe())
        toggleLauncher(false)
    }

    private fun buildColumnView(window: DesktopWindow): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(24), Color.argb(72, 255, 255, 255), 1)
            elevation = 12f
            tag = window.id
            setOnClickListener { focusColumn(window.id) }
        }
                card.layoutParams = LinearLayout.LayoutParams(dp(340), LinearLayout.LayoutParams.MATCH_PARENT).apply {
            leftMargin = dp(4)
            rightMargin = dp(6)
            topMargin = dp(2)
            bottomMargin = dp(4)
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(3), dp(6), dp(3))
            background = Material3Tokens.surface(Material3Tokens.PrimaryContainer, dp(18))
        }
        title.addView(text("${window.app.glyph}  ${window.app.label}", 13f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        title.addView(windowAction("▤") {
            focusColumn(window.id)
            updateConsole(corePlanner.sessionBlueprint(window.app.packageName.ifEmpty { packageInput.text.toString() }, window.bounds))
        })
        title.addView(windowAction("⌁") {
            focusColumn(window.id)
            val commands = corePlanner.coreLaunchScript(window.app.packageName.ifEmpty { packageInput.text.toString() }, window.bounds)
            copyText("Androiddesktop core bootstrap", commands)
            updateConsole(commands)
        })
        title.addView(windowAction("↔") { toggleFloating(window.id) })
        title.addView(windowAction("×") { removeColumn(window.id) })
        card.addView(title)

                val surface = FrameLayout(this).apply {
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(20), Color.argb(54, 157, 202, 255), 1)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToOutline = true
        }
        val targetPackage = window.app.packageName.trim()
        if (targetPackage.isNotEmpty()) {
                        val embedded = EmbeddedAppSurfaceView(this, targetPackage, multiWindowLauncher) { state ->
                if (state.launched && ::launcherPanel.isInitialized && launcherPanel.visibility == View.VISIBLE) {
                    toggleLauncher(false)
                }
                updateConsole(buildString {
                    appendLine("== Embedded display session ==")
                    appendLine("windowId=${window.id}")
                    appendLine("target=$targetPackage")
                    appendLine("displayId=${state.displayId ?: "<none>"}")
                    appendLine("coreConnected=${state.coreConnected}")
                    appendLine("launched=${state.launched}")
                    appendLine("message=${state.message}")
                    appendLine()
                    append(corePlanner.readinessReport().toHumanReport())
                })
            }
            embeddedSessions[window.id] = embedded
            surface.addView(embedded, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        } else {
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            body.addView(iconView(window.app, 62))
            body.addView(text("请输入有效包名后再创建窗口", 12f, color = Material3Tokens.Warning))
            surface.addView(body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
                card.addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(4)
            leftMargin = dp(4)
            rightMargin = dp(4)
            bottomMargin = dp(4)
        })
        return card
    }

    private fun focusColumn(id: Int) {
        applyWorkspaceState(niriManager.focusWindow(id))
        updateConsole(niriManager.describe())
    }

        private fun removeColumn(id: Int) {
        embeddedSessions.remove(id)?.releaseSession()
        columnViews.remove(id)?.let { columnStrip.removeView(it) }
        applyWorkspaceState(niriManager.removeWindow(id))
        updateConsole(niriManager.describe())
    }

    private fun toggleFloating(id: Int) {
        val state = niriManager.toggleFloating(id)
        columnViews[id]?.let { NiriWindowMotion.pulse(it) }
        applyWorkspaceState(state)
        updateConsole("Floating toggled for window $id.\nFloating is a utility state and does not affect the scrollable tiling strip yet.\n\n${niriManager.describe()}")
    }

    private fun focusNextColumn() {
        applyWorkspaceState(niriManager.focusNext())
        updateConsole(niriManager.describe())
    }

    private fun focusPreviousColumn() {
        applyWorkspaceState(niriManager.focusPrevious())
        updateConsole(niriManager.describe())
    }

    private fun applyWorkspaceState(state: NiriWorkspaceState) {
        state.columns.forEach { column ->
            columnViews[column.windowId]?.let { NiriWindowMotion.focusColumn(it, column.focused) }
        }
        NiriWindowMotion.smoothScrollTo(workspaceScroll, state.scrollX)
    }

    private fun toggleLauncher(forceVisible: Boolean? = null) {
        val show = forceVisible ?: launcherPanel.visibility != View.VISIBLE
        if (show) {
            launcherPanel.visibility = View.VISIBLE
            launcherPanel.translationY = dp(20).toFloat()
            launcherPanel.animate().alpha(1f).translationY(0f).setDuration(180).start()
        } else {
            launcherPanel.animate().alpha(0f).translationY(dp(20).toFloat()).setDuration(140).withEndAction { launcherPanel.visibility = View.GONE }.start()
        }
    }

    private fun showCoreSessionContract() {
        val targetPackage = if (::packageInput.isInitialized) packageInput.text.toString() else "com.android.settings"
        updateConsole(corePlanner.sessionBlueprint(targetPackage, Rect(dp(80), dp(80), dp(920), dp(720))) + "\n" + niriManager.describe())
    }

    private fun copyCoreCommands() {
        val targetPackage = if (::packageInput.isInitialized) packageInput.text.toString() else "com.android.settings"
        val commands = corePlanner.coreLaunchScript(targetPackage, Rect(dp(80), dp(80), dp(920), dp(720)))
        copyText("Androiddesktop privileged core bootstrap", commands)
        updateConsole(commands)
        Toast.makeText(this, "无线调试/核心脚本已复制", Toast.LENGTH_SHORT).show()
    }

        private fun showWirelessDebugGuide() {
        val guide = wirelessGuideContent()
        wirelessGuideText.text = guide
        updateConsole("无线调试导引已打开。完成配对并启动 shell core 后，重新创建窗口即可优先使用特权 display launch + 输入转发。")
        toggleWirelessGuide(true)
    }

    private fun toggleWirelessGuide(show: Boolean) {
        if (!::wirelessGuidePanel.isInitialized) return
        if (show) {
            toggleLauncher(false)
            wirelessGuidePanel.visibility = View.VISIBLE
            wirelessGuidePanel.scaleX = 0.97f
            wirelessGuidePanel.scaleY = 0.97f
            wirelessGuidePanel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        } else {
            wirelessGuidePanel.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(160).withEndAction {
                wirelessGuidePanel.visibility = View.GONE
            }.start()
        }
    }

    private fun updateConsole(value: String) {
        if (!::console.isInitialized) console = TextView(this)
        if (console.parent == null) {
            val scroll = ScrollView(this).apply {
                background = Material3Tokens.surface(Material3Tokens.SurfaceContainerHigh, dp(18), Color.argb(50, 255, 255, 255), 1)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                addView(console)
            }
            desktopLayer.addView(scroll, FrameLayout.LayoutParams(dp(430), dp(190), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(10)
                bottomMargin = dp(10)
            })
        }
        console.setTextColor(Material3Tokens.OnSurfaceVariant)
        console.textSize = 11f
        console.setTextIsSelectable(true)
        console.text = value
        console.alpha = 0.25f
        console.animate().alpha(1f).setDuration(160).start()
    }

    private fun launcherTile(app: DesktopApp): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = Material3Tokens.ripple(Material3Tokens.Surface, dp(22))
            setOnClickListener {
                if (app.packageName.isEmpty()) addCustomWindow() else addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "从启动台追加到 niri-like 列。")
                toggleLauncher(false)
            }
        }
        tile.addView(iconView(app, 38))
        tile.addView(text(app.label, 12f, bold = true, color = Material3Tokens.OnSurface).apply { gravity = Gravity.CENTER })
        tile.addView(text(app.description, 9f, color = Material3Tokens.OnSurfaceVariant).apply { gravity = Gravity.CENTER })
        return tile
    }

    private fun appDockButton(app: DesktopApp, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
                        background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(18))
            setPadding(dp(5), dp(3), dp(5), dp(3))
            setOnClickListener {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onClick()
                }.start()
            }
        }
                box.addView(iconView(app, 24))
        box.addView(text(app.label, 8f, color = Material3Tokens.OnSurfaceVariant))
        box.layoutParams = LinearLayout.LayoutParams(dp(62), dp(48)).apply { rightMargin = dp(5) }
        return box
    }

    private fun dockButton(label: String, glyph: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
                        background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(18))
            setPadding(dp(6), dp(3), dp(6), dp(3))
            setOnClickListener {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onClick()
                }.start()
            }
        }
                box.addView(text(glyph, 16f, bold = true, color = Material3Tokens.OnSurface))
        box.addView(text(label, 8f, color = Material3Tokens.OnSurfaceVariant))
        box.layoutParams = LinearLayout.LayoutParams(dp(68), dp(48)).apply { rightMargin = dp(5) }
        return box
    }

    private fun iconView(app: DesktopApp, sizeDp: Int): ImageView = ImageView(this).apply {
        setImageDrawable(resolveAppIcon(app))
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = app.label
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { bottomMargin = dp(4) }
    }

        private fun resolveAppIcon(app: DesktopApp): Drawable {
        val pkg = app.packageName
        if (pkg.isNotEmpty()) {
            runCatching { return packageManager.getApplicationIcon(pkg) }
        }
        return DefaultAppIconDrawable(app.label, app.glyph, app.packageName)
    }

    private fun resolveLauncherPackage(vararg candidates: String): String =
        candidates.firstOrNull { candidate ->
            runCatching { packageManager.getLaunchIntentForPackage(candidate) != null }.getOrDefault(false)
        } ?: candidates.firstOrNull().orEmpty()

    private fun smallButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        setTextColor(Material3Tokens.OnSurface)
        background = Material3Tokens.ripple(Material3Tokens.PrimaryContainer, dp(18))
        setOnClickListener { onClick() }
    }

    private fun smallButton(label: String, onClick: () -> Unit, params: LinearLayout.LayoutParams): Button = smallButton(label, onClick).apply { layoutParams = params }

    private fun windowAction(label: String, onClick: () -> Unit): TextView = text(label, 14f, bold = true, color = Material3Tokens.OnSurface).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.ripple(Color.argb(40, 255, 255, 255), dp(14))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(30)).apply { leftMargin = dp(6) }
    }

        private fun chip(label: String): TextView = text(label, 9f, bold = true, color = Material3Tokens.OnSurface).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.surface(Material3Tokens.TertiaryContainer, dp(14))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(5) }
    }

    private fun text(value: String, size: Float, bold: Boolean = false, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun buildNiriIntro(): String = buildString {
        appendLine("== Androiddesktop niri-like target ==")
        appendLine("1. Scrollable tiling columns: windows live on a horizontal strip.")
        appendLine("2. Opening a new app appends a column and does not resize existing columns.")
        appendLine("3. Focus moves by smooth horizontal scrolling plus scale/glow animations.")
        appendLine("4. Floating is a utility flag outside the main tiling model.")
        appendLine("5. VR UI is isolated in VrDesktopActivity and only opens on VR/XR-capable devices.")
        appendLine("6. Default wallpaper and fallback app icons are drawn locally; no external assets are bundled.")
        appendLine("7. Wireless debugging guide is available from the Dock and copied for adb execution.")
        appendLine()
        append(niriManager.describe())
    }

        override fun onDestroy() {
        embeddedSessions.values.toList().forEach { it.releaseSession() }
        embeddedSessions.clear()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
