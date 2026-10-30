# 回顾之回顾：对 2026-10-30 代码评审的复核

对象：本目录六份模块报告 + `architecture.md` / `code-quality.md` / `performance.md` / `README.md` + `findings.tsv` 的 128 条。
基线：仓库 HEAD `f1e7d76`（评审生成后无代码改动）。复核为只读回代码，未运行构建/测试。
结论先行：**这份评审整体可信，没有整条误报，也没有发现它把「设计取舍」硬当成缺陷的系统性倾向。** 需要修的是三类东西——少数结论被证据写反了、性能影响几乎全是推演被写成事实、以及约 10 条「照做会让项目变糟」的建议。本文给出逐条判定、必须改写的结论、数字矛盾清单和执行顺序。

---

## 1. 方法与判定口径

6 路并行回代码核验（runtime / chat+performance / app-core+architecture / screens+code-quality / plugins+shell / build-tests+数字），每条给四类判定：

- **成立**：机理、`file:line`、引文与结论都能对上。
- **部分成立**：主结论成立，但严重度、影响链、数字或引用有错（最常见）。
- **误报**：结论不成立（本次没有整条误报，只有 4 条错误子结论 + 1 条建议方向反了）。
- **无法验证**：运行期数据、构建耗时、编译警告数等，仓库内没有可复算证据。

判定分布（按 128 条 findings，逐条口径）：

| 判定 | 数量 | 说明 |
|---|---|---|
| 成立 | 约 73 条 | 机理与证据扎实；多数可直接排期 |
| 部分成立 | 约 52 条 | 主结论对，但夸大/引用错/影响链窄化或过宽 |
| 含错误子结论 | 4 条（+2 条跨模块表） | 见 §2 |
| 整条误报 | 0 条 | — |

`architecture.md` 的 A-01…A-10：8 条成立、A-05 部分成立、A-01 的建议写法有硬伤。

---

## 2. 必须修正的结论（按危害排序）

| # | 位置 | 原文 | 复核结论 | 依据 |
|---|---|---|---|---|
| 1 | `performance.md` P-01；README 局限 | 「无 baseline profile：仓库无 `androidx.profileinstaller`/macrobenchmark」 | **误报（半条）**：`androidx.profileinstaller:1.4.0` 已是传递依赖，APK 内含 `assets/dexopt/baseline.prof`(9,746B)+`baseline.profm`，合并清单含 `ProfileInstallerInitializer`；缺的只是**应用自有** `baseline-prof.txt` 与 macrobenchmark 模块 | APK 解包 + 依赖树 |
| 2 | `module-runtime.md` RT-010 | 「SDK 内部持锁分发 → ABBA 死锁」 | **结论被源码否定**：`subscribe` 用独立 collector 协程回调 listener，不在事件发射线程同步回调；`isBusy`/`getLastAssistantText` 都是无锁内存读；`stop` 回调的 `emit` 是 `tryEmit`。最多算「锁内回调外部对象」的代码卫生（降 P3） | pi2kotlin `AgentSession.kt:124/176/184/343/586` |
| 3 | `module-app-screens.md` SC-017 | 「`OAuthLoginSheet` 手写了与 `OAuthLoginState.waiting` 完全相同的判断，应改用 waiting」 | **错误 + 照做会改行为**：`waiting` 要求 `url != null`；UI 条件要求 `url == null && message == null`，语义不同 | `OAuthLoginSession.kt:28-30` vs `OAuthLoginSheet.kt:160-164` |
| 4 | `module-plugins-shell.md` PL-002 路径二 | 「UI 取消不取消安装（`connect()` 未登记 setupJob）」 | **误报**：`connect()` 全仓库零调用（死代码）；setup sheet 的取消走 `launchBusy` 登记的 `setupJob`，可取消。真实缺口是「Agent 发起的安装被用户点 sheet 取消」时不中断 | `PluginManager.kt:199-203`（无调用点）、`:363-386` |
| 5 | `module-chat-ui.md` CH-012 建议 | 「跟随不必等 2 帧，`layoutInfo` 已是上一帧结果」 | **方向反了**：内容变更后布局尚未发生，用上一帧 layoutInfo 算 overflow 会贴底失败/回弹。可行部分只有「合并三条入口」 | `ChatScrollController.kt:31-35/87-118` |
| 6 | `module-chat-ui.md` CH-010 触发路径 | 「`send` 异常走 `userFacingError`，run 终态走 `runErrorKind`，同错两文案」 | 子结论错：`send` 异常走 `startTurn` catch→`finishRunError`→`runError`；`userFacingError` 用于初始化/steer/工作区/模型/打开会话。「两套分类并存」成立，但「同一 provider 错误两入口文案不同」基本不成立，P2→P3 | `ChatViewModel.kt:536-541/1455-1468` |
| 7 | `module-app-screens.md` SC-009 / `module-chat-ui.md` CH-003 | 「22 个页面用 forEach 渲染列表」「消息内用嵌套 LazyColumn 做块级懒加载」 | 前者数字夸大（23 文件含 `verticalScroll`，仅 14 含 `forEach`，真实列表页约 10）；后者**不可实现**（同轴嵌套 LazyColumn 无界高度），块展平是跨 `ChatTurn`+`ChatMarkdown` 渲染链的改造，成本 L 不是 M | grep 统计 + Compose 测量约束 |
| 8 | `module-plugins-shell.md` PL-003 建议 | 「启动只读持久化状态、探测按需化」 | **照做会功能退化**：插件 `status()` 没有持久化载体，却是 `promptBody()`（每轮系统提示）、`onAppForeground()`、详情页状态的唯一来源；冷启不探测 → 首轮 `official_plugins` 提示段缺失 | `PluginSnapshot.kt:3-15`、`PluginManager.kt:353-361` |

