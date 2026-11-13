# runtime · Agent 运行时

DeepDeep SDK（`io.github.whywood.deepdeep.sdk`）之上的 agent 运行时：会话与事件流、工具、Provider/凭据、MCP、定时与目标、后台任务、小程序、工作区。系统提示词由 SDK `buildSystemPrompt` 生成（不再维护 `SYSTEM.md` 与工具规则段）；会话以 pi JSONL 为唯一权威，目标与任务只在进程内；测试在 `src/test/kotlin`（JUnit4 + coroutines-test）。

## 文件

### 会话、工作区与引擎

| 文件 | 职责 |
|---|---|
| `AgentRuntimeHost.kt` | 会话总管装配：事件流入口、接口委托与目录适配构造 |
| `RuntimeHandle.kt` | 单个已打开会话的句柄：引擎、会话、cwd、命令状态、排队输入与活跃时间 |
| `RuntimePool.kt` | 句柄池：打开/关闭/重载/淘汰/重配与按会话取句柄；关闭时按原因发出 `SessionClosure`；自定义 Provider 变更时下发到所有已打开会话 |
| `RuntimeRuns.kt` | 运行编排：发送、排队、重试、提醒、后台回合、停止、终态与事件投影 |
| `RuntimeMcp.kt` | 对所有已打开会话重载 MCP 配置与重连单台服务器，输出状态日志 |
| `RuntimeEventTranslator.kt` | 把 SDK 会话/Agent 事件翻译为运行时事件与活动状态 |
| `RuntimeWorkspaces.kt` | 工作区列表、增删改、Home 解析与可见 cwd |
| `RuntimeModels.kt` | 会话模型/思考档、历史、用量与压缩 |
| `RuntimeSessions.kt` | 会话查询、列表、重命名/置顶/归档与删除 |
| `PiSessionEngine.kt` | 装配 pi 会话：模型、工具、MCP、系统提示词与 `RuntimeHooks`；Agent 级工具钩子覆盖动态 MCP/tool_search，输入变换在 prompt 前完成；paths 段注入共享存储与挂载目录；构造参数 `excludedSkills`/`excludedMcpServers` 按工作区策略排除技能与 MCP 服务器（技能含被排除插件贡献的 skill id）；`pluginPromptOverrides` 每轮合并插件 prompt override（preamble/rules 单胜者、sections 按 priority、单插件 32KiB/总量 64KiB、冲突与超限记 `PluginPrompt` warn） |
| `PluginRuntimeHooks.kt` | `RuntimeHooks` 聚合（prompt/input/tool/会话绑定，`onSessionBound` 传 `SessionBinding`）与 Agent 级工具钩子桥（Block/Rewrite/Replace，保留 SDK 结果归一化） |
| `SessionEngine.kt` | 建会话、会话目录与工作区目录三组接口 |
| `RuntimeDirectories.kt` | 会话目录与工作区目录的存储适配器 |
| `SessionHandle.kt` | 单会话操作面：发消息、压缩、MCP、用量、插件自定义消息追加、自定义 Provider 注册 |
| `SessionBinding.kt` | 会话身份（id、文件、cwd）容器；保持身份相等供缓存；支持 host 注入的 live provider，插件工具执行时动态解析当前会话 |
| `SessionCommandPlane.kt` | 会话运行状态机：忙闲、runId、停止、重配 |
| `SessionIndex.kt` | 扫 JSONL 建索引，按大小与时间戳增量缓存 |
| `SessionCatalog.kt` | 会话列表：合并 JSONL 与置顶/归档/重命名 |
| `SessionMetaStore.kt` | 会话元数据（置顶/归档/重命名）存 DataStore |
| `HistoryMapper.kt` | JSONL 条目转消息与工具轨迹（含 rawArguments/结构化 resultPayload），识别定时提醒；回合内 CustomMessageEntry 追加为 `CustomCard` step、回合结束/空闲期投递的仍为独立消息，保留全部 Text step 并把末尾文本写入 message.text 作复制兜底；工具结果按 toolCallId 回填旧记录，FileRef 从 ToolResultMessage.details 的保留键还原 |
| `RunLedger.kt` | 落盘未完成运行，重启后提示被中断 |
| `HandleBudget.kt` | 空闲淘汰规则：闲置超 5 分钟即回收，不设数量上限；当前打开、忙碌、重配、keepAlive 的会话除外；每分钟扫描一次 |
| `SessionCloseReason.kt` | 句柄关闭原因（Idle/Reload/Reconfigure/Deleted/Closed）与 `SessionClosure` 事件模型 |
| `PendingInputs.kt` | 插话与排队输入的本地队列及认领 |
| `Personalization.kt` | 称呼、交互风格与语气等提示词分节；环境段由 `HostEnvironment.promptLines` 提供；`buildSessionSystemPromptSections` 支持插件 preamble/rules 覆盖与 plugins 段 |
| `PluginPromptOverrides.kt` | 插件 prompt override 模型与纯合并函数：preamble/rules 按 priority + 注册顺序取单胜者、sections 按 priority 排序去重、单插件 32KiB/总量 64KiB 超限跳过，冲突经 `PluginPrompt` warn 输出 |
| `PromptPaths.kt` | 提示词 paths 段：Skills / App data / Shared storage / Mounted directories（挂载目录名称、路径与说明） |
| `PromptResourceSections.kt` | 每轮重读提示词资源（技能、项目指令、附加系统提示），渲染 skills/project_context/addendum 三段覆盖建会话时的旧值；删空时用空串清掉旧段 |
| `SkillCatalog.kt` | 发现技能并渲染为提示词块 |
| `WorkspaceStore.kt` | 工作区列表持久化（DataStore） |
| `RuntimeLog.kt` | 包内 Log 包装，失败不抛 |

