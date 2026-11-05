# plugin/host/events · 插件事件与运行时钩子

插件事件分发与运行时钩子桥：把 `PluginRegistrySnapshot.hooks` 观察型事件广播给插件，把四类变换/闸门分派成 pi SDK 运行时可用的 `CodingHooks` 与工具执行钩子。纯逻辑、无 Android UI，单测在 `app/src/test/.../plugin/host/events`。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginHookFailure.kt` | 钩子失败模型（插件 id/阶段/消息/原因）、阶段枚举、失败上报器（默认走 `Log.w`，测试可替换） |
| `PluginHookSource.kt` | `PluginHookSession`（sessionId/cwd/file）、`PluginHookSource`（按顺序取钩子快照）、`registryHookSource`、`PluginRuntimeHooksFactory`（每会话造适配器） |
| `PluginEventBus.kt` | 观察型事件总线：每次分发取一次快照、逐个插件按 5s 超时派发、异常/超时隔离并聚合失败 |
| `PluginHookDispatchers.kt` | 四类变换/闸门：提示词链式变换（fail-open）、输入变换（Handled 短路、错误 fail-open）、工具前闸门（Block 短路、错误/超时 fail-closed）、工具后结果链（错误/超时 fail-open） |
| `PluginToolResultConversion.kt` | SDK `AgentToolResult` ↔ `PluginToolResult` 边界转换（文本/Rich/图片 base64/FileRef 文本行/details/isError） |
| `PluginRuntimeHooksAdapter.kt` | 绑定单个会话，把分派器暴露为 runtime 的 `promptTransform`/`inputHandler`/`toolHooks` 与 `emit(event)` |

## 要点

- 每次分发只读一次 `PluginHookSource.entries()`，分发过程中注册表变化不影响本轮；插件顺序即注册表顺序。
- 观察型事件（22 个）异常只上报不抛出，`CancellationException` 原样上抛；超时按 `OBSERVE_TIMEOUT_MS`（5s）。
- 变换/闸门超时按 `TRANSFORM_TIMEOUT_MS`（2s）；提示词/输入/工具后失败保留上一个值（fail-open），工具前失败返回 `Block`（fail-closed）。
- 工具前 `Rewrite` 会改写后续插件看到的 args 与最终执行 args；`Block` 用插件给的 reason，插件自身异常/超时的 reason 由宿主补插件 id。
- 会话 id 由适配器写入所有请求；SDK 输入请求的图片无法映射成 plugin-api 的 imagePaths，插件改文本时保留原图片。
- p9 装配：`AppGraph` 用 `PluginRuntimeHooksFactory(registryHookSource(registry))` 按会话 `create(...)`，把三个钩子传给 `PiSessionEngine`（构造参数 `promptTransform`/`inputHandler`/`toolHooks`）。
