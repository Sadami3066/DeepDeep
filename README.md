# DeepDeep

Android 上的个人 agent：pi2kotlin agent 运行时 + proot Linux + 插件 + 创意工坊，**不需要 root**。

当前状态：个人项目、开发中。仓库暂为私有，但按开源标准维护（见 `CONTRIBUTING.md`）。

## 能做什么

- **对话**：Claude 风格聊天、多会话与工作区、按会话切换模型与思考等级、42 家 Provider、Markdown/代码/表格、语音进出
- **动手**：Alpine/proot 里的 `bash`、后台任务、定时提醒投递回原对话
- **接入**：MCP、插件（飞书 / 企业微信 / 小米云 / 网络搜索 / GUI 操作）、无障碍操作、系统日历、分享接收
- **造东西**：创意工坊——Agent 写出的 HTML 轻应用，WebView 渲染，代码与数据分离
- **系统**：前台服务、通知中心、系统助手角色、深链接

完整功能与边界见 [`docs/GUIDE.md`](docs/GUIDE.md)。

## 跑起来

需要 JDK 17+、Android SDK Platform 36 + build-tools 36.0.0。

```bash
./scripts/dev-setup.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`dev.pi` SDK 由 Gradle 任务自动从 pi2kotlin `v0.2.4` Release 下载并校验 sha256，不依赖本机 `~/.m2`。

## 门禁

```bash
./scripts/verify.sh      # 编译 + 单测 + lint + 模块边界（带全局锁，串行）
```

基线见 [`docs/refactor/BASELINE.md`](docs/refactor/BASELINE.md)：1024 单测、0 lint error、13 编译警告。

## 边界

- 不做权限/审批系统，只有 Linux/root 总开关（[ADR-0002](docs/adr/0002-no-permission-approval.md)）
- Linux 是 proot 用户空间，**不是安全沙箱**
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据
- 创意工坊页面只能通过 bridge 读写自己的数据，不能调用 App 内部函数、执行 shell 或读取其他数据
- 本地分发：release 变体用 debug 证书签名（[ADR-0001](docs/adr/0001-local-distribution-and-signing.md)）

## 文档地图

| 想了解 | 去哪 |
|---|---|
| 功能与边界 | [`docs/GUIDE.md`](docs/GUIDE.md) |
| 开发环境、命令、约定 | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) |
| 模块结构与数据所有权 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| 为什么这么设计 | [`docs/adr/`](docs/adr/README.md) |
| 真机验收条目 | [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md) |
| 给 AI agent 的规则 | [`AGENTS.md`](AGENTS.md) |
| 当前重构计划与任务 | [`docs/refactor/`](docs/refactor/README.md) |
| 参与贡献 / 行为准则 / 安全问题 | [`CONTRIBUTING.md`](CONTRIBUTING.md) · [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) · [`SECURITY.md`](SECURITY.md) |
| 发布流程（未启用）与开源就绪清单 | [`docs/RELEASING.md`](docs/RELEASING.md) · [`docs/OPEN_SOURCE_CHECKLIST.md`](docs/OPEN_SOURCE_CHECKLIST.md) |
| 变更记录 | [`CHANGELOG.md`](CHANGELOG.md) |

## 许可

自有代码采用 Apache-2.0（见 [`LICENSE`](LICENSE)）。随 APK 分发的第三方组件（PRoot、Alpine、字体、图标、pi2kotlin SDK 等）分别适用各自许可证，见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。本项目不授予任何商标许可。
