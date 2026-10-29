# Architecture

## 会话与工作目录（Home / Workspace）

- 工作目录从 `workspace` 更名 `home`；数据迁移在 AppGraph 初始化前执行：`files/workspace`→`files/home`，旧 session 目录按 cwd 编码重命名并重写 JSONL 头的 cwd。迁移只匹配完整路径段，不能把 `files/workspaces/...` 误写成 `files/homes/...`；旧版本写坏的 `/files/homes` 会话会修复回 `/files/workspaces`。
- Home 是默认工作区；用户在抽屉「工作区」里可新建/重命名/删除 workspace，目录为 `files/workspaces/<id>`，WorkspaceStore(DataStore) 只存 id/name/cwd/createdAt；旧 `files/projects`、旧 `projects` DataStore 与旧 session 目录/cwd 均做幂等迁移。
- 每条 session 绑定创建时的 cwd（home 或某个 workspace）；session 文件仍由 pi SDK 按 `AgentPaths.sessionsForCwd()` 存放在 `agent/sessions/<encoded-cwd>/`，列表由 `SessionCatalog` 按已知 cwd 聚合后叠加 SessionMetaStore（置顶/归档/标题覆盖）。
- 会话列表由 `SessionIndex` 支撑：`files/session-index.json` 以会话文件路径为 key 缓存 `size+mtime → sessionId/cwd/createdAt/name/messageCount`，列表只对新增或变化的文件解析 header 与 `session_info` 行，其余直接复用；`updatedAt` 取 header 时间与文件 mtime 的较大值，因此继续一个老会话会让它排到最近，排序仍是置顶优先再按 `updatedAt`。索引损坏或 schema 不符时自动重建，JSONL 仍是消息权威。
- 抽屉按工作区分组展示全部未归档会话（`ChatViewModel.refreshSessions()` 读取 `runtime.allSessions()`，用 session.cwd 匹配工作区）；工作区置顶是 UI 偏好，存 `AppPreferences`（`pinned_workspace_ids`），会话置顶仍由 SessionMetaStore 持久化。
- 新建会话入口只在抽屉：工作区行展开后的 + 与工作区长按菜单走 `ChatViewModel.newSession(workspaceId)`（Home 同样），顶栏不提供新建；顶栏 + 已移除。
- 附件导入/预览/文件引用解析都以当前会话 cwd 为 root（`AttachmentStore.import/file/shareIntent/resolve(raw, root)`），project 会话的附件落在 project 目录。

## 多会话并发

- `AgentRuntimeHost` 持有 `Map<sessionId, Handle>`：每个 Handle 有自己的 PiSessionEngine(cwd)、AgentSession、shell executor；会话之间互不关闭。空闲 handle 有预算：超过 6 个且空闲超 10 分钟时才回收，回收前检查 busy/重配/最近打开，并询问 App 的 `keepHandleAlive`（未投递提醒、活跃目标、运行中 job、待答问题、当前会话都算保留）。被回收的会话在下次 send/retry/提醒投递时按 `SessionCatalog` 懒重连（重新打开同一 JSONL），提醒不会因为会话空闲而被丢掉。
- `RunLedger` 记录每次运行的开始与结束（`files/tasks/runs.json`）。进程在运行中被系统结束，下次启动把这些残留记录转成 `interruptedRuns`，打开对应会话时插一条「上次运行被中断，结果未知」的本地提示，发下一条消息时清除；正常结束会删除记录，因此不会把完成的一轮误报为中断。
- `SessionCommandPlane` 是每个会话运行状态的唯一所有者：每会话一把锁、一个单调递增的 epoch；send/retryLastTurn/deliverReminder/deliverBackgroundTurn/stop 的状态迁移都在锁内完成，`busy` 只由持有该 `runId` 的路径清除。停止时 epoch 递增，旧的延时重试/目标续跑作废；后台入口在忙时把内容注入当前轮（`QueueBehavior.FollowUp`），空闲才新起 run，不再用“先读 busy 再决定”的竞态判断。
- 运行事件统一包装为 `SessionRuntimeEvent(sessionId, event)`；活动状态是类型化的 `RuntimeActivity`（思考/重试/压缩/运行工具），由 ChatViewModel 结合 `ChatUiStrings`（字符串资源）本地化后进入 UI，runtime 层不承载用户可见文案；ChatViewModel 用 `conversationCache` 保存每个会话的消息/流式状态，活跃会话直接更新 UI，后台会话事件写入缓存，切回时立即恢复并继续接收。
- 发送/停止按 sessionId 定向；`busySessionIds` StateFlow 供 UI 展示后台运行状态；新建会话/切换项目不打断正在运行的任务。
- 模型与思考档位是**会话级配置**：SDK 把 `model_change`/`thinking_level_change` 写进会话 JSONL，打开会话时从文件恢复；在某个会话里切换只改该会话，同时写入 `ModelConfigStore` 作为新会话默认值。设置页的厂商详情只做配置（API Key/OAuth），不提供模型切换或“设为当前”。
- 运行态以 `AgentRuntime.activeRuns: StateFlow<Map<sessionId, ActiveRunSnapshot>>` 为准：由 `SessionCommandPlane` 的 begin/finish/stop/detach 同步维护。事件仍是 UI 快速通道，但结构事件（RunStarted/终态/后台轮）在事件缓冲溢出时会用挂起 emit 兜底；`ChatViewModel` 在“busy 消失但仍在 sending”、回到前台、无活动 run 的 stop、打开会话快照落地后二次确认这几个点做对账：用 history + sessionModel 重建消息并清掉 streaming/sending，避免后台唤醒、锁屏断网重试后 UI 停在运行中。
- 插件能力变化（启停、安装、插件设置变更、MCP 配置更新）只调用 `AgentRuntimeHost.reconfigure()`：空闲会话立即重建，忙碌会话登记 `pendingReconfigure`，在该会话本轮结束的安全点再重建；重配期间该会话的新发送会得到 busy 错误，不会出现“等待全部空闲后重建”时被新 run 插队、或正在运行的会话被关闭。

## 模块

```
:app
 ├─ :runtime   pi2kotlin 适配、事件、Provider 目录、会话元数据、Key
 ├─ :shell     Alpine/proot、LinuxShellExecutor
 └─ :design    Compose 主题、字体、motion tokens、组件
```

依赖方向：`:app` 依赖三个模块；`:runtime`/`:shell`/`:design` 不互相依赖。

## 运行时

```
Compose ChatScreen
  → ChatViewModel
  → AgentRuntime interface
  → AgentRuntimeHost
  → PiSessionEngine
  → createAgentSession(...)
  → provider/model
  → RuntimeEvent(runId, ...)
  → ChatUiState
```

关键点：

