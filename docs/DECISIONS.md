# 设计取舍

这些是**有意为之**的决定，不是缺陷。改代码前按这里判断；要推翻先改本文件并写清迁移代价。

1. **签名**：release 也用 debug 证书。换正式 keystore 会让现装设备无法覆盖安装，`allowBackup=false` 下卸载即丢全部会话与凭据；只在「愿意让所有人重装」的发版点换。
2. **不做二次审批，只分权限档**：个人设备、单人使用，风险自担；系统级能力只有「权限」页三档（普通/无线调试 ADB/Root），ADB 授权走系统无线调试配对，Root 授权交给系统 root 管理器，App 不另做审批流。
3. **单 Activity + 自研 `AnimatedScreenHost` 导航**，不引入 Navigation-Compose；跨层级转场与页面状态保存已按此实现。
4. **`:app` 不拆 `:feature:*`/`:core:*`**：`:runtime-api` 是唯一纯 JVM 契约层；`:app` 只依赖 `:runtime/:shell/:design`，模块边界由 Gradle 任务强制。
5. **本地分发**：不上架；Maven Central 与仓库公开都推迟，等真有外部使用者再说。
6. **会话以 pi JSONL 为唯一权威**：置顶/归档/改名/草稿都是本地元数据，不回写会话正文。
7. **创意工坊是安全边界**：独立 origin + document-start bridge + 工具白名单；页面不能调 App 内部函数、不能执行 shell、读不到其他数据。
8. **Root 是可选增强层**：默认普通档；选中特权档且 root 可用时注册 `root_shell`（与无线调试是否可用无关）；授权交给系统 root 管理器，App 不做二次审批；前台、后台、定时、目标续跑等任何轮次都可执行。
9. **不做无实测的性能优化**：声称变快必须给 `gfxinfo` 前后数字。
10. **持久化纪律**：原子写（tmp + rename）、坏文件先备份 `.corrupt` 再重建、带 `schemaVersion`。
11. **许可边界**：自有代码 Apache-2.0；PRoot（GPL-2.0-or-later）、Alpine 包、字体/图标、pi2kotlin SDK（MIT）各自许可；不对外说「整个 APK 是 Apache-2.0」。
12. **会话状态只有一个所有者**：`SessionCommandPlane` 用每会话锁与单调 epoch 管理 busy/stop/retry；只有当前 run 能清除 busy，迟到事件按 runId 隔离。
13. **状态与事件分工**：运行状态以 `activeRuns` 为准；事件保持多订阅者广播，UI 从运行快照与历史对账，结构事件不得被流式 delta 挤出顺序。
14. **能力重配置走安全点**：插件能力变化经 `host.reconfigure()`；忙碌会话在本轮结束的安全点应用，不提前关闭正在使用的会话。
15. **会话实时状态是运行时事件的纯投影**：主聊天页、系统助手浮层等任何 surface 都用同一个 `reduce` 与 `SessionProjection` 从 `runtime.events` 还原当前会话；`RunStarted` 对用户轮次携带原文 prompt，界面不得假设「这一轮由自己发起」。界面外壳只保留输入、滚动、可见性等本地状态；消息/过程/工具渲染共用同一套组件。
16. **shell 工具统一执行管线**：proot Alpine 与 Android 特权 shell 是两个命名空间，不合并运行环境；`root_shell` 与 `adb_shell` 复用 SDK `bash` 工具的执行、截断与格式化管线，输出契约一致（截断提示 + 退出码）。
17. **浮起面允许着色投影与克制的光效**：输入卡与助手浮层是浮起面，共用 12dp 着色投影（暗色补 1dp 发丝描边）；浮层输入卡可叠加蓝色系深海光晕、半透明底与渐变描边，光效随当前轮次状态变化但只做视觉层，不另建运行订阅。回答首字的流光沿四边自下而上，整圈只保留低强度底光。这是用户明确要求的氛围效果，不要按旧纪律删掉；其余平面内容仍保持无阴影/渐变。
18. **助手浮层是入口，不是第二个聊天页**：唤起时底部滑入输入卡（默认打字；麦克风短按切换、按住说话松手结束；工作区入口在面板标题右侧，点开后先选工作区、再选会话；两级紧凑浮层卡，不撑高面板；模型与思考档同在一个模型 sheet）。发送后顶部滑下会话面板，高度按各消息实测高度实时跟随内容：发送后先长到消息高度，生成中随字符逐行下探，超过半屏后才固定并跟底，空闲时收敛到内容高度（短会话不会先撑满半屏）；键盘打开时面板顶部不动、不隐藏，底部与输入卡之间保留固定间隔并做高度动画（高度动画只在 layout 阶段读动画状态，避免逐帧重组），输入卡单独抬到键盘上方；右上角全屏按钮进 App，不做下拉进 App 手势；点空白/返回关闭浮层。消息、过程、交付与提问复用主界面同一套组件。
19. **轮次生命周期归进程，不归界面**：浮层用应用级 scope 驱动轮次，并作为入口启动前台服务；关闭浮层、从浮层跳进 App 只销毁界面，不取消正在跑的轮次。任何界面切换都不得中断轮次，只有用户主动停止才终止。
20. **发送/插话/排队是运行时语义**：会话空闲时发送即起新轮；忙碌时发送=插话（steer）、长按=排队（follow-up），由运行时统一处理，不与某个界面绑定；队列状态属于共享会话投影，主界面与浮层消费同一份数据。
21. **输入框回车换行，只按按钮发送**：主界面与浮层一致，避免回车误发，多行输入保留换行。
22. **过程行行首转圈 + 本地化耗时取代「思考中」提示**：不再在输入框上方显示思考状态；运行中在过程行最前显示转圈与实时秒数，结束后显示总耗时。
23. **光晕取色**：深色取星空与深海的深蓝，浅色取天空、海浪与浪花的天蓝/海蓝。
24. **光效技术取舍**：优先 Compose 绘制管线；不引入 GLSL、预渲染资源或第三方动画库，除非先证明 UI 线程不是瓶颈；RuntimeShader 只作为 API 33+ 的后续原型。
25. **浮层入场等首帧就绪再起步**：唤起时立即压暗背景并起光效，但输入卡与面板的滑动要等会话加载、面板完成首帧布局后才开始；面板内容列表从底部附近起步，避免先组合顶部再跳到底部。这样入场期间的组合/测量尖峰落在静止背景上，卡片运动本身保持高刷流畅；冷启动的剩余卡顿属于进程/首帧成本，不等于动画卡顿。
26. **语音入口只有一个形态**：旧语音 sheet 及其状态机已删除；主界面输入卡与浮层麦克风统一为「短按切换、按住说话、松手结束」，识别结果回填输入框（设置允许且输入为空时直接发送）。任何入口收到 `ACTION_ASSIST` 都转到系统助手浮层，包括旧版本遗留在系统里、仍指向 `MainActivity` 的助手组件；不再保留第二套语音界面。

