package io.github.androiddesktop

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * User-consent boundary for becoming the system HOME application.
 *
 * Androiddesktop only declares HOME eligibility in the manifest. This helper
 * never changes the default launcher silently: API 29+ uses RoleManager's
 * system-owned consent UI, and older releases fall back to Home settings.
 */
object HomeRoleController {
    data class Status(
        val roleAvailable: Boolean,
        val isDefaultHome: Boolean,
        val mode: String
    ) {
        val compactLabel: String
            get() = if (isDefaultHome) "桌面角色 · 已启用" else "桌面角色 · 待选择"
    }

    fun status(context: Context): Status {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
        } else {
            null
        }
        val roleAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        } else {
            true
        }
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleAvailable) {
            roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            false
        }
        val resolvedHome = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
        return Status(
            roleAvailable = roleAvailable,
            isDefaultHome = roleHeld || resolvedHome,
            mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleAvailable) "RoleManager.ROLE_HOME" else "Settings.ACTION_HOME_SETTINGS"
        )
    }

    fun requestIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = context.getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                return manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun userGuide(context: Context): String {
        val status = status(context)
        return buildString {
            appendLine("== 桌面主屏幕角色 ==")
            appendLine("manifest=MAIN + HOME + DEFAULT，Androiddesktop 已具备桌面候选资格。")
            appendLine("current=${if (status.isDefaultHome) "Androiddesktop 已是默认桌面" else "尚未设为默认桌面"}")
            appendLine("requestMode=${status.mode}")
            appendLine("点击“设为桌面”后由 Android 系统显示选择/授权界面；应用不会静默替换默认桌面。")
            appendLine("需要恢复时，在系统“默认应用 / 主屏幕应用”中选择原桌面即可。")
        }
    }
}