- `AgentRuntime` 是接口，`AgentRuntimeHost` 实现；Chat 层可替换/测试。
- 每次发送生成 `runId`；流式事件带 runId，UI 丢弃旧 run 的迟到事件。
- `RunStarted` / `ActivityChanged` / `TextDelta` / `ReasoningDelta` / `ToolStarted` / `ToolFinished` / `RunFinished` / `RunFailed` 组成 UI 投影。
- 重试：`AgentRuntime.retryLastTurn` 走 SDK 的 `retryLastAssistant()`——先按 context edit 移除末尾失败的 assistant 回合，再从原用户消息继续运行并复用重试循环，所以不会追加新的用户消息；`AgentSession.canRetryLastAssistant()` 用来判断是否可重试。
- 自动重试在 `ChatViewModel`：`RunFailed` 后按错误类别决定是否重试（超时/鉴权/余额/模型不存在不重试），最多 5 次、间隔 2 秒，状态行显示 `自动重试 n/5`；成功后清零。停止会同时取消该会话尚未到点的重试倒计时，倒计时结束前还会复核自己是否仍是当前重试任务，避免“点了停止又自己跑起来”。倒计时期间忽略失败 runId 的终态 `ActivityChanged`，否则宿主的“活动清空”事件会把倒计时标签覆盖回默认的“思考中”。
- 模型切换：`ChatViewModel.setProvider` 用 `ProviderDirectory.models()` 选该厂商当前或首个模型，经 `runtime.setSessionModel` 只改当前会话，并写入 `ModelConfigStore` 作为新会话默认值；composer 的模型 sheet 顶部提供已配置厂商下拉。
- 目标续跑：宿主成功结束后向 `GoalDriver.nextRound` 取“下一轮提案”（含 goalId/revision），400ms 后在同一会话的命令面复核 epoch、用 `commitRound` 复核 revision/ACTIVE/轮次上限，通过才真正启动并递增轮次；期间暂停/清除/编辑目标或停止会话都会让旧提案失效。
- 发送前先写 session 标题并发出 `SessionListChanged`，抽屉立即出现新会话。
- 全进程共享一个 OkHttp `HttpTransport`（`AppGraph` 构造）：ProviderCatalog、Provider OAuth、MCP 探测/OAuth、每个 `PiSessionEngine` 与 `WorkshopAgentService` 都复用它，引擎不再各自创建。
- ProviderCatalog 从 pi2kotlin `builtinProviders` 读取 42 家 Provider、模型数和 OAuth 标记；`ProviderOAuthService` 用 SDK 的 `bundledOAuthFlows()` 提供登录/退出，浏览器回调由 App 设置 `OAuthRuntime.callbackServerFactory` 后走本机 loopback。
- OpenCode（Zen / Go）的网关要求每个请求带 `x-opencode-session`，否则返回 400 `MissingSessionID`；SDK 0.1.2 起由 `Models` 层用 `StreamOptions.sessionId` 自动注入，coding agent 把会话 id 传进 `AgentOptions`。
- ModelConfigStore 保存 provider/model/thinking；聊天 composer 切换模型和思考。
- 凭据：`EncryptedSecretStore`（Android Keystore AES-GCM + DataStore `secrets`）支撑 `ApiKeyStore`（`api_key_` 前缀）与 `OAuthCredentialStore`（`oauth_` 前缀，存 OAuth 原始 JSON）；`SdkCredentialStore` 合并两者、维护内存 cache 与 `authKinds` StateFlow，并按 provider 保证 API Key / OAuth 只存在一种（写一种会清另一种）。落盘按 provider 串行（修订号 + 写入任务链，只有最新意图落盘），`awaitCommit(providerId)` 可等提交完成；旧 token 刷新不会覆盖之后的登出/换 Key。provider OAuth token 的自动刷新由 SDK resolver 完成。
- SessionMetaStore/SessionRepository 在 SDK JSONL 之上维护标题覆盖、置顶、归档和删除。
- 会话文件读取/写入在 IO dispatcher；Chat 层注入 `ioDispatcher` 以便测试。
- ChatViewModel 为每个已加载过的会话保留消息快照缓存；再次打开时先渲染缓存，再在后台 `open` SDK session（单 mutex 串行，epoch 丢弃过期打开），发送会等待打开完成。

## OAuth 与 MCP

- Provider OAuth 使用 pi-ai 的 9 个内置 flow；`OAuthRuntime.callbackServerFactory` 在 `AppGraph` 初始化时设为 Android 的 loopback 服务器工厂。登录交互（浏览器、选择、输入）由 App 的 `OAuthLoginSession` / `OAuthLoginSheet` 承载；成功后写 OAuth 凭据并清掉该 provider 的 API Key。App 自定义 `DeepDeepOAuthCallbackServerFactory` 替代 SDK 默认实现：授权回调页返回后自动跳到 `deepdeep://oauth/callback`（MainActivity 已注册 VIEW intent filter），因此浏览器授权完成会直接回到 App。
- OpenAI ChatGPT OAuth 要求每次安装的稳定 UUID（`ext_agent_host_id`）与 agent 名称：`InstallationIdStore` 在 `files/agent/install-id` 持久化一个 UUID，登录时以 `OAuthLoginOptions(deviceId=…, agentName="DeepDeep")` 传给 SDK。
- MCP 使用 pi2kotlin 0.2.4 的 `McpManager`：配置存 `files/agent/mcp.json`（`mcpServers` 格式，原子写），OAuth token 存 `files/agent/mcp-auth.json`；App 只管理 Streamable HTTP 服务器（Android 无 stdio 子进程）。
- `PiSessionEngine` 以 `McpOptions(loadConfig=true, samplingEnabled=false, rootsEnabled=true, autoEnableToolSearch=true)` 创建会话：deferred 工具由 `tool_search` 加载，resources 工具自动注册；工具名为 `mcp__<server>__<tool>`。
- `McpConfigStore` 负责读写配置并保留未知/stdio 字段；`McpServerProbe` 独立探测连接与工具数（供 MCP 页状态展示），`McpOAuthService` 负责 MCP OAuth 登录/退出。保存配置后 `AgentRuntimeHost.reloadMcp()` 对已打开会话热更新，登录后 `reconnectMcp()` 重连；两者都在 IO 线程执行并串行化，失败写日志。
- `McpOAuthSession` 用本机 loopback 回调（`startOAuthCallbackServerOrNull`）接收浏览器授权结果，失败时回退到粘贴回调链接。

## 官方插件

- 插件在 `app/.../plugin/`：`OfficialPlugin` 契约描述生命周期与贡献，不包含 Linux/CLI/登录概念；具体实现放在 `plugin/<name>/`（飞书 `plugin/feishu/`、网络搜索 `plugin/websearch/`…），内部插件在 `plugin/internal/`。
- 插件分两类（`PluginKind`，默认 `EXTERNAL`）：
  - 外部插件：需要下载安装，有下载/更新/重新安装/删除入口；安装/卸载是事务，失败只清理暂存，不清除旧版本、记录与技能。
  - 内部插件：随 App 提供，`PluginManager.start()` 为没有记录的内部插件补建安装记录（已安装、默认启用、version/skillIds 来自插件），安装/卸载为空操作，不参与更新检查，详情页不显示下载/更新/删除入口；停用后列表与详情页入口保留，只是不再贡献工具/技能/提示，重新开启恢复。当前：`goal`（目标）、`jobs`（后台任务）、`schedule`（定时任务）、`workshop`（创意工坊）。
