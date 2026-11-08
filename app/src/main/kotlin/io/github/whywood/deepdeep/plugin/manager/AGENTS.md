# plugin/manager · 插件宿主管理器

管理外部 `.ddp` 插件的运行期状态：装载、启停、贡献聚合、技能同步、外部包安装与更新、崩溃恢复。UI 与 runtime 只读管理器状态，不直接持有插件对象。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginHostManager.kt` | 编排：`start`/`refresh`、协作者装配、UI 状态发布；启停/卸载入口委托给 `PluginManagerCommands` |
| `PluginManagerDelegation.kt` | `PluginManagerFacade` 的唯一委托基类：外部包/session/slot/page/composer 查询方法 |
| `PluginManagerCommands.kt` | 用户/系统命令：enable/disable、卸载、重试激活、回滚、busy 互斥、错误状态 |
| `PluginRuntimeRegistry.kt` | 唯一状态写入点（状态闸门）：`PluginRecord`（用户意图）+ `ManagedPlugin`（运行期事实）表，串行写、发布不可变 `PluginRuntimeView`、派生 descriptor |
| `PluginHostModels.kt` | `ManagedPlugin` 运行期事实、`PluginRuntimeView` 不可变快照、`toDescriptor` |
| `PluginContributionMaterializer.kt` | 一次物化：每次 refresh 每插件每方法最多调用一次，结果写入状态闸门 |
| `PluginLifecycle.kt` | 每插件 `SupervisorJob` scope、`PluginHostFactory`、enable/disable/uninstall 收尾、技能同步 |
| `PluginActivationProtocol.kt` | 激活协议 prepare→probe→commit→swap 与失败矩阵 |
| `PluginSessionHooks.kt` | 会话 hooks 绑定（`SessionBinding`）、每 `SessionBinding` 一次 `contributionsFor`（工具与会话 hooks 共用）、per-session hook source、run 级冻结、运行期/SDK 事件派发 |
| `PluginUiProjection.kt` | UI 状态投影：只读 record+runtime+descriptor，不调用插件 |
| `PluginRules.kt` | 纯规则：更新可用 |
| `PluginRecoveryPolicy.kt` | 崩溃恢复纯策略：激活中断（marker attempts）与同版本连续启动次数 → 继续/需手动重试/自动禁用 |
| `PluginManagerFacade.kt` | 对 UI/装配层暴露的接口（状态流、启停/卸载、导入/更新、槽位、页面、composer 动作、恢复） |
| `PluginUiModels.kt` | UI 状态模型：快照（含 pages）/相位/可用性/恢复结果 |
| `PluginExternalUiModels.kt` | 外部包 UI 模型：导入检查/安装结果/更新候选 |
| `PluginExternalFlow.kt` | 外部 `.ddp` 流程：记录注册/启动激活（支持 skip 集合供崩溃守卫跳过）、导入校验/首装、启停装载、更新检查与暂存、Staged 同步 pending\*、错误落盘 |
| `PluginExternalActivation.kt` | 外部 pending 版本的激活协议接线：prepare/probe（onUpgrade+contributions/skills/ui）/commit/swap/discard/abort；marker begin/complete 与失败时旧版本保持可用的 lastError 语义 |
| `ExternalPluginRuntime.kt` | 外部 `.ddp` 的装载/句柄/提交/卸载包装，以及恢复原语（activation marker/startup 计数/previous 回滚/版本目录清理） |

## 要点

- 单一事实源：用户意图（enabled/installedVersion/pending\*/previous\*/lastError）在 `PluginRecord`；运行期事实（实例/host/scope/会话绑定/物化贡献/failed/degraded）在 `ManagedPlugin`；相位由 `PluginPhase.derive(record, facts)` 纯函数派生，只在写 `PluginRegistry` descriptor 时算一次；UI 的 `PENDING_RESTART` 由 `record.pendingVersion` 派生，不另存。
- 状态变更只经 `PluginRuntimeRegistry`（写串行、读返回不可变视图）；写盘（外部记录 v4）由流程负责：先写盘，再过闸门。
- 一次物化：每次 refresh 每插件调用 `availability()`/`skills()` 各 1 次，enabled 时 `contributions()`/`ui()`/`promptSections()` 各 1 次；工具装配、`promptBody`、技能同步、槽位、冲突诊断、UI 快照只读物化结果，消费路径不再调用插件。
- 生命周期：每插件独立 `SupervisorJob` scope；disable/uninstall/close 统一 cancel scope → `close()`（5s 超时）→ 释放安装句柄 → 移除注册或清贡献 → 同步技能（会话钩子在当前 run 内按冻结集合继续派发，run 结束后的刷新快照生效）；单插件 `Throwable` 隔离（`CancellationException` 重抛）。禁用但未装载的插件为 `DISABLED` 而非 `FAILED`。
- 会话：`bindSessionHooks` 接 `SessionBinding`，每会话一个 `PluginRuntimeHooksAdapter`；同一 `SessionBinding` 实例只调用一次 `contributionsFor` 并缓存（身份相等、弱引用、按 binding 分锁），工具与会话 hooks 共用这次物化，不同会话的物化互不阻塞；会话贡献返回非空 hooks 时按 pluginId 覆盖该插件的全局 hooks，默认空实现（`PluginHooks.None`）沿用全局 hooks；run 开始冻结会话钩子集合、`RunFinished`/`RunFailed` 派发完成后解冻并刷成最新快照（冻结期间启停插件不影响本轮，解冻后按最新快照生效）。`close()` 幂等：关闭中的会话不再接收新事件，排空后移除 adapter 与贡献缓存。
- 外部安装/更新只做「校验 + 暂存」，`PENDING_RESTART` 由 `record.pendingVersion` 表达，下次启动由 `PluginActivationProtocol` 走 prepare/probe/commit/swap；失败按矩阵回滚（旧版本保持可用）。安装后不再调用插件 `install()` 钩子，插件自身的 CLI 下载/登录由插件页面或工具完成。
- `start()` 重复调用不会重复注册/启用插件，但仍会激活待生效的 pending 版本：更新只经启动安全点生效，进程内重试与真实重启走同一条路径。
- 崩溃恢复：启动时对启用中的外部插件读 activation marker；中断→跳过自动激活并记 `lastError`（`attempts>=2` 自动禁用）；未中断且非 pending → `noteStartup`，同版本连续 2 次启动崩溃自动 `enabled=false`；激活/启用成功后清计数。facade 提供 `retryActivation(pluginId)` 与 `rollbackPlugin(pluginId)`（回滚 previous 版本）；用户手动启用（`PluginManagerCommands.enablePlugin`）先清 startup 计数/marker 再装载，保证自动禁用后可手动恢复。

## 会话级排除

`PluginManagerFacade` / `PluginManagerDelegation` 的 `tools`、`deferredTools`、`promptBody`、`bindSessionHooks` 接受 `excludedPlugins: Set<String>`；`PluginSessionHooks` 按 pluginId 过滤 contributions 与 hooks，`PluginHostManager.promptBody` 过滤对应 promptSections。用于工作区关闭继承插件，未传时行为不变。
