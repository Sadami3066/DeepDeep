# DeepDeep 代码评审报告 · 2026-10-30

对象：`/home/whywood/project/DeepDeep`（Android 个人 Agent App）
规模：主代码 **50,916 行**（`:app` 37,248 / `:runtime` 10,772 / `:shell` 956 / `:design` 1,940），测试 20,087 行；251 个 Kotlin 主文件。
范围：代码质量、架构质量、性能与效率三维度；工程化/构建/测试作为支撑维度。按项目设计理念，"无权限审批"不作为评审项。

## 这份清单怎么用

`findings.tsv` 是唯一事实源：128 条 × 11 列，最后一列 `status` 是状态。当前 **123 open / 5 fixed**。

> 这是 2026-10-30 的结构化**评审快照**，不是现行 backlog；现行任务清单见 `docs/dev/refactor/tasks.tsv`。5 条 fixed 的修复提交：RT-001、GL-009 → `9b6a07b`；PL-001、RT-014 → `5cd66d4`；RT-004 → `0d36d17`。其余 123 条 open 均未进入 M0–M3（M0–M3 只做结构/契约/文档，不含行为修复）；M4 为 deferred（不执行）、M5 仅做文档校准，因此这些 open 条目不排期，作为 M4 决策后的修复候选池（优先级见下方 Top 15 与 `REVIEW_OF_REVIEW.md` §4）。

| status | 含义 |
|---|---|
| `open` | 未修复 |
| `fixed <短 sha>` | 已修复，sha 为实际修复提交 |
| `wontfix` | 明确不修（须在 `REVIEW_OF_REVIEW.md` 或 issue 里写理由） |
| `duplicate` | 重复条目（指向保留条目） |

```bash
# 状态分布（验收命令）
awk -F'\t' 'NR>1{print $NF}' docs/dev/review/10-30/findings.tsv | sort | uniq -c
# 未修条目按严重度排期
awk -F'\t' 'NR>1 && $NF=="open" && $2 ~ /^P[01]$/' docs/dev/review/10-30/findings.tsv | cut -f1,2,7
# 已修条目与修复提交
awk -F'\t' 'NR>1 && $NF ~ /^fixed/{print $1, $NF}' docs/dev/review/10-30/findings.tsv
```

首批 4 条在 M0 阶段修掉（基线 `f1e7d76` 之后），提交信息零引用 ID，本次按 `git log` + `git show` 回代码逐条回填：`RT-001`、`GL-009` → `9b6a07b`；`PL-001`、`RT-014` → `5cd66d4`（`9b6a07b` 首修，`5cd66d4` 补齐备份无损、损坏期间拒绝写入、备份失败暂停写入）。

**对账规则**：M1 起每个修复提交标题必须带 backlog ID（如 `fix: … [RT-001]`）；条目关闭时在同一 PR 内把该行 `status` 改为 `fixed <sha>`（合并后回填最终 sha），不再靠人工考古。

## 评审方法

- 6 路并行深读：runtime、聊天 UI、App 核心、功能页面+设计系统、插件+shell、工程化扫描，各自通读范围内全部文件，问题条目均带 `file:line` 证据。
- Lead 交叉核验：对 20 条 P0/P1 逐条回代码验证（含 `AppGraph` 初始化、`ScheduleService` 事件循环、`plugins.json` 覆盖路径、`PiSessionEngine.createSession` 线程、`ChatUiState` 双模型等），并对全文数值与引用做一致性检查（`findings.tsv` 的 128 条 file/line 基本可解析、ID 与六份模块报告一一对应）。
- 2026-10-30 追加「回顾之回顾」：6 路回代码逐条复核 128 条，结论与修正见 `REVIEW_OF_REVIEW.md`；本文与各模块报告的待修正处已在行内加 `〔复核…〕` 批注。
- 机器可验证证据：`./gradlew :app:compileDebugKotlin`（通过）、四模块 `--rerun-tasks` 全量编译（13 条警告）、`:app:testDebugUnitTest :runtime:test :shell:test`（1,022 用例 / 0 失败）、`:app:lintDebug`（0 errors / 160 warnings / 5 hints）、`--rerun-tasks` 生成完整 Compose 编译器报告（224 个 Composable / 61 个 unstable class）、release APK 解包统计。
- 未做的事：未构造并发实况（事件乱序、迁移竞态等窗口类问题按代码路径定性，部分标注"推测"）；未在真机上复测性能（项目自身 `docs/archive/STATUS.md` 与 `artifacts/validation/` 的实测数据被引用为基线）。

## 总体结论

