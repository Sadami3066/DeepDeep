# 性能与效率评审

评审对象：DeepDeep（Android + Jetpack Compose，单 Activity，Agent 运行时 + 聊天流式 UI）。
本文覆盖：启动、重建/重组、流式热路径、长会话、内存、IO 与后台任务、包体与构建产物。逐模块的细节见 `module-chat-ui.md`、`module-runtime.md`、`module-app-core.md`、`module-app-screens.md`。

本机实测：`./gradlew :app:compileDebugKotlin` 通过、`app 626 / runtime 364 / shell 32 = 1022` 个单元测试全绿。项目自身在 `docs/archive/STATUS.md` 与 `artifacts/validation/round41/` 记录了 release 冷启动 637/638/674/741ms（基线 967ms）、页面循环后 PSS 251MB（Redmi 22101316UCP / Android 13）——这些数据比"感觉快了"更有价值，本文在此基础上判断仍可优化的点。

---

## 1. 总体结论

**评分：7.4 / 10。** 性能是这个项目**主动设计过**的部分：流式 16ms 合并、Markdown 增量规划 + 块级缓存、字体 Async + 预热、抽屉/IME 在 draw 相位读值、转场冻结聊天状态、SessionIndex size+mtime 缓存、后台任务输出合并与环形缓冲——这些机制都在代码里，且 `docs/archive/STATUS.md` 有前后对照数据。剩余问题集中在三处：**根 Composable 的重组范围**、**流式/长会话的对象分配**、**发布包体（未 minify）**。

| 维度 | 分数 | 理由 |
|---|---|---|
| 冷启动 | 7.0 | release 中位 ~670ms，可用；但无 baseline profile、未 minify（31MB dex）、Application 主线程构造 73 个装配属性、`attachBaseContext` 同步读 SharedPreferences |
| 重组与帧预算 | 7.5 | strong skipping + 关键路径 draw 相位优化做得好；但根 Composable 在流式期间 ~60 次/秒重组、后台任务 10 次/秒重组 |
| 流式渲染 | 8.0 | 16ms 合并、增量 Markdown、代码高亮延后；扣分在每批次整表复制与字符串累积 |
| 长会话 | 6.5 | 全量 JSONL 解析 + 全量消息驻留，无分页（项目已承认的限制），1000+ 消息会话首开与内存线性增长 |
| 内存 | 7.0 | 三套 LRU（会话 8 个、Markdown 图 24MB、缩略图 8MB）设计合理，但图片 LRU 无 trim 入口，PSS 251MB 偏高 |
| IO/主线程 | 8.0 | 绝大多数磁盘/JSON 在 IO dispatcher；少数主线程读（SharedPreferences、字体探测）与轮询点 |
| 后台/长任务 | 8.5 | 定时器睡到下一个到期、Job 输出合并、环形缓冲、TTL 缓存、8KiB 行切分——设计正确 |
| 包体/构建产物 | 5.5 | release 40.5MB / dex 30.3MiB / 3 个 dex，minify 与资源收缩都关闭 |

---

## 2. 亮点（有代码依据）