### 宿主环境

| 文件 | 职责 |
|---|---|
| `host/HostAccess.kt` | 系统访问档位契约：`HostAccess`（LOCAL/ADB/ROOT）、`HostAccessState`、只降级不自动升级的 `resolveHostAccess` 与 `HostAccessController` |
| `host/HostEnvironment.kt` | 宿主环境契约：`HostCapabilities`（bash 是否可用、系统 shell、Root 文件、屏幕控制、Root Linux 就绪）、`HostEnvironment`（bash/bashStreaming/files/system/promptLines）、`HostEnvironmentProvider`、`HostAccessUnavailableException` |
| `host/StreamingShell.kt` | 流式 shell 契约：`StreamingShellExecutor` 与结果模型 |
| `host/DynamicHostAccess.kt` | 执行时解析包装：`DynamicShellExecutor`/`DynamicFileSystem` 每次调用向 `HostEnvironmentProvider` 现查当前环境，避免会话级快照 |
| `RootFileSystem.kt` | su 支撑的 okio FileSystem：读写、stat、目录操作全经 root runner；列目录时一次 `stat` 批量取回类型与大小并缓存元数据，供文件浏览器等逐项分类 |

`PiSessionEngine` 只依赖 `HostEnvironmentProvider`：会话的工具（bash/文件）与提示词环境段在每次执行/每轮开始时动态解析当前环境，不在建会话时缓存。

### 工具

| 文件 | 职责 |
|---|---|
| `AppTools.kt` | App 专有工具注入：提问、插件工具与扩展工厂 |
| `ToolVariants.kt` | 从统一工具派生具名变体并校验参数 |
| `CommandResult.kt` | 命令结果模型与可带详细输出的执行器 |
| `DeepDeepBashTool.kt` | bash 工具：执行、截尾、退出码与超时；描述与模式无关，模式与路径布局由系统提示词环境段说明 |
| `SystemShellTool.kt` | system_shell 工具：Android 系统命令，合并输出与超时/退出码；执行器由 provider 每次调用时解析，当前档位没有系统 Shell 时返回错误提示切换档位 |
| `RootCommands.kt` | Root 命令结果模型与执行器接口（AdbBootstrap、无障碍注入等共用） |
| `AskUserQuestionTool.kt` | 结构化提问：1–5 题，等答最多 10 分钟 |
| `QuestionCenter.kt` | 待答问题的状态流、回答/取消与持久化钩子 |
| `QuestionPersistence.kt` | 待答问题落盘，重启后提示曾中断 |
| `PresentTool.kt` | present 交付：文件、图片、视频、HTML、小程序 |
| `WorkspacePaths.kt` | 强制文件工具绝对路径，输出补全目录前缀 |
| `ToolPayloadMapping.kt` | SDK 工具内容 + details 保留键 ↔ runtime-api `ToolResultPayload`（含 FileRef 还原）、JsonObject 参数展示映射 |

