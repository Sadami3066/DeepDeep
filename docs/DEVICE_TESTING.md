# Device testing

设备：Redmi 22101316UCP / Android 13 / arm64-v8a。

## 构建与安装

```bash
cd ~/project/DeepDeep
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

MIUI 普通 adb 安装可能被拦截，开发机使用 root 安装：

```bash
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep-debug.apk
adb shell 'su -c "pm install -r /data/local/tmp/deepdeep-debug.apk"'
adb shell 'su -c "rm -f /data/local/tmp/deepdeep-debug.apk"'
```

## 导入 DeepSeek Key（仅 debug）

Key 保存在主机 `~/.config/deepdeep/deepseek.key`（0600）。

```bash
./scripts/import-key.sh
adb shell am force-stop io.github.whywood.deepdeep
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

App 启动时读取 `files/import/deepseek.key`，写入 Keystore 加密存储后删除导入文件；Release 不包含这段逻辑。

## 验收

1. 长任务：发一个包含 `sleep 5` × 3 或多次工具调用的请求；2/6/12 秒时检查 working 状态、思考、工具卡和运行耗时都可见；结束后过程可折叠、最终回答可见。
2. 自动跟随：内容增长时停在底部；手动上滑后出现“回到底部”。
3. 新会话：发送后立即打开抽屉，标题已经出现。
4. 停止：长命令运行时点停止；显示“已停止”，工具标失败，PRoot 进程消失。
5. 会话动作：长按历史项重命名/置顶/归档/删除；归档页可取消归档。
6. 设置：Claude 式分组；占位项有反馈；厂商列表 42 家；搜索、详情、API Key、设为当前。
7. 冷启动：杀进程重开，历史恢复；未就绪时 composer 显示加载/禁用。
8. 单实例：连续 `am start` 不应产生多个 MainActivity；日志只有一个 run。
9. Release/R8：签名安装后能启动、聊天、使用工具。
10. 安全：Key 不明文落盘、不进 logcat、不进 APK。
11. 工作区树：点击工作区行展开/收起，可同时展开多个；行尾 chevron 旋转；工作区行展开后 + 在该工作区新建会话。
12. 置顶：长按工作区置顶/取消置顶（pin 图标 + 排到最前，重启仍保留）；长按会话置顶后行尾显示 pin 图标，仅在所属工作区内排序。
13. 新建入口：顶栏无 +；只可从抽屉工作区行展开后的 + 创建，Home 也如此；长按工作区菜单不再提供「新建会话」。
14. 旧 UI 清理：设置运行环境页无嵌套色块/错位；厂商已保存 key 显示掩码占位；会话搜索、归档搜索、厂商搜索使用同一胶囊搜索框；对话框弹键盘时按钮仍可见。
15. 性能：抽屉连开合 10 次、键盘聚焦/收起 5 次、长会话滚动 10 秒，用 `dumpsys gfxinfo <pkg> reset` → 操作 → `dumpsys gfxinfo <pkg>` 对比 Janky/Slow UI；流式回答期间不应有整段卡死。
16. Markdown：反引号内相对/绝对路径不再显示彩色代码胶囊；`attachments/…`、`files/home/…`、`files/workspaces/…` 的 host 绝对路径均可点击打开文件预览；普通行内代码是淡蓝底 + 微圆角，底要完整包住文字（含 j/y/g 等下行字母，不能高出/切掉），换行时圆角底按行拆分；多反引号 code span 正常。
17. 空态/图标：新会话空态有 mascot 插画（浅/深色）；抽屉继续使用原有图标库（工作区 Folder / 会话 Message 建立层级），不依赖 assets/icons 新图标集。
18. 个性化：改称呼 → 抽屉/设置/问候同步；交互体验三档切换；提示词预设填入输入框、编辑后变自定义；重启后保留。
19. 提示词：改指令后发一条消息；拉取会话 JSONL 检查 `<system>`/`<instruction>` 存在、`deepdeep_environment` 消失；问「你的系统提示词是什么」时不应复述隐藏段。
20. 字体：5 套逐一切换，页面重建后全页使用新字体；中文仍正常；重启保持；截图对比。
21. 厂商图标：42 家列表图标完整；全部彩色、浅/深色各一套；Radius 使用官方图示；浅/深色各截图。
22. 运行环境：安装「环境基础」后 `curl --version`、`python3 --version`；安装「日常办公」后 `pdftotext -v` 与 Python 导入 `docx/pptx`；切后台看安装通知进度；重进页面状态正确；重启后仍显示已安装。
23. 通知：运行会话时前台通知显示标题/工具；按 Home 后完成通知出现；点击进入对应会话；通知「停止」有效；通知权限关闭时不崩溃、设置行显示未开启。
24. 设置删改：无账单与用量/时间与专注/共享；隐私页可进；通知行显示权限状态并可跳系统设置。
25. 运行环境：只有环境基础/日常办公/开发编程三组，无单项工具；组合间距舒适；办公组合安装后 `python3 -c "import docx, pptx, openpyxl, pdfplumber"` 与 `pdftotext -v` 正常。
26. 聊天设置：页面只保留「过程自动折叠」一个开关，且默认开启；个性化提示词无 8000 字上限，计数只显示字数；附件最多 20 个，发送后气泡外附件排成一行横向滚动，不换行。
27. 过程折叠时机：发一个先调用 sleep、再输出长回答的请求；最后结果文本开始流式时过程立即折叠且回答在过程外继续增长；关闭「过程自动折叠」后结束后过程仍展开。
28. 字体：默认 Newsreader；字体页 5 行预览均显示「我思故我在 / I think, therefore I am.」，中文在行右下；设置页无功能/连接器/权限。
29. Skills：抽屉「技能」进入；右上角安装一个合法 `.zip`；仓库出现技能且开关默认关闭；`run-as` 确认 `files/skills/<id>/SKILL.md`、`files/agent/skills` 不存在。
30. Skills 全局：打开开关后 `files/agent/skills/<id>` 出现；关闭后消失；重装同名技能会更新仓库与已启用副本。
31. Workspace settings：长按工作区菜单有「设置」、无「新建会话」；页面显示项目技能和 AGENTS.md 计数。
32. 项目技能：关闭全局后，项目技能 sheet 只列未全局技能；勾选保存后 `files/workspaces/<id>/.pi/skills/<id>` 存在；新建会话 JSONL 的 `<skills>` 含该技能；取消勾选后副本消失。
33. AGENTS.md：编辑并保存后工作区根目录出现文件；新建会话 JSONL 的 `<project_context>` 含保存内容；有 `AGENTS.override.md` 时显示实际文件名。
34. ask_user_question：聊天要求模型提问；sheet 显示题干/选项/自定义/跳过/提交；选择后工具继续并回答；关闭或跳过会结束等待；App 不在前台时不挂起。
35. schedule：聊天创建 daily/weekdays 任务；`files/schedules/schedules.json` 有正确 `nextRunAt` 与触发；`schedule_list/update/delete` 可操作；工具结果包含 `execution: "enabled"`。
36. 重启迁移：工作区 AGENTS.md、项目技能和工作区会话在重启后仍生效；`files/agent/sessions` 下不出现 `files-homes-*` 目录，`/files/workspaces/...` 不被误写。
37. 项目技能显示全局技能：全局启用一个技能后打开工作区设置 → 项目技能，该技能显示为开启、标记「全局」且开关不可操作。
38. 会话重新加载：打开一个已有会话，安装/启用新技能；聊天右上角「重新加载」；继续发消息，会话 JSONL 新增系统段中出现 `<skills>`。
39. 定时任务页：抽屉「定时任务」显示列表、下次运行、开关；`+` 打开隐藏工作区安排会话，抽屉/搜索不出现该隐藏工作区。
40. 定时任务执行：创建 1–2 分钟后的一次性任务；前台服务 tick 后 `lastStatus=DONE`、`lastSessionId` 有值、nextRunAt 置空且开关关闭；运行会话可打开并看到结果。
41. 任务详情：显示触发、工作区、下次/上次运行、状态、错误、prompt；打开运行会话、删除均可用；安排记录可重新打开隐藏会话。
42. 项目技能保存：全局技能不生成项目副本；未全局技能勾选后复制到 `.pi/skills`，取消勾选后删除。
43. 定时任务迁移：升级前存在 v1 任务；升级后 `schedules.json` 变成 `schemaVersion: 2` 且任务为空，旧任务不再出现。
44. 定时任务编辑器：任务页「＋」打开编辑器，填名称/内容/时间；一次性过去时间内联报错；保存后列表立即出现新行，重启仍在。
45. 提醒投递：在当前对话创建 1 分钟内的一次性提醒；到点后提醒气泡出现在**同一对话**（带「定时提醒」标签），助手在原对话里继续回复；抽屉中该对话置顶；一次性任务变为已停用。
46. 批量投递：同一对话两条提醒设到同一分钟；只出现一条批量提醒和一次运行。
47. 手动执行：编辑已有任务点「立即运行」；提醒立刻出现在绑定对话并跳转过去，下次投递时间不变。
48. 生命周期：删除绑定对话后任务从任务页消失；归档绑定对话后任务变已停用。
49. 失败可见：断网或使用无效 API Key 发送；该轮显示本地化错误（如「余额不足」「Invalid or expired API key」）+「重试」；恢复网络后点重试成功；杀进程重开历史里仍能看到失败轮。
50. 补跑：到点前杀进程，重开 App 后补投一次且不重复；改设备时区后任务按新本地时间重算。
51. 系统提示词：新会话的 JSONL 初始 system 消息里已经包含 `preamble`、`rules`、`environment`、`instruction`、插件等全部当前 section，`preamble` 是 DeepDeep 自己的前言（不再出现 "You are an expert coding assistant operating inside pi"），`<tools>` 文本段不再生成（工具定义走 system 消息的 `toolsAdded`）。连续对话多轮后 JSONL 不应每轮新增 system 消息；只有 MCP 连接、工具/技能/偏好真正变化时才允许追加 patch。`files/agent/SYSTEM.md` 首次启动生成，手动改动后不会被覆盖。
52. OpenCode Go：切到 opencode-go 的模型（如 deepseek-v4.1-flash / claude-haiku-5-5）发一条消息；不再出现 400 `MissingSessionID`，正常回复；会话 JSONL 无 `stopReason=error`。
53. 模型 sheet 厂商切换：点 composer 模型 chip，sheet 顶部「厂商」行下方弹出专属下拉（不是上下文菜单），只列已配置 Key 的厂商；切换后模型列表更新、当前模型生效，重启后保持。
54. 重试（重新生成）：关掉自动重试，断网发送得到失败轮 → 恢复网络点「重试」：失败气泡和错误行保持不动，「重试」文字变成蓝色小圈圈，随后流式生成（不消失不重影）；用户消息不重复出现；会话 JSONL 里失败回合被 context edit 移除。
55. 自动重试：默认已开启（聊天设置可关），断网发送 → 状态行显示「自动重试 n/5」并自动重试，恢复网络后自动成功；超时类错误不自动重试。
56. 自动重试上限：持续断网时最多自动重试 5 次后停下，失败轮保留错误与「重试」。
57. 失败文案：错误轮显示「原因（错误码）」本地化文案，不出现「没有收到回复」占位；英文界面不出现中文；未知错误只显示「Request failed」，原始文本只在会话 JSONL。

