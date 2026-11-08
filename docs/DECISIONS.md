# 设计取舍

这些是**有意为之**的决定，不是缺陷。改代码前按这里判断；要推翻先改本文件并写清迁移代价。

1. **签名**：release 用正式 `release.keystore`（口令在 `keystore.properties`，两者都已加入 `.gitignore`、不进版本库）。2026-11 起首次切换正式证书，旧 debug 证书安装的设备必须卸载重装一次，`allowBackup=false` 下会丢本机会话与凭据；此后沿用同一 keystore，不再更换。
2. **不做二次审批，只分权限档**：个人设备、单人使用，风险自担；系统级能力只有「权限」页三档（普通/无线调试 ADB/Root），ADB 授权走系统无线调试配对，Root 授权交给系统 root 管理器，App 不另做审批流。
3. **单 Activity + 自研 `AnimatedScreenHost` 导航**，不引入 Navigation-Compose；跨层级转场与页面状态保存已按此实现。
4. **`:app` 不拆 `:feature:*`/`:core:*`**：`:runtime-api` 是唯一纯 JVM 契约层；`:app` 只依赖 `:runtime/:shell/:design`，模块边界由 Gradle 任务强制。
5. **本地分发**：不上架；Maven Central 与仓库公开都推迟，等真有外部使用者再说。
6. **会话以 pi JSONL 为唯一权威**：置顶/归档/改名/草稿都是本地元数据，不回写会话正文。
7. **小程序页面与 Agent 分层**：WebView 保留独立 origin 与 document-start bridge；页面不能直接调用任意 App 内部函数。页面发起的 Agent 不设目录隔离或权限白名单，复用当前文件、shell、插件与 MCP 配置；调用方可主动缩小工具和技能集合。运行仍是一次性的，不接入目标续跑、定时提醒或聊天交付卡；页面关闭取消，结束时等待本次并行命令收尾，只清理临时会话元数据，保留工作文件；MCP 凭据使用共享存储，刷新结果不会随临时会话删除。
8. **Root 是可选增强层**：默认普通档；选中特权档且 root 可用时注册 `root_shell`（与无线调试是否可用无关）；授权交给系统 root 管理器，App 不做二次审批；前台、后台、定时、目标续跑等任何轮次都可执行。
9. **不做无实测的性能优化**：声称变快必须给 `gfxinfo` 前后数字。
10. **持久化纪律**：原子写（tmp + rename）、坏文件先备份 `.corrupt` 再重建、带 `schemaVersion`。
11. **许可边界**：自有代码 Apache-2.0；PRoot（GPL-2.0-or-later）、Alpine 包、字体/图标、pi2kotlin SDK（MIT）各自许可；不对外说「整个 APK 是 Apache-2.0」。
12. **会话状态只有一个所有者**：`SessionCommandPlane` 用每会话锁与单调 epoch 管理 busy/stop/retry；只有当前 run 能清除 busy，迟到事件按 runId 隔离。
13. **状态与事件分工**：运行状态以 `activeRuns` 为准；事件保持多订阅者广播，UI 从运行快照与历史对账，结构事件不得被流式 delta 挤出顺序。
14. **能力重配置走安全点**：插件能力变化经 `host.reconfigure()`；忙碌会话在本轮结束的安全点应用，不提前关闭正在使用的会话。
15. **会话实时状态是运行时事件的纯投影**：主聊天页、系统助手浮层、通知与悬浮窗都观察同一个 `ConversationStore`（单订阅 `runtime.events`，纯函数 `reduce` 投影成 `ConversationState`）；`RunStarted` 对用户轮次携带原文 prompt，界面不得假设「这一轮由自己发起」。界面外壳只保留输入、滚动、可见性等本地状态；消息/过程/工具渲染共用同一套组件。
16. **shell 输出契约**：Alpine Linux 与 Android 系统 shell 保留各自运行环境，共享工作文件使用相同真实绝对路径。所有 shell 工具报告真实退出码、超时、取消和上游截断；普通 Linux 命令返回真正的末尾输出，超出 2000 行或 50 KiB 时提供完整日志绝对路径。上游已截断的 ADB/Root 输出不得伪称完整日志。
17. **浮起面允许着色投影与克制的光效**：输入卡与助手浮层是浮起面，共用 12dp 着色投影（暗色补 1dp 发丝描边）；浮层输入卡可叠加蓝色系深海光晕、半透明底与渐变描边，光效随当前轮次状态变化但只做视觉层，不另建运行订阅。回答首字的流光沿四边自下而上，整圈只保留低强度底光。这是用户明确要求的氛围效果，不要按旧纪律删掉；其余平面内容仍保持无阴影/渐变。
18. **助手浮层是入口，不是第二个聊天页**：唤起时底部滑入输入卡（折叠/展开两种形态，见 #52；麦克风点击进入语音录音条，见 #53；工作区入口在面板标题右侧，点开后先选工作区、再选会话；两级紧凑浮层卡，不撑高面板；模型与思考档同在一个模型 sheet）。进入浮层后是否自动开语音/相机由「使用习惯」决定（见 #54），触发在入场动画开始时执行，不先闪一帧文本输入框。发送后顶部滑下会话面板，高度按各消息实测高度实时跟随内容：发送后先长到消息高度，生成中随字符逐行下探，超过半屏后才固定并跟底，空闲时收敛到内容高度（短会话不会先撑满半屏）；键盘打开时面板顶部不动、不隐藏，底部与输入卡之间保留固定间隔并做高度动画（高度动画只在 layout 阶段读动画状态，避免逐帧重组），输入卡单独抬到键盘上方；右上角全屏按钮进 App，不做下拉进 App 手势；点空白/返回关闭浮层。消息、过程、交付与提问复用主界面同一套组件。
19. **轮次生命周期归进程，不归界面**：浮层用应用级 scope 驱动轮次，并作为入口启动前台服务；关闭浮层、从浮层跳进 App 只销毁界面，不取消正在跑的轮次。任何界面切换都不得中断轮次，只有用户主动停止才终止。
20. **发送/插话/排队是运行时语义**：会话空闲时发送即起新轮；忙碌时发送=插话（steer）、长按=排队（follow-up），由运行时统一处理，不与某个界面绑定；队列状态属于共享会话投影，主界面与浮层消费同一份数据。
21. **输入框回车换行，只按按钮发送**：主界面与浮层一致，避免回车误发，多行输入保留换行。
22. **过程行行首转圈 + 本地化耗时取代「思考中」提示**：不再在输入框上方显示思考状态；运行中在过程行最前显示转圈与实时秒数，结束后显示总耗时。
23. **光晕取色**：深色取星空与深海的深蓝，浅色取天空、海浪与浪花的天蓝/海蓝。
24. **光效技术取舍**：优先 Compose 绘制管线；不引入 GLSL、预渲染资源或第三方动画库，除非先证明 UI 线程不是瓶颈；RuntimeShader 只作为 API 33+ 的后续原型。
25. **浮层入场等首帧就绪再起步**：唤起时立即压暗背景并起光效，但输入卡与面板的滑动要等会话加载、面板完成首帧布局后才开始；面板内容列表从底部附近起步，避免先组合顶部再跳到底部。这样入场期间的组合/测量尖峰落在静止背景上，卡片运动本身保持高刷流畅；冷启动的剩余卡顿属于进程/首帧成本，不等于动画卡顿。
26. **语音输入是「录音条」而不是按住说话**：主界面输入卡与浮层麦克风统一为点击进入录音条（见 #53），旧的语音 sheet、长按/按住说话手势与「识别后直接发送」设置全部删除。任何入口收到 `ACTION_ASSIST` 都转到系统助手浮层，包括旧版本遗留在系统里、仍指向 `MainActivity` 的助手组件；不再保留第二套语音界面。

27. **权限分三档，后端可用性优先于档位**：普通档只有 PRoot Alpine 里的 `bash`（主动不使用特权）；选中无线调试（ADB）/Root 后按实际可用后端注册工具：ADB 守护可用 → `adb_shell`（App 内嵌 ShellDaemon，uid 2000），Root 可用 → `root_shell`（`su`）；只按 `effective` 注册一个 shell 工具，两者都可用时不再同时暴露，避免模型选错。`effective` 表示默认/显示档位：选中后端可用就用它，否则回落另一可用后端；选普通档仍不启用特权。ADB 档不可用回落 Root、Root 档不可用回落 ADB；存量 `SHIZUKU` 持久值解析为 ADB。启动时仅 `selected != NORMAL` 才探测 root；选择特权档、权限页刷新、手动检测仍探测；root 为 `Denied`/`Unsupported` 时不自动反复探测，UI 提供手动重新检测。`selected`/`effective`/可用后端变化都走 `host.reconfigure()`。虚拟屏幕可用 = 选了特权档且至少一个后端可用，启动时选中后端优先、另一可用随后。
28. **无线调试不再依赖 Shizuku**：项目默认不加第三方依赖；本轮为自建 ADB 配对与守护破例引入 `com.github.MuntashirAkon:libadb-android:3.1.1`（JitPack，Apache-2.0/BSD-3 双许可）、`org.lsposed.hiddenapibypass:hiddenapibypass:6.1`（配对时经隐藏 Conscrypt 取 TLS exporter）、`org.bouncycastle:bcpkix-jdk15to18:1.86`（自签 ADB 证书）、`com.squareup.okhttp3:okhttp:5.4.0`（本地 HTTP 客户端）。不再有 Shizuku 依赖/provider/代码。首次配对通过系统无线调试配对码 + 通知 RemoteInput；配对成功后 App 自己连接一次 ADB TLS、拉起 `ShellDaemon`（uid 2000）后断开，之后全部走 `127.0.0.1:18242` + token。密钥/证书/token 存 App 私有目录，跨升级保留；配对/守护的完整流程见 32。实测环境：Android 16 无 root 测试机通过无线调试配对、冷启动与虚拟屏；Root 回落由单测覆盖（有 root 手机未连机复测）。

29. **悬浮窗是第三层入口**：小鲸鱼气泡用 `TYPE_APPLICATION_OVERLAY` + `SYSTEM_ALERT_WINDOW`，开启后在 App、助手浮层与其他应用上一律显示；点击气泡打开运行面板、再点不做任何事，面板右上角是进入 App 与 X 收起；面板跟随气泡一起拖动，拖动自由摆放、不吸附边缘，位置持久化；长按开/关虚拟屏小预览：只看画面，可与事件卡或运行面板同屏（顺序 [气泡] → [卡片/面板] → [预览块]，预览在远离气泡一侧、独立圆角块、10dp 间隔），再次长按收起；拖动气泡、点预览外不影响预览；长按时气泡圆周出现蓝色渐变进度环，开启预览顺时针填充、关闭预览逆时针褪色，早于阈值松手则淡出；虚拟屏未运行时不显示画面，只给一块深色短提示 chip，不回退到助手浮层。弹出物（事件卡、运行面板、虚拟屏小预览）按气泡所在半屏选上下：气泡在下半屏弹出物在上、卡片/面板贴圆的顶部，气泡在上半屏弹出物在下、贴圆的底部；不再画三角指针，卡片/面板/预览统一 10dp 常规外边距（与气泡视觉间距约 16dp），和 App 内页面同一圆角卡片风格；事件卡与运行面板整体按 0.75 缩放（布局按原尺寸测量、绘制缩放、上报缩放后尺寸，排版比例与字号等比缩小），浮层占地更少。虚拟屏预览默认按 0.6 倍宽缩略、点按切到当前宽度、再点回缩略，收起预览复位缩略；预览块靠气泡同侧对齐（浮窗在右贴右、在左贴左），不再居中。Agent 调用 virtual_screen 工具启动虚拟屏时自动打开预览并提示可长按小鲸鱼查看/关闭。提问/完成用气泡旁的事件卡（优先级 提问 > 完成，只在 App/助手浮层不在前台时出现，避免与页内提问重复）：卡片持久显示，点卡片外不再消失；提问卡只在回答后消失，完成卡点卡片打开会话并移除该条、右上角小 X 只标记已读（不删面板「已完成」条目、关闭后下一条接着显示）；多个会话同时有待处理时按单卡队列逐条展示：提问按先到先处理在前、完成按新到在前，header 标签后显示「· N 条」剩余总数（N>1 时），处理一条后下一条顶上来；气泡内只内联单选提问，文本/多选跳助手浮层。事件卡与面板复用运行状态单一来源（`ConversationStore.activeRuns`/`runCompletions` + `QuestionCenter.pending` + `present` 工具结果），不另建运行订阅。「已完成」不区分是否有交付：所有已结束会话都列出，有交付用交付卡、无交付用回复文本/状态卡；点击条目或气泡卡打开对应会话并立即移除该条；角标 = 待回答 + 未查看完成数；同一会话重新运行时该条隐藏且不计入角标。入口链路：点击面板「正在运行/已完成」条目直接带 session 进 App 对应会话；面板「待回答」条目与气泡事件卡先开助手浮层，再从浮层全屏进 App（长按不再唤起浮层，悬浮窗没有无 session 的浮层入口）。虚拟屏小预览只在长按时按需订阅 ≤5fps 帧，关闭即停订阅，稳定状态不携带帧；不再有面板内预览区与全屏查看器页面。浮窗几何取 overlay 窗口父区（全屏 bounds 减系统栏 insets），见 #50；总高受气泡到屏边可用空间约束，预览与卡片/面板同屏时空间不足会等比缩小预览、过窄或过挤则丢弃并回退只显示预览。鲸鱼娘四态取自 `assets/ui-kit/whale-flat-v1`（idle/wave/thinking/sleepy），只表达状态，成败仍以工具结果与文字为准。