27. **权限分三档，后端可用性优先于档位**：普通档只有 PRoot Alpine 里的 `bash`（主动不使用特权）；选中无线调试（ADB）/Root 后按实际可用后端注册工具：ADB 守护可用 → `adb_shell`（App 内嵌 ShellDaemon，uid 2000），Root 可用 → `root_shell`（`su`）；只按 `effective` 注册一个 shell 工具，两者都可用时不再同时暴露，避免模型选错。`effective` 表示默认/显示档位：选中后端可用就用它，否则回落另一可用后端；选普通档仍不启用特权。ADB 档不可用回落 Root、Root 档不可用回落 ADB；存量 `SHIZUKU` 持久值解析为 ADB。启动时仅 `selected != NORMAL` 才探测 root；选择特权档、权限页刷新、手动检测仍探测；root 为 `Denied`/`Unsupported` 时不自动反复探测，UI 提供手动重新检测。`selected`/`effective`/可用后端变化都走 `host.reconfigure()`。虚拟屏幕可用 = 选了特权档且至少一个后端可用，启动时选中后端优先、另一可用随后。
28. **无线调试不再依赖 Shizuku**：项目默认不加第三方依赖；本轮为自建 ADB 配对与守护破例引入 `com.github.MuntashirAkon:libadb-android:3.1.1`（JitPack，Apache-2.0/BSD-3 双许可）、`org.lsposed.hiddenapibypass:hiddenapibypass:6.1`（配对时经隐藏 Conscrypt 取 TLS exporter）、`org.bouncycastle:bcpkix-jdk15to18:1.86`（自签 ADB 证书）、`com.squareup.okhttp3:okhttp:5.4.0`（本地 HTTP 客户端）。不再有 Shizuku 依赖/provider/代码。首次配对通过系统无线调试配对码 + 通知 RemoteInput；配对成功后 App 自己连接一次 ADB TLS、拉起 `ShellDaemon`（uid 2000）后断开，之后全部走 `127.0.0.1:18242` + token。密钥/证书/token 存 App 私有目录，跨升级保留；配对/守护的完整流程见 32。实测环境：Android 16 无 root 测试机通过无线调试配对、冷启动与虚拟屏；Root 回落由单测覆盖（有 root 手机未连机复测）。