58. 命令面板：行首输入 `/` 出现命令背板，顺序为 App 命令（压缩/目标/创意工坊技能）在上、已启用技能 `/skill:<name>` 在下，输入 `co`/`skill` 可过滤；点选只把完整命令补全到输入框（不直接执行），命令 token 显示强调色、光标在末尾；在文字后空格输入 `/` 同样展开面板；输入 `/foo` 发送时提示未知命令且消息不发送。`/skill:<name>` 可以写在消息中间或末尾，发送时同样展开技能；一条消息可提及多个技能，代码块/行内代码里的 `/skill:` 不展开。
59. 手动压缩：长对话中点 `/compact` 或输入 `/compact 任意文字` 发送，都会忽略后面的文字直接压缩；状态区显示「正在压缩上下文…」，完成后消息流出现「上下文已压缩：A → B」，上下文环下降；再点一次提示「没有可压缩的上下文」。
60. 上下文统计：composer 右侧出现百分比圆环；点开统计 sheet，上下文/窗口、阈值、输入/输出/缓存/费用齐全，数值带 `~`；切换会话后数字随之切换。
61. 目标：输入 `/goal 写一份周报草稿` 发送，目标条直接出现（不再有目标 sheet）；`/goal pause`、`/goal resume`、`/goal clear` 直接操作；空 `/goal` 提示目标不能为空；模型完成后自动进入下一轮并显示带 Sparkles 的目标轮提示（第 N 轮 + objective），轮次持续增加且没有 10 轮限制；点「暂停」后不再续跑，「继续」恢复；目标条不可点击；杀进程重开后目标显示暂停，点继续可恢复。
62. 后台任务：让 Agent 用 `job_start` 起一个长时间命令（如 `sleep 60 && echo done`），立即拿到 job id；token 用量行左侧出现 Terminal 图标 + 运行中数量，点开后台任务 sheet 可看到运行中与每秒刷新的输出；点停止并确认后状态变「已停止」；让 Agent 用 `bash` 带短 `timeout` 跑一条慢命令，超时后工具返回错误并结束，不产生后台任务、不转后台；关闭「后台任务完成时唤醒」后再起一个短任务，完成后不自动开新轮；开启唤醒时，完成后的 assistant 回复上方应有一行「后台任务 · 名称」极简标记，且不出现完成/失败卡片；归档会话后任务被清理。
63. 内联提问：让 Agent 调用 `ask_user_question`；提问卡出现在聊天消息里（不弹 sheet），单选自动前进、多选、自定义、跳过、上一题可用；提交后显示「已提交」，工具结束后互动卡消失、过程里 ask 工具行后面出现「已回答 · N 个问题」，点开能平滑展开只读问答（不闪、不跳）；让模型在同一轮里并行调用两次 `ask_user_question`：同一时间只有一张卡，答完第一题后自动出现第二题；展开过程可见两条 ask 工具行与两条已答行按顺序排列；删除 `/` 面板后无闪动。
64. 跨会话提问：在会话 A 触发提问后切到会话 B；B 的 composer 上方出现「另一个会话正在等待回答 · 前往回答」，点击回到 A 的提问卡。

