# 0003 单 Activity + 自研导航（AnimatedScreenHost）

- 状态：已采纳 · 2026-10-30 · 任务 M1-8（相关 M3-1；实现提交 `8ce82c1`、`1ff85f3`、`bd127d2`、`cb756bc`）

## 背景

导航现状：**一个承载页面的 Activity + 自研路由栈/转场/状态保存**，不是“没有导航”，而是不引入 Navigation 库。

- `AndroidManifest.xml` 里只有 `MainActivity` 是带 `MAIN`/`LAUNCHER` 的页面宿主（`:91-95`，`launchMode="singleTask"`）。另两个 Activity 不参与页面栈：`MiCloudLoginActivity`（WebView 登录，`:77-82`）与 `ArrangePermissionActivity`（透明权限请求，`:84-89`）。
- `AppDestination.kt:3-100` 定义 19 个目的地（`routeKey` + `depth`），`AppNavigator.kt:9-46` 是 46 行的栈实现，`listSaver` 把 `routeKey` 栈交给 `rememberSaveable`（`:37-41`）。
- `AnimatedScreenHost` 在 `app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:57-222`：每页独立 `graphicsLayer`、`translationX` 只在 draw 阶段读（`:252-257`），聊天页常驻 base 并冻结/解冻（`:67-80`），`rememberSaveableStateHolder()` + `SaveableStateProvider(routeKey)`（`:65`、`:266`），出栈一帧后 `removeState`（`:224-235`），`PredictiveBackHandler` 驱动跟手预览（`:94-140`）。
- 深链自研：`deeplink/DeepLink.kt:17-32` 解析 `deepdeep://` 为会话或页面，`MainActivity.kt:113-121` 注入，`DeepDeepApp.kt:573-592` 在 ready 后 reset 到 Chat 并补父页。
- 依赖侧证据：`gradle/libs.versions.toml` 与 `app/build.gradle.kts` 没有任何 navigation 依赖（`grep -n 'navigation'` 无命中）。

为什么必须现在决定：2026-10-30 评审把单 Activity、自研导航、单个 ViewModel 当成结构性错误（`docs/review/10-30/findings.tsv` AC-003/AC-004 及其建议），复核则指出这是项目的明确设计，问题在“要不要拆”而不是“错了没有”（`docs/review/10-30/REVIEW_OF_REVIEW.md §4.6`）。同时 UI 层没有 Compose 测试网（androidTest 只有 `ArrangeDeviceTest`/`ImageNormalizerDeviceTest`，`createComposeRule` 全仓 0 命中，GL-003/SC-014），任何“换导航库”都是一次无安全网重写。不写下这件事的代价，下一个 agent 会按“官方推荐”引入 Navigation 3。

## 决定

**导航宿主保持单 Activity（`MainActivity`）+ `AppNavigator` 截断栈 + 自研 `AnimatedScreenHost`；不引入 Navigation-Compose / Navigation 3，不把页面拆成多 Activity。新增页面只扩展 `AppDestination` 与壳层路由；转场动画只在 layer/draw 阶段驱动，不在 measure/layout 阶段改 offset。**

落地口径：

1. 新增路由：`AppDestination` + `fromRouteKey` 反序列化 + 需要深链时同步 `DeepLink.parse` + 壳层 `when` 路由 + 返回逻辑，四处缺一不可。
2. 转场：层级推进用 shared-axis（位移 25% + 淡入淡出），由 `graphicsLayer` 在 draw 阶段消费进度；不写 measure/layout 型动画。
3. 页面状态：一律 `rememberSaveable` + 按 `routeKey` 的 `SaveableStateProvider`；不要给每个页面建 ViewModel（`docs/research/STATE_MANAGEMENT.md:170-172`）。
4. 预测返回：`PredictiveBackHandler` 只做手势预览与 pop，不引入第二套返回栈；`enableOnBackInvokedCallback` 保持开启（`AndroidManifest.xml:36`）。
5. M3-1 只允许把路由表抽成纯函数并补单测，不允许顺手换库。

## 换来什么

- **每页独立 RenderNode，转场只改图层属性**：避开了 `AnimatedContent` 的 `slideIn/Out` 在 measure/place 阶段每帧改 offset、带动聊天大 display list 每帧重录的问题（根因与 A/B 数据见 `docs/research/NAVIGATION_MOTION.md:9-11`）。round42 最终采样 p50 9–10ms、p90 9–28ms、janky 3–5 帧（41–42 帧）；同轮基线为 22 帧里 21 帧 janky。研究文档明确注明单次 `dumpsys` 波动大、需受控实验，这些数字只作方向证据（`NAVIGATION_MOTION.md:47`）。
- **跨层级共享轴转场**：push 从右滑入、pop 向右滑出，不受“同层级才动画”的限制；聊天页常驻底层并单独成 layer，覆盖后冻结、返回动画结束再解冻（`AppScreenTransition.kt:67-80`）。
- **预测返回**：手势进度直接驱动返回目标页预览，取消回弹、完成落位（`:94-140`）；这是自研 host 才能拿到的控制权，`AnimatedContent` 没有预测进度钩子。
- **每页 SaveableState + 进程死亡恢复**：滚动位置、Tab、搜索词随出栈保存，routeKey 栈由 `rememberSaveable` 持久化，杀进程重开可恢复（`:65`、`:224-235`；`AppNavigator.kt:37-41`）。
- **深链与“补父页”显式可控**：`deepdeep://plugins/<id>` 这类详情页由壳层显式 push 父页再 push 目标（`DeepDeepApp.kt:573-592`），不需要与库的路由图语义对齐。

