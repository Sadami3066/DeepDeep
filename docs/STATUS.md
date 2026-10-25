# Status

## Done: 运行状态对账、每会话模型、交付/工坊升级与 Android 能力

**会话与模型**

- 修复“后台唤醒/锁屏断网重试结束后 UI 仍显示运行中、需要手动停止”：runtime 新增 `activeRuns` StateFlow 作为运行态真相；事件缓冲溢出时结构事件用挂起 emit 兜底；ChatViewModel 在 busy→idle、回前台、无活动 run 的 stop、打开会话快照落地后二次对账（history + sessionModel 重建并清 streaming/sending）；reducer 在终态 runId 不匹配时终结最后一条 streaming 消息
- 模型/思考改为会话级：打开会话从 JSONL 的 `model_change`/`thinking_level_change` 恢复，切换只影响当前会话并同时成为新会话默认值；设置页厂商详情删除模型列表与“设为当前”，只保留 API Key/OAuth 配置
- 思考计时改为本地化 h/m/s（`1h 2m 3s` / `1 小时 2 分 3 秒`，省略高位零单位）

**交付与工坊**

- `present` v2：显式 `items` 支持 `file`/`image`/`html`/`creative`；聊天里图片内联预览并全屏、HTML 内联 WebView 渲染并全屏、文件行卡、创意入口卡；`creative` 从工坊清单查询；旧 `files` 输出兼容，坏项逐条丢弃
- 工坊入口卡不再由 `workshop_load` 自动贴出：新输出带 `delivery:"present"` 标记，模型需显式 `present` 创意；旧历史卡片保留
- 页面 Agent 改为事件流：最终 `Promise<string>` 不变，新增可选 `onEvent`（run_start/text_delta/thinking_delta/tool_start/tool_end/turn_end/run_end/run_error）；native 40ms 合批、终态前 flush；180s 超时/失败会发 `run_error` 并 reject，不再永久 pending；maxTurns 中止 `truncated`；工坊页面 Agent 工具目录过滤 `read_sms`/`gui_*`
- workshop 技能文档补齐事件流用法，删除“单文件/单页优先”“只返回最终文本”等旧建议，不新增文件数建议

**技能**

- 打开技能页/回前台/每轮 RunFinished 后自动把 `files/agent/skills`、home 与各工作区 `.pi/skills` 中合法且仓库缺失的技能复制进仓库；插件标记跳过、幂等；技能页监听 `changes` 自动刷新
- PRoot 固定挂载 `/skills`→`files/agent/skills`、`/skill-repo`→`files/skills`，Agent 可直接 bash 读写；系统提示的 `environment` 写明路径与生效时机

**Android 能力**

- GUI 操作官方插件（id `gui`）：App 内 `AccessibilityService` + `gui_observe/tap/set_text/scroll/global/wait` + `deepdeep-gui` 技能；服务需用户手动开启，插件状态与工具错误提示明确
- 语音助手：composer 麦克风 + `ACTION_ASSIST` 入口；系统 `SpeechRecognizer` 转写（权限/错误完整映射）、系统 TTS 朗读回答、可申请 `ROLE_ASSISTANT`；设置页语音开关与状态；自动朗读只在语音面板内生效
- 接收分享：`ACTION_SEND`/`ACTION_SEND_MULTIPLE` 冷/热启动插入当前（冷启动最近）会话附件或预填文本；先复制到 cache 防授权过期；生成中排队；自身分享 chooser 排除自己
- 读取短信：设置「读取短信」开关 + `READ_SMS` 运行时权限 + 只读 `read_sms` 工具（limit/box/address/since，正文截断 2000 字）；只注册给聊天会话，工坊页面不可见；隐私页补充说明
- 新增 `docs/ANDROID_CAPABILITIES.md`：已实现能力的权限/边界 + 闹钟/日历/待办/提醒接口调研与落地建议

**验证**

- 687 unit tests green（runtime 286 / app 380 / shell 21），lint 0 errors，模块边界与 debug 构建通过
- 真机（Redmi / Android 13）验证：技能自动入库、Android 能力设置组、短信权限与 `read_sms` 工具、分享冷/热启动、GUI 插件连接与 observe/global、ASSIST 语音面板、present 图片/HTML/文件/创意、思考计时 1m 级、长任务锁屏返回无残留运行态
- 已知平台限制：MIUI 对非默认短信应用只暴露部分短信记录；无障碍服务需手动开启；部分 OEM 的助手角色/识别服务可能不可用
- 证据：`artifacts/validation/round32/`

## Done: 会话 system prompt 去重与内联技能