**综合 6.9 / 10**（代码质量/架构/性能三维度均值；工程化作为支撑维度未计入综合分）。 这是一个明显高于个人项目平均水平的代码库：模块边界有自动化校验、运行状态有单一所有者（命令面 + epoch）、持久化与进程死亡恢复成体系、UI 性能有机制化设计、1,022 个单测质量扎实，且很少见的"验证文化"（47 个真机验收证据目录，round6–round42 等）。它当前的问题是**结构性集中**而非基础失守：复杂度堆在 6 个 800+ 行文件里，重复（store 样板、页面骨架、双状态模型）没有被抽象收口，质量门禁（CI/lint/边界校验）全部缺位，另有一个触发概率低但后果严重的启动崩溃点（数据文件损坏）。

这些问题几乎都可以渐进修复，不需要推倒重来。

> **复核批注（2026-10-30，回顾之回顾）**：本文已逐条回代码核验（HEAD f1e7d76），行内标注 `〔复核修正〕`=事实/数字错误、`〔复核降级〕`=严重度或影响夸大、`〔复核勿做〕`=照做会退化或不可行。汇总、误报清单与建议执行顺序见 `REVIEW_OF_REVIEW.md`。


### 总评分

| 维度 | 分数 | 一句话依据 | 详见 |
|---|---|---|---|
| 架构质量 | **7.0** | 设计骨架 8.0（模块边界、状态所有权、事件/真相双源、恢复语义）；落地 6.5（UI 无状态层、AppGraph 隐式装配、壳层集散） | `architecture.md`、`module-*.md` |
| 代码质量 | **6.9** | 协程/错误处理纪律好、测试质量高；复杂度过分集中、重复未收口、静默失败 5 处 | `code-quality.md` |
| 性能与效率 | **6.8** | 流式/Markdown/字体/转场/后台任务机制化做得好（机制评分 7.4）；各模块实测 6.0–6.5；根重组、长会话、包体与启动仍有结构性问题 | `performance.md` |
| 工程化与测试 | **5.5** | 构建配置正确、单测 1,022 例；但无 CI/无门禁/无静态检查/关键集成层零测试/构建不可脱离本机 | `build-tests.md` |
| 安全与稳健性 | **7.0** | 无注入/路径穿越/凭据明文；工坊边界扎实；扣分在 3 处 WebView 渲染进程崩溃未处理、1 个启动崩溃点 | `module-plugins-shell.md` |

> 复核：评分口径不一致——`build-tests.md` 四维为 6/7/5/3，均分 5.25 而非 5.5；全篇 P1 定级整体偏重约一档（依据见 `REVIEW_OF_REVIEW.md` §3）。

## 亮点（值得保留的设计）

1. **命令面单一所有权**：每会话锁 + 单调 epoch + `runId` + `terminalEmitted` + reconfigure token，停止/重试/目标续跑都靠 epoch 失效而非"读 busy 再判断"（`SessionCommandPlane.kt`）。
2. **事件是快速通道而非唯一真相**：宿主维护 `activeRuns` StateFlow，UI 在 busy→idle、回前台、打开会话时用 history 对账（`AgentRuntimeHost.kt`）。
3. **四类"中途死亡"都有恢复路径**：RunLedger（运行中断）、QuestionPersistence（待答问题）、delivery-intents（提醒至少一次投递）、JobPersistence（后台任务中断）——对随时可能被 MIUI 杀掉的 App 是刚需。
4. **持久化纪律统一**：原子写（tmp+rename）、坏文件 `.corrupt`、schemaVersion、Clock 注入；工坊另有 SQLite 数据分离。
5. **流式渲染的相位决策正确**：16ms delta 合批、Markdown 增量规划 + 块级缓存、`live` 时跳过高亮/表格测宽、动画值在 draw/layout 相位读、字体 Async 加载 + 启动预热。
6. **工坊是显式安全边界**：独立 origin + PathHandler + document-start bridge + main-frame/origin 双重校验 + 页面 Agent 工具白名单 + `ScopedFileSystem`。
7. **插件契约干净**：框架不认识 Linux/CLI/登录；能力变化走 `reconfigure()` 安全点（忙碌会话排到本轮结束）；技能/工具/提示按启用状态注入。
8. **路径与压缩防护系统性**：技能 zip、rootfs tar、插件技能写入、页面文件工具四处独立实现都拒绝同类攻击面。
9. **测试写行为不写行数**：fake runtime + `StandardTestDispatcher` + `MutableTestClock`；Markdown 逐字符前缀等价断言；`SessionCommandPlane` 真线程并发测试。
10. **验证文化**：47 个 `artifacts/validation/` 真机证据目录（round6–round42 等，904 个文件） + `docs/archive/STATUS.md` 记录"已验证/未验证"，使评审能区分声称与实测。

## 问题分布

共 **128 条**（P0 1 / P1 19 / P2 65 / P3 43），全部在 `findings.tsv`，按模块拆：

