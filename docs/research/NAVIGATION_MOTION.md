# 页面转场卡顿根因与生产级丝滑方案

> 范围：DeepDeep（Android + Jetpack Compose，BOM 2026.03.00 / animation 1.10.5，release 包，Redmi Android 13 120Hz）的页面进出动效。
> 证据：`artifacts/validation/round42/{before,after,screens}/`（基线 `before/`、最终实现 `after/`）；采集时 HEAD 为 round41（`5fa3f21`）及其上的实验构建。
> **实现状态（round42 收尾）**：P0 的图层隔离、转场期冻结、输入屏障已落地，P1 的自研 `AnimatedScreenHost` 也已实现并替换 `AnimatedContent`（`app/.../ui/navigation/AppScreenTransition.kt`：每页独立 layer、draw 阶段 translationX、`rememberSaveableStateHolder`、出栈清理、RTL、转场触摸屏障）。最终 3 次采样 p50 9–10ms / p90 9–28ms / janky 3–5 帧（41–42 帧），细节见 `artifacts/validation/round42/lead/QA-LOG.md`。**未做**：PredictiveBackHandler 跟手进度、outgoing 语义隐藏、Macrobenchmark 门禁。

## 一、结论（先行）

1. **瓶颈不在 Compose 组合/布局，而在 RenderThread 的 `issue draw commands`。** 返回聊天的基线段（22 帧）：UI 线程 draw 中位 **1.0ms**（p90 2.1ms），而 RT 的 `IssueDrawCommandsStart→FrameCompleted` 中位 **11.9ms**（p90 19.5ms）、整帧中位 **26.7ms**；Vsync→SyncStart 中位 **11.8ms**（其中"帧晚开始"占大头）。120Hz 的预算是 8.33ms、60Hz 是 16.67ms，22 帧里 21 帧 janky（95.45%），`Frame deadline missed` 21 帧。
2. **结构根因：聊天页（base）在 `AnimatedContent` 之外、且没有独立图层。** 1.10.5 的 `slideIn/Out` 由 `EnterExitTransitionModifierNode`（`LayoutModifierNodeWithPassThroughIntrinsics`）在 **measure/place** 阶段每帧改 offset（`placeable.placeWithLayer`），它会带动包含"聊天大文本列表"的同一份 display list 每帧重录；聊天页没有 `graphicsLayer`，于是每帧都在重录聊天的大 display list。这与官方 `RenderNode` 文档的指导直接对应：把复杂场景拆成多个 RenderNode，"只更新局部、transform 不需要重录 display list"。
3. **最小修复已被 A/B 数据验证：给 base 与动画页各加一块 `graphicsLayer`。** 同一台 Redmi 上新装构建（layer-back-1/2）：`Slow issue draw commands` **18→1**，RT issue 中位 **11.9→6.3/6.5ms**，`p50` 整帧 **20→9/10ms**，janky **95.45%→13.3/10.3%**，同一 320ms 动画渲染帧数 **22→29/30**。（A/B 有构建/profile 混杂，需用 Macrobenchmark 复核，见 §6。）
4. **但图层后仍未达 120Hz：** 中位帧 10.3ms > 8.33ms；首/尾帧仍有 48–85ms 尖峰（`Vsync→DrawStart` 最大 48–70ms），原因包括：转场开始时重新开始绘制聊天（`baseDrawSuspended` 造成的大重录）、流式/滚动等仍在转场期间驱动聊天重组、以及没有转场级冻结与屏障。→ 需要"图层隔离 + 转场期冻结 + 输入/语义屏障"三件套，而不是只加图层。
5. **推荐路线：**
   - **P0（半天～1 天）**：保留 `AnimatedContent`，给 base 与页面加 `graphicsLayer`；把聊天状态冻结扩展至整个转场（进/出都冻结，出转场结束后再解冻）；转场期间阻断输入、隐藏 outgoing 语义；先复测。改动小、已有实测收益。
   - **P1（2–4 天）**：自研 `AnimatedScreenHost`：`Animatable<Float>` 进度 + 每页 `graphicsLayer { translationX = ... }`（在 **draw 阶段 lambda** 读进度，零组合/零布局）；outgoing 保留到动画结束；支持 **可中断/预测性返回**（`PredictiveBackHandler` 驱动进度，取消时回弹）；RTL、`rememberSaveableStateHolder`、输入屏障一并处理。
   - **P2（评估后再定）**：迁移 **Navigation 3 `NavDisplay`**（官方 2026 路线，Now in Android、Jetnews 均已采用）：`transitionSpec/popTransitionSpec/predictivePopTransitionSpec` + `SharedTransitionLayout` 自带预测性返回/共享元素；但注意 `NavDisplay` 内部同样基于 `AnimatedContent`，重页面依旧需要图层隔离，且迁移有 back stack / 状态恢复 / 依赖成本。
