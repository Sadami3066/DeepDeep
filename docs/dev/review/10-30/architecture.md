# 架构评审

评审对象：DeepDeep（Android 个人 Agent App）。规模：`:app` 171 个主文件 37,248 行、`:runtime` 58 个文件 10,772 行、`:shell` 8 个文件 956 行、`:design` 14 个文件 1,940 行；主代码合计 50,916 行，测试 20,087 行。构建（`:app:compileDebugKotlin`）与 1022 个单元测试均已实测通过。

本文只讨论跨模块的架构问题；逐模块的代码/性能细节见 `module-*.md`，全量问题清单见 `findings.tsv`。

---

## 1. 总体结论

**评分：8.0 / 10。** 这是一个明显高于个人项目平均水平的架构：模块边界有自动化校验、运行时状态有单一所有者、持久化与进程死亡恢复有成体系的约定、UI 性能有专门的机制（不是靠"感觉优化"），并且这些设计写进了 `docs/dev/ARCHITECTURE.md` 作为契约。主要架构债集中在两处：**UI 层没有状态层（全 App 仅 1 个 ViewModel），功能页面直接持有 Store/Service**，以及**装配层 `AppGraph` 靠属性初始化顺序 + lazy 构成的隐式依赖图**。这两点把"可测试性、生命周期边界、启动成本"的风险集中到了应用层。

| 维度 | 分数 | 理由 |
|---|---|---|
| 模块边界与依赖方向 | 9.0 | 4 模块单向依赖，`:runtime`/`:shell`/`:design` 互不依赖，且有 Gradle 校验任务；扣分在校验未接入构建生命周期 |
| 分层与职责划分 | 6.5 | `runtime` 承担 domain+data 双层职责尚可；但 UI 层无状态层/用例层，页面直接操作 Store，ChatViewModel 1639 行承担 10+ 职责 |
| 状态所有权与并发模型 | 8.5 | 每会话命令面（锁+epoch+runId+token）设计严谨；事件与真相双源对账；扣分在跨层状态仍靠闭包/lateinit 回调拼装 |
| 持久化与恢复语义 | 8.5 | 原子写、坏文件备份、schemaVersion、Clock 注入、四类中断恢复；扣分在 5 套持久化机制并存与重复样板 |
| 可测试性 | 6.5 | 20K 行测试且质量不低（1022 例）；但 UI 层零 instrumented 测试、页面依赖具体 Store，无法对界面行为做单测 |
| 可演进性/扩展性 | 8.0 | 插件契约、能力快照重建（reconfigure 安全点）、新增功能 7 步接线手册；扣分在 PluginManager 与 AppGraph 两个 God Object |
| 文档作为架构契约 | 8.5 | ARCHITECTURE/STATUS/TOOLS/PLUGINS 等文档质量罕见地高；扣分在少量文档-代码漂移与"设计中的系统"混杂 |

---

## 2. 体系结构

### 2.1 模块与依赖

```
:app ──→ :runtime   (AgentRuntime 接口 + AgentRuntimeHost 实现)
   ├──→ :shell     (PRoot/LinuxEnvironment/ShellExecutor)
   └──→ :design    (主题/字体/组件/motion)
:runtime、:shell、:design 之间无相互依赖
```

- 依赖方向由根 `build.gradle.kts` 的 `verifyModuleBoundaries` 任务静态校验（`build.gradle.kts:8-59`），白名单写在 `allowed` map 中。
- `:runtime` 只依赖 pi2kotlin SDK（`api`）与 okio/DataStore/coroutines，不含 Compose（`runtime/build.gradle.kts:25-31`）；`:design` 只依赖 Compose（`design/build.gradle.kts`）。
- 运行时 SDK 依赖是 `api(libs.pi.coding.agent.android)`：`:app` 能直接用到 SDK 类型，是有意为之（`AgentRuntimeHost` 需要直接暴露/消费 SDK 模型）。

**结论：边界本身是干净的，这是全项目最扎实的一块。** 唯一问题是校验任务不挂在 `check`/`build` 上（见 §4 A-01），在没有 CI 的仓库里等于"靠自觉运行"。

