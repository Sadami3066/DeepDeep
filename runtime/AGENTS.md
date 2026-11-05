# runtime · Agent 运行时

dev.pi SDK 之上的 agent 运行时：会话与事件流、工具、Provider/凭据、MCP、定时与目标、后台任务、创意工坊、工作区。会话以 pi JSONL 为唯一权威，目标与任务只在进程内；测试在 `src/test/kotlin`（JUnit4 + coroutines-test）。

## 文件

### 会话、工作区与引擎

| 文件 | 职责 |
|---|---|
| `AgentRuntimeHost.kt` | 会话总管：句柄池、事件流、忙闲与工作区 |
| `PiSessionEngine.kt` | 装配 pi 会话：模型、工具、MCP 与系统提示词 |
| `SessionEngine.kt` | 建会话、会话目录与工作区目录三组接口 |
| `SessionHandle.kt` | 单会话操作面：发消息、压缩、MCP、用量 |
| `SessionBinding.kt` | 会话身份（id、文件、cwd）的绑定容器 |
| `SessionCommandPlane.kt` | 会话运行状态机：忙闲、runId、停止、重配 |
| `SessionIndex.kt` | 扫 JSONL 建索引，按大小与时间戳增量缓存 |
| `SessionCatalog.kt` | 会话列表：合并 JSONL 与置顶/归档/重命名 |
| `SessionMetaStore.kt` | 会话元数据（置顶/归档/重命名）存 DataStore |
| `HistoryMapper.kt` | JSONL 条目转消息与工具轨迹，识别定时提醒 |
| `RunLedger.kt` | 落盘未完成运行，重启后提示被中断 |
| `HandleBudget.kt` | 句柄上限与空闲淘汰规则（6 个 / 10 分钟） |
| `PendingInputs.kt` | 插话与排队输入的本地队列及认领 |
| `SystemPrompt.kt` | 系统提示词文件、全局规则与工具规则合并 |
| `Personalization.kt` | 称呼、交互风格、共享存储等提示词分节 |
| `SkillCatalog.kt` | 发现技能并渲染为提示词块 |
| `WorkspaceStore.kt` | 工作区列表持久化（DataStore） |
| `RuntimeLog.kt` | 包内 Log 包装，失败不抛 |

### 工具

| 文件 | 职责 |
|---|---|
| `AppTools.kt` | App 专有工具注入：提问、插件与扩展工厂 |
| `ToolVariants.kt` | 从统一工具派生具名变体并校验参数 |
| `CommandResult.kt` | 命令结果模型与可带详细输出的执行器 |
| `DeepDeepBashTool.kt` | bash 工具：执行、截尾、退出码与超时 |
| `PrivilegedShellTool.kt` | adb_shell / root_shell 的共用骨架 |
| `RootShellTool.kt` | Root 执行适配器，产出 root_shell 工具 |
| `AskUserQuestionTool.kt` | 结构化提问：1–5 题，等答最多 10 分钟 |
| `QuestionCenter.kt` | 待答问题的状态流、回答/取消与持久化钩子 |
| `QuestionPersistence.kt` | 待答问题落盘，重启后提示曾中断 |
| `PresentTool.kt` | present 交付：文件、图片、视频、HTML、创意 |
| `PluginTools.kt` | plugin_setup：拉起插件安装与连接流程 |
| `WorkspacePaths.kt` | 强制文件工具绝对路径，输出补全目录前缀 |

### Provider、凭据与 MCP

| 文件 | 职责 |
|---|---|
| `ProviderCatalog.kt` | 从 pi 内置目录生成 Provider/模型清单与默认配置 |
| `ProviderDirectory.kt` | Provider 与模型查询、配置归一接口 |
| `ProviderOAuthService.kt` | Provider OAuth 登录/登出，复用 pi 流程 |
| `SdkCredentialStore.kt` | 内存凭据缓存、串行写盘与鉴权种类流 |
| `ApiKeyStore.kt` | API Key 的加密存取（`api_key_` 前缀） |
| `OAuthCredentialStore.kt` | OAuth 凭据的加密存取（`oauth_` 前缀） |
| `EncryptedSecretStore.kt` | Keystore AES-GCM 加密的密钥存取 |
| `ModelConfigStore.kt` | 全局默认模型与思考档（DataStore） |
| `InstallationIdStore.kt` | 持久安装 ID，作 OAuth 设备标识 |
| `DeepDeepOAuthCallbackServerFactory.kt` | 本地 HTTP 回调服务器，收 OAuth code |
| `McpConfigStore.kt` | mcp.json 的读写、增删改、启停与导入 |
| `McpJson.kt` | 宽松解析粘贴的 MCP JSON，格式化单条 |
| `McpServices.kt` | MCP 服务器探测、OAuth 登录与鉴权 |

### 定时、目标与后台任务

| 文件 | 职责 |
|---|---|
| `TimerModels.kt` | 定时触发模型、下次运行计算与 JSON 编解码 |
| `TimerCron.kt` | 五段 cron 解析、下次运行与人类可读摘要 |
| `TimerStore.kt` | timers.json 持久化，含迁移与损坏备份 |
| `TimerService.kt` | 到点投递回会话、补投与去重 |
| `TimerTools.kt` | 定时任务的建/列/改/删四个工具 |
| `GoalService.kt` | 目标状态机：轮次、暂停/恢复/阻塞 |
| `GoalTools.kt` | get/create/update_goal，按回合来源限权 |
| `JobRegistry.kt` | 后台任务注册表：输出环形缓冲、通知与回收 |
| `JobTools.kt` | 后台任务的启动/读取/列出/取消四个工具 |

### 创意工坊

| 文件 | 职责 |
|---|---|
| `WorkshopModels.kt` | manifest 校验与草稿/条目数据模型 |
| `WorkshopStore.kt` | 工坊目录：扫描、草稿装载、删除与数据文件 |
| `WorkshopDataStore.kt` | 每个创意的 SQLite 键值数据层 |
| `WorkshopTools.kt` | 建草稿/装载/列出/删除/改数据的工坊工具 |
| `WorkshopBridge.kt` | 页面与 App 的 JS bridge 方法路由 |
| `WorkshopAgentService.kt` | 页面发起的一次性 Agent：临时引擎与工具裁剪 |
| `WorkshopAgent.kt` | 一次性 Agent 的请求、事件与网关模型 |
