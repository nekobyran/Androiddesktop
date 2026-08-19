# Androiddesktop

Androiddesktop 是一个 Apache-2.0 许可的 clean-room Kotlin Android 桌面容器原型。当前版本默认横屏启动，桌面壳采用 niri-like scrollable tiling：窗口以横向滚动列排列，新窗口追加到右侧且不重排旧窗口，焦点切换有平滑滚动、缩放和透明度动画。普通手机/平板只显示正常 2D 桌面；检测到 VR/XR 设备特征时才切换到专用 VR 空间桌面 Activity。界面内置默认壁纸、默认/真实应用图标和完整无线调试导引。

> 重要边界：普通 Android 三方 App 自身不能任意托管其他应用的 Activity。本项目通过用户显式开启无线调试后启动的 shell-UID `app_process` core，把目标 App 启动到宿主创建的 `VirtualDisplay`，由 `SurfaceView` 显示内容并通过 display-aware input 转发点击；不启用特权 core 时仍受普通 App 的跨 display 启动权限限制。

## 当前结论

- 已授权 APK 保存于 `resource/apk/VoyageOS_demo.apk`，静态分析脚本输出到 `resource/analysis/`。
- 静态与华为真机界面证据显示原软件更接近“桌面容器 + 特权核心 + 虚拟显示”模型，而不只是普通 `ActivityOptions.launchBounds` 多窗口启动器。
- niri-like 目标来自 scrollable tiling 思路：窗口存在于横向滚动列中，打开新窗口不会导致既有窗口重新缩放或重排。
- 华为 BRQ-AN00 真机已验证：Androiddesktop 同时创建两个 `1053×469` VirtualDisplay，系统设置运行在独立 display，华为文件管理器运行在另一独立 display；对应 display 截图均出现真实 App 内容。

## 实现模块

| 文件 | 作用 |
|---|---|
| `MainActivity.kt` | Material 3 风格 niri-like 正常 2D 桌面容器 UI：默认横屏、默认壁纸、Dock、启动台、横向滚动列、无线导引、session contract、核心脚本入口。 |
| `DesktopWallpaperDrawable.kt` | 代码绘制默认壁纸，无外部素材依赖。 |
| `DefaultAppIconDrawable.kt` | 代码绘制 fallback 默认图标；可解析包名优先使用系统真实应用图标。 |
| `WirelessDebugGuide.kt` | 完整无线调试、ADB pairing/connect、授权、shell core 和验证命令导引。 |
| `EmbeddedAppSurfaceView.kt` | SurfaceView + VirtualDisplay 会话槽，尝试通过 privileged core 启动目标 App 并转发点击。 |
| `PrivilegedCoreClient.kt` | 与无线 ADB shell core 通信的 127.0.0.1 客户端，每条命令携带安装级随机令牌。 |
| `CoreAuthTokenStore.kt` | 为特权核心生成并持久化 256-bit 随机认证令牌。 |
| `PrivilegedShellCore.kt` | 可由 app_process 启动的 shell-UID core，认证后提供 launch/tap/key 命令。 |
| `NiriStyleWindowManager.kt` | scrollable tiling 窗口管理模型：固定列宽、新窗口追加、焦点索引、浮动状态。 |
| `NiriWindowMotion.kt` | 窗口入场、焦点列缩放/透明度、横向平滑滚动、pulse 动画。 |
| `VrRuntimeDetector.kt` | VR/XR 运行时 gate：普通设备保持正常界面，VR/XR 设备切入 VR Activity。 |
| `VrDesktopActivity.kt` | VR-only 空间桌面 Activity：双目画面、漂浮窗口面板、VR Dock、核心状态面板。 |
| `VrSpatialPreviewView.kt` | VR 专用空间桌面渲染：双目画面、漂浮窗口面板、凝视准星。 |
| `Material3Tokens.kt` | Material 3 风格颜色、surface、ripple、圆角 token。 |
| `DesktopContainerModels.kt` | 桌面 App、窗口、显示模式、特权核心状态模型。 |
| `CoreSessionContracts.kt` | 特权核心会话、显示 Surface、输入桥接和窗口生命周期契约。 |
| `ContainerCorePlanner.kt` | 无线调试、Shizuku/ADB core、虚拟显示、输入转发的架构说明与命令规划。 |
| `DesktopModeController.kt` | 探测外接显示、输入设备、freeform 特性、Huawei/Honor ROM 特征和 global settings。 |
| `PrivilegedCommandPlanner.kt` | ADB windowingMode/bounds 级验证命令规划。 |
| `AdbPermissionDiagnostics.kt` | 检测悬浮窗、使用情况访问、`WRITE_SECURE_SETTINGS`，生成 ADB 授权命令。 |