### 2.2 运行时数据流

```
Compose ChatScreen ──→ ChatViewModel ──→ AgentRuntime(接口)
                                        └─ AgentRuntimeHost
                                            ├─ SessionCommandPlane（每会话锁+epoch+runId）
                                            ├─ Handle 表（每会话 PiSessionEngine/AgentSession/executor）
                                            └─ events: SharedFlow<SessionRuntimeEvent>
                                                    ↓
                              ChatViewModel 投影为 ChatUiState → Compose
```

关键设计（全部有代码佐证）：

1. **命令面是运行状态的唯一所有者**（`SessionCommandPlane.kt:7-217`）：每会话一把锁、单调递增 epoch、`runId` 标识、`terminalEmitted` 防重复终态、`reconfigureOwner/pendingReconfigure` 处理重配。`begin/finish/stop/deliver` 全部在锁内做状态迁移，停止时 epoch 递增使旧的延时重试/目标续跑作废（`AgentRuntimeHost.kt:524-543`）。
2. **事件是快速通道而非唯一真相**：宿主同时维护 `activeRuns`（`AgentRuntimeHost.kt:85`），UI 在 busy→idle、回前台、打开会话时用 `history()` 重建对账（`ChatViewModel.kt:663-724`）。这是本项目最值得保留的可靠性设计。
3. **重配安全点**：插件/MCP/设置变化不关闭运行中会话，只登记 `pendingReconfigure`，等本轮 `release()` 后再重建（`AgentRuntimeHost.kt:480-486`、`682-708`），避免"改配置把正在跑的任务打断"。
4. **Handle 预算**：空闲句柄超过 6 个且空闲 10 分钟才回收，回收前询问 App 的 `keepHandleAlive`（未投递提醒、活跃目标、运行中 job、待答问题、当前会话都算保留，`AppGraph.kt:340-347`）。这是对的取舍——把"内存"风险交给显式的存活判据，而不是系统内存回调。

### 2.3 UI 层结构

- `MainActivity`（单 Activity，`singleTask`）→ `DeepDeepApp`（1159 行根 Composable）→ `AnimatedScreenHost`（自研导航 + 每个 destination 一个 `SaveableStateProvider`）。
- 全 App **只有 1 个 ViewModel**：`ChatViewModel`（`ui/chat/ChatViewModel.kt:65`）。其余功能屏中 10+ 个直接接收具体 Store/Service：`SkillsScreen(repository=…)`、`PluginsScreen(manager=…)`、`McpScreen(store,probe,oauth=…)`、`ScheduleScreen(service,runtime=…)`、`WorkshopScreen(store=…)`（`DeepDeepApp.kt:803-888`）。
- 状态来源分三类：全局 Store 的 `StateFlow`（在根节点用 `collectAsStateWithLifecycle` 收集，共 19 处）、`ChatUiState`（ChatViewModel）、页面内 `rememberSaveable`/`produceState`。

这是项目**最重要的架构分歧点**：继续"页面直连 Store"还是补一层 per-screen 状态持有者，决定了后续可测试性与启动/重组成本的走向。取舍分析见 §4 A-02。

---

## 3. 值得肯定的架构决策

以下 10 条不是客套——每一条都在代码里有对应实现，且是同类项目里经常缺失的：

