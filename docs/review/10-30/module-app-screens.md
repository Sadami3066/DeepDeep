# App 功能页面与设计系统评审

评审范围：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/` 下除 `chat/`、`questions/`、`navigation/`、`DeepDeepDrawer.kt` 外的全部页面（52 个文件，11,417 行，127 个 `@Composable`），以及 `design/` 模块全部 14 个文件（1,940 行；原文 2,439 与 `code-quality.md` 自相矛盾）〔复核修正〕。所有问题均已在代码中核实；标注"推测"的条目说明依据与不确定性。项目约定（无注释、手动 DI、单 Activity + 自研导航、双语资源、不做权限审批）不作为问题。

## 概览

页面层整体完成度高：设置层级、厂商/OAuth、技能、MCP、插件、工坊、定时任务、后台任务、目标条、文件浏览、语音、归档/搜索全部可用，视觉与交互基本统一落在 `design` 自有组件上（无 Material 默认 AlertDialog/Switch/CircularProgressIndicator）。编译报告显示本次范围内 127 个 Composable **全部 skippable + restartable**，说明稳定性配置与状态拆分在编译层面是干净的。〔复核修正：不成立——范围内实际报告 128 行，只有 111 个 skippable+restartable；17 个返回 `String`/`Color` 的 `*Label`/`*Subtitle` 辅助函数不可跳过，这是 Compose 正常现象，不应作为结论依据。〕

主要债务集中在三处：

1. **页面没有 screen state holder**：页面直接把 `SkillRepository`/`PluginManager`/`McpConfigStore`/`ScheduleService`/`WorkshopStore` 当 Composable 参数，在 UI 内用 `remember + rememberCoroutineScope` 编排"加载→保存→错误"状态机；10+ 个页面各写一遍，导致无法 `@Preview`/测试，且错误处理行为已经漂移。
2. **模板重复**：17 个页面复制同一段 64dp 顶栏（返回键 + 标题）与整页 `Column + verticalScroll` 骨架，7 处空态、5 处加载块、3 处 `SettingsGroup` 卡片各自实现；4 份 sheet 的 `fillMaxHeight` 比例从 0.66 到 0.94 不等，同类问题已产生可见不一致（见 SC-011）。
3. **可访问性系统性欠账**：设计系统默认触控目标 36–44dp（低于 48dp）、`DdListRow` 用 `contentDescription=title` 覆盖合并语义、行内嵌套开关 7 处、sheet 关闭按钮写死中文"关闭"，这些是组件 API 层问题，会在每个页面重复放大。

WebView（创意工坊）是本次范围内质量最高的子系统：生命周期销毁、renderer 崩溃重建、bridge origin/main-frame 双重校验、`allowFileAccess=false`/`mixedContent NEVER_ALLOW` 都做对了；剩余边界问题是外部跳转未做 scheme 白名单（SC-013）。

## 评分

| 维度 | 分数 | 理由 |
|---|---|---|
| 架构质量 | 6 | 模块依赖方向干净（app→design/runtime/shell，design 零反向依赖），导航/状态保存/动效分层有明确设计与文档；但页面层普遍绕过状态持有者直接编排数据层，UI 与 Store/Manager 强耦合、不可预览不可测，且同一加载/保存/错误流程在 12+ 页面重复实现。 |
| 代码质量 | 6 | 命名、结构、资源化文案整体规范，纯函数过滤逻辑已抽到可测文件；扣分项：模板重复与行为漂移、硬编码中文/中文标点、表单草稿不可保存、错误被吞成空数据、可访问性语义缺失。 |
| 性能与效率 | 7 | 编译期无不稳定参数、无逐帧状态读取，MCP 探测缓存、WebView 事件 40ms 合批、字体 IO 预热等关键点做得好；扣分项：22 个页面用非虚拟化列表、复合字体每次冷启动重拷 4.4MB、字体失败静默回退、JobsSheet 每秒整页重组、主题 TextStyle 每次读取重建。 |

## 亮点

1. **WebView 生命周期与安全基线扎实**：`onRelease` + `DisposableEffect` 双路销毁，renderer 崩溃后 `crashKey` 重建并保留错误页，`onRenderProcessGone` 返回 true 防进程崩溃；`allowFileAccess=false`、`allowContentAccess=false`、`MIXED_CONTENT_NEVER_ALLOW`；bridge 只在 `isMainFrame` 且 `sourceOrigin == origin` 时处理消息，document-start 脚本也按 origin 注入。见 `app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:126-135,164-172,207-217,271-298`、`WorkshopBridgeHost.kt:54-59,84-89`。
2. **Compose 编译质量好**：`app/build/compose-reports/app-composables.csv` 中本次范围 127 个 Composable 全部 skippable/restartable；`app/compose-stability.conf` 把 runtime 模型、UI state、`AttachmentRef`、`File` 声明为 stable，热路径稳定性治理有据可查。
3. **列表 key 与生命周期收集有正确示范**：`ArchivedSessionsScreen.kt:142-147`、`SessionSearchScreen.kt:177-197` 的 `LazyColumn` 均带稳定 key；`ScheduleScreen.kt:49`、`McpEditor.kt:308` 等使用 `collectAsStateWithLifecycle`。
4. **MCP 探测的缓存/失效策略**：只有 stale 或字段签名变化的服务器才重新探测，60s TTL + `probeSignature()`（URL/headers/auth/timeout 全参与），并有 `McpProbeCacheTest` 单测；见 `McpScreen.kt:110-133,393-446`。
5. **技能 zip 安装的 IO/内存边界处理正确**：`readArchive` 全程 `Dispatchers.IO`、先读 `OpenableColumns.SIZE` 预判、按 32MB 上限流式读取、超限再拒；见 `SkillsScreen.kt:580-631`。
6. **设计系统 token 完整、暗色映射齐全，组件全部自绘统一**：`DeepDeepTheme.kt:25-111` 的 20+ token 与 Material 色彩桥接完整；`DdSwitch`/`DdSegmentedControl`/`DdDialog`/`DdSheet`/`DdSpinner`/`DdTypingDots` 均无 Material 默认控件泄漏；纹理由 `DdHairline` 统一 0.45 alpha。

## 问题清单

### P0（崩溃/数据丢失/ANR/内存泄漏/正确性错误）

未发现。

### P1（明显性能损耗、资源泄漏、错误静默失败、已造成维护痛苦的重复/耦合）

**[SC-001] 页面层直接持有 Store/Manager/Service 并在 Composable 内编排异步状态机，没有 screen state holder**（P1 · 架构）
- 证据：`SkillsScreen.kt:88`（`repository: SkillRepository`）、`McpScreen.kt:66-72`（store/probe/oauth 三件套）、`PluginsScreen.kt:62,68`、`ScheduleScreen.kt:40-41`（service+runtime）、`WorkshopScreen.kt:61-63`（store）、`AgentsDocSheet.kt:44`、`ProjectSkillsSheet.kt:43-44`、`WorkshopViewerScreen.kt:58-60`、`FileBrowserSheet.kt:65`（`root: File`）、`SettingsScreen.kt:96`（在组合内 `LanguagePreferences(context)`）。各页面自行 `remember { mutableStateOf(...) }`（范围内共 131 处 `mutableStateOf`）并用 `rememberCoroutineScope().launch { store.xxx() }` 串行编排加载/保存/删除/错误；`LaunchedEffect` 里没有 try/finally 保底（如 `AgentsDocSheet.kt:63-70`、`SkillsScreen.kt:108-113`）。
- 影响：页面无法独立预览或 UI 测试（范围内 0 个 `@Preview`）；同一"加载→空态→失败→保存确认"流程在 12+ 页面重复且已漂移（错误展示方式 4 种、sheet 高度 5 种）；深链/刷新类需求（插件详情、工坊详情的"数据没了就 120ms 后 onBack"）只能靠组合内 hack 实现，见 `PluginDetailScreen.kt:82-87`、`WorkshopViewerScreen.kt:93-98`。这也违反项目自己的接线约定"UI 用 collectAsStateWithLifecycle 读状态、动作回调回服务"（`docs/ARCHITECTURE.md:282`），只是页面直接调 service 而非经 ViewModel。
- 建议：至少为技能、MCP、插件、定时任务四个重页面引入 plain UI state holder（不强制 ViewModel，可用 `remember(store) { XxxStateHolder(store, scope) }`），页面只接收 `UiState + 事件回调`；抽 `DdPageScaffold(title, onBack, actions) { }` 统一顶栏/insets。先做 1 个页面作为样板再复制。
- 工作量：L

**[SC-002] 页面骨架/分组卡片/空态/加载块在 17+ 页面重复复制，且已出现行为漂移**（P1 · 可维护性）
- 证据：17 个文件复制了完全相同的顶栏 `Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp))` + `DdIconButton(ArrowLeft)` + 标题（`SettingsScreen.kt:114-125`、`ProviderListScreen.kt:80-91`、`SkillsScreen.kt:206-238`、`McpScreen.kt:163-206`、`ScheduleScreen.kt:154-165`、`PluginsScreen.kt:99-125`、`WorkshopScreen.kt:105-145`、`WorkshopViewerScreen.kt:146-216`、`SessionSearchScreen.kt:123-147`、`ArchivedSessionsScreen.kt:85-114`、`ChatSettingsScreen.kt:53-64`、`PrivacyScreen.kt:42-53`、`RuntimeSettingsScreen.kt:56-67`、`FontScreen.kt:56-67`、`PersonalizationScreen.kt:106-117`、`ProviderDetailScreen.kt:113-124`、`WorkspaceSettingsScreen.kt:72-83`）；`SettingsGroup` 被 `SkillsScreen.kt:391-399` 整段重写（同样的 20dp 圆角 + surface 背景）；空态有 7 份独立实现（`SkillsScreen.kt:355`、`McpScreen.kt:323`、`WorkshopScreen.kt:227`、`ScheduleScreen.kt:271`、`JobsSheet.kt:138`、`ProjectSkillsSheet.kt:180`、`FileBrowserSheet.kt:247`），加载块 6 份（如 `top = 120.dp` 固定偏移重复出现）。
- 已造成漂移的证据：`DdSheet` 内容高度比例分别用了 `fillMaxHeight(0.66f)`（`SkillsScreen.kt:463`）〔复核修正：该行实际是 `screenHeightDp * 0.66f` 的 `maxBodyHeight`，不是 `fillMaxHeight`；真正的 `fillMaxHeight` 只有 0.85/0.88/0.92/0.94〕、`0.85f`（`ProjectSkillsSheet.kt:90`）、`0.88f`（`McpImport.kt:70`）、`0.92f`（`McpEditor.kt:149`、`ScheduleEditor.kt:74`）、`0.94f`（`AgentsDocSheet.kt:133`）；错误展示分别用 `DdDialog`（`SkillsScreen.kt:331-351`）、底部 inline Text（`McpEditor.kt:177-184`）、`errorRes` 复用列表页（`ScheduleScreen.kt:197-200`）、无反馈（SC-003）。
- 影响：改一次视觉规范（顶栏高度、空态插画、错误样式）要改 17 个文件，且已经改不齐。
- 建议：抽 `DdPageScaffold`、`DdEmptyState`、`DdLoadingBlock`、`DdSheetSize`（Small/Medium/Large 常量）四个组件；`SettingsGroup` 移到 design 或提升为公共 `DdGroupCard` 供 skills/workspaces 复用。
- 工作量：M

**[SC-003] UI 层把失败吞成空数据或无任何反馈（错误静默失败）**（P1 · 正确性）
- 证据：
  - `SkillsScreen.kt:115-119` 订阅 `repository.changes` 后直接 `skills = repository.list()`；`SkillRepository.list()` 内部 `catch (error: Exception) { emptyList() }`（`data/SkillRepository.kt:44-52`）。一次 IO 异常就会把整页技能清空并显示"还没有技能"空态，用户会误以为技能被删。
  - `WorkshopScreen.kt:89-95` 删除失效创意 `runCatching { store.delete(...) }` 结果被丢弃后直接 `refreshKey++`，删除失败无任何提示；`WorkshopScreen.kt:81-86` 扫描失败同样静默保留旧数据。
  - `PluginDetailScreen.kt:88-92` 详情行 `runCatching { detailRows(...) }.getOrDefault(emptyList())`，插件详情行加载失败静默消失。
  - `ProviderDetailScreen.kt:61,278-287` 保存 API Key 的契约是 `(String) -> Unit`，无成功/失败回调，UI 在点击后立刻 `draft = ""`；调用方 `DeepDeepApp.kt:789-793` 是裸 `scope.launch { writeApiKey(...) }`，写失败不会提示、输入框已清空。
- 影响：用户会做出错误判断（重装技能、重复填 Key、以为删除成功），且没有可诊断反馈。

> 〔复核降级〕四处证据全准，但「一次 IO 异常清空整页」概率被夸大（多数失败已被内部 `runCatching` 兜住）；P1→P2 更合适，`Result/sealed` 建议正确。
- 建议：Store 的"空结果"与"读取失败"必须可区分（返回 `Result`/sealed）；页面统一"错误行/Toast + 重试"；保存类回调改为 `suspend (…) -> Result` 或由 state holder 暴露 `error`。
- 工作量：M

### P2（可维护性/组件 API/可访问性/测试薄弱）

**[SC-004] 设计系统默认触控目标低于 48dp，波及所有页面**（P2 · 可访问性）
- 证据：`DdIconButton` 默认 `size = 44.dp`（`design/.../DdComponents.kt:110`），30+ 处返回/刷新/加号按钮都用默认值；`DdTextAction` `heightIn(min = 44.dp)` 且水平只 padding 2dp（`DdComponents.kt:178-188`）；`DdChip` 高 36dp（`:276`）；`DdMenuItem` 高 44dp（`DdMenu.kt:73`）；`DdSearchField` 清除按钮 32dp（`DdSearchField.kt:107-111`）；日历格 38dp（`ScheduleDatePicker.kt:196-212`）；插件数字步进的 "+"/"−" 是 `DdTextAction`，实际宽度约 14dp（`PluginDetailScreen.kt:442-462`）。design 模块没有任何 `minimumInteractiveComponentSize`（grep 0 处）。
- 影响：48dp 是 Android 可触控最小目标；小文本按钮和 38dp 日历格在单手操作/TalkBack 探索时明显难点，属于系统性 a11y 缺陷而非个别页面问题。〔复核修正：44dp 只差 4dp，一般可用；真正严重的是插件 `±` 步进（触控宽约 14-18dp）与 38dp 日历格。「波及所有页面」应限定为「受影响的默认尺寸控件」。〕
- 建议：design 层给 `DdIconButton` 默认 48dp、`DdTextAction` 用 `Modifier.minimumInteractiveComponentSize()` 或最小 48×48 触控面（视觉尺寸可保持小），数字步进改 48dp 的 `DdIconButton`。
- 工作量：S

**[SC-005] `DdListRow` 对每行强制 `contentDescription = title`，且无点击的行也变成 disabled clickable → 副标题/右值不可读**（P2 · 可访问性）
- 证据：`DdComponents.kt:208-214`：`DdPressable(onClick = onClick ?: {}, ..., enabled = onClick != null && enabled, contentDescription = title)`。全范围 42 处 `DdListRow` 调用全部继承该语义。带长副标题的信息行（`PrivacyScreen.kt:67-100` 五条隐私说明、`PluginDetailScreen.kt:163-196` 状态/版本/大小/技能、`ProviderDetailScreen.kt:253-262` 模型数/Base URL）在 TalkBack 下合并节点优先播报 `contentDescription`，副标题与 value 通常不会被读出（至少与 title 重复），纯展示行还会被播报为"按钮，已停用"。
- 影响：隐私页、插件详情等纯信息页面在 TalkBack 下实质不可用〔复核降级：标题仍可读，准确说法是「副标题/右值不播报、纯展示行被播报为停用按钮」〕；用户听到的是"隐私 · 本地保存"而不是完整说明。
- 建议：`DdListRow` 不再设置 `contentDescription`（可见 Text 会自然合并）；`onClick == null` 时不挂 `clickable`；如确需自定义播报，用 `mergeDescendants=false` + 明确组合文案。
- 工作量：S

**[SC-006] 行内嵌套可交互开关 7 处，点击行与点击开关是两个动作，语义合并后 TalkBack 焦点混乱**（P2 · 可访问性/组件 API）
- 证据：`ChatSettingsScreen.kt:128-139`（行 onClick 切换 + 尾随 `DdSwitch` 也切换）、`SkillsScreen.kt:402-427`、`McpScreen.kt:347-363`、`PluginsScreen.kt:252-327`（还在 switch 上加了 `semantics { contentDescription = name }`）、`LinuxEnvironmentSection.kt:111-129`、`ProjectSkillsSheet.kt:122-145`、`ScheduleScreen.kt:252-267`。`DdSwitch` 的 `onCheckedChange` 为必填非空（`DdControls.kt:41-47`），没有"只读/由父行处理"的形态。
- 影响：合并语义下整行与内部开关可能是两个可聚焦目标，也可能合并成一个同时带按钮与开关属性的节点；开关状态播报依赖调用方手传 `stateDescription`，容易出现"行说开、开关说关"。
- 建议：design 层提供 `DdSwitchRow(title, subtitle, checked, onCheckedChange)`（整行 `toggleable`、`DdSwitch(onCheckedChange = null)`）；`DdSwitch` 的 callback 改可空并补 `stateDescription` 默认由 `checked` 推导。
- 工作量：S

**[SC-007] 硬编码中文散落：sheet 关闭按钮、MCP 编辑器 4 条错误、中文顿号拼接**（P2 · 可访问性/文案）
- 证据：`design/.../DdComponents.kt:380` `contentDescription = "关闭"`，`DdSheetHeader` 有 14 处调用，英文环境下 TalkBack 读中文；`McpEditor.kt:85` `"服务器配置不存在"`、`:110` `"保存失败"`、`:117` `"JSON 配置不正确"`、`:143` `"删除失败"` 直接展示给用户；`DevPackagesSection.kt:115` 与 `PluginDetailScreen.kt:193` 用 `joinToString("、")` 拼列表，英文界面出现中文顿号。（`FontScreen.kt:158-165` 的中英预览词是 UI.md:82 明确规定的设计，不计问题。）
- 影响：中英双语是项目硬约定，这 7 处英文环境破功；用户在 MCP 编辑器出错时看到中文。
- 建议：`DdSheetHeader` 增加 `closeContentDescription: String` 必填参数（或 `@StringRes`），由调用方 `stringResource`；MCP 错误补 strings 资源；分隔符改用 `stringResource(R.string.list_separator)` 或直接 `joinToString(", ")`。
- 工作量：S

**[SC-008] 表单草稿与页面临时状态放在普通 `remember`，旋转/进程回收即静默丢失**（P2 · 正确性）
- 证据：`AgentsDocSheet.kt:55`（AGENTS.md 编辑正文）、`McpEditor.kt:76`（服务器 JSON）、`McpImport.kt:43`（导入 JSON）、`ScheduleEditor.kt:46`（定时任务整个表单）、`ProviderDetailScreen.kt:275`（API Key 草稿）、`WorkspaceDialogs.kt:39`（工作区名）、`WorkspaceSettingsScreen.kt:51-55`；`ProviderDetailScreen.kt:70` 的 OAuth 登录 session 也放在 `remember(provider.id)`，旋转会直接取消进行中的授权并在无提示的情况下关掉 sheet。`AndroidManifest.xml` 未声明 `configChanges`、未锁横竖屏，Activity 旋转必然重建；范围内 131 处 `mutableStateOf` 对应仅 17 处 `rememberSaveable`。
- 影响：用户在编辑 AGENTS.md/MCP JSON/定时任务时旋转设备（或分屏/字体大小变化触发配置变更）会丢失全部未保存内容，且 sheet 直接消失、没有"放弃修改？"确认。
- 建议：短字符串/表单字段改 `rememberSaveable`；复杂草稿（ScheduleDraft）实现 `Saver` 或提升到 state holder（saveable）；OAuth session 由上层持有并在重建后恢复 sheet。
- 工作量：M

**[SC-009] 约 10 个真实列表页用 `Column + verticalScroll + forEach` 渲染列表，只有 4 处使用 `LazyColumn`**（P3 · 性能）〔复核修正：「22 个页面」夸大——23 个文件含 `verticalScroll`，其中仅 14 个含 `forEach`，真正列表页约 10 个；42 项厂商列表影响可忽略。只值得改 `FileBrowserSheet`、会话选择器、`ProviderListScreen` 三处，全量替换会引入嵌套滚动/位置恢复风险〕
- 证据：范围内 23 个文件含 `verticalScroll`，仅 4 个文件使用 `LazyColumn`（`ArchivedSessionsScreen`、`SessionSearchScreen`、`ScheduleTimePicker`、`ProjectSkillsSheet`）。典型：`ProviderListScreen.kt:123-131`（42 家厂商）、`FileBrowserSheet.kt:97-136`（目录项数量不受控）、`ScheduleEditor.kt:297-315`（会话选择器一次渲染全部未归档会话）、`SkillsScreen.kt:248-288`、`McpScreen.kt:216-268`、`PluginsScreen.kt:145-200`、`WorkshopScreen.kt:155-213`、`JobsSheet.kt:91-133`、`ScheduleScreen.kt:187-202`。
- 机制：`verticalScroll` 不虚拟化，进入页面时一次性组合、测量并保留全部行；任何状态变化（搜索词、开关、每秒 tick）都要重组所有行。对文件浏览器/会话选择器这类无界列表是 O(n) 首帧成本与内存占用。
- 建议：列表页统一改 `LazyColumn` + 稳定 key（可复用现有 4 处写法），并把"滑块内的说明尾部"改为 `item {}`；抽公共 `DdList` 减少每页样板。
- 工作量：M

**[SC-010] 复合字体每次冷启动把 4.4MB 字体资源重拷进 cacheDir，加载失败静默回退；主题 TextStyle 每次读取重建**（P2 · 性能/资源）
- 证据：`DdCompositeFont.kt:111-126` 的 `fontFile()` 只查进程内 `files` map，不检查目标文件是否已存在，每次进程启动都把 raw 字体重新写入 `cacheDir/fonts/`；`preload`（`DeepDeepTheme.kt:142-144`，由 `DeepDeepApplication.kt:19` 在 IO scope 调用）预热 2 套拉丁字体（212KB + 165KB）与 4.08MB Noto SC 子集（`design/src/main/res/font/noto_sans_sc_subset.ttf` 实测 4,081,304 字节），即冷启动约 4.4MB 额外磁盘写入 + 与 APK 内的双份存储；`build()` 失败时 `getOrElse { Typeface.create("sans-serif", …) }`（`:85-87`）静默降级，用户只会觉得"字体没生效"。
- 另：`DdType` 的所有 style 都是 `get()` 动态构造 `TextStyle`（`DeepDeepTheme.kt:157-240`），页面层 188 处 `DdType.xxx` 读取每次组合都新建对象；`DeepDeepTheme` 还在组合体中写全局 `DdFonts.apply(fontStyle)`（`:334`），字体切换依赖 Activity 重建而非快照状态。
- 影响：低端机冷启动多一次 4MB 级磁盘写（虽在 IO 线程），cache 与 APK 体积重复；字体异常无诊断线索。
- 建议：字体文件名带版本/长度前缀，存在且大小匹配则跳过拷贝；把 `Typeface` 缓存结果加上版本标记；DdType 改为按 fontStyle 组合期构建一次的不可变 `DdType` 对象；`DdFonts.apply` 移出组合体。
- 工作量：M

**[SC-011] 设计系统存在两份分段控件，使用面已分裂；`DdPressable` 按压反馈与设计约定冲突**（P2 · 可维护性）
- 证据：`DdSegmented`（`DdSegmented.kt:34`，高 44dp、14dp 圆角、options 为 `List<String>`）与 `DdSegmentedControl`（`DdControls.kt:114`，高 36dp、胶囊、泛型 + `label`）是同一语义的两套实现；`PluginsScreen.kt:47,126` 用旧的 `DdSegmented`，`SettingsScreen.kt:384`、`PersonalizationScreen.kt:164,192` 用 `DdSegmentedControl` → 同一 App 中分段控件高度/形状/动画不一致，UI.md:179 只承认后者〔复核修正：原文 181 行号错〕。另 `DdPressable` 同时挂 Material `ripple(...)`（`DdComponents.kt:83-89`）和自绘 wash（`:92-99`），与 `docs/UI.md:174/178/179` 的「无 Material 默认 ripple」约定冲突〔复核修正：原文引用的「UI.md:204 按压态统一为圆角灰 wash，不使用 ripple」**该句不存在**，那是把多处约定拼成了一句〕，实际是双重心反馈。
- 建议：删除 `DdSegmented`，`PluginsScreen` 迁移到 `DdSegmentedControl`；`DdPressable` 去掉 ripple 只保留 wash（如需水波则统一改文档）。
- 工作量：S

**[SC-012] `DdListRow` 缺少 leading slot / pressed 状态，导致 5+ 个页面重写"行"；归档行自绘手势存在陈旧闭包风险**（P2 · 组件 API）
- 证据：`DdListRow` 的 leading 只有 `icon: DdIcon?`（`DdComponents.kt:222-225`），于是：`WorkshopScreen.kt:267-317` 为 emoji 图标整段重写行、`ScheduleScreen.kt:243-268` 为重写（12dp 圆角，与其它行 20dp 不一致）、`JobsSheet.kt:181-240`、`FileBrowserSheet.kt:189-234`、`ProviderListScreen.kt:145-181` 各自用 `DdPressable + Row` 复刻同一布局。`ArchivedSessionsScreen.kt:208-239` 进一步手写 `MutableInteractionSource + PressInteraction + detectTapGestures`（`pointerInput(Unit)`），`SessionSearchScreen.kt:211-234` 又手写一份 wash 动画。
- 风险（推测，需按 Compose 版本复核）：`pointerInput(Unit)` 的协程不随重组重启，`onLongClick`/`onTap` 闭包捕获首次组合的 `session` 对象；归档页会话对象更新后（例如标题被改名），长按菜单传给重命名对话框的可能是旧标题。建议用 `rememberUpdatedState` 包装回调。
- 建议：`DdListRow` 增加 `leading: (@Composable RowScope.() -> Unit)? = null` 与 `pressed`/`contentPadding`/`shape` 参数，统一行样式；提供 `Modifier.ddRowInteraction(...)` 复用按压态，避免每页手写手势。
- 工作量：M

**[SC-013] 工坊 WebView 允许页面打开任意 scheme 的外部 Intent，且远程子资源不做拦截**（P2 · 安全）
- 证据：`WorkshopWebView.kt:228-240` 的 `shouldOverrideUrlLoading` 对任何非本域 URL 直接 `Intent(ACTION_VIEW, uri).addFlags(NEW_TASK)` 启动，无 scheme 白名单（`intent://`、`tel:`、`market://`、自定义 scheme 都会触发），也无"用户手势"限制；页面可通过脚本自动跳转拉起外部 App。〔复核：scheme 白名单缺失属实，但「脚本自动跳转」未验证（不同 WebView 版本对无手势导航行为不一致）；工坊内容是 Agent 生成、页面是用户自装，威胁模型偏弱，P2→P3。〕`shouldInterceptRequest`（`:224-226`）只接管本域请求，页面仍可任意加载远程图片/脚本把数据外发（页面数据经 bridge 只限本域，但网络出口未限制）。正面：bridge 的 `isMainFrame + sourceOrigin == origin` 校验、document-start 脚本 origin 限制、`allowFileAccess=false`/`mixedContent NEVER_ALLOW` 都正确（`WorkshopBridgeHost.kt:54-59`、`WorkshopWebView.kt:207-217,283-289`）。
- 影响：创意页面内容由 Agent 生成，属于半可信输入；恶意/被提示词注入的页面可以用自动跳转骚扰用户或触发其它 App 的深链行为，也可以用远程资源做数据外发。
- 建议：外部跳转只允许 `http/https` 且要求主框架+用户手势（或先弹确认）；可选注入 CSP `default-src 'self' data: blob:` 限制远程子资源；`file:`/`content:`/`intent:` 直接丢弃。
- 工作量：S

