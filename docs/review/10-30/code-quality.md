# 代码质量评审（跨模块）

本文回答"代码写得怎么样"：命名、复杂度、重复、错误处理、死代码、测试质量、静态检查基线。逐模块的完整问题清单在 `module-*.md`，跨模块架构问题在 `architecture.md`，性能机制在 `performance.md`。

**评分：6.9 / 10。** 这是一个"纪律好、结构局部失控"的代码库：协程与错误处理有统一的写法，几乎没有 TODO/死注释/`println`，1022 个单测质量高于平均；但复杂度高度集中在 6 个 800+ 行文件里，重复与"双真相"状态模型在应用层累积，静默失败在关键路径上仍有 5 处会造成用户可感知的错误。

---

## 1. 客观指标（本次实测）

| 指标 | 数值 | 说明 |
|---|---|---|
| 主源码 | 50,916 行 / 251 个 `.kt` 文件 / 1,178 个顶层声明 | `:app` 37,248 + `:runtime` 10,772 + `:shell` 956 + `:design` 1,940 |
| 文件规模 | 27 个 >400 行、11 个 >600 行、6 个 >800 行 | 最大：ChatViewModel 1,639 / MarkdownParser 1,193 / DeepDeepApp 1,159 / AgentRuntimeHost 1,083 / MiCloudTools 1,008 / DeepDeepDrawer 804 |
| 顶层声明规模 | 17 个 >300 行、61 个 >150 行 | 最长：ChatViewModel 类 1,576 行、AgentRuntimeHost 类 1,026 行、DeepDeepApp 函数 1,004 行 |
| 函数 | 约 2,200 个（启发式统计，含嵌套） | 单函数超长的不多，问题在"类/文件"而非"函数" |
| 编译警告 | **13 条**（四模块 `--rerun-tasks` 全量重编译） | 1 处多余 cast、2 处未使用表达式、3 处多余 `!!`/安全调用、1 处 delicate API、2 处 deprecated 字段、1 处 override deprecated |
| Lint | **0 errors / 160 warnings / 5 hints**（`:app:lintDebug` 实测） | `UnusedResources 79`、`IconDuplicates 26`、`PluralsCandidate 12`、`UseKtx 11`、`GradleDependency 8`、`MissingOnRenderProcessGone 6`（真实缺陷 GL-009） |
| 单元测试 | **1,022 个 / 0 失败**（app 626 / runtime 364 / shell 32） | `@Ignore` 0 个；androidTest 仅 2 个文件 |
| 协程纪律 | `GlobalScope` 0、主源码 `runBlocking` 0、`Thread.sleep` 1（有界 PID 轮询）、`CancellationException` 显式重抛约 55 处（127 是全仓提及次数）〔复核修正〕 | 唯一空 catch 4 处（2 处在交付路径，见 C-03） |
| 错误处理 | `runCatching` 323 处、`catch (_: Exception)` 18、`catch (_: Throwable)` 2 | 抽查未发现取消被系统性吞掉，但静默分支缺日志 |
| 空安全 | `!!` 3 处（均有同分支守卫）、`lateinit` 1 处 | 良好 |
| 死代码信号 | 真 `TODO/FIXME/HACK` 0、`println` 0、`Log.d/v/w` 22 处集中在 3 文件、未使用字符串资源 79 条 | 良好（lint 死资源除外） |
| i18n | 933 个 string key，中英 100% 对齐 | UI 层仍 7 处硬编码中文（SC-007），插件/工具层约 200 处中文错误文案 |

---

## 2. 亮点

