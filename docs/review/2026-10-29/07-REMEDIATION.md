# 修复对照（round37，2026-10-29）

基准：审阅报告 [`README.md`](README.md) / [`03-QUALITY-AND-BUGS.md`](03-QUALITY-AND-BUGS.md) / [`04-PERFORMANCE.md`](04-PERFORMANCE.md)。
代码基准：`master@bdbaa07`，本轮修复提交 `8574e49`。SDK 基准：pi2kotlin 0.2.2 → 0.2.3。

## P1

| 项 | 状态 | 实现位置 | 回归测试 | 真机 |
|---|---|---|---|---|
| B01 工坊 Agent 越过临时工作区 | 修复 | `runtime/.../ScopedFileSystem.kt`、`WorkshopAgentService.kt`（白名单 8 工具、无 shell）、`PiSessionEngine.kt`（`toolFs` 只给 Agent 文件工具） | `ScopedFileSystemTest` 8、`PiSessionEngineScopeTest` 2、`WorkshopAgentServiceTest` 31 | 页面 `capabilities()` 只列出 8 个工具；`run({tools:["bash"]})`、`["gui_tap"]` 均 `invalid_params`；`read ../outside.txt`、`read /etc/passwd` 在引擎测试中被拒 |
| B02 JSONL 坏尾吞记录 | 修复 | pi2kotlin 0.2.3 `SessionManager.kt`：打开时校验尾部，坏尾备份 `<file>.corrupt` 后截断，追加前保证 LF | SDK `SessionTailRepairTest` 6；DeepDeep 集成 | 对真实会话追加 47 字节半条 JSON → 重开 App 后文件恢复原大小 234027、生成 `.corrupt`、会话正常打开，继续发消息后文件增长到 247076 |
| B03 停止未取消自动重试 | 修复 | `ChatViewModel.kt`：`stop()` 先 `clearAutoRetry`，延时任务带 token 复核 | `ChatViewModelTest`（倒计时中停止 → `retryLastTurn` 0 次） | 未做（需制造断网失败，单测覆盖） |
| B04 升级失败破坏旧插件 | 修复 | `PluginManager.kt`：失败只 `recover()`；仅首次安装才 uninstall/清技能 | `PluginManagerTest`（升级失败保留 1.0、installed、技能；首次失败清理） | 未做（避免动用户已安装插件，单测覆盖） |
| B05 全天事件日期偏移 | 修复 | `ArrangeCalendar.kt`（按 zone 取 LocalDate 再编码 UTC 日期边界）、`ArrangeRequests.kt`（全天按 UTC 格式化） | `ArrangeCalendarTest`/`ArrangeRequestsTest`/`ArrangeToolsTest`（Shanghai/NY/UTC/Santiago、单日/跨日/多日/merge） | `ArrangeDeviceTest.allDayEventKeepsRequestedLocalDate` 在真机 CalendarProvider 创建并读回：请求日期 == 读回日期，start 为 86400000 整数倍 |
| R01 多入口覆盖会话运行状态 | 修复 | 新增 `SessionCommandPlane.kt`（每会话锁 + epoch）；`AgentRuntimeHost.kt` 全部入口接入；`stop` 递增 epoch | `SessionCommandPlaneTest` 13 | 发消息→模型答完的完整链路正常（`run=2b0f17ee…`），进程无异常；停止路径由 plane 单测覆盖 |

## P2

