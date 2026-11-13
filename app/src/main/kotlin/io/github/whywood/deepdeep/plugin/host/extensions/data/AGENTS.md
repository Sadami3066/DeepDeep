# plugin/host/extensions/data · 插件数据能力适配器

`plugin-api 1.1.0` 数据能力的宿主实现：设置存储、定位、联系人、短信、日历、媒体、语音、网页、凭据、无障碍。由 `buildDataPluginServices(deps)` 一次构造为 `DataPluginServices`，再由装配层注入 `PluginHost` 的对应访问器；本目录不持有插件实例，也不做生命周期管理。

## 文件

| 文件 | 职责 |
|---|---|
| `DataServiceDeps.kt` | `DataServiceDeps`（上下文/插件目录/交互桥/宿主服务依赖）与 `DataPluginServices` / `buildDataPluginServices` 工厂 |
| `DataPermissions.kt` | 运行时权限常量、`ContextCompat` 检查与可读异常；插件权限 wire ↔ Android `Manifest.permission` 双向映射 |
| `PluginSettingsService.kt` | `files/plugins/<id>/host/settings.json`：`PluginSettingsStore`（Mutex、临时文件原子 rename、StateFlow）+ `observe`、JSON 值读写 |
| `LocationPluginService.kt` | `LocationManager` 最佳 provider、lastKnown/current（超时）、`updates()`、Geocoder 正/反地理编码 |
| `ContactsPluginService.kt` | `ContactsContract` 批量查询（电话/邮箱/公司/备注/星标）、搜索、创建/更新/删除；纯映射 `buildPluginContact` |
| `SmsPluginService.kt` | `content://sms` 会话与消息读取、`SmsManager` 单发/分片发送、已读标记与按会话删除；纯分片与线程聚合 |
| `CalendarPluginService.kt` | 复用 `arrange/ArrangeCalendarGateway`（唯一写路径）读写日历/事件/提醒；权限申请走 `PluginInteraction`；纯 DTO 映射 |
| `MediaPluginService.kt` | 下载/相册写入、媒体扫描、打开/分享/预览（FileProvider）、缩略图与读图（`ImageNormalizer`/`DdImageScaling`）、content uri 导入 |
| `SpeechPluginService.kt` | `TextSpeaker` TTS（插件 scope 结束释放）与 `SpeechRecognizerController` 单次识别（麦克风权限经交互桥）；错误文案映射 |
| `WebPluginService.kt` | 复用宿主 `WebProvider`（默认 `DeepSeekWebProvider`）做搜索与网页抓取，不可用时 `available=false` |
| `CredentialPluginService.kt` | `@PluginExperimentalApi` 只读凭据：服务商 API key/baseUrl、MCP OAuth token、`installationId`/`adbToken` hostValues；纯合并映射，不写宿主凭据、不打印敏感值 |
| `AccessibilityPluginService.kt` | `AccessibilityEventHub` 单例事件总线（128 buffer、DROP_OLDEST）与 `performGlobalAction` 映射；`GuiAccessibilityService` 负责转发与绑定实例 |

## 要点

- `DataServiceDeps` 的 `webProvider`/`credentialStore`/`customProviders`/`providerCatalog`/`mcpConfig`/`mcpTokenStore`/`installationId`/`adbToken` 均为可空注入，装配层按插件能力传值；`arrangeCalendar` 缺省用 `AndroidArrangeCalendarGateway`。
- 权限不足一律抛可读 `SecurityException`，不返回假数据；申请权限统一经 `PluginInteraction.requestPermissions`（日历/麦克风）。
- 阻塞 IO 走 `Dispatchers.IO`；Flow 用 `callbackFlow`/`awaitClose` 管理监听注销。
- 日历写路径只经 `ArrangeCalendarGateway`；提醒读取直接查 `CalendarContract.Reminders`。
- 媒体导入文件落在 `pluginDataDir/imports`（FileProvider 只暴露 files/plugins，cache/plugins 不可分享），分享 chooser 复用 `ui/common/ShareChooser`，文案复用现有 `strings`。
- 单测在 `app/src/test/.../plugin/host/extensions/data`，覆盖设置存储原子写/序列化、短信分片与线程聚合、联系人/日历/凭据映射、权限与无障碍动作映射等纯逻辑。