- 修掉每轮对话都往 JSONL 追加 system 消息的 bug：根因是 `environment` section 带微秒级当前时间，每次 diff 都变化；现改为日期 + 时区（同一天内稳定），并把环境/偏好/插件/共享目录等动态 section 作为 `initialSystemPromptSections` 写进会话首条 system 快照，不再每轮补一次
- pi2kotlin SDK 新增 `AgentSessionOptions.initialSystemPromptSections`（0.2.2）：只写入首条 system 快照，后续仍由 hooks 动态 diff；DeepDeep 升级到 0.2.2 并本地发布
- 真机连续 7 轮对话的会话 JSONL 只有 3 条 system 消息：首条完整快照 + MCP 连接后的 `mcp_servers` + 延迟工具声明；不再随用户轮次增长
- `/skill:<name>` 支持写在消息中间或末尾（如「使用一下这个 skill /skill:workshop」），一条消息可提及多个技能，代码块/行内代码中的 `/skill:` 不展开；技能块仍从用户气泡中隐藏
- 555 unit tests green（runtime 266 / app 269 / shell 20），lint 0 errors、模块边界与 debug 构建通过；pi2kotlin 1434 JVM tests green、本地发布 48/48 校验通过

## Done: 页面驱动一次性 Agent

- bridge 新增 `agent.models` / `agent.capabilities` / `agent.run`：页面 JS 可以列出已配置模型与完整工具/技能目录，并用 `app.agent.run({prompt, providerId, modelId, thinkingLevel, systemPrompt, maxTurns, tools, skills})` 启动一次性 Agent，只拿到最终文本；并行/串行编排完全由页面 JS 决定
- `WorkshopAgentService` 在 App 进程内为每次运行创建一次性 `PiSessionEngine`：临时 workspace/agentDir、页面选择的 tools 过滤、选中的 skills 复制、页面 systemPrompt 追加到系统提示、关闭 MCP、`maxTurns` 限制、180s 超时、结束后关闭 session 并清理临时目录；不写入聊天会话/JSONL
- Agent 生命周期绑定 WebView：`agent.run` 的协程属于 viewer/WebView scope，页面关闭/返回/销毁会取消正在运行的 Agent；没有后台任务、没有 run 持久化
- `PiSessionEngine.createSession` 新增 toolsFilter / mcpEnabled / extraSystemPromptSections，默认行为不变；模型来自已配置 provider，页面选择由 App 校验
- workshop 技能升级为完整教程包：`SKILL.md` + `references/api-reference.md` + `references/tutorial.md` + `references/design-checklist.md` + 4 个可运行样例（hello / agent workbench / agent pipeline / daily report），随内置技能目录整体安装
- 544 unit tests green（runtime 262 / app 262 / shell 20），lint 0 errors、模块边界与 debug 构建通过；真机验证 models/capabilities、单 Agent 返回 PONG、并行 ALPHA/BETA、systemPrompt 生效、非法 tool 返回 invalid_params、长任务返回页面后取消且无残留 run 目录

## Done: 创意工坊数据层 Agent 工具

- 新增 `workshop_data_get` / `workshop_data_set` / `workshop_data_delete` / `workshop_data_keys`：Agent 直接读写每个创意独立的 JSON KV 数据层，不需要改页面文件或 `workshop_load`；app 经 `workshop_list` 的 id 校验，key 统一 `[a-z0-9][a-z0-9_-]{0,63}`，单值 ≤256KB，SQLite 在每次调用内打开/关闭
- 数据结构完全由 Agent 定义（任意 JSON，可存 `schema`、`settings`、按日期分的日报 key）；页面继续从 `window.app.data` 读同一数据；数据与代码仍物理分离，重写页面不丢数据
- 工具不暴露原始 SQLite/SQL；Agent 只通过作用域化的数据工具更新内容，页面代码保持稳定
- 新增 11 个 runtime 单测；真机让 Agent 仅用数据工具把「跳动的心」的 taps 从 2 改成 7，页面重新打开显示 7，未改任何页面文件

## Done: 斜杠命令统一

- 命令面板点选只把完整命令补全到输入框，不再直接执行；composer 对行首 `/command` token 使用主题强调色，光标落在补全内容末尾，补参数后发送才执行
- 面板顺序固定为 App 命令（压缩/目标）在上、已启用技能 `/skill:<name>` 在下；workshop 也作为普通已启用技能出现，不再有 `/workshop-skill` 专用别名；技能列表来自当前会话 cwd 的 SDK 技能发现，打开面板时刷新，已停用技能不显示；点选技能补全 `/skill:<name> `
- `/goal <目标>` 直接创建目标，删除目标 sheet（目标条改为纯状态展示，保留暂停/继续）；`/goal pause|resume|clear` 直接操作，空参数提示目标不能为空
- `/compact` 忽略后面的任意文字，直接执行原生压缩，不把文字当压缩指导，也不再报「不接受参数」
- 加载技能后的用户气泡不再显示「技能 · name」标记；只剩需求文字（无需求时显示 `/skill:<name>` 纯文本），原始 `<skill>` 块始终隐藏
- 505 unit tests green、lint 0 errors；真机验收面板补全/强调色、技能列表顺序、goal 直接创建与 pause/clear、compact 忽略文字、气泡无技能标记

## Done: 创意工坊 v2（技能驱动的普通会话 + 无分类轻应用）