1. **协程取消语义有统一纪律**：`catch (cancelled: CancellationException) { throw cancelled }` 在 `SkillRepository`、`QuestionCenter`、`PluginManager`、`McpOAuthSession`、`AgentRuntimeHost` 等主要类中反复出现且写法一致（全仓 127 处）；`runCatching` 虽多，但被抽查的 suspend 位置都没有吞取消。这类一致性在无 lint 约束的仓库里只能靠习惯，说明作者对此有明确标准。
2. **测试写的是"行为"而不是"行数"**：`ChatViewModelTest` 1,662 行 / 51 用例用 fake runtime 驱动事件序列并断言状态迁移（`ignoresStaleRunEvents`、`sendShowsWorkingStateImmediatelyAndFinishes`）；`ScheduleServiceTest` 注入 `MutableTestClock` 而非等待真实时间；`SessionCommandPlaneTest` 用 8 线程 CountDownLatch 断言"并发 begin 只起一个 run"；`MarkdownChunkingTest` 对 48 篇语料 × 每个字符前缀断言"整篇解析 == 分块解析"并校验稳定 chunk 实例复用（`assertSame`）。这是全项目质量最高的一部分。
3. **数据校验与路径安全是系统性的**（不是个别点）：`SkillArchive` 的 zip 防护（大小/条目/加密/`..`/盘符）、`RootfsExtractor` 的 tar 防护（绝对路径、symlink 逃逸、父路径 symlink、checksum、单条目上限）、`PluginSkillStore` 的技能路径校验、`ScopedFileSystem` 的 canonicalize 祖先校验。四处独立实现都拒绝同类攻击面，说明是"知道自己在防什么"。
4. **纯函数化到位**：`ConversationReducer`（499 行事件→状态纯函数）、`HandleBudget`、`ScheduleCron`、`HistoryMapper`、`buildDrawerEntries`、`parseMcpEntries` 等都被抽成可单测单元，UI 层过滤逻辑也多放在 `remember(x, query)` 的纯函数里（`SkillsFilter`/`McpFilter`/`ScheduleDraft` 均有配套测试）。
5. **命名与错误文案可读**：函数名多是"动词+对象"，错误信息带上下文（`"plugins.json 解析失败：…"`、`"定时任务数据缺少 schemaVersion"`），对排障友好；异常类型也按域命名（`CorruptScheduleDataException` 等）。
6. **没有注释债，也没有"注释即谎言"**：源码零注释是项目约定，代码靠命名与结构自解释；抽查的文档-代码漂移（`build-tests.md` 的 13 行抽查表）多数是版本号/常量值层面的过时，不是设计描述失真。

---

## 3. 问题清单（按主题）

### C-01 复杂度集中在 6 个文件（重要）

17 个顶层声明超过 300 行、6 个文件超过 800 行，且它们恰好都是"多主题合订本"：

> 〔复核〕文件级数字（>400 行 27 个、>600 行 11 个、>800 行 6 个）全部复算一致；但「17 个 >300 行顶层声明 / 61 个 >150 行」无法用同一口径复现，请以文件级数字为准。

| 文件 | 行数 | 实际承担的主题 |
|---|---|---|
| `ChatViewModel.kt` | 1,639（类跨度 1,576、124 个成员、30 个私有字段、87 个函数） | 发送/插话/排队、会话打开切换、草稿附件、滚动锚点、自动重试、对账、模型/思考、工作区 CRUD、搜索、命令、缓存淘汰、标题本地化（CH-004） |
| `DeepDeepApp.kt` | 1,159（函数跨度 1,004、94 个 call site） | 导航分发、抽屉、4 个 Sheet、3 个 Dialog、分享/深链接/语音/命令、19 个全局 StateFlow 收集（AC-004） |
| `MarkdownParser.kt` | 1,193 | 块解析、行内 AST、嵌套列表、表格、setext、引用式链接/脚注、`InlineScanner`（约 340 行内部类），阶段边界不清 |
| `AgentRuntimeHost.kt` | 1,083（类跨度 1,026） | handle 生命周期、命令面桥接、事件投影、MCP 重配、history/usage、目标续跑、会话 CRUD、句柄回收（RT-022） |
| `MiCloudTools.kt` | 1,008 | 工具 schema、HTTP 调用、WebView 写路径、cookie 校验、错误映射（PL 系列） |
| `PluginManager.kt` | 655 | 安装/卸载事务、状态缓存、技能同步、设置、工具收集、提示拼接（PL-006） |

影响：不是"看不懂"（代码本身可读），而是导航成本、合并冲突面、单测构造成本随文件大小上升；且这些文件里都混着 3-12 类职责，改动一处需要理解整个文件的不变量。