1. **流式增量合并 16ms**（`ChatViewModel.kt:1399-1434`）：TextDelta/ReasoningDelta 进入批次，其他事件先 flush，保证工具/文本顺序；后台会话直接写缓存不触发 UI。
2. **Markdown 增量规划 + 块级缓存**：`MarkdownStreamPlanner` 只重扫末尾未闭合段，`ChatMarkdown` 按块文本缓存解析结果；流式期间代码块暂不高亮、表格暂不测量列宽，settle 后补齐（STATUS 有记录）。这是全项目最有效的性能设计。
3. **字体异步加载 + 启动预热**（`design/DdCompositeFont.kt:60-101`、`DeepDeepApplication.kt`）：自定义 `AndroidFont` + `FontLoadingStrategy.Async` + `TypefaceLoader` 在 IO 线程构建复合字体，`Application` 后台 `DdFonts.preload`；避免了 3.9MB 中文字体在首帧主线程构建。
4. **转场冻结聊天状态**：`rememberFrozenChatState`（`DeepDeepApp.kt:1150-1157`）+ `AnimatedScreenHost` 每页独立 `graphicsLayer`，进度只在 draw 阶段驱动 translationX，覆盖页打开时聊天 base 吃冻结快照。STATUS 记录覆盖 10s 帧数 1178 → 10。
5. **抽屉动画在 draw 相位读进度**（`DeepDeepApp.kt:892-914`）：遮罩 alpha 与抽屉 translationX 都在 `graphicsLayer`/`drawBehind` lambda 里读，根节点不逐帧重组。
6. **`deriveStateOf` 隔离根读取**：`paletteOpen` 用 `derivedStateOf` 读 `stateHolder.value.input`（`DeepDeepApp.kt:363`），输入框每次按键不会让整壳跟随重组。
7. **SessionIndex 按 size+mtime 缓存**（`SessionIndex.kt:110-140`）：会话列表只解析新增/变化文件，其余复用索引；损坏自动重建。
8. **后台任务输出合并 100ms + 环形缓冲 256KB/16KB + 8KiB 行切分**（`JobRegistry`、`PRootExecutor`）：后台输出不会无限增长，UI 不会被逐行更新打爆。
9. **定时器"睡到下一个到期"**（`ScheduleService.kt:317-330`）：`nextRunAt - now` 的 wait + `wake` Channel + 15 分钟兜底，而不是固定 tick 轮询；比常见实现省电。
10. **插件状态 120s / MCP 探测 60s TTL + 显式刷新**：进入插件页不重复跑 PRoot/网络探测。
11. **图片解码全部在 IO 且带 `inSampleSize`**（`MarkdownImage.kt:318-330`、`AttachmentUi.kt:551-562`、`ImageNormalizer.kt`），并有三套 LRU 缓存。
12. **长输出/无换行输出不撑大内存**：`PRootExecutor` 的 8KiB 行切分 + `LINE_CHUNK_LIMIT` 限制。

---

## 3. 问题清单

### P-01 启动路径：主线程构造整个装配图 + 两个同步 SharedPreferences 读（中）

证据：
- `DeepDeepApplication.onCreate` 直接 `graph = AppGraph(this)`（`DeepDeepApplication.kt:15-19`），`AppGraph` 有 52 个属性 + 4 个 `init` 块〔复核修正〕，构造期执行：3 个目录 `mkdirs`（`AppGraph.kt:101-103`）、`EncryptedSecretStore`/多个 Store 实例、`okHttpTransport()`（146 行）、`credentials`（147 行）、`host`（324 行，非 lazy）——全部在 `Application.onCreate` 主线程。
- `MainActivity.attachBaseContext` → `LanguagePreferences.wrap(newBase)`（`MainActivity.kt:42-44`）→ `getSharedPreferences(...).getString(...)` 同步读盘（`LanguagePreferences.kt:25-30`）。
- `MainActivity.onCreate` 再 `graph.fontPreferences.read()`（`MainActivity.kt:56`）→ 第二次同步 SharedPreferences 读盘（`FontPreferences.kt:7-9`）。
- 没有**应用级** baseline profile：`androidx.profileinstaller:1.4.0` 已作为传递依赖存在，APK 内含依赖合并的 `assets/dexopt/baseline.prof`（9,746 B）+ `baseline.profm`，合并清单含 `ProfileInstallerInitializer`；缺的是 `app/src/main/baseline-prof.txt` 与 macrobenchmark 模块。〔复核修正：原文"仓库无 androidx.profileinstaller"是误报〕
- release 未 minify：dex 合计 30.3 MiB / 3 个 dex（见 P-07）。

影响：冷启动的固定成本被抬高（Application 构造 ~几十个对象 + 2 次 SharedPreferences 首次读盘 + 31MB dex 的类加载/验证）。项目已有 637-741ms 的 release 冷启动，不算差，但以上三项都是"一次改对长期受益"的。

