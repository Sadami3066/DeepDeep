# Android 系统级语音助手与悬浮 UI 调研

状态：**研究，不实现**。本文只记录平台能力、本机证据与取舍，不涉及代码改动与实施计划。

调研时间 2026-10-25；对象为 DeepDeep（`io.github.whywood.deepdeep`，minSdk 29）与本机 Redmi Note 12 Pro+（`22101316UCP` / Android 13 API 33 / MIUI 14 V140，已 root，环境详见 [Root 与高级能力](../archive/ROOT.md)）。App 内语音助手现状见 [Android 能力](ANDROID_CAPABILITIES.md)「语音助手」一节。

## 结论摘要

- Android 的“助手”是两层架构：轻量入口是 `ROLE_ASSISTANT` + `ACTION_ASSIST` Activity；完整形态是 `VoiceInteractionService`（系统常驻绑定）+ `VoiceInteractionSessionService`（交互期间由系统绑定、UI 由系统管理）。两者都允许普通第三方 App 声明和申请，不需要系统签名。
- 本机默认助手已被小米小爱占用：`ROLE_ASSISTANT` holder、`Settings.Secure.ASSISTANT`、`voice_interaction_service` 全部指向 `com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService`；识别服务为 `com.xiaomi.mibrain.speech/.asr.AsrService`。小爱是平台签名的 `SYSTEM/UPDATED_SYSTEM_APP`，并持有 `DEVICE_POWER`、`ACCESS_VOICE_INTERACTION_SERVICE` 等签名权限。
- 长按电源 0.5s 唤起小爱不是 AOSP 的 `ACTION_ASSIST` 路径，而是 MIUI 私有按键映射：`settings get system long_press_power_key` = `launch_voice_assistant`（长按主页键同样是 `launch_voice_assistant`），由系统按键处理 + MIUI 语音服务完成。普通 App 无法注册/改写这个映射。
- 普通未 root 设备上，“随时唤起”按成本排序：悬浮窗（`SYSTEM_ALERT_WINDOW` + 前台服务）→ 通知/快捷设置磁贴 → 默认助手角色（能拿则拿，拿不到不影响前两者）。系统助手角色是否可选由 ROM 决定，本机大概率被锁死。
- 完整语音助手（常驻、唤醒词、`VoiceInteractionSession` 窗口、assist 数据/截图）只有“成为默认助手 + 系统绑定”才成立；后台常听/热词在普通 App 上不可行（权限、Android 14 前台服务限制、耗电与隐私）。
- 需要改系统行为（强制助手角色、改 secure settings、电源键路由、禁用小爱）时必须 Shizuku（shell 级）或 root/LSPosed；只适合开发者设备，不进入产品承诺。本机 shell 具备 `MANAGE_ROLE_HOLDERS`/`WRITE_SECURE_SETTINGS`，root 也可用，验证可行。
- 小爱悬浮层不是独立 App：`com.miui.voiceassistoverlay` 实为 `/product/overlay` 下的 RRO 资源覆盖包（`overlayTarget=android`），不是悬浮窗应用；小爱 UI 由小爱主包的应用进程承载（本次未展开验证其窗口类型），不能把“小爱悬浮层”当成独立 App。
- 对 DeepDeep 的建议：保留现有 App 内语音面板（`SpeechRecognizer` + TTS）作为唯一识别/朗读实现；下一里程碑优先做“悬浮球/磁贴/通知 → 打开现有面板”的入口层，默认助手角色作为跨 ROM 的可选增强；唤醒词与电源键不做。

## 一、平台架构

### 1.1 两种助手形态

| 维度 | 轻量：`ACTION_ASSIST` Activity | 完整：`VoiceInteractionService` + Session |
| --- | --- | --- |
| 声明方式 | Activity 加 `android.intent.action.ASSIST` + `DEFAULT` filter | Service 声明 `android.service.voice.VoiceInteractionService`，菜单 + `BIND_VOICE_INTERACTION`；meta-data `android.voice_interaction` 指向 `<voice-interaction-service>` XML |
| 谁拉起 | 系统助手入口（长按 Home/手势/键映射）启动该 Activity | 系统绑定 VIS；交互时再绑定 Session Service，创建 `VoiceInteractionSession` 并显示其窗口 |
| 常驻能力 | 无，进程可随时回收 | VIS 由系统保持运行，可接热词/assist 状态/截图等 |
| 语音识别 | App 自己用 `SpeechRecognizer`（当前 DeepDeep 的做法） | 可在 `<voice-interaction-service>` 里指定 `recognitionService`，与系统助手语音链路绑定 |
| 窗口 | 普通 Activity | 系统管理的 Session 窗口（非普通 overlay），支持 assist 数据、截图、`startVoiceActivity` |
| 第三方可行性 | 高，任何 App 都可声明；进入系统助手入口还需用户把本 App 设为默认助手 | 中，可声明；需要成为默认助手才会被系统绑定 |
| 签名要求 | 无 | 无（`BIND_VOICE_INTERACTION` 是服务自身要求的签名级权限，用来限制“谁能 bind 该服务”，系统持有即可绑定） |

