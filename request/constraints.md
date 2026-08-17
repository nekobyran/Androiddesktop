# Androiddesktop 长期约束

- Clean-room only: 不复制、反编译移植、复用或提交授权 APK 的私有源码/资源/DEX 深度内容。
- 不暴露授权 APK 下载 URL、签名参数、token、keystore、密码或任何凭据。
- 本项目真实产品定位是“App 内桌面容器”，不是普通多窗口启动器。
- UI 方向必须优先 Material 3 风格：surface、圆角、状态 chip、dock、启动台、桌面窗口、动效反馈。
- 其他应用真实画面嵌入不得伪装已完成；只有在实现无线调试/Shizuku/root/系统签名特权核心、display session、Surface 绑定和输入转发后，才能标记为真实嵌入。
- 当前 release APK 可包含占位 Surface/VirtualDisplay 插槽、命令规划和架构说明，但必须在 UI/README 中标明特权核心边界。
- 构建使用 `D:\vibecoding\sdk`，产物输出到 `D:\vibecoding\release\Androiddesktop\<debug|release>`。
- Git 提交不得包含 `resource/apk/*.apk`、`*.jks`、`*.keystore`、深度 DEX 字符串、构建缓存或 release APK。
