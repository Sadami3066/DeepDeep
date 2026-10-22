# Architecture

## 模块

```
:app
 ├─ :runtime   pi2kotlin 适配、事件、Provider 目录、会话元数据、Key
 ├─ :shell     Alpine/proot、LinuxShellExecutor
 └─ :design    Compose 主题、字体、motion tokens、组件
```

依赖方向：`:app` 依赖三个模块；`:runtime`/`:shell`/`:design` 不互相依赖。

## 运行时

```
Compose ChatScreen
  → ChatViewModel
  → AgentRuntime interface
  → AgentRuntimeHost
  → PiSessionEngine
  → createAgentSession(...)
  → provider/model
  → RuntimeEvent(runId, ...)
  → ChatUiState
```

关键点：

- `AgentRuntime` 是接口，`AgentRuntimeHost` 实现；Chat 层可替换/测试。
- 每次发送生成 `runId`；流式事件带 runId，UI 丢弃旧 run 的迟到事件。
- `RunStarted` / `ActivityChanged` / `TextDelta` / `ReasoningDelta` / `ToolStarted` / `ToolFinished` / `RunFinished` / `RunFailed` 组成 UI 投影。
- 发送前先写 session 标题并发出 `SessionListChanged`，抽屉立即出现新会话。
- ProviderCatalog 从 pi2kotlin `builtinProviders` 读取 42 家 Provider、模型数和 OAuth 标记。
- ModelConfigStore 保存 provider/model/thinking；聊天 composer 切换模型和思考。
- SdkCredentialStore 使用 Android Keystore 加密的 ApiKeyStore，可按 provider 预加载/清除 key。
- SessionMetaStore/SessionRepository 在 SDK JSONL 之上维护标题覆盖、置顶、归档和删除。
- 会话文件读取/写入在 IO dispatcher；Chat 层注入 `ioDispatcher` 以便测试。
- ChatViewModel 为每个已加载过的会话保留消息快照缓存；再次打开时先渲染缓存，再在后台 `open` SDK session（单 mutex 串行，epoch 丢弃过期打开），发送会等待打开完成。

## 消息模型

- 一个 UI assistant message 对应一个用户回合。
- `AssistantStep` 有序保存 `Reasoning`、`Text`、`Tool`；`AssistantMessage.text` 是最终回复。
- 历史映射按用户消息分 turn，把连续 assistant/tool 消息聚合成一个 message；工具前的文本留在过程，工具后的最终文本成为 answer。
- 实时发送时文本先进入过程的 `Text` step，结束时把尾部最终文本移到 answer。

## UI 与动效

- `ui/navigation`：`AppDestination`、`AppNavigator`、`AppScreenTransition`、`AnimatedScreenHost`。
- 设置层级使用 typed stack + `AnimatedContent`；系统返回手势 -> pop。
- `design/DdMotion` 统一时长/easing；`DdTypingDots` 承载思考动画。
- 抽屉用持久 composition + `graphicsLayer` translation，避免开关时反复组合；遮罩只在可见时挂载，避免拦截触摸。
- ChatScrollController 按实际 overflow 跟随；用户上拖后暂停。
- 按压态统一为圆角灰 wash（`DdPressable` / `DdListRow` / `DdMenu`），不使用方框 ripple。
- `DdMenu` 统一浮层圆角、纯白背景、图标左对齐、danger 项与按压态。
- Markdown 解析在 `MarkdownParser`（嵌套列表树、图片块、递归引用、表格、setext、行内 AST、引用式链接/脚注上下文）；渲染在 `ChatMarkdown`（行内 span、表格横滚、引用条、数学块、脚注列表）；`CodeHighlighter` 负责语法高亮，`MarkdownImage` 负责图片异步加载/缓存/失败占位与全屏预览。
- 消息长度操作在 `ChatTurn` / `ChatActions`（长按锚定菜单、引用预览在 `ChatComposer`）。
- Compose stability config 位于 `app/compose-stability.conf`，把 runtime 模型与 UI state 声明为 stable，避免流式更新重组合全部消息。

## 存储

```
filesDir/
  workspace/        cwd，同时是 Alpine /workspace
  agent/            agentDir，SDK sessions/skills
  linux/rootfs/     Alpine rootfs
```

API Key、模型配置、会话元数据在 DataStore；会话正文由 pi2kotlin 写入 JSONL。

## 工具

- pi 原生：`read`、`write`、`edit`、`ls`、`grep`、`find`
- `bash`：`LinuxShellExecutor` → `LinuxEnvironment` → PRoot/Alpine
- 不注册 `device_info`、`open_app` 等自定义工具

## root 扩展

`:shell` 预留 `ShellExecutor` 实现位。以后增加 `RootShellExecutor`，同一个 `bash` 工具改用它即可；不新增工具名。
