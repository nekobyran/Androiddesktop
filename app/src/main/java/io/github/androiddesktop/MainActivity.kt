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
import android.util.Log
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast


class MainActivity : Activity() {
    private lateinit var rootLayer: FrameLayout
    private lateinit var desktopLayer: FrameLayout
    private lateinit var workspaceScroll: HorizontalScrollView
    private lateinit var columnStrip: LinearLayout
        private lateinit var console: TextView
    private lateinit var consolePanel: View
    private lateinit var packageInput: EditText
        private lateinit var launcherPanel: View
    private lateinit var toolsPanel: View
    private lateinit var scalePanel: View
    private lateinit var scaleValueView: TextView
    private lateinit var wirelessGuidePanel: WirelessDebugGuidePanel

    private lateinit var homeStatusChip: TextView
    private val homeRoleRequestCode = 4102
    private val layoutMetrics by lazy { AdaptiveDesktopMetrics.from(this) }
    private var lastConsoleText = ""


    private lateinit var corePlanner: ContainerCorePlanner
    private lateinit var niriManager: NiriStyleWindowManager
    private lateinit var multiWindowLauncher: MultiWindowLauncher
    private var windowSeq = 1
        private val columnViews = linkedMapOf<Int, View>()
    private val embeddedSessions = linkedMapOf<Int, EmbeddedAppSurfaceView>()
    private val iconCache = linkedMapOf<String, Drawable>()


        private val apps by lazy {
                listOf(
            DesktopApp("设置", resolveLauncherPackage("com.android.settings"), "⚙", "系统设置，基础承载基线"),
            DesktopApp("番茄小说", resolveLauncherPackage("com.dragon.read"), "阅", "阅读类主基准：长列表、图片、文字与持续交互"),
            DesktopApp("华为阅读", resolveLauncherPackage("com.huawei.hwread.dz", "com.huawei.hwireader"), "书", "设备原生阅读类对照基准"),
            DesktopApp("文件", resolveLauncherPackage("com.huawei.filemanager", "com.google.android.documentsui", "com.android.documentsui"), "▣", "真实文件管理器多窗口基准"),
            DesktopApp("浏览器", resolveLauncherPackage("com.huawei.browser", "com.android.chrome"), "◎", "网页滚动与复杂绘制基准"),
            DesktopApp("图库", resolveLauncherPackage("com.huawei.photos", "com.android.gallery3d", "com.google.android.apps.photos"), "◧", "图片内容与手势基准"),
            DesktopApp("计算器", resolveLauncherPackage("com.huawei.calculator", "com.android.calculator2", "com.google.android.calculator"), "＋", "轻量对照基准"),
            DesktopApp("自定义", "", "+", "使用下方输入的包名")
        )
    }