建议（按收益排序）：
1. release 开启 R8 minify + `shrinkResources`（P-07）。
2. 加**应用级** baseline profile（`androidx.profileinstaller` 已是传递依赖，缺的是 `baseline-prof.txt`/macrobenchmark 生成），预计冷启动 15-30% 改善〔复核：「15-30%」无依据，属行业经验值〕。
3. `AppGraph` 中不需要在启动即用的 Store 全部改 `by lazy`（现有约一半是 eager `val`），把 `host` 的构造改为显式 `start()` 阶段。
4. 两个 SharedPreferences 读合并为一次 `prefs.get()`（语言 + 字体同文件或预热），或把字体/语言读放到 `Application` 的 IO 协程，首帧用默认值、读到后 `recompose`（当前 `fontStyle` 是 setContent 外读的，改动需注意首帧字体切换闪动）。

### P-02 根 Composable 是重组热点：流式 ~60 次/秒、后台任务 10 次/秒（重要）

证据：
- `DeepDeepApp` 在 body 直接读 `state`（`DeepDeepApp.kt:217-218`）并收集 19 个 `StateFlow`（217-306 行）。
- 流式 delta 每 16ms 合并一次（`ChatViewModel.kt:1399-1434`）→ `ChatUiState` 每 16ms 更新一次 → `DeepDeepApp` 每 16ms 重组一次（约 60 次/秒）。
- 后台 job 输出 100ms 合并 → `jobRegistry.jobs` 更新 → 根节点 10 次/秒重组（`DeepDeepApp.kt:294`）。
- Compose 编译器报告：`DeepDeepApp` 有 94 个 call site，函数体约 1,003 行（文件 1,159 行）〔复核修正〕；strong skipping 保证子节点不重复执行，但函数体本身（19 个 flow 的 `.value` 读取、13 个 `remember`、30+ lambda 构造、派生过滤）每次都要跑。
- `activeQuestions`/`foreignQuestion`（`DeepDeepApp.kt:290-291`）、`reminderSessionIds`（921 行）未 `remember`，每次根重组重新过滤/分配。

影响：每帧多出的工作不一定掉帧（子节点跳过），但会消耗主线程预算、增加 GC 压力；在低端机/长会话流式时与 Markdown 解析争用同一主线程，是"流式时滑动/点击不跟手"的候选原因。

建议：
1. 把 `ChatScreen` 及其所需状态包进子 Composable，根只传 `callbacks + 少量稳定状态`；或让 `ChatScreen` 直接接收 `State<ChatUiState>`（`stateHolder`）在内部读——这样 delta 只重组 chat 子树。
2. 4 个 Sheet（Usage/Jobs/Files/Voice）与 3 个 Dialog 拆成各自 Composable，各自 collect 需要的 flow，不再由根读取。
3. `activeQuestions`/`reminderSessionIds` 用 `remember(pendingQuestions, conversationId)` 派生。
成本 M，收益直接（根重组从 60/s 降到 0/s）。

> 〔复核降级〕该结论不成立：`DeepDeepApp` 在 220/342/360/506/512/524 等处仍无条件读取 chat state，只把 `ChatScreen` 包一层不会减少 root 重组；要接近 0/s 必须整体重构 1,159 行 root，成本为 L，且项目已有冻结快照 + `remember`/`derivedStateOf`，round41 实测流式 4.17% janky，收益未被验证。建议只做「Sheet/Dialog 独立 Composable」这一低风险子项。

### P-03 流式热路径的整表复制与字符串累积（重要）

证据（`ConversationReducer.kt`）：
- 每个 TextDelta/ReasoningDelta 都会 `messages.toMutableList()` 复制整个消息列表，再 `steps.toMutableList()` 复制步骤列表，再 `AssistantMessage.copy`（78-98、273、292、305 行等）。16ms 批次下一批多个 delta，`applyPendingDeltas` 逐条 reduce（`ChatViewModel.kt:1425-1434`），每条都走一次 O(N)。
- 文本累积 `last.text + event.text`（86、94 行）：每次拼接复制当前流式文本全长，长回复（如数万字代码）时是 O(n²) 字节复制。
- `onSessionEvent` 在 `viewModelScope`（Main）里跑 reduce（`ChatViewModel.kt:104`），Markdown 尾部重解析也在主线程 composition 中。

