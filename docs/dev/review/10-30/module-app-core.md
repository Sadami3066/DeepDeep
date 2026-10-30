# App 核心（装配/生命周期/导航/数据/服务）评审

## 概览

范围：`DeepDeepApp.kt`、`AppGraph.kt`、`MainActivity.kt`、`DeepDeepApplication.kt`、`assistant/`、`ui/navigation/`、`ui/DeepDeepDrawer.kt`、`data/`（19 文件）、`service/`（8 文件）、`deeplink/`、`voice/`，以及 data/navigation/drawer/service 相关测试（约 6,600 行主代码 + 约 1,900 行相关测试）。已通读 `docs/dev/ARCHITECTURE.md` 全文（290 行），代码与文档不一致处按代码判定并单列。

总体判断：**数据层与自研导航是本模块最扎实的部分**（原子写、zip 加固、路径校验、routeKey + SaveableStateHolder + PredictiveBack 的状态恢复语义完整）；问题集中在**装配顺序的隐式性**（AppGraph 4 个 `init` 块 + `prepare()` + `AgentService` 三条启动路径互相无显式等待）、**1159 行壳层 Composable 的职责与重组范围**、以及**冷启动主线程/串行 IO**。未发现 P0；发现 1 个 P1（升级首启迁移与首会话恢复并发）、11 个 P2、4 个 P3。

无法验证项：`app/src/main` 无 instrumented/UI 测试工程可对照，启动耗时均为代码路径推演 + 量级估算（未实测），文中标注为“估算”。

## 评分

| 维度 | 分数 | 理由 |
| --- | --- | --- |
| 架构质量 | 6.5 | 分层与数据层设计好（store/service/纯函数分层、原子写、状态流单一来源）；但手动 DI 图的初始化顺序靠“声明位置 + 多个 init 块 + lazy”隐式维持，启动分三路（Application/Activity/Service）无阶段边界；壳层直接持有 19 个服务 StateFlow、无 screen state holder；AppGraph 具体类 + 全局 `OAuthRuntime` 改写导致装配不可注入。 |
| 代码质量 | 7 | data/service 层错误处理一致（CancellationException 透传、IO 失败降级、原子替换）；导航/抽屉抽出了可单测纯函数。扣分在 `DeepDeepApp` 1159 行/9 类职责、`buildList`+大段状态声明；抽屉行自绘点击缺无障碍语义；部分注释/文档与实现漂移（迁移时机、DataStore 清单）。 |
| 性能与效率 | 6.5 | 重活基本已挪到 IO（prepare/AgentService/字体预热/技能扫描均在后台），转场/抽屉用 graphicsLayer 与 draw-phase 读值；但壳层顶层读 ChatUiState 导致流式时整壳重组、`prepare()` 串行链拖慢首会话恢复、冷启动仍有少量主线程同步 IO 与主题首帧闪烁。 |

## 亮点

1. **导航状态恢复语义完整**：`rememberSaveable` 保存 routeKey 栈（`AppNavigator.kt:36-46`），页面用 `SaveableStateProvider(routeKey)` 隔离，出栈动画结束后才 `removeState`（`AppScreenTransition.kt:142-173,224-235`），PredictiveBack 手势期间预组合返回目标页并以 `previewFraction` 驱动共享轴位移、取消时回弹（`AppScreenTransition.kt:94-140`）。这比多数自研导航完整。
2. **数据写入普遍原子化**：技能目录“临时目录 → 备份 rename → 激活 → 清备份”（`SkillRepository.kt:299-342`），AGENTS.md 写入同样临时文件 + rename（`AgentDocStore.kt:227-240`），失败路径保留旧版本。
3. **zip 导入防炸弹/防穿越**：32MB 归档 / 64MB 解压 / 1000 条目上限，拒绝加密条目、`..`、绝对路径、盘符（`SkillArchive.kt:64-67,172-228`），并对“无标记同名目录认领”保留用户版本（`SkillRepository.kt:192-204`）。
4. **附件导入的限额与空间检查**：大小上限、`usableSpace` 余量（含 64MB margin）、图片像素上限、流式复制超限删除半成品（`AttachmentStore.kt:39-71,188-213`；`ImageNormalizer.kt:31-57`）。
5. **语音资源生命周期完整**：`TextSpeaker` 所有 TTS 回调 post 回主线程、`release()` 幂等并 shutdown（`TextSpeaker.kt:62-74,157-176`）；录音回调流在 `awaitClose` 中 cancel+destroy（`SpeechRecognizerController.kt:254-283`）；Sheet 用 `DisposableEffect` 释放（`VoiceAssistantSheet.kt:173-179`）。
6. **抽屉的廉价动画实现**：持久 composition + `graphicsLayer.translationX`、遮罩仅可见时挂载、展开集合/列表条目由纯函数 `buildDrawerEntries` 生成并可单测（`DeepDeepApp.kt:321-330,892-914`；`DeepDeepDrawer.kt:103-136`；`ui/DeepDeepDrawerEntriesTest.kt`）。

