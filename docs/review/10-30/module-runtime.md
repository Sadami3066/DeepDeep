# :runtime 模块评审

## 概览（规模/职责/依赖/边界）

- 规模：`runtime/src/main` 58 个文件 10,772 行；测试 34 个文件 8,034 行（34 个测试类 / 364 个 `@Test`）〔复核修正〕。
- 依赖：pi2kotlin SDK（AgentSession/事件/MCP/OAuth）、okio、kotlinx.serialization、AndroidX DataStore / Android Keystore、java.time。不被其他自研模块依赖，只被 `:app` 依赖，符合 `docs/ARCHITECTURE.md` 的依赖方向。
- 职责可分成六簇：宿主与命令面（AgentRuntimeHost/SessionCommandPlane/RuntimeTypes/SessionBinding/HandleBudget）、会话与历史（SessionIndex/SessionCatalog/SessionMetaStore/HistoryMapper/RunLedger/WorkspaceStore）、调度·目标·任务·提问（Schedule*/Goal*/Job*/Question*/PendingInputs）、工坊（Workshop*/ScopedFileSystem）、引擎与提示词（PiSessionEngine/SystemPrompt/Personalization/SkillCatalog/Provider*）、凭据与 MCP（SdkCredentialStore/EncryptedSecretStore/Mcp*/OAuth/InstallationId/PresentTool）。
- 边界评价：模块外部契约干净——`AgentRuntime` 是 _app_ 与运行时的唯一入口，runtime 不承载用户可见文案（活动状态用 `RuntimeActivity`，UI 本地化）；持久化全部落在 `filesDir` 下的 JSON/JSONL；工具层都是薄适配器。但**模块内部**没有进一步分层：`AgentRuntimeHost` 一个类同时承担 handle 生命周期、预算回收、运行台账、目标续跑、MCP 热更新、事件投影、工作区 CRUD（1083 行），是本模块最大的结构性风险。

## 评分

| 维度 | 分数 | 理由 |
|---|---|---|
| 架构质量 | 7/10 | 命令面单一所有权 + epoch + activeRuns 对账的设计正确且实现一致；持久化/恢复约定统一。扣分：宿主中心化（God class）、全局 mutex、suspend API 无 IO 边界、事件有序性无保证、`AgentRuntime` 40 方法胖接口、宿主级事件用 `runId=""` 哨兵。 |
| 代码质量 | 7/10 | 小函数、显式校验、错误文案清晰；多数 Store 有 schemaVersion/原子写/损坏隔离；测试写得实（真线程并发、FakeClock、runTest）。扣分：损坏数据策略不一致（Goal/Schedule 抛异常，其余隔离）、若干空 catch 与吞 CancellationException、5 份几乎相同的“临时文件+ATOMIC_MOVE”实现、少量死代码。 |
| 性能与效率 | 6/10 | 主路径（发一条消息）设计合理，但存在明确的机制级浪费：调度服务对每个流式事件读盘、会话索引对变化文件整文件重扫、任务持久化每秒重写 ~800KB、usage 投影在全局锁内按工具调用触发、重连在 Main 线程解析完整 JSONL。 |

## 亮点

1. 命令面把“运行状态唯一所有权”做成了可验证的状态机：每会话一把锁 + 单调 epoch + `claimTerminal` 原子终结，`finish/stop/claimReconfigure` 都由持有 runId 的一方推进，杜绝了“先读 busy 再决策”的旧竞态（`SessionCommandPlane.kt:101-153`、`187-205`）。并发正确性有真线程测试兜底（`SessionCommandPlaneTest.kt:34-60` 用 8 线程 CountDownLatch 断言只起一个 run）。
2. 事件/状态双通道设计贯彻到底：`activeRuns: StateFlow` 是运行态真相、事件只做快速通道，宿主在 begin/finish/stop/detach 同步维护快照，UI 负责用 history 对账（`AgentRuntimeHost.kt:82-88`、`881-892`；`RuntimeTypes.kt:313-317`）。这是本项目最有价值的架构决策之一。
3. handle 预算回收与懒重连的组合：`HandleBudget` 纯函数化（可单测），只有“非忙碌/非重配/非 keepAlive/非当前选中且空闲超 10 分钟”才回收，`keepHandleAlive` 把提醒、目标、运行中任务、待答问题都纳入保留条件（`HandleBudget.kt:16-36`、`AgentRuntimeHost.kt:772-820`、`AppGraph.kt:340-347`）——不会出现“回收把定时提醒吞掉”。
4. 持久化恢复链路完整：RunLedger 崩溃残留 → `interruptedRuns` → 本地灰字；Schedule 用 delivery-intent 先写后投递实现至少一次投递 + 幂等去重（`ScheduleService.kt:157-191`、`218-226`），并有“恰好一次恢复”测试（`ScheduleServiceTest.kt:402-498`）；Job 重启把 running 转 INTERRUPTED 并保留输出尾巴，损坏文件做 `.corrupt` 隔离（`JobPersistence.kt:40-53`、`89-98`）。
5. 工坊沙箱边界清晰：ScopedFileSystem 只允许临时 workspace（`ScopedFileSystem.kt:16-37`），页面 Agent 工具显式白名单 + 无 bash/MCP + 180s 超时 + maxTurns 截断（`WorkshopAgentService.kt:39-40`、`127-155`、`169-179`），能力/模型选择全部校验（`WorkshopAgentService.kt:298-377`）。
6. 工具适配层纪律好：所有 `*Tool` 的 execute 都先 `signal.throwIfAborted()`，错误统一 `textResult(..., isError=true)` 返回给模型，并显式重抛 `RequestAbortedException`/`CancellationException`（`GoalTools.kt:301-310`、`JobTools.kt:178-187`、`WorkshopTools.kt:368-376` 等）——模型可恢复错误与协程取消被正确区分。

## 问题清单（P0 → P3）

**[RT-001] 损坏的 goals.json / schedules.json 会以未捕获异常击穿启动流程**（P0，正确性）· `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:34-48`、`GoalService.kt:19`、`ScheduleStore.kt:246-270`、`ScheduleService.kt:48-68`

证据：
```kotlin
// GoalStore.kt:34-43  ,  任何 JSON/字段问题都 throw
private fun load(): Map<String, Goal> {
    val text = try { file.readText() } catch (error: Exception) {
        throw CorruptGoalDataException("Unable to read goal data", error)
    }
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        ?: throw CorruptGoalDataException("Goal data is not a JSON object")
// GoalService.kt:19
private val persisted: MutableMap<String, Goal> = store.read().toMutableMap()
// ScheduleService.kt:48-53  start() 没有任何异常兜底
fun start() {
    if (job?.isActive == true) return
    job = scope.launch { recoverPending(); refresh(); deliverDue(); ... }
}
```
影响：`GoalStore` 在 `GoalService` 构造器里同步读盘，而 `AppGraph.kt:324` 的 `host` 是 eager `val`、构造参数 `goalDriver = goalService`（:333），因此 `AppGraph` 构造时（`DeepDeepApplication.onCreate` 主线程）**立即**构造 `GoalService` 并读 `goals.json`，比原文更确定、更早；〔复核修正〕`ScheduleService.start()` 由前台服务 `AgentService.onCreate` 调用（`app/.../service/AgentService.kt:39`），`refresh()/recoverPending()` 首次 `store.list()` 即抛异常。这个异常发生在 `CoroutineScope(SupervisorJob()+IO)` 的 `launch` 里且无 `CoroutineExceptionHandler`，在 Android 上会走线程默认异常处理器 → **进程崩溃**。由于文件损坏是持久的，用户会进入每次启动都崩溃的循环，且无法从 UI 修复（对比 `JobPersistence`/`RunLedger`/`QuestionPersistence` 都是隔离继续运行）。
建议：把损坏策略统一成“备份为 `<file>.corrupt` + 返回空数据 + 日志”，在 `GoalStore.load/ScheduleStore.load` 内完成（与 `JobPersistence.quarantine()` 对齐）；`ScheduleService.start()` 用 `scope.launch { runCatching {...}.onFailure { Log.e } }` 包裹初始化，并给 `AppGraph.scope` 加 `CoroutineExceptionHandler` 兜底；迁移/未来 schema 变更走显式 version 分支而不是 throw。工作量 S。