6. **不建议**：保持"全屏 layout offset 滑动 + 未分层聊天页"继续调 easing/duration（治标不治本）；也不要用 `alpha`/`CompositingStrategy.Offscreen` 给整页增层来"看起来顺"——fade 会创建 offscreen 合成层、增加 GPU 负担，社区已有在预测性返回动画中改用 Offscreen 导致闪烁/额外层的案例（Decompose #919）。现代 Material 全屏导航更常用 **scale/fade + sharedBounds**，而非整页横向位移（Jetnews 的 predictivePop 就是 `unveilIn togetherWith scaleOut`）。

## 二、实测数据

### 2.1 基线（改动前：AnimatedContent + slide，back 转场，22 帧）

| 阶段（framestats 语义） | min / median / p90 / max (ms) | 解读 |
|---|---|---|
| Vsync→SyncStart（≈ Slow UI 判定区间） | 2.7 / **11.8** / 23.5 / 33.5 | 含"帧晚开始"与 UI 组合+布局+录制+同步 |
| SyncStart→IssueDrawCommandsStart（≈ Slow bitmaps 区间） | 0.1 / 0.2 / 0.4 / 0.8 | 可忽略 |
| IssueDrawCommandsStart→FrameCompleted（≈ Slow issue draw 判定区间） | 4.2 / **11.9** / 19.5 / 27.5 | **RT 发命令/重录/重放，主瓶颈** |
| DrawStart→SyncQueued（UI draw 录制） | 0.6 / **1.0** / 2.1 / 11.8 | UI 不慢 |
| SwapBuffers→FrameCompleted（GPU/队列） | 2.3 / 4.2 / 7.4 / 8.2 | GPU 不是主因 |
| IntendedVsync→DrawStart（帧晚开始） | 1.3 / 7.4 / 17.0 / 24.2 | 前 4 帧 21.2/24.2/21.5/14.7ms |
| IntendedVsync→FrameCompleted（整帧） | 12.9 / **26.7** / 38.7 / 67.3 | 120Hz 预算 8.33ms |

基线 gfxinfo 摘要：22 帧 / 21 janky（95.45%）/ p50 20ms / p90 32ms / p99 61ms；Missed Vsync 7、High input latency 15、Slow UI thread 14、Slow issue draw commands 18、Frame deadline missed 21；GPU p50 4ms。**UI 线程 draw 仅 1ms，却报 Slow UI thread 14 次**——因为 AOSP 的 kSlowUI 判定是 `Vsync→SyncStart`（见 §4.2），其中主要是前一帧 RT 拖累导致的"晚开始"，不是组合/布局本身。

### 2.2 A/B 汇总（同一设备，不同次录制/构建）

