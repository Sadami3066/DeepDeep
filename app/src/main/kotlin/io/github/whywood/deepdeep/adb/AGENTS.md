# adb · 内嵌无线调试

通过系统无线调试或本地 TCP ADB 在 adbd 下起一个本地 shell daemon，App 借它执行 uid 2000 的命令并拉起虚拟屏 helper。无线调试首次配对走系统配对码；本地模式由电脑执行 `adb tcpip 5555` 打开 adbd 的本地端口，App 直连 `127.0.0.1:5555`，首次连接在手机上允许调试授权。daemon 只监听环回、token 鉴权，不联网。

## 文件

| 文件 | 职责 |
|---|---|
| `AdbBootstrap.kt` | 配对/连接/守护进程编排，按 `AdbConnectMode`（无线/本地）分派；暴露状态、模式与本地端点、exec 与 helper 启动 |
| `AdbLocalEndpoint.kt` | 本地 ADB 端点解析与默认值（127.0.0.1:5555） |
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
- 本地模式：`connectLocalAndStart(host, port)` 校验端点后直连，跳过无线调试门槛与配对；未授权时回 `LOCAL_AUTH_REQUIRED`，连不上回 `LOCAL_CONNECT_FAILED`；连接成功后与无线模式共用 `startDaemon`/健康检查。手机重启后 adbd 回到 USB 模式，需要重新执行电脑命令。
- 模式与本地端点存 SharedPreferences（`connect_mode`/`local_host`/`local_port`），`AdbBridgeState` 同时携带模式与端点供设置页渲染。
- 虚拟屏 helper 也由 daemon 拉起（`startHelper`/`killHelper`）；Root 后端不依赖 AdbBootstrap。
