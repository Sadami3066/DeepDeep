# Status

## Done: 抽屉折叠、内置中文字体、五页搜索、插件内外分类与四个能力插件化

**对话与界面**
- 抽屉每个工作区默认只显示最近 5 条会话（置顶优先），其余折叠为「更多历史 · N」，展开后末尾「收起」；展开状态按工作区记忆，收起工作区时清除。
- 中文字体改为随包内置的思源黑体子集（Noto Sans SC，GB2312 常用字，约 3.9MB），经 `Typeface.CustomFallbackBuilder` 与 Lora/Manrope 组成复合字体族；中文在任何字体设置下都是无衬线黑体，英文仍是 Lora（默认）/ Manrope；许可面板新增 OFL 行。
- 插件 / MCP / 技能 / 创意工坊 / 定时任务五个页面顶部新增搜索栏（即时过滤，无匹配提示）。

**插件**
- 插件分「外部 / 内部」两类（通栏两段选项卡 + 搜索）：外部 = 飞书、企业微信、小米云服务（彩色品牌图标，来自新素材包）；内部 = 网络搜索、界面操作、安排、目标、后台任务、定时任务、创意工坊，随 App 内置、无下载/更新/删除。
- 插件契约扩展：`kind`、`tools(binding)`、`settings()`/`onSettingsChanged`；新增 `PluginSettingsStore`（`files/plugins/settings.json`）与详情页设置 UI（开关 / 下拉 / 数字步进）。目标可设 `max_rounds`（默认 0 不限）、后台任务 `max_running`（默认 10）、创意工坊 `skill_enabled`（默认开）与 `max_turns`（默认 16）；开关/设置变化会重建已打开会话的工具与技能。
- 插件技能支持多文件（SKILL.md + references/examples + `.deepdeep-plugin` marker）；`workshop` 技能从 BuiltinSkills 迁到创意工坊插件，旧无 marker 目录按 `legacyOwned` 整体迁移。
- 工坊数据工具四个合并为 `workshop_edit`（`action=get/set/delete/keys`，≤256KB），技能正文与文档同步。
- 插件页状态缓存 120s，显式刷新才强制探测；进入页面不再每次跑 PRoot/网络探测。

**小米云**
- 掉登录自愈：快照失效时先用 WebView Cookie jar 静默校验回写（无需点击）；jar 也没有时后台离屏 WebView 加载 i.mi.com 等 `serviceToken`（≤12s），成功回写并刷新插件状态；5 分钟节流。用户主动「退出登录」写入持久阻断标记，重启也不会被 SSO 静默登回；登录成功解除。
- `MiCloudWebWriter` 内部 WebView 空闲 120s 释放、按需重建；登录 Activity `onDestroy` 补 WebView 销毁。

**性能**
- 覆盖层（设置等整页）打开后聊天页吃冻结的 `ChatUiState` 快照，过场结束（320ms）跳过 base 绘制；后台流式时被覆盖的 10s 帧数由 1178 降到 10。
- 抽屉动画进度改为在 `graphicsLayer`/`drawBehind` 的 draw 阶段读取，根节点不再逐帧重组；root 派生值（命令列表、服务商、计数、configured copy）按依赖 remember，`paletteOpen` 用 `derivedStateOf`。
- 流式代码块暂不高亮、表格暂不测量列宽，settle 后补齐；冷启动的 StorageMigration 加版本标记并移出主线程，`AppGraph.prepare()`/`ChatViewModel.prepareRuntime()` 的磁盘工作改到 IO；全进程共享一个 OkHttp transport；插件状态缓存 120s、MCP 探测缓存 60s（配置变化失效）；页面进出 5 轮稳态 PSS 283→251MB。
- 未达验收的部分如实记录：抽屉开合 jank 未下降（成本在首帧组合/绘制，不在根重组）；长会话首屏仍受全量 JSONL 解析 + 历史映射限制（分页已按用户要求移除）。复合字体改 Async 加载以消除首次布局的主线程构建（见验证）。
- 新增 release 构建（复用 debug keystore 签名），可覆盖安装用于日常使用和性能对比。