| 录制 | 构建/进程 | 帧数 | janky | p50 | p90 | p99 | Slow issue draw | Missed Vsync | Deadline missed | GPU p50 |
|---|---|---|---|---|---|---|---|---|---|---|
| back（基线） | HEAD / pid 4494 | 22 | 21 (95.45%) | 20 | 32 | 61 | 18 | 7 | 21 | 4ms |
| fwd（基线，正向） | HEAD / pid 4494 | 31 | 14 (45.16%) | 13 | 25 | 44 | 14 | 3 | 14 | 6ms |
| back2（基线复测） | HEAD / pid 4494 | 24 | 9 (37.50%) | 13 | 32 | 117 | 8 | 3 | 9 | 4ms |
| layer-back-1 | 新装（+graphicsLayer）/ pid 7589 | 30 | **4 (13.33%)** | **9** | 38 | 85 | **1** | **1** | **4** | 4ms |
| layer-back-2 | 同上 | 29 | **3 (10.34%)** | **10** | 26 | 57 | **1** | **1** | **3** | 3ms |

对应 RT 阶段（framestats）中位数：`Issue→FrameCompleted` 基线 **11.9ms** → layer1 **6.3ms** / layer2 **6.5ms**；`Vsync→SyncStart` 基线 **11.8ms** → layer 组 **3.5/3.3ms**；整帧中位 **26.7 → 10.3/10.4ms**。layer 组的 `Issue→FrameCompleted` 超过 12.5ms 的帧为 **0**，而基线有 10 帧。

> 注意：baseline 复测（back2）janky 37.5%，与 back 95.45% 差异很大 → 单次 `dumpsys` 波动大，正式结论需 Macrobenchmark。layer 组是第一/二次录制之间的"新装构建"，与基线并非同一次构建的严格 A/B；必须记录 git hash + 构建号做受控实验（附录 A）。

## 三、为什么是 RT 重录（代码与机制证据）