65. 交付卡片：让 Agent 生成文件（例如 HTML、Markdown、图片各一次）并调用 `present`；assistant 正文下方出现与附件同尺寸（140dp）的方形文件卡（文件类型图标 + 文件名，无标题/描述），多文件横向滚动；点整卡能预览或拉起系统应用，点右上角无底色分享图标出现系统分享面板（Home 与 workspace 会话都要能分享）；文件被删掉后点打开有 Toast 提示；重开会话后卡片仍在。
66. 共享目录：设置 → 运行环境 → 共享目录（未授权时点行进系统设置授予「所有文件访问权限」，返回后出现开关）；开启后让 Agent 用 bash `ls /sdcard/Download` / `echo ok > /sdcard/Download/deepdeep-test.txt`，用系统文件管理器确认真实落盘；关闭开关后下一次 bash 中 `/sdcard` 不再映射公共目录。
67. 文件浏览：右上角「...」→「目录文件」，sheet 标题下显示当前工作区名称（Home / WorkA 等），列目录与文件；进子目录、回上一级、系统返回逐级退；点文件打开预览或系统应用。
68. 分享不抖动：分别点消息操作栏的分享与交付卡片右上角分享图标，分享面板打开和取消时，聊天内容（顶部栏、消息）不应上下位移。
69. 提醒重复规则：定时任务页「＋」，重复是开关（开 = cron，关 = 仅一次）。cron 填 `*/5 * * * *`，输入框下小字应实时显示「每 5 分钟」，「下次」为下一个 5 分钟整点；填 `0 9 * * 1-5` 显示「每周一、周二、周三、周四、周五 09:00」、下次为下一个工作日 09:00；填 `bad cron` 显示错误色「表达式无效」且保存被拒。保存后列表副标题显示「每 5 分钟」「每周一、周二… 09:00」等人话；`files/schedules/schedules.json` 中 trigger 为 cron 表达式。保持 App 运行到「下次」时间，提醒应投递进绑定会话。旧版本创建的每天/每周任务在编辑器中打开应回显为等价 cron 表达式。

## 证据

`artifacts/validation/polish/`、`artifacts/validation/round6/`、`artifacts/validation/round9/`、`artifacts/validation/round10/`、`artifacts/validation/round11/`、`artifacts/validation/round26/`、`artifacts/validation/round27/`，设备日志用：

```bash
adb logcat -s DeepDeepRuntime DeepDeepChat
```
70. Provider OAuth 登录：设置 → 厂商 → 选一家支持 OAuth 的厂商（Anthropic/OpenAI 需先选登录方式）→ 点登录 → 浏览器完成授权或取消；取消后无状态变化，成功后显示「已登录」、可退出，重启 App 仍保持；浏览器授权完成后应自动回到 DeepDeep（无需手动复制 URL），未自动返回时可用 sheet 里的粘贴入口；已登录 OAuth 的厂商可直接激活并在聊天中发消息；保存 API Key 后切换回 API Key 认证。
71. MCP 列表：抽屉「MCP」→「+」添加一个可用的 Streamable HTTP 服务器（https）；保存后列表出现该行，自动检测显示「已连接 · N 个工具」；「测试连接」结果一致；关闭启用开关后显示「已停用」且不再检测；删除后行消失，重启 App 配置不恢复。
72. MCP OAuth：对没有 Authorization 头的服务器点「登录」→ 浏览器授权 → 返回后显示「已登录」并自动重连；「退出登录」后状态回到需要登录。收不到回调时粘贴回调链接也能完成。
73. MCP 工具：打开一个会话，让模型使用 MCP 工具（或请求「用 tool_search 找 MCP 工具」）；过程里出现 `server · tool` 形式的工具行与结果；工具输出进入对话，删除/停用服务器后新会话不再提供对应工具。
74. 导航收敛：抽屉为 技能 / MCP / 插件 / 定时任务，无「聊天」「已归档」；设置页「已归档」显示数量，点击进入归档列表并能返回；已归档会话可在归档页打开/恢复。