| 模块文档 | ID 前缀 | P0 | P1 | P2 | P3 | 合计 |
|---|---|---|---|---|---|---|
| `module-runtime.md` | RT- | 1 | 5 | 16 | 10 | 32 |
| `module-chat-ui.md` | CH- | 0 | 4 | 7 | 6 | 17 |
| `module-app-core.md` | AC- | 0 | 1 | 11 | 4 | 16 |
| `module-app-screens.md` | SC- | 0 | 3 | 12 | 5 | 20 |
| `module-plugins-shell.md` | PL- | 0 | 2 | 10 | 8 | 20 |
| `build-tests.md` | GL- | 0 | 4 | 9 | 10 | 23 |

按类别：性能 37、正确性 21、可维护性 14、架构 9、并发 8、代码质量 8、工程化 6、测试 6、构建 6、资源 5、安全 4、可访问性 4。工作量分布：S 83 / M 40 / L 5 —— **65% 的问题一天内可修**。

> 复核：128 条中**无整条误报**，但 4 条含错误子结论（P-01 baseline profile、RT-010 死锁、SC-017 waiting、PL-002 路径二），1 条 P0 触发概率被抬高（RT-001），1 条建议照做会丢数据（GL-006 换签名）。S/M/L 对「宿主拆锁」与「壳层拆分」两处偏低一档。

## Top 15 优先修复

| # | 问题 | ID | 模块 | 成本 | 为什么现在做 |
|---|---|---|---|---|---|
| 1 | 损坏的 `goals.json`/`schedules.json` 以未捕获异常击穿启动（`GoalService` 构造器同步读盘） | RT-001（P0） | runtime | S | 唯一 P0：持久化成损坏 → 每次启动崩溃循环，且 UI 无法修复 |
| 2 | 损坏的 `plugins.json` 被启动流程静默覆盖，外部插件安装记录丢失 | PL-001（P1） | plugins | S | 静默数据丢失，与文档承诺相反 |
| 3 | 存储迁移 `scope.launch` 发射后不管，与首会话恢复并发 | AC-001（P1） | app-core | M | 升级首启"历史看起来没了"并新建空会话；`writeText` 重写有截断窗口 |
| 4 | 安装取消语义坏：取消时回滚被取消吞掉 / UI 取消但安装继续 | PL-002（P1） | plugins | M | 用户以为取消，实际可能装好或留下半安装 |
| 5 | 宿主 suspend API 无 IO 边界：重连时在 Main 线程解析完整 JSONL | RT-002（P1） | runtime | M | 长会话切换/重连卡主线程 |
| 6 | `ScheduleService` 对**每个**运行时事件（含 TextDelta）都读+解析 `schedules.json` | RT-004（P1） | runtime | S | 流式期间 ~60 次/秒磁盘 IO，机制最确定、修复最小 |
| 7 | 全局 mutex 粒度过粗（建/关会话、history、usage 都在锁内） | RT-003（P1） | runtime | M | 并发会话互相阻塞 |
| 8 | 事件有序性无保证（缓冲溢出延后 emit、stop 终态可能早于 RunStarted） | RT-005（P1） | runtime | M | 状态机正确性风险窗口 |
| 9 | `AgentRuntimeHost` 1,083 行零直接单测 | RT-006（P1） | runtime | M | 最贵的并发/回收/重连逻辑没有安全网 |
| 10 | 流式终态单帧同步全量高亮 + 表格逐 cell 文本测量 | CH-001（P1） | chat | M | 长代码/表格回复结束瞬间掉帧 |
| 11 | 16ms 批次驱动 `ChatScreen`（54 参数）整页重组 + LazyColumn DSL O(N) 重建 | CH-002（P1） | chat | M | 长会话流式的主线程预算被吃 |
| 12 | 长 assistant 消息无块级懒加载，整条消息所有 Markdown 块一次组合/测量 | CH-003（P1） | chat | M | 长回复滚动/打开卡顿 |
| 13 | `ChatViewModel` 1,639 行/12 类职责 + `ChatUiState`/`ConversationState` 双模型 | CH-004（P1） | chat | L | 单文件应用，测试与演进的根障碍 |
| 14 | 页面无 state holder + 17 份页面骨架复制 + UI 错误静默 | SC-001/002/003（P1） | screens | L/M | "抽一个组件全 App 生效"与"错误被吞成空数据"都在这 |
| 15 | 质量门禁全缺：无 CI、lint 无门禁、边界校验未接线、构建依赖 mavenLocal | GL-001/002/005/004（P1） | 工程化 | S/M | 前 14 项之后，"下一次"靠它防复发 |

