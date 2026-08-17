package io.github.androiddesktop

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build

class MultiWindowLauncher(private val context: Context) {
    fun launchPackage(packageName: String, bounds: Rect = defaultBounds(), displayId: Int? = null): LaunchResult {
        val normalized = packageName.trim()
        if (normalized.isEmpty()) return LaunchResult(false, "目标包名为空")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(normalized)
            ?: return LaunchResult(false, "未找到可启动入口: $normalized")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        if (Build.VERSION.SDK_INT >= 24) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        }

        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 24) {
            options.launchBounds = bounds
        }
        if (Build.VERSION.SDK_INT >= 26 && displayId != null) {
            runCatching { options.setLaunchDisplayId(displayId) }
        }

        return try {
            context.startActivity(launchIntent, options.toBundle())
            LaunchResult(true, "已请求以多窗口/指定边界启动 $normalized，bounds=$bounds displayId=${displayId ?: "default"}")
        } catch (e: SecurityException) {
            LaunchResult(false, "权限不足: ${e.message}")
        } catch (e: ActivityNotFoundException) {
            LaunchResult(false, "启动入口不存在: ${e.message}")
        } catch (e: RuntimeException) {
            LaunchResult(false, "启动失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun launchIntent(intent: Intent, bounds: Rect = defaultBounds(), activity: Activity? = null): LaunchResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 24) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        val options = ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= 24) options.launchBounds = bounds
        return try {
            if (activity != null) activity.startActivity(intent, options.toBundle()) else context.startActivity(intent, options.toBundle())
            LaunchResult(true, "Intent 已提交: ${intent.action ?: intent.component?.flattenToShortString() ?: intent.toUri(0)}")
        } catch (e: RuntimeException) {
            LaunchResult(false, "Intent 启动失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun defaultBounds(): Rect = Rect(80, 80, 900, 720)
}

data class LaunchResult(
    val success: Boolean,
    val message: String
)