- 创意不再有类型/分类：`manifest.json` 只要求 `id`/`name`/`entry`（可选 `icon`/`version`/`description`），任何自包含 HTML/CSS/JS 页面都可以；旧 manifest 的 `type`/`permissions` 解析时忽略，旧创意照常打开
- 创建回到普通对话：内置 workshop 技能（`app/src/main/assets/skills/workshop/SKILL.md`）在启动时写入技能仓库与全局生效目录；`/skill:<name>` 在发送前把技能文件渲染成 `<skill>` 块读入本轮，聊天里只显示需求文字、不显示技能标记或原始 XML；工坊页 `+` 直接新建 Home 会话并预填 `/skill:workshop `
- 工具收敛为 `workshop_create`（只建 manifest 草稿）、`workshop_load`（校验入口/清单/大小后原子安装）、`workshop_list`（列出现有创意路径）；不再生成 AGENTS.md/starter，也没有新建对话框与类型选择
- WebView 仍是每创意独立虚拟域名 + `WebViewAssetLoader` + 文档启动 SDK + 主题变量；bridge 基础能力为 `info`、`data.get/set/remove/keys`、`toast`，页面 Agent 见上方新条目；详情页只读，修改页面统一回到聊天
- 列表行 = icon（缺省 ✦）+ 名称 + 说明；失效目录显示原因并可删除；代码与数据继续物理分离，删除时清理 DB 与 journal
- 修复 Compose 托管 WebView 时 CSS 视口高度为 0（`100vh`/`height:100%` 失效、页面只渲染顶部一条）的问题：WebView 先放进 FrameLayout 容器再交给 `AndroidView`
- 505 unit tests green（runtime 223 / app 262 / shell 20），lint 0 errors、模块边界与 debug 构建通过；真机验收技能强制加载、`+` 预填、普通 Agent 创建爱心应用、入口卡片打开、bridge 数据交互与重启持久化、无页面 Agent、旧 manifest 兼容、技能页可见
- 证据：`artifacts/validation/round30/v2_*.png`；格式、技能与页面接口见 `docs/WORKSHOP.md`

## Done: MCP 热更新改到 IO 并串行化

- 修复会话内关闭再开启 MCP 后 `tool_search` 搜不到工具：`reloadMcp()` / `reconnectMcp()` 在 UI 主线程发起连接，SDK 读取响应体触发 `NetworkOnMainThreadException`，服务器状态停在 failed、deferred 目录为空；现统一在 `Dispatchers.IO` 执行，失败写日志不再静默
- MCP 热更新串行化：启停、编辑、导入、删除、登录后的重连共用一个 mutex，快速连点或并发操作不会让连接与状态错位
- `reconfigure()`（运行环境启停、插件贡献变化）同样会重建会话并连接 MCP，现改为在 IO 执行，避免同一异常
- MCP 页的启停、保存、删除、导入改为等待热更新完成再收起/刷新，应用期间开关不可再点
- 398 unit tests green（runtime 150 / app 228 / shell 20），lint、debug 构建通过；真机验证会话内关闭再开启 MCP 后状态 Connected/35、`tool_search` 返回工具并在下一轮可调用

## Done: 插件启停、网络搜索、MCP JSON 导入与 UI 收敛

- 插件框架增加启用状态（schemaVersion 2）、`OfficialPlugin.tools()` 与动态贡献收集；关闭插件后不再注入 prompt/skill/tools，重新开启恢复；安装/启用变化在没有忙碌会话时重建会话
- 插件技能改为仓库 + 生效双目录：技能页能看到飞书技能，关闭插件时只移除生效副本
- 飞书：部分 scope 授权不再误判为失败，详情显示缺失权限；应用权限入口 + `drive +export` 表格兜底；授权完成后 sheet 自动关闭并尝试回到前台
- 新增网络搜索插件：`web_search` / `web_fetch`，复用 DeepSeek API Key，走 DeepSeek Search/Fetch API
- MCP 添加改为粘贴 `{"mcpServers":{...}}` JSON（占位提示、不预填），支持 `streamablehttp` 写法、批量添加、同名不覆盖；点击已有服务器直接编辑 `"name": {...}` JSON；刷新图标移到标题右侧；技能页删除底部说明
- 修复 MCP 连接探测在主线程读取网络响应导致的 `NetworkOnMainThreadException`；探测与 OAuth 的文件/网络操作改到 IO dispatcher
- 插件列表改为图标 + 启用开关，删除只保留在详情页；聊天页常驻底层，从技能/MCP/插件/定时任务返回不再重建消息列表；移除 assistant 底部复制/引用/分享图标行
- 398 unit tests green（runtime 150 / app 228 / shell 20），lint、debug 构建通过；真机验证网络搜索、插件启停、技能可见、MCP JSON 导入与编辑、返回流畅度

## Done: 官方插件页与飞书 CLI 接入