影响：长会话（N 条消息）流式时，每 16ms 做 O(N) 列表拷贝 + O(当前文本长度) 字符串拷贝；这是"流式长回复越到后面越卡"的典型机理，也是 GC 抖动来源。

建议：
1. 流式文本改为 `StringBuilder`（或按段落存 chunk 列表，UI 侧拼接渲染），避免每 delta 全量字符串复制；
2. reduce 只复制受影响下标（`ArrayList` 原地修改 + 递增 revision，或 `PersistentList`/immutable 列表结构）；消息对象本身不可变不变；

> 〔复核勿做〕「`ArrayList` 原地修改 + revision」会破坏 `ConversationReducer` 的纯函数/不可变契约（这是项目测试基础与设计亮点），可能引入跨 run 状态污染；如需优化，只保留批内 `StringBuilder` 合并（建议 1/3），不动 reducer 契约。
3. 批次内先合并同一 runId 的所有 TextDelta 文本，再一次 reduce（当前是逐条 reduce，虽然在同一 16ms 窗口内，但仍是 N 次列表复制）。

### P-04 长会话打开成本与内存（重要，项目已知）

证据：打开会话走 `AgentRuntime.history()` → `PiSessionEngine.history()` 读 JSONL 全量 + `HistoryMapper` 聚合（`runtime/HistoryMapper.kt`）；`ChatViewModel` 把完整消息列表放进 `conversationCache`（LRU 8，`ChatViewModel.kt:88-89`、`1279-1290`）。STATUS 明确记录"长会话首屏仍受全量 JSONL 解析 + 历史映射限制（分页已按用户要求移除）"。

影响：1000+ 条消息的会话，打开时线性解析全文件 + 全量消息驻留（8 个会话）→ 首开慢、内存高；进程重启后每个已缓存会话都要重读。

建议：不加分页 UI 的前提下，可做"首屏窗口 + 后台补全"：先用 `session-index` 已有的 header/`session_info` + 文件尾部 N KB 快速渲染最后若干条，再异步 `history()` 全量替换（对用户表现为"先看到最近对话"）；或给 JSONL 配 sidecar 消息偏移索引，二次打开直接 seek。成本 M-L，建议与用户确认交互预期后再做。

### P-05 全局状态集中在根节点（中）

19 个 `collectAsStateWithLifecycle` 覆盖：credentials/authKinds/revision、默认模型、schedules、linuxState、devPackages、pluginsState、voice 偏好、ui 偏好、pendingQuestions、goals、jobs、personalization 等（`DeepDeepApp.kt:245-306`）。这些状态中任何一个变化（插件 TTL 刷新、linux 安装进度、语音偏好切换、目标轮次更新）都会让根 Composable 走一遍。与 P-02 同源，作为独立条目是因为修复方式不同：**按屏下沉 collect**，而不是只拆 ChatScreen。

建议：设置相关、插件相关、工坊相关的 flow 只在对应页面 collect；根只保留导航（navigator）、抽屉开关、全局会话状态（`ChatUiState` 的少量字段）。

### P-06 列表与派生计算（中）

- `FileBrowserSheet` 用 `Column + verticalScroll + forEachIndexed` 渲染目录（`FileBrowserSheet.kt:100/119`）：目录内所有文件行一次性组合，无窗口复用。workspace/home 目录可能包含数百个附件/产物文件。
- `DeepDeepApp` 每次根重组重建 `reminderSessionIds`（`DeepDeepApp.kt:921`，`schedules.filter{}.mapTo(mutableSetOf())`）与 `activeQuestions` 过滤（290-291）。
- `ChatViewModel.refreshSessions()` 每次 `SessionListChanged` 都做 3 次串行 IO：`workspaces()`、`allSessions()`、`archivedSessions()`（`ChatViewModel.kt:1470-1496`）；而后两者底层各是一次 `catalog.listAll()`（`AgentRuntimeHost.kt:172-173`、`563-564`），即每次刷新 **两遍全目录扫描 + 两次 index persist**。`SessionListChanged` 由每轮运行结束（`AgentRuntimeHost.kt:471`）、重命名、归档、删除等触发。
- `SessionIndex.listAll` 在 `mutex.withLock` 内 `persist()`（`SessionIndex.kt:79-96`），扫描+写 JSON 串行。

