package io.github.androiddesktop

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
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
    private lateinit var corePlanner: ContainerCorePlanner
    private lateinit var niriManager: NiriStyleWindowManager
    private var windowSeq = 1
    private val columnViews = linkedMapOf<Int, View>()

    private val apps = listOf(
        DesktopApp("设置", "com.android.settings", "⚙", "系统设置，常用于验证窗口承载"),
        DesktopApp("文件", "com.android.documentsui", "▣", "DocumentsUI 文件选择/管理"),
        DesktopApp("浏览器", "com.android.chrome", "◎", "浏览器类应用占位"),
        DesktopApp("图库", "com.android.gallery3d", "◧", "媒体窗口占位"),
        DesktopApp("终端", "com.termux", "⌘", "ADB/Shizuku 调试占位"),
        DesktopApp("自定义", "", "+", "使用下方输入的包名")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VrRuntimeDetector.shouldUseVr(this)) {
            startActivity(Intent(this, VrDesktopActivity::class.java))
            finish()
            return
        }
        corePlanner = ContainerCorePlanner(packageName)
        niriManager = NiriStyleWindowManager(dp(18), dp(340), dp(330))
        setContentView(buildDesktopShell())
        desktopLayer.post {
            addWindow(apps.first(), DisplayMode.PlaceholderSurface, "niri-like scrollable tiling column；真实画面仍需特权 display session。")
            updateConsole(buildNiriIntro())
        }
    }

    private fun buildDesktopShell(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Material3Tokens.Background) }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        root.addView(main, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        main.addView(buildTopBar())
        desktopLayer = FrameLayout(this).apply {
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(28), Color.argb(50, 255, 255, 255), 1)
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        main.addView(desktopLayer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(12)
            bottomMargin = dp(10)
        })
        desktopLayer.addView(buildScrollableWorkspace(), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        main.addView(buildDock())
        launcherPanel = buildLauncherPanel()
        root.addView(launcherPanel)
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text("Androiddesktop", 24f, bold = true, color = Material3Tokens.OnSurface))
        titleBlock.addView(text("niri-like scrollable tiling · normal 2D desktop · privileged core contract", 12f, color = Material3Tokens.OnSurfaceVariant))
        bar.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(chip("Scrollable columns"))
        bar.addView(chip("No resize on open"))
        bar.addView(chip("Normal mode"))
        return bar
    }

    private fun buildScrollableWorkspace(): View {
        workspaceScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(8), dp(8), dp(8), dp(210))
        }
        columnStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(460), dp(12))
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
            background = Material3Tokens.surface(Material3Tokens.SurfaceContainer, dp(30), Color.argb(64, 255, 255, 255), 1)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(row)
        row.addView(dockButton("启动台", "◉") { toggleLauncher() })
        row.addView(dockButton("核心", "◆") { updateConsole(corePlanner.principle()) })
        row.addView(dockButton("会话", "▤") { showCoreSessionContract() })
        row.addView(dockButton("左移", "‹") { focusPreviousColumn() })
        row.addView(dockButton("右移", "›") { focusNextColumn() })
        row.addView(dockButton("脚本", "⌁") { copyCoreCommands() })
        apps.take(5).forEach { app ->
            row.addView(dockButton(app.label, app.glyph) { addWindow(app, DisplayMode.PlaceholderSurface, "追加到 niri-like 横向滚动列。") })
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

    private fun addCustomWindow() {
        val pkg = packageInput.text.toString().trim().ifEmpty { "com.android.settings" }
        addWindow(DesktopApp("自定义", pkg, "+", "用户输入目标包"), DisplayMode.PlaceholderSurface, "等待特权核心把 $pkg 启动到容器显示会话。")
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
            leftMargin = dp(8)
            rightMargin = dp(10)
            topMargin = dp(8)
            bottomMargin = dp(18)
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            background = Material3Tokens.surface(Material3Tokens.PrimaryContainer, dp(22))
        }
        title.addView(text("${window.app.glyph}  ${window.app.label}", 14f, bold = true, color = Material3Tokens.OnSurface), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        title.addView(windowAction("↔") { toggleFloating(window.id) })
        title.addView(windowAction("×") { removeColumn(window.id) })
        card.addView(title)

        val surface = FrameLayout(this).apply {
            background = Material3Tokens.surface(Material3Tokens.Surface, dp(20), Color.argb(54, 157, 202, 255), 1)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        body.addView(text(window.app.glyph, 42f, bold = true, color = Material3Tokens.Primary))
        body.addView(text(window.app.packageName.ifEmpty { "custom package" }, 13f, color = Material3Tokens.OnSurface))
        body.addView(text("niri column · Surface/VirtualDisplay slot", 12f, color = Material3Tokens.OnSurfaceVariant))
        body.addView(text("打开新窗口不会重排旧列；焦点切换使用横向平滑滚动。真实 App 内容仍需特权 core 绑定 display session。", 12f, color = Material3Tokens.Warning).apply { gravity = Gravity.CENTER })
        surface.addView(body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(surface, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(10)
            leftMargin = dp(10)
            rightMargin = dp(10)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        actions.addView(smallButton("查看 session contract") {
            focusColumn(window.id)
            updateConsole(corePlanner.sessionBlueprint(window.app.packageName.ifEmpty { packageInput.text.toString() }, window.bounds))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        actions.addView(smallButton("复制该窗口核心脚本") {
            focusColumn(window.id)
            val commands = corePlanner.coreLaunchScript(window.app.packageName.ifEmpty { packageInput.text.toString() }, window.bounds)
            copyText("Androiddesktop core bootstrap", commands)
            updateConsole(commands)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        card.addView(actions)
        return card
    }

    private fun focusColumn(id: Int) {
        applyWorkspaceState(niriManager.focusWindow(id))
        updateConsole(niriManager.describe())
    }

    private fun removeColumn(id: Int) {
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
                if (app.packageName.isEmpty()) addCustomWindow() else addWindow(app, DisplayMode.PlaceholderSurface, "从启动台追加到 niri-like 列。")
                toggleLauncher(false)
            }
        }
        tile.addView(text(app.glyph, 28f, bold = true, color = Material3Tokens.Primary))
        tile.addView(text(app.label, 12f, bold = true, color = Material3Tokens.OnSurface).apply { gravity = Gravity.CENTER })
        tile.addView(text(app.description, 9f, color = Material3Tokens.OnSurfaceVariant).apply { gravity = Gravity.CENTER })
        return tile
    }

    private fun dockButton(label: String, glyph: String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(22))
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener {
                animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onClick()
                }.start()
            }
        }
        box.addView(text(glyph, 20f, bold = true, color = Material3Tokens.OnSurface))
        box.addView(text(label, 9f, color = Material3Tokens.OnSurfaceVariant))
        box.layoutParams = LinearLayout.LayoutParams(dp(72), dp(64)).apply { rightMargin = dp(8) }
        return box
    }

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

    private fun chip(label: String): TextView = text(label, 12f, bold = true, color = Material3Tokens.OnSurface).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.surface(Material3Tokens.TertiaryContainer, dp(18))
        setPadding(dp(12), dp(7), dp(12), dp(7))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) }
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
        appendLine()
        append(niriManager.describe())
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
