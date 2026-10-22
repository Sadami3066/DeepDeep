# Architecture

## 模块

```
:app
 ├─ :runtime   pi2kotlin 适配、事件、Provider 目录、会话元数据、Key
 ├─ :shell     Alpine/proot、LinuxShellExecutor
 └─ :design    Compose 主题与组件
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
- `ProviderCatalog` 从 pi2kotlin `builtinProviders` 读取 42 家 Provider、模型数和 OAuth 标记。
- `ModelConfigStore` 保存 provider/model/thinking；聊天 composer 切换模型和思考。
- `SdkCredentialStore` 使用 Android Keystore 加密的 `ApiKeyStore`，可按 provider 预加载/清除 key。
- `SessionMetaStore`/`SessionRepository` 在 SDK JSONL 之上维护标题覆盖、置顶、归档和删除。

## UI

- ChatScreen：Claude 式顶栏、左侧用户气泡、全宽 assistant、浮动 composer。
- 过程区：DSH 式过程摘要 + reasoning/tool 卡片；运行中默认展开，完成后可折叠。
- ChatScrollController：按布局实际 overflow 跟随底部，用户上拖后暂停并显示“回到底部”。
- SettingsScreen：Claude 式分组卡；占位项有可见反馈；Provider/运行环境/颜色模式/关于是真实入口。
- ProviderList/ProviderDetail：42 家 Provider、搜索、API Key 存储、OAuth 占位、设为当前 Provider。
- Drawer：顶部功能项带图标；历史项纯文字，置顶/最近分区；长按重命名/置顶/归档/删除；归档页。

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