- 抽象插件框架：`OfficialPlugin` 契约 + `PluginManager`（`app/.../plugin/`）。框架不含 Linux/CLI/登录概念；安装记录、互斥、技能同步、系统提示、进度通知、`plugin_setup` 工具由框架负责
- 抽屉新增「插件」；列表/详情/删除/更新/重新安装；每插件自己的状态与操作（飞书：连接、重新授权、退出登录、重新检测）
- 首个插件飞书：按需下载官方 CLI（npmmirror + GitHub 兜底、SHA-256 校验、guest `/usr/local/bin/lark-cli`），设备流建应用/登录，状态由飞书插件自己探测（`lark-cli auth status --json`）
- Agent 与 UI 同步：`plugin_setup` 工具走同一个 `PluginManager.setup` 与同一个 setup sheet；Agent 直接用 bash 改 CLI 状态后，插件页进入/恢复/运行结束会重新探测；每轮 `official_plugins` 系统提示按当前状态生成
- 插件技能写 `files/agent/skills/deepdeep-feishu`（`.deepdeep-plugin` 标记）；不预设定时任务、总结模板或界面
- 353 unit tests green（runtime 142 / app 191 / shell 20），lint、模块边界、debug 构建通过

## Done: Provider OAuth、App MCP 与导航收敛

- Provider OAuth：内置 9 家 OAuth 厂商（Anthropic、OpenAI、GitHub Copilot、OpenAI Codex、xAI、OpenRouter、Kimi、Meta、Radius）可在设置页登录/退出；OAuth token 与 API Key 同样用 Android Keystore 加密保存，同一厂商只保留一种认证；登录后厂商立即视为已配置并可选用
- MCP：抽屉新增「MCP」，管理 Streamable HTTP 服务器（新增/编辑/删除/启停、请求头、说明、连接测试、OAuth 登录）；配置存 `files/agent/mcp.json`，保存后对已打开会话热更新；工具/资源经 SDK 进入对话，`mcp__server__tool` 命名，deferred 工具通过 `tool_search` 按需加载（Android 无 stdio）
- 导航：抽屉去掉「聊天」「已归档」；「已归档」移入设置页并显示数量；占位「插件」入口由 MCP 取代
- OpenAI ChatGPT OAuth 使用持久化的安装 UUID（`install-id`）与 `agentName=DeepDeep`，满足 SDK 的 `ext_agent_host_id` 要求
- 浏览器授权完成后自动跳回 App（自定义 loopback 回调页 → `deepdeep://oauth/callback`），手动粘贴仅作兜底；登录 sheet 会说明自动返回
- 修复 OAuth 登录/退出需要点两次：去掉登录回调里的异步磁盘 preload 竞争，UI 直接读凭据缓存；pendingWrites 防止 preload 覆盖未落盘写入
- SDK 升级到 pi2kotlin 0.2.1；MCP sampling 默认关闭，不触发隐式模型调用
- 323 unit tests green（runtime 135 / app 168 / shell 20），lint、模块边界、debug 构建通过


## Done: 提醒重复规则收敛为 cron

- 提醒重复方式简化：「一次」+「cron」；运行时只新增这两种触发器，旧的每天/每周任务继续按原规则投递，在编辑器打开时转换成等价 cron
- cron 为 5 段本地时间表达式：支持 `*`、列表、范围、步长、月份/星期英文名；日与星期同时限定时取「或」；最多向后找 5 年，无匹配视为无效
- 编辑器用一个「重复」开关切换一次 / cron；cron 输入框下方只保留实时渲染的小字时间设定（每天 09:00 / 每周一、周三 09:00 / 每 15 分钟 / 每月 1 日 09:00 等），不显示语法说明；列表副标题同样显示人话规则
- 模型工具 `schedule_create/update` 同步收敛为 `once` / `cron` 两种
- 295 unit tests green（runtime 121 / app 154 / shell 20），lint、模块边界、debug 构建通过

## Done: 交付卡片、路径解析、行内代码、proot 共享目录、文件浏览

- 交付卡片美化：文件类型图标 + 文件名，分享改为右上角无底色图标；多文件横向滚动
- 路径解析：相对子路径（如 `src/…`）按会话 cwd 解析，`/workspace/…` 与 `workspace/…` 统一映射；FileProvider 增加 `workspaces/`，workspace 会话里的交付文件可正常打开和分享
- 行内代码：背景按文字基线计算高度（上 0.84em / 下 0.32em），完整包住 j/y/g 等下行字母
- PRoot 共享目录：设置 → 运行环境新增「共享目录」，授予「所有文件访问权限」后可开启，`/storage/emulated/0` 与 `/sdcard` 绑定进 Linux 环境，并在系统提示里告知模型；运行期切换下一次 bash 生效
- 修复分享面板打开/关闭时聊天整体上下位移：系统分享面板会临时隐藏状态栏，窗口失焦期间冻结状态栏/导航栏 inset，不再跟随瞬时变化（消息操作栏分享与交付卡片分享都生效）
- 右上角「...」菜单新增「目录文件」：打开当前工作区文件浏览器 sheet（工作区名/相对路径、目录在前、文件带大小、逐级返回、点文件打开）
- 281 unit tests green（runtime 109 / app 152 / shell 20），lint、模块边界、debug 构建通过


