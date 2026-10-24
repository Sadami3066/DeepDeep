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
51. 系统提示词：新会话的 JSONL 初始 system 消息里 `preamble` 是 DeepDeep 自己的前言（不再出现 "You are an expert coding assistant operating inside pi"），`<rules>` 仍在、`<tools>` 文本段不再生成（工具定义走 system 消息的 `toolsAdded`）；下一轮只追加 `<environment>` / `<instruction>` patch，不再有重复身份的 `<system>` section。`files/agent/SYSTEM.md` 首次启动生成，手动改动后不会被覆盖。
52. OpenCode Go：切到 opencode-go 的模型（如 deepseek-v4.1-flash / claude-haiku-5-5）发一条消息；不再出现 400 `MissingSessionID`，正常回复；会话 JSONL 无 `stopReason=error`。
53. 模型 sheet 厂商切换：点 composer 模型 chip，sheet 顶部「厂商」行下方弹出专属下拉（不是上下文菜单），只列已配置 Key 的厂商；切换后模型列表更新、当前模型生效，重启后保持。
54. 重试（重新生成）：关掉自动重试，断网发送得到失败轮 → 恢复网络点「重试」：失败气泡和错误行保持不动，「重试」文字变成蓝色小圈圈，随后流式生成（不消失不重影）；用户消息不重复出现；会话 JSONL 里失败回合被 context edit 移除。
55. 自动重试：默认已开启（聊天设置可关），断网发送 → 状态行显示「自动重试 n/5」并自动重试，恢复网络后自动成功；超时类错误不自动重试。
56. 自动重试上限：持续断网时最多自动重试 5 次后停下，失败轮保留错误与「重试」。
57. 失败文案：错误轮显示「原因（错误码）」本地化文案，不出现「没有收到回复」占位；英文界面不出现中文；未知错误只显示「Request failed」，原始文本只在会话 JSONL。

58. 命令面板：行首输入 `/` 出现命令背板（只有「压缩上下文」「目标」），输入 `co` 过滤；在文字后空格输入 `/` 同样展开面板；点选后输入里的命令被移除、其他文字保留；输入 `/foo` 发送时提示未知命令且消息不发送。
59. 手动压缩：长对话中点 `/compact`，状态区显示「正在压缩上下文…」，完成后消息流出现「上下文已压缩：A → B」，上下文环下降；再点一次提示「没有可压缩的上下文」。
60. 上下文统计：composer 右侧出现百分比圆环；点开统计 sheet，上下文/窗口、阈值、输入/输出/缓存/费用齐全，数值带 `~`；切换会话后数字随之切换。
61. 目标：`/goal 写一份周报草稿`，目标条出现；模型完成后自动进入下一轮并显示带 Sparkles 的目标轮提示（第 N 轮 + objective），轮次持续增加且没有 10 轮限制；点「暂停」后不再续跑，「继续」恢复；完成后目标条消失并留一条「目标已完成」提示；杀进程重开后目标显示暂停，点继续可恢复。
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

91. 创意工坊入口与新建：抽屉「创意工坊」→ 空态显示「还没有创意」+ 创建提示 → 点右上角 `+` → 填名称、选类型（看板/工作台/工作流）→ 创建后直接进入详情页，starter 页面正常渲染、返回正常；列表行显示 emoji 图标、「类型 · 描述」与 chevron。
92. 对话创建：新对话里「做一个学习看板创意」→ 模型调用 `workshop_create` 建草稿、用绝对路径写入页面、调用 `workshop_register` 上架；回复下方出现入口卡片（emoji + 名称 + 「类型 · 已创建」）；点卡片直接打开该创意并正常交互；重新打开历史会话卡片仍在。
93. 数据持久化：在看板页面添加/勾选条目 → 杀进程重开 → 数据仍在；「让 Agent 修改」重写页面后数据仍在（数据在 `files/workshop/userdata/<id>.db`，不在页面目录）。
94. 站内修改：详情页 ✨ → 输入修改需求 → 状态流式显示 → 成功后 sheet 自动关闭并刷新页面，修改生效且数据未丢；运行中可停止。
95. 页面调用受限 Agent：工作台 starter（或页面按钮）调用 `agent.run` → 页面显示 started/text/tool/finished 增量与最终结果；运行中再次调用返回 busy；关闭页面会取消运行；页面 Agent 只能读写本创意目录（尝试读目录外文件被拒绝），manifest 去掉 `agent.run` 后调用返回 forbidden。
96. 报错自愈：页面未捕获异常/console error 后 ✨ 出现红点；打开 Agent 面板自动预填「根据报错修复」+ 堆栈；开始修复后成功后页面刷新、红点消失；主框架加载失败显示整页错误与重试。
97. 注册失败路径：`workshop_create` 后删掉入口文件或把 manifest 写坏再 `workshop_register` → 工具返回错误、工坊列表不出现该创意、草稿仍在；补齐后注册成功。
98. 扫描兜底：用 adb 把一个合法创意目录放进 `files/workshop/apps/`（含 manifest.json 与 entry）→ 进入工坊页自动出现并可使用；无效目录显示「格式无效 · 原因」并可删除；删除后目录与 `userdata/<id>.db*` 全部移除。
99. 渲染细节：页面内图片/字体正常；`<video>` 通过 Range 播放与拖动（拦截器返回 206）；外部链接用系统浏览器打开；页面输入框键盘不遮挡；App 切换深浅色时页面 CSS 变量同步；WebView 渲染进程崩溃后自动重建。
100. 创意工坊回归：聊天流式与停止、MCP 启停热更新、导航转场与返回均不受影响；`adb logcat -s DeepDeepWorkshop DeepDeepRuntime` 无 `NetworkOnMainThreadException`、无重复 WebView 销毁异常。

证据：`artifacts/validation/round30/`（列表/新建、聊天入口卡片、看板交互、重启持久化、Agent 修改、受限 Agent、报错红点与修复、深色主题、workflow 自动运行、扫描兜底与删除清理）。