| 项 | 状态 | 实现位置 | 回归测试 | 真机 |
|---|---|---|---|---|
| B06 凭据写入乱序 | 修复 | `SdkCredentialStore.kt`：每 Provider 修订号 + 写入任务链，仅最新意图落盘；`awaitCommit/awaitAll` | `SdkCredentialStoreTest`（磁盘=new、旧刷新被丢弃、awaitCommit） | 未做 |
| B07 时钟重算跳过未投递提醒 | 修复 | `ScheduleStore.kt`：启用且未投递的错过 Once 保留并把 nextRunAt 置回原定时刻；`zoneProvider` 每次读当前时区 | `ScheduleStoreTest` 27、`ScheduleServiceTest` 14 | 未做 |
| B08 无换行输出无界缓冲 | 修复 | `shell/PRootExecutor.kt`：`LineChunkBuffer` 8KiB 上限、UTF-8 边界切分 | `PRootExecutorTest` 13（含 8MiB 无换行） | 未做 |
| B09 每条命令全盘扫描 | 修复 | `LinuxRootfsInstaller.kt`（`quickState()` O(1) + 缓存 size）、`LinuxEnvironment.kt`、`LinuxInstallState.kt`（`sizeBytes: Long?`） | `LinuxRootfsInstallerTest` 7（快查不遍历） | 运行环境页显示 `Space used 691 MB`（进入页面 refresh 一次后统计），命令路径走快查 |
| R02 重配关闭刚启动会话 | 修复 | `AgentRuntimeHost.kt`：逐会话 `claimReconfigure`，忙则 `pendingReconfigure` 在本轮结束应用 | `SessionCommandPlaneTest`（reconfigure 阻塞/延迟应用） | 未做 |
| R03 目标续跑未复核 revision | 修复 | `GoalContracts.kt`/`GoalService.kt`：`nextRound` 只提案，`commitRound` 复核 goalId/revision/ACTIVE/上限；宿主捕获 epoch | `GoalServiceTest` 14 | 未做 |
| R04 附件大小检查可绕过 | 修复 | `AttachmentStore.kt`：`copyWithLimit` 流式限额 25MiB；`ChatViewModel.kt` 导入完成复核会话 | `AttachmentStoreTest`、`ChatViewModelTest` | 未做（需文件选择器/超限文件） |
| R05 read 整文件读入 | 修复（保留输出文案） | pi2kotlin 0.2.3 `ReadTool.kt`：文本单遍流式、目标窗口外只计数；图片先嗅探且 ≤8MB 才整读 | SDK `ReadToolStreamingTest` 5（含 23MB 文件）；原 `ReadToolTest` 14 未改断言 | 未做 |
| R06 最近活动排序退化 | 修复 | 新增 `SessionIndex.kt`（`files/session-index.json`，size+mtime 缓存）；`SessionCatalog.kt` 改走索引，`updatedAt=max(header,mtime)` | `SessionIndexTest` 10 | 真机：老会话发一条消息后抽屉立即显示 `Just now` 并排到 Home 第一；索引文件已生成（4321 字节） |
| R07 资源无淘汰策略 | 修复 | `ChatViewModel` 会话缓存限 8（LRU，当前会话保留）；`JobRegistry` 只留最近 50 条已结束记录；round39 补 Host 空闲 handle 预算：超过 6 个且空闲 >10 分钟才回收，回收前检查 busy/重配/最近打开 + App 的保留条件（未投递提醒/活跃目标/运行中 job/待答问题），被回收会话按需懒重连（提醒投递不丢） | `JobRegistryTest` 13、`HandleBudgetTest` 7、`RunLedgerTest` 5 | 真机：提醒在 round39 构建上准时投递；handle 预算（10 分钟 TTL）未在真机压测中触发 |
| R08 持久性与成功语义 | round38 已补最小闭环 | 提醒投递 outbox（`delivery-intents.json` + `recoverPending()` 补投一次）、job 记录持久化（`jobs.json`，重启后 RUNNING→`interrupted`，结果未知但可读）、待答问题持久化（`pending.json`，重启后 `interrupted` 并在会话内提示 + 汇总通知） | `ScheduleServiceTest` 18、`JobRegistryTest` 13、`QuestionCenterTest` 12、`ChatViewModelTest`（中断提示/自动重试标签）、`NotificationCenterTest` | 真机见 `08-DEVICE-QA.md`（round38）；持久任务表/TaskRepository 仍未做，见文末 |

## 说明与偏差