## 问题清单

### P1

**[AC-001] 存储迁移异步启动且无人等待，与首个会话恢复并发**（P1）· `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:97`
- 证据：`init { scope.launch { StorageMigration.migrate(appContext.filesDir) } }`（AppGraph.kt:96-99）在 `CoroutineScope(SupervisorJob()+Dispatchers.IO)` 上“发射后不管”；同一进程里 `prepare()`（AppGraph.kt:377-389）只 `await` 了 `LegacyWorkspaceMigration`，从未 `await` StorageMigration；而 ChatViewModel 在 `prepare()` 返回后立即 `restoreInitialSession()`（`ChatViewModel.kt:136-145` → `1026-1053`），逐工作区 `runtime.sessions(...)` 读会话目录。`StorageMigration.migrateSessions()` 对每个会话文件 `readLines()` 全量读入、按需 `writeText()` 重写整个 JSONL、再 `renameTo` 迁移目录（`StorageMigration.kt:40-74`），这些都在 IO 线程上与上述会话读取并行。
- 影响：升级后首次启动时（需重写全部会话 cwd），`restoreInitialSession` 可能在目录尚未改名时查询 → 会话列表为空 → `recent == null` → **直接新建一个空会话并展示为最近会话**，用户可见“历史不见了”（数据实际未丢，下次启动恢复）；重写用非原子 `writeText`，若会话在重写窗口内被 SDK 打开（SDK 会对坏尾做备份+截断）或进程在写盘中被杀，存在历史截断/丢失风险（后半句为推测，窗口小但机制成立）。

> 〔复核〕范围要收窄：「首启历史不见」只在**需要重写 cwd 的跨版本升级**成立（`.storage-migrated` marker 会让已迁移数据只做只读扫描，日常增量升级无此竞态）；实际更常见的是「打开较旧/空会话」。坏尾截断子场景也比描述窄——列表只扫已改名的新目录，可见文件已写完；更现实的是进程在 `writeText` 中被杀。建议本身合理，但 P1 对日常升级偏重。
- 附带文档漂移：`docs/dev/ARCHITECTURE.md:5` 声称“数据迁移在 AppGraph 初始化前执行”，与实现相反。
- 建议：把迁移变成启动阶段的显式前序——在 `Application.onCreate`/`prepare()` 起点的 `withContext(IO)` 里首先 `StorageMigration.migrate(...)` 并等其完成（`host.start()`、`restoreInitialSession` 都排在它之后）；重写会话文件改为“临时文件 + rename”原子替换；marker 用原子写。工作量 M。

### P2