**[SC-014] 页面层几乎没有可回归的测试：0 个 `@Preview`、0 个 Compose UI/截图/a11y 测试**（P2 · 测试）
- 证据：本次范围 127 个 Composable、52 个文件，`@Preview` 数量为 0；`app/src/androidTest` 只有 `ArrangeDeviceTest.kt` 与 `ImageNormalizerDeviceTest.kt`，与页面无关；`design` 模块无任何测试目录。现有相关单测只覆盖纯函数（`ui/sessions/SessionSearchTest.kt`、`ui/skills/SkillsFilterTest.kt`、`ui/mcp/McpFilterTest.kt`、`ui/schedules/ScheduleDraftTest.kt`、`ui/schedules/ScheduleFilterTest.kt`、`ui/mcp/McpProbeCacheTest.kt`、`ui/settings/OAuthLoginSessionTest.kt`）。
- 影响：SC-005/SC-006 这类语义/a11y 回归、SC-008 的状态恢复、SC-003 的错误路径只能手测；重构（SC-001/SC-002）没有安全网。
- 建议：先给 design 组件加 Compose UI 测试（DdListRow 语义、DdSwitchRow、DdDialog 返回键）；给 3 个标杆页面加 `createComposeRule` 状态渲染测试 + `@Preview`；a11y 用 `AccessibilityChecks` 跑一遍。
- 工作量：M