> 〔复核勿做〕不要在 `AppGraph.scope` 全局挂 `CoroutineExceptionHandler`——那会吞掉所有未捕获异常，与 RT-019「关键失败不能静默」冲突；应在 `ScheduleService.start()`/宿主启动处局部兜底。另：`ScheduleStoreTest.kt:421/437/447` 明确断言「损坏文件保留并 throw」，是刻意设计，改策略要同步改测试。

**[RT-002] 宿主 suspend API 没有 IO 边界：会话重连时在 Main 线程解析完整 JSONL**（P1，性能/正确性）· `AgentRuntimeHost.kt:270-274`、`405-419`、`725-741`；`PiSessionEngine.kt:44-113`；调用方 `app/.../ui/chat/ChatViewModel.kt:535`

证据：
```kotlin
// AgentRuntimeHost.kt:270-274（send 直接在当前 dispatcher 上跑）
override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) {
    val handle = handleFor(sessionId)          // 可能走重连
    val start = begin(handle, TurnSource.USER) ?: error("Agent session is busy")
    runTurn(handle, start.runId, text, images.toImageContents())
}
// AgentRuntimeHost.kt:405-418  handle 不存在时 catalog.listAll + attach（锁内建 engine）
// PiSessionEngine.kt:52-58  打开会话时先整文件解析一次
ensureSystemPromptFile(agentDirectory.toFile())
...
val restored = if (file == null) null else
    runCatching { SessionManager.open(file, fs).buildSessionContext() }.getOrNull()
// ChatViewModel.kt:535（viewModelScope = Main.immediate）
viewModelScope.launch { sessionOpenJobs[sessionId]?.join(); runtime.send(sessionId, prompt, images) }
```
影响：handle 被预算回收（空闲 10 分钟，常见）后用户再发消息，`send()` 在 **Main 线程**执行 `ensureSystemPromptFile`（文件 IO）、`SessionManager.open().buildSessionContext()`（对整份 JSONL 做全量解析，几十 MB 的会话可达数十～数百 ms，且 `createAgentSession` 还会再打开同一文件一次——后者基于 `sessionFile` 参数语义，**推测**、未读 SDK 源码）、`createAgentSession`（读 SYSTEM.md/AGENTS.md/skills/MCP 配置）。`retryLastTurn`、`steer/followUp`、`setSessionModel`（写 model_change）、`compact` 同样从 Main 直接进入 runtime，没有 `withContext(IO)`。这是目前最明确的 ANR/掉帧路径。
建议：给 `AgentRuntimeHost` 注入 `ioDispatcher`，把 `attach/engineFactory/createSession/history/compact/setSessionModel` 全部 `withContext(ioDispatcher)`，入口方法只做 dispatch；或在 ChatViewModel 统一用 `withContext(ioDispatcher) { runtime.send(...) }`（但这修不了其他调用方，宿主自持 IO 边界更可靠）。工作量 M。

> 〔复核修正〕两点收窄：① `compact`/`setSessionModel` 在 handle 缺失时直接 `return`，**不触发重连**；`history`/`openSession`/`newSession` 在 UI 侧已 `withContext(ioDispatcher)`（`ChatViewModel.kt:678/1136/1211`）。② 「ANR」需「handle 已被回收 + 超长会话」同时成立，日常通常只是掉帧；把 `attach/createSession` 全面下移的成本高于 M。

**[RT-003] 全局 mutex 粒度过粗：建/关会话、history 映射、可挂起的 emit 全在锁内（usage 投影实为锁外）**（P1，并发/性能）· `AgentRuntimeHost.kt:90`、`178-207`、`545-551`、`627-644`、`725-741`、`850-852`

证据：
```kotlin
// 178-186：newSession 在全局 mutex 内建 engine（磁盘/JSONL）并 emit
val opened = mutex.withLock { ... val handle = attach(workspace.cwd, null) ... emitListChanged() ... }
// 545-551：history 全量映射在锁内
val loaded = mutex.withLock { handles[sessionId]?.let { it to it.engine.history(it.session) } }
// 627-634：usage 在锁内做上下文 token 估算（O(消息数)）
val handle = mutex.withLock { handles[sessionId] } ?: return null
runCatching { ... estimateProjectedContextTokens(buildSessionProjection(branch), branch).tokens ... }
// 850-852：emitListChanged 是挂起 emit，可能在锁内挂起
private suspend fun emitListChanged() { eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged())) }
```
影响：`mutex` 是**进程级**单锁，覆盖所有会话。任一操作在锁内做磁盘/JSONL/映射时，其它会话的 `openSession/closeSession/delete/history/send(reconnect)` 全部排队；`eventsBuffer` 满时 `emitListChanged()` 在锁内挂起，会无限期持有全局锁。更隐蔽的是 `onSessionEvent → emitUsage → usage()` 在 SDK 事件分发线程上取这把锁：某会话正在建 engine（数十 ms 到数百 ms）时，**所有会话的流式事件分发被阻塞**，表现为多会话并行时文字卡顿。

> 〔复核修正〕锁内只有 `handles[sessionId]` 查表（`AgentRuntimeHost.kt:628`），`buildSessionProjection`/token 估算在 629-643 **锁外**；「usage 投影在锁内」不成立（RT-009 同错）。另外拆全局锁会触动 `closingFiles/closingSessions/eviction/lastOpenedSessionId` 依赖的全局串行不变量，成本高于 M。`attach` 里的 `existing` 去重、`lastOpenedSessionId`、`closingFiles` 之类只需要保护 map 本身，没必要串行化重活。
建议：拆锁——handles 用 `ConcurrentHashMap` + 每 handle 生命周期用 per-session mutex（或直接依赖 `SessionCommandPlane` 的锁），全局锁只保护 `lastOpenedSessionId/closingSessions/closingFiles` 这几处短临界区；`attach` 改成“锁内占位、锁外建 engine、锁内发布”的两段式；`emitListChanged` 移出锁；`history/usage` 移到 IO 且锁外（只锁内取 handle 引用）。工作量 M。

**[RT-004] ScheduleService 对每一个运行时事件都 refresh()：流式期间按事件频率读+解析 schedules.json**（P1，性能）· `ScheduleService.kt:60-65`、`193-203`、`332-334`、`75-79`

证据：
```kotlin
// 60-65：所有事件（含 TextDelta）
launch { runtime.events.collect { event -> handleRuntimeEvent(event); wake.trySend(Unit) } }
// 193-203：只对 Removed/Archived 做事，但最后无条件 refresh()
internal suspend fun handleRuntimeEvent(event: SessionRuntimeEvent) {
    when (val inner = event.event) { ... else -> Unit }
    refresh()   // = list() = withContext(IO) { store.list() } = mutex + load() 整文件 readText + JSON parse
}
```
影响：`runtime.events` 包含每个 token 的 `TextDelta/ReasoningDelta`（典型 20–100 次/秒）。于是流式输出期间每秒会做几十～上百次 `readText()` + `Json.parseToJsonElement` + `List<Schedule>` 反序列化（`ScheduleStore.load()` 无内存缓存），并落在 IO 线程上与会话 JSONL 追加写竞争 IO。`schedulesState.value` 因内容相等不会触发 UI 重组，CPU/IO 却全部白烧。
建议：在 collect 收口处先 `when` 过滤（只有 `SessionRemoved/SessionArchived/SessionListChanged` 才 refresh），其余直接 `wake.trySend(Unit)`；`ScheduleStore` 增加内存缓存，`changes` 只在自身写入时驱动重读；`refresh()` 从事件路径彻底移除。工作量 S。

**[RT-005] 事件有序性无保证：缓冲溢出时结构事件被延后 emit；stop 终态可能早于 RunStarted**（P1，并发）· `AgentRuntimeHost.kt:76-80`、`421-446`、`493-500`、`524-543`、`839-848`；`SessionCommandPlane.kt:139-153`

