# Androiddesktop 长期约束

- 本工程采用 clean-room 实现：只使用 APK 元数据、Manifest、权限、公开 API 行为和用户授权测试结论，不复制、反编译复刻或移植私有源码。
- 不泄露下载 URL、签名、AccessKey、Token、Cookie 或设备敏感信息。
- 默认 PowerShell 7、UTF-8；SDK/JDK 使用 `D:\vibecoding\sdk`，不得把构建环境放到 C 盘。
- Android 多窗口能力只通过公开 SDK、显式用户操作和 ADB 授权诊断实现，不使用破坏性隐藏 API 绕过 ROM 策略。
- 华为真机测试必须记录 ADB 授权状态、显示状态和实际画面表现；不得伪造通过。