**[SC-015] 输入/选择类组件没有可访问名称与状态：DdTextField/DdTextArea/DdSearchField 无 label 语义，日历/时间选择器只有数字**（P2 · 可访问性）
- 证据：`DdTextField.kt:67-93` 与 `DdTextArea.kt:126-149` 用 `BasicTextField` + 同层 `Text(placeholder)`，placeholder 不是 field 的 `semantics label`；`DdSearchField.kt:64-101` 同样只有占位文本。调用方普遍在外部用 `DdSectionLabel`/`Text` 写标签（如 `ProviderDetailScreen.kt:298-303`、`PersonalizationScreen.kt:131-137`），TalkBack 聚焦输入框时读不到用途。`ScheduleDatePicker.kt:156-219` 的 `DayCell` 只有 `Text(date.dayOfMonth)`，无"2026年10月30日/已选中/今天"语义；`ScheduleTimePicker.kt:115-137` 的小时/分钟列只有数字，无列表角色与列标题。
- 影响：VoiceOver/TalkBack 用户无法确定输入框用途，也无法在日历里确认所选日期。
- 建议：`DdTextField`/`DdTextArea` 增加 `label: String?`（写入 `semantics { contentDescription }` 或 `TextField` 的 label 装饰）；日期格 `semantics { contentDescription = 格式化日期; selected = ...; role = Button }` 并补 48dp 触控；时间列加列标题语义。
- 工作量：M