另有 3 处引用/表述错误，已在对应文档行内修正：`code-quality.md`「CancellationException 显式重抛 127 处」（实际约 55 处，127 是提及次数）；`build-tests.md` 亮点「插件层文案也走资源」（插件目录 193 个中文字面量、仅 9 处走资源）；`module-app-screens.md`「design 14 文件 2,439 行」（实际 1,940 行，与 `code-quality.md` 自相矛盾）。

---

## 3. 夸大的三类（严重度整体偏重约一档）

### 3.1 性能结论几乎全是推演

评审自己写了「未在真机上复测性能」，但正文多处把推演写成事实。复核确认：**唯一实测数据是转引项目自身 round41/STATUS**，其余「几十 ms」「60 次/秒」「O(N²)」「1-3 秒」都没有本机测量。

与项目已有实测不吻合的例子：
- CH-001「流式终态单帧几十 ms」：500 行≈15KB lexer 实际亚毫秒级，20×6=140 次短文本 layout 约个位数 ms。
- CH-005「2×10⁷ 字符复制是流式卡顿主因」：约 40MB 累计流量摊到整段回答；项目 round41 实测流式 4.17% janky，不支持该量级。
- P-02「根重组 60/s 实打实掉帧」：60/s 是 16ms 批的理论上界；round41 覆盖层 10s 帧数 1178→10，说明已有机制在起作用。

结论：性能条目按「机制确定性」排期可以，按「ms 数值」宣传不行。

### 3.2 把「理论上可达」写成「会发生」

- RT-012（handle 被回收导致后台唤醒丢失）：回收还要求「>6 handle 且空闲 >10 分钟」，而 `release()` 刚 `touch()` 刷新，窗口几乎不可达 → P3。
- RT-018（kill 与 start 竞态）：窗口是几条指令（µs 级）→ P3。
- AC-010（NotificationCenter 跨线程）：需服务重建瞬间恰有并发事件 → P2 偏高。
- AC-006（prepare 串行链）、AC-008（技能扫描）、P-10（JobsSheet 轮询）：量级未测，实测大概率更低。
- RT-008：用「最坏 800KB/秒」当常态（需 50 条 settled 全写满 16KB）。