1. **HEAD 结构**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt`
   - `AppScreenTransition.of()`（HEAD L31-53）：前进/后退用 `slideInHorizontally / slideOutHorizontally`（320ms，`DdMotion.SCREEN`），`SizeTransform(clip=false)`；同层切换用 fade。
   - `AnimatedScreenHost`（HEAD L57-88）：聊天页是**base**，位于 `AnimatedContent` 之外的 `Box`，只通过 `.drawWithContent { if (baseDrawingEnabled) drawContent() }` 控制是否绘制（**没有 graphicsLayer**）；`AnimatedContent` 内的非聊天页只有 `overlayTouchBarrier`。
   - `DeepDeepApp.kt` HEAD：`covered`（L266-275）控制 `baseDrawingEnabled`，覆盖 320ms 后 `baseDrawSuspended=true` 停止绘制聊天；`rememberFrozenChatState(state, covered)`（L1110-1116）只在 `covered` 时冻结，**返回聊天瞬间就解冻并合入 live state**，于是返回转场期间聊天仍会随流式/状态变化重组。
2. **Compose 1.10 的 slide 在布局阶段**：AndroidX `EnterExitTransition.kt` 中 `EnterExitTransitionModifierNode` 继承 `LayoutModifierNodeWithPassThroughIntrinsics`，在 `MeasureScope.measure` 里读取 slide 动画值，最后 `placeable.placeWithLayer(offset.x, offset.y, 0f, layerBlock)`。即：每帧都走 measure/layout/placement；动画页自身会因 `placeWithLayer` 得到 layer，但**未分层的同级 base（聊天）仍在同一份 display list 记录里**，其重录跟着一起发生。
3. **RenderNode 的本意**：官方文档明确"RenderNodes 把复杂场景切成小块，局部更新更便宜；只有内容本身变化才需要重录 display list；**transform 可以直接改属性而不重录**"。聊天页 = 大文本/列表（glyph 多、draw op 多），每帧重录正是 RT `issue draw commands` 超时的主因。
4. **gfxinfo 计数与阈值**（AOSP `JankTracker.cpp`）：`kSlowUI` = `Vsync→SyncStart > 0.5×frameInterval`；`kSlowSync` = `SyncStart→IssueDrawCommandsStart > 0.2×`；`kSlowRT` = `IssueDrawCommandsStart→FrameCompleted > 0.75×`；`kMissedVsync` = `IntendedVsync→Vsync > 1ns`。且这些"类型计数只在 miss deadline 的帧上统计"（`JankTracker::finishFrame` 先判 `GpuCompleted < deadline`，jank 才进入 COMPARISONS）——所以 baseline 的 18 次 Slow issue draw 应理解为"janky 帧中的 RT 超预算"，不要与全部帧数混用；精确分析用 framestats CSV 的阶段时长而非摘要计数。
5. **AnimatedContent 的固有行为/限制**（官方源码注释）：转场期间新旧内容**都保持组合**，`ContentTransform` 结束后才 dispose outgoing；`SizeTransform` 会做尺寸动画（本项目设 `clip=false`）；`contentKey` 决定是否触发动画。它没有给外部"预测性返回进度"的钩子，也没有"整页共享 layer"的配置——这是 P1/P2 方案的出发点。

## 四、方案对比

| 方案 | 机制 | 改动量 | 优点 | 风险/成本 | 预期（基于数据） | 结论 |
|---|---|---|---|---|---|---|
| **A 现状（HEAD）** | AnimatedContent + layout slide；base 无 layer、覆盖后停画 | 0 | 无 | RT 重录、janky 95% | p50 20ms+ | 不改 |
| **B 现状+图层隔离（最小）** | 保留 AnimatedContent/slide；base 与动画页各加 `graphicsLayer`；转场期间冻结聊天；输入/语义屏障 | ~20–40 行 | 实测收益大；风险低；可随时回滚 | 每块全屏 layer 有 GPU 内存（需实测，见 §5）；未解决 120Hz 首帧尖峰 | p50 9–10ms、janky 10–13%、slow draw 1 | **P0 先做** |
| **C 自研 AnimatedScreenHost** | `Animatable` progress + 每页 `graphicsLayer{translationX/…}`（draw 阶段读值，跳过组合与布局）；outgoing 保留到结束；`PredictiveBackHandler` 驱动可中断进度 | 150–250 行（工作树已有原型，见附录 B） | 彻底去 layout offset；帧成本=改 RenderNode 属性；预测性返回、可中断、无 AnimatedContent 的 SizeTransform 开销 | 命中测试/语义/a11y/RTL/快速连点/进程恢复都要自己保证；无法直接使用 `sharedElement`（需 AnimatedVisibilityScope） | 目标 p50≤8.3ms、janky<5% | **P1 推荐**（先把 B 稳住） |
| **D Navigation 3 `NavDisplay`** | 官方 2026 路线：`ContentTransform` + `transitionSpec/popTransitionSpec/predictivePopTransitionSpec`，内部基于 AnimatedContent；`SharedTransitionLayout` 支持共享元素 | 中-大：新增 navigation3 依赖、NavKey/back stack、状态恢复迁移 | 官方维护；预测性返回/共享元素开箱；NiA、Jetnews 采用 | 仍是 layout 型转场，重页面仍需图层；新依赖/版本/学习成本；back stack 与现有 `AppNavigator` 语义差异 | 与 B+C 类似，取决图层 | **P2 评估**；若近期要做共享元素则优先 D |
| **E Navigation Compose（2.8+）** | `NavHost` 的 `enterTransition/exitTransition/pop*` + Activity 1.6+ 预测性返回 | 中：路由化改造 | 生态成熟、文档多 | 项目已自研 `AppNavigator`，迁回成本高；转场仍 layout 型 | 同 B 的基础问题 | 不优先 |

## 五、推荐方案与代码骨架

### 5.1 P0：图层隔离 + 转场冻结（推荐先落地）

```kotlin
// AppScreenTransition.kt（保留 AnimatedContent，只做隔离）
Box(
    Modifier
        .fillMaxSize()
        .drawWithContent { if (baseDrawingEnabled) drawContent() }
        .graphicsLayer(),            // ← 关键：聊天页独立 RenderNode，动画期间不被整棵树重录
) { base() }