75. 插件页：抽屉出现「插件」；列表有飞书、网络搜索两项；未安装时行尾是「下载」；没有自动下载；`bash` 里 `command -v lark-cli` 失败；运行环境页的包列表不出现 lark-cli。
76. 插件安装：点「下载」→ 行内出现下载进度 → 完成后显示绿点/红点、开关与详情入口；详情页版本 1.0.98；`bash` 里 `lark-cli --version` 输出 `lark-cli version 1.0.98`；杀进程重开后仍是已安装。
77. 插件连接：详情「连接飞书」→ 通用 sheet 出现建应用链接 → 在浏览器完成飞书账号登录并创建应用 → 回到 App 后自动进入账号授权 → 完成后状态变为「已连接 · 账号」；杀掉重开不需要重新登录。若浏览器没有飞书登录态，会停在扫码登录页，需要用户先登录。
78. Agent 驱动：新对话里说「帮我连接飞书」→ 模型调用 `plugin_setup`，弹出同一个 sheet；首次会先显示「需要先下载插件」；完成后工具返回结果，模型能继续；取消时工具返回取消，模型询问原因而不是自动重试。
79. 显示同步：在聊天里让 Agent 用 `bash` 运行 `lark-cli --version` 能成功；让 Agent 运行 `lark-cli auth logout` 后进入插件页，状态重新探测为未连接；插件页/App 恢复时状态与 CLI 实际状态一致。
80. 插件删除与异常：删除确认后安装记录消失、guest 内二进制/CLI 配置/凭证、仓库与生效技能目录都不存在；断网下载失败不留半安装；安装记录损坏时页面显示错误且文件保留；中英切换、深浅色下插件页、详情页与 setup sheet 正常。
81. 插件卡片：插件页标题右侧是刷新图标，无说明小字；每行左侧有插件图标，标题与摘要间距正常，右侧是开关；卡片无状态圆点和删除按钮，删除只在详情页。
82. 插件启停：关闭飞书后，开关关闭、技能页里的飞书技能变为未启用、`files/agent/skills/deepdeep-feishu` 消失而 `files/skills/deepdeep-feishu` 保留；新会话的系统提示中不再出现飞书。重新开启后技能恢复、下一轮提示恢复。
83. 飞书授权收敛：点「重新授权」完成浏览器授权；如果服务端裁掉了部分 scope，登录仍应成功，状态显示「缺少 N 项权限」，详情列出缺失项；sheet 自动关闭，App 尽量自动回到前台；如果进程超时但 `auth status` 已就绪，也按成功处理。
84. 表格兜底：在缺少 `sheets:spreadsheet:read` / `base:table:read` 的账号上让 Agent 读《财务及发票管理》与多维表格；模型不应反复要求重新授权，而应使用 `lark-cli drive +export` 导出后解析；详情页「应用权限」能打开飞书开放平台应用权限页。
85. 网络搜索：安装「网络搜索」后启用开关可打开；新会话让 Agent 搜索「今天的 AI 新闻」并抓取一个 URL，过程出现 `web_search` / `web_fetch` 行，回答带来源链接；没有 DeepSeek API Key 时状态为未配置、工具返回配置提示。
86. 网络搜索关闭：关闭网络搜索插件后，新会话不再出现 `web_search` / `web_fetch`；重新开启后新会话恢复（已有会话在空闲时重建后生效）。
87. MCP JSON 导入与编辑：MCP 页「+」→ 输入框为空、只显示占位提示 → 粘贴 `{"mcpServers":{"mcd-mcp":{"type":"streamablehttp","url":"https://mcp.mcd.cn","headers":{"Authorization":"Bearer …"}}}}` 或 `"mcd-mcp": {...}` → 导入；列表出现 `mcd-mcp`，`type` 写法被接受；一次导入多个；同名不覆盖；非法 JSON 留在 sheet 内报错，原 `mcp.json` 不变。点已有行直接显示 `"mcd-mcp": {...}` JSON，可编辑/重命名并保存。
88. MCP 刷新与技能文案：MCP 标题右侧刷新图标触发探测，页面显示 spinner；不再有「重新检测」文字行；技能页底部无说明小字。
89. 聊天与返回流畅度：assistant 消息底部没有复制/引用/分享图标行；长按选择仍有复制/引用/朗读，代码块复制可用；从技能/MCP/插件/定时任务返回聊天，聊天内容立即出现，不重新加载；`dumpsys gfxinfo` 无整页重建式长帧。
90. MCP 热更新：在已打开会话的 MCP 页关闭再开启服务器，应用期间开关不可点；`adb logcat -s DeepDeepRuntime` 出现 `mcp reload ... servers=[<name>=Connected/N/] deferred=N loaded=0`，无 `NetworkOnMainThreadException`；回到聊天让模型用 `tool_search` 搜该服务器工具，能返回工具并在下一轮调用；编辑/导入/删除服务器后同样立即生效；运行环境页开启/关闭 Linux 时不再因重建会话崩溃。