**其他**
- 厂商图标素材包 `assets/vendor-logos/`（10 个品牌，含来源/许可/可复现脚本），入库 `app/src/main/res/drawable-nodpi/vendor_*.png` 与插件 id 映射。
- 新增 `docs/TEXTS.md`：164 条模型可见/功能描述文本的位置清单（系统提示、工具说明、插件文案、技能、MCP、SDK 侧、运行期注入），纯 UI 文案单列附录。
- 新增 `docs/DEVICE_TESTING.md` round41 验收项（158–170）；更新 README、UI/PLUGINS/ARCHITECTURE/TOOLS/SKILLS/SCHEDULING/WORKSHOP/MCP 文档。

**验证**
- 995 unit tests 全绿（app 599 / runtime 364 / shell 32，0 failures）；lint 0 errors / 160 warnings（基线 158，新增为未使用串/复数建议）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:assembleRelease`、`verifyModuleBoundaries` 通过。
- 真机（Redmi 22101316UCP / Android 13）：抽屉折叠、中文字体、五页搜索、插件内外分类与品牌图标、内部插件设置生效、停用/启用工具与技能、`workshop_edit` 四种 action、小米云掉登录自愈与退出登录阻断、插件/MCP 缓存、覆盖层冻结、release 覆盖安装与数据保留；证据 `artifacts/validation/round41/`。

## Done: 运行中插话、原始历史、图片多模态与界面收敛

**对话**
- 运行中可继续输入：点发送=插话（当前工具执行后、下一次模型调用前送达），长按发送=排队（本轮结束后送达）；队列条支持清空并把文本回填输入框，停止也会回填未送达文本；切走/杀进程后历史顺序一致。
- UI 历史改读会话原始分支：压缩前的消息不再消失，压缩/分支摘要以可折叠卡片展示；压缩卡片在切会话与 force-stop 重开后保持。
- 图片附件改为多模态直传：长边 ≤2048、EXIF 方向校正、PNG/JPEG 阶梯压缩；模型无需 read 文件即可描述。单附件上限 1 GiB（1.0 GB），图片准入 64MP 与可解码校验，空间不足/格式不支持有明确提示。
- 上下文压缩：触发估算统一为投影 + usage 口径，并在工具批结束后、下一次模型调用前检查；SDK 发布 0.2.4（Maven Local 与远端仓库）。

**界面与资源**
- 语言菜单改为锚定下拉，选中先收起再重建 Activity；中英切换后菜单不再残留。
- 45 个 UI 图标全部替换为 Tabler（MIT 许可同步）。
- 英文字体收敛为 Lora（默认）与 Manrope（OFL）；移除 Instrument Sans/Geist/IBM Plex/Newsreader 及其许可与资源。

**Bug 修复**
- proot 内 bash 时间比系统慢 8 小时：启动环境注入 POSIX `TZ`，`date +%z` 为 +0800（`date` 的时区缩写显示 GMT，时间与偏移正确）。
- 删除 `docs/review/` 与相关引用。

**验证**
- 949 unit tests 全绿（app 552 / runtime 365 / shell 32，0 failures；round39 为 916）；lint 0 errors / 158 warnings（低于此前 161 基线）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyModuleBoundaries` 通过。
- 真机（Redmi 22101316UCP / Android 13）11 项：插话、排队与清空回填、停止回填、压缩卡片与 kill 后历史保留、JPEG 多模态描述、1.15 GB 文件被 1.0 GB 上限拒绝且无残留、语言切换菜单消失、字体仅两档、Tabler 图标、bash `+0800`、回归无 `FATAL EXCEPTION`。证据 `artifacts/validation/round40/`。
- SDK 0.2.4：`pi-coding-agent` 540 tests 全绿。

## Done: 运行恢复入口、空闲 handle 预算与流式解析增量