1. **接口先于实现**：`AgentRuntime`（390 行接口，`RuntimeTypes.kt:313-390`）完整描述了宿主能力；`ProviderDirectory`、`ModelSettings`、`SkillExpander` 同样是接口。Chat 层因此可注入 fake，`ChatViewModelTest` 1662 行才写得出来。
2. **状态迁移全部走命令面**，而不是散落的 `busy` 布尔判断。全仓库找不到 `GlobalScope`/`runBlocking`（唯一一处 `Thread.sleep` 在 `PRootExecutor.kt:351` 的 PID 就绪轮询里），协程纪律整体良好。
3. **四类"中途死亡"都有恢复路径**：`RunLedger`（运行中断记录 → 下次打开会话插入提示）、`QuestionPersistence`（待答问题 → 启动汇总通知）、`delivery-intents.json`（提醒投递 → 重启补投）、`JobPersistence`（后台任务 → 重启标记 interrupted）。这在一个本地进程随时可能被 MIUI 杀掉的 App 里是刚需。
4. **持久化纪律统一**：手写 JSON store 一律 `*.tmp` + `renameTo` 原子写，坏文件转 `.corrupt`，带 `schemaVersion`，`ScheduleStore`/`WorkshopStore` 支持注入 `Clock`。抽查 `ScheduleStore.kt:280`、`JobPersistence.kt:75/91` 均符合。
5. **原子写之外还有"意图先落盘"**：提醒投递先写 `delivery-intents.json`（含 `scheduleId@nextRunAt` 去重键）再投递，成功才删除，进程死在投递中也不会重复投递（ARCHITECTURE 有描述，`ScheduleService` 实现）。
6. **插件契约与"能力快照"边界**：`OfficialPlugin` 只描述生命周期与贡献（install/start/stop/status/tools/skills/promptBody/settings），不引用 Linux/CLI/登录概念；运行时通过 `reconfigure()` 在安全点整体重建能力，而不是增量 patch。这是插件系统里最容易做错的地方。
7. **工坊是显式安全边界**：独立虚拟域名 `https://<id>.workshop.local/`、自定义 `PathHandler`、`addDocumentStartJavaScript` 注入 bridge、`addWebMessageListener` 校验 origin、页面 Agent 的工具白名单 + `ScopedFileSystem`（canonicalize 后校验祖先）+ 临时 workspace。这是全项目设计得最"工程化"的子系统。
8. **UI 性能机制化**：转场冻结 `ChatUiState`、`graphicsLayer` 在 draw 阶段做位移动画、`SaveableStateHolder` 按 destination 保状态、`derivedStateOf` 隔离根节点读取、流式 16ms 合并、Markdown 增量规划 + 块级缓存、字体 Async 加载 + 启动预热。这些不是散点优化，是成体系的。
9. **"功能接线 7 步"作为架构治理约定**（ARCHITECTURE 末尾）：持久化→宿主服务→模型工具→运行时事件→UI→测试→文档，缺一项算没打通。这让新增功能有一致的形状，也是本项目十几个功能域还能保持结构相似的原因。
10. **审计友好的验证文化**：`artifacts/validation/roundNN/` 记录真机证据、`docs/dev/DEVICE_TESTING.md` 验收条目、`docs/archive/STATUS.md` 记录每轮"已验证/未验证"。审查时能快速区分"声称"与"实测"——这在个人项目里非常少见。

---

## 4. 架构问题与风险

### A-01 模块边界校验未接入构建生命周期（中）

`build.gradle.kts:8` 注册了 `verifyModuleBoundaries`，`gradle.projectsEvaluated` 里收集违规（`build.gradle.kts:14-33`），`doLast` 里 `check(violations.isEmpty())`。但全仓库没有任何 `tasks.named("check") { dependsOn(...) }` 或 `preBuild` 依赖，`./gradlew build`/`check` 不会执行它。

证据：`build.gradle.kts` 全文只有 `tasks.register` + `configure`，无消费方；`./gradlew tasks --all` 显示为独立任务；STATUS 里它是作为独立命令手动跑的（"`verifyModuleBoundaries` 通过"）。

影响：没有 CI 的仓库里，边界校验只会在"记得手动跑"时生效；重构时第一个被绕过的架构约束往往是它。

建议：`tasks.named("check") { dependsOn(verifyModuleBoundaries) }`（根 project），或让 `:app` 的 `compileDebugKotlin` 依赖它。成本 S。

> 〔复核修正〕前半句有硬伤：**根 project 未应用 `base`/android 插件，没有 `check` 生命周期任务**，照写会在配置期抛 `UnknownTaskException`；应挂到子项目 `check`（或 `:app:check`）上，或让 `:app` 编译依赖它。