`ROLE_ASSISTANT` 是 API 29 起的“默认助手”角色，先 `RoleManager.createRequestRoleIntent(...)` 拉起系统授权页，再 `isRoleAvailable` / `isRoleHeld` 判断状态。`Settings.Secure.ASSISTANT` 是隐藏设置，SDK 注释明确：它可以是 voice interaction service、或处理 `ACTION_ASSIST` 的 Activity、或空（默认处理），且“应通过设置 `ROLE_ASSISTANT` 角色间接设置”，不应由第三方直接写。

### 1.2 VoiceInteractionService / VoiceInteractionSession

- `VoiceInteractionService.SERVICE_INTERFACE = "android.service.voice.VoiceInteractionService"`；`SERVICE_META_DATA = "android.voice_interaction"`，meta-data 指向含 `<voice-interaction-service>` 标签的 XML。
- `<voice-interaction-service>` 的典型属性：`sessionService`（`VoiceInteractionSessionService`）、`recognitionService`、`supportsAssist`、`supportsLaunchVoiceAssistFromKeyguard`。AOSP 自带示例 `frameworks/base/tests/Assist` 的 manifest 与 XML 是完整参考。
- `VoiceInteractionSessionService` 本身不带 intent-filter，由上面的 XML 引用，由系统在需要交互时绑定；`VoiceInteractionSession` 负责 UI（默认显示，`onCreateContentView()` 自定义），可以拿 assist 数据/截图、用 `startVoiceActivity` 打开新 Activity。
- Session 窗口由系统 VoiceInteractionManagerService 管理，**不是**第三方 App 想弹就能弹的 overlay；不想成为默认助手就没有这层窗口。
- 名称说明：公开 SDK 里没有叫 `AssistInteractionSession` 的框架类（API 36 的 `android.jar` 与 `android.service.voice` 包内均无）；框架公开类是 `VoiceInteractionSession` / `VoiceInteractionSessionService`。`AssistInteractionService` / `AssistInteractionSessionService` 是 AOSP 示例工程里的 App 类名，MIUI 的小爱直接沿用了这套命名（本机证据见下）。

### 1.3 RecognitionService

- 实现 `android.speech.RecognitionService` 基类，声明 `android.speech.RecognitionService` 的 service；meta-data `android.speech` 指向 `<recognition-service>` 或 `<on-device-recognition-service>`。
- 系统当前识别服务由隐藏设置 `Settings.Secure.VOICE_RECOGNITION_SERVICE` 指定，普通 App 不能通过公开 API 成为默认识别器；设置页的“语音输入”是用户层入口。
- 对本 App：默认走系统 `SpeechRecognizer` 即可（当前实现）；自建 `RecognitionService` 只在想替换系统识别器时有意义，需要系统/用户选择，普通 ROM 上价值低。

### 1.4 系统应用 / 签名限制