**[AC-002] 初始化顺序隐式且分散，插件启动/重配置与首个会话创建、提醒投递无同步**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:361`
- 证据：启动共有三条无相互等待的路径：(a) AppGraph 的 4 个 `init` 块——迁移（96-99）、`appPreferences` 收集（126-130）、`linuxSettings` 收集（131-137）、`pluginManager.start(); host.reconfigure()`（361-375）；(b) `prepare()`——question 恢复、`host.start()`、中断通知、legacy 迁移、`credentials.preloadAll()`（377-389）；(c) `AgentService.onCreate`——`ProcessExitLog`、`host.ensureStarted()`、`scheduleService.start()`（`AgentService.kt:36-40`）。`engineFactory` 创建会话时读取 `pluginManager.promptBody()`/`tools()`（AppGraph.kt:309-322），而 `PluginManager.tools()/promptBody()` 读的是 `_state`，在 `start()` 完成前为空（`PluginManager.kt:93-101,353-361`）；`scheduleService.start()` 会在启动时 `recoverPending()/deliverDue()` 立即投递到点提醒（`ScheduleService.kt:48-68`），该轮会话可能拿到空插件工具/提示。`reconfigure()` 只对调用瞬间 `handles.keys` 内的会话重建（`AgentRuntimeHost.kt:673-680`），窗口内新建的会话不会被补齐。
- 影响：启动即到点的提醒、或极快恢复的会话，可能缺 schedule/goal/job 工具与插件系统提示一轮；表现为“Agent 突然不会用定时任务/目标工具”，直到下次配置变更才恢复。属低频但难排查的用户可见能力缺失。

> 〔复核修正〕「直到下次配置变更才恢复」不成立：问题会话在 `handles` 快照内会被 `reconfigure()` 重建，而 `reconfigure()` 紧跟 `start()` 执行；实际残留只是 start 窗口内已投递/已开始的那一轮提醒缺工具，影响从「持续缺失」降为「最多一轮」。
- 建议：引入显式启动阶段（如 `suspend fun bootstrap()` 依次 await：迁移 → pluginManager.start() → host.start() → 通知恢复 → credentials 预热 → 释放 `ready`），`AgentService` 的 schedule 起点放在 `bootstrap` 之后；`pluginManager` 暴露 `awaitStarted()` 供 engineFactory 等待。工作量 M。

**[AC-003] 壳层顶层读取 ChatUiState，流式增量驱动整个 DeepDeepApp 重组**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:217`
- 证据：`val stateHolder = chatViewModel.state.collectAsStateWithLifecycle(); val state by stateHolder` 位于 1159 行 Composable 的顶层作用域，随后 19 个 `collectAsStateWithLifecycle`、`commandItems`、`remember` 派生、`buildList` 全在该作用域内读取/使用；流式文本按 ≤16ms 合批（文档 ARCHITECTURE.md:214），即最高约 62 次/秒发射，每次都使整个 `DeepDeepApp` 重组作用域失效。`rememberFrozenChatState`（1150-1157）在翻页后只冻结 `ChatUiState` 的**数据**，不缩小重组范围；`AnimatedScreenHost` 的 `base`/`content` lambda 在 `DeepDeepApp` 内定义且捕获 `state`（如 604-608、697、825），覆盖页（设置、归档页）内容也会随聊天增量被重新调用。抽屉参数同理（`reminderSessionIds` 每次重建 `mutableSetOf()`，DeepDeepApp.kt:921）。
- 影响：流式输出期间即使当前停留在设置/技能/归档页或抽屉打开，主线程仍承担壳层（含 1159 行函数体、19 个 flow 读、派生计算）逐帧重组，低端机上会出现额外掉帧风险；`NavigationChrome` 只隔离了导航状态读取（1138-1148），没有隔离聊天状态。
- 建议：把 `ChatUiState` 读取下沉到 `base` 子树（`base` 已接受 `frozen` 参数，可改为在 `ChatScreen` 包裹层内直接 `collectAsState`），壳层只通过 `derivedStateOf` 读取它真正需要的少数字段（会话数、当前 id、busy 等）；非聊天页面参数用 `remember(state.xxx)` 锁定。工作量 M。

**[AC-004] 1159 行单 Composable 承担 9 类职责，无 screen state holder**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:120`
- 证据：单函数内同时负责：ViewModel 装配与 40+ 条本地化字符串（136-216）、附件选择/预览（241-282）、导航（283）、抽屉与工作区菜单（287-330,892-995）、分享/deeplink/会话打开三个启动副作用（337-359,506-543）、命令面板与分发（368-501）、全部页面路由（676-890）、10 个 Sheet/Dialog（1030-1120）；合计 17 个 `by remember`、37 个 `remember`、14 个 `LaunchedEffect`、19 个 `collectAsStateWithLifecycle`。所有页面回调（导航、服务调用、Toast）都内联在此，页面层无法静态预览或用 fake 回调测试壳层。
- 影响：改任何一个页面接线都要动同一文件，合并冲突与回归面大；没有独立的 shell state holder，职责无法单测（目前壳层测试仅为导航/抽屉纯函数）。
- 建议：按职责切分——`DeepDeepShell`（导航+抽屉+overlay 宿主）/ `ChatHost`（ViewModel 装配与聊天状态）/ `DeepLinkIntake`（三个 intake 副作用）/ `OverlayHost`（Sheet/Dialog）；页面参数集中成 per-route 数据类。工作量 L。

**[AC-005] 冷启动主线程同步 IO 与大量 eager 构造**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:42`
- 证据（全部发生在主线程）：`attachBaseContext` 里 `LanguagePreferences.wrap` 首载 SharedPreferences（`LanguagePreferences.kt:37-43`）；`MainActivity.onCreate` 再 `graph.fontPreferences.read()` 首载第二个 SharedPreferences（`MainActivity.kt:56`，`FontPreferences.kt:9`）；`AppGraph` 构造里 3 次 `mkdirs()`（AppGraph.kt:101-103）、`LinuxEnvironment` 构造时 `LinuxRootfsInstaller` 初始化即 `MutableStateFlow(quickState())` 同步 stat+读 rootfs marker（`LinuxRootfsInstaller.kt:27,119-131`）、9 个 DataStore 实例化（`AppGraph.kt:105-114,138-139,159,161`，DataStore 构造本身无 IO）、`okHttpTransport()` 与 ~30 个 store 对象。
- 影响（估算）：两个 prefs 首次 XML 解析各 1-3ms、marker stat+read <1ms、mkdirs <1ms、对象构造数 ms，合计约**数 ms 到 ~10ms 级**占用冷启动关键路径；远不到 ANR 级，但属于可消除的同步盘 IO。真正的 DataStore 文件读取都在 IO（首次 composition 的 19 个 collect 触发约 7-9 个 DataStore 文件异步读）。
- 建议：字体/语言选择改用一份带内存首选项缓存的存储（或 DataStore + 启动时一次异步读后 `remember`，首帧用缓存值）；〔复核勿做：语言在 `attachBaseContext` **必须同步读** locale，异步化不可行；主题/字体再落一份同步副本等于在 A-05 批评的多套持久化上再加一套并引入双写不一致。实际收益仅数 ms。〕`LinuxEnvironment.quickState` 改为懒初始化/后台刷新（现已有 `refreshState()`）；DataStore 实例尽量保持惰性。工作量 S。

