# plugin/host/events · 插件事件与运行时钩子

插件事件分发与运行时钩子桥：把 `PluginRegistrySnapshot.hooks` 观察型事件广播给插件，把四类变换/闸门分派成 pi SDK 运行时可用的 `CodingHooks` 与工具执行钩子。纯逻辑、无 Android UI，单测在 `app/src/test/.../plugin/host/events`。

## 文件

| 文件 | 职责 |
|---|---|
| `HostPluginEvents.kt` | 21 个观察事件的宿主实现（`data class` 实现 plugin-api 只读事件接口，附 `HostPluginBusEvent`） |
| `PluginHookFailure.kt` | 钩子失败模型（插件 id/阶段/消息/原因）、阶段枚举、失败上报器（默认走 `Log.w`，测试可替换） |
| `PluginHookSource.kt` | `PluginHookSession`（sessionId/cwd/file）、`PluginHookSource`（按顺序取钩子快照）、`HookSnapshot`、`RuntimeHookSource`（registry 物化快照）、`SessionHookSource`（会话源：registry 快照按 pluginId 覆盖会话贡献 hooks，run 级冻结）、`PluginRuntimeHooksFactory`（按源造适配器） |
| `PluginEventBus.kt` | 观察型事件总线：每次分发取一次快照、逐个插件按 5s 超时派发、异常/超时隔离并聚合失败；事件用 `is XxxEvent` 分派 |
| `PluginHookDispatchers.kt` | 四类变换/闸门：提示词链式变换（fail-open）、输入变换（Handled 短路、错误 fail-open）、工具前闸门（Block 短路、错误/超时 fail-closed）、工具后结果链（错误/超时 fail-open） |
| `PluginToolResultConversion.kt` | SDK `AgentToolResult` ↔ `PluginToolResult` 边界转换（文本/Rich/图片 base64/FileRef 文本回退与 details 保留键写入/details/isError） |
| `RuntimeEventMapper.kt` | runtime `RuntimeEvent`/pi SDK `SessionEvent`（含 `AgentEvent`）→ `PluginEvent` 映射与分发；Turn/Message/Tool/ThinkingLevel 全部有来源，runtime 路径不再重复映射工具与 delta |
| `SessionEventPump.kt` | 每会话单泵：structural/delta 双通道（结构优先、delta 可丢）、空 hooks 快路径、close 排空后停止 |
| `PluginHooksDelegate.kt` | `RuntimeHooks` 与当前会话适配器之间的稳定转发层：`AppGraph` 每份 `RuntimeHooks` 一个 delegate，会话绑定时替换 target，未绑定时全部 no-op |
| `PluginRuntimeHooksAdapter.kt` | 绑定单个会话，把分派器暴露为 runtime 的 `promptTransform`/`inputHandler`/`toolHooks` 与 `emit(event)`；输入钩子做 `ImagePart.path` ↔ plugin-api `imagePaths` 双向转换（返回的新路径读文件回填，不可读忽略） |

## 要点

- 每次分发只读一次 `PluginHookSource.entries()`，分发过程中注册表变化不影响本轮；插件顺序即注册表顺序。
- 会话源：基础 entries 取 registry 快照（已按相位过滤），会话贡献返回非空 hooks 时按 pluginId 覆盖该插件的全局 hooks；会话贡献为默认空实现（`PluginHooks.None`）时不覆盖、沿用全局 hooks（默认 `contributionsFor` 委托 `contributions` 时仍是每插件一条，不重复派发）；run 开始时冻结当前快照，run 期间刷新不影响本轮（已冻结集合里的插件即使被停用也继续收到本轮剩余事件），RunFinished/RunFailed 派发完成后解冻并换用最新快照；冻结集合为空时事件泵快速丢弃，由 run 结束的兜底路径解冻。
- 观察型事件（21 个）异常只上报不抛出，`CancellationException` 原样上抛；超时按 `OBSERVE_TIMEOUT_MS`（5s）。
- 变换/闸门超时按 `TRANSFORM_TIMEOUT_MS`（2s）；提示词/输入/工具后失败保留上一个值（fail-open），工具前失败返回 `Block`（fail-closed）。
- 工具前 `Rewrite` 会改写后续插件看到的 args 与最终执行 args；`Block` 用插件给的 reason，插件自身异常/超时的 reason 由宿主补插件 id。
- 会话 id 由适配器写入所有请求；输入钩子的图片以 `ImagePart.path` 作为 plugin-api `imagePaths` 双向转换，插件返回的路径按扩展名判定图片并读文件回填，空列表按契约清空图片。
- 事件映射单一来源：runtime 路径只映射 Run 生命周期与注入/用户消息，Turn/Message/Tool/ThinkingLevel 一律来自 SDK `AgentEventReceived`；compaction/retry 只来自 SDK `SessionEvent`，避免重复派发。
- 事件由 `SessionEventPump` 每会话串行派发；`SessionStarted` 在 `updateSnapshot` 之后入队，`SessionClosed` 入队后再 `close`，确保不越序。
- 装配：`AppGraph` 为每个 `RuntimeHooks` 建一个 `PluginHooksDelegate` 传给 `PiSessionEngine`；会话建立时 `PluginHostManager.bindSessionHooks` 收到该会话的 `SessionBinding`，用会话贡献 + registry 快照造一个 `SessionHookSource`，再由 `PluginRuntimeHooksFactory` 造适配器并 `delegate.bind(adapter)`；小程序用 `hooksFactory` 每 run 新建 delegate，因此两个入口共用同一条管线。
