# Androiddesktop

Androiddesktop 是一个 Apache-2.0 许可的 clean-room Kotlin Android 桌面容器原型。目标是做一个类似 Windows 桌面的 App 内容器：Material 3 风格桌面层、可拖动窗口、Dock 栏、启动台，以及无线调试/特权核心/虚拟显示架构边界。

> 重要边界：普通 Android 三方 App 不能直接把任意其他应用的真实 Activity 画面嵌入自己的 View 层。本项目当前实现桌面壳、窗口容器和特权核心命令规划；真实 App 画面嵌入需要 Shizuku、无线 ADB、root 或系统签名级别的 core 进程创建/管理显示会话并转发输入。

## 当前结论

- 已授权 APK 保存于 `resource/apk/VoyageOS_demo.apk`，静态分析脚本输出到 `resource/analysis/`。
- 静态迹象显示原软件更接近“桌面容器 + 特权核心”模型，而不只是普通 `ActivityOptions.launchBounds` 多窗口启动器。
- 推测链路：无线调试或 Shizuku 启动特权 core，core 调 DisplayManager/ActivityTaskManager/WindowManager/InputManager 能力，创建或管理 display session，把目标 App 启动到该显示会话，再由宿主 UI 以 Surface/Texture 插槽形式呈现。
- 本开源版当前不会伪装已经完成真实第三方 App 画面嵌入；UI 中窗口内容明确标记为 `SurfaceView / VirtualDisplay 插槽`。

## 实现模块

| 文件 | 作用 |
|---|---|
| `MainActivity.kt` | Material 3 风格桌面容器 UI：桌面层、Dock、启动台、可拖动窗口、命令面板。 |
| `Material3Tokens.kt` | Material 3 风格颜色、surface、ripple、圆角 token。 |
| `DesktopContainerModels.kt` | 桌面 App、窗口、显示模式、特权核心状态模型。 |
| `ContainerCorePlanner.kt` | 无线调试、Shizuku/ADB core、虚拟显示、输入转发的架构说明与命令规划。 |
| `DesktopModeController.kt` | 探测外接显示、输入设备、freeform 特性、Huawei/Honor ROM 特征和 global settings。 |
| `PrivilegedCommandPlanner.kt` | ADB windowingMode/bounds 级验证命令规划。 |
| `AdbPermissionDiagnostics.kt` | 检测悬浮窗、使用情况访问、`WRITE_SECURE_SETTINGS`，生成 ADB 授权命令。 |

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

1. 打开 App 后看到 Material 3 风格桌面容器。
2. 点击 Dock 的“启动台”选择目标应用或输入包名。
3. App 会创建一个可拖动窗口，占位为 `SurfaceView / VirtualDisplay 插槽`。
4. 点击“命令”或窗口内“复制该窗口核心命令”获取无线调试/特权核心验证命令。
5. 后续要完成真实画面嵌入，需要实现 core 进程和 Surface/display session 绑定。

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
- `analysis-summary.txt`

## License

Apache-2.0