AnimatedContent(/* ... 现有配置不变 ... */) { destination ->
    if (destination == AppDestination.Chat) Box(Modifier.fillMaxSize())
    else Box(Modifier.fillMaxSize().graphicsLayer().overlayTouchBarrier()) { content(destination) }
}
```

配套（`DeepDeepApp.kt`，工作树已在做）：
- 进出转场期间都冻结 `ChatUiState`（`baseFrozen` 直到动画结束），避免聊天流式/滚动在 320ms 内持续驱动重组与绘制；出转场结束后 flush 一次。
- 进入/退出页面时输入屏障只覆盖转场窗口（`progress < 1`），不要永久 consume；outgoing 页面 `Modifier.semantics { invisibleToUser() }`。
- `baseDrawSuspended` 停画策略需要 A/B：停画省电但会让返回首帧重录聊天层（当前尖峰来源之一）；可试验"停画但保留 layer 内容"或"停画 + 提前一帧恢复"。
- 若目标只是"看起来顺"，可把 `DdMotion.SCREEN` 320ms 缩短到 240–280ms、popping 用更短时长（Material 3 建议全屏 begin/end 用 emphasized ≈500ms 是偏长的品牌动效；导航应更短），但时长不是根因。

### 5.2 P1：自研 `AnimatedScreenHost`（可中断/预测性返回）

```kotlin
@Composable
fun AnimatedScreenHost(navigator: AppNavigator, base: @Composable () -> Unit,
                       content: @Composable (AppDestination) -> Unit) {
    val progress = remember { Animatable(1f) }        // 0..1，1=到位
    var front by remember { mutableStateOf(navigator.current) }
    var back by remember { mutableStateOf<AppDestination?>(null) }
    var dir by remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1f else 1f

    LaunchedEffect(navigator.current) {
        val target = navigator.current
        if (target == front) return@LaunchedEffect
        back = front.takeIf { it != AppDestination.Chat }
        dir = AppScreenTransition.direction(front, target)
        front = target
        progress.animateTo(1f, tween(DdMotion.SCREEN, easing = DdMotion.EaseOut)) // 可被新目标打断，从当前值继续
        back = null
    }

    PredictiveBackHandler(enabled = navigator.canPop) { events ->
        try {
            events.collect { progress.snapTo(it.progress) }   // 手指进度直接驱动
            navigator.pop(); progress.animateTo(1f, tween(DdMotion.FAST))
        } catch (c: CancellationException) {
            progress.animateTo(1f, tween(DdMotion.FAST)); throw c  // 取消回弹
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithContent { if (front == AppDestination.Chat) drawContent() }.graphicsLayer()) { base() }
        listOfNotNull(back?.takeIf { it != AppDestination.Chat }, front.takeIf { it != AppDestination.Chat })
            .forEach { d ->
                key(d.routeKey) {
                    val incoming = d == front
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer {                       // draw 阶段读 progress：无重组、无布局
                            val p = progress.value
                            translationX = sign * size.width * fraction(d, incoming, dir, p)
                            alpha = if (dir == 0) (if (incoming) p else 1f - p) else 1f
                        }
                        .pointerInput(Unit) { if (progress.value < 1f) awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                        .semantics { if (!incoming) invisibleToUser() }
                    ) { content(d) }
                }
            }
    }
}
```
要点：① 进度值只在 `graphicsLayer{}` 读（官方 phase deferral 指南：alpha/translation 应 draw 阶段）；② 动画页各自 layer，outgoing 保留到 `back=null`；③ 预测性返回用 `PredictiveBackHandler`（activity ≥1.6，本项目 1.12.4 满足；**Android 13 需 manifest 加 `android:enableOnBackInvokedCallback="true"`**，当前 `AndroidManifest.xml` 没有）；④ 新导航打断时从当前 `progress` 继续，不 snap 回 0。

### 5.3 P2：Navigation 3（若要官方/共享元素路线）

- 依赖 `androidx.navigation3`（BOM 管理），`NavKey` + back stack；`NavDisplay(transitionSpec={...}, popTransitionSpec={...}, predictivePopTransitionSpec={...})`；外层 `SharedTransitionLayout` 可让列表→详情共享元素（Jetnews 已用：`unveilIn togetherWith scaleOut` + `sharedBounds`）。
- 仍需：重页面 `graphicsLayer` 隔离；`AnimatedContent` 内部行为与 §三 相同。
- 迁移风险：现有 `AppNavigator` 的 `rememberSaveable` 只存 routeKey，Nav3 的 `rememberNavBackStack` 有自己序列化；进程恢复/详情页参数要重测。

## 六、风险与边界（必须一起设计）

| 风险 | 说明/对策 |
|---|---|
| 命中测试 | 转场期间两页都在组合中；必须显式消费指针，且仅在动画窗口内拦截；动画结束后 incoming 才可点，避免点到 outgoing 或"穿透"到底层聊天。 |
| 语义/无障碍 | outgoing 页面在转场中仍可能进入语义树；用 `semantics { invisibleToUser() }`/`clearAndSetSemantics` 隐藏；转场结束后把焦点移到新页面首元素并播报。 |
| RTL | 平移方向随 `LayoutDirection` 取反；不要用固定正负号。 |
| 快速连续导航 | 目标可能在 320ms 内再次变化：从当前 `Animatable` 值反向/继续，不要 `snapTo(0)`；同一时刻最多保留 1 个 outgoing；中间页状态只在离开 back stack 时 `removeState`。 |
| 进程/配置恢复 | `AppNavigator.Saver` 只保存 routeKey；页面内部 `rememberSaveable` 在动画容器替换/销毁时可能丢失 → 用 `rememberSaveableStateHolder().SaveableStateProvider(routeKey)`，并在 routeKey 不在栈内时清理；配置变更/进程重建后不播动画。 |
| IME/Insets | 返回聊天时不要同时做 IME 弹出、`jumpToBottom`、`followContent` 等布局动作，延迟到转场结束（`isTransitioning` 结束回调）。 |
| 图层内存 | 每块全屏 `graphicsLayer` 可能对应一块全屏 buffer（本机约 1080×2400×4≈10MB 量级）；本次录制里 `RenderTarget 10.09MB` 在基线复测与 layer 组都出现过，**不能直接归因于图层**，必须单独测（`dumpsys gfxinfo` GPU caches / 每次进出后对比）。低内存设备可只在转场期间挂 layer，结束时移除（要接受一次重录成本）。 |
| 共享元素 | `sharedElement/sharedBounds` 需要 `SharedTransitionLayout` + `AnimatedVisibilityScope`（由 AnimatedContent/AnimatedVisibility/NavHost 提供）。自研 host 不提供该 scope；若共享元素在路线图上，优先 AnimatedContent（B）或 Nav3（D）。 |
| 预测性返回 | Android 13 需要在 manifest 开启 `enableOnBackInvokedCallback` 且系统中打开开发者选项才能看到动画；Android 15+ 默认。`PredictiveBackHandler` 需 activity 1.6+（已满足）。 |
| 过度合成 | 不要为了"顺"给整页加 `alpha`/`CompositingStrategy.Offscreen`：会创建 offscreen 合成层，增加 GPU/带宽；Decompose #919 正是把预测性返回改成"单 layer + Offscreen"以合并属性，但社区也记录了闪烁/依赖问题——按需使用，不做默认。 |

## 七、验收指标建议

**A. 设备 gfxinfo（每次测试前 `reset`，只跑一次转场）**
- `Janky frames` ≤ 5%（同时看 `Frame deadline missed` 帧数 ≤ 10%）；
- `Number Slow issue draw commands` ≤ 2；`Number Slow UI thread` ≤ 1；`Missed Vsync` ≤ 2；
- `p50`：120Hz 目标 ≤ 10ms（后续追 ≤8.33ms）；`p90` ≤ 16.7ms；`p99` ≤ 33ms；
- 同时用 framestats CSV 记录 `IssueDrawCommandsStart→FrameCompleted` 中位 ≤ 7ms、>12.5ms 帧为 0。

**B. Macrobenchmark（正式门禁）**
- `FrameTimingMetric`：push/pop/预测性返回/连续快速进出 4 个场景；
- 目标：`frameOverrunMs` p90 ≤ 0（60Hz 预算）/ ≤ -2ms；`frameDurationCpuMs` p90 < 8ms；
- 同一构建、warm/cold 各 N=10，取中位数与 p90。

**C. 内存/稳定性**
- 进出 20 次后 `dumpsys gfxinfo` GPU cache 无持续增长；`dumpsys meminfo` Java/Native 无逐次上升（layer/state holder 泄漏）；
- 进程重建后导航栈/页面状态恢复正确、不播放动画。

**D. 视觉/交互检查矩阵**：前进/后退、预测性返回取消/提交、快速连点 10 次、RTL、长会话（1000 条消息）+ 流式回复中进出、硬件返回键/手势、TalkBack 焦点顺序、画中画/旋转（如适用）。

## 八、参考链接

官方文档：
- Compose 性能最佳实践（phase/lambda 读值、skippability）：https://developer.android.com/develop/ui/compose/performance/bestpractices
- 官方 Compose 动画性能专项（phase deferral、graphicsLayer/offset lambda、sharedBounds 优化建议）：https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/references/animation
- AnimatedContent 用法与 ContentTransform/SizeTransform：https://developer.android.com/develop/ui/compose/animation/composables-modifiers#animatedcontent
- Compose 阶段（Composition/Layout/Draw）：https://developer.android.com/develop/ui/compose/phases
- 预测性返回（Compose 设置）：https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- 预测性返回进度（PredictiveBackHandler）：https://developer.android.com/develop/ui/compose/system/predictive-back-progress
- 共享元素：https://developer.android.com/develop/ui/compose/animation/shared-elements
- 共享元素 + 导航/预测性返回：https://developer.android.com/develop/ui/compose/animation/shared-elements/navigation
- Navigation 3 转场（ContentTransform / transitionSpec / predictivePopTransitionSpec）：https://developer.android.com/guide/navigation/navigation-3/animate-destinations
- `NavDisplay` API：https://developer.android.com/reference/kotlin/androidx/navigation3/ui/NavDisplay.composable
- `RenderNode`（display list / 分块重录 / transform 不重录）：https://developer.android.com/reference/android/graphics/RenderNode
- Profile GPU Rendering（帧阶段：Draw / Measure-Layout / Sync-Upload / Command Issue / Swap Buffers）：https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering
- `Modifier.graphicsLayer` 参考：https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/GraphicsLayerModifier
- Material 3 缓动与时长建议（Emphasized/Standard、250–500ms）：https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration

开源实现/源码：
- AndroidX `EnterExitTransition.kt`（slide 由 LayoutModifierNode 在 measure/place 实现）：https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/EnterExitTransition.kt
- AndroidX `AnimatedContent.kt`（outgoing 保留到 ContentTransform 结束、SizeTransform、contentKey）：https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/AnimatedContent.kt
- AndroidX `NavDisplay.kt`（Navigation 3 内部使用 AnimatedContent）：https://github.com/androidx/androidx/blob/androidx-main/navigation3/navigation3-ui/src/commonMain/kotlin/androidx/navigation3/ui/NavDisplay.kt
- Now in Android（2026 已迁移 Navigation 3 `NavDisplay`）：https://github.com/android/nowinandroid/blob/main/app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt
- Jetnews（`NavDisplay` + `SharedTransitionLayout` + `predictivePopTransitionSpec` 的 `unveilIn togetherWith scaleOut`）：https://github.com/android/compose-samples/blob/main/JetNews/app/src/main/java/com/example/jetnews/ui/JetnewsNavDisplay.kt
- Decompose #919（预测性返回用单个 graphicsLayer + CompositingStrategy.Offscreen 的取舍与闪烁讨论）：https://github.com/arkivanov/Decompose/pull/919

AOSP（jank 判定与 frame 字段）：
- `FrameInfo.h`（framestats 列：Flags/IntendedVsync/DrawStart/SyncQueued/IssueDrawCommandsStart/FrameCompleted…）：https://github.com/aosp-mirror/platform_frameworks_base/blob/main/libs/hwui/FrameInfo.h
- `JankTracker.cpp`（kSlowUI/kSlowSync/kSlowRT 判定阈值与只在 janky 帧计类型）：https://github.com/aosp-mirror/platform_frameworks_base/blob/main/libs/hwui/JankTracker.cpp
- `ProfileData.h`（JankType 枚举顺序）：https://github.com/aosp-mirror/platform_frameworks_base/blob/main/libs/hwui/ProfileData.h
- `ProfileData.cpp`（jank 类型显示名）：https://github.com/aosp-mirror/platform_frameworks_base/blob/main/libs/hwui/ProfileData.cpp

## 附录 A：测量方法与口径

- 采集：`adb shell dumpsys gfxinfo io.github.whywood.deepdeep reset` → 执行一次 返回聊天 → `dumpsys gfxinfo ... framestats`（CSV）→ `dumpsys gfxinfo ...`（摘要）。所有数据在 release 包、同设备。
- CSV 列表头与 AOSP `FrameInfoIndex` 一致；**注意本机 Android 13 的 framestats 表头把 `FrameStartTime`/`FrameInterval` 两列名字换位了**（实际数据列 10=FrameStartTime、列 11=FrameInterval，值 8333333ns=120Hz），直接按表头取 `FrameInterval` 会得到错误值；本报告用时间戳差值校验后使用。
- gfxinfo 摘要的 "janky" 采用 legacy 16.67ms 阈值；120Hz 下应额外看 `FrameDeadline/FrameInterval`（framestats）与 `Frame deadline missed`。`Slow issue draw commands` 等类型计数只在 janky 帧统计（JankTracker.cpp），不能当作"全部帧里慢的帧数"。
- 本次可比性：back/back2/fwd 同一进程 pid 4494（HEAD 构建）；layer-back-1/2 为新装进程 pid 7589（00:48 安装，早于最终实现）；A/B 非同一次构建。最终实现的收尾后实测见附录 B 与 `artifacts/validation/round42/lead/QA-LOG.md`；正式门禁仍需 Macrobenchmark。

## 附录 B：最终实现与收尾后实测

- `AppScreenTransition.kt` 最终实现：`Animatable<Float>` 进度；`front/back` 双页 + 聊天 base；每页 `graphicsLayer { translationX = ... }`（draw 阶段读进度，不触发布局）；push 新页全宽滑入、pop 旧页全宽滑出（z 序按方向），同层级淡入淡出；`rememberSaveableStateHolder` + `SaveableStateProvider(routeKey)`；转场触摸屏障；RTL 取反；`key(routeKey)` 提到页面循环体最外层；调试日志已移除。快速连续导航仍采用「上一段直接收尾再开始下一段」的简单策略，没有 retarget。
- `DeepDeepApp.kt` 最终实现：导航状态读取隔离进 `NavigationChrome`/`NavigationBackHandler`；聊天在转场期间吃冻结快照，返回动画结束后再延后 160ms 解冻；`baseDrawingEnabled` 逻辑保留。
- 最终构建 3 次采样：41–42 帧、janky 3–5（7–12%）、p50 9–10ms、p90 9–28ms（其中一次设备被后台抢占，GPU p50 13.8ms）；atrace 实测返回键到首帧开始动画 3–5ms，说明 gfxinfo 首帧 60–70ms 的 outlier 是 FrameTimeline IntendedVsync 计数而非真实延迟；动画结束帧约 20ms（退出页销毁 + 聊天解冻，已移到动画后）。
- **未做**：PredictiveBackHandler 跟手进度（需 manifest `enableOnBackInvokedCallback`）、outgoing 页语义隐藏、Macrobenchmark/Baseline Profile 门禁；转场中快速连点仍是收尾跳变。A/B 数据来自不同构建与进程，正式门禁仍需 Macrobenchmark。
