package io.github.androiddesktop

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView

class EmbeddedAppSurfaceView(
    context: Context,
    private val targetPackage: String,
    private val launcher: MultiWindowLauncher,
    private val onState: (State) -> Unit
) : FrameLayout(context), SurfaceHolder.Callback {

    data class State(
        val displayId: Int?,
        val coreConnected: Boolean,
        val launched: Boolean,
        val message: String
    )

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        private val coreClient = PrivilegedCoreClient(context)
    private val surfaceView = SurfaceView(context)
    private val statusView = TextView(context)
    private var virtualDisplay: VirtualDisplay? = null
    private var lastWidth = 0
    private var lastHeight = 0
    @Volatile private var released = false

    init {
        setBackgroundColor(Color.BLACK)
        clipChildren = true
        surfaceView.setZOrderOnTop(false)
        surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { _, event ->
            forwardTouch(event)
            true
        }
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        statusView.apply {
            text = "正在创建应用显示会话…"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(176, 8, 16, 28))
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        addView(statusView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(8)
            topMargin = dp(8)
        })
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        ensureDisplay(width.coerceAtLeast(320), height.coerceAtLeast(240))
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        ensureDisplay(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseDisplay()
    }

    fun releaseSession() {
        released = true
        surfaceView.holder.removeCallback(this)
        releaseDisplay()
    }

    private fun ensureDisplay(width: Int, height: Int) {
        if (released || !surfaceView.holder.surface.isValid) return
        val existing = virtualDisplay
        if (existing != null) {
            if (width != lastWidth || height != lastHeight) {
                existing.resize(width, height, resources.displayMetrics.densityDpi)
                existing.surface = surfaceView.holder.surface
                lastWidth = width
                lastHeight = height
            }
            return
        }

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        val created = displayManager.createVirtualDisplay(
            "Androiddesktop-${targetPackage.replace('.', '-')}-${hashCode()}",
            width,
            height,
            resources.displayMetrics.densityDpi,
            surfaceView.holder.surface,
            flags
        )
        if (created == null) {
            publish(State(null, false, false, "VirtualDisplay 创建失败"))
            return
        }
        virtualDisplay = created
        lastWidth = width
        lastHeight = height
        publish(State(created.display.displayId, false, false, "display=${created.display.displayId} 已创建，正在连接特权核心…"))
        launchTarget(created.display.displayId, width, height)
    }

    private fun launchTarget(displayId: Int, width: Int, height: Int) {
        Thread({
            val ping = coreClient.ping()
            if (released) return@Thread
            if (ping.success) {
                val launch = coreClient.launch(displayId, targetPackage)
                post {
                    if (!released) {
                        publish(
                            State(
                                displayId,
                                true,
                                launch.success,
                                if (launch.success) "特权核心已连接 · $targetPackage → display=$displayId" else "核心启动失败: ${launch.response}"
                            )
                        )
                    }
                }
            } else {
                post {
                    if (released) return@post
                    val direct = launcher.launchPackage(targetPackage, Rect(0, 0, width, height), displayId)
                    publish(
                        State(
                            displayId,
                            false,
                            direct.success,
                            if (direct.success) {
                                "未检测到 shell core；已直接请求 $targetPackage → display=$displayId"
                            } else {
                                "display=$displayId 已创建，但目标启动失败；请先按无线导引启动 shell core。${direct.message}"
                            }
                        )
                    )
                }
            }
        }, "Androiddesktop-display-$displayId").start()
    }

    private fun forwardTouch(event: MotionEvent) {
        val displayId = virtualDisplay?.display?.displayId ?: return
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        val x = event.x.toInt().coerceIn(0, lastWidth.coerceAtLeast(1) - 1)
        val y = event.y.toInt().coerceIn(0, lastHeight.coerceAtLeast(1) - 1)
        Thread({ coreClient.tap(displayId, x, y) }, "Androiddesktop-input-$displayId").start()
    }

        private fun publish(state: State) {
        Log.i("AndroiddesktopEmbed", "target=$targetPackage displayId=${state.displayId} core=${state.coreConnected} launched=${state.launched} message=${state.message}")
        statusView.text = state.message
        statusView.visibility = if (state.launched) GONE else VISIBLE
        onState(state)
    }

    private fun releaseDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        lastWidth = 0
        lastHeight = 0
    }

    override fun onDetachedFromWindow() {
        releaseSession()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