30. **虚拟屏幕是特权能力**：用 `app_process` helper 在 shell uid 2000 下创建 trusted VirtualDisplay，宽高与 dpi 由 helper 取真实屏幕指标：宽=短边、高=长边、dpi=真实 densityDpi，**始终竖屏**，App 不再下发尺寸；实测 Android 16 上 VirtualDisplay 不继承主屏 rotation（主屏 ROTATION_90 时 VD 仍 rotation=0、1220×2712）。`am start --display` 启动 App，反射 `InputManager` 注入输入、ImageReader 取帧；helper 必须 uid 2000（uid 0 会被系统「包名与 uid 不匹配」校验拒绝），root 路径为 `su -c "su 2000 -c ..."`。IPC 改为 App 建 loopback TCP `ServerSocket`、helper 反向 connect `127.0.0.1:port`（抽象 LocalSocket 在 shell 域被 SELinux 拒绝，实测超时）；hello 携带 uid + 一次性 nonce 双校验。帧流 360 宽 JPEG ≈12fps，截图在无新帧时回退最近一帧缓存。该能力要求选了特权档且无线调试/Root 至少一个实际可用；启动顺序见 27；按屏无障碍读取见 33。已知限制：helper 的 `text` 只支持 ASCII；loopback TCP 仅 token/nonce 防护，理论上本机其他进程可抢连导致一次启动失败（重试即可）。预览帧改为 5fps，帧编码不再每帧复制整幅 RGBA（复用 full-res/preview 两块 Bitmap 做 Canvas 裁剪缩放，单帧只分配 JPEG 字节），截图回退缓存按 ≥1s 节流；TEXT/SCREENSHOT 命令响应由命令线程直写、BINARY 预览帧走容量 2 的队列 + 单写线程，队列满丢帧，避免帧背压把命令响应拖过 App 的 10s 超时；tap/swipe 注入前校验坐标是否在虚拟屏范围内并给出明确错误；HELLO 帧携带 `pid`，ShellDaemon 日志 append 并记录 helper pid，便于断连归因。
31. **ui-kit 鲸鱼娘素材的用途**：`assets/ui-kit/whale-flat-v1` 的 `mascot_idle/wave/thinking/sleepy` 导出为 192px WebP 放 `drawable-nodpi`，只用于悬浮窗气泡的状态表情（运行 thinking、待处理 wave、常态 idle、长时间无事件 sleepy）；不做按钮底色或可拉伸九宫格，UI 组件仍用 Compose 绘制。
32. **内嵌无线调试配对与守护进程**：首次配对由 App 发通知（RemoteInput 6 位码）并打开开发者选项；若系统通知不支持内联输入（如 MIUI 只能整条提交空值），点「配对无线调试」时 App 同时弹出内联输入弹窗，保持系统配对页可见（必要时分屏）后把 6 位码填进通知或 App 弹窗，receiver 用 libadb 完成 TLS 配对并自动点火 `ShellDaemon`。通知权限不再阻塞配对（通知发不出去时仍可用 App 内弹窗）。权限页在已配对/守护运行时仍提供「重新配对无线调试」入口，信任被系统删除后可直接重配，无需重置 App 或杀进程。无线调试连接发现做 8s × 3 次重试，成功后把端口缓存到私有 prefs，下次优先直连 `127.0.0.1:<缓存端口>` 绕开 mDNS（实测 MIUI 的 NsdService 会把 IPv6 链路本地地址解析结果丢掉，导致「配对成功但连不上」）。守护进程用 APK 自身的 `app_process` + `CLASSPATH` 启动，绑定 `127.0.0.1:18242`，用 `X-Token` 鉴权；只保留 `/health`、`/exec`、`/start-helper`、`/kill-helper`，旧的主屏 `/tap`、`/swipe`、`/settings` 已删除，屏幕注入统一走 `input`/helper。daemon 生命周期挂在 adbd 的 cgroup 上：关闭无线调试或重启后 adbd 退出，daemon 随之被系统清理；App 重新打开无线调试后点「连接并启动守护进程」即可重新点火，无需重新配对。App 升级保留 `files/adb` 下的 key/cert 与 prefs 里的 paired/token；force-stop App 不会杀 daemon。token 通过命令行传给 daemon，`/proc` 对 shell/root 可见；HTTP 明文只对 loopback 白名单放行（network_security_config），不暴露到局域网。
33. **双屏 GUI，优先虚拟屏**：按用户最新选择恢复主屏，screen_* 可选 target=virtual|main，省略时默认 virtual；提示模型优先虚拟屏，主屏须显式选择，失败不自动换屏。无障碍按 display 读取节点、节点定位与 Unicode 输入；虚拟屏 helper 提供坐标手势、截图、ASCII 输入与启动应用，交互要求虚拟屏在线，列表不要求在线。主屏无需虚拟屏在线，手势通过无障碍或已授权的 ADB/Root，系统输入显式指定 display 0；无障碍全局按键在另一屏拥有焦点时拒绝，主屏截图使用 Android 11+ 无障碍接口，锁屏或受保护窗口报错。启动应用前检查所在屏幕，拒绝移动另一屏上的任务；无特权时只在虚拟屏确认停止后允许主屏启动，并验证实际落点。ref 绑定所选屏幕，普通操作、再次观察或窗口变化后按节点身份（resourceId、text/desc、class、bounds、序号）自动重定位，不要求重新 observe，只有屏幕重建才失效；observe 默认读面积可比的最大应用窗口，系统气泡/引导弹窗不再顶掉应用节点树，存在多个窗口时结果带 windows 元信息（package/title/type/focused/active/selected/bounds），packageName 可指定读取某个窗口（含系统弹窗包），没有可读应用窗口或指定包窗口时错误里列出可见窗口并提示先 screen_launch；过滤按 AND 组合，text 同时匹配可见文本与 contentDescription，匹配前忽略空白与中英文分隔符（逗号、顿号、点等），"9,801" 可被 "9801" 命中；扫描与返回预算分别计数，截断时不声称穷尽。取消传播、不自动重跑；恢复不迁移文件或历史会话，不恢复旧 gui_* 工具、含糊的活动窗口回落或 daemon 主屏端点。

34. **无障碍自动配置**：`PrivilegeControllerImpl` 的 `AccessibilityBootstrapRunner` 通过**当前生效的特权后端**写入系统设置（effective=ROOT 走 root shell，否则走 ADB daemon），用 `settings put secure enabled_accessibility_services` 合并写入本应用组件（保留其他已启用服务）并置 `accessibility_enabled=1`；触发时机为 App 启动、选择特权档、Root 探测、ADB 连接与 daemon `false→true` 上升沿，幂等、15s 节流、失败退避重试 2 次后静默等下次触发，不做轮询对抗用户手动关闭。此前实现只绑定 ADB daemon 健康状态且 exec 只走 daemon：APK 升级后旧 daemon 仍跑升级前的守护进程代码（`/health` 不带新协议标记），健康检查判假后自动开启就永久不触发；现在只要当前特权后端可用就执行，daemon 不在线也能用 Root 自愈。HyperOS/MIUI 的 force-stop 会经 AMS `onPackagesForceStoppedLocked` 把本包从启用列表摘掉，所以每次冷启动/切档都要重新确保一次；首次安装且两种后端都不就绪时仍可能需要用户手动开启一次无障碍。

35. **虚拟屏连接可靠性**：App→helper 每秒 tick、每 5s 发 PING（负数 id，不占业务命令通道）、15s 无 pong 判定 transport lost；命令响应按 id 关联（pong 隔离、超时 id 记 abandoned、迟到响应丢弃），截图用 epoch 关联并在超时后丢弃迟到帧；`start()` 在 Online 时先做 2s 健康 ping，失败则清理重建（修复半开连接无法恢复）；transport lost 后按 1s/3s/10s 最多自动重连 3 次，`stop()`/手动 start 取消重连，BACKEND_UNAVAILABLE 首次即放弃；重连成功写 `lastTransportNotice`、失败写结构化 `lastFailure(category=TRANSPORT/BACKEND/START, recoverable, reason)`，screen 工具层据此给模型可操作提示；所有失败路径打结构化 transport 日志（event/reason/backend/helperPid/displayId），readLoop EOF 不再静默。

36. **工具说明与暴露粒度**：说明保留正确操作所需的环境、路径、权限、输出截断、取消与恢复契约，不设字符上限或术语黑名单。规则按实际工具和会话能力生成，不无条件要求 present，不规定失败两次必须停；已有授权内直接执行，超出授权的破坏性或不可逆动作再询问。get_goal/create_goal/update_goal、job_start/output/list/kill、schedule_create/list/update/delete 分别暴露，参数保持 camelCase，拒绝不适用参数。screen 手势保持原子，screen_list_apps 与 screen_launch 分开；virtual_screen(action=start|stop|status) 保留。历史工具记录不改写；新调用使用新接口。SDK 固定 0.4.0（0.3.0 补齐图片请求按模型 inputLimits 规范化；0.4.0 增加请求级图片预算：构建请求时超出预算的最旧图片以占位文本省略，会话历史不变，默认最近 20 张 / 20MiB base64）。**跨调用规则只有一个 owner**：每条规则挂在所属工具的 `promptGuidelines` 上（文件工具、bash、系统 shell、屏幕、日历、联网搜索等），运行时按当前实际工具集汇总去重成 `<rules>`，不再在汇总函数里手写工具名 if 链；工具未注册时它的规则随之消失，不会留下过期规则。工具 description 只写单次调用的语义与结果形态，不重复跨调用规则与其它工具的说明。

37. **绝对路径**：文件工具、交付与小程序装载必须显式传真实绝对路径，拒绝相对路径、~ 和省略路径；工作区只注入提示词提醒，不据此补全或限制访问。App 私有数据整棵按真实绝对路径映射进 Linux（files、cache、shared_prefs 均可见；/data/data 与 /data/user/0 前缀等价），共享存储按设置映射，贯通工作文件、截图、技能、小程序、下载和日志，不再支持 /workspace、/gui、/skills 等别名。文件定位结果使用绝对路径，交付必须检查存在、可读且为普通文件；不改写正文、HTML 资源引用、glob 或 shell 命令。无需迁移既有文件或会话。

38. **第三方依赖按需判断**：不再默认禁止第三方库，也不预设「优先用库」或「优先自己写」的倾向；按具体需求评估——现成成熟库能明显省事就用，自己实现更简单就不引入。新增依赖需在本文件记录用途、许可与体积影响；模块依赖图、`settings.gradle.kts`、签名配置、`.gitignore` 仍不经说明不改。本条取代规则页原先的「不加第三方依赖」，既有的 #28 依赖记录保持不变；本轮文件预览（Markdown/HTML/源码/视频）未新增依赖。

39. **插件页面自绘**：插件不注册 Activity，也没有框架提供的连接/配置界面；插件的登录、安装、授权与状态管理都画在插件页面里，页面可以调用宿主服务、把状态写进自己的数据目录或加密存储，动作完成后调 `host.requestContributionsRefresh()` 让宿主重建贡献与会话。需要独立返回栈或全屏的流程由插件在页面内部组织，不新增系统组件。

40. **等待上限与闲置回收**：`ask_user_question` 最多等用户 10 分钟，到点撤销提问、只按普通工具结果回一句超时说明，不追加后续指引；其它工具各自的超时保持不变。虚拟屏在线后由引擎看门狗每 30 秒检查，连续 **15 分钟**没有任何 `screen_*` 调用（target=virtual）就自动停止并释放 helper，省掉常驻的 5fps 预览编码与虚拟显示开销；判定只看 agent 的屏幕操作——启动虚拟屏、悬浮窗长按预览都不刷新计时，预览开着也不会阻止回收。闲置关闭静默执行，不发通知、不写失败状态；App 进程退出时 helper 因连接断开自行退出，不依赖本机制。