**运行中断可见**
- 新增 `RunLedger`（`files/tasks/runs.json`）：每次运行开始写一条，正常结束删除；进程在运行中被系统杀掉，下次启动把残留记录转成“运行中断”，打开对应会话时插一条本地灰字「上次运行被中断，结果未知」，发下一条消息时消失；正常完成的一轮不会误报。
- 启动汇总通知与待答问题合并为一条「有未完成项」，点击打开最早一条受影响会话。

**空闲 handle 预算**
- Host 会话 handle 超过 6 个且空闲超过 10 分钟才开始回收；回收前检查忙/重配/最近打开，并询问 App 的保留条件（未投递提醒、活跃目标、运行中 job、待答问题）。
- 被回收的会话在下一次 send/retry/提醒投递时按会话目录懒重连（重新打开同一 JSONL）；提醒不会因为会话空闲而丢失。回收与新开同一会话之间有并发门闩，避免同时写同一个会话文件。

**性能**
- Job 快照发布：输出触发的刷新按 100ms 合并（最多 10 次/秒，末次最新），开始/结束/停止等状态变化立即发布。
- Markdown 流式规划改为有状态增量：文本追加时复用前缀行、定义与稳定块，只重扫尾部；单元层在 12970 字符 / 541 批下，规划耗时从 round38 的 63–91ms 降到 2ms（约 30–45x），整段与增量的输出等价性由 48 篇语料 + 逐字符前缀断言。
- 真机固定提示词（先开键盘 → reset → 发送 → 27s 窗口）gfxinfo：A 94/965（9.74%）、B 105/1037（10.13%），round38 同类测量为 183/1651（11.1%）。窗口长度、回答长度与刷新模式不同，**不能据此宣称帧率提升**；Slow UI 绝对帧数下降（90/102 vs 154）与规划耗时下降方向一致，但仍需固定输入的基准才能下结论。

**真机验收（round39）**
- B06 凭据连写：快速连写 invalid→real 后落盘为最后一次意图，重开 App 稳定（通过，有限观察：看不到磁盘中间态）。
- R03 续跑窗口：命中一次（轮次结束后 ~50ms 点暂停），无第 3 个 run，重开后旧轮不复活。
- R04 超大附件：30MB 分享提示「文件超过 25MB」，附件目录无残留。
- 运行中断提示 smoke 发现 D3（发消息后灰字不消失），已修复并复测通过：提示出现 → 发消息后 4s 内消失 → 切走再回不重现 → 通知出现一次，无 FATAL。
- 提醒投递在 round39 构建上准时（一次性提醒 19:02:00.006 投递、无错误）。
- 回归：启动、会话恢复与切换、抽屉排序、`.corrupt` 无新增、无 `FATAL EXCEPTION`。