- `PluginManager` 负责安装记录与启用状态（`files/plugins/plugins.json`，schemaVersion 2，原子写）、插件设置（`files/plugins/settings.json`，schemaVersion 1）、全局互斥、安装/卸载/更新/启停、状态探测与缓存（60 秒 TTL，显式刷新强制）、技能同步（仓库 `files/skills` + 生效 `files/agent/skills`，`.deepdeep-plugin` 标记）、工具收集、进度通知与系统提示拼接；它不依赖 shell，也不引用具体插件。
- 设置：`PluginSettingSpec` 分 Toggle / Choice / Number，声明 key、标题、默认值与取值范围；缺失值取声明默认值，设置文件缺失或损坏按全部默认值运行。`PluginManager.setSetting` 校验（Number 夹到区间、Choice 校验选项）后落盘、调用 `plugin.onSettingsChanged(含默认值的完整映射)`、重新同步技能并触发 `host.reconfigure()`。运行时直接消费的设置由 `AppGraph` 注入默认参数 lambda：目标 `max_rounds`（0 不限）→ `goalTools(defaultMaxRounds = …)`；后台任务 `max_running`（1–20，默认 10）→ `JobRegistry(maxRunningPerSession = …)`；创意工坊 `max_turns`（1–32，默认 16）→ `WorkshopAgentService(defaultMaxTurns = …)`；`skill_enabled` 控制工坊技能是否贡献。
- 插件贡献：`promptBody`（每轮系统提示）、`skills`（技能，支持多文件）、`tools`（`CodingTool`）、`settings`（设置项）。只有已安装且已启用的插件参与；启停、安装、设置变更、MCP 配置变化后调用 `host.reconfigure()`，由宿主的会话命令面在安全点更新能力快照（忙碌会话排到本轮结束，不会被提前关闭）。
- 工具注入：外部插件实现 `tools()`，内部插件实现 `tools(binding: SessionBinding)` 以取得会话绑定；`PluginManager.tools(binding)` 按 kind 分派并按工具名去重。定时任务 `schedule_*`、目标 `goal_*`、后台任务 `job_*` 都由对应内部插件贡献，不再由 `appTools` 直接注入；`present` 仍由 `AppGraph` 的 `extraToolFactories` 提供。
- 技能安装：`PluginSkill` 携带 `markdown`（`SKILL.md` 正文）与可选 `files: List<PluginSkillFile>`（相对路径 + 文本内容）；写入时先在临时目录写全 `SKILL.md`、随附文件与标记，再整体替换目标目录，路径拒绝绝对路径、`..`、空段、保留名与重复项。同名无标记目录按规则认领：普通插件技能要求 `SKILL.md` 内容一致才整体替换，不一致保留用户版本并跳过该技能；`legacyOwned` 技能直接整体替换。创意工坊技能即 `legacyOwned` 多文件技能，升级设备上旧的 BuiltinSkills 目录会被整体替换。
- 安装/登录态的判定属于插件个体：飞书插件自己执行 `lark-cli auth status --json` 并映射为 `Ready`/`NotReady`/`Failed`；框架只显示它返回的文案。
- Agent 入口是 `plugin_setup`（runtime `PluginTools.kt` → `PluginController` → App 的 `PluginManager.setup`）；UI 与 Agent 共用同一份 `PluginsUiState` 与同一个全局 setup sheet。Agent 直接用 bash 操作插件命令后，插件页进入/App 恢复/一轮运行结束会按 60 秒 TTL 刷新状态缓存；App 回前台时若 setup 已就绪会完成等待并关闭 sheet。
- `PiSessionEngine` 增加 `pluginPromptBody` 参数，在 hooks 中每轮注入 `official_plugins` section；`AppToolContext.pluginTools(binding)` 在会话创建时收集插件工具，内部插件拿到 binding，工具执行时读取已绑定的会话。
- 飞书插件安装官方 CLI 到 guest `/usr/local/bin/lark-cli`，凭证留在 CLI 自己的 `/root/.lark-cli` 与 `/root/.local/share/lark-cli`；不上报运行环境包列表。网络搜索插件不依赖 Linux，复用 DeepSeek provider 的 API Key 调 Search/Fetch API。

## 个性化与提示词

- 基础系统提示词由 SDK 的 `SYSTEM.md` 覆盖机制替换：`PiSessionEngine` 在创建会话前确保 `files/agent/SYSTEM.md` 存在（已存在则保留用户编辑），SDK 读到后不再使用自带的 "You are an expert coding assistant operating inside pi" 前言。工作区里放 `.pi/SYSTEM.md` 可覆盖全局这一份。
- 自定义前言会让 SDK 跳过它自己的 `<tools>`/`<rules>` section。`<tools>` 不需要重建：模型真正拿到的工具定义来自 system 消息的 `toolsAdded` 字段，provider 层据此组装请求的 `tools`。runtime 只重建 `<rules>`（各工具 `promptGuidelines` + 简洁/路径规则）作为静态 section。
- `PersonalizationStore`（DataStore `personalization`）保存称呼、交互模式、提示词预设、用户指令；`PiSessionEngine` 通过 `CodingHooks.extraSystemPromptSections` 每轮注入两个动态 section：
  - `environment`：当前日期（不含时分秒）、当前会话实际模型、工作区、Linux 技能挂载（`/skills`、`/skill-repo` 可直接 bash 读写）、称呼、交互风格与对应规则；手动包 `<environment>…</environment>`。日期只按天变化、技能说明固定，避免每轮对话都触发 system patch；会话 JSONL 里的 `model_change` 会让模型标签按需更新。
  - `instruction`：用户长期偏好；仅非空时注入，手动包 `<instruction>…</instruction>`，内容做 XML 转义。
- hook extra 不会自动包 section 标签，包装在 runtime 内完成。
- 提示词随每轮 `syncSystemPromptIfNeeded()` diff 生效（只追加变化的 section）；修改个性化不需要重启会话。Skills 的 `<skills>`、AGENTS 的 `<project_context>` section 由 SDK 注入。

## Skills 与工作区规范

