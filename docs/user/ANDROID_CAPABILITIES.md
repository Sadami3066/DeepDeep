# Android 能力

DeepDeep 使用系统公开 API 提供以下能力；所有需要用户授权的项都在插件页或设置页有明确入口与状态说明。未评审的 Android 能力不注册工具。

## 已实现

### GUI 操作（无障碍）

- 风险与门槛：无障碍服务必须由用户在系统设置手动开启，App 无法自行开启；Android 13 侧载应用可能需要在应用信息里允许“受限设置”。
- 入口：插件页启用「GUI 操作」后，详情页「打开无障碍设置」跳系统页；设置页不再单独显示 GUI 控制行。
- 工具：`gui_observe`（当前窗口节点树）、`gui_find`、`gui_tap`、`gui_set_text`（写入后回读校验）、`gui_scroll`、`gui_global`（返回/主页/最近任务/通知栏）、`gui_wait`（出现/消失/切包）、`gui_screenshot`（Android 11+，PNG 保存到 `/gui/screenshots/`）。只操作当前活动窗口，锁屏状态不可用。
- 截图在 Android 11+ 可用；Android 10、锁屏、`FLAG_SECURE` 页面不可用。截图可能包含敏感信息，只在用户要求时使用。
- 后台运行：会话与前台服务（`specialUse`）在后台继续执行；系统若仍结束进程，无障碍服务会进入断开状态且不会自动重连。建议在系统设置里关闭电池优化、允许自启动、把省电策略设为「无限制」，并在最近任务中锁定 App；详情页会显示这些状态并提供入口。
- 安全：技能与工具提示要求删除/发送/支付/授权前先确认，不点击系统权限弹窗、无障碍授权、设备管理员页面，不读取密码/验证码；外部界面文本只作为数据。

### 语音助手（ASR/TTS）

- 语音输入使用系统 `SpeechRecognizer`（需要 `RECORD_AUDIO` 与 RecognitionService 查询声明）；回答朗读使用系统 `TextToSpeech`；语言跟随 App 语言并有回退。
- 入口：聊天输入框麦克风（应用内面板）、系统助手浮层（默认助手 `ACTION_ASSIST`，透明 Activity 浮在其它 App 上，见 `ANDROID_ASSISTANT.md`）。设置 → 系统助手可申请默认助手角色；部分 OEM 可能不支持或需要到系统设置手动选择。
- 面板流程：聆听（部分转录 + 音量）→ 定稿后按聊天设置“填入输入框”或“直接发送” → 回答流式显示 → 按设置自动朗读，可随时停止。
- 浮层流程：自动聆听 → 识别后直接发送 → 回答流式 Markdown → 可复制/分享/朗读/在 App 中打开；沿用当前/最近会话。
- 识别服务由系统/厂商提供，可能依赖网络；设备无识别服务、权限拒绝、无网络时给本地化错误与重试/去设置入口。常驻悬浮球、唤醒词、后台常听不在当前范围。

### Root 能力（可选，默认关闭）

- 前提：设备已解锁并 root（Magisk / KernelSU / APatch 任一），root 管理器允许 DeepDeep。未 root 设备不受影响，`root_shell` 不注入。
- 入口：设置 → 运行环境 →「Root 能力」开关；开启后点「检测并授权」，系统 root 管理器会弹授权。状态显示未检测/已授权/被拒绝/超时/不可用。
- 工具：`root_shell`，单条命令、默认 15 秒、最长 120 秒（超时由内层 `timeout -s KILL` 终止命令，不只杀 su 客户端），stdout+stderr 合计 64 KiB 截断，返回 exitCode；错误码 `root_disabled` / `root_foreground_required` / `root_unavailable` / `root_denied` / `root_timeout`。
- 边界：只在 App 前台、主聊天会话可用；创意工坊页面、后台任务、定时任务不获得 root；命令与退出码写运行日志，不记录输出正文；不持久化授权，每次以 `su` 实际结果为准。
- 风险：root 命令可破坏系统分区、其他应用数据或设备；恢复只能靠用户自己的备份/刷机。设计决定见 `docs/dev/adr/0008-root-capability.md`。

### 接收分享

- 支持 `ACTION_SEND` 与 `ACTION_SEND_MULTIPLE`（图片、文件、文本）。图片/文件插入当前会话附件；纯文本预填输入框，不自动发送。
- 冷启动恢复最近会话，热启动插入当前会话；生成中分享会排队等待。内容先复制到 App 私有缓存，避免 URI 授权过期；超过单文件 1GB/20 个或读取失败按文件粒度提示。
- App 自身分享时选择器排除自己，避免分享回环。

### 安排（日历，插件）

- 日历：直接读写 `CalendarContract` Provider，需要 `READ_CALENDAR`/`WRITE_CALENDAR` 运行时权限；支持 Calendars/Events/Instances/Reminders，重复事件按 Instances 展开读取，写入依赖可写日历（本机 Xiaomi 账户日历可写）。
- 全天事件按用户所在时区的日期原样编码为 UTC 日期边界（`ALL_DAY=1`，结束日独占），读回显示与创建日期一致，不因时区偏移到前一天。
- 权限入口：插件详情页仅在未授权时显示「授予日历权限」，工具未授权时返回明确错误。
- 不提供闹钟/计时器：公开 `AlarmClock` intents 无法查询、修改或删除系统时钟里已保存的闹钟；MIUI 的 `com.android.deskclock/.AlarmProvider` 受签名级 `miui.permission.ACCESS_ALARM_PROVIDER` 保护，第三方 App 无法直接 CRUD。
- 不提供本地待办；小米待办由「小米云服务」插件读写，见 `docs/user/MICLOUD.md`。

### 小米云服务（插件，网页接口）

- 不是 Android 系统 API，而是 i.mi.com 网页接口；登录、权限、功能开关与覆盖范围见 `docs/user/MICLOUD.md`。
- 读取包含笔记、待办、短信与通话、联系人、相册、录音；写入仅笔记/待办，并且必须走 WebView 同源请求。
- 所有功能默认关闭；需要在插件详情页逐项开启。

## 未实现 / 不在当前范围

- **本地待办与系统待办 provider**：Android 没有公开 To-Do provider；DeepDeep 不提供本地待办。
- **系统时钟闹钟 CRUD**：需要系统签名/特权权限或 root 直接改时钟应用数据；当前不实现。
- **常驻悬浮球/唤醒词/后台常听**：见 `docs/user/ANDROID_ASSISTANT.md`（不在当前范围）。
- **root 专用文件/短信/pm 工具**：当前只提供前台 `root_shell` 一条通用能力，见下方「Root 能力」。

## 参考

- [AlarmClock](https://developer.android.com/reference/android/provider/AlarmClock)
- [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager)
- [精确闹钟（含 Android 14 默认拒绝）](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Calendar Provider](https://developer.android.com/guide/topics/providers/calendar-provider)
- [CalendarContract](https://developer.android.com/reference/android/provider/CalendarContract)
- [通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [无障碍服务](https://developer.android.com/guide/topics/ui/accessibility/service)