- **R05**：审阅要求“不整文件读入”。现有输出文案里有精确的 `of N` / `N more lines`，要精确必须扫完全部字节。本轮选择保留文案契约、单遍流式（内存只留当前行 + 目标窗口），即解决内存问题但不减少 I/O；如需“读几行就停”，要改工具输出契约，留作单独决定。
- **R01 实现**：每会话锁用 JVM monitor（`AgentRuntime.stop()` 是非挂起函数），所有状态迁移都在同步临界区且不挂起，语义等价于 Mutex。
- **B01 设计**：`PiSessionEngine` 内部落盘（session/settings/skills）仍用系统文件系统，因为 SDK 的 `ResourceLoader` 会向上遍历祖先目录找 AGENTS.md；只有 Agent 可调用的文件工具走 `ScopedFileSystem`，允许根为本次 run 的临时 workspace。
- **工坊工具面**：白名单为 `read/ls/grep/find/write/edit` + `web_search/web_fetch`（后者仅插件注册时可见），与内置 workshop 技能的页面 Agent 示例一致；`bash`、`gui_*`、`arrange_*`、`mi_cloud_*`、`job_*`、`goal`、`present` 不再暴露。
- **SDK 0.2.3**：pi2kotlin 仓库改动未提交 git，已 `publishToMavenLocal`（`~/.m2/repository/dev/pi/*/0.2.3/`）；DeepDeep `gradle/libs.versions.toml` 的 `pi` 已升到 `0.2.3`。
- **真机未覆盖**：B03/B04/B06/B07/B08/R02/R03/R04/R05 只做了单元/集成测试；B04 需要动已安装插件、B03 需要制造断网失败，本轮按低风险不制造这些现场。

## 验证汇总

- SDK：`pi-coding-agent:jvmTest` 537 tests / 0 failures（新增 11）。
- DeepDeep：865 unit tests 全绿（app 508 / runtime 331 / shell 26，0 failures）；lint 0 errors / 161 warnings / 5 hints（与审阅基线一致）；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyModuleBoundaries` 通过。
- 真机（Redmi 22101316UCP / Android 13 / MIUI 14）：安装启动、会话恢复与排序、运行环境页、创意工坊页面渲染、页面 Agent 白名单拒绝、全天日历 Provider 写入/读回、JSONL 坏尾修复与修复后追加均通过；`adb logcat` 无 `FATAL EXCEPTION`。

## round38 追加（R08 最小闭环 + 真机缺陷）

- R08-1 提醒投递 outbox：`files/schedules/delivery-intents.json`，投递前写 intent（`scheduleId@nextRunAt` 去重键），成功标记后删除；启动 `recoverPending()` 只补投未完成 intent，已投递/不存在/已归档的删除，不重复投递。
- R08-2 job 持久化：`files/jobs/jobs.json`（≤50 条、输出尾部 ≤16KB）；重启后上次运行中的 job 变 `interrupted`，`job_output` 可读中断说明，不再静默消失。
- R08-3 待答问题持久化：`files/questions/pending.json`；进程在等待提问时被杀，重启后转 `interrupted`，打开会话插本地灰字提示、发下一条消息时清除，并汇总一条通知。
- D1（真机发现）：自动重试倒计时标签被宿主终态 `ActivityChanged(failedRunId, null)` 覆盖，状态行一直显示 Thinking。修复：倒计时期间忽略该 runId 的终态活动事件，`beginRetry`/清理时解除。
- D2（真机发现，P1）：插件安装失败回滚在“无备份”时删除已安装 CLI（飞书/企微同构问题）。修复：安装前记录是否已有二进制，回滚时有备份恢复、之前已有则保留、首次安装才清理半成品；回滚命令用真实 bash 临时目录测试。
- 真机复测与性能前后对比见 [`08-DEVICE-QA.md`](08-DEVICE-QA.md)。

## round39 追加（R07 完成 + 运行中断入口 + 流式解析增量）

- R07 空闲 handle 预算：`HandleBudget` 纯策略 + Host 懒重连 + App `keepHandleAlive` 四类引用检查；回收与重开同一会话有并发门闩。测试 `HandleBudgetTest` 7、`RunLedgerTest` 5。
- R08 运行中断可见：`RunLedger`（`files/tasks/runs.json`）记录运行开始/结束，进程被杀后残留转 `interruptedRuns`，会话内灰字提示 + 启动汇总通知；发下一条消息清除。
- 性能：Job 输出快照发布 100ms 合并（≤10 次/秒，状态变化立即）；Markdown 流式规划改有状态增量（单元层 63–91ms → 2ms，540 批 12970 字符；等价性 48 篇 + 逐字符前缀）。
- 真机发现 D3（中断提示发消息后不消失）→ 修复 `dismissInterruptedNotice` 同步清理本地消息与缓存集合，补 4 个测试后复测通过。详见 [`09-DEVICE-QA-ROUND39.md`](09-DEVICE-QA-ROUND39.md)。
