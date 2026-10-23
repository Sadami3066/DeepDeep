# Architecture

## 会话与工作目录（Home / Workspace）

- 工作目录从 `workspace` 更名 `home`；数据迁移在 AppGraph 初始化前执行：`files/workspace`→`files/home`，旧 session 目录按 cwd 编码重命名并重写 JSONL 头的 cwd。迁移只匹配完整路径段，不能把 `files/workspaces/...` 误写成 `files/homes/...`；旧版本写坏的 `/files/homes` 会话会修复回 `/files/workspaces`。
- Home 是默认工作区；用户在抽屉「工作区」里可新建/重命名/删除 workspace，目录为 `files/workspaces/<id>`，WorkspaceStore(DataStore) 只存 id/name/cwd/createdAt；旧 `files/projects`、旧 `projects` DataStore 与旧 session 目录/cwd 均做幂等迁移。
- 每条 session 绑定创建时的 cwd（home 或某个 workspace）；session 文件仍由 pi SDK 按 `AgentPaths.sessionsForCwd()` 存放在 `agent/sessions/<encoded-cwd>/`，列表由 `SessionCatalog` 按已知 cwd 聚合后叠加 SessionMetaStore（置顶/归档/标题覆盖）。
- 抽屉按工作区分组展示全部未归档会话（`ChatViewModel.refreshSessions()` 读取 `runtime.allSessions()`，用 session.cwd 匹配工作区）；工作区置顶是 UI 偏好，存 `AppPreferences`（`pinned_workspace_ids`），会话置顶仍由 SessionMetaStore 持久化。
- 新建会话入口只在抽屉：工作区行展开后的 + 与工作区长按菜单走 `ChatViewModel.newSession(workspaceId)`（Home 同样），顶栏不提供新建；顶栏 + 已移除。
- 附件导入/预览/文件引用解析都以当前会话 cwd 为 root（`AttachmentStore.import/file/shareIntent/resolve(raw, root)`），project 会话的附件落在 project 目录。

## 多会话并发

- `AgentRuntimeHost` 持有 `Map<sessionId, Handle>`：每个 Handle 有自己的 PiSessionEngine(cwd)、AgentSession、shell executor、run 状态；会话之间互不关闭。
- 运行事件统一包装为 `SessionRuntimeEvent(sessionId, event)`；活动状态是类型化的 `RuntimeActivity`（思考/重试/压缩/运行工具），由 ChatViewModel 结合 `ChatUiStrings`（字符串资源）本地化后进入 UI，runtime 层不承载用户可见文案；ChatViewModel 用 `conversationCache` 保存每个会话的消息/流式状态，活跃会话直接更新 UI，后台会话事件写入缓存，切回时立即恢复并继续接收。
- 发送/停止按 sessionId 定向；`busySessionIds` StateFlow 供 UI 展示后台运行状态；新建会话/切换项目不打断正在运行的任务。模型与思考档位是全局配置，变更会同步到所有已打开会话。

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
- 发送前先写 session 标题并发出 `SessionListChanged`，抽屉立即出现新会话。
- ProviderCatalog 从 pi2kotlin `builtinProviders` 读取 42 家 Provider、模型数和 OAuth 标记。
- ModelConfigStore 保存 provider/model/thinking；聊天 composer 切换模型和思考。
- SdkCredentialStore 使用 Android Keystore 加密的 ApiKeyStore，可按 provider 预加载/清除 key。
- SessionMetaStore/SessionRepository 在 SDK JSONL 之上维护标题覆盖、置顶、归档和删除。
- 会话文件读取/写入在 IO dispatcher；Chat 层注入 `ioDispatcher` 以便测试。
- ChatViewModel 为每个已加载过的会话保留消息快照缓存；再次打开时先渲染缓存，再在后台 `open` SDK session（单 mutex 串行，epoch 丢弃过期打开），发送会等待打开完成。

## 个性化与提示词

- 基础系统提示词由 SDK 的 `SYSTEM.md` 覆盖机制替换：`PiSessionEngine` 在创建会话前确保 `files/agent/SYSTEM.md` 存在（已存在则保留用户编辑），SDK 读到后不再使用自带的 "You are an expert coding assistant operating inside pi" 前言。工作区里放 `.pi/SYSTEM.md` 可覆盖全局这一份。
- 自定义前言会让 SDK 跳过它自己的 `<tools>`/`<rules>` section。`<tools>` 不需要重建：模型真正拿到的工具定义来自 system 消息的 `toolsAdded` 字段，provider 层据此组装请求的 `tools`。runtime 只重建 `<rules>`（各工具 `promptGuidelines` + 简洁/路径规则）作为静态 section。
- `PersonalizationStore`（DataStore `personalization`）保存称呼、交互模式、提示词预设、用户指令；`PiSessionEngine` 通过 `CodingHooks.extraSystemPromptSections` 每轮注入两个动态 section：
  - `environment`：当前时间、模型、工作区、称呼、交互风格与对应规则；手动包 `<environment>…</environment>`。
  - `instruction`：用户长期偏好；仅非空时注入，手动包 `<instruction>…</instruction>`，内容做 XML 转义。