### A-02 UI 层缺状态层：页面直连 Store，全 App 仅 1 个 ViewModel（重要）

现状：
- `DeepDeepApp.kt:803-888` 里 10+ 个功能屏的构造参数是具体 Store/Service（`SkillsScreen(repository=graph.skillRepository)`、`PluginsScreen(manager=graph.pluginManager)`、`McpScreen(store=…,probe=…,oauth=…)`……）。
- 全仓库 `class .*ViewModel` 只有 `ChatViewModel`。
- 各页面用 `LaunchedEffect` + 本地 `remember` 自取数据，例如 `SkillsScreen` 自己调 `repository.list()`/`syncActiveSkills()`，`McpScreen` 自己调 `probe`。

**收益**：省掉 19 套 ViewModel/StateHolder 样板，页面数量增长时开发速度快；数据本身在 Store 的 `StateFlow` 里，进程内不会丢。
**代价**：
1. **可测试性空白**：页面行为（加载态、空态、错误态、确认对话框）无法在不启动 Activity 的情况下测试；20K 行测试全部集中在 runtime/reducer/parser，UI 层 0 覆盖。`app/src/androidTest` 只有 2 个文件。
2. **生命周期边界模糊**：页面在 `LaunchedEffect` 里发起的加载/同步随 composition 存活，配置变化/快速进出页面靠取消兜底；`MainActivity.onStart` 里直接 `skillRepository.syncIfChanged()` 是这一模式的极端形态（见 `module-app-core.md`）。
3. **屏幕状态无法跨进程死亡恢复**（除了手工 `rememberSaveable` 的少数字段）；`SaveableStateHolder` 只拯救 Composable 内状态，不拯救异步加载结果。
4. **参数即依赖**：Screen 的签名随 Store 变化而变化，`DeepDeepApp` 被迫成为所有依赖的集散地（见 A-03）。

建议（不必一次做完）：先给 3 个最复杂/最常改的页面（`SkillsScreen`、`PluginsScreen`/`PluginDetailScreen`、`McpScreen`）加轻量 `StateHolder`（`remember` + `produceState` 或 ViewModel，不引入 UseCase 层），把"取自 Store → 加载态 → 错误态"三段收敛成可测对象；其余页面维持现状。收益/成本比最好的切口是 MCP 与插件详情（它们有探测、连接、OAuth 多阶段状态）。

### A-03 根 Composable 成为全局状态集散地（重要）

`DeepDeepApp.kt`：
- 19 个 `collectAsStateWithLifecycle`（行 217-306）+ 17 个 `mutableStateOf` + 2 个 `mutableIntStateOf` + 13 个 `remember` 派生值；〔复核修正〕
- 1159 行函数体，包含导航分发、抽屉、模型/用量/任务/文件 4 个 Sheet、3 个 Dialog〔复核修正：模型 Sheet 实际在 `ChatComposer` 内，壳层是 3 个 Sheet + 3 个 Dialog〕、分享/深链接/语音/命令分发、预览；
- 所有 Screen 的构造与回调都在这里。

影响（两层）：
1. **架构**：任何新功能都必须改 `DeepDeepApp` + `AppGraph` 两处；这是全项目合并冲突最集中的文件。功能之间没有边界，抽屉里的一个收藏操作与聊天 composer 的模型切换共享同一个作用域。
2. **性能**：`DeepDeepApp` 在 body 里读取 `state`（`ChatUiState`）与 19 个 flow，流式期间每 16ms 一次、后台任务输出每 100ms 一次都会触发根重组（详见 `performance.md` P-02）。根 Composable 有 94 个 call site，重组即使全部跳过子节点也要重跑函数体与派生计算。

建议：
- 短期：把 `AnimatedScreenHost` 的 `base` 内容与"屏幕路由 when 分发"拆成两个子 Composable，把 Sheet/Dialog 收进各自的小 Composable（只读需要的 flow，不读 `state`），让根只保留导航/抽屉必需状态。
- 中期：按功能域拆 `ChatPane`、`DrawerPane`、`OverlayHost`、`Routes`；`DeepDeepApp` 目标 ≤300 行。
- 长期：若采取 A-02 的 per-screen StateHolder，根节点自然只需要导航状态。

