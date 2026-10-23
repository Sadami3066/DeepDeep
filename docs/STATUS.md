# Status

## Done: 提醒触发器扩展（间隔 / 每月 / cron）

- 提醒新增触发类型：间隔（分钟/小时按每天 00:00 对齐；天类型从起始日期起算）、每月指定日（短月取最后一天）、cron 5 段表达式（`*`、列表、范围、步长、月份/星期英文名；日与星期同时限定时取「或」；最多向后找 5 年）
- runtime 在写入前校验触发参数：越界、缺 `time`/`start_date`、cron 无效或 5 年内无匹配都会被拒绝；工具 `schedule_create/update` 新增 `interval`、`interval_unit`、`start_date`、`day_of_month`、`cron` 参数
- 提醒编辑器：重复改为底部选择器（8 种类型 + 说明）；按类型显示字段；底部实时「下次：<时间>」；cron 语法提示 + 3 个预设；列表副标题展示新规则
- 304 unit tests green（runtime 125 / app 159 / shell 20），lint、模块边界、debug 构建通过


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