- hook extra 不会自动包 section 标签，包装在 runtime 内完成。
- 提示词随每轮 `syncSystemPromptIfNeeded()` diff 生效（只追加变化的 section）；修改个性化不需要重启会话。Skills 的 `<skills>`、AGENTS 的 `<project_context>` section 由 SDK 注入。

## Skills 与工作区规范

- 技能仓库 `files/skills/<id>` 不被 SDK 扫描；全局开启复制到 `files/agent/skills/<id>`，项目开启复制到 `<cwd>/.pi/skills/<id>`。
- 项目技能页展示全部仓库技能；全局技能固定开启、不可关闭；全局开启会移除对应项目副本，避免关闭全局后项目副本继续生效。
- 聊天右上角「重新加载」调用 `AgentRuntime.reloadSession`：关闭并重新打开当前 session 文件，重新读取 skills/AGENTS.md；运行中的会话拒绝重载。
- 使用复制而非软链接：PRoot 只绑定当前 cwd 到 `/workspace`，仓库在 cwd 外，软链接内的脚本在 `bash` 中无法解析。
- AGENTS.md 由 SDK 原生读取：先 `agentDir`，再 cwd 祖先；每个目录按 `AGENTS.override.md`、`AGENTS.md`、`AGENTS.MD`、`CLAUDE.md`、`CLAUDE.MD` 取第一个。App 编辑工作区根目录的有效文件，保存后新会话生效。
- 技能/AGENTS.md 都在 `createAgentSession` 时加载；已有会话保持旧快照，不自动重建。

## App 工具

- `PiSessionEngine` 在 `defaultCodingTools` 后追加 `appTools(cwd, AppToolContext, ScheduleBinding)`；工具由 `CodingTool` 定义，不修改 SDK。
- `QuestionCenter` 维护待答队列并暴露 `StateFlow`，`ask_user_question` 工具等待 UI 回答；App 不在前台时直接返回错误。停止会话通过 `RunSignal` 取消等待。
- `ScheduleBinding` 在会话创建后绑定 `AgentSession`，schedule 工具据此把提醒绑定到调用它的会话。
- `ScheduleStore` 保存 `files/schedules/schedules.json`（schemaVersion 2）；`ScheduleService` 负责计时、批量与投递，工具与任务页都经它读写。
- 到点提醒投递回原对话：`ScheduleService` 打开绑定会话并调用 `AgentRuntime.deliverReminder`，由宿主注入 `[定时提醒]` 消息并开始一次运行；不新建会话、不另开工作区。
- 工具分类与契约见 `docs/TOOLS.md`，调度语义见 `docs/SCHEDULING.md`。

## 运行时事件

- `SessionRuntimeEvent(sessionId, event)` 是宿主唯一的事件出口；会话级事件带 sessionId，宿主级事件用空 sessionId。
- 事件类型：`RunStarted`、`ActivityChanged`、`TextDelta`、`ReasoningDelta`、`ToolStarted`、`ToolFinished`、`RunFinished`、`RunFailed`、`SessionListChanged`、`TurnInjected`、`SessionRemoved`、`SessionArchived`。
- `RunFailed` 由宿主在运行结束后检查最后一条 assistant 消息的 stopReason 产生（`Error`），带厂商错误原文；UI 统一本地化后展示在失败轮上。
- `TurnInjected` 是宿主主动注入的用户消息（定时提醒），UI 追加用户气泡；空闲时同时补一个流式 assistant 占位。
- UI 只订阅事件与状态流，不轮询；`ScheduleService.schedules` 是任务页的数据源。

## 通知

- `NotificationCenter` 由 `AgentService` 启动，收集 `RuntimeEvent`，维护 active runs。
- 前台服务通知：会话标题 + 当前 `RuntimeActivity`；多会话显示数量；动作停止最近会话。
- 完成通知：`RunFinished/RunFailed` 且 `AppVisibility.foreground == false` 时发出，点击带 sessionId 回到对应会话。
- 开发包安装：`DevPackageManager` 调用 `notifyInstall`，展示阶段/`n`/`m` 进度；完成后自动取消或发结果。
- 频道：`agent_runtime`（LOW）、`session_results`（DEFAULT）、`downloads`（LOW）。
- 通知权限沿用系统 `POST_NOTIFICATIONS`；设置页显示状态并可跳转系统通知设置。

## 开发包

