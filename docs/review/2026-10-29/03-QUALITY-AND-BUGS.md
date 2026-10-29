# 代码质量与缺陷

## 总体结构判断

代码已经有四个值得保留的基础：单向构建模块、独立于具体 UI 的 AgentRuntime 接口、可测试的 ConversationReducer、统一设计与官方插件接口。SDK 负责模型循环，Host 负责 App 会话投影，Linux 实现 ShellExecutor，这种分工成立。不能因为存在适配代码就称为“胶水”，也不能只因为大文件就断言为坏代码。

真正的问题是多个子系统对同一个事实分别做决定：

| 事实 | 当前散落位置 | 已造成或可能造成的后果 |
|---|---|---|
| 会话是否可以开始运行 | ViewModel.sending、Host.busy、SDK.activeOperation、后台回调 | UI 的防重复不保护后台入口，runId/终态可覆盖 |
| 停止是否完成 | ViewModel.stop、Host.stopRequested/STOPPED、SDK.abort、PRoot kill | 停止信号、UI 终态、进程真正退出被混合 |
| 是否应该重试 | SDK retry loop、ViewModel 自动重试 | 用户停止没有取消 UI 侧倒计时；两层重试的总预算不清楚 |
| 能力是否改变 | PluginManager、MiCloud 开关、AppGraph wait-empty、Host.reconfigure | 等待空闲与真正重配不是一个原子操作；会话整体重建 |
| 数据是否已可靠保存 | 内存 cache、异步写、各 Store 的临时文件/错误策略 | UI 显示成功和磁盘提交成功并不总一致 |
| 工坊是否隔离 | 独立 origin、临时 cwd、工具名字过滤、共享 Linux | 网页 origin 隔离被误当成 Agent 能力隔离 |

这些是结构性复杂度，靠再加几个判断容易继续变复杂。最优先的重构目标应是把这些事实各自收回一个明确的所有者。

## 复杂度集中在哪里

| 文件 | 行数 | 判断与拆分依据 |
|---|---:|---|
| ChatViewModel.kt | 1,254 | 恢复/发送/重试/技能/附件/模型/工作区/搜索/缓存/对账集中；按用例与运行控制拆，不能只按行数切文件 |
| DeepDeepApp.kt | 1,036 | 页面装配有必要，但大量业务副作用和 handler 可移到 feature coordinator；根组件应保留导航与依赖连接 |
| MiCloudTools.kt | 1,008 | 七个数据域的 schema、请求、结果裁剪集中；适合按 notes/todo/messages/contacts/media 拆，复用共同网关 |
| MarkdownParser.kt | 922 | 是相对内聚的算法模块，复杂度不是跨层混杂；重点是边界输入、增量性能和维护成本，不宜仅拆文件掩盖复杂性 |
| AgentRuntimeHost.kt | 726 | 运行、目录、模型、MCP、后台注入都在同类；应提取 SessionRegistry、会话命令处理、SDK事件适配与配置更新 |
| PluginManager.kt | 548 | 生命周期、交互、进度与贡献管理相关，但升级事务与现有安装保护需要独立契约 |

app 89 个 UI 文件共 21,642 行，35 个插件文件 7,531 行。app 的大部分代码是展示/集成，不能用整个 app 模块比例推断算法效率或“业务核心很脏”。

## 统一性具体评价

- **视觉较统一**：主题、Dd 组件、motion、图标和常见菜单被集中管理；缩略图 LruCache、工具卡解析 remember 和 graphicsLayer 动画有明确优化意图。
- **类型大体统一**：RuntimeEvent/AssistantMessage/ToolTrace、sealed interface、Flow 与不可变状态是通用风格；但 SDK 类型通过 runtime/shell 的 `api` 依赖向 app 暴露，工具 schema 与 App 领域模型相连较深。
- **存储策略不统一**：目标/提醒损坏会抛异常，插件记录损坏返回空记录加错误，会话目录删除吞异常，SDK JSONL 追加没有尾部修复。并非所有数据都需要相同存储格式，但成功/损坏/恢复语义需要一致。
- **并发策略不统一**：Mutex、synchronized、AtomicReference、StateFlow、全局 busy、延时和 UI 再对账并存。机制多不是问题，缺少明确的每会话所有权和原子状态迁移才是问题。
- **错误与取消不统一**：部分模块显式重抛 CancellationException，部分 runCatching 或网络阻塞调用没有贯穿取消；工具文案里存在中文/双语硬编码，ChatUiStrings 又另行分类。
- **文档存在漂移**：ARCHITECTURE 中“模型切换同步到所有会话”的旧描述与当前按 sessionId 切换实现不一致；STATUS 是历史证据，本轮 lint warning 数也不同。文档不能替代代码或本轮实测。