29. **悬浮窗是第三层入口**：小鲸鱼气泡用 `TYPE_APPLICATION_OVERLAY` + `SYSTEM_ALERT_WINDOW`，开启后在 App、助手浮层与其他应用上一律显示；点击气泡打开运行面板、再点不做任何事，只有面板右上角 X 收起；面板跟随气泡一起拖动，拖动自由摆放、不吸附边缘，位置持久化；长按打开助手浮层。提问/交付/完成用气泡旁的事件卡（优先级 提问 > 交付 > 完成，只在 App/助手浮层不在前台时出现，避免与页内提问重复），气泡内只内联单选提问，文本/多选跳助手浮层。事件卡与面板复用运行状态单一来源（`RunActivityTracker` + `QuestionCenter.pending` + `present` 工具结果），不另建运行订阅。入口链路固定为 悬浮窗 → 助手浮层 → App。面板里的虚拟屏区展示 ≤5fps 的缩略预览，点击进全屏查看器；预览只重组面板，稳定状态不携带帧。浮窗几何按 overlay 实际 content frame（排除状态栏/导航栏）计算，不用全屏 `displayMetrics`，否则下半屏时面板与气泡会多出系统栏高度。鲸鱼娘四态取自 `assets/ui-kit/whale-flat-v1`（idle/wave/thinking/sleepy），只表达状态，成败仍以工具结果与文字为准。
30. **虚拟屏幕是特权插件**：用 `app_process` helper 在 shell uid 2000 下创建 trusted VirtualDisplay，宽高与 dpi 由 helper 取真实屏幕指标：宽=短边、高=长边、dpi=真实 densityDpi，**始终竖屏**，App 不再下发尺寸；实测 Android 16 上 VirtualDisplay 不继承主屏 rotation（主屏 ROTATION_90 时 VD 仍 rotation=0、1220×2712）。`am start --display` 启动 App，反射 `InputManager` 注入输入、ImageReader 取帧；helper 必须 uid 2000（uid 0 会被系统「包名与 uid 不匹配」校验拒绝），root 路径为 `su -c "su 2000 -c ..."`。IPC 改为 App 建 loopback TCP `ServerSocket`、helper 反向 connect `127.0.0.1:port`（抽象 LocalSocket 在 shell 域被 SELinux 拒绝，实测超时）；hello 携带 uid + 一次性 nonce 双校验。帧流 360 宽 JPEG ≈12fps，截图在无新帧时回退最近一帧缓存。插件要求选了特权档且无线调试/Root 至少一个实际可用；启动顺序见 27；按屏无障碍读取见 33。已知限制：helper 的 `text` 只支持 ASCII；loopback TCP 仅 token/nonce 防护，理论上本机其他进程可抢连导致一次启动失败（重试即可）。
31. **ui-kit 鲸鱼娘素材的用途**：`assets/ui-kit/whale-flat-v1` 的 `mascot_idle/wave/thinking/sleepy` 导出为 192px WebP 放 `drawable-nodpi`，只用于悬浮窗气泡的状态表情（运行 thinking、待处理 wave、常态 idle、长时间无事件 sleepy）；不做按钮底色或可拉伸九宫格，UI 组件仍用 Compose 绘制。
32. **内嵌无线调试配对与守护进程**：首次配对由 App 发通知（RemoteInput 6 位码）并打开开发者选项；用户打开系统「使用配对码配对设备」后把码填入通知，receiver 用 libadb 完成 TLS 配对并自动点火 `ShellDaemon`。守护进程用 APK 自身的 `app_process` + `CLASSPATH` 启动，绑定 `127.0.0.1:18242`，用 `X-Token` 鉴权；只保留 `/health`、`/exec`、`/start-helper`、`/kill-helper`，旧的主屏 `/tap`、`/swipe`、`/settings` 已删除，屏幕注入统一走 `input`/helper。daemon 生命周期挂在 adbd 的 cgroup 上：关闭无线调试或重启后 adbd 退出，daemon 随之被系统清理；App 重新打开无线调试后点「连接并启动守护进程」即可重新点火，无需重新配对。App 升级保留 `files/adb` 下的 key/cert 与 prefs 里的 paired/token；force-stop App 不会杀 daemon。token 通过命令行传给 daemon，`/proc` 对 shell/root 可见；HTTP 明文只对 loopback 白名单放行（network_security_config），不暴露到局域网。
33. **统一屏幕操作层**：不再分别暴露 `gui_*` 与 `virtual_screen_*` 操作工具；一套 `screen_*`（status/observe/find/screenshot/tap/swipe/scroll/type/key/wait/launch/list_apps）用 `target=main|virtual` 选屏，`virtual_screen_start/stop/status` 只保留生命周期。后端分层：无障碍负责节点树/ref/语义操作/Unicode/等待；ADB 负责主屏坐标输入、启动与 uiautomator 兜底；虚拟屏 helper 负责坐标输入/截图/启动/list_apps。主屏截图需要无障碍；虚拟屏节点操作需要无障碍，关闭时明确降级为坐标+截图。主屏输入文本时若给了 ref 但无障碍不可用，直接报错，不能盲注入当前焦点。虚拟屏在线时主屏节点读取不再走 ADB uiautomator（活动窗口可能是副屏），只走无障碍；无障碍关闭时明确报错。旧 `GuiTools.kt` 与旧工具已删除；`screen_*` 的 skill 说明与提示词同步更新。
