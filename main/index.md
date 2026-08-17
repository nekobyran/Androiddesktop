# Androiddesktop Index

## 关键路径

- 工程根目录：`D:\vibecoding\project\Androiddesktop`
- Release APK：`D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk`
- Debug APK：`D:\vibecoding\release\Androiddesktop\debug\Androiddesktop-debug.apk`
- APK 静态分析输出：`resource\analysis\`

## 当前产品定位

Androiddesktop 是一个 clean-room Android 桌面容器原型。目标不是只把外部应用启动到系统自由窗，而是在本 App 内构建类 Windows 桌面：Material 3 风格桌面层、可拖动窗口、Dock、启动台、无线调试/特权核心状态与命令规划。真实嵌入其他 App 画面需要特权核心创建/管理 display session，并转发输入。

## 入口文件

- `app/src/main/java/io/github/androiddesktop/MainActivity.kt`：Material 3 风格桌面容器 UI、Dock、启动台、可拖动窗口、session contract 和核心脚本入口。
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