### 3.3 数字/口径错误（已在原处修正）

| 位置 | 原文 | 实际 |
|---|---|---|
| build-tests / GL-002 等多处 | 「165 条 warning」 | 0 errors / **160 warnings + 5 hints**（165 是 issue 总数） |
| code-quality C-01 / README | 「CancellationException 重抛 127 处」 | 真正 catch+rethrow 约 55 处 |
| module-chat-ui 头部 | 「ui/chat 31 文件 10,758 行」 | 9,971 行（10,758 重复计入了 `ui/questions` 787 行） |
| module-app-screens 头部 | 「design 14 文件 2,439 行」 | 1,940 行 |
| module-app-screens 概览 | 「范围内 127 Composable 全部 skippable」 | 128 行中 111 个可跳过，17 个 `*Label` 辅助函数不可跳过（正常） |
| RT-022 / CH-002 | 「60 个接口成员」「33 字段」 | 40（36 fun + 4 val）/ 31 字段（33 把 `QuotedMessage` 算进来了） |
| build-tests | 「versions.toml 31 个条目」「10 万行级」 | 44 条目（15+25+4）；约 7.1 万行 Kotlin |
| GL-012 | 「3 次字体/rootfs 提交」「6.4MB 字体」 | 4 次提交；tracked ttf 合计 4,458,200 B |
| RT-006 头部 | 「35 个测试类」 | 34 个测试类 / 364 个 `@Test` |
| findings.tsv | 「128 条 file/line 全部可解析」 | 127/128（GL-008 的 file 列是描述而非路径，已修正） |

---

## 4. 照做会变糟的建议（不要执行，或必须先改前提）

| # | 建议 | 后果 | 替代做法 |
|---|---|---|---|
| 1 | **GL-006 换专用 release keystore** | 现装 APK 是 debug 证书，换签后无法覆盖安装；`allowBackup=false`，卸载即丢 `files/` 会话、schedule/goal/job、DataStore 凭据 | 备份现有 `~/.android/debug.keystore` 到项目外受控位置，`signingConfig` 指向这份**同证书**副本；等某次愿意卸载重装再换 |
| 2 | **PL-001 主建议「损坏时跳过一切写入 / upsert 拒绝覆盖」** | 7 个 INTERNAL 插件的记录、工具、技能会永久补不回来，比现状（只丢 3 条外部记录、内部功能正常）更糟 | 先备份为 `plugins.json.corrupt`，再置 `state.error`，备份成功后允许重建 |
| 3 | **PL-003「启动不探测、只读快照」** | 首轮系统提示缺 `official_plugins` 段，UI 只能显示「已安装」 | 只加 Mutex + `lastProbeAt` 去抖 + 并行化；探测按需化要等补出「未探测」状态 |
| 4 | **RT-024「begin 只更新内存、异步落盘」** | 摧毁 RunLedger 的唯一价值（发送前落盘→崩溃后提示 interrupted） | 合并 `clear`、减少重写；`begin` 的 await 保留 |
| 5 | **RT-005「事件出口改单消费者 Channel(UNLIMITED)」** | `events` 有 4 个订阅者，Channel 单消费者会丢广播；UNLIMITED 在 token 洪峰下无界内存 | 只做「`RunStarted` 在 `begin` 成功后立即发」 |
| 6 | **P-02/P-05「根节点下沉，60/s→0/s，成本 M」** | root 在 220/342/360/506/512/524 多处仍无条件读 chat state，单包一层无效；整体重构 1159 行 root 是 L，且踩在刚落地的冻结/预测返回/SaveableState 机制上，而 UI 层零测试 | 只把 Sheet/Dialog 拆成独立 Composable；其余等测试安全网 |
| 7 | **CH-003「消息内嵌套 LazyColumn」** | 同轴无界高度会触发非法约束，不能直接实现 | 块展平到外层（L 级改造）或「前 N 块 + 展开全部」 |
| 8 | **CH-012「去掉 awaitSettled 两帧」** | 用陈旧 layoutInfo 算 overflow，贴底失败/回弹 | 只合并三条入口 |
| 9 | **SC-017「改用 state.waiting」** | 改变等待态判断语义 | 保持现状，只删死 token/死分支 |
| 10 | **PL-011「卸载时删除截图/录音目录」** | 截图是 Agent 任务产物、录音是用户主动下载的文件，属未告知的数据丢失 | 保留；如需清理给显式确认 |
| 11 | **AC-005/AC-007「主题/字体/语言再落一份同步缓存」** | 在 A-05 自己批评的多套持久化上再加一套，引入双写不一致；语言在 `attachBaseContext` 必须同步读 locale | 主题用 SplashScreen 撑过首次真实值；语言保持现状 |
| 12 | **AC-012「一次重试后 consume」** | 削弱冷启动「等会话列表加载完再跳」的正确语义 | 找不到会话时提示 + 回落最近会话 |
| 13 | **RT-001「给 AppGraph.scope 加 CoroutineExceptionHandler」** | 全局吞掉未捕获异常，与 RT-019 冲突 | 在 `ScheduleService.start()`/宿主启动处局部兜底 |
| 14 | **GL-010 ABI 只留 arm64** | 非 arm64 so 压缩后仅 22KB（0.05%）；`abiFilters` 会让 x86_64 模拟器直接装不上 | 不做，或只在启动时提示「Linux 能力不可用」 |
| 15 | **build-tests 的 detekt/ktlint、CI、Kover、baseline profile** | 单人本地项目收益低、维护成本真实；CI 还被 GL-004 阻塞 | 先做根 `check` 聚合 + pre-commit 一条命令，其余按需 |