91. 创意工坊入口与列表：抽屉「创意工坊」→ 列表行显示 icon（manifest emoji，缺省 ✦）+ 名称 + 说明，无类型标签；失效目录显示「格式无效 · 原因」并可删除；空态提示用 `+` 或 `/skill:workshop` 创建。
92. `+` 预填：点右上角 `+` → 直接回到聊天并新建 Home 会话，输入框预填 `/skill:workshop `；不出现新建对话框或类型选择。
93. 技能可见与强制加载：技能页出现全局 `workshop` 技能，可查看/停用；在 Home 会话输入 `/skill:workshop 做一个心跳爱心` 发送 → 用户气泡只显示需求文字，没有技能标记，不出现 `<skill>` XML；模型知道创意工坊格式并调用 `workshop_create` / 文件工具 / `workshop_load`。
94. 普通会话创建：Agent 用绝对路径写页面并 `workshop_load` 成功后，assistant 下方出现入口卡片（emoji + 名称 + 「已创建」）；点卡片打开页面，交互正常；重新打开历史会话/重启 App 后卡片仍在。
95. 数据与持久化：在页面里操作产生数据（如爱心点击次数）→ 杀进程重开 → 数据仍在；代码在 `files/workshop/apps/<id>/`，数据在 `files/workshop/userdata/<id>.db`，Agent 重写页面不丢数据；删除创意同时清理目录与 DB（含 journal）。
96. 页面无 Agent：页面里 `typeof window.deepdeep.agent === 'undefined'`，API 只有 `env/info/data/toast`；没有 `permissions/capabilities`；详情页只有返回/标题/更多（重新加载/重命名/删除），没有 ✨、错误红点和 Agent sheet；用户要修改时退出到聊天。
97. 布局视口：使用 `100vh` / `height:100%` 的页面铺满 WebView（不再只渲染顶部一条 `100vh==0` 的异常）；页面滚动、键盘、深浅色主题变量正常。
98. 旧 manifest 兼容：把带 `type`/`permissions` 字段的 v1 创意留在 `files/workshop/apps/`，扫描与打开正常，列表不显示类型；无效目录删除后无残留。
99. 斜杠技能：命令面板只把 `/compact`、`/goal` 作为 App 命令显示，workshop 与其他已启用技能一起列在下方，点选补全 `/skill:<name> ` 到输入框；`/skill:workshop 需求` 与手输相同；发送后聊天气泡只显示需求文字，没有技能标记，也不显示原始 `<skill>` XML；`/skill:nope 需求` 提示「未找到技能」并保留输入框内容。
100. 重新打开会话后，含 `<skill>` 块的历史用户消息只显示需求文字（无技能标记）；命令面板重新打开时已启用技能列表仍与技能页一致。
101. 数据工具：在会话里让 Agent 只改某个已上架创意的数据（如「把跳动的心 taps 改成 7，不要改页面文件」）→ 模型调用 `workshop_list` 确认 app id，再 `workshop_edit`（`action=get`/`action=set`）；不调用 `workshop_load`、不改 `apps/<id>/` 文件；打开该创意显示新数据（点击次数 7），页面布局/代码未变；`action=keys` 能列出 key，`action=delete` 删除后页面读不到。
102. 页面 Agent 基础：打开一个创意页面，页面 JS 调用 `await app.agent.models()` 得到已配置 provider/model/thinkingLevels；调用 `await app.agent.capabilities()` 得到 tools 和 skills；`app.agent.run({prompt:"Return exactly PONG", providerId/modelId, thinkingLevel:"off", maxTurns:2, tools:[], skills:[]})` 返回 `PONG`；没有进入聊天会话列表。
103. 页面 Agent 并行/系统提示：页面用 `Promise.all` 同时启动两个 plain Agent，分别返回 ALPHA/BETA；带 `systemPrompt:"只输出大写"` 的 Agent 返回大写结果；请求不存在的 tool/skill 时 Promise reject，错误码 `invalid_params`。
104. 页面 Agent 生命周期：启动一个带 bash `sleep` 的 Agent 后立即返回/关闭创意页面 → App 不崩溃，正在运行的 Agent 被取消，`files/agent-runs/` 不残留运行目录；页面重新打开后不显示旧运行状态。
105. 页面 Agent 模型选择：页面用 `models()` 渲染 provider/model 选择（如 deepseek/deepseek-flash 与 opencode-go/deepseek-v4.1-flash），选择后 run 生效；provider/model 不合法时返回错误，不会使用其他模型。
106. 回归：聊天流式/停止、技能页、MCP 启停热更新、导航转场与返回不受影响；`adb logcat -s DeepDeepWorkshop DeepDeepRuntime` 无 `NetworkOnMainThreadException`、无重复 WebView 销毁异常、无 agent 取消导致的崩溃。
107. 技能包安装：`files/skills/workshop/` 与 `files/agent/skills/workshop/` 下同时存在 `SKILL.md`、`references/`（api-reference/tutorial/design-checklist）、`examples/`（4 个 html）；技能页 workshop 可见；普通会话加载 `/skill:workshop` 后 Agent 能 read `references/tutorial.md` 和 `examples/02-agent-workbench.html`。
108. 运行状态收敛：发送长任务后锁屏/切后台，恢复网络或等到后台任务唤醒结束，回到 App；模型已有最终回答时 UI 必须同步结束（无 working 指示、无转圈），不需要点停止。再测断网重试失败：失败轮/错误文案出现后 working 立即结束；回前台/切回会话自动对账。连续 3 次通过；`adb logcat -s DeepDeepRuntime` 无异常。
109. 会话独立模型：会话 A 选模型 X 发送一轮；切到会话 B 选模型 Y；回到 A，composer 显示 X 且下一轮请求实际用 X（厂商/模型正确）；B 显示 Y；重启 App 后各自保持。设置 → 厂商详情只剩 API Key/OAuth/配置状态，没有模型列表与「设为当前」；设置首页账号行只显示“新会话默认模型”。
110. 思考计时：长任务运行中状态行显示 `1h 2m 3s` 风格（中文为“1 小时 2 分 3 秒”），满分钟/满小时后高位正确；后台返回、切换会话后继续累计，不重置为 0 或倒退。
111. 交付 v2：模型调用 `present` 交付图片 → 聊天内出现缩略图，点开全屏可看；交付 html → 卡片内 WebView 直接渲染（可交互），点开放大全屏，外链走浏览器；交付文件 → 文件卡可打开/分享；交付创意 → 出现入口卡，点击进入工坊；旧会话里只有 `{"files":[...]}` 的历史仍正常显示；`workshop_load` 新运行不再自动贴入口卡（需显式 present）。
112. 页面 Agent 事件流：创意页调用 `app.agent.run({prompt, onEvent})`，页面能实时显示文本增量/工具开始结束，最终 `await` 仍得到完整文本；超时（180s）或取消时收到 `run_error`/Promise reject，不永久 loading；技能文档不再出现“单文件优先/单页优先”建议，事件流写法已更新。
113. 技能自发现：在 bash 里 `mkdir -p /skills/foo && cat > /skills/foo/SKILL.md`（带 name/description），或在会话里用 write 写 `files/agent/skills/foo/SKILL.md`；打开技能页能看到 foo（已全局启用），可启停/删除；新会话的系统提示/`/` 面板能看到 foo；写入 `/skill-repo` 的技能也能在技能页看到但默认不启用。
114. 无障碍 GUI：安装并启用「GUI 操作」插件（插件页状态未连接→打开无障碍设置→手动开启 DeepDeep 服务→回 App 变已连接；Android 13 受限设置需先允许）；让 Agent「先观察当前屏幕，点网络，返回」：工具返回精简节点树、tap/global 生效；scroll/set_text/wait 正常；关闭服务后工具返回明确错误；Agent 不点系统权限弹窗。
115. 语音助手：设置里申请系统助手角色成功；锁屏/桌面长按助手手势或 `adb shell am start -a android.intent.action.ASSIST` 打开语音 sheet；中文/英文各说一句，partial 更新、定稿后按设置填入或直发；回答完成后按设置自动朗读，可停止；首次麦克风权限允许/拒绝/永久拒绝均有正确提示；无识别服务/飞行模式有本地化错误。
116. 接收分享：从文件管理器与微信分别分享一张图片、一个文件、一段文本到 DeepDeep；冷启动（先杀进程）与热启动都进入聊天，图片/文件出现在附件 chips、文本预填输入框；生成中分享会等待而不是静默丢弃；超过 20 个/25MB 有提示；从 DeepDeep 自己分享时 chooser 不出现 DeepDeep。
117. 读取短信：设置 → Android 能力 → 读取短信：首次点击请求 READ_SMS，允许后开关开启；让 Agent 读取最近 5 条收件箱短信，返回地址/时间/正文（截断）且不写入/发送；关闭开关或撤销权限后工具报“未开启/无权限”；隐私页能看到短信说明。
118. 原功能回归：聊天流式/停止/重试/自动重试/压缩/目标/后台任务、技能安装与 `/skill:`、MCP 启停热更新、官方插件安装启停、创意工坊创建/数据工具/页面 Agent、定时任务投递、分享文件打开均正常；无崩溃、无 ANR。
119. 稳定性与外观：中英切换、深浅色、字体切换下新增界面正常；快速切会话/前后台切换 10 次无卡死；`adb logcat` 无 `NetworkOnMainThreadException`、无未捕获异常。