            override fun attachBaseContext(newBase: Context) {
        val wrapped = GlobalUiScale.wrap(newBase)
        Log.i(
            "AndroiddesktopScale",
            "attach baseDpi=${newBase.resources.configuration.densityDpi} wrapped=${GlobalUiScale.summary(wrapped)}"
        )
        super.attachBaseContext(wrapped)
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (VrRuntimeDetector.shouldUseVr(this)) {
            startActivity(Intent(this, VrDesktopActivity::class.java))
            finish()
            return
        }
                corePlanner = ContainerCorePlanner(packageName, GlobalUiScale.effectiveDensityDpi(this))

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
        rootLayer = root
                val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(layoutMetrics.outerHorizontalDp),
                dp(layoutMetrics.outerVerticalDp),
                dp(layoutMetrics.outerHorizontalDp),
                dp(layoutMetrics.outerVerticalDp)
            )
        }

        root.addView(main, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        main.addView(buildTopBar())
                desktopLayer = FrameLayout(this).apply {
            // The desktop is the wallpaper/workspace itself, not a giant card.
            // Window surfaces provide their own hierarchy and elevation.
            clipToPadding = false
            setPadding(0, 0, 0, 0)
        }
        main.addView(desktopLayer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
        })
                desktopLayer.addView(buildScrollableWorkspace(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
                main.addView(buildDock())
        // Heavy hidden surfaces are created on first use. This keeps startup
        // focused on the visible desktop and measurably reduces the initial
        // view tree / package-icon work.
                return root
    }



        private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (layoutMetrics.compactPhone) 36 else 44)
        }
        if (layoutMetrics.compactPhone) {
            bar.addView(
                                text("Androiddesktop · 桌面容器", layoutMetrics.topTitleSp, bold = true, color = Material3Tokens.OnSurface),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            val status = HomeRoleController.status(this)
            homeStatusChip = chip(if (status.isDefaultHome) "⌂ ✓" else "⌂").apply {
                background = Material3Tokens.ripple(Material3Tokens.TertiaryContainer, dp(14))
                contentDescription = if (status.isDefaultHome) {
                    "Androiddesktop 当前已是桌面主屏幕应用"
                } else {
                    "设置 Androiddesktop 为桌面主屏幕应用"
                }
                AppMotion.installPressFeedback(this)
                setOnClickListener { requestHomeRole() }
            }
            bar.addView(homeStatusChip)
        } else {
            val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            titleBlock.addView(text("Androiddesktop", layoutMetrics.topTitleSp, bold = true, color = Material3Tokens.OnSurface))
                        titleBlock.addView(text("桌面容器 · 50%–125% 全局缩放（当前 ${GlobalUiScale.percent(this)}%）· 无线调试 · 可设为主屏幕", 9f, color = Material3Tokens.OnSurfaceVariant))
            bar.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            bar.addView(chip("Scrollable columns"))
            bar.addView(chip("No resize on open"))
            homeStatusChip = chip(HomeRoleController.status(this).compactLabel).apply {
                background = Material3Tokens.ripple(Material3Tokens.TertiaryContainer, dp(14))
                contentDescription = "设置 Androiddesktop 为桌面主屏幕应用"
                AppMotion.installPressFeedback(this)
                setOnClickListener { requestHomeRole() }
            }
            bar.addView(homeStatusChip)
        }
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
            setPadding(dp(if (layoutMetrics.compactPhone) 3 else 4), dp(3), dp(if (layoutMetrics.compactPhone) 3 else 4), dp(3))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        row.addView(dockButton("启动台", "◉") { toggleLauncher() })
        if (layoutMetrics.compactPhone) {
                        row.addView(dockButton("左移", "‹") { focusPreviousColumn() })
            row.addView(dockButton("右移", "›") { focusNextColumn() })
            row.addView(dockButton("缩放", "↕") { toggleScalePanel() })
            row.addView(dockButton("工具", "⋯") { toggleToolsPanel() })

            listOfNotNull(apps.getOrNull(0), apps.getOrNull(1), apps.getOrNull(3)).forEach { app ->
                row.addView(appDockButton(app) { addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "从紧凑 Dock 追加到 niri-like 横向滚动列。") })
            }
        } else {
            row.addView(dockButton("核心", "◆") { showDiagnosticConsole(corePlanner.principle()) })
            row.addView(dockButton("会话", "▤") { showCoreSessionContract(); showDiagnosticConsole() })
                        row.addView(dockButton("性能", "⌁") { showPerformanceSnapshot(); showDiagnosticConsole() })
            row.addView(dockButton("缩放", "↕") { toggleScalePanel() })
            row.addView(dockButton("无线导引", "⇄") { showWirelessDebugGuide() })

            row.addView(dockButton("设为桌面", "⌂") { requestHomeRole() })
            row.addView(dockButton("左移", "‹") { focusPreviousColumn() })
            row.addView(dockButton("右移", "›") { focusNextColumn() })
            row.addView(dockButton("脚本", "⌁") { copyCoreCommands() })
            apps.take(5).forEach { app ->
                row.addView(appDockButton(app) { addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "追加到 niri-like 横向滚动列。") })
            }
        }
        return scroll
    }


    private fun buildLauncherPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
                        background = Material3Tokens.surface(Material3Tokens.SurfaceContainerHigh, dp(28), Color.argb(70, 255, 255, 255), 1)
            setPadding(
                dp(layoutMetrics.launcherPaddingDp),
                dp(layoutMetrics.launcherPaddingDp),
                dp(layoutMetrics.launcherPaddingDp),
                dp(layoutMetrics.launcherPaddingDp)
            )
            elevation = 18f
        }
        panel.layoutParams = FrameLayout.LayoutParams(dp(layoutMetrics.launcherWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(if (layoutMetrics.compactPhone) 8 else 20)
            bottomMargin = dp(if (layoutMetrics.compactPhone) 52 else 64)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
                header.addView(text("启动台", if (layoutMetrics.compactPhone) 17f else 20f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

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
                        setPadding(dp(if (layoutMetrics.compactPhone) 10 else 14), dp(if (layoutMetrics.compactPhone) 7 else 10), dp(if (layoutMetrics.compactPhone) 10 else 14), dp(if (layoutMetrics.compactPhone) 7 else 10))

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    addCustomWindow()
                    true
                } else false
            }
        }
                panel.addView(packageInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(if (layoutMetrics.compactPhone) 8 else 14)
            bottomMargin = dp(if (layoutMetrics.compactPhone) 8 else 12)
        })
        val grid = GridLayout(this).apply { columnCount = 4; rowCount = (apps.size + 3) / 4 }
        apps.forEach { app ->
            grid.addView(
                launcherTile(app),
                ViewGroup.LayoutParams(dp(layoutMetrics.launcherTileWidthDp), dp(layoutMetrics.launcherTileHeightDp))
            )
        }

        panel.addView(grid)
        panel.addView(smallButton("复制无线调试/核心脚本") { copyCoreCommands() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(if (layoutMetrics.compactPhone) 8 else 12) })

        return panel
    }

        private fun ensureLauncherPanel() {
        if (::launcherPanel.isInitialized) return
        launcherPanel = buildLauncherPanel()
        rootLayer.addView(launcherPanel)
    }

    private fun buildToolsPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = Material3Tokens.surface(
                Material3Tokens.SurfaceContainerHigh,
                dp(24),
                Color.argb(70, 255, 255, 255),
                1
            )
            setPadding(dp(12), dp(10), dp(12), dp(12))
            elevation = dp(16).toFloat()
        }
        panel.layoutParams = FrameLayout.LayoutParams(
            dp(if (layoutMetrics.compactPhone) 312 else 360),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            rightMargin = dp(if (layoutMetrics.compactPhone) 8 else 18)
            bottomMargin = dp(if (layoutMetrics.compactPhone) 52 else 64)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("工具", if (layoutMetrics.compactPhone) 16f else 18f, bold = true, color = Material3Tokens.OnSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(smallButton("关闭") { toggleToolsPanel(false) })
        panel.addView(header)

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 3
        }
        val tileWidth = if (layoutMetrics.compactPhone) 140 else 164
        val tileHeight = if (layoutMetrics.compactPhone) 52 else 58
        fun addTool(label: String, glyph: String, action: () -> Unit) {
            grid.addView(toolTile(label, glyph, action), ViewGroup.LayoutParams(dp(tileWidth), dp(tileHeight)))
        }
        addTool("核心", "◆") {
            toggleToolsPanel(false)
            showDiagnosticConsole(corePlanner.principle())
        }
        addTool("会话", "▤") {
            toggleToolsPanel(false)
            showCoreSessionContract()
            showDiagnosticConsole()
        }
        addTool("性能", "⌁") {
            toggleToolsPanel(false)
            showPerformanceSnapshot()
            showDiagnosticConsole()
        }
        addTool("无线调试", "⇄") {
            toggleToolsPanel(false)
            showWirelessDebugGuide()
        }
        addTool("桌面角色", "⌂") {
            toggleToolsPanel(false)
            requestHomeRole()
        }
        addTool("复制脚本", "⧉") {
            toggleToolsPanel(false)
            copyCoreCommands()
        }
        panel.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        return panel
    }

    private fun ensureToolsPanel() {
        if (::toolsPanel.isInitialized) return
        toolsPanel = buildToolsPanel()
        rootLayer.addView(toolsPanel)
    }

        private fun toggleToolsPanel(forceVisible: Boolean? = null) {
        val currentlyVisible = ::toolsPanel.isInitialized && toolsPanel.visibility == View.VISIBLE
        val show = forceVisible ?: !currentlyVisible
        if (show) {
            ensureToolsPanel()
            toggleLauncher(false)
            toggleScalePanel(false)
            toggleConsolePanel(false)
            toolsPanel.post { AppMotion.showPopover(toolsPanel, fromBottomStart = false) }
        } else if (currentlyVisible) {
            AppMotion.hidePopover(toolsPanel) { }
        }
    }

    private fun buildScalePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = Material3Tokens.surface(
                Material3Tokens.SurfaceContainerHigh,
                dp(24),
                Color.argb(70, 255, 255, 255),
                1
            )
            setPadding(dp(14), dp(12), dp(14), dp(14))
            elevation = dp(16).toFloat()
        }
        panel.layoutParams = FrameLayout.LayoutParams(
            dp(if (layoutMetrics.compactPhone) 330 else 390),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            rightMargin = dp(if (layoutMetrics.compactPhone) 8 else 18)
            bottomMargin = dp(if (layoutMetrics.compactPhone) 52 else 64)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("全局缩放", if (layoutMetrics.compactPhone) 16f else 18f, bold = true, color = Material3Tokens.OnSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(smallButton("关闭") { toggleScalePanel(false) })
        panel.addView(header)

        scaleValueView = text(
            GlobalUiScale.summary(this),
            if (layoutMetrics.compactPhone) 11f else 12f,
            bold = true,
            color = Material3Tokens.Primary
        ).apply {
            setPadding(0, dp(8), 0, dp(2))
        }
        panel.addView(scaleValueView)
        panel.addView(
            text(
                "改变的是 Androiddesktop 的逻辑 DPI，不修改系统 DPI；桌面壳和 VirtualDisplay 会统一缩放。",
                if (layoutMetrics.compactPhone) 9.5f else 10.5f,
                color = Material3Tokens.OnSurfaceVariant
            )
        )

        val current = GlobalUiScale.percent(this)
        val slider = SeekBar(this).apply {
            max = (GlobalUiScale.MaxPercent - GlobalUiScale.MinPercent) / GlobalUiScale.StepPercent
            progress = (current - GlobalUiScale.MinPercent) / GlobalUiScale.StepPercent
            contentDescription = "androiddesktop-global-scale-slider"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = GlobalUiScale.MinPercent + progress * GlobalUiScale.StepPercent
                    scaleValueView.text = "$value% · 松手应用"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val value = GlobalUiScale.MinPercent + (seekBar?.progress ?: 0) * GlobalUiScale.StepPercent
                    applyGlobalScale(value)
                }
            })
        }
        panel.addView(slider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        presets.addView(smallButton("−5%") {
            applyGlobalScale(GlobalUiScale.percent(this) - GlobalUiScale.StepPercent)
        }.apply { contentDescription = "androiddesktop-scale-minus" })
        presets.addView(smallButton("桌面 60%") {
            applyGlobalScale(GlobalUiScale.DesktopPresetPercent)
        }.apply { contentDescription = "androiddesktop-scale-desktop" })
        presets.addView(smallButton("系统 100%") {
            applyGlobalScale(GlobalUiScale.SystemPercent)
        }.apply { contentDescription = "androiddesktop-scale-system" })
        presets.addView(smallButton("+5%") {
            applyGlobalScale(GlobalUiScale.percent(this) + GlobalUiScale.StepPercent)
        }.apply { contentDescription = "androiddesktop-scale-plus" })
        panel.addView(presets, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        return panel
    }

    private fun ensureScalePanel() {
        if (::scalePanel.isInitialized) return
        scalePanel = buildScalePanel()
        rootLayer.addView(scalePanel)
    }

    private fun toggleScalePanel(forceVisible: Boolean? = null) {
        val currentlyVisible = ::scalePanel.isInitialized && scalePanel.visibility == View.VISIBLE
        val show = forceVisible ?: !currentlyVisible
        if (show) {
            ensureScalePanel()
            scaleValueView.text = GlobalUiScale.summary(this)
            toggleLauncher(false)
            toggleToolsPanel(false)
            toggleConsolePanel(false)
            scalePanel.post { AppMotion.showPopover(scalePanel, fromBottomStart = false) }
        } else if (currentlyVisible) {
            AppMotion.hidePopover(scalePanel) { }
        }
    }

    private fun applyGlobalScale(requestedPercent: Int) {
        val current = GlobalUiScale.percent(this)
        val target = GlobalUiScale.setPercent(this, requestedPercent)
        if (target == current) {
            if (::scaleValueView.isInitialized) scaleValueView.text = GlobalUiScale.summary(this)
            return
        }
        Toast.makeText(this, "全局缩放 $target% · 正在按新 DPI 重建桌面", Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun buildWirelessGuidePanel(): WirelessDebugGuidePanel = WirelessDebugGuidePanel(

        host = this,
        hostPackage = packageName,
        targetPackageProvider = {
            if (::packageInput.isInitialized) packageInput.text.toString().trim().ifEmpty { "com.dragon.read" } else "com.dragon.read"
        },
        sessionSummaryProvider = { sessionSummary() },
        onClose = { toggleWirelessGuide(false) },
                onMessage = { updateConsole(it) }
    )

    private fun ensureWirelessGuidePanel() {
        if (::wirelessGuidePanel.isInitialized) return
        wirelessGuidePanel = buildWirelessGuidePanel()
        rootLayer.addView(wirelessGuidePanel)
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
                                if (::wirelessGuidePanel.isInitialized && wirelessGuidePanel.visibility == View.VISIBLE) {
                    wirelessGuidePanel.refreshStatus()
                }
                updateConsole(buildString {
                    appendLine("== Embedded display session ==")
                    appendLine("windowId=${window.id}")
                    appendLine("target=$targetPackage")
                    appendLine("displayId=${state.displayId ?: "<none>"}")
                                        appendLine("coreConnected=${state.coreConnected}")
                    appendLine("launched=${state.launched}")
                    appendLine("displayCreateMs=${state.displayCreateMs?.let { "%.2f".format(it) } ?: "<pending>"}")
                    appendLine("sessionReadyMs=${state.sessionReadyMs?.let { "%.2f".format(it) } ?: "<pending>"}")
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
        val currentlyVisible = ::launcherPanel.isInitialized && launcherPanel.visibility == View.VISIBLE
        val show = forceVisible ?: !currentlyVisible
        if (show) {
            ensureLauncherPanel()
            toggleToolsPanel(false)
            toggleConsolePanel(false)
            launcherPanel.post { AppMotion.showPopover(launcherPanel, fromBottomStart = true) }
        } else if (currentlyVisible) {
            AppMotion.hidePopover(launcherPanel) { }
        }
    }



    private fun showCoreSessionContract() {
        val targetPackage = if (::packageInput.isInitialized) packageInput.text.toString() else "com.android.settings"
        updateConsole(corePlanner.sessionBlueprint(targetPackage, Rect(dp(80), dp(80), dp(920), dp(720))) + "\n" + niriManager.describe())
    }

            private fun showPerformanceSnapshot() {
        val snapshot = PerformanceDiagnostics.snapshot(this, embeddedSessions.size)
        val sessions = embeddedSessions.entries.joinToString("\n") { (id, session) ->
            val state = session.currentState
            "window=$id display=${state.displayId ?: "-"} launched=${state.launched} createMs=${state.displayCreateMs?.let { "%.2f".format(it) } ?: "-"} readyMs=${state.sessionReadyMs?.let { "%.2f".format(it) } ?: "-"}"
        }
        updateConsole(snapshot.toHumanReport() + if (sessions.isNotEmpty()) "\n$sessions" else "")
    }

        private fun requestHomeRole() {
        val before = HomeRoleController.status(this)
        if (before.isDefaultHome) {
            showDiagnosticConsole(HomeRoleController.userGuide(this))
            refreshHomeRoleStatus()
            return
        }
        val request = HomeRoleController.requestIntent(this)
        val launched = runCatching {
            @Suppress("DEPRECATION")
            startActivityForResult(request, homeRoleRequestCode)
            true
        }.getOrDefault(false)
        if (!launched) {
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        }
        updateConsole(HomeRoleController.userGuide(this))
        Toast.makeText(this, "请在 Android 系统界面中选择主屏幕应用", Toast.LENGTH_SHORT).show()
    }

    private fun refreshHomeRoleStatus() {
        if (!::homeStatusChip.isInitialized) return
        val status = HomeRoleController.status(this)
        homeStatusChip.text = if (layoutMetrics.compactPhone) {
            if (status.isDefaultHome) "⌂ ✓" else "⌂"
        } else {
            status.compactLabel
        }
        homeStatusChip.contentDescription = if (status.isDefaultHome) {
            "Androiddesktop 当前已是桌面主屏幕应用"
        } else {
            "设置 Androiddesktop 为桌面主屏幕应用"
        }
    }



    private fun sessionSummary(): String {
        val total = embeddedSessions.size
        val ready = embeddedSessions.values.count { it.currentState.launched }
        return when {
            total == 0 -> "尚未创建窗口"
            ready == total -> "$ready/$total 会话已就绪"
            else -> "$ready/$total 会话已就绪 · 其余等待 core"
        }
    }

    private fun copyCoreCommands() {

        val targetPackage = if (::packageInput.isInitialized) packageInput.text.toString() else "com.android.settings"
        val commands = corePlanner.coreLaunchScript(targetPackage, Rect(dp(80), dp(80), dp(920), dp(720)))
        copyText("Androiddesktop privileged core bootstrap", commands)
        updateConsole(commands)
        Toast.makeText(this, "无线调试/核心脚本已复制", Toast.LENGTH_SHORT).show()
    }

                private fun showWirelessDebugGuide() {
        ensureWirelessGuidePanel()
        wirelessGuidePanel.refreshStatus()
        updateConsole("无线调试向导已打开：按 1→5 完成设备侧设置、电脑配对、认证 core 和真实 VirtualDisplay 验证。普通 APK 只能引导/检测，不能自行绕过系统授权开启无线调试。")
        toggleWirelessGuide(true)
    }

    private fun toggleWirelessGuide(show: Boolean) {
                if (show) {
            ensureWirelessGuidePanel()
            toggleLauncher(false)
            toggleToolsPanel(false)
            toggleConsolePanel(false)
            wirelessGuidePanel.refreshStatus()
            wirelessGuidePanel.post { AppMotion.showModal(wirelessGuidePanel) }

        } else if (::wirelessGuidePanel.isInitialized && wirelessGuidePanel.visibility == View.VISIBLE) {
            AppMotion.hideModal(wirelessGuidePanel) { }
        }
    }


            private fun updateConsole(value: String) {
        lastConsoleText = value
        if (::console.isInitialized && console.text.toString() != value) {
            console.text = value
        }
    }

    private fun buildConsolePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = Material3Tokens.surface(
                Material3Tokens.SurfaceContainerHigh,
                dp(18),
                Color.argb(50, 255, 255, 255),
                1
            )
            setPadding(dp(10), dp(8), dp(10), dp(10))
            elevation = dp(14).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("诊断", if (layoutMetrics.compactPhone) 13f else 14f, bold = true, color = Material3Tokens.OnSurface),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(smallButton("关闭") { toggleConsolePanel(false) })
        panel.addView(header)

        console = TextView(this).apply {
            setTextColor(Material3Tokens.OnSurfaceVariant)
            textSize = if (layoutMetrics.compactPhone) 9.5f else 11f
            setTextIsSelectable(true)
            alpha = 1f
            text = lastConsoleText
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(console)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(6)
        })
        panel.layoutParams = FrameLayout.LayoutParams(
            dp(layoutMetrics.consoleWidthDp),
            dp(layoutMetrics.consoleHeightDp),
            Gravity.BOTTOM or Gravity.END
        ).apply {
            rightMargin = dp(if (layoutMetrics.compactPhone) 6 else 10)
            bottomMargin = dp(if (layoutMetrics.compactPhone) 6 else 10)
        }
        return panel
    }

    private fun ensureConsolePanel() {
        if (::consolePanel.isInitialized) return
        consolePanel = buildConsolePanel()
        desktopLayer.addView(consolePanel)
    }

    private fun showDiagnosticConsole(value: String? = null) {
        if (value != null) updateConsole(value)
        if (lastConsoleText.isBlank()) updateConsole(buildNiriIntro())
        ensureConsolePanel()
        if (console.text.toString() != lastConsoleText) console.text = lastConsoleText
        toggleToolsPanel(false)
        toggleLauncher(false)
        if (consolePanel.visibility != View.VISIBLE) {
            consolePanel.post { AppMotion.showPopover(consolePanel, fromBottomStart = false) }
        }
    }

    private fun toggleConsolePanel(show: Boolean) {
        if (!::consolePanel.isInitialized) return
        if (show) {
            showDiagnosticConsole()
        } else if (consolePanel.visibility == View.VISIBLE) {
            AppMotion.hidePopover(consolePanel) { }
        }
    }



        private fun launcherTile(app: DesktopApp): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(if (layoutMetrics.compactPhone) 5 else 8),
                dp(if (layoutMetrics.compactPhone) 4 else 8),
                dp(if (layoutMetrics.compactPhone) 5 else 8),
                dp(if (layoutMetrics.compactPhone) 4 else 8)
            )
            background = Material3Tokens.ripple(Material3Tokens.Surface, dp(22))
            AppMotion.installPressFeedback(this)
            setOnClickListener {
                if (app.packageName.isEmpty()) addCustomWindow() else addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "从启动台追加到 niri-like 列。")
                toggleLauncher(false)
            }
        }
        tile.addView(iconView(app, layoutMetrics.launcherIconDp))
        tile.addView(text(app.label, if (layoutMetrics.compactPhone) 10.5f else 12f, bold = true, color = Material3Tokens.OnSurface).apply { gravity = Gravity.CENTER })
        if (!layoutMetrics.compactPhone) {
            tile.addView(text(app.description, 9f, color = Material3Tokens.OnSurfaceVariant).apply { gravity = Gravity.CENTER })
        }
        return tile
    }


        private fun appDockButton(app: DesktopApp, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(18))
            setPadding(dp(if (layoutMetrics.compactPhone) 3 else 5), dp(2), dp(if (layoutMetrics.compactPhone) 3 else 5), dp(2))
            AppMotion.installPressFeedback(this)
            setOnClickListener { onClick() }
        }
        box.addView(iconView(app, layoutMetrics.dockIconDp))
        box.addView(text(app.label, layoutMetrics.dockLabelSp, color = Material3Tokens.OnSurfaceVariant))
        box.layoutParams = LinearLayout.LayoutParams(dp(layoutMetrics.dockAppWidthDp), dp(layoutMetrics.dockHeightDp)).apply {
            rightMargin = dp(if (layoutMetrics.compactPhone) 3 else 5)
        }
        return box
    }

        private fun dockButton(label: String, glyph: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(18))
            setPadding(dp(if (layoutMetrics.compactPhone) 3 else 6), dp(2), dp(if (layoutMetrics.compactPhone) 3 else 6), dp(2))
            contentDescription = automationId(label) ?: label
            AppMotion.installPressFeedback(this)
            setOnClickListener { onClick() }
        }

        box.addView(text(glyph, if (layoutMetrics.compactPhone) 15f else 16f, bold = true, color = Material3Tokens.OnSurface))
        box.addView(text(label, layoutMetrics.dockLabelSp, color = Material3Tokens.OnSurfaceVariant))
        box.layoutParams = LinearLayout.LayoutParams(dp(layoutMetrics.dockButtonWidthDp), dp(layoutMetrics.dockHeightDp)).apply {
            rightMargin = dp(if (layoutMetrics.compactPhone) 3 else 5)
        }
        return box
    }

        private fun toolTile(label: String, glyph: String, onClick: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Material3Tokens.ripple(Material3Tokens.Surface, dp(16))
        setPadding(dp(10), dp(4), dp(8), dp(4))
        contentDescription = automationId(label) ?: label
        AppMotion.installPressFeedback(this)

        setOnClickListener { onClick() }
        addView(text(glyph, 15f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(text(label, if (layoutMetrics.compactPhone) 10.5f else 11.5f, bold = true, color = Material3Tokens.OnSurface))
    }


    private fun iconView(app: DesktopApp, sizeDp: Int): ImageView = ImageView(this).apply {
        setImageDrawable(resolveAppIcon(app))
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = app.label
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { bottomMargin = dp(4) }
    }

            private fun resolveAppIcon(app: DesktopApp): Drawable {
        val key = app.packageName.ifEmpty { "fallback:${app.label}:${app.glyph}" }
        iconCache[key]?.let { return it }
        val resolved = if (app.packageName.isNotEmpty()) {
            runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
        } else null
        val drawable = resolved ?: DefaultAppIconDrawable(app.label, app.glyph, app.packageName)
        if (iconCache.size >= 24) iconCache.remove(iconCache.keys.firstOrNull())
        iconCache[key] = drawable
        return drawable
    }


    private fun resolveLauncherPackage(vararg candidates: String): String =
        candidates.firstOrNull { candidate ->
            runCatching { packageManager.getLaunchIntentForPackage(candidate) != null }.getOrDefault(false)
        } ?: candidates.firstOrNull().orEmpty()

            private fun smallButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = if (layoutMetrics.compactPhone) 10.5f else 12f
        isAllCaps = false
        contentDescription = automationId(label) ?: label

        setTextColor(Material3Tokens.OnSurface)
        background = Material3Tokens.ripple(Material3Tokens.PrimaryContainer, dp(18))
        AppMotion.installPressFeedback(this)
        setOnClickListener { onClick() }
    }


    private fun smallButton(label: String, onClick: () -> Unit, params: LinearLayout.LayoutParams): Button = smallButton(label, onClick).apply { layoutParams = params }

        private fun windowAction(label: String, onClick: () -> Unit): TextView = text(label, 14f, bold = true, color = Material3Tokens.OnSurface).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.ripple(Color.argb(40, 255, 255, 255), dp(14))
        AppMotion.installPressFeedback(this)
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

        private fun automationId(label: String): String? = when (label) {
        "启动台" -> "androiddesktop-dock-launcher"
        "左移" -> "androiddesktop-dock-prev"
        "右移" -> "androiddesktop-dock-next"
                "工具" -> "androiddesktop-dock-tools"
        "缩放" -> "androiddesktop-dock-scale"
        "核心" -> "androiddesktop-tool-core"

        "会话" -> "androiddesktop-tool-session"
        "性能" -> "androiddesktop-tool-performance"
        "无线调试", "无线导引" -> "androiddesktop-tool-wireless"
        "桌面角色", "设为桌面" -> "androiddesktop-tool-home"
        "复制脚本", "脚本" -> "androiddesktop-tool-script"
        "关闭" -> "androiddesktop-close"
        else -> null
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

    override fun onResume() {
        super.onResume()
        refreshHomeRoleStatus()
    }

    @Deprecated("Legacy Activity result callback is required for the platform RoleManager consent intent.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == homeRoleRequestCode) {
            refreshHomeRoleStatus()
            updateConsole(
                HomeRoleController.userGuide(this) +
                    "\nroleRequestResult=${if (resultCode == RESULT_OK) "granted" else "not-granted"}"
            )
        }
    }

    override fun onDestroy() {
        embeddedSessions.values.toList().forEach { it.releaseSession() }
        embeddedSessions.clear()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
