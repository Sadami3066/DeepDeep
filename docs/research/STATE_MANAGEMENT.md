# DeepDeep 页面状态持久化 / 状态管理体系设计

> 调研对象：DeepDeep（Android + Jetpack Compose，BOM 2026.03.00 → compose-runtime 1.10.5，minSdk 29，自研导航 `AppNavigator`）。
> **实现状态（round42 收尾）**：下列方案已落地——页面宿主 `AnimatedScreenHost` 已重写为自研 host 并用 `rememberSaveableStateHolder()` + `SaveableStateProvider(routeKey)` 包住每个 destination，出栈后延迟一帧 `removeState`；审计中两处 query 已改 `rememberSaveable`（`ArchivedSessionsScreen`、`SessionSearchScreen`）。真机验证设置滚动与插件 Tab 均可恢复（`artifacts/validation/round42/screens/`）。
> 结论先行：**用 `rememberSaveableStateHolder()` + 按 `routeKey` 的 `SaveableStateProvider` 包住 `AnimatedScreenHost` 的每个 destination，出栈时 `removeState`。约 20 行改动即可修复全部"返回丢滚动/tab/搜索词"问题，并顺带获得进程死亡恢复**；不需要引入 Navigation 库、Hilt 或每页 ViewModel。

## 一、结论与判据

### 1.1 结论（按优先级）

1. **加 `SaveableStateHolder` 作用域是唯一必需的结构性改动**。改动前 `AnimatedScreenHost` 用 `AnimatedContent` 只组合当前 destination，退出动画后整棵 content 被销毁；销毁时 `rememberSaveable` 的值只是从 registry 反注册，**没有"按目的地"的保存桶**，所以值直接消失。这正是用户实测的两个 bug 的根因（设置页滚动、插件页 tab）。
2. **修复面不是"给每个页面写代码"，而是页面宿主管好保存作用域**。官方 `SaveableStateHolder` 的定位就是"保存 backstack 中当前未组合的屏幕的 UI 状态，例如滚动位置"（API 文档原文：*"Allows to save the state defined with rememberSaveable for the subtree before disposing it to make it possible to compose it back next time with the restored state. It allows different navigation patterns to keep the ui state like scroll position for the currently not composed screens from the backstack."*）。Navigation Compose 的 `NavHost` 内部就是 `rememberSaveableStateHolder()`（androidx `NavHost.kt:848`），我们只是把同一套机制搬进自研 host。
3. **绝大多数页面的"滚动位置"不需要改代码**：`rememberScrollState()`、`rememberLazyListState()` 内部已经是 `rememberSaveable` + Saver（官方 state-saving 文档的 LazyListState 例子即此），只是此前没人保存；holder 一接上就恢复。
4. **少数页面需要把"应保留"的 `remember` 改成 `rememberSaveable`**：`ArchivedSessionsScreen` 搜索词（`ui/sessions/ArchivedSessionsScreen.kt:69`）、`SessionSearchScreen` 搜索词（`ui/sessions/SessionSearchScreen.kt:78`）。其余搜索词/ tab 已经是 `rememberSaveable`，holder 后自动生效。
5. **不该保存的保持现状**：API key/token/MCP 凭据输入框、屏幕内表单 sheet 的草稿、菜单/对话框可见性、加载中状态、列表数据本身。saveable 只存"重新进入后能恢复 UI 形状"的最小状态，数据从 store/manager 重新加载。
6. **不要为状态保存引入 ViewModel/SavedStateHandle/Navigation 库**：本 app 页面没有跨配置变更的业务逻辑（数据由 root 层 `StateFlow`/manager 提供），页面级 ViewModel 只会增加样板；`SavedStateHandle` 解决的是 ViewModel 内的进程死亡恢复，与我们的场景不匹配。详见第七节。

### 1.2 状态该由谁持有（判据）