证据：
```kotlin
// 839-848：tryEmit 失败后结构事件丢给 backgroundScope.launch，和之后的事件竞争
private fun emit(handle: Handle, event: RuntimeEvent) {
    if (eventsBuffer.tryEmit(scoped)) return
    if (event is TextDelta || event is ReasoningDelta) { Log.w(...); return }
    backgroundScope.launch { eventsBuffer.emit(scoped) }   // 可能晚于后续 tryEmit 成功的事件
}
// 421-433 begin → afterBegin 内部 recordRunStart 会挂起（RunLedger 写盘）
// 493-500 runTurn 在挂起之后才 emit RunStarted；而 stop() 524-543 可在挂起期间同步 emit RunFinished(STOPPED)
```
影响：1）快速通道“顺序”假设被破坏：`RunStarted/TurnInjected` 被延后时可能晚于 `TextDelta`，甚至晚于自己这一轮的 `RunFinished`；UI 全靠 runId 过滤兜底，属于正确性依赖运气。2）用户在 `send()` 的“begin 之后、RunStarted 之前”窗口点停止（该窗口包含 `runLedger.begin` 的两次 IO，毫秒级但真实），UI 会先收到 `RunFinished` 再收到同一 runId 的 `RunStarted`，流式占位可能被重新点亮。
建议：事件出口改为单消费者 `Channel(UNLIMITED)` + 一个收集协程顺序 emit（或每会话事件队列串行化），结构事件永不丢、永不乱序；〔复核勿做：`events` 有 4 个订阅者（`ChatViewModel.kt:104`、`NotificationCenter.kt:55`、`AppGraph.kt:367`、`ScheduleService.kt:61`），Channel 单消费者会把广播变成「谁先取谁有」；`UNLIMITED` 在 token 洪峰下有无界内存风险。可做的只有后半句「`RunStarted` 立即发出」〕`RunStarted` 在 `begin` 成功后的同一同步段立即发出（在 `runLedger.begin` 之前），把 IO 放到事件之后；溢出时对 delta 做可观测丢弃计数而不是逐条日志。工作量 M。

**[RT-006] 核心宿主零单测：1083 行的并发/回收/重连逻辑无人验证**（P1，测试）· `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:43-1065`；`runtime/src/test/kotlin/io/github/whywood/deepdeep/runtime/`

证据：
```kotlin
// runtime/src/test 下 35 个测试类，没有一个针对 AgentRuntimeHost；
// 只有状态机本体有覆盖，例如 SessionCommandPlaneTest.kt:34-60
@Test fun concurrentBeginsStartSingleRun() { ... 8 threads ... assertEquals(1, started.get()) }
```
影响：宿主才是把命令面、事件、台账、目标续跑、预算回收、关闭闸门缝在一起的地方，而这些缝合点恰恰是竞态高发处（RT-003/005/011/012 都在这里）。现在的测试只能保证“零件”对，不能保证“装配”对——例如 `closingFiles` 闸门与 `openSession` 的互斥、`sweepIdleHandles` 的二次校验、`deliverReminder` 的 Started/Injected 两条路、`stop()` 与 `maybeContinueGoal` 的 epoch 失效、`RunStarted` 与终态顺序，均无测试。Schedule 侧也只覆盖了“损坏 intent 文件”，没有覆盖损坏 schedules.json（RT-001）。〔复核修正：后者已被 `ScheduleStoreTest.kt:421/437/447` 覆盖，且断言「损坏保留并 throw」是刻意设计〕
建议：用 fake `PiSessionEngine`/`AgentSession`（好在该宿主已经通过 `engineFactory`/`catalog`/`runLedger` 留了接缝）+ `runTest` 补：evict→send 重连、closing 闸门并发 open、deliverReminder 忙/闲两态、stop 期间 goal 续跑失效、事件顺序与溢出、reconfigure 排队。工作量 M。

> 〔复核修正〕「fake 接缝」并不存在：`PiSessionEngine` 是 final class、SDK `AgentSession` 是 final + internal 构造器，必须先抽接口或改可见性（生产代码改造），M 低估。

**[RT-007] SessionIndex 对任何变化的会话文件整文件重读**（P2，性能）· `SessionIndex.kt:97-125`、`127-176`

证据：
```kotlin
// 107-115 缓存只看 size+mtime，变了就整文件重扫
val entry = if (cached != null && cached.size == size && cached.mtime == mtime) cached
            else { val parsed = parse(file, size, mtime) ?: ...; parsedFileCount += 1; ... }
// 134-161 parse 逐行 readLine，只为数 messageCount 和找 session_info
file.bufferedReader(Charsets.UTF_8).use { reader -> while (true) { val line = reader.readLine() ?: break ... } }
```
影响：当前会话每完成一轮 JSONL 就变大 → 下一次 `list/listAll`（抽屉刷新、`allSessions`、`sessionTitle`、重连）触发该文件**全量**重读并逐行分配 String；大会话（几百轮、工具输出可达 MB 级）每次都是 O(文件字节) 的 IO+分配，且 `persist()` 在 dirty 时整写 `session-index.json`。`sessionTitle`/`deleteSession` 还走 `catalog.find`→`listAll(所有 cwd)`，即在循环里放大成全量扫描（`SessionCatalog.kt:30-31`，调用点 `app/.../ScheduleScreen.kt:67`、`app/.../service/NotificationCenter.kt:190,236`）。
建议：索引条目增加 `scannedOffset/messageCount`，文件变大时从上次偏移读尾部增量计数〔复核降级：`messageCount` 在 app 内无消费者（仅 `SessionIndexTest` 断言），为它加增量计数是给无用字段增复杂度；更省的做法是只扫头/尾或删掉该字段，P2→P3〕（只在尾部解析新增行，同时处理 session_info 改名）；文件变小/头部变化才全量重建；`sessionTitle` 改为 `filePath` 定向读（meta + 已知 summary），避免全目录扫描；`persist` 做 200–500ms debounce 或只落脏条目。工作量 M。

**[RT-008] 后台任务持久化写放大：每秒重写 ~800KB JSON，并对每条记录做 256KB 级拷贝**（P2，性能）· `JobRegistry.kt:38-47`、`158-168`、`217-224`、`377-391`；`JobPersistence.kt:55-69`、`152-158`

证据：
```kotlin
// JobRegistry.kt:217-224 每个输出 chunk 都请求持久化（节流 1s）
val exitCode = runner.run(record.command) { chunk -> record.append(chunk); publishOutput(); requestPersist() }
// 166-168 persistedJobs() 对每条 record 做 ring.snapshot()（运行中 256KB 拷贝）
private fun persistedJobs(): List<PersistedJob> = synchronized(lock) { records.values.map { it.persisted() } }
// JobPersistence.kt:57-66 全量重写，settled 各带 16KB 输出 → 50×16KB=800KB
val running = jobs.filter { it.status == Running }; ...
```
影响：`MAX_RECORDS=50`、settled 每条保留 16KB 输出，`save()` 每次把全部记录序列化重写 `jobs.json`（最坏 ~800KB）+ 每条运行中记录 `ring.snapshot()` 拷 256KB + `outputTail()` 再 `toByteArray` 一次。聊天的后台任务持续输出时最多每秒一次，等于每秒接近 1MB 的写盘与 2×RSS 瞬时垃圾，Flash 寿命与 GC 都不划算。

> 〔复核〕这是**最坏值**当常态：800KB 需 50 条 settled 全部写满 16KB；「每条记录 256KB 拷贝」只对运行中记录成立。结论保留，但建议按 P3 排期。
建议：`jobs.json` 只持久化“运行中记录 + settled 的元数据（不含 output 或只留 2–4KB）”，output 独立文件/ring 直接切尾；持久化间隔从 1s 提到 5s，且只在状态变化时强制；`persistedJobs()` 复用 `OutputRing` 的私有快照避免多一层 ByteArray 转换。工作量 M。

**[RT-009] usage 投影按工具调用触发（锁内只取 handle）**（P2，性能）· `AgentRuntimeHost.kt:627-644`、`668-671`、`1039-1053`、`894-909`

