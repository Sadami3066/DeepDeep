# adb · 内嵌无线调试

通过系统无线调试在 adbd 下起一个本地 shell daemon，App 借它执行 uid 2000 的命令并拉起虚拟屏 helper。首次配对走系统配对码；daemon 只监听环回、token 鉴权，不联网。

## 文件

| 文件 | 职责 |
|---|---|
| `AdbBootstrap.kt` | 配对/连接/守护进程编排，暴露状态、exec 与 helper 启动 |
| `ShellDaemon.kt` | 挂在 adbd 下的守护进程：本地 HTTP 执行 shell 与 helper |
| `ShellClient.kt` | daemon 的本地 HTTP 客户端（健康检查与命令执行） |
| `AdbBridgeState.kt` | 桥状态与错误码、异常定义 |
| `AdbBridgeRuntime.kt` | 进程级 `AdbBootstrap` 单例持有与初始化 |
| `AdbKeyStore.kt` | ADB 密钥对与自签证书的生成、持久化 |
| `LocalAdbManager.kt` | 内嵌 ADB 连接管理器（环回、密钥、超时） |
| `AdbPairingNotifier.kt` | 配对码输入通知（RemoteInput）与通知渠道 |
| `AdbPairingReceiver.kt` | 接收通知里的配对码并转交前台服务 |

## 要点

- daemon 由 adbd 拉起、跑在 shell(2000)；关闭无线调试或重启后会被系统清理，需要在「设置 → 系统访问」重新「连接并启动」，不必重新配对。
- 配对码链路：通知 RemoteInput 或 App 输入 → `AgentService.ACTION_PAIRING_CODE` → `AdbBootstrap.handlePairingReply`；配对状态与 token 存 SharedPreferences。
- 虚拟屏 helper 也由 daemon 拉起（`startHelper`/`killHelper`）；Root 后端不依赖 AdbBootstrap。
