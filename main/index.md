# Androiddesktop Index

## 关键路径

- 工程根目录：`D:\vibecoding\project\Androiddesktop`
- Release APK：`D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk`
- Debug APK：`D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk`
- APK 静态分析输出：`resource\analysis\`

## 当前产品定位

Androiddesktop 是一个 clean-room Android 桌面容器原型。当前目标参考 niri 的 scrollable tiling 思路：窗口以横向滚动列排列，新窗口追加到右侧且不重排旧窗口，焦点切换使用平滑滚动和缩放/透明度动画。普通手机/平板只显示正常 2D 桌面；VR/XR 设备由运行时 gate 自动切到专用 VR 空间桌面 Activity。真实嵌入其他 App 画面仍需要特权核心创建/管理 display session，并转发输入。

## 入口文件

- `app/src/main/java/io/github/androiddesktop/MainActivity.kt`：Material 3 风格 niri-like 正常 2D 桌面容器 UI、Dock、启动台、横向滚动列、session contract 和核心脚本入口。
- `app/src/main/java/io/github/androiddesktop/NiriStyleWindowManager.kt`：niri-like scrollable tiling 窗口管理模型，固定列宽、新窗口追加、焦点索引、浮动状态。
- `app/src/main/java/io/github/androiddesktop/NiriWindowMotion.kt`：窗口入场、焦点列缩放/透明度、横向平滑滚动、pulse 动画。
- `app/src/main/java/io/github/androiddesktop/VrRuntimeDetector.kt`：VR/XR 运行时 gate；普通设备留在 MainActivity，VR/XR 设备切到 VrDesktopActivity。
- `app/src/main/java/io/github/androiddesktop/VrDesktopActivity.kt`：VR-only 空间桌面 Activity，承载双目漂浮面板和 VR Dock。
- `app/src/main/java/io/github/androiddesktop/VrSpatialPreviewView.kt`：VR 专用双目空间桌面渲染，使用窗口列映射到漂浮面板。
- `app/src/main/java/io/github/androiddesktop/Material3Tokens.kt`：Material 3 风格颜色、surface、ripple token。
- `app/src/main/java/io/github/androiddesktop/DesktopContainerModels.kt`：桌面 App、窗口、特权核心状态模型。
- `app/src/main/java/io/github/androiddesktop/CoreSessionContracts.kt`：特权核心会话、显示 Surface、输入桥接和窗口生命周期契约。
- `app/src/main/java/io/github/androiddesktop/ContainerCorePlanner.kt`：无线调试/特权核心/虚拟显示架构说明、session blueprint 与 bootstrap 脚本规划。
- `app/src/main/java/io/github/androiddesktop/DesktopModeController.kt`：设备显示、输入、freeform 状态探测。
- `app/src/main/java/io/github/androiddesktop/PrivilegedCommandPlanner.kt`：ADB windowingMode/bounds 级命令规划。

## 构建脚本

- `command/Build-Release.ps1`：构建、zipalign、签名、验证 release APK。
- `command/Build-Debug.ps1`：构建 debug APK。
- `command/Verify-Project.ps1`：分析原 APK 并验证当前 release APK。
- `command/Analyze-Apk.ps1`：授权 APK 的 Manifest/权限/字符串证据分析。
- `command/Publish-GitHubRelease.ps1`：把当前 release APK 发布/覆盖到 GitHub Releases。