证据：
```kotlin
// 1039-1053 每个 ToolExecutionEnd 都 emitUsage
is AgentEvent.ToolExecutionEnd -> { emit(...); emit(ActivityChanged(...)); emitUsage(handle, runId) }
// 627-634 取全局 mutex 后做 O(消息数) 投影
val handle = mutex.withLock { handles[sessionId] } ?: return null
... estimateProjectedContextTokens(buildSessionProjection(branch), branch).tokens
```
影响：一次工具循环可能有几十次 ToolEnd，每次都重建会话投影并估算 token（O(分支消息数)、大量临时对象），且在全局锁内执行 → 叠加 RT-003 的串行化。〔复核修正：投影实际是 O(全部上下文消息/字符)，比原文更贵，但在**锁外**执行（`628` 只取 handle）；`finishRun` 的 `messages.asReversed()` 是 SDK `toList()` 视图，每轮一次可忽略〕`finishRun` 还用 `messages.asReversed()` 复制整条消息列表（`894-895`）。上下文用量 UI 只需要“roughly current”，不需要每次工具调用精确刷新。
建议：usage 采样节流（如 500ms 合并一次，工具结束只置 dirty），计算移出锁并放 IO；`buildSessionProjection` 增量维护（以最后一条消息为锚，只在 compaction/branch 变化时全量重建）；`finishRun` 用 `asReversed().firstNotNullOfOrNull` 改为 index 反向遍历。工作量 M。

**[RT-010] SessionCommandPlane 在自身锁内回调外部（session.isBusy / emit + session 扫描），存在锁序反转隐患**（P2，并发）· `SessionCommandPlane.kt:106-113`、`139-153`；`AgentRuntimeHost.kt:527-543`

证据：
```kotlin
// plane 在 synchronized(state.lock) 里调用宿主回调，宿主的 emitStopped 又会回调 session
fun stop(sessionId: String, emitStopped: (Stop) -> Unit): Stop? {
    synchronized(state.lock) { ... if (!state.terminalEmitted) { state.terminalEmitted = true; emitStopped(stop) } ... }
}
// AgentRuntimeHost.kt:527-537 回调内读 session 状态
val stopped = commands.stop(sessionId) { stop -> val text = runCatching { handle.session.getLastAssistantText() }.getOrDefault(""); emit(...) }
// deliver 也把 session.isBusy 的调用放进 plane 锁内（106-113）
synchronized(state.lock) { if (state.reconfigureOwner != 0L) return null; if (state.busy || streaming()) return Injected(...) }
```
影响：`state.lock` 是纯内存锁，却被用来保护“调用 session（SDK 对象）再 emit”的复合动作。若 SDK 在持有内部锁的事件分发线程里回调订阅者（`onSessionEvent → emitTerminal → claimTerminal` 会拿 `state.lock`），而 `stop()` 此时持有 `state.lock` 去调 `getLastAssistantText()`（很可能需要同一把 SDK 锁），就是典型的 ABBA 死锁。当前没有触发报告，但这是“锁内不调用外部对象”的硬规则被打破。

> 〔复核降级：SDK 源码否定该风险——`subscribe` 用独立 collector 协程回调 listener，不在事件发射线程同步回调；`isBusy`/`getLastAssistantText` 都是无锁内存读；`stop` 回调里的 `emit` 是 `tryEmit`。无死锁证据，最多算代码卫生（P3）。〕
建议：plane 的方法只返回状态迁移结果（`Stop`/`Delivery.Started` 已足够），宿主在锁外查 session 并 emit；`deliver` 的 `streaming()` 判定改为宿主在锁外取得布尔值后传入；给 plane 增加“锁内只做字段读写”的注释性约束并补并发测试。工作量 S。

**[RT-011] steer/followUp 的“先查后做”竞态：begin 判忙与 session.prompt 之间无锁**（P2，并发）· `AgentRuntimeHost.kt:296-320`、`866-872`

证据：
```kotlin
val start = begin(handle, TurnSource.USER)      // 判忙
if (start != null) { runTurn(...); return }
handle.pending.enqueue(kind, text)              // 之后 run 可能已结束并被 release→dropPendingInputs 清空
emitQueueChanged(...)
try { handle.session.prompt(text, contents, kind.behavior()) } catch ... { handle.pending.discard(...) }
```
影响：`release()` 里的 `dropPendingInputs()` 可能在 enqueue 之后、prompt 之前把这条输入当“丢弃”上报（用户看到 `QueuedInputsDropped`），或者反过来在 enqueue 之前 drain 导致输入永久滞留 `PendingInputs`（下次才被清）。窗口小，但违反 ARCHITECTURE 声称的“不再用先读 busy 再决策的竞态判断”。
建议：把“入队 + prompt”收进命令面（`commands.enqueueOrBegin(...) { text -> session.prompt }`，在 state.lock 内完成状态迁移并返回需要执行的动作），或给该会话加 per-session mutex 覆盖整个注入段。工作量 M。

**[RT-012] deliverBackgroundTurn 在 handle 缺失时静默丢弃目标轮/任务唤醒**（P2，正确性）· `AgentRuntimeHost.kt:378-401`

证据：
```kotlin
suspend fun deliverBackgroundTurn(sessionId: String, notice: TurnNotice?, prompt: String, source: TurnSource) {
    val handle = mutex.withLock { handles[sessionId] } ?: return   // 静默 return
    when (val delivery = commands.deliver(...)) { ... }
}
```
影响：目标续跑与后台任务唤醒都走这里。`keepHandleAlive` 只覆盖“运行中任务/活跃目标”，任务 settle 后到 `notifier` 投递之间、或目标 400ms 续跑延迟期间，另一会话打开触发 `sweepIdleHandles` 就可能把 handle 回收；投递随之被吞，模型不会收到任务完成/目标下一轮的提示，UI 上看任务已完成却“没有下文”，且无日志。

> 〔复核降级〕触发窗口几乎不可达：运行中 job 被 `keepHandleAlive` 保护，`release()` 时 `handle.touch()` 刚刷新 `lastActiveAt`，而回收还要求「>6 个 handle 且空闲 >10 分钟」。建议保留 `Log.w` 防御，但按 P3 定级。
建议：返回投递结果（`Delivered/Skipped`），失败时用 `handleFor(sessionId)` 懒重连再投一次，或至少 `Log.w` + 发 NoticeEvent 让用户知道唤醒失败；`keepHandleAlive` 增加“有未投递的后台唤醒”状态。工作量 S。

**[RT-013] SessionCatalog.delete 吞掉删除失败，meta 已删、事件已发**（P2，正确性）· `SessionCatalog.kt:33-36`；`AgentRuntimeHost.kt:583-593`

证据：
```kotlin
suspend fun delete(filePath: String) = withContext(Dispatchers.IO) {
    runCatching { fs.delete(filePath.toPath()) }
    Unit
}
// AgentRuntimeHost.deleteSession：先 detach、删 meta、删文件（可能失败）、emit SessionRemoved
```
影响：删除失败（文件被占用/IO 错误）时用户看到会话消失、标题/置顶元数据丢失，但 JSONL 仍在；下一次索引重建后会话以“新对话”标题重新出现，且已无法恢复覆盖过的标题。
建议：`delete` 返回 `Boolean`，失败时 `deleteSession` 保留 meta 并把错误抛给 UI（或返回失败结果）；文件删除走 `FileSystem.delete` 的 `mustCreate=false` 语义并记录日志。工作量 S。

**[RT-014] ScheduleStore 对 legacy schemaVersion=1 直接清空全部提醒**（P2，正确性/数据丢失）· `ScheduleStore.kt:246-270`

证据：
```kotlin
if (version == LEGACY_SCHEMA_VERSION) {
    persist(emptyList())
    return emptyList()
}
if (version != SCHEMA_VERSION) throw CorruptScheduleDataException("不支持的 schemaVersion：$version")
```
影响：旧版本用户升级后所有定时提醒被静默删除，没有任何备份或提示（`ARCHITECTURE.md` 写的是“旧 `projects` DataStore 与旧 session 目录/cwd 均做幂等迁移”，把提醒直接丢弃与迁移承诺不符；如果 v1 的语义无法迁移，至少应保留 `.v1.bak`）。
建议：升级前把原文件复制为 `schedules.v1.bak` 并 `Log.w`；能从 v1 字段映射的部分做迁移；确实无法迁移时发一次本地通知/设置页提示。工作量 S。