其余高价值 P2：`GL-009` 3 处 WebViewClient 缺 `onRenderProcessGone`（渲染进程 OOM 白屏，S）、`RT-014` schedule v1 数据被静默清空（S）、`CH-009` 新消息无条件把上翻阅读的用户拉回底部（S）、`SC-008` 表单草稿旋转丢失（M）、`PL-003` 启动串行跑 5 个 PRoot 探测 + 1 次 HTTPS（M）。

## 文档索引

```
docs/dev/review/10-30/
├── README.md              本文件：总评、评分、问题分布、Top 15、索引
├── architecture.md        跨模块架构评审（A-01…A-10）：模块边界、分层、状态所有权、并发、持久化、可测试性
├── code-quality.md        跨模块代码质量评审（C-01…C-08）：复杂度、重复、静默失败、死代码、测试质量、静态检查
├── performance.md         跨模块性能评审（P-01…P-12）：启动、重组、流式、长会话、内存、IO、包体
├── module-runtime.md      :runtime 深评（RT-001…RT-032）
├── module-chat-ui.md      聊天 UI 与 Markdown 深评（CH-001…CH-017）
├── module-app-core.md     App 核心：装配/生命周期/导航/数据/服务（AC-001…AC-016）
├── module-app-screens.md  功能页面 + design 设计系统（SC-001…SC-020）
├── module-plugins-shell.md 插件体系 + :shell（PL-001…PL-020）
├── build-tests.md         工程化/构建/测试/仓库卫生 + 文档漂移抽查（GL-001…GL-023）
├── findings.tsv           全量 128 条问题清单（机器可读，已按复核修正若干数字）
└── REVIEW_OF_REVIEW.md    回顾之回顾：逐条判定、误报/夸大/有害建议清单、数字矛盾与执行顺序
```

**编号规则**：`A-xx` 架构、`P-xx` 性能（跨模块；与模块内条目有重叠时会互相引用）、`C-xx` 代码质量；`RT/CH/AC/SC/PL/GL-xxx` 是模块内条目，与 `findings.tsv` 的 `id` 一一对应。

**`findings.tsv` 字段**：`id / severity / category / module / file / line / title / evidence / recommendation / effort / status`（UTF-8、Tab 分隔、首行为表头；`status` 见「这份清单怎么用」）。复核后已修正其中若干数字与路径（CH-002 字段数、RT-022 成员数、GL-008 的 file 列等），确定性分级见 `REVIEW_OF_REVIEW.md`。

## 基线数据（本次实测，可复现）

```bash
./gradlew :app:compileDebugKotlin                        # 通过
./gradlew :app:compileDebugKotlin :runtime:compileDebugKotlin \
          :shell:compileDebugKotlin :design:compileDebugKotlin --rerun-tasks   # 13 条警告
./gradlew :app:testDebugUnitTest :runtime:test :shell:test                   # 1022 用例 / 0 失败
./gradlew :app:lintDebug                                  # 0 errors / 160 warnings / 5 hints
```

| 项 | 数值 |
|---|---|
| release APK | 40,547,437 B（40.5 MB），dex 3 个合计 30.3 MiB（未压缩存放），res 5.4 MB，assets 4.2 MB |
| debug APK | 62.7 MB |
| 应用级 baseline profile | 无（仅依赖 AAR 合并的 9.7 KB profile） |
| 单测 / instrumented 测试 | 1,022 / 2 个文件 |
| 最长文件 | ChatViewModel 1,639、MarkdownParser 1,193、DeepDeepApp 1,159、AgentRuntimeHost 1,083、MiCloudTools 1,008、DeepDeepDrawer 804 |
| Compose 编译报告 | 224 个 Composable，26 个不可跳过（均为 `remember*`/`*Label` 辅助函数），61 个 unstable class |
| 项目自身基线（引用） | release 冷启动 637/638/674/741ms（`docs/archive/STATUS.md` round41），页面循环后 PSS 251MB |

## 局限

- 性能问题多数是"机制级"结论（代码相位、锁、IO 频率），除项目已有数据外未做真机 profiling；`CH-001` 的"单帧几十 ms"是按工作量推演，未用 Macrobenchmark 实测。

> 复核：性能类结论全部属于机制推理或纯估算，**不能作为验收依据**；项目自身 round41 实测流式 4.17% janky、覆盖层 10s 帧数 1178→10，与本文若干「卡顿主因」判断不一致。
- 并发类问题（`RT-001` 之外的窗口、`AC-001`、`RT-005`）是代码路径推导，未构造实况复现；报告中已逐条标注。
- 可访问性问题来自静态审查（无 TalkBack 实测）；安全审查限于代码层，未做动态测试/流量分析。
- 未评审 `../pi2kotlin`（外部 SDK 依赖）内部实现，`:runtime` 相关问题只到适配层。