**验证**
- 916 unit tests 全绿（app 538 / runtime 352 / shell 26，0 failures；round38 为 890）；lint 0 errors / 161 warnings / 5 hints（与基线一致）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyModuleBoundaries` 通过。
- 设备证据 `artifacts/validation/round39/`。

## Done: 可靠性收尾（R08 最小闭环）与真机缺陷修复

**R08 持久性与恢复**
- 提醒投递 outbox：`files/schedules/delivery-intents.json`。投递前写 intent（`scheduleId@nextRunAt` 去重键），成功标记已投递后再删除；进程在投递中死亡，下次启动 `recoverPending()` 补投一次，已投递的不重投，连续启动幂等，文件损坏重建。
- Job 持久化：`files/jobs/jobs.json`（最多 50 条、输出尾部 16KB、原子写）。重启后上次仍在运行的 job 显示为 `interrupted`（结果未知）并保留输出，不再静默消失；损坏隔离重建。
- 待答问题持久化：`files/questions/pending.json`。正常问答/取消即删除；进程在等待提问时被杀，重启后转成“中断问题”，在打开该会话时插一条本地提示、发下一条消息时清除，并在启动时发一条汇总通知（点击打开最早会话）。

**真机缺陷修复**
- 自动重试状态行：倒计时标签被宿主的终态“活动清空”事件覆盖，状态行一直显示“思考中”。现在倒计时期间忽略失败 runId 的终态活动事件，停止/开始重试/成功/耗尽时清理。
- 插件安装回滚：飞书/企微在“无备份”时失败会删除已安装 CLI。现在安装前记录是否已有二进制，失败时有备份就恢复、之前已有则保留、只清理本次半成品；回滚命令用真实 shell 在临时目录验证。

**聊天流 Markdown**
- 流式文本按稳定块缓存解析：`planMarkdownStream` 做定义提取与结构扫描，`parseMarkdownChunk` 只解析单块，`ChatMarkdown` 按块文本缓存；文本增长时只重解析末尾未闭合块。等价性用 48 篇输入 + 富文档逐字符前缀与整段解析对比。
- 单元层解析 67ms → 41ms（约 1.6x，540 批 12970 字符）。真机流式 gfxinfo 未观察到可归因的改善（两次结果 11.1%/14.1%，基线 8.5%，但提示词、采集窗口与帧率不同，不具可比性），因此不记为性能提升；下一步需固定输入的 Macrobenchmark/Perfetto trace 再判断卡顿来源。

**验证**
- 890 unit tests 全绿（app 524 / runtime 340 / shell 26，0 failures；round37 为 865）；lint 0 errors / 161 warnings / 5 hints（与基线一致）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyModuleBoundaries` 通过。
- 真机（Redmi 22101316UCP / Android 13 / MIUI 14）：D1 断网自动重试时状态行显示 `Retrying n/5`；D2 断网 Reinstall 失败后 `lark-cli` md5/大小不变、插件仍已连接、记录与技能完好；强制杀进程后 `job_list` 显示 `interrupted`、待答问题重启后提示+通知；R02 运行中插件变更不中断本轮；抽屉排序、`.corrupt` 无新增、无 `FATAL EXCEPTION`。证据 `artifacts/validation/round38/`。

## Done: 全局整合（按 2026-10-29 代码审阅报告收敛运行秩序）

**背景与结论**
- 10-29 审阅报告指出：模块边界清楚，但会话运行、停止、重试、重配、持久化、能力范围由多处各自决定，出现“多个功能各自正确、组合起来不可靠”。本轮按报告修复 P1/P2 缺陷并把运行状态收回到单一所有者，不重写架构、不换执行语言。

**会话运行秩序**
- 新增 `SessionCommandPlane`：每会话一把锁 + 单调 epoch，send/retry/deliverReminder/deliverBackgroundTurn/stop 的状态迁移全部在锁内完成；busy 只由持有该 runId 的路径清除，停止后旧的延时重试/目标续跑作废。后台入口在忙时注入当前轮（`QueueBehavior.FollowUp`），空闲才新起 run。
- 插件能力变化与 MCP 配置更新改走 `reconfigure()` 的安全点重配：忙碌会话排到本轮结束，空闲会话立即重建；不再用“等待全部空闲”当锁，也不会关闭正在运行的会话。
- 目标续跑改为“提案 + 0.4s 后复核 epoch/revision + `commitRound` 才启动”，轮次在真正开始时递增；暂停/清除/编辑/停止都会让旧提案失效。
- 会话列表改由 `SessionIndex`（`files/session-index.json`，按 size+mtime 缓存）支撑，updatedAt 取 header 时间与文件 mtime 的较大值；继续老会话会排到最近，列表不再每次解析全部 JSONL。