## niri-like 行为

```text
1. Dock 或启动台打开 App 时，新窗口追加到最右侧列。
2. 旧窗口保持原宽度和位置，不因为新窗口出现而缩放。
3. “左移 / 右移”切换焦点列，并对 HorizontalScrollView 做平滑滚动。
4. 焦点列使用 scale/elevation/alpha 动画突出显示。
5. 浮动按钮当前作为 utility flag，不破坏主滚动列布局。
6. 普通 2D 界面不显示 VR 入口；VR/XR 设备自动进入 VR-only 空间桌面。
7. 默认横屏，桌面背景由代码绘制；默认图标优先读取系统 App 图标，缺失时生成 fallback 图标。
8. Dock 的“无线导引”提供从开发者选项、adb pair/connect 到 shell core、dumpsys 验证的完整流程。
```

## 构建

Release 构建与签名：

```powershell
cd D:\vibecoding\project\Androiddesktop
# 仅首次或全新开发机执行；初始化后请安全备份 app/signing 下的本地签名材料
.\command\Initialize-ReleaseSigning.ps1
.\command\Build-Release.ps1
```

产物路径：

```text
D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk
```

首次构建前只执行一次 `.\command\Initialize-ReleaseSigning.ps1`，它会在 `app/signing/` 创建固定的 `androiddesktop-release.jks` 与 `release.local.properties`；两者都被 `.gitignore` 排除，必须一起安全备份。`Build-Release.ps1` 绝不会自动生成替代密钥：任一文件缺失会直接失败，避免版本之间随机换签。也可通过 `ANDROIDDESKTOP_KEYSTORE`、`ANDROIDDESKTOP_KEY_ALIAS`、`ANDROIDDESKTOP_KEYSTORE_PASS`、`ANDROIDDESKTOP_KEY_PASS` 显式覆盖本地签名配置。

## 原理边界

- `DisplayManager.createVirtualDisplay()` 可以创建虚拟显示，并把虚拟显示内容渲染到调用方提供的 `Surface`；但普通 App 创建的虚拟显示不等于可以任意托管其他 App 的 Activity。
- `ActivityOptions.setLaunchDisplayId()` 可以请求把 Activity 启动到指定 display，但系统会根据 display 权限、Activity 策略和 ROM 策略决定是否允许。
- Shizuku 的价值在于让普通 App 通过一个由 adb/root 权限启动的 Java 进程调用系统 API；这可以解释“无线调试 + 特权核心”的实现路线。
- 真正的 App 内桌面窗口需要同时解决：显示会话创建、目标任务启动、Surface 显示、窗口管理、输入坐标映射、焦点/键盘、生命周期和权限提示。

## 使用方向

1. 打开 App 后默认横屏进入 Material 3 风格 niri-like 正常 2D 桌面容器。
2. 点击 Dock 或启动台选择目标应用；窗口会追加到横向滚动列。
3. 点击“左移 / 右移”切换焦点列。
4. 点击“无线导引”查看并复制完整无线调试、shell core 和验证命令。
5. 点击“脚本”或窗口内按钮复制当前窗口特权核心验证脚本。

## License

Apache-2.0