**[AC-006] `prepare()` 串行链拖慢首会话恢复**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:377`
- 证据：`ChatViewModel.init` 先 `withContext(ioDispatcher) { prepareRuntime(); runtime.ensureStarted() }`，之后才 `resolveConfig → save → restoreInitialSession()`（`ChatViewModel.kt:136-145`）。`prepare()` 内部严格串行：question 恢复读文件（377-380）→ `host.start()` 读 `tasks/runs.json` 并把残留清空（`AgentRuntimeHost.kt:99-117`；`RunLedger.kt:59-65`）→ 中断通知构建 → `LegacyWorkspaceMigration`（workspaceStore DataStore 读 + 可能的旧 `projects` DataStore 读，`LegacyWorkspaceMigration.kt:16-43`）→ `credentials.preloadAll()`（secrets DataStore 全量读 + 每个已配置 provider 一次 Android Keystore 解密，`SdkCredentialStore.kt:59-71`；`EncryptedSecretStore.kt:48-66`）。
- 影响（估算）：Keystore 首次解密/密钥初始化在许多机型上是 10-100ms 量级，叠加 3-4 次 DataStore/文件读，首会话内容出现时间被推迟数十到上百 ms〔复核：未实测，Keystore 10-100ms 是机型上界；`question` 恢复有 Boolean 短路、`LegacyWorkspaceMigration` 在旧文件不存在时直接 return，正常启动无这些读。整体可能被高估，P2→P3 可选〕；首帧不受阻（异步），但“可交互历史”变慢。且这些工作彼此独立、可并行。
- 建议：`prepare()` 内用 `coroutineScope { async }` 并行 question 恢复 / legacy 迁移 / credentials 预热；`restoreInitialSession` 不必等 credentials 预热（模型配置解析失败可重试）；`RunLedger` 清理可在 host 启动后后台做。工作量 M。

**[AC-007] 主题首帧恒按 SYSTEM 渲染，读盘后切换造成可见闪变**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:58`
- 证据：`val theme by graph.themePreferences.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)`——`ThemePreferences.theme` 是 DataStore flow（`ThemePreferences.kt:12-27`），首帧必然走 `SYSTEM`；用户设置 LIGHT/DARK 且与系统不同时，首帧后异步读到真实值再切换。窗口背景由主题 XML 决定（`res/values-night/styles.xml`），Compose 内容会先出现一帧错误配色。
- 影响：每个冷启动一次可见闪变（深色用户设浅色或反之），属启动体验问题；无功能影响。
- 建议：像字体一样把主题选择写一份同步可读的轻量缓存（SharedPreferences/小文件一行），首帧用作 initialValue，DataStore 仍为权威；或用 SplashScreen 保持到首次真实值到达。工作量 S。〔复核：主题同步缓存会引入双写不一致（DataStore 权威 + 缓存副本），SplashScreen 方案更稳；且闪变只有 1-2 帧，P2 略重。〕

