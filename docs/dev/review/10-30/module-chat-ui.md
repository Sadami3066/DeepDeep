# 聊天 UI 与 Markdown 评审

## 概览

评审范围：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/**`（31 文件，9,971 行；原文 10,758 把 `ui/questions` 的 787 行重复计入）〔复核修正〕、`ui/questions/**`（3 文件 787 行）、对应单测（10 文件 3,207 行），并用 `app/build/compose-reports/app-composables.csv`/`app-classes.txt` 核实可跳过性与稳定性，用 `docs/dev/ARCHITECTURE.md`「消息模型 / UI 与动效 / 运行时事件」核对设计意图。

总体判断：这是一个**对流式热路径做了认真设计**的聊天实现——16ms delta 合批、纯函数 reducer、有状态的 Markdown 增量规划器、流式期间跳过代码高亮与表格测量、滚动跟随时序用 layoutInfo 而非轮询、动画值放在 draw/layout 相位读、stability config 让热路径 composable 全部可跳过；单测质量高于同规模项目（`ChatViewModelTest` 用 `StandardTestDispatcher` + FakeRuntime 覆盖了批处理、对账、自动重试、LRU、`onTrimMemory`、中断提示等），`MarkdownChunkingTest` 甚至对 48 篇语料与**每个字符前缀**做整篇/分块等价断言。这些是真实亮点，不是文档口径。

主要矛盾集中在三处：
1. **状态所有权**：`ChatViewModel` 1639 行/14 个构造依赖/36 个公开方法/30 个私有可变字段承担 12 类职责；`ChatUiState`（31 字段）与 `ConversationState`（8 字段）是两套并行状态模型，且消息、输入框、模型选择、工作区列表全在一条 StateFlow 上发射——流式每个 16ms 批次都让 `ChatScreen`（54 参数）整页根重组。
2. **消息内渲染没有懒加载**：Markdown 块用 `Column + forEach` 全量组合，`LazyColumn` 的懒加载只到"消息"粒度；长回答进入视口、流式终态、关闭"过程自动折叠"时都会在主线程同步做全量解析/高亮/文本测量。
3. **解析与测量结果只活在 composition 的 `remember` 里**：`LazyColumn` 回收 item 后全部丢弃，滚动回视口重新全量解析；解析/高亮都在组合阶段主线程执行，没有可复用的缓存或异步通道。

未发现 P0（崩溃/数据丢失/ANR 级）问题。竞态与事件顺序处理（runId 过滤、终态对账、busy→idle reconcile、自动重试 token）经代码与测试核对是正确的。

## 评分

| 维度 | 分数 | 理由 |
| --- | --- | --- |
| 架构质量 | 6 / 10 | reducer 纯函数化、planner 增量状态化、SelectionCoordinator 局部化、事件按 runId 过滤都是好架构；但 `ChatViewModel` 一个类承担会话缓存/草稿/流式合并/重试/对账/附件/命令/工作区/模型/搜索/内存回收 12 类职责，14 个构造依赖、8 张私有可变表之间靠隐式不变量维系，无法单独替换或测试；`ChatUiState` 与 `ConversationState` 双模型字段重复、需要手写 7 字段双向映射；UI 包内 Markdown 解析器/规划器/高亮器与渲染强耦合，解析层无法脱离 Compose 独立演进。 |
| 代码质量 | 7 / 10 | 命名清晰、无注释但自解释、reducer 与 planner 的不可变数据模型设计干净、错误路径普遍有 `CancellationException` 透传；扣分项：死代码（`ChatViewModel.consumeFinalText` 副本、`AttachmentUi.attachmentIcon`）、`ChatScreen` 重复 import、无效 `remember`、两套错误分类逻辑（英文子串匹配 vs `ChatUiStrings.runErrorKind`）、`ChatComposer` 双输入状态、若干魔法常量散落。 |
| 性能与效率 | 6 / 10 | 热路径做对了大部分相位决策（见"亮点"），但仍有 4 处量级问题：每批次整页根重组 + `LazyColumn` DSL O(消息数) 重建；长消息无块级懒加载；流式终态一帧内同步全量高亮/表格逐 cell 文本测量；每 delta O(当前文本长度) 字符串拷贝且后台会话完全不批处理。这些在长会话/长回答/代码表格多的回答下会实打实掉帧。 |

## 亮点

1. **Markdown 增量规划器 + 逐前缀等价测试**：`MarkdownStreamPlanner` 只重扫末尾两个 chunk（`MarkdownParser.kt:200-249`），文本缩短/替换时整篇重建（`MarkdownParser.kt:141-152`）；`MarkdownChunkingTest.kt:9-82` 对 48 篇语料和流式文档的**每个字符前缀**断言"整篇解析 == 分块解析"，并断言稳定 chunk 实例被复用（`assertSame`）、CRLF 跨批边界、fence/list 跨空行不被切开。这种测试密度在 UI 层很罕见。
2. **流式期间主动跳过昂贵渲染**：代码块 `live=true` 时只渲染纯文本、不上色（`ChatMarkdown.kt:261-277`）；表格流式期间不测宽（`ChatMarkdown.kt:430-431`）；行内 code 背景在 draw 相位用 `TextLayoutResult` 自绘（`InlineCodeText.kt:36-71`）；chevron 旋转在 `graphicsLayer` 里读 `animateFloatAsState`（`ProcessSection.kt:127-132`、`ToolCard.kt:134-139`）；IME 动画值在 `Modifier.layout` 里读、不经过 `AnimatedContent`（`ChatScreen.kt:188-208`）。这些选择与 ARCHITECTURE.md 的说法一致。
3. **16ms 合批 + 非 delta 事件前 flush**：`bufferActiveDelta`/`flushPendingDeltas` 保证"工具/文本顺序、结束文本完整"，把状态发射压到 ≤60 次/秒，且有测试覆盖（`ChatViewModel.kt:1399-1434`、`ChatViewModelTest.kt:587-607`、文本→工具→文本顺序测试 `ChatViewModelTest.kt:159-200`）。
4. **reducer 纯函数 + runId 强过滤**：`reduce` 不读时间以外的外部状态，`onRun`/`finishTargetIndex` 对不认识/迟到的 runId 一律丢弃并只做"收尾 settle"（`ConversationReducer.kt:253-268, 370-373`），避免跨 run 串写；`ConversationReducerTest.kt:163-272` 覆盖未知 run、runId 不匹配的终态、清理所有 streaming 消息。
5. **滚动跟随用测量结果而不是轮询**：`ChatScrollController` 基于 `LazyListState.layoutInfo` 的 overflow 精确贴底，用户拖动通过 `interactionSource` 判定并停止跟随（`ChatScrollController.kt:31-118`）；滚动锚点写入 ViewModel 的普通 map 而不是 snapshot state（`ChatViewModel.kt:623-630`），滚动过程不触发重组。
6. **稳定性配置生效**：compose 报告显示 `ChatScreen`/`ChatTurn`/`ChatComposer`/`MarkdownBlockItem`/`ProcessSection` 等热路径 composable 全部 `skippable=1`；`classes.txt` 中 `ChatUiState` 经 `app/compose-stability.conf` 声明为 stable，非可跳过项只剩 `rememberChunkBlocks`/`rememberFileClickHandler` 这类返回值的 `@Composable` 辅助函数（`app/build/compose-reports/app-composables.csv`）。

## 问题清单

### P0

无。

### P1

**[CH-001] 流式终态会在单帧内同步执行全部代码高亮与表格单元格文本测量**（P1 性能）
· `ChatMarkdown.kt:261-277, 418-451`，触发点 `ChatTurn.kt:475`（`live = message.streaming`）
· 证据：`val highlighted = remember(code, language, colors, live) { if (live) AnnotatedString(code) else { val tokens = highlightCode(code, language) ... } }`；`TableBlock` 的 `naturalWidths` 以 `live` 为 remember key，`live=false` 时对每个列、每个 cell 调用 `textMeasurer.measure(...)` 且 `rememberTextMeasurer()` 未指定 cacheSize（默认 8 条）。
· 影响：`RunFinished` 一到，`ChatScreen` 重组 → `ChatMarkdown` 的 `live` 由 true 变 false → `MarkdownBlocks` 以新 `live` 参数重组 → **该消息所有块**的 `MarkdownBlockItem` 因参数变化全部重组 → 每个代码块在主线程跑一遍 O(代码字符数) 的 lexer（`CodeHighlighter.kt:279-341` 每字符若干 `startsWith`/`matchAt`，见 `CodeHighlighter.kt:267-341`），每张表做 R×C 次 text layout（8 条 cache 对 R×C 基本全 miss）。长代码（500 行）+ 20×6 表格的回答，这一帧主线程多出几十毫秒；历史消息滚动进视口首次组合同样付这笔钱（`live=false`）。

> 〔复核降级〕「几十毫秒」是纯估算：500 行≈15KB 的 lexer 实际亚毫秒级，20×6=140 次短文本 layout 约个位数 ms；且只有 Code/Quote/Table 块有重活，「该消息所有块」偏大。建议「每列只测最宽 cell」在逻辑上不成立（不求 max 得不到列宽），后台高亮会导致终态闪烁，`cacheSize=rows*cols` 是浪费。本条适合作为「机制备忘」，不宜当性能结论。
· 建议：高亮/测宽改为可缓存的后台计算（`produceState(Default)` + 按 `(code, language)`、`(table, style, width)` 做 LRU）；`rememberTextMeasurer(cacheSize = rows*cols)` 并按列复用 1 次测量宽度（同一列宽度只取最宽 cell，但当前实现每 cell 都测）；至少要把 `live` 的变化合并到一帧之外（例如流式结束后 `LaunchedEffect` 里置 finished 再渲染）。
· 工作量：M

**[CH-002] 单一大 `ChatUiState` 让每个流式批次都整页根重组，并 O(消息数) 重建 LazyColumn DSL**（P1 性能）
· `ChatScreen.kt:212`（`val messages = state.messages`）、`ChatScreen.kt:338-388`（`items(messages)`）、`ChatViewModel.kt:1370-1387, 1425-1434`
· 证据：`ChatViewModel` 只有一条 `mutableState: MutableStateFlow<ChatUiState>`；`applyPendingDeltas` 每 16ms `mutableState.update { it.applyConversation(reduced) }` 发射一个全新的 31 字段 `ChatUiState`。`ChatScreen` 的函数体直接读 `state.messages`/`state.input`/`state.sending`，因此每批次 `ChatScreen`（54 个参数）整体重执行：`items(messages)` 的 DSL 为 N 条消息重建 interval/key/item content lambda；文件末尾 `stablePendingQuestions.firstOrNull { pending -> messages.none { ... steps.any { ... } } }` 做 O(P·N·S) 扫描；`ChatComposer` 的 `onSend = { if (!state.sending) {...}; onSend() }` 与 `onVoiceClick = onVoiceClick?.let { ... }` 捕获整个 `state`/不可记忆化，导致 composer（含 `BasicTextField` 与每次新建的 `SlashCommandColor(colors.accent)`）也在每批次重组。
· 影响：单批次成本 O(N)（N = 消息数），60 批次/秒；消息数上百后就是持续的主线程分配与组合开销，且打字（`onInputChange` 同样发射整条状态）与流式互不隔离：输入一个字也会让整页包含消息列表的作用域失效。
· 建议：拆状态通道——`messages` 单独 `StateFlow`/子状态，`input` 本地提升或单独 flow；把 `ChatTopBar`/`ChatComposer`/`WorkingIndicator`/`ContextMeter` 移到只读稳定入参的子 composable；把 `messages` 列表直接传给只包含 `LazyColumn` 的子组合，使流式批次只失效列表与尾部 item；`onSend` 只捕获 `sending` 布尔值而非整个 state；`LaunchedEffect` 的 key 预先 `remember`。
· 工作量：M

**[CH-003] 长 assistant 消息没有块级懒加载：一条消息的全部 Markdown 块一次性组合与测量**（P1 性能）
· `ChatMarkdown.kt:86-90, 115-126`、`ChatScreen.kt:344`、`ChatTurn.kt:442-506`
· 证据：`val blocks = ArrayList...; plan.chunks.forEach { chunk -> blocks += rememberChunkBlocks(chunk) }`，随后 `MarkdownBlocks` 用 `Column + blocks.forEach { MarkdownBlockItem(...) }` 组合；外层是 `LazyColumn` 的**一个 item**（`items(messages, key = { it.id })`）。
· 影响：一条长回答（上百段/列表/代码块，agent 回答很常见）在该 item 进入视口时要一次性组合并布局全部块，每块至少一次 `Text` 的 text layout；100+ 块即数十到上百 ms 的一帧卡顿，滚动进出视口、切换会话、以及流式开始前都会命中。`LazyColumn` 的懒加载在这里完全失效，长会话里"翻到一条长回答"是最容易掉帧的交互。
· 建议：把消息内容拆成块级列表（消息内用一个 `LazyColumn`，key 用 `messageId:blockIndex`〔复核勿做：同轴 LazyColumn 嵌套在 LazyColumn 的 item 内是**无界高度**，会触发非法约束/必须固定高度，不能直接实现〕；或把块展平成外层 LazyColumn 的 item（跨 `ChatTurn`+`ChatMarkdown`+选择/引用/脚注链路的改造，成本应为 L 而不是 M）），或对超长消息做"只渲染前 N 块 + 展开全部"；至少不要在一个 item 内 `Column` 全量组合。
· 工作量：M

**[CH-004] `ChatViewModel` 1639 行 / 14 个构造依赖 / 12 类职责，状态所有权过载、双状态模型重复**（P1 架构）
· `ChatViewModel.kt:65-101`（构造与 30 个私有可变字段）、`ChatUiState.kt:13-49`、`ConversationReducer.kt:18-27`
· 证据：公开方法 36 个、私有方法 51 个；职责横跨会话打开/切换（`openSession/switchToSummary/createSession`）、会话缓存与 LRU（`conversationCache/sessionDrafts/trimConversationCache/onTrimMemory`）、草稿与引用/附件（`quote/attach/launchAttachmentImport/normalizeAttachments`）、流式合批（`bufferActiveDelta/flushPendingDeltas`）、终态对账（`reconcile/scheduleReconcile`）、自动重试状态机（`maybeAutoRetry/beginRetry/clearAutoRetry` + 4 张表）、命令与技能展开（`expandAndSend/expandInlineAndSend`）、工作区 CRUD、模型/思考档位、用量/压缩、会话搜索、标题本地化。`ChatUiState` 的 `messages/sending/activity/activityStartedAt/usage/conversationTitle` 与 `ConversationState` 同名同义，靠手写 `conversationState()`/`applyConversation()` 双向映射（`ChatViewModel.kt:1436-1453`）。
· 影响：8 张私有表（cache/drafts/openJobs/reconcileJobs/autoRetry×4/pendingDeltas/2 个 notice set）之间的不变量（"打开中的会话事件不能覆盖新状态""retry 期间忽略旧 run 的 ActivityChanged"等）只存在于实现顺序里，任何一处漏掉 guard 就是竞态；且这些逻辑只能在 VM 内通过 FakeRuntime 注入事件测试，无法独立替换/复用（例如把会话缓存策略改成磁盘缓存、或给聊天加第二套 UI 入口都要动这个类）。ARCHITECTURE 已把"事件是快速通道而非唯一真相"作为设计原则，但实现层缺少对应的"状态容器"抽象来承载。
· 建议：按职责拆出 `ConversationStore`（cache/drafts/evicted 策略）、`DeltaBatcher`（16ms + flush 语义）、`TurnRetryController`（attempt/token/retiredRunId 状态机）、`AttachmentSender`、`WorkspaceListController`；VM 只做事件路由与装配。把 `ConversationState` 提升为唯一真相，`ChatUiState` 改为 `combine` 派生的 UI 投影，消除 7 字段双向映射。
· 工作量：L

### P2

**[CH-005] 每个 delta 做 O(当前文本长度) 字符串拼接 + 整列表拷贝；16ms 批次只合并了状态发射，没合并 reducer 计算；后台会话完全不批处理**（P2 性能）
· `ConversationReducer.kt:76-100, 285-293`、`ChatViewModel.kt:1425-1434, 1388-1391`
· 证据：`updateAssistant` 里 `messages.toMutableList()`、`steps.toMutableList()`、`last.copy(text = last.text + event.text)` —— 每次 delta 都新建一个长度为 L 的 String 并复制整个 messages/steps 列表；`applyPendingDeltas` 对批内每个事件**逐个** `reduce`（`events.forEach { snapshot = reduce(...) }`），批内 k 个 delta 就是 k 次列表拷贝 + O(k·L) 字符复制；非当前会话走 `else` 分支直接 `reduce` + `storeConversation`（`ChatViewModel.kt:1388-1391`），**连 16ms 合并都没有**，后台 run 的每个 token 都触发一次完整 reducer + 缓存写入。
· 影响：20KB 回答 / 2000 个 delta ≈ 累计 2×10⁷ 字符复制 + 2000 次列表分配，加上后台会话并发时成倍放大；GC 压力和主线程耗时随回答长度线性增长（总量 O(N²/deltaSize)），在低端机上是流式卡顿的主要来源之一。

> 〔复核降级〕2×10⁷ 字符≈40MB 累计流量摊到整段回答，不是「卡顿主因」；项目自身 round41 实测流式 4.17% janky，不支持该量级判断。批内 `StringBuilder` 合并（建议 1/3）安全有效，其余降为观测项。
· 建议：批内先按 `(runId, TextDelta|ReasoningDelta)` 用 `StringBuilder` 合并成一次 reduce（把 k 次拼接降为 1 次）；后台会话并入同一个 per-session 批次器（定时 flush 到 cache，不需要重组）；长期可把"流式中的尾部文本"从不可变 `String` 改为追加型 buffer，只在批边界生成不可变快照。
· 工作量：M

**[CH-006] Markdown 解析/块缓存只活在 composition 的 `remember` 里：LazyColumn 回收后滚动回来要重新全量解析，且解析在组合阶段主线程同步执行**（P2 性能/资源）
· `ChatMarkdown.kt:74-100`、`MarkdownParser.kt:141-152, 253-254`
· 证据：planner 是 `remember { MarkdownStreamPlanner() }`、`remember(text) { planner.plan(text) }`、每块 `remember(chunk) { parseMarkdownChunk(...) }`；没有 messageId/文本 hash 级的外部缓存。`LazyColumn` 回收视口外 item 时这些 remember 全部销毁，滚回来重新 `plan`（首帧 `rebuild`：`split('\n')` + `extractDefinitionLines` 全量）和 `parseMarkdownChunk`（`text.split('\n')` + 逐块解析 + InlineScanner）。
· 影响：长会话里上下滚动会反复在主线程解析同一批长消息；打开含多条长回答的历史会话时，可见的几条消息在同一帧内串行解析（数十 KB 的 markdown 常见），会话打开卡顿与滚动掉帧；极端长消息（数百 KB，如把长日志当回答）在组合阶段同步解析可能造成数百 ms 卡顿。
· 建议：把 `MarkdownStreamPlan` + chunk blocks 缓存提升到 ViewModel/单例（key = `messageId:contentHash`，LRU），解析跑在 `Dispatchers.Default`（`produceState`），composition 只读结果；滚动回来直接命中缓存。〔复核：收益被高估——单块解析是毫秒级，项目自测 10KB<50ms；VM 级缓存会放大内存，并给流式带来顺序/闪烁风险，优先级应降。〕
· 工作量：M

**[CH-007] `ProcessSection` 未把 `live` 传给 `ChatMarkdown`：关闭"过程自动折叠"时，流式文本每批次都在主线程全量高亮/测表**（P2 性能）
· `ProcessSection.kt:191-195`（`ChatMarkdown(text = step.text.trim(), modifier = ..., onFileClick = ...)`，无 `live` 参数）；对照 `ChatTurn.kt:475` 的 answer 传了 `live = message.streaming`
· 证据：`ChatMarkdown(live = false)` 是默认值，`CodeBlock` 的 `remember(code, language, colors, live)` 在 code 增长时每次失效并重新 `highlightCode`，`TableBlock` 每次重新逐 cell 测宽。
· 影响：用户关闭"过程自动折叠"（`autoCollapseProcess=false`）后，过程区常驻展开，流式文本落在这里，于是每个 16ms 批次都做一次全量代码高亮/表格测量，长代码过程文本会持续掉帧；这与 CH-001 是不同触发路径。
· 建议：把 `live = message.streaming`（或"该 step 是否为尾部且在增长"）传给 `ProcessSection` 内的 `ChatMarkdown`；更好的做法是让 `ChatMarkdown` 自己根据"文本最近是否变化"推断 live，避免调用点漏传。
· 工作量：S

**[CH-008] `ToolCard` 展开时把完整工具输出一次性交给 Text 排版**（P2 性能）
· `ToolCard.kt:193-205`
· 证据：`Text(text = output.trim(), modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(...), ...)` —— `heightIn`/`verticalScroll` 只限制可视高度，`Text` 仍需对完整字符串做一次 text layout（并持有整段 `String`）。
· 影响：bash/read 等工具输出可达数十到数百 KB〔复核修正：上游已截断——bash/read 2000 行 / 51,200B、MCP 20KB，实际上限≈50KB，「数百 KB」不成立〕，展开一次就是一次 O(output) 的排版（主线程）；长输出也会加重对象内存。
· 建议：超过阈值（如 16KB）截断显示 + "展开全部"（再分段/分页），或改用惰性文本查看器；复制操作仍可用完整 output。
· 工作量：S

**[CH-009] 消息数增加时无条件跳到底部并恢复 follow：用户上翻阅读历史会被定时提醒/通知/后台轮次/steer 准入强制拉回底部**（P2 正确性）
· `ChatScreen.kt:258-263`，配合 `ChatScrollController.kt:46-63`（`jumpToBottom` 第一行 `following = true`）
· 证据：`LaunchedEffect(state.conversationId, state.messages.size) { if (state.conversationId == knownSessionId && size > knownCount) controller.jumpToBottom(animate = false) ... }`。`TurnInjected`（定时提醒）、`NoticeEvent`（压缩/目标提示）、`BackgroundTurnStarted`（目标续跑/后台任务）、`UserInputAdmitted`（steer 被准入）都会追加消息。
· 影响：用户正在向上翻看历史时，来一条提醒/notice/后台轮次，视图会瞬间跳到底部并重新进入跟随状态，丢失阅读位置。属于用户可见的滚动竞态（"滚动锚点"设计里 `ScrollAnchor.atBottom` 本意是恢复现场，但这条 effect 会覆盖它）。
· 建议：仅当 `controller.following == true` 或本次增长来自用户自己发送（`startTurn` 可带标记）时才自动跳底；其它类型增长只更新 `knownCount`，保留用户位置并让"跳到最新"气泡提示。
· 工作量：S

**[CH-010] 两套错误分类逻辑并存且口径不同：`userFacingError` 英文子串匹配 vs `ChatUiStrings.runErrorKind`**（P2 代码质量）
· `ChatViewModel.kt:1556-1566`、`ChatUiStrings.kt:68-100`
· 证据：`userFacingError` 用 `message.contains("API key") || message.contains("401")`、`"429"`、`"timed out"`、`"Network"`、`"500"/"503"` 判类；`runErrorKind` 是另一份更完整的枚举分类（含 `负数余额`、`模型不可用`），`reduce`/`reconcile` 走的是后者。同一条错误若发生在 `send` 抛异常路径走 `userFacingError`，发生在 run 终态走 `runErrorKind`，展示文案可能不同；〔复核修正：`send` 异常实际走 `startTurn` catch→`finishRunError`→`runError`（`ChatViewModel.kt:536-541/1455-1468`）；`userFacingError` 用于初始化/steer/工作区/模型/打开会话，不是 send 路径。两套分类并存成立，但「同一 provider 错误两入口文案不同」基本不成立；直接委托 `runErrorKind` 会让工作区错误变「请求失败」，P2→P3。〕且依赖 Provider 英文原文，中文化或换 SDK 文案即失配。
· 影响：错误提示不一致/误判（例如 "Network is unreachable" 大小写或本地化后不再命中），属于会直接暴露给用户的错误静默降级。
· 建议：统一为一个 `ErrorClassifier`（基于错误码/结构化异常优先、原文兜底），`userFacingError` 删除或委托给 `runErrorKind`；补一条测试断言两条入口对同一错误产出一致文案。
· 工作量：S

**[CH-011] 测试缺口：滚动跟随/Composer 双状态/长消息渲染/后台并发流式无覆盖**（P2 测试）
· `app/src/test/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModelTest.kt:60`、`MarkdownChunkingTest.kt:9`；全仓库仅 2 个 instrumented 测试（`app/src/androidTest/...`），均不在聊天页
· 证据：VM 层覆盖良好（`StandardTestDispatcher`、FakeRuntime 事件注入、`advanceTimeBy`、约 221 处断言〔复核修正〕、LRU/`onTrimMemory`/autoRetry/reconcile/interrupted notice 均有测试），但：① `ChatScrollController`/`ChatScreen` 的滚动跟随与 CH-009 跳底策略没有组合级测试，只能人工验证；② "后台会话流式 + 切回" 只有 `backgroundRunKeepsStreamingAfterSwitchingAway`（`ChatViewModelTest.kt:479`）一条正向用例，没有覆盖 `openSession` 用 history 快照覆盖 cache 期间的 delta 窗口（`ChatViewModel.kt:1132-1206`）、`switchToSummary` 与 pending delta 的交错；③ `CodeHighlighter` 的词法正确性、`TableBlock.distributeRemaining` 的宽度分配、`MarkdownImage` 的缓存 key/采样逻辑均无单测。〔复核修正：`CodeHighlighter` 已有测试——`MarkdownParserTest.kt:282-315` 的 `highlightsKotlinTokens`/未闭合字符串/未闭合注释/diff 用例；「仅 2 个 instrumented 测试」实为 2 文件 4 个 `@Test`。〕
· 影响：最容易回归的恰好是交互时序（滚动/输入/切换会话）与渲染正确性（高亮/表格宽度），当前这些只能靠人工试。
· 建议：给 `ChatScrollController` 抽一个不依赖 Compose 的纯逻辑层做单测；为 `openSession` 覆盖"历史读取窗口内到达 delta"的确定性测试（FakeRuntime 在 `history()` 里注入新事件）；补 `CodeHighlighter`/`distributeRemaining` 纯函数测试（成本极低）。
· 工作量：M

### P3

**[CH-012] 滚动跟随链路每批次 2 帧空转：`followContent` 内两次 `withFrameNanos` + 最多 2 轮 scrollBy，与 16ms 批次叠加后几乎每帧都在等帧**（P3 性能）
· `ChatScrollController.kt:31-35, 87-118`、`ChatScreen.kt:252-256, 265-269`
· 证据：`followContent` → `awaitSettled()`（`withFrameNanos{}` ×2）→ `settleByOverflow`（`repeat(2)` 内再 `withFrameNanos`）；`ChatScreen` 同时挂着 `snapshotFlow { latestMessages }`、`snapshotFlow { animatedBottomInset.value to composerHeightPx }`、`LaunchedEffect(state.sending)` 三条跟随入口。
· 影响：跟随最多滞后 2-3 帧（约 30-50ms），用户感知为"流式文本贴底慢一拍"；`withFrameNanos` 空转本身不贵，但三条入口在 IME 动画/流式期间叠加调度。
· 建议：跟随改为在 `LazyListState` 布局后一次完成（overflow 直接 `scrollBy`，不需要等待 2 帧，因为 `layoutInfo` 已是上一帧结果）；合并三条入口为一条 `snapshotFlow`。

> 〔复核勿做〕前半句方向反了：内容变更后布局尚未发生，用上一帧 `layoutInfo` 算 overflow 会导致贴底失败/回弹——`withFrameNanos` 正是等新布局。**只做「合并三条入口」**，保留至少一帧等待。
· 工作量：S

**[CH-013] `MarkdownImage` 内存缓存以 `source` 为 key、忽略目标宽度；decode 的 `targetHeight` 又额外乘 2**（P3 资源）
· `MarkdownImage.kt:228-233, 318-338`
· 证据：`memory.get(source)` 命中即返回（与 `targetWidthPx` 无关），`produceState(key2 = targetWidthPx)` 因宽度变化重启后仍拿旧尺寸 bitmap；`sampleSize` 里 `val targetHeight = (targetWidthPx * 2)`，而调用方 `targetWidthPx = (widthPx * 2).coerceAtLeast(1)` 已经乘过 2，实际解码上限约为屏宽的 4 倍。
· 影响：旋转/分屏后图片可能被拉伸或超采样；〔复核修正：`ContentScale.Fit` 下不会「拉伸」，decode 也不放大；真实问题是 key 忽略宽度导致同图不同尺寸共用一份 bitmap，以及两次乘 2 造成过度解码。给出的「key 加 targetWidthPx」会让同一张图产生多份副本，反而增内存；应按尺寸分桶。〕24MB 图片缓存 + 8MB 附件缩略图缓存 + 每张图 ~4 倍必要的像素，低内存设备更易触发 GC/trim。
· 建议：cache key 用 `(source, targetWidthPx)` 或尺寸分桶；修正采样目标（宽度 = 容器宽 × 屏幕密度，高度按比例，不要二次乘 2）。
· 工作量：S

**[CH-014] 死代码与无效代码**（P3 可维护性）
· `ChatViewModel.kt:1544-1554`（`consumeFinalText` 私有副本从未被调用，真正使用在 `ConversationReducer.kt:338`）、`AttachmentUi.kt:512-515`（`attachmentIcon` 未使用）、`ChatScreen.kt:213`（`stablePendingQuestions = remember(pendingQuestions) { pendingQuestions }` 是恒等 remember，没有稳定化效果）、`ChatScreen.kt:22/32、51/53、52/54`（`padding`/`mutableStateOf`/`remember` 重复 import）
· 影响：读者会误以为 `ChatViewModel.consumeFinalText` 是终态文本处理的另一处真相；恒等 remember 会让人误判 `pendingQuestions` 的身份稳定性。
· 建议：删除；`stablePendingQuestions` 的意图若是不随 `state` 变化而重新分配，应改为 `rememberUpdatedState` 或直接从调用方稳定引用。
· 工作量：S

**[CH-015] `refreshSessions()` 无合并：`SessionListChanged/SessionRemoved/SessionArchived` 每个事件 launch 一次全量刷新（workspaces + allSessions + archivedSessions 三次 IO + 全量标题映射 + 状态发射）**（P3 性能）
· `ChatViewModel.kt:1346-1362, 1470-1494`
· 证据：三个分支都是 `viewModelScope.launch { refreshSessions() }`，没有 conflate/debounce；`refreshSessions` 末尾还会 `cacheCurrentSession()`。批量归档/删除或插件批量写会话时事件成串到达，会并发跑多次全量刷新。
· 影响：批量操作时多次重复 IO 与全量列表映射，且每次发射整条 `ChatUiState` 触发一次全屏重组。
· 建议：用一个 `MutableSharedFlow(extraBufferCapacity=1, onBufferOverflow=DROP_OLDEST)` + 单个 collector 合并刷新；或 `debounce(100)`。
· 工作量：S

**[CH-016] `ChatComposer` 双输入状态：本地 `TextFieldValue` + VM `input`，外部值变化时把光标强制移到文本末尾**（P3 代码质量）
· `ChatComposer.kt:139-146`
· 证据：`var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }` + `LaunchedEffect(value) { if (textFieldValue.text != value) textFieldValue = TextFieldValue(value, TextRange(value.length)) }`。打字路径是幂等的，但 `QueuedInputsDropped` 合并文本、切会话恢复草稿、发送清空等外部变更会重置光标到末尾、丢失选区；IME 组合态下被外部覆盖还可能打断组合。
· 建议：单一来源（`TextFieldState` 或把 `TextFieldValue` 提升到 VM/ChatUiState），外部变更时保留原 selection 并 coerce 到新长度。
· 工作量：S

**[CH-017] "引用/朗读"通过复制到剪贴板取选中文本：污染用户剪贴板且依赖 Copy 菜单项存在**（P3 正确性）
· `SelectionActions.kt:181-187, 220-237`
· 证据：`copySelection()` 先 `copyItem?.onClick?.invoke(session)`，再读 `clipboard.primaryClip`；引用与朗读两个按钮都调用它。若平台/Compose 版本不提供 `CopyKey`（或用户禁用了复制），返回 null 则静默无反应。
· 影响：用户在菜单里点"引用"会意外覆盖系统剪贴板内容（无提示）；将来 Compose context-menu API 变化会静默失效。
· 建议：优先从 `TextContextMenuDataProvider`/`TextToolbar` 的选中文本 API 取文本；〔复核：Compose 公开 API 没有读取选区文本的接口，该建议**不可实现**；可行做法是自定义 `TextToolbar` 或复制后恢复剪贴板（13+ 恢复会触发系统提示）。〕保留剪贴板兜底时至少在非 Copy 动作里恢复原剪贴板或提示。
· 工作量：S

## 架构评估

**做对的部分**：事件 → reducer 的单向数据流是清晰的；`reduce` 是纯函数（除 `now` 参数外无副作用），`ConversationState` 不可变，`onRun` 用 runId 做闸门，使"迟到/重复/跨会话事件"这类最常见的流式 bug 被结构性挡住。`ChatScrollController` 把滚动策略抽成了可读的独立类，`SelectionCoordinator` 用 `staticCompositionLocalOf` 把选择状态限制在局部，`MarkdownStreamPlanner` 把增量解析从渲染中剥离成有状态对象——这三处都体现了"把状态放到最低必要所有者"的意识。UI 层没有权限/审批相关的耦合，符合项目设计理念。

**主要架构问题**：
1. **ViewModel 是"上帝对象"**：14 个构造依赖意味着任何新功能都往这里加；`ChatUiState` 31 字段 + `ConversationState` 8 字段双轨，字段同义重复（messages/title/sending/activity/activityStartedAt/usage/sessionModel），映射函数手写（`ChatViewModel.kt:1436-1453`）。这直接导致 CH-002 的整页重组，也让"输入框状态"这种纯 UI 状态被放进会话状态，打字与流式互相污染。
2. **解析层与渲染层同包同层**：`MarkdownParser`（1193 行，含 planner）、`ChatMarkdown`、`CodeHighlighter` 都放在 `ui/chat` 且 `ChatMarkdown` 直接调用 `planner.plan`/`parseMarkdownChunk`。解析是可独立测试的纯逻辑（现有测试也证明了），却被 composition 生命周期绑定（CH-006），缓存无法跨 item/跨页面复用。应把解析/规划/高亮抽到一个无 Compose 依赖的 `markdown` 子包或模块，UI 只消费 `MarkdownDocument`/`RenderedInline`。
3. **缓存/对账/重试状态机的隐式不变量**：`conversationCache`、`sessionDrafts`、`sessionOpenJobs`、`reconcileJobs`、`autoRetry*`、`pendingDeltas`、两个 notice set 共 8 张表，互相之间的时序约束只靠代码顺序保证，没有类型或单一状态机承载；CH-004 已列。
4. **UI 组件体量**：`ChatScreen` 54 参数、9 个 `LaunchedEffect`、3 个 `snapshotFlow`〔复核修正〕，把滚动跟随、锚点保存、选择清理、IME inset、palette 联动全塞在一个函数里；`ChatComposer` 614 行把模型选择 sheet、队列条、引用条、附件条、输入框全放一处。它们能工作，但任何一处交互调整都要在 600+ 行函数里做。

## 性能与效率

按热路径给出机制与量级（流式 16ms 批次 = 最多 60 次/秒）：

1. **每批次固定成本 O(消息数)**：`ChatUiState` 发射 → `ChatScreen` 54 参数函数体重执行 → `LazyColumn` 的 `items(messages)` DSL 重建 N 个 interval + key + item lambda；`stablePendingQuestions.firstOrNull { messages.none { steps.any } }` 是 P·N·S 扫描。N=200 时每批次数百次分配，60 次/秒；输入一个字符同样付这笔钱（`onInputChange` 走同一条 StateFlow）。→ CH-002。
2. **尾部 Markdown 处理**：每批次 `planner.plan(text)` 的 `scanTail` 从前两个 chunk 起重扫 + `addChunk` join 出新 String + `parseMarkdownChunk` 重解析增长中的块 + 行内 `InlineScanner` 全段扫描 + 段落 `Text` 全段 layout。成本 O(当前未闭合块长度)，单段长回答时总量 O(N²)；单块 5KB 时每帧约 0.3-1ms 解析 + 5KB 分配，尚可接受，但它是线性于"未闭合块"的，遇到超长单段/长代码块会放大。`MarkdownChunkingTest` 保证了正确性。
3. **流式终态尖峰**：`live` true→false 一帧内对该消息所有块做代码高亮（O(代码字符数) lexer）与表格逐 cell text layout（R×C 次，measurer cache 8 条基本全 miss）。500 行代码 + 20×6 表格约几十 ms 主线程。→ CH-001。
4. **长消息无懒加载**：块级全量组合/测量发生在 item 进入视口时，100+ 块 = 数十至上百 ms 单帧。→ CH-003。
5. **文本累积成本**：每 delta O(L) 字符串拷贝 + 整列表拷贝，后台会话每 token 一次完整 reducer；20KB 回答累计约 2×10⁷ 字符复制。→ CH-005。
6. **解析缓存生命周期**：只活在 composition `remember` 中，滚动回收即失效；解析在主线程组合阶段。→ CH-006。
7. **图片**：`produceState` + `Dispatchers.IO` 解码、按目标宽度采样、24MB bitmap LruCache、8MB 缩略图 LruCache（`MarkdownImage.kt:224-338`、`AttachmentUi.kt:534-549`）——主线程无 IO，这是对的；扣分在 cache key 忽略宽度与采样尺寸偏大（CH-013）。附件预览文本上限 256KB、图片预览上限 2048px 也有护栏。
8. **滚动**：跟随基于 `layoutInfo` 精确 overflow，用户上翻停止跟随，锚点写普通 map 不触发重组（`ChatViewModel.kt:623-630`）——机制对；问题是每批次 `awaitSettled` 2 帧导致贴底滞后 2-3 帧（CH-012）与消息数增长无条件跳底（CH-009）。
9. **相位决策**：IME 动画值在 `Modifier.layout` 读（`ChatScreen.kt:195-208`）、行内 code 背景在 draw 画（`InlineCodeText.kt:36-71`）、chevron 旋转在 `graphicsLayer` 读（`ProcessSection.kt:127-132`）、选择菜单 bounds 写 `Rect`（有 equals，滚动帧不触发重组）。没有发现"动画逐帧驱动整页重组"或"在 composition 里读取滚动状态"的问题；`imeAnimationTarget` 在 composition 读是目标值（动画期间恒定），可接受。
10. **Selection**：每条消息一个 `SelectionContainer` + `Popup`；选区清理用根节点 Initial pass 命中测试（`ChatScreen.kt:289-297`），没有逐帧监听。开销可忽略。

## 优先修复建议（Top 5）

1. **拆状态通道消除每批次整页重组（CH-002，M）**：`messages` 独立为子 `StateFlow`，`ChatScreen` 内把 `LazyColumn`、`ChatComposer`、`ChatTopBar` 分别改成只读稳定入参的子组合；`onSend` 等 lambda 只捕获必要值。这是收益最大且不改变行为的改动：流式批次从 O(消息数) 降为 O(1) + 一个列表 item 的失效。
2. **长消息块级懒加载（CH-003，M）+ 终态高亮/测表移出主线程（CH-001，M）**：两者一起做——块级 Lazy 后"进入视口才解析/高亮/测宽"，再配 `produceState(Default)` 与 `(code,language)`/`(table,width)` LRU，可同时消掉长回答卡顿与流式终态尖峰。
3. **合批内文本合并 + 后台会话并入批次（CH-005，M）**：批内 `StringBuilder` 合并 k 个 delta 后一次 reduce；后台 run 也走 16ms 批次写 cache。改动局部、无行为风险，直接降 GC 与主线程耗时。
4. **拆 `ChatViewModel` 的会话缓存/重试/附件/工作区职责（CH-004，L）**：先动风险最低的三块——`DeltaBatcher`、`TurnRetryController`、`ConversationStore`（cache+drafts+openJobs，接 CH-009 的滚动锚点语义），把 8 张私有表收敛为 2-3 个有明确 API 的状态容器；`ChatUiState` 改为派生投影。
5. **修正滚动跟随语义（CH-009 + CH-012，S）**：新消息只在 "following==true 或本人发送" 时跳底；合跟随入口、去掉每批次 2 帧空转。这是用户可直接感知的体验问题，且改动量最小。
