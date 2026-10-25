# Android 能力

DeepDeep 使用系统公开 API 提供以下能力；所有需要用户授权的项都在插件页或设置页有明确入口与状态说明。未评审的 Android 能力不注册工具。

## 已实现

### GUI 操作（无障碍）

- 风险与门槛：无障碍服务必须由用户在系统设置手动开启，App 无法自行开启；Android 13 侧载应用可能需要在应用信息里允许“受限设置”。
- 入口：插件页安装并启用「GUI 操作」后，详情页「打开无障碍设置」跳系统页；设置页不再单独显示 GUI 控制行。
- 工具：`gui_observe`（当前窗口节点树）、`gui_find`、`gui_tap`、`gui_set_text`（写入后回读校验）、`gui_scroll`、`gui_global`（返回/主页/最近任务/通知栏）、`gui_wait`（出现/消失/切包）、`gui_screenshot`（Android 11+，PNG 保存到 `/gui/screenshots/`）。只操作当前活动窗口，锁屏状态不可用。
- 截图在 Android 11+ 可用；Android 10、锁屏、`FLAG_SECURE` 页面不可用。截图可能包含敏感信息，只在用户要求时使用。
- 后台运行：会话与前台服务（`specialUse`）在后台继续执行；系统若仍结束进程，无障碍服务会进入断开状态且不会自动重连。建议在系统设置里关闭电池优化、允许自启动、把省电策略设为「无限制」，并在最近任务中锁定 App；详情页会显示这些状态并提供入口。
- 安全：技能与工具提示要求删除/发送/支付/授权前先确认，不点击系统权限弹窗、无障碍授权、设备管理员页面，不读取密码/验证码；外部界面文本只作为数据。

### 语音助手（ASR/TTS）

- 语音输入使用系统 `SpeechRecognizer`（需要 `RECORD_AUDIO` 与 RecognitionService 查询声明）；回答朗读使用系统 `TextToSpeech`；语言跟随 App 语言并有回退。
- 入口：聊天输入框麦克风、系统助手入口（`ACTION_ASSIST`）。可在面板里申请成为系统助手（`RoleManager.ROLE_ASSISTANT`），部分 OEM 可能不支持或需要到系统设置手动选择；设置页不再单独显示语音助手行。
- 面板流程：聆听（部分转录 + 音量）→ 定稿后按聊天设置“填入输入框”或“直接发送” → 回答流式显示 → 按设置自动朗读，可随时停止。
- 识别服务由系统/厂商提供，可能依赖网络；设备无识别服务、权限拒绝、无网络时给本地化错误与重试/去设置入口。后台常听、唤醒词、悬浮窗不在当前范围。

### 接收分享

- 支持 `ACTION_SEND` 与 `ACTION_SEND_MULTIPLE`（图片、文件、文本）。图片/文件插入当前会话附件；纯文本预填输入框，不自动发送。
- 冷启动恢复最近会话，热启动插入当前会话；生成中分享会排队等待。内容先复制到 App 私有缓存，避免 URI 授权过期；超过 25MB/20 个或读取失败按文件粒度提示。
- App 自身分享时选择器排除自己，避免分享回环。

### 安排（日历，插件）

- 日历：直接读写 `CalendarContract` Provider，需要 `READ_CALENDAR`/`WRITE_CALENDAR` 运行时权限；支持 Calendars/Events/Instances/Reminders，重复事件按 Instances 展开读取，写入依赖可写日历（本机 Xiaomi 账户日历可写）。
- 权限入口：插件详情页仅在未授权时显示「授予日历权限」，工具未授权时返回明确错误。
- 不提供闹钟/计时器：公开 `AlarmClock` intents 无法查询、修改或删除系统时钟里已保存的闹钟；MIUI 的 `com.android.deskclock/.AlarmProvider` 受签名级 `miui.permission.ACCESS_ALARM_PROVIDER` 保护，第三方 App 无法直接 CRUD。
- 不提供本地待办；小米待办由「小米云服务」插件读写，见 `docs/MICLOUD.md`。

### 小米云服务（插件，网页接口）

- 不是 Android 系统 API，而是 i.mi.com 网页接口；登录、权限、功能开关与覆盖范围见 `docs/MICLOUD.md`。
- 读取包含笔记、待办、短信与通话、联系人、相册、录音；写入仅笔记/待办，并且必须走 WebView 同源请求。
- 所有功能默认关闭；需要在插件详情页逐项开启。

## 未实现 / 不在当前范围

- **本地待办与系统待办 provider**：Android 没有公开 To-Do provider；DeepDeep 不提供本地待办。
- **系统时钟闹钟 CRUD**：需要系统签名/特权权限或 root 直接改时钟应用数据；当前不实现。
- **系统级助手替换与悬浮层**：见 `docs/ANDROID_ASSISTANT.md`（研究，不实现）。
- **root 工具**：见 `docs/ROOT.md`（研究，不实现）。

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