### P3（微优化）

**[SC-016] `DdDropdown` 的关闭动画永远不会播放，`shown` 标志从不重置**（P3 · 正确性/代码质量）
- 证据：`DdDropdown.kt:49-52` `shown` 只在 `expanded` 时置 true，从不复位；`:63-67` `progress` 目标为 `expanded && shown`，但 `:77` 的 `Popup` 在 `expanded=false` 时被同步移除，退出 tween 作用在已被移除的节点上 → 淡出/上移只有进入方向生效；`shown` 一旦为 true 后续不再影响目标值，是冗余状态。
- 建议：要么用 `AnimatedVisibility`/自管 `visible` 延迟移除实现对称的进入/退出，要么删掉 `shown` 与 exit 动画，明确"下拉只有淡入"。
- 工作量：S

**[SC-017] 死代码与未使用 API：DdMotion 6 个 token、`OAuthLoginState.waiting`、工坊详情不可达分支**（P3 · 可维护性）
- 证据：`DdMotion.kt:10-22` 中 `PRESS_IN`、`ENTER`、`DRAWER`、`SHEET`、`STAGGER`、`NAV` 在全仓库 0 处引用（`PRESS`/`PRESS_OUT`、`BASE`/`SCREEN`/`NAV` 语义重复）；`OAuthLoginSession.kt:28-30` 定义了 `waiting` 但 UI 在 `OAuthLoginSheet.kt:160-169` 手写了完全相同的判断，`waiting` 0 引用；`WorkshopViewerScreen.kt:243` `creation != null -> UnsupportedWebView()` 不可达（`data = creation?.let { ... }`，data 为空当且仅当 creation 为空）。
- 建议：删除死 token/死分支，或让 `OAuthLoginSheet` 使用 `state.waiting`〔复核勿做：`waiting` 要求 `url != null`，而 `OAuthLoginSheet.kt:160-164` 的 UI 条件要求 `url == null && message == null`，语义不同；照改会改变等待态逻辑〕；motion token 表收敛为实际使用的 6 个左右并在 UI.md 对齐。
- 工作量：S