证据：`artifacts/validation/round30/v2_*.png`（v2 工坊基础）、`v3_*.png`（斜杠补全/目标）、`v4_*.png`（统一 `/skill:workshop` 面板、+ 预填、Agent 只改数据后页面显示 7），`v5_*.txt`（页面 Agent models/capabilities/PONG/并行/systemPrompt/校验错误）；技能包文件清单见 `v6_skill_package.txt`。

本轮证据（`artifacts/validation/round32/`）：`d2_skills.png`/`d3_skills_sync.png`（仓库副本删除后由全局技能目录自动回填）、`d4_settings.png`/`d5_settings_capabilities.png`（设置账号行与新 Android 能力组）、`d6_sms_permission.png`/`d7_sms_tool.png`（短信权限与 `read_sms` 工具执行）、`d8_share_cold.png`/`d9_share_text.png`（冷启动图片分享插入附件、热启动文本预填）、`d10_plugins.png`/`d11_gui_plugin.png`/`d12_gui_detail.png`/`d13_gui_home.png`（GUI 插件安装、无障碍已连接、observe/global 执行）、`d14_voice_sheet.png`（ASSIST 打开语音面板）、`d15_present.png`/`d16_present_file_creative.png`（present HTML/图片内联、文件卡与创意卡）、`d17_timer_5s.png`/`d21_timer_68s.png`/`d20_runstate_after.png`（计时 5s/1m 2s，长任务锁屏返回后无残留运行态、终态 DONE 保留）、`d22_model_switch.png`/`d24_one_session.png`（会话级模型切换与切回保持）、`d25_stream_page.png`~`d29_stream_events_done.png`（页面 Agent 事件流：run_start/thinking_delta/text_delta/turn_end/run_end，最终文本正常）、`android-capability-survey.md`（系统闹钟/日历/待办调研底稿）。

120. 小米云插件安装与开关：插件页出现「Xiaomi Cloud」；安装后详情显示未登录、7 项功能开关（笔记、待办、短信与通话、联系人、相册、录音、账号），默认全关；笔记/待办各一个开关同时控制读写；未登录时开关 disabled 并提示先登录；登录入口可见。
121. 小米云登录方式评估：浏览器打开 i.mi.com 正常，但 Chrome cookie 与 App CookieManager 隔离，插件读不到；系统 `com.xiaomi` 账号存在，但 auth token 需要签名级 `AUTH_SERVICE` 权限；WebView 仍保留为 fallback。真机 WebView 页面 DOM 已加载（CDP 可读到「使用小米账号登录」），但 CSS 高度为 0、登录内容不可见；登录未完成验证。
122. 安排插件：详情只显示状态/版本/大小；未授权日历权限时出现「授予日历权限」，授权后无打开类入口；不显示闹钟、日历介绍行或打开按钮。
123. 企业微信插件：下载约 9.1 MB 官方 CLI，安装后详情显示版本 1.3.4、CLI 版本 1.3.4、未连接；点「连接企业微信」打开 setup sheet，显示二维码、浏览器链接与等待提示；未完成扫码授权。
124. GUI 截图：无障碍服务已连接后，让 Agent 调用 `gui_screenshot`；工具返回 `/data/user/0/io.github.whywood.deepdeep/files/gui/screenshots/gui-…png`、约 449 KB、1080×2400；文件真实存在；`present` 在聊天里内联显示截图。
125. 聊天框：模型 sheet 内出现 Thinking level（Off/Low/High/Max），composer 无思考 chip；语音按钮为圆形；运行中停止按钮为红色圆点。
126. 设置/隐私：Android 能力组只有 Voice assistant / GUI control，无「读取短信」；隐私页显示小米云服务说明（加密 cookie、按开关读取、写入确认）。
127. 回归：插件页安装/启用/详情、安排打开系统应用、GUI 截图/present、composer 三态按钮、设置页滚动均正常；无崩溃。
128. 待补：企业微信扫码授权；小米云真实登录与 7 项数据读写真机验证；GUI find/wait/写文字校验真机验证。