- 技能仓库 `files/skills/<id>` 不被 SDK 扫描；全局开启复制到 `files/agent/skills/<id>`，项目开启复制到 `<cwd>/.pi/skills/<id>`。插件技能同样写入仓库，并由插件启用状态控制生效副本；因此技能页能看到插件技能。插件技能可携带随附文件（`PluginSkillFile`：`references/*.md`、`examples/*.html` 等），相对路径与 `SKILL.md` 一起落盘；无 `.deepdeep-plugin` 标记的同名目录按内容认领（`legacyOwned` 技能直接整体替换），不一致时保留用户版本并跳过注入。
- 项目技能页展示全部仓库技能；全局技能固定开启、不可关闭；全局开启会移除对应项目副本，避免关闭全局后项目副本继续生效。
- 自动入库：打开技能页（以及页面回前台）和每轮 RunFinished 后，`SkillRepository.syncActiveSkills()` 扫描 `files/agent/skills`、home 与各工作区的 `.pi/skills`；合法技能（安全 id、能解析出 name/description、非 `.deepdeep-plugin` 标记）复制进仓库 `files/skills`，幂等且不覆盖已有 id。因此 Agent 自写的技能会出现在技能页，位于全局生效目录的技能自动显示为已启用。
- Linux 内固定挂载 `/skills`→`files/agent/skills`、`/skill-repo`→`files/skills`（PRoot `extraBinds`，rootfs 目标目录自动创建）；Agent 可直接用 bash 写这两个目录。写入 `/skills` 对新会话生效，已有会话仍可用 `/skill:<name>`（每次重读正文）或点「重新加载」。
- 聊天右上角「重新加载」调用 `AgentRuntime.reloadSession`：关闭并重新打开当前 session 文件，重新读取 skills/AGENTS.md；运行中的会话拒绝重载。
- 使用复制而非软链接：PRoot 只绑定当前 cwd 到 `/workspace`，仓库在 cwd 外，软链接内的脚本在 `bash` 中无法解析。
- AGENTS.md 由 SDK 原生读取：先 `agentDir`，再 cwd 祖先；每个目录按 `AGENTS.override.md`、`AGENTS.md`、`AGENTS.MD`、`CLAUDE.md`、`CLAUDE.MD` 取第一个。App 编辑工作区根目录的有效文件，保存后新会话生效。
- 技能/AGENTS.md 都在 `createAgentSession` 时加载；已有会话保持旧快照，不自动重建。

## App 工具

- `PiSessionEngine` 用 `defaultCodingTools(...)`（含 SDK 的 `bash`）后追加 `appTools(cwd, AppToolContext, SessionBinding)`；`AppToolContext` 提供 `questionCenter`、`pluginController`、`pluginTools: (SessionBinding) -> List<CodingTool>` 与 `extraToolFactories: List<(Path, SessionBinding) -> List<CodingTool>>`。`pluginTools` 汇总外部插件 `tools()` 与内部插件 `tools(binding)`；`AppGraph` 的 `extraToolFactories` 只保留 `present`。SDK 依赖 pi2kotlin 0.2.4（Maven Local 发布，含 JSONL 尾部修复与 read 流式读取）。
- `QuestionCenter` 维护待答队列并暴露 `StateFlow`；`PendingQuestion` 带 `sessionId`，UI 按当前会话过滤并内联渲染。App 不在前台时直接返回错误。停止会话通过 `RunSignal` 取消等待。待答问题写入 `files/questions/pending.json`：正常问答/取消会删除记录，进程在等待中被杀则启动时转成 `InterruptedQuestion`（`QuestionCenter.interrupted`），App 在打开该会话时插一条本地提示，发下一条消息时清除，并在启动时汇总发一条通知。
- `SessionBinding` 在会话创建后绑定 `AgentSession`（sessionId/sessionFile/cwd）；内部插件的 schedule/goal/job 工具据此把数据绑定到调用它的会话。
- `ScheduleStore` 保存 `files/schedules/schedules.json`（schemaVersion 2）；`ScheduleService` 负责计时、批量与投递，工具与任务页都经它读写。
- 到点提醒投递回原对话：`ScheduleService` 打开绑定会话并调用 `AgentRuntime.deliverReminder`，由宿主注入 `[定时提醒]` 消息并开始一次运行；不新建会话、不另开工作区。投递前先写 `files/schedules/delivery-intents.json`（intent 含 `scheduleId@nextRunAt` 去重键），成功后标记已投递再删 intent；进程在投递中死亡则下次启动 `recoverPending()` 补投一次，已投递的不重复投。
- `GoalStore` 保存 `files/goals/goals.json`（按会话），`GoalService` 同时实现 `GoalController`（UI 读/建/暂停/继续/完成/清除）与 `GoalDriver`（宿主每轮记录 `TurnSource`、成功后取下一轮 prompt、停止时暂停）；目标轮由宿主以 `BackgroundTurnStarted` 事件开新的一轮，`TurnNotice.GoalRound` 带 objective，UI 渲染成带图标的目标轮提示。轮次上限来自目标内部插件设置 `max_rounds`（默认 0 表示不限），作为 `create_goal` 未传 `max_goal_rounds` 时的默认值；加载后目标为未激活（显示暂停），需用户继续。
- `JobRegistry` 保存进程内后台任务：环形输出（运行中 256KB / 结束后 16KB）、模型增量游标、每会话运行中任务上限（默认 10，由后台任务内部插件设置 `max_running` 控制，范围 1–20）、等待上限 600s；`job_start` 创建任务，`job_output`/`job_list`/`job_kill` 读取与停止；`LinuxJobRunner` 走会话 cwd 对应的 `LinuxEnvironment.executeStreaming`；任务完成按聊天设置决定是否唤醒模型，唤醒轮在聊天流里只留一行极简任务标记；会话归档/删除时清理。任务记录同时写入 `files/jobs/jobs.json`（最多 50 条、输出只留尾部 16KB）；进程重启后上次仍在运行的 job 显示为 `interrupted`（结果未知）并保留输出尾巴，不会静默消失。输出触发快照发布按 100ms 合并（最多 10 次/秒，末次最新），开始/结束/停止等状态变化仍立即发布。
- `present` 交付 v2：`items`（1–4 项）显式指定 `file|image|html|creative`；文件/图片/网页按 cwd 解析并校验存在性，创意经注入的 `WorkshopStore` 查询清单后返回 id/name/icon/version。聊天里图片内联预览、HTML 用内联 WebView 渲染并可全屏、文件为行卡、创意为入口卡；旧 `files` 输出与旧工坊入口卡保持兼容。
- 设备短信工具已移除；短信与通话改由「小米云服务」插件读取（`mi_cloud_sms`），不申请 `READ_SMS`。
- GUI 插件（官方插件 `gui`）通过 App 自己的 `AccessibilityService` 暴露 `gui_observe/find/tap/set_text/scroll/global/wait/screenshot`；无障碍服务需用户在系统设置手动开启，未开启时插件状态为未就绪、工具返回明确错误。截图保存到 `files/gui/screenshots/`，PRoot 固定挂载为 `/gui`。
- `/` 命令是 UI 层能力：`ChatCommand` 定义命令表，`parseChatCommand` 解析，`DeepDeepApp` 分发；命令不写入会话 JSONL，压缩等提示类结果以 `NoticeEvent` 进入消息流。
- 上下文用量：宿主 `usage()` 用 `SessionManager.getSessionStats()` + `estimateContextTokens(buildSessionContext())` 计算，`UsageChanged` 推给 UI；`/compact` 调用 `AgentSession.compact()`。
- 工具分类与契约见 `docs/TOOLS.md`，调度语义见 `docs/SCHEDULING.md`，命令见 `docs/COMMANDS.md`。