**[SC-018] `JobsSheet` 每秒在 sheet 顶层写入 `now`，把耗时文本的重组放大到整页所有行**（P3 · 性能）
- 证据：`JobsSheet.kt:65,73-79` 的 ticker 每秒 `now = System.currentTimeMillis()`，`:101-121` 在所有 `JobRow` 调用处读取；每次 tick 导致 sheet 顶层重组并向下传递新参数，所有运行中/已结算行重组（`elapsed` 只需要在运行中的行内更新）。
- 建议：把 ticker 移到 `JobRow` 内部（仅运行中的行 `LaunchedEffect` 每秒更新局部 elapsed），或用 `derivedStateOf` + 单独 `@Composable JobElapsed(jobId)`，避免整页重组。
- 工作量：S

**[SC-019] `OAuthLoginSession.succeed()` 丢弃 pending waiters 且不 cancel，异常时序下协程悬挂**（P3 · 并发）
- 证据：`OAuthLoginSession.kt:196-209` `succeed()` 调用 `takeWaitersLocked()` 后直接丢弃返回值，未像 `fail()`/`cancel()`（`:162-194`）那样对 waiter 调 `cancel()`；若登录成功时仍有 `prompt`/`select` 的 `suspendCancellableCoroutine` 未完成，会一直挂起到作用域取消。
- 建议：与 `cancel` 路径一致 `stale.cancel()`；加一条"成功时清理 waiter"的单测。
- 工作量：S