## Done: chat polish — 提问卡、展开动画、后台任务标记、交付卡片

- 提问：同一时间只显示一张互动卡；同一轮里并发或先后多次 `ask_user_question` 时，回答完当前问题后同一位置出现下一题。回答结束后互动卡消失，回答按工具调用顺序收进过程：ask 工具行后面出现「已回答 · N 个问题」，可平滑展开只读问答
- 修复已答卡片展开/收起的瞬闪：改为与思考/工具卡相同的「常驻标题行 + 单个 AnimatedVisibility」，不再两个并列区域互切
- 修复断网重试的跳变：重试操作占固定 44dp 槽（spinner 与文字同槽），retrying 期间失败行保留到收尾再清理，不再瞬间塌陷
- 后台任务完成提示收敛为一行极简标记：Terminal 图标 + 「后台任务 · 名称」（失败时文案对应），不显示完成/失败卡片
- 行内代码：主题浅蓝底（accentSoft）+ 3dp 微圆角，换行时按行拆分；路径样式、`Kbd`、代码块不变
- 交付卡片：`present` 工具把生成的文件展示为与附件同尺寸（140dp）的方形文件卡，横向滚动；只显示文件名，整卡点击打开，右上角分享图标
- 附件：上限 20；发送后的附件单行横向滚动，不再换行
- 279 unit tests green（runtime 109 / app 152 / shell 18），lint、模块边界、debug 构建通过

## Done: P12 commands, compaction, token usage, goals, background jobs, inline questions

- `/` 命令：`/compact`、`/goal`；输入框上方是可过滤的命令面板，未知命令不发送，命令不进入模型上下文
- Manual compaction: `/compact` runs the SDK compact and reports before→after tokens; auto compaction keeps the SDK thresholds; busy/empty/failure each have a localized message
- Token usage: a ring in the composer shows context occupancy; the session sheet shows context/window, auto-compaction threshold, input/output/cache read/cache write tokens and cost, with `~` marking estimates
- Goals: per-session goal store, `get_goal`/`create_goal`/`update_goal` tools with direct-human authority checks, automatic round continuation up to the cap, pause/resume/complete/clear, goal bar + sheet, restart leaves goals paused
- 后台任务：`job_start` 创建（`command` 必填、`label` 可选），`job_output`/`job_list`/`job_kill` 读取与停止；进程内注册表使用环形输出（运行中 256KB / 结束后 16KB）与增量游标，每会话最多 10 个运行中任务；完成后按「后台任务完成时唤醒」决定是否开新一轮；任务数显示在 composer 上方的 token 用量行左侧，点击打开后台任务 sheet
- Questions are now an inline card in the chat transcript instead of a bottom sheet: pager, options, multi-select, custom answer, skip, collapse, read-only answer summary after settling, cross-conversation prompt
- New events (`NoticeEvent`, `BackgroundTurnStarted`, `UsageChanged`) and session-scoped pending questions; no SDK changes
- `/` 只由人使用（`/compact`、`/goal`），在任意词边界触发并可过滤滚动；目标默认不限轮次，目标轮提示带 objective，进程重启后显示暂停（激活状态只存内存）；上下文用量环移到 composer 上方；上下文占用标注为 provider 用量 + 估算，累计 token 与费用来自 provider 上报；后台任务沿用 DSH 限制（256KB 环形缓冲、每会话 10 个、等待上限 600s、归档清理、运行中每秒刷新），由 App 层 `job_start` 创建，SDK 保持 pi2kotlin 0.1.3 的原生 `bash`（前台阻塞，不提供后台参数），后台任务提示只有唤醒轮的一行极简标记

## Done: P11.3 model switching, regenerate retry, auto-retry

- Model sheet: a provider row lets you switch between configured providers; the model list below follows the selected provider
- Retry now regenerates the failed reply instead of resending the user message: the SDK removes the failed assistant turn with a context edit and continues from the original user message (`retryLastTurn` / `retryLastAssistant`)
- Chat settings gained an auto-retry switch (default on): retryable failures regenerate automatically up to 5 times with a 2s interval and a "Retrying n/5" status line; timeouts, auth/balance errors and missing models are not retried
- Retry keeps the failed bubble and its error line in place and swaps the Retry action for a spinner, assistant failures no longer write a placeholder text, and errors render as a localized reason plus status code; the provider picker uses a dedicated anchored `DdDropdown` distinct from the context menu
- pi2kotlin 0.1.3 (session header for OpenCode Go + regenerate API), 233 unit tests green

## Done: P11.2 visible failures, conversation-bound reminders, editable schedules