影响：有数百会话时，每轮对话结束都做两次目录扫描（内存中索引命中所以主要是 `listFiles` + JSON 序列化），单次成本可控但频次高；FileBrowser 在附件多的目录会一次组合几百行。

建议：`AgentRuntime` 增加 `sessionSnapshot()` 一次返回 workspaces + 非归档 + 归档；`SessionIndex.persist()` 防抖（如 1s 合并）；FileBrowser 改 `LazyColumn`（列头/分隔线用 item 即可）。成本 S。

### P-07 发布包体：39MB APK / 31MB dex，未 minify（中高，取舍相关）

证据：`app/build.gradle.kts` 的 `release` 只有 `signingConfig = debug` + `isMinifyEnabled = false`，无 `isShrinkResources`；实测 `app-release.apk` 40.5 MB（40,547,437 B），其中 `classes.dex 14.2 MB + classes2.dex 10.1 MB + classes3.dex 7.5 MB = 30.3 MiB`（dex 在 APK 内未压缩存放），res 5.4 MB，assets 4.2 MB（含 4.0 MB alpine rootfs；中文字体 4.08 MB 在 `design/src/main/res/font/`）。

影响：3 个 dex 增加安装后首次类加载与 ART 验证成本，也让冷启动（P-01）和更新安装更慢；本地分发场景下"包大"本身不是问题，但它直接参与启动时间。R8/资源收缩是 Android 上性价比最高的启动优化之一。

建议：release 打开 `isMinifyEnabled = true` + `isShrinkResources = true`，补最小 keep 规则（项目未用反射型 DI/序列化框架，主要需保留的是 Compose 与 SDK 的 keep 规则；先跑 `assembleRelease` + 真机回归一轮）。若坚持不混淆，至少开 `isShrinkResources`。签名继续用 debug keystore（项目有意，覆盖安装）。〔复核：与 `build-tests.md` GL-006「换专用 keystore」的建议互斥；换签需卸载重装且 `allowBackup=false`，会丢全部本地数据，详见 `REVIEW_OF_REVIEW.md`〕

### P-08 图片 LRU 无内存压力清理入口（中）

证据：`MarkdownImage.kt:225` 24MB Bitmap LRU、`AttachmentUi.kt:534` 8MB 缩略图 LRU，均为进程级单例、无 `evictAll`/`trimToSize` 入口；`DeepDeepApp` 已有 `ComponentCallbacks2` 回调（`DeepDeepApp.kt:225-239`）但只转发给 `chatViewModel.onTrimMemory`，后者只清空闲会话的消息缓存（`ChatViewModel.kt:632-643`）。

影响：系统内存压力时（尤其 MIUI 后台），几张长图/多个附件缩略图会一直占住 32MB 上限，可能触发更激进的进程回收；项目实测 PSS 251MB 偏高，图片缓存是一个可预测的部分。

建议：两个 LRU 暴露 `trim()`（`trimToSize(maxSize/2)` 或 `evictAll`），在 `onTrimMemory(RUNNING_LOW/CRITICAL)` 调用；`onLowMemory` 也接一下。成本 S。

### P-09 热点路径的正则/解析重复构造（低）

证据：`ChatViewModel.quoteExcerpt` 每次调用 `Regex("[ \\t]+")`（`ChatViewModel.kt:1496-1503`）；`userFacingError`/`titleFrom`/`localizedTitle` 等路径也有内联正则与字符串扫描；`Character` 判断多。单次成本微秒级，但引用属于用户高频操作（每次长按引用一条消息）。

建议：提为文件级 `private val` 常量。成本 S，无风险。

### P-10 JobsSheet 输出轮询（低）〔复核降级：仅 JobsSheet 打开且选中运行中任务时发生，1.25 次/秒〕

