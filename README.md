# DeepDeep

Android 上的个人 agent：pi2kotlin 运行时 + proot Linux + 插件 + 创意工坊，**不需要 root**。

## 能做什么

- **对话**：Claude 风格聊天、多工作区/会话、42 家 Provider、按会话切模型与思考档、Markdown/代码/表格、语音进出
- **动手**：Alpine/proot 里的 `bash`、后台任务、定时提醒投递回原对话、目标续跑；无线调试 `adb_shell` 或已 root 设备的 `root_shell`（只注册当前 effective 的一个）
- **接入**：MCP、插件（飞书 / 企业微信 / 小米云 / 网络搜索 / 屏幕 / 安排）、无障碍、系统日历、分享接收；`screen_*` 优先操作后台虚拟屏，也可显式选择主屏
- **造东西**：创意工坊——Agent 写的 HTML 轻应用，WebView 渲染，数据与代码分离
- **系统**：前台服务、通知中心、系统助手角色与浮层、深链接

功能细节与已知边界见 [docs/GUIDE.md](docs/GUIDE.md)；不可随手推翻的设计取舍见 [docs/DECISIONS.md](docs/DECISIONS.md)。

## 构建与安装

需要 JDK 17+、Android SDK Platform 36 + build-tools 36.0.0。

```bash
./scripts/dev-setup.sh        # 首次：检查环境、写 local.properties、拉 SDK、出 APK
./scripts/check.sh            # 日常：编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --device   # 再加覆盖安装到手机并启动
```

`dev.pi` SDK 由 Gradle 任务从 pi2kotlin `v0.3.0` Release 下载并校验 sha256，不依赖本机 `~/.m2`。无线调试客户端依赖 `com.github.MuntashirAkon:libadb-android:3.1.1`（JitPack），首次构建需能访问 JitPack/Maven Central。所有 Gradle 调用走 `./scripts/gradlew-locked`（全局串行锁；本机并发构建会 OOM）。

## 边界

- 不做审批流程；系统级能力走权限页三档（普通 / 无线调试 ADB / Root），ADB 首次用系统无线调试配对码，Root 交给系统 root 管理器
- 内嵌 ADB 守护进程挂在 adbd 下：关闭无线调试或重启后需重新开启并在权限页点一次连接；不常驻自启动
- Linux 是 proot 用户空间，**不是安全沙箱**
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据
- 本地分发，release 也用 debug 证书签名（换签 = 所有设备卸载重装 = 丢数据，见 `DECISIONS`）
- 自有代码 Apache-2.0；随包分发的第三方组件各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