**[AC-008] 技能扫描在每次回前台/每轮运行结束全量 listFiles+stat，成本随工作区线性增长**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:206`
- 证据：`activeSkillsFingerprint()` → `activeSkillDirectories()`（SkillRepository.kt:206-233）：global 技能目录、`home/.pi/skills`、`workspacesDir` 下每个工作区的 `.pi/skills` 各一次 `listFiles()`，再对每个技能目录 `SKILL.md` 做 `lastModified()`+`length()`（2 次 stat）。触发点：每次 `MainActivity.onStart`（`MainActivity.kt:104`）与每轮 `RunFinished/RunFailed`（`AppGraph.kt:366-374`），外加技能页进入（`SkillsScreen.kt:110,123` 其中一次 force）。
- 影响（估算）：10 个工作区 × 3 技能 ≈ 4+10 次 listFiles + 60-90 次 stat，几 ms 到十几 ms（在 IO 线程，不阻塞主线程），但它是**固定频率的全量目录遍历**，且每个工作区越用越多时线性增长；同时指纹只含 `SKILL.md` mtime+size，同秒同大小修改会漏检（P3 级）。
- 建议：改用目录 mtime 聚合作为快速指纹（或维护一个“技能变更标记”文件）；非 force 扫描设最小间隔（如 30s）+ 与 RunFinished 事件合并/防抖；后台任务跑完不必立即扫。工作量 S。

**[AC-009] 抽屉可点击行自绘手势，缺少无障碍 onClick 语义**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:459`
- 证据：工作区行与会话行用 `pointerInput { detectTapGestures(onTap/onLongPress/onPress) }` 实现点击与按压 wash（`DeepDeepDrawer.kt:459-476`、`687-704`），没有 `Modifier.clickable`/`semantics { onClick(...) }`，也没有 `role`/`onClickLabel`；其余行（导航项、账户行）用的是 `clickable`（如 632、768）。
- 影响：TalkBack/无障碍服务无法把“展开工作区”“打开会话”作为可点击动作暴露，键盘/外接设备也只能靠触摸坐标；这是 drawer 里最高频的两个交互。
- 建议：改用 `Modifier.clickable(interactionSource = interaction, indication = null, onClickLabel = ...)`（保留自绘 wash 与长按）或显式 `semantics { onClick(label, action) }`。工作量 S。

**[AC-010] NotificationCenter 的前台状态映射跨线程无同步**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:45`
- 证据：`active` 是普通 `LinkedHashMap`（45-46），全部由 `Dispatchers.Default` 上的事件收集协程写入（54-55, 191-233）；`foregroundNotification()` 在主线程被 `AgentService.onCreate` 调用并遍历（`AgentService.kt:33` → `NotificationCenter.kt:74-104`）；`stop()` 在主线程 `scope.cancel()` 后立即 `active.clear()`（59-67），而协程取消不是即时的，正在执行的 `onEvent` 仍可能写 map。
- 影响：极端时间窗下主线程/收集线程并发读写同一 LinkedHashMap，可能 `ConcurrentModificationException`（主线程抛异常 = 崩溃）或漏读一次状态；窗口小但机制成立，属正确性隐患。

> 〔复核降级〕需服务重建瞬间恰好有事件并发，概率极低；结论技术上正确，但 P2 偏高（收敛到单一执行上下文仍值得做，成本低）。
- 建议：把 active 状态收敛到单一执行上下文（全部 `withContext(Dispatchers.Main.immediate)` 或专用单线程 dispatcher），或改成 `MutableStateFlow<Map>` 用 CAS 更新；`stop()` 用 `collector?.cancelAndJoin()` 后再清理。工作量 S。

**[AC-011] DI 图是具体类且不可注入，启动装配路径无测试**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:89`
- 证据：`class AppGraph(context: Context)` 无接口/无构造注入；`DeepDeepApp(graph: AppGraph)` 直接消费具体类型（`DeepDeepApp.kt:122`），所有页面与 Shell 都无法用 fake graph 装配或预览；`init` 中改写进程级全局 `OAuthRuntime.callbackServerFactory`（AppGraph.kt:98）；`prepare()` 用普通 `Boolean questionRecoveryLoaded` 做一次性守卫（94, 378-383），并发调用无保护且后半段（legacy 迁移/preloadAll）每次都重跑。测试现状：data 层覆盖好（`AgentDocStoreTest`、`AttachmentCodecTest`、`AttachmentStoreTest`、`DevPackageCatalogTest`、`SharePayloadTest`、`SkillArchiveTest`、`SkillRepositoryTest`、`StorageMigrationTest`）、导航/抽屉/通知只测纯函数（`AppNavigatorTest`、`AppScreenTransitionMotionTest`、`DeepDeepDrawerEntriesTest`、`NotificationCenterTest`），但 AppGraph 装配顺序、`prepare()`、`MainActivity.handleIntent` 解析、`AnimatedScreenHost` 的 SaveableState/PredictiveBack 行为均无测试。
- 影响：启动顺序类回归（本次 AC-001/AC-002）只能靠真机发现；壳层改动无法用测试兜底。
- 建议：抽 `AppDependencies` 接口/数据类承载外部可替换项（Context、IO dispatcher、Clock、各 store 接口），AppGraph 提供默认实现；`prepare()` 改成串行阶段函数并加幂等锁（`Mutex`/`AtomicBoolean`）；把 `handleIntent` 的解析抽成纯函数（`MainActivity` 只做状态合并）以便单测。工作量 M。