`DeepDeepApp.kt:1052-1061`：抽屉外的 JobsSheet 打开且选中任务时，`while (true) { jobRegistry.read(id); delay(800) }`。`JobRegistry` 本来就有 `jobs` StateFlow（100ms 合并发布），输出可以随快照发布或提供 `outputFlow(id)`。影响：单任务 1.25 次/秒全量输出读取与字符串分配；属于设计一致性（架构 A-07）与效率双重问题。建议：改为 flow 订阅。成本 S。

### P-11 Compose 报告与稳定性配置的可持续性（低）

- `app/compose-stability.conf` 用 `io.github.whywood.deepdeep.runtime.*` 整包声明 stable（4 行配置），当前 runtime 里都是不可变 data class/接口，成立；但将来在 runtime 包放可变类型会被静默当成 stable，掩盖问题。
- 本次强制全量编译后统计：224 个 Composable，26 个不可跳过（都是 `*Label`/`remember*` 辅助函数，属正常），61 个 unstable class（Store/Service/Manager/WebView 状态类为主，作为 Composable 参数出现时靠 strong skipping 兜底）。
- 增量编译会覆盖 `compose-reports`（本次初始只包含 12 行增量数据），不能作为趋势指标。

建议：把 wildcard 收敛为显式类列表；如要把跳过性作为回归指标，在 CI/手动脚本中 `--rerun-tasks` 全量生成并对比数量。

### P-12 构建期效率（低）

- `gradle.properties`：`org.gradle.parallel=true`、`caching=true`、`workers.max=2`、Kotlin daemon 768m、Gradle 1.5g——配置合理但偏保守；全量重编译（4 模块）在本机约 1-2 分钟量级。
- `:app` 单模块 37K 行 + 82 个 Compose 文件，无 KSP/kapt，增量编译表现正常。
- 没有 `lint`/静态检查挂到构建，`./gradlew check` 只跑测试；lint 在 STATUS 中是手动跑过的（0 errors / 160 warnings）。
- 建议：CI 或提交前脚本固定跑 `assembleRelease` + `test` + `lint` + `verifyModuleBoundaries`（见 `build-tests.md`）；构建期不是瓶颈，不建议过度调参。

---

## 4. 优先级建议

| 优先级 | 项 | 预期收益 | 成本 |
|---|---|---|---|
| 1 | P-02/P-05 根节点重组范围收敛（拆 ChatScreen/Sheet/Dialog，按屏 collect） | 流式时主线程预算显著下降，低端机跟手性改善 | M |
| 2 | P-03 流式 reduce 的对象分配与字符串累积 | 长回复/长会话流式帧稳定，GC 下降 | M |
| 3 | P-07 release 开 R8 + 资源收缩 | 包体 -30~40%，冷启动改善 | S-M |
| 4 | P-01 baseline profile + 装配懒加载 + SharedPreferences 合并 | 冷启动 10-30% | S-M |
| 5 | P-04 长会话首屏窗口/侧索引 | 大历史会话可感知变快；需产品决策 | M-L |
| 6 | P-06 列表/派生/会话扫描合并 | 抽屉与文件浏览延迟下降 | S |
| 7 | P-08/P-09/P-10 | 内存压力、热点正则、轮询 | S |

不建议现在做的：把 Compose 换成视图层、给所有 Composable 加 `@Immutable`、为跑分引入额外缓存层——当前瓶颈不在这些地方。

---

## 5. 结论

性能优化的"正确方向"这个项目基本都走对了：先量化（STATUS/artifacts 有帧与启动数据）、再针对机制改（draw 相位、增量解析、冻结状态）、最后才谈微优化。剩下的性能问题不是"哪里都慢"，而是三个明确的结构性热点：根 Composable 的重组（P-02/P-05）、流式与长会话的内存/CPU 线性成本（P-03/P-04）、以及发布构建没有做裁剪（P-07）。前两项是长会话体验的上限所在，第三项是冷启动和包体的一次性红利；三者都可增量推进，不需要推倒重来。