## 证据等级与严重性

“复现”指真实项目类/当前 SDK 在隔离测试中表现出问题；Android 和第三方服务依赖可以由可控替身提供。“静态确认”指明确存在相关代码路径，但未测出设备端后果。“风险”不等于已经观察到线上故障。P1/P2 是修复顺序，不是声称每个问题都能远程利用。

审阅用例在本目录 harness 中，通过额外 Gradle init 文件接入，未改原测试目录。测试断言记录当前有问题的行为；修复后应改成断言正确行为。

## B01 [P1，已复现] 工坊 Agent 可越过临时工作区

**触发**：网页经 `app.agent.run()` 使用默认工具，Agent 调 read/write 或取得 bash。  
**行为**：`workshopAgentToolVisible()` 只排除 arrange_/mi_cloud_/gui_ 前缀；原生 read/write/edit 等仍在。SDK `resolveToolPath` 允许绝对路径和 `..`，文件系统是 `FileSystem.SYSTEM`，没有 scoped wrapper。临时 cwd 仅改变默认相对目录。

隔离用例在临时目录创建 `isolated-workspace` 和兄弟哨兵文件，通过实际 SDK read 工具读取 `../outside.txt` 成功。没有读取用户真实文件。由源码可推导同 UID 可访问文件可能暴露；本轮没有测试 Android Keystore 解密或跨 UID 访问，不能把该结果扩大为读取所有手机数据。

来源：[工坊过滤](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:260)、[工具与 FileSystem.SYSTEM](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:117)、[当前 SDK 路径解析](evidence/sdk-0.2.2/PathUtils.kt)。

**修复契约**：给工坊显式允许的工具和数据范围；文件工具验证 canonical 路径及 symlink；shell 必须单独授权或交给真正有隔离保证的后端。单纯黑名单或 system prompt 不能形成这个边界。MCP 在工坊中确实关闭，此点不应误报。

## B02 [P1，已复现] 崩溃尾行吞掉下次写入

**触发**：JSONL 末尾出现半条记录，重新打开后追加新记录。  
**行为**：读取器跳过无效 JSON，写入器直接 appendingSink，不在追加前截断/隔离尾部或补齐记录边界。新记录与坏尾拼为一行，再打开后整行被跳过。

用例先写有效 header、名称 before 和损坏尾行；重开追加 after-crash；再次重开名称仍是 before。用的是当前构建对应的 SDK 0.2.2，而非旧版本记忆。完整但缺换行的尾行也存在追加边界风险；本轮主要复现的是半条尾行。

来源：[实际持久化代码](evidence/sdk-0.2.2/SessionManager.kt)、[复现用例](harness/runtime/io/github/whywood/deepdeep/runtime/AuditRuntimeTest.kt)。

**修复契约**：SDK 层启动时扫描并修复最后有效记录边界，坏尾另存，追加必须保证 LF；写入崩溃点、重开追加、再重开可见性必须一起验收。这项修复应进入 pi2kotlin，再发布并在 App 集成验证。

## B03 [P1，已复现] 停止未取消自动重试倒计时

**触发**：自动重试已启用，网络失败后进入 2 秒等待，此时点停止。  
**行为**：`stop()` 只调用 runtime.stop；这时 runtime 可能已经没有 active run，停止无事可做。`autoRetryJobs[sessionId]` 未取消，延时结束仍 `beginRetry()`。

实际 ChatViewModel 与仓库 fake runtime 联调：发 RunFailed(network error) → stop → 前进 2.5 秒，`runtime.retryLastTurn` 调用计数为 1。