**[RT-015] 构造器里同步读盘 + by lazy 首次访问可能落在主线程；ProviderCatalog 在主线程全量建目录**（P2，性能）· `AppGraph.kt:91`、`148`、`174-186`；`GoalService.kt:19`；`JobRegistry.kt:38-40`；`GoalStore.kt:23`；`JobPersistence.kt:40`；`DeepDeepApplication.kt:18`

证据：
```kotlin
// AppGraph.kt:174-186
val goalService: GoalService by lazy { GoalService(GoalStore(...), scope) }   // 构造器 store.read() 同步读 goals.json
val jobRegistry: JobRegistry by lazy { JobRegistry(...) }                     // init restorePersisted → persistence.load() 同步读 jobs.json
// AppGraph.kt:148 在 Application.onCreate 的主线程执行
val providerCatalog = ProviderCatalog(httpTransport, credentials)             // builtinProviders 全量构建 42 家 provider+模型
```
影响：`by lazy` 不保证首次访问线程——Compose 首次读取 `goalService.goals` 或插件首次收集工具时就可能在 Main 上同步读盘（几 KB～几十 KB，Flash 冷缓存可达数 ms～数十 ms）；`ProviderCatalog` 的构造是确定的 Main 线程启动开销（42 家 provider、数百模型对象）。
建议：Store 增加 `suspend fun load()` 并在 `AppGraph.prepare()` 的 IO 段预热（`goalService/jobRegistry/scheduleService` 在 `prepare()` 内显式 touch）；`GoalService` 构造器接收已加载的 map；`ProviderCatalog` 改 lazy 或异步预热。工作量 S。

**[RT-016] HistoryMapper 对每个工具结果重建整条消息 steps（长工具链回合成平方级拷贝）**（P2，性能）· `HistoryMapper.kt:117-159`

证据：
```kotlin
} else {
    val updated = existing.copy(...)
    traces[message.toolCallId] = updated
    current = base.copy(steps = base.steps.map { step ->          // 每个 tool result O(steps) 重建
        if (step is AssistantStep.Tool && step.trace.toolCallId == message.toolCallId) AssistantStep.Tool(updated) else step
    }, finishedAt = message.timestamp)
}
```
影响：一个回合 50 个工具调用 → 50 次逐步增长的 list 复制（O(k²) 引用复制，元素复用、只分配 list，k=50 约几千次引用拷贝，微秒级）〔复核降级 P3〕；`history()` 在 UI 打开会话、后台对账、回前台对账时都会调用（`ChatViewModel.kt:678/1137/1145/1307`），会话越长越慢，且 RT-003 让它在全局锁内执行。
建议：`AssistantMessage.steps` 构建改为可变 `ArrayList` + `toolCallId→index` 映射就地替换，结束时一次性 `toList()`；`traces` 直接用 index map。工作量 S。

**[RT-017] sessionTitle 一次标题查询退化为全量扫描**（P2，性能）· `AgentRuntimeHost.kt:553-556`；`SessionCatalog.kt:30-31`

证据：
```kotlin
override suspend fun sessionTitle(sessionId: String): String? = mutex.withLock {
    handles[sessionId]?.session?.sessionManager?.getSessionName()
        ?: catalog.find(visibleCwds(), sessionId)?.title
}
// SessionCatalog.find：listAll(cwds).firstOrNull { it.id == sessionId }
```
影响：handle 不在（绝大多数不是当前会话）时走 `listAll`：遍历所有 cwd 的会话目录 + 索引扫描 + DataStore 读。`ScheduleScreen` 在 `for (id in missing) runtime.sessionTitle(id)` 里对每个缺失会话调用一次（`app/.../ui/schedules/ScheduleScreen.kt:67`），N 个提醒 = N 次全量扫描；`NotificationCenter` 每条通知也各调一次（`app/.../service/NotificationCenter.kt:190,236`）。且它还在全局 mutex 内（RT-003）。
建议：Catalog/Index 提供 `titleOf(visibleCwds, sessionId)` 的定向查找（按 cwd 定位目录 + 只读 meta/header），宿主缓存 `sessionId→(filePath,cwd,title)`；调用点改为批量接口。工作量 M。

**[RT-018] JobRegistry kill 与 start 的竞态：coroutine 在 launch 后才赋值**（P2，并发）· `JobRegistry.kt:78-81`、`119-130`

证据：
```kotlin
val coroutine = scope.launch(start = CoroutineStart.LAZY) { execute(record) }
record.coroutine = coroutine        // 赋值前 kill 读到 null
coroutine.start()
// kill: val coroutine = synchronized(record.lock) { ...; record.coroutine }
//       coroutine?.cancel()        // null → 只置 killRequested，命令照跑
```
影响：`start()` 与 `kill()` 来自不同线程（工具执行线程 / UI 或另一工具），在 `records` 发布后到 `record.coroutine` 赋值之间（含 `publish()` + `requestPersist()` 的耗时）kill 会“成功返回 false 但取消不了”，任务继续跑完才被判为 KILLED —— 用户点“停止”后进程仍在执行，且 UI 先显示 running 再显示 killed。
建议：在 `synchronized(lock)` 内创建 LAZY coroutine 并赋给 `record.coroutine`，锁外再 `start()`；`kill` 若读到 `null` 也置 `killRequested` 并在 `execute` 开头检查。工作量 S。

**[RT-019] 关键失败静默：任务唤醒异常空 catch、OAuth 流程吞 CancellationException、会话模型恢复失败无日志**（P2，正确性/可维护性）· `JobRegistry.kt:267-279`；`ProviderOAuthService.kt:29`；`McpServices.kt:101-116`；`PiSessionEngine.kt:54-58`

证据：
```kotlin
// JobRegistry.kt:275-278
} catch (cancelledError: CancellationException) { throw cancelledError } catch (_: Exception) { }
// ProviderOAuthService.kt:29
): Result<Unit> = runCatching { ... loginAndStoreOAuthCredential(...) ... }   // CancellationException 被收进 Result.failure
// McpServices.kt:101-116 同一模式：withContext(IO) { runCatching { provider.signIn(...) } }
// PiSessionEngine.kt:54-58 恢复失败静默降级
val restored = if (file == null) null else runCatching { SessionManager.open(...).buildSessionContext() }.getOrNull()
```
影响：1）后台任务的 `notifier`（唤醒模型）失败被彻底吞掉，用户与日志都看不到“任务完成但没有唤醒”；2）`runCatching` 包裹挂起调用会把取消语义降级成普通失败——调用方协程已取消，OAuth/登录流程可能继续，UI 侧还会把取消当成“登录失败”提示；3）会话文件头损坏/模型已下线时静默回退到默认模型，用户只会看到“模型变了”。
建议：异常处理区分三类——`CancellationException` 一律 rethrow；可恢复业务失败返回 Result；剩余 `Log.w` 记录。给 JobRegistry 的 notifier 增加日志和一次重试。工作量 S。

**[RT-020] 工坊桥接入口无长度上限、agent.run 无并发闸门**（P2，安全/资源）· `WorkshopBridge.kt:42-68`、`193-213`；`WorkshopAgentService.kt:79-200`

证据：
```kotlin
// WorkshopBridge.kt:43 直接解析任意长度原始消息（来自页面 JS）
val request = runCatching { Json.parseToJsonElement(rawMessage) }.getOrNull() as? JsonObject ?: ...
// WorkshopAgentService.run 每次调用：新建 run 目录 + engine + session（无信号量/无并发上限）
val root = createRunDirectory(); ... val engine = createEngine(workspace, tempAgentDir); ...
```
影响：`data.set` 的值上限 256KB 是在解析之后才检查，页面可以一次发数 MB 消息让主/WebView 线程做 JSON 解析与分配；`agent.run` 可被页面并发调用任意次（每次完整 engine + 网络会话 + 工具集），180s 超时只限制单次时长不限制并发数，低内存设备上可能 OOM。

