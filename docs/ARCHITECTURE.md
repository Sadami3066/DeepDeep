# 架构

三张表回答三个问题：**模块怎么依赖、数据谁是权威、启动按什么顺序**。实现细节在 `docs/design-notes.md`（历史归档，可能漂移）；为什么这么设计在 `docs/adr/`。

## 1. 模块与依赖

```
:app        Compose 界面、导航、前台服务、装配
 ├─ :runtime    pi2kotlin 适配、事件、会话元数据、凭据、MCP、调度/目标/任务/工坊
 │   └─ :runtime-api  纯 JVM 契约（事件/通知/用量/目标/任务/插件/模型配置/提问类型），无 Android 与 SDK 依赖
 ├─ :shell      Alpine/proot 与 ShellExecutor
 └─ :design     主题、字体、图标、motion tokens、共享组件
```

| 模块 | 职责 | 允许依赖 |
|---|---|---|
| `:app` | UI、导航、服务、装配（AppGraph） | `:runtime`、`:shell`、`:design` |
| `:runtime` | agent 运行时适配与 App 级领域服务 | `:runtime-api`、Android SDK、`dev.pi` SDK |
| `:runtime-api` | 纯 JVM 契约：事件、通知、用量、目标/任务/插件契约、模型配置、提问类型 | 仅 coroutines/stdlib（编译器强制无 Android/SDK） |
| `:shell` | proot 用户空间执行 | 无业务模块 |
| `:design` | 纯 UI 基础件 | Compose |

依赖方向由根项目 `verifyModuleBoundaries` 强制，`./gradlew check` 会执行；新增模块必须登记进边界表，否则构建失败。包级约束由 `verifyPackageBoundaries` 强制（feature 之间禁止互相 import；`ui.navigation`/`ui.shell` 为 composition layer 例外；`ui` 不得 import `AppGraph`）。

`:app` 内分层：`core`（装配）→ `data`（附件/技能/偏好）→ `plugin` → `ui`；`service`/`voice`/`deeplink` 为平台入口。`:runtime` 内按领域分包（session/schedule/goal/job/question/workshop/mcp/provider）。

已解决的边界债（M2/M3）：壳层改为 `AppDependencies` 接口；`:runtime` 契约抽成纯 JVM 模块；Skills/MCP/Plugins/Schedule/Workshop 五屏已用 state holder；`DeepDeepApp` 1213 → ~640 行（路由/overlay/抽屉/intake/命令已抽出）；`ChatViewModel` 1660 → 807 行（7 步拆分中第 1–6 步完成）。

仍存在：部分页面（设置/Provider/文件/会话等）直连 Store；`DeepDeepApp` 仍持有 ViewModel 工厂与 19 个 `collectAsState`；`:app` 未按 feature 拆模块（有意为之，见 ADR-0004 与重构路线）。

## 2. 数据所有权（唯一真相源）

| 数据 | 权威位置 | 写入方 | 恢复/一致性 |
|---|---|---|---|
| 会话消息 | pi JSONL（SDK 管理） | SDK + 宿主 | 坏尾行先备份 `.jsonl.corrupt` 再截断；JSONL 是唯一权威 |
| 会话列表 | `files/session-index.json`（索引，可重建） | `SessionIndex` | size+mtime 缓存，损坏自动重建 |
| 置顶/归档/标题覆盖 | DataStore `session_meta` | `SessionMetaStore` | 与会话文件分离 |
| 待答问题 | `files/questions/pending.json` | `QuestionCenter` | 进程被杀后转「提问中断」提示 |
| 运行台账 | `files/tasks/runs.json` | `RunLedger` | 残留记录启动时转「运行被中断」 |
| 提醒 | `files/schedules/schedules.json` + `delivery-intents.json` | `ScheduleStore` / `ScheduleService` | intent 去重键 `scheduleId@nextRunAt`，启动补投一次 |
| 目标 / 后台任务 | **仅进程内存** | `GoalService` / `JobRegistry` | 进程重启即清空（有意取舍） |
| 插件记录 / 设置 | `files/plugins/{plugins,settings}.json` | `PluginManager` | 损坏先备份 `.corrupt` 再重建 |
| 技能仓库 / 生效副本 | `files/skills/<id>` / `files/agent/skills/<id>` | `SkillRepository` | 复制而非软链；`.deepdeep-plugin` 标记归属 |
| 创意页面 / 数据 | `files/workshop/apps/<id>/` + `userdata/<id>.db` | Agent + `WorkshopStore` | 代码与数据分离，删创意同时删 DB |
| 凭据（API Key/OAuth/Cookie） | DataStore `secrets`（Keystore AES-GCM） | `SdkCredentialStore` | 按 provider 串行落盘，写一种清另一种 |
| 模型默认 / 个性化 / 偏好 | DataStore 各 store | 各 Store | — |

统一纪律：原子写（tmp + rename）、坏文件先备份 `.corrupt` 再重建、`schemaVersion` 字段、时间通过注入的 `Clock`（便于测试）。任何新增持久化都必须沿用，不允许直接 `writeText` 覆盖。

## 3. 启动顺序（现状）

| # | 位置 | 做什么 |
|---|---|---|
| 1 | `DeepDeepApplication.onCreate` | 构造 `AppGraph`（DataStore 实例化、若干 `init` 块） |
| 2 | `AppGraph.prepare()` | 存储迁移 → `runtime.start()` → 凭据预热（异步，不保证在会话恢复前完成） |
| 3 | `AgentService.onCreate` | 通知中心、`ScheduleService.start()`（恢复待投递提醒） |
| 4 | `ChatViewModel.init` | `prepare()` → 恢复初始会话 |
| 5 | `DeepDeepApp` | ready 后渲染；处理待消费的分享/深链 |

已知问题：顺序靠源码与协程时序隐式保证（AC-002）、存储迁移与首个会话恢复并发（AC-001）。目标形态是显式 `bootstrap()` 阶段串行，属于 M2 任务，改之前不要依赖当前时序。

## 4. 关键不变量（重构时不得破坏）

1. **会话运行状态只有一个所有者**：`SessionCommandPlane` 用每会话锁 + 单调 epoch 管理 busy/stop/retry；`busy` 只能由持有该 run 的路径清除。
2. **事件是快速通道，不是真相**：`activeRuns: StateFlow` 为准；UI 在 busy→idle、回前台、打开会话、无 run 的 stop、history 落地后五处对账。
3. **迟到事件按 runId 丢弃**；结构事件不得被 delta 洪峰挤出顺序。
4. **插件能力变化只经 `host.reconfigure()`**：忙碌会话排到本轮结束的安全点，不提前关闭。
5. **工坊是安全边界**：独立 origin + PathHandler + document-start bridge + 主框架/origin 校验 + 工具白名单 + `ScopedFileSystem`。
6. **会话 JSONL 是唯一权威**；本地元数据（置顶/归档/标题）不得反向改写会话正文。
7. **本地分发用 debug 证书**（ADR-0001）；换签名等于强制卸载，先读 ADR。

## 5. 下一步

重构顺序、任务卡与验收标准见 `docs/refactor/README.md` 与 `docs/refactor/tasks.tsv`。本文只描述"现在是什么"，不描述"将要变成什么"。