41. **文件预览与交付的媒体能力**：文件页面标题进顶栏；预览顶栏右侧放眼睛/R 图标切换 Markdown/HTML 的预览与源码（替代占高度的分段选项卡），另有下载与分享图标，下载把当前文件写入公共 `Download` 目录（`MediaStore.Downloads`，任何格式，重名由系统处理）；图片预览长按弹出底部操作单（保存到下载/分享，关闭用右上角 X 或点空白，不做取消行），视频不做长按。`present` 新增 `video` 类型：交付卡显示视频封面（`MediaMetadataRetriever` 取 1 秒处帧、720 宽上限），点封面进内置播放器；长按保存只做在图片预览上，视频不做。GIF/动画 WebP/APNG 归入图片类，md 内联图、图片交付、文件预览统一用系统 `ImageDecoder` + `AnimatedImageDrawable` 播放动画（失败回退静态首位帧；远程 http 图片仍按静态处理），不为动图引入第三方图片库。

42. **聊天列表不自动跟底**：生成过程中不因新内容改变滚动位置，由用户自己决定何时往下看；只有发送消息、切换会话/恢复滚动锚点、出现待回答提问卡和手动点「回到底部」会移动视口。原实现按消息事件跟底，并在最后一条不可见时用 `scrollToItem` 把最后一条消息的顶部贴到屏幕顶部；流式回合是单个列表条目且高度持续变化（过程区展开/折叠、文本逐字变长），补偿滚动跟不上或手势打断时视角会被拉回回合开头，故整体移除自动跟底（主聊天与系统助手浮层共用同一滚动控制器）。浮层/输入区高度变化引起的补底与手动跳转保留，仍是「仅在底部时」生效。

43. **运行环境安装组合只留结果与详情**：组合行不再写分类副标题和「包含 …」清单，也不在页尾提示「更多需求让 DeepDeep 自行安装」；行内只保留组合名、已装进度/体积与安装按钮。点整行弹出详情弹窗，逐包显示名称、体积与一句用途，底部给合计体积与安装入口（已装组合只读）。这样列表保持克制，需要了解装了什么的用户再展开，不再把开发包清单塞进设置页。
44. **屏幕操作是 App 自带能力**：虚拟屏与主屏由 `screen_*` + `virtual_screen` 工具操作，工具在 `graph/RuntimeGraph` 的 `AppToolContext` 单点装配；设置页一个开关（`files/capabilities.json` 的 `screenOps`，默认关闭）决定这组工具是否进入会话，开关变化走 `host.reconfigure()` 重建会话生效。
45. **屏幕操作按可用性报错，不隐藏入口**：开关打开后工具总是注册，权限档位与后端可用性由工具在调用时报告——`screen_status` 给出两屏真实能力，操作失败返回明确原因，不自动换屏、不静默降级；用户想停用整组能力时直接关设置开关。
46. **初始工具不含 grep/ls/find**：主会话与小程序一次性 Agent 的初始工具都只保留 `read`/`write`/`edit`/`bash`，文件与目录查找统一走 Linux 环境里的 `bash`（`ls`/`rg`/`find` 等命令可用）。SDK 里的工具实现与绝对路径包装源码不改，仅在 `buildTools` 组装时跳过，避免默认工具表冗长和模型在 read/grep 之间重复试探；工具说明与规则由实际注册工具自动汇总，不再单独维护。
47. **应用列表用 manifest 查询修复**：Android 11+ 包可见性让 `queryIntentActivities` 只看到少量应用（此前 12 个），在 `<queries>` 声明 `MAIN`/`LAUNCHER` 后 `PackageManager` 返回全部桌面应用与名称，不再为拿全列表去解析特权 shell 输出；`screen_list_apps`、`screen_launch` 与主屏启动共用同一目录。
48. **悬浮窗订阅与右滑**：运行面板每个运行中会话带一个圆形订阅勾选，单订阅、默认自动追最新发起的会话；用户手动勾选后不被新会话覆盖，再点已勾选的圆点即取消订阅（记为手动选择，运行快照/新会话不会自动订阅回来；运行会话清空后复位，下一次新会话恢复默认追最新）；订阅会话结束后清除并回落最新运行中会话。订阅只在气泡旁事件卡的空闲位显示：优先级低于任何会话的提问与完成卡，只有没有其它卡时才显示，避免追踪信息盖过需要用户处理的事件。订阅卡最多两行：主行显示重试/压缩等异常状态或「工具 · 名称」，其次显示最近一条思考（「思考 · …」）或消息尾行，没思考时主行退化为状态/消息；不再显示「跟踪中」标签、会话名标题与「在浮层查看」说明，整卡点击直接进会话浮层。完成卡与其它事件卡也去掉「在浮层查看」，只有问题卡的「在浮层回答」保留（单选内联时是进入浮层作答的入口）。已完成行右侧有移除图标，点一下直接移除、不必进入会话；点行本身打开会话。
49. **聊天内相机**：`+ → 拍照` 改为应用内 `ModalBottomSheet` 相机（`ui/chat/CameraCaptureSheet.kt`）：CameraX 1.6.2 把 `Preview` 与 `ImageCapture` 同时绑定到当前摄像头，快门写 `cacheDir/camera/<uuid>.jpg` 后经 FileProvider 交给既有附件导入，切换前后镜头与权限拒绝引导都在 Sheet 内完成。Sheet 关闭即 `unbindAll`，拍照回调在关闭后丢弃；不再使用 `ACTION_IMAGE_CAPTURE` 与系统相机 App。`DdSheet` 的 `fullBleed` 模式（默认 false，不影响其他 Sheet）承载相机：取景区 3:4 竖幅铺满 Sheet 宽度（`CameraXViewfinder` EMBEDDED + Crop 只裁切不拉伸），Sheet 只保留内容高度（约 6 成屏高）、顶部用 Sheet 自身的 28dp 圆角与黑色容器收口，画面四角无黑边/留白；关闭、72dp 快门、切换镜头以浮层叠在画面底部（navigationBarsPadding + 120dp 黑渐变保证可读），权限引导页是同尺寸 3:4 黑块，不再有取景区下方的独立控件栏。拍照写入的仍是完整相机原图，取景显示的是画面裁切带。顶部圆角依赖当前系统对 Sheet 窗口内 `SurfaceView` 的裁剪，Android 13/MIUI 实测正常，旧系统 best-effort。新增依赖（记录用途/许可/体积）：`androidx.camera:camera-camera2`、`camera-lifecycle`、`camera-compose` 1.6.2，Apache-2.0；传递引入 camera-core/camera-camera2-pipe/viewfinder 等，AAR 合计约 4.2 MB，debug APK 约 66 MB（比引入前 +5 MB 级）。相机权限 `CAMERA` 运行时申请，`android.hardware.camera` 声明为非必需。首次打开相机在真机上要等系统相机服务初始化（本机约 5–10s 黑屏预热），随后正常取景。

50. **浮窗弹层锚点按窗口父区高度**：overlay 窗口的 `Gravity.TOP/BOTTOM` 都以窗口父区（全屏减去系统栏，本机 1080×2262）为坐标基准。此前 `updateSurfaceWindow` 把 `contentFrame.height` 当父区高度传给 placement：该值由 `context.resources.displayMetrics`（在 per-window 配置下已经是父区高度 2262）再减一次系统栏 inset 得到 2124，而 `viewContentFrame()` 的 `getWindowVisibleDisplayFrame` 结果又因同一比较基准被错误拒绝；结果向上展开（`Gravity.BOTTOM`）的弹层整体下移 138px（=状态栏 94 + 导航栏 44），底边落到气泡圆心、挡住气泡，向下展开不受影响。修复：`metricsContentFrame` 改用 `WindowManager.currentWindowMetrics.bounds` 减 systemBars/cutout insets（=父区 2262）并优先于 view 探测，`viewContentFrame` 仅作为 API<30 回退；单测改为断言卡片底边=气泡顶-gap。该偏差最早由「修正 content frame 坐标」把 placement 的 screenHeight 从 `displayMetrics.heightPixels` 换成 `contentFrame.height` 引入，常驻的订阅追踪卡让它变成高频可见问题。
51. **虚拟屏截图以最近产出的真实帧为准**：helper 每收到一帧都更新内存快照（去掉 1 秒节流），截图返回最近一帧而不是另起一条取帧路径；截图请求前若 3 秒内有坐标/按键/输入，或 6 秒内有启动应用，先等新帧出现（≤0.8s）或等首帧渲染完整（≤3.5s），等待基准取最近一次操作时刻而不是请求时刻。纯透明或未渲染的帧直接报错（提示先 screen_launch），不再把旧的空白图当作成功返回；engine 也不再吞掉 helper 的截图错误，统一抛给工具层转成 isError 结果。代价是全屏透明浮层类界面可能被判为无内容，按用户偏好宁可明确失败也不返回空白图。

52. **输入框折叠/展开三态（主界面与浮层一致）**：输入卡只有三种形态：折叠（52dp 胶囊、水平外边距 26dp、26dp 圆角，单行「+ 附件 / 占位文字 / 麦克风 / 发送圆钮」）、展开（水平外边距 12dp、28dp 圆角，上方输入区与附件/引用/队列、下方控制行「+ / 模型 chip / 麦克风 / [停止] / 发送」）、录音（整卡替换为录音条，见 #53）。折叠态下麦克风与发送都在右侧，麦克风不再有圆形底；展开条件由输入焦点、非空文本、引用/附件/队列/发送中/录音状态派生，没有额外的展开按钮。折叠条点击即展开并请求焦点、弹键盘；清除文本并失焦自动收回。宽度差通过卡片外的动画水平内边距实现（0↔14dp），高度用 AnimatedContent + SizeTransform 平滑过渡（260ms EaseOut），圆角 26↔28 同步动画；不要用 spring 做内边距动画（欠阻尼会过冲成负值崩溃）。两处输入卡共用同一套尺寸与动画参数（`ComposerMetrics`），浮层沿用原有发光卡片底。

53. **连续语音识别：缓冲追加 + 滚动音量波形 + 玻璃预览**：麦克风点击进入录音条，识别会话复用同一个 `SpeechRecognizer`，每段 `onResults` 后自动重启继续听（80ms 级滚动采样的音量来自 `onRmsChanged`）；每段 Final 追加进内存缓冲，界面上方的半透明玻璃小卡实时显示「已确认文字 + 正在识别的半句（淡色）」。录音条按钮语义固定：X=取消并丢弃缓冲；方块=手动停止并把缓冲文本追加到输入框（不是覆盖）；箭头=停止并把缓冲追加后直接发送。停止时先请求最后一段结果并最多等 1.2s 再提交，避免吞掉最后一句话；NO_MATCH/静默超时在录音中静默重启，网络类错误有限重试后报错结束，权限/不可用/忙等硬错误直接结束并保留已识别文本。波形不是固定的中间簇：进入录音后是一排横向小圆点持续从左往右滚动（新采样从左侧进入、向右移出），某个点的胶囊高度与颜色深浅代表那一时刻的音量，静音时保持小圆点滚动，停止等待期间继续滚动并渐隐。旧的按住说话、识别后直接发送、聆听中的转写气泡都已删除。

54. **「使用习惯」决定进入浮层时的行为**：设置首页新增一级入口「使用习惯」，独立页面里只有一个下拉项「进入浮层时的行为」：语音模式（默认）/拍照模式/无。每次进入助手浮层时在入场动画开始处触发一次：语音模式直接进入录音条（不再先显示文本输入框）、拍照模式直接滑出 3:4 相机、无则不自动开任何东西且不弹键盘（输入卡保持折叠）。触发用一次性守卫，同一浮层实例只执行一次，从相机返回不重复触发；从浮层面板进入具体会话时同样按此行为。旧的「识别后直接发送」开关与相关链路一起删除，语音一律手动停止后追加输入框或按箭头发送。

55. **附件来源面板改横排**：聊天与浮层共用的「添加附件」面板从三行列表改为一行三个等宽图标项（上方 52dp 圆角图标容器、下方 12sp 小字），顺序固定为照片、文件、拍照；整项可点，保留 Sheet 头部与关闭。入口仍是输入卡的 +。