---

## 4.5 过度 / 不必要的条目（不是错，是现阶段做了不划算）

| 条目 | 为什么不必做 |
|---|---|
| GL-001 无 CI（P1） | 单人本地迭代 + 唯一 SDK 依赖只在 mavenLocal（GL-004）→ CI 本身就被堵死；一条根 `check` 或 pre-commit 已覆盖 90% 价值。 |
| GL-002 加 detekt/ktlint（M） | Kotlin 2.4.21 + AGP 9.4.1 下的兼容性未评估；「下一个 800 行文件」用拆文件习惯解决，不需要新工具链。 |
| GL-013 Kover 覆盖率（S） | 报告只做不看就会变成噪声；本项目测试是「按风险补」而不是「按覆盖率补」。 |
| GL-008 应用级 baseline profile（P2） | 收益未验证、手写 profile 容易过期；本地分发也没有 Play 云 profile 兜底，投入产出低。 |
| GL-010 ABI 收敛 | 收益 0.05% 体积，代价是模拟器装不上。 |
| GL-012 `gc --aggressive` / LFS / 剥历史 | 12.5MB 的仓库，普通 `git gc` 一次足够；LFS 会给以后每次 clone 增加配置负担。 |
| RT-023 states 加 LRU | 每会话几十字节；`detach`/evict 收尾 `forget` 即可，LRU 是过度设计。 |
| RT-032 从 SDK 元数据推导 provider 名单 | SDK 是否暴露 auth 元数据未验证；加一条差集断言测试成本更低。 |
| A-08 拆 `MiCloudTools` | 体积大但职责相对单一（HTTP/写路径已拆出），属可选优化。 |
| SC-014 立即补 Compose UI 测试 | 方向对，但应作为第二批「重构前置」做，而不是第一批；第一批做纯函数/解析类测试收益更高。 |
| GL-022 根 LICENSE / 统一 git author | 对本地私有项目无实际影响；开源或分享前再补。 |
| 「页面 ≤300 行」「DeepDeepApp 目标 ≤300 行」 | 无依据的重构偏好；按职责切分比按行数切分更有意义。 |

