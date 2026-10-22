# Architecture

## 模块

```
:app
 ├─ :runtime   pi2kotlin 适配、DeepSeek、会话、历史、Key
 ├─ :shell     Alpine/proot、LinuxShellExecutor
 └─ :design    Compose 主题与组件
```

依赖方向：`:app` 依赖三个模块；`:runtime`/`:shell`/`:design` 不互相依赖。

## 运行时

```
Compose UI
  → AgentRuntimeHost
  → PiSessionEngine
  → createAgentSession(
        cwd = filesDir/workspace,
        agentDir = filesDir/agent,
        model = deepseek/<model>,
        tools = pi 原生文件工具 + bash,
        credentials = SdkCredentialStore
    )
  → DeepSeek
  → RuntimeEvent / StateFlow
```

`bash` 使用 pi2kotlin 自带的 `bashTool`，只把 `ShellExecutor` 换成 `LinuxShellExecutor`；命令在 Alpine `/workspace` 中执行。

## 存储

```
filesDir/
  workspace/        cwd，同时是 Alpine /workspace
  agent/            agentDir，SDK sessions/skills/AGENTS.md
  linux/rootfs/     Alpine rootfs
```

API Key 由 Android Keystore 加密后存 DataStore；会话由 pi2kotlin 写入 JSONL。

## 工具

- pi 原生：`read`、`write`、`edit`、`ls`、`grep`、`find`
- `bash`：`LinuxShellExecutor` → `LinuxEnvironment` → PRoot/Alpine
- 不注册 `device_info`、`open_app` 等自定义工具

## root 扩展

`:shell` 预留 `ShellExecutor` 实现位。以后增加 `RootShellExecutor`，同一个 `bash` 工具改用它即可；不新增工具名。