### A-04 AppGraph 手动 DI 的初始化顺序脆弱（重要）

`AppGraph.kt:89-402`：53 个属性 + 4 个 `init {}` 块（第 96、126、131、361 行，穿插在属性声明之间），依赖关系靠 Kotlin "按源码顺序初始化" + `lazy` 隐式解决：
- `host`（324 行，非 lazy）在构造时接收 `goalDriver = goalService`（174 行，lazy），会触发 lazy 求值；
- `host` 的 `sessionCleaner`/`keepHandleAlive`/回调里引用 `jobRegistry`、`scheduleService`、`goalService`、`questionCenter`（`questionCenter` 实际非 lazy，`AppGraph.kt:187`）——第一次运行期调用才构造〔复核修正〕；
- `pluginManager`（191 行 lazy）与 `notificationCenter`（350 行 lazy）、`scheduleService`（351 行 lazy）构成双向引用，靠"不在构造期调用"成立；
- `init { pluginManager.start(); host.reconfigure() }`（361-365）在 361 行执行，此时 `host` 必须已经初始化——正确性依赖"host 声明在 init 之前"这一行号关系。

风险：任何属性移动/新增 lazy 都可能引入"首次调用时构造顺序不对"的偶发问题，且没有测试能覆盖（`AppGraph` 需要真实 `Context`）。启动成本也集中在这里（`Application.onCreate` 主线程构造整个图）。

建议：
1. 最小改动：把所有 `init {}` 移到文件末尾，或在 `fun start()` 里显式按序执行；把依赖图写成注释上方一行（现在完全没有）；
2. 更彻底：抽 `AppCoreGraph`（stores/services）与 `AgentGraph`（host/engine），用构造函数参数显式传递，`AppGraph` 只做组合；
3. 中期：为 `AppGraph` 加一个 `@VisibleForTesting` 的 `val` 快照测试（用 Robolectric 或 instrumented test）验证"构造后所有 lazy 可求值且引用一致"。

### A-05 5 套持久化机制并存（中等）

- DataStore Preferences：11 个实例（`secrets`、`model_config`、`personalization`、`session_meta`、`linux_settings`、`app_preferences`、`voice`、`micloud_capabilities`、`workspaces`，以及 `appearance`、`projects` 两个专用/迁移文件）；〔复核：`module-app-core.md` 计为 9 个，两文口径未互注〕
- 手写 JSON store：`ScheduleStore`、`GoalStore`、`JobPersistence`、`QuestionPersistence`、`RunLedger`、`McpConfigStore`、`WorkshopStore`、`PluginRecordStore`、`PluginSettingsStore`、`AgentDocStore`、`SessionIndex`、`ScheduleService.delivery-intents`（13 个文件出现 `.tmp` 原子写模式）；
- SharedPreferences：`LanguagePreferences`、`FontPreferences`；
- SQLite：工坊 `userdata/<id>.db`；
- JSONL：pi 会话文件（唯一权威）。

影响：一致性靠"每个 store 作者记得照抄模式"维持；13 处原子写/3 处坏文件恢复（`QuestionPersistence`、`JobPersistence`、`RunLedger`，其余无 `.corrupt` 隔离）各写一遍（合计约 300-500 行，无法精确验证）〔复核修正〕；新功能接线第一步就要再抄一遍。这不算错误，但确实是维护成本。

建议：抽一个 `runtime/AtomicJsonFile`（`read(): T?` 带损坏备份、`write(value)` tmp+rename、schemaVersion 校验、`Clock`/dispatcher 注入），已实现原子写的 store 逐步迁移；DataStore 的 11 个实例可以合并成 2-3 个（secrets 单独保留是对的），但**优先级低于** A-02/A-03。

### A-06 ChatViewModel 是"单文件应用"（重要）