**[SC-020] 裸 `collectAsState` 2 处，与项目 lifecycle-aware 约定不一致**（P3 · 代码质量）
- 证据：`PluginsScreen.kt:68`、`PluginDetailScreen.kt:68` 用 `manager.state.collectAsState()`；同范围内其它 6 处均用 `collectAsStateWithLifecycle`（如 `ScheduleScreen.kt:49`、`McpEditor.kt:308`），项目约定见 `docs/ARCHITECTURE.md:282`。插件状态包含下载进度/状态探测等更新，App 退后台后仍会触发不可见页面的重组。
- 建议：改为 `collectAsStateWithLifecycle()`；顺手在 CI 里禁止裸 `collectAsState(`。
- 工作量：S

## 架构评估

**页面层职责**：页面被设计成"大 Composable 直接接线"（52 文件中约 20 个超过 200 行，最大 `SkillsScreen` 641 行、`SettingsScreen` 563 行），一个文件里同时承担：数据加载、业务校验、IO 调度、错误文案选择、布局、交互状态。与 `ChatScreen` 有 `ChatViewModel` 相比，功能页没有对应的状态所有者，`docs/ARCHITECTURE.md:282` 的接线约定（"UI 用 collectAsStateWithLifecycle 读状态、动作回调回服务"）只在少数页面成立。后果：深链/返回/旋转这类导航生命周期问题只能在页面内用 `delay(120) + onBack()`、`Handler.post(recreate)` 等 hack 修补（`PluginDetailScreen.kt:82-87`、`WorkshopViewerScreen.kt:93-98`、`SettingsScreen.kt:411-416`）。