**数据可靠性与能力边界**
- 停止会先取消该会话尚未到点的自动重试倒计时，倒计时结束前再复核任务身份，避免“点了停止又自己跑起来”。
- 凭据按 Provider 串行落盘（修订号 + 写入任务链，只有最新意图落盘），新增 `awaitCommit`；旧刷新 token 不会覆盖之后的登出/换 Key。
- 一次提醒若已错过且未投递，重算时保留并补投，不再静默禁用；时区计算每次读取当前系统时区。
- JSONL 尾部损坏不再吞掉下一条记录（SDK 0.2.3）：打开时把坏尾备份到 `<file>.jsonl.corrupt` 并截断到最后一个合法记录，追加前保证文件以 LF 结尾。
- 插件升级失败不再卸载旧版本、不删记录与技能，只清理暂存；首次安装失败才清理半成品。
- 工坊页面 Agent 改为显式白名单（`read`/`write`/`edit`/`ls`/`grep`/`find` + `web_search`/`web_fetch`），文件工具绑定只能落在本次运行临时目录的 `ScopedFileSystem`，无 bash；`../`、绝对路径、外指 symlink 被拒。
- 全天日历事件按用户时区日期编码为 UTC 日期边界，修掉上海等正时区写前一天的问题，负时区读回也不再退一天。
- 附件导入边拷边限额 25MB（元数据 size 未知也受限），导入完成时复核会话，切换会话后不再把附件塞进新会话。
- 性能与资源：Linux 命令前安装检查改为 O(1) 标记检查，磁盘占用只在设置页统计一次；PRoot 行输出按 8KiB 上限切分，无换行长输出不再无界累积；read 工具文本路径改为单遍流式，不再整文件读入；job 只保留最近 50 条已结束记录，聊天会话缓存限 8 个。

**SDK 0.2.3**
- pi2kotlin 发布 0.2.3（Maven Local）：JSONL 尾部修复 + read 流式读取；DeepDeep 升级依赖。SDK 侧 jvmTest 537 tests 全绿（含 11 个新增尾部/流式用例）。

