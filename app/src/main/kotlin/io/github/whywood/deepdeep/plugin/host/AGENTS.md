# plugin/host · 插件宿主

插件系统的宿主侧：外部 `.ddp` 的发现与 DEX 装载、签名与版本校验、注册表与贡献快照、生命周期与失败隔离。插件实现只依赖 `:plugin-api` 契约；宿主可以依赖 `:app` 内部能力，但不得把内部类型泄漏进契约。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginPaths.kt` | 规范目录：`files/plugins` → `<id>` → `<version>/{bundle,assets,lib}`，插件运行数据固定在 `<id>/data` |
| `PluginRecord.kt` | 用户意图记录 `PluginRecord`（enabled/installedVersion/pending\*/previous\*/lastError）与运行期事实 `PluginRuntimeFacts`；相位的唯一派生函数 `PluginPhase.derive(record, facts)` |
| `PluginRegistry.kt` | 内存插件表：`PluginDescriptor`（manifest + 派生相位 + error + 物化贡献）、线程安全 `PluginRegistrySnapshot`（递增 version、tools/skills/promptSections/ui/hooks/bindings/pages/composerActions 聚合、重复 id 拒绝、跨插件冲突记录） |
| `PluginHostImpl.kt` | `PluginHost` 实现：服务访问器委托给 `PluginServices` 持有的实现，未提供抛 `PluginServiceUnavailable`；保留旧签名 `text` |
| `PluginTextResolver.kt` | `LocalizedText` 解析：values 按语言标签取、缺省回退 `default`；`String.format` 参数（插件 API 已无资源 id） |
| `PluginToolAdapter.kt` | `plugin-api` 的 `PluginTool` 接口 → pi SDK `CodingTool`：元数据直传，`execute(call)` 经宿主 `PluginToolCall` 实现传入，sessionId 在 execute 时解析，Text/Rich 结果（`text/blocks`）映射 `TextContent`/`ImageContent`/FileRef 文本回退 + details 保留键，`partial` 桥接为 `AgentToolUpdateCallback`，异常转 isError |
| `external/` | 外部 `.ddp` 管道：包读取、ddp-1 签名校验、TOFU 信任库、记录 v4、安装器、DEX 装载（见目录内 AGENTS.md） |
| `events/` | 插件事件总线、变换/闸门派发、runtime hooks 适配（见目录内 AGENTS.md） |
| `ui/` | 插件 UI 槽位：Slot 注册与解析、Compose 槽宿主 |
| `services/` | 基础宿主服务适配器：`PluginServiceDependencies` + `buildPluginServices`（Linux/屏幕/通知/加密存储/网络/文件/会话/job/timer/goal/小程序/MCP/UI/AppInfo/总线/日志/聊天） |
| `extensions/` | plugin-api 1.1.0 扩展能力适配器：`device/`（设备/剪贴板/权限/包管理/Root·ADB/Intent/代理组件/后台任务）与 `data/`（设置存储/定位/联系人/短信/日历/媒体/语音/网页/凭据/无障碍），各自持有 `build*PluginServices` 工厂与 `AGENTS.md` |
| `interaction/` | 插件交互桥 `PluginInteraction`：ActivityResult 队列、确认/输入/选择/日期时间/文件/图片/扫码/目录、Snackbar 与页面上下文；`PluginInteractionOverlay` 挂在 MainActivity 根部，`PluginInteractionHost.forPlugin()` 为每个插件提供实现 |
| `components/` | manifest 注册的插件宿主 Android 组件：`PluginHostService`（specialUse 前台服务）、`PluginSystemEventReceiver`（开机/更新/精确闹钟）、`PluginHostActivity`（独立插件窗口）、`PluginNotificationListenerService`/`PluginNotificationActionReceiver`（通知监听与动作，见目录内 AGENTS.md） |
| `HostToolNames.kt` | 宿主保留工具名表：引擎默认（read/write/edit/bash）、宿主工具（ask_user_question/present/system_shell、goal/job/timer/miniapp 变体、web/arrange/screen/virtual 工具）、SDK 工具（tool_search/mcp_\*）与 `mcp__` 前缀；外部插件安装期做工具名冲突校验 |

## 要点

- 服务适配器已接入：`AppGraph` 按插件构造 `PluginServiceDependencies` 并注入 `PluginServices`；未注入的服务访问器抛 `PluginServiceUnavailable`。
- 插件日志统一走 `host.logs()`（`LogService`，按 `PluginLogLevel` 分级）；`PluginHost` 不再提供 `log(level: Int)` 旧签名。
- 相位由 `PluginPhase.derive` 从 `PluginRecord` + `PluginRuntimeFacts` 派生后写进 descriptor（`PENDING_RESTART` 由记录派生，不存运行期）；只有 `ACTIVE/DEGRADED` 贡献工具/技能/提示词段/UI/hooks，其它相位仍可查询描述但不聚合贡献。
- 快照 version 从 0 起、每次成功 `register/unregister/update` 递增；旧快照发布后不再变化。插件 id 重复注册返回 `Rejected` 且不加版本；跨插件工具名/技能 id/提示词段 id 重复时保留先注册者并记录到 `conflicts`。
- `PluginPaths` 的运行数据目录固定在 `<id>/data` 而不是版本目录，升级不丢插件数据；版本目录只放 `bundle/assets/lib`。
- `PluginTextResolver` 的语言 tag 由宿主注入（App 语言），不在宿主里硬编码语言；`text` 参数为宿主提供的 `String.format` 参数。
- 工具结果映射：`ImageBlock` 的字节按 base64 进 `ImageContent`；`FileRefBlock` 的 content 保留一行 `file: <path> (<mime>)` 文本供模型读取，宿主同时把 `{path, mimeType}` 写入 details 保留键 `deepdeep.fileRefs`（工具卡与历史消息据此渲染文件行），path 按插件给出的绝对路径原样输出，不读文件、不当附件投递；插件不要读写该保留键。
- `partial` 更新经 unlimited channel 在 `execute` 内顺序转发，插件回调可在任意线程调用；`execute` 异常（取消/中断除外）转成 `isError` 结果。
- 槽渲染没有崩溃隔离：插件 Compose 内容抛异常会带崩 App（与工具/回调异常一致）；插件作者需自测，宿主不会静默禁用绑定位。
- 外部插件清单只解析一次（`PluginManifestCodec.parse`），严格校验（`decode/validate`）由安装流程负责。
- manifest 契约（`PluginManifest`/`UiManifest`/`Provides`/`PublisherInfo`/`PluginComponentDeclaration`）是宿主实现的只读接口，内部实现与解析/校验在 `external/HostPluginManifest.kt` 与搬入的 `PluginManifestCodec/PluginValidation/PluginVersion`；1.1.0 起 manifest 还包含 `permissions` 与 `components`，安装期校验权限/组件与 capability 的一致性；事件同理由 `events/HostPluginEvents.kt` 实现。
- 无会话系统事件（通知接收/动作、后台任务、分享、深链、应用前后台）由 `manager/PluginSystemEvents` 按 capability 与 `components` 门槛串行派发到各插件全局 hooks；会话内事件仍走 `PluginSessionHooks`。
- 外部包安装/装载时 `apiLevel` 不在 `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL` 会被拒绝；低于下限（level 5）由 manager 映射为「插件 API 5 已停用，请更新插件」。
- 新增目录/文件时同步本索引。