56. **应用内相机内置扫码**：相机同时承担拍照与扫码，不新增入口。CameraX 绑定 `Preview + ImageCapture + ImageAnalysis` 三用例；`ImageAnalysis` 每 250ms 最多处理一帧、分析分辨率下限 1280×960，由 ML Kit 内置条码识别（`com.google.mlkit:barcode-scanning:17.3.0`，离线、不依赖 Google Play 服务；许可为 Google ML Kit Terms，非开源；AAR 9.9 MB，arm64 原生库 4.9 MB + 模型 0.9 MB，debug 通用 APK 由 69.9 MB 增至 80.0 MB）。识别到码后取景底部浮出结果卡（app 图标 + 动作 + 内容预览），画面中不再有码 1.8s 后自动收起，点卡才跳转；快门与扫码互不影响，三用例绑定失败的机型静默回落为纯拍照。路由：链接的 host 后缀（或自定义 scheme）命中已安装的 app → 拉起该 app 扫一扫；拉起失败 → 打开该 app 主页；未命中 app 的 http/https → 浏览器打开；非链接内容 → 复制。安全：只把 http/https 交给系统打开，不直接启动任意自定义 scheme；支付/登录在目标 app 内完成，扫码内容不联网、不上传。真机验证过的扫一扫入口：微信 `com.tencent.mm.ui.LauncherUI` + `com.tencent.mm.ui.ShortCutDispatchAction` + `launch_type_scan_qrcode`（即微信给桌面快捷方式用的派发 Intent，非公开接口，微信版本更新可能失效，失败回落打开微信主页）；支付宝 `alipays://platformapi/startapp?appId=10000007`；QQ `mqqapi://qrscan/open?source=1`；美团 `imeituan://www.meituan.com/page/scanQRCodeForMBarResult?openAR=0&shortcuts=true`；京东 `openapp.jdmobile://virtual?params=%7B%22category%22%3A%22jump%22%2C%22des%22%3A%22scan%22%7D`；抖音 `snssdk1128://scan`。表只收真机验证过的入口，未命中的 app/内容不做猜测。已知边界：微信小程序圆码不是标准二维码，通用解码器解不出；各厂商二维码内容形态差异大（URL、自定义 scheme、专有码），当前按 host 后缀 + scheme 匹配，后续按实际遇到的码逐步补表。

57. **扫一扫策略库与多码分卡**：扫码匹配从代码内固定表改为设置层的策略库：内置 `app/src/main/assets/scan/strategies.json`，运行时可被 App 私有文件 `files/scan/strategies.json` 覆盖——设置 → 扫一扫 可添加/编辑/删除策略、复制策略文件路径、恢复内置；重开相机生效，也可让 Agent 直接用文件工具改该文件。匹配只吃内容特征：scheme（100）> host 后缀（90+后缀长度，边界匹配）> 正则（80，忽略大小写）> 内容包含（70）；只匹配本机已安装的 app，同分按 `priority` 再按文件顺序。动作支持 `uri`（scheme 深链）和 `component`（显式 Activity + action + 带类型的 extras + flags）；拉起失败按 `fallbackMain` 打开 app 主页，仍失败则用浏览器打开 http(s)。未命中的非 http(s) 内容给两个选项：「复制内容」和「用其他 app 扫一扫」（已收录且已安装的策略列表）。相机分析返回画面内全部码（按内容去重、面积排序），底部结果卡一码一行最多 4 行，点行执行；码离开画面 1.2 秒后整卡收起，不再用单码大卡。内置默认收录真机验证过的 微信/支付宝/QQ/美团/京东/抖音/飞书 七个入口；微信/QQ 走的是它们给桌面快捷方式的非公开派发 Intent，版本更新可能失效，失效回退打开主页。明确不做 OCR（中文 OCR 的 native 库 arm64 就 11 MB，通用 APK 会到约 123 MB，收益不划算）和按二维码位置渲染标签（需要坐标映射、多码还会遮挡画面）；扫一扫是 App 基础能力，不放进插件层、不新增工具或技能。新增仅测试依赖 `org.json:json`（生产用 Android 自带实现，APK 体积不变）。

58. **扫一扫策略库第二轮**：官方策略与自定义策略拆开——官方来自内置 asset 且只读，自定义只存覆盖文件 `files/scan/strategies.json`，匹配时自定义在前（同分优先）。旧版合并文件在读取时迁移到 schema v2：与官方完全一致的副本丢弃，修改过的官方条目保留为覆盖。设置页分「官方策略 / 自定义策略 / 策略文件」三块：官方条目点开只读详情（字段、打开方式、备注）并可「试一下」直接执行 launch；自定义条目编辑对话框新增「试一下」，拉起成功/失败都有 toast；「恢复官方策略」只删除包名属于官方条目的自定义覆盖，自己添加的新 app 策略保留。新增原始 JSON 编辑页（只编辑自定义策略，保存前校验）与导入（校验、迁移后整体替换自定义）导出（分享 JSON 文件，写入 `cache/share-incoming` 经 FileProvider）；选择已安装应用弹窗显示 app 图标。策略加载改到 IO 线程并做 mtime+size 缓存：外部改文件后，重开相机或重进设置页即生效。异常态：覆盖文件损坏回退官方并在设置页提示，空文件视为无自定义且不报错。官方条目不可编辑，需要改时用自定义 JSON 写同包名覆盖。

59. **定时任务模块改名为 timer**：用户可见中文仍是「定时任务」，英文与内部标识统一 timer——工具 `timer_create|list|update|delete`、路由与深链 `timers`、类/文件/包名与 testTag 全部 Timer，中文文案不改。存储从 `files/schedules/` 迁到 `files/timers/`：AppGraph 初始化时若旧目录存在且新目录为空则复制过去（复制不移动，旧目录保留作备份；文件名 `schedules.json`→`timers.json`、备份文件同步映射），schema 升到 3，v2 的旧顶层 key 与 v1 旧文件仍可读，投递 intents 升到 2 并兼容 `scheduleId`；历史会话里的旧工具名照旧显示，不影响新调用。

60. **中山大学教务作为官方外部插件**：用户自用的纯 Node CLI `jwxt`（零第三方依赖）随插件包分发（约 84KB，去掉真实凭据与开发文档），插件页首次使用时复制到 proot 的 `/root/projects/jwxt` 并软链 `/usr/local/bin/jwxt`；卸载插件删除程序、凭据与 session。运行前提是「运行环境」已装 Node.js 22+ 且开启 Linux，不打包 node、不自动 `apk add`。凭据（NetID/密码）在插件页面填写，写入 rootfs 的 `/root/.config/jwxt/credentials`（0600）；插件自己的登录与安装状态写插件数据目录（App `allowBackup=false`，与 rootfs 凭据同一威胁模型）。登录不读系统通知：`jwxt login --send-code` 发企业微信验证码并把这次 CAS 会话写到 `/root/.config/jwxt/mfa.json`（0600、10 分钟有效），agent 用 `ask_user_question` 取 4 位码后 `jwxt login --code 1234` 在同一会话上提交；恢复会话失败会自动回退完整账密登录。请假 `leave apply` 是唯一写操作且不可撤销：技能要求先 `--dry-run` 预览、再用 `ask_user_question` 确认、确认后才加 `--yes`，附件名只能 ASCII。接口是逆向的，学校改版失效时只说明现象、不反复重试；成绩与个人信息不外发。

61. **新用户引导只在首次安装出现一次**：`OnboardingPreferences` 记 completed/started；只有「没有任何 provider 凭据、没有会话、运行时已就绪」才展示，老用户升级看到凭据或会话就自动标记完成直接进聊天，中途被系统杀掉再启会继续引导；不提供设置里的重看入口。共 6 屏：欢迎 → 账号（头像、称呼、语气、语言）→ 连接模型（推荐 DeepSeek，也可从其余 41 家厂商里选）→ 通知 / 悬浮窗 / 系统助手 → Linux 运行环境 → 权限等级（普通 / 无线调试 ADB / Root）。每屏一个主按钮，可选页给次级「跳过」；切页用 slide+fade，顶部 6 段进度当前段加宽，欢迎页鲸鱼娘轻微浮动，勾选态用 spring。头像走系统相册，EXIF 校正后居中裁剪到 512px 存 `files/profile/avatar.jpg`；聊天空状态有头像时显示 104dp 圆形头像（没有则保留鲸鱼娘），设置 → 个性化可更换或移除。中途切换语言保存后 recreate，靠 rememberSaveable 保留当前步骤与已填内容；启动时的通知权限请求在引导未完成时跳过，改由第 4 屏请求；Linux 安装完成后自动打开「允许助手运行 Linux 命令」，引导结束刷新一次插件可用性，避免新用户马上进插件页看到过期的「未安装 Linux」提示。

62. **头像图片选择后保存失败**：`ProfileAvatarStore.decode` 之前用 `runCatching { ... }.getOrNull()` 判断解码失败，但 `inJustDecodeBounds` 模式解码成功时 `BitmapFactory.decodeStream` 本来就返回 `null`，于是 bounds 解码永远被当成失败、`save` 永远返回 false，从相册选图后头像不更新。改为对 `openInputStream`/解码显式捕获 `IOException`/`SecurityException`，成功后再校验 `outWidth/outHeight`，第二次解码同样只把异常当失败；`ProfileAvatarStore` 增加可选 `baseDirectory`（默认 `filesDir`）便于测试隔离，新增 `ProfileAvatarStoreDeviceTest` 用真实 JPEG 与损坏文件在真机 instrumentation 上覆盖保存、加载与拒绝路径（真机 2 例通过）。

63. **引导页连接模型：OAuth、真实厂商图标与克制的选中态**：厂商图标与 OAuth 登录 UI（`ProviderIcon`、`OAuthLoginSession`、`OAuthLoginSheet`）下沉到 `ui.common`，引导与设置共用。引导第 3 屏所有厂商（推荐行、其他厂商行、选择弹层逐行）显示真实 logo；选中态不再用 `accentSoft` 铺底，只保留右侧对勾（蓝底与白色卡片不协调），按压反馈保留。支持 OAuth 的厂商在引导里直接用浏览器登录（弹层承载浏览器/验证码交互，已登录状态来自 `credentials.authKinds`，可退出），支持 API Key 的保留输入框，两者都支持就都展示，都不支持才提示去设置里连接。登录成功只写凭据，默认模型由第 64 条的机制接管，引导不单独保存模型配置。

64. **模型配置以已配置厂商为准，不再绑死 DeepSeek**：默认模型配置的初始值是 DeepSeek，过去只有聊天里手动切模型才会改写；只配置 OpenCode 时聊天仍按 DeepSeek 判断「未配置」，且模型 chip 在未配置时只会跳设置、选择器打不开，看起来像必须先配 DeepSeek。改为：`ModelSelection.resolve` 在当前默认厂商没有凭据、但存在其他已配置厂商时，按 provider catalog 顺序切到第一个已配置厂商的首个模型；`ModelSelection.ensure` 在 AppGraph 启动时与 `credentials.authKinds` 变化时自动执行（设置/引导刚存完凭据就生效，无需重启），`prepare` 在 `preloadAll` 之后也会 ensure，保证 ChatViewModel 初次读取前已纠正。聊天侧 `ModelController.adoptConfiguredModel` 只对空会话/新会话（无消息、未发送、未切会话）改成纠正后的默认模型并持久化 session + settings；有消息的会话绝不静默改模型。模型 chip 只要有任一已配置厂商就能打开选择器；选择器在当前厂商未配置时隐藏它的模型与思考档、显示「未配置模型」，底部固定「管理厂商」入口；未配置提示行只在完全没有已配置厂商时出现。删除当前厂商凭据会按 catalog 顺序回落到剩余厂商，重新加回不会自动切回。测试：`ModelSelectionTest` 覆盖无凭据、当前已配置、只配其他厂商、catalog 顺序、catalog 外 id；`ChatControllersTest` 追加 adopt 的空会话持久化与有消息会话不动的用例。

65. **字体扩充 Open Runde 与 Fredoka，默认 Fredoka**：新增 Open Runde（3 个静态 CFF OTF：Regular/Medium/Semibold，约 312/324/316KB）与 Fredoka（variable TTF，159KB），均 SIL OFL 1.1，APK 约 +1.06MiB；`DdFontStyle` 默认改为 FREDOKA（试用后用户选 Fredoka 作默认），Open Runde、Lora、Manrope 保留为可选项。`DdCompositeFont` 增加 `variable` 标志：静态字体只 `setWeight`、不做 `wght` variation，变量字体维持 `wght` 轴 + 系统 sans-serif 中文回退；字体页自动列出 4 套，许可弹层与 THIRD_PARTY_NOTICES（含 OFL 全文与 sha256）同步补齐。默认只影响新装/未存过选择的设备，存过其他字体的用户保持原选择；Open Runde 是项目首次引入 CFF 轮廓 OTF，加载失败会静默回退系统 sans-serif（不崩溃），真机字体页已确认渲染与切换正常。