- 小爱本机为平台签名（`seinfo=platform`）+ `SYSTEM | UPDATED_SYSTEM_APP`，持有 `android.permission.DEVICE_POWER`、`com.miui.powercenter.permission.POWER_COMMAND`、`ACCESS_VOICE_INTERACTION_SERVICE` 等签名/私有权。
- 唤醒词由系统 `priv-app` `com.miui.voicetrigger`（`/product/priv-app/VoiceTrigger`）承担，持有 `CAPTURE_AUDIO_HOTWORD`、`MANAGE_SOUND_TRIGGER`、`INTERNAL_SYSTEM_WINDOW`、`MANAGE_USERS`、`WRITE_SECURE_SETTINGS` 等权限；这些权限普通 App 拿不到。
- 公开 SDK 不提供热词检测入口：`HotwordDetectionService`、`SoftwareHotwordDetector` 不在公开 API；`AlwaysOnHotwordDetector` 是 `@SystemApi` 且已从公开 SDK（API 31 起）移除。结论：普通 App 无法实现系统级唤醒词。
- AOSP 少数权限的保护级别带 `role` 标志（如 `CAPTURE_AUDIO_HOTWORD`、`START_ACTIVITIES_FROM_BACKGROUND`），默认助手角色在部分系统上有机会获得；但具体是否授予由 ROM 的角色配置决定，不能作为产品假设。
- 纯公开路径能拿到的是：被系统入口拉起（Activity/Session）、assist 上下文（若声明支持）、以及用户显式授权的悬浮窗/麦克风/通知；拿不到电源键、唤醒词、系统级常驻。

## 二、本机证据

设备：Redmi `22101316UCP`（codename `rubypro`，MTK），Android 13（API 33），MIUI 14 `V140`，`user/release-keys`，已 root（APatch + Zygisk Next + LSPosed，详见 [Root 与高级能力](../archive/ROOT.md)）。

### 2.1 默认助手与语音服务

```text
$ adb shell settings get secure assistant
com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService
$ adb shell settings get secure voice_interaction_service
com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService
$ adb shell settings get secure voice_recognition_service
com.xiaomi.mibrain.speech/.asr.AsrService
$ adb shell dumpsys role | grep -A1 "android.app.role.ASSISTANT"
        name=android.app.role.ASSISTANT
        holders=com.miui.voiceassist
```

`dumpsys voiceinteraction` 关键字段：

```text
mEnableService: true
mComponent=com.miui.voiceassist/com.xiaomi.voiceassistant.AssistInteractionService
Session service=com.xiaomi.voiceassistant.AssistInteractionSessionService
  permission=android.permission.BIND_VOICE_INTERACTION
  processName=com.miui.voiceassist:core
Recognition service=com.xiaomi.mibrain.speech.asr.AsrService
Hotword detection service=null
Supports assist=true
Supports launch from keyguard=false
mBound=true
mDetectorType=normal
ST Event log: loadKeyphraseSoundModel(): id = 00000000-0000-0000-0000-4d544b5f3032
```

说明：小爱就是 AOSP VoiceInteraction 形态（`AssistInteractionService` + `AssistInteractionSessionService` 命名照搬 AOSP 示例），系统已绑定；assist 支持，但“锁屏启动语音助手”配置为 false；热词检测不走框架 `HotwordDetectionService`，而走 MTK sound trigger（`mDetectorType=normal` + keyphrase sound model）。

### 2.2 组件、签名与私有触发

```text
$ adb shell dumpsys package com.miui.voiceassist | grep -E "versionName|flags="
versionName=7.13.34.3812
flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA UPDATED_SYSTEM_APP LARGE_HEAP ]
# ApplicationInfo.seinfo=platform（平台签名）
# 已授予：android.permission.DEVICE_POWER
#         com.miui.powercenter.permission.POWER_COMMAND
#         android.permission.ACCESS_VOICE_INTERACTION_SERVICE

$ adb shell pm path com.miui.voicetrigger
package:/product/priv-app/VoiceTrigger/VoiceTrigger.apk
$ adb shell dumpsys package com.miui.voicetrigger | grep "android.permission."
# CAPTURE_AUDIO_HOTWORD / MANAGE_SOUND_TRIGGER / INTERNAL_SYSTEM_WINDOW /
# MANAGE_USERS / WRITE_SECURE_SETTINGS / START_FOREGROUND_SERVICES_FROM_BACKGROUND …

$ adb shell pm path com.miui.voiceassistoverlay
package:/product/overlay/VoiceAssistAndroidOverlay/VoiceAssistAndroidOverlay.apk
$ adb shell dumpsys package com.miui.voiceassistoverlay | grep -E "overlayTarget|versionName"
versionName=2.0
overlayTarget=android
```

`com.miui.voiceassistoverlay` 是 RRO 资源覆盖包（只覆盖 android 资源），**不是悬浮窗应用**；把“小爱悬浮层”归到它头上是误读。

长按电源 0.5s 是 MIUI 私有触发（非 AOSP `ACTION_ASSIST`）：

