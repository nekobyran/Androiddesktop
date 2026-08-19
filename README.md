# Androiddesktop

Androiddesktop 是一个 Apache-2.0 许可的 clean-room Kotlin Android 桌面容器原型。当前版本把桌面壳升级为 niri-like scrollable tiling：窗口以横向滚动列排列，新窗口追加到右侧且不重排旧窗口，焦点切换有平滑滚动、缩放和透明度动画。普通手机/平板只显示正常 2D 桌面；检测到 VR/XR 设备特征时才切换到专用 VR 空间桌面 Activity。

> 重要边界：普通 Android 三方 App 不能直接把任意其他应用的真实 Activity 画面嵌入自己的 View 层。本项目当前实现正常 2D 桌面壳、niri-like 窗口管理、VR-only 空间桌面 Activity、窗口容器和特权核心命令规划；真实 App 画面嵌入需要 Shizuku、无线 ADB、root 或系统签名级别的 core 进程创建/管理显示会话并转发输入。

## 当前结论

- 已授权 APK 保存于 `resource/apk/VoyageOS_demo.apk`，静态分析脚本输出到 `resource/analysis/`。
- 静态与华为真机界面证据显示原软件更接近“桌面容器 + 特权核心 + 虚拟显示”模型，而不只是普通 `ActivityOptions.launchBounds` 多窗口启动器。
- niri-like 目标来自 scrollable tiling 思路：窗口存在于横向滚动列中，打开新窗口不会导致既有窗口重新缩放或重排。
- 本开源版当前不会伪装已经完成真实第三方 App 画面嵌入；UI 中窗口内容明确标记为 `SurfaceView / VirtualDisplay slot`。

## 实现模块

| 文件 | 作用 |
|---|---|
| `MainActivity.kt` | Material 3 风格 niri-like 正常 2D 桌面容器 UI：Dock、启动台、横向滚动列、session contract、核心脚本入口。 |
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
```

## 构建

Release 构建与签名：

```powershell
cd D:\vibecoding\project\Androiddesktop
.\command\Build-Release.ps1
```

产物路径：

```text
D:\vibecoding\release\Androiddesktop\release\Androiddesktop-release.apk
```

`Build-Release.ps1` 默认使用临时本地签名密钥生成可安装 release APK，并在签名后删除临时密钥。生产签名可通过 `ANDROIDDESKTOP_KEYSTORE`、`ANDROIDDESKTOP_KEY_ALIAS`、`ANDROIDDESKTOP_KEYSTORE_PASS`、`ANDROIDDESKTOP_KEY_PASS` 环境变量传入；不要把 keystore 或密码提交进源码。

## 原理边界

- `DisplayManager.createVirtualDisplay()` 可以创建虚拟显示，并把虚拟显示内容渲染到调用方提供的 `Surface`；但普通 App 创建的虚拟显示不等于可以任意托管其他 App 的 Activity。
- `ActivityOptions.setLaunchDisplayId()` 可以请求把 Activity 启动到指定 display，但系统会根据 display 权限、Activity 策略和 ROM 策略决定是否允许。
- Shizuku 的价值在于让普通 App 通过一个由 adb/root 权限启动的 Java 进程调用系统 API；这可以解释“无线调试 + 特权核心”的实现路线。
- 真正的 App 内桌面窗口需要同时解决：显示会话创建、目标任务启动、Surface 显示、窗口管理、输入坐标映射、焦点/键盘、生命周期和权限提示。

## 使用方向

1. 打开 App 后看到 Material 3 风格 niri-like 桌面容器。
2. 点击 Dock 或启动台选择目标应用；窗口会追加到横向滚动列。
3. 点击“左移 / 右移”切换焦点列。
4. 点击“VR”查看当前窗口列的双目空间预览。
5. 点击“脚本”或窗口内按钮复制无线调试/特权核心验证脚本。

## License

Apache-2.0