## 放弃什么（代价，必须承认）

- **没有现成的 deep link 框架**：URI 解析、参数校验、`routeKey` 序列化、补父页逻辑全部自研，19 个目的地、4 类带参路由都要手工维护（`AppDestination.kt:110-147`、`DeepLink.kt`）。
- **没有现成的 back stack 框架**：栈、截断、进程恢复、`pop` 失败清理、手势取消回弹都由 `AppNavigator` 与 `AnimatedScreenHost` 自己保证；预测返回与常规转场两段状态机（`AppScreenTransition.kt:94-173`）是热点。
- **没有 Material sharedElement**：`sharedElement/sharedBounds` 需要 `AnimatedVisibilityScope`（由 `AnimatedContent`/`NavHost` 提供），自研 host 不提供（`NAVIGATION_MOTION.md:166`）。当前只有 shared-axis 位移，不是真正的共享元素转场；要做共享元素必须重新评估（见推翻条件）。
- **无障碍/命中测试/语义/RTL/快速连点/进程恢复都要自己保证**：这些是 `NAVIGATION_MOTION.md:66` 对方案 C 明列的风险；壳层 `DeepDeepApp.kt` 仍是 1213 行热点（`AGENTS.md:53`），导航改动只能靠真机证据（ADR-0005），没有 Compose UI 测试网。
- **放弃库生态的收益**：类型安全路由、嵌套图、测试库、官方文档与升级路径都没有；这些收益需要**在测试网补齐后**才值得交换。

## 被否决的方案

- **Navigation-Compose**：要重写 host、转场、返回处理与 `BackHandler` 才能保住冻结/预测返回机制；唯一稳定收益是现成的 `SaveableStateProvider`，而它用约 20 行 `rememberSaveableStateHolder` 就能拿到（`docs/research/STATE_MANAGEMENT.md:170`，官方 `NavHost` 也是这么做的）。
- **Navigation 3 `NavDisplay`**：官方 2026 路线、自带 `predictivePopTransitionSpec` 与 `SharedTransitionLayout`，但内部仍基于 `AnimatedContent` 的布局型转场（重页面仍需图层隔离），且 back stack 语义与 `AppNavigator` 不同；迁移中-大、无 UI 测试兜底（`NAVIGATION_MOTION.md:67` 方案 D），列为“需要共享元素时再评估”。
- **保留 `AnimatedContent` + 只加 `graphicsLayer`（方案 B）**：改动小、实测有收益（`NAVIGATION_MOTION.md:66`），但没有预测返回进度钩子、跨层级转场仍受限，只作为 P0 过渡，最终被自研 host 取代。
- **每个页面一个 Activity**：返回栈与转场交给系统，但跨页状态共享、聊天 base 冻结、预测返回、深链都需重做，且放弃 Compose 生态默认的单 Activity 假设（Navigation/AnimatedContent/SaveableState 都按单宿主设计）；回归面全局。
- **把导航拆成“官方库 + 自研”混合**：两套栈语义并存会产生两套返回逻辑与状态保存，成本高于纯自研或纯库。

## 后果

- 代价：导航是“自己维护的框架”，每次新增页面/深链都要动 4 处；任何转场/返回缺陷都要真机复现，单测只能覆盖纯函数（`translationFraction`/`alpha`/`popTarget`/`direction`）。
- **被永久驳回的评审建议**：引入 Navigation-Compose/Navigation 3 替换自研 host；以“官方推荐”为由重写根组合或壳层（REVIEW_OF_REVIEW §4 第 6 条：根组合 1159 行整体重写 L 级且踩在冻结/预测返回/SaveableState 机制上）；在没有 Compose 测试网前做导航层一揽子重构。
- SC-014“页面上 Compose UI 测试”仍需做，但按 ADR-0005 列入第二批（先补纯函数/解析类测试），且测试对象是组件语义而不是“替导航库搬家”。
- 新增路由的验收必须包含真机条目：进/出转场、返回、深链直达、进程死亡恢复。

## 推翻条件

- **UI 层有了可用的 Compose 测试网**（至少覆盖 19 条路由的 push/pop/状态恢复），且 Navigation 3 能在不引入布局型转场的前提下提供预测返回与每页状态 → 可重估迁移。
- **真正的 Material 共享元素转场成为产品需求** → 必须评估 `SharedTransitionLayout`（自研 host 不提供 scope），可能推翻“自研 host”而非“单 Activity”。
- **路由复杂度超过手工维护阈值**（例如 50+ 目的地、URI 模板、嵌套路由图）→ 引入路由库或先重构 `AppDestination`/`DeepLink`。
- **需要多 Activity 的系统级集成**（每页独立任务栈、多窗口分屏独立栈）→ 推翻单 Activity 前提，单独 ADR。
