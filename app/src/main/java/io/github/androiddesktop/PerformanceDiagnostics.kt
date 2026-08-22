package io.github.androiddesktop

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.view.WindowManager

object PerformanceDiagnostics {
    data class Snapshot(
        val refreshRateHz: Float,
        val totalPssKb: Int,
                val javaHeapUsedMb: Int,
        val reducedMotion: Boolean,
        val activeEmbeddedSessions: Int,
        val globalScalePercent: Int,
        val effectiveDensityDpi: Int,
        val logicalWidthDp: Int,
        val logicalHeightDp: Int

    ) {
        fun toHumanReport(): String = buildString {
            appendLine("== 性能快照 ==")
            appendLine("宿主刷新率=${"%.1f".format(refreshRateHz)}Hz")
            appendLine("进程 PSS=${totalPssKb}KB")
            appendLine("Java heap=${javaHeapUsedMb}MB")
                        appendLine("reducedMotion=$reducedMotion")
            appendLine("embeddedSessions=$activeEmbeddedSessions")
            appendLine("globalScale=$globalScalePercent% · density=${effectiveDensityDpi}dpi · logical=${logicalWidthDp}x${logicalHeightDp}dp")

            appendLine("motion=press .97/120ms · popover 200ms · content 220ms · modal 240ms")
            appendLine("curve=easeOut(0.23,1,0.32,1) · easeInOut(0.77,0,0.175,1)")
            appendLine("spring=mass1/stiffness420/damping30，仅用于低频 settle")
        }
    }

    fun snapshot(context: Context, activeEmbeddedSessions: Int): Snapshot {
        val refreshRate = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.refreshRate
        }.getOrDefault(60f)
                val pssLong: Long = runCatching {
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activity.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()?.totalPss?.toLong() ?: Debug.getPss()
        }.getOrDefault(Debug.getPss())
        val pss = pssLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val runtime = Runtime.getRuntime()
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).toInt()
                val configuration = context.resources.configuration
        return Snapshot(
            refreshRateHz = refreshRate,
            totalPssKb = pss,
            javaHeapUsedMb = usedMb,
            reducedMotion = AppMotion.reducedMotion(context),
            activeEmbeddedSessions = activeEmbeddedSessions,
            globalScalePercent = GlobalUiScale.percent(context),
            effectiveDensityDpi = GlobalUiScale.effectiveDensityDpi(context),
            logicalWidthDp = configuration.screenWidthDp,
            logicalHeightDp = configuration.screenHeightDp
        )

    }
}
