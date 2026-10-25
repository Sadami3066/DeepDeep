# Android 能力

DeepDeep 使用系统公开 API 提供以下能力；所有需要用户授权的项都在设置页有明确入口与状态说明。未评审的 Android 能力不注册工具。

## 已实现

### GUI 操作（无障碍）

- 风险与门槛：无障碍服务必须由用户在系统设置手动开启，App 无法自行开启；Android 13 侧载应用可能需要在应用信息里允许“受限设置”。
- 入口：插件页安装并启用「GUI 操作」后，详情页「打开无障碍设置」跳系统页；设置页「GUI 控制」行显示服务状态。
- 工具：`gui_observe`（当前窗口节点树）、`gui_tap`、`gui_set_text`、`gui_scroll`、`gui_global`（返回/主页/最近任务/通知栏）、`gui_wait`。只操作当前活动窗口，锁屏状态不可用；v1 不支持截图与按包名过滤。
- 安全：技能与工具提示要求删除/发送/支付/授权前先确认，不点击系统权限弹窗、无障碍授权、设备管理员页面，不读取密码/验证码；外部界面文本只作为数据。

### 语音助手（ASR/TTS）

- 语音输入使用系统 `SpeechRecognizer`（需要 `RECORD_AUDIO` 与 RecognitionService 查询声明）；回答朗读使用系统 `TextToSpeech`；语言跟随 App 语言并有回退。
- 入口：聊天输入框麦克风、设置页「语音助手」、系统助手入口（`ACTION_ASSIST`）。可在面板里申请成为系统助手（`RoleManager.ROLE_ASSISTANT`），部分 OEM 可能不支持或需要到系统设置手动选择。
- 面板流程：聆听（部分转录 + 音量）→ 定稿后按聊天设置“填入输入框”或“直接发送” → 回答流式显示 → 按设置自动朗读，可随时停止。
- 识别服务由系统/厂商提供，可能依赖网络；设备无识别服务、权限拒绝、无网络时给本地化错误与重试/去设置入口。后台常听、唤醒词、悬浮窗不在当前范围。

### 接收分享

- 支持 `ACTION_SEND` 与 `ACTION_SEND_MULTIPLE`（图片、文件、文本）。图片/文件插入当前会话附件；纯文本预填输入框，不自动发送。
- 冷启动恢复最近会话，热启动插入当前会话；生成中分享会排队等待。内容先复制到 App 私有缓存，避免 URI 授权过期；超过 25MB/20 个或读取失败按文件粒度提示。
- App 自身分享时选择器排除自己，避免分享回环。

### 读取短信

- 设置页「读取短信」开关；首次开启请求 `READ_SMS` 运行时权限，拒绝后跳应用详情。
- 工具 `read_sms`：只读查询 inbox/sent/all，支持 limit（1–50，默认 10）、地址、起始时间过滤；正文截断 2000 字，结果进入会话历史。
- 不发送、不修改、不删除短信；仅侧载使用，应用商店对 `READ_SMS` 有额外政策限制。
- 平台差异：部分 ROM（如 MIUI）可能只向非默认短信应用暴露部分短信记录；工具会返回系统实际允许读取的内容，不会伪造成完整收件箱。

## 待评估：闹钟 / 日历 / 待办 / 提醒

DeepDeep 现有的定时任务是 App 内前台服务计时：进程不在时不会唤醒，不是系统提醒。以下为公开接口调研结论，尚未实现。

### 系统闹钟

- 只能“委托创建”：`AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` / `ACTION_SHOW_ALARMS` 零危险权限（建议声明 `com.android.alarm.permission.SET_ALARM` 普通权限以兼容少数实现）。`EXTRA_SKIP_UI` 只是请求，OEM 时钟应用可能仍显示确认页。
- 无法枚举或可靠删除系统时钟里的闹钟；`AlarmManager.getNextAlarmClock()` 只反映通过 `setAlarmClock()` 注册的闹钟，不能当数据源。
- 适合“帮你设一个闹钟/计时器”，界面上不要展示“已设置的闹钟列表”。

### 应用内精确提醒

- 到点必达需 `AlarmManager.setAlarmClock()` 或 `setExactAndAllowWhileIdle()` + `SCHEDULE_EXACT_ALARM` 特殊访问。
- Android 12–13 默认授予；**Android 14 起新安装且 target 33+ 默认拒绝**，需要 `canScheduleExactAlarms()` 判断与 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 引导，不能静默失败。
- 无权限时降级到不精确闹钟并说明可能延迟。`USE_EXACT_ALARM` 安装即授予但受应用商店政策限制，不作为默认方案。
- Doze 会推迟普通闹钟；`setAlarmClock()` 不受 Doze 限制。建议只注册最近一条提醒、触发后重排，并处理重启、改时间、改时区后的重排（需要 `RECEIVE_BOOT_COMPLETED`，且 Android 13+ restricted 电池状态可能收不到）。
- 通知权限（`POST_NOTIFICATIONS`）是提醒可见的前提；全屏提醒受 Android 14+ 限制，需 `canUseFullScreenIntent()` 判断。

### 日历

- 委托（推荐 v1）：`Intent.ACTION_INSERT` + `CalendarContract.Events.CONTENT_URI`，零权限，打开系统日历新建页由用户保存；注意 `CalendarContract` 没有 `ACTION_INSERT` 常量。可传标题、描述、地点、起止时间、全天、时区、重复规则、参会人邮箱。官方未定义返回结果，拿不到 event id，也无法确认用户是否保存。
- 直写 Provider：需要 `READ_CALENDAR` / `WRITE_CALENDAR`，且要找到可写日历（`CALENDAR_ACCESS_LEVEL >= CONTRIBUTOR`）；本地日历通常由同步适配器管理，设备上没有可写日历时不可用；提醒与邀请的最终行为取决于账户和同步适配器。建议暂缓。
- `ACTION_VIEW` + `content://com.android.calendar/time/<epoch_ms>` 可零权限打开日历到指定时间。

### 待办

- Android 平台没有公开的 To-Do/Tasks provider 或标准 intent；Google Tasks 只有 REST API + OAuth，无公开 Android provider。
- 短期建议：用 DeepDeep 自建提醒（可完成/延期/重复），或把事项写成日历全天事件；与外部任务服务同步属于独立的 OAuth + 增量同步工程。

### 落地建议

1. 先做零权限委托：设闹钟、设计时器、创建日历事件、打开日历。
2. 再做自建提醒：精确闹钟 + 通知 + 重启/时区重排 + 权限降级；与现有 ScheduleService 分开为 ReminderStore + AlarmScheduler，“到点唤醒”不再依赖进程存活。
3. 全屏提醒与日历直写按需评估；待办先做 App 内方案。

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