- `DevPackageCatalog` 定义 3 个场景组合（环境基础 / 日常办公 / 开发编程）；`DevPackageManager` 在 proot 内执行 `apk update` + `apk add`，办公组合随后用 pip 安装 `python-docx`/`python-pptx`/`pdfplumber`。
- `LinuxEnvironment.executeStreaming` / `PRootExecutor.executeStreaming` 提供逐行输出回调；按 `(n/m) Installing` 更新进度。
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
- 设置层级使用 typed stack + `AnimatedContent`；系统返回手势 -> pop。
- `design/DdMotion` 统一时长/easing；`DdTypingDots` 承载思考动画。
- 抽屉用持久 composition + `graphicsLayer` translation，避免开关时反复组合；遮罩只在可见时挂载，避免拦截触摸。
- 抽屉工作区树由 `sessions + workspaces + pinnedWorkspaceIds + expandedWorkspaceIds` 驱动，展开状态由 `DeepDeepApp` 的 `rememberSaveable` 集合持有；工作区/会话行用 `LazyColumn` + `animateItem()` 做展开/收起和重排。
- 设计系统组件：`DdSwitch`、`DdSegmentedControl`（无 ripple）、`DdDialog`（Dialog + IME 自适应）、`DdSearchField`、`DdTextField`、`DdSpinner`；设置/Provider/会话/归档页统一使用这些自写控件，不再直接使用 Material AlertDialog/Switch/CircularProgressIndicator。
- 运行环境页是设置分组内的标准行（DdSwitch + DdListRow + DdDialog），不再嵌套 wash 卡片。
- Provider 详情保存 key 后由 DeepDeepApp 通过 `ApiKeyStore.read(providerId)` 读出并传给 `ProviderDetailScreen(savedApiKey=…)`，UI 只显示掩码 placeholder。
- ChatScrollController 按实际 overflow 跟随；用户上拖后暂停。
- 按压态统一为圆角灰 wash（`DdPressable` / `DdListRow` / `DdMenu`），不使用方框 ripple。
- `DdMenu` 统一浮层圆角、纯白背景、图标左对齐、danger 项与按压态。
- Markdown 解析在 `MarkdownParser`（嵌套列表树、图片块、递归引用、表格、setext、行内 AST、引用式链接/脚注上下文）；渲染在 `ChatMarkdown`（行内 span、表格横滚、引用条、数学块、脚注列表）；`CodeHighlighter` 负责语法高亮，`MarkdownImage` 负责图片异步加载/缓存/失败占位与全屏预览。
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
  skills/<id>/      技能仓库（SDK 不扫描）
  schedules/        schedules.json（提醒绑定到对话）
  linux/rootfs/     Alpine rootfs
```

API Key、模型配置、会话元数据、个性化在 DataStore；字体选择在 SharedPreferences `font_settings`；会话正文由 pi2kotlin 写入 JSONL。

## 设计中的系统

- 定时任务：`docs/SCHEDULING.md`（提醒投递、任务页编辑、手动执行已实现）。
- Skills：`docs/SKILLS.md`（仓库、zip 安装、全局/项目启停已实现）。
- 工具分类：`docs/TOOLS.md`。

## 工具

- pi 原生：`read`、`write`、`edit`、`ls`、`grep`、`find`
- `bash`：`LinuxShellExecutor` → `LinuxEnvironment` → PRoot/Alpine
- App 层：`ask_user_question`、`schedule_create/list/update/delete`
- 不注册 `device_info`、`open_app` 等 Android 能力工具

## 功能接线

新增或改造一个功能时，按这个顺序接完，缺一项就算没打通：

1. 持久化：`runtime/<Feature>Store`，原子写、损坏时保留原文件、可注入 `Clock`、每次写入后发出 `changes` 信号。
2. 宿主服务：`runtime/<Feature>Service`，`start()/stop()` 生命周期、`StateFlow` 状态、监听 `runtime.events` 处理会话生命周期。
3. 模型工具：`runtime/<Feature>Tools`，`CodingTool` 薄适配，按调用会话绑定，结果是 JSON 文本。
4. 运行时事件：由宿主发出；UI 只订阅事件与状态流，不新增定时轮询。
5. UI：`app/ui/<feature>` 用 `collectAsStateWithLifecycle` 读状态、动作回调回服务，界面不直接持有 store。
6. 测试：store/service/reducer/纯函数单测 + `docs/DEVICE_TESTING.md` 验收条目 + `artifacts/validation/` 证据。
7. 文档：`docs/TOOLS.md`、`docs/UI.md`、本文件、`docs/STATUS.md` 同步；用户可见文案中英双语。

## root 扩展

`:shell` 预留 `ShellExecutor` 实现位。以后增加 `RootShellExecutor`，同一个 `bash` 工具改用它即可；不新增工具名。