```text
$ adb shell settings get system long_press_power_key
launch_voice_assistant
$ adb shell settings get system long_press_home_key
launch_voice_assistant
$ adb shell settings get system double_click_power_key
mi_pay
$ adb shell settings get global voice_trigger_enabled
1
$ adb shell settings get global key_xiaoai_ui_settings
8
$ adb shell settings get secure disable_voicetrigger
0
# system 里的 MTK 唤醒词项：VOICE_WAKEUP_*，训练数据在
# /data/user/0/com.mediatek.voicecommand/training/...
```

即：电源键长按的处理方是 MIUI 系统按键映射（`launch_voice_assistant`），配合小爱的 `DEVICE_POWER`/`POWER_COMMAND` 权限与 MTK `com.mediatek.voicecommand` 唤醒链路；AOSP 长按电源是关机/全局菜单，第三方 App 没有任何公开接口注册该手势。

### 2.3 第三方助手注册面（说明“能声明”但“不一定是默认”）

```text
$ adb shell cmd package query-activities -a android.intent.action.ASSIST --brief
com.miui.voiceassist/.../CTAAlertActivity
com.anthropic.claude/.../AssistantOverlayActivity
com.google.android.googlequicksearchbox/.../GoogleAppImplicitActionAssistGatewayInternal
com.openai.chatgpt/.../AssistantProxyActivity
io.github.whywood.deepdeep/.MainActivity        # 当前已声明

$ adb shell cmd package query-services -a android.service.voice.VoiceInteractionService --brief
com.miui.voiceassist/.../AssistInteractionService
com.anthropic.claude/.../ClaudeVoiceInteractionService
com.google.android.googlequicksearchbox/.../GsaVoiceInteractionService
com.openai.chatgpt/.../AssistantVoiceInteractionService

$ adb shell cmd package query-services -a android.speech.RecognitionService --brief
com.xiaomi.mibrain.speech/.asr.AsrService      # isDefault=true
com.anthropic.claude/.../ClaudeRecognitionService

$ adb shell cmd package resolve-activity --brief -a android.intent.action.ASSIST
android/com.android.internal.app.MiuiResolverActivity
```

ChatGPT / Claude / Google 均非系统应用（`flags=[HAS_CODE ...]`），说明普通第三方可以完整声明 ACTION_ASSIST、VoiceInteractionService、RecognitionService；但系统实际默认助手仍由 role/secure settings 决定，`resolve-activity` 被 MIUI 的 `MiuiResolverActivity` 截获，普通 startActivity 走不到小爱或第三方助手。

### 2.4 设置入口与本 App 现状

```text
$ adb shell cmd package resolve-activity --brief -a android.settings.MANAGE_DEFAULT_APPS_SETTINGS
com.android.permissioncontroller/.role.ui.DefaultAppListActivity
$ adb shell cmd package resolve-activity --brief -a android.settings.VOICE_INPUT_SETTINGS
com.android.settings/.Settings$ManageAssistActivity
```

系统存在角色默认应用列表与“语音输入/助手”设置页；它们是否向用户暴露“第三方助手”选项由 MIUI 决定（本机默认项已被小爱占用，实测替换需真机尝试，见第 3 节）。

DeepDeep 当前已完成的基线（本次不改动）：

- Manifest：`RECORD_AUDIO`、TTS/RecognitionService `<queries>`、MainActivity 的 `ACTION_ASSIST` filter、`singleTask`。
- `MainActivity` 收到 `ACTION_ASSIST` → 打开应用内 `VoiceAssistantSheet`；语音面板内有申请默认助手角色的入口（`voice_role`），设置页不再单独显示语音助手行。
- 语音识别/朗读全部走系统 `SpeechRecognizer` / `TextToSpeech`，无后台常听、无悬浮窗。

## 三、替换路径与限制

### 3.1 默认助手角色（公开 API，无需 root）

- 可行做法：Manifest 声明 `ACTION_ASSIST` Activity（或完整 VIDS），用 `RoleManager` 请求角色；用户同意后，系统助手入口（长按 Home/手势/语音键，具体由 ROM 决定）会拉起本 App；本 App 已声明 ASSIST Activity 并在语音面板发起角色请求。
- 拿到角色后：轻量形态只能被系统“拉起一个 Activity”；完整形态还要实现 VIDS + Session，才能拿 assist 数据/截图/会话窗口。
- 限制：角色 UI 是否列出本 App、是否允许第三方、是否被 OEM 锁定都不确定；即使角色被授予，电源键/语音键的键位映射仍可能硬指向小爱（本机 `long_press_power_key=launch_voice_assistant` 是系统设置，不随 role 变化）；MIUI 是否尊重 `Settings.Secure.ASSISTANT` 的第三方值需实测。
- 本机：`ROLE_ASSISTANT` 已被小爱持有；通过公开 API 请求时，系统角色页大概率只显示/保留小爱，第三方无法保证可选。