## 运行时事件

- `SessionRuntimeEvent(sessionId, event)` 是宿主唯一的事件出口；会话级事件带 sessionId，宿主级事件用空 sessionId。
- 事件类型：`RunStarted`、`ActivityChanged`、`TextDelta`、`ReasoningDelta`、`ToolStarted`、`ToolFinished`、`RunFinished`、`RunFailed`、`SessionListChanged`、`TurnInjected`、`SessionRemoved`、`SessionArchived`、`NoticeEvent`、`BackgroundTurnStarted`、`UsageChanged`。
- `RunFailed` 由宿主在运行结束后检查最后一条 assistant 消息的 stopReason 产生（`Error`），带厂商错误原文；UI 统一本地化后展示在失败轮上。
- `TurnInjected` 是宿主主动注入的用户消息（定时提醒），UI 追加用户气泡；空闲时同时补一个流式 assistant 占位。
- `NoticeEvent` 是本地提示（压缩结果、目标轮次、目标完成/阻塞），UI 在消息流里渲染为一条灰字，不写入会话。
- `BackgroundTurnStarted` 表示目标续跑或后台任务唤醒开启了新的一轮：目标轮带 `TurnNotice.GoalRound`，UI 追加提示与流式 assistant；后台任务唤醒带 `TurnNotice.JobFinished`，UI 追加一行极简任务标记与流式 assistant。`UsageChanged` 携带会话的上下文与累计用量。
- 事件是快速通道而非唯一真相：宿主用 `activeRuns` StateFlow 记录每个会话的活动 run，UI 在 busy→idle、回前台、打开会话、无 run 的 stop 时用 `history`+`sessionModel` 对账；reducer 在终态 runId 对不上但仍在 sending 时把最后一条 streaming 消息终结，避免“回答已结束但界面还在转圈”。
- UI 只订阅事件与状态流，不轮询；`ScheduleService.schedules` 是任务页的数据源。

## 创意工坊

- 每个创意是 `files/workshop/apps/<id>/` 下的自包含静态站点（`manifest.json` + entry + 静态资源），形态完全由 Agent 按需求决定，没有分类/模板。App 用 WebView 渲染，每个创意运行在独立的虚拟域名 `https://<id>.workshop.local/` 下。
- 资源由 `WebViewAssetLoader` + 自定义 PathHandler 从创意目录提供（MIME、`Cache-Control: no-store`、`Range` 206）；`WebViewCompat.addDocumentStartJavaScript` 在页面脚本前注入 bridge SDK（`window.deepdeep` / `window.app`）与主题 CSS 变量；`WebViewCompat.addWebMessageListener` 只接受该 origin 的主框架消息。
- Bridge 暴露页面数据与轻提示：`info`、`data.get/set/remove/keys`、`ui.toast`，以及页面驱动的 `agent.models/capabilities/run`（一次性任务 Agent，见下）；页面不能调用其他 App 内部函数、不能读写其他创意或 App 数据。详情页只读，修改页面统一回到聊天。
- 页面 Agent 是事件流：最终 Promise 仍返回完整文本，同时可选 `onEvent` 收到 `{id,event}` 增量包（`run_start/text_delta/thinking_delta/tool_start/tool_end/turn_end/run_end/run_error`）。native 侧按 40ms 合批、`webView.post` 保序并在终态前 flush；超时/失败会发 `run_error` 并 reject（不再让页面 Promise 永久 pending），maxTurns 中止时 `run_end.truncated=true`。页面 Agent 的能力是显式白名单：`read`/`write`/`edit`/`ls`/`grep`/`find` + 联网的 `web_search`/`web_fetch`；没有 `bash`，也没有 `gui_*`、`arrange_*`、`mi_cloud_*`、`job_*`、`goal`、`present`。文件工具绑定一个只能落在本次 run 临时 workspace 内的 `ScopedFileSystem`（canonicalize 后校验祖先，`../`、绝对路径、外指 symlink 都被拒）。
- 代码与数据分离：页面数据保存在 `files/workshop/userdata/<id>.db`（SQLite KV）；页面经 bridge 读写，Agent 经 `workshop_edit` 工具（action=get/set/delete/keys）读写同一数据层（不暴露原始 SQLite），重写页面不影响数据；删除创意同时删除目录与 DB。
- 创建流程由普通会话完成：`workshop_create` 在 `files/workshop/build/` 建草稿（只写 manifest.json）→ Agent 用绝对路径写页面 → `workshop_load` 校验入口/清单/大小并原子移动到 `apps/<id>/`；`workshop_load` 结果带 `delivery:"present"` 标记，Agent 需显式调用 `present`（`type=creative, id=…`）交付，聊天里出现入口卡。旧历史里没有该标记的 `workshop_load` 输出仍按旧逻辑显示入口卡；工坊页进入时扫描 `apps/` 作为导入兜底，草稿目录不展示。
- 内置 workshop 技能由创意工坊内部插件贡献：`WorkshopPlugin.skills()` 读取 `app/src/main/assets/skills/workshop/` 下的 `SKILL.md` 正文，并把 `references/*.md`、`examples/*.html` 等作为随附文件随技能写入技能仓库与全局生效目录，教普通 Agent 格式、工具、页面规范与数据 bridge；`skill_enabled` 关闭时不贡献技能。旧的 BuiltinSkills 目录按 `legacyOwned` 规则整体迁移。`SkillCatalog` 读取已启用技能；`/skill:<name>` 在发送前把技能文件内容渲染成 `<skill>` 块作为用户消息（同 pi 的 skill command 语义），命令可出现在消息任意位置、单条可加载多个技能；UI 隐藏该块、只显示用户文字。
- 工坊页 `+` 会新建 Home 会话并预填 `/skill:workshop `；列表图标/说明来自 manifest，缺失 icon 使用 ✦。