来源：[自动重试](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:278)、[停止入口](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:412)、[复现](harness/app/io/github/whywood/deepdeep/ui/chat/AuditRetryTest.kt)。

**修复契约**：Stop 取消该会话所有待启动重试/目标续跑/后台 run 请求，递增执行 epoch；旧 epoch 的延时任务不能复活。短期可先 clearAutoRetry，但最终要收回统一运行控制器。

## B04 [P1，已复现] 升级失败破坏旧插件

**触发**：已经安装 1.0 的插件更新到 2.0，下载/安装阶段失败。  
**行为**：`installInternal()` 的异常分支无条件 `plugin.uninstall()`，即使新包尚未替换旧包。旧安装记录保留，技能还可能被删除。

用实际 PluginManager 和 fake 插件复现：升级前 installed=true；install 在替换前抛错；升级后 installed=false，但持久记录仍是 1.0，UI 的 `snapshot.installed` 仍为 true。对飞书这种 uninstall 会删除 CLI 配置/数据目录的实现，潜在后果包含重新授权；本轮没有用真实账号执行此场景。

来源：[升级与异常处理](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:305)、[飞书卸载内容](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:37)、[复现](harness/app/io/github/whywood/deepdeep/plugin/AuditPluginTest.kt)。

**修复契约**：下载校验→暂存安装→健康检查→切换版本→提交记录；失败回滚旧版本，不能用“卸载整个插件”清理一次失败升级。首次安装清理和已有安装升级必须分开。

## B05 [P1，已复现] 全天日历事件日期偏移

**触发**：Asia/Shanghai 下输入 `2026-10-30`、allDay=true。  
**行为**：`parseCalendarMillis` 先按上海零点转成 `2026-10-29T16:00Z`，`resolveEventRange` 再向 UTC 零点取整，得到 10 月 29 日；返回显示也为 10 月 29 日。