**组件化**：`design` 模块抽出了控件级组件（Switch/Segmented/Dialog/Sheet/Search/TextField/Spinner/TypingDots/Icons/Motion）与 token，这是本项目最好的部分之一；但**缺少布局级组件**：PageScaffold、GroupCard、ListRow（leading slot/state）、EmptyState、LoadingBlock、SheetSize 都没有，导致每个页面各自拼装，重复与漂移集中在这一层。`SettingsGroup` 甚至只存在于 `ui/settings` 被 skills/plugins/workspaces 跨包引用（`WorkspaceSettingsScreen.kt:39`、`PluginsScreen.kt:58`），说明共享布局组件的需求已经存在但没有归位到 design。

**状态边界**：UI 状态（搜索词、Tab、展开态、草稿）基本在 Composable 内，业务状态在 runtime/service 且通过 StateFlow 暴露——这个方向正确；问题在于**边界画在 Composable 内部**：页面同时是 renderer、controller 和 controller 的调度器。`McpScreen` 甚至把 `McpProbeCache` 顶层单例放在 UI 文件里（`McpScreen.kt:393-446`），状态跨页面/跨测试共享。

**与 runtime 的耦合**：页面直接依赖 `PluginManager`、`ScheduleService`、`AgentRuntime`、`SkillRepository`、`McpConfigStore` 等具体类型（`ScheduleScreen.kt:40-41`、`WorkshopViewerScreen.kt:58-60`），`PluginDetailScreen` 还 import 了具体插件实现 `MiCloudPlugin`/`MiCloudFeatureSection`（`:55-56`）做特判。这使 `:app` 的 UI 层无法用 fake 数据渲染，也让"插件框架不引用具体插件"的架构约定在 UI 侧被打破（框架层做到了，UI 层没做）。