---

## 4.6 评审对项目定位的几处理解偏差

- **把「本地分发的取舍」当工程债**：debug 签名、关闭 minify、universal APK 都是有意的本地安装选择（`performance.md:125`、`architecture.md:204` 自己也这么写），但 `build-tests.md` 的 GL-006/007/010 又要求改成生产形态，两份文档互相矛盾。
- **用团队项目的标准要求单人项目**：CI、detekt/ktlint、Kover、多人提交规范、Git LFS——这些对「一个人、一台机器、直接 adb 安装」的仓库不是缺陷，是成本。
- **把「单一 Activity + 自研导航 + 单个 ViewModel」当成结构性错误**：这是项目的明确设计（`docs/dev/ARCHITECTURE.md` 写明），评审自己也在架构亮点里肯定了状态所有权与恢复语义；建议里却倾向「补 state holder / 拆壳层 / 拆 ViewModel」，需要区分「可测性收益」与「改动风险」。
- **把「无注释」当默认合规是对的**：这一点评审在 C-05 明确排除，值得肯定。
- **低估了「验证文化」的替代作用**：本项目用 `artifacts/validation/` 的真机证据替代 CI 回归，评审把它列为亮点，但在排期时又没有把「真机验收成本」计入 M/L 估算。

---

## 5. 成本被低估的建议（M 实际是 L）

| 建议 | 原文成本 | 实际 | 原因 |
|---|---|---|---|
| RT-003 拆全局锁 | M | L | `closingFiles`/`closingSessions`/eviction/选中会话都依赖全局串行不变量 |
| RT-006 宿主单测 | M | L（前置） | `PiSessionEngine` final、`AgentSession` final+internal 构造器，需先抽接口（生产代码改造） |
| RT-002 宿主 IO 边界 | M | M-L | 要把 `attach/createSession` 全面下移，入口只 dispatch |
| CH-003 块级懒加载 | M | L | 跨 `ChatTurn`+`ChatMarkdown`+选择/引用/脚注链路 |
| CH-002 拆状态通道 | M | M+ | 回调 lambda 每批新建，只拆 `messages` 流不能让可见 item 免重组 |
| AC-003/AC-004 壳层拆分 | M/L | L | 1159 行 root、94 call site、零 UI 测试 |
| P-02/P-05 根节点下沉 | M | L | 同上 |
| GL-007 R8 | M | M+ | pi SDK 无 consumer proguard、依赖 kotlinx.serialization、`Class.forName` 平台类反射；无 CI（回归靠人工真机） |

---

## 6. 建议执行顺序

**第一批：今天可做，低风险、机制确定（不需新增测试）**

1. `GL-005` 边界校验接到子项目 `check`（注意：**根项目没有 `check` 任务**，A-01 的写法要改）。
2. `RT-004` `ScheduleService` 事件过滤 + `ScheduleStore` 内存缓存（S）。
3. `PL-005` 在 `RunFinished` 接 `pluginManager.refreshIfStale()`，并把 TTL 文档统一为 120s（6 处）。
4. `PL-001` 损坏 `plugins.json`：先备份再重建（按 §4 改写后的版本）。
5. `GL-009` 三处 WebView `onRenderProcessGone`（S，已是 lint 明示缺陷）。
6. `RT-019` 三类异常分离（Cancellation 重抛 / Result / Log.w）。
7. `RT-014` v1 `schedules.json` 先备份 `.v1.bak` 再清空（S）。
8. `GL-019` debug 变体开 StrictMode（零成本护栏）。
9. `GL-014` 死资源清理 + `GL-017` 死配置。
10. `AC-009` 抽屉行补无障碍 `onClick` 语义（S）。
11. `SC-007` `DdSheetHeader` 关闭文案参数化 + MCP 错误资源化。
12. UI 小修：`CH-014` 死代码、`CH-015` 刷新合并、`CH-009` 上翻不被拉底、`CH-016` 光标、`SC-018` ticker 下沉、`SC-016` 下拉动画。
13. `RT-001` 损坏数据隔离（**同步改 `ScheduleStoreTest` 的 3 条「保留并 throw」断言**）。