现有测试分别验证了本地日期解析与 UTC 取整，却没有组合二者。真实函数联调复现 requested=2026-10-30、actual=2026-10-29。Android Calendar 的全天事件应使用 UTC 对应日期边界；应从 LocalDate 直接编码该日期，而不是把一个当地时间 Instant 粗暴向下取整。[Calendar 官方契约](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns#ALL_DAY)

来源：[日期解析](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt:23)、[UTC 取整](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:99)、[复现](harness/app/io/github/whywood/deepdeep/plugin/arrange/AuditCalendarTest.kt)。

**修复契约**：全天事件保留 LocalDate/日期范围；普通事件保留 Instant+ZoneId。分别覆盖正/负时区、跨日和夏令时，验证实际 Provider 保存日期。

## B06 [P2，已复现] 凭据写入未按 Provider 串行

**触发**：短时间内保存新凭据、OAuth 刷新/切换认证方式，旧写入恰好较慢。  
**行为**：缓存同步更新，磁盘各自 `scope.launch(ioDispatcher)`；先调用的协程不保证先提交。pendingWrites 是 Set，无法表示同一 provider 多个写入数量/版本。

在旧 saveRecord 被可控挂起的替身中，先写 old、再写 new、释放 old：内存 new，磁盘 old。加密/DataStore 的单次写事务不能替代上层“最后一次用户意图获胜”的排序。该用例没有测真实手机磁盘出现频率。

来源：[异步写](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:123)、[复现](harness/runtime/io/github/whywood/deepdeep/runtime/AuditRuntimeTest.kt)。

**修复契约**：按 Provider 的串行队列/修订号、可 await 的提交、失败状态回传、刷新与登出互斥；重启后最新值保持，禁止旧刷新 token 在登出后重新落盘。

## B07 [P2，已复现] 时钟重算会跳过未投递提醒

**触发**：一次性提醒原定 09:01，尚未投递，系统时钟调整后当前时间为 09:02，触发 recomputeTargets。  
**行为**：`nextRun(now)` 返回 null，被直接 enabled=false、nextRunAt=null；lastDeliveredAt 仍为空。该次提醒失去补投机会。

另一个静态限制：ScheduleStore/Service 默认创建 `Clock.systemDefaultZone()` 并长期保留，该 Clock 的 zone 是创建时固定的；仅调用同一个 Store 的重算不等于重新读取系统新时区。本轮未做手机改时区实验。

来源：[重算](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:223)、[广播](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:27)、[复现](harness/runtime/io/github/whywood/deepdeep/runtime/AuditRuntimeTest.kt)。

**修复契约**：把“已错过尚未投递”与“无下一次”分开；先处理 overdue/outbox，再按当前 zone 重算未来触发。

## B08 [P2，已复现] 输出有上限，行缓冲却没有

**触发**：后台命令输出很长、一直没有换行。  
**行为**：result.output 只保存 64KiB，但 `pendingLine` 对每个字节持续累积，直到换行/EOF 才回调；下游 256KiB ring 无法限制它上游的内存。

8MiB 无换行输出：最终 output=65,536 字节且 truncated=true，但单次 callback_chunk=8,388,608 字符。更大输出的内存增长由源码直接可见；本轮没有进行 OOM 或手机内存测试。

来源：[pendingLine](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:136)、[复现](harness/shell/io/github/whywood/deepdeep/shell/AuditShellTest.kt)。

**修复契约**：按有限大小字节块发布、增量 UTF-8 解码、独立输出文件和游标回读；行仅作为展示形式。分别限制读取缓冲、事件队列、持久输出和模型上下文。

## B09 [P2，静态确认 + 性能观察] 每条 shell 命令扫描全部 rootfs

`LinuxEnvironment.execute/executeStreaming` 调 `installer.currentState()`，进入 readState→directorySize→Files.walk(rootfs)。已安装标记校验和展示磁盘占用被绑在一起。`LinuxEnvironment` 的安装状态初始化也会走同样路径。

合成 100/1,000/10,000 文件目录，本机状态检查中位数约 0.93/3.91/27.21ms；这是 JVM + 热文件缓存结果，不能当手机耗时。安装 Node/CLI 后大量文件会提高每条命令的固定成本。

来源：[命令前检查](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:63)、[目录扫描](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:101)。

**修复契约**：执行前只做 O(1) marker/状态检查；大小统计异步按需/缓存；安装/重置后失效。

## R01 [P1，静态高风险] 多入口可覆盖同一个会话的运行状态

`send()` 在拿出 handle 后不检查 busy，不把 beginRun 放在单会话锁里。deliverReminder/deliverBackgroundTurn 的 busy 检查与 beginRun 也不是原子操作。后台 scope 是 IO 多线程，不能假设只会从 UI 单线程进入。

可能交错：A、B 都看到空闲 → A beginRun/runId=A 启动 prompt → B beginRun 把 runId 改成 B → B 的 SDK prompt 报 busy → B finally 无条件 busy=false，并可能清 runId；A 仍执行，事件归属/终态已经被污染。SDK 自己拒绝 busy，并不能回滚 Host 已经改坏的状态。

来源：[send](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:211)、[后台入口](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:251)、[beginRun](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:286)。

没有做 Host + 真正慢模型流的竞态复现；按静态高风险处理。修复需用每会话 actor/队列覆盖 send/retry/stop/followUp/compact/reconfigure/close，并给所有命令 run epoch，而非再加一个 UI 防重复按钮。

## R02 [P1/P2，静态高风险] 插件重配可能关闭刚启动的会话

AppGraph 多处 `busySessionIds.first { it.isEmpty() }` 之后调用 reconfigure。等待结束和重配之间可以开始新 run；Host.reconfigure 自己没有原子地检查“无运行 + 阻止新运行”，而是全部 detach 再 attach。

来源：[AppGraph 能力变更回调](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:238)、[全量重配](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:487)。

修复：每会话在安全点更新工具快照/版本；或由同一个会话队列提交 reconfigure。避免用观察到空闲作为执行锁。未做设备复现。

## R03 [P2，静态确认] 目标续跑预订后没有再次检查目标版本

Host 在成功时先 `driver.nextRound()`，再延时 400ms。真正投递时只检查 `!handle.busy`。如果这段时间用户 pause/clear/edit goal，旧 GoalRound 仍可能发起；如果会话恰好忙，轮次已递增，但续跑请求被直接跳过。

来源：[续跑延时](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:589)。修复应携带 goalId/revision 和可取消的待运行请求，在队列内复核；轮次计数在实际开始时提交。

## R04 [P2，静态确认] 附件大小检查可被未知长度绕过

`FileAttachmentStore.import()` 只比较元数据 size>25MiB；size=-1 或上游填错时仍 `input.copyTo(output)` 到 EOF，没有边复制边限额。与 PluginDownloader 的 written 上限实现不一致。可能占用大量磁盘/IO，尚未做 Android ContentProvider 复现。

来源：[导入大小与 copyTo](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:28)。修复：流式计数并提前终止/清理；导入完成时复核接收会话，防止导入过程中切换会话后把附件加入另一个会话状态。

## R05 [P2，静态确认] SDK read 的输出限制不限制读取内存

SDK read 工具先 `fs.read { readByteArray() }` 读整文件、解码、split，再按 offset/limit 裁剪。读大型日志时，即使只要求几行，也会加载整文件。这是 SDK 层的问题，不应归罪于 Compose。

来源：[实际 ReadTool 0.2.2](evidence/sdk-0.2.2/ReadTool.kt)。建议 text 流式逐行/分块读取，图片先查大小/格式并采样；用真实大文件验收峰值分配需另行授权测试。

## R06 [P2，静态确认] 会话最近活动排序可能退化为创建时间排序

SDK SessionManager.list 内部按 mtime 排序，但 SessionInfo.timestamp 装的是 header.timestamp。SessionCatalog 又转成 updatedAt 后重新排序；header 通常是创建时间。因此继续较老会话并不保证按本轮活动时间排到最新。

来源：[SDK loadSessionInfo](evidence/sdk-0.2.2/SessionManager.kt)、[目录 updatedAt 映射](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:66)。建议把 createdAt 和 lastActivityAt 分成两个字段，建立持久索引；本轮未做真实抽屉 UI 复现。

## R07 [P2，静态风险] 长期打开的会话与任务缺少资源淘汰策略

Host.handles、AppGraph.shellEnvironments、ChatViewModel.conversationCache，以及未移除的 JobRegistry settled records 会随着使用积累。打开多个带 MCP 的会话，还会保留各自连接/SDK 状态。不能从此静态事实断言已经内存泄漏或给出手机内存数字；目前可确认的是缺少明确的空闲关闭/缓存预算/保留期限。

建议：运行会话保持活跃，空闲 SDK handle 按 LRU/数量上限关闭；UI 只缓存有限消息摘要；历史/任务详情从持久记录按需加载。按用户要求，本轮不做内存实测。

## R08 [P2，静态风险] 持久性与成功语义不够完整

- Reminder 交给 Host/排队就 markDelivered；进程在实际 prompt 前死亡时，投递状态与执行事实之间存在窗口。
- QuestionCenter 与 job 都只有内存权威，进程死亡后无法向用户说明等待问题/未知工具结果的完整状态。
- SDK JSONL 是会话权威，但目标/任务/问题不应靠聊天文本前缀反向解析成自己的唯一状态。
- 同 cwd 的多个 session 各有 FileMutationQueue，原生写/edit 不跨会话串行；bash/CLI 又可绕过该队列。当前没有跨入口的文件观察版本/提交冲突契约。

上述是可靠性设计缺口，未声称每一种窗口均已复现。应建立任务/outbox、幂等键、unknown-outcome 与恢复状态，具体见架构建议。

## 测试质量为什么不足以抵消这些问题

804 个现有测试通过是有价值的回归证据，但它们主要验证纯规则和替身边界。没有覆盖本轮发现的跨函数时区转换、停止与延迟重试、失败升级保留旧安装、损坏文件追加等组合场景。当前缺少直接驱动真实 Host 多入口的系统性生命周期测试，Android instrumentation 只有一个日历 CRUD 测试文件。

没有执行覆盖率工具，79 个单测 Kotlin 文件也不等于 79 个产品模块都有相同比例覆盖。建议把测试投资转向执行不变量/故障注入，而不是单纯增加测试数量。完整验收矩阵见 [06](06-ACCEPTANCE.md)。