建议（按拆分收益/风险排序，均可在后续迭代渐进完成）：
1. `MiCloudTools`（纯业务，依赖已拆出 `MiCloudClient`/`MiCloudWebWriter`，风险最低）→ schema/校验/调用/错误映射四个文件；
2. `PluginManager` → `PluginInstaller`（安装事务）+ `PluginStatusCache`（探测/TTL）+ `PluginContributions`（技能/工具/提示）；
3. `DeepDeepApp` → 按域拆 `ChatPane`/`DrawerPane`/`OverlayHost`/`Routes`（与 `architecture.md` A-03 同一改动）；
4. `MarkdownParser` → 按解析阶段拆（block/paragraph/inline/table），现状内部 `InlineScanner` 已是自然切口；
5. `ChatViewModel` 最难，先抽 `ConversationStore`/`DeltaBatcher`/`RetryController`（与 A-06/CH-004 同一改动），`AgentRuntimeHost` 拆锁之后再拆类。

### C-02 结构性重复（中等）

1. **手写 JSON store 样板**：13 个文件重复〔复核修正：13 是该类文件并集，没有一个是四件套（tmp+ATOMIC_MOVE+`.corrupt`+schemaVersion）全做；`.corrupt` 只有 3 个 store〕"`*.tmp` 写入 + `Files.move(ATOMIC_MOVE)` + 坏文件 `.corrupt` + `schemaVersion` 校验"约 300-500 行同构代码（`ScheduleStore`/`GoalStore`/`JobPersistence`/`QuestionPersistence`/`RunLedger`/`McpConfigStore`/`WorkshopStore`/`PluginRecordStore`/`PluginSettingsStore`/`AgentDocStore`/`SessionIndex`/`ScheduleService`/`MarkdownImage`）。部分 store 已有 `Clock` 注入，但公共逻辑未抽出（`architecture.md` A-05）。
2. **页面骨架复制**：17 个页面复制同一段 64dp 顶栏与 `Column + verticalScroll` 骨架，7 份空态、6 份加载块、sheet 高度上限 0.85/0.88/0.92/0.94（`SkillsScreen` 的 0.66 是 `screenHeightDp * 0.66f` 的正文上限，不是 `fillMaxHeight`）〔复核修正〕，并且已经产生可观察的漂移（SC-002）。
3. **两套同义组件/逻辑并存**：`DdSegmented` vs `DdSegmentedControl`（SC-011）、`userFacingError` 英文子串匹配 vs `ChatUiStrings.runErrorKind`（CH-010）、`ChatUiState` vs `ConversationState` 双模型手写映射（CH-004）、`refreshSessions` 每次做两次 `listAll`（P-06）。
4. **同名函数的合理与不合理**：跨文件同名函数 196 组，其中 `read/save/load` 的重复属于 store 样板（不合理），`install/tools/uninstall` 的重复多是插件接口实现（合理）。

建议：抽 `AtomicJsonFile` 与 `DdPageScaffold`/`DdGroupCard`/`DdEmptyState`/`DdLoadingBlock` 后，C-02 的 1、2 两项可消除约 60%；两项都是机械重构，风险低。

### C-03 静默失败与吞异常（重要）

全仓只有 4 处空 catch（`MiCloudPlugin.kt:235`、`QuestionCenter.kt:144`、`JobRegistry.kt:277`、`PRootExecutor.kt:130`），数量很好；但**静默失败的后果被低估**：

- `QuestionCenter`/`JobRegistry` 的交付路径空 catch 没有日志，后台唤醒失败时排障无门（RT-019）；
- `SessionCatalog.delete` 吞掉删除失败但仍删 meta、发 `SessionRemoved`（RT-013）——用户看到会话消失，文件可能还在；
- 工坊删除、插件详情加载失败、API Key 保存无反馈（SC-003）——UI 直接静默返回旧/空数据；
- `PluginManager.start()` 丢弃 `load().error` 后继续 upsert，**损坏的 plugins.json 被静默覆盖**，外部插件安装记录一次性丢失（PL-001，P1）；
- 启动阶段所有插件恢复/技能同步/设置应用错误被 `runCatching` 吞掉（PL-012）；
- `GoalStore`/`ScheduleStore` 的损坏策略是抛异常（而非隔离），会把启动流程打崩（RT-001，P0）。

建议：统一成两级约定——"持久化/后台任务失败 → 必须有日志或计数 + 降级返回值"；"用户操作失败 → 必须回传 Result 并由 UI 展示错误行/Toast"。前者可以做一个 `logFailure(tag)` 工具，后者把保存/删除类回调从 `(…) -> Unit` 改成 `suspend (…) -> Result<Unit>`。这两条能把 C-03 的大部分一次性收敛。

### C-04 状态模型"双真相"与隐式不变量（重要）