- Run failures: the host inspects the final assistant stop reason, so 401/402/429/5xx/timeout/network/model-missing errors show a localized message with Retry instead of an empty reply; history reloads keep the failed turn; background notifications carry the error
- i18n: every new user-visible string exists in `values` and `values-en`; runtime stores schedule error codes and the UI localizes them
- Schedules rewritten to reminder-delivery: a reminder belongs to the conversation that created it; at due time the message is posted back into that conversation and runs there, with its history/AGENTS.md/skills and no new session
- `ScheduleService`: precise wake-ups at `nextRunAt`, per-conversation batching, catch-up on service start, session deleted/archived cleanup, clock/timezone recompute; `ScheduleStore` schemaVersion 2 (v1 tasks discarded); tools bind to the calling session
- Schedules page rebuilt: minimal grouped list, editor sheet (name, content, recurrence, time/date/weekday pickers, target conversation, enable), manual run, delete; chat header clock for active reminders and reminder label in bubbles
- Chat: pure `ConversationReducer`; reopening a conversation always refetches history (fixes blank conversations after background events); injected reminder messages stream live
- Provider compatibility: OpenCode Zen/Go requests now carry `x-opencode-session` (pi2kotlin 0.1.2) instead of failing with 400 `MissingSessionID`
- Tests: 227 unit tests (app 131 / runtime 78 / shell 18); lint and module boundaries green
- Evidence: `artifacts/validation/round11/`

## Done: P11.1 skill scopes, session reload, scheduled execution

- Project skills: all repository skills are listed; globally enabled skills are on and cannot be turned off, marked Global
- Chat overflow: Settings replaced by Reload; reloads skills/AGENTS.md for the current session via detach/reattach, refuses while running
- Scheduled tasks: `ScheduleRunner` in AgentService ticks every 30s, runs due tasks in target workspaces, persists status/session/error, one-time tasks disable after running
- Hidden `schedules` workspace: excludes drawer/search/restore; `+` opens an arrangement session, arrangement history can be reopened
- Schedules page: list, next run, enable switch, detail, delete, open run session; statuses and next-run timestamps refresh every 5s
- Storage migration fix: workspace paths no longer get rewritten to `/files/homes`
- Evidence: `artifacts/validation/round10/`

## Done: P11 skills, workspace settings, app tools

- Skills: 技能页从抽屉进入；zip 安装（单技能、路径/大小校验）到 `files/skills`；全局开关复制到 `files/agent/skills`；删除清理仓库与所有启用副本
- Workspace settings: 长按工作区「设置」替代「新建会话」；项目技能 bottom sheet（只列未全局技能，复制到 `.pi/skills`）；AGENTS.md bottom sheet（64KB、override 优先、空内容删除）
- App tools: `ask_user_question`（DSH schema，全 App bottom sheet，题库/多选/自定义/跳过/取消）与 `schedule_create/list/update/delete`（P11 只落库并计算 nextRunAt，执行器见 P11.1）
- Storage migration: fixed `/files/workspace` prefix matching `/files/workspaces`; repairs sessions previously rewritten to `/files/homes` back to `/files/workspaces`
- Docs: 新增 `docs/TOOLS.md`；重写 `docs/SKILLS.md`、`docs/SCHEDULING.md`；更新架构/UI/测试说明
- Evidence: `artifacts/validation/round9/`

## Done: P10.2 final polish

- Settings: removed 功能 / 连接器 / 权限 rows
- Fonts: default Newsreader; font preview sample is 「我思故我在」+「I think, therefore I am.」
- Chat settings: only 「过程自动折叠」 remains in the UI; haptics / keyboard / streaming / reasoning are always on but removed from the UI
- Process auto-collapse: collapses when the final result text starts streaming, answer continues outside the process; off keeps it expanded
- Provider icons: replaced with the user-supplied 42 color logos (light + night PNGs); Chinese label is now 厂商
- Evidence: `artifacts/validation/round8/` (settings, font samples, chat settings, collapse-at-streaming)

## Done: P10.1 follow-up polish

- Runtime page: 3 bundles only (环境基础 / 日常办公 / 开发编程), no individual tools; roomier title/subtitle spacing and one-line package summary; self-install hint
- Personalization: 8000-character instruction limit removed; "语气" renamed to "提示词"
- Attachments: max 15 (was 5)
- Chat settings: haptics / keyboard / streaming / reasoning / process expansion moved to a dedicated page
- Office bundle: poppler-utils, qpdf, openpyxl, xlsxwriter, Pillow, tesseract + pip python-docx/python-pptx/pdfplumber; verified imports and pdftotext

## Done: P10 personalization, notifications, runtime tools

- Settings: profile → personalization (nickname, interaction style, prompt presets, user instruction); billing/time-focus/sharing removed; privacy page; font picker; notification permission row
- Prompt assembly: `<system>` (identity/environment/hidden interaction rules) + `<instruction>` (user preference) injected through SDK hook; old `deepdeep_environment` removed; XML escaping
- Notifications: foreground service shows session title/activity, multi-session count, stop action; background completion notification opens the session; download progress channel
- Runtime: 3 developer-tool bundles (environment basics / office / development), background `apk update/add` with line streaming, optional pip post-install for office, notification progress, installed-state refresh
- Provider icons: 42 providers, all color (LobeHub + Radius official), light/dark variants, list/detail wiring
- Fonts: Instrument Sans / Geist / IBM Plex Sans / Newsreader / Lora bundled; Chinese falls back to system; font picker persists and rebuilds
- Design docs: `docs/SCHEDULING.md`, `docs/SKILLS.md`
- Unit tests for prompt assembly and package catalog; existing tests green