66. **英文模式下的插件/通知文案跟随应用语言**：应用语言此前只在 Activity（MainActivity、助手浮层、小米云登录）与浮层 windowContext 上通过 `attachBaseContext(LanguagePreferences.wrap)` 生效，AppGraph、Service、NotificationCenter 等长生命周期 context 仍跟随系统语言；插件状态/不可用原因、通知、开发包、ADB 配对、分享失败提示都是在这些 context 上用 `getString` 物化的字符串，所以系统中文 + App 英文时这些内容仍显示中文（真机通知栏证据：「后台服务已就绪」）。修复：新增 `LocalizedContext`（`data/LanguagePreferences.kt`），按当前 AppLanguage 动态解析 resources 并缓存；AppGraph 用 `uiContext` 统一提供给插件 context、`AndroidPluginStrings`、`NotificationCenter`、`DevPackageManager`、`ShareStaging`，另接 `AdbPairingNotifier`、`FloatingWindowController` 的 windowContext、`AgentService` 的插件名与虚拟屏引擎；语言切换后调用 `pluginManager.refresh()` 重建插件快照（availability 与页面文案是刷新时物化的），通知渠道名与已发布通知要等进程重启或下一次发布才更新。另把 12 处用户可见硬编码中文资源化（开发包显示名、扫一扫表单 hint、MCP 编辑错误、design 关闭按钮无障碍描述），新增 `AppLanguageTest`。刻意保留中文：插件 skill markdown 的 description/promptBody、runtime 的定时提醒与 MCP 校验错误（纯 JVM 无 res，后续可走错误码映射）、插件外部包管线的底层异常文案（无 context，保持原样）、字体预览的中英双行样张、语言选择器里的「简体中文」原生名。
67. **重配置不重建未落盘会话，引导结束换新会话**：`host.reconfigure()`（启动、插件贡献、特权/Linux 设置变化时触发）会 detach 再重新 open 每个会话；SDK 对尚不存在的 session 文件会生成新的 session id（新会话在首条消息前不落盘），于是 ChatViewModel 仍持有的旧 id 既没有运行时 handle、也不在磁盘会话目录里，首条消息被 `handleFor` 抛出「Agent session is not ready」，且原始英文错误直接显示在中文界面。修复两处：`reconfigureSession` 跳过尚未落盘（无文件或空文件）的会话，避免 id 被换掉；引导完成时新建一个会话替换启动时创建的占位会话，让引擎带着引导后的模型、插件与 Linux 能力创建。真机复现：清空凭据与引导标记启动 → 走完引导 → 首条消息由报错变为正常发起请求（无效 key 得到 401）；新增 `reconfigureKeepsUnpersistedSessionAttached` 单测。

68. **插件模块边界与 SDK 工件**：宿主保留 `:plugin-api`（Android library，对外唯一契约/SDK，依赖 coroutines + kotlinx-serialization-json + Compose BOM）；插件示例、模板与官方插件都在同级 `plugins/` 仓库，按 `mavenLocal` 消费 SDK 并各自打包 `.ddp`。模块边界表改为 `:app → {:runtime,:shell,:design,:plugin-api}`、`:runtime → {:runtime-api}`、`:plugin-api → ∅`。代价：插件工程与宿主分开构建，改契约后要先 `publishToMavenLocal` 再编插件；SDK 版本需与 ABI 一起维护；`:runtime` 不依赖 plugin-api，插件工具在 app 侧转换成 `CodingTool`，避免把 SDK ABI 绑进运行时。

69. **插件契约只服务外部 `.ddp`**：`DeepDeepPlugin` 只有 availability/uninstall/onEnable/onDisable/onUpgrade/skills/promptSections/contributions/contributionsFor/ui/close；贡献面是 tools/skills/promptSections/ui/hooks，服务面是 18 个宿主服务 + `requestContributionsRefresh`。清单声明 `apiLevel`/`minHostVersion`/`ui.apiLevel`，宿主加载时拒绝不兼容；工具 DTO 用 plugin-api 自有类型，不直透 pi SDK。外部插件只能用 `LocalizedText.Literal` 与 asset 图标。安装/连接/设置/状态/动作/详情行等声明式面全部删除，插件自己的状态与界面由插件页面负责。

70. **外部插件包格式、装载与信任**：外部插件是 `.ddp` zip（`plugin.json` + `classes*.dex` + `assets` + 可选 `lib` + `META-INF/plugin-signature.json`），无 AndroidManifest/资源；宿主用 `InMemoryDexClassLoader` 从内存装载（Android 14+ 禁止从可写文件动态加载），失败回退只读文件 `PathClassLoader`。首装必须 Ed25519 签名（ddp-1：对除 `META-INF/` 外全部条目按路径 UTF-8 排序的 `<sha256> <size> <path>\n` 做 Ed25519 签名，`keyId=ed25519:sha256(SPKI DER)`）并在确认页展示发布者指纹、sha256、将新增的能力，确认后记入 TOFU 信任库；更新必须同钥。P0 真机结论：Android 16/HyperOS 上「可写文件不得动态加载」未实际执法，因此装载器默认 `InMemoryDexClassLoader`、文件回退必须写只读副本，不能依赖平台兜底。同进程插件 = App 全部权限、无沙箱、安装即信任；插件崩溃可带崩 App；不提供权限审批/危险操作确认。

71. **插件记录 v4（破坏式）**：外部插件记录在 `files/plugins/plugins.json`（schemaVersion 4：version/installedAt/enabled/skillIds/installPath/activeBundleSha256/size/publisherKeyId/apiLevel/uiApiLevel/lastError/pendingVersion/pendingBundleSha256，含 previous* 回滚字段；相位已改为纯函数派生，不再落盘，见 #77），读到非 v4 时备份 `.v2.bak` 后重建。相位 `INSTALLED/ACTIVE/DEGRADED/FAILED/DISABLED`（`PENDING_RESTART` 由 UI 从 pending 记录派生）表达「用户选择 ≠ 加载成功」。

72. **插件 UI 扩展（页面 + 两类槽位）**：宿主维护槽位目录 `tool.card.content:<toolName>`、`chat.message:<customType>`，插件按 slotId 注册 Compose 绑定、宿主默认渲染兜底；插件页面通过 `PluginUiContributions.pages` 的 `PluginPage(id, title, icon?, content)` 声明并内联内容，是插件唯一的整页界面入口（见 #101）；输入区动作用 `ComposerAction` 贡献，点击打开对应页面。不替换主壳/导航/聊天骨架，不注册 Activity；槽位与页面内容无进程隔离（异常会带崩 App，与工具/回调一致）；崩溃标记已移除（见 #104）。

73. **插件事件语义与安全点**（观察事件现为 21 个、`toolSetChanged` 已随无消费方删除，见 #79）：`PluginHooks` 覆盖 session/run/turn/message/prompt/input/tool-before/tool-after/compaction/model/retry/MCP/总线事件；语义分观察/变换/闸门，闸门 fail-closed；超时 2s（变换/闸门）/5s（观察）；派发按加载+注册顺序（每次分发读当前生效快照；run 级冻结见 #86）；能力变更走 `host.reconfigure()` 安全点提交（复用 #14）；插件抛错只记录，不吞安全闸门错误。接线：`PiSessionEngine` 在会话绑定后把 hooks delegate 交给 `PluginHostManager.bindSessionHooks`（读写 `PluginEventBus`/闸门派发），运行期事件由 `RuntimeEventMapper` 从 `host.events` 转成 `PluginEvent` 派发；SDK `SessionEvent` 通过 `AgentRuntimeHost.sdkEvents` 暴露并映射（ModelChanged/Compaction/Retry/MCP/自定义消息）；runtime 事件路线的重复 fallback 已关闭。

74. **插件分发与自动更新**：外部插件统一索引 `index.json`（条目含 id/version/apiLevel/minHostVersion/url/sha256/size/publisherKeyId），索引 URL 与检查开关存 `files/plugins/sources.json`；更新需用户确认，下载后校验 sha256 + 签名同钥，staged 到新版本目录，下一次进程启动激活，失败回滚上一版本；索引要求 `schemaVersion:1`，索引里的 `publisherKeyId` 只做候选筛选、不会自动信任发布者（信任仍来自 TOFU 库）；插件页「插件源」可编辑索引 URL 与启动自动检查；首装必确认、sha256 必校验；不引入第三方仓库/二进制分发依赖。

75. **官方插件与宿主服务边界**：飞书/企业微信/教务/小米云四个官方插件在 `plugins/` 仓库构建为外部 `.ddp`，以插件页自绘页面完成 CLI 安装、扫码登录与能力开关；宿主不暴露 Provider 凭据/OAuth、ADB 配对流程与内部加密密钥，插件直接用宿主服务或 `host.context`；插件工具默认进入主会话与小程序一次性子 agent（一次性运行按可用工具过滤）。

76. **插件 SDK 依赖记录（#38 扩展）**：显式声明 `org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0`（Apache-2.0，原先随 pi SDK 传递，APK 增量为 0）；`:plugin-api` 发布 AAR 依赖 Compose BOM/ui/foundation/material3 与 coroutines（均已有）；新增 `maven-publish` 仅用于构建插件 SDK，不改变 App 运行时依赖。

77. **插件单一事实源**：用户意图（`enabled`/`installedVersion`/`pending*`/`previous*`/`lastError`）只写持久化 record（`files/plugins/plugins.json` v4）；运行期事实（插件实例/host/任务 scope/会话绑定/物化贡献/failed/degraded）只在内存 `ManagedPlugin`；相位、UI 状态、注册表快照全部由这两类事实纯函数派生，不存第二份。状态变更只经 `PluginRuntimeRegistry` 状态闸门，写盘由流程负责且先写盘再过闸门。代价：消费方（工具装配、提示词、技能同步、槽位、冲突诊断、插件页）不能再自己调用插件取贡献，只能读快照；刷新路径必须完成一次物化。

78. **每插件独立任务作用域**：每个插件一个 `SupervisorJob` + `CoroutineScope`（即 `PluginHost.scope`）；停用/卸载/更新/进程退出统一走「取消 scope → `close()`（5s 超时）→ 释放安装句柄 → 移除注册/清贡献 → 同步技能」，任一步异常记 `lastError` 但收尾继续。重新启用会创建新的 scope 与新的 host，插件不得缓存 host/scope；插件页行尾菜单提供重试激活/回滚上一版本入口（见 #80），回滚会先停用收尾再切回旧版本重建。代价：插件后台协程必须挂在 `host.scope` 上，跨插件共享的后台任务只能走 `jobs()`/`timers()` 等宿主服务，否则会在停用时被一起取消。

79. **唯一 hook 管线与每会话事件泵**：所有插件 hooks 汇入同一条管线。4 个变换/闸门按插件注册顺序串行执行、单个 handler 2s 超时，`promptTransform`/`inputTransform`/`afterToolExecution` 失败 fail-open（保留上一个值），`beforeToolCall` fail-closed（按 `Block` 阻止调用）；21 个观察事件按会话单泵串行派发、单个 handler 5s 超时，结构事件优先于流式 delta，delta 过载丢最旧。工具钩子挂在 Agent 级执行点而不是只包初始工具，因此 MCP/tool_search 等动态工具同样经过 before/after。代价：hooks 在会话关键路径上必须快速返回，重活由插件自己异步；派发顺序由宿主决定，插件不能插入或跳过其它插件。

80. **外部插件激活协议**：pending 版本不在安装时生效，重启后按准备（验包/装载/实例化）→ 校验（注入服务、`onUpgrade(fromVersion)`、贡献/技能/UI 预生成、`availability`）→ 提交（写 record，旧版本进 `previous*`）→ 换装（替换句柄、关闭旧实例）执行；准备/校验失败丢弃 pending、删除坏版本目录并保留旧版本可用，提交写盘失败保留 pending 与 `lastError`（下次启动重试），换装后 `onEnable` 抛错时新版本已提交、记 `lastError` 并转 `FAILED`，激活中断写 marker 供下次启动跳过并恢复，同一版本连续启动失败 2 次自动 `enabled=false`。插件页在 `FAILED`/`DEGRADED` 且 `lastError` 非空时显示错误文本与恢复入口：重试激活重跑本协议（清 marker/启动计数后重新装载；有待生效版本时完整重跑），记录有 `previousVersion` 时提供「回滚到上一版本」（切回旧版本并清空 pending/previous）。代价：更新必须重启进程才生效，且新版本校验失败一次就会丢弃暂存包（需要重新下载/导入）。

81. **预发布撤回，不留兼容层**：按「发布前没有用户」的规则，本轮直接删除声明式 UI 全套（`UiNode`/`UiNodeTypes`/`Declarative`/`DeclarativeUiRenderer`）、旧 `ExternalPluginLoader` 直载器与 spike 测试、`PluginSkill.legacyOwned`、`PluginManifest.requires/order`、`Provides.events`、`ComposerActionState`、`PluginValidation.isCompatible`/`validateUiNode`、`PluginHost.log(level: Int)` 等无用或未接线 API；旧插件不迁移、不保留回退分支。代价：第三方老插件必须按新 SDK 重编译并改成 Compose 槽位，没有过渡期。