## 性能与效率

- **列表**：23 个文件使用 `verticalScroll` 做页面滚动（多数是列表页、少量表单页），只有 4 个 `LazyColumn`（SC-009）。这是范围内最确定的性能债，尤其是 `FileBrowserSheet`、`ConversationPickerSheet`（全部会话）与 42 家厂商列表。
- **重组范围**：编译报告干净（范围内 111/128 可跳过，17 个 `*Label` 辅助函数不可跳过属正常）〔复核修正〕，但运行期仍有可以避免的整页重组：`JobsSheet` 每秒 tick（SC-018）；`PluginsScreen` 的列表项在组合期 `stringResource` 构造（每次重组重算，n≈10，影响小）；`ScheduleScreen` 每重组都 `schedules.map { it.sessionId }` 作为 effect key 和 `filterSchedules(...)`/排序（`ScheduleScreen.kt:63,175,188-189`），属 P3 级。
- **字体/主题**：4.4MB/次冷启动的字体解包写盘、双份存储、失败静默（SC-010）；`DdType` 188 处读取每次构造 `TextStyle`（SC-010）。
- **WebView**：页面 Agent 事件 40ms 合批 + 保序 + 无 pending Promise 是正确设计（`WorkshopBridgeHost.kt:103-189`）；复用单例 `WorkshopAssetLoader` 按页创建、`no-store` 缓存策略明确；WebView 销毁流程完整。唯一成本点是 `themeApplyScript` 与主题 JSON 在每次主题变化时 `evaluateJavascript`（正常）。
- **其他正面项**：`DdSwitch`/`DdTypingDots` 等动画值只在 `graphicsLayer`/draw 阶段读取（`DdControls.kt:102-108`、`DdTypingDots.kt:58-63`）；`DdSearchField` 过滤在 `remember(skills, query)` 中做（`SkillsScreen.kt:106`），没有在组合体内做 O(n) 过滤；`ProviderListScreen.kt:53-62` 同样 memo 化。

## 优先修复建议（Top 5）

1. **先解决"页面无状态所有者 + 模板重复"**（SC-001/SC-002）：抽 `DdPageScaffold`、`DdGroupCard`、`DdEmptyState`、`DdLoadingBlock`，并选技能页或 MCP 页做一个 state holder 样板；这是后续所有修复（a11y、测试、列表虚拟化）的前置条件，成本 L 但收益覆盖 17 个页面。
2. **修错误静默**（SC-003）：Store 区分"空"与"失败"，技能/工坊/插件/厂商 Key 保存路径补错误反馈；其中 `SkillRepository.list()` 返回空列表导致整页清空是最容易被用户误判的一条，建议单独先修。
3. **design 层做一轮 a11y 修复**（SC-004/005/006/007/015）：触控目标 ≥48dp、`DdListRow` 去掉 contentDescription、`DdSwitchRow`/只读开关、`DdSheetHeader` 关闭文案参数化、输入框 label 语义。全部集中在 design 模块，一处改动全 App 生效，性价比最高。
4. **表单状态 saveable**（SC-008）：AGENTS.md 编辑、MCP JSON、定时任务表单、API Key 草稿改 `rememberSaveable`/Saver，避免旋转静默丢编辑。
5. **列表虚拟化与字体缓存**（SC-009/010）：优先 `FileBrowserSheet`、`ConversationPickerSheet`、`ProviderListScreen` 换 `LazyColumn`；字体解包加存在性/版本校验或改为随 APK 的 Typeface 缓存。

补充：WebView 外部跳转 scheme 白名单（SC-013）建议本轮顺手修，改动小且属于安全边界。
