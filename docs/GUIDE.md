# 功能与边界

产品层说明：什么能用、怎么用、边界在哪。构建与安装见 [../README.md](../README.md)，不可推翻的设计取舍见 [DECISIONS.md](DECISIONS.md)。

## 对话

- Claude 风格布局：右侧浅色用户气泡、全宽 assistant、浮动 composer
- 过程按 DSH 思路展示：运行中展开，工具/思考行默认收起，结束后自动折叠只留最终回复（聊天设置可关）
- 工具调用与中间文本按事件顺序进入过程；历史会话按一个用户回合聚合
- Markdown：标题、段落、引用条、有序/无序/任务列表、表格、带语言标签与复制按钮的代码块
- 42 家 Provider，API Key / OAuth 登录（Anthropic、OpenAI、GitHub Copilot、Codex、xAI、OpenRouter、Kimi、Meta、Radius…）；模型与思考档**按会话独立**，只列已配置的厂商
- 会话历史、置顶、归档、重命名、删除；抽屉按工作区分组（每区默认显示最近 5 条）
- 运行状态对账：后台唤醒、锁屏断网重试结束后不会停在「运行中」；思考计时本地化（`1 小时 2 分 3 秒`）
- 正文字号 85%–140%（聊天设置，只缩放消息正文/代码/表格/过程）
- 字体：英文 Lora（默认）/ Manrope，中文走系统字体
- 上下文 token 环 + 会话统计 sheet（占用/窗口、输入/输出/缓存、费用）
- 选择文本后复制/全选/引用/朗读；引用以 blockquote 前缀随消息发出
- 附件：回形针多选，最多 20 个、单文件上限 1 GB，气泡外横排预览卡；支持图片、文本、`.md`
- 交付卡片：模型调 `present` 后按顺序显示图片（内联）、HTML（内联 WebView）、文件卡、创意入口卡
- 右上角「…」→「目录文件」进入工作区文件页面（可逐级返回）

## `/` 命令

只有整行是一个命令才执行；命令不进模型上下文。

| 命令 | 参数 | 行为 |
|---|---|---|
| `/compact` | 无（后面文字忽略） | 立即压缩较早历史，显示压缩前后 token；运行中/无可压缩内容时有提示 |
| `/goal` | `<目标文本>` / `pause` / `resume` / `clear` | 文本直接创建目标；pause/resume/clear 操作当前目标 |
| `/skill:<name>` | `[需求]` | 强制加载任意已启用技能，正文以 `<skill>` 块进入本轮；未知技能保留输入并提示 |

输入 `/` 会展开面板（只补全不执行），App 命令在上、已启用技能在下；未知命令不发送。

## 动手

- Alpine/proot 里的 `bash`：无 root 用户空间，**不是安全沙箱**
- 可选 root：已 root 设备在 设置 → 运行环境 开总开关后可用 `root_shell`（单条命令，默认 15s、上限 120s）；只在 App 前台且用户主动发起的轮次可用，后台/定时/目标续跑/工坊 Agent 都拿不到
- 后台任务：`job_start` / `job_output`（增量）/ `job_list` / `job_kill`；运行中数量显示在 composer 上方
- 定时任务：五段 cron（英文名、`*`/`/`/`,`/`-`、范围含 1827 天），绑定会话，到点投递回原对话；错过时下次打开补投一次；Service 被杀后不保证投递
- 目标（goal）：自动续跑，可暂停/继续/完成/清除；轮数上限来自设置或显式参数；**只在进程内有效**，重启清空
- 交付：`present` 文件/图片/HTML/创意

## 接入

- **MCP**：Streamable HTTP 服务器管理（含 OAuth、连接检测），工具/资源经 `tool_search` 按需加载，工具名 `mcp__<server>__<tool>`
- **插件**：
  - 外部（按需下载/安装/更新/卸载）：飞书、企业微信、小米云服务
  - 内部（内置，可单独启停）：目标、后台任务、定时任务、创意工坊、网络搜索、GUI 操作、安排
- **GUI 操作**：通过无障碍 `gui_observe/find/tap/set_text/scroll/global/wait/screenshot` 观察并操作当前界面
- **安排**：读写系统日历事件与提醒（需日历权限）
- **小米云服务**：WebView 登录 i.mi.com，按开关读写账号、笔记、待办、短信与通话、联系人、相册、录音
- **企业微信**：安装官方 `wecom-cli` 后扫码授权，操作消息/文档/日程/待办；**飞书**：安装 CLI 后走 device-flow 授权
- **技能**：zip 包安装到仓库，可全局或按工作区启用；复制到 agent 技能目录生效（不是软链）
- **语音**：系统 ASR 转写、系统 TTS 朗读；可申请 Android 系统助手角色
- **系统助手浮层**：成为默认助手后，任意 App 上长按电源/助手手势浮出助手卡；设置里可打开系统「按住电源按钮」隐藏页并预览
- **分享接收**：从别的 App 分享图片/文件/文本，插入当前会话或预填输入框

## 工具一览（写提示词时参考）

| 类别 | 工具 |
|---|---|
| pi 原生 | `read` `write` `edit` `ls` `grep` `find` `bash` |
| App 核心 | `ask_user_question` `plugin_setup` `present` |
| 目标 | `get_goal` `create_goal` `update_goal` |
| 后台任务 | `job_start` `job_output` `job_list` `job_kill` |
| 定时任务 | `schedule_create` `schedule_list` `schedule_update` `schedule_delete` |
| 创意工坊 | `workshop_create` `workshop_load` `workshop_list` `workshop_edit` |
| 网络搜索 | `web_search` `web_fetch` |
| GUI / 安排 / 小米云 | `gui_*`、`arrange_*`、`mi_cloud_*` |
| root（可选） | `root_shell` |
| MCP | `<server>` 提供的工具，形如 `mcp__<server>__<tool>` |

## 创意工坊

- 每个创意是 Agent 按需求写的 HTML/CSS/JS 文件夹，WebView 渲染成可交互轻应用；没有分类/模板
- 内置 workshop 技能可用 `/skill:workshop` 强制加载；建好后 `workshop_load` 装载、`present` 交付
- 页面经 bridge 读写自己的数据（SQLite，按创意隔离）；`workshop_edit` 可只改数据不重写页面；页面 JS 可用 `app.agent.*` 编排一次性 Agent
- 边界：页面不能调用 App 内部函数、不能执行 shell、读不到 App 其他数据

## 界面

- 常驻抽屉；设置与整页层级推入转场；统一 motion tokens 与按压态
- 浅白底、鲸蓝强调色、发丝线分隔；不用阴影/渐变/毛玻璃（开关 thumb 与分段选中 pill 除外）
- 设置 → 聊天设置：正文字号 + 过程自动折叠、自动重试、后台任务唤醒、识别后直接发送、自动朗读

## 已知边界

- 不做权限/审批系统；Linux 运行环境与可选 root 各有一个总开关
- Linux 是 proot 用户空间，不是安全沙箱
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据
- 定时投递只在服务存活时保证，错过补投一次
- 目标与后台任务只存在进程内存，重启清空
- 本地分发；release 变体用 debug 证书签名