82. **一次物化**：每次刷新对每个插件最多调用一次贡献方法：`availability()`/`skills()` 总是调用，启用且已安装时再调用 `contributions()`/`ui()`/`promptSections()`；`contributionsFor()` 每个会话一次（工具与会话 hooks 共用这次物化，见 #86）。结果存入运行期事实，工具装配、`promptBody`、技能同步、槽位渲染、冲突诊断、UI 快照只读这份结果。代价：贡献必须可重复快速计算，不能在方法里做重 IO 或依赖必须实时刷新的外部状态。

83. **结构化工具载荷**：工具调用在全链路保留结构化数据——`PluginToolCall.args` 与 `ToolCardSlotState.args`/`result` 是 `JsonObject`/`PluginToolResult`，runtime-api 的 `ToolTrace`/`ToolStarted`/`ToolFinished` 携带 `rawArguments` 与 `ToolResultPayload`（文本/图片/FileRef 块），不再把参数与结果拍平成字符串。工具卡与自定义消息卡因此能读取真实参数与结果；图片块进模型时转 base64 `ImageContent`；`FileRef` 的结构化与文本回退见 #87。代价：历史消息映射与 UI 消费方都要按结构化载荷解析，不能再假设 `details` 是字符串。

84. **插件错误可见**：所有失败路径都留痕且可达用户：装载/激活/启用的 `Throwable` 记入 record 的 `lastError` 并显示在插件页行内错误文本；单插件失败只影响自己（`CancellationException` 重抛，其它捕获后继续启动其它插件）；插件日志统一走 `host.logs()`（`LogService`，logcat tag `DeepDeepPlugin/<id>`）；Compose 槽渲染异常不做兜底（会带崩 App）；外部包安装/更新失败保留旧版本并显示原因。代价：错误不再静默，部分原本「跳过继续」的路径会显式标 `FAILED`/`DEGRADED`。

85. **插件 UI 单轨：只保留自由 Compose + `:design` 组件**：插件 UI 扩展只有 `SlotContent.Compose(render)`（工具卡/自定义消息两类槽位）与 `PluginPage.content`（整页界面），不提供声明式 UI 节点。UI 兼容按清单 `ui.apiLevel`/`ui.kotlin`/`ui.compose` 判定，不匹配只禁用该插件的 UI（`pagesOf` 空、槽位不渲染）并显示原因，工具/技能/hooks 照常；组件与 App 同源，来自按同版本发布的 `design` 模块的 Dd 组件（见 #100），不再有 `plugin-api` 自带的最小组件库，也不建议直接使用 Material3 默认控件。代价：插件与宿主 Kotlin/Compose 版本强绑，宿主升级 Compose 后插件必须重编译；跨版本稳定的声明式 UI 不提供。

86. **会话级 hooks 与 run 级冻结**：`contributionsFor(host, session)` 返回的 `Contributions` 是该会话生效的工具 + hooks，会话 hooks 按 pluginId 覆盖该插件的全局 hooks；默认实现委托 `contributions`，只在 `contributionsFor` 里加工具（`contributions(host).copy(tools = ...)`）时保留全局 hooks。会话贡献返回默认 `PluginHooks.None`（未显式提供 hooks）时沿用全局 hooks，避免写了 `contributionsFor` 后 hooks 静默失效。同一 `SessionBinding` 从建立到关闭只调用一次 `contributionsFor`，工具与会话 hooks 共用这次物化；插件能力变化经 `host.reconfigure()` 在安全点重建会话（新的 `SessionBinding`）后才重新物化，当前 run 内不变。每轮 run 开始时冻结当时生效的 hooks 集合（观察事件与变换/闸门共用）：本轮内启停插件、刷新贡献、新增插件都不影响冻结集合——已冻结的插件即使被停用也继续收到本轮剩余事件，新启用插件从下一轮开始生效；`RunFinished`（失败 run 同样以 `RunFinished(error)` 到达）派发完成后解冻，下一轮按最新快照重新冻结。实现为 per-session `SessionHookSource`（registry 快照 + 会话覆盖 + frozen 槽）；冻结集合为空时事件泵会直接丢弃事件，因此 run 结束还有一条兜底解冻路径，不能只依赖派发回调。前提：同一会话同一时刻只有一个活跃 run（run 生命周期串行）；若将来允许并发 run，需要按 runId 多槽冻结。代价：会话 hooks 只物化一次，插件不能靠运行中刷新改变当前会话的 hooks；停用后本轮仍可能收到事件，回调需自行判断插件状态。

87. **FileRef 结构化**：`ToolBlock.FileRef` 不再只拍成一行文本。content 仍保留 `file: <path> (<mime>)` 供模型读取（模型只看到文本），宿主同时把 `{path, mimeType}` 写进 `AgentToolResult.details` 的保留键 `deepdeep.fileRefs`（编解码在 runtime-api 的 `ToolResultDetails.encodeFileRefs/decodeFileRefs`，结构化载荷类型为 `ToolResultPayload.Block.FileRef`）；工具卡与历史消息从这个保留键还原文件行，点文件行经 `onFileClick` 打开。历史 JSONL 原样保存 `details`，重开会话后文件行仍可还原；保留键由宿主读写，插件不要占用。代价：同一信息在 content 与 details 各存一份（模型读文本、UI 读结构，二者必须由宿主一起生成）；`afterToolExecution` 的 `Replace` 若丢掉 details，文件行会随之消失。

88. **装配子图（PluginGraph）**：`AppGraph` 只保留懒加载入口，插件相关的依赖构造与接线（`PluginInstaller`/`ExternalPluginRuntime`/`ExternalUpdateSupport`/`PluginHostManager`/`PluginServiceDependencies`）收进 `app/src/main/kotlin/io/github/whywood/deepdeep/graph/PluginGraph.kt`。依赖通过 `PluginGraphDeps` 显式注入：`graph/` 包不 import `AppGraph`，`AppGraph` 的依赖属性保持 `private`，子图可用测试替身单独构造；会在构造期产生副作用的懒加载依赖（`miniappAgentService`/`jobRegistry`/`timerService`/`goalService`/`virtualScreenFeature`/`mcpPluginBridge`/`pluginScreenController`/`pluginMainScreenController`）以 `() -> T` 传入，保持原来的懒加载时机。约定：新增 `XxxGraph` 时同样用显式依赖类注入，`AppGraph` 只留 `private val xxxGraph by lazy { ... }` 与对外 getter。代价：装配代码跨文件，改插件装配要同时改 `PluginGraphDeps` 与 `AppGraph` 的构造；`AppGraph` 从 656 行降到约 557 行（deps 构造在 `AppGraph` 侧，约 34 行），`graph/` 目录另有 AGENTS.md 维护索引。

89. **进程启动唯一入口（ProcessRuntime）**：插件启动、`prepare()`、`host.reconfigure()`、`host.ensureStarted()` 从前分散在 `AppGraph.init`、`MainActivity`、`AgentService`、浮层 Activity，顺序与幂等靠调用点自觉。现收敛到 `service/ProcessRuntime`：Application/Activity/浮层/前台服务都调 `start()`，内部用闸门保证并发只跑一次、失败置 `FAILED` 并从诊断日志留痕（`files/diagnostics/errors.log`，由 `service/Diagnostics` 统一写入）、下次调用可重试；插件启动失败只记日志不阻断主进程其余初始化。前台切换统一走 `onForeground()`（插件前台通知 + 刷新 + 技能同步）。`ProcessRuntime` 以 lambda 接收各步骤，单测可直接验证顺序/并发/失败重试，不依赖真实插件与 runtime host。代价：新增一层编排，调用点不能再自行决定初始化步骤；`PluginHostManager.start()` 重复调用不再重复注册/启用插件，但仍会激活待生效版本（更新只在启动安全点生效）。

90. **Compose 根拆分（AppShell / AppOnboardingGate）**：`DeepDeepApp` 只保留 ChatViewModel/会话状态采集、引导门控与参数转发（约 65 行）；引导判定收进 `ui/shell/AppOnboardingGate`（`rememberSaveable` 决策 + 首启 `resolveOnboardingGate`，未进聊天前渲染引导/启动屏），主壳（导航、`AnimatedScreenHost`、聊天 base）收进 `ui/shell/AppShell`，全局叠层/抽屉与叠层状态收进 `ui/shell/AppOverlays`（`AppOverlaysState` + `rememberSaveable` Saver），通过 `content` 槽由门控决定何时组合。代价：聊天相关局部状态（抽屉、sheet、导航）从首帧延迟到引导完成后创建；根不再直接持有 UI 状态，后续改壳层改 `AppShell`。

## 91. 页面路由拆分

`AppRouteContent` 原来有 28+ 个参数，所有页面都收整个 `ChatUiState`，新增页面要改巨型签名，非聊天页也被迫依赖聊天状态。

现在 `AppRouteContent(destination, AppRouteHost)` 只做分发：`AppRouteHost` 携带导航、`AppDependencies`、聊天回调与 `ChatRouteData`（会话 id/工作区/归档）以及 `ShellRouteUi`（主题/字体/昵称/头像/模型）；具体装配下沉到 `ui/navigation/routes/` 的按功能 composable，每个 route 只取自己需要的字段与依赖。新增页面只改 `AppDestination`、新的 route 文件与 `AppRouteContent` 的一行。

代价是多了一层 route 文件与装配上下文类型；换来的收益是页面参数收窄、非聊天页不再依赖 `ChatUiState`、新增页面不再改动共享壳层签名。

## 92. 共享聊天 UI 归入 ui.common.chat

浮层与悬浮窗需要复用聊天回合、Markdown、工具卡、交付卡、提问卡、插件槽位、输入卡、附件与相机扫码等组件，这些以前都在 `ui/chat`，导致 `assistant`、`floating` 反向依赖聊天 feature，`ui` 又依赖 `assistant`（助手模型类），依赖方向混乱。

现在共享渲染组件集中在 `ui/common/chat/`（`state/` 放草稿与附件导入），调用方只观察 `ConversationStore` 并把投影传进来。`ui.common.chat` 不 import 任何 `ui.<feature>`：feature 会用到的小卡片（提问卡、已答卡、小程序卡、插件槽位）也随之下沉到 `common/chat`，被 feature 页面反过来引用（target 是 `common`，边界检查允许）。`AssistantRole`/`AssistantEntry` 移到 `core/`。

`verifyPackageBoundaries` 增加两条规则：`ui.*` 不得 import `assistant`；`assistant`/`floating` 不得 import `ui.chat`。`gradle/package-boundaries.allow` 从 14 条降到 4 条（仅剩 ChatScreen→GoalBar、搜索命中、SettingsGroup 等历史耦合）。

## 93. 运行时内核拆分

`AgentRuntimeHost` 原来 1186 行，把句柄池、SDK 事件翻译、工作区、模型、会话查询、运行编排与事件投影全塞在一个类里，改一处要读整段。

现在按职责拆成：`RuntimeHandle`（单会话句柄）、`RuntimeMcp`、`RuntimeEventTranslator`（SDK 事件 → 运行时事件）、`RuntimeWorkspaces`、`RuntimeModels`、`RuntimeSessions`、`RuntimePool`（句柄池、打开/关闭/重载/淘汰/重配）、`RuntimeRuns`（发送、排队、重试、提醒、后台回合、停止、终态与事件投影）；`AgentRuntimeHost` 只保留装配、事件流入口与接口委托，297 行。`RuntimeRuns` 通过函数参数拿 `handleFor`/`sweepIdleHandles`/`reconfigureSession`，避免与 `RuntimePool` 构造循环。

事件可靠性不变：`emit` 先 `tryEmit`；缓冲满时 delta 丢弃并记日志，其它事件（含 RunFinished/RunFailed/SessionListChanged）改由后台协程 `emit` 等待投递，终态不会被静默吞掉。`runStartedPrecedesDeltasAndStructureSurvivesFlood` 扩展为断言终态恰好一次、状态 DONE 且在全部 delta 之后。

## 94. 统一命名为小程序（miniapp）

原「创作类轻应用」功能在产品里已经是一个独立入口：用户从抽屉打开一个由 Agent 写的 HTML 轻应用，像 App 一样长期使用。这一轮把它统一命名为「小程序 / miniapp」：工具 `miniapp_create`/`miniapp_load`/`miniapp_list`/`miniapp_delete`/`miniapp_edit`、路由与深链 `miniapp`、随包技能 `miniapp`、存储目录 `files/miniapp`、`present` 交付类型 `miniapp`、插件宿主服务 `miniapp()`，界面文案、空态与聊天交付卡同步。

行为不变：每个小程序仍是独立 origin 的 WebView 页面，页面经 bridge 读写按 id 隔离的数据，并可用 `app.agent.*` 编排一次性 Agent。