**[AC-012] 通知/深链指向不可见会话时静默失败，pending 永不消费**（P2）· `app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:512`
- 证据：`LaunchedEffect(openSessionId, state.ready, state.conversations, state.archivedConversations)` 中，找到会话才执行打开并 `onOpenSessionConsumed()`；找不到则直接 return，`pendingSessionId` 保持非空（512-522）。`MainActivity.handleIntent` 对通知点击只注入 sessionId（`MainActivity.kt:122`），没有任何“会话不存在/已删除”的反馈路径。同理 `pendingShare` 在 `state.conversationId == null` 时可永久挂起（DeepDeepApp.kt:342-346 的 return）。
- 影响：用户点“运行完成”通知，但该会话已被删除（或属于已归档且列表未刷新）→ 应用打开后停在原页面，无提示、无跳转；待消费值残留到下次 intent 覆盖前。属用户可见的静默失败。
- 建议：条件不满足时给反馈（Toast/一次重试后 consume）；〔复核勿做：「一次重试后 consume」会削弱冷启动「等会话列表加载完再跳」的现有正确语义；建议只做「找不到会话时提示 + 回落最近会话」，不要缩短等待。〕会话不存在则回落到最近会话或 Chat 根页；intake 状态加超时/版本号（record id）避免残留。工作量 S。

### P3

**[AC-013] 消息内附件路径未做 canonical 约束，仅靠 FileProvider 兜底阻止外发**（P3）· `app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:97`
- 证据：`parseAttachmentBlock` 把用户消息里 `<attachments>` 块的 `path` 原样放进 `AttachmentRef`（Attachment.kt:97-126），UI 直接 `graph.attachmentStore.file(attachment, root)` = `File(root, ref.path)`（`AttachmentStore.kt:73`，无 `..`/canonical 校验，与 `resolve()` 的 canonical 前缀校验 75-119 不一致），随后用于预览/分享（`DeepDeepApp.kt:1030-1045`）。分享路径经 `FileProvider`，其 roots 只有 `home/`、`workspace/`、`workspaces/`、`cache/share-incoming/`（`res/xml/file_paths.xml`），越界文件会因不在 root 内而抛异常被 `runCatching` 吞掉，所以**外发被兜底拦住**，本地预览会读取 `filesDir` 内任意文件（如 `agent/mcp-auth.json`、`datastore/*.pb`）。〔复核修正：预览只对图片解码，非图片不会展示内容；外发被 FileProvider roots 拦住。属加固缺口而非信息泄漏，P3 恰当。〕
- 影响：需要用户消息自身包含特制块（自伤型/被分享文本注入），影响仅限本机展示；但违反“附件只能落在 workspace”的隐含约束，属安全加固缺口。
- 建议：`file()`/预览入口统一做 `canonicalFile` 前缀校验（复用 `resolve` 的 roots 判定），解码时丢弃非法 path。工作量 S。

**[AC-014] 配置变更不保存 pending 会话/深链，分享 staging 中旋转会重复导入**（P3）· `app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:89`
- 证据：`onSaveInstanceState` 只保存 share/voice（89-97）；`pendingSessionId`/`pendingDeepDestination` 是 Activity 字段（37-38），`savedInstanceState != null` 时不再 `handleIntent`（49-53），旋转后二者丢失；分享 payload 被保存恢复，若旋转发生在 `LaunchedEffect` 的 `withContext(IO) { shareStaging.stage(...) }` 中（DeepDeepApp.kt:342-359），协程被取消、重建后再次 stage → cache 目录重复落盘、可能重复 attach。〔复核修正：「重复 attach」不成立——`withContext` 有 prompt cancellation，取消后后续 attach 不会执行。〕
- 影响：深链冷启动瞬间旋转/分屏会静默丢一次跳转；分享导入在旋转窗口内可能附件重复一次。低频。
- 建议：pending intake 用 `rememberSaveable`/ViewModel 持有（或写入 savedState），staging 以 payload hash 做幂等键。工作量 S。