- `ChatUiState`（31 个字段）与 `ConversationState`（8 个字段）并存，`ChatViewModel.kt:1436-1453` 手写 7 字段双向映射；reducer 只认识后者，UI 只认识前者，加字段时两处都要改且容易漏（CH-004）。
- `ChatViewModel` 有 8 张私有可变表（`sessionDrafts`、`conversationCache`、`sessionOpenJobs`、`reconcileJobs`、`pendingDeltas`、`autoRetry*`、`interrupted*NoticeSessions`），各自的生命周期/清理时机没有集中文档或断言，靠"每处记得 remove"维持（`trimConversationCache`/`protectedSessionIds` 是补丁式维护）。
- `AgentRuntimeHost` 的 `handles`/`closingSessions`/`closingFiles`/`lastOpenedSessionId`/`started` 之间靠 mutex 与调用顺序维持一致性，`started` 还是非 volatile 无锁读取（RT-030）。

建议：`ConversationState` 作为唯一真相，`ChatUiState` 改为 `combine` 派生（或删除其一）；8 张表抽成一个 `SessionUiStore` 并把清理逻辑集中；host 的 `started` 改 `AtomicBoolean` 并在类头写 5 行"锁与状态所有权"注释（项目虽不写注释，但这类并发契约值得例外）。

### C-05 死代码、未用 API 与过期声明（低）

- `ChatViewModel.consumeFinalText` 副本、`AttachmentUi.attachmentIcon`（CH-014）；
- `DdMotion` 中 6 个 0 引用 token、`OAuthLoginState.waiting`、`WorkshopViewerScreen` 不可达分支（SC-017）；
- `SessionMetaStore` 冗余 API 与 `RuntimeTypes.kt:341` 的实现（RT-028）；
- lint `UnusedResources 79` 条（GL-014）；`versionCode` 恒为 1（GL-023）；`kotlin.android` alias 从未应用（GL-017）；
- `docs/ARCHITECTURE.md` 的字体清单（5 套 → 实际 2 套）、插件状态 TTL（60s → 实际 120s，且"RunFinished 刷新"未接线）已过时（build-tests.md 漂移表）。

建议：做一次"清理提交"（删死代码/死资源/死配置 + 修正文档漂移），然后把 lint 死资源检查接进 `check` 防复发。

### C-06 命名与契约一致性（低）

- `PluginKind.INTERNAL` 的 GUI/网络搜索/安排插件与三份文档的"外部插件"描述相反（PL-017）；
- `installInternal` 命名误导（实际是"补建内部插件记录"，PL-013 附近）；
- `AgentRuntime` 是 40 方法胖接口，宿主级事件用 `runId=""` 空串哨兵（RT-022）——类型上无法表达"无 run 的事件"；
- UI/设计层 7 处硬编码中文（SC-007）；插件层约 200 处中文错误文案（部分会显示给用户，i18n 例外）。
建议：文档对齐 + 事件模型改用 `runId: String?`，其余按需。

### C-07 测试质量：核心覆盖好，关键盲区明确（中等偏上）

- 正面：1,022 用例 / 0 失败 / 0 `@Ignore`；fake 注入 + `StandardTestDispatcher` + `MutableTestClock`；断言密度高（scout 统计 1,022 用例仅 1 个无断言）；持久化/并发/协议/迁移层都有覆盖。
- 盲区：`AgentRuntimeHost`（1,083 行，并发/回收/重连）零直接测试（RT-006）；`AppGraph` 装配零测试（GL-003）；Compose UI 零测试、0 `@Preview`（SC-014）；WebView 路径零自动化；2 个 device test 不在任何流水线；无覆盖率工具（GL-013）；`ScheduleServiceTest` 有墙钟轮询与虚拟时间混用（RT-029）。
- 判断：这不是"测试写得差"，而是"测试资源集中在纯逻辑，没覆盖最贵的集成层"。补测优先级：host 的 6 个关键场景（evict→重连、closing gate、reminder 两态、stop 期间 goal 失效、事件溢出、reconfigure 排队）> AppGraph 装配冒烟 > 3 个页面的 Compose 状态渲染。
- 建议：不要追求覆盖率数字；把上述 10 个左右用例补上，配合 `architecture.md` 的 A-02 拆分，就能覆盖"最容易回归且最难手测"的部分。

### C-08 静态检查基线（低，但影响长期）