代价：旧工具名、旧存储目录与旧深链不保留兼容；插件里调用旧宿主服务的代码需改为 `miniapp()` 并重新编译。

## 95. App 自带工具与插件分离

web/目标/后台任务/定时/小程序/安排/屏幕这组能力以前是随包编译的插件，要经过插件装载、贡献物化、启停与设置链路。现在它们全部改为 App 自带工具，在 `graph/RuntimeGraph` 的 `AppToolContext` 单点装配：`builtInTools()` 按当前权限后端、屏幕开关与小程序存储直接产出 `CodingTool`，随包技能（`deepdeep-gui`/`deepdeep-arrange`/`miniapp`）在进程启动时先同步；设置页不再有它们的插件开关。

代价：这组工具的启用条件从「插件记录」变成装配时的显式条件（权限后端、能力开关、运行环境），改装配只影响 `RuntimeGraph`，不再经过插件状态机；它们的不可用原因改由工具结果和对应设置页表达，不再有插件可用性字段。

## 96. 插件系统只服务外部 `.ddp`

插件契约收敛到外部 `.ddp` 单一来源：删除随包编译的插件层与 `source` 概念，删除安装/连接/设置/状态/动作/详情行等声明式面（连同面向模型的连接工具），`plugin-api` 升到 `0.5.0` / apiLevel 2。`DeepDeepPlugin` 只保留 availability/uninstall/onEnable/onDisable/onUpgrade/skills/promptSections/contributions/contributionsFor/ui/close；插件 UI 只有自由 Compose，插件页面是唯一整页界面，插件列表每行只有启用开关 + 行尾菜单。`plugin.json` 同时去掉 `source`/`installable`/`tintIcon`/`sizeLabel`，图标只用 asset。

代价：老插件必须按新 SDK 重编译并改写成页面自绘；插件的登录、CLI 安装与状态管理全部由插件自己实现（宿主不再提供安装/连接流程），插件改完能力后要主动调 `host.requestContributionsRefresh()`。

## 97. 屏幕操作由一个能力开关控制

屏幕工具不再挂在插件上，改由设置页一个开关控制（`files/capabilities.json` 的 `screenOps`，默认关闭）。开关只决定 `screen_*` 与 `virtual_screen` 是否进入会话，真正的可用性由权限档位与后端状态在调用时报告；开关变化经 `host.reconfigure()` 重建会话生效。这样「读屏」是明确的用户授权项，不会被插件开关或旧记录状态间接打开。

代价：不再有独立的虚拟屏启停入口，虚拟屏由 Agent 调 `virtual_screen(action=start)` 启动，15 分钟没有 `screen_*` 操作自动关闭。

## 98. app / plugins / website 仓库拆分

宿主仓库只保留 App 本体与插件契约：官方插件、示例、模板与共享打包脚本迁到同级 `plugins/` 仓库，各自独立构建签名与分发；官网在 `website/` 仓库独立维护。宿主保留 `plugin-api` 与真机 e2e 脚本（`scripts/plugin-e2e.sh` 跑 `sample`，`scripts/plugin-bundles-e2e.sh` 跑四个官方插件包）。

代价：改 `plugin-api` 后要先 `publishToMavenLocal` 再编插件；插件构建与本机签名密钥不再跟宿主一起版本化，官方插件发版与宿主发版解耦。

## 99. plugin-api 留在宿主仓库

`plugin-api` 是宿主加载器的 ABI 契约，版本、apiLevel、Compose/Kotlin 版本与宿主强绑，校验与 ABI 基线（`plugin-api/abi/`、`scripts/plugin-api-abi.sh`）也依赖宿主构建；因此它继续放在宿主仓库作为唯一契约源，通过 mavenLocal 发布给 `plugins/` 仓库消费，而不是独立仓库或独立发布渠道。

代价：插件作者要先拿到与宿主匹配的 SDK 版本；发布 SDK 需要宿主仓库可构建（或已发布到 mavenLocal 的 AAR）。

## 100. design 作为插件 UI 的 SDK

`:design` 与 `plugin-api` 最初以同一 `pluginApiVersion` 发布到 mavenLocal（`io.github.whywood.deepdeep:design`）；自 `plugin-api` 0.7.0 起两者版本号独立（`plugin-api` 0.7.0 / `design` 0.6.0）。`plugin-api` 用 `api(project(":design"))` 把设计系统传递给插件；插件仍然 `compileOnly` 不打包，运行期由宿主 parent classloader 解析同一份类。插件页面直接用 `DdGroupCard`/`DdListRow`/`DdPrimaryButton`/`DdTextField`/`DdSwitch`/`DdType`/`deepDeepColors()`，与 App 完全同源。原 `plugin-api` 自带的 `ui/PluginComponents.kt` 最小工具盒（`PluginCard`/`PluginButton`/`PluginTheme` 等）删除。

代价：公共 Dd 组件的签名与视觉成为跨仓库 API，改组件要同步 `plugin-api` ABI、`docs/PLUGIN_DEV.md` 并重编 `plugins/`；插件作者不能再只靠 `plugin-api` 拿到 UI 组件，需要能解析 `design` 制品（由 `api()` 传递，通常无感）。

## 101. 插件页面内联内容，取消 plugin.page 槽位

`PluginPage(id, title, icon?, content)` 直接携带 `@Composable () -> Unit` 页面内容，宿主 `PluginPageHost` 渲染；删除 `plugin.page:<pageId>` 槽位、`SlotIds.page` 与对应绑定。一个页面只属于一个插件、一份内容，槽位机制在这里只产生重复声明与分散的兼容判定。

代价：页面不再经过 `SlotState`，需要宿主信息时在 composable 闭包里捕获 `host`；页面渲染异常不做兜底（Compose 无错误边界），插件需自测。

## 102. 去掉 SlotContent.Compose.uiApiLevel

UI 兼容性统一由 manifest `ui` 块（`ui.apiLevel`/`ui.kotlin`/`ui.compose`）判定；绑定级再写 `uiApiLevel` 是重复信息，而且逐绑定禁用会造成同一插件只有一半 UI 可用。

代价：不能再为单个槽位声明低于插件的 UI 等级；UI 不兼容时整个插件的页面与槽位一起禁用，工具/技能/hooks 不受影响。插件需删除该参数重编译。

## 103. 主题补 surfaceContainerHighest 映射

Material3 `Card` 的默认容器色是 `surfaceContainerHighest`，而 `DeepDeepTheme.materialScheme()` 之前没有映射这个角色，导致任何 Material3 `Card`（尤其是插件页面）落回默认紫色 `#E6E0E9`，与 App 白底卡片完全脱节。明暗两套 scheme 都补 `surfaceContainerHighest = raised`。

代价：App 内所有 Material3 `Card` 底色统一到 `raised`；当前没有界面依赖默认紫，属纯修复。

## 104. 删除槽位崩溃标记

原 `PluginFailureGuard` 想实现「插件 Compose 渲染崩溃后下次自动回退」：绑定渲染进入组合时写 `files/plugins/slots/guard_*.json`，正常 dispose 时清除。但 Compose 组合抛异常时 `DisposableEffect` 根本不会执行，标记只能在「页面打开期间进程死亡/被 force-stop」时残留——那不是渲染崩溃，却被当成崩溃禁用页面（用户看到「该插件没有提供这个页面的内容」）。Compose 也不支持错误边界：在宿主 `try/catch` 里捕获组合异常会让后续组合处于半提交状态并再次崩溃，不能作为兜底。

删除 `PluginFailureGuard`、`PluginManagerFacade.slotFailureGuard()`、各处 `failureGuard` 接线与相关测试；槽位与页面渲染直接抛出，与工具/回调异常一致（带崩 App）。

代价：插件 UI 代码异常不再有任何「下次禁用」保护，插件作者必须自测；宿主也不会因残留标记而误禁页面。


## 105. 单会话导出替代会话备份

全量 zip 备份需要用户先想清楚「备份到哪、什么时候恢复」，而实际需求多是「把这段会话带走/存一份」。改为聊天顶栏「导出会话」：按当前会话直接写 JSONL（pi 的 session-export 同思路），不再提供导入、Markdown 与全量备份页。

代价：没有「一次备份全部会话」的入口；换机需要逐个导出。`android:allowBackup="false"` 下方可依赖系统备份。

## 106. 按需工具：统一 tool_search + tool_execute

MCP 的 deferred 工具原本由 SDK 的 `tool_search` 搜索后 `addTools` 全量加载进会话；插件工具则全量常驻。现在统一为：所有 `DEFERRED` 工具（MCP + 插件）只进入宿主 catalog，模型先 `tool_search` 找名字/参数，再 `tool_execute(name, arguments)` 按名执行，不把工具加载进会话。MCP 侧通过 pi2kotlin 新增的 `McpManager.callDeferredTool` 按名执行；插件侧由 `PluginTool.exposure = DEFERRED` 声明。

代价：工具调用多一轮；`tool_search` 的返回 schema 也占 token；`autoEnableToolSearch` 关闭后旧 MCP 行为改变。插件作者必须自行决定哪些工具折叠，折叠工具不能依赖常驻提示里的 `promptSnippet`。

## 107. pi2kotlin 随宿主发版

`tool_execute` 需要 MCP 连接层的按名执行与队列按条撤回，这些 API 不在 pi SDK 0.4.0 的公开面里；在 `pi2kotlin` 仓库补 `McpManager.callDeferredTool`、`McpToolAdapter.execute`、`AgentSession.removeQueuedInput` 后发 0.5.0（m2 zip + GitHub release + sha256），app 升级依赖。

代价：宿主与 SDK 强绑定，升级 SDK 要先发测试版；release 网络失败时需用 `-PpiSdkLocalZip` 本地兜底。

## 108. Agent 设置页承接屏幕操作

屏幕操作开关从权限页移到 Agent 设置页，与「权限级别」入口并列；权限页只保留权限档位与 ADB/Root 操作，不再重复放开关。

代价：权限页不再能一眼看到屏幕操作状态；Agent 页需要在开关未生效时给出「缺 ADB/Root」提示。

## 109. 运行中发送模式与排队缓冲

聊天设置新增「运行中发送：插话/排队」。排队时输入区展示缓冲区行（文本 + 撤回 + 直接发送），不再有长按/菜单与「运行中：发送=插话，菜单=排队」提示；停止按钮改为实心方块。

代价：长按快捷入口删除，切换模式需要进设置；直接发送会跳过队列顺序。

## 110. 自动跟底改为按帧驱动

原来的 `snapshotFlow(followContentSignal)` 只观察最后一条消息的少量字段，过程/工具高度增长与流式输出不在信号里；改为发送期间每帧调用 `followContent()`，只要用户仍在底部且最后一项可见就直接 `scrollBy` 贴底。

代价：发送期间每帧一次轻量计算；用户上滑后仍按 80dp 阈值重新吸附。


## 111. 过程区运行态只显示当前活动

过程区运行中不再显示「执行中 · N 个思考 · N 个工具 · 耗时」，而是按当前活动显示「正在深度思考 / 正在调用工具 / 正在回复」；结束、停止或失败后才变成「已完成/已停止/失败 · 思考数 · 工具数 · 耗时」。同时取消连续工具折叠成「N 个工具」组，每个工具卡单独展示。

代价：运行中无法一眼看到已产生的思考/工具数量；工具多时过程区更长，用户需依赖折叠态查看结果。


## 112. 会话回收改为按闲置时间，不设数量上限

原来的规则是「打开超过 6 个句柄，且闲置超过 3 分钟」才淘汰最老的空闲句柄，开第七个会话之前不会回收任何会话，会话关闭事件（以及关闭时的记忆纪要）很难触发。改为不设数量上限：只要句柄闲置超过 5 分钟就回收，当前打开、忙碌、重配中或 keepAlive 的会话除外，并由后台每分钟扫描一次。

代价：后台常驻一个每分钟的轻量扫描；闲置会话被回收后再次进入需要重新 attach（会话文件不变）。

## 113. 记忆纪要改为增量自适应，只在真正结束时触发

原来每次句柄关闭都会触发纪要——包括切换模型引起的 reload/reconfigure、删除会话、闲置回收。同一会话被反复总结，且纪要固定按「决定 / 结论与教训 / 未完成 / 用户情况」四段输出，日常对话被硬套模板。

现在：

- 关闭原因由 `SessionCloseReason` 区分（Idle / Reload / Reconfigure / Deleted / Closed），经 `AgentRuntimeHost.sessionClosures` 事件暴露；只有 Idle（闲置回收）与 Closed（显式关闭）写纪要，其余直接丢弃已抓取的 transcript。
- `summaries.log` 记录每条纪要的 `sessionId|hash|lastEntryId`，下次只总结该 entry 之后的增量（首次要求 ≥4 条消息，增量 ≥2 条），同一会话的纪要由 per-session Mutex 串行。
- 纪要 prompt 改为自适应：只列值得跨会话记住的内容（≤6 条），没有可记就输出 `SKIP`；不再固定分段、不再强制分类。
- 手动「沉淀当前会话」不推进水位，后续自动纪要可能包含已手动总结的内容。