### 3.2 无障碍按键过滤

- `AccessibilityService` 可用 `FLAG_REQUEST_FILTER_KEY_EVENTS` 进入按键过滤链，做按键转发/手势唤起；也可用无障碍按钮、悬浮按钮打开语音面板。
- 限制：电源长按由系统按键处理 + MIUI 映射消费（本机为 `launch_voice_assistant`），无障碍按键过滤既拿不到“0.5s 长按”这种 OEM 手势语义，也无法改写映射；即便收到 `KEYCODE_POWER` 也只是普通按键事件，不能接管系统长按行为。能做的只有“另一个入口”，不是“替换电源键”。
- 需要用户手动开启无障碍；系统更新/省电策略可能关闭服务。

### 3.3 Shizuku（ADB shell 级，无需解锁 BL）

- 本机 `adb shell`（UID 2000）实际持有 `MANAGE_ROLE_HOLDERS`、`WRITE_SECURE_SETTINGS`、`CHANGE_OVERLAY_PACKAGES`；`cmd role` 支持 `add-role-holder`、`set-bypassing-role-qualification`。
- 因此 Shizuku 路线上有能力：强制赋予/回收 `ROLE_ASSISTANT`、写 `assistant`/`voice_interaction_service`、改 `long_press_power_key` 等系统设置、授予悬浮窗 appop。
- 限制：Shizuku 每次重启需重新通过 ADB/无线调试启动；MIUI 可能对角色/键映射做二次校验，不保证改了设置就按预期生效；普通用户门槛高。
- 本机未安装 Shizuku，上述为权限层面结论，具体命令效果需真机验证。

### 3.4 Root

- 本机已 root：`su -c id` 返回 root，LSPosed/Zygisk 齐备（见 [Root 与高级能力](../archive/ROOT.md)）。
- 能做的极限：改 `/system`（删/换小爱）、改任意 secure/system 设置、给 App 授签名权限、hook 系统按键、装 Magisk/LSPosed 模块把电源键路由到 DeepDeep。
- 限制：解锁/root 风险、OTA 破坏、Play Integrity、售后与安全面；只适合自用开发机，不能作为产品能力。

### 3.5 Xposed / LSPosed

- 可 hook SystemUI/PhoneWindowManager/VoiceInteractionManagerService/AssistUtils 等，精准改“长按电源 → 打开 DeepDeep 语音面板”“替换默认助手路由”；本机已有 LSPosed 2.2.0，具备验证条件。
- 限制：需 root + LSPosed、系统版本强适配、bootloop 风险、用户不可预期；只做实验/自用，不进产品。

### 3.6 OEM / 系统签名

- 唯一能稳定拿到电源键、语音键、唤醒词、默认助手硬绑定的方式；需要预装/合作/系统签名，与独立 App 分发不兼容，不适用。

### 3.7 路径对比

| 路径 | 前提 | 可获得 | 关键限制 | 本机可行性 |
| --- | --- | --- | --- | --- |
| 默认助手角色（公开 API） | 用户同意 + 系统角色页提供本 App | 系统助手入口拉起、可选的 VIDS 完整能力 | OEM 可锁死/隐藏；键位映射不随 role 变 | 角色已被小爱占用，待真机尝试 |
| 悬浮窗 + 前台服务 | 用户授予悬浮窗、通知 | 全局悬浮入口、随时点开语音面板 | 被清理/省电/锁屏限制；麦克风仍受前台服务限制 | 可行（需 MIUI 悬浮窗授权） |
| 通知 / 快捷设置磁贴 | 通知权限；磁贴由用户添加 | 低打扰的快速入口 | 不能常驻逐帧 UI | 可行 |
| 无障碍按键过滤 | 用户开启无障碍 | 额外唤起入口 | 电源手势拿不到；非全局替换 | 可行但不解决电源键 |
| Shizuku（shell） | 用户安装并启动 Shizuku | 改 role/secure settings/appop/键映射 | 重启失效；ROM 是否尊重设置未知 | 权限层面可行（未安装） |
| Root | 解锁 + root | 改系统/签名权限/按键 | 风险高，仅自用 | 已 root，可验证 |
| Xposed/LSPosed | root + LSPosed | hook 系统按键/助手路由 | 版本适配、bootloop，仅实验 | 已具备（LSPosed 2.2.0） |
| OEM/系统签名 | 预装/合作 | 全部系统级能力 | 不适用独立分发 | 不适用 |