- 页面驱动 Agent：`WorkshopBridgeRouter` 增加 `agent.models/capabilities/run`，`WorkshopAgentService` 实现 `WorkshopAgentGateway`。每次 run 在 App 进程内用一次性的 `PiSessionEngine` 执行：临时 workspace/agentDir、白名单工具、按请求复制的 skills、页面 systemPrompt 写入临时 `APPEND_SYSTEM.md`、关闭 MCP；文件工具走 `ScopedFileSystem`，引擎内部落盘（session/settings/skills）仍用系统文件系统，因此 SDK 的上下文文件扫描不会被误拦。最终返回最后一条 assistant 文本，同时把文本/工具/轮次事件经 `onEvent` 旁路发给页面。没有 JSONL 会话、没有后台任务、没有 run 持久化；调用协程属于 WebView 生命周期，页面关闭即取消。
- `WorkshopAgentService.models()` 从已配置 provider 读取模型；`capabilities()` 暴露白名单工具 + 已启用全局 skills；页面通过 `app.agent.run` 指定 provider/model/thinking/systemPrompt/maxTurns/tools/skills，`tools` 只能从 `capabilities()` 给出的名字里选，越界名字直接报错。`PiSessionEngine.createSession` 支持 toolsFilter/mcpEnabled/extraSystemPromptSections，默认行为不变。

## 通知

- `NotificationCenter` 由 `AgentService` 启动，收集 `RuntimeEvent`，维护 active runs。
- 前台服务通知：会话标题 + 当前 `RuntimeActivity`；多会话显示数量；动作停止最近会话。
- 完成通知：`RunFinished/RunFailed` 且 `AppVisibility.foreground == false` 时发出，点击带 sessionId 回到对应会话。
- 启动时的中断提示：如果上次进程在等待提问或运行中被杀，启动后发一条汇总通知（`session_results`，文案为“未完成项”），点击打开最早一条受影响会话；会话内另有本地灰字提示（提问中断 / 运行中断分别一条），发下一条消息时清除。
- 开发包安装：`DevPackageManager` 调用 `notifyInstall`，展示阶段/`n`/`m` 进度；完成后自动取消或发结果。
- 频道：`agent_runtime`（LOW）、`session_results`（DEFAULT）、`downloads`（LOW）。
- 通知权限沿用系统 `POST_NOTIFICATIONS`；设置页显示状态并可跳转系统通知设置。

## 开发包

- `DevPackageCatalog` 定义 3 个场景组合（环境基础 / 日常办公 / 开发编程）；`DevPackageManager` 在 proot 内执行 `apk update` + `apk add`，办公组合随后用 pip 安装 `python-docx`/`python-pptx`/`pdfplumber`。
- `PRootExecutor` 把会话 cwd 绑定到 `/workspace`；设置里开启「共享目录」且已授予所有文件访问权限时，额外把 `/storage/emulated/0` 绑定到 guest 的同名路径与 `/sdcard`，开启状态运行期切换、下一次 bash 生效。
- `PiSessionEngine` 在共享目录开启时给系统提示追加一节（英文），说明公共目录挂载位置与谨慎修改用户文件；会话创建时读取当前开关。
- `LinuxEnvironment.executeStreaming` / `PRootExecutor.executeStreaming` 提供逐行输出回调；按 `(n/m) Installing` 更新进度。逐行回调按 8KiB 上限切分，无换行的长输出不会让内存随输出增长。
- 每次命令前的安装检查是 O(1)：只看 rootfs 目录与 `.deepdeep-rootfs` 标记（读版本行），不遍历文件；磁盘占用只在进入运行环境页时统计一次并缓存，安装/重置后失效，因此命令启动成本不随已安装文件数增加。
- 安装状态不单独持久化，进入运行环境页时用 `apk info -e` 查询真实状态；Linux 重置后自动清空。
- 一次只允许一个安装；失败保留最后输出片段；安装任务由 app scope 持有，离开页面不取消。

## Provider 图标与字体

- Provider 图标按 provider id 映射到 `drawable-nodpi` / `drawable-night-nodpi` 的彩色 PNG；浅色/深色各一套，全部原色显示；Radius 也有官方图示。
- 字体 5 套（Instrument Sans / Geist / IBM Plex Sans / Newsreader / Lora）随 APK，默认 Newsreader；`DdFonts.apply` 切换 `DdType` getter 使用的 family，中文回退系统；切换后重建 Activity 以刷新全部页面。
- 许可统一在设置「开源许可」列出；Provider 图标的商标归各品牌方。

## 消息模型

- 一个 UI assistant message 对应一个用户回合。
- `AssistantStep` 有序保存 `Reasoning`、`Text`、`Tool`；`AssistantMessage.text` 是最终回复。
- 历史映射按用户消息分 turn，把连续 assistant/tool 消息聚合成一个 message；工具前的文本留在过程，工具后的最终文本成为 answer。
- 实时发送时文本先进入过程的 `Text` step；「过程自动折叠」开启时，最后一段 `Text` 一开始输出就作为流式 answer 移到过程外，过程折叠；关闭时过程始终展开，直到用户手动收起。结束时把尾部最终文本正式移到 answer。

## UI 与动效