代价：纪要变成分散在当天 daily 的增量列表，时间线上的连续性变弱；水位依赖 JSONL entry id，分支重写时可能跳过一轮。

## 114. 记忆重写为四区语义与时间树

原记忆是「profile / context / playbook + 平铺 daily」的四类文件：`playbook` 偏开发、`context` 被当成项目模板、daily 每天一个平铺文件。对个人 agent 来说语义不对，且注入把昨天、今天、记忆混在一起，时间线上重叠。

现在：

- 四区固定一级标题：`# 用户与我`（profile.md，用户/协作/环境）、`# 当下与将来`（context.md，正在维持或推进的事、答应过的事）、`# 经验与教训`（cookbook.md，可复用的方法与踩过的坑）、`# 日记`（daily 时间树）。二级标题由模型按内容自由生长，工具只描述分区依据，不规定条目范式；一级标题由 App 维护。
- 日记按时间树落盘：`daily/YYYY/YYYY.md`、`daily/YYYY/MM/YYYY-MM.md`、`daily/YYYY/MM/YYYY-MM/YYYY-MM-DD.md`（日文件只增不改；月/年文件由后台归卷写入）。
- 归档：会话启动与 App 启动后台触发，日文件汇总成月文件、月文件汇总成年文件；文件内 `## 日期` 标记保证幂等，模型失败时不写标记、下次重试，一次不成功即停（离线不走反复超时），不限流。
- 注入改为四区 + 日记三层（今天 raw、本月、本年），互不重叠；去掉 `## Your Memory` 头，预算 16K。
- 纪要失败落 `pending/` 持久队列，下次启动顺序重试；所有后台任务失败只记日志，不阻塞对话；离线时记忆本地读写与检索照常可用。
- 用户可见语义：旧数据不迁移，启动时自动删除旧的 `playbook.md` 与平铺 `daily/*.md`。

代价：日记是两层汇总结构，模型在归卷时可能丢失细节；月/年文件不能再被 memory_write 直接修改（只由归卷生成）；迁移会丢弃旧记忆内容。

## 115. 图标体系切换为 Milo Modern，引导页改用 ui-kit 吉祥物

- 图标：应用内线性图标换成 Milo Modern 200 枚素材包（`assets/milo-modern-icons-200-revised/semibold/`，线宽约 2.15dp），`DdIcons` 全量重映射并新增 Archive/Privacy/License/Workspace/Database/Notifications/FloatingWindow/ChatPreferences/Sync 等语义成员，避免不同页面共用同一图形；无对应语义的 6 项手写同风格 vector。浮动窗口的鲸鱼心情图与 Canvas 动画图标（DdSpinner/DdTypingDots/波形/悬浮环）保持原样。
- 引导：六步流程不变，视觉按提供的参考稿重做（白底左对齐、大标题、下划线输入、灰底胶囊分段、右上步骤计数、带箭头主按钮）；吉祥物用 `assets/ui-kit/whale-flat-v1` 的四张全身像（wave/idle/thinking/sleepy），旧的 `assets/mascot` 候选插画移出仓库。
- 素材目录只保留在用内容（milo 图标、ui-kit 四张吉祥物、provider/vendor 图标与 branding 记录），未使用素材移出 `assets/`。

## 116. Linux 挂载目录与提示词 paths 段合并

整块公共存储共享之外，需要按目录挂载（如 Obsidian vault）并让模型知道每个目录的用途。现在：

- 存储：`files/storage-mounts.json`（`StorageMountStore`），字段 `id/name/description/path`；最多 20 个，名称 ≤32、说明 ≤160，路径限定在 `/storage/emulated/0/` 下，原子写。
- 选择：设置 → Linux → 挂载目录，经 Storage Access Framework 选公共存储子目录（`PublicStoragePicker` 只接受 `externalstorage` + `primary`），保存为宿主绝对路径。
- 提示词：原 `shared_storage` 与 `paths` 两段合并为单个 `<paths>`（`PromptPaths.buildPathsSection`），依次输出 Skills / App data / Shared storage / Mounted directories；挂载目录渲染为 `- 名称 (路径): 说明`。`Personalization` 不再接收 sharedStorage。
- 执行：`PRootExecutor` / `LinuxEnvironment` 的 `extraBinds` 改为 provider lambda，按当前挂载列表把宿主目录绑定到 guest 同路径，并在 bind 前 mkdir 目标。

理由：单一来源、少一段提示词、挂载配置不写死在代码里。代价：SAF 只能选公共存储；挂载目录被删除后 bind 失败由 proot 报错，不额外兜底。

## 117. 提示词渲染修复与转义单点契约

按风格审计与用户第 1 项要求修提示词：environment 段删除与 `<cwd>` 重复的 Working directory 行（`buildPersonalizationSections` 不再收 cwd）；memory 渲染去掉重复的区标题、日记三层标题按层级降级（`# 日记` → `## 今天` → `### 纪要`）、HTML 注释转成可读文本而非 `&lt;!-- --&gt;`；`plugin_context` 删除 runtime 外层 wrap，保留 adapter 每插件一个 `<plugin_context plugin="…">` 兄弟段；`wrapPromptSection(name, content, escape = true)` 统一转义，调用方不再各自 escape（nickname 不再双重转义）；plugin-api `PromptSection.body` 补 KDoc 契约（纯文本/Markdown、禁 XML、禁 `#` 一级标题）。

`mcp_servers` 裸文本本次未修：SDK `AgentSession.kt:738-741` 用 map 覆盖且 `buildMcpServersSection` 为 internal，app 仓库无法包裹，标记为上游 SDK 待修，未改 pi2kotlin、未发布 mavenLocal。

代价：`plugin_context` 顶层从单容器变成每插件一个兄弟段；该段在 `createSession` 内局部函数，无自动化测试，只能靠 SDK 渲染路径核对。

## 118. Linux 设置表单化、组合拓展与引导强制安装

挂载目录按 mock 改为底部表单（DdSheet：右上关闭、标题/副标题、共享目录卡片+更换、名称、说明选填、底部主按钮），列表去图标与路径、只留「闭合文件夹 + 名称 + 说明」，编辑时保留移除。安装组合改为「列表 + 表单 sheet」：包含应用、附加应用（包目录多选）、叠加组合、软件源、构建工具下载。组合拓展持久化到 `files/dev_bundle_customization.json`（附加包/叠加/镜像，未知 id 过滤）；镜像支持官方/阿里云/清华/腾讯 v3.24 main+community，默认阿里云，每次组合安装前重写 `/etc/apk/repositories` 并 `apk update`；新增 `apkdev` 预置组合（基础包 + JDK21 musl、Android SDK aarch64、platform-36、Gradle 9.8.2 的下载/校验/解压步骤，约 480 MB）。引导页 Linux 步不可跳过：进入即装 rootfs，随后自动装「环境基础」并显示阶段进度，失败可重试/续装。

代价与风险：默认阿里云会覆盖用户已有源；apkdev 的下载步骤只做编译与命令字符串单测，未真机跑过（依赖 proot 内 curl/sha256sum/tar/unzip 与网络）；组合拓展只做追加/叠加，不支持用户命名自定义组合；持久化放在 ui/settings 的 JSON 而非 data 包 DataStore。

## 119. 抽屉收纳、远程连接占位与市场/我的本地预设

抽屉一级只保留 5 项（小程序、技能、插件、记忆、远程连接），连接器与定时任务收进「展开更多」折叠行；新增远程连接空页（返回、标题、空态）与 `AppDestination.Remote` 路由。技能/连接器/插件三页在顶栏与搜索框之间加「市场 / 我的」分段：市场为本地静态预设（每类 8 条，可搜索名称/说明/标签，安装按钮置灰并提示即将开放），「我的」保持原列表与操作；两个 tab 的搜索词独立。中文「厂商」统一改为「服务商」。

代价：市场内容为占位，默认 tab 是「市场」；tab 与市场搜索词是页面级 `rememberSaveable`，重新进入会复位；接入真实市场时需替换 `MarketPresets` 与行尾按钮逻辑。

## 120. 新建工作区表单与自定义目录

新建工作区由「只输名称」改为表单：名称 + 工作区目录二选一（应用私有目录，默认，显示路径预览；公共存储目录，经 SAF 选择并引导「所有文件访问权限」）。runtime `createWorkspace(name, directory?)` 支持自定义 cwd（绝对路径、非 Home、未被占用校验，mkdirs）；删除工作区只删除 app 私有 `workspacesDir` 内的目录，自定义/公共目录保留，避免误删用户数据；重命名弹窗收敛为纯重命名。会话 JSONL 仍写 app 私有 `agentDir/sessions`（按 cwd 编码目录），不污染用户目录。

代价与风险：公共目录依赖 `MANAGE_EXTERNAL_STORAGE`，用户撤销权限后读写失败；私有目录路径预览在 AppOverlays 用 `LocalContext.filesDir` 重复计算；工作区相关 key 复用 `strings_drawer.xml`，后续迁移需同步；SAF 选择与公共目录下 bash/文件工具行为未真机验证。

## 121. 小米云插件按登录态注入工具

`MiCloudPlugin` 的 `contributions` 改为仅在 `MiCloudSessionState.VERIFIED/UNVERIFIED` 时注入 `mi_cloud_*` 工具；状态探测、登录、退出、清空、过期时调用 `host.requestContributionsRefresh()`，启动时首次物化拿不到登录态，靠探测完成后的刷新补上。插件版本升到 1.0.1（同版本重装升级会卡在导入对话框，升版本可正常升级，旧 cookie/secrets 保留）。

验证：真机装 1.0.1 后，未登录/未知状态的新会话没有 `mi_cloud_*` 工具，提示「登录状态未知」；登录成功后的注入路径未在本机验证（无账号）。代价：贡献物化与登录探测存在一个刷新周期，登录后立即开新会话可能仍看到无工具，下一次刷新后恢复。

## 122. Linux 环境内置化

Linux 不再作为「推荐安装」的附加组件，而是 App 自带能力：rootfs（alpine-minirootfs，约 4 MB）随 APK 内置，启动后由 `AppGraph` 在 IO 线程静默解压安装（`shouldAutoInstallLinux(supported, state)` 只在 `supported && NotInstalled` 时触发，失败仅记日志、下次启动重试）。设置页 Linux 状态行改为「内置环境」（已内置 / 正在准备 / 待准备 / 准备失败可点重试），不再出现安装按钮；新装用户「允许运行 Linux 命令」默认开启（`LinuxSettingsStore.enabled` 默认 true，已存值优先），共享存储与 Root 仍默认关闭。基础工具（apk 包）仍需联网按需下载，由安装组合提供。

代价：失败时只有日志与设置页重试，没有全局提示；默认开启对从未保存过开关值的新用户生效。

## 123. 引导页「选择工作环境」取代 Linux 安装步骤

引导第 4 步原本是安装 Linux 与环境基础；内置化后改为「选择工作环境」：列出开发组合（环境基础 / 日常办公 / 开发编程 / APK 开发组合），显示名称、说明与体积，多选，环境基础默认选中，已安装组合显示「已安装」不可重复选。点「开始使用」后把选中组合合并成一个队列交给 `OnboardingEnvironmentInstaller`：后台等待 rootfs 就绪（最多 10 分钟）→ 合并为单个 `DevBundle`（packageIds 按目录顺序去重、postInstall 换行合并、downloads 去重）→ 调一次 `DevPackageManager.installBundle`，不阻塞进入 App；「稍后」直接下一步。引导顺序变为：欢迎 → 称呼 → 模型 → 权限 → 工作环境 → 权限等级。

代价：下载进度只在系统通知与设置页可见，引导内没有进度条；10 分钟内 rootfs 未就绪时队列静默放弃；不支持 Linux 的设备上组合可选但不会安装。

## 124. 安装组合去掉「组合拓展」UI

组合 sheet 原本把「包含应用」「附加应用」「叠加组合」并列，再叠一个镜像源，用户反馈信息混在一起、不分明。现在只保留：标题 + 一句说明、包含应用（应用数 + 清单）、软件源、底部安装按钮；组合列表行去掉图标与体积，行尾只显示安装进度计数（`2/8`，装完显示满数），不再显示「已安装」文字。底层 `DevBundleCustomizationStore` 暂时保留 extras/overlays 字段以兼容旧数据，但不再有 UI 入口。

代价：用户不能再在 UI 里给单个组合加包或叠加其他组合，只能直接安装整组；需要更细的组合时得回到组合定义或后续另设入口。