`ChatViewModel.kt` 1639 行、87 个函数、30+ 可变字段（`mutableState`、`sessionDrafts`、`conversationCache`、`sessionOpenJobs`、`reconcileJobs`、`pendingDeltas`、`autoRetry*`、`interrupted*NoticeSessions`……）。职责横跨：
发送/插话/排队、会话打开/切换/新建/删除、草稿与附件、滚动锚点、自动重试、对账、模型与思考切换、工作区 CRUD、搜索、命令、缓存淘汰、标题本地化。

好的部分：`ConversationReducer`（499 行）已经把"事件 → 消息列表"的纯函数逻辑抽出来了，这是正确的方向；`ChatViewModelTest` 1662 行证明核心可测。
问题：reducer 之外仍有大量状态机（重试、对账、打开会话串行化、缓存）混在同一个类里，且它们的可变字段之间没有不变量约束文档。

建议：按"状态机"而不是"功能"拆（拆分后仍是 1 个 VM 对外）：
- `ConversationStore`：messages/cache/deltas/scroll（纯状态，可单测）
- `SessionSwitcher`：open/switch/new/close + epoch 丢弃逻辑
- `RetryController`：autoRetry/手动 retry 状态机
- `DraftStore`：sessionDrafts/附件/引用
`ChatViewModel` 只做事件分发与 UI 状态合成，目标 ≤500 行。

### A-07 事件/状态流与轮询混用（中）

ARCHITECTURE 声称"UI 只订阅事件与状态流，不轮询"。实际至少两处反例：
- `DeepDeepApp.kt:1052-1061`：`LaunchedEffect(jobsSheetOpen, selectedJobId)` 里 `while (true) { jobRegistry.read(id); delay(800) }` 轮询任务输出；
- `MainActivity.onStart`（`MainActivity.kt:99-105`）每次回前台触发 `skillRepository.syncIfChanged()` 全量扫描（扫描范围是 home + 所有 workspace 的 `.pi/skills`）。

影响：前者的开销可控（800ms + 单任务），但它是"UI 订阅"原则之外的例外，任务输出其实已经通过 `JobRegistry.jobs` StateFlow 发布（100ms 合并），读输出可以做成 `snapshotFlow`/`LaunchedEffect(job.outputVersion)` 而不是固定轮询；后者在技能目录大时会带来可感知的启动后 IO（详见 `module-app-core.md`）。

建议：任务输出改为由 `JobRegistry` 暴露 `outputFlow(id)`（或把输出尾部放进 `JobSnapshot`），UI 直接 collect；技能扫描改为"事件触发的 dirty 标记 + 进入技能页时同步"，并确认 `syncLocked(force = false)` 的指纹短路是否覆盖了每轮 RunFinished 的调用。

### A-08 平台能力与业务逻辑的耦合（中）

插件层（`app/plugin/*`）直接使用 `Activity`、`AccessibilityService`、`WebView`、`CalendarContract`、`PackageManager`。作为"适配层"这是合理的，问题是**体积与职责**：
- `MiCloudTools.kt` 1008 行，把工具 schema、HTTP 调用、WebView 写入、cookie 校验、错误映射揉在一个文件；
- `PluginManager.kt` 655 行：安装/卸载事务 + 状态缓存 + 技能同步 + 设置 + 工具收集 + 提示拼接，是第二个 God Object；
- `GuiTools.kt` 592 行 + `GuiAccessibilityActions.kt` 320 行，节点树遍历与工具层耦合。

建议：`MiCloudTools` 至少拆成 `MiCloudTools`（schema/参数校验）、`MiCloudClient`（已有，HTTP）、`MiCloudWebWriter`（已有）、`MiCloudAuth`（cookie/session 校验）；`PluginManager` 把"技能同步"与"安装事务"抽成独立类。这是纯机械重构，风险低、收益高（插件层是后续新增能力最密集的区域）。

### A-09 测试架构与 UI 架构的断裂（中）

