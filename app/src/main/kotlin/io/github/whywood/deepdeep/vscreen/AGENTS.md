# vscreen · 虚拟屏幕

虚拟屏幕能力：由 shell(2000) 或 root 身份的 helper 进程建后台屏幕，App 侧引擎经本地 socket 驱动它。只监听环回并用 nonce/token 鉴权，不联网。

## 文件

| 文件 | 职责 |
|---|---|
| `VirtualScreenEngine.kt` | 客户端引擎：连接、命令、心跳、恢复、截图 |
| `VirtualScreenHelperMain.kt` | 特权 helper 入口：建虚拟屏、注入输入、启动应用、推帧 |
| `VirtualScreenContracts.kt` | 状态、结果与控制器接口契约 |
| `VirtualScreenProtocol.kt` | helper 与 App 的帧协议编解码与大小上限 |
| `VirtualScreenFrames.kt` | 帧透明像素判定、截图等待策略与失败文案 |
| `VirtualScreenHeartbeat.kt` | 心跳发送与 pong 超时判定 |
| `VirtualScreenRecovery.kt` | 断线分类、重连调度与启动/恢复决策 |
| `VirtualScreenIdle.kt` | 15 分钟无操作自动关屏策略 |
| `VirtualScreenCorrelation.kt` | 请求/响应 id 对账与截图闸门 |
| `VirtualScreenTransportLog.kt` | 传输事件与断线的结构化日志 |
| `PrivilegedLauncher.kt` | 按当前特权后端启动/清理 helper 进程 |

## 要点

- helper 是独立进程（ADB uid 2000 或 root）；无线调试关闭、App 进程结束或 15 分钟没有 `screen_*` 操作都会导致它退出并释放虚拟屏。
- 只有用户主动要求在线（desiredOnline）时才自动重连；失败分 transport/backend/start 三类，供 screen 工具给可执行提示。
- 虚拟屏始终竖屏，分辨率取真实屏幕短边×长边；在线态带 instanceId，重建后旧 ref 与坐标系作废。