> 〔复核修正〕JSON 解析实际在 `Dispatchers.IO`（`WorkshopBridgeHost.kt:58`），不是主/WebView 线程；工坊页面是用户自装 app，威胁模型有限，P2→P3。
建议：`handle()` 入口先判断 `rawMessage.length` 上限（如 512KB，超限回 `invalid_request`）；`WorkshopAgentService` 加 `Semaphore(1..2)` 串行/限流并在超限时回 `agent_unavailable`；`capabilities()` 结果做短 TTL 缓存。工作量 S。

**[RT-021] WorkshopStore.loadDraft 的 fallback 可能覆盖同名已安装创意**（P2，正确性）· `WorkshopStore.kt:126-140`

证据：
```kotlin
if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) throw WorkshopLoadException("an app with id ... already exists")
try { Files.move(draft.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
catch (_: AtomicMoveNotSupportedException) {
    Files.move(draft.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)   // 存在性检查到 move 之间是 TOCTOU
}
```
影响：不支持 ATOMIC_MOVE 的存储/跨文件系统场景下，第二个并发 `workshop_load` 或检查后的目录创建会被 `REPLACE_EXISTING` 覆盖；`loadDraft` 是工具可并发调用的，虽然概率低，但后果是用户已安装创意被静默替换。

> 〔复核修正〕「静默覆盖」不成立：NIO 用 `REPLACE_EXISTING` 把目录 move 到**非空**目标会抛 `DirectoryNotEmptyException`，被现有 `IOException` 分支转成「已存在/加载失败」，不会替换。去掉 `REPLACE_EXISTING` 仍是正确小修，严重度 P3。
建议：fallback 不带 `REPLACE_EXISTING`，捕获 `FileAlreadyExistsException` 转成同样的“已存在”错误；或先 move 到 `apps/<id>.<random>` 再原子 rename 到目标。工作量 S。

**[RT-022] 架构中心化：AgentRuntime 胖接口 + AgentRuntimeHost God class + 事件模型分散/空 runId 哨兵**（P2，架构）· `RuntimeTypes.kt:203-390`；`AgentRuntimeHost.kt:43-1065`；`Notices.kt:25-38`

证据：
```kotlin
interface AgentRuntime {          // 40 个方法：工作区 CRUD、会话生命周期、历史、模型配置、用量、事件、提醒、队列……
    suspend fun newSession(workspaceId: String): OpenedSession
    suspend fun setSessionThinkingLevel(...)
    ...
}
// 事件模型的宿主级事件用空字符串 runId
data class SessionRemoved(override val runId: String = "") : RuntimeEvent
data class SessionListChanged(override val runId: String = "") : RuntimeEvent
// NoticeEvent/BackgroundTurnStarted/UsageChanged 又定义在 Notices.kt，与 RuntimeTypes 分离
```
影响：1）`AgentRuntime` 40 方法让任何 fake/替代实现都很重（`ChatViewModel` 测试的 FakeAgentRuntime 已有数百行），也无法按 UI 场景做接口隔离；2）宿主 1083 行里 handle 生命周期、预算、台账、目标续跑、MCP 热更新、事件投影互相缠绕，是本模块后续演进的瓶颈（改一处要读全文）；3）`runId=""` 哨兵把“宿主事件”和“运行事件”混在同一层，UI 需要靠语义判断“空 runId 表示宿主事件”；`NoticeEvent` 等又在另一个文件，事件模型的单一真相被拆散。

> 〔复核修正〕`findings.tsv` 写「60 个接口成员」有误，实测 36 个 `fun` + 4 个属性 = 40；`FakeAgentRuntime` 约 151 行而非「数百行」。
>
> 〔复核勿做〕按本条做 L 级接口/宿主/事件重构是本报告风险收益比最差的一项：宿主零测试、接口被 app 多处消费且有 4 个 fake 实现、事件哨兵语义散落 UI；收益只是「可演进性」，回归面却是全局。
建议：接口按用途切分（`SessionCommands`/`SessionHistory`/`WorkspaceAdmin`/`RuntimeState` 或让 UI 只依赖需要的子接口）；宿主抽出 `HandleRegistry`（handles/预算/关闭闸门）、`RunCoordinator`（begin/turn/release/goal continuation）、`SessionEventBus`（emit/有序/溢出）、`McpReconfigurer`；事件 sealed 拆 `HostEvent`（无 runId）与 `RunEvent`（runId 非空），`NoticeEvent/BackgroundTurnStarted/UsageChanged` 归并进 RuntimeTypes。工作量 L。

**[RT-023] SessionCommandPlane 的 states 不随 closeSession 清理**（P3，资源）· `SessionCommandPlane.kt:66-89`；`AgentRuntimeHost.kt:209-211`

证据：
```kotlin
private val states = ConcurrentHashMap<String, SessionState>()
fun attach(sessionId: String) = states.computeIfAbsent(sessionId) { SessionState() }
fun detach(sessionId: String, keepReconfigure: Boolean = false) { ... }   // 只清字段，不 remove
// 宿主 closeSession = mutex.withLock { detach(sessionId) }，没有 forget
```
影响：进程生命周期内每个打开过的 session 常驻一个 `SessionState`（含 lock + volatile 字段），虽然小，但会随使用单调增长；`closeSession` 在 app 内没有调用者，指标上更接近“永不回收”。
建议：`detach` 在“无 pendingReconfigure”时直接 `forget`，或宿主在 `closeSession` 时 `forget`；给 states 加 LRU 上限。工作量 S。

**[RT-024] RunLedger 每次 begin/clear 都整文件读+重写，且 begin 在发送路径上被 await**（P3，性能）· `RunLedger.kt:35-57`；`AgentRuntimeHost.kt:435-446`

证据：
```kotlin
suspend fun begin(...) = writeMutex.withLock { withContext(io) {
    val current = readEntries(target); val next = current.filterNot{...} + record; writeEntries(target, next)
} }
suspend fun clear(runId) = ... readEntries + writeEntries ...
// recordRunStart 在 afterBegin（send 开始前）await 这段 IO
```
影响：每次运行开始/结束各做一次完整 `runs.json` 读+写（两次文件 IO + JSON 解析），`begin` 还在实际发请求之前同步等待（增加首 token 延迟几 ms～十几 ms）；文件最多 64 条因此成本可控，但调用频率是“每轮 2 次”。
建议：内存维护 `Map<runId, record>` + 合并写（300–500ms debounce），`begin` 只更新内存并异步落盘；或用 append-only 日志 + 启动时 compaction。工作量 M。

> 〔复核勿做〕「`begin` 只更新内存、异步落盘」会摧毁 RunLedger 的唯一价值——它存在就是为了「发送前先把在途 run 落盘」，崩溃后据此提示 interrupted；异步窗口内进程被杀就丢恢复线索。正确优化是合并 `clear`、减少重写，保留 `begin` 的 await。

**[RT-025] Cron 每次 nextRun 都重新解析表达式；timer 循环重复读 store**（P3，性能）· `ScheduleModels.kt:97-102`；`ScheduleService.kt:317-330`；`ScheduleStore.kt:201-231`

证据：
```kotlin
private fun ScheduleTrigger.Cron.nextRunForCron(now: ZonedDateTime): ZonedDateTime? =
    try { CronExpression.parse(expression).nextRun(now) } catch (...) { null }
// timerLoop 每轮 store.list()，deliverDue 里又 store.list() 一次
```
影响：`markDelivered/recomputeTargets/validation/nextRun` 每次都对每个 cron 提醒重新 `split + parseField`（含正则与数组分配）；timer 每 15 分钟或被唤醒时读盘两次。规模小但完全是重复计算。
建议：`Schedule` 增加惰性解析缓存（`CronExpression` 以 expression 字符串为 key 的进程级 cache，或 `Cron` trigger 内 `by lazy` 字段）；`timerLoop` 复用一次 `list()` 结果传给 `deliverDue(snapshot)`。工作量 S。

**[RT-026] 热路径上的小分配**（P3，性能）· `PiSessionEngine.kt:148-152`；`HistoryMapper.kt:235-237`；`ProviderCatalog.kt:30-39`