**[AC-015] 前台通知与安装进度通知无节流/合并**（P3）· `app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:200`
- 证据：`ActivityChanged` 每次更新都 `updateForeground()` → 重建通知 + `NotificationManager.notify`（200-204, 243-246）；思考/工具切换在单个工具调用内至少 2-3 次事件，连续工具时每秒多次 binder 调用。安装进度对每条 `(n/m) Installing` 行都 `notifyInstall`（`DevPackageManager.kt:160-169`）。
- 影响：额外 binder/系统服务调用与 GC 压力（每次构建 Notification 对象、读字符串），无功能问题；系统侧低频更新通知刷新率本身有限制，收益小。
- 建议：前台通知按 ≥250-500ms 合并（`ActivityChanged` 只保留最新值，用 `snapshotFlow` debounce），安装进度按 1-2% 粒度节流。工作量 S。〔复核：「`snapshotFlow` debounce」不适用于 service 类，应改 `flow.conflate()`/`debounce()`；其余成立。〕

**[AC-016] per-cwd LinuxEnvironment 无界缓存、每次建引擎读一次 DataStore**（P3）· `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:268`
- 证据：`shellEnvironments: mutableMapOf<String, LinuxEnvironment>` 按 cwd 缓存且永不淘汰（268-282），每个 `LinuxEnvironment` 构造都同步读一次 rootfs marker（`LinuxRootfsInstaller.kt:27,119-131`）并持有 `PRootExecutor`；`shellExecutorFor` 每次建引擎都 `linuxSettings.isEnabled()`（DataStore `first()`，284-290）。
- 影响：长生命周期进程 + 多工作区时 map 单调增长（每项小对象，非严重泄漏）；建引擎时多一次 DataStore 读（毫秒级）。
- 建议：缓存上限/LRU 或复用“共享配置 + 按 cwd 传入 PRootExecutor 绑定”的结构；`isEnabled()` 值缓存由 flow 收集维护。工作量 S。

## 架构评估

**分层**：`:app` 只依赖三个模块，职责边界清楚；data/ 只做存储与纯编解码（`Attachment.kt`、`SkillArchive.kt` 无 Android UI 依赖），runtime 交互通过接口（`AttachmentStore`、`SkillExpander`）注入，是本模块最好的部分。`service/` 依赖 App 的 graph 属于合理方向。唯一跨层味道是 `DeepDeepApp` 直接拿 19 个服务实例做 UI 编排（可以接受，但应抽 holder 降低壳层耦合）。

**DI**：手动 AppGraph 的优点是显式、无注解魔法；缺点是**顺序语义完全隐式**——4 个 `init` 块 + 多个 `by lazy` + `prepare()` + `AgentService` 四条线，谁先谁后靠“构造顺序 + 协程发车时间”保证，没有 await 关系（AC-001/AC-002 都由此产生）。对象图约 30 个 store/service、9 个 DataStore 全部 eager 构造，实际主线程成本不大（构造无 IO），但无法为测试替身留缝（AC-011）。

**状态所有权**：会话/聊天状态集中在 ChatViewModel（合理）；但壳层自己持有 17 个 `remember` UI 状态（抽屉、面板、dialog target），其中分享/deeplink 的 intake 放在 Activity 字段 + Composable `LaunchedEffect` 里，跨进程死亡/配置变更的恢复语义不完整（AC-014）。数据/服务状态都来自 `StateFlow` + `collectAsStateWithLifecycle`（方向正确），缺失的是**读取点的粒度**（AC-003）。

**恢复语义**：导航栈 + 页面 SaveableState 的进程死亡恢复做得完整（routeKey 栈 + removeState 时机 + PredictiveBack 清理）；会话恢复依赖 `prepare()` 完成（AC-006）；迁移与恢复的时序没有保证（AC-001）；intake 与主题的恢复各缺一环（AC-012/AC-007/AC-014）。整体“恢复能工作，但关键恢复点没有显式顺序契约”。

**可测试性**：data 层/纯函数可测且已有测试；导航与抽屉抽出的纯函数也测了。启动装配、Activity intent、AnimatedScreenHost 行为没有测试，AppGraph 具体类型阻塞了从外层注入 fake 的可能——这是本次两条 P1/P2 顺序问题无法靠 CI 发现的结构性原因。

## 性能与效率

**冷启动时间线（主线程标注 M，IO 标注 I；None 为代码路径推演）**