**第二批：先补安全网，再动结构**

1. `RT-006`+`GL-003`：先给宿主抽可注入接口，补 6 组宿主用例；再补 `AppGraph` 装配冒烟与 3 个页面的 Compose 状态测试。
2. 有安全网后：`RT-002` IO 边界 → `RT-003` 拆锁 → `RT-005` `RunStarted` 顺序。
3. `PL-002` 真实取消路径（`NonCancellable` 回滚 + 限时），补取消测试。
4. `SC-003` 错误上行（Result/sealed + UI 错误行）。
5. `GL-001` 根 `check` 一条命令 + pre-commit。

**第三批：需要产品决策或大改版，现阶段不动**

- 壳层拆分（AC-003/AC-004）、`ChatViewModel` 拆分（CH-004/A-06）、`AgentRuntime` 接口拆分（RT-022）、`PluginManager` 拆分（PL-006）——纯结构收益，回归面全局。
- R8 + `shrinkResources`：先做一次 PoC 真机冒烟并保留 mapping。
- 应用自有 baseline profile / macrobenchmark：等有连续迭代需求再做。
- 换 release 签名：等一次愿意卸载重装时。
- 长会话首屏窗口（P-04）、块级懒加载（CH-003）：需产品确认交互。
- `GL-004` 构建可复现：先修 README 版本号即可；`includeBuild`/私有仓等有多机需求再说。

---

## 7. 确定性分级（排期时请按这个读）

| 级别 | 含义 | 本评审中的例子 |
|---|---|---|
| 代码事实（可直接修） | 读到代码即可确认 | `plugins.json` 覆盖链、`SessionCatalog.delete` 吞错、`DdListRow` 语义、三处 `onRenderProcessGone` 缺失、TTL 120s vs 文档 60s |
| 机制推理（可信，但不等于已发生） | 有代码路径，无实测 | `ScheduleService` 每事件读盘、全局锁跨重活、`Markdown` 全量高亮、`Filter` 每批重建 |
| 纯估算（只可用于排序，不可验收） | 无任何测量 | 「几十 ms」「1-3 秒」「60 次/秒」「O(N²)」「PSS 可降 X」 |
| 无法验证 | 仓库内没有证据 | 编译警告 13 条、`check --dry-run` 202 行、lint 耗时、R8 后 DEX 大小、行业基线 15-30% |

另：本评审没有覆盖「运行期并发实况」「TalkBack 实测」「安全动态测试」，报告已自述；`release` 包在真机上的验证仍以 `docs/archive/STATUS.md` 与 `artifacts/validation/` 为准。

---

## 8. 对评审本身的评分

| 维度 | 评价 |
|---|---|
| 事实准确度 | 高。128 条 file/line 基本可解析，P0/P1 的代码路径复核后仍成立。 |
| 证据纪律 | 中上。引用精确，但「推测/估算」与「事实」在正文里混写，需要读者自己分辨。 |
| 严重度校准 | 偏高一档。P1 里约 1/3、P2 里约一半按本项目（单人、本地分发、无 CI）应降一档。 |
| 建议可执行性 | 中。约 10 条建议有害或不可行（见 §4），另有 8 条成本低估。 |
| 对项目的理解 | 好。识别出了真实的结构热点（壳层、ChatViewModel、宿主、PluginManager），也正确肯定了命令面/恢复/工坊边界这些设计。 |

**如果只记一句话**：把这份评审当「问题地图」用（可信），不要当「施工图」用（§4、§5 的地雷要先绕开）。