## 四、悬浮 UI 方案

### 4.1 TYPE_APPLICATION_OVERLAY + 前台服务

- 悬浮窗类型 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`（API 26+）显示在所有 Activity 之上、状态栏/输入法之下；需要用户授予 `SYSTEM_ALERT_WINDOW` 特殊权限，运行时用 `Settings.canDrawOverlays()` 判断，`ACTION_MANAGE_OVERLAY_PERMISSION` 跳设置。
- 常驻悬浮球必须有前台服务保活（否则进程随时被回收）；Android 14（targetSdk 34）起，所有前台服务都必须声明 `foregroundServiceType`。若悬浮球只做“点开面板”，可用 `specialUse`（现有 `AgentService` 已是 `specialUse`）；若要服务内直接持续录音，必须 `microphone` 类型 + `FOREGROUND_SERVICE_MICROPHONE`。
- 麦克风前台服务限制：Android 14 起，以 while-in-use 权限（麦克风/相机/位置）创建前台服务时，系统要求在调用时确实持有该权限，因此**不能在后台启动麦克风前台服务**；必须先有可见界面（用户点击悬浮球 → 面板可见 → 才开始录音）。这正好匹配现有“点开面板再识别”的交互。
- MIUI 现实约束：除 Android 权限外，MIUI 有自己的“悬浮窗/后台弹出界面/自启动”开关与省电策略，需要引导用户逐项授权；锁屏上普通 overlay 不显示，全屏游戏/视频场景也可能被系统隐藏。

### 4.2 助手 Activity 的启动豁免

- 当前 Android“Activity security / 后台启动 Activity 限制”的例外清单里没有“默认助手”一项；与助手相关的通行做法是：
  - 系统（SystemUI/AssistUtils）发起助手 Activity 启动，属于系统进程启动，不受限制；
  - 普通 App 从后台弹 Activity，依靠“已授予 `SYSTEM_ALERT_WINDOW`”这一例外（悬浮球点击打开聊天/语音面板正是这种情况）。
- 另外，受控的 PendingIntent（通知、磁贴、悬浮球）在 Android 14+ 需要 opt-in 后台启动权限（`ActivityOptions.setPendingIntentBackgroundActivityStartMode` / 发送方显式授权），实现时按官方要求处理，不要假设“有 PendingIntent 就一定能拉起”。
- 结论：悬浮球/通知点开现有面板是合规且稳的；不要依赖“默认助手的后台启动豁免”。

### 4.3 通知气泡 / 快捷设置磁贴 / 通知

- 气泡（Bubbles）：基于 `Notification.BubbleMetadata` 的会话式悬浮 UI，需要通知渠道 + 用户允许；适合聊天消息流，不适合常驻麦克风按钮，且用户可随时关闭、受免打扰/锁屏策略影响。
- 快捷设置磁贴（`TileService`）：用户在编辑面板里手动添加；点击 `startActivityAndCollapse(PendingIntent)` 打开现有语音面板，属于系统 UI 用户交互，启动路径稳定；不能常驻 UI。
- 通知：配合前台服务通知或普通通知 + action 打开面板；`POST_NOTIFICATIONS` 已声明。作为“低打扰入口”成本最低。

### 4.4 VoiceInteractionSession 不是通用悬浮层

- Session 窗口只在 App 被系统绑定为默认助手的 VIS 后、由系统创建；窗口层级、焦点、锁屏行为由系统管理。它不是第三方可自弹的 `TYPE_APPLICATION_OVERLAY`，也不能用来做“全局悬浮麦克风”。
- 想用 Session 的窗口能力，就必须走“完整助手 + 默认助手角色”，等于接受 3.1 的所有不确定性。

### 4.5 后台麦克风 / 唤醒词限制

- Android 11+ 后台麦克风必须前台服务 `microphone` 类型；Android 14 进一步禁止后台启动需要 while-in-use 权限的前台服务（见 4.1）。
- 唤醒词需要系统级热词检测（`HotwordDetectionService`/`AlwaysOnHotwordDetector` 为系统 API，公开 SDK 不可用）+ `CAPTURE_AUDIO_HOTWORD`/`MANAGE_SOUND_TRIGGER` 等签名级权限；本机由 `com.miui.voicetrigger`（priv-app）+ MTK voicecommand 实现。普通 App 无法做到“锁屏常听 + 唤醒词”，也不建议在普通前台服务里长时间录音（耗电/隐私/被系统杀）。
- 可接受的产品形态：用户主动点悬浮球/磁贴/通知 → 面板可见 → 开始识别（现有 W6 流程），面板关闭即停止。

## 五、对 DeepDeep 的结论与下一里程碑建议

现状：应用内语音面板 + `ACTION_ASSIST` 入口已完成（见 [Android 能力](ANDROID_CAPABILITIES.md)），语音识别/TTS 均使用系统服务；本调研中的“系统级”能力均为增量，不改变现有识别/朗读实现。

建议按以下顺序推进，每步都可独立回退：

1. **M1 悬浮入口（推荐先做）**：设置项 + 悬浮球（`SYSTEM_ALERT_WINDOW` + `specialUse` 前台服务）+ 点击打开现有 `VoiceAssistantSheet`；补齐悬浮窗/通知授权的引导与状态显示。不做常驻录音，只做入口。验收：MIUI 上授权后悬浮球存活、点击可打开面板、面板内识别/朗读正常、关闭后停止。
2. **M2 快捷入口**：QS 磁贴 + 前台服务通知 action，均打开现有面板；零新增高风险权限，适合作为 M1 的替代/补充。
3. **M3 默认助手角色（跨 ROM 可选）**：保留现有 `ROLE_ASSISTANT` 角色请求与 `ACTION_ASSIST` 接收；在非 MIUI 设备上验证“设为默认助手后系统入口可拉起本 App”。MIUI 上不承诺可替换小爱，UI 明确提示“部分系统不支持”。
4. **M4 实验（不产品化）**：Shizuku 脚本（强制 role / 改 `assistant` / 改键映射 / 禁用小爱）、LSPosed hook 电源键，仅在开发者设备上验证并记录；不进设置页、不默认开启。
5. **不做**：自建唤醒词、后台常听、替换系统 RecognitionService、第三方 VIDS 常驻助手。它们需要系统签名/OEM 合作，收益与风险不匹配。

风险提示：悬浮窗在 MIUI 上受清理策略影响最大；前台服务类型与后台启动限制随 Android 版本收紧；若未来上架应用商店，悬浮窗/无障碍/麦克风类能力都有额外政策审查。

## 六、参考

平台与 API：

- [RoleManager（ROLE_ASSISTANT）](https://developer.android.com/reference/android/app/role/RoleManager)
- [Intent.ACTION_ASSIST](https://developer.android.com/reference/android/content/Intent#ACTION_ASSIST)
- [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)
- [VoiceInteractionSession](https://developer.android.com/reference/android/service/voice/VoiceInteractionSession)
- [VoiceInteractionSessionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionSessionService)
- [RecognitionService](https://developer.android.com/reference/android/speech/RecognitionService)
- [Settings.Secure（ASSISTANT / VOICE_INTERACTION_SERVICE / VOICE_RECOGNITION_SERVICE）](https://developer.android.com/reference/android/provider/Settings.Secure)
- [AOSP 助手示例（frameworks/base/tests/Assist）](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/tests/Assist/)

悬浮 UI 与限制：

- [Settings.canDrawOverlays](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))
- [TYPE_APPLICATION_OVERLAY](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Activity security（后台启动 Activity 限制与例外）](https://developer.android.com/guide/components/activities/background-starts)
- [前台服务后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [前台服务类型（microphone / specialUse）](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 14 前台服务类型强制要求](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [通知气泡](https://developer.android.com/develop/ui/views/notifications/bubbles)
- [快捷设置磁贴](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- [无障碍按键过滤（FLAG_REQUEST_FILTER_KEY_EVENTS）](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS)

周边路径：

- [Shizuku](https://shizuku.rikka.app/)
- [LSPosed](https://github.com/LSPosed/LSPosed)
- 本仓库：[Root 与高级能力](../archive/ROOT.md)、[Android 能力](ANDROID_CAPABILITIES.md)