本轮证据（`artifacts/validation/round33/`）：`r33_01_home.png`（composer 无 thinking chip、圆形语音）、`r33_02_model_sheet.png`（模型 sheet 内 Thinking level）、`r33_03_sending.png`（红色停止圆）、`r33_05_plugins.png`/`r33_06_plugins_installed.png`（新插件列表与安装）、`r33_09_micloud_detail.png`/`r33_10_micloud_actions.png`（小米云开关与登录入口）、`r33_11_micloud_login.png`（WebView 空白问题证据）、`r33_13_settings.png`/`r33_14_settings_capabilities.png`（设置无短信项）、`r33_15_arrange.png`/`r33_16_alarms.png`/`r33_17_calendar.png`（安排插件与系统闹钟/日历）、`r33_19_wecom.png`/`r33_20_wecom_installed.png`/`r33_21_wecom_detail.png`/`r33_22_wecom_qr.png`（企微安装、状态、二维码 setup）、`r33_23_gui_tool.png`/`r33_24_gui_done.png`（GUI 截图工具与 present 内联图）。

129. 安排·日历：会话里让 Agent 调用 `arrange_calendar` 的 `calendars`/`create`/`list`/`get`/`update`/`delete`；创建的日程在系统日历 App 可见，更新/删除同步；重复事件按实例展开读取；`reminders` 写入后系统日历显示提醒。
130. 安排·权限：撤销日历权限后工具返回明确错误；插件页出现「授予日历权限」，授予后恢复；不出现打开闹钟/日历按钮，也无闹钟相关工具。
131. 小米云开关：笔记、待办各只有一个开关，打开后 `mi_cloud_notes`/`mi_cloud_todo` 的读取与写入都可用；关闭后两者都不可用；其他 5 项只读且默认关闭。
132. 设置页：不再显示「语音助手」「GUI 控制」；GUI 权限从「GUI 操作」插件详情页进入，语音从 composer 麦克风/系统助手进入。
133. Composer：语音圆形按钮位于发送按钮左侧，附件与模型 chip 在左侧；运行中停止按钮仍为红色圆点；发送/停止、键盘、附件不受影响。
134. 回归：插件列表/详情、安排日历工具、小米云开关、设置页滚动、聊天输入与发送均正常；无崩溃。
135. GUI 性能 smoke：会话内输入/删除文字、键盘 inset 动画、展开 Process/ToolCard（箭头旋转 180°）、缩略图滚出再滚回；画面/行为与优化前一致，`adb logcat` 无 FATAL EXCEPTION。

本轮证据（`artifacts/validation/round35/`）：`01-composer-voice-left-of-send.png`（语音按钮在发送左侧）、`02-settings-no-voice-gui.png`（设置页无语音助手/GUI 控制）、`03-arrange-detail.png`（安排仅状态/版本/大小，无打开入口、无介绍行）、`04-micloud-switches.png`（7 项开关、笔记/待办各一个、默认 0/7）。androidTest：`:app:assembleDebugAndroidTest` 后 `adb shell am instrument -w io.github.whywood.deepdeep.test/androidx.test.runner.AndroidJUnitRunner`，1 test OK（日历 Provider CRUD）。

历史证据（`artifacts/validation/round34/`）：小米云登录页修复截图与 CDP 数据（`01-imi-home.png`~`06-account-login-cold.png`、`cdp-evidence.txt`）。

## round37（全局整合）验收

136. 停止与自动重试：断网发送进入「自动重试 n/5」倒计时，点停止 → 倒计时消失、之后不再自动重试、UI 回到可输入；再次发送正常。
137. 会话列表排序：打开一个较老会话发一条消息 → 抽屉里它排到所属工作区最前；杀进程重开仍按最近活动排序，`files/session-index.json` 存在且可重建。
138. 全天日历：让 Agent 创建 `2026-10-30` 全天事件 → 系统日历显示 30 号，`arrange_calendar list` 读回也是 30 号；跨日/多日事件结束日独占正确。
139. 插件升级失败保留旧版：已安装飞书后断网触发「更新」→ 失败提示；插件仍显示已安装/已连接、`lark-cli --version` 仍是旧版本、技能页技能仍在。
140. 工坊页面 Agent 边界：`app.agent.capabilities()` 只列 8 个白名单工具；`run({tools:["bash"]})` 被拒；让 Agent 读 `../` 或 `/etc/passwd` → 工具报 path outside workshop workspace；页面数据与事件流不受影响。
141. 运行中插件变更：会话跑长任务时切换插件开关或改 MCP 配置 → 会话不中断，本轮结束后下一轮生效，不被关闭重开。
142. 会话 JSONL 坏尾：`run-as` 在会话文件末尾追加半条 JSON → 重开 App 继续对话 → 新消息在历史里可见，同目录出现 `<file>.jsonl.corrupt`。
143. 附件大小：分享/选择超过 25MB 或未知长度的文件 → 提示过大，`files/home/attachments/` 无残留；导入中切到其他会话 → 附件不出现在新会话。
144. 回归：发消息/停止/重试/压缩/目标/提醒/后台任务/插件/工坊/日历读写/分享均正常；`adb logcat` 无 FATAL EXCEPTION。

## round38（可靠性收尾）验收