- 编译警告 13 条（多为多余 `!!`/安全调用/未使用表达式）——一次清理提交可归零；〔复核：无构建日志可复算，属未验证项〕
- lint 165 条 issue（= 160 warnings + 5 hints，不是 165 条 warning）：79 死资源、26 图标重复、12 单复数、11 UseKtx、8 依赖版本、6 真实 WebView 缺陷（GL-009）、6 Modifier 参数位置、5 装箱 State；
- 没有 detekt/ktlint/CI（GL-001/GL-002），因此"下一个 800 行文件"不会被任何工具拦下。

建议：清理 lint 到 ~40 条以内并把 `lintDebug` 挂 `check`；detekt 只开复杂度规则（LongMethod/ComplexMethod/LargeClass），用来给 C-01 的拆分提供持续压力，而不是引入全套风格规则。

---

## 4. Top 10 代码质量修复

| 顺序 | 项 | 关联 ID | 工作量 | 为什么排这个位置 |
|---|---|---|---|---|
| 1 | 损坏数据隔离策略统一（`.corrupt` + 空数据 + 日志），修 `plugins.json` 覆盖与 goals/schedules 击穿 | RT-001、PL-001、RT-014 | S | 唯一 P0 + 两个静默数据丢失，触发条件罕见但后果是数据/启动 |
| 2 | 静默失败补日志与 UI 反馈（技能列表清空、删除、保存 Key） | SC-003、RT-019、RT-013、PL-012 | S-M | 用户会据此做错误决策（重装/重填），修起来都是局部 |
| 3 | 存储迁移改为启动显式前序并原子写 | AC-001 | M | 升级首启"历史看起来没了"，是用户最敏感的数据问题 |
| 4 | 分类器：`ChatViewModel`/`DeepDeepApp` 拆分计划落地（先抽 state holder） | CH-004、AC-004、SC-001 | L | 后续所有测试/重组/a11y 修复的前置条件 |
| 5 | 抽 `DdPageScaffold`/`DdEmptyState`/`DdLoadingBlock` + 17 页面迁移 | SC-002 | M | 一次性消除最大面积的重复与漂移 |
| 6 | 抽 `AtomicJsonFile` 并迁移 4-6 个 store | A-05、C-02 | M | 减少同构代码，同时把损坏策略统一到一处 |
| 7 | 清理死代码/死资源/死配置 + 文档漂移 | C-05、GL-014、GL-017、GL-023 | S | 低风险、让基线可信 |
| 8 | `ScheduleService` 事件过滤 + 事件有序性 + mutex 拆分 | RT-004、RT-005、RT-003 | M | 机制确定的性能/并发问题，见 performance.md |
| 9 | host 关键场景单测 + AppGraph 装配冒烟 | RT-006、GL-003 | M | 补上最贵集成层的安全网 |
| 10 | lint 挂门禁 + 编译警告归零 | GL-002、C-08 | S | 让"下一次"有护栏 |

---

## 5. 明确不计入问题的项

- **源码零注释**：项目约定（用户偏好），代码命名与结构足以自解释；本文没有把"缺注释"当缺陷。
- **手动 DI、无 KMP、无权限审批系统、未开 R8**：均为有意的设计/分发取舍，只在 `architecture.md`/`performance.md`/`build-tests.md` 里分析代价，不重复计为代码质量问题。
- **中英文案以中文为主**：i18n 资源 100% 对齐，双语机制成立；只把"硬编码绕过资源"计入问题（SC-007）。

---

## 6. 结论

代码质量的问题不是"写得糙"，而是两个结构性现象：**复杂度过分集中**（6 个 800+ 行文件承担了 App 一半的行为）和**重复没有被抽象收口**（store 样板、页面骨架、双模型、双组件）。这两个现象都与本项目的开发模式一致——单人长期迭代、功能优先、每次新增都是"在最近的落点再加一段"。因此修复也应顺着这个模式：只做"抽公共件 + 拆热点"的局部手术，不引入新的框架或强制规范。

值得强调的是：底层的错误处理纪律、取消语义、数据校验、纯函数测试都是真正的好底子；只要把 C-03（静默失败）和 C-02（重复收口）这两类做掉，代码质量的实际分数会比 6.9 更早地变成 7.5+，因为剩下的主要是"长文件"这种不改变行为、可以慢慢拆的债。
