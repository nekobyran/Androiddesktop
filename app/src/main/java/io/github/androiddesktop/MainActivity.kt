package io.github.androiddesktop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity

import android.view.View
import android.view.ViewGroup
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
    private lateinit var shellContent: LinearLayout
    private lateinit var desktopLayer: FrameLayout
    private lateinit var workspaceScroll: HorizontalScrollView
    private lateinit var columnStrip: LinearLayout
    private lateinit var console: TextView
    private lateinit var consolePanel: View
    private lateinit var launcherPanel: View
    private lateinit var launcherGrid: GridLayout
    private lateinit var launcherSearchInput: EditText
    private lateinit var launcherCountView: TextView
    private lateinit var dockAppsRow: LinearLayout
    private lateinit var toolsPanel: View
    private lateinit var scalePanel: View
    private lateinit var scaleValueView: TextView
    private lateinit var wirelessGuidePanel: WirelessDebugGuidePanel

    private lateinit var homeStatusChip: TextView
    private val homeRoleRequestCode = 4102
    private val layoutMetrics by lazy { AdaptiveDesktopMetrics.from(this) }
    private val onboardingPrefs by lazy { getSharedPreferences("androiddesktop.onboarding", Context.MODE_PRIVATE) }
    private var lastConsoleText = ""
    private var lastTargetPackage = "com.android.settings"

    private lateinit var corePlanner: ContainerCorePlanner
    private lateinit var niriManager: NiriStyleWindowManager
    private lateinit var multiWindowLauncher: MultiWindowLauncher
    private var windowSeq = 1
    private val columnViews = linkedMapOf<Int, View>()
    private val windowApps = linkedMapOf<Int, DesktopApp>()
    private val embeddedSessions = linkedMapOf<Int, EmbeddedAppSurfaceView>()
    private val iconCache = linkedMapOf<String, Drawable>()

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
        updateConsole(buildNiriIntro())
        desktopLayer.post {
            if (!wirelessSetupComplete()) {
                showWirelessDebugGuide(required = true)
            } else {
                restoreWirelessCore()
            }
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
        shellContent = main

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
            bar.addView(chip("工具").apply {
                contentDescription = "androiddesktop-top-tools"
                AppMotion.installPressFeedback(this)
                setOnClickListener { toggleToolsPanel() }
            })
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
            bar.addView(chip("工具").apply {
                contentDescription = "androiddesktop-top-tools"
                AppMotion.installPressFeedback(this)
                setOnClickListener { toggleToolsPanel() }
            })
            bar.addView(chip("缩放").apply {
                contentDescription = "androiddesktop-top-scale"
                AppMotion.installPressFeedback(this)
                setOnClickListener { toggleScalePanel() }
            })
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
        val holder = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        holder.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(layoutMetrics.dockHeightDp + 16)
        )

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            background = Material3Tokens.surface(
                Color.argb(224, 31, 35, 41),
                dp(24),
                Color.argb(82, 255, 255, 255),
                1
            )
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dockButton("启动台", "◉") { toggleLauncher() })
        dockAppsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(dockAppsRow)
        scroll.addView(row)
        holder.addView(
            scroll,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(layoutMetrics.dockHeightDp + 6), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(10)
            }
        )
        return holder
    }

    private fun refreshDockApps() {
        if (!::dockAppsRow.isInitialized) return
        dockAppsRow.removeAllViews()
        val running = windowApps.values
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
        running.forEach { app ->
            dockAppsRow.addView(appDockButton(app) {
                val id = windowApps.entries.lastOrNull { it.value.packageName == app.packageName }?.key
                if (id != null && columnViews.containsKey(id)) focusColumn(id)
            })
        }
    }

    private fun buildLauncherPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            alpha = 0f
            setBackgroundColor(Color.argb(218, 8, 12, 20))
            setPadding(
                dp(if (layoutMetrics.compactPhone) 28 else 54),
                dp(if (layoutMetrics.compactPhone) 18 else 34),
                dp(if (layoutMetrics.compactPhone) 28 else 54),
                dp(if (layoutMetrics.compactPhone) 20 else 38)
            )
            isClickable = true
            elevation = dp(30).toFloat()
        }
        panel.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text("启动台", if (layoutMetrics.compactPhone) 24f else 30f, bold = true, color = Color.WHITE))
        launcherCountView = text("正在读取系统应用…", if (layoutMetrics.compactPhone) 10f else 12f, color = Color.argb(196, 255, 255, 255))
        titleBlock.addView(launcherCountView)
        header.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(smallButton("关闭") { toggleLauncher(false) })
        panel.addView(header)

        launcherSearchInput = EditText(this).apply {
            hint = "搜索应用"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(164, 255, 255, 255))
            background = Material3Tokens.surface(Color.argb(152, 42, 47, 58), dp(22), Color.argb(72, 255, 255, 255), 1)
            setPadding(dp(16), dp(9), dp(16), dp(9))
            contentDescription = "androiddesktop-launchpad-search"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    refreshLauncherApps(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        panel.addView(
            launcherSearchInput,
            LinearLayout.LayoutParams(dp(if (layoutMetrics.compactPhone) 330 else 430), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
                bottomMargin = dp(10)
            }
        )

                launcherGrid = GridLayout(this).apply {
            columnCount = launcherColumnCount()
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val gridScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(launcherGrid, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        panel.addView(gridScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        panel.addView(
            text("来自 Android PackageManager 的真实可启动 Activity · 点击后创建真实 VirtualDisplay 会话", if (layoutMetrics.compactPhone) 9f else 10.5f, color = Color.argb(176, 255, 255, 255)).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        )
        return panel
    }

    private fun launcherColumnCount(): Int {
        val width = resources.configuration.screenWidthDp
        return when {
            width >= 1200 -> 9
            width >= 1000 -> 8
            width >= 780 -> 7
            else -> 6
        }
    }

    private fun launcherTileWidthDp(): Int {
        val columns = launcherColumnCount()
        val width = resources.configuration.screenWidthDp - if (layoutMetrics.compactPhone) 72 else 126
        return (width / columns).coerceIn(82, 132)
    }

    private fun queryLaunchableApps(): List<DesktopApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(intent, 0)
        return resolved.asSequence()
            .filter { it.activityInfo?.packageName?.isNotBlank() == true }
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = runCatching { info.loadLabel(packageManager).toString().trim() }
                    .getOrNull().orEmpty().ifBlank { pkg.substringAfterLast('.') }
                val glyph = label.firstOrNull()?.toString() ?: "●"
                DesktopApp(label, pkg, glyph, pkg)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun refreshLauncherApps(query: String = "") {
        if (!::launcherGrid.isInitialized) return
        val allApps = queryLaunchableApps()
        val normalized = query.trim().lowercase()
        val apps = if (normalized.isEmpty()) allApps else allApps.filter {
            it.label.lowercase().contains(normalized) || it.packageName.lowercase().contains(normalized)
        }
        launcherGrid.removeAllViews()
        launcherGrid.columnCount = launcherColumnCount()
        val tileWidth = dp(launcherTileWidthDp())
        val tileHeight = dp(if (layoutMetrics.compactPhone) 82 else 104)
        apps.forEach { app ->
            launcherGrid.addView(launcherTile(app), ViewGroup.LayoutParams(tileWidth, tileHeight))
        }
        if (::launcherCountView.isInitialized) {
            launcherCountView.text = if (normalized.isEmpty()) "${allApps.size} 个可启动应用" else "${apps.size} / ${allApps.size} 个应用"
        }
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
        targetPackageProvider = { lastTargetPackage },
        sessionSummaryProvider = { sessionSummary() },
        onClose = { toggleWirelessGuide(false) },
        onSetupComplete = {
            onboardingPrefs.edit().putBoolean("wireless_pairing_complete_v1", true).apply()
            updateConsole("无线调试已完成本机配对，shell core 已就绪。")
        },
        onMessage = { updateConsole(it) }
    )

    private fun ensureWirelessGuidePanel() {
        if (::wirelessGuidePanel.isInitialized) return
        wirelessGuidePanel = buildWirelessGuidePanel()
        rootLayer.addView(wirelessGuidePanel)
    }

    private fun addWindow(app: DesktopApp, mode: DisplayMode, note: String) {
        if (app.packageName.isBlank()) return
        lastTargetPackage = app.packageName
        val id = windowSeq++
        val state = niriManager.addWindow(id, app)
        val bounds = state.focusedColumn?.bounds ?: Rect(0, 0, dp(340), dp(330))
        val window = DesktopWindow(id, app, bounds, mode, note)
        val view = buildColumnView(window)
        columnViews[id] = view
        windowApps[id] = app
        columnStrip.addView(view)
        refreshDockApps()
        NiriWindowMotion.enterColumn(view)
        applyWorkspaceState(state)
        updateConsole(corePlanner.sessionBlueprint(app.packageName, bounds) + "\n" + niriManager.describe())
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
            updateConsole(corePlanner.sessionBlueprint(window.app.packageName, window.bounds))
        })
        title.addView(windowAction("⌁") {
            focusColumn(window.id)
            val commands = corePlanner.coreLaunchScript(window.app.packageName, window.bounds)
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
        windowApps.remove(id)
        refreshDockApps()
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
            toggleScalePanel(false)
            toggleConsolePanel(false)
            refreshLauncherApps(launcherSearchInput.text?.toString().orEmpty())
            applyLauncherBlur(true)
            launcherPanel.post { AppMotion.showLaunchpad(launcherPanel) }
        } else if (currentlyVisible) {
            AppMotion.hideLaunchpad(launcherPanel) { applyLauncherBlur(false) }
        } else if (!show) {
            applyLauncherBlur(false)
        }
    }

    private fun applyLauncherBlur(enabled: Boolean) {
        if (!::shellContent.isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            shellContent.setRenderEffect(
                if (enabled) RenderEffect.createBlurEffect(dp(18).toFloat(), dp(18).toFloat(), Shader.TileMode.CLAMP) else null
            )
        }
        shellContent.alpha = if (enabled) 0.84f else 1f
    }



    private fun showCoreSessionContract() {
        val targetPackage = lastTargetPackage
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

        val targetPackage = lastTargetPackage
        val commands = corePlanner.coreLaunchScript(targetPackage, Rect(dp(80), dp(80), dp(920), dp(720)))
        copyText("Androiddesktop privileged core bootstrap", commands)
        updateConsole(commands)
        Toast.makeText(this, "无线调试/核心脚本已复制", Toast.LENGTH_SHORT).show()
    }

    private fun showWirelessDebugGuide(required: Boolean = false) {
        ensureWirelessGuidePanel()
        wirelessGuidePanel.setRequiredSetup(required)
        wirelessGuidePanel.refreshStatus()
        updateConsole(
            if (required) {
                "首次启动：请完成无线调试系统开关、六位码本机配对与 shell core 启动。系统无线调试开关必须由用户亲自确认。"
            } else {
                "无线调试配对已打开：Androiddesktop 会在设备内完成 mDNS 发现、TLS 配对、ADB 连接与 shell core 启动。"
            }
        )
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

    private fun wirelessSetupComplete(): Boolean =
        onboardingPrefs.getBoolean("wireless_pairing_complete_v1", false)

    private fun restoreWirelessCore() {
        CoreIoDispatcher.execute {
            val token = runCatching { CoreAuthTokenStore(this).ensureToken() }.getOrNull()
            val result = if (token != null) AndroidAdbBridge.get(this).bootstrapCore(packageName, token) else null
            runOnUiThread {
                if (result?.coreStarted == true) {
                    updateConsole("${buildNiriIntro()}\n\n无线 ADB 已自动恢复，shell core 已就绪。")
                } else if (result != null) {
                    updateConsole("${buildNiriIntro()}\n\n无线 ADB 自动恢复未完成：${result.message}")
                }
            }
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
            setPadding(dp(6), dp(5), dp(6), dp(5))
            background = Material3Tokens.ripple(Color.argb(46, 255, 255, 255), dp(24))
            contentDescription = "启动 ${app.label}"
            AppMotion.installPressFeedback(this)
            setOnClickListener {
                addWindow(app, DisplayMode.PrivilegedVirtualDisplay, "从真实启动台创建 VirtualDisplay 会话。")
            }
        }
        tile.addView(iconView(app, if (layoutMetrics.compactPhone) 44 else 58))
        tile.addView(text(app.label, if (layoutMetrics.compactPhone) 10f else 11.5f, bold = true, color = Color.WHITE).apply {
            gravity = Gravity.CENTER
            maxLines = 1
        })
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
        appendLine("6. Launchpad reads real launcher activities from PackageManager; Dock only mirrors currently open app windows.")
        appendLine("7. Wireless debugging pairs locally inside Androiddesktop; the user only confirms Android's system Wireless debugging UI.")
        appendLine()
                append(niriManager.describe())
    }

    override fun onResume() {
        super.onResume()
        refreshHomeRoleStatus()
        if (::wirelessGuidePanel.isInitialized && wirelessGuidePanel.visibility == View.VISIBLE) {
            wirelessGuidePanel.refreshStatus()
        }
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