145. 提醒投递补投：创建一条 1 分钟内提醒后立即杀进程，重开 App → 到点只补投一次（不丢不重复）；`files/schedules/delivery-intents.json` 不残留已投递 intent。
146. Job 中断可见：让 Agent `job_start` 一个长任务（如 `sleep 90`）→ `files/jobs/jobs.json` 出现 RUNNING 记录 → 杀进程重开 → `job_list` 显示该 job 为 `interrupted`（结果未知），`job_output` 能读到中断说明；新任务可正常继续。
147. 待答问题中断：`ask_user_question` 等待时杀进程重开 → `files/questions/pending.json` 清空，打开该会话出现「上次提问已中断」灰字提示，通知栏出现汇总通知；发一条消息后提示清除。
148. 自动重试状态行：断网发送 → 状态行出现 `自动重试 n/5`（英文 `Retrying n/5`）并且 5 次内可持续看到；停止后消失且不再重试。
149. 插件升级失败（真机断网）：已安装飞书后断网点「重新安装」→ 失败提示，`lark-cli` 文件仍在且 md5 不变、详情页仍已连接、`lark-cli --version` 仍旧版本；技能与安装记录完好。
150. Markdown 流式：长回答流式过程中标题/列表/表格/代码块渲染与优化前一致，滚动不跳、无重复块；`adb logcat` 无异常。

## round39（运行恢复与预算）验收

151. 运行中断提示：发消息后立即杀进程 → 重开 App，该会话出现「上次运行被中断，结果未知」灰字提示，通知栏出现「有未完成项」；发下一条消息后提示立即消失，切走再回不重现；正常完成的会话不出现该提示。
152. 空闲 handle 预算：连续打开 ≥7 个会话（不发送）并等待空闲超过 10 分钟后，旧的会话 handle 被回收；回到被回收会话发消息自动重连成功；给被回收会话创建 1 分钟内提醒仍能正常投递。
153. 中断提示与提醒共存：同一会话同时存在「中断运行」和「中断提问」两条提示时发送消息，两条一起消失。
154. Job 输出节流：让 Agent `job_start` 一个持续输出的命令，后台任务面板输出仍秒级刷新；任务结束状态立即变为完成/失败，无卡在旧状态。
155. Markdown 流式渲染：长回答流式过程中标题、列表、表格、代码块与优化前一致，页脚/引用链接编号正确，滚动不跳动。
156. 凭据连写：连续快速保存两次不同 API Key，最终生效的是最后一次；重开 App 聊天可用。
157. 超大附件：分享超过 25MB 的文件 → 提示过大，`files/home/attachments/` 无残留。


## round41（抽屉/字体/搜索/插件化）验收

158. 抽屉折叠：某个工作区有 >5 条会话时，展开后只显示最近 5 条（置顶优先）+ 一行「更多历史 · N」；点击展开全部并在末尾显示「收起」；收起工作区再展开回到 5 条；切工作区/重启 App 后展开状态按工作区记忆。
159. 中文字体：聊天正文、设置页、表格、技能页中文均为无衬线黑体（非宋体）；切换到 Manrope 后英文变 Manrope、中文不变；emoji 正常显示；字体页仍只有 Lora / Manrope 两档；许可面板出现「思源黑体（Noto Sans SC）· OFL」。
160. 五页搜索：插件、MCP、技能、创意工坊、定时任务顶部各有搜索框；输入中英文片段即时过滤；无结果显示「没有匹配结果」；清除按钮恢复全部；定时任务空分组隐藏。
161. 插件分类：插件页顶部两段选项卡「外部 / 内部」；外部 3 条（飞书 / 企业微信 / 小米云服务）显示彩色品牌图标；内部 7 条（网络搜索 / 界面操作 / 安排 / 目标 / 后台任务 / 定时任务 / 创意工坊）；内部无下载/更新/删除入口；切换 tab 与搜索互不丢状态。
162. 内部插件设置：目标详情把「目标轮数上限」从 0 调到 3 并保存 → `files/plugins/settings.json` 出现 `goal.max_rounds=3`；创建目标时未显式指定轮数会按 3 封顶，`update_goal` 只改 objective 不影响轮数；改回 0 恢复不限。后台任务改 `max_running` 后同会话超限报错符合；创意工坊关 `skill_enabled` 后 `/skill:workshop` 不可用、开启恢复，改 `max_turns` 后页面 Agent 默认轮数变化。
163. 停用内部插件：停用「目标」后新会话（或重新进入会话）工具列表里没有 `get_goal`/`create_goal`/`update_goal`；重新启用后恢复且目标数据仍在。停用「创意工坊」后 `workshop_*` 工具与 workshop 技能消失，重新启用后 `files/agent/skills/workshop` 恢复多文件结构（SKILL.md + references + examples + marker）。
164. workshop_edit：让 Agent 对一个已上架创意分别执行 get / set / delete / keys；set 后页面重开读到新值；get 不存在的 key 返回 null；delete 后读不到；keys 返回排序列表；不调用 `workshop_load`、不改页面文件。
165. 小米云静默续期：正常登录小米云后 `am force-stop`，备份并删除 `files/datastore/secrets.preferences_pb`（同时会丢 API Key，测试后恢复），重启 App 打开插件页 → 数秒内自动回到「已登录」，无需手动操作；点「退出登录」后杀进程重启 → 保持「未登录」，且不会偷偷恢复；点「登录」正常打开登录页。
166. 插件页缓存：进入插件页先显示上次状态、后台再刷新；手动刷新图标强制探测；已停用/未连接状态显示正确；连续进出插件页 5 次不重复长时间转圈。
167. MCP 探测缓存：配置 ≥2 个启用的 HTTP MCP 时进入 MCP 页，60s 内再次进入不重复发起完整握手（标题栏 spinner 不常驻）；手动刷新强制探测；编辑/启停 server 后结果更新。
168. 冷启动与性能：`am start -W` 冷启动 TotalTime 相比基线（967ms）下降；输入 20 字符、抽屉开合 ×5、长会话打开首屏的 gfxinfo jank 不高于基线；设置页停留 10s（后台流式）时帧数明显下降、jank 不再高于聊天页；`dumpsys meminfo` 无 WebView 泄漏，线程数随会话开关不再无界增长。
169. release 包对比：`assembleRelease` 安装（同签名可覆盖）后，同一组场景 jank 低于 debug；App 数据（会话、插件、设置）保留。
170. 回归：发消息/停止/插话排队、提醒投递、后台任务、压缩卡片、`plugin_setup` 授权面板、创意工坊打开与数据读写、日历工具、MCP 工具调用均正常；`adb logcat` 无 `FATAL EXCEPTION`、无 `NetworkOnMainThreadException`。