- 1022 个单元测试、20,087 行，质量高于平均（有 `SessionCommandPlaneTest`、`HistoryMapperTest`、`ScheduleStoreTest`、Markdown 48 篇语料等价断言）；
- 但 instrumented 测试只有 2 个文件，Compose UI 测试依赖加了（`compose-test`）却几乎没用；
- 因为 UI 直连 Store（A-02），无法为页面写"假 Store → 断言 UI"的测试——测试架构与 UI 架构是同一问题的两面。

建议：不为覆盖率而补 UI 测试；按 A-02 的切口先让 3 个页面可测，再各加 2-3 个 `createComposeRule` 用例覆盖加载/错误/确认路径即可。

### A-10 构建可复现性与治理缺口（中）

- `pi = 0.2.4` 从 `mavenLocal` 解析（`settings.gradle.kts` 的 `mavenLocal { includeGroup("dev.pi") }`）：新机器/CI 必须先 `cd ../pi2kotlin && ./gradlew publishToMavenLocal`，否则构建失败；没有版本锁定校验（本地 maven 中的 0.2.4 可能被覆盖发布）。
- `release` 关闭 minify/资源收缩（`app/build.gradle.kts`），用 debug keystore 签名：本地分发合理，但产物 39MB、dex 31MB（见 `performance.md` P-07）。
- 无 CI、无 lint/detekt/ktlint 配置、无 baseline profile；模块边界任务手动跑（A-01）。
- `rs.html`（4.7MB）未跟踪、`assets/` 未跟踪，仓库卫生待整理（详见 `build-tests.md`）。

这些在个人项目里可以接受，但要明确它们是"取舍"而非"完成态"；一旦要长期维护/多人协作，前两项应最先补（CI 跑 build+test+边界校验；把 pi 源改为 composite build 或发布到私有 maven）。

---

## 5. 演进路线建议

**低风险、高收益（1-2 天）**
1. `verifyModuleBoundaries` 接入 `check`（A-01）。
2. 任务输出改为 flow，去掉 800ms 轮询（A-07）。
3. `DeepDeepApp` 的 4 个 Sheet / 3 个 Dialog 拆成独立 Composable，根只留导航与抽屉（A-03 短期）。

**中等（1-2 周，按需）**
4. 3 个页面引入轻量 StateHolder + Compose 测试（A-02/A-09）。
5. 抽 `AtomicJsonFile` 并迁移 3-4 个 store（A-05）。
6. `AppGraph` 的 `init` 整理为显式 `start()`；给装配图写 5 行依赖顺序注释。

> 〔复核〕执行顺序补两条前提：① 路线 7（拆根 Composable / 拆 `ChatViewModel` / 拆宿主接口）必须**先补测试安全网**——项目 UI 层零测试、`AgentRuntimeHost` 零测试，原地改会无兜底；② A-01 的接线写法按上文修正。本轮真正低风险且立刻可做的只有 A-01、A-07 与「Sheet/Dialog 拆成独立 Composable」。

**长期（重构级）**
7. 拆 `ChatViewModel`（A-06）、拆 `PluginManager`/`MiCloudTools`（A-08）、`DeepDeepApp` 按域拆分（A-03 长期）。
8. 会话索引/长历史分页方案（与 `performance.md` P-05 一起决策）。

---

## 6. 结论

架构的"骨架"是对的：模块边界清晰，状态所有权集中，事件/真相双源，持久化有恢复语义，插件与安全边界有显式契约。真正的债不在骨架，而在**应用层的装配与 UI 状态组织**——`AppGraph`（隐式顺序）、`DeepDeepApp`（全局集散）、`ChatViewModel`（单类多职责）三处共同承担了过大的责任，导致"新增功能容易、改全局行为难、UI 无法测试"。

这些债都是渐进可还的：A-01/A-07 是一天内的收益，A-02/A-03 是"每加一个功能就多还一点"的持续改善，A-06/A-08 可以留到下一次大改版。不建议为了"架构纯洁"引入 UseCase/DI 框架重写——当前架构的可读性和一致性足以支撑，问题在局部热点而非全局设计。