**验证**
- 865 unit tests 全绿（app 508 / runtime 331 / shell 26，0 failures），比上一轮 804 增加 61 个回归用例；lint 0 errors / 161 warnings / 5 hints，与审阅基线持平；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyModuleBoundaries` 通过。
- 真机（Redmi 22101316UCP / Android 13 / MIUI 14）：安装启动、会话恢复与排序（老会话发消息后抽屉立即 `Just now` 并排最前，`files/session-index.json` 生成）、运行环境页（`Space used 691 MB` 来自进入页面时一次统计）、创意工坊页面渲染、页面 Agent 白名单（`capabilities()` 只列 8 个工具，`tools:["bash"]`/`["gui_tap"]` 被拒）、全天日历 Provider 提权创建并读回日期一致、JSONL 坏尾修复（47 字节坏尾被备份到 `.corrupt`，会话恢复原大小后可继续追加）均通过；`adb logcat` 无 `FATAL EXCEPTION`；`ArrangeDeviceTest` 2 tests OK。

**未在本轮处理**
- 持久任务/outbox/未知结果模型（R08）：需要独立的任务表与恢复语义，留待下一轮单独立项。
- Host 空闲 handle 淘汰：提醒/目标/待答问题仍可能引用会话，需先做引用检查。
- Markdown 增量解析与真机帧率基线：需先有 profile trace。

## Done: 后台保活（GUI 长任务防系统杀进程）

**问题与结论**
- 真机日志确认：App 的前台服务在后台保持 `adj=200`，本次观察期间进程未被系统结束；但 MIUI 的 `MiuiMemoryService` 会在内存清理时结束后台进程（系统 `am_kill` 可见，官方原因还包括 OneKeyClean、SwipeUpClean、LockScreenClean、AutoPowerKill 等），`specialUse` 前台服务不能完全避免。
- 进程一旦结束，无障碍服务进入系统 crashed 状态，系统不会自动重绑；用户必须在无障碍设置里关掉再打开，或重启手机。

**修复**
- 「GUI 操作」插件页新增后台保活状态：无障碍连接、电池优化豁免、后台限制、最近任务锁定提示；返回页面时自动刷新。
- 新增动作「允许后台运行」（未豁免时显示，跳系统/小米省电策略页）、「自启动设置」（小米自启动管理页）、「省电策略」（系统限制时显示）。
- 进程重启后检测无障碍服务：已开启但未连接时发通知，点击直达无障碍设置；工具报错也会说明需要关闭再打开或重启。
- 记录上次进程退出原因到 `files/diagnostics/process-exits.log`（`ApplicationExitInfo`），用于确认系统 kill 的具体原因。
- 文档补充后台运行要求与小米设置路径。

**验证**
- 真机：插件页状态/动作、MIUI 省电策略与自启动跳转、电池优化豁免开关往返、后台前台服务保活、进程退出原因落盘均验证；`artifacts/validation/round36/`。
- 804 unit tests green（app 497 / runtime 286 / shell 21，0 failures）；lint 0 errors / 152 warnings / 5 hints；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、模块边界检查通过。

## Done: 安排精简为日历、云服务合并开关、设置/聊天框整理

**安排**
- 删除闹钟/计时器相关功能与全部系统时钟入口（系统公开 API 无法 CRUD 已保存闹钟）；插件只保留日历。
- `arrange_calendar`：`calendars`/`list`/`get`/`create`/`update`/`delete`，直接读写 `CalendarContract`；需要 `READ_CALENDAR`/`WRITE_CALENDAR`。
- 插件页只保留状态与「授予日历权限」（未授权时）；不再显示打开闹钟/日历入口和自定义说明行。

**小米云服务**
- 功能开关精简为 7 项：笔记、待办、短信与通话、联系人、相册、录音、账号；笔记/待办的读写合并为同一个开关，其余只读。默认全部关闭。

**设置与聊天框**
- 设置页移除「语音助手」「GUI 控制」入口；GUI 无障碍设置从「GUI 操作」插件进入，语音从 composer 麦克风或系统助手进入。
- Composer 语音按钮移到发送按钮左侧。

**性能**
- 聊天过程/工具/推理卡片的展开箭头改为 `graphicsLayer` 内读动画状态，减少每帧重组；spinner 同理。
- `present`/`workshop_register`/`ask_user_question` 的解析结果按 tool trace 缓存，避免每次重组重新解析。
- 附件缩略图增加 8MB LruCache（按路径+大小+修改时间），主题 colorScheme 按深浅色 remember。
- 以上改动保持布局/动画/交互不变；真机 8 次按键 `Compose:recompose` 段 22–24 → 8（每键约 3 次 → 1 次）；全量单测、构建与设备 smoke 通过。

**验证**
- 804 unit tests green（app 497 / runtime 286 / shell 21，0 failures）；androidTest 1/1（日历 Provider CRUD）。
- lint 0 errors / 152 warnings / 5 hints；`:app:assembleDebug`、模块边界检查通过。

## Done: 安排完整 CRUD、小米云登录页修复

**安排插件**
- `arrange_alarm`：Agent 可创建、列出、修改、删除 App 管理的闹钟与计时器；`AlarmManager.setAlarmClock` 精确调度，`specialUse` 前台服务响铃（系统 alarm ringtone + 振动 + 停止通知），重启/时间/时区变化自动重排；`list.systemNext` 只读展示系统时钟的下次闹钟。
- `arrange_calendar`：通过 `CalendarContract` Provider 直接创建/读取/修改/删除系统日历事件与提醒，支持 Calendars/Instances/Reminders；重复事件按实例展开读取，list 返回的 id 可直接用于 update/delete。
- 权限：`READ_CALENDAR`/`WRITE_CALENDAR`、`USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM` 等；插件页有「授予日历权限」「允许精确闹钟」入口。系统时钟应用里已有的闹钟受签名级 `miui.permission.ACCESS_ALARM_PROVIDER` 保护，公开 API 只能打开，不能读写。
- 真机证据：androidTest 4 项（日历 CRUD、闹钟 CRUD、精确调度、响铃通知）通过；App 内闹钟响铃通知与前台服务在 MIUI 上验证。

**小米云服务**
- 修复 WebView 登录页空白：等非零布局后加载 + 探测 `100vh==0` 时注入最小高度修正；i.mi.com 与 account.xiaomi.com 真机截图完整可见、可输入。
- 登录后的 cookie 保存/profile 校验仍需真实账号完成。

**验证**
- 837 unit tests green（app 530 / runtime 286 / shell 21，0 failures）。
- `:app:assembleDebug`、`verifyModuleBoundaries` 通过；lint 0 errors / 156 warnings / 5 hints。
- 证据：`artifacts/validation/round34/`。

## Done: 小米云服务/安排/企微插件、GUI 增强与聊天框整理

**插件**
- 新增「小米云服务」插件（`micloud`）：WebView 登录 i.mi.com，加密保存 cookie；账号、笔记、待办、短信与通话、联系人、相册、录音分别有读开关，笔记/待办另有写开关，默认全关；读取走 HTTP，写入走隐藏 WebView 同源 fetch（POST 带 `serviceToken`）；工具 `mi_cloud_*`，录音下载到 `/micloud/recordings`。未覆盖云备份、日历/云盘等无接口能力，见 `docs/MICLOUD.md`。
- 新增「安排」插件（`arrange`）：`arrange_alarm` 与 `arrange_calendar` 委托系统时钟/日历，零危险权限；不提供本地待办。
- 新增「企业微信」插件（`wecom`）：安装官方 `wecom-cli` 1.3.4（Linux arm64），扫码授权；setup sheet 同时显示二维码与浏览器链接；技能 `deepdeep-wecom`。
- GUI 插件升级：新增 `gui_screenshot`（API 30+，保存 `/gui/screenshots/`）、`gui_find`、`gui_wait forTextGone`、`gui_set_text` 写入回读校验；GUI 版本 2.0.0。
- 删除设备短信能力：移除 `read_sms`、`READ_SMS`、设置开关、`CapabilityPreferences`；设置页 Android 能力组只剩语音助手/GUI 控制。待办改由小米云服务承担。
- PRoot 新增固定 bind `/gui`、`/micloud`；页面 Agent 过滤 `gui_*`/`arrange_*`/`mi_cloud_*`。

**聊天框**
- thinking 档位从 composer chip 移入模型选择 sheet；语音按钮改 38dp 圆形；运行中停止按钮改为红色实心圆。

**调研**
- `docs/ANDROID_ASSISTANT.md`：系统助手/悬浮 UI 研究，结论是公开 API 只能到默认助手角色，长按电源 0.5s 为 MIUI 私有触发；悬浮层需 `SYSTEM_ALERT_WINDOW` + FGS。
- `docs/ROOT.md`：root 获取与工具改造研究；本机实际是 APatch（非 Magisk），`ro.boot.flash.locked` 为伪装值；`su` 可获 uid 0；只做调研不实现。

**验证**
- 804 unit tests green（app 497 / runtime 286 / shell 21，0 failures）。
- lint 0 errors / 153 warnings / 5 hints；模块边界与 debug APK 构建通过。
- 真机（Redmi / Android 13）：composer 模型 sheet 内 thinking、圆形语音、红色停止圆；插件页新插件；小米云详情 9 项开关与登录入口；安排打开系统闹钟/日历；企业微信 CLI 1.3.4 安装、状态、二维码 setup；GUI 无障碍连接、`gui_screenshot` 落盘并由 `present` 内联显示；设置页无短信项。
- **未完成/已知问题**：小米云 WebView 登录页在真机渲染空白（DOM 已加载、CSS 高度为 0），登录尚未验证；浏览器/系统账号接口均不可行（详见 `docs/MICLOUD.md`）。企业微信扫码授权未完成；小米云各云数据读写、GUI 截图以外的 GUI 新能力未做完整真机验证。
- 证据：`artifacts/validation/round33/`。

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
