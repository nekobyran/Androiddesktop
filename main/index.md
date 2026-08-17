# Androiddesktop 入口索引

## 工程入口

- Android Manifest：`app/src/main/AndroidManifest.xml`
- 主界面：`app/src/main/java/io/github/androiddesktop/MainActivity.kt`
- UI 动画工具：`app/src/main/java/io/github/androiddesktop/UiMotion.kt`
- 窗口预览动画：`app/src/main/java/io/github/androiddesktop/WindowPreviewView.kt`
- 桌面模式探测：`app/src/main/java/io/github/androiddesktop/DesktopModeController.kt`
- 多窗口启动：`app/src/main/java/io/github/androiddesktop/MultiWindowLauncher.kt`
- ADB 权限诊断：`app/src/main/java/io/github/androiddesktop/AdbPermissionDiagnostics.kt`
- 华为画面测试计划：`app/src/main/java/io/github/androiddesktop/HuaweiVisualProbe.kt`

## 脚本入口

- APK 静态分析：`command/Analyze-Apk.ps1`
- Debug 构建：`command/Build-Debug.ps1`
- Release 构建与签名：`command/Build-Release.ps1`
- 复制产物：`command/Copy-Release.ps1`
- 全量验证：`command/Verify-Project.ps1`

## 数据与产物

- 已授权 APK：`resource/apk/VoyageOS_demo.apk`
- 静态分析输出：`resource/analysis/`
- Debug 产物：`D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk`
- Release 产物：`D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk`
