# 插件体系与 :shell 评审

## 概览

范围：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/**`（约 8.5K 行，10 个插件：飞书/网络搜索/GUI/安排/小米云/企业微信 + 目标/后台任务/定时任务/创意工坊，加 9 个框架文件）与 `shell/src/main/**`（8 文件 956 行），并交叉核对了 `AppGraph.kt` 的装配、插件 UI、`docs/user/PLUGINS.md`、`docs/dev/ARCHITECTURE.md`、`docs/user/ANDROID_CAPABILITIES.md` 与相关单测（app 侧插件测试约 5.3K 行，shell 侧约 0.6K 行）。按约定未重复跑构建与测试，结论以源码为准。

总体判断：这是本项目里抽象质量较高的一块。插件契约（`OfficialPlugin`）确实做到了"框架不认识 Linux/CLI/登录"，持久化拆成 record/settings/skill 三个带原子写的 store，技能写入与 rootfs 解压的路径防护做得比多数同类代码扎实，PRoot 的流式输出、超时与进程树清理也处理了常见坑。真正的问题集中在两处：**安装事务的取消/异常路径**（P1）与**启动即全量探测的状态机**（启动成本 + 副作用 + 与文档契约漂移），其次是几个大插件的"状态探测不是只读操作"和 GUI/日历的局部正确性。

没有发现 P0（无脚本注入、无路径穿越、无凭据明文落盘、无 ANR 级主线程 IO）。

## 评分

| 维度 | 分数 | 理由 |
|---|---|---|
| 架构质量 | 7 | 契约稳定且最小化，插件与框架、App 与 Shell 边界清楚，内部/外部分派明确；扣分在 `PluginManager` 职责过载、`status()` 被允许带重副作用、`Ready` 语义被插件滥用，以及状态缓存刷新契约与文档不一致。 |
| 代码质量 | 7.5 | 命名/错误映射/JSON 解析/纯函数拆分一致，纯逻辑单测覆盖好（PluginManager 580 行、GuiTools 594、MiCloud 534、SkillStore 264、PRoot 256）；扣分在 `runCatching` 吞异常的习惯、取消路径的恢复 bug、8 个可变并发标志集中在一个类、`installInternal` 这类误导命名。 |
| 性能与效率 | 6 | 进程启动即串行跑 5 个 PRoot 子进程 + 1 次网络探测、GUI observe 全量建树后才截断、每次启动全量重写技能目录、截图/录音无清理配额、下载进度通知逐 1% 推送；Shell 侧输出处理与 O(1) 安装检查是不错的（`--kill-on-exit`、8KiB 切分、无换行输出不涨内存）。 |

## 亮点

- **安装事务的主体设计是对的**：`FeishuPlugin.kt:58-115` 下载 → 内嵌 SHA-256 校验 → 暂存目录里解压并跑 `--version` 验证 → `cp` 到临时名 + `mv` 原子替换 + 复核版本 → 失败走 `feishuRestoreCommand`（`FeishuPlugin.kt:427-441`）用备份回滚。除取消路径外，事务语义与 `docs/user/PLUGINS.md:56` 一致。
- **插件技能写入的路径防护完整**：`PluginSkillStore.kt:150-171` 拒绝绝对路径、`..`、空段、控制字符、盘符与保留名，`PluginSkillStore.kt:106-123` 先写临时目录再 `replaceDirectory`（rename + 旧目录备份），`PluginSkillStore.kt:190-202` 失败时回滚；`PluginSkillStoreTest.kt` 用 264 行覆盖了认领/拒收/替换/归属删除。
- **rootfs 解压防护强度高**：`RootfsExtractor.kt:36-134` 校验绝对路径、`..`、目标必须落在 root 内、父路径不得是 symlink、symlink 目标不得逃逸、重复条目、512 字节头校验和与单条目 64MiB 上限；`LinuxRootfsInstaller.kt:50-67` 边解压边算 SHA-256 并与内嵌常量比对。
- **PRoot 输出路径避开了典型坑**：`PRootExecutor.kt:136-172` 64KiB 总输出上限 + `truncated` 标志，`LineChunkBuffer`（`:275-343`）按 8KiB 切分且用 `utf8Cut` 保证不切断多字节字符、无换行的长输出不会让内存随输出增长，读取线程同步调用回调形成天然背压；`:58-64` 的取消与 `:219-231` 的进程树 kill 覆盖了 suspend 取消与超时两条路径（`PRootExecutorTest.kt:163-201` 有对应测试）。
- **小米云写路径的注入面被正确关掉**：`MiCloudWebWriter.kt:215-242` 用 `JSONObject.quote` 对 URL 与 form body 做 JS 字符串转义（用户笔记内容不进入 JS 语法），`:36-47` 只允许 `https://i.mi.com` 前缀，`getJson/download` 直接拒绝非 i.mi.com 的最终 host（`MiCloudClient.kt:36,57`）；cookie 走 `EncryptedSecretStore`（`MiCloudCookieStore.kt:7-13`），不存在"用户内容拼 JS 导致注入"的路径。
- **UI 与 Agent 共用同一状态机**：`PluginManager.kt:234-339`（Agent 的 `plugin_setup` 与 UI 走同一 `setup()`）+ `:220-232`（回前台时若已就绪自动完成等待并关闭 sheet），加上全局 `busy` CAS（`:270`），把"用户手动操作 / Agent 调用 / 安装中"三态收敛到一个互斥内，行为可测（`PluginManagerTest.kt:253-398`）。

## 问题清单

### P1

**[PL-001] 损坏的 plugins.json 会在下次启动时被静默覆盖，安装记录丢失（与文档承诺相反）**（P1）· `PluginManager.kt:50`、`PluginRecordStore.kt:69-77`、`PluginRecordStore.kt:45-50`

```kotlin
// PluginManager.kt:50 —— 丢掉 load() 的 error，只取 records
val records = recordStore.load().records.toMutableMap()
...
if (plugin.kind == PluginKind.INTERNAL && plugin.id !in records) {
    runCatching { recordStore.upsert(record) }   // :62
```

`PluginRecordStore.loadLocked()` 在解析失败时返回 `PluginRecords(error = "plugins.json 解析失败…")`（`:75-77`），但 `upsert()`（`:45-50`）完全忽略 `current.error`，直接 `writeLocked(current.records + record)`。于是：plugins.json 一旦损坏，`PluginManager.start()` 会认为所有插件都"没有记录"，接着给 7 个内部插件逐个 `upsert`〔复核修正：INTERNAL 实为 7 个——`internal/` 4 个 + `GuiPlugin`/`WebSearchPlugin`/`ArrangePlugin`，原文「4 个」与同报告 PL-017 自相矛盾〕，第一次写入就把原文件替换成只含内部插件的合法文件，外部插件（飞书/企微/小米云…的记录）全部丢失；随后 `refreshStatuses()`（`:553`）读到的是新合法文件，`records.error` 为空，**用户也看不到任何损坏提示**。`docs/user/PLUGINS.md:105` 与 `:241` 明确承诺"损坏时保留原文件并报错"。

影响：一次性丢失全部外部插件安装记录（技能/二进制仍在，需重新安装才能恢复启用状态与设置展示）；错误被吞，排查无门。触发条件罕见（原子写已挡住半写），但一旦发生就是静默数据丢失，且与文档契约相反。

建议：`start()` 先判 `load().error`，非空时置 `state.error` 并跳过一切写入（`upsert` 也应在 `current.error != null` 时拒绝覆盖，或先把原文件备份为 `plugins.json.corrupt` 再重建）。工作量 S。

**[PL-002] 安装的取消语义不可靠：协程取消时 guest 回滚被取消吞掉，UI 取消则根本不取消安装**（P1）· `FeishuPlugin.kt:109-113`、`WecomPlugin.kt:109-113`、`PluginManager.kt:345-351`

```kotlin
} catch (error: Exception) {
    runCatching { restoreOrRemoveBinary(hadBinary) }   // 内部是 suspend 的 linux.execute
    throw error
}
```

取消有两条路径，两条都不完整：

1. **Agent 路径**（`plugin_setup` 被"停止"中断 → 工具协程取消）：`install()` 的 `catch (Exception)` 在**已取消的协程**里执行 `runCatching { restoreOrRemoveBinary(hadBinary) }`（`FeishuPlugin.kt:110`）。其内部 `linux.execute` 最终走到 `PRootExecutor.executeStreaming` 的 `suspendCancellableCoroutine`，取消态下立即以 `CancellationException` 返回并被 `runCatching` 吞掉，`feishuRestoreCommand`（`:427-441`）实际不执行；`WecomPlugin.kt:110` 同构。
2. **UI 路径**（setup sheet 的取消按钮，`PluginSetupSheet.kt:103,148` → `cancelInteraction()`）：此时 `setupJob` 为 `null`（`connect()` 只是 `scope.launch { setup(...) }`，没有登记 job；`launchBusy` 只在列表安装/卸载时登记），`cancelInteraction` 只置 `cancelRequested=true` 并清 interaction，**安装协程根本不取消**。下载/解压/安装继续跑完、安装记录照写，`setup()` 末尾再按 `cancelRequested` 返回 `cancelledResult`（`PluginManager.kt:299`）——用户看到"已取消"，插件其实装上了。

> 〔复核修正〕路径二**是误报**：`connect()` 全仓库零调用（死代码），UI 的 setup sheet 取消走的是 `cancelInteraction` 里登记的 `setupJob`（`launchBusy` 会登记），能被取消；且 `connect()` 丢弃结果、不展示给用户。真实残留缺口是「Agent 发起的安装被用户点 sheet 取消」——install 不中断、最后给 Agent 返回 cancelled。按 P2 定级仍成立，但描述要按此改写。

影响：路径 1 下若取消落在安装命令的 `cp/mv` 与 `--version` 之间，guest 里可能留下未经验证的新二进制与 `.lark-cli.old` 备份，而记录未写、页面显示未安装（再装会覆盖，属可恢复）；路径 2 是"取消无效 + 结果自相矛盾"〔复核：路径 2 不成立，见上方批注；真实缺口是 Agent 发起的安装被用户取消〕，与 `docs/user/PLUGINS.md:56,240` 的事务/取消承诺不符。

建议：回滚放进 `withContext(NonCancellable)`；`connect()` 把协程登记为 `setupJob`（或在 `setup()` 内部用可取消的子 job 承载 install）〔复核：`connect()` 是死代码，要么删除它、要么把「Agent 安装可被用户取消」做进 `setup()` 的 install 阶段〕，让取消真正中断安装阶段；`installInternal` 取消分支补 `plugin.recover()`。工作量 M。

### P2

**[PL-003] 进程启动即串行全量探测：5 个 PRoot 子进程 + 1 次 HTTPS，且探测无互斥〔复核修正：不固定——企微未授权时 4 个，Linux 不可用或未安装时为 0；「1-3 秒/数百毫秒」是代码推演，仓库内无实测〕**（P2）· `PluginManager.kt:48-76,523-556`、`FeishuPlugin.kt:130-141`、`WecomPlugin.kt:127-147`、`MiCloudPlugin.kt:65`

`AppGraph.init`（`AppGraph.kt:361-363`）在**每个进程启动**（含仅为了投递提醒而拉起 `AgentService` 的后台启动）都 `scope.launch { pluginManager.start() }`，`start()` 末尾 `refreshStatuses()` 对每个"有记录且启用"的插件串行 `status()`。已安装飞书 + 企微时一次启动固定跑 5 个 PRoot 命令（飞书 `--version` + `auth status --json`；企微 `--version` + `auth show --status` + `auth show`），再叠一次小米云 `profile()` 的 HTTPS 请求；每刷新一次 TTL 再跑一遍。`refreshStatuses` 没有任何互斥：`refresh()` 只挡 `refreshJob`（`:78-86`），而 `setEnabled`（`:119-124`）、`setup/launchBusy` 的 finally（`:336,382`）都直接调用它，两个线程同时进入就会重复探测、后写覆盖先写。

影响：PRoot 冷启动单次数百毫秒、5 次串行 ⇒ 每次启动 1-3 秒纯后台 CPU/IO（后台唤醒也照跑），电量与首屏资源竞争；并发刷新时探测翻倍。不是 ANR（都在 Dispatchers.IO），但属于"每个进程启动都付一次的固定税"。

建议：`status()` 探测改为按需（插件页可见 / `plugin_setup` / 显式刷新）或用 `Mutex` 串行化 + 最短探测间隔；启动时只读取持久化状态并用 `Installed` 快照占位，不跑 CLI；〔复核勿做：插件状态**没有持久化载体**，`PluginSnapshot.status` 是运行时探测结果，却是 `promptBody()`（每轮系统提示）、`onAppForeground()`（回前台收尾 sheet）、详情页状态的唯一来源；冷启不探测会让首轮 `official_plugins` 提示段缺失，UI 也只能显示「已安装」。要做必须先补「未探测」状态 + 首轮前刷新策略，否则是功能退化。只保留「Mutex/去抖/并行」子项。〕若保留启动探测，至少并行化（每插件 `async`）并给 CLI 探测加 1-2 分钟的 `lastProbeAt` 去抖。工作量 M。

**[PL-004] 小米云 `status()` 带网络与主线程离屏 WebView 副作用，被任何刷新触发**（P2）· `MiCloudPlugin.kt:65-70,226-240`、`MiCloudSessionKeeper.kt:24-65`

```kotlin
var session = resolveSessionState()                 // HTTP GET /status/lite/profile
if (session.needsRecovery() && !silentRecoveryBlocked) {
    if (recoverFromCookieJar()) ...                 // 再发一次 HTTP verify（3s 超时）
    scheduleWebViewRecovery()                       // 1.5s 后在 Main 上 new WebView + loadUrl + 500ms 轮询 12s
}
```

`PluginManager` 把 `status()` 当廉价只读探测（60s/120s TTL 缓存、列表页/详情页/启动时都会调），但小米云这里做了三件重事：网络请求、写回 cookie、在主线程创建离屏 WebView 并轮询 12 秒（`MiCloudSessionKeeper.kt:31-56`）。因此只要会话过期，打开插件页或冷启动就会在 Main 上初始化 WebView（WebView 首次初始化 + 可能的 provider 安装是百毫秒级，且不可取消的 12s 轮询），同时产生用户未发起的网络请求。〔复核：副作用成立且比原文更广——**未登录（cookie 为空）也会触发离屏 WebView**；但「每次刷新都建」被 5 分钟 attempt 门限挡住，且 `status()` 是异步 `launch`，不阻塞调用方。〕

影响：状态探测变成"有副作用且有成本"的伪只读操作，违背框架对 `status()` 的隐含契约；在无 WebView/厂商 WebView 异常的设备上可能出现主线程卡顿或异常路径。会话过期是常态（cookie 会自然过期），所以这不是边缘情况。

建议：`status()` 只做本地 cookie 判空 + 必要时一次带超时的轻量 HTTP，不调度 WebView 恢复；把"静默恢复"移到 `onAppForeground` / 插件页进入 / `plugin_setup` 这类用户主动场合，或用 `delay` + 前台可见性判断守住。工作量 M。

**[PL-005] 状态缓存刷新契约与文档不一致：TTL 120s（文档 60s），且"一轮运行结束刷新"未实现**（P2）· `PluginManager.kt:653,88-91`、`docs/user/PLUGINS.md:225`

`private companion object { const val REFRESH_STALE_INTERVAL_MS = 120_000L }`，而 `docs/user/PLUGINS.md:69,130,225,227`、`docs/dev/ARCHITECTURE.md:83,89` 六处写 60 秒。更实质的是：`refreshIfStale()` 只被 `PluginsScreen.kt:73-76` 与 `PluginDetailScreen.kt:76-79` 调用；`AppGraph` 只在 `pluginManager.start()` 与小米云 `onSessionRestored` 时 refresh，`RunFinished/RunFailed` 的 collector（`AppGraph.kt:366-373`）只同步技能，**没有"一轮运行结束刷新插件状态"**。

影响：Agent 用 bash 自己完成 `lark-cli auth login`（文档明确允许，PLUGINS.md:225）后，`promptBody()` 会在后续轮次继续告诉模型"未连接，请调 plugin_setup"，直到用户点开插件页；UI 标题/开关同理滞后。属于文档承诺的能力缺失 + 提示词与实际状态漂移。

建议：在宿主 `RunFinished` 事件里接 `pluginManager.refreshIfStale()`（这是文档本意），TTL 常量与文档统一（建议把文档改成 120s 后代码不再动，或反过来）；把 TTL 作为可注入参数便于测试。工作量 S。

**[PL-006] `PluginManager` 职责过载：一个 655 行类里同时是安装器、状态缓存、UI 状态机与贡献注入器**（P2）· `PluginManager.kt:22-46,390-437,479-521,580-626`

一个类同时承担：安装记录/启停、设置持久化、安装/更新/卸载事务（`installInternal:390`，注意它装的是**外部**插件，命名与语义相反）、setup 交互状态机（`interaction/confirmWaiter/setupJob/externalReady/cancelRequested` 5 个可变标志 + `awaitInstallConfirm/runSetupBody/ManagerUi`）、状态探测与缓存、技能同步编排、工具/提示词贡献。并发正确性靠 `busy/cancelRequested/externalReady/contributionsPending/lastRefreshAt` + 2 个 AtomicReference 人工维护，`cancelInteraction`（`:345-351`）与 `onAppForeground`（`:220-232`）是两条隐式状态转移，只有测试在守（`PluginManagerTest.kt`）。

影响：任何安装流程改动都要在 655 行 + 5 个跨协程标志里推演；`installInternal` 这种命名已让评审误判一次；新增"安装后置步骤"（如校验、迁移）没有明确的插入点。这是可维护性成本，而非立即故障。

建议：按职责拆三个协作者：`PluginInstaller`（install/update/uninstall + 回滚，纯 suspend、无 UI 状态）、`PluginStatusCache`（TTL + 互斥 + 探测策略）、`PluginSetupSession`（interaction/confirm/cancel 状态机），`PluginManager` 只保留编排与 `StateFlow` 组装。工作量 L。

**[PL-007] GUI `observe` 先无条件构完整棵节点树，再按 maxNodes 截断**（P2）· `GuiAccessibilityActions.kt:32-45,244-267`

```kotlin
val result = GuiNodeTree.buildSnapshot(root = convert(root), ...)   // convert 全量递归，MAX_LIVE_DEPTH=24
private fun convert(node: AccessibilityNodeInfo, depth: Int = 0): GuiNode { ... (0 until node.childCount).mapNotNull { ... convert } }
```

`convert` 递归构造整棵 `GuiNode` 树（每个节点含 `AccessibilityNodeInfo` token），之后 `GuiNodeTree.buildSnapshot` 才在 `maxNodes=120/400` 处停下。未截断的兄弟子树仍然被完整构建并随即丢弃。对列表/WebView 类页面（成百上千节点）这是每次 `gui_observe`/`gui_find` 的固定浪费〔复核修正：`gui_wait` 走 `containsText`，不建树；原文把它算进来是过度归纳〕，且 `gui_find` 为了拿 ref 还固定用 `GUI_MAX_NODES`（`GuiTools.kt:295`）。

影响：单次 observe 的分配量 ≈ 整树节点数（含对象、Rect、字符串、token），是典型 GUI Agent 循环里最频繁的调用；大页面上会带来几十毫秒级额外分配与 GC 压力，并放大本可避免的内存占用。

建议：把 `maxNodes/maxDepth` 下推给 `convert`（或改成惰性序列/迭代 DFS + 早停），`GuiNode` 只保留需要输出的字段，token 只对已保留节点保存。工作量 S。

**[PL-008] 日历写入非事务：事件 insert 成功后提醒写入失败即抛错但事件已存在，模型重试会产生重复事件**（P2）· `ArrangeCalendar.kt:294-323,450-465`

`create()` 先 `resolver.insert(Events)` 再 `replaceReminders(eventId, reminders)`（`:321`）；`replaceReminders` 先删旧提醒再逐条 insert，任一步失败都会抛出（`:459-463`），`create` 不删除已建事件。工具层会把异常映射成 `isError=true`（`ArrangeTools.kt:288-295`，原文 317-330 行号错〔复核修正〕），模型按提示"重试"就会创建第二个同名事件〔复核：只有 `:323` 的「请稍后重试」文案能支撑「模型会重试」，提醒失败文案没有该提示；概率低，P2 偏高〕。`update()`（`:325-354`）同样先更新事件再替换提醒，提醒失败时事件已改、返回值未产生。

影响：用户日历里出现重复/半更新事件，属于真实数据污染；提醒写入失败在日历 Provider 上并非不可能（日程只读、Provider 忙、账户限制）。文档（`docs/user/PLUGINS.md:195`）只写"可写系统日历事件、读取、修改、删除与提醒"，没有说明提醒失败会留下事件。

建议：`create` 失败时补偿删除刚插入的事件（best-effort）；或先写提醒再插入事件不可行（需 eventId），因此补偿是唯一路径；`update` 的提醒替换可先算好新列表并对失败保留旧列表（当前先删后插，失败会丢旧提醒）。工作量 S。

**[PL-009] 日历插件无权限时仍返回 `PluginStatus.Ready`，`promptBody` 恒称"已可用"**（P2）· `ArrangePlugin.kt:39-43,79`、`ArrangeTools.kt:317-330`

```kotlin
override suspend fun status(): PluginStatus = if (hasCalendarPermission()) {
    PluginStatus.Ready(string(R.string.plugin_arrange_status_ready))
} else {
    PluginStatus.Ready(string(R.string.plugin_arrange_status_calendar_permission))   // 仍是 Ready
}
override fun promptBody(status: PluginStatus): String = PROMPT_BODY   // 恒为"安排插件已可用"
```

框架把 `Ready` 当"可用"：`plugin_setup` 对 `Ready` 直接返回 `ok=true` 并跳过 `setup()`（`PluginManager.kt:285-288,302-312`）；`promptBody` 也不看 status。结果是没授权时：状态显示"就绪（需授权）"〔复核修正：UI 实际显示「日历权限未授予」，不存在「就绪（需授权）」这个标签〕、`plugin_setup` 成功、模型被告知"已可用"，真正调 `arrange_calendar` 才撞权限错误。〔复核：拦截点在 `PluginManager.kt:237-249` 的 INTERNAL 提前返回，不是原文引用的 285-288/302-312 外部分支；`promptBody` 正文已含「未授权时先让用户在插件页授权」，影响主要是信号语义而非功能。〕文档 `ANDROID_CAPABILITIES.md:33` 说"未授权时工具返回明确错误"，这条本身没错，但契约上 `Ready` 的语义被破坏了。

影响：Agent 无法通过 `plugin_setup` 完成授权闭环（它拿不到"未就绪"信号），只能靠工具报错文本自行判断；插件页状态标签与实际可用性不一致。属于契约一致性问题，不造成数据错误。

建议：无权限返回 `NotReady`，把 `promptBody` 改成按 status 分支（与 Feishu/Wecom 一致）；`actions()` 的授权入口在 `NotReady` 下已存在，链路是通的。工作量 S。

**[PL-010] 下载阶段不响应取消，且无磁盘空间/单源重试；取消后仍可能下载完整 100MB**（P2）· `PluginDownloader.kt:22-48,50-92`

`download()` 在 `withContext(Dispatchers.IO)` 里顺序遍历镜像，`ensureActive()` 只在每个 URL 开始前检查一次（`:28,42`）；`downloadFrom` 内部是阻塞的 `HttpURLConnection` 读循环（`:66-88`），没有 `ensureActive`、没有 `connection.disconnect()` 的取消钩子。协程被取消后，阻塞读继续直到读完（上限 100MB，读超时 60s）〔复核修正：100MB 是 `maxBytes` 上限，实际包约 14MB（飞书）/9.1MB（企微），取消代价被放大 ~7 倍〕，然后才在下一个挂起点发现取消。另外：没有 `StatFs` 磁盘空间预检（100MB 包 + 解压后 ~200MB，`/data` 紧张时会在写文件时抛 IOException 且只看到底层错误）〔复核：按实际包大小应改为「14/9.1MB 包 + 解压后约 2 倍」；建议里的 `3×maxBytes=300MB` 阈值会误拒可装设备，应按实际大小或 3×下载字节数——复核〕；每个 URL 只尝试一次，没有同源重试或断点续传。

影响：安装协程被取消（Agent `plugin_setup` 被停止）后网络与存储仍在消耗，与"取消即停止"的承诺不一致；低存储设备上安装失败只报底层 IOException。属资源与健壮性问题，无数据危害。

建议：读循环里每 N 次迭代检查 `currentCoroutineContext().isActive`，并注册 `invokeOnCancellation { connection.disconnect() }`；下载前用 `StatFs` 检查剩余空间（建议 3×maxBytes）并给出可读错误；失败重试 1 次（指数退避）后再换镜像。工作量 S。

**[PL-011] 插件媒体落盘无清理/配额：GUI 截图与小米云录音只增不减，卸载也不删除**（P2）· `GuiTools.kt:499-504`、`MiCloudTools.kt:443-445`、`MiCloudPlugin.kt:54-61`

`gui_screenshot` 每次写 ~450KB PNG 到 `files/gui/screenshots/gui-<ts>.png`，没有数量/天数上限，`GuiPlugin.uninstall()` 是空操作；`mi_cloud_recordings` 下载到 `files/micloud/recordings/`（大文件，通话录音常见几十 MB），`MiCloudPlugin.uninstall()` 只清 cookie/prefs/WebView，不删目录。两者都通过 bind 暴露给 guest（`/gui`、`/micloud`），Agent 也可以自行写入。

影响：长期使用后 App 数据目录无界增长（截图 100 张 ≈ 45MB，录音无上限），用户只能在系统设置里清 App 数据；这是明确的数据资源管理缺口而非正确性错误。

建议：截图保留最近 N 张（或 7 天）并在 `gui_screenshot` 写入后顺手清理；录音/截图目录在对应插件卸载时删除〔复核勿做：截图是 Agent 任务产物、录音是用户主动下载的文件；现有文档的卸载语义只覆盖二进制/配置/凭证/技能/安装记录，自动删除属于未告知的用户数据丢失，应保留或显式确认〕；`/micloud/recordings` 可加"下载总量上限"并在超过时提示。工作量 S。

**[PL-012] 启动阶段所有插件恢复/技能同步/设置应用错误全部被 `runCatching` 吞掉**（P2）· `PluginManager.kt:52,62,68,71`、`PluginManager.kt:628-641`

```kotlin
plugins.values.forEach { plugin ->
    runCatching { plugin.recover() }        // :52
    ...
    runCatching { recordStore.upsert(record) }   // :62
    runCatching { skillStore.sync(plugin.id, skills, record.skillIds, record.enabled) }  // :68
    runCatching { recordStore.upsert(record.copy(skillIds = ids)) }  // :71
```

`applySettings` 里 `onSettingsChanged` 也 `runCatching`（`:630`）。启动时一旦技能同步因磁盘/权限失败，用户看到的是"插件已安装"但技能缺失，没有任何界面提示（`state.error` 只在 refreshStatuses 的 records.error 和用户操作失败时设置）。

影响：故障静默，排查只能看 logcat；与项目其它模块（如 `refreshStatuses` 会把 records.error 上抛）的处理不一致。注意 `installInternal` 里对 `skillStore.sync` 是有显式错误上报的（`:416-421`），说明标准是有的，只是 start 没遵守。

建议：至少把一个聚合错误写进 `state.error`；`start()` 里区分"可恢复（recover）"与"必须暴露（sync/settings）"。工作量 S。

### P3

**[PL-013] 每次进程启动都重写全部插件技能目录（含 content 未变时）**（P3）· `PluginManager.kt:67-73`、`PluginSkillStore.kt:91-123`

`start()` 对每个已安装插件调用 `skillStore.sync(...)`，`sync` 对每个技能无条件 `publishSkillDirectory`（临时目录 + 全量写 `SKILL.md`/随附文件 + rename 替换），即使内容与磁盘完全一致。一个装了飞书+企微+GUI+小米云+创意工坊的设备每次启动要重写约 5-8 个技能目录（数十次文件写 + 目录替换）。

影响：启动 IO 与 inode churn，纯浪费；不是正确性问题，但和 PL-003 叠加构成"启动固定税"。

建议：`writeSkill` 前比对目标 `SKILL.md` 与随附文件内容（已有读逻辑可复用），一致则跳过；或用 marker 里记录的内容哈希。工作量 S。

**[PL-014] rootfs 激活是"删除旧目录 → rename"两步，存在崩溃窗口**（P3）· `LinuxRootfsInstaller.kt:74-75`

`if (rootfsDir.exists()) rootfsDir.deleteRecursively()` 然后 `require(temp.renameTo(rootfsDir))`。正常路径只有在 rootfs 已存在但缺 marker（损坏/半装）时才走到删除，所以不会丢正常安装；但窗口内进程被杀会留下"无 rootfs + 残留 temp"状态（下次 install 会被 `removeStaleTemps` 清理并重装）。

影响：仅损坏态下的可恢复回退，用户需重新安装 Linux；插件二进制与 guest 数据本来也在此场景丢失。属于"文档说事务、实际有窗口"的一致性瑕疵。

建议：把旧目录 rename 成 `.old-uuid` 再 rename 新目录，成功后删除旧目录；失败时回滚 rename。工作量 S。

**[PL-015] RootfsExtractor 只支持普通文件/目录/符号链接，pax 扩展头与硬链接直接抛错**（P3）· `RootfsExtractor.kt:44-70`

`when (type) { '5' -> ...; '2' -> ...; '0','\u0000' -> ...; else -> throw IllegalArgumentException("unsupported tar entry type") }`。当前内嵌 rootfs 是自建的、能通过；但将来换用官方 minirootfs tar 或带 pax 头（`'x'`/`'g'`）、硬链接（`'1'`）的归档会整体失败，且错误信息只报类型不报已解压条目。〔复核修正：异常已带 `$fullName`（`RootfsExtractor.kt:70`），「附条目名」是重复建议；另外纯跳过 pax 头会丢 longname/path 覆盖，需要解析而不是跳过。〕

影响：可维护性陷阱，非当前故障。

建议：忽略 `'x'/'g'` 扩展头（跳过其数据），把 `'1'` 按普通文件内容处理或在构建脚本里记录约束；错误信息附条目数与文件名。工作量 S。

**[PL-016] PRoot 每条命令固定起 2 个 OS 线程；kill 时 `/proc` 子进程探测有全量扫描回退**（P3）· `PRootExecutor.kt:42-63,119-134,233-258`

`executeStreaming` 每条命令创建 `linux-command` 工作线程（阻塞 IO 无法避免）+ `linux-timeout` watchdog 线程（`waitFor(timeout)`）；`childPids` 先读 `/proc/<pid>/task/<pid>/children`，**读不到或为空**就回退到遍历整个 `/proc` 并逐个读 `stat`（`:250-257`）。每次 `killTree` 在无子进程的常见情况下会触发一次全表扫描（数十到数百次文件读）。

影响：命令吞吐不高时无感；但 `job_*`/多会话并发 bash 时线程数与 kill 延迟（100ms 级）会累积，线程数随并发命令线性增长，kill 延迟多出一次 /proc 扫描。

建议：把 watchdog 改成 `ScheduledExecutorService` 的一次性调度（可取消、无需一命令一线程）；`childPids` 先判 `/proc/<pid>/task/<pid>/children` 文件是否存在，存在且为空就直接返回空列表（语义上 children 文件为空即无子进程，不需要回退）。工作量 S。

**[PL-017] `gui`/`web-search`/`arrange` 的 `kind=INTERNAL` 与三份文档的"外部插件"描述不一致**（P3）· `GuiPlugin.kt:34`、`WebSearchPlugin.kt:21`、`ArrangePlugin.kt:26`、`docs/user/PLUGINS.md:9`

代码把 GUI、网络搜索、安排都声明为 `PluginKind.INTERNAL`（启动自动补建记录、无下载/更新/删除入口、大小显示"内置"），而 `docs/user/PLUGINS.md:9` 把它们列在"外部插件（EXTERNAL）"下（第 178-188、190-198 行也按"安装/卸载"描述），`docs/user/ANDROID_CAPABILITIES.md:10,33` 同样写"插件页安装并启用"。`docs/user/PLUGINS.md:248` 又建议"只贡献工具的插件可设 INTERNAL"，与前者矛盾。

影响：文档与实现漂移，评审/新人需要反复确认；用户实际行为按代码走（这三个插件没有卸载/重装入口，但本来就是空操作）。属于可维护性。

建议：统一成代码现状（这三者确实是"随 App 提供、无安装物"），更新 PLUGINS.md 的分类表与相关段落；若产品上希望保留"外部"分类，则改代码但需解释空安装流程的意义。

**[PL-018] 安装进度按 1% 推送通知，一次下载可达 100 次 binder 调用**（P3）· `PluginManager.kt:558-571,611-623`、`PluginDownloader.kt:77-85`

`PluginDownloader` 在每个百分比变化时回调（`if (percent != lastPercent)`），`progressAdapter.report` 每次 `_state.update` + `notifyInstall(...)`（`NotificationManager.notify`，`NotificationCenter.kt:106-117`）。100MB 包 = 100 次通知更新；再加上每次 `_state` 复制整份插件列表。〔复核修正：回调次数与包大小无关（每个百分比变化一次，最多 101 次，实际包 14/9.1MB 回调更少）；`_state.update` 是 `copy()` 浅拷贝、`plugins` 列表引用复用，不是「复制整份列表」。P3 仍成立。〕

影响：安装期间通知栏/系统服务无谓刷新，低端机上可见轻微卡顿；不是正确性问题。

建议：进度回调节流（如每 5% 或 ≥200ms 一次），通知只在阶段变化与整十百分比更新。工作量 S。

**[PL-019] `gui_tap` 的坐标选择器不校验屏幕边界，可点到状态栏/导航栏/其他窗口**（P3）· `GuiTools.kt:546-573`、`GuiAccessibilityActions.kt:269-276`

`parseSelector` 只校验 `x,y >= 0`，`tapPoint` 直接派发手势；没有与 `service.rootInActiveWindow` 的 bounds 或 `DisplayMetrics` 比对，也没有"点在系统栏/系统弹窗上"的保护（技能里靠提示词约束）。模型算错坐标或页面刚滚动时容易误点。

影响：误操作风险（可能触发返回/主页手势区），属于功能风险而非安全漏洞。

建议：坐标派发前钳制到当前窗口 bounds 内；点在系统栏区域时返回明确错误。工作量 S。〔复核：clamp 会把越界坐标变成「另一种误点」，返回明确错误比 clamp 更稳；两者可只取后者。〕

**[PL-020] 企业微信"退出登录"只删本地凭据文件，不调用服务端登出**（P3）· `WecomPlugin.kt:178-186`、`WecomCli.kt:40`

`logoutCommand() = "rm -f $CREDENTIALS_PATH; true"`，与飞书的 `lark-cli auth logout`（`FeishuCli.kt:37`）不一致。若 CLI 没有提供登出命令，这是合理取舍，但 SKILL 与提示词都写"退出登录"〔复核修正：不实——`WecomPlugin` 的 SKILL 与 `promptBody` 均无「登出」字样，只有 UI 动作文案写「退出登录」〕，语义上会让用户以为服务端会话已失效。

影响：本地凭据已删，App 侧不可再调用；服务端 token 可能仍有效（直到过期），属于安全加固点而非漏洞（token 在 guest 加密文件里，已随文件删除）。

建议：若 `wecom-cli` 支持 `auth logout` 则调用；否则在 UI 动作文案/文档里明确"仅移除本机凭据"。工作量 S。

## 架构评估

**插件契约**：`OfficialPlugin`（`PluginApi.kt:13-62`）稳定且小，23 个成员里 13 个有默认实现（必须实现的只有 10 个），`tools(binding)` 的默认实现把"外部插件不需要会话绑定"这件事表达得很干净；`PluginKind` 只有两值，内部分派在 `PluginManager.tools()`（`:93-101`）一处，没有散落。缺点有三：一是 `status()` 被当作廉价只读探测但没有任何文档/类型约束阻止副作用（PL-004）；二是 `PluginStatus.Ready/NotReady/Failed` 与"可安装/已连接/需授权"三种语义被插件各自解释，`arrange` 把"未授权"塞进 `Ready`（PL-009）；三是 `setup()`/`runAction()`/`actions()` 三件套在无状态（arrange）与有状态（飞书登录、小米云 WebView）插件间的职责不一致，但都还算在契约内。

**状态机与并发**：全局互斥 (`busy` CAS) 覆盖 install/update/uninstall/setup/runAction，方向正确；UI 与 Agent 共用 `PluginsUiState` 与同一个 sheet，回前台自动完成等待（`:220-232`）是很聪明的实现。问题是互斥之外还有 4 条并发写路径：`setEnabled`（`:105-129`）与 `setSetting`（`:131-171`）完全绕过 `busy`（只靠 UI 禁用规避，UI 是唯一调用方所以暂时安全，但没有防御）；`refreshStatuses` 无锁（PL-003）；`cancelInteraction` 直接改 5 个标志。`@Volatile`/Atomic 用得对，但组合状态不是原子的（例如 cancel 与 install 的竞态正是 PL-002）。

**扩展性**：新增插件只需实现接口 + 在 `AppGraph` 注册（`docs/user/PLUGINS.md:246-254` 的流程与代码一致），确实做到了"框架不改"。技能多文件、设置三种类型、`legacyOwned` 迁移、内部插件补建记录，这些机制都在框架层，插件侧零样板。真正的扩展性瓶颈是 `PluginManager` 的体积（PL-006）和"安装后置步骤无处安放"。

**与 App/Shell 的边界**：`plugin` 包只 import `io.github.whywood.deepdeep.shell` 的 `LinuxEnvironment`（飞书/企微构造参数），没有反向依赖；`runtime` 侧只暴露 `PluginController` 接口（`PluginContracts.kt:3-5`）给 `plugin_setup` 工具。`:shell` 完全不知道插件存在，`extraBinds` 由 App 组装传入，这条边界是干净的。唯一模糊处是 App 侧插件自己拼 shell 命令（飞书/企微的 install/extract 脚本）——命令只由常量拼成，`deviceCode` 等外来值经 `shellQuote`/`encodeComponent` 转义，没有发现注入路径。

## 性能与效率

1. **启动探测固定税**（PL-003）：每进程启动最多 5 个 PRoot + 1 次 HTTPS，串行、无去抖；对只在后台投递提醒的进程启动同样生效。这是最值得优先处理的一处性能问题。
2. **GUI 全量建树**（PL-007）：`convert` 在 `maxNodes` 截断前构造整棵树，GUI Agent 循环里最高频的调用每次都付全量分配成本。
3. **启动技能重写**（PL-013）：内容未变也全量写盘 + 目录替换，5-8 个技能，启动时数十次文件 IO。
4. **状态探测的串行化**：`refreshStatuses` 里 `plugins.values.map { ... }` 是顺序 await（`PluginManager.kt:526-547`），每个 CLI 探测 20-30s 超时，最坏情况一次刷新可占用 2 分钟以上（超时叠加），期间 `refreshJob` 占位、后续 refresh 被丢弃。建议并行化 + 单插件超时收敛。
5. **媒体文件无上限**（PL-011）：截图 450KB/张、录音几十 MB/个，长期无限增长，用户清 App 数据前不可回收。
6. **Shell 侧做得好的地方**：bash/流式输出没有按行缓冲整行（8KiB 切分 + UTF-8 边界），输出总量 64KiB 硬上限，安装检查 O(1)（rootfs marker 读一行），磁盘占用只在进入运行环境页统计一次——这些正是文档 `ARCHITECTURE.md` 声称的性质，代码与文档一致。
7. **可观察的成本**：安装进度 1% 一帧通知（PL-018）、下载后的 SHA-256 全量重读（100MB 读一遍，必要）、PRoot 每命令 2 线程（PL-016）。

## 优先修复建议

1. **PL-002 取消回滚**（P1，S）：`withContext(NonCancellable)` 包住 `restoreOrRemoveBinary` / `restoreCommand`，`installInternal` 的 cancel 分支补 `recover()`。这是安装事务承诺里最主要的一处洞，改动很小。
2. **PL-001 损坏记录保护**（P1，S）：`start()` 检查 `load().error` 后拒绝写入并把错误暴露到 UI，`PluginRecordStore.upsert` 在 error 时备份原文件。防的是罕见的静默数据丢失。
3. **PL-003 + PL-005 状态探测策略**（P2，M）：启动只读持久化状态、探测加 `Mutex` + 去抖、`RunFinished` 接 `refreshIfStale()`，并把 TTL 从 120s 与文档统一。一次改动同时解决启动成本与提示词滞后。
4. **PL-004 小米云探测去副作用**（P2，M）：`status()` 不做 WebView 恢复与二次网络验证，把静默恢复移到用户主动路径。这能去掉主线程 WebView 初始化的抖动源。
5. **PL-007 + PL-013 GUI/技能热路径**（P2/P3，S）：GUI `convert` 下推截断参数；技能同步内容一致就跳过。两处都是小改动、高频收益。
