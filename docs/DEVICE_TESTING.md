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

`artifacts/validation/polish/`、`artifacts/validation/round6/`、`artifacts/validation/round9/`、`artifacts/validation/round10/`、`artifacts/validation/round11/`，设备日志用：

```bash
adb logcat -s DeepDeepRuntime DeepDeepChat
```
70. Provider OAuth 登录：设置 → 厂商 → 选一家支持 OAuth 的厂商（Anthropic/OpenAI 需先选登录方式）→ 点登录 → 浏览器完成授权或取消；取消后无状态变化，成功后显示「已登录」、可退出，重启 App 仍保持；浏览器授权完成后应自动回到 DeepDeep（无需手动复制 URL），未自动返回时可用 sheet 里的粘贴入口；已登录 OAuth 的厂商可直接激活并在聊天中发消息；保存 API Key 后切换回 API Key 认证。
71. MCP 列表：抽屉「MCP」→「+」添加一个可用的 Streamable HTTP 服务器（https）；保存后列表出现该行，自动检测显示「已连接 · N 个工具」；「测试连接」结果一致；关闭启用开关后显示「已停用」且不再检测；删除后行消失，重启 App 配置不恢复。
72. MCP OAuth：对没有 Authorization 头的服务器点「登录」→ 浏览器授权 → 返回后显示「已登录」并自动重连；「退出登录」后状态回到需要登录。收不到回调时粘贴回调链接也能完成。
73. MCP 工具：打开一个会话，让模型使用 MCP 工具（或请求「用 tool_search 找 MCP 工具」）；过程里出现 `server · tool` 形式的工具行与结果；工具输出进入对话，删除/停用服务器后新会话不再提供对应工具。
74. 导航收敛：抽屉只剩 技能 / MCP / 定时任务，无「聊天」「已归档」；设置页「已归档」显示数量，点击进入归档列表并能返回；已归档会话可在归档页打开/恢复。