证据：
```kotlin
fun titleFor(text: String): String {
    val normalized = text.trim().replace(Regex("\\s+"), " ")   // 每次编译 Regex
// HistoryMapper.toStringMap：value.toString().trim('"')        // 每个工具参数都 JsonElement.toString
// ProviderCatalog.models()：每次重建 List<ModelOption>（UI 频繁调用）
```
影响：单次调用成本小，但 `titleFor` 每新会话一次、`models()` 被 composer/设置页频繁调用、`toStringMap` 每个工具调用一次；属于“顺手能省”的分配。
建议：`Regex` 提为 `private val`；`toStringMap` 对 `JsonPrimitive` 直接取 `content`/`toString` 按需；`models()` 结果缓存（provider 目录启动后不变）。工作量 S。

**[RT-027] 解密失败与“未配置”不可区分；readAll 逐个解密无缓存**（P3，可维护性）· `EncryptedSecretStore.kt:42-55`、`86-101`

证据：
```kotlin
private fun decrypt(encoded: String): String? = try { ... }
catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }          // Keystore 失效 / 密文损坏 → 与“没有 Key”同义
```
影响：设备锁屏/密钥失效导致解密失败时，UI 表现为“API Key 未配置”，用户只会重填；无法区分“数据损坏”并给出针对性提示。`readAll` 对每个 provider 各做一次 Keystore 解密，也没有内存缓存（`SdkCredentialStore.preload` 重复调用时）。
建议：`decrypt` 返回 `Result<String>`，失败时 `Log.w` 并在设置页提示“凭据需要重新录入”；`readAll` 结果由 `SdkCredentialStore` 缓存（已有 cache，改用 `preloadAll` 即可，避免 `preload` 在循环里调用 `readRawRecords()` 的 O(n²) 解密——见 `SdkCredentialStore.kt:48-57`）。工作量 S。〔复核：`AppGraph.prepare()` 已调用 `preloadAll()`，生产路径的 O(n²) 只在 `preload(单 provider)` 且多 provider 配置时出现；本条实际可改的只剩 `decrypt` 返回 `Result` + 日志。〕

**[RT-028] 死代码/重复职责**（P3，可维护性）· `SessionMetaStore.kt:86-124`；`RuntimeTypes.kt:341`；`PiSessionEngine.kt:137-146`

证据：
```kotlin
class SessionRepository(engine: PiSessionEngine, metaStore: SessionMetaStore) { ... }  // 全仓库无引用
suspend fun closeSession(sessionId: String)                                            // app 内无调用
suspend fun listSessions(): List<ConversationSummary> = SessionManager.list(paths, fs)...  // 与 SessionIndex/SessionCatalog 重复
suspend fun deleteSessionFile(filePath: String) { runCatching { fs.delete(...) } }     // 吞错
```
影响：`SessionRepository` 是 SessionIndex 之前的旧实现，留着会误导新代码（两条会话列表路径、两份 delete 语义）；`closeSession` 无调用让人误以为会话会被及时回收（结合 RT-023）。
建议：删除 `SessionRepository`/`closeSession`/`PiSessionEngine.listSessions/deleteSessionFile`，或迁移到唯一实现并加 `@Deprecated` 提示。工作量 S。

**[RT-029] 测试里的墙钟轮询与虚拟时间混用**（P3，测试）· `runtime/src/test/kotlin/io/github/whywood/deepdeep/runtime/ScheduleServiceTest.kt:524-531`

证据：
```kotlin
private fun TestScope.awaitStoreIo(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 3_000
    while (System.currentTimeMillis() < deadline) { runCurrent(); if (condition()) return; Thread.sleep(2) }
    runCurrent()
}
```
影响：`runTest` 的虚拟时间与真实 `Thread.sleep(2)` 轮询混用，慢 CI 上可能超过 3s deadline 变成 flaky；也可能“假立即通过”。这类等待掩盖了 service 的异步边界，不如改用 `Dispatchers.setMain`/可控 IO dispatcher 或 `advanceUntilIdle`。
建议：给 `ScheduleService` 注入 `ioDispatcher`（便于测试注入 `UnconfinedTestDispatcher`），测试用 `advanceUntilIdle()` 收敛；若必须轮询，用 `withTimeout(3.seconds)` + `yield()` 而不是 `Thread.sleep`。工作量 S。

**[RT-030] `started` 非 volatile 被无锁读取；`turnSources` 进程内只增不清**（P3，并发）· `AgentRuntimeHost.kt:96-121`；`GoalService.kt:21`、`131-135`、`229-235`

证据：
```kotlin
private var started = false                                   // 无 @Volatile
override suspend fun ensureStarted() { if (!started) start() } // 锁外读
// GoalService
private val turnSources = ConcurrentHashMap<String, TurnSource>()
override suspend fun noteTurn(sessionId: String, source: TurnSource) { turnSources[sessionId] = source }
// 只有 clearSession 会 remove
```
影响：`started` 的可见性问题被 `start()` 内的 mutex 与幂等检查兜住（后果只是多做一次空转），属代码卫生；`turnSources` 每个有过运行的会话一条，映射很小但生命周期与进程一致。
建议：`started` 改 `@Volatile`（或用 `AtomicBoolean`）；`noteTurn` 在会话关闭/归档时清理（`sessionCleaner`/`SessionRemoved` 监听），或直接由 handle 持有 source 状态。工作量 S。

**[RT-031] 工坊 capabilities() 每次调用都建临时 run 目录 + engine + 全量工具**（P3，性能）· `WorkshopAgentService.kt:59-77`

证据：
```kotlin
override suspend fun capabilities(): WorkshopAgentCapabilities {
    val root = createRunDirectory()
    try { ... val engine = createEngine(workspace, tempAgentDir)
          val tools = buildWorkshopAgentTools(engine.availableTools())
          val skills = discoverSkills(workspace) ... } finally { root.deleteRecursively() }
}
```
影响：页面每次 `app.agent.capabilities()`（页面初始化、可能每次打开）都会 create/delete 目录、构造 engine、`defaultCodingTools` 全量构建、扫描技能目录；结果其实是静态的（工具名/技能名）。
建议：缓存 capabilities（provider/技能变化时失效，或 10–30s TTL），并放到 IO 执行。工作量 S。

**[RT-032] Provider 能力名单硬编码，与 SDK 目录易漂移**（P3，可维护性）· `ProviderCatalog.kt:79-88`

证据：
```kotlin
private val OAUTH_PROVIDERS = bundledOAuthFlows().keys
private val KEYLESS_PROVIDERS = setOf("amazon-bedrock", "github-copilot", "openai-codex", "kimi-coding", "meta", "radius")
```
影响：`supportsApiKey` 依赖手写集合，SDK 新增/更名 provider 时设置页判断会静默错误（显示“支持 API Key”但实际只支持 OAuth）。
建议：用 SDK 暴露的 provider 元数据（若有）或从 `builtinProviders` 的 auth 字段推导；否则加单元测试断言名单与 `bundledOAuthFlows` 的差集稳定。工作量 S。

## 架构评估

**分层与边界**：外部边界（app ↔ runtime）清晰且稳定：`AgentRuntime` + 少量数据类 + `RuntimeEvent`；runtime 不含 Android UI、不本地化用户文案、不直接读 App 资源（系统提示词是 runtime 自带常量 + SDK 覆盖机制），这几点在实际代码里是守住的。内部边界则模糊：宿主类同时是“会话管理器 / 事件总线 / 持久化协调者 / 目标驱动器 / MCP 管理器”，六簇职责在 `AgentRuntimeHost` 一个 1083 行文件里汇合；其余簇（Schedule/Goal/Job/Workshop）反而分得很好（Store/Service/Tools/Contracts 四件套一致）。

**状态所有权**：最值得肯定是 `SessionCommandPlane` 对“运行状态”的单一所有权 + `activeRuns` 作为 UI 真相 + `events` 作为快速通道 + UI 侧 history 对账，这套三段式与 ARCHITECTURE 描述一致，代码也真的这么实现（`AgentRuntimeHost.kt:82-88`、`460-487`、`911-943`；UI 侧 `ChatViewModel.reconcile`）。问题在于“所有权”之外还有并列的 **handle 表 + 台账 + 命令面三套状态**，它们的更新点分散在 `begin/afterBegin/release/detach/sweep`，缺少一个不变量检查器（例如“busy=true ⇒ activeRuns 有该项 ⇒ ledger 有记录”）；RT-005/010/011 本质上都是这类分散更新造成的窗口。

