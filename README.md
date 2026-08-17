# Androiddesktop

Androiddesktop 是一个 clean-room Kotlin 示例工程，用于复现“Android 桌面模式 / 应用多窗口化”的公开原理：检测外接显示与输入设备、诊断 ADB 权限、请求以指定窗口边界启动目标 Activity，并为华为 / 荣耀真机画面测试提供步骤化证据采集。

## 当前结论

- 已授权 APK 保存于 `resource/apk/VoyageOS_demo.apk`，静态分析脚本输出到 `resource/analysis/`。
- 本工程不复制 APK 私有源码，只提炼公开可复用机制：Manifest/权限/外接显示/输入设备/freeform/multi-window/ADB settings。
- 三方应用可以通过 `android:resizeableActivity="true"`、`FLAG_ACTIVITY_LAUNCH_ADJACENT`、`ActivityOptions.setLaunchBounds()` 请求更适合桌面窗口的启动形态。
- 最终是否真正显示为可自由调整窗口，取决于设备是否开放 freeform/windowing、目标 Activity 是否可调整、ROM 桌面模式策略、显示器能力与用户/ADB 授权。

## 实现模块

| 文件 | 作用 |
|---|---|
| `MainActivity.kt` | 纯 Android View UI，展示状态、权限、ADB 命令、华为测试计划，并触发多窗口启动。 |
| `DesktopModeController.kt` | 探测外接显示、输入设备、freeform 特性、Huawei/Honor ROM 特征和 global settings。 |
| `MultiWindowLauncher.kt` | 用公开 `Intent` flags + `ActivityOptions.launchBounds` 发起多窗口/指定边界启动请求。 |
| `AdbPermissionDiagnostics.kt` | 检测悬浮窗、使用情况访问、`WRITE_SECURE_SETTINGS`，生成 ADB 授权命令。 |
| `HuaweiVisualProbe.kt` | 输出华为真机画面测试流程与 dumpsys 采集建议。 |

## 构建

Debug 构建：

```powershell
cd D:\vibecoding\project\Androiddesktop
.\command\Build-Debug.ps1
```

Release 构建与签名：

```powershell
cd D:\vibecoding\project\Androiddesktop
.\command\Build-Release.ps1
```

产物路径：

```text
D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk
D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk
```

`Build-Release.ps1` 默认使用临时本地签名密钥生成可安装 release APK，并在签名后删除临时密钥。生产签名可通过 `ANDROIDDESKTOP_KEYSTORE`、`ANDROIDDESKTOP_KEY_ALIAS`、`ANDROIDDESKTOP_KEYSTORE_PASS`、`ANDROIDDESKTOP_KEY_PASS` 环境变量传入；不要把 keystore 或密码提交进源码。

全量验证：

```powershell
cd D:\vibecoding\project\Androiddesktop
.\command\Verify-Project.ps1
```

## APK 静态分析

```powershell
cd D:\vibecoding\project\Androiddesktop
.\command\Analyze-Apk.ps1
```

输出内容：

- `aapt-badging.txt`
- `aapt-permissions.txt`
- `manifest-xmltree.txt`
- `zip-entries.tsv`
- `dex-keyword-strings.txt`
- `analysis-summary.txt`

## 华为真机 / ADB 测试

1. 手机开启开发者选项和 USB debugging。
2. 使用 USB 连接电脑，执行 `adb devices`，确认设备已授权。
3. 安装 `Androiddesktop-release.apk`。
4. 打开应用，点击“复制 ADB 命令”，在电脑 PowerShell 中执行。
5. 连接外接屏、扩展坞、无线投屏或华为桌面模式入口。
6. 在目标包名中输入需要多窗口化的应用，例如 `com.android.settings`。
7. 点击“启动多窗口”，观察目标应用是否进入分屏、外屏窗口或自由窗口。
8. 点击“Huawei 画面测试”，按提示采集 dumpsys/display/activity/window 证据。

## 原理边界

- `ActivityOptions.setLaunchBounds()` 是请求，不是强制；没有 freeform 或对应显示能力时可能被系统忽略。
- 普通三方应用不能直接控制系统桌面服务、WindowManager 内部 windowing mode 或厂商私有桌面协议。
- `WRITE_SECURE_SETTINGS`、`PACKAGE_USAGE_STATS`、悬浮窗等权限需要用户或 ADB 授权；未授权时只能做诊断和跳转设置页。
- 华为 / 荣耀 ROM 的桌面模式策略可能随机型、系统版本、投屏路径和企业策略不同而变化。