### Provider、凭据与 MCP

| 文件 | 职责 |
|---|---|
| `ProviderCatalog.kt` | 从 pi 内置目录生成 Provider/模型清单与默认配置，合并 models.json 中的自定义 provider（组合失败的单独隔离），并把组合结果暴露给宿主下发到已打开会话 |
| `ProviderDirectory.kt` | Provider 与模型查询、配置归一接口 |
| `ProviderOAuthService.kt` | Provider OAuth 登录/登出，复用 pi 流程 |
| `SdkCredentialStore.kt` | 内存凭据缓存、串行写盘与鉴权种类流 |
| `ApiKeyStore.kt` | API Key 的加密存取（`api_key_` 前缀） |
| `OAuthCredentialStore.kt` | OAuth 凭据的加密存取（`oauth_` 前缀） |
| `EncryptedSecretStore.kt` | Keystore AES-GCM 加密的密钥存取 |
| `ModelConfigStore.kt` | 全局默认模型与思考档（DataStore） |
| `InstallationIdStore.kt` | 持久安装 ID，作 OAuth 设备标识 |
| `DeepDeepOAuthCallbackServerFactory.kt` | 本地 HTTP 回调服务器，收 OAuth code |
| `McpConfigStore.kt` | mcp.json 的读写、增删改、启停、导入与写盘变更流（`changes`） |
| `McpJson.kt` | 宽松解析粘贴的 MCP JSON，格式化单条 |
| `McpServices.kt` | MCP 服务器探测、OAuth 登录与鉴权；`McpPluginBridge` 供插件 tools/call |

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

### 小程序

| 文件 | 职责 |
|---|---|
| `MiniappModels.kt` | manifest 校验与草稿/条目数据模型 |
| `MiniappStore.kt` | 小程序目录：扫描、草稿装载、删除与数据文件 |
| `MiniappDataStore.kt` | 每个小程序的 SQLite 键值数据层 |
| `MiniappTools.kt` | 建草稿/装载/列出/删除/改数据的小程序工具 |
| `MiniappBridge.kt` | 页面与 App 的 JS bridge 方法路由 |
| `MiniappAgentService.kt` | 页面发起的一次性 Agent：临时引擎、工具裁剪与 `hooksFactory`（同一套插件钩子/会话绑定） |
| `MiniappAgentSupport.kt` | 小程序 Agent 的事件映射、请求校验/模型与工具选择、技能复制与资源文件系统 |
| `MiniappAgent.kt` | 一次性 Agent 的请求、事件与网关模型 |

### 长期记忆

| 文件 | 职责 |
|---|---|
| `MemoryMarkdown.kt` | 记忆文本工具：四区一级标题、日期与时间戳、预览/截断、标题分段与注入段渲染（16K 预算：profile 2.5K / context 2.5K / cookbook 800 / 今天 2.5K / 本月 1.2K / 本年 800） |
| `MemoryStore.kt` | `agent/memory` 下四区文件（profile/context/cookbook）+ 时间树 `daily/YYYY/YYYY.md → daily/YYYY/MM/YYYY-MM.md → daily/YYYY/MM/YYYY-MM/YYYY-MM-DD.md`：原子写、stable 快照、BM25 语料、归卷标记、纪要去重与水位、纪要失败持久队列 `pending/` |
| `MemorySearch.kt` | 自研 BM25 检索：中文 bigram、拉丁分词与片段摘要 |
| `MemoryTools.kt` | `memory_write / memory_read / memory_search / memory_status` 四个工具（write 目标 profile/context/cookbook/today；read 可读 month/year） |
| `MemoryRecap.kt` | 闲置/显式关闭与手动纪要：按 entries 水位只总结增量、自适应列表或 SKIP、便宜模型候选，写入当天日志；失败落 pending 下次启动重试 |
| `MemoryRollup.kt` | 日→月、月→年后台归卷：会话启动/App 启动触发，文件内 `## 日期` 标记幂等，模型失败即停且下次重试，不限流 |