## Done: P0-P5 polish

- Claude-style chat: right user bubbles, fixed thinking row, quiet collapsed process
- process redesign: expanded while running, details collapsed, auto collapse at finish; intermediate text/tools in order; final answer separate
- history turns grouped; single process per user turn
- conversation switch: persistent drawer, short centered spinner, no horizontal page push
- drawer: light paper background, white menus, no selected accent, anchored context menu, rounded press state
- menu text centered, compact width, press follows row; session rows have rounded press state
- full-width rounded press highlights on settings rows / provider rows
- process text rendered with the same Markdown pipeline as final answers
- lighter default hairlines
- settings: compact grouped cards, no verbose copy, anchored theme menu, Toast placeholders, nested runtime page
- palette: light background + pure white contrast + teal-blue accent; lighter hairlines
- motion system: DdMotion tokens, DdTypingDots, typed navigation, AnimatedScreenHost
- press states: rounded gray wash on menus/list rows/provider rows/session rows
- old Assistant*/LinuxSettingsScreen removed
- markdown: task lists, ordered start, fenced language/copy, quote bar
- DdMenu unified topbar/session/theme menus with rounded press states; DdPressable fast press-in
- whale-blue accent + danger red; icon-left menus; API key input margin; process row padding
- settings rows/provider/theme/license migrated to DdPressable; tap gets bounded ripple, long press gets wash
- menu icons left-aligned; danger delete red; whale-blue palette
- API key field margin and process row padding
- markdown upgrade: nested lists/tasks, block images (async loader + cache), horizontally scrolling tables, syntax highlighting, recursive blockquotes, setext headings
- markdown inline elements: escapes/hard breaks, autolinks (URL/email), reference links, inline HTML subset (b/i/code/kbd/sup/sub/br/a/span color), math fallback (inline + display block), footnote refs + end list
- image tap opens a full-screen lightbox (scrim, fit-center, fade, back/tap to dismiss)
- message actions: text-label selection toolbar (copy/select all/quote/read aloud), draggable handles, outside-tap clears selection/menu, composer quote preview, quoted context in user bubbles
- attachments: system file picker (multi-select), Claude-style composer preview cards (image thumbnail / text snippet / file card + circular remove), workspace copy for agent access, attachments rendered as separate equal-size square cards outside the user bubble, in a horizontal row (cropped image thumbs, extension-badge file tiles), full-screen preview (image/text/markdown/unpreviewable) with FileProvider share
- file references in assistant messages: attachments/workspace/absolute/file:// paths and markdown links are clickable and resolve to previews
- Claude Android app reverse-engineered for selection + attachment UX (Compose TextContextMenu provider, text-label toolbar, native handles, image/preview sheets)
- conversation switch cache: finished turns cached per conversation, reopening a visited conversation renders instantly while the SDK session opens in background
- performance: Compose stability config for runtime models + ChatUiState, draw-phase press wash, memoized markdown spans, image fade read in graphics layer

- i18n: values-en full English translations (app/chat/drawer/settings + status/error strings) + in-app language switcher (system/zh/en, persisted, attachBaseContext locale, no appcompat); runtime activity is a typed RuntimeActivity localized in ChatViewModel, session default titles localized
- search: independent drawer search (inline, title filter, empty hint, back/ESC close) and archived-screen search; drawer placeholders 技能/插件/定时任务 show toast
- projects: workspace renamed to home with session/data migration; create/select projects (files/projects/<id>) from drawer; sessions bound to cwd; project picker dialog
- multi-session concurrency: per-session runtime pool, background runs keep streaming while switching/new chat; busySessionIds; per-session cache restores instantly
- DeepSeek thinking levels now include MAX (off/low/high/max from model catalog)

- UI v2: Claude/ChatGPT-style DdSwitch (52×32, thumb shadow, spring) + DdSegmentedControl (theme), 5 persisted interaction switches (haptics/send-hides-keyboard/stream-follow/show-reasoning/process-expanded), settings wiring
- dedicated session search screen (drawer search icon, title-only match, workspace grouping, relative time, empty/loading states); drawer inline search removed
- Workspace rename (Project→Workspace end-to-end: UI/API/type/files/DataStore/directories/session cwd/docs) with idempotent migration; drawer DSH-style Workspaces section, create/rename/delete (Home fixed), running-session spinner, relative time
- IME: animated bottom inset (imeAnimationTarget + tween) + per-frame list anchoring, removed delay hacks; keyboard-up drawer open stays smooth

