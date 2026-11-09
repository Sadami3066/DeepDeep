# data/remote · 远程连接数据

电脑端 DSH 的远程连接数据层：电脑与入口模型、配对链接解析、本地凭据、证书固定、网关请求与连接协调。UI 与装配通过 `RemoteConnection` 使用，不直接碰网络与加密存储。

## 文件

| 文件 | 职责 |
|---|---|
| `RemoteModels.kt` | `RemoteComputer`（一台电脑，`installationId` 标识机器）+ `RemoteEntry`（一个入口/配对）+ `RemoteStatus` 状态与 `RemoteSession` 连接结果 |
| `RemotePairingLink.kt` | 解析电脑端配对链接（origin、instance、一次性 token、是否要求 CA），局域网/公网入口判定 |
| `RemoteComputerStore.kt` | 电脑列表 JSON（`files/remote-computers.json`）：原子写、读容错、按 `installationId`/实例合并同机入口、重命名/删除/最近使用 |
| `RemoteSecrets.kt` | 每台设备的 device/session/csrf token，经 `EncryptedSecretStore` 加密存本机 |
| `RemoteTls.kt` | 证书编解码、SHA-256 指纹、TOFU 固定 CA 的 TrustManager 与 OkHttp 客户端 |
| `RemoteGatewayClient.kt` | 局域网入口的配对/续期/探测请求与响应解析；错误映射为 `RemoteGatewayFailure` |
| `RemoteBrowser.kt` | 远程入口的浏览器通道：隐藏 WebView 同源 fetch 完成配对/续期/探测，cookie 留在 CookieManager；配对响应映射（401/403/设备上限/限流/异常响应）抽成纯函数供单测 |
| `RemoteConnection.kt` | 连接协调：列表状态、配对、续期、入口选择、写入 WebView cookie、删除 |

## 要点

- 电脑端唯一权威是电脑上的 dsh-mobile 插件与 DSH 进程；这里只保存连接所需的最小状态，不缓存会话内容。
- 局域网入口用原生 HTTP 通道（`RemoteGatewayClient`）；远程入口改走浏览器通道（`RemoteBrowser`）：部分网络会按 TLS 客户端指纹拦截 OkHttp/curl 而放行 Chromium，用隐藏 WebView 做同源 fetch 既能通过，也能让配对与续期留下的 cookie 被连接页直接复用。
- 入口的 `auth` 字段区分两条通道（`Native`/`Browser`），`Browser` 入口不在本机保存 token。
- 证书固定是 TOFU：局域网入口必须取 `/mobile-access/ca.cer` 并固定 CA；公开远程入口用系统信任，自签入口才抓取并固定 CA。固定后证书不匹配即失败，不降级。
- 配对/续期/探测都带 `x-dsh-mobile-capabilities: installation-id`，用返回的 `installationId` 把同一台电脑的局域网与远程入口合并；远程入口使用更长超时。
- 探测按局域网优先逐个入口尝试，连接失败会自动回退到同电脑的其它入口。
- token 只存在加密存储，JSON 列表里不写 token；删除电脑时连同入口 token 一起清理。
- 配对链接、请求体、响应解析与错误映射都是纯函数，单测覆盖；网络调用在 IO dispatcher 上。
- 公网入口优先走浏览器通道配对：只有浏览器通道不可达/响应异常/证书被拒时才回退原生通道；浏览器配对成功后持久化失败不再回退，避免电脑端留下重复设备；协程取消原样抛出。