1. `M` `DeepDeepApplication.onCreate`：`AppGraph(...)` 构造——3×`mkdirs`、9 个 DataStore 实例化、~30 个 store/plugin/service 对象、`okHttpTransport()`、`LinuxEnvironment` 内 **1 次 rootfs marker stat+read**；同帧发起 4 个后台任务：StorageMigration（升级首启为全量会话扫描/重写）、`pluginManager.start()+reconfigure`、2 个 DataStore collect；`I` 字体预载（2 字体 × 3 权重 + CJK fallback 构建）。
2. `M` `MainActivity.attachBaseContext`：`LanguagePreferences.wrap` 首载 SharedPreferences（≈1-3ms）。
3. `M` `MainActivity.onCreate`：`handleIntent`、`startForegroundService`、请求通知权限、`fontPreferences.read()` 首载第二个 SharedPreferences（≈1-3ms）、`setContent`。
4. `M` 首帧 composition：`DeepDeepApp` 顶层启动 19 个 flow 收集 → 约 7-9 个 DataStore 文件异步读（I）；主题首帧固定 `SYSTEM`（AC-007）；字体若预载未完成会先以 fallback 排版再切（推测，`AndroidFont` 为 `FontLoadingStrategy.Async`，无预载完成屏障，`DdCompositeFont.kt:31-38,56-61`）。
5. `M` `AgentService.onCreate`：3 个通知频道创建 + 前台通知构建 + `startForeground`（binder）+ 注册时钟 receiver（≈数 ms）；`I` ProcessExitLog、host.ensureStarted、scheduleService.start（可能立即投递到点提醒）。
6. `I` ChatViewModel init → `prepare()` 串行链（AC-006）→ `restoreInitialSession` 逐工作区列会话（AC-001 的竞态点）→ 打开最近会话。
7. 稳态：每轮 RunFinished/App 回前台触发技能指纹扫描（AC-008）；流式期间壳层按 ≤16ms 批次重组（AC-003）；通知随活动状态刷新（AC-015）。

**主线程工作结论**：没有发现阻塞型主线程 IO（会话/技能/迁移/Keystore 都在 IO）；主线程同步盘操作只剩 2 个 SharedPreferences 首载、rootfs marker、mkdirs，合计**约几 ms 到 ~10ms 级（估算）**；真正影响启动体验的是“首会话恢复被 prepare 串行链拖后”与“主题首帧错误”。IO 次数：启动阶段约 2 次 prefs + 1 次 marker + 8-9 次 DataStore 文件读 + 1 次 runs.json + 1 次 pending.json + 会话索引/会话头（与工作区数线性）+ 升级时全量会话重写；运行期每次回前台/每轮结束一次技能目录遍历。**DataStore 数量 9 个**（secrets/model_config/appearance/app_preferences/voice/linux_settings/personalization/session_meta/workspace），设计上可合并且文档 `ARCHITECTURE.md:243` 漏列 `appearance`。

## 优先修复建议（Top 5）

1. **（P1）把迁移变成显式启动前序并原子化会话重写**（AC-001）：`prepare()`/Application 首步 await StorageMigration，`host.start()` 与会话恢复排在其后；`writeText` 换临时文件+rename。这同时修掉文档与实现的漂移。
2. **（P2）引入显式启动阶段与 await 关系**（AC-002、AC-006）：`bootstrap()` 顺序为 迁移 → pluginManager.start → host.start → 通知恢复 → 释放 ready；独立任务（credentials 预热、legacy 迁移、RunLedger 清理）并行 async；`AgentService` 的 schedule 起点排在 bootstrap 之后。
3. **（P2）缩小壳层重组范围并拆分 DeepDeepApp**（AC-003、AC-004）：ChatUiState 读取下沉到 Chat 子树，壳层只读派生字段；同时按 Shell/ChatHost/Intake/Overlay 切开，给页面接线留出可预览的边界。
4. **（P2）清理启动关键路径**（AC-005、AC-007）：预取主题/字体为同步可读缓存，LinuxEnvironment 状态懒初始化，消除主线程盘 IO 与首帧主题闪变。
5. **（P2）技能扫描与通知刷新节流**（AC-008、AC-015）：目录 mtime 聚合指纹 + 最小间隔，前台通知按 250-500ms 合并。

> 〔复核〕Top5 的执行顺序需调整：AC-001 之外的四项都属 P2/P3 体验优化，其中 AC-003/AC-004（壳层拆分/下沉）成本被低估且踩在刚落地的转场/冻结/SaveableState 机制上，而项目 UI 层零测试——应排在「补测试安全网」之后。本轮真正低风险的是 AC-009（无障碍语义）、AC-012 的提示部分、AC-015 的节流。