**并发模型**：混合了 `Mutex`（全局与 per-store）、`synchronized`（plane/ring/PendingInputs）、`ConcurrentHashMap`、`@Volatile`、`StateFlow` 更新（`update{}` 与直接赋值混用）。单点看都合理，组合起来存在三处系统性问题：全局锁跨重活（RT-003）、锁内回调外部对象（RT-010）、事件通道无顺序保证（RT-005）。`busyState.value = commands.busySessionIds()`（`AgentRuntimeHost.kt:829-831`）这种直接赋值没问题（值由权威状态重算），但 `activeRunState.update{}` 与 `publishBusy` 不在同一原子段，短暂不一致由 UI 对账兜底——设计上接受，文档也写明。

**持久化与恢复**：每个 feature 一个目录一个 JSON + schemaVersion + 临时文件 + ATOMIC_MOVE 的约定执行得不错（RunLedger/JobPersistence/QuestionPersistence/ScheduleStore/GoalStore/McpConfigStore 都遵循，`SessionIndex` 也是）。恢复语义分层清楚：Run 残留→interrupted 提示；Question 残留→interrupted 通知；Job 残留→INTERRUPTED；Schedule 投递→intent 补投。缺陷有二：损坏策略不统一（RT-001）与 v1 提醒静默清空（RT-014）。另外 `SessionIndex` 的持久化是“列表调用顺带整写”，缺少 debounce，写频率高于必要。

**可测试性**：好的一面是几乎所有外部边界都做了注入——`engineFactory`、`CoroutineScope`、`Clock`、`ioDispatcher`、`runner`/`runnerFactory`、`keepHandleAlive`、`isForeground`，测试里 FakeAgentRuntime/MutableTestClock/fake engine 都用起来了（Schedule/Job/Goal/Question/Workshop/CommandPlane 都有高质量测试）。弱点集中在宿主本身未测（RT-006）；另外若干 Store 在构造器里同步读盘（RT-015）导致不好在测试里控制初始化失败路径。

**可演进性**：新增“一个 feature”按 ARCHITECTURE 的四件套（Store/Service/Tools/事件）扩展摩擦很小，Schedule/Goal/Job 已经证明这套模板可复制。真正的演进瓶颈是宿主：任何新增运行态能力（例如新的 `TurnSource`、新的后台唤醒源）都要改 `AgentRuntimeHost` 的 5–6 处状态更新点。若要支持更多插件/多窗口/后台并行，建议先做 RT-003/005/022 的结构性重构，再谈功能。

## 性能与效率

**主线程 IO**（最重）：`send/retryLastTurn/steer/followUp/setSessionModel/compact` 从 Main 协程直接进入宿主（`ChatViewModel.kt:320,378,535,602,733,941`），宿主无 dispatcher 边界（RT-002）。正常路径 handle 已在时同步段很薄（`begin` 只做内存状态 + `RunLedger` 的挂起 IO 在 IO 线程）；但 handle 被回收后的重连会在 Main 上做 `ensureSystemPromptFile`（文件 exists/mkdirs/write）、`SessionManager.open().buildSessionContext()`（整个 JSONL 解析）、`createAgentSession`（skills/AGENTS/MCP 配置读取），随后 `ensureTitle → appendSessionInfo` 也在 Main 写会话文件。会话 JSONL 通常几十 KB～几 MB，冷 IO 下是几十～几百 ms 的掉帧，极端情况接近 ANR 阈值。

**锁粒度**：一把进程级 `Mutex` 覆盖六类操作（RT-003）。量化影响：任一 `attach`（建 engine + JSONL 解析）期间，其它会话的 `onSessionEvent→emitUsage→usage()` 也排队在这把锁上，而该回调在 SDK 事件分发线程执行——等于一次重连能阻塞全部会话的流式事件分发；`history()` 的 O(entries) 映射也在锁内，打开一个长会话会让其它会话发送/停止等操作等它映射完成。

**扫描/序列化热点**（按触发频率排序）：
1. `ScheduleService` 每事件一次 `schedules.json` 读+解析（RT-004）：流式时几十次/秒，纯浪费。
2. `SessionIndex` 变化文件整文件重读（RT-007）：每轮对话后至少一次，成本 O(会话字节)，且 dirty 时整写索引文件。
3. `JobPersistence` 满量重写（RT-008）：运行中后台任务时 ≤1 次/秒、最坏 ~800KB/次写 + 每运行中记录 256KB 快照拷贝 + 256KB→16KB 的 `toByteArray`。
4. `usage` 投影（RT-009）：每次 ToolEnd 一次 O(消息数) 投影 + 全局锁。
5. `RunLedger` 每轮 2 次整文件读写（RT-024）：发送前 await 写盘。
6. `SessionTitle→catalog.find`：全 cwd 扫描，调用点可能成环（RT-017）。

**分配热点**：`HistoryMapper` 的 steps 平方级复制（RT-016）、`ToolFinished` 把整段工具输出 `filterIsInstance<TextContent>().joinToString` 复制成事件字段（大输出工具一次几 MB 的额外串）、`EventBuffer` 512 容量可同时持有多个含大字符串的 `ToolFinished`、`JobRegistry.OutputRing.snapshot()/persisted()` 每次整环拷贝、`ProviderCatalog.models()` 每次重建。另 `Regex` 在 `titleFor/slug/promptPreview` 等每次调用时编译（RT-026）。

**内存驻留**：handles 上限 6 但每个 handle 是完整 engine+session+toolset（重）；`JobRegistry` 环形缓冲运行中 256KB×running + settled 16KB×50（~1MB+）常驻；`SessionCommandPlane.states` 与 `GoalService.turnSources` 无上限增长（RT-023/030）；`eventsBuffer` 在 UI 卡顿时会持有 512 个事件对象（含 ToolFinished 大字符串）；`SessionIndex.entries` 随会话数增长（正常），已删工作区的条目不会清理（`SessionIndex.kt:97-125` 仅扫描传入 cwd 的目录前缀）。

**序列化频率**：`schedules.json` 每事件读（RT-004）、`session-index.json` 每次列表 dirty 全写（RT-007）、`jobs.json` 每秒全写（RT-008）、`goals.json` 每次目标变更全写（正常，量小）、`runs.json` 每轮 2 次全读写（RT-024）、`pending.json` 每次问答追加/删除各一次全读写（QuestionPersistence 无缓存，`QuestionPersistence.kt:25-44`，量小可接受）。

## 优先修复建议

1. **RT-001（损坏数据 → 启动崩溃）**：一行 `runCatching` 到启动路径就能避免“永久启动崩溃”，修复成本最低、收益（可恢复性）最高；同时对齐所有 Store 的隔离策略，避免以后再出现同类崩溃。
2. **RT-002 + RT-003（Main 线程 IO + 全局锁）**：两者是同一结构性问题的两面，且影响面最大（卡顿/ANR、多会话互相拖慢）。先给宿主加 `ioDispatcher` 边界（M），再拆全局锁（M），风险可控且可用现有测试回归。
3. **RT-004（调度服务按事件读盘）**：改动极小（事件过滤 + Store 内存缓存），直接把流式期间的每秒几十次磁盘读降为零，属于“立刻可做、无风险”的性能修复。
4. **RT-005（事件有序性）**：这是架构承诺（“事件快速通道 + 状态真相”）的薄弱点；一旦出现“终态先到”的 UI 卡死/回光，排查成本很高。建议在重构宿主事件出口时一并解决（单消费者队列 + RunStarted 先于任何挂起）。
5. **RT-006（宿主测试）**：上述 2–5 的改造都必须有宿主级测试兜底；先补 evict→reconnect、stop/epoch、deliver 两态、事件顺序四组用例，再动结构。可与 RT-002/003 的重构交替进行（先测试后改）。〔复核：与本文第 2–4 条的编号顺序矛盾——应先做 RT-006 再动 RT-002/003/005；且 fake 接缝需先抽接口，见 RT-006 批注〕
