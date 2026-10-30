# 文档索引

基线：**2026-10-30**，`master@706caa3`（M0–M3 重构完成、1165 单测全绿）。文档与代码冲突时，以 `AGENTS.md` 里的命令输出为准，然后先改文档。

## 怎么选入口

| 你是谁 / 想干什么 | 去哪 |
|---|---|
| 想用这个 App、想知道它能干什么、边界在哪 | [`user/`](user/GUIDE.md) |
| 要改代码、要开机、要提交 PR、要验收 | [`dev/DEVELOPMENT.md`](dev/DEVELOPMENT.md) → [`AGENTS.md`](../AGENTS.md) |
| 想知道「为什么当初这么设计」 | [`dev/adr/`](dev/adr/README.md) |
| 想找旧计划、旧验收流水、旧调研 | [`archive/`](archive/README.md) |

## 产品文档 `user/`

面向使用者与开源访客：功能是什么、怎么用、边界在哪。

| 文档 | 内容 |
|---|---|
| [`GUIDE.md`](user/GUIDE.md) | 功能总览与已知边界（先读这篇） |
| [`COMMANDS.md`](user/COMMANDS.md) | `/` 命令：压缩、目标、技能 |
| [`TOOLS.md`](user/TOOLS.md) | 工具分层：pi 原生、App 核心、内部/外部插件 |
| [`PLUGINS.md`](user/PLUGINS.md) | 插件框架与已接入插件 |
| [`SKILLS.md`](user/SKILLS.md) | 技能仓库、启用范围、`/skill:` 加载 |
| [`MCP.md`](user/MCP.md) | MCP 服务器接入与 OAuth |
| [`WORKSHOP.md`](user/WORKSHOP.md) | 创意工坊：页面、数据、bridge |
| [`SCHEDULING.md`](user/SCHEDULING.md) | 定时提醒、cron、投递语义 |
| [`MICLOUD.md`](user/MICLOUD.md) | 小米云服务插件（WebView 登录与读写项） |
| [`ANDROID_CAPABILITIES.md`](user/ANDROID_CAPABILITIES.md) | 已评审接入的 Android 系统能力 |
| [`ANDROID_ASSISTANT.md`](user/ANDROID_ASSISTANT.md) | 系统助手角色、语音进出、分享接收 |

## 开发文档 `dev/`

| 文档 | 内容 |
|---|---|
| [`DEVELOPMENT.md`](dev/DEVELOPMENT.md) | 环境、首次搭建、日常命令、编码约定 |
| [`ARCHITECTURE.md`](dev/ARCHITECTURE.md) | 模块依赖、数据所有权、启动顺序、关键不变量 |
| [`UI.md`](dev/UI.md) | UI 结构、配色、组件与动效规范 |
| [`TEXTS.md`](dev/TEXTS.md) | 进入模型上下文的提示词与文本清单（快照，按标识定位） |
| [`DEVICE_TESTING.md`](dev/DEVICE_TESTING.md) | 真机测试环境、安装、日志与证据采集操作 |
| [`ACCEPTANCE.md`](dev/ACCEPTANCE.md) | 稳定验收条目 ID（`ACC-*`），PR 只引用 ID |
| [`evidence/README.md`](dev/evidence/README.md) | 证据目录规范与压缩入库规则 |
| [`RELEASING.md`](dev/RELEASING.md) | 发布流程（未启用，发版时才执行） |
| [`OPEN_SOURCE_CHECKLIST.md`](dev/OPEN_SOURCE_CHECKLIST.md) | 开源/发版前逐项打勾清单 |
| [`refactor/`](dev/refactor/README.md) | 重构总纲、任务卡、执行协议、基线、测试约定、仓库卫生 |
| [`review/`](dev/review/10-30/README.md) | 2026-10-30 的结构化评审快照（`findings.tsv` 是未修项的对账口径） |
| [`adr/`](dev/adr/README.md) | 7 篇架构决策记录（不要用「顺手优化」推翻） |

仓库根目录另有 [`AGENTS.md`](../AGENTS.md)（AI agent 工作规则，命令与数字最优先）、[`CONTRIBUTING.md`](../CONTRIBUTING.md)、[`SECURITY.md`](../SECURITY.md)、[`CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md)、[`CHANGELOG.md`](../CHANGELOG.md)。

## 归档 `archive/`

不再维护，只作历史与决策输入保留；内容可能已与代码漂移。见 [`archive/README.md`](archive/README.md)。

## 维护规则

1. **事实以命令输出为准**：文档写的测试数、lint 数、警告数必须能用 `AGENTS.md` 里的命令复算。
2. **功能变更同步改文档**：新增/删除用户可见功能，同一次提交里改 `user/` 对应文档；没有对应文档说明该功能缺少说明，需要补。
3. **过时先归档再重写**：不直接删除历史文档，移入 `archive/` 并加横幅；保留「当时为什么这么定」。
4. **一份文档只回答一类问题**：`ARCHITECTURE.md` 只写「现在是什么」，「将要变成什么」写进 `refactor/`，「为什么」写进 `adr/`。
5. **不写个人环境**：文档里不出现本机绝对路径、密钥、真实账号、未脱敏截图。