- UI v3 drawer: workspaces laid out directly as an expandable tree (Home is the default workspace row), multiple workspaces can stay expanded, chevron-only disclosure, no section labels and no selection accent; top-bar new-chat button removed; sessions can only be created from the workspace row + (or workspace long-press menu)
- workspace pin (long-press menu, persisted in AppPreferences, pinned-first order, small pin icon); session pin only within its workspace, shown as a small pin icon instead of relative time; per-workspace + creates a session in that workspace
- drawer loads all non-archived sessions across workspaces (`allSessions`) and groups them by cwd; workspace rows get folder icon + 15sp medium name, session rows get message icon + smaller secondary text and indentation; expand/collapse via LazyColumn `animateItem()`
- legacy UI replaced with self-written controls: DdDialog (no Material AlertDialog, IME-aware), DdSearchField (session/archived/provider search), DdTextField (API key + rename), DdSpinner (all loaders); DdSegmentedControl no longer shows square press ripple
- settings runtime-environment page fixed (removed nested wash card and misaligned size line; DdSwitch/DdSpinner/DdDialog standard rows)
- Provider detail shows masked saved key placeholder (`sk-*****`) instead of "已保存，输入新 Key 可替换"

- UI v4 performance: IME bottom inset read moved to layout phase (janky frames 15.57% -> 7.64%, slow-UI frames 66 -> 35); drawer scrim alpha moved to draw phase; 16ms stream delta batching (streaming same-prompt 20s: janky 29.95% -> 23.76%, slow-UI 339 -> 238); stability config now includes AttachmentRef + File, full compiler report has no unstable ChatScreen params
- UI v4 visual: original icon library kept (attempted new Lucide asset set reverted); drawer hierarchy uses existing Folder/Message icons; mascot welcome WebP (480px, ~56KB) in new-chat empty state (light/dark)
- UI v4 markdown: CommonMark code spans (multi-backtick, space trim, newline normalization); workspace file refs render as links (no code chip); path-like inline code renders monospace/no background; other inline code uses subtle neutral codeBackground; new tests + docs

## Checks

- `verifyModuleBoundaries` pass
- `:runtime:testDebugUnitTest`, `:app:testDebugUnitTest`, `:shell:testDebugUnitTest` pass（168 tests：105 app / 45 runtime / 18 shell）
- `lintDebug` 0 errors
- `assembleDebug` / `assembleRelease` pass
- P11 device smoke: zip install, global copy on/off, WorkA project skill copy, AGENTS.md saved into session `<project_context>`, session `<skills>` contains demo-skill, ask sheet answered, schedule JSON contains daily/weekdays tasks
- Device smoke: expandable workspace tree + per-workspace session creation, workspace pin ordering persistence across restart, session pin icon, custom dialog above keyboard, runtime page layout, masked Provider key, unified search fields, segmented control without ripple
- Perf smoke (gfxinfo): drawer/IME/scroll/streaming scenarios captured before/after in `artifacts/validation/perf/`
- Markdown/visual smoke: light/dark empty state with mascot, new icons in drawer/settings, path table rendering with clickable workspace refs and no code chips

## Evidence

`artifacts/validation/polish/` plus latest:

- `process-running-v2.png`, `process-final-v2.png`
- `menu-anchored-v2.png`
- `settings-pressed-v2.png`
- `history-grouped.png`
- `topbar-menu.png`, `session-menu-new.png`, `final-settings-press.png`
- `artifacts/validation/markdown/`: markdown tour, nested quote/code, table scroll, dark theme, message menu, quote composer, copy toast, cached switch, inline elements, footnote list, image lightbox
- `artifacts/validation/attachments/`: selection toolbar, selection quote, file picker, composer chips, attachment bubble, image/text preview, share sheet, image thumbnail, markdown preview, clickable file references
- `artifacts/validation/claude-re/`: Claude Android selection toolbar/overflow/handle-drag/tap-outside screenshots
- `artifacts/validation/ui-round3/`: drawer workspace tree, workspace pin order, session pin icon, dialog above keyboard, runtime settings fixed, provider masked key, provider search, session search, archived search
- `artifacts/validation/ui-round4/`: new icon drawer/settings, mascot empty state light/dark, markdown path rendering (links vs monospace vs codeBackground)
- `artifacts/validation/perf/`: performance baseline/after notes and raw gfxinfo dumps
- `artifacts/validation/ui-round5/`: original icon library restored in drawer/settings, simple-prompt natural Markdown answer, `files/home` absolute path rendered clickable and preview opened
- `artifacts/validation/round7/`: 3 bundle runtime layout, chat settings page, prompt rename/no limit, office bundle install (`office-libs-ok`, `pdftotext` 25.12)
- `artifacts/validation/round6/`: personalization (nickname/mode/tone/preset), settings removals/privacy/fonts, 42 provider icons, runtime package install (network + Python, notifications, installed state, 72MB rootfs), prompt `<system>`/`<instruction>` evidence, foreground/completion notifications and session open

Device: Redmi 22101316UCP / Android 13 / arm64-v8a.

## Pending

- root: `ShellExecutor` extension point; implementation deferred until root testing is authorized
- scheduled tasks: manual "run now", editable form, run history list, schedule-level notification toggle
- skills: editing/creation, `.md` import, market, hot reload
- proactive cron scheduling tied to interaction style