- `ui/navigation`：`AppDestination`、`AppNavigator`、`AppScreenTransition`、`AnimatedScreenHost`。
- 设置层级使用 typed stack + 自研 `AnimatedScreenHost`：页面根节点各自持有 `graphicsLayer`，转场进度只在该 layer 的 draw 阶段驱动 translationX（push 新页从右滑入、pop 旧页向右滑出），不经过 `AnimatedContent`，也不再逐帧触发页面 measure/layout；聊天页常驻底层并单独成 layer。
- 页面状态按目的地保存：`rememberSaveableStateHolder()` + `SaveableStateProvider(routeKey)` 包住每个 destination，出栈一帧后 `removeState`；`AppNavigator` 的 routeKey 栈由 `rememberSaveable` 保存，因此进程死亡也能恢复栈与栈内页面状态。`key(routeKey)` 位于页面循环体最外层，保证进出页面不被同帧销毁重建。
- 会话运行时缓存：`ChatViewModel` 内按 sessionId 保存输入草稿/引用/附件/滚动锚点（`ScrollAnchor` 含 `atBottom`），`conversationCache` 为 LRU（8 个，当前会话与 `runtime.busySessionIds` 不淘汰），`onTrimMemory(RUNNING_LOW/CRITICAL)` 只清空闲会话。
- PredictiveBack：`AnimatedScreenHost` 用 `androidx.activity.compose.PredictiveBackHandler`，手势期间预组合返回目标页并用 `previewFraction` 驱动 shared-axis 位移/透明度；手势完成 `navigator.pop()` 并跳过常规动画，取消时回弹到 0；非手势返回走常规动画。manifest 已开 `enableOnBackInvokedCallback`。
- 深链接：`deeplink/DeepLink.kt` 用 `java.net.URI` 解析 `deepdeep://...` 为 `Session(sessionId)` 或 `Page(AppDestination)`；`MainActivity` 把 Session 注入既有 `pendingSessionId`、Page 注入 `pendingDeepDestination`；`DeepDeepApp` 在 ready 后 reset 到 Chat 并按需补父页（`PluginDetail`→Plugins、`WorkshopDetail`→Workshop）再 push 目标。`oauth` host 不参与导航。
- 聊天 base 在转场期间吃冻结的 `ChatUiState`，动画结束后再解冻；导航状态读取（`navigator.current`/`canPop`）隔离在 `NavigationChrome`/`NavigationBackHandler` 子 composable，避免导航时整壳重组。系统返回手势 -> pop。
- `design/DdMotion` 统一时长/easing；`DdTypingDots` 承载思考动画。
- 抽屉用持久 composition + `graphicsLayer` translation，避免开关时反复组合；遮罩只在可见时挂载，避免拦截触摸。
- 抽屉工作区树由 `sessions + workspaces + pinnedWorkspaceIds + expandedWorkspaceIds` 驱动，展开状态由 `DeepDeepApp` 的 `rememberSaveable` 集合持有；工作区/会话行用 `LazyColumn` + `animateItem()` 做展开/收起和重排。
- 设计系统组件：`DdSwitch`、`DdSegmentedControl`（无 ripple）、`DdDialog`（Dialog + IME 自适应）、`DdSearchField`、`DdTextField`、`DdSpinner`；设置/Provider/会话/归档页统一使用这些自写控件，不再直接使用 Material AlertDialog/Switch/CircularProgressIndicator。
- 运行环境页是设置分组内的标准行（DdSwitch + DdListRow + DdDialog），不再嵌套 wash 卡片。
- Provider 详情保存 key 后由 DeepDeepApp 通过 `ApiKeyStore.read(providerId)` 读出并传给 `ProviderDetailScreen(savedApiKey=…)`，UI 只显示掩码 placeholder。
- ChatScrollController 按实际 overflow 跟随；用户上拖后暂停。
- 按压态统一为圆角灰 wash（`DdPressable` / `DdListRow` / `DdMenu`），不使用方框 ripple。
- `DdMenu` 统一浮层圆角、纯白背景、图标左对齐、danger 项与按压态。
- Markdown 解析在 `MarkdownParser`（嵌套列表树、图片块、递归引用、表格、setext、行内 AST、引用式链接/脚注上下文）；渲染在 `ChatMarkdown`（行内 span、表格横滚、引用条、数学块、脚注列表）；`CodeHighlighter` 负责语法高亮，`MarkdownImage` 负责图片异步加载/缓存/失败占位与全屏预览。流式文本用有状态的 `MarkdownStreamPlanner` 增量规划：文本追加时复用已确定的行、定义与稳定块，只重扫末尾未闭合段；`ChatMarkdown` 按块文本缓存解析结果，只有尾部增长块重新解析。整段与增量结果由 `MarkdownChunkingTest` 对 48 篇语料与逐字符前缀做等价断言（含 CRLF 跨批边界）。
- 会话搜索：抽屉搜索图标进入 `Search` 页面，`ChatViewModel.loadSearchSessions()` 通过 runtime `allSessions()` 跨工作区读取未归档会话，按工作区分组、只匹配会话标题；归档页搜索独立保留。

文本选择由 Compose context-menu provider 接管（`SelectionActions`）：自绘文字版复制/全选/引用/朗读菜单，通过 copy action + 剪贴板读取选中文本，朗读使用系统 TTS（manifest 需要 TTS_SERVICE queries）；`SelectionCoordinator` 记录当前选区 bounds，屏幕根节点在 Initial pass 检测选区外按下并重置对应消息的选择子树，从而即使 Compose 不主动清除也能收起选择与菜单。
- 附件：`data/Attachment.kt` 定义 `AttachmentRef` 与会话文本编解码（`<attachments>` 块）；`FileAttachmentStore` 把 URI 复制到 `workspace/attachments/`、生成预览类型，并提供 `resolve(raw)` 把消息里的相对/绝对/file:// 路径映射回 workspace 内文件；`ChatViewModel` 负责待发附件与 prompt 拼装；`AttachmentUi` 负责气泡缩略图卡、composer chips、全屏预览（图片/文本/Markdown/不可预览）与 FileProvider 分享。
- 文件引用：Markdown 行内 AST 增加 `FileRef`（`MarkdownParser.isWorkspaceFileReference` 保守判定），`ChatMarkdown.onFileClick` 把引用渲染成可点击链接；未传回调时外观与普通文本一致。
- 消息长度操作在 `ChatTurn` / `ChatActions`（长按锚定菜单、引用预览在 `ChatComposer`）。
- Compose stability config 位于 `app/compose-stability.conf`，把 runtime 模型、UI state、AttachmentRef 与 java.io.File 声明为 stable；ChatScreen 热路径无不稳定参数。
- 流式增量在 `ChatViewModel` 侧合并：`TextDelta`/`ReasoningDelta` 进入最多 16ms 的批次，其他事件先 flush 再处理，保证工具/文本顺序和结束文本完整；后台会话仍直接写入缓存。
- IME bottom inset 动画值在 layout phase 读取（自定义 `Modifier.layout`），抽屉遮罩 alpha 在 draw phase 读取；避免动画期间整页重组。
- Markdown 行内 code 由 `isPathLikeInlineCode` 分类：workspace 文件引用渲染为链接，普通路径渲染为无背景 monospace，其他 code 使用中性 `codeBackground`；code span 匹配遵循 CommonMark（多反引号/空格裁剪/换行归一化）。
- 图标来自 `assets/icons/lucide-app-v1` 的 Lucide 微调版，按需转为 design 模块 VectorDrawable（线宽 1.75）；空态插画来自 `assets/mascot` 并导出为 app drawable-nodpi WebP，候选原图不打包。

## 存储

```
filesDir/
  home/             cwd，同时是 Alpine /workspace
  workspaces/<id>/  自建工作区 cwd
  agent/            agentDir，SDK sessions/skills
  session-index.json  会话列表索引（按文件 size+mtime 缓存，可重建）
  agent/skills/     全局技能；插件技能也在这里（插件启用时），带 .deepdeep-plugin 标记
  plugins/          plugins.json（插件安装记录与启用状态）、settings.json（插件设置，schemaVersion 1）
  skills/<id>/      技能仓库（SDK 不扫描；插件技能仓库副本也在这里）
  schedules/        schedules.json（提醒绑定到对话）、delivery-intents.json（投递中 intent）
  goals/            goals.json（按会话的目标与轮次）
  jobs/             jobs.json（后台任务记录，最多 50 条，重启后显示中断/未知）
  questions/        pending.json（等待中的提问，正常结束即删除）
  tasks/            runs.json（进行中的运行记录，重启后残留转“运行被中断”）
  workshop/apps/<id>/   创意页面（manifest.json、entry、静态资源）
  workshop/build/       Agent 写作中的创意草稿
  workshop/userdata/<id>.db  每个创意的数据（SQLite KV）
  linux/rootfs/     Alpine rootfs（插件二进制在 guest /usr/local/bin）
```

