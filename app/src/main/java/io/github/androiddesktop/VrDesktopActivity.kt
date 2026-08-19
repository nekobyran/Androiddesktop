package io.github.androiddesktop

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class VrDesktopActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var preview: VrSpatialPreviewView
    private lateinit var console: TextView
    private lateinit var niriManager: NiriStyleWindowManager
    private lateinit var corePlanner: ContainerCorePlanner
    private var nextId = 1

    private val apps = listOf(
        DesktopApp("设置", "com.android.settings", "⚙", "VR panel target"),
        DesktopApp("文件", "com.android.documentsui", "▣", "VR panel target"),
        DesktopApp("浏览器", "com.android.chrome", "◎", "VR panel target"),
        DesktopApp("终端", "com.termux", "⌘", "VR panel target")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        corePlanner = ContainerCorePlanner(packageName)
        niriManager = NiriStyleWindowManager(dp(18), dp(340), dp(330))
        setContentView(buildVrShell())
        root.post {
            apps.take(3).forEach { app -> niriManager.addWindow(nextId++, app) }
            preview.setWorkspace(niriManager.snapshot())
            updateConsole(buildVrIntro())
        }
    }

    private fun buildVrShell(): View {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 10, 16)) }
        preview = VrSpatialPreviewView(this)
        root.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Material3Tokens.surface(Color.argb(170, 25, 28, 32), dp(24), Color.argb(70, 157, 202, 255), 1)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        top.addView(text("Androiddesktop VR", 22f, true, Material3Tokens.OnSurface))
        top.addView(text("VR-only spatial shell · niri-like columns mapped to stereoscopic panels", 12f, false, Material3Tokens.OnSurfaceVariant))
        root.addView(top, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            leftMargin = dp(18)
            rightMargin = dp(18)
            topMargin = dp(18)
        })

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = Material3Tokens.surface(Color.argb(180, 31, 35, 41), dp(28), Color.argb(64, 255, 255, 255), 1)
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        dock.addView(vrButton("左移") { focusRelative(-1) })
        dock.addView(vrButton("右移") { focusRelative(1) })
        dock.addView(vrButton("新增") { addVrPanel() })
        dock.addView(vrButton("核心") { updateConsole(corePlanner.principle() + "\n" + VrRuntimeDetector.report(this)) })
        dock.addView(vrButton("关闭VR") { finish() })
        root.addView(dock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(22)
        })

        console = TextView(this).apply {
            setTextColor(Material3Tokens.OnSurfaceVariant)
            textSize = 11f
            setTextIsSelectable(true)
        }
        val consoleBox = ScrollView(this).apply {
            background = Material3Tokens.surface(Color.argb(155, 25, 28, 32), dp(18), Color.argb(46, 255, 255, 255), 1)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(console)
        }
        root.addView(consoleBox, FrameLayout.LayoutParams(dp(430), dp(176), Gravity.RIGHT or Gravity.BOTTOM).apply {
            rightMargin = dp(18)
            bottomMargin = dp(100)
        })
        return root
    }

    private fun addVrPanel() {
        val app = apps[(nextId - 1) % apps.size]
        val state = niriManager.addWindow(nextId++, app)
        preview.setWorkspace(state)
        updateConsole("Added VR spatial panel: ${app.label}\n\n${niriManager.describe()}")
    }

    private fun focusRelative(delta: Int) {
        val state = if (delta > 0) niriManager.focusNext() else niriManager.focusPrevious()
        preview.setWorkspace(state)
        updateConsole(niriManager.describe())
    }

    private fun updateConsole(value: String) {
        console.text = value
        console.alpha = 0.3f
        console.animate().alpha(1f).setDuration(180).start()
    }

    private fun vrButton(label: String, onClick: () -> Unit): TextView = text(label, 13f, true, Material3Tokens.OnSurface).apply {
        gravity = Gravity.CENTER
        background = Material3Tokens.ripple(Material3Tokens.SecondaryContainer, dp(20))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) }
    }

    private fun buildVrIntro(): String = buildString {
        appendLine("== VR-only mode ==")
        appendLine("This activity is reserved for VR/XR-capable devices.")
        appendLine("Normal phones and tablets stay in MainActivity with no VR controls.")
        appendLine()
        append(VrRuntimeDetector.report(this@VrDesktopActivity))
        appendLine()
        append(niriManager.describe())
    }

    private fun text(value: String, size: Float, bold: Boolean, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