| 状态类型 | 例子 | 归属 | 机制 |
|---|---|---|---|
| 瞬时交互态 | 菜单/对话框展开、焦点、动画、按压 | 就地 | `remember`（不需要恢复） |
| UI 元素状态 | 滚动位置、tab、搜索词、折叠状态、表单草稿 | 页面作用域 | `rememberSaveable` + `SaveableStateHolder(routeKey)` |
| 屏幕业务数据 | 列表、加载中/错误、探测结果 | 页面（可重新获取） | 普通 `remember`/`collectAsState`，不保存；返回时重新加载 |
| 跨屏/全局应用状态 | 会话、模型选择、设置、schedule、jobs | root（`AppGraph`/manager 的 `StateFlow`） | 现状正确，不动 |
| 大对象/敏感数据 | 消息列表、图片、API key、OAuth token | 数据层/不保存 | 持久化存储（已有 DataStore/文件）；**禁止进 Bundle** |

官方依据：UI 元素状态 vs 屏幕 UI 状态的划分见 [State holders and UI state](https://developer.android.com/topic/architecture/ui-layer/stateholders)；saved state 只适合"primitive types and simple, small objects"，大对象会导致 `TransactionTooLargeException`（[Save UI states](https://developer.android.com/develop/ui/compose/state-saving)、[Saving UI state](https://developer.android.com/topic/libraries/architecture/saving-states)）。

## 二、为什么现在会丢（代码级根因）

- 改动前用 `AnimatedContent(targetState = navigator.current, contentKey = { it.routeKey })`，只组合当前 destination；push 子页后，父页在退出动画结束时被销毁。
- `rememberSaveable` 在销毁时会从 `LocalSaveableStateRegistry` 反注册，**没有 `SaveableStateProvider` 时没有任何人接住这份值**。`rememberScrollState()` 本质是 `rememberSaveable(saver = ScrollState.Saver)`，所以"能保存但没人保存"。
- 根组合里的 `rememberSaveable`（`drawerOpen`、`expandedWorkspaceIds`、`AppNavigator` 自身）不受影响，因为 root 始终在组合中。
- 一个容易误判的现象：**配置变更（旋转）时当前页的 `rememberSaveable` 是能恢复的**（当前页在组合中，值进了 Activity 的 root registry，位置键一致），所以问题只在"返回导航"路径。这与用户实测（返回后丢、旋转后不丢）完全吻合。
- 进程死亡时 `AppNavigator.Saver` 会恢复 routeKey 栈（`ui/navigation/AppNavigator.kt:37-40`），但各 destination 的 UI 状态没有随栈一起保存，恢复后回到默认值。

## 三、机制对比

| 机制 | 存储位置 | 配置变更 | 进程死亡 | 返回导航（目的页已销毁后回来） | 容量/限制 | 适用 | DeepDeep 结论 |
|---|---|---|---|---|---|---|---|
| `remember` | 内存（组合） | 丢 | 丢 | 丢 | 无 | 瞬时 UI 态 | 仅用于菜单/对话框等 |
| `rememberSaveable` | Bundle（经 registry） | 保 | 保 | **单独用仍丢**（除非有 SaveableStateProvider） | 仅 primitive/小对象，Bundle 有 1MB 级上限 | 页面内 UI 元素状态 | 审计中已在用，需 holder 作用域 |
| `SaveableStateHolder` + `SaveableStateProvider(routeKey)` | 内存 map（随宿主 registry 进 Bundle） | 保 | 保 | **保** | 同 saveable；`removeState` 控制内存 | 多屏导航的 UI 态 | **采用** |
| `ViewModel` | 内存（lifecycle 作用域） | 保 | 丢 | 视宿主而定 | 内存 | 屏幕业务数据/逻辑 | 不引入（无页面级业务逻辑） |
| `SavedStateHandle` | Bundle（ViewModel 内） | 保 | 保 | 视宿主 | 同 saveable | ViewModel 内的少量恢复键 | 不引入（无页面级 ViewModel） |
| 持久化（DataStore/Room/文件） | 磁盘 | 保 | 保 | 保 | 仅磁盘 | 应用数据、大对象 | 已有，继续用于数据层 |

官方对比（ViewModel / Saved state / Persistent storage 三维度）见 [Saving UI state](https://developer.android.com/topic/libraries/architecture/saving-states)。

## 四、推荐实现骨架

### 4.1 `AnimatedScreenHost`（`app/.../ui/navigation/AppScreenTransition.kt`）

```kotlin
@Composable
fun AnimatedScreenHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    baseDrawingEnabled: Boolean = true,
    base: @Composable () -> Unit,
    content: @Composable (AppDestination) -> Unit,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val routeKeys = navigator.stack.map { it.routeKey }
    val knownKeys = remember { mutableStateOf(routeKeys.toSet()) }
    LaunchedEffect(routeKeys) {
        val live = routeKeys.toSet()
        (knownKeys.value - live).forEach(saveableStateHolder::removeState)
        knownKeys.value = live
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().drawWithContent { if (baseDrawingEnabled) drawContent() }) { base() }
        AnimatedContent(
            targetState = navigator.current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { AppScreenTransition.of(this) },
            contentKey = { it.routeKey },
            label = "app-screen",
        ) { destination ->
            if (destination == AppDestination.Chat) {
                Box(Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().overlayTouchBarrier()) {
                    saveableStateHolder.SaveableStateProvider(destination.routeKey) {
                        content(destination)
                    }
                }
            }
        }
    }
}
```

要点：
- **不要包 `AppDestination.Chat`**：聊天页是 `base`，始终在组合中，本身不会丢状态；包了也无害但没必要。
- key 用 `destination.routeKey`：同一 route 返回时恢复上一次的状态（back 语义），`ProviderDetail(id)` 因 routeKey 含 id 天然隔离。
- `knownKeys` 自己维护：`SaveableStateHolder.keys` 是 compose-runtime 1.13.0-beta02 才新增的 API，当前 BOM（runtime 1.10.5）没有，不要依赖。
- `SaveableStateProvider` 内部要求 key 在**同时组合**的 provider 中唯一（源码 `require(key !in registries)`），`AppNavigator.push` 已阻止连续重复 push（`AppNavigator.kt:20-23`），A→B→A 也不会同时组合两个 A；若未来支持双栏/多实例，需要改成 `"$routeKey#$instanceId"`。

### 4.2 清理时机（内存边界）

- **出栈即清理**：`navigator.stack` 变化时，对"消失的 routeKey"调用 `removeState`。这正是 `removeState` 的设计用途；源码语义（[SaveableStateHolder.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime-saveable/src/commonMain/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder.kt)）：
  - key 仍在组合中（退出动画期间）：`removeState` 移除 active registry，于是该 content 销毁时不会再写回 savedStates —— 立即调用是安全的，不需要等动画结束。
  - key 已不在组合中：直接从 savedStates 删除。
- **不需要延迟到动画结束**：这与 Navigation Compose 的 entry 销毁语义一致；延迟反而会留下"已出栈页面的状态"并在快速重进时恢复脏状态。
- **内存边界**：savedStates 里每个 key 只存该页 `rememberSaveable` 的值（字符串/整数/布尔/滚动偏移），量级 ≈ 栈深 × 几百字节；栈深由用户导航层数决定（本 app 最多 3-4 层）。列表数据、探测结果、消息都不进 saveable，因此不会随数据增长。
- **reset 到 Chat**（`navigator.reset()`，用于"从搜索结果/定时任务跳回聊天"）：栈被清空 → 所有非 Chat key 被 `removeState` → 流程结束即释放，符合预期。
- 可选防御：若未来支持深链/深栈，可在 `removeState` 前按"最多保留 N 个 key（如 8）"做上限。

### 4.3 进程死亡恢复

- `rememberSaveableStateHolder()` 本身是 `rememberSaveable`，其 Saver 把 savedStates 写进 Activity 的 Bundle（只保存非空 state，空 state 会被丢弃以减小 Bundle，源码 `SaveableStateHolderImpl.Saver`）。
- 恢复顺序：Activity 重建 → `AppNavigator` 从 Bundle 恢复 routeKey 栈 → 当前 destination 组合 → provider 用 `savedStates[routeKey]` 恢复；**栈下方未组合的页面状态留在 savedStates 里**，等用户 back 回来时再恢复。这是 holder 相对"只保存当前页"的关键优势。
- Bundle 限制：只允许 Bundle 可存类型（String/Int/Boolean/Parcelable/Serializable 或自定义 `Saver`），且全 app 共享同一个 saved instance state，**禁止放列表/大字符串/位图/密钥**（官方明确 `TransactionTooLargeException` 风险）。
- 现状 `AppNavigator.Saver` 已能恢复栈；holder 补齐后，"杀进程再回来"能恢复到页面 + 滚动/tab/搜索词。

### 4.4 配套小改（仅两处）

1. `ui/sessions/ArchivedSessionsScreen.kt:69`：`var query by remember { mutableStateOf("") }` → `rememberSaveable`。
2. `ui/sessions/SessionSearchScreen.kt:78`：同上。
（其余搜索词/tab 已是 `rememberSaveable`：ProviderList `:52`、Plugins `:70-71`、Mcp `:87`、Skills `:104`、Schedule `:56`、Workshop `:75`。）

### 4.5 注意事项

- **组合顺序变化会错位**：`rememberSaveable` 的自动 key 是位置键；同一页面内新增/删除一个 saveable 会使其后所有 saveable 的恢复值错位。跨版本发布时，对关键状态使用显式 key（`rememberSaveable("settings.scroll") { ... }` 不适用于 ScrollState，可用 `rememberSaveable(saver = ...)` 包裹的显式 key 变体）。审计范围内暂无"条件分支里放 rememberSaveable"的冲突写法。
- **敏感输入不要 saveable**：`ProviderDetailScreen.kt:275` 的 API key 草稿、`McpEditor` 的 token/callback 草稿保持 `remember`（Bundle 会被系统保存到内存/磁盘副本，不应承载凭据）。
- **屏幕内 sheet 的草稿**（`OAuthLoginSheet`、`ScheduleEditor`、`AgentsDocSheet`、`ProjectSkillsSheet`）随页面销毁而丢：这是可接受的产品行为（离开页面=放弃编辑）；若要保留，需把草稿提升为 `rememberSaveable`，但会引入"陈旧草稿"风险，不建议本轮做。

## 五、DeepDeep 全页面审计

> "返回后丢失"指：从该页 push 子页再 back（该页曾被销毁后重建）。所有非 Chat destination 当前都丢；下表逐页列出应保留项与改法。

| # | 页面（文件:行） | 应保留的状态 | 当前实现 | 返回后丢失 | 建议 |
|---|---|---|---|---|---|
| 1 | SettingsScreen `settings/SettingsScreen.kt:97,98,129` | 滚动位置；语言/许可对话框瞬时态 | `rememberScrollState()`；对话框 `remember` | **滚动丢失（用户实测）** | holder 自动修复；对话框不需保存 |
| 2 | ProviderListScreen `settings/ProviderListScreen.kt:52,95` | 搜索词、滚动 | query `rememberSaveable`；scroll | 两者都丢 | holder 后均恢复 |
| 3 | ProviderDetailScreen `settings/ProviderDetailScreen.kt:128,275-276` | 滚动；API key 草稿（**不应保存**） | scroll；draft `remember` | 滚动丢失；草稿丢失（正确） | holder 修滚动；草稿保持非 saveable |
| 4 | RuntimeSettingsScreen `settings/RuntimeSettingsScreen.kt:72` | 滚动 | scroll | 丢失 | holder |
| 5 | PersonalizationScreen `settings/PersonalizationScreen.kt:52-57,121` | 昵称/交互/语气/指令草稿、滚动 | 全部 `rememberSaveable` + scroll | 全部丢失（无 holder） | holder 后自动恢复（注意 `edited/initialized` 逻辑已能防覆盖） |
| 6 | PrivacyScreen `settings/PrivacyScreen.kt:57` | 滚动 | scroll | 丢失 | holder |
| 7 | FontScreen `settings/FontScreen.kt:72` | 滚动 | scroll | 丢失 | holder |
| 8 | ChatSettingsScreen `settings/ChatSettingsScreen.kt:68` | 滚动 | scroll | 丢失 | holder |
| 9 | McpScreen `mcp/McpScreen.kt:76-87,220` | 搜索词、滚动；编辑器/探测结果为数据态 | query `rememberSaveable`；其余 `remember` | query/滚动丢失；数据每次重载+重新 probe | holder 修 query/滚动；重复 probe 是独立性能项（Round41 已记录） |
| 10 | PluginsScreen `plugins/PluginsScreen.kt:70,71,149` | tab、搜索词、滚动 | tab/query `rememberSaveable`；scroll | **全部丢失（用户实测 tab 回 0）** | holder 后恢复 |
| 11 | PluginDetailScreen `plugins/PluginDetailScreen.kt:72,153` | 滚动；删除确认瞬时态 | scroll；`remember` | 滚动丢失 | holder |
| 12 | ArchivedSessionsScreen `sessions/ArchivedSessionsScreen.kt:65-69,141` | 搜索词、列表滚动 | query 是 `remember`（**非 saveable**）；LazyColumn 默认 state | 丢失（进程死亡也丢） | query → `rememberSaveable`；holder 管滚动 |
| 13 | SessionSearchScreen `sessions/SessionSearchScreen.kt:76-80,176` | 搜索词、结果滚动 | query 是 `remember` | 丢失 | query → `rememberSaveable`；结果重查；holder 管滚动 |
| 14 | SkillsScreen `skills/SkillsScreen.kt:96-104,252` | 搜索词、滚动 | query `rememberSaveable`；scroll | 丢失 | holder |
| 15 | ScheduleScreen `schedules/ScheduleScreen.kt:50-56,187` | 搜索词、滚动；编辑器草稿（可丢弃） | query `rememberSaveable`；scroll；editorDraft `remember` | 搜索/滚动丢失；草稿丢失（可接受） | holder；editorDraft 保持非 saveable |
| 16 | WorkshopScreen `workshop/WorkshopScreen.kt:70-75,159` | 搜索词、滚动 | query `rememberSaveable`；scroll | 丢失 | holder |
| 17 | WorkshopViewerScreen `workshop/WorkshopViewerScreen.kt:71-76` | 菜单/对话框瞬时态；**WebView 页面内部状态** | `remember`；WebView 随组合销毁 | 菜单态丢失（无所谓）；WebView 页面状态丢失（重新加载） | holder 不解决 WebView；如需保留用 keep-alive 或 `WebView.saveState/restoreState`（不建议本轮） |
| 18 | WorkspaceSettingsScreen `workspaces/WorkspaceSettingsScreen.kt:88,133,146` | 滚动；两个 sheet 草稿（可丢弃） | scroll；sheet draft `remember` | 滚动丢失；草稿丢失（可接受） | holder |
| 19 | ChatScreen（base）`chat/ChatScreen.kt:169` | 消息列表滚动、输入框、展开状态 | `rememberLazyListState`（saveable）+ 根级 state | **不丢失**（Chat 是 base，始终组合） | 不动；若未来 Chat 进 AnimatedContent 再包 provider |
| 20 | 根级 sheet（Usage/Jobs/FileBrowser/Voice/PluginSetup）`DeepDeepApp.kt:1029-1085` | 可见性 + 内部瞬时态 | 根级 `remember` | 导航不丢；进程死亡丢可见性（可接受） | 不动 |
| 21 | 屏幕内 sheet（OAuth `ProviderDetailScreen.kt:173`、ScheduleEditor `ScheduleScreen.kt:207`、AgentsDoc/ProjectSkills `WorkspaceSettingsScreen.kt:133,146`） | 表单草稿 | `remember` | 随页面销毁丢失 | 产品决定；建议保持丢弃 |
| 22 | DeepDeepDrawer `DeepDeepDrawer.kt:187` | 展开的工作区集合 | `rememberSaveable`（root） | 不丢 | 已完成 |

## 六、优先级清单

| 优先级 | 改动 | 影响 | 成本 |
|---|---|---|---|
| **P0** | `AnimatedScreenHost` 加 `rememberSaveableStateHolder` + `SaveableStateProvider(routeKey)` + 出栈 `removeState` | 修复全部页面的滚动/tab/搜索词返回丢失 + 进程死亡恢复 | ~20 行，1 个文件 |
| **P1** | `ArchivedSessionsScreen.kt:69`、`SessionSearchScreen.kt:78` 的 query 改 `rememberSaveable` | 这两个页面的搜索词可恢复 | 2 行 |
| **P2** | 回归验证：设置页滚动、插件 tab、个人化草稿、进程死亡（`adb shell am kill` 后重开） | 验收 P0 | 手工 |
| **P3（独立）** | McpScreen 返回时不重复 probe（缓存/节流） | 性能，与状态保存无关 | 单独排期 |

## 七、明确"不需要做"的减法

1. **不引入 Navigation Compose / Navigation 3**：自研 `AppNavigator` 只有 46 行却已覆盖 20 个 destination 的栈式导航、routeKey 序列化、深度转场；引入库需要重写 host、转场、返回处理与 `BackHandler`，唯一收益是现成的 SaveableStateProvider——而这一点用 20 行 holder 就能拿到。官方 Navigation 也是这么做的（[NavHost.kt](https://github.com/androidx/androidx/blob/androidx-main/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/NavHost.kt) 内部 `rememberSaveableStateHolder`）。
2. **不引入 Hilt/Koin 等 DI**：状态保存与依赖注入正交；页面依赖继续从 `AppGraph` 显式传入（现状清晰、可测）。
3. **不给每个页面建 ViewModel**：页面没有需要跨配置变更保留的业务逻辑/异步任务；数据来自 root 层 `StateFlow`（`ScheduleService`、`PluginManager`、`JobRegistry` 等）或一次性 `loadSessions()`。每页 ViewModel 只会增加样板与作用域复杂度（NiA 用 ViewModel 是因为它有 repository 流 + 业务逻辑，见 [NiA 架构文档](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)）。
4. **不用 SavedStateHandle**：它服务于 ViewModel 内的进程死亡恢复；我们没有页面级 ViewModel，root 级状态已经用 `rememberSaveable` 覆盖。
5. **不把页面状态集中到 root 的 Map/全局仓库**：会破坏组合生命周期（无法自动回收）、让 root 每帧重组（Round41 已踩过）、并与 Compose 保存机制打架。
6. **不把列表/消息/探测结果/图片放进 saveable**：Bundle 上限与序列化开销（官方明确 `TransactionTooLargeException`），这些数据应从 store 重新加载。
7. **不为 WebView 内部状态做持久化**（`WorkshopViewerScreen`）：成本高（keep-alive 或 `saveState`），收益低（工作坊页面重进重新加载可接受）。
8. **不保存敏感输入**（API key、OAuth/MCP token）：saveable 会进系统保存的 Bundle，不应承载凭据。

## 八、参考链接（均已实际抓取核对）

- Compose 保存 UI 状态（rememberSaveable / Saver / Bundle 限制）：<https://developer.android.com/develop/ui/compose/state-saving>
- `SaveableStateHolder` API（Provider/removeState/keys 语义与官方示例）：<https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder>
- 保存 UI 状态总览（ViewModel / Saved state / 持久化 对比与进程死亡）：<https://developer.android.com/topic/libraries/architecture/saving-states>
- ViewModel + SavedStateHandle（进程死亡）：<https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate>
- 状态持有者与 UI 状态（UI element state vs screen UI state）：<https://developer.android.com/topic/architecture/ui-layer/stateholders>
- Navigation Compose（导航状态管理章节）：<https://developer.android.com/develop/ui/compose/navigation>
- Navigation Compose `NavHost` 源码（内部使用 `rememberSaveableStateHolder`，见第 848 行）：<https://github.com/androidx/androidx/blob/androidx-main/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/NavHost.kt>
- `SaveableStateHolder` 源码（`removeState` 与销毁保存时序）：<https://github.com/androidx/androidx/blob/androidx-main/compose/runtime/runtime-saveable/src/commonMain/kotlin/androidx/compose/runtime/saveable/SaveableStateHolder.kt>
- Now in Android（UI 层架构、ViewModel + SavedStateHandle 范式）：[仓库](https://github.com/android/nowinandroid)、[ArchitectureLearningJourney.md](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md)
- JetNews（Compose 官方示例，页面状态与 ViewModel 用法）：<https://github.com/android/compose-samples/tree/main/JetNews>
- Compose 官方示例集合：<https://github.com/android/compose-samples>