- `cacheDir/share-incoming/<token>/`：外部 App 分享进来的文件先落到这里，防止 URI 授权过期；插入会话附件后由后续启动自动清理。
- 会话 JSONL 由 SDK 追加写入并保证以 LF 结尾；打开时若发现半条损坏尾行，会先把坏尾备份到 `<file>.jsonl.corrupt` 再截断到最后一个合法记录，新记录不会再被拼到坏行后面丢掉。`.corrupt` 文件不参与会话列表。
- DataStore：`secrets`（API Key/OAuth/小米云 cookie）、`model_config`（新会话默认模型/思考）、`personalization`、`session_meta`、`linux_settings`、`app_preferences`、`voice`（自动朗读/识别后直发）、`micloud_capabilities`（小米云功能开关）。
- API Key、模型配置、会话元数据、个性化在 DataStore；字体选择在 SharedPreferences `font_settings`；会话正文由 pi2kotlin 写入 JSONL。

## Android 能力

- GUI 操作：官方插件 `gui` + App 内 `GuiAccessibilityService`（`BIND_ACCESSIBILITY_SERVICE`，`res/xml/gui_accessibility_service.xml`）。服务由用户在系统设置手动开启；`GuiAccessibilityState` 用 StateFlow 暴露连接状态，插件据此显示就绪/未就绪。工具为 `gui_observe/find/tap/set_text/scroll/global/wait/screenshot`，只操作当前活动窗口，节点 ref 只在下一次 observe/find 前有效。截图走 `AccessibilityService.takeScreenshot`（API 30+），文件在 `/gui/screenshots/`。
- 语音助手：系统 `SpeechRecognizer` 做语音输入（`RECORD_AUDIO` + `RecognitionService` queries），`TextSpeaker` 用系统 TTS 朗读回答；`AssistantRole` 通过 `RoleManager.ROLE_ASSISTANT` 申请系统助手角色，`MainActivity` 接收 `ACTION_ASSIST` 后打开语音面板。composer 麦克风按钮与系统助手入口共用同一面板；设置页不再单独提供语音助手行；后台常听/唤醒词不做。
- 接收分享：Manifest 声明 `ACTION_SEND`/`ACTION_SEND_MULTIPLE`，MainActivity 解析 stream/clip/text，`ShareStaging` 先复制到 cache，`DeepDeepApp` 在会话就绪且不在发送/切换时经现有 `ChatViewModel.attach` 插入附件，纯文本预填输入框；App 自身分享时 chooser 排除自己。
- 安排：官方插件 `arrange` 用 `CalendarContract` Provider 直接增删改查系统日历；只保留日历，不管理闹钟，也不需要精确闹钟权限。
- 小米云服务：官方插件 `micloud`，WebView 登录 i.mi.com；读走 HTTP，写走同源 WebView；功能开关与覆盖范围见 `docs/MICLOUD.md`。录音下载到 `files/micloud/recordings/`，PRoot 固定挂载为 `/micloud`。
- 系统助手/悬浮 UI 与 root 只做调研：`docs/ANDROID_ASSISTANT.md`、`docs/ROOT.md`，不注册工具。

## 设计中的系统

- 定时任务：`docs/SCHEDULING.md`（提醒投递、任务页编辑、手动执行已实现）。
- Skills：`docs/SKILLS.md`（仓库、zip 安装、全局/项目启停、自动入库、bash 挂载已实现；插件技能单独说明）。
- 插件：`docs/PLUGINS.md`（插件契约、外部/内部插件、插件设置、多文件技能与迁移、飞书 CLI、网络搜索、GUI 操作、安排、小米云服务、企业微信、安装/连接/UI 与 Agent 同步）。
- 创意工坊：`docs/WORKSHOP.md`（文件夹格式、bridge SDK 事件流、创建与修改流程）。
- Android 能力：`docs/ANDROID_CAPABILITIES.md`（GUI/语音/分享/安排与系统能力边界）。
- 小米云服务：`docs/MICLOUD.md`（登录、功能开关、工具、读写机制、覆盖范围与风险）。
- 系统助手：`docs/ANDROID_ASSISTANT.md`（研究，不实现）。
- Root：`docs/ROOT.md`（研究，不实现）。
- 工具分类：`docs/TOOLS.md`。

## 工具

- pi 原生：`read`、`write`、`edit`、`ls`、`grep`、`find`、`bash`（PRoot 前台阻塞，参数 `command`、`timeout`）
- App 层：`ask_user_question`、`present`、`plugin_setup`；由内部插件贡献：`schedule_create/list/update/delete`（定时任务）、`get_goal`/`create_goal`/`update_goal`（目标）、`job_start`/`job_output`/`job_list`/`job_kill`（后台任务）、`workshop_create`/`workshop_load`/`workshop_list`/`workshop_edit`（创意工坊）
- 插件贡献：`web_search`/`web_fetch`（网络搜索）、`gui_*`（GUI 操作）、`arrange_*`（安排）、`mi_cloud_*`（小米云服务）；工坊页面 Agent 只开放 `read`/`write`/`edit`/`ls`/`grep`/`find` 与 `web_search`/`web_fetch`
- 不注册 `device_info`、`open_app`、设备短信、联系人等未评审的 Android 能力工具；新增系统能力先评估权限与商店政策

## 功能接线

新增或改造一个功能时，按这个顺序接完，缺一项就算没打通：

1. 持久化：`runtime/<Feature>Store`，原子写、损坏时保留原文件、可注入 `Clock`、每次写入后发出 `changes` 信号。
2. 宿主服务：`runtime/<Feature>Service`，`start()/stop()` 生命周期、`StateFlow` 状态、监听 `runtime.events` 处理会话生命周期。
3. 模型工具：`runtime/<Feature>Tools`，`CodingTool` 薄适配，按调用会话绑定，结果是 JSON 文本。
4. 运行时事件：由宿主发出；UI 只订阅事件与状态流，不新增定时轮询。
5. UI：`app/ui/<feature>` 用 `collectAsStateWithLifecycle` 读状态、动作回调回服务，界面不直接持有 store。
6. 测试：store/service/reducer/纯函数单测 + `docs/DEVICE_TESTING.md` 验收条目 + `artifacts/validation/` 证据。
7. 文档：`docs/TOOLS.md`、`docs/UI.md`、本文件、`docs/STATUS.md` 同步；用户可见文案中英双语。

Android 能力类功能额外接线：Manifest 权限/组件（service、intent-filter、queries、uses-feature）→ `AppGraph` 装配（插件/工具/StateFlow）→ 设置页入口与运行时权限申请 → 工具 execute 内二次校验 → `docs/ANDROID_CAPABILITIES.md` 记录权限与平台限制 → 真机验收（部分能力需手动开启系统开关）。

## root 扩展

`:shell` 预留 `ShellExecutor` 实现位。以后增加 `RootShellExecutor`，同一个 `bash` 工具改用它即可；不新增工具名。
